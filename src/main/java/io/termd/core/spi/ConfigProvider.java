package io.termd.core.spi;

import java.util.regex.Pattern;

public interface ConfigProvider {

  public String get(String p, String defaultVal);

  public int getHistoryMaxSize();

  public Pattern getHistoryIgnorePattern();
}
