/*
 * Copyright 2015 Julien Viet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.termd.core.util;

import io.termd.core.function.Supplier;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class Wait {

    public static void forCondition(Supplier<Boolean> evaluationSupplier, long timeout, TimeUnit timeUnit) throws InterruptedException, TimeoutException {
        forCondition(evaluationSupplier, timeout, timeUnit, "");
    }

    public static void forCondition(Supplier<Boolean> evaluationSupplier, long timeout, TimeUnit timeUnit, String failedMessage) throws InterruptedException, TimeoutException {
        long started = System.currentTimeMillis();
        while (true) {
            if (started + timeUnit.toMillis(timeout) < System.currentTimeMillis()) {
                throw new TimeoutException(failedMessage + " Reached timeout " + timeout + " " + timeUnit);
            }
            if (evaluationSupplier.get()) {
                break;
            } else {
                Thread.sleep(100);
            }
        }
    }


}
