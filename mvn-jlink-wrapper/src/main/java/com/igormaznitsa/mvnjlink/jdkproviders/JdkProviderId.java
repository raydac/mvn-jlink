package com.igormaznitsa.mvnjlink.jdkproviders;

import com.igormaznitsa.mvnjlink.AbstractJlinkMojo;
import com.igormaznitsa.mvnjlink.jdkproviders.providers.AdoptOpenJdkProvider;
import com.igormaznitsa.mvnjlink.jdkproviders.providers.MavenJdkProvider;

import javax.annotation.Nonnull;
import java.lang.reflect.InvocationTargetException;

public enum JdkProviderId {
  ADOPT(AdoptOpenJdkProvider.class),
  MAVEN(MavenJdkProvider.class);

  @Nonnull
  private final Class<? extends AbstractJdkProvider> implementation;

  JdkProviderId(@Nonnull final Class<? extends AbstractJdkProvider> implementation) {
    this.implementation = implementation;
  }

  @Nonnull
  public AbstractJdkProvider makeInstance(@Nonnull final AbstractJlinkMojo mojo) {
    try {
      return this.implementation.getDeclaredConstructor(AbstractJlinkMojo.class).newInstance(mojo);
    } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException | InstantiationException e) {
      throw new Error("Unexpected error, can't create instance of JDK provider", e);
    }
  }
}
