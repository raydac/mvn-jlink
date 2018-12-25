package com.igormaznitsa.mvnjlink.mojos;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import static com.igormaznitsa.mvnjlink.utils.SystemUtils.findJdkExecutable;

@Mojo(name = "jdeps", defaultPhase = LifecyclePhase.PACKAGE)
public class MvnJdepsMojo extends AbstractJlinkMojo {

  @Parameter(name = "options")
  private List<String> options = new ArrayList<>();

  @Parameter(name = "output", required = true)
  private String output;

  @Override
  public void onExecute() throws MojoExecutionException, MojoFailureException {
    try {
      this.getProvider().makeInstance(this).prepareJdkFolder();
    } catch (IOException ex) {
      throw new MojoExecutionException("Can't prepare JDK provider", ex);
    }

    final File jdkFolder = findJavaHome();
    final File exeJdeps;
    try {
      exeJdeps = findJdkExecutable(jdkFolder, "jdeps");
    } catch (IOException ex) {
      throw new MojoExecutionException("Can't find jdeps utility", ex);
    }

    final List<String> commandLine = new ArrayList<>();
    commandLine.add(exeJdeps.getAbsolutePath());
    commandLine.addAll(this.options);

    this.getLog().debug("Command line: " + commandLine);

    final ByteArrayOutputStream consoleOut = new ByteArrayOutputStream();
    final ByteArrayOutputStream consoleErr = new ByteArrayOutputStream();

    final ProcessResult executor;
    try {
      executor = new ProcessExecutor(commandLine)
          .readOutput(true)
          .redirectOutput(consoleOut)
          .redirectError(consoleErr)
          .executeNoTimeout();
    } catch (IOException ex) {
      throw new MojoExecutionException("Error during execution", ex);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new MojoFailureException("Execution interrupted", ex);
    }

    if (executor.getExitValue() == 0) {
      final String text = new String(consoleOut.toByteArray(), Charset.defaultCharset());
      final String error = new String(consoleErr.toByteArray(), Charset.defaultCharset());

      this.getLog().debug(text);
      this.getLog().debug(error);

      if (text.isEmpty()) {
        throw new MojoFailureException("JDEPS has generated empty text, check your JAR file, may be there are not classes!");
      }

      if (text.contains("Path does not exist: ")) {
        this.getLog().error(text);
        throw new MojoFailureException("Can't find file for analyzing");
      }

      if (this.output != null) {
        final File file = new File(this.output);
        try {
          FileUtils.write(file, text, Charset.defaultCharset());
        } catch (IOException ex) {
          throw new MojoExecutionException("Can't write jdeps file: " + file, ex);
        }
      }
    } else {
      this.getLog().info(new String(consoleOut.toByteArray(), Charset.defaultCharset()));
      this.getLog().error(new String(consoleErr.toByteArray(), Charset.defaultCharset()));
      throw new MojoFailureException("JDeps execution error code: " + executor.getExitValue());
    }

  }
}
