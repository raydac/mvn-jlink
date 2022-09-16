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

package com.igormaznitsa.mvnjlink.jdkproviders.providers;

import static com.igormaznitsa.mvnjlink.utils.ArchUtils.unpackArchiveFile;
import static com.igormaznitsa.mvnjlink.utils.StringUtils.escapeFileName;
import static java.nio.file.Files.delete;
import static java.nio.file.Files.isDirectory;
import static java.util.Collections.singletonList;
import static java.util.Locale.ENGLISH;
import static java.util.stream.Stream.of;
import static org.apache.commons.io.FileUtils.deleteDirectory;

import com.igormaznitsa.meta.annotation.MustNotContainNull;
import com.igormaznitsa.meta.common.utils.GetUtils;
import com.igormaznitsa.mvnjlink.exceptions.FailureException;
import com.igormaznitsa.mvnjlink.jdkproviders.AbstractJdkProvider;
import com.igormaznitsa.mvnjlink.mojos.AbstractJdkToolMojo;
import com.igormaznitsa.mvnjlink.utils.ArchUtils;
import com.igormaznitsa.mvnjlink.utils.HttpUtils;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.Header;
import org.apache.http.client.HttpClient;
import org.apache.maven.plugin.logging.Log;

/**
 * Provider of prebuilt OpenJDK archives from <a href="https://adoptopenjdk.net/">AdoptOpenJDK</a>
 */
public class AdoptOpenJdkProvider extends AbstractJdkProvider {
  private static final String BASEURL = "https://api.adoptopenjdk.net/v3";

  public AdoptOpenJdkProvider(@Nonnull final AbstractJdkToolMojo mojo) {
    super(mojo);
  }

  @Nonnull
  private static String toUrl(@Nonnull final String str) {
    try {
      return URLEncoder.encode(str, "UTF-8");
    } catch (UnsupportedEncodingException ex) {
      throw new Error(ex);
    }
  }

  @Nonnull
  @Override
  public Path getPathToJdk(
      @Nullable final String authorization,
      @Nonnull final Map<String, String> config,
      @Nonnull @MustNotContainNull final Consumer<Path>... loadedArchiveConsumers
  ) throws IOException {
    final Log log = this.mojo.getLog();

    assertParameters(config, "release", "arch");

    final String defaultOs = findCurrentOs("mac");

    log.debug("Default OS recognized as: " + defaultOs);

    final String jdkRelease = config.get("release");
    final String jdkArch = config.get("arch");
    final String jdkImpl = config.getOrDefault("impl", "hotspot");
    final String jdkOs = GetUtils.ensureNonNull(config.get("os"), defaultOs);
    final String jdkImageType = config.getOrDefault("type", "jdk");
    final String jdkReleaseType = config.getOrDefault("releaseType", "ga");
    final String jdkHeapSize = config.getOrDefault("heapSize", "normal");
    final String jdkVendor = config.getOrDefault("vendor", "adoptopenjdk");
    final String jdkProject = config.getOrDefault("project", "");

    final boolean keepArchiveFile = Boolean.parseBoolean(config.getOrDefault("keepArchive", "false"));

    final String cachedJdkFolderName = String.format(
        "ADOPT_%s_%s_%s_%s_%s_%s_%s_%s_%s",
        escapeFileName(jdkRelease.toLowerCase(ENGLISH).trim()),
        escapeFileName(jdkOs.toLowerCase(ENGLISH).trim()),
        escapeFileName(jdkArch.toLowerCase(ENGLISH).trim()),
        escapeFileName(jdkImpl.toLowerCase(ENGLISH).trim()),
        escapeFileName(jdkImageType.toLowerCase(ENGLISH).trim()),
        escapeFileName(jdkReleaseType.toLowerCase(ENGLISH).trim()),
        escapeFileName(jdkHeapSize.toLowerCase(ENGLISH).trim()),
        escapeFileName(jdkVendor.toLowerCase(ENGLISH).trim()),
        escapeFileName(jdkProject.toLowerCase(ENGLISH).trim())
    );

    log.info("looking for '" + cachedJdkFolderName + "' in the cache folder");

    final Path cacheFolder = this.mojo.findJdkCacheFolder();
    final Path cachedJdkPath = cacheFolder.resolve(cachedJdkFolderName);

    Path result;

    if (isDirectory(cachedJdkPath)) {
      log.info("Found cached JDK: " + cachedJdkFolderName);
      result = cachedJdkPath;
    } else {
      if (isOfflineMode()) {
        throw new FailureException("Unpacked JDK (" + cachedJdkFolderName + ") is not found, stopping process because offline mode is active");
      } else {
        log.info("Can't find cached JDK: " + cachedJdkFolderName);
      }

      try {
        Integer.parseInt(jdkRelease);
        result = loadFeaturedVersion(
            authorization,
            cacheFolder,
            cachedJdkPath,
            jdkRelease,
            jdkReleaseType,
            jdkOs,
            jdkArch,
            jdkImageType,
            jdkImpl,
            jdkHeapSize,
            jdkVendor,
            jdkProject,
            keepArchiveFile,
            loadedArchiveConsumers
        );
      } catch (NumberFormatException ex) {
        result = loadReleaseVersion(
            authorization,
            cacheFolder,
            cachedJdkPath,
            jdkRelease,
            jdkOs,
            jdkArch,
            jdkImageType,
            jdkImpl,
            jdkHeapSize,
            jdkVendor,
            jdkProject,
            keepArchiveFile,
            loadedArchiveConsumers
        );
      }
    }

    return result;
  }

