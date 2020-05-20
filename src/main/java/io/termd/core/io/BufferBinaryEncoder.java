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

package io.termd.core.io;

import io.termd.core.function.Consumer;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.IntBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author gongdewei 2020/05/19
 */
public class BufferBinaryEncoder implements Consumer<IntBuffer> {

  private CharsetEncoder charsetEncoder;
  private volatile Charset charset;
  private final Consumer<ByteBuffer> onByte;
  private final char[] charsBuf = new char[2];
  private int capacity = 8192;
  private ByteBuffer cachedByteBuffer;
  private CharBuffer cachedCharBuffer;

  public BufferBinaryEncoder(Charset charset, Consumer<ByteBuffer> onByte) {
    this.setCharset(charset);
    this.onByte = onByte;
  }

  /**
   * Set a new charset on the encoder.
   *
   * @param charset the new charset
   */
  public void setCharset(Charset charset) {
    this.charset = charset;
    charsetEncoder = charset.newEncoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE);

    //check buffer
    ensureBuffer();
  }

  @Override
  public void accept(IntBuffer codePoints) {
    int[] array = codePoints.array();
    int offset = codePoints.position();
    int limit = codePoints.limit();

    int capacity = 0;
    for (int i = offset; i < limit; i++) {
      capacity += Character.charCount(array[i]);
    }

    //convert code points to chars
    CharBuffer charBuffer = getCharBuffer(capacity);
    for (int i = offset; i < limit; i++) {
      int size = Character.toChars(array[i], charsBuf, 0);
      charBuffer.put(charsBuf, 0, size);
    }
    charBuffer.flip();

    //encode chars to bytes
    ByteBuffer byteBuffer = getByteBuffer(getByteCapacity(capacity));
    charsetEncoder.encode(charBuffer, byteBuffer, true);
    byteBuffer.flip();

    onByte.accept(byteBuffer);
  }

  private CharBuffer getCharBuffer(int capacity) {
    CharBuffer charBuffer = null;
    if (capacity <= cachedCharBuffer.capacity()) {
      charBuffer = cachedCharBuffer;
      charBuffer.clear();
    } else {
      charBuffer = CharBuffer.allocate(capacity);
    }
    return charBuffer;
  }

  private ByteBuffer getByteBuffer(int capacity) {
    ByteBuffer byteBuffer = null;
    if (capacity <= cachedByteBuffer.capacity()) {
      byteBuffer = cachedByteBuffer;
      byteBuffer.clear();
    } else {
      byteBuffer = ByteBuffer.allocate(capacity);
    }
    return byteBuffer;
  }

  private void ensureBuffer() {
    if (cachedCharBuffer ==null || cachedCharBuffer.limit() != capacity) {
      cachedCharBuffer = CharBuffer.allocate(capacity);
    }
    int byteBufCapacity = getByteCapacity(capacity);
    if (cachedByteBuffer ==null || cachedByteBuffer.limit() != byteBufCapacity) {
      cachedByteBuffer = ByteBuffer.allocate(byteBufCapacity);
    }
  }

  private int getByteCapacity(int capacity) {
    return (int) (capacity *charsetEncoder.averageBytesPerChar());
  }
}
