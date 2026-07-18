package io.termd.core.http;

import io.termd.core.function.Consumer;
import io.termd.core.util.Helper;
import io.termd.core.util.Vector;
import org.junit.Test;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class HttpTtyConnectionTest {

  @Test
  public void testTextInputUsesConnectionCharset() {
    final List<Integer> actual = new ArrayList<Integer>();
    HttpTtyConnection connection = new HttpTtyConnection(
        Charset.forName("UTF-16BE"), new Vector(80, 24)) {
      @Override
      protected void write(byte[] buffer) {
      }

      @Override
      public void execute(Runnable task) {
        task.run();
      }

      @Override
      public void schedule(Runnable task, long delay, TimeUnit unit) {
        task.run();
      }

      @Override
      public void close() {
      }
    };
    connection.setStdinHandler(new Consumer<int[]>() {
      @Override
      public void accept(int[] codePoints) {
        actual.addAll(Helper.list(codePoints));
      }
    });

    connection.writeToDecoder("{\"action\":\"read\",\"data\":\"中文\"}");

    assertEquals(Helper.list(Helper.toCodePoints("中文")), actual);
  }
}
