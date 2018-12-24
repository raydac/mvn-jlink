package com.igormaznitsa.mvnjlink.jdkproviders.providers;

import com.igormaznitsa.mvnjlink.mojos.AbstractJlinkMojo;
import com.igormaznitsa.mvnjlink.jdkproviders.AbstractJdkProvider;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;

public class AdoptOpenJdkProvider extends AbstractJdkProvider {
  public AdoptOpenJdkProvider(@Nonnull final AbstractJlinkMojo mojo) {
    super(mojo);
  }

  @Override
  public File prepareJdkFolder() throws IOException {
    throw new Error();
  }
}
