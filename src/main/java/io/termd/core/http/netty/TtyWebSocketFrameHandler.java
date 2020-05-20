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

package io.termd.core.http.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.termd.core.function.Consumer;
import io.termd.core.http.HttpTtyConnection;
import io.termd.core.tty.TtyConnection;
import io.termd.core.util.ByteBufPool;

import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class TtyWebSocketFrameHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

  private final ChannelGroup group;
  private final Consumer<TtyConnection> handler;
  private ChannelHandlerContext context;
  private HttpTtyConnection conn;
  private final ByteBufPool byteBufPool;
  private Class[] removingHandlerClasses;

  public TtyWebSocketFrameHandler(ChannelGroup group, Consumer<TtyConnection> handler, Class... removingHandlerClasses) {
    this.group = group;
    this.handler = handler;
    this.removingHandlerClasses = removingHandlerClasses;
    byteBufPool = new ByteBufPool();
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) throws Exception {
    super.channelActive(ctx);
    context = ctx;
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
    if (evt == WebSocketServerProtocolHandler.ServerHandshakeStateEvent.HANDSHAKE_COMPLETE) {
      if (removingHandlerClasses != null) {
        for (Class handlerClass : removingHandlerClasses) {
          ctx.pipeline().remove(handlerClass);
        }
      }
      group.add(ctx.channel());
      conn = new HttpTtyConnection() {
        @Override
        protected void write(byte[] buffer, int offset, int length) {

          int start = offset;
          int remain = length;
          while (remain > 0) {
            if (context == null) {
              break;
            }

            //ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer(remain<=32?32: (remain<=64?64: byteBufSize));
            final ByteBuf byteBuf = byteBufPool.get(50, TimeUnit.MILLISECONDS);
            boolean done = false;
            int size = 0;

            try {
              //write segment
              size = Math.min(remain, byteBuf.writableBytes());
              byteBuf.writeBytes(buffer, start, size);
              if (context != null) {
                context.writeAndFlush(new TextWebSocketFrame(byteBuf)).addListener(new ChannelFutureListener() {
                  @Override
                  public void operationComplete(ChannelFuture future) throws Exception {
                    byteBufPool.put(byteBuf);
                  }
                });
                done = true;
              }
            } finally {
              if (!done) {
                //discard
                byteBufPool.discard(byteBuf);
              }
            }

            start += size;
            remain -= size;
          }

        }

        @Override
        public void schedule(Runnable task, long delay, TimeUnit unit) {
          if (context != null) {
            context.executor().schedule(task, delay, unit);
          }
        }

        @Override
        public void execute(Runnable task) {
          if (context != null) {
            context.executor().execute(task);
          }
        }

        @Override
        public void close() {
          if (context != null) {
            context.close();
          }
          byteBufPool.release();
        }
      };
      handler.accept(conn);
    } else {
      super.userEventTriggered(ctx, evt);
    }
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    HttpTtyConnection tmp = conn;
    context = null;
    conn = null;
    if (tmp != null) {
      Consumer<Void> closeHandler = tmp.getCloseHandler();
      if (closeHandler != null) {
        closeHandler.accept(null);
      }
    }
  }

  public void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg) throws Exception {
    conn.writeToDecoder(msg.text());
  }
}
