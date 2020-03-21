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

import static com.igormaznitsa.meta.common.utils.Assertions.assertNotNull;
import static com.igormaznitsa.mvnjlink.utils.HttpUtils.doGetRequest;
import static java.lang.String.format;
import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.newInputStream;
import static java.nio.file.Files.newOutputStream;
import static java.util.stream.Stream.of;
import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;


import com.igormaznitsa.meta.annotation.MustNotContainNull;
import com.igormaznitsa.meta.common.utils.Assertions;
import com.igormaznitsa.mvnjlink.exceptions.IORuntimeWrapperException;
import com.igormaznitsa.mvnjlink.mojos.AbstractJdkToolMojo;
import com.igormaznitsa.mvnjlink.utils.StringUtils;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.lang3.SystemUtils;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.maven.plugin.logging.Log;

public abstract class AbstractJdkProvider {

  protected final AbstractJdkToolMojo mojo;

  public AbstractJdkProvider(@Nonnull final AbstractJdkToolMojo mojo) {
    this.mojo = assertNotNull(mojo);
  }

  private static String hideSensitiveText(final String text) {
    return text.charAt(0) + "********" + text.charAt(text.length() - 1);
  }

  @Nonnull
  protected Function<HttpRequestBase, HttpRequestBase> tuneRequestBase(@Nullable final String authorization) {
    return x -> {
      if (authorization != null && !authorization.isEmpty()) {
        mojo.getLog().debug("Providing authorization: " + hideSensitiveText(authorization));
        x.setHeader(HttpHeaders.AUTHORIZATION, authorization);
      }
      return x;
    };
  }

  @Nullable
  protected Function<HttpClientBuilder, HttpClientBuilder> tuneClient(@Nullable final String authorization) {
    return x -> x;
  }

