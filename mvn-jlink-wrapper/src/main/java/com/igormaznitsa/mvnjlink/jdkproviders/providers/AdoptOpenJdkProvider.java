package com.igormaznitsa.mvnjlink.jdkproviders.providers;

import com.igormaznitsa.mvnjlink.AbstractJlinkMojo;
import com.igormaznitsa.mvnjlink.jdkproviders.AbstractJdkProvider;

import javax.annotation.Nonnull;

public class AdoptOpenJdkProvider extends AbstractJdkProvider {
  public AdoptOpenJdkProvider(@Nonnull final AbstractJlinkMojo mojo) {
    super(mojo);
  }
}
