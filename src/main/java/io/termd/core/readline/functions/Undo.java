package io.termd.core.readline.functions;

import io.termd.core.readline.Function;
import io.termd.core.readline.LineBuffer;
import io.termd.core.readline.Readline;

/**
 * @author bw on 02/11/2016.
 */
public class Undo implements Function {
    @Override
    public String name() {
        return "undo";
    }

    @Override
    public void apply(Readline.Interaction interaction) {
        LineBuffer buf = interaction.buffer().copy();
        buf.setSize(0);
        interaction.refresh(buf);
        interaction.resume();
    }
}
