package com.igormaznitsa.mvnjlink.jdkproviders.providers;

import com.igormaznitsa.meta.annotation.MustNotContainNull;
import com.igormaznitsa.meta.common.utils.GetUtils;
import com.igormaznitsa.mvnjlink.exceptions.FailureException;
import com.igormaznitsa.mvnjlink.jdkproviders.AbstractJdkProvider;
import com.igormaznitsa.mvnjlink.mojos.AbstractJdkToolMojo;
import com.igormaznitsa.mvnjlink.utils.HttpUtils;
import com.igormaznitsa.mvnjlink.utils.WildCardMatcher;
import org.apache.http.client.HttpClient;
import org.apache.maven.plugin.logging.Log;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.igormaznitsa.mvnjlink.utils.ArchUtils.unpackArchiveFile;
import static java.nio.file.Files.*;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.io.FileUtils.deleteDirectory;

public class LibericaOpenJdkProvider extends AbstractJdkProvider {

  private static final String RELEASES_LIST = "https://api.github.com/repos/bell-sw/Liberica/releases";

  public LibericaOpenJdkProvider(@Nonnull final AbstractJdkToolMojo mojo) {
    super(mojo);
  }

  @Nonnull
  @Override
  public Path getPathToJdk(@Nonnull final Map<String, String> config) throws IOException {
    final Log log = this.mojo.getLog();

    assertParameters(config, "type", "version", "arch");

    final String defaultOs = findCurrentOs("macos");

    log.debug("Default OS recognized as: " + defaultOs);

    final String jdkType = config.get("type");
    final String jdkVersion = config.get("version");
    final String jdkOs = GetUtils.ensureNonNull(config.get("os"), defaultOs);
    final String jdkArch = config.get("arch");
    final boolean keepArchiveFile = Boolean.parseBoolean(config.getOrDefault("keepArchive", "false"));

    final Path cacheFolder = this.mojo.findJdkCacheFolder();
    final Path cachedJdkPath = cacheFolder.resolve(String.format("LIBERICA_%s%s_%s_%s", jdkType, jdkVersion, jdkOs, jdkArch));

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

      final HttpClient httpClient = HttpUtils.makeHttpClient(log, this.mojo.getProxy(), this.mojo.isDisableSSLcheck());
      final ReleaseList releaseList = new ReleaseList(log, doHttpGetText(httpClient, RELEASES_LIST, "application/vnd.github.v3+json"));

      final List<ReleaseList.Release> releases = releaseList.find(jdkType, jdkVersion, jdkOs, jdkArch);

      if (releases.isEmpty()) {
        log.warn("Found releases\n" + releaseList.makeReport());
        throw new IOException(String.format("Can't find release for version='%s', type='%s', os='%s', arch='%s'", jdkVersion, jdkType, jdkOs, jdkArch));
      } else {

        log.debug("Found releases: " + releases);

        final Optional<ReleaseList.Release> tarRelease = releases.stream().filter(x -> "tar.gz".equalsIgnoreCase(x.extension)).findFirst();
        final Optional<ReleaseList.Release> zipRelease = releases.stream().filter(x -> "zip".equalsIgnoreCase(x.extension)).findFirst();

        final ReleaseList.Release releaseToLoad = tarRelease.isPresent() ? tarRelease.get() : zipRelease.get();
        result = loadJdkIntoCacheIfNotExist(cacheFolder, cachedJdkPath.getFileName().toString(), tempFolder ->
            downloadAndUnpack(httpClient, cacheFolder, tempFolder, releaseToLoad, keepArchiveFile)
        );
      }
    }
    return result;
  }

  private void downloadAndUnpack(
      @Nonnull final HttpClient client,
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
      log.info(String.format("Loading archive, %d kB : %s", release.size / 1024L, release.link));
      final String archiveHash = this.doHttpGetIntoFile(client, release.link, pathToArchiveFile, release.mime);

      log.info("Archive has been loaded successfuly, hash is " + archiveHash);

    } else {
      log.info("Archive loading is skipped");
    }

    if (isDirectory(destUnpackFolder)) {
      log.info("Detected existing target folder, deleting it: " + destUnpackFolder.getFileName());
      deleteDirectory(destUnpackFolder.toFile());
    }

    final String archiveRoorName = "jdk-" + release.version;
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

    private ReleaseList(@Nonnull final Log log, @Nonnull final String json) {
      final JSONArray array = new JSONArray(json);
      for (int i = 0; i < array.length(); i++) {
        final JSONObject release = array.getJSONObject(i);
        if (!release.has("tag_name")) {
          continue;
        }

        final boolean draft = release.getBoolean("draft");
        final boolean prerelease = release.getBoolean("prerelease");
        if (draft || prerelease) {
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
              this.releases.add(new Release(fileName, link, mime, size));
            } else {
              log.debug("Ignoring because non-unpackable file: " + asset);
            }
          }
        }
      }
    }

    @Nonnull
    @MustNotContainNull
    public List<Release> find(@Nonnull final String type, @Nonnull final String version, @Nonnull final String os, @Nonnull final String arch) {
      final WildCardMatcher matcher = new WildCardMatcher(version, true);
      return this.releases.stream()
          .filter(x -> x.type.equalsIgnoreCase(type))
          .filter(x -> x.os.equalsIgnoreCase(os))
          .filter(x -> x.arch.equalsIgnoreCase(arch))
          .filter(x -> matcher.match(x.version)).collect(toList());
    }

    @Nonnull
    public String makeReport() {
      return this.releases.stream().map(Release::toString).collect(joining("\n"));
    }

    private static class Release {

      private static final Pattern BELLSOFT_FILENAME_PATTERN = Pattern.compile("^bellsoft-([a-z]+)([.0-9+]+)-([a-z]+)-([^.]+).(.+)$", Pattern.CASE_INSENSITIVE);

      private final String type;
      private final String version;
      private final String os;
      private final String arch;
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
        final Matcher matcher = BELLSOFT_FILENAME_PATTERN.matcher(fileName);
        if (matcher.find()) {
          this.type = matcher.group(1);
          this.version = matcher.group(2);
          this.os = matcher.group(3);
          this.arch = matcher.group(4);
          this.extension = matcher.group(5);
        } else {
          throw new IllegalArgumentException("Can't parse file name: " + fileName);
        }
      }

      @Nonnull
      @Override
      public String toString() {
        return String.format("Release[type='%s',version='%s',os='%s',arch='%s',ext='%s']", this.type, this.version, this.os, this.arch, this.extension);
      }
    }
  }

}
