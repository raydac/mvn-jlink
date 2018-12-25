package com.igormaznitsa.mvnjlink.utils;

import com.igormaznitsa.meta.annotation.MustNotContainNull;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StringUtils {
  private StringUtils() {
  }

  private static final Pattern MODULE_LINE = Pattern.compile("^(.*)->(.*)$");

  @Nonnull
  @MustNotContainNull
  public static List<String> extractModuleNames(@Nonnull final String text) {
    final List<String> result = new ArrayList<>();
    for (final String line : text.split("\\n")) {
      final Matcher lineMatcher = MODULE_LINE.matcher(line);
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
