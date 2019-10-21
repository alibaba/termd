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

package io.termd.core.util;

import io.termd.core.function.Consumer;
import io.termd.core.function.IntConsumer;
import io.termd.core.spi.ConfigProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Various utils.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Helper {

  public static void uncheckedThrow(Throwable throwable) {
    Helper.<RuntimeException>throwIt(throwable);
  }

  private static <T extends Throwable> void throwIt(Throwable throwable) throws T {
    throw (T)throwable;
  }

  /**
   * Do absolutely nothing. This can be useful for code coverage analysis.
   */
  public static void noop() {}

  /**
   * Convert the string to an array of code points.
   *
   * @param s the string to convert
   * @return the code points
   */
  public static int[] toCodePoints(String s) {
    List<Integer> codePoints = new ArrayList<Integer>();
    for (int offset = 0; offset < s.length();) {
      int cp = s.codePointAt(offset);
      codePoints.add(cp);
      offset += Character.charCount(cp);
    }
    return convert(codePoints);
  }

  /**
   * Code point to string conversion.
   *
   * @param codePoints the code points
   * @return the corresponding string
   */
  public static String fromCodePoints(int[] codePoints) {
    return new String(codePoints, 0, codePoints.length);
  }

  public static void appendCodePoints(int[] codePoints, final StringBuilder sb) {
    consumeTo(codePoints, new IntConsumer() {
      @Override
      public void accept(int cp) {
        sb.appendCodePoint(cp);
      }
    });
  }

  public static void consumeTo(int[] i, IntConsumer consumer) {
    for (int codePoint : i) {
      consumer.accept(codePoint);
    }
  }

  public static <S> List<S> loadServices(ClassLoader loader, Class<S> serviceClass) {
    ArrayList<S> services = new ArrayList<S>();
    Iterator<S> i = ServiceLoader.load(serviceClass, loader).iterator();
    while (i.hasNext()) {
      try {
        S service = i.next();
        services.add(service);
      } catch (Exception ignore) {
        // Log me
      }
    }
    return services;
  }

  public static List<Integer> list(int... list) {
    ArrayList<Integer> result = new ArrayList<Integer>(list.length);
    for (int i : list) {
      result.add(i);
    }
    return result;
  }

  public static List<String> split(String s, char c) {
    List<String> ret = new ArrayList<String>();
    int prev = 0;
    while (true) {
      int pos = s.indexOf('\n', prev);
      if (pos == -1) {
        break;
      }
      ret.add(s.substring(prev, pos));
      prev = pos + 1;
    }
    ret.add(s.substring(prev));
    return ret;
  }

  /**
   * Escape a string to be printable in a terminal: any non printable char is replaced by its
   * octal escape and the {@code \} char is replaced by the @{code \\} sequence.
   *
   * @param s the string to escape
   * @return the escaped string
   */
  public static String escape(String s) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0;i < s.length();i++) {
      char c = s.charAt(i);
      if (c == 0) {
        sb.append("\\0");
      }  else if (c < 32) {
        sb.append("\\");
        String octal = Integer.toOctalString(c);
        for (int j = octal.length();j < 3;j++) {
          sb.append('0');
        }
        sb.append(octal);
      } else if (c == '\\') {
        sb.append("\\\\");
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  public static int[] findLongestCommonPrefix(List<int[]> entries) {
    if (entries.isEmpty()) {
      return new int[0];
    }
    int minLen = min(entries);
    int len = 0;
    out:
    while (len < minLen) {
      for (int j = 1;j < entries.size();j++) {
        if (entries.get(j)[len] != entries.get(j - 1)[len]) {
          break out;
        }
      }
      len++;
    }
    return Arrays.copyOf(entries.get(0), len);
  }


  public static int[] computeBlock(Vector size, List<int[]> completions) {
    if (completions.size() == 0) {
      return new int[0];
    }
    int max = max(completions);
    int row = size.x() / (max + 1);
    int count = 0;
    StringBuilder sb = new StringBuilder();
    for (int[] completion : completions) {
      Helper.appendCodePoints(completion, sb);
      for (int i = completion.length;i < max;i++) {
        sb.append(' ');
      }
      count++;
      if (count < row) {
        sb.append(' ');
      } else {
        sb.append('\n');
        count = 0;
      }
    }
    sb.append("\n");
    return Helper.toCodePoints(sb.toString());
  }

  /**
   * Compute the position of the char at the specified {@literal offset} of the {@literal codePoints} given a
   * {@literal width} and a relative {@literal origin} position.
   *
   * @param origin the relative position to start from
   * @param width the screen width
   * @return the height
   */
  public static Vector computePosition(int[] codePoints, Vector origin, int offset, int width) {
    if (offset < 0) {
      throw new IndexOutOfBoundsException("Offset cannot be negative");
    }
    if (offset > codePoints.length) {
      throw new IndexOutOfBoundsException("Offset cannot bebe greater than the length");
    }
    int col = origin.x();
    int row = origin.y();
    for (int i = 0;i < offset;i++) {
      int cp = codePoints[i];
      int w = Wcwidth.of(cp);
      if (w == -1) {
        if (cp == '\r') {
          col = 0;
        } else if (cp == '\n') {
          col = 0;
          row++;
        }
      } else {
        if (col + w > width) {
          if (w > width) {
            throw new UnsupportedOperationException("Handle this case gracefully");
          }
          col = 0;
          row++;
        }
        col += w;
        if (col >= width) {
          col -= width;
          row++;
        }
      }
    }
    return new Vector(col, row);
  }

  public static Consumer<Throwable> startedHandler(final CompletableFuture<?> fut) {
    return new Consumer<Throwable>() {
      @Override
      public void accept(Throwable err) {
        if (err == null) {
          fut.complete(null);
        } else {
          fut.completeExceptionally(err);
        }
      }
    };
  }

  public static Consumer<Throwable> stoppedHandler(final CompletableFuture<?> fut) {
    return new Consumer<Throwable>() {
      @Override
      public void accept(Throwable err) {
        fut.complete(null);
      }
    };
  }

  public static int[] convert(List<Integer> ints) {
    int[] result = new int[ints.size()];
    for (int index = 0; index < ints.size(); index++) {
      result[index] = ints.get(index);
    }
    return result;
  }


  private static int min(List<int[]> entries) {
    int minLen = entries.get(0).length;
    for (int i = 1; i < entries.size(); i++) {
      int len = entries.get(i).length;
      if (minLen > len) {
        minLen = len;
      }
    }
    return minLen;
  }

  private static int max(List<int[]> entries) {
    int maxLen = entries.get(0).length;
    for (int i = 1; i < entries.size(); i++) {
      int len = entries.get(i).length;
      if (maxLen < len) {
        maxLen = len;
      }
    }
    return maxLen;
  }
  private static final class Hold4ConfigSPI {
    static final ConfigProvider INST = loadService(ConfigProvider.class);
  }

  public static ConfigProvider getConfigProvider() {
    return Hold4ConfigSPI.INST;
  }
  public static String  getConfig(String p , String defaultValue) {
    return Hold4ConfigSPI.INST.get(p, defaultValue);
  }

  public  static int  getConfigInt(String p , int defaultValue) {
       String tmp = Hold4ConfigSPI.INST.get(p, null);
       if (tmp == null || tmp.isEmpty()) {
           return defaultValue;
       }
       return Integer.parseInt(tmp);
  }
  
  protected  static <T> T loadService(final Class<T> spiClass) {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    if(classLoader == null ) {
      classLoader = Helper.class.getClassLoader();
    }
    Iterator<T> iter = ServiceLoader.load(spiClass, classLoader).iterator();
    if (!iter.hasNext() && (classLoader != Helper.class.getClassLoader())) {
      iter = ServiceLoader.load(spiClass, Helper.class.getClassLoader()).iterator();
    }
    if (iter.hasNext()) {
      final T o = iter.next();
      Logging.READLINE.info("SPI {}={}", spiClass , o);
      return o ;
    }
    throw new IllegalStateException("SPI not found!"+spiClass );
  }
}
