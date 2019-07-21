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

import com.igormaznitsa.meta.annotation.MustNotContainNull;
import com.igormaznitsa.meta.common.utils.GetUtils;
import com.igormaznitsa.mvnjlink.exceptions.FailureException;
import com.igormaznitsa.mvnjlink.exceptions.IORuntimeWrapperException;
import com.igormaznitsa.mvnjlink.jdkproviders.AbstractJdkProvider;
import com.igormaznitsa.mvnjlink.mojos.AbstractJdkToolMojo;
import com.igormaznitsa.mvnjlink.utils.ArchUtils;
import com.igormaznitsa.mvnjlink.utils.StringUtils;
import com.igormaznitsa.mvnjlink.utils.WildCardMatcher;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.maven.plugin.logging.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.igormaznitsa.mvnjlink.utils.ArchUtils.unpackArchiveFile;
import com.igormaznitsa.mvnjlink.utils.HttpUtils;
import static com.igormaznitsa.mvnjlink.utils.HttpUtils.doGetRequest;
import static com.igormaznitsa.mvnjlink.utils.HttpUtils.makeHttpClient;
import static com.igormaznitsa.mvnjlink.utils.StringUtils.escapeFileName;
import static java.nio.file.Files.*;
import static java.util.Locale.ENGLISH;
import static java.util.regex.Pattern.compile;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.io.FileUtils.deleteDirectory;

/**
 * Provider of prebuilt OpenJDK archives from <a href="https://adoptopenjdk.net/">AdoptOpenJDK</a>
 */
public class AdoptOpenJdkProvider extends AbstractJdkProvider {
  private static final Pattern RELEASE = compile("^([a-z]+)-?([0-9.]+)(.*)$");
  private static final String BASEURL_OPENJDK_RELEASES = "https://api.adoptopenjdk.net/v2/info/releases/";

  public AdoptOpenJdkProvider(@Nonnull final AbstractJdkToolMojo mojo) {
    super(mojo);
  }

  @Nonnull
  @Override
  public Path getPathToJdk(@Nonnull final Map<String, String> config) throws IOException {
    final Log log = this.mojo.getLog();

    assertParameters(config, "release", "arch", "type", "impl");

    final String defaultOs = findCurrentOs("mac");

    log.debug("Default OS recognized as: " + defaultOs);

    final String jdkRelease = config.get("release");
    final String jdkOs = GetUtils.ensureNonNull(config.get("os"), defaultOs);
    final String jdkArch = config.get("arch");
    final String jdkType = config.get("type");
    final String jdkImpl = config.get("impl");
    final String jdkReleaseListUrl = config.get("releaseListUrl");
    final boolean keepArchiveFile = Boolean.parseBoolean(config.getOrDefault("keepArchive", "false"));

    final String cachedJdkFolderName = String.format(
        "ADOPT_%s_%s_%s_%s",
        escapeFileName(jdkRelease.toLowerCase(ENGLISH).trim()),
        escapeFileName(jdkOs.toLowerCase(ENGLISH).trim()),
        escapeFileName(jdkArch.toLowerCase(ENGLISH).trim()),
        escapeFileName(jdkImpl.toLowerCase(ENGLISH).trim())
    );

    log.info("looking for '" + cachedJdkFolderName + "' in the cache folder");

    final Path cacheFolder = this.mojo.findJdkCacheFolder();
    final Path cachedJdkPath = cacheFolder.resolve(cachedJdkFolderName);
    final Path result;

    if (isDirectory(cachedJdkPath)) {
      log.info("Found cached JDK: " + cachedJdkFolderName);
      result = cachedJdkPath;
    } else {
      if (isOfflineMode()) {
        throw new FailureException("Unpacked JDK (" + cachedJdkFolderName + ") is not found, stopping process because offline mode is active");
      } else {
        log.info("Can't find cached JDK: " + cachedJdkFolderName);
      }

      final String adoptApiUri;
      if (jdkReleaseListUrl == null) {
        final Matcher matcher = RELEASE.matcher(jdkRelease.trim().toLowerCase(ENGLISH));
        if (matcher.find()) {
          final String jdkVersion = matcher.group(2);
          final int dotIndex = jdkVersion.indexOf('.');
          log.debug("Extracted JDK version " + jdkVersion + " from " + jdkRelease);
          adoptApiUri = BASEURL_OPENJDK_RELEASES + "openjdk" + (dotIndex < 0 ? jdkVersion : jdkVersion.substring(0, dotIndex));
        } else {
          throw new IOException("Can't parse 'release' attribute, may be incorrect format: " + jdkRelease);
        }
      } else {
        adoptApiUri = jdkReleaseListUrl;
      }

      log.debug("Adopt list OpenJdk API URL: " + adoptApiUri);
      final HttpClient httpClient = makeHttpClient(log, this.mojo.getProxy(), this.mojo.isDisableSSLcheck());
      final AtomicReference<String> text = new AtomicReference<>();

      try {
        doGetRequest(httpClient, adoptApiUri, this.mojo.getProxy(), x -> {
          try {
            text.set(EntityUtils.toString(x));
          } catch (IOException ex) {
            throw new IORuntimeWrapperException(ex);
          }
        }, this.mojo.getConnectionTimeout(), this.isAllowOctetStream(), "application/json");
      } catch (IORuntimeWrapperException ex) {
        throw ex.getWrapped();
      }

      final JSONArray jsonReleaseArray;
      try {
        jsonReleaseArray = new JSONArray(text.get());
      } catch (JSONException ex) {
        log.error(text.get());
        throw new IOException("Can't parse JSON file for list of releases", ex);
      }

      final ReleaseList releaseList = new ReleaseList(jsonReleaseArray);
      final ReleaseList.Release.Binary foundReleaseBinary = releaseList.findBinary(jdkRelease, jdkOs, jdkArch, jdkType, jdkImpl);

      if (foundReleaseBinary == null) {
        log.error(String.format("Can't find appropriate JDK binary, check list of allowed releases : %s [os='%s',arch='%s',type='%s',impl='%s']", jdkRelease, jdkOs, jdkArch, jdkType, jdkImpl));
        log.error(releaseList.makeListOfAllReleases());
        throw new IOException(String.format("Can't find appropriate JDK binary: %s [os='%s',arch='%s',type='%s',impl='%s']", jdkRelease, jdkOs, jdkArch, jdkType, jdkImpl));
      } else {
        log.debug("Found release binary: " + foundReleaseBinary);

        result = loadJdkIntoCacheIfNotExist(cacheFolder, cachedJdkFolderName, tempFolder ->
            downloadAndUnpack(httpClient, foundReleaseBinary, cacheFolder, tempFolder, keepArchiveFile)
        );
      }
    }
    return result;
  }

