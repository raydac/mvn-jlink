package com.igormaznitsa.mvnjlink.jdkproviders.providers;

import static com.igormaznitsa.mvnjlink.utils.HttpUtils.MIME_ALL;
import static com.igormaznitsa.mvnjlink.utils.StringUtils.mergeUrl;

import com.igormaznitsa.meta.annotation.MustNotContainNull;
import com.igormaznitsa.mvnjlink.mojos.AbstractJdkToolMojo;
import com.igormaznitsa.mvnjlink.utils.HostOs;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.maven.plugin.logging.Log;

/**
 * Amazon Corretto JDK Provider.
 * Builds download URLs like:
 * <pre>https://corretto.aws/latest/amazon-corretto-[version]-[arch]-[os]-[type].[ext]</pre>
 *
 * @since 1.2.5
 */
public class CorrettoJdkProvider extends UrlLinkJdkProvider {

  public static final String DEFAULT_DOWNLOAD_PATH = "latest";
  public static final String DEFAULT_CHECKSUM_PATH = "latest_sha256";

  public CorrettoJdkProvider(@Nonnull final AbstractJdkToolMojo mojo) {
    super(mojo);
  }

  private static final String TEMPLATE_FILE_NAME = "amazon-corretto-%s-%s-%s-%s"; // version, arch, os, type
  private static final String DEFAULT_DOWNLOAD_URL_PREFIX = "https://corretto.aws/downloads/";

  @Nonnull
  private String findExtensionForHost(@Nullable final HostOs currentOs) {
    if (currentOs == HostOs.WINDOWS) {
      return "zip";
    }
    return "tar.gz";
  }

  @Nonnull
  private static String makeCorrettoUrl(
      @Nonnull final String urlPrefix,
      @Nonnull final String path,
      @Nonnull final String fileName
  ) {
    return mergeUrl(urlPrefix, path, fileName);
  }

  @Nonnull
  @Override
  @SafeVarargs
  public final Path getPathToJdk(@Nullable final String authorization,
                                 @Nonnull final Map<String, String> config,
                                 @Nonnull @MustNotContainNull Consumer<Path>... loadedArchiveConsumers
  ) throws IOException {
    final Log log = this.mojo.getLog();
    final HostOs hostOs = findCurrentOs(HostOs.LINUX);

    // Parameters with defaults
    final String downloadUrl = config.getOrDefault("downloadUrl", DEFAULT_DOWNLOAD_URL_PREFIX);
    final String downloadPath = config.getOrDefault("downloadPath", DEFAULT_DOWNLOAD_PATH);
    final String downloadChecksumPath =
        config.getOrDefault("downloadChecksumPath", DEFAULT_CHECKSUM_PATH);
    final String jdkFeatureVersion = config.getOrDefault("featureVersion", "25");
    final String jdkArch = config.getOrDefault("arch", "x64"); // x64 or aarch64
    final String jdkOs = config.getOrDefault("os", hostOs.isMac() ? "macos" : hostOs.getId()); // linux or windows or macos or alpine or al (Amazon Linux) or al2023 (Amawon Linux 2023)
    final String imageType = config.getOrDefault("imageType", "jdk"); // jdk or jre
    final String extension = config.getOrDefault("extension", findExtensionForHost(hostOs));

    final String baseArchiveName =
        config.getOrDefault("file", String.format(TEMPLATE_FILE_NAME, jdkFeatureVersion,
            jdkArch.toLowerCase(Locale.ROOT),
            jdkOs.toLowerCase(Locale.ROOT),
            imageType.toLowerCase(Locale.ROOT)));
    final String archiveFileName = baseArchiveName + (extension.isEmpty() ? "" : '.' + extension);

    log.info("Corretto archive name: " + archiveFileName);

    final String urlArchive = makeCorrettoUrl(downloadUrl, downloadPath, archiveFileName);

    // SHA256: if user provided sha256, use it. Otherwise, try latest_checksum endpoint if requested.
    String sha256 = config.getOrDefault("sha256", "");
    if (sha256.trim().isEmpty()) {
      // Corretto exposes per-file checksum by simply changing the path to latest_sha256
      final String shaUrl = makeCorrettoUrl(downloadUrl, downloadChecksumPath, archiveFileName);
      log.info("Attempt to load SHA256 from: " + shaUrl);
      try {
        final String body = this.doHttpGetText(
            createHttpClient(authorization),
            this.tuneRequestBase(authorization),
            shaUrl,
            this.mojo.getConnectionTimeout(),
            MIME_ALL
        ).trim();
        // the body is expected to be just the hex hash, possibly with spaces/newlines
        final StringBuilder buf = new StringBuilder();
        for (final char c : body.toCharArray()) {
          if (Character.isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) {
            buf.append(c);
          } else if (Character.isWhitespace(c)) {
            if (buf.length() > 0) break; // stop at first whitespace after hash
          } else {
            break;
          }
        }
        if (buf.length() > 0) {
          sha256 = buf.toString();
          log.info("Extracted downloaded SHA256: " + sha256);
        }
      } catch (Exception ex) {
        log.warn("Can't load SHA256 for Corretto from 'latest_checksum': " + ex.getMessage());
      }
    }

    final Map<String, String> newConfig = new HashMap<>();
    newConfig.put("id", baseArchiveName);
    newConfig.put("url", urlArchive);
    newConfig.put("sha256", sha256);

    return super.getPathToJdk(authorization, newConfig, loadedArchiveConsumers);
  }
}
