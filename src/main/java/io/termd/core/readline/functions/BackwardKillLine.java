package io.termd.core.readline.functions;

import io.termd.core.readline.Function;
import io.termd.core.readline.LineBuffer;
import io.termd.core.readline.Readline;

/**
 * @author wangtao 2016-12-15 15:12.
 */
public class BackwardKillLine implements Function {

    @Override
    public String name() {
        return "backward-kill-line";
    }

    @Override
    public void apply(Readline.Interaction interaction) {
        LineBuffer buf = interaction.buffer().copy();
        buf.delete(-buf.getCursor());
        interaction.refresh(buf);
        interaction.resume();
    }
}
