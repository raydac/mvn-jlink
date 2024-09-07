package com.igormaznitsa.mvnjlink.jdkproviders.providers;

import static com.igormaznitsa.meta.common.utils.Assertions.assertNotNull;
import static com.igormaznitsa.mvnjlink.utils.ArchUtils.unpackArchiveFile;
import static com.igormaznitsa.mvnjlink.utils.StringUtils.escapeFileName;
import static com.igormaznitsa.mvnjlink.utils.StringUtils.longHash;
import static java.nio.file.Files.delete;
import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.isRegularFile;
import static java.util.Locale.ENGLISH;
import static org.apache.commons.io.FileUtils.deleteDirectory;

import com.igormaznitsa.meta.annotation.MustNotContainNull;
import com.igormaznitsa.meta.common.utils.GetUtils;
import com.igormaznitsa.mvnjlink.exceptions.FailureException;
import com.igormaznitsa.mvnjlink.jdkproviders.AbstractJdkProvider;
import com.igormaznitsa.mvnjlink.mojos.AbstractJdkToolMojo;
import com.igormaznitsa.mvnjlink.utils.ArchUtils;
import com.igormaznitsa.mvnjlink.utils.HostOs;
import com.igormaznitsa.mvnjlink.utils.HttpUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.Header;
import org.apache.http.client.HttpClient;
import org.apache.maven.plugin.logging.Log;

public class AdoptiumApiOpenJdkProvider extends AbstractJdkProvider {

  public static final String API_BASE_URL = "https://api.adoptium.net/v3/";

  public AdoptiumApiOpenJdkProvider(@Nonnull final AbstractJdkToolMojo mojo) {
    super(mojo);
  }

  @Nonnull
  private static String makeCachePath(
      @Nonnull final String baseUrl,
      @Nullable final String jdkFeatureVersion,
      @Nullable final String jdkReleaseName,
      @Nonnull final String jdkReleaseType,
      @Nonnull final String jdkArch,
      @Nonnull final String jdkHeapSize,
      @Nonnull final String jdkImageType,
      @Nonnull final String jdkJvmImpl,
      @Nonnull final String jdkOs,
      @Nonnull final String jdkVendor,
      @Nullable final String jdkClib,
      @Nullable final String jdkProject
  ) {
    String folderName = "ADOPT_" +
        Long.toHexString(longHash(baseUrl)).toUpperCase(ENGLISH) +
        '_' + (jdkFeatureVersion == null ? "" : jdkFeatureVersion.trim().toLowerCase(
        ENGLISH)) +
        (jdkReleaseName == null ? "" : jdkReleaseName.trim().toLowerCase(
            ENGLISH)) +
        (jdkFeatureVersion == null ? "" : '_' + jdkReleaseType.trim().toLowerCase(ENGLISH)) +
        '_' + jdkArch.trim().toLowerCase(ENGLISH) +
        '_' + jdkHeapSize.trim().toLowerCase(ENGLISH) +
        '_' + jdkImageType.trim().toLowerCase(ENGLISH) +
        '_' + jdkJvmImpl.trim().toLowerCase(ENGLISH) +
        '_' + jdkOs.trim().toLowerCase(ENGLISH) +
        '_' + jdkVendor.trim().toLowerCase(ENGLISH);
    if (jdkClib != null || jdkProject != null) {
      folderName += '_' + (jdkClib == null ? "" : jdkClib.trim().toLowerCase(ENGLISH)) +
          (jdkProject == null ? "" : jdkProject.trim().toLowerCase(ENGLISH));
    }
    return escapeFileName(folderName);
  }

  @Nonnull
  private static String makeUrl(
      @Nonnull final String baseUrl,
      @Nullable final String jdkFeatureVersion,
      @Nullable final String jdkReleaseName,
      @Nonnull final String jdkReleaseType,
      @Nonnull final String jdkArch,
      @Nonnull final String jdkHeapSize,
      @Nonnull final String jdkImageType,
      @Nonnull final String jdkJvmImpl,
      @Nonnull final String jdkOs,
      @Nonnull final String jdkVendor,
      @Nullable final String jdkClib,
      @Nullable final String jdkProject) {
    final StringBuilder builder = new StringBuilder(baseUrl);
    if (!builder.toString().endsWith("/")) {
      builder.append('/');
    }

    if (jdkReleaseName != null) {
      builder.append("binary/version/").append(jdkReleaseName);
    } else if (jdkFeatureVersion != null) {
      builder.append("binary/latest/").append(jdkFeatureVersion).append('/').append(jdkReleaseType);
    } else {
      throw new IllegalStateException("There must be defined either featureVersion or releaseName");
    }

    builder
        .append("/").append(jdkOs.trim())
        .append("/").append(jdkArch.trim())
        .append("/").append(jdkImageType.trim())
        .append("/").append(jdkJvmImpl.trim())
        .append("/").append(jdkHeapSize.trim())
        .append("/").append(jdkVendor.trim());

    if (jdkClib != null || jdkProject != null) {
      builder.append("?");
      boolean added = false;
      if (jdkClib != null) {
        added = true;
        builder.append("c_lib=").append(jdkClib.trim());
      }
      if (jdkProject != null) {
        if (added) {
          builder.append('&');
        }
        builder.append("project=").append(jdkProject.trim());
      }
    }

    return builder.toString();
  }

