package com.igormaznitsa.mvnjlink.utils;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public final class SystemUtils {
  private SystemUtils() {

  }

  @Nonnull
  public static File findJdkExecutable(final File jdkFolder, final String fileName) throws IOException {
    final String extenstion = SystemUtils.findAppropriateBinExtension();
    final String relativePath = "bin" + File.separatorChar + fileName + (extenstion.isEmpty() ? extenstion : '.' + extenstion);

    final File result = new File(jdkFolder, relativePath);
    if (!result.isFile()) {
      throw new IOException("Can't find file: " + result);
    }
    if (!Files.isExecutable(result.toPath())) {
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
