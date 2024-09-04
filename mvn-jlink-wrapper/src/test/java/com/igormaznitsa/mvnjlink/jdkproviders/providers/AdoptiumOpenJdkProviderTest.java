package com.igormaznitsa.mvnjlink.jdkproviders.providers;

import static com.igormaznitsa.meta.common.utils.Assertions.assertEquals;
import static com.igormaznitsa.meta.common.utils.Assertions.fail;

import java.util.regex.Matcher;
import org.junit.jupiter.api.Test;

public class AdoptiumOpenJdkProviderTest {

  private static void assertFileNameParseable(final String expectedOs, final String expectedBuild,
                                              final String expectedExtension,
                                              final String version) {
    final Matcher matcher =
        AdoptiumOpenJdkProvider.ReleaseList.Release.ADOPTGIT_FILENAME_PATTERN.matcher(version);
    if (matcher.find()) {
      assertEquals(expectedOs, matcher.group(4));
      assertEquals(expectedBuild, matcher.group(6));
      assertEquals(expectedExtension, matcher.group(7));
    } else {
      fail("Doesn't match pattern: " + version);
    }
  }

  @Test
  public void testFileNameForPattern() {
    assertFileNameParseable("alpine-linux", "2022-09-26-18-05", "tar.gz",
        "OpenJDK8U-debugimage_aarch64_alpine-linux_hotspot_2022-09-26-18-05.tar.gz");
    assertFileNameParseable("windows", "21.0.4_7", "7zip",
        "OpenJDK21U-debugimage_x64_windows_hotspot_21.0.4_7.7zip");
    assertFileNameParseable("windows", "21.0.4_7", "zip",
        "OpenJDK21U-debugimage_x64_windows_hotspot_21.0.4_7.zip");
    assertFileNameParseable("alpine-linux", "21.0.4_7", "tar.gz",
        "OpenJDK21U-debugimage_aarch64_alpine-linux_hotspot_21.0.4_7.tar.gz");
    assertFileNameParseable("windows", "2022-06-15-11-45", "zip",
        "OpenJDK19-testimage_x64_windows_hotspot_2022-06-15-11-45.zip");
    assertFileNameParseable("linux", "21.0.4_7", "tar.gz",
        "OpenJDK21U-jdk_x64_linux_hotspot_21.0.4_7.tar.gz");
    assertFileNameParseable("linux", "17.0.12_7", "tar.gz",
        "OpenJDK17U-debugimage_aarch64_linux_hotspot_17.0.12_7.tar.gz");
    assertFileNameParseable("linux", "17.0.12_7", "7zip",
        "OpenJDK17U-debugimage_aarch64_linux_hotspot_17.0.12_7.7zip");
  }

}