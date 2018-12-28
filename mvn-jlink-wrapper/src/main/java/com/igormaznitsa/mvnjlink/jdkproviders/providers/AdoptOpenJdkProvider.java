package com.igormaznitsa.mvnjlink.jdkproviders.providers;

import com.igormaznitsa.meta.annotation.MustNotContainNull;
import com.igormaznitsa.mvnjlink.exceptions.FailureException;
import com.igormaznitsa.mvnjlink.exceptions.IORuntimeWrapperException;
import com.igormaznitsa.mvnjlink.jdkproviders.AbstractJdkProvider;
import com.igormaznitsa.mvnjlink.mojos.AbstractJlinkMojo;
import com.igormaznitsa.mvnjlink.utils.StringUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.maven.plugin.logging.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.igormaznitsa.mvnjlink.utils.ArchUtils.unpackArchiveFile;
import static com.igormaznitsa.mvnjlink.utils.HttpUtils.doGetRequest;
import static com.igormaznitsa.mvnjlink.utils.HttpUtils.makeHttpClient;
import static java.nio.file.Files.*;
import static java.util.Locale.ENGLISH;
import static java.util.regex.Pattern.compile;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Stream.of;
import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.apache.commons.io.IOUtils.copy;

public class AdoptOpenJdkProvider extends AbstractJdkProvider {
  private static final Pattern RELEASE = compile("^([a-z]+)-?([0-9.]+)(.*)$");

  public AdoptOpenJdkProvider(@Nonnull final AbstractJlinkMojo mojo) {
    super(mojo);
  }

  @Nonnull
  private static String calcSha256ForFile(@Nonnull final Path file) throws IOException {
    try (final InputStream in = newInputStream(file)) {
      return sha256Hex(in);
    }
  }

  private static void assertAttributes(@Nonnull final Map<String, String> attrMap, @Nonnull @MustNotContainNull final String... attributes) {
    final Optional<String> notFoundAttribute = of(attributes).filter(x -> !attrMap.containsKey(x)).findAny();
    if (notFoundAttribute.isPresent()) {
      throw new IllegalArgumentException(String.format("Attribute '%s' must be presented", notFoundAttribute.get()));
    }
  }

  @Nonnull
  @Override
  public Path prepareJdkFolder(@Nonnull final Map<String, String> config) throws IOException {
    final Log log = this.mojo.getLog();

    assertAttributes(config, "release", "os", "arch", "type", "impl");

    final String jdkRelease = config.get("release");
    final String jdkOs = config.get("os");
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

    log.info("looking for '" + cachedJdkFolderName + "' in cache");
    final Path cachePath = this.mojo.findJdkCacheFolder();

    final Path cachedJdkFolderPath = cachePath.resolve(cachedJdkFolderName);
    log.info("Cache folder: " + cachePath);

    if (isDirectory(cachedJdkFolderPath)) {
      log.info("Found cached JDK: " + cachedJdkFolderName);
      return cachedJdkFolderPath;
    } else {
      if (isOfflineMode()) {
        throw new FailureException("Unpacked JDK (" + cachedJdkFolderName + ") is not found, stopping process because offline mode is active");
      } else {
        log.info("Can't find cached sdk: " + cachedJdkFolderName);
      }

      final String adoptApiUri;
      if (jdkReleaseListUrl == null) {
        final Matcher matcher = RELEASE.matcher(jdkRelease.trim().toLowerCase(ENGLISH));
        if (matcher.find()) {
          final String jdkVersion = matcher.group(2);
          final int dotIndex = jdkVersion.indexOf('.');
          log.debug("Extracted JDK version " + jdkVersion + " from " + jdkRelease);
          adoptApiUri = "https://api.adoptopenjdk.net/v2/info/releases/openjdk" + (dotIndex < 0 ? jdkVersion : jdkVersion.substring(0, dotIndex));
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
        }, "application/json");
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
      final ReleaseList.Release foundRelease = releaseList.findRelease(jdkRelease);

      if (foundRelease == null) {
        log.error("Can't find release : " + jdkRelease);
        log.error("List of JDKs\n---------------------\n" + releaseList.makeListOfAllReleases());
        throw new IOException("Can't find appropriate JDK release in provided list for '" + jdkRelease + '\'');
      } else {
        log.debug("Found release for name : " + jdkRelease);
      }

      final ReleaseList.Release.Binary foundReleaseBinary = releaseList.findBinary(foundRelease, jdkOs, jdkArch, jdkType, jdkImpl);

      if (foundReleaseBinary == null) {
        log.error("Can't find release binary : " + jdkRelease);
        log.error(releaseList.makeListOfAllReleases());
        throw new IOException("Can't find appropriate JDK release binary in provided list for '" + jdkRelease + '\'');
      } else {
        log.debug("Found release binary: " + foundReleaseBinary);
        downloadAndUnpack(httpClient, foundReleaseBinary, cachePath, cachedJdkFolderPath, foundRelease.releaseName, keepArchiveFile);
      }
    }
    return cachedJdkFolderPath;
  }

