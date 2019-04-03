
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

        int currentHistoryIndex = interaction.getHistoryIndex();

        if (currentHistoryIndex == 0 && LineBufferUtils.equals(buf, history.get(currentHistoryIndex))) {
            // 当前索引为0，说明上一个输入是一个空行里输入一个UP。所以重新设置为空行
            interaction.refresh(new LineBuffer());
            interaction.setHistoryIndex(-1);
        } else if (currentHistoryIndex > 0 && LineBufferUtils.equals(buf, history.get(currentHistoryIndex))
                        && cursor == buf.getSize()) {
            // 当前索引大于0，并且当前行的内容和当前history index一致，说明是输入多个UP翻页得到的。所以直接返回下一条history，光标设置为行尾。
            interaction.refresh(new LineBuffer().insert(history.get(currentHistoryIndex - 1)));
            interaction.setHistoryIndex(currentHistoryIndex - 1);
        } else {
            // 找到当前光标内容 和 历史记录 里匹配的项，光标仍然设置为当前的位置
            int searchStart = currentHistoryIndex - 1;
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
        }

        interaction.resume();
    }

}
