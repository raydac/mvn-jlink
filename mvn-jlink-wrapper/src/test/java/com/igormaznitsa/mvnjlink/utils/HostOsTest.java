package com.igormaznitsa.mvnjlink.utils;

import static com.igormaznitsa.meta.common.utils.Assertions.assertEquals;
import static org.junit.jupiter.api.condition.OS.FREEBSD;
import static org.junit.jupiter.api.condition.OS.LINUX;
import static org.junit.jupiter.api.condition.OS.MAC;
import static org.junit.jupiter.api.condition.OS.SOLARIS;
import static org.junit.jupiter.api.condition.OS.WINDOWS;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;

class HostOsTest {

  @Test
  @EnabledOnOs(LINUX)
  void testLinux() {
    assertEquals(HostOs.LINUX, HostOs.findHostOs());
  }

  @Test
  @EnabledOnOs(WINDOWS)
  void testWindows() {
    assertEquals(HostOs.WINDOWS, HostOs.findHostOs());
  }

  @Test
  @EnabledOnOs(MAC)
  void testMac() {
    assertEquals(true, HostOs.findHostOs().isMac());
  }

  @Test
  @EnabledOnOs(SOLARIS)
  void testSolaris() {
    assertEquals(HostOs.SOLARIS, HostOs.findHostOs());
  }

  @Test
  @EnabledOnOs(FREEBSD)
  void testFreeBsd() {
    assertEquals(HostOs.FREE_BSD, HostOs.findHostOs());
  }

}