package com.igormaznitsa.mvnjlink.utils;

import org.apache.maven.plugin.logging.Log;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SystemUtils {
  private SystemUtils() {

  }

  @Nullable
  public static Path findJdkExecutable(@Nonnull final Log log, @Nonnull final Path jdkFolder, @Nonnull final String jdkExecutableFileName) {
    Path result = jdkFolder.resolve("bin" + File.separatorChar + ensureOsExtension(jdkExecutableFileName));
    if (!Files.isRegularFile(result)) {
      log.error("Can't find file: " + result);
      result = null;
    } else if (!Files.isExecutable(result)) {
      log.error("Can't find executable file: " + result);
      result = null;
    }
    return result;
  }

  @Nullable
  public static String ensureOsExtension(@Nullable final String text) {
    final String result;
    if (text == null) {
      result = null;
    } else {
      if (org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS) {
        if (!text.endsWith(".exe")) {
          result = text + ".exe";
        } else {
          result = text;
        }
      } else {
        result = text;
      }
    }
    return result;
  }

  public static void closeCloseable(@Nullable final Closeable closeable, @Nonnull final Log logger) {
    if (closeable != null) {
      try {
        closeable.close();
      } catch (Exception ex) {
        logger.debug("Can't close closeable object: " + closeable, ex);
      }
    }
  }


}
