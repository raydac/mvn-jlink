package com.igormaznitsa.mvnjlink;

import com.igormaznitsa.mvnjlink.utils.ProxySettings;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;

import javax.annotation.Nullable;

public abstract class AbstractJlinkMojo extends AbstractMojo {

  @Parameter(name = "skip", defaultValue = "false")
  private boolean skip;

  @Parameter(name = "disableSSLcheck", defaultValue = "false")
  private boolean disableSSLcheck;

  @Parameter(name = "proxy")
  private ProxySettings proxy;

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

  public final void execute() throws MojoExecutionException, MojoFailureException {
    if (isSkip()) {
      this.getLog().debug("Skip flag is active");
    } else {
      onExecute();
    }

  }

  public abstract void onExecute() throws MojoExecutionException, MojoFailureException;
}