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
import java.util.Properties;

import static com.igormaznitsa.meta.common.utils.GetUtils.ensureNonNull;

public abstract class AbstractJlinkMojo extends AbstractMojo {

  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  @Parameter(defaultValue = "${user.home}${file.separator}.mvnJlink", name = "storeFolder")
  private String storeFolder = "";

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
  public String getJavaHome() {
    return this.javaHome;
  }

  public void setJavaHome(@Nullable final String value) {
    this.javaHome = ensureNonNull(value, System.getProperty("java.home"));
  }

  @Nonnull
  public Map<String, String> getProviderConfig() {
    return this.providerConfig;
  }

  public void setProviderConfig(@Nullable final Map<String, String> value) {
    this.providerConfig = ensureNonNull(value, new HashMap<>());
  }

  @Nonnull
  public String getStoreFolder() {
    return ensureNonNull(this.storeFolder, "");
  }

  public void setStoreFolder(@Nullable final String value) {
    this.storeFolder = ensureNonNull(value, "");
  }

  @Nonnull
  public JdkProviderId getProvider() {
    return this.provider;
  }

  public void setProvider(@Nullable final JdkProviderId value) {
    this.provider = ensureNonNull(value, JdkProviderId.LOCAL);
  }

  @Nullable
  public ProxySettings getProxy() {
    return this.proxy;
  }

  public void setProxy(@Nullable final ProxySettings value) {
    this.proxy = value;
  }


  public boolean isDisableSSLcheck() {
    return this.disableSSLcheck;
  }

  public void setDisableSSLcheck(final boolean value) {
    this.disableSSLcheck = value;
  }


  public boolean isSkip() {
    return this.skip;
  }

  public void setSkip(final boolean value) {
    this.skip = value;
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
  public File prepareStoreFolder() throws IOException {
    final String storeFolder = this.getStoreFolder();
    if (storeFolder.trim().isEmpty()) {
      throw new IOException("Path to the store folder is not provided");
    }
    final File result = new File(storeFolder);
    if (!result.isDirectory() && !result.mkdirs()) {
      throw new IOException("Can't create the store folder: " + result);
    }
    final Path asPath = result.toPath();
    if (!Files.isReadable(asPath)) {
      throw new IOException("Can't read from the store folder, check rights: " + result);
    }
    if (!Files.isWritable(asPath)) {
      throw new IOException("Can't write to the store folder, check rights: " + result);
    }
    return result;
  }

  public abstract void onExecute() throws MojoExecutionException, MojoFailureException;
}