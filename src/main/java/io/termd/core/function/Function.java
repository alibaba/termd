package io.termd.core.function;

/**
 * @author bw on 27/10/2016.
 */
public interface Function<T, R> {
    R apply(T t);
}
