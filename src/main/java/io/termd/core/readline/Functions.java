package io.termd.core.readline;

import io.termd.core.util.Helper;

import java.util.ArrayList;
import java.util.List;

/**
 * @author bw on 25/10/2016.
 */
public class Functions {
    /**
     * Load the defaults function via the {@link java.util.ServiceLoader} SPI.
     *
     * @return the loaded function
     */
    public static List<Function> loadDefaults() {
        List<Function> functions = new ArrayList<Function>();
        for (io.termd.core.readline.Function function : Helper.loadServices(Thread.currentThread().getContextClassLoader(), io.termd.core.readline.Function.class)) {
            functions.add(function);
        }
        return functions;
    }
}
