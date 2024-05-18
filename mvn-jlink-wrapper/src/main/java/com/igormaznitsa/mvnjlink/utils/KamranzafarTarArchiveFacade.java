package com.igormaznitsa.mvnjlink.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Date;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.kamranzafar.jtar.TarEntry;
import org.kamranzafar.jtar.TarInputStream;

/**
 * Wrapper for TAR reader from https://github.com/kamranzafar/jtar because it works more stable than Apache Compress.
 */
public final class KamranzafarTarArchiveFacade extends ArchiveInputStream<ArchiveEntry> {

  public KamranzafarTarArchiveFacade(@Nonnull final InputStream inputStream) {
    super(new TarInputStream(inputStream), Charset.defaultCharset().name());
  }

  @Override
  @Nullable
  public ArchiveEntry getNextEntry() throws IOException {
    final TarEntry nextEntry = ((TarInputStream) this.in).getNextEntry();
    if (nextEntry == null) {
      return null;
    }
    return new TarEntryWrapper(nextEntry);
  }

  private static class TarEntryWrapper implements ArchiveEntry {
    private final TarEntry tarEntry;

    TarEntryWrapper(@Nonnull TarEntry entry) {
      this.tarEntry = entry;
    }

    @Override
    @Nonnull
    public Date getLastModifiedDate() {
      return this.tarEntry.getModTime();
    }

    @Override
    @Nonnull
    public String getName() {
      return this.tarEntry.getName();
    }

    @Override
    public long getSize() {
      return this.tarEntry.getSize();
    }

    @Override
    public boolean isDirectory() {
      return this.tarEntry.isDirectory();
    }
  }
}
