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
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractJlinkMojo extends AbstractMojo {

  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  @Parameter(defaultValue = "${user.home}${file.separator}.mvnJlinkCache", name = "cacheFolder")
  private String cacheFolder = System.getProperty("user.home") + File.separator + ".mvnJlinkCache";

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

  @Parameter(name = "javaHome", defaultValue = "${java.home}")
  private String javaHome = System.getProperty("java.home");

  @Nonnull
  public Map<String, String> getProviderConfig() {
    return this.providerConfig;
  }

  @Nonnull
  public String getCacheFolder() {
    return this.cacheFolder;
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
  public File prepareAndGetCacheFolder() throws IOException {
    final String storeFolder = this.getCacheFolder();

    if (storeFolder.trim().isEmpty()) {
      throw new IOException("Path to the cache folder is not provided");
    }

    final File result = new File(storeFolder);

    if (!result.isDirectory() && !result.mkdirs()) {
      throw new IOException("Can't create the cache folder: " + result);
    }

    final Path asPath = result.toPath();

    if (!Files.isReadable(asPath)) {
      throw new IOException("Can't read from the cache folder, check rights: " + result);
    }

    if (!Files.isWritable(asPath)) {
      throw new IOException("Can't write to the cache folder, check rights: " + result);
    }
    return result;
  }

  @Nullable
  public File findJavaHome() {
    File result = new File(this.javaHome);
    if (!result.isDirectory()) {
      result = null;
    }
    return result;
  }


  public abstract void onExecute() throws MojoExecutionException, MojoFailureException;
}