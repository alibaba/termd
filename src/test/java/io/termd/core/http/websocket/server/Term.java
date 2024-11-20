/*
 * Copyright 2015 Julien Viet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.termd.core.http.websocket.server;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import io.termd.core.function.BiConsumer;
import io.termd.core.function.Consumer;
import io.termd.core.pty.PtyMaster;
import io.termd.core.pty.Status;
import io.termd.core.pty.TtyBridge;
import io.undertow.server.HttpHandler;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.WebSocketProtocolHandshakeHandler;
import io.undertow.websockets.core.CloseMessage;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.ChannelListener;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

/**
* @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
* @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
*/
class Term {

  private Logger log = LoggerFactory.getLogger(Term.class);

  final String context;
  private Runnable onDestroy;
  final Set<Consumer<TaskStatusUpdateEvent>> statusUpdateListeners = new HashSet<Consumer<TaskStatusUpdateEvent>>();
  private WebSocketTtyConnection webSocketTtyConnection;
  private boolean activeCommand;
  private ScheduledExecutorService executor;

  public Term(TermServer termServer, String context, Runnable onDestroy, ScheduledExecutorService executor) {
    this.context = context;
    this.onDestroy = onDestroy;
    this.executor = executor;
  }

  public void addStatusUpdateListener(Consumer<TaskStatusUpdateEvent> statusUpdateListener) {
    statusUpdateListeners.add(statusUpdateListener);
  }

  public void removeStatusUpdateListener(Consumer<TaskStatusUpdateEvent> statusUpdateListener) {
    statusUpdateListeners.remove(statusUpdateListener);
  }

  public Consumer<PtyMaster> onTaskCreated() {
    return new Consumer<PtyMaster>() {
      @Override
      public void accept(final PtyMaster ptyMaster) {
        ptyMaster.setChangeHandler(new BiConsumer<Status, Status>() {
          @Override
          public void accept(Status prev, Status next) {
            notifyStatusUpdated(new TaskStatusUpdateEvent("" + ptyMaster.getId(), prev, next, context));
          }
        });
      }
    };
  }

  void notifyStatusUpdated(TaskStatusUpdateEvent event) {
    if (event.getNewStatus().isFinal()) {
      activeCommand = false;
      log.trace("Command [context:{} taskId:{}] execution completed with status {}.", event.getContext(), event.getTaskId(), event.getNewStatus());
      destroyIfInactiveAndDisconnected();
    } else {
      activeCommand = true;
    }
    for (Consumer<TaskStatusUpdateEvent> statusUpdateListener : statusUpdateListeners) {
      log.debug("Notifying listener {} in task {} with new status {}", statusUpdateListener, event.getTaskId(), event.getNewStatus());
      statusUpdateListener.accept(event);
    }
  }

  private void destroyIfInactiveAndDisconnected() {
    if (!activeCommand && !webSocketTtyConnection.isOpen()) {
      log.debug("Destroying Term as there is no running command and no active connection.");
      onDestroy.run();
    }
  }

  synchronized HttpHandler getWebSocketHandler() {
    WebSocketConnectionCallback onWebSocketConnected = new WebSocketConnectionCallback() {
      @Override
      public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel webSocketChannel) {
        if (webSocketTtyConnection == null) {
          webSocketTtyConnection = new WebSocketTtyConnection(webSocketChannel, executor);
          webSocketChannel.addCloseTask(new ChannelListener<WebSocketChannel>() {
            @Override
            public void handleEvent(WebSocketChannel webSocketChannel) {
              webSocketTtyConnection.removeWebSocketChannel();
              destroyIfInactiveAndDisconnected();
            }
          });
          TtyBridge ttyBridge = new TtyBridge(webSocketTtyConnection);
          ttyBridge
                  .setProcessListener(onTaskCreated())
                  .readline();
        } else {
          if (webSocketTtyConnection.isOpen()) {
            webSocketTtyConnection.addReadonlyChannel(webSocketChannel);
            webSocketChannel.addCloseTask(new ChannelListener<WebSocketChannel>() {
              @Override
              public void handleEvent(WebSocketChannel webSocketChannel) {
                webSocketTtyConnection.removeReadonlyChannel(webSocketChannel);
                destroyIfInactiveAndDisconnected();
              }
            });
          } else {
            webSocketTtyConnection.setWebSocketChannel(webSocketChannel);
            webSocketChannel.addCloseTask(new ChannelListener<WebSocketChannel>() {
              @Override
              public void handleEvent(WebSocketChannel webSocketChannel) {
                webSocketTtyConnection.removeWebSocketChannel();
                destroyIfInactiveAndDisconnected();
              }
            });
          }
        }
      }
    };
    return new WebSocketProtocolHandshakeHandler(onWebSocketConnected);
  }

  HttpHandler webSocketStatusUpdateHandler() {
    WebSocketConnectionCallback webSocketConnectionCallback = new WebSocketConnectionCallback() {
      @Override
      public void onConnect(WebSocketHttpExchange exchange, final WebSocketChannel webSocketChannel) {
        final Consumer<TaskStatusUpdateEvent> statusUpdateListener = new Consumer<TaskStatusUpdateEvent>() {
          @Override
          public void accept(TaskStatusUpdateEvent event) {
              Map<String, Object> statusUpdate = new HashMap<>();
              statusUpdate.put("action", "status-update");
              statusUpdate.put("event", event);
              try {
                  String message = JSON.toJSONString(statusUpdate);
                  WebSockets.sendText(message, webSocketChannel, null);
              } catch (JSONException e) {
                  log.error("Cannot write object to JSON", e);
                  String errorMessage = "Cannot write object to JSON: " + e.getMessage();
                  WebSockets.sendClose(CloseMessage.UNEXPECTED_ERROR, errorMessage, webSocketChannel, null);
              }
          }
        };

        log.debug("Registering new status update listener {}.", statusUpdateListener);
        addStatusUpdateListener(statusUpdateListener);
        webSocketChannel.addCloseTask(new ChannelListener<WebSocketChannel>() {
          @Override
          public void handleEvent(WebSocketChannel webSocketChannel) {
            removeStatusUpdateListener(statusUpdateListener);
          }
        });
      }
    };

    return new WebSocketProtocolHandshakeHandler(webSocketConnectionCallback);
  }
}
