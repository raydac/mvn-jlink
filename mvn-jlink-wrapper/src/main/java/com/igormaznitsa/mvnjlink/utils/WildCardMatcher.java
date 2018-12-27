package com.igormaznitsa.mvnjlink.utils;

import javax.annotation.Nonnull;
import java.util.regex.Pattern;

import static java.lang.Integer.toHexString;
import static java.util.Locale.ENGLISH;
import static java.util.regex.Pattern.compile;

public final class WildCardMatcher {

  private final Pattern pattern;
  private final String addressPattern;

  public WildCardMatcher(@Nonnull final String txt) {
    this.addressPattern = txt.trim();
    final StringBuilder builder = new StringBuilder();
    for (final char c : this.addressPattern.toCharArray()) {
      switch (c) {
        case '*': {
          builder.append(".*");
        }
        break;
        case '?': {
          builder.append('.');
        }
        break;
        default: {
          final String code = toHexString(c).toUpperCase(ENGLISH);
          builder.append("\\u").append("0000", 0, 4 - code.length()).append(code);
        }
        break;
      }
    }
    this.pattern = compile(builder.toString());
  }

  public boolean match(@Nonnull final String txt) {
    return this.pattern.matcher(txt).matches();
  }

  @Nonnull
  @Override
  public String toString() {
    return this.addressPattern;
  }
}