  @SafeVarargs
  @Nonnull
  @Override
  public final Path getPathToJdk(@Nullable String authorization,
                                 @MustNotContainNull @Nonnull Map<String, String> config,
                                 @MustNotContainNull @Nonnull
                                 Consumer<Path>... loadedArchiveConsumers) throws IOException {
    final Log log = this.mojo.getLog();

    final HostOs hostOs = HostOs.findHostOs();

    final String apiUrl = GetUtils.ensureNonNull(config.get("apiUrl"), API_BASE_URL);

    final String jdkFeatureVersion = config.get("featureVersion");
    final String jdkReleaseName = config.get("releaseName");
    final String jdkArch = GetUtils.ensureNonNull(config.get("arch"), "x64");
    final String jdkReleaseType = GetUtils.ensureNonNull(config.get("releaseType"), "ga");
    final String jdkHeapSize = GetUtils.ensureNonNull(config.get("heapSize"), "normal");
    final String jdkImageType = GetUtils.ensureNonNull(config.get("imageType"), "jdk");
    final String jdkJvmImpl = GetUtils.ensureNonNull(config.get("jvmImpl"), "hotspot");
    final String jdkOs = GetUtils.ensureNonNull(config.get("os"),
        HostOs.isAlpineLinux() ? "alpine-linux" : hostOs.isMac() ? "mac" : hostOs.getId());
    final String jdkVendor = GetUtils.ensureNonNull(config.get("vendor"), "eclipse");

    final String jdkClib = config.get("cLib");
    final String jdkProject = config.get("project");

    final boolean keepArchiveFile =
        Boolean.parseBoolean(config.getOrDefault("keepArchive", "false"));

    final String cacheJdkFolder = makeCachePath(
        apiUrl,
        jdkFeatureVersion,
        jdkReleaseName,
        jdkReleaseType,
        jdkArch,
        jdkHeapSize,
        jdkImageType,
        jdkJvmImpl,
        jdkOs,
        jdkVendor,
        jdkClib,
        jdkProject
    );

    log.info("Cache folder: " + cacheJdkFolder);

    final Path cacheFolder = this.mojo.findJdkCacheFolder();
    final Path cachedJdkPath = cacheFolder.resolve(cacheJdkFolder);

    if (isDirectory(cachedJdkPath)) {
      log.info("Found cached JDK: " + cachedJdkPath.getFileName());
      return cachedJdkPath;
    }

    final String requestUrl = makeUrl(
        apiUrl,
        jdkFeatureVersion,
        jdkReleaseName,
        jdkReleaseType,
        jdkArch,
        jdkHeapSize,
        jdkImageType,
        jdkJvmImpl,
        jdkOs,
        jdkVendor,
        jdkClib,
        jdkProject
    );

    log.info("Formed url link: " + requestUrl);

    if (isOfflineMode()) {
      throw new FailureException("Unpacked '" + cachedJdkPath.getFileName() +
          "' is not found, stopping process because offline mode is active");
    } else {
      log.info("Can't find cached: " + cachedJdkPath.getFileName());
    }

    this.loadJdkIntoCacheIfNotExist(cacheFolder,
        assertNotNull(cachedJdkPath.getFileName()).toString(), tempFolder ->
            downloadAndUnpack(
                requestUrl,
                makeHttpClient(authorization),
                authorization,
                cacheFolder,
                tempFolder,
                keepArchiveFile,
                loadedArchiveConsumers));

    return cachedJdkPath;
  }


  @Nonnull
  private HttpClient makeHttpClient(@Nullable final String authorization) throws IOException {
    return HttpUtils.makeHttpClient(this.mojo.getLog(), this.mojo.getProxy(),
        this.tuneClient(authorization),
        this.mojo.isDisableSSLcheck());

  }

  @SafeVarargs
  private final void downloadAndUnpack(
      @Nonnull final String url,
      @Nonnull final HttpClient client,
      @Nullable final String authorization,
      @Nonnull final Path tempFolder,
      @Nonnull final Path destinationUnpackedFolder,
      final boolean keepArchiveFile,
      @Nonnull @MustNotContainNull final Consumer<Path>... loadedArchiveConsumers
  ) throws IOException {
    final Log log = this.mojo.getLog();
    final String tempFileName = ".tempAdopt" + UUID.randomUUID();

    final Path pathToArchiveFile = tempFolder.resolve(tempFileName);

    if (isRegularFile(pathToArchiveFile)) {
      log.warn("Deleting found temp file: " + pathToArchiveFile);
      Files.delete(pathToArchiveFile);
    }

    final MessageDigest digest = DigestUtils.getSha256Digest();
    final Header[] responseHeaders = this.doHttpGetIntoFile(
        client,
        this.tuneRequestBase(authorization),
        url,
        pathToArchiveFile,
        Collections.singletonList(digest),
        this.mojo.getConnectionTimeout(),
        "*/*"
    );

    log.debug("Response headers: " + Arrays.toString(responseHeaders));


    for (final Consumer<Path> c : loadedArchiveConsumers) {
      c.accept(pathToArchiveFile);
    }

    if (isDirectory(destinationUnpackedFolder)) {
      log.info("Detected existing target folder, deleting it: " +
          destinationUnpackedFolder.getFileName());
      deleteDirectory(destinationUnpackedFolder.toFile());
    }

    final String archiveRootName = ArchUtils.findShortestDirectory(pathToArchiveFile);
    log.debug("Root archive folder: " + archiveRootName);
    log.info("Unpacking archive...");
    final int numberOfUnpackedFiles =
        unpackArchiveFile(this.mojo.getLog(), true, pathToArchiveFile, destinationUnpackedFolder,
            archiveRootName);
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
