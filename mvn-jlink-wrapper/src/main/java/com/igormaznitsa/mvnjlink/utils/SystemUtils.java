package com.igormaznitsa.mvnjlink.utils;

import org.apache.maven.plugin.logging.Log;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SystemUtils {
  private SystemUtils() {

  }

  @Nonnull
  public static Path findJdkExecutable(@Nonnull final Path jdkFolder, @Nonnull final String jdkExecutableFileName) throws IOException {
    final String extenstion = SystemUtils.findAppropriateBinExtension();

    final Path result = jdkFolder.resolve("bin" + File.separatorChar + jdkExecutableFileName + (extenstion.isEmpty() ? extenstion : '.' + extenstion));
    if (!Files.isRegularFile(result)) {
      throw new IOException("Can't find file: " + result);
    }
    if (!Files.isExecutable(result)) {
      throw new IOException("Can't get execution rights: " + result);
    }
    return result;
  }

  @Nonnull
  public static String findAppropriateBinExtension() {
    String result = "";
    if (org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS) {
      result = "exe";
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
