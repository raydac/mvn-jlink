package com.igormaznitsa.mvnjlink.mojos;

import com.igormaznitsa.meta.annotation.MustNotContainNull;
import com.igormaznitsa.meta.common.utils.Assertions;
import com.igormaznitsa.meta.common.utils.GetUtils;
import com.igormaznitsa.mvnjlink.utils.SystemUtils;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.igormaznitsa.mvnjlink.utils.StringUtils.extractModuleNames;
import static java.util.stream.Collectors.joining;

@Mojo(name = "jlink", defaultPhase = LifecyclePhase.PACKAGE)
public class MvnJlinkMojo extends AbstractJlinkMojo {

  @Parameter(name = "jdepsOut")
  private String jdepsOut;

  @Parameter(name = "options")
  private List<String> options = new ArrayList<>();

  @Parameter(name = "addModules")
  private List<String> addModules = new ArrayList<>();

  @Parameter(name = "output", required = true)
  private String output;

  @Nonnull
  @MustNotContainNull
  public List<String> getOptions() {
    return this.options;
  }

  public void setOptions(@Nullable @MustNotContainNull final List<String> value) {
    this.options = GetUtils.ensureNonNull(value, new ArrayList<>());
  }

  @Nullable
  @MustNotContainNull
  private List<String> getModulesFromJdepsFile() {
    if (this.jdepsOut == null) {
      return Collections.emptyList();
    }

    final File jdepsFile = new File(this.jdepsOut);

    try {
      return extractModuleNames(FileUtils.readFileToString(jdepsFile, Charset.defaultCharset()));
    } catch (IOException ex) {
      this.getLog().error("Can't read jdeps file:" + jdepsFile, ex);
      return null;
    }
  }

  @Override
  public void onExecute() throws MojoExecutionException, MojoFailureException {
    try {
      this.getProvider().makeInstance(this).prepareJdkFolder();
    } catch (IOException ex) {
      throw new MojoExecutionException("Can't prepare JDK provider", ex);
    }

    final File jdkFolder = findJavaHome();
    final File exeJlink;
    try {
      exeJlink = SystemUtils.findJdkExecutable(jdkFolder, "jlink");
    } catch (IOException ex) {
      throw new MojoExecutionException("Can't find jlink utility", ex);
    }

    final List<String> modulesFromJdeps = getModulesFromJdepsFile();
    if (modulesFromJdeps == null) {
      throw new MojoExecutionException("Can't get module list from jdeps file");
    }

    final List<String> totalModules = new ArrayList<>(modulesFromJdeps);
    totalModules.addAll(this.addModules);

    final String joinedAddModules = totalModules.stream().map(String::trim).collect(joining(","));

    this.getLog().info("Modules: "+joinedAddModules);

    final List<String> commandLineOptions = new ArrayList<>(this.getOptions());

    final int indexOptions = commandLineOptions.indexOf("--add-modules");
    if (indexOptions < 0) {
      if (joinedAddModules.isEmpty()) {
        throw new MojoExecutionException("There are not provided modules to be added.");
      }
      commandLineOptions.add("--add-modules");
      commandLineOptions.add(joinedAddModules);
    } else {
      if (!joinedAddModules.isEmpty()) {
        commandLineOptions.set(indexOptions + 1, commandLineOptions.get(indexOptions + 1) + ',' + joinedAddModules);
      }
    }

    final List<String> commandLine = new ArrayList<>();
    commandLine.add(exeJlink.getAbsolutePath());
    commandLine.add("--output");
    commandLine.add(Assertions.assertNotNull(this.output));
    commandLine.addAll(commandLineOptions);

    this.getLog().debug("Command line: " + commandLine);

    final ByteArrayOutputStream consoleOut = new ByteArrayOutputStream();
    final ByteArrayOutputStream consoleErr = new ByteArrayOutputStream();

    final ProcessResult executor;
    try {
      executor = new ProcessExecutor(commandLine)
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
      getLog().debug(new String(consoleOut.toByteArray(), Charset.defaultCharset()));
    } else {
      getLog().info(new String(consoleOut.toByteArray(), Charset.defaultCharset()));
      getLog().error(new String(consoleErr.toByteArray(), Charset.defaultCharset()));
      throw new MojoFailureException("Jlink execution error code: " + executor.getExitValue());
    }
  }
}
