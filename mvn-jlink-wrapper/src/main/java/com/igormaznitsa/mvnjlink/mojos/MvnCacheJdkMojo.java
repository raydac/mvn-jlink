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

package com.igormaznitsa.mvnjlink.mojos;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import javax.annotation.Nonnull;
import java.nio.file.Path;

/**
 * Allows to doLoad JDK by its provider into cache and save path of cached JDK into project property.
 */
@Mojo(name = "cache-jdk", defaultPhase = LifecyclePhase.INITIALIZE, threadSafe = true)
public class MvnCacheJdkMojo extends AbstractJdkToolMojo {

  /**
   * Name of project property to save path to cached JDK.
   */
  @Parameter(name = "jdkPathProperty", defaultValue = "mvnjlink.cache.jdk.path")
  private String jdkPathProperty = "mvnjlink.cache.jdk.path";

  @Nonnull
  public String getJdkPathProperty() {
    return this.jdkPathProperty;
  }

  @Override
  public void onExecute() throws MojoExecutionException, MojoFailureException {
    final Path path = this.getSourceJdkFolderFromProvider();
    this.getLog().info("cached JDK path: " + path);
    this.getProject().getProperties().setProperty(this.getJdkPathProperty(), path.toString());
    this.getLog().info(String.format("Project property '%s' <= '%s'", this.getJdkPathProperty(), path.toString()));
  }
}
