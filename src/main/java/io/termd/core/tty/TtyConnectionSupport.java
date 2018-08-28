package io.termd.core.tty;

import io.termd.core.util.Helper;

/**
 * @author bw on 25/10/2016.
 */
public abstract class TtyConnectionSupport implements TtyConnection {
    @Override
    public void close(int exit) {
        close();
    }

    /**
     * Write a string to the client.
     *
     * @param s the string to write
     */
    @Override
    public TtyConnection write(String s) {
        int[] codePoints = Helper.toCodePoints(s);
        stdoutHandler().accept(codePoints);
        return this;
    }
}
