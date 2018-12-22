package com.igormaznitsa.mvnjlink;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "jlink", defaultPhase = LifecyclePhase.PREPARE_PACKAGE)
public class MvnJlinkMojo extends AbstractJlinkMojo {

  @Override
  public void onExecute() throws MojoExecutionException, MojoFailureException {

  }
}
