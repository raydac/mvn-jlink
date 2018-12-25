package com.igormaznitsa.mvnjlink.utils;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;

public class StringUtilsTest {
  @Test
  public void testExtractModuleNames() throws Exception {
    final String text = IOUtils.resourceToString("jmods.out", StandardCharsets.UTF_8, StringUtilsTest.class.getClassLoader());
    final List<String> modules = StringUtils.extractModuleNames(text);
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