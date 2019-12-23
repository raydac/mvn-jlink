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

import static com.igormaznitsa.mvnjlink.jdkproviders.providers.LibericaOpenJdkProvider.ReleaseList.Release.BELLSOFT_FILENAME_PATTERN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


import java.util.regex.Matcher;
import org.junit.Test;

public class LibericaOpenJdkProviderTest {

  private void assertDistr(
      final String expectedType,
      final String expectedVersion,
      final String expectedOs,
      final String expectedArch,
      final String expectedExtension,
      final Matcher matcher
  ) {
    assertTrue(matcher.find());
    assertEquals(expectedType, matcher.group(1));
    assertEquals(expectedVersion, matcher.group(2));
    assertEquals(expectedOs, matcher.group(3));
    assertEquals(expectedArch, matcher.group(4));
    assertEquals(expectedExtension, matcher.group(5));
  }

  @Test
  public void testPatternForDistributive() {
    assertDistr("jre", "13.0.1", "linux", "arm32-vfp-hflt", "deb", BELLSOFT_FILENAME_PATTERN.matcher("bellsoft-jre13.0.1-linux-arm32-vfp-hflt.deb"));
    assertDistr("jre", "13.0.1", "linux", "ppc64le", "tar.gz", BELLSOFT_FILENAME_PATTERN.matcher("bellsoft-jre13.0.1-linux-ppc64le.tar.gz"));
    assertDistr("jre", "13.0.1", "macos", "amd64", "zip", BELLSOFT_FILENAME_PATTERN.matcher("bellsoft-jre13.0.1-macos-amd64.zip"));
    assertDistr("jre", "13.0.1", "macos", "amd64", "zip", BELLSOFT_FILENAME_PATTERN.matcher("bellsoft-jre13.0.1-macos-amd64.zip"));
    assertDistr("jdk", "11", "linux", "aarch64-lite", "tar.gz", BELLSOFT_FILENAME_PATTERN.matcher("bellsoft-jdk11-linux-aarch64-lite.tar.gz"));
    assertDistr("jdk", "11", "linux", "aarch64-lite", "tar.gz", BELLSOFT_FILENAME_PATTERN.matcher("bellsoft-jdk11-linux-aarch64-lite.tar.gz"));
    assertDistr("jre", "1.8.0", "linux", "aarch64", "tar.gz", BELLSOFT_FILENAME_PATTERN.matcher("bellsoft-jre1.8.0-linux-aarch64.tar.gz"));
  }

}