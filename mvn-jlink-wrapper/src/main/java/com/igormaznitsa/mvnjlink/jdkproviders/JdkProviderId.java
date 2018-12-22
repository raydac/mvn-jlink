package com.igormaznitsa.mvnjlink.jdkproviders;

import com.igormaznitsa.mvnjlink.jdkproviders.providers.AdoptOpenJdkProvider;
import com.igormaznitsa.mvnjlink.jdkproviders.providers.LocalJdkProvider;

import javax.annotation.Nonnull;
import java.lang.reflect.InvocationTargetException;

public enum JdkProviderId {
  ADOPT(AdoptOpenJdkProvider.class),
  LOCAL(LocalJdkProvider.class);

  @Nonnull
  private final Class<? extends AbstractJdkProvider> implementation;

  JdkProviderId(@Nonnull final Class<? extends AbstractJdkProvider> implementation) {
    this.implementation = implementation;
  }

  @Nonnull
  public AbstractJdkProvider makeInstance() {
    try {
      return this.implementation.getDeclaredConstructor().newInstance();
    }catch(InvocationTargetException | NoSuchMethodException | IllegalAccessException | InstantiationException e) {
      throw new RuntimeException("Can't create instance of JDK provider", e);
    }
  }
}