  private void downloadAndUnpack(
      @Nonnull final HttpClient client,
      @Nonnull final ReleaseList.Release.Binary binary,
      @Nonnull final Path workFolder,
      @Nonnull final Path destUnpackFolder,
      final boolean keepArchiveFile
  ) throws IOException {

    final Log log = this.mojo.getLog();

    final String digestCode;
    if (!binary.linkHash.isEmpty()) {
      digestCode = StringUtils.extractFileHash(doHttpGetText(client, binary.linkHash, this.mojo.getConnectionTimeout(), HttpUtils.MIME_OCTET_STREAM, "text/plain"));
      log.info("Expected archive SHA256 digest: " + digestCode);
    } else {
      log.warn("The Release doesn't have listed hash link");
      digestCode = "";
    }

    final Path archiveFile = workFolder.resolve(binary.binaryName);

    boolean doLoadArchive = true;

    if (isRegularFile(archiveFile)) {
      log.info("Detected archive: " + archiveFile.getFileName());

      if (digestCode.isEmpty()) {
        log.warn("Because digest is undefined, archive will be deleted!");

        if (!Files.deleteIfExists(archiveFile)) {
          throw new IOException("Detected archive '" + archiveFile.getFileName() + "', which can't be deleted");
        }
      } else if (!digestCode.equalsIgnoreCase(calcSha256ForFile(archiveFile))) {
        log.warn("Calculated digest for found archive is wrong, the archive will be reloaded!");
        delete(archiveFile);
      } else {
        log.info("Found archive hash is OK");
        doLoadArchive = false;
      }
    }

    if (doLoadArchive) {
      final MessageDigest digest = DigestUtils.getSha256Digest();
      doHttpGetIntoFile(client, binary.link, archiveFile, digest, this.mojo.getConnectionTimeout());
      final String calculatedStreamDigest = Hex.encodeHexString(digest.digest());

      log.info("Archive has been loaded successfuly, calculated SHA256 digest is " + calculatedStreamDigest);

      if (digestCode.isEmpty()) {
        log.warn("Don't check hash because etalon hash is not provided by host");
      } else {
        if (digestCode.equalsIgnoreCase(calculatedStreamDigest)) {
          log.info("Calculated digest is equal to the provided digest");
        } else {
          throw new IOException("Calculated digest is not equal to the provided digest: " + digestCode + " != " + calculatedStreamDigest);
        }
      }
    } else {
      log.info("Archive loading is skipped");
    }

    if (isDirectory(destUnpackFolder)) {
      log.info("Detected existing target folder, deleting it: " + destUnpackFolder.getFileName());
      deleteDirectory(destUnpackFolder.toFile());
    }

    final String rootArchiveFolder = ArchUtils.findShortestDirectory(archiveFile);

    if (rootArchiveFolder == null) {
      log.error("Can't find root folder in downloaded archive: " + archiveFile);
    } else {
      log.info("Detected archive root folder: " + rootArchiveFolder);
    }

    log.info("Unpacking archive...");
    final int numberOfUnpackedFiles = unpackArchiveFile(this.mojo.getLog(), true, archiveFile, destUnpackFolder, rootArchiveFolder);
    if (numberOfUnpackedFiles == 0) {
      throw new IOException("Extracted 0 files from archive! May be wrong root folder name: " + rootArchiveFolder);
    }
    log.debug(String.format("Unpacked %d files into %s", numberOfUnpackedFiles, destUnpackFolder.toString()));
    log.info(String.format("Unpacked %d files into %s", numberOfUnpackedFiles, destUnpackFolder.getFileName()));

    if (keepArchiveFile) {
      log.info("Keep downloaded archive file in cache: " + archiveFile);
    } else {
      log.info("Deleting archive: " + archiveFile);
      delete(archiveFile);
    }
  }

