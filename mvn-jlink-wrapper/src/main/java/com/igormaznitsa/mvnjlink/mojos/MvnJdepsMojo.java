package com.igormaznitsa.mvnjlink.mojos;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.commons.io.FileUtils.write;

@Mojo(name = "jdeps", defaultPhase = LifecyclePhase.PACKAGE)
public class MvnJdepsMojo extends AbstractJlinkMojo {

  @Parameter(name = "options")
  private List<String> options = new ArrayList<>();

  @Parameter(name = "output", required = true)
  private String output;

  @Override
  public void onExecute() throws MojoExecutionException, MojoFailureException {
    final Log log = this.getLog();

    this.processJdkProvider();

    final Path baseJdkHomeFolder = findBaseJdkHomeFolder();
    log.info("Base JDK home folder: " + baseJdkHomeFolder);

    final String pathToJdeps = this.findJdkTool("jdeps");
    if (pathToJdeps == null) {
      throw new MojoExecutionException("Can't find jdeps in JDK");
    }
    final Path execJdepsPath = Path.of(pathToJdeps);

    final List<String> cliArguments = new ArrayList<>();
    cliArguments.add(execJdepsPath.toString());
    cliArguments.addAll(this.options);

    log.info("CLI arguments: " + cliArguments.stream().skip(1).collect(Collectors.joining(" ")));

    final ByteArrayOutputStream consoleOut = new ByteArrayOutputStream();
    final ByteArrayOutputStream consoleErr = new ByteArrayOutputStream();

    final ProcessResult executor;
    try {
      executor = new ProcessExecutor(cliArguments)
          .readOutput(true)
          .redirectOutput(consoleOut)
          .redirectError(consoleErr)
          .executeNoTimeout();
    } catch (IOException ex) {
      throw new MojoExecutionException("Error during execution", ex);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new MojoExecutionException("Execution interrupted", ex);
    }

    if (executor.getExitValue() == 0) {
      final String text = new String(consoleOut.toByteArray(), Charset.defaultCharset());
      final String error = new String(consoleErr.toByteArray(), Charset.defaultCharset());

      log.debug(text);
      log.debug(error);

      if (text.isEmpty()) {
        throw new MojoFailureException("jdeps has generated empty output stream, check your jar, may be it is empty");
      }

      if (text.contains("Path does not exist: ")) {
        log.error(text);
        throw new MojoFailureException("A record that some path doesn't exist has been detected in out stream, it is recognized as error");
      }

      if (this.output != null) {
        final File file = new File(this.output);
        try {
          write(file, text, Charset.defaultCharset());
        } catch (IOException ex) {
          throw new MojoExecutionException("Can't write jdeps file: " + file, ex);
        }
        log.info("Saved " + text.length() + " chars into file : " + file.getAbsolutePath());
      }
    } else {
      log.info(new String(consoleOut.toByteArray(), Charset.defaultCharset()));
      log.error(new String(consoleErr.toByteArray(), Charset.defaultCharset()));
      throw new MojoFailureException("Call of jdeps returns status code " + executor.getExitValue());
    }

  }
}
