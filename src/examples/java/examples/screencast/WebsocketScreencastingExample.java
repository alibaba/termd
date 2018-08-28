package examples.screencast;

import io.termd.core.function.Consumer;
import io.termd.core.http.netty.NettyWebsocketTtyBootstrap;
import io.termd.core.tty.TtyConnection;

import java.awt.*;
import java.util.concurrent.TimeUnit;

public class WebsocketScreencastingExample {

  public synchronized static void main(String[] args) throws Throwable {
    NettyWebsocketTtyBootstrap bootstrap = new NettyWebsocketTtyBootstrap().setHost("localhost").setPort(8080);
    final Robot robot = new Robot();
    bootstrap.start(new Consumer<TtyConnection>() {
      @Override
      public void accept(TtyConnection conn) {
        new Screencaster(robot, conn).handle();
      }
    }).get(10, TimeUnit.SECONDS);
    System.out.println("Web server started on localhost:8080");
    WebsocketScreencastingExample.class.wait();
  }
}
