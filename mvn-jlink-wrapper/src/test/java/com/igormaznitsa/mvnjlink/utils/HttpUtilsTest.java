package com.igormaznitsa.mvnjlink.utils;

import org.junit.Test;
import static org.junit.Assert.*;

public class HttpUtilsTest {

  @Test
  public void testContentTypeParsed() {
    assertTrue(new ContentTypeParsed("text/some+json").equals(new ContentTypeParsed("text/json")));
    assertTrue(new ContentTypeParsed("text/json").equals(new ContentTypeParsed("text/ddd+fff+json")));
    assertFalse(new ContentTypeParsed("image/json").equals(new ContentTypeParsed("text/ddd+fff+json")));
    assertFalse(new ContentTypeParsed("image/json").equals(new ContentTypeParsed("text/ddd+fff+json")));
    assertFalse(new ContentTypeParsed("text/json").equals(new ContentTypeParsed("text/ddd+fff")));
  }

}
