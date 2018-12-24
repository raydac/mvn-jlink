package com.igormaznitsa.mvnjlink.jdkproviders.providers;

import com.igormaznitsa.mvnjlink.jdkproviders.AbstractJdkProvider;
import com.igormaznitsa.mvnjlink.mojos.AbstractJlinkMojo;
import com.igormaznitsa.mvnjlink.utils.SystemUtils;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;

public class LocalJdkProvider extends AbstractJdkProvider {
  public LocalJdkProvider(@Nonnull final AbstractJlinkMojo mojo) {
    super(mojo);
  }

  @Nonnull
  @Override
  public File prepareJdkFolder() throws IOException {
    final File javaHome = new File(this.mojo.getJavaHome());
    if (!javaHome.isDirectory()) {
      throw new IOException("Can't find java folder for path: " + javaHome);
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
