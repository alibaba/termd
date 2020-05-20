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


import io.termd.core.function.Consumer;

import java.nio.IntBuffer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author gongdewei 2020/05/19
 */
public class BufferTtyOutputMode implements Consumer<IntBuffer> {

  private static final IntBuffer CRLF = IntBuffer.wrap(new int[]{'\r', '\n'});
  private final Consumer<IntBuffer> readHandler;

  public BufferTtyOutputMode(Consumer<IntBuffer> readHandler) {
    this.readHandler = readHandler;
  }

  @Override
  public void accept(IntBuffer data) {
    synchronized (this){
      if (readHandler != null && data.remaining() > 0) {
        int[] array = data.array();
        int offset = data.position();
        int limit = data.limit();
        int prev = offset;
        int ptr = offset;
        while (ptr < limit) {
          // Simple implementation that works only on system that uses /n as line terminator
          // equivalent to 'stty onlcr'
          int cp = array[ptr];
          if (cp == '\n') {
            if (ptr > prev) {
              sendChunk(data, prev, ptr);
            }
            //reset crlf read index
            CRLF.position(0);
            readHandler.accept(CRLF);
            prev = ++ptr;
          } else {
            ptr++;
          }
        }
        if (ptr > prev) {
          sendChunk(data, prev, ptr);
        }
      }
    }
  }

  private void sendChunk(IntBuffer data, int prev, int ptr) {
    //hotspot, reduce memory fragment
    //change limit first, then position
    data.limit(ptr);
    data.position(prev);
    readHandler.accept(data);
  }
}