  private void downloadAndUnpack(
      @Nonnull final HttpClient client,
      @Nonnull final ReleaseList.Release.Binary binary,
      @Nonnull final Path cacheFolder,
      @Nonnull final Path targetFolder,
      @Nonnull final String archiveRootFolder,
      final boolean keepArchiveFile
  ) throws IOException {

    final Log log = this.mojo.getLog();

    final String digestCode;
    if (!binary.linkHash.isEmpty()) {
      final AtomicReference<String> hashRef = new AtomicReference<>();
      try {
        doGetRequest(client, binary.linkHash, this.mojo.getProxy(), x -> {
          try {
            hashRef.set(EntityUtils.toString(x));
          } catch (IOException ex) {
            throw new IORuntimeWrapperException(ex);
          }
        }, "text/plain");
      } catch (IORuntimeWrapperException ex) {
        throw ex.getWrapped();
      }

      digestCode = StringUtils.extractFileHash(hashRef.get());
      log.info("Expected archive hash: " + digestCode);
    } else {
      log.warn("The Release doesn't have listed hash link");
      digestCode = "";
    }

    final Path downloadArchiveFile = cacheFolder.resolve(binary.binaryName);

    boolean doLoadArchive = true;

    if (isRegularFile(downloadArchiveFile)) {
      log.info("Detected loaded archive: " + downloadArchiveFile.getFileName());

      if (digestCode.isEmpty()) {
        log.warn("Because digest is undefined, the existing archive will be deleted!");
        if (!Files.deleteIfExists(downloadArchiveFile)) {
          throw new IOException("Detected cached archive '" + downloadArchiveFile.getFileName() + "', which can't be deleted");
        }
      } else if (!digestCode.equalsIgnoreCase(calcSha256ForFile(downloadArchiveFile))) {
        log.warn("Calculated hash for found archive is wrong, the archive will be reloaded!");
        delete(downloadArchiveFile);
      } else {
        log.info("Found archive hash is OK");
        doLoadArchive = false;
      }
    }

    if (doLoadArchive) {
      log.info(String.format("Loading archive, %d kB : %s", binary.size / 1024L, binary.link));

      final String archiveHash = getArchiveAndSave(client, binary.link, downloadArchiveFile);

      log.info("Archive has been loaded successfuly, hash is " + archiveHash);

      if (!digestCode.isEmpty()) {
        if (digestCode.equalsIgnoreCase(archiveHash)) {
          log.info("Hash code of downloaded archive is OK");
        } else {
          throw new IOException("Loaded archive has wrong hash: " + digestCode + " != " + archiveHash);
        }
      } else {
        log.warn("Etalon hash is not provided so hash check is ignored");
      }
    } else {
      log.info("Archive loading is skipped");
    }

    if (isDirectory(targetFolder)) {
      log.info("Detected existing target folder, deleting it: " + targetFolder);
      deleteDirectory(targetFolder.toFile());
    }

    final int numberOfUnpackedFiles = unpackArchiveFile(this.mojo.getLog(), downloadArchiveFile, targetFolder, archiveRootFolder);
    if (numberOfUnpackedFiles == 0) {
      throw new IOException("Extracted 0 files from archive! May be wrong root folder name: " + archiveRootFolder);
    }
    log.info("Archive has been unpacked successfully, extracted " + numberOfUnpackedFiles + " files");

    if (keepArchiveFile) {
      log.info("Keeping archive file in cache: " + downloadArchiveFile);
    } else {
      log.info("Deleting archive: " + downloadArchiveFile);
      delete(downloadArchiveFile);
    }
  }

  @Nonnull
  private String getArchiveAndSave(@Nonnull final HttpClient client, @Nonnull final String link, @Nonnull final Path file) throws IOException {
    try {
      doGetRequest(client, link, this.mojo.getProxy(), httpEntity -> {
        try {
          try (final OutputStream fileOutStream = Files.newOutputStream(file)) {
            copy(httpEntity.getContent(), fileOutStream, 128 * 1024);
          }
        } catch (IOException ex) {
          throw new IORuntimeWrapperException(ex);
        }
      }, "application/x-gzip", "application/zip", "application/gzip");
    } catch (IORuntimeWrapperException ex) {
      throw ex.getWrapped();
    }
    return calcSha256ForFile(file);
  }

  private static final class ReleaseList {
    private final List<Release> releases = new ArrayList<>();

    private ReleaseList(@Nonnull JSONArray json) {
      for (int i = 0; i < json.length(); i++) {
        this.releases.add(new Release(json.getJSONObject(i)));
      }
    }

    @Nonnull
    private String makeListOfAllReleases() {
      return this.releases.stream().map(Release::toStringList).flatMap(Collection::stream).collect(joining("\n"));
    }

    @Nullable
    private Release findRelease(@Nonnull final String name) {
      final String normalizedName = name.toLowerCase(ENGLISH);
      final Optional<Release> release = this.releases.stream().filter(x -> x.releaseName.toLowerCase(ENGLISH).startsWith(normalizedName)).findFirst();
      return release.orElse(null);
    }

    @Nullable
    private Release.Binary findBinary(
        @Nonnull final Release release,
        @Nonnull final String os,
        @Nonnull final String arch,
        @Nonnull final String type,
        @Nonnull final String impl) {

      final Optional<Release.Binary> binary = release.binaries.stream()
          .filter(x -> x.os.equalsIgnoreCase(os))
          .filter(x -> x.arch.equalsIgnoreCase(arch))
          .filter(x -> x.type.equalsIgnoreCase(type))
          .filter(x -> x.impl.equalsIgnoreCase(impl))
          .findFirst();

      return binary.orElse(null);
    }

    private static final class Release {

      private final String releaseName;
      private final List<Binary> binaries = new ArrayList<>();

      private Release(@Nonnull JSONObject json) {
        this.releaseName = json.getString("release_name");

        final JSONArray binariesArray = json.getJSONArray("binaries");
        for (int i = 0; i < binariesArray.length(); i++) {
          binaries.add(new Binary(binariesArray.getJSONObject(i)));
        }
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
