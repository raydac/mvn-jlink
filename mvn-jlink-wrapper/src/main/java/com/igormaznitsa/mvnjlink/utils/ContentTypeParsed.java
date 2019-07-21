package com.igormaznitsa.mvnjlink.utils;

import com.igormaznitsa.meta.annotation.MustNotContainNull;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ContentTypeParsed {

  private final String type;
  private final String[] subParts;

  public ContentTypeParsed(@Nonnull final String mime) {
    final String[] parsed = mime.split("/");
    if (parsed.length != 2) {
      throw new IllegalArgumentException("Illegal mime: " + mime);
    }
    this.type = parsed[0].trim();
    this.subParts = Stream.of(parsed[1].split("\\+")).map(x -> x.trim()).toArray(String[]::new);
  }

  @Nonnull
  public String getType() {
    return this.type;
  }

  @Nonnull
  @MustNotContainNull
  public String[] getSubTypes() {
    return this.subParts.clone();
  }

  @Nonnull
  @Override
  public String toString() {
    return this.type + '/' + Stream.of(this.subParts).collect(Collectors.joining("+"));
  }

  @Override
  public int hashCode() {
    return this.type.hashCode() ^ this.subParts.length;
  }

  @Override
  public boolean equals(@Nullable final Object that) {
    if (that == null) {
      return false;
    }
    if (this == that) {
      return true;
    }

    if (that instanceof ContentTypeParsed) {
      final ContentTypeParsed thatContent = (ContentTypeParsed) that;
      if (!this.type.equals(thatContent.type)) {
        return false;
      }
      for (final String subPart : this.subParts) {
        for (final String thatSubPart : thatContent.subParts) {
          if (subPart.equals(thatSubPart)) {
            return true;
          }
        }
      }
    }
    return false;
  }
}
