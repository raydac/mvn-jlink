/*
 * Copyright 2019 Igor Maznitsa.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.igormaznitsa.mvnjlink.jdkproviders;

import com.igormaznitsa.mvnjlink.jdkproviders.providers.AdoptGitOpenJdkProvider;
import com.igormaznitsa.mvnjlink.jdkproviders.providers.AdoptOpenJdkProvider;
import com.igormaznitsa.mvnjlink.jdkproviders.providers.GraalVmCeJdkProvider;
import com.igormaznitsa.mvnjlink.jdkproviders.providers.LibericaOpenJdkProvider;
import com.igormaznitsa.mvnjlink.jdkproviders.providers.LocalJdkProvider;
import com.igormaznitsa.mvnjlink.jdkproviders.providers.SapmachineOpenJdkProvider;
import com.igormaznitsa.mvnjlink.jdkproviders.providers.UrlLinkJdkProvider;
import com.igormaznitsa.mvnjlink.mojos.AbstractJdkToolMojo;
import java.lang.reflect.InvocationTargetException;
import javax.annotation.Nonnull;

public enum JdkProviderId {
  ADOPT(AdoptOpenJdkProvider.class),
  ADOPTGIT(AdoptGitOpenJdkProvider.class),
  BELLSOFT(LibericaOpenJdkProvider.class),
  SAPMACHINE(SapmachineOpenJdkProvider.class),
  GRAALVMCE(GraalVmCeJdkProvider.class),
  URL(UrlLinkJdkProvider.class),
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
