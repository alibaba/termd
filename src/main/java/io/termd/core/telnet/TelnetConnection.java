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

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
* @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
* @author gongdewei 2020/05/20
*/
public abstract class TelnetConnection {

  public static final byte BYTE_IAC = (byte)  0xFF;
  public static final byte BYTE_DONT = (byte) 0xFE;
  public static final byte BYTE_DO = (byte)   0xFD;
  public static final byte BYTE_WONT = (byte) 0xFC;
  public static final byte BYTE_WILL = (byte) 0xFB;
  public static final byte BYTE_SB = (byte)   0xFA;
  public static final byte BYTE_SE = (byte)   0xF0;

  private byte[] pendingBuffer = new byte[256];
  private int pendingLength = 0;
  Status status;
  Byte paramsOptionCode;
  byte[] paramsBuffer;
  int paramsLength;
  boolean paramsIac;
  boolean sendBinary;
  boolean receiveBinary;
  final TelnetHandler handler;

  public TelnetConnection(TelnetHandler handler) {
    this.status = Status.DATA;
    this.paramsOptionCode = null;
    this.paramsBuffer = null;
    this.paramsIac = false;
    this.sendBinary = false;
    this.receiveBinary = false;
    this.handler = handler;
  }

  private void appendToParams(byte b) {
    while (paramsLength >= paramsBuffer.length) {
      paramsBuffer = Arrays.copyOf(paramsBuffer, paramsBuffer.length + 100);
    }
    paramsBuffer[paramsLength++] = b;
  }

  public void onInit() {
    handler.onOpen(this);
  }

  public abstract void close();

  /**
   * Write a <i>do</i> option request to the client.
   *
   * @param option the option to send
   */
  public final void writeDoOption(Option option) {
    send(new byte[]{BYTE_IAC, BYTE_DO, option.code});
  }

  /**
   * Write a do <i>will</i> request to the client.
   *
   * @param option the option to send
   */
  public final void writeWillOption(Option option) {
    send(new byte[]{BYTE_IAC, BYTE_WILL, option.code});
  }

  protected abstract void execute(Runnable task);

  protected abstract void schedule(Runnable task, long delay, TimeUnit unit);

  protected abstract void send(byte[] data, int offset, int len);

  protected void send(byte[] data) {
    this.send(data, 0, data.length);
  }

  public void receive(byte[] data) {
    for (byte b : data) {
      status.handle(this, b);
    }
    flushDataIfNecessary();
  }

  /**
   * Write data to the client, escaping data if necessary or truncating it. The original buffer can
   * be mutated if incorrect data is provided.
   *
   * @param data the data to write
   */
  public final void write(byte[] data) {
    this.write(data, 0, data.length);
  }

  /**
   * Write data to the client, escaping data if necessary or truncating it. The original buffer can
   * be mutated if incorrect data is provided.
   *
   * @param data the data to write
   * @param offset
   * @param len
   */
  public final void write(byte[] data, int offset, int len) {
    if (sendBinary) {
      // actually the logic here never get executed.
      int prev = 0;
      int end = offset+len;
      for (int i = offset;i < end;i++) {
        if (data[i] == -1) {
          send(data, prev, i - prev);
          send(new byte[]{-1, -1});
          prev = i + 1;
        }
      }
      send(data, prev, data.length - prev);
    } else {
      // Not fully understand the logic below, but
      // Chinese characters will be truncated by the following logic.
      // So currently these logic is commented out.
      // see middleware-container/arthas/issues/246 for more details
      //
//      for (int i = 0;i < data.length;i++) {
//        data[i] = (byte)(data[i] & 0x7F);
//      }
      send(data, offset, len);
    }
  }

  protected void onClose() {
    handler.onClose();
  }

  /**
   * Handle option <code>WILL</code> call back. The implementation will try to find a matching option
   * via the {@code Option#values()} and invoke it's {@link Option#handleWill(TelnetConnection)} method
   * otherwise a <code>DON'T</code> will be sent to the client.<p>
   *
   * This method can be subclassed to handle an option.
   *
   * @param optionCode the option code
   */
  protected void onOptionWill(byte optionCode) {
    for (Option option : Option.values()) {
      if (option.code == optionCode) {
        option.handleWill(this);
        return;
      }
    }
    send(new byte[]{BYTE_IAC, BYTE_DONT, optionCode});
  }

  /**
   * Handle option <code>WON'T</code> call back. The implementation will try to find a matching option
   * via the {@code Option#values()} and invoke it's {@link Option#handleWont(TelnetConnection)} method.<p>
   *
   * This method can be subclassed to handle an option.
   *
   * @param optionCode the option code
   */
  protected void onOptionWont(byte optionCode) {
    for (Option option : Option.values()) {
      if (option.code == optionCode) {
        option.handleWont(this);
        return;
      }
    }
  }