  @Nonnull
  private Path loadFeaturedVersion(
      @Nullable final String authorization,
      @Nonnull final Path workFolder,
      @Nonnull final Path unpackFolder,
      @Nonnull final String version,
      @Nonnull final String releaseType,
      @Nonnull final String os,
      @Nonnull final String arch,
      @Nonnull final String imageType,
      @Nonnull final String jvmImpl,
      @Nonnull final String heapSize,
      @Nonnull final String vendor,
      @Nullable final String project,
      final boolean keepArchive,
      @Nonnull @MustNotContainNull final Consumer<Path>... loadedArchiveConsumers
  ) throws IOException {

    this.mojo.getLog().debug("Loading featured version");

    String url = "/binary/latest"
        + '/' + toUrl(version)
        + '/' + toUrl(releaseType)
        + '/' + toUrl(os)
        + '/' + toUrl(arch)
        + '/' + toUrl(imageType)
        + '/' + toUrl(jvmImpl)
        + '/' + toUrl(heapSize)
        + '/' + toUrl(vendor);

    if (project != null && !project.isEmpty()) {
      url += "?project=" + toUrl(project);
    }

    final HttpClient httpClient = HttpUtils.makeHttpClient(
        this.mojo.getLog(),
        this.mojo.getProxy(),
        this.tuneClient(authorization),
        this.mojo.isDisableSSLcheck());

    downloadAndUnpack(BASEURL + url, httpClient, authorization, workFolder, unpackFolder,
        keepArchive, loadedArchiveConsumers);
    return unpackFolder;
  }

  @Nonnull
  private Path loadReleaseVersion(
      @Nullable final String authorization,
      @Nonnull final Path workFolder,
      @Nonnull final Path unpackFolder,
      @Nonnull final String releaseName,
      @Nonnull final String os,
      @Nonnull final String arch,
      @Nonnull final String imageType,
      @Nonnull final String jvmImpl,
      @Nonnull final String heapSize,
      @Nonnull final String vendor,
      @Nullable final String project,
      final boolean keepArchive,
      @Nonnull @MustNotContainNull final Consumer<Path>... loadedArchiveConsumers)
      throws IOException {

    this.mojo.getLog().debug("Loading release version");

    String url = "/binary/version"
        + '/' + toUrl(releaseName)
        + '/' + toUrl(os)
        + '/' + toUrl(arch)
        + '/' + toUrl(imageType)
        + '/' + toUrl(jvmImpl)
        + '/' + toUrl(heapSize)
        + '/' + toUrl(vendor);

    if (project != null && !project.isEmpty()) {
      url += "?project=" + toUrl(project);
    }

    final HttpClient httpClient = HttpUtils.makeHttpClient(
        this.mojo.getLog(),
        this.mojo.getProxy(),
        this.tuneClient(authorization),
        this.mojo.isDisableSSLcheck());

    downloadAndUnpack(BASEURL + url, httpClient, authorization, workFolder, unpackFolder,
        keepArchive, loadedArchiveConsumers);
    return unpackFolder;
  }

