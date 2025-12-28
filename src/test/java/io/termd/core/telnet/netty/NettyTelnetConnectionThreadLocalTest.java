package io.termd.core.telnet.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalEventLoopGroup;
import io.netty.channel.local.LocalServerChannel;
import io.netty.util.internal.InternalThreadLocalMap;
import io.termd.core.telnet.TelnetHandler;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NettyTelnetConnectionThreadLocalTest {

  @Test
  public void writeFromNonEventLoopDoesNotCreateNettyThreadLocal() throws Exception {
    EventLoopGroup group = new LocalEventLoopGroup(1);
    Channel serverChannel = null;
    Channel clientChannel = null;
    try {
      LocalAddress address = new LocalAddress("termd-" + System.nanoTime());
      final AtomicReference<NettyTelnetConnection> connectionRef = new AtomicReference<NettyTelnetConnection>();
      final CountDownLatch connectionCreated = new CountDownLatch(1);

      serverChannel = new ServerBootstrap()
        .group(group)
        .channel(LocalServerChannel.class)
        .childHandler(new ChannelInitializer<LocalChannel>() {
          @Override
          protected void initChannel(LocalChannel ch) throws Exception {
            ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
              @Override
              public void channelActive(ChannelHandlerContext ctx) throws Exception {
                NettyTelnetConnection conn = new NettyTelnetConnection(new TelnetHandler(), ctx);
                conn.onInit();
                connectionRef.set(conn);
                connectionCreated.countDown();
              }
            });
          }
        })
        .bind(address)
        .sync()
        .channel();

      clientChannel = new Bootstrap()
        .group(group)
        .channel(LocalChannel.class)
        .handler(new ChannelInboundHandlerAdapter())
        .connect(address)
        .sync()
        .channel();

      assertTrue(connectionCreated.await(5, TimeUnit.SECONDS));

      final NettyTelnetConnection conn = connectionRef.get();
      assertNotNull(conn);

      final AtomicReference<InternalThreadLocalMap> before = new AtomicReference<InternalThreadLocalMap>();
      final AtomicReference<InternalThreadLocalMap> after = new AtomicReference<InternalThreadLocalMap>();
      Thread writerThread = new Thread(new Runnable() {
        @Override
        public void run() {
          InternalThreadLocalMap.remove();
          before.set(InternalThreadLocalMap.getIfSet());
          conn.write("hello".getBytes(StandardCharsets.UTF_8));
          after.set(InternalThreadLocalMap.getIfSet());
        }
      });
      writerThread.start();
      writerThread.join(TimeUnit.SECONDS.toMillis(5));
      assertFalse(writerThread.isAlive());

      assertNull(before.get());
      assertNull(after.get());
    } finally {
      if (clientChannel != null) {
        clientChannel.close().sync();
      }
      if (serverChannel != null) {
        serverChannel.close().sync();
      }
      group.shutdownGracefully().sync();
    }
  }
}

