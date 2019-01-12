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

package com.igormaznitsa.mvnjlink.jdkproviders.providers;

import com.igormaznitsa.mvnjlink.jdkproviders.AbstractJdkProvider;
import com.igormaznitsa.mvnjlink.mojos.AbstractJdkToolMojo;
import org.apache.maven.plugin.logging.Log;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static com.igormaznitsa.meta.common.utils.Assertions.assertNotNull;

/**
 * Provider for locally presented JDK.
 * Priority:
 * <ul>
 *   <li>JDK described by <b>toolJdk</b></li>
 *   <li>JDK provided by toolchain</li>
 * </ul>
 */
public class LocalJdkProvider extends AbstractJdkProvider {
  public LocalJdkProvider(@Nonnull final AbstractJdkToolMojo mojo) {
    super(mojo);
  }

  @Nonnull
  @Override
  public Path getPathToJdk(@Nonnull final Map<String, String> config) throws IOException {
    final Log log = this.mojo.getLog();

    final String toolPath = this.mojo.findJdkTool("javac");

    if (toolPath == null) {
      log.error("Can't find jlink in the JDK, JDK version must be 9+");
    } else {
      log.debug("Detected jlink path: " + toolPath);
      final Path path = Paths.get(toolPath);
      Path parent = path.getParent();
      if (parent != null && "bin".equals(assertNotNull(parent.getFileName()).toString())) {
        return assertNotNull(parent.getParent());
      }
    }
    throw new IOException("Can't find JDK folder");
  }
}
