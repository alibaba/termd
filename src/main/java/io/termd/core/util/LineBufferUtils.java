package io.termd.core.util;

import io.termd.core.readline.LineBuffer;

/**
 *
 * @author hengyunabc 2018-11-17
 *
 */
public class LineBufferUtils {
    public static boolean matchBeforeCursor(LineBuffer buf, int[] line) {
        if (line == null) {
            return false;
        }

        int cursor = buf.getCursor();

        if (line.length < cursor) {
            return false;
        }

        for (int i = 0; i < cursor; ++i) {
            if (buf.getAt(i) != line[i]) {
                return false;
            }
        }

        return true;
    }

    public static boolean equals(LineBuffer buf, int[] line) {
        if (line == null) {
            return false;
        }

        int bufSize = buf.getSize();
        if (bufSize != line.length) {
            return false;
        }
        for (int i = 0; i < bufSize; ++i) {
            if (buf.getAt(i) != line[i]) {
                return false;
            }
        }

        return true;
    }
}
