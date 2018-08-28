package io.termd.core.util;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author bw on 27/10/2016.
 */
public class CompletableFuture<T> {
    private boolean completed;
    private T value;
    private Throwable ex;

    public synchronized boolean complete(T value) {
        this.completed = true;
        this.value = value;
        notifyAll();
        return true;
    }

    public synchronized boolean completeExceptionally(Throwable ex) {
        this.completed = true;
        this.ex = ex;
        notifyAll();
        return true;
    }

    public synchronized T get() throws Throwable {
        while (!completed) {
            wait();
        }

        if (ex != null) {
            throw ex;
        } else {
            return value;
        }
    }

    public synchronized T get(long timeout, TimeUnit unit) throws Throwable {
        while (!completed) {
            wait(unit.toMicros(timeout));
        }

        if (!completed) {
            throw new TimeoutException();
        } else {
            if (ex != null) {
                throw ex;
            } else {
                return value;
            }
        }
    }
}
