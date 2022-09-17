package com.igormaznitsa.mvnjlink.jdkproviders.providers;

import static com.igormaznitsa.meta.common.utils.Assertions.assertNotNull;
import static com.igormaznitsa.mvnjlink.utils.ArchUtils.unpackArchiveFile;
import static com.igormaznitsa.mvnjlink.utils.HttpUtils.makeHttpClient;
import static java.lang.Boolean.parseBoolean;
import static java.lang.Integer.toHexString;
import static java.lang.String.format;
import static java.nio.file.Files.delete;
import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.isRegularFile;
import static java.util.Locale.ENGLISH;
import static org.apache.commons.io.FileUtils.deleteDirectory;

import com.igormaznitsa.meta.annotation.MustNotContainNull;
import com.igormaznitsa.meta.annotation.ReturnsOriginal;
import com.igormaznitsa.mvnjlink.exceptions.FailureException;
import com.igormaznitsa.mvnjlink.jdkproviders.AbstractJdkProvider;
import com.igormaznitsa.mvnjlink.mojos.AbstractJdkToolMojo;
import com.igormaznitsa.mvnjlink.utils.ArchUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.codec.digest.MessageDigestAlgorithms;
import org.apache.http.Header;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.maven.plugin.logging.Log;

public class UrlLinkJdkProvider extends AbstractJdkProvider {

  protected static final String[] MIMES = new String[] {
      "application/zip",
      "application/octet-stream",
      "application/x-zip-compressed",
      "application/x-gzip",
      "application/x-gtar",
      "application/x-tar",
      "application/tar",
      "application/x-compress",
      "application/x-compressed",
      "application/x-tgz",
      "application/tar+gzip"
  };

  public UrlLinkJdkProvider(@Nonnull final AbstractJdkToolMojo mojo) {
    super(mojo);
  }

  @ReturnsOriginal
  @Nonnull
  private static String asserAppropriateFileName(@Nonnull final String name) {
    if (name.isEmpty()) {
      throw new IllegalArgumentException("File name is empty");
    } else {
      if (name.length() > 96) {
        throw new IllegalArgumentException(
            "Too long file name (must contain less than 97 chars): " + name);
      }
      for (final char c : name.toCharArray()) {
        if (Character.isISOControl(c)) {
          throw new IllegalArgumentException("FIle name must not contain ISO control chars");
        }
        if (!(Character.isAlphabetic(c)
            || Character.isDigit(c)
            || c == '-'
            || c == '_'
            || c == '+'
            || c == '.'
            || Character.isWhitespace(c))) {
          throw new IllegalArgumentException(
              "File name contains non-allowed char '" + c + "': " + name);
        }
      }
    }
    return name;
  }

  @Nonnull
  private static String normalizeChecksum(@Nonnull final String text) {
    final StringBuilder result = new StringBuilder(text.length());

    for (final char c : text.toCharArray()) {
      if (Character.isDigit(c) || Character.isAlphabetic(c)) {
        result.append(Character.toUpperCase(c));
      }
    }

    return result.toString();
  }

  private static void assertChecksum(
      @Nonnull final String expected,
      @Nonnull @MustNotContainNull final List<MessageDigest> calculated,
      @Nonnull final String algorithm) {
    MessageDigest messageDigest = null;
    for (final MessageDigest d : calculated) {
      if (d.getAlgorithm().equalsIgnoreCase(algorithm)) {
        messageDigest = d;
        break;
      }
    }

    if (messageDigest == null) {
      throw new IllegalStateException("Can't find digest for algorithm: " + algorithm);
    }

    final String value =
        normalizeChecksum(Hex.encodeHexString(messageDigest.digest()));

    if (!value.equals(normalizeChecksum(expected))) {
      throw new IllegalStateException(
          "Digest is not correct, expected '" + expected + "' but calculated '" + value +
              "'");
    }
  }

  @Nonnull
  @Override
  public Path getPathToJdk(
      @Nullable final String authorization,
      @Nonnull final Map<String, String> config,
      @Nonnull @MustNotContainNull Consumer<Path>... loadedArchiveConsumers
  ) throws IOException {

    final Log log = this.mojo.getLog();
    assertParameters(config, "id", "url");

    final String id = asserAppropriateFileName(config.get("id").trim());
    final String url = config.get("url");
    final String sha256 = config.get("sha256");
    final String sha384 = config.get("sha384");
    final String sha512 = config.get("sha512");
    final String md5 = config.get("md5");
    final String md2 = config.get("md2");
    final String mimes = config.get("mime");

    final String[] allowedMimes = mimes == null ? MIMES : mimes.split(",");
    for (int i = 0; i < allowedMimes.length; i++) {
      allowedMimes[i] = allowedMimes[i].trim();
    }

    final boolean keepArchiveFile =
        parseBoolean(config.getOrDefault("keepArchive", "false"));

    final Path cacheFolder = this.mojo.findJdkCacheFolder();
    final Path cachedJdkPath = cacheFolder.resolve(id);

    final Path result;

    if (isDirectory(cachedJdkPath)) {
      log.info("Found cached JDK: " + cachedJdkPath.getFileName());
      result = cachedJdkPath;
    } else {
      if (isOfflineMode()) {
        throw new FailureException("Unpacked '" + cachedJdkPath.getFileName() +
            "' is not found, stopping process because offline mode is active");
      } else {
        log.info("Can't find cached: " + cachedJdkPath.getFileName());
      }

      final HttpClient httpClient =
          makeHttpClient(log, this.mojo.getProxy(), this.tuneClient(authorization),
              this.mojo.isDisableSSLcheck());

      final String archiveFileName =
          format(".%s-%s.arch", id, toHexString(id.hashCode()).toUpperCase(
              ENGLISH));

      log.info("Loading URL: " + url);
      result = loadJdkIntoCacheIfNotExist(cacheFolder,
          assertNotNull(cachedJdkPath.getFileName()).toString(), tempFolder ->
              downloadAndUnpack(httpClient, authorization, cacheFolder, tempFolder, url,
                  archiveFileName,
                  sha256,
                  sha384,
                  sha512,
                  md2,
                  md5,
                  keepArchiveFile,
                  allowedMimes,
                  loadedArchiveConsumers
              )
      );
    }
    return result;
  }

