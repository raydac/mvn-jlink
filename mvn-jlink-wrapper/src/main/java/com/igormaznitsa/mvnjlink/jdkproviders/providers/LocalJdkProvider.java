package com.igormaznitsa.mvnjlink.jdkproviders.providers;

import com.igormaznitsa.mvnjlink.jdkproviders.AbstractJdkProvider;
import com.igormaznitsa.mvnjlink.mojos.AbstractJlinkMojo;
import com.igormaznitsa.mvnjlink.utils.SystemUtils;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public class LocalJdkProvider extends AbstractJdkProvider {
  public LocalJdkProvider(@Nonnull final AbstractJlinkMojo mojo) {
    super(mojo);
  }

  @Nonnull
  @Override
  public Path prepareJdkFolder(@Nonnull final Map<String, String> config) throws IOException {
    final Path javaHome = this.mojo.findBaseJdkHomeFolder();
    if (javaHome == null) {
      throw new IOException("Can't find Java defined home folder");
    }

    try {
      SystemUtils.findJdkExecutable(javaHome, "jlink");
    } catch (IOException ex) {
      this.mojo.getLog().error("Can't find jlink in the JDK, JDK version must be 9+");
      throw new IOException("JDK without jlink util detected: " + javaHome);
    }

    return javaHome;
  }
}
