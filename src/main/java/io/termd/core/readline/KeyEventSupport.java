package io.termd.core.readline;

import java.nio.IntBuffer;

/**
 * @author bw on 26/10/2016.
 */
public abstract class KeyEventSupport implements KeyEvent {
    @Override
    public IntBuffer buffer() {
        int length = length();
        IntBuffer buf = IntBuffer.allocate(length);
        for (int i = 0; i < length; i++) {
            buf.put(getCodePointAt(i));
        }
        buf.flip();
        return buf;
    }
}
