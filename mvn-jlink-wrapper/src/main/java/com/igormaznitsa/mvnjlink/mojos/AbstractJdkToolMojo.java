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

import com.igormaznitsa.mvnjlink.exceptions.FailureException;
import com.igormaznitsa.mvnjlink.jdkproviders.JdkProviderId;
import com.igormaznitsa.mvnjlink.utils.ProxySettings;
import com.igormaznitsa.mvnjlink.utils.SystemUtils;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.ToolchainManager;

public abstract class AbstractJdkToolMojo extends AbstractMojo {
  /**
   * internal cache for JDK tool paths.
   */
  private final Map<String, String> toolPathCache = new HashMap<>();

  /**
   * Current maven project.
   */
  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  /**
   * Disable loading and use only cached JDKs.
   * Can be overridden by property 'mvn.jlink.use.only.cache'
   */
  @Parameter(defaultValue = "false", name = "useOnlyCache")
  private boolean useOnlyCache;

  /**
   * Path to JDK cache folder.
   * Can be overridden by property 'mvn.jlink.jdk.cache.path'
   */
  @Parameter(defaultValue = "${user.home}${file.separator}.mvnJlinkCache", name = "jdkCachePath")
  private String jdkCachePath = System.getProperty("user.home") + File.separator + ".mvnJlinkJdkCache";

  /**
   * Current maven session.
   */
  @Parameter(defaultValue = "${session}", readonly = true, required = true)
  private MavenSession session;

  /**
   * Skip processing of the mojo.
   * Can be overridden by property 'mvn.jlink.skip'
   */
  @Parameter(name = "skip", defaultValue = "false")
  private boolean skip;

  /**
   * Text to be used in authorization field during HTTP requests.
   *
   * @since 1.0.8
   */
  @Parameter(name = "authorization")
  private String authorization;

  /**
   * Disable SSL check during network operations.
   * Can be overridden by 'mvn.jlink.disable.ssl.check'
   */
  @Parameter(name = "disableSSLcheck", defaultValue = "false")
  private boolean disableSSLcheck;

  /**
   * Define connection timeout for HTTP requests in milliseconds.
   * Can be overridden by property 'mvn.jlink.connection.timeout'
   *
   * @since 1.0.2
   */
  @Parameter(name = "connectionTimeout", defaultValue = "60000")
  private int connectionTimeout = 60000;

  /**
   * Proxy settings for network operations.
   */
  @Parameter(name = "proxy")
  private ProxySettings proxy;

  /**
   * JDK provider.
   */
  @Parameter(name = "provider", defaultValue = "LOCAL")
  private JdkProviderId provider = JdkProviderId.LOCAL;

  /**
   * Configuration for selected JDL provider.
   */
  @Parameter(name = "providerConfig")
  private Map<String, String> providerConfig = new HashMap<>();

  /**
   * Path to JDK which tools will be used.
   * Can be overridden by property 'mvn.jlink.tool.jdk'
   */
  @Parameter(name = "toolJdk")
  private String toolJdk = null;

  @Component
  private ToolchainManager toolchainManager;

  public int getConnectionTimeout() {
    return Integer.parseInt(Objects.requireNonNull(findProperty("mvn.jlink.connection.timeout",
        Integer.toString(this.connectionTimeout))));
  }

  @Nullable
  public String getToolJdk() {
    return this.findProperty("mvn.jlink.tool.jdk", this.toolJdk);
  }

  /**
   * Check that only cache must be used without network operations.
   *
   * @return true if only cache, false otherwise
   */
  public boolean isUseOnlyCache() {
    return Boolean.parseBoolean(
        this.findProperty("mvn.jlink.use.only.cache",
            Boolean.toString(this.useOnlyCache)));
  }

  @Nonnull
  public Map<String, String> getProviderConfig() {
    return this.providerConfig;
  }

  @Nonnull
  public String getJdkCachePath() {
    return Objects.requireNonNull(this.findProperty("mvn.jlink.jdk.cache.path", this.jdkCachePath));
  }

  @Nonnull
  public JdkProviderId getProvider() {
    return this.provider;
  }

  @Nullable
  public ProxySettings getProxy() {
    return this.proxy;
  }

  /**
   * Check that SSL certificate check should be skipped.
   *
   * @return true if should be skipped, false otherwise
   */
  public boolean isDisableSSLcheck() {
    return Boolean.parseBoolean(
        this.findProperty("mvn.jlink.disable.ssl.check",
            Boolean.toString(this.disableSSLcheck)));
  }

  @Nullable
  public String getAuthorization() {
    return this.findProperty("mvn.jlink.authorization", this.authorization);
  }

  public boolean isSkip() {
    return Boolean.parseBoolean(this.findProperty("mvn.jlink.skip", Boolean.toString(this.skip)));
  }

