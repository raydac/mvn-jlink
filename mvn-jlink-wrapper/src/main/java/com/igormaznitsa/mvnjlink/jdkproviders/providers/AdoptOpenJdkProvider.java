package com.igormaznitsa.mvnjlink.jdkproviders.providers;

import com.igormaznitsa.meta.annotation.MustNotContainNull;
import com.igormaznitsa.mvnjlink.jdkproviders.AbstractJdkProvider;
import com.igormaznitsa.mvnjlink.mojos.AbstractJlinkMojo;
import com.igormaznitsa.mvnjlink.utils.HttpUtils;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.maven.plugin.logging.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
import static java.util.Locale.ENGLISH;
import static java.util.regex.Pattern.compile;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.io.IOUtils.copy;

public class AdoptOpenJdkProvider extends AbstractJdkProvider {
  private static final Pattern RELEASE = compile("^([a-z]+)-?([0-9]+)(.*)$");

  public AdoptOpenJdkProvider(@Nonnull final AbstractJlinkMojo mojo) {
    super(mojo);
  }

  @Nonnull
  private static String calcSha256ForFile(@Nonnull final File file) throws IOException {
    try (final FileInputStream in = new FileInputStream(file)) {
      return DigestUtils.sha256Hex(in);
    }
  }

  @Override
  public File findJdkFolder(@Nonnull final Map<String, String> config) throws IOException {
    final Log log = this.mojo.getLog();

    final String release = config.get("release");
    final String os = config.get("os");
    final String arch = config.get("arch");
    final String type = config.get("type");
    final String impl = config.get("impl");
    final String listUrl = config.get("listUrl");

    if (release == null) {
      throw new IOException("'release' attribute is not defined");
    }
    if (os == null) {
      throw new IOException("'os' attribute is not defined");
    }
    if (arch == null) {
      throw new IOException("'arch' attribute is not defined");
    }
    if (type == null) {
      throw new IOException("'type' attribute is not defined");
    }
    if (impl == null) {
      throw new IOException("'impl' attribute is not defined");
    }

    final String jdkFolderName = String.format(
        "ADOPT_%s_%s_%s_%s",
        escapeFileName(release.toLowerCase(ENGLISH).trim()),
        escapeFileName(os.toLowerCase(ENGLISH).trim()),
        escapeFileName(arch.toLowerCase(ENGLISH).trim()),
        escapeFileName(impl.toLowerCase(ENGLISH).trim())
    );

    log.info("looking for '" + jdkFolderName + "' in cache");
    final File cacheFolder = this.mojo.prepareAndGetCacheFolder();

    final File jdkFolder = new File(cacheFolder, jdkFolderName);
    log.info("Cache folder: " + cacheFolder);

    if (jdkFolder.isDirectory()) {
      log.info("Found cached sdk: " + jdkFolderName);
      return jdkFolder;
    } else {
      log.info("Can't find cached sdk: " + jdkFolderName);

      final String adoptApiUri;
      if (listUrl == null) {
        final Matcher matcher = RELEASE.matcher(release.trim().toLowerCase(ENGLISH));
        if (matcher.find()) {
          adoptApiUri = "https://api.adoptopenjdk.net/v2/info/releases/openjdk" + matcher.group(2);
        } else {
          throw new IOException("Can't parse 'release' attribute, may be incorrect format: " + release);
        }
      } else {
        adoptApiUri = listUrl;
      }

      log.debug("Adopt OpenJdk API URL: " + adoptApiUri);
      final HttpClient httpClient = HttpUtils.makeHttpClient(log, this.mojo.getProxy(), this.mojo.isDisableSSLcheck());
      final AtomicReference<String> text = new AtomicReference<>();

      doGetRequest(httpClient, adoptApiUri, this.mojo.getProxy(), x -> {
        try {
          text.set(EntityUtils.toString(x));
        } catch (IOException ex) {
          throw new RuntimeException(ex);
        }
      }, "application/json");

      final JSONArray jsonReleaseArray;
      try {
        jsonReleaseArray = new JSONArray(text.get());
      } catch (JSONException ex) {
        log.error(text.get());
        throw new IOException("Can't parse JSON file for list of releases", ex);
      }

      final ReleaseList releaseList = new ReleaseList(jsonReleaseArray);
      final ReleaseList.Release foundRelease = releaseList.findRelease(release);

      if (foundRelease == null) {
        log.error("Can't find release : " + release);
        log.error(releaseList.makeListOfAllReleases());
        throw new IOException("Can't find appropriate JDK release in provided list for '" + release + '\'');
      } else {
        log.debug("Found release for name : " + release);
      }

      final ReleaseList.Release.Binary foundBinary = releaseList.findBinary(foundRelease, os, arch, type, impl);

      if (foundBinary == null) {
        log.error("Can't find release binary : " + release);
        log.error(releaseList.makeListOfAllReleases());
        throw new IOException("Can't find appropriate JDK release binary in provided list for '" + release + '\'');
      } else {
        log.debug("Found release binary: " + foundBinary);
        downloadAndUnpack(httpClient, foundBinary, cacheFolder, jdkFolder, foundRelease.releaseName);
      }
    }
    return jdkFolder;
  }

