package com.igormaznitsa.mvnjlink.jdkproviders;

import com.igormaznitsa.mvnjlink.jdkproviders.providers.AdoptOpenJdkProvider;
import com.igormaznitsa.mvnjlink.jdkproviders.providers.BellSwOpenJdkProvider;
import com.igormaznitsa.mvnjlink.jdkproviders.providers.LocalJdkProvider;
import com.igormaznitsa.mvnjlink.mojos.AbstractJdkToolMojo;

import javax.annotation.Nonnull;
import java.lang.reflect.InvocationTargetException;

public enum JdkProviderId {
  ADOPT(AdoptOpenJdkProvider.class),
  BELLSOFT(BellSwOpenJdkProvider.class),
  LOCAL(LocalJdkProvider.class);

  @Nonnull
  private final Class<? extends AbstractJdkProvider> implementation;

  JdkProviderId(@Nonnull final Class<? extends AbstractJdkProvider> implementation) {
    this.implementation = implementation;
  }

  @Nonnull
  public AbstractJdkProvider makeInstance(@Nonnull final AbstractJdkToolMojo mojo) {
    try {
      return this.implementation.getDeclaredConstructor(AbstractJdkToolMojo.class).newInstance(mojo);
    } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException | InstantiationException e) {
      throw new Error("Unexpected error, can't create instance of JDK provider", e);
    }
  }
}
