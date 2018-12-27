package com.igormaznitsa.mvnjlink.utils;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class StringUtilsTest {

  @Test
  public void testExtractFileHash() throws IOException {
    assertEquals("ab8674dd80538dd47279d90c37fd51bdac713d2e67ec09c3b4d1cb5d16a3cfa8", StringUtils.extractFileHash("ab8674dd80538dd47279d90c37fd51bdac713d2e67ec09c3b4d1cb5d16a3cfa8  OpenJDK8-OPENJ9_ppc64_AIX_jdk8u162-b12_openj9-0.8.0.tar.gz"));
  }

  @Test
  public void testExtractModuleNames() throws Exception {
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