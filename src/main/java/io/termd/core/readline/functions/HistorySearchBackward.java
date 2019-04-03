package io.termd.core.readline.functions;

import java.util.List;

import io.termd.core.readline.Function;
import io.termd.core.readline.LineBuffer;
import io.termd.core.readline.Readline;
import io.termd.core.util.LineBufferUtils;

/**
 *
 * @author hengyunabc 2018-11-16
 *
 */
public class HistorySearchBackward implements Function {

    @Override
    public String name() {
        return "history-search-backward";
    }

    @Override
    public void apply(Readline.Interaction interaction) {
        LineBuffer buf = interaction.buffer().copy();
        int cursor = buf.getCursor();
        List<int[]> history = interaction.history();

        int curr = interaction.getHistoryIndex();

        boolean applyNext = false;
        // 当前光标内容为空，则直接找前一条历史记录
        if (buf.getSize() == 0) {
            applyNext = true;
        }
        // 当前光标在行尾，并且行的内容和当前的历史记录一致，说明刚翻到当前的记录，因此直接再向前翻
        if (cursor == buf.getSize() && curr >= 0 && LineBufferUtils.equals(buf, history.get(curr))) {
            int next = curr + 1;
            if (next < history.size()) {
                applyNext = true;
            }
        }

        if (applyNext) {
            // 向前翻一条记录，光标设置为行尾
            int next = curr + 1;
            if (next < history.size()) {
                int[] nextHistory = history.get(next);
                interaction.refresh(new LineBuffer().insert(nextHistory));
                interaction.setHistoryIndex(next);
            }
        } else {
            // 获取当前行首到光标的内容，在历史记录里查找匹配的。光标还是当前位置
            int searchStart = curr + 1;
            for (int i = searchStart; i < history.size(); ++i) {
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
        }

        interaction.resume();
    }
}
