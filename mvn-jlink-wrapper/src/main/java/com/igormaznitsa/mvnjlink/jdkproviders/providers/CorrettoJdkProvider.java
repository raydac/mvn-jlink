package com.igormaznitsa.mvnjlink.jdkproviders.providers;

import com.igormaznitsa.meta.annotation.MustNotContainNull;
import com.igormaznitsa.mvnjlink.mojos.AbstractJdkToolMojo;
import com.igormaznitsa.mvnjlink.utils.HostOs;
import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.igormaznitsa.mvnjlink.utils.HttpUtils.MIME_ALL;

/**
 * Amazon Corretto JDK Provider.
 * Builds download URLs like:
 * <pre>https://corretto.aws/latest/amazon-corretto-[version]-[arch]-[os]-[type].[ext]</pre>
 *
 */
public class CorrettoJdkProvider extends UrlLinkJdkProvider {

  public static final String DOWNLOAD_PATH = "latest";
  public static final String CHECKSUM_PATH = "latest_sha256";

  public CorrettoJdkProvider(@Nonnull final AbstractJdkToolMojo mojo) {
    super(mojo);
  }

  private static final String TEMPLATE_FILE_NAME = "amazon-corretto-%s-%s-%s-%s"; // version, arch, os, type
  private static final String TEMPLATE_FILE_URL = "https://corretto.aws/downloads/%s/%s";

  @Nonnull
  private String findExtensionForHost(@Nullable final HostOs currentOs) {
    if (currentOs == HostOs.WINDOWS) {
      return "zip";
    }
    return "tar.gz";
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
    final String jdkFeatureVersion = config.getOrDefault("featureVersion", "17");
    final String jdkArch = config.getOrDefault("arch", "x64"); // x64 or aarch64
    final String jdkOs = config.getOrDefault("os", hostOs.isMac() ? "macos" : hostOs.getId()); // linux or windows or macos or alpine or al (Amazon Linux) or al2023 (Amawon Linux 2023)
    final String imageType = config.getOrDefault("imageType", "jdk"); // jdk or jre
    final String extension = config.getOrDefault("extension", findExtensionForHost(hostOs));

    final String baseArchiveName = config.getOrDefault("file", String.format(TEMPLATE_FILE_NAME, jdkFeatureVersion, jdkArch, jdkOs, imageType));
    final String archiveFileName = baseArchiveName + (extension.isEmpty() ? "" : '.' + extension);

    log.info("Corretto archive name: " + archiveFileName);

    final String urlArchive = String.format(TEMPLATE_FILE_URL, DOWNLOAD_PATH, archiveFileName);

    // SHA256: if user provided sha256, use it. Otherwise, try latest_checksum endpoint if requested.
    String sha256 = config.get("sha256");
    if (sha256 == null) {
      // Corretto exposes per-file checksum by simply changing the path to latest_sha256
      final String shaUrl = String.format(TEMPLATE_FILE_URL, CHECKSUM_PATH, archiveFileName);
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
