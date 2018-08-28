package examples.events;

import io.termd.core.function.BiConsumer;
import io.termd.core.function.Consumer;
import io.termd.core.tty.TtyConnection;
import io.termd.core.tty.TtyEvent;
import io.termd.core.util.Vector;

/**
 * This example show how to handle TTY events.
 */
public class EventsExample {

  public static void handle(final TtyConnection conn) {

    conn.setEventHandler(new BiConsumer<TtyEvent, Integer>() {
      @Override
      public void accept(TtyEvent event, Integer key) {
        switch (event) {
          case INTR:
            conn.write("You did a Ctrl-C\n");
            break;
          case SUSP:
            conn.write("You did a Ctrl-Z\n");
            break;
          case EOF:
            conn.write("You did a Ctrl-D: closing\n");
            conn.close();
            break;
        }
      }
    });

    conn.setSizeHandler(new Consumer<Vector>() {
      @Override
      public void accept(Vector size) {
        conn.write("You resized your terminal to " + size + "\n");
      }
    });

    conn.setTerminalTypeHandler(new Consumer<String>() {
      @Override
      public void accept(String term) {
        conn.write("Your terminal is " + term + "\n");
      }
    });

    conn.setStdinHandler(new Consumer<int[]>() {
      @Override
      public void accept(int[] keys) {
        for (int key : keys) {
          conn.write("You typed " + key + "\n");
        }
      }
    });
  }

}
