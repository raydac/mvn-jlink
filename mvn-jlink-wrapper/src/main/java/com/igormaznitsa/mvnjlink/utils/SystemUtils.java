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

import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.isRegularFile;

import com.igormaznitsa.meta.annotation.MustNotContainNull;
import java.io.Closeable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.maven.plugin.logging.Log;

public final class SystemUtils {
  private SystemUtils() {

  }

  @Nullable
  public static Path findJdkExecutable(
      @Nonnull final Log log,
      @Nonnull final Path jdkFolder,
      @Nonnull final String jdkExecutableFileName,
      @Nonnull final HostOs hostOs,
      @Nonnull @MustNotContainNull final Map<HostOs, String> forceExtensions
  ) {
    Path binFolder = jdkFolder.resolve("bin");
    if (isDirectory(binFolder)) {
      log.debug("Detected JDK bin folder: " + binFolder);
    } else {
      log.debug("Can't find bin folder in jdk: " + jdkFolder);
      binFolder = jdkFolder.resolve("Contents/Home/bin");
      if (isDirectory(binFolder)) {
        log.debug("Detected MacOS JDK bin folder: " + jdkFolder);
      } else {
        log.debug("Can't find MacOS bin folder in jdk: " + jdkFolder);
        return null;
      }
    }

    Path result = binFolder.resolve(
        addHostFileExtensionIfNeeded(jdkExecutableFileName, hostOs, forceExtensions));
    if (!isRegularFile(result)) {
      log.error("Can't find file: " + result);
      result = null;
    } else if (!Files.isExecutable(result)) {
      log.error("Can't find executable file: " + result);
      result = null;
    }
    return result;
  }

  @Nonnull
  public static String addHostFileExtensionIfNeeded(
      @Nonnull final String fileName,
      @Nonnull final HostOs hostOs,
      @Nonnull @MustNotContainNull final Map<HostOs, String> forceExtensions
  ) {
    final String result;
    final String extension = forceExtensions.getOrDefault(hostOs, hostOs.getDefaultExtension());
    if (extension.isEmpty() || fileName.contains(".")) {
      result = fileName;
    } else {
      if (fileName.toLowerCase(Locale.ENGLISH)
          .endsWith(('.' + extension).toLowerCase(Locale.ENGLISH))) {
        result = fileName;
      } else {
        result = fileName + '.' + extension;
      }
    }
    return result;
  }

  public static void closeCloseable(@Nullable final Closeable closeable,
                                    @Nullable final Log logger) {
    if (closeable != null) {
      try {
        closeable.close();
      } catch (Exception ex) {
        if (logger != null) {
          logger.debug("Can't close closeable object: " + closeable, ex);
        }
      }
    }
  }


}