  protected static void assertParameters(@Nonnull final Map<String, String> attrMap, @Nonnull @MustNotContainNull final String... names) {
    final Optional<String> notFoundAttribute = of(names).filter(x -> !attrMap.containsKey(x)).findAny();
    if (notFoundAttribute.isPresent()) {
      throw new IllegalArgumentException(format("Parameter named '%s' must be presented", notFoundAttribute.get()));
    }
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
  protected String doHttpGetText(
      @Nonnull final HttpClient client,
      @Nullable final Function<HttpRequestBase, HttpRequestBase> customizer,
      @Nonnull final String url,
      final int connectionRequestTimeout,
      @Nonnull @MustNotContainNull String... acceptedContent
  ) throws IOException {
    final AtomicReference<String> result = new AtomicReference<>();
    doGetRequest(client, customizer, url, this.mojo.getProxy(),
        x -> this.logRateLimitIfPresented(url, x),
        x -> {
          try {
            result.set(EntityUtils.toString(x));
          } catch (IOException ex) {
            throw new IORuntimeWrapperException(ex);
          }
        }, connectionRequestTimeout, false, acceptedContent);
    return result.get();
  }

  protected void logRateLimitIfPresented(@Nonnull final String resourceUrl, @Nonnull final HttpResponse response) {
    final Log logger = this.mojo.getLog();

    Header rateLimitLimit = response.getFirstHeader("X-RateLimit-Limit");
    if (rateLimitLimit == null) {
      rateLimitLimit = response.getFirstHeader("X-Rate-Limit-Limit");
    }

    Header rateLimitRemaining = response.getFirstHeader("X-RateLimit-Remaining");
    if (rateLimitRemaining == null) {
      rateLimitRemaining = response.getFirstHeader("X-Rate-Limit-Remaining");
    }

    Header rateLimitReset = response.getFirstHeader("X-RateLimit-Reset");
    if (rateLimitReset == null) {
      rateLimitReset = response.getFirstHeader("X-Rate-Limit-Reset");
    }

    final String rateLimitLimitValue = rateLimitLimit == null ? null : rateLimitLimit.getValue().trim();
    final String rateLimitRemainingValue = rateLimitRemaining == null ? null : rateLimitRemaining.getValue().trim();
    final String rateLimitResetValue = rateLimitReset == null ? null : rateLimitReset.getValue().trim();

    long rateLimitLimitLong;
    try {
      rateLimitLimitLong = rateLimitLimitValue == null ? -1L : Long.parseLong(rateLimitLimitValue);
    } catch (NumberFormatException ex) {
      logger.warn(format("Detected unexpected '%s' value in rate limit limit header for '%s'", rateLimitLimitValue, resourceUrl));
      rateLimitLimitLong = -1L;
    }

    long rateLimitRemainingLong;
    try {
      rateLimitRemainingLong = rateLimitRemainingValue == null ? -1L : Long.parseLong(rateLimitRemainingValue);
    } catch (NumberFormatException ex) {
      logger.warn(format("Detected unexpected '%s' value in rate limit remaining header for '%s'", rateLimitRemainingValue, resourceUrl));
      rateLimitRemainingLong = -1L;
    }

    long rateLimitResetLong;
    try {
      rateLimitResetLong = rateLimitResetValue == null ? -1L : Long.parseLong(rateLimitResetValue);
    } catch (NumberFormatException ex) {
      logger.warn(format("Detected unexpected '%s' value in rate limit reset header for '%s'", rateLimitResetValue, resourceUrl));
      rateLimitResetLong = -1L;
    }
    logger.debug(format("Resource '%s', limit-remaning=%d, limit-limit=%d, limit-reset=%d", resourceUrl, rateLimitRemainingLong, rateLimitLimitLong, rateLimitResetLong));

    final String rateLimitResetDate = rateLimitResetLong < 0L ? "UNKNOWN" : new Date(rateLimitResetLong * 1000L).toString();

    if (rateLimitRemainingLong < 0L) {
      logger.debug("Rate limit remaining is not provided");
    } else if (rateLimitRemainingLong == 0L) {
      logger.error(format("Detected zero limit remaining for '%s'! Rate reset expected at '%s'", resourceUrl, rateLimitResetDate));
    } else if (rateLimitRemainingLong < 5L) {
      logger.warn(format("Detected %d limit remaining for '%s'.", rateLimitRemainingLong, resourceUrl));
    } else {
      logger.info(format("Detected %d limit remaining for '%s'.", rateLimitRemainingLong, resourceUrl));
    }
  }

  /**
   * Download content file through GET request and calculate its SHA256 hash
   *
   * @param client                   http client
   * @param url                      url of the content file
   * @param targetFile               target file to save the content
   * @param digest                   calculator of needed digest
   * @param connectionRequestTimeout timeout for connection request
   * @param acceptedContent          mime types of accepted content
   * @return response headers
   * @throws IOException it any transport error
   */
  @MustNotContainNull
  @Nonnull
  protected Header[] doHttpGetIntoFile(
      @Nonnull final HttpClient client,
      @Nullable final Function<HttpRequestBase, HttpRequestBase> customizer,
      @Nonnull final String url,
      @Nonnull final Path targetFile,
      @Nonnull final MessageDigest digest,
      final int connectionRequestTimeout,
      @Nonnull @MustNotContainNull final String... acceptedContent
  ) throws IOException {
    final Log log = this.mojo.getLog();
    log.debug(format("Loading %s into file %s, request timeout %d ms", url, targetFile.toString(), connectionRequestTimeout));

    Header[] responseHeaders;

    try {
      responseHeaders = doGetRequest(client, customizer, url, this.mojo.getProxy(),
          x -> this.logRateLimitIfPresented(url, x),
          httpEntity -> {
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
                  log.info(format("Loading file %s, size %d bytes", targetFile.getFileName().toString(), contentSize));
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
              log.error(format("Can't download %s into %s: %s", url, targetFile, ex.getMessage()));
              if (Files.exists(targetFile)) {
                log.debug(format("Deleting file %s", targetFile));
                try {
                  Files.delete(targetFile);
                } catch (IOException exx) {
                  log.error(format("Can't delete file %s: %s", targetFile, exx.getMessage()));
                }
              }
              throw new IORuntimeWrapperException(ex);
            } finally {
              if (showProgress) {
                System.out.println();
              }
            }
          }, connectionRequestTimeout, true, acceptedContent);
    } catch (IORuntimeWrapperException ex) {
      throw ex.getWrapped();
    }
    return Assertions.assertNotNull(responseHeaders);
  }

  @Nonnull
  protected Path loadJdkIntoCacheIfNotExist(@Nonnull final Path cacheFolder, @Nonnull final String targetFolderName, @Nonnull IoLoader loader) throws IOException {
    final Log log = this.mojo.getLog();

    final Path tempFolder = cacheFolder.resolve(".TMP" + targetFolderName);
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
  public abstract Path getPathToJdk(@Nullable final String authorization, @Nonnull final Map<String, String> config) throws IOException;

  @FunctionalInterface
  public interface IoLoader {

    void doLoad(@Nonnull final Path destinationFolder) throws IOException;
  }
}
