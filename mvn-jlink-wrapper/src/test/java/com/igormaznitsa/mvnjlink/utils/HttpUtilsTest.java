package com.igormaznitsa.mvnjlink.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class HttpUtilsTest {

  @Test
  void testContentTypeParsed() {
    assertEquals(new ContentTypeParsed("text/some+json"), new ContentTypeParsed("text/json"));
    assertEquals(new ContentTypeParsed("text/json"), new ContentTypeParsed("text/ddd+fff+json"));
    assertNotEquals(new ContentTypeParsed("image/json"),
        new ContentTypeParsed("text/ddd+fff+json"));
    assertNotEquals(new ContentTypeParsed("image/json"),
        new ContentTypeParsed("text/ddd+fff+json"));
    assertNotEquals(new ContentTypeParsed("text/json"), new ContentTypeParsed("text/ddd+fff"));
  }

}