  private void downloadAndUnpack(
      @Nonnull final HttpClient client,
      @Nonnull final ReleaseList.Release.Binary binary,
      @Nonnull final File cacheFolder,
      @Nonnull final File targetFolder,
      @Nonnull final String archiveRootFolder
  ) throws IOException {

    final Log log = this.mojo.getLog();

    final String digestCode;
    if (!binary.linkHash.isEmpty()) {
      final AtomicReference<String> hash = new AtomicReference<>();
      try {
        doGetRequest(client, binary.linkHash, this.mojo.getProxy(), x -> {
          try {
            hash.set(EntityUtils.toString(x));
          } catch (IOException ex) {
            throw new RuntimeException(ex);
          }
        }, "text/plain");
      } catch (RuntimeException ex) {
        if (ex.getCause() instanceof IOException) {
          throw (IOException) ex.getCause();
        }
        throw ex;
      }

      final Matcher hashMatcher = compile("([0-9a-fA-F]+)\\s+(.+)").matcher(hash.get());
      if (hashMatcher.find()) {
        digestCode = hashMatcher.group(1);
      } else {
        throw new IOException("Can't parse hash code: " + hash.get());
      }

      log.info("Expected archive hash: " + digestCode);
    } else {
      log.warn("The Release doesn't have listed hash link");
      digestCode = "";
    }

    final File archiveFile = new File(cacheFolder, binary.binaryName);

    boolean doLoadArchive = true;

    if (archiveFile.isFile()) {
      log.info("Detected loaded archive: " + archiveFile.getName());

      if (digestCode.isEmpty()) {
        log.warn("Because digest is undefined, the existing archive will be deleted!");
        if (!archiveFile.delete()) {
          throw new IOException("Detected cached archive '" + archiveFile.getName() + "', which can't be deleted");
        }
      } else if (!digestCode.equalsIgnoreCase(calcSha256ForFile(archiveFile))) {
        log.warn("Calculated hash for found archive is wrong, the archive will be reloaded!");
        if (!archiveFile.delete()) {
          throw new IOException("Detected cached archive '" + archiveFile.getName() + "', which can't be deleted");
        }
      } else {
        log.info("Found archive hash is OK");
        doLoadArchive = false;
      }
    }

    if (doLoadArchive) {
      log.info(String.format("Loading archive, %d kB : %s", binary.size / 1024L, binary.link));

      final String archiveHash = getArchiveAndSave(client, binary.link, archiveFile);

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

    if (targetFolder.isDirectory()) {
      log.info("Detected existing target folder, deleting it: " + targetFolder);
      FileUtils.deleteDirectory(targetFolder);
    }

    final int numberOfUnpackedFiles = unpackArchiveFile(this.mojo.getLog(), archiveFile, targetFolder, archiveRootFolder);
    if (numberOfUnpackedFiles == 0) {
      throw new IOException("Extracted 0 files from archive! May be wrong root folder name: " + archiveRootFolder);
    }
    log.info("Archive has been unpacked, extracted " + numberOfUnpackedFiles + " files");
  }

  @Nonnull
  private String getArchiveAndSave(final HttpClient client, final String link, final File file) throws IOException {
    doGetRequest(client, link, this.mojo.getProxy(), httpEntity -> {
      try {
        try (final FileOutputStream fileOutStream = new FileOutputStream(file)) {
          copy(httpEntity.getContent(), fileOutStream, 128 * 1024);
        }
      } catch (Exception ex) {
        throw new RuntimeException("Error during downloading", ex);
      }
    }, "application/x-gzip", "application/zip", "application/gzip");
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
      public List<String> toStringList() {
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
            throw new RuntimeException("Can't get expected value: " + json, ex);
          }
        }

        @Nonnull
        @Override
        public String toString() {
          final StringBuilder buffer = new StringBuilder();
          buffer.append("os='").append(this.os).append('\'');
          buffer.append(',').append("arch='").append(this.arch).append('\'');
          buffer.append(',').append("type='").append(this.type).append('\'');
          buffer.append(',').append("impl='").append(this.impl).append('\'');
          return buffer.toString();
        }
      }

    }
  }
}
