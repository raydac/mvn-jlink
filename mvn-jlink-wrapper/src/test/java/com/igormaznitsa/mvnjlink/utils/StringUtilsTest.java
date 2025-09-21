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

package com.igormaznitsa.mvnjlink.utils;

import static com.igormaznitsa.meta.common.utils.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

class StringUtilsTest {

  @Test
  public void testMergeUrl() {
    assertEquals("https://hello.com/some/path",
        StringUtils.mergeUrl("https://hello.com", "some", "path"));
    assertEquals("https://hello.com/some/path",
        StringUtils.mergeUrl("https://hello.com/", "/some", "/path"));
    assertEquals("https://hello.com/some/path",
        StringUtils.mergeUrl("https://hello.com/", "/some/", "/path"));
    assertEquals("https://hello.com/some/path",
        StringUtils.mergeUrl("https://hello.com/", "some", "/path"));
  }

  @Test
  public void testExtractFileHash() throws IOException {
    assertEquals("ab8674dd80538dd47279d90c37fd51bdac713d2e67ec09c3b4d1cb5d16a3cfa8", StringUtils.extractFileHash("ab8674dd80538dd47279d90c37fd51bdac713d2e67ec09c3b4d1cb5d16a3cfa8  OpenJDK8-OPENJ9_ppc64_AIX_jdk8u162-b12_openj9-0.8.0.tar.gz"));
  }

  @Test
  void testExtractModuleNames() throws Exception {
    final String text = IOUtils.resourceToString("jmods.out", StandardCharsets.UTF_8, StringUtilsTest.class.getClassLoader());
    final List<String> modules = StringUtils.extractJdepsModuleNames(text);
    assertArrayEquals(
        new String[] {
            "java.base",
            "java.compiler",
            "java.datatransfer",
            "java.desktop",
            "java.logging",
            "java.naming",
            "java.prefs",
            "java.scripting",
            "java.sql",
            "jdk.unsupported"
        }, modules.toArray());
  }
}