package com.igormaznitsa.mvnjlink.jdkproviders;

import com.igormaznitsa.meta.annotation.MustNotContainNull;
import com.igormaznitsa.mvnjlink.exceptions.IORuntimeWrapperException;
import com.igormaznitsa.mvnjlink.mojos.AbstractJdkToolMojo;
import org.apache.commons.lang3.SystemUtils;
import org.apache.http.HttpRequest;
import org.apache.http.client.HttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.maven.plugin.logging.Log;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static com.igormaznitsa.meta.common.utils.Assertions.assertNotNull;
import static com.igormaznitsa.mvnjlink.utils.HttpUtils.doGetRequest;
import static java.nio.file.Files.*;
import static java.util.stream.Stream.of;
import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;
import static org.apache.commons.io.IOUtils.copy;

public abstract class AbstractJdkProvider {

  @FunctionalInterface
  public interface IoLoader {
    void doLoad(@Nonnull final Path destinationFolder) throws IOException;
  }

  protected static void assertParameters(@Nonnull final Map<String, String> attrMap, @Nonnull @MustNotContainNull final String... names) {
    final Optional<String> notFoundAttribute = of(names).filter(x -> !attrMap.containsKey(x)).findAny();
    if (notFoundAttribute.isPresent()) {
      throw new IllegalArgumentException(String.format("Parameter named '%s' must be presented", notFoundAttribute.get()));
    }
  }

  protected final AbstractJdkToolMojo mojo;

  public AbstractJdkProvider(@Nonnull final AbstractJdkToolMojo mojo) {
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

  @Nonnull
  protected File lockCache(@Nonnull final Path cacheFolder) throws IOException {
    final Log log = this.mojo.getLog();

    final File lockFile = cacheFolder.resolve(".#cacheLocker#.").toFile();
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
  protected static String calcSha256ForFile(@Nonnull final Path file) throws IOException {
    try (final InputStream in = newInputStream(file)) {
      return sha256Hex(in);
    }
  }

  @Nonnull
  protected String doHttpGetText(@Nonnull final HttpClient client, @Nonnull final String url, @Nonnull @MustNotContainNull String ... acceptedContent) throws IOException {
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

  @Nonnull
  protected String doHttpGetIntoFile(@Nonnull final HttpClient client, @Nonnull final String url, @Nonnull final Path targetFile, @Nonnull @MustNotContainNull final String... acceptedContent) throws IOException {
    final Log log = this.mojo.getLog();
    log.debug(String.format("Getting file %s into %s", url, targetFile.toString()));
    try {
      doGetRequest(client, url, this.mojo.getProxy(), httpEntity -> {
        try {
          try (final OutputStream fileOutStream = newOutputStream(targetFile)) {
            copy(httpEntity.getContent(), fileOutStream, 128 * 1024);
          }
        } catch (IOException ex) {
          throw new IORuntimeWrapperException(ex);
        }
      }, acceptedContent);
    } catch (IORuntimeWrapperException ex) {
      throw ex.getWrapped();
    }
    return calcSha256ForFile(targetFile);
  }

  @Nonnull
  protected Path loadJdkIntoCacheIfNotExist(@Nonnull final Path cacheFolder, @Nonnull final String targetFolderName, @Nonnull IoLoader loader) throws IOException {
    final Log log = this.mojo.getLog();

    final Path tempFolder = cacheFolder.resolve(".#" + targetFolderName);
    final Path resultFolder = cacheFolder.resolve(targetFolderName);

    File lockingFile = null;
    try {
      lockingFile = this.lockCache(cacheFolder);
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
  public abstract Path prepareSourceJdkFolder(@Nonnull final Map<String, String> config) throws IOException;
}
