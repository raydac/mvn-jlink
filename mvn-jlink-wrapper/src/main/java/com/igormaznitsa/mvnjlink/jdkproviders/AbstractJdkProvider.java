package com.igormaznitsa.mvnjlink.jdkproviders;

import com.igormaznitsa.mvnjlink.mojos.AbstractJlinkMojo;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static com.igormaznitsa.meta.common.utils.Assertions.assertNotNull;

public abstract class AbstractJdkProvider {

  protected final AbstractJlinkMojo mojo;

  public AbstractJdkProvider(@Nonnull final AbstractJlinkMojo mojo) {
    this.mojo = assertNotNull(mojo);
  }

  @Nonnull
  protected static String escapeFileName(@Nonnull final String text) {
    final StringBuilder result = new StringBuilder(text.length());
    for (final char c : text.toCharArray()) {
      switch (c) {
        case '\\':
        case '/':
        case ':':
        case '*':
        case '?':
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

  protected boolean isOfflineMode() {
    return this.mojo.isOffline();
  }

  @Nonnull
  public abstract Path prepareJdkFolder(@Nonnull final Map<String, String> config) throws IOException;
}
