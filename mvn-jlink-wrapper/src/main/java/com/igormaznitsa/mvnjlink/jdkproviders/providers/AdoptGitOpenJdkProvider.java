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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.Header;
import org.apache.http.client.HttpClient;
import org.apache.maven.plugin.logging.Log;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Provider of prebuilt Adopt OpenJDK archives <a href="https://github.com/AdoptOpenJDK">in Git repository</a>
 */
public class AdoptGitOpenJdkProvider extends AbstractJdkProvider {

  private static final String RELEASES_LIST_TEMPLATE = "https://api.github.com/repos/AdoptOpenJDK/%s/releases";
  private static final Pattern ETAG_PATTERN = Pattern.compile("^\"?([a-fA-F0-9]{32}).*\"?$");

  public AdoptGitOpenJdkProvider(@Nonnull final AbstractJdkToolMojo mojo) {
    super(mojo);
  }

  @Nonnull
  @Override
  public Path getPathToJdk(@Nullable final String authorization, @Nonnull final Map<String, String> config) throws IOException {
    final Log log = this.mojo.getLog();

    assertParameters(config, "repositoryName", "arch", "type", "impl", "releaseDate");

    final String defaultOs = findCurrentOs("macos");

    log.debug("Default OS recognized as: " + defaultOs);

    final String gitRepositoryName = config.get("repositoryName");
    final String jdkVersion = config.getOrDefault("version", "");
    final String releaseDate = config.get("releaseDate");
    final String jdkOs = GetUtils.ensureNonNull(config.get("os"), defaultOs);
    final String jdkArch = config.get("arch");
    final String jdkType = config.get("type");
    final String jdkImpl = config.get("impl");
    final boolean keepArchiveFile = Boolean.parseBoolean(config.getOrDefault("keepArchive", "false"));

    final String cachedJdkFolderName = String.format(
        "ADOPTGIT_%s_%s_%s_%s_%s",
        escapeFileName(jdkVersion.toLowerCase(ENGLISH).trim()),
        escapeFileName(releaseDate.toLowerCase(ENGLISH).trim()),
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
        throw new FailureException("Unpacked '" + cachedJdkPath.getFileName() + "' is not found, stopping process because offline mode is active");
      } else {
        log.info("Can't find cached: " + cachedJdkPath.getFileName());
      }

      final HttpClient httpClient = HttpUtils.makeHttpClient(log, this.mojo.getProxy(), this.tuneClient(authorization), this.mojo.isDisableSSLcheck());

      final ReleaseList releaseList = new ReleaseList();
      List<ReleaseList.Release> releases = Collections.emptyList();

      int page = 1;
      while (!Thread.currentThread().isInterrupted()) {
        log.debug("Loading releases page: " + page);

        final String pageUrl = String.format(RELEASES_LIST_TEMPLATE, gitRepositoryName) + "?per_page=100&page=" + page;
        log.debug("Page url: " + pageUrl);
        final ReleaseList pageReleases = new ReleaseList(log, doHttpGetText(httpClient, this.tuneRequestBase(authorization), pageUrl,
            this.mojo.getConnectionTimeout(), "application/vnd.github.v3+json"));
        releaseList.add(pageReleases);
        releases = releaseList.find(jdkVersion, jdkType, jdkArch, jdkOs, jdkImpl, releaseDate);

        if (!releases.isEmpty() || pageReleases.isEmpty()) {
          break;
        }

        page++;
      }

      if (releases.isEmpty()) {
        log.warn("Found releases\n" + releaseList.makeReport());
        throw new IOException(String.format("Can't find release for version='%s', type='%s', os='%s', arch='%s'", jdkVersion, jdkType, jdkOs, jdkArch));
      } else {
        log.debug("Found releases: " + releases);

        final Optional<ReleaseList.Release> tarRelease = releases.stream().filter(x -> "tar.gz".equalsIgnoreCase(x.extension)).findFirst();
        final Optional<ReleaseList.Release> zipRelease = releases.stream().filter(x -> "zip".equalsIgnoreCase(x.extension)).findFirst();

        final ReleaseList.Release releaseToLoad = of(tarRelease, zipRelease).filter(Optional::isPresent).findFirst().get().get();
        result = loadJdkIntoCacheIfNotExist(cacheFolder, assertNotNull(cachedJdkPath.getFileName()).toString(), tempFolder ->
            downloadAndUnpack(httpClient, authorization, cacheFolder, tempFolder, releaseToLoad, keepArchiveFile)
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
      final boolean keepArchiveFile
  ) throws IOException {

    final Log log = this.mojo.getLog();

    final Path pathToArchiveFile = tempFolder.resolve(release.fileName);

    boolean doLoadArchive = true;

    if (isRegularFile(pathToArchiveFile)) {
      log.info("Detected loaded archive: " + pathToArchiveFile.getFileName());
      doLoadArchive = false;
    }

    if (doLoadArchive) {
      final MessageDigest digest = DigestUtils.getMd5Digest();
      final Header[] responseHeaders = this.doHttpGetIntoFile(client, this.tuneRequestBase(authorization), release.link, pathToArchiveFile, digest, this.mojo.getConnectionTimeout(), release.mime);

      log.debug("Response headers: " + Arrays.toString(responseHeaders));

      final String calculatedMd5Digest = Hex.encodeHexString(digest.digest());

      log.info("Archive has been loaded successfuly, calculated MD5 digest is " + calculatedMd5Digest);

      final Optional<Header> etag = of(responseHeaders).filter(x -> "ETag".equalsIgnoreCase(x.getName())).findFirst();

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
        log.warn("ETag is not presented in the response or its value can't be parsed");
      }

    } else {
      log.info("Archive loading is skipped");
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

  private static class ReleaseList {
    private final List<Release> releases = new ArrayList<>();

    private ReleaseList() {
    }

    public void add(@Nonnull final ReleaseList list) {
      this.releases.addAll(list.releases);
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
                              @Nonnull final String releaseDate) {
      final WildCardMatcher matcher = new WildCardMatcher(releaseDate, true);
      return this.releases.stream()
          .filter(x -> x.version.equalsIgnoreCase(version))
          .filter(x -> x.type.equalsIgnoreCase(type))
          .filter(x -> x.arch.equalsIgnoreCase(arch))
          .filter(x -> x.os.equalsIgnoreCase(os))
          .filter(x -> x.impl.equalsIgnoreCase(impl))
          .filter(x -> matcher.match(x.releaseDate)).collect(toList());
    }

    @Nonnull
    public String makeReport() {
      return this.releases.stream().map(Release::toString).collect(joining("\n"));
    }

    private static class Release {

      private static final Pattern ADOPTGIT_FILENAME_PATTERN = Pattern.compile("^OpenJDK([\\da-z]*)-([a-z]+)_([0-9a-z\\-]+)_([0-9a-z]+)_([0-9a-z_]+)_([\\-a-zA-Z0-9]+).(.+)$", Pattern.CASE_INSENSITIVE);

      private final String version;
      private final String type;
      private final String arch;
      private final String os;
      private final String impl;
      private final String releaseDate;
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
          this.releaseDate = matcher.group(6);
          this.extension = matcher.group(7);
        } else {
          throw new IllegalArgumentException("Can't parse file name: " + fileName);
        }
      }

      @Nonnull
      @Override
      public String toString() {
        return String.format("Release[version='%s',type='%s',arch='%s',os='%s',impl='%s',date='%s',extension='%s']",
            this.version,
            this.type,
            this.arch,
            this.os,
            this.impl,
            this.releaseDate,
            this.extension);
      }
    }
  }

}
