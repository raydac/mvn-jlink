package com.igormaznitsa.mvnjlink.utils;

import com.igormaznitsa.meta.annotation.MustNotContainNull;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.compile;

public final class StringUtils {
  private static final Pattern PATTERN_MODULE_LINE = compile("^(.*)->(.*)$");
  private static final Pattern PATTERN_FILE_HASH = compile("([0-9a-fA-F]+)\\s+(.+)");
  private StringUtils() {
  }

  @Nonnull
  public static String extractFileHash(@Nonnull final String text) throws IOException {
    final Matcher hashMatcher = PATTERN_FILE_HASH.matcher(text.trim());
    if (hashMatcher.find()) {
      return hashMatcher.group(1);
    } else {
      throw new IOException("Can't extract file hash from '" + text + '\'');
    }
  }

  @Nonnull
  @MustNotContainNull
  public static List<String> extractJdepsModuleNames(@Nonnull final String text) {
    final List<String> result = new ArrayList<>();
    for (final String line : text.split("\\n")) {
      final Matcher lineMatcher = PATTERN_MODULE_LINE.matcher(line);
      if (lineMatcher.find()) {
        final String moduleName = lineMatcher.group(2).trim();
        if (!moduleName.contains(" ")) {
          result.add(moduleName);
        }
      }
    }
    return result;
  }
}
