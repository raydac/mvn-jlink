package com.igormaznitsa.mvnjlink;


import org.apache.maven.plugin.testing.MojoRule;
import org.apache.maven.plugin.testing.WithoutMojo;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MvnJlinkMojoTest {
  @Rule
  public MojoRule rule = new MojoRule() {
    @Override
    protected void before() throws Throwable {
    }

    @Override
    protected void after() {
    }
  };

  @Test
  public void testSomething() throws Exception {
    File pom = new File("target/test-classes/project-to-test/");
    assertNotNull(pom);
    assertTrue(pom.exists());

    final MvnJlinkMojo myMojo = (MvnJlinkMojo) rule.lookupConfiguredMojo(pom, "jlink");
    assertNotNull(myMojo);
    myMojo.execute();

//    File outputDirectory = (File) rule.getVariableValueFromObject(myMojo, "outputDirectory");
//    assertNotNull(outputDirectory);
//    assertTrue(outputDirectory.exists());
//
//    File touch = new File(outputDirectory, "touch.txt");
//    assertTrue(touch.exists());

  }

  /**
   * Do not need the MojoRule.
   */
  @WithoutMojo
  @Test
  public void testSomethingWhichDoesNotNeedTheMojoAndProbablyShouldBeExtractedIntoANewClassOfItsOwn() {
    assertTrue(true);
  }

}