  @SafeVarargs
  protected final void downloadAndUnpack(
      @Nonnull final HttpClient client,
      @Nullable final String authorization,
      @Nonnull final Path tempFolder,
      @Nonnull final Path destUnpackFolder,
      @Nonnull final String downloadLink,
      @Nonnull final String archiveFileName,
      @Nullable final String sha256checksum,
      @Nullable final String sha384checksum,
      @Nullable final String sha512checksum,
      @Nullable final String md2checksum,
      @Nullable final String md5checksum,
      final boolean keepArchiveFile,
      @Nonnull @MustNotContainNull final String[] allowedMimes,
      @Nonnull @MustNotContainNull Consumer<Path>... loadedArchiveConsumers
  ) throws IOException {

    final Log log = this.mojo.getLog();

    final Path pathToArchiveFile = tempFolder.resolve(archiveFileName);

    boolean doLoadArchive = true;

    if (isRegularFile(pathToArchiveFile)) {
      log.info("Detected loaded archive: " + pathToArchiveFile.getFileName());
      doLoadArchive = false;
    }

    String mimeContentType = "unknown";
    if (doLoadArchive) {
      final List<MessageDigest> digests = new ArrayList<>();
      if (sha384checksum != null) {
        digests.add(DigestUtils.getSha384Digest());
      }
      if (sha256checksum != null) {
        digests.add(DigestUtils.getSha256Digest());
      }
      if (sha512checksum != null) {
        digests.add(DigestUtils.getSha512Digest());
      }
      if (md2checksum != null) {
        digests.add(DigestUtils.getMd2Digest());
      }
      if (md5checksum != null) {
        digests.add(DigestUtils.getMd5Digest());
      }

      final Header[] responseHeaders =
          this.doHttpGetIntoFile(client, this.tuneRequestBase(authorization), downloadLink,
              pathToArchiveFile, digests, this.mojo.getConnectionTimeout(), allowedMimes);

      mimeContentType =
          Arrays.stream(responseHeaders).filter(x -> x.getName().equalsIgnoreCase("content-type"))
              .findFirst()
              .map(NameValuePair::getValue)
              .orElse("");

      log.debug("Downloaded file content type: " + mimeContentType);
      log.debug("Response headers: " + Arrays.toString(responseHeaders));

      if (md2checksum != null) {
        assertChecksum(md2checksum, digests, MessageDigestAlgorithms.MD2);
        log.info("MD2 digest is OK");
      }

      if (md5checksum != null) {
        assertChecksum(md5checksum, digests, MessageDigestAlgorithms.MD5);
        log.info("MD5 digest is OK");
      }

      if (sha256checksum != null) {
        assertChecksum(sha256checksum, digests, MessageDigestAlgorithms.SHA_256);
        log.info("SHA256 digest is OK");
      }

      if (sha384checksum != null) {
        assertChecksum(sha384checksum, digests, MessageDigestAlgorithms.SHA_384);
        log.info("SHA384 digest is OK");
      }

      if (sha512checksum != null) {
        assertChecksum(sha512checksum, digests, MessageDigestAlgorithms.SHA_512);
        log.info("SHA512 digest is OK");
      }
      log.info(
          "Archive file has been loaded successfully as: " + pathToArchiveFile);

      for (final Consumer<Path> c : loadedArchiveConsumers) {
        c.accept(pathToArchiveFile);
      }
    } else {
      log.info("Archive load is skipped");
    }

    if (isDirectory(destUnpackFolder)) {
      log.info("Detected existing target folder, deleting it: " + destUnpackFolder.getFileName());
      deleteDirectory(destUnpackFolder.toFile());
    }

    final String archiveRootName = ArchUtils.findShortestDirectory(pathToArchiveFile);
    log.debug("Root archive folder: " + archiveRootName);
    log.info("Unpacking archive...");
    final int numberOfUnpackedFiles =
        unpackArchiveFile(
            this.mojo.getLog(),
            true,
            pathToArchiveFile,
            destUnpackFolder,
            archiveRootName
        );
    if (numberOfUnpackedFiles == 0) {
      throw new IOException(
          "Extracted 0 files from archive! May be wrong root folder name: " + archiveRootName);
    }
    log.info(
        "Archive has been unpacked successfully, extracted " + numberOfUnpackedFiles + " files");

    if (keepArchiveFile) {
      log.info("Keep downloaded archive file in cache: " + pathToArchiveFile);
    } else {
      log.info("Deleting archive: " + pathToArchiveFile);
      delete(pathToArchiveFile);
    }
  }

}
