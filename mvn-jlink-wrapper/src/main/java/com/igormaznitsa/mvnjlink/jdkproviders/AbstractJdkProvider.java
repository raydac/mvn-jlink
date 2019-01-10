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

package com.igormaznitsa.mvnjlink.jdkproviders;

import com.igormaznitsa.meta.annotation.MustNotContainNull;
import com.igormaznitsa.meta.common.utils.Assertions;
import com.igormaznitsa.mvnjlink.exceptions.IORuntimeWrapperException;
import com.igormaznitsa.mvnjlink.mojos.AbstractJdkToolMojo;
import com.igormaznitsa.mvnjlink.utils.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.http.Header;
import org.apache.http.client.HttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.maven.plugin.logging.Log;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static com.igormaznitsa.meta.common.utils.Assertions.assertNotNull;
import static com.igormaznitsa.mvnjlink.utils.HttpUtils.doGetRequest;
import static java.lang.String.format;
import static java.nio.file.Files.*;
import static java.util.stream.Stream.of;
import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;

public abstract class AbstractJdkProvider {

  protected final AbstractJdkToolMojo mojo;

  public AbstractJdkProvider(@Nonnull final AbstractJdkToolMojo mojo) {
    this.mojo = assertNotNull(mojo);
  }

  protected static void assertParameters(@Nonnull final Map<String, String> attrMap, @Nonnull @MustNotContainNull final String... names) {
    final Optional<String> notFoundAttribute = of(names).filter(x -> !attrMap.containsKey(x)).findAny();
    if (notFoundAttribute.isPresent()) {
      throw new IllegalArgumentException(format("Parameter named '%s' must be presented", notFoundAttribute.get()));
    }
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

  @Nonnull
  protected static String calcSha256ForFile(@Nonnull final Path file) throws IOException {
    try (final InputStream in = newInputStream(file)) {
      return sha256Hex(in);
    }
  }

  @Nonnull
  protected File lockCache(@Nonnull final Path cacheFolder, @Nonnull final String jdkId) throws IOException {
    final Log log = this.mojo.getLog();

    final File lockFile = cacheFolder.resolve(".#" + jdkId).toFile();
    lockFile.deleteOnExit();

    if (!lockFile.createNewFile()) {
      boolean locked = false;
      log.info("Detected existing lock, waiting for unlocking");
      while (!Thread.currentThread().isInterrupted()) {
        locked = lockFile.createNewFile();
        if (locked) {
          break;
        } else {
          try {
            Thread.sleep(500L);
          } catch (InterruptedException ex) {
            log.warn("Process interrupted");
            Thread.currentThread().interrupt();
          }
        }
      }
      if (!locked) {
        throw new IOException("Can't lock folder");
      }
    }

    return lockFile;
  }

  protected boolean isOfflineMode() {
    return this.mojo.isOfflineModeActive();
  }


  @Nonnull
  protected String findCurrentOs(@Nonnull final String macOsId) {
    final String defaultOs;
    if (SystemUtils.IS_OS_MAC) {
      defaultOs = macOsId;
    } else if (SystemUtils.IS_OS_WINDOWS) {
      defaultOs = "windows";
    } else if (SystemUtils.IS_OS_AIX) {
      defaultOs = "aix";
    } else if (SystemUtils.IS_OS_FREE_BSD) {
      defaultOs = "freebsd";
    } else if (SystemUtils.IS_OS_IRIX) {
      defaultOs = "irix";
    } else if (SystemUtils.IS_OS_ZOS) {
      defaultOs = "zos";
    } else {
      defaultOs = "linux";
    }
    return defaultOs;
  }

  @Nonnull
  protected String doHttpGetText(@Nonnull final HttpClient client, @Nonnull final String url, @Nonnull @MustNotContainNull String... acceptedContent) throws IOException {
    final AtomicReference<String> result = new AtomicReference<>();
    doGetRequest(client, url, this.mojo.getProxy(), x -> {
      try {
        result.set(EntityUtils.toString(x));
      } catch (IOException ex) {
        throw new IORuntimeWrapperException(ex);
      }
    }, acceptedContent);
    return result.get();
  }

  /**
   * Download content file through GET request and calculate its SHA256 hash
   *
   * @param client          http client
   * @param url             url of the content file
   * @param targetFile      target file to save the content
   * @param digest          calculator of needed digest
   * @param acceptedContent mime types of accepted content
   * @return response headers
   * @throws IOException it any transport error
   */
  @MustNotContainNull
  @Nonnull
  protected Header[] doHttpGetIntoFile(@Nonnull final HttpClient client, @Nonnull final String url, @Nonnull final Path targetFile, @Nonnull final MessageDigest digest, @Nonnull @MustNotContainNull final String... acceptedContent) throws IOException {
    final Log log = this.mojo.getLog();
    log.debug(format("Loading %s into file %s", url, targetFile.toString()));

    Header[] responseHeaders = null;

    try {
      responseHeaders = doGetRequest(client, url, this.mojo.getProxy(), httpEntity -> {
        boolean showProgress = false;
        try {
          try (final OutputStream fileOutStream = newOutputStream(targetFile)) {
            final byte[] buffer = new byte[1024 * 1024];

            final long contentSize = httpEntity.getContentLength();
            final InputStream inStream = httpEntity.getContent();

            log.debug("Reported content size: " + contentSize + " bytes");

            final int PROGRESSBAR_WIDTH = 10;
            final String LOADING_TITLE = format("Loading %d Mb ", (contentSize / (1024L * 1024L)));

            showProgress = contentSize > 0L && !this.mojo.getSession().isParallel();

            if (!showProgress) {
              log.info(String.format("Loading file %s, size %d bytes", targetFile.getFileName().toString(), contentSize));
            }

            long downloadByteCounter = 0L;

            int lastShownProgress = -1;

            if (showProgress) {
              lastShownProgress = StringUtils.printTextProgress(LOADING_TITLE, downloadByteCounter, contentSize, PROGRESSBAR_WIDTH, lastShownProgress);
            }

            digest.reset();

            while (!Thread.currentThread().isInterrupted()) {
              final int length = inStream.read(buffer);
              if (length < 0) {
                break;
              }

              fileOutStream.write(buffer, 0, length);
              digest.update(buffer, 0, length);

              downloadByteCounter += length;

              if (showProgress) {
                lastShownProgress = StringUtils.printTextProgress(LOADING_TITLE, downloadByteCounter, contentSize, PROGRESSBAR_WIDTH, lastShownProgress);
              }
            }
            fileOutStream.flush();
          }
        } catch (IOException ex) {
          throw new IORuntimeWrapperException(ex);
        } finally {
          if (showProgress) {
            System.out.println();
          }
        }
      }, acceptedContent);
    } catch (IORuntimeWrapperException ex) {
      throw ex.getWrapped();
    }
    return Assertions.assertNotNull(responseHeaders);
  }

  @Nonnull
  protected Path loadJdkIntoCacheIfNotExist(@Nonnull final Path cacheFolder, @Nonnull final String targetFolderName, @Nonnull IoLoader loader) throws IOException {
    final Log log = this.mojo.getLog();

    final Path tempFolder = cacheFolder.resolve(".#" + targetFolderName);
    final Path resultFolder = cacheFolder.resolve(targetFolderName);

    File lockingFile = null;
    try {
      lockingFile = this.lockCache(cacheFolder, targetFolderName);
      if (isDirectory(resultFolder)) {
        log.debug("Already cached JDK folder detected, skip loading: " + resultFolder);
      } else {
        log.debug("JDK cache has been locking, the locking file: " + lockingFile);

        loader.doLoad(tempFolder);

        if (tempFolder.toFile().renameTo(resultFolder.toFile())) {
          log.debug("Renamed " + tempFolder.getFileName() + " to " + resultFolder.getFileName());
        } else {
          log.error("Can't rename " + tempFolder.getFileName() + " to " + resultFolder.getFileName());
          throw new IOException("Can't rename temp folder " + tempFolder + " to " + resultFolder);
        }
      }
    } finally {
      if (lockingFile != null) {
        log.debug("Locker delete status is " + lockingFile.delete());
      } else {
        log.debug("Locker is null");
      }
    }
    return resultFolder;
  }

  @Nonnull
  public abstract Path getPathToJdk(@Nonnull final Map<String, String> config) throws IOException;

  @FunctionalInterface
  public interface IoLoader {
    void doLoad(@Nonnull final Path destinationFolder) throws IOException;
  }
}
