package com.igormaznitsa.mvnjlink.jdkproviders.providers;

import com.igormaznitsa.mvnjlink.jdkproviders.AbstractJdkProvider;
import com.igormaznitsa.mvnjlink.mojos.AbstractJdkToolMojo;
import org.apache.maven.plugin.logging.Log;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static com.igormaznitsa.meta.common.utils.Assertions.assertNotNull;

public class LocalJdkProvider extends AbstractJdkProvider {
  public LocalJdkProvider(@Nonnull final AbstractJdkToolMojo mojo) {
    super(mojo);
  }

  @Nonnull
  @Override
  public Path prepareSourceJdkFolder(@Nonnull final Map<String, String> config) throws IOException {
    final Log log = this.mojo.getLog();

    final String toolPath = this.mojo.findJdkTool("javac");

    if (toolPath == null) {
      log.error("Can't find jlink in the JDK, JDK version must be 9+");
    } else {
      log.debug("Detected jlink path: " + toolPath);
      final Path path = Paths.get(toolPath);
      Path parent = path.getParent();
      if (parent != null && "bin".equals(assertNotNull(parent.getFileName()).toString())) {
        return assertNotNull(parent.getParent());
      }
    }
    throw new IOException("Can't find JDK folder");
  }
}
