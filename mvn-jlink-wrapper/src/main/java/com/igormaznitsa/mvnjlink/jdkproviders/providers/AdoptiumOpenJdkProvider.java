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

import static com.igormaznitsa.meta.common.utils.Assertions.assertNotNull;
import static com.igormaznitsa.mvnjlink.utils.ArchUtils.unpackArchiveFile;
import static com.igormaznitsa.mvnjlink.utils.StringUtils.escapeFileName;
import static java.nio.file.Files.delete;
import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.isRegularFile;
import static java.util.Locale.ENGLISH;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.of;
import static org.apache.commons.io.FileUtils.deleteDirectory;

import com.igormaznitsa.meta.annotation.MustNotContainNull;
import com.igormaznitsa.meta.common.utils.GetUtils;
import com.igormaznitsa.mvnjlink.exceptions.FailureException;
import com.igormaznitsa.mvnjlink.jdkproviders.AbstractJdkProvider;
import com.igormaznitsa.mvnjlink.mojos.AbstractJdkToolMojo;
import com.igormaznitsa.mvnjlink.utils.ArchUtils;
import com.igormaznitsa.mvnjlink.utils.HostOs;
import com.igormaznitsa.mvnjlink.utils.HttpUtils;
import com.igormaznitsa.mvnjlink.utils.WildCardMatcher;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.codec.digest.MessageDigestAlgorithms;
import org.apache.http.Header;
import org.apache.http.client.HttpClient;
import org.apache.maven.plugin.logging.Log;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Provider of prebuilt Adopt OpenJDK archives <a href="https://github.com/adoptium">in Git repository</a>
 */
public class AdoptiumOpenJdkProvider extends AbstractJdkProvider {

  private static final String RELEASES_LIST_TEMPLATE =
      "https://api.github.com/repos/adoptium/%s/releases";

  public AdoptiumOpenJdkProvider(@Nonnull final AbstractJdkToolMojo mojo) {
    super(mojo);
  }

  @Nonnull
  private static String getRawVersion(@Nonnull final String version) {
    final StringBuilder result = new StringBuilder();
    for (final char c : version.trim().toCharArray()) {
      if (Character.isDigit(c)) {
        result.append(c);
      } else {
        break;
      }
    }
    return result.toString();
  }

