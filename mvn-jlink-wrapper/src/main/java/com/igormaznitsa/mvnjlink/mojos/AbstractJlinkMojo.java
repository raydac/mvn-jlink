package com.igormaznitsa.mvnjlink.mojos;

import com.igormaznitsa.mvnjlink.jdkproviders.JdkProviderId;
import com.igormaznitsa.mvnjlink.utils.ProxySettings;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractJlinkMojo extends AbstractMojo {

  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  @Parameter(defaultValue = "false", name = "offline")
  private boolean offline;

  @Parameter(defaultValue = "${user.home}${file.separator}.mvnJlinkCache", name = "jdkCachePath")
  private String jdkCachePath = System.getProperty("user.home") + File.separator + ".mvnJlinkJdkCache";

  @Parameter(name = "skip", defaultValue = "false")
  private boolean skip;

  @Parameter(name = "disableSSLcheck", defaultValue = "false")
  private boolean disableSSLcheck;

  @Parameter(name = "proxy")
  private ProxySettings proxy;

  @Parameter(name = "provider", defaultValue = "LOCAL")
  private JdkProviderId provider = JdkProviderId.LOCAL;

  @Parameter(name = "providerConfig")
  private Map<String, String> providerConfig = new HashMap<>();

  @Parameter(name = "baseJdkHome", defaultValue = "${java.home}")
  private String baseJdkHome = System.getProperty("java.home");

  public boolean isOffline() {
    return this.offline;
  }

  @Nonnull
  public Map<String, String> getProviderConfig() {
    return this.providerConfig;
  }

  @Nonnull
  public String getJdkCachePath() {
    return this.jdkCachePath;
  }

  @Nonnull
  public JdkProviderId getProvider() {
    return this.provider;
  }

  @Nullable
  public ProxySettings getProxy() {
    return this.proxy;
  }

  public boolean isDisableSSLcheck() {
    return this.disableSSLcheck;
  }

  public boolean isSkip() {
    return this.skip;
  }

  @Nonnull
  public MavenProject getProject() {
    return this.project;
  }

  public final void execute() throws MojoExecutionException, MojoFailureException {
    if (isSkip()) {
      this.getLog().debug("Skip flag is active");
    } else {
      onExecute();
    }
  }

  @Nonnull
  public Path findJdkCacheFolder() throws IOException {
    final String storeFolder = this.getJdkCachePath();

    if (storeFolder.trim().isEmpty()) {
      throw new IOException("Path to the cache folder is not provided");
    }

    final Path result = Paths.get(storeFolder);

    if (!Files.isDirectory(result)) {
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

  @Nullable
  public Path findBaseJdkHomeFolder() {
    Path result = Paths.get(this.baseJdkHome);
    if (!Files.isDirectory(result)) {
      result = null;
    }
    return result;
  }


  public abstract void onExecute() throws MojoExecutionException, MojoFailureException;
}