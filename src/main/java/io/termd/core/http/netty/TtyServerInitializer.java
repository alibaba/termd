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

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.termd.core.function.Consumer;
import io.termd.core.tty.TtyConnection;


/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class TtyServerInitializer extends ChannelInitializer<SocketChannel> {

  private final ChannelGroup group;
  private final Consumer<TtyConnection> handler;
  private String httpResourcePath;

  public TtyServerInitializer(ChannelGroup group, Consumer<TtyConnection> handler) {
    this(group, handler, null);
  }

  public TtyServerInitializer(ChannelGroup group, Consumer<TtyConnection> handler, String httpResourcePath) {
      this.group = group;
      this.handler = handler;
      this.httpResourcePath = httpResourcePath;
  }

  @Override
  protected void initChannel(SocketChannel ch) throws Exception {
    ChannelPipeline pipeline = ch.pipeline();
    pipeline.addLast(new HttpServerCodec());
    pipeline.addLast(new ChunkedWriteHandler());
    pipeline.addLast(new HttpObjectAggregator(64 * 1024));
    HttpRequestHandler httpRequestHandler = null;
        if (httpResourcePath == null) {
            httpRequestHandler = new HttpRequestHandler("/ws");
        } else {
            httpRequestHandler = new HttpRequestHandler("/ws", httpResourcePath);
        }

    pipeline.addLast(httpRequestHandler);
    pipeline.addLast(new WebSocketServerProtocolHandler("/ws"));
    pipeline.addLast(new TtyWebSocketFrameHandler(group, handler, HttpRequestHandler.class));
  }
}
