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

package io.termd.core.telnet;

import io.termd.core.function.BiConsumer;
import io.termd.core.function.Consumer;
import io.termd.core.io.BufferBinaryEncoder;
import io.termd.core.tty.*;
import io.termd.core.util.Helper;
import io.termd.core.util.Vector;
import io.termd.core.io.BinaryDecoder;
import io.termd.core.io.TelnetCharset;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.Charset;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * A telnet handler that implements {@link io.termd.core.tty.TtyConnection}.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author gongdewei 2020/05/20
 */
public final class TelnetTtyConnection extends TelnetHandler implements TtyConnection {

  private static final Charset US_ASCII = Charset.forName("US-ASCII");

  private final boolean inBinary;
  private final boolean outBinary;
  private boolean receivingBinary;
  private boolean sendingBinary;
  private boolean accepted;
  private Vector size;
  private String terminalType;
  private Consumer<Vector> sizeHandler;
  private Consumer<String> termHandler;
  private Consumer<Void> closeHandler;
  protected TelnetConnection conn;
  private final Charset charset;
  private final TtyEventDecoder eventDecoder = new TtyEventDecoder(3, 26, 4);
  private final ReadBuffer readBuffer = new ReadBuffer(new Executor() {
    @Override
    public void execute(Runnable command) {
      TelnetTtyConnection.this.execute(command);
    }
  });

  private final BinaryDecoder decoder;
  private final BufferBinaryEncoder encoder;

  private final Consumer<IntBuffer> stdout;
  private final Consumer<int[]> stdoutWrapper;
  private final Consumer<TtyConnection> handler;
  private final IntBuffer codePointBuf = IntBuffer.allocate(8192);
  private long lastAccessedTime = System.currentTimeMillis();

  public TelnetTtyConnection(boolean inBinary, boolean outBinary, Charset charset, Consumer<TtyConnection> handler) {
    this.charset = charset;
    this.inBinary = inBinary;
    this.outBinary = outBinary;
    this.handler = handler;
    this.size = new Vector();
    this.decoder = new BinaryDecoder(512, TelnetCharset.INSTANCE, readBuffer);
    this.encoder = new BufferBinaryEncoder(charset, new Consumer<ByteBuffer>() {
      @Override
      public void accept(ByteBuffer data) {
        conn.write(data.array(), data.position(), data.remaining());
      }
    });
    this.stdout = new BufferTtyOutputMode(encoder);
    this.stdoutWrapper = new Consumer<int[]>() {
      @Override
      public void accept(int[] data) {
        stdout.accept(IntBuffer.wrap(data));
      }
    };
  }

  @Override
  public long lastAccessedTime() {
    return lastAccessedTime;
  }

  @Override
  public String terminalType() {
    return terminalType;
  }

  @Override
  public void execute(Runnable task) {
    conn.execute(task);
  }

  @Override
  public void schedule(Runnable task, long delay, TimeUnit unit) {
    conn.schedule(task, delay, unit);
  }

  @Override
  public Charset inputCharset() {
    return inBinary ? charset : US_ASCII;
  }

  @Override
  public Charset outputCharset() {
    return outBinary ? charset : US_ASCII;
  }

  @Override
  protected void onSendBinary(boolean binary) {
    sendingBinary = binary;
    if (binary) {
      encoder.setCharset(charset);
    }
    checkAccept();
  }

  @Override
  protected void onReceiveBinary(boolean binary) {
    receivingBinary = binary;
    if (binary) {
      decoder.setCharset(charset);
    }
    checkAccept();
  }

  @Override
  protected void onData(byte[] data) {
    lastAccessedTime = System.currentTimeMillis();
    decoder.write(data);
  }

  @Override
  protected void onOpen(TelnetConnection conn) {
    this.conn = conn;

    // Kludge mode
    conn.writeWillOption(Option.ECHO);
    conn.writeWillOption(Option.SGA);

    //
    if (inBinary) {
      conn.writeDoOption(Option.BINARY);
    }
    if (outBinary) {
      conn.writeWillOption(Option.BINARY);
    }

    // Window size
    conn.writeDoOption(Option.NAWS);

    // Get some info about user
    conn.writeDoOption(Option.TERMINAL_TYPE);

    //
    checkAccept();
  }

  private void checkAccept() {
    if (!accepted) {
      if (!outBinary | (outBinary && sendingBinary)) {
        if (!inBinary | (inBinary && receivingBinary)) {
          accepted = true;
          readBuffer.setReadHandler(eventDecoder);
          handler.accept(this);
        }
      }
    }
  }

  @Override
  protected void onTerminalType(String terminalType) {
    this.terminalType = terminalType;
    if (termHandler != null) {
      termHandler.accept(terminalType);
    }
  }

  @Override
  public Vector size() {
    return size;
  }

  @Override
  protected void onSize(int width, int height) {
    this.size = new Vector(width, height);
    if (sizeHandler != null) {
      sizeHandler.accept(size);
    }
  }

  @Override
  public Consumer<Vector> getSizeHandler() {
    return sizeHandler;
  }

  @Override
  public void setSizeHandler(Consumer<Vector> handler) {
    this.sizeHandler = handler;
  }

  @Override
  public Consumer<String> getTerminalTypeHandler() {
    return termHandler;
  }

  @Override
  public void setTerminalTypeHandler(Consumer<String> handler) {
    termHandler = handler;
    if (handler != null && terminalType != null) {
      handler.accept(terminalType);
    }
  }

  @Override
  public BiConsumer<TtyEvent, Integer> getEventHandler() {
    return eventDecoder.getEventHandler();
  }

  @Override
  public void setEventHandler(BiConsumer<TtyEvent, Integer> handler) {
    eventDecoder.setEventHandler(handler);
  }

  @Override
  public Consumer<int[]> getStdinHandler() {
    return eventDecoder.getReadHandler();
  }

  @Override
  public void setStdinHandler(Consumer<int[]> handler) {
    eventDecoder.setReadHandler(handler);
  }

  @Override
  public Consumer<int[]> stdoutHandler() {
    //TODO replace with Consumer<IntBuffer>
    return stdoutWrapper;
  }

  @Override
  public void setCloseHandler(Consumer<Void> closeHandler) {
    this.closeHandler = closeHandler;
  }

  @Override
  public Consumer<Void> getCloseHandler() {
    return closeHandler;
  }

  @Override
  protected void onClose() {
    if (closeHandler != null) {
      closeHandler.accept(null);
    }
  }

  @Override
  public void close() {
    conn.close();
  }

  @Override
  public void close(int exit) {
    close();
  }

  @Override
  public TtyConnection write(String s) {
    synchronized (this) {
      int count = Helper.codePointCount(s);
      IntBuffer buffer = null;
      if (count < codePointBuf.capacity()) {
        buffer = codePointBuf;
      } else {
        buffer = IntBuffer.allocate(count);
      }

      buffer.clear();
      Helper.toCodePoints(s, buffer);
      buffer.flip();

      stdout.accept(buffer);
      return this;
    }
  }
}
