
package io.termd.core.readline.functions;

import io.termd.core.readline.Function;
import io.termd.core.readline.LineBuffer;
import io.termd.core.readline.Readline;
import io.termd.core.util.LineBufferUtils;

import java.util.List;

/**
 *
 * @author hengyunabc 2018-11-17
 *
 */
public class HistorySearchForward implements Function {

    @Override
    public String name() {
        return "history-search-forward";
    }

    @Override
    public void apply(Readline.Interaction interaction) {
        LineBuffer buf = interaction.buffer().copy();
        int cursor = buf.getCursor();

        List<int[]> history = interaction.history();

        int curr = interaction.getHistoryIndex();

        int searchStart = curr - 1;

        for (int i = searchStart; i >= 0; --i) {
            int[] line = history.get(i);

            if (LineBufferUtils.equals(buf, line)) {
                continue;
            }

            if (LineBufferUtils.matchBeforeCursor(buf, line)) {
                interaction.refresh(new LineBuffer().insert(line).setCursor(cursor));
                interaction.setHistoryIndex(i);
                break;
            }
        }

        interaction.resume();
    }

}
