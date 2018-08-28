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

package io.termd.core.tty;

import io.termd.core.TestBase;
import io.termd.core.function.BiConsumer;
import io.termd.core.function.Consumer;
import io.termd.core.util.Helper;
import io.termd.core.util.Vector;
import org.junit.Test;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class TtyTestBase extends TestBase {

  protected Charset charset = Charset.forName("UTF-8");

  protected abstract void assertConnect(String term) throws Exception;

  /**
   * Assert we can read a string of a specified length in bytes.
   *
   * @param len the lenght in bytes
   * @return the string
   * @throws Exception
   */
  protected abstract String assertReadString(int len) throws Exception;

  protected abstract void assertWrite(String s) throws Exception;

  protected abstract void assertWriteln(String s) throws Exception;

  protected abstract void server(Consumer<TtyConnection> onConnect);

  protected abstract void resize(int width, int height) throws Exception;

  protected void assertDisconnect(boolean clean) throws Exception {
    throw new UnsupportedOperationException();
  }

  protected void assertConnect() throws Exception {
    assertConnect(null);
  }

  protected final void assertWrite(int... codePoints) throws Exception {
    assertWrite(Helper.fromCodePoints(codePoints));
  }

  @Test
  public void testWrite() throws Exception {
    final AtomicInteger requestCount = new AtomicInteger();
    server(new Consumer<TtyConnection>() {
      @Override
      public void accept(TtyConnection conn) {
        requestCount.incrementAndGet();
        conn.stdoutHandler().accept(new int[]{'%', ' '});
      }
    });
    assertConnect();
    assertEquals("% ", assertReadString(2));
    assertEquals(1, requestCount.get());
  }

  @Test
  public void testRead() throws Exception {
    final ArrayBlockingQueue<int[]> queue = new ArrayBlockingQueue<int[]>(1);
    server(new Consumer<TtyConnection>() {
      @Override
      public void accept(final TtyConnection conn) {
          conn.setStdinHandler(new Consumer<int[]>() {
            @Override
            public void accept(int[] data) {
              queue.add(data);
              conn.stdoutHandler().accept(new int[]{'h', 'e', 'l', 'l', 'o'});
            }
          });
      }
    });
    assertConnect();
    assertWriteln("");
    int[] data = queue.poll(10, TimeUnit.SECONDS);
    assertTrue(Arrays.equals(new int[]{'\r'}, data));
    assertEquals("hello", assertReadString(5));
  }

  @Test
  public void testSignalInterleaving() throws Exception {
    final StringBuilder buffer = new StringBuilder();
    final AtomicInteger count = new AtomicInteger();
    server(new Consumer<TtyConnection>() {
      @Override
      public void accept(TtyConnection conn) {
        conn.setStdinHandler(new Consumer<int[]>() {
          @Override
          public void accept(int[] event) {
            Helper.appendCodePoints(event, buffer);
          }
        });

        conn.setEventHandler(new BiConsumer<TtyEvent, Integer>() {
          @Override
          public void accept(TtyEvent event, Integer cp) {
            if (event == TtyEvent.INTR) {
              switch (count.get()) {
                case 0:
                  assertEquals("hello", buffer.toString());
                  buffer.setLength(0);
                  count.set(1);
                  break;
                case 1:
                  assertEquals("bye", buffer.toString());
                  count.set(2);
                  testComplete();
                  break;
                default:
                  fail("Not expected");
              }
            }
          }
        });
      }
    });

    assertConnect();
    assertWrite('h', 'e', 'l', 'l', 'o', 3, 'b', 'y', 'e', 3);
    await();
  }

  @Test
  public void testSignals() throws Exception {
    final StringBuilder buffer = new StringBuilder();
    final AtomicInteger count = new AtomicInteger();
    server(new Consumer<TtyConnection>() {
      @Override
      public void accept(TtyConnection conn) {
        conn.setStdinHandler(new Consumer<int[]>() {
          @Override
          public void accept(int[] event) {
            Helper.appendCodePoints(event, buffer);
          }
        });
        conn.setEventHandler(new BiConsumer<TtyEvent, Integer>() {
          @Override
          public void accept(TtyEvent event, Integer cp) {
            switch (count.get()) {
              case 0:
                assertEquals(TtyEvent.INTR, event);
                count.set(1);
                break;
              case 1:
                assertEquals(TtyEvent.EOF, event);
                count.set(2);
                break;
              case 2:
                assertEquals(TtyEvent.SUSP, event);
                count.set(3);
                testComplete();
                break;
              default:
                fail("Not expected");
            }
          }
        });
      }
    });
    assertConnect();
    assertWrite(3, 4, 26);
    await();
  }

  @Test
  public void testServerDisconnect() throws Exception {
    final CountDownLatch closedLatch = new CountDownLatch(1);
    server(new Consumer<TtyConnection>() {
      @Override
      public void accept(final TtyConnection conn) {
        conn.setStdinHandler(new Consumer<int[]>() {
          @Override
          public void accept(int[] bytes) {
            conn.close();
          }
        });
        conn.setCloseHandler(new Consumer<Void>() {
          @Override
          public void accept(Void v) {
            closedLatch.countDown();
          }
        });
      }
    });
    assertConnect();
    assertWrite("whatever");
    assertTrue(closedLatch.await(10, TimeUnit.SECONDS));
  }

  @Test
  public void testClientDisconnectClean() throws Exception {
    testClientDisconnect(true);
  }

  @Test
  public void testClientDisconnect() throws Exception {
    testClientDisconnect(false);
  }

  private void testClientDisconnect(boolean clean) throws Exception {
    final CountDownLatch disconnectLatch = new CountDownLatch(1);
    final CountDownLatch closedLatch = new CountDownLatch(1);
    server(new Consumer<TtyConnection>() {
      @Override
      public void accept(TtyConnection conn) {
        disconnectLatch.countDown();
        conn.setCloseHandler(new Consumer<Void>() {
          @Override
          public void accept(Void v) {
            closedLatch.countDown();
          }
        });
      }
    });
    assertConnect();
    assertTrue(disconnectLatch.await(10, TimeUnit.SECONDS));
    assertDisconnect(clean);
    assertTrue(closedLatch.await(10, TimeUnit.SECONDS));
  }

  @Test
  public void testTerminalType() throws Exception {
    final Consumer<String> assertTerm = new Consumer<String>() {
      @Override
      public void accept(String term) {
        if (term.equals("xterm") || term.equals("vt100")) {
          testComplete();
        } else {
          fail("Unexpected term " + term);
        }
      }
    };
    server(new Consumer<TtyConnection>() {
      @Override
      public void accept(TtyConnection conn) {
        if (conn.terminalType() != null) {
          assertTerm.accept(conn.terminalType());
        } else {
          conn.setTerminalTypeHandler(assertTerm);
        }
      }
    });
    assertConnect("xterm");
    assertWrite("bye");
    await();
  }

  @Test
  public void testSize() throws Exception {
    server(new Consumer<TtyConnection>() {
      @Override
      public void accept(final TtyConnection conn) {
        if (conn.size() != null) {
          assertEquals(80, conn.size().x());
          assertEquals(24, conn.size().y());
          testComplete();
        } else {
          conn.setSizeHandler(new Consumer<Vector>() {
            @Override
            public void accept(Vector size) {
              assertEquals(80, conn.size().x());
              assertEquals(24, conn.size().y());
              testComplete();
            }
          });
        }
      }
    });
    assertConnect();
    await();
  }

  @Test
  public void testResize() throws Exception {
    server(new Consumer<TtyConnection>() {
      @Override
      public void accept(final TtyConnection conn) {
        conn.setSizeHandler(new Consumer<Vector>() {
          @Override
          public void accept(Vector size) {
            assertEquals(40, conn.size().x());
            assertEquals(12, conn.size().y());
            testComplete();
          }
        });
      }
    });

    assertConnect();
    resize(40, 12);
    await();
  }

  @Test
  public void testConnectionClose() throws Exception {
    final AtomicInteger closeCount = new AtomicInteger();
    server(new Consumer<TtyConnection>() {
      @Override
      public void accept(final TtyConnection conn) {
        conn.setCloseHandler(new Consumer<Void>() {
          @Override
          public void accept(Void v) {
            if (closeCount.incrementAndGet() > 1) {
              fail("Closed call several times");
            } else {
              testComplete();
            }
          }
        });
        conn.setStdinHandler(new Consumer<int[]>() {
          @Override
          public void accept(int[] text) {
            conn.close();
          }
        });
      }
    });
    assertConnect();
    assertWrite("bye");
    await();
  }

  /**
   * Check if the client is disconnected, this affects the connection, so this should not be used if the
   * connection needs to be used after.
   *
   * @return if the client is disconnected
   */
  public boolean checkDisconnected() {
    throw new UnsupportedOperationException();
  }

  @Test
  public void testConnectionCloseImmediatly() throws Exception {
    server(new Consumer<TtyConnection>() {
      @Override
      public void accept(TtyConnection conn) {
        conn.setCloseHandler(new Consumer<Void>() {
          @Override
          public void accept(Void v) {
            new Thread() {
              @Override
              public void run() {
                for (int i = 0;i < 100;i++) {
                  if (checkDisconnected()) {
                    testComplete();
                    return;
                  }
                  try {
                    Thread.sleep(10);
                  } catch (InterruptedException e) {
                    fail(e);
                  }
                }
              }
            }.start();
          }
        });
        conn.close();
      }
    });
    assertConnect();
    await();
  }

  @Test
  public void testScheduleThread() throws Exception {
    server(new Consumer<TtyConnection>() {
      @Override
      public void accept(TtyConnection conn) {
        final Thread connThread = Thread.currentThread();
        conn.execute(new Runnable() {
          @Override
          public void run() {
            Thread schedulerThread = Thread.currentThread();
            try {
              assertThreading(connThread, schedulerThread);
            } catch (Exception e) {
              fail(e);
            }
            testComplete();
          }
        });
      }
    });
    assertConnect();
    await();
  }

  protected void assertThreading(Thread connThread, Thread schedulerThread) throws Exception {
  }

  @Test
  public void testLastAccessedTime() throws Exception {
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicInteger count = new AtomicInteger();
    final long connTime = System.currentTimeMillis();
    server(new Consumer<TtyConnection>() {
      @Override
      public void accept(final TtyConnection conn) {
        assertTrue(conn.lastAccessedTime() >= connTime);
        final long openTime = System.currentTimeMillis();
        conn.setStdinHandler(new Consumer<int[]>() {
          @Override
          public void accept(int[] cp) {
            long delta = conn.lastAccessedTime() - openTime;
            switch (count.getAndIncrement()) {
              case 0:
                assertTrue(delta >= 0);
                latch.countDown();
                break;
              case 1:
                assertTrue(delta >= 10);
                testComplete();
                break;
            }
          }
        });
      }
    });
    assertConnect();
    Thread.sleep(15);
    assertWrite("hello");
    awaitLatch(latch);
    Thread.sleep(15);
    assertWrite("byebye");
    await();
  }

  @Test
  public void testDifferentCharset() throws Exception {
    charset = Charset.forName("ISO-8859-1");
    server(new Consumer<TtyConnection>() {
      @Override
      public void accept(TtyConnection conn) {
        // 20AC does not exists in ISO_8859_1 and is replaced by `?`
        conn.write("\u20AC");
      }
    });
    assertConnect();
    String s = assertReadString(1);
    assertEquals("?", s);
  }
}
