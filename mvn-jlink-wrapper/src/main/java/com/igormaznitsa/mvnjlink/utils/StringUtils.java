/*
 * Copyright 2019 Igor Maznitsa.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.igormaznitsa.mvnjlink.utils;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.regex.Pattern.compile;

import com.igormaznitsa.meta.annotation.MustNotContainNull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;

public final class StringUtils {
  private static final Pattern PATTERN_MODULE_LINE = compile("^(.*)->(.*)$");
  private static final Pattern PATTERN_FILE_HASH = compile("([0-9a-fA-F]+)\\s+(.+)");

  private StringUtils() {
  }

  @Nonnull
  public static String escapeFileName(@Nonnull final String text) {
    final StringBuilder result = new StringBuilder(text.length());
    for (final char c : text.toCharArray()) {
      switch (c) {
        case '*':
          result.append('#');
          break;
        case '?':
          result.append('_');
          break;
        case '\\':
        case '/':
        case ':':
        case '\"':
        case '<':
        case '>':
        case '|':
          result.append('.');
          break;
        default: {
          if (!(Character.isWhitespace(c) || Character.isISOControl(c))) {
            result.append(c);
          }
        }
        break;
      }
    }
    return result.toString();
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

  public static int printTextProgress(@Nonnull final String text, final long value,
                                      final long maxValue, final int progressBarWidth,
                                      final int lastValue) {
    final StringBuilder builder = new StringBuilder();
    builder.append("\r\u001B[?25l");
    builder.append(text);
    builder.append("[");

    final int progress = max(0, min(progressBarWidth,
        (int) Math.round(progressBarWidth * ((double) value / (double) maxValue))));

    for (int i = 0; i < progress; i++) {
      builder.append('▒');
    }
    for (int i = progress; i < progressBarWidth; i++) {
      builder.append('-');
    }
    builder.append("]\u001B[?25h");

    if (progress != lastValue) {
      System.out.print(builder.toString());
      System.out.flush();
    }

    return progress;
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

  /**
   * Match string with a pattern
   *
   * @param patArr          pattern chars, can contain wildcards
   * @param strArr          string char array
   * @param isCaseSensitive if true them macth is case-sensitive
   * @return true if matched
   * @since 1.2.1
   */
  public static boolean match(
      final @Nonnull char[] patArr,
      final @Nonnull char[] strArr,
      final boolean isCaseSensitive) {
    int patIdxStart = 0;
    int patIdxEnd = patArr.length - 1;
    int strIdxStart = 0;
    int strIdxEnd = strArr.length - 1;
    char ch;

    boolean containsStar = false;
    for (char aPatArr : patArr) {
      if (aPatArr == '*') {
        containsStar = true;
        break;
      }
    }

    if (!containsStar) {
      // No '*'s, so we make a shortcut
      if (patIdxEnd != strIdxEnd) {
        return false; // Pattern and string do not have the same size
      }
      for (int i = 0; i <= patIdxEnd; i++) {
        ch = patArr[i];
        if (ch != '?' && !equals(ch, strArr[i], isCaseSensitive)) {
          return false; // Character mismatch
        }
      }
      return true; // String matches against pattern
    }

    if (patIdxEnd == 0) {
      return true; // Pattern contains only '*', which matches anything
    }

    // Process characters before first star
    while ((ch = patArr[patIdxStart]) != '*' && strIdxStart <= strIdxEnd) {
      if (ch != '?' && !equals(ch, strArr[strIdxStart], isCaseSensitive)) {
        return false; // Character mismatch
      }
      patIdxStart++;
      strIdxStart++;
    }
    if (strIdxStart > strIdxEnd) {
      // All characters in the string are used. Check if only '*'s are
      // left in the pattern. If so, we succeeded. Otherwise failure.
      for (int i = patIdxStart; i <= patIdxEnd; i++) {
        if (patArr[i] != '*') {
          return false;
        }
      }
      return true;
    }

    // Process characters after last star
    while ((ch = patArr[patIdxEnd]) != '*' && strIdxStart <= strIdxEnd) {
      if (ch != '?' && !equals(ch, strArr[strIdxEnd], isCaseSensitive)) {
        return false; // Character mismatch
      }
      patIdxEnd--;
      strIdxEnd--;
    }
    if (strIdxStart > strIdxEnd) {
      // All characters in the string are used. Check if only '*'s are
      // left in the pattern. If so, we succeeded. Otherwise failure.
      for (int i = patIdxStart; i <= patIdxEnd; i++) {
        if (patArr[i] != '*') {
          return false;
        }
      }
      return true;
    }

    // process pattern between stars. padIdxStart and patIdxEnd point
    // always to a '*'.
    while (patIdxStart != patIdxEnd && strIdxStart <= strIdxEnd) {
      int patIdxTmp = -1;
      for (int i = patIdxStart + 1; i <= patIdxEnd; i++) {
        if (patArr[i] == '*') {
          patIdxTmp = i;
          break;
        }
      }
      if (patIdxTmp == patIdxStart + 1) {
        // Two stars next to each other, skip the first one.
        patIdxStart++;
        continue;
      }
      // Find the pattern between padIdxStart & padIdxTmp in str between
      // strIdxStart & strIdxEnd
      int patLength = (patIdxTmp - patIdxStart - 1);
      int strLength = (strIdxEnd - strIdxStart + 1);
      int foundIdx = -1;
      strLoop:
      for (int i = 0; i <= strLength - patLength; i++) {
        for (int j = 0; j < patLength; j++) {
          ch = patArr[patIdxStart + j + 1];
          if (ch != '?' && !equals(ch, strArr[strIdxStart + i + j], isCaseSensitive)) {
            continue strLoop;
          }
        }

        foundIdx = strIdxStart + i;
        break;
      }

      if (foundIdx == -1) {
        return false;
      }

      patIdxStart = patIdxTmp;
      strIdxStart = foundIdx + patLength;
    }

    // All characters in the string are used. Check if only '*'s are left
    // in the pattern. If so, we succeeded. Otherwise failure.
    for (int i = patIdxStart; i <= patIdxEnd; i++) {
      if (patArr[i] != '*') {
        return false;
      }
    }
    return true;
  }

  /**
   * Compare chars with case sensitivity flag
   *
   * @param c1              first char
   * @param c2              second char
   * @param isCaseSensitive if true then case-sensitive compare
   * @return true if chars equal
   * @since 1.2.1
   */
  public static boolean equals(
      final char c1,
      final char c2,
      final boolean isCaseSensitive
  ) {
    if (c1 == c2) {
      return true;
    }
    if (!isCaseSensitive) {
      // NOTE: Try both upper case and lower case as done by String.equalsIgnoreCase()
      if (Character.toUpperCase(c1) == Character.toUpperCase(c2)
          || Character.toLowerCase(c1) == Character.toLowerCase(c2)) {
        return true;
      }
    }
    return false;
  }

}
