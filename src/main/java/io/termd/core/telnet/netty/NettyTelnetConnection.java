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

package io.termd.core.telnet.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.termd.core.telnet.TelnetConnection;
import io.termd.core.telnet.TelnetHandler;
import io.termd.core.util.ByteBufPool;

import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author gongdewei 2020/05/20
 */
public class NettyTelnetConnection extends TelnetConnection {

  final ChannelHandlerContext context;

  final ByteBufPool byteBufPool;

  public NettyTelnetConnection(TelnetHandler handler, ChannelHandlerContext context) {
    super(handler);
    this.context = context;
    byteBufPool = new ByteBufPool();
  }

  @Override
  protected void execute(Runnable task) {
    context.channel().eventLoop().execute(task);
  }

  @Override
  protected void schedule(Runnable task, long delay, TimeUnit unit) {
    context.channel().eventLoop().schedule(task, delay, unit);
  }

  // Not properly synchronized, but ok for now
  @Override
  protected void send(byte[] data, int offset, int len) {

      int start = offset;
      int remain = len;
      while (remain > 0) {
//        ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer(remain<=32?32: (remain<=64?64: byteBufSize));
        final ByteBuf byteBuf = byteBufPool.get();

        //write segment
        int size = Math.min(remain, byteBuf.writableBytes());
        byteBuf.writeBytes(data, start, size);
        context.writeAndFlush(byteBuf).addListener(new ChannelFutureListener() {
          @Override
          public void operationComplete(ChannelFuture future) throws Exception {
            byteBufPool.put(byteBuf);
          }
        });

        start += size;
        remain -= size;
      }
  }

  @Override
  protected void onClose() {
    super.onClose();
  }

  @Override
  public void close() {
    context.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
  }
}
