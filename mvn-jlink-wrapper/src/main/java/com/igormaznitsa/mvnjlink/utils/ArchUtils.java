package com.igormaznitsa.mvnjlink.utils;

import com.igormaznitsa.meta.annotation.MustNotContainNull;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.logging.Log;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.of;
import static org.apache.commons.io.FilenameUtils.normalize;

public final class ArchUtils {

  private static final ArchiveStreamFactory ARCHIVE_STREAM_FACTORY = new ArchiveStreamFactory();

  private ArchUtils() {

  }

  /**
   * Upack whole archive or some its folders into a folder.
   *
   * @param logger            maven logger for logging, must not be null
   * @param archiveFile       the archive to be unpacked
   * @param destinationFolder the destination folder for unpacking
   * @param folders           folders which content should be extracted
   * @return number of extracted files
   * @throws IOException it will be thrown for error in unpack process
   */
  public static int unpackArchiveFile(@Nonnull final Log logger, @Nonnull final File archiveFile, @Nonnull final File destinationFolder, @Nonnull @MustNotContainNull final String... folders) throws IOException {
    final String lowCaseArchiveName = archiveFile.getName().toLowerCase(Locale.ENGLISH);

    final ArchEntryGetter entryGetter;

    final ZipFile zipFile;
    final ArchiveInputStream archiveInputStream;
    if (lowCaseArchiveName.endsWith(".zip")) {
      logger.debug("Detected ZIP archive");
      zipFile = new ZipFile(archiveFile);
      archiveInputStream = null;

      entryGetter = new ArchEntryGetter() {
        private final Enumeration<ZipArchiveEntry> iterator = zipFile.getEntries();

        @Nullable
        @Override
        public ArchiveEntry getNextEntry() throws IOException {
          ArchiveEntry result = null;
          if (this.iterator.hasMoreElements()) {
            result = this.iterator.nextElement();
          }
          return result;
        }
      };
    } else {
      zipFile = null;
      try {
        if (lowCaseArchiveName.endsWith(".tar.gz")) {
          logger.debug("Detected TAR.GZ archive");

          archiveInputStream = new TarArchiveInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(archiveFile))));

          entryGetter = new ArchEntryGetter() {
            @Nullable
            @Override
            public ArchiveEntry getNextEntry() throws IOException {
              final TarArchiveInputStream tarInputStream = (TarArchiveInputStream) archiveInputStream;
              return tarInputStream.getNextTarEntry();
            }
          };

        } else {
          logger.debug("Detected OTHER archive");
          archiveInputStream = ARCHIVE_STREAM_FACTORY.createArchiveInputStream(new BufferedInputStream(new FileInputStream(archiveFile)));
          logger.debug("Created archive stream : " + archiveInputStream.getClass().getName());

          entryGetter = new ArchEntryGetter() {
            @Nullable
            @Override
            public ArchiveEntry getNextEntry() throws IOException {
              return archiveInputStream.getNextEntry();
            }
          };
        }

      } catch (ArchiveException ex) {
        throw new IOException("Can't recognize or read archive file : " + archiveFile, ex);
      } catch (CantReadArchiveEntryException ex) {
        throw new IOException("Can't read entry from archive file : " + archiveFile, ex);
      }
    }

    try {
      final List<String> normalizedFolders = of(folders).map(x -> normalize(x, true) + '/').collect(toList());

      int unpackedFilesCounter = 0;
      while (!Thread.currentThread().isInterrupted()) {
        final ArchiveEntry entry = entryGetter.getNextEntry();
        if (entry == null) {
          break;
        }
        logger.debug("Unpacking entry: " + entry.getName());

        final String normalizedPath = normalize(entry.getName(), true);

        logger.debug("Detected archive entry : " + normalizedPath);

        if (normalizedFolders.isEmpty() || normalizedFolders.stream().anyMatch(normalizedPath::startsWith)) {
          final String normalizedFolder = normalizedFolders.stream().filter(normalizedPath::startsWith).findFirst().orElse("");
          final File targetFile = new File(destinationFolder, normalizedPath.substring(normalizedFolder.length()));

          if (entry.isDirectory()) {
            logger.debug("Folder : " + normalizedPath);
            if (!targetFile.exists() && !targetFile.mkdirs()) {
              throw new IOException("Can't create folder " + targetFile);
            }
          } else {
            final File parent = targetFile.getParentFile();

            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
              throw new IOException("Can't create folder : " + parent);
            }

            try (final FileOutputStream fos = new FileOutputStream(targetFile)) {
              if (zipFile != null) {
                logger.debug("Unpacking ZIP entry : " + normalizedPath);

                try (final InputStream zipEntryInStream = zipFile.getInputStream((ZipArchiveEntry) entry)) {
                  if (IOUtils.copy(zipEntryInStream, fos) != entry.getSize()) {
                    throw new IOException("Can't unpack file, illegal unpacked length : " + entry.getName());
                  }
                }
              } else {
                logger.debug("Unpacking archive entry : " + normalizedPath);

                if (!archiveInputStream.canReadEntryData(entry)) {
                  throw new IOException("Can't read archive entry data : " + normalizedPath);
                }
                if (IOUtils.copy(archiveInputStream, fos) != entry.getSize()) {
                  throw new IOException("Can't unpack file, illegal unpacked length : " + entry.getName());
                }
              }
            }
            unpackedFilesCounter++;
          }
        } else {
          logger.debug("Archive entry " + normalizedPath + " ignored");
        }
      }
      return unpackedFilesCounter;
    } finally {
      closeCloseable(zipFile, logger);
      closeCloseable(archiveInputStream, logger);
    }
  }

  protected static void closeCloseable(@Nullable final Closeable closeable, @Nonnull final Log logger) {
    if (closeable != null) {
      try {
        closeable.close();
      } catch (Exception ex) {
        logger.debug("Can't close closeable object: " + closeable, ex);
      }
    }
  }

  private interface ArchEntryGetter {

    @Nullable
    ArchiveEntry getNextEntry() throws IOException;
  }

  public static final class CantReadArchiveEntryException extends RuntimeException {
    public CantReadArchiveEntryException(@Nullable final Throwable cause) {
      super("Can't read archive entry for exception", cause);
    }
  }

}
