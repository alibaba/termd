package io.termd.core.function;

/**
 * @author bw on 25/10/2016.
 */
public interface BiConsumer<T, U> {
    void accept(T t, U u);
}
