package io.termd.core.pty;

import io.termd.core.function.BiConsumer;
import io.termd.core.tty.TtyEvent;

/**
 * @author bw on 25/10/2016.
 */
public class EventHandler implements BiConsumer<TtyEvent, Integer> {
    private PtyMaster task;

    public EventHandler(PtyMaster task) {
        this.task = task;
    }

    @Override
    public void accept(TtyEvent event, Integer integer) {
        if (event == TtyEvent.INTR) {
            task.interruptProcess();
        }
    }
}
