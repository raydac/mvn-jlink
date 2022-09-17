package com.igormaznitsa.mvnjlink.jdkproviders.providers;

import com.igormaznitsa.meta.annotation.MustNotContainNull;
import com.igormaznitsa.mvnjlink.mojos.AbstractJdkToolMojo;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.maven.plugin.logging.Log;

public class MicrosoftJdkProvider extends UrlLinkJdkProvider {
  public MicrosoftJdkProvider(@Nonnull final AbstractJdkToolMojo mojo) {
    super(mojo);
  }

  private static final String TEMPLATE_FILE_NAME = "microsoft-%s-%s-%s-%s";
  private static final String TEMPLATE_FILE_URL = "https://aka.ms/download-jdk/%s";

  @Nonnull
  private String findAppropriateExtension() {
    final String os = this.findCurrentOs("macos");
    if (os.toLowerCase(Locale.ENGLISH).contains("windows")) {
      return "zip";
    } else {
      return "tar.gz";
    }
  }

  @Nonnull
  @Override
  @SafeVarargs
  public final Path getPathToJdk(@Nullable final String authorization,
                                 @Nonnull final Map<String, String> config,
                                 @Nonnull @MustNotContainNull
                                 Consumer<Path>... loadedArchiveConsumers
  ) throws IOException {
    final Log log = this.mojo.getLog();

    final String jdkType = config.getOrDefault("type", "jdk");
    final String jdkVersion = config.getOrDefault("version", "17.0.4.1");
    final String jdkOs = config.getOrDefault("os", findCurrentOs("macOs"));
    final String jdkArch = config.getOrDefault("arch", "x64");
    final String jdkExtension = config.getOrDefault("extension", this.findAppropriateExtension());
    final String sha256 = config.getOrDefault("sha256", null);

    final String baseArchiveName = config.getOrDefault("file",
        String.format(TEMPLATE_FILE_NAME, jdkType, jdkVersion, jdkOs, jdkArch));

    final String archiveFileName =
        baseArchiveName + (jdkExtension.isEmpty() ? "" : '.' + jdkExtension);

    log.info("Archive name: " + archiveFileName);

    final String shaFile = config.getOrDefault("fileSha256", archiveFileName + ".sha256sum.txt");

    final String urlArchive = String.format(TEMPLATE_FILE_URL, archiveFileName);
    final String urlArchiveSha = String.format(TEMPLATE_FILE_URL, shaFile);

    final String sha256signature;
    if (sha256 == null) {
      log.info("Loading SHA256 signature file: " + urlArchiveSha);
      final String body = this.doHttpGetText(
          createHttpClient(authorization),
          this.tuneRequestBase(authorization),
          urlArchiveSha,
          60000, MIME_TEXT
      ).trim();
      final StringBuilder buffer = new StringBuilder();
      for (final char c : body.toCharArray()) {
        if (Character.isDigit(c) || Character.isAlphabetic(c)) {
          buffer.append(c);
        } else {
          break;
        }
      }
      sha256signature = buffer.toString();
      log.info("Extracted downloaded SHA256: " + sha256signature);
    } else {
      log.info("Use provided SHA256 signature: " + sha256);
      sha256signature = sha256;
    }

    final Map<String, String> newConfig = new HashMap<>();
    newConfig.put("id", baseArchiveName);
    newConfig.put("url", urlArchive);
    newConfig.put("sha256", sha256signature);

    return super.getPathToJdk(authorization, newConfig);
  }

}
