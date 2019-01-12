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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static java.nio.charset.Charset.defaultCharset;
import static org.apache.commons.io.FileUtils.writeByteArrayToFile;

/**
 * Execute JDEPS tool from provided JDK, output will be saved.
 */
@Mojo(name = "jdeps", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class MvnJdepsMojo extends AbstractJdkToolMojo {

  /**
   * Options, they will be added to command line.
   */
  @Parameter(name = "options")
  private List<String> options = new ArrayList<>();

  /**
   * Output file where will be written output stream of the tool.
   */
  @Parameter(name = "output", required = true)
  private String output;

  @Override
  public void onExecute() throws MojoExecutionException, MojoFailureException {
    final Log log = this.getLog();

    this.getSourceJdkFolderFromProvider();

    final String pathToJdeps = this.findJdkTool("jdeps");
    if (pathToJdeps == null) {
      throw new MojoExecutionException("Can't find jdeps in JDK");
    }
    final Path execJdepsPath = Paths.get(pathToJdeps);

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
          .exitValueAny()
          .executeNoTimeout();
    } catch (IOException ex) {
      throw new MojoExecutionException("Error during execution", ex);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new MojoExecutionException("Execution interrupted", ex);
    }

    if (executor.getExitValue() == 0) {
      final String text = new String(consoleOut.toByteArray(), defaultCharset());
      final String error = new String(consoleErr.toByteArray(), defaultCharset());

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
        final File outFile = new File(this.output);
        try {
          writeByteArrayToFile(outFile, consoleOut.toByteArray());
        } catch (IOException ex) {
          throw new MojoExecutionException("Can't write jdeps file: " + outFile, ex);
        }
        log.info("Saved " + text.length() + " chars into file : " + outFile.getAbsolutePath());
      }
    } else {
      final String strOut = new String(consoleOut.toByteArray(), Charset.defaultCharset());
      final String strErr = new String(consoleErr.toByteArray(), Charset.defaultCharset());

      if (strErr.isEmpty()) {
        log.error(strOut);
      } else {
        log.info(new String(consoleOut.toByteArray(), Charset.defaultCharset()));
        log.error(new String(consoleErr.toByteArray(), Charset.defaultCharset()));
      }

      throw new MojoFailureException("jdeps returns error status code: " + executor.getExitValue());
    }

  }
}
