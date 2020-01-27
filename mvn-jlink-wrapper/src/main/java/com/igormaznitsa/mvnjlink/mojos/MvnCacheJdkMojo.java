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

import static java.nio.file.Files.walk;
import static java.util.stream.Collectors.toMap;


import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.SelectorUtils;

/**
 * Allows to download JDK from provider into cache and save path of cached JDK folder into project property.
 */
@Mojo(name = "cache-jdk", defaultPhase = LifecyclePhase.INITIALIZE, threadSafe = true)
public class MvnCacheJdkMojo extends AbstractJdkToolMojo {

  /**
   * Name of project property to save path to cached JDK.
   */
  @Parameter(name = "jdkPathProperty", defaultValue = "mvnjlink.cache.jdk.path")
  private String jdkPathProperty = "mvnjlink.cache.jdk.path";

  /**
   * Find file paths in JDK root and place found ones as project properties.
   * Tag name is used as property name (it will be added into project properties) and value contains ANT match pattern.
   * If any file for pattern is not found then mojo execution failed
   *
   * @since 1.0.6
   */
  @Parameter(name = "pathAsProperty")
  private Map<String, String> pathAsProperty;

  @Nonnull
  public String getJdkPathProperty() {
    return this.jdkPathProperty;
  }

  @Override
  public void onExecute() throws MojoExecutionException, MojoFailureException {
    final Path jdkPath = this.getSourceJdkFolderFromProvider();
    this.getLog().info("cached JDK path: " + jdkPath);
    this.getProject().getProperties().setProperty(this.getJdkPathProperty(), jdkPath.toString());
    this.getLog().info(String.format("Project property '%s' <= '%s'", this.getJdkPathProperty(), jdkPath.toString()));

    if (this.pathAsProperty != null && !this.pathAsProperty.isEmpty()) {
      final Map<String, Path> found = findForPatterns(jdkPath, this.pathAsProperty);
      if (found.size() < this.pathAsProperty.size()) {
        for (final Map.Entry<String, String> f : pathAsProperty.entrySet()) {
          if (!found.containsKey(f.getKey())) {
            this.getLog().error("Can't find any file for pattern: " + f.getKey());
          }
        }
        throw new MojoExecutionException("Can't find some files in JDK for path patterns, see log!");
      }
      for (final Map.Entry<String, Path> f : found.entrySet()) {
        this.getLog().info(String.format("Project property '%s' <= '%s' (pattern: %s)", f.getKey(), jdkPath.relativize(f.getValue()).toString(), this.pathAsProperty.get(f.getKey())));
        this.getProject().getProperties().setProperty(f.getKey(), f.getValue().toAbsolutePath().toString());
      }
    }
  }

  @Nonnull
  private Map<String, Path> findForPatterns(@Nonnull final Path rootFolder, @Nonnull final Map<String, String> patterns) throws MojoExecutionException {
    final Map<String, Path> result = new HashMap<>();
    final Map<String, String> normalized = patterns.entrySet().stream()
        .collect(toMap(Map.Entry::getKey,
            Map.Entry::getValue));

    try (Stream<Path> fileWalker = walk(rootFolder)) {
      fileWalker.forEach(x -> {
        for (final Map.Entry<String, String> e : normalized.entrySet()) {
          final String propertyName = e.getKey();
          final String pattern = e.getValue();
          final Path absolutePath = rootFolder.resolve(x).toAbsolutePath();
          if (SelectorUtils.match(pattern, absolutePath.toString())) {
            result.put(propertyName, x);
            break;
          }
        }
      });
    } catch (IOException ex) {
      throw new MojoExecutionException("IO Error during JDK folder walk", ex);
    }

    return result;
  }
}