  private static final class ReleaseList {
    private final List<Release> releases = new ArrayList<>();

    private ReleaseList(@Nonnull JSONArray json) {
      for (int i = 0; i < json.length(); i++) {
        this.releases.add(new Release(json.getJSONObject(i)));
      }
      Collections.sort(this.releases);
    }

    @Nonnull
    private String makeListOfAllReleases() {
      return this.releases.stream().map(Release::toStringList).flatMap(Collection::stream).collect(joining("\n"));
    }

    @Nullable
    private Release.Binary findBinary(
        @Nonnull final String name,
        @Nullable final String jdkOs,
        @Nullable final String jdkArch,
        @Nullable final String jdkType,
        @Nullable final String jdkImpl
    ) {
      final WildCardMatcher wildCardMatcher = new WildCardMatcher(name, true);

      return this.releases.stream().filter(x -> wildCardMatcher.match(x.releaseName)).flatMap(x -> x.binaries.stream())
          .filter(x -> jdkOs == null || jdkOs.equalsIgnoreCase(x.os))
          .filter(x -> jdkArch == null || jdkArch.equalsIgnoreCase(x.arch))
          .filter(x -> jdkType == null || jdkType.equalsIgnoreCase(x.type))
          .filter(x -> jdkImpl == null || jdkImpl.equalsIgnoreCase(x.impl))
          .findFirst().orElse(null);
    }

    private static final class Release implements Comparable<Release> {

      private final String releaseName;
      private final List<Binary> binaries = new ArrayList<>();

      private Release(@Nonnull JSONObject json) {
        this.releaseName = json.getString("release_name");

        final JSONArray binariesArray = json.getJSONArray("binaries");
        for (int i = 0; i < binariesArray.length(); i++) {
          binaries.add(new Binary(binariesArray.getJSONObject(i)));
        }
      }

      @Override
      public boolean equals(@Nullable final Object obj) {
        if (!(obj instanceof Release)) {
          return false;
        }
        final Release release = (Release) obj;
        return Objects.equals(this.releaseName, release.releaseName) &&
            Objects.equals(this.binaries, release.binaries);
      }

      @Override
      public int hashCode() {
        return Objects.hash(this.releaseName, this.binaries);
      }

      @Override
      public int compareTo(@Nonnull final Release release) {
        return release.releaseName.compareTo(this.releaseName);
      }

      @Nonnull
      @Override
      public String toString() {
        return this.releaseName;
      }

      @Nonnull
      @MustNotContainNull
      private List<String> toStringList() {
        final List<String> result = new ArrayList<>();
        this.binaries.forEach(x -> result.add(this.releaseName + " [" + x.toString() + ']'));
        return result;
      }

      private static final class Binary {
        private final String os;
        private final String arch;
        private final String type;
        private final String impl;
        private final String binaryName;
        private final long size;
        private final String link;
        private final String linkHash;

        private Binary(@Nonnull final JSONObject json) {
          try {
            this.os = json.getString("os");
            this.arch = json.getString("architecture");
            this.type = json.getString("binary_type");
            this.binaryName = json.getString("binary_name");
            this.impl = json.getString("openjdk_impl");
            this.size = json.getLong("binary_size");
            this.link = json.getString("binary_link");
            this.linkHash = json.has("checksum_link") ? json.getString("checksum_link") : "";
          } catch (JSONException ex) {
            throw new FailureException("Can't get expected value: " + json, ex);
          }
        }

        @Nonnull
        @Override
        public String toString() {
          return String.format("os='%s',arch='%s',type='%s',impl='%s'", this.os, this.arch, this.type, this.impl);
        }
      }

    }
  }
}
