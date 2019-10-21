package io.termd.core.spi.impl;

import java.util.regex.Pattern;
import io.termd.core.spi.ConfigProvider;

public final class ConfigProvider4default implements ConfigProvider {

  private final int historyMaxSize;
  private final Pattern historyIgnorePattern;

  public ConfigProvider4default() {
    super();
    historyMaxSize = Integer.getInteger("termd_max_history_size", 500);
    String tmp = get("termd_history_ignore_pattern", null);// "\\s*history"
    if (tmp == null || tmp.isEmpty()) {
      historyIgnorePattern = null;
    } else {
      historyIgnorePattern = Pattern.compile(tmp);
    }
  }

  @Override
  public String get(String p, String defaultVal) {
    return System.getProperty(p, defaultVal);
  }

  @Override
  public int getHistoryMaxSize() {
    return historyMaxSize;
  }

  @Override
  public Pattern getHistoryIgnorePattern() {
    return historyIgnorePattern;
  }
}