  @Nonnull
  public MavenProject getProject() {
    return this.project;
  }

  @Override
  public final void execute() throws MojoExecutionException, MojoFailureException {
    if (isSkip()) {
      this.getLog().info("Skipping execution");
    } else {
      onExecute();
    }
  }

  public boolean isOfflineModeActive() {
    return this.isUseOnlyCache() || this.getSession().isOffline();
  }

  @Nonnull
  public MavenSession getSession() {
    return this.session;
  }

  @Nonnull
  public Path findJdkCacheFolder() throws IOException {
    final String storeFolder = this.getJdkCachePath();

    if (storeFolder.trim().isEmpty()) {
      throw new IOException("Path to the cache folder is not provided");
    }

    final Path result = Paths.get(storeFolder);

    if (!result.toFile().isDirectory()) {
      this.getLog().info("Creating cache folder: " + result);
      Files.createDirectories(result);
    }

    if (!Files.isReadable(result)) {
      throw new IOException("Can't read from the cache folder, check rights: " + result);
    }

    if (!Files.isWritable(result)) {
      throw new IOException("Can't write to the cache folder, check rights: " + result);
    }
    return result;
  }

  @Nonnull
  protected Path getSourceJdkFolderFromProvider() throws MojoExecutionException, MojoFailureException {
    try {
      return this.getProvider().makeInstance(this).getPathToJdk(this.getAuthorization(), this.getProviderConfig());
    } catch (IOException ex) {
      throw new MojoExecutionException("Provider can't prepare JDK folder, see log for errors!", ex);
    } catch (FailureException ex) {
      throw new MojoFailureException(ex.getMessage());
    }
  }

  @Nullable
  public String findJdkTool(@Nonnull final String toolName) {
    final Log log = this.getLog();

    String toolPath = this.toolPathCache.get(toolName);

    if (toolPath == null) {
      log.debug("toolJdk = " + this.getToolJdk());
      if (this.getToolJdk() == null) {
        final Toolchain toolchain = getToolchain();
        log.debug("Toolchain: " + toolchain);
        if (toolchain == null) {
          final String mavenJavaHome = System.getProperty("java.home");
          log.debug("Maven java.home: " + mavenJavaHome);
          final Path path = SystemUtils.findJdkExecutable(log, Paths.get(mavenJavaHome), SystemUtils.ensureOsExtension(toolName));
          toolPath = path == null ? null : path.toString();
        } else {
          log.debug("Detected toolchain: " + toolchain);
          toolPath = SystemUtils.ensureOsExtension(toolchain.findTool(toolName));
        }
      } else {
        final Path jdkHome = Paths.get(this.getToolJdk());
        if (jdkHome.toFile().isDirectory()) {
          log.debug("Tool base JDK home: " + jdkHome);
          final Path foundPath = SystemUtils.findJdkExecutable(this.getLog(), jdkHome, toolName);
          toolPath = foundPath == null ? null : foundPath.toString();
        } else {
          log.error("Can't find directory: " + jdkHome);
        }
      }
      if (toolPath != null) {
        log.debug("Caching path for tool '" + toolName + "' -> " + toolPath);
        this.toolPathCache.put(toolName, toolPath);
      }
    } else {
      log.debug("Detected cached path for tool '" + toolName + "' -> " + toolPath);
    }
    return toolPath;
  }

  @Nullable
  protected Toolchain getToolchain() {
    Toolchain result = null;
    if (this.toolchainManager != null) {
      result = this.toolchainManager.getToolchainFromBuildContext("jdk", this.session);

      if (result == null) {
        try {
          final Method getToolchainsMethod = this.toolchainManager.getClass().getMethod("getToolchains", MavenSession.class, String.class, Map.class);

          @SuppressWarnings("unchecked") final List<Toolchain> toolchainList = (List<Toolchain>) getToolchainsMethod.invoke(this.toolchainManager, this.session, "jdk", Collections.singletonMap("version", "[1.8,)"));

          if (toolchainList != null && !toolchainList.isEmpty()) {
            result = toolchainList.get(toolchainList.size() - 1);
          }
        } catch (Exception ex) {
          this.getLog().debug("Exception during getToolchain()", ex);
        }
      }
    }

    return result;
  }


  public abstract void onExecute() throws MojoExecutionException, MojoFailureException;

  @Nullable
  public String findProperty(
      @Nonnull final String key,
      @Nullable final String dflt
  ) {
    final Properties properties = new Properties();
    properties.putAll(this.project.getProperties());
    properties.putAll(this.session.getSystemProperties());
    properties.putAll(this.session.getUserProperties());
    return properties.getProperty(key, dflt);
  }

}