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

package io.termd.core.http.websocket.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import io.termd.core.function.Consumer;
import io.termd.core.function.Supplier;
import io.termd.core.http.websocket.server.TaskStatusUpdateEvent;
import io.termd.core.util.ObjectWrapper;
import io.termd.core.util.Wait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 * @see "https://github.com/undertow-io/undertow/blob/5bdddf327209a4abf18792e78148863686c26e9b/websockets-jsr/src/test/java/io/undertow/websockets/jsr/test/BinaryEndpointTest.java"
 */
public class Client {

  public static final String WEB_SOCKET_TERMINAL_PATH = "/socket/term";
  public static final String WEB_SOCKET_LISTENER_PATH = "/socket/process-status-updates";

  private static final Logger log = LoggerFactory.getLogger(Client.class);

  ProgramaticClientEndpoint endpoint = new ProgramaticClientEndpoint();
  private Consumer<Session> onOpenConsumer;
  private Consumer<String> onStringMessageConsumer;
  private Consumer<byte[]> onBinaryMessageConsumer;
  private Consumer<CloseReason> onCloseConsumer;
  private Consumer<Throwable> onErrorConsumer;

  public Endpoint connect(String websocketUrl) throws Exception {
    ClientEndpointConfig clientEndpointConfig = ClientEndpointConfig.Builder.create().build();
    ContainerProvider.getWebSocketContainer().connectToServer(endpoint, clientEndpointConfig, new URI(websocketUrl));
    return endpoint;
  }

  public void close() throws Exception {
    log.debug("Client is closing connection.");
    endpoint.session.close();
//        endpoint.closeLatch.await(10, TimeUnit.SECONDS);
  }

  public void onOpen(Consumer<Session> onOpen) {
    onOpenConsumer = onOpen;
  }

  public void onStringMessage(Consumer<String> onStringMessage) {
    onStringMessageConsumer = onStringMessage;
  }

  public void onBinaryMessage(Consumer<byte[]> onBinaryMessage) {
    onBinaryMessageConsumer = onBinaryMessage;
  }

  public void onClose(Consumer<CloseReason> onClose) {
    onCloseConsumer = onClose;
  }

  public void onError(Consumer<Throwable> onError) {
    onErrorConsumer = onError;
  }

  public RemoteEndpoint.Basic getRemoteEndpoint() {
    return endpoint.session.getBasicRemote();
  }

  public class ProgramaticClientEndpoint extends Endpoint {
    volatile Session session;

    @Override
    public void onOpen(Session session, EndpointConfig config) {
      log.debug("Client received open.");
      this.session = session;

      session.addMessageHandler(new MessageHandler.Whole<String>() {
        @Override
        public void onMessage(String message) {
          log.trace("Client received text MESSAGE: {}", message);
          if (onStringMessageConsumer != null) {
            onStringMessageConsumer.accept(message);
          }
        }
      });
      session.addMessageHandler(new MessageHandler.Whole<byte[]>() {
        @Override
        public void onMessage(byte[] bytes) {
          log.trace("Client received binary MESSAGE: {}", new String(bytes));
          if (onBinaryMessageConsumer != null) {
            onBinaryMessageConsumer.accept(bytes);
          }
        }
      });
      if (onOpenConsumer != null) {
        onOpenConsumer.accept(session);
      }
    }

    @Override
    public void onClose(Session session, CloseReason closeReason) {
      log.debug("Client received close.");
      onCloseConsumer.accept(closeReason);
    }

    @Override
    public void onError(Session session, Throwable thr) {
      if (onErrorConsumer != null) {
        onErrorConsumer.accept(thr);
      } else {
        log.error("No error handler defined. Received error was: ", thr);
      }
    }
  }

  public static Client initializeDefault() {
    Client client = new Client();

    Consumer<Session> onOpen = new Consumer<Session>() {
      @Override
      public void accept(Session session) {
        log.info("Client connection opened.");
      }
    };

    Consumer<CloseReason> onClose = new Consumer<CloseReason>() {
      @Override
      public void accept(CloseReason closeReason) {
        log.info("Client connection closed. " + closeReason);
      }
    };

    client.onOpen(onOpen);
    client.onClose(onClose);

    return client;
  }


  public static Client connectStatusListenerClient(String webSocketUrl, final Consumer<TaskStatusUpdateEvent> onStatusUpdate) {
    Client client = Client.initializeDefault();
    Consumer<String> responseConsumer = new Consumer<String>() {
        public void accept(String text) {
            log.trace("Decoding response: {}", text);
            JSONObject jsonObject = null;
            try {
                jsonObject = JSON.parseObject(text);
            } catch (JSONException e) {
                log.error("Cannot read JSON string: " + text, e);
                return;
            }
            try {
                JSONObject eventJson = jsonObject.getJSONObject("event");
                TaskStatusUpdateEvent taskStatusUpdateEvent = TaskStatusUpdateEvent.fromJson(eventJson.toString());
                onStatusUpdate.accept(taskStatusUpdateEvent);
            } catch (Exception e) {
                log.error("Cannot deserialize TaskStatusUpdateEvent.", e);
            }
        }
    };

    client.onStringMessage(responseConsumer);

    client.onClose(new Consumer<CloseReason>() {
      @Override
      public void accept(CloseReason closeReason) {
        // no op
      }
    });

    try {
      client.connect(webSocketUrl + "/");
    } catch (Exception e) {
      throw new AssertionError("Failed to connect to remote client.", e);
    }
    return client;
  }

  public static Client connectCommandExecutingClient(String webSocketUrl, final Consumer<String> responseDataConsumer) throws InterruptedException, TimeoutException {
    final ObjectWrapper<Boolean> connected = new ObjectWrapper<Boolean>(false);

    Client client = Client.initializeDefault();
    Consumer<byte[]> responseConsumer = new Consumer<byte[]>() {
      @Override
      public void accept(byte[] bytes) {
        String responseData = new String(bytes);
        if ("% ".equals(responseData)) { //TODO use events
          connected.set(true);
        } else {
          if (responseDataConsumer != null) {
            responseDataConsumer.accept(responseData);
          }
        }
      }
    };

    client.onBinaryMessage(responseConsumer);

    client.onClose(new Consumer<CloseReason>() {
      @Override
      public void accept(CloseReason closeReason) {
        // no op
      }
    });

    try {
      client.connect(webSocketUrl + "/");
    } catch (Exception e) {
      throw new AssertionError("Failed to connect to remote client.", e);
    }
    Wait.forCondition(new Supplier<Boolean>() {
      @Override
      public Boolean get() {
        return connected.get();
      }
    }, 5, TimeUnit.SECONDS, "Client was not connected within given timeout.");
    return client;
  }

  public static void executeRemoteCommand(Client client, String command) {
    log.info("Executing remote command ...");
    RemoteEndpoint.Basic remoteEndpoint = client.getRemoteEndpoint();
    String data = "{\"action\":\"read\",\"data\":\"" + command + "\\r\\n\"}";
    try {
      remoteEndpoint.sendBinary(ByteBuffer.wrap(data.getBytes()));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
