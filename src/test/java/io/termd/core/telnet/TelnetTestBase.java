package io.termd.core.telnet;

import io.termd.core.TestBase;
import io.termd.core.function.Function;
import io.termd.core.function.Supplier;
import org.junit.Rule;

import java.io.Closeable;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class TelnetTestBase extends TestBase {

  @Rule
  public TelnetServerRule server = new TelnetServerRule(serverFactory());

  @Rule
  public TelnetClientRule client = new TelnetClientRule();
  
  protected abstract Function<Supplier<TelnetHandler>, Closeable> serverFactory();

}