  @Nonnull
  @Override
  public Path getPathToJdk(
      @Nullable final String authorization,
      @Nonnull final Map<String, String> config,
      @Nonnull @MustNotContainNull final Consumer<Path>... loadedArchiveConsumers
  ) throws IOException {
    final Log log = this.mojo.getLog();

    assertParameters(config, "version", "arch", "type", "impl", "build");

    final HostOs defaultOs = findCurrentOs(HostOs.LINUX);

    log.debug("Default OS recognized as: " + defaultOs);

    final String jdkVersion = config.get("version").toUpperCase(ENGLISH);
    final String gitRepositoryName =
        config.getOrDefault("repositoryName", "temurin" + getRawVersion(jdkVersion) + "-binaries");
    final String build = config.get("build");
    final String jdkOs =
        GetUtils.ensureNonNull(config.get("os"), defaultOs.isMac() ? "mac" : defaultOs.getId());
    final String jdkArch = config.get("arch");
    final String jdkType = config.get("type");
    final String jdkImpl = config.get("impl");
    final boolean checkArchive = Boolean.parseBoolean(config.getOrDefault("check", "true"));
    final int perPage = ensurePageSizeValue(config.getOrDefault("perPage", "40"));
    final boolean keepArchiveFile =
        Boolean.parseBoolean(config.getOrDefault("keepArchive", "false"));

    final String cachedJdkFolderName = String.format(
        "ADOPTIUM_%s_%s_%s_%s_%s",
        escapeFileName(jdkVersion.toLowerCase(ENGLISH).trim()),
        escapeFileName(build.toLowerCase(ENGLISH).trim()),
        escapeFileName(jdkOs.toLowerCase(ENGLISH).trim()),
        escapeFileName(jdkArch.toLowerCase(ENGLISH).trim()),
        escapeFileName(jdkImpl.toLowerCase(ENGLISH).trim())
    );

    log.info("looking for '" + cachedJdkFolderName + "' in the cache folder");

    final Path cacheFolder = this.mojo.findJdkCacheFolder();
    final Path cachedJdkPath = cacheFolder.resolve(cachedJdkFolderName);
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

      log.info("Source GIT repository: " + gitRepositoryName);

      final HttpClient httpClient =
          HttpUtils.makeHttpClient(log, this.mojo.getProxy(), this.tuneClient(authorization),
              this.mojo.isDisableSSLcheck());

      final ReleaseList releaseList = new ReleaseList();
      List<ReleaseList.Release> releases = Collections.emptyList();

      int page = 1;
      while (!Thread.currentThread().isInterrupted()) {
        log.debug("Loading releases page: " + page);

        final String pageUrl =
            String.format(RELEASES_LIST_TEMPLATE, gitRepositoryName) + "?per_page=" + perPage +
                "&page=" + page;
        log.debug("Page url: " + pageUrl);
        final ReleaseList pageReleases = new ReleaseList(log,
            doHttpGetText(httpClient, this.tuneRequestBase(authorization), pageUrl,
                this.mojo.getConnectionTimeout(), "application/vnd.github.v3+json"));
        releaseList.add(pageReleases);
        releases = releaseList.find(jdkVersion, jdkType, jdkArch, jdkOs, jdkImpl, build);

        if (!releases.isEmpty() || pageReleases.isEmpty()) {
          break;
        }

        page++;
      }

      if (releases.isEmpty()) {
        log.warn("Found releases\n" + releaseList.makeReport());
        throw new IOException(
            String.format("Can't find release for version='%s', type='%s', os='%s', arch='%s'",
                jdkVersion, jdkType, jdkOs, jdkArch));
      } else {
        log.debug("Found releases: " + releases);

        final Optional<ReleaseList.Release> tarRelease =
            releases.stream().filter(x -> "tar.gz".equalsIgnoreCase(x.extension)).findFirst();
        final Optional<ReleaseList.Release> zipRelease =
            releases.stream().filter(x -> "zip".equalsIgnoreCase(x.extension)).findFirst();

        final ReleaseList.Release releaseToLoad =
            of(tarRelease, zipRelease).filter(Optional::isPresent).findFirst().get().get();
        result = loadJdkIntoCacheIfNotExist(cacheFolder,
            assertNotNull(cachedJdkPath.getFileName()).toString(), tempFolder ->
                downloadAndUnpack(httpClient, authorization, cacheFolder, tempFolder, releaseToLoad,
                    checkArchive, keepArchiveFile, loadedArchiveConsumers)
        );
      }
    }
    return result;
  }

  private void downloadAndUnpack(
      @Nonnull final HttpClient client,
      @Nullable final String authorization,
      @Nonnull final Path tempFolder,
      @Nonnull final Path destUnpackFolder,
      @Nonnull final ReleaseList.Release release,
      final boolean check,
      final boolean keepArchiveFile,
      @Nonnull @MustNotContainNull final Consumer<Path>... loadedArchiveConsumers
  ) throws IOException {

    final Log log = this.mojo.getLog();

    final Path pathToArchiveFile = tempFolder.resolve(release.fileName);

    boolean doLoadArchive = true;

    if (isRegularFile(pathToArchiveFile)) {
      log.info("Detected loaded archive: " + pathToArchiveFile.getFileName());
      doLoadArchive = false;
    }

    if (doLoadArchive) {
      final MessageDigest digest = DigestUtils.getSha256Digest();
      final Header[] responseHeaders = this.doHttpGetIntoFile(
          client,
          this.tuneRequestBase(authorization),
          release.link,
          pathToArchiveFile,
          Collections.singletonList(digest),
          this.mojo.getConnectionTimeout(),
          release.mime
      );

      log.debug("Response headers: " + Arrays.toString(responseHeaders));

      if (check) {
        final String sha256link = release.link + ".sha256.txt";

        final String sha256text;
        try {
          log.debug("Loading SHA256 text: " + sha256link);
          sha256text = this.doHttpGetText(
              createHttpClient(authorization),
              this.tuneRequestBase(authorization),
              sha256link,
              mojo.getConnectionTimeout(),
              MIME_TEXT
          ).trim();
        } catch (Exception ex) {
          log.error("Can't find SHA256 for distributive: " + sha256link, ex);
          throw ex;
        }
        final StringBuilder buffer = new StringBuilder();
        for (final char c : sha256text.toCharArray()) {
          if (Character.isDigit(c) || Character.isAlphabetic(c)) {
            buffer.append(c);
          } else {
            break;
          }
        }
        final String sha256signature = buffer.toString();
        log.info("Loaded SHA256 for distributive: " + sha256signature);
        assertChecksum(sha256signature, Collections.singletonList(digest),
            MessageDigestAlgorithms.SHA_256);
      } else {
        log.warn("Archive check skipped");
      }
    } else {
      log.info("Archive loading is skipped");
    }

    for (final Consumer<Path> c : loadedArchiveConsumers) {
      c.accept(pathToArchiveFile);
    }

    if (isDirectory(destUnpackFolder)) {
      log.info("Detected existing target folder, deleting it: " + destUnpackFolder.getFileName());
      deleteDirectory(destUnpackFolder.toFile());
    }

    final String archiveRoorName = ArchUtils.findShortestDirectory(pathToArchiveFile);
    log.debug("Root archive folder: " + archiveRoorName);
    log.info("Unpacking archive...");
    final int numberOfUnpackedFiles =
        unpackArchiveFile(this.mojo.getLog(), true, pathToArchiveFile, destUnpackFolder,
            archiveRoorName);
    if (numberOfUnpackedFiles == 0) {
      throw new IOException(
          "Extracted 0 files from archive! May be wrong root folder name: " + archiveRoorName);
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

  static class ReleaseList {
    private final List<Release> releases = new ArrayList<>();

    private ReleaseList() {
    }

    private ReleaseList(@Nonnull final Log log, @Nonnull final String json) {
      final JSONArray array = new JSONArray(json);
      for (int i = 0; i < array.length(); i++) {
        final JSONObject release = array.getJSONObject(i);
        if (!release.has("tag_name")) {
          continue;
        }

        final boolean draft = release.getBoolean("draft");
        if (draft) {
          continue;
        }
        if (release.has("assets")) {
          final JSONArray assets = release.getJSONArray("assets");
          for (int a = 0; a < assets.length(); a++) {
            final JSONObject asset = assets.getJSONObject(a);
            final String fileName = asset.getString("name");
            final String mime = asset.getString("content_type");
            final long size = asset.getLong("size");

            if (fileName.endsWith(".zip") || fileName.endsWith(".tar.gz")) {
              final String link = asset.getString("browser_download_url");
              try {
                final Release newRelease = new Release(fileName, link, mime, size);
                this.releases.add(newRelease);
                log.debug("Added found release: " + newRelease);
              } catch (IllegalArgumentException ex) {
                log.debug("Ignoring for non-standard package name: " + fileName);
              }
            } else {
              log.debug("Ignoring because becuae non-archive: " + asset);
            }
          }
        }
      }
    }

    public void add(@Nonnull final ReleaseList list) {
      this.releases.addAll(list.releases);
    }

    public boolean isEmpty() {
      return this.releases.isEmpty();
    }

    @Nonnull
    @MustNotContainNull
    public List<Release> find(@Nonnull final String version,
                              @Nonnull final String type,
                              @Nonnull final String arch,
                              @Nonnull final String os,
                              @Nonnull final String impl,
                              @Nonnull final String build) {
      final WildCardMatcher matcher = new WildCardMatcher(build, true);
      return this.releases.stream()
          .filter(x -> x.version.equalsIgnoreCase(version))
          .filter(x -> x.type.equalsIgnoreCase(type))
          .filter(x -> x.arch.equalsIgnoreCase(arch))
          .filter(x -> x.os.equalsIgnoreCase(os))
          .filter(x -> x.impl.equalsIgnoreCase(impl))
          .filter(x -> matcher.match(x.build)).collect(toList());
    }

    @Nonnull
    public String makeReport() {
      return this.releases.stream().map(Release::toString).collect(joining("\n"));
    }

    static class Release {

      static final Pattern ADOPTGIT_FILENAME_PATTERN = Pattern.compile(
          "^OpenJDK([\\da-z]*)-([a-z]+)_([0-9a-z\\-]+)_([0-9a-z\\-]+)_([a-z0-9]+)_([\\-a-zA-Z0-9]+|\\d[\\d._]+\\d(?![a-z\\-])).([.a-z0-9]+)$",
          Pattern.CASE_INSENSITIVE);

      private final String version;
      private final String type;
      private final String arch;
      private final String os;
      private final String impl;
      private final String build;
      private final String fileName;
      private final String link;
      private final String mime;
      private final String extension;
      private final long size;

      private Release(
          @Nonnull final String fileName,
          @Nonnull final String link,
          @Nonnull final String mime,
          final long size
      ) {
        this.fileName = fileName;
        this.link = link;
        this.mime = mime;
        this.size = size;
        final Matcher matcher = ADOPTGIT_FILENAME_PATTERN.matcher(fileName);
        if (matcher.find()) {
          this.version = matcher.group(1);
          this.type = matcher.group(2);
          this.arch = matcher.group(3);
          this.os = matcher.group(4);
          this.impl = matcher.group(5);
          this.build = matcher.group(6);
          this.extension = matcher.group(7);
        } else {
          throw new IllegalArgumentException("Can't parse file name: " + fileName);
        }
      }

      @Nonnull
      @Override
      public String toString() {
        return String.format(
            "Release[version='%s',type='%s',arch='%s',os='%s',impl='%s',build='%s',extension='%s']",
            this.version,
            this.type,
            this.arch,
            this.os,
            this.impl,
            this.build,
            this.extension);
      }
    }
  }

}
