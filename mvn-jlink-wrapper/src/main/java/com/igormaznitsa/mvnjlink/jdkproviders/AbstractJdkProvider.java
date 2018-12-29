package com.igormaznitsa.mvnjlink.jdkproviders;

import com.igormaznitsa.mvnjlink.mojos.AbstractJdkToolMojo;
import org.apache.maven.plugin.logging.Log;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static com.igormaznitsa.meta.common.utils.Assertions.assertNotNull;

public abstract class AbstractJdkProvider {

  protected final AbstractJdkToolMojo mojo;

  public AbstractJdkProvider(@Nonnull final AbstractJdkToolMojo mojo) {
    this.mojo = assertNotNull(mojo);
  }

  @Nonnull
  protected File lockCache(@Nonnull final Log log, @Nonnull final Path cacheFolder) throws IOException {
    final File lockFile = cacheFolder.resolve(".#loadLock").toFile();
    lockFile.deleteOnExit();
    if (!lockFile.createNewFile()) {
      log.info("Detected SDK loading, waiting for the process end");
      while (lockFile.exists()) {
        try {
          Thread.sleep(100L);
        } catch (InterruptedException ex) {
          throw new IOException("Wait of SDK loading is interrupted", ex);
        }
      }
      log.info("Loading process has been completed");
    }
    return lockFile;
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
    return this.mojo.isOfflineModeActive();
  }

  @Nonnull
  public abstract Path prepareSourceJdkFolder(@Nonnull final Map<String, String> config) throws IOException;
}