  private void downloadAndUnpack(
      @Nonnull final String url,
      @Nonnull final HttpClient client,
      @Nullable final String authorization,
      @Nonnull final Path tempFolder,
      @Nonnull final Path destUnpackFolder,
      final boolean keepArchiveFile,
      @Nonnull @MustNotContainNull final Consumer<Path>... loadedArchiveConsumers
  ) throws IOException {

    final Log log = this.mojo.getLog();

    log.debug("Formed API uri is " + url);

    Path pathToArchiveFile = tempFolder.resolve("." + destUnpackFolder.getFileName().toString() + ".arch");
    if (Files.isRegularFile(pathToArchiveFile, LinkOption.NOFOLLOW_LINKS) && !Files.deleteIfExists(pathToArchiveFile)) {
      throw new IOException("Can't delete archive: " + pathToArchiveFile);
    }

    final MessageDigest digest = DigestUtils.getMd5Digest();
    final Header[] responseHeaders = this.doHttpGetIntoFile(
        client,
        this.tuneRequestBase(authorization),
        url,
        pathToArchiveFile,
        singletonList(digest),
        this.mojo.getConnectionTimeout(),
        "application/zip",
        "application/octet-stream",
        "application/x-zip-compressed",
        "multipart/x-zip",
        "application/x-gzip",
        "application/x-tar+gzip"
    );

    log.debug("Response headers: " + Arrays.toString(responseHeaders));

    final Optional<Header> originalFileName =
        of(responseHeaders).filter(x -> "Content-Disposition".equalsIgnoreCase(x.getName()))
            .findFirst();

    if (originalFileName.isPresent()) {
      Path newArch = pathToArchiveFile.resolveSibling(originalFileName.get().getValue());
      log.debug("Renaming " + pathToArchiveFile + " -> " + newArch);
      pathToArchiveFile = Files.move(pathToArchiveFile, newArch);
    } else {
      throw new IOException("Can't find Content-Disposition among headers in response");
    }

    for (final Consumer<Path> c : loadedArchiveConsumers) {
      c.accept(pathToArchiveFile);
    }

    final String calculatedMd5Digest = Hex.encodeHexString(digest.digest());

    log.info(
        "Archive has been loaded successfuly, calculated MD5 digest is " + calculatedMd5Digest);

    final Optional<Header> etag =
        of(responseHeaders).filter(x -> "ETag".equalsIgnoreCase(x.getName())).findFirst();

    if (etag.isPresent()) {
      final Matcher matcher = ETAG_PATTERN.matcher(etag.get().getValue());
      if (matcher.find()) {
        final String extractedEtag = matcher.group(1);
        if (calculatedMd5Digest.equalsIgnoreCase(extractedEtag)) {
          log.info("Calculated MD5 is equal to the ETag in response");
        } else {
          log.warn("Calculated MD5 is not equal to the ETag in response: " + calculatedMd5Digest + " != " + extractedEtag);
        }
      } else {
        log.error("Can't extract MD5 from ETag: " + etag.get().getValue());
      }
    } else {
      throw new IOException("ETag is not presented in the response or its value can't be parsed");
    }

    if (isDirectory(destUnpackFolder)) {
      log.info("Detected existing target folder, deleting it: " + destUnpackFolder.getFileName());
      deleteDirectory(destUnpackFolder.toFile());
    }

    final String archiveRoorName = ArchUtils.findShortestDirectory(pathToArchiveFile);
    log.debug("Root archive folder: " + archiveRoorName);
    log.info("Unpacking archive...");
    final int numberOfUnpackedFiles = unpackArchiveFile(this.mojo.getLog(), true, pathToArchiveFile, destUnpackFolder, archiveRoorName);
    if (numberOfUnpackedFiles == 0) {
      throw new IOException("Extracted 0 files from archive! May be wrong root folder name: " + archiveRoorName);
    }
    log.info("Archive has been unpacked successfully, extracted " + numberOfUnpackedFiles + " files");

    if (keepArchiveFile) {
      log.info("Keep downloaded archive file in cache: " + pathToArchiveFile);
    } else {
      log.info("Deleting archive: " + pathToArchiveFile);
      delete(pathToArchiveFile);
    }
  }


}
