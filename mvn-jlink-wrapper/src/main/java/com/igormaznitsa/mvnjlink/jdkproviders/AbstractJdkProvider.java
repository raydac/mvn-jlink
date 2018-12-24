package com.igormaznitsa.mvnjlink.jdkproviders;

import com.igormaznitsa.mvnjlink.mojos.AbstractJlinkMojo;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;

import static com.igormaznitsa.meta.common.utils.Assertions.assertNotNull;
import static java.util.Locale.ENGLISH;

public abstract class AbstractJdkProvider {

  protected final AbstractJlinkMojo mojo;

  public AbstractJdkProvider(@Nonnull final AbstractJlinkMojo mojo) {
    this.mojo = assertNotNull(mojo);
  }

  public static String makeCachedJdkFolderName(
      @Nonnull final JdkProviderId providerId,
      @Nonnull final String releaseName,
      @Nonnull final String type,
      @Nonnull final String os,
      @Nonnull final String arch,
      @Nullable final String impl
  ) {
    return String.format("%s_%s_%s_%s_%s_%s",
        providerId.name(),
        releaseName.toLowerCase(ENGLISH).trim(),
        type.toLowerCase(ENGLISH).trim(),
        os.toLowerCase(ENGLISH).trim(),
        arch.toLowerCase(ENGLISH).trim(),
        (impl == null || impl.trim().isEmpty() ? "unkn" : impl.toLowerCase(ENGLISH).trim())
    );
  }

  public abstract File prepareJdkFolder() throws IOException;
}