  /**
   * Handle option <code>DO</code> call back. The implementation will try to find a matching option
   * via the {@code Option#values()} and invoke it's {@link Option#handleDo(TelnetConnection)} method
   * otherwise a <code>WON'T</code> will be sent to the client.<p>
   *
   * This method can be subclassed to handle an option.
   *
   * @param optionCode the option code
   */
  protected void onOptionDo(byte optionCode) {
    for (Option option : Option.values()) {
      if (option.code == optionCode) {
        option.handleDo(this);
        return;
      }
    }
    send(new byte[]{BYTE_IAC, BYTE_WONT, optionCode});
  }

  /**
   * Handle option <code>DON'T</code> call back. The implementation will try to find a matching option
   * via the {@code Option#values()} and invoke it's {@link Option#handleDont(TelnetConnection)} method.<p>
   *
   * This method can be subclassed to handle an option.
   *
   * @param optionCode the option code
   */
  protected void onOptionDont(byte optionCode) {
    for (Option option : Option.values()) {
      if (option.code == optionCode) {
        option.handleDont(this);
        return;
      }
    }
  }

  /**
   * Handle option parameters call back. The implementation will try to find a matching option
   * via the {@code Option#values()} and invoke it's {@link Option#handleParameters(TelnetConnection, byte[])} method.
   *
   * This method can be subclassed to handle an option.
   *
   * @param optionCode the option code
   */
  protected void onOptionParameters(byte optionCode, byte[] parameters) {
    for (Option option : Option.values()) {
      if (option.code == optionCode) {
        option.handleParameters(this, parameters);
        return;
      }
    }
  }

  /**
   * Append a byte in the {@link #pendingBuffer} buffer. When the {@link #pendingBuffer} buffer is full, data
   * is flushed.
   *
   * @param b the byte
   * @see #flushData()
   */
  private void appendData(byte b) {
    if (pendingLength >= pendingBuffer.length) {
      flushData();
    }
    pendingBuffer[pendingLength++] = b;
  }

  /**
   * Flush the {@link #pendingBuffer} buffer when it is not empty.
   *
   * @see #flushData()
   */
  private void flushDataIfNecessary() {
    if (pendingLength > 0) {
      flushData();
    }
  }

  /**
   * Flush the {@link #pendingBuffer} buffer to {@link TelnetHandler#onData(byte[])}.
   */
  private void flushData() {
    byte[] data = Arrays.copyOf(pendingBuffer, pendingLength);
    pendingLength = 0;
    handler.onData(data);
  }

  enum Status {

    DATA() {
      @Override
      void handle(TelnetConnection session, byte b) {
        if (b == BYTE_IAC) {
          if (session.receiveBinary) {
            session.status = ESC;
          } else {
            session.flushDataIfNecessary();
            session.status = IAC;
          }
        } else {
          session.appendData(b);
        }
      }
    },

    ESC() {
      @Override
      void handle(TelnetConnection session, byte b) {
        if (b == BYTE_IAC) {
          session.appendData((byte)-1);
        } else {
          session.flushDataIfNecessary();
          IAC.handle(session, b);
        }
      }
    },

    IAC() {
      @Override
      void handle(TelnetConnection session, byte b) {
        if (b == BYTE_DO) {
          session.status = DO;
        } else if (b == BYTE_DONT) {
          session.status = DONT;
        } else if (b == BYTE_WILL) {
          session.status = WILL;
        } else if (b == BYTE_WONT) {
          session.status = WONT;
        } else if (b == BYTE_SB) {
          session.paramsBuffer = new byte[100];
          session.paramsLength = 0;
          session.status = SB;
        } else {
          session.handler.onCommand(b);
          session.status = DATA;
        }
      }
    },

    SB() {
      @Override
      void handle(TelnetConnection session, byte b) {
        if (session.paramsOptionCode == null) {
          session.paramsOptionCode = b;
        } else {
          if (session.paramsIac) {
            session.paramsIac = false;
            if (b == BYTE_SE) {
              try {
                session.onOptionParameters(session.paramsOptionCode, Arrays.copyOf(session.paramsBuffer, session.paramsLength));
              } finally {
                session.paramsOptionCode = null;
                session.paramsBuffer = null;
                session.status = DATA;
              }
            } else if (b == BYTE_IAC) {
              session.appendToParams((byte) -1);
            }
          } else {
            if (b == BYTE_IAC) {
              session.paramsIac = true;
            } else {
              session.appendToParams(b);
            }
          }
        }
      }
    },

    DO() {
      @Override
      void handle(TelnetConnection session, byte b) {
        try {
          session.onOptionDo(b);
        } finally {
          session.status = DATA;
        }
      }
    },

    DONT() {
      @Override
      void handle(TelnetConnection session, byte b) {
        try {
          session.onOptionDont(b);
        } finally {
          session.status = DATA;
        }
      }
    },

    WILL() {
      @Override
      void handle(TelnetConnection session, byte b) {
        try {
          session.onOptionWill(b);
        } finally {
          session.status = DATA;
        }
      }
    },

    WONT() {
      @Override
      void handle(TelnetConnection session, byte b) {
        try {
          session.onOptionWont(b);
        } finally {
          session.status = DATA;
        }
      }
    },

    ;

    abstract void handle(TelnetConnection session, byte b);
  }
}
