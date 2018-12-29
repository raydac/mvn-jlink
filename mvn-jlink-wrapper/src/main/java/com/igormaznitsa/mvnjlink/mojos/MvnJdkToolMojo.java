package com.igormaznitsa.mvnjlink.mojos;

import com.igormaznitsa.meta.annotation.MustNotContainNull;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.nio.charset.Charset.defaultCharset;
import static java.nio.file.Files.isExecutable;
import static java.nio.file.Files.isRegularFile;

/**
 * Execute tool from JDK.
 */
@Mojo(name = "jdk-tool", defaultPhase = LifecyclePhase.NONE, threadSafe = true)
public class MvnJdkToolMojo extends AbstractJdkToolMojo {

  /**
   * Name of JDK tool.
   */
  @Parameter(name = "tool", required = true)
  private String tool;

  /**
   * CLI options for tool execution.
   */
  @Parameter(name = "options")
  private List<String> options = new ArrayList<>();

  /**
   * Path to save output of the tool execution.
   */
  @Parameter(name = "output")
  private String output = null;

  /**
   * Path to save error output of the tool execution.
   */
  @Parameter(name = "outputErr")
  private String outputErr = null;

  /**
   * Timeout for execution in seconds. If it is less or equal zero then ignored.
   */
  @Parameter(name = "timeout", defaultValue = "-1")
  private long timeout = -1L;

  @Nonnull
  public String getTool() {
    return this.tool;
  }

  @Nonnull
  @MustNotContainNull
  public List<String> getOptions() {
    return this.options;
  }

  @Nullable
  public String getOutput() {
    return this.output;
  }

  @Nullable
  public String getOutputErr() {
    return this.outputErr;
  }

  public long getTimeout() {
    return this.timeout;
  }

  @Override
  public void onExecute() throws MojoExecutionException, MojoFailureException {
    final Log log = this.getLog();
    final Path providerJdk = this.getSourceJdkFolderFromProvider();
    log.debug("Provider jdk: " + providerJdk);

    final String pathToTool = this.findJdkTool(this.getTool());
    if (pathToTool == null) {
      throw new MojoExecutionException("Can't find tool in JDK: " + this.getTool());
    }

    final Path execToolPath = Paths.get(pathToTool);
    if (!isRegularFile(execToolPath) || !isExecutable(execToolPath)) {
      throw new MojoExecutionException("Can't find executable file: " + execToolPath);
    }

    final List<String> cliOptions = new ArrayList<>();
    cliOptions.add(execToolPath.toString());
    cliOptions.addAll(this.getOptions());

    final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    final ByteArrayOutputStream errorStream = new ByteArrayOutputStream();

    try {
      log.debug("CLI: " + cliOptions);

      final ProcessExecutor executor = new ProcessExecutor(cliOptions)
          .redirectError(errorStream)
          .redirectOutput(outStream)
          .readOutput(true)
          .exitValueAny();

      final ProcessResult result;
      if (this.getTimeout() > 0L) {
        result = executor.executeNoTimeout();
      } else {
        result = executor.timeout(this.getTimeout(), TimeUnit.SECONDS).execute();
      }

      final String strOut = new String(outStream.toByteArray(), defaultCharset());
      final String strErr = new String(errorStream.toByteArray(), defaultCharset());

      log.debug("----OUT----\n");
      log.debug(strOut);

      log.debug("----ERR----\n");
      log.debug(strErr);

      boolean failed = false;
      final int exitStatus = result.getExitValue();
      if (exitStatus == 0) {
        log.info("Successfully completed");
      } else {
        log.error("Completed with error status: " + exitStatus);
        failed = true;
      }

      if (this.getOutput() != null) {
        try {
          FileUtils.writeByteArrayToFile(new File(this.getOutput()), outStream.toByteArray());
        } catch (Exception ex) {
          log.error("Can't save output into file: " + this.getOutput());
          failed = true;
        }
      }

      if (this.getOutputErr() != null) {
        try {
          FileUtils.writeByteArrayToFile(new File(this.getOutputErr()), errorStream.toByteArray());
        } catch (Exception ex) {
          log.error("Can't save error output into file: " + this.getOutputErr());
          failed = true;
        }
      }

      if (failed) {
        throw new MojoFailureException("Execution failed, see log");
      }
    } catch (TimeoutException ex) {
      throw new MojoFailureException("Timeout");
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new MojoFailureException("Interrupted");
    } catch (IOException ex) {
      throw new MojoExecutionException("Exception during execution", ex);
    }
  }
}
