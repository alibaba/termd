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

package io.termd.core.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.termd.core.function.BiConsumer;
import io.termd.core.function.Consumer;
import io.termd.core.io.BinaryDecoder;
import io.termd.core.io.BinaryEncoder;
import io.termd.core.tty.TtyConnectionSupport;
import io.termd.core.tty.TtyEvent;
import io.termd.core.tty.TtyEventDecoder;
import io.termd.core.tty.TtyOutputMode;
import io.termd.core.util.Vector;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;

/**
 * A connection to an http client, independant of the protocol, it could be straight Websockets or
 * SockJS, etc...
 *
 * The incoming protocol is based on json messages:
 *
 * {
 *   "action": "read",
 *   "data": "what the user typed"
 * }
 *
 * or
 *
 * {
 *   "action": "resize",
 *   "cols": 30,
 *   "rows: 50
 * }
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public abstract class HttpTtyConnection extends TtyConnectionSupport {

  public static final Vector DEFAULT_SIZE = new Vector(80, 24);

  private Charset charset;
  private Vector size;
  private Consumer<Vector> sizeHandler;
  private final TtyEventDecoder eventDecoder;
  private final BinaryDecoder decoder;
  private final Consumer<int[]> stdout;
  private Consumer<Void> closeHandler;
  private Consumer<String> termHandler;
  private long lastAccessedTime = System.currentTimeMillis();

  public HttpTtyConnection() {
    this(Charset.forName("UTF-8"), DEFAULT_SIZE);
  }

  public HttpTtyConnection(Charset charset, Vector size) {
    this.charset = charset;
    this.size = size;
    this.eventDecoder = new TtyEventDecoder(3, 26, 4);
    this.decoder = new BinaryDecoder(512, charset, eventDecoder);
    this.stdout = new TtyOutputMode(new BinaryEncoder(charset, new Consumer<byte[]>() {
      @Override
      public void accept(byte[] bytes) {
        write(bytes);
      }
    }));
  }

  @Override
  public Charset outputCharset() {
    return charset;
  }

  @Override
  public Charset inputCharset() {
    return charset;
  }

  @Override
  public long lastAccessedTime() {
    return lastAccessedTime;
  }

  @Override
  public String terminalType() {
    return "vt100";
  }

  protected abstract void write(byte[] buffer);

  /**
   * Special case to handle tty events.
   *
   * @param bytes
   */
  public void writeToDecoder(byte[] bytes) {
    lastAccessedTime = System.currentTimeMillis();
    decoder.write(bytes);
  }

  public void writeToDecoder(String msg) {
    ObjectMapper mapper = new ObjectMapper();
    Map<String, Object> obj;
    String action;
    try {
      obj = mapper.readValue(msg, Map.class);
      action = (String) obj.get("action");
    } catch (IOException e) {
      // Log this
      return;
    }
    if ("read".equals(action)) {
      lastAccessedTime = System.currentTimeMillis();
      String data = (String) obj.get("data");
      decoder.write(data.getBytes()); //write back echo
    } else if ("resize".equals(action)) {
      try {
        int cols = (Integer) getOrDefault(obj, "cols", size.x());
        int rows = (Integer) getOrDefault(obj, "rows", size.y());
        if (cols > 0 && rows > 0) {
          Vector newSize = new Vector(cols, rows);
          if (!newSize.equals(size())) {
            size = newSize;
            if (sizeHandler != null) {
              sizeHandler.accept(size);
            }
          }
        }
      } catch (Exception e) {
        // Invalid size
        // Log this
      }
    }
  }

  private static Object getOrDefault(Map<String, Object> map, String key, Object defaultValue) {
    return (map.containsKey(key)) ? map.get(key) : defaultValue;
  }

  public Consumer<String> getTerminalTypeHandler() {
    return termHandler;
  }

  public void setTerminalTypeHandler(Consumer<String> handler) {
    termHandler = handler;
  }

  @Override
  public Vector size() {
    return size;
  }

  public Consumer<Vector> getSizeHandler() {
    return sizeHandler;
  }

  public void setSizeHandler(Consumer<Vector> handler) {
    this.sizeHandler = handler;
  }

  @Override
  public BiConsumer<TtyEvent, Integer> getEventHandler() {
    return eventDecoder.getEventHandler();
  }

  @Override
  public void setEventHandler(BiConsumer<TtyEvent, Integer> handler) {
    eventDecoder.setEventHandler(handler);
  }

  public Consumer<int[]> getStdinHandler() {
    return eventDecoder.getReadHandler();
  }

  public void setStdinHandler(Consumer<int[]> handler) {
    eventDecoder.setReadHandler(handler);
  }

  public Consumer<int[]> stdoutHandler() {
    return stdout;
  }

  @Override
  public void setCloseHandler(Consumer<Void> closeHandler) {
    this.closeHandler = closeHandler;
  }

  @Override
  public Consumer<Void> getCloseHandler() {
    return closeHandler;
  }
}
