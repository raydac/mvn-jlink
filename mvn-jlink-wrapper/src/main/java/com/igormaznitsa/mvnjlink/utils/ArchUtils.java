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

package com.igormaznitsa.mvnjlink.utils;

import static com.igormaznitsa.meta.common.utils.Assertions.assertNotNull;
import static com.igormaznitsa.mvnjlink.utils.SystemUtils.closeCloseable;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.newInputStream;
import static java.nio.file.Files.newOutputStream;
import static java.nio.file.Paths.get;
import static java.util.Arrays.stream;
import static java.util.Locale.ENGLISH;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.of;
import static org.apache.commons.io.FilenameUtils.normalize;
import static org.apache.commons.io.IOUtils.copy;

import com.igormaznitsa.meta.annotation.MustNotContainNull;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.GZIPInputStream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.logging.Log;

public final class ArchUtils {

  public static final Path NOP_PATH = Paths.get("NOP");
  private static final ArchiveStreamFactory ARCHIVE_STREAM_FACTORY = new ArchiveStreamFactory();

  private ArchUtils() {

  }

  /**
   * Find shortes directory path in archive
   *
   * @param archiveFile archive file
   * @return shortest directory path
   * @throws IOException if any error
   */
  @Nullable
  public static String findShortestDirectory(@Nonnull final Path archiveFile)
      throws IOException {

    final String archiveType = findArchiveType(archiveFile);

    final ArchEntryGetter entryGetter;

    final ZipFile zipFile;
    final ArchiveInputStream archiveInputStream;

    if ("zip".equals(archiveType)) {
      zipFile = new ZipFile(archiveFile.toFile());
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
        if ("tar.gz".equals(archiveType)) {
          archiveInputStream = new KamranzafarTarArchiveFacade(
              new GZIPInputStream(fileAsInputStream(archiveFile)));

          entryGetter = new ArchEntryGetter() {
            @Nullable
            @Override
            public ArchiveEntry getNextEntry() throws IOException {
              return archiveInputStream.getNextEntry();
            }
          };

        } else {
          archiveInputStream = ARCHIVE_STREAM_FACTORY.createArchiveInputStream(
              new BufferedInputStream(fileAsInputStream(archiveFile)));

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
      String result = null;
      while (!Thread.currentThread().isInterrupted()) {
        final ArchiveEntry entry = entryGetter.getNextEntry();
        if (entry == null) {
          break;
        }
        String path = entry.getName();
        boolean dotRootPrefix = false;
        if (path.startsWith("./")) {
          path = path.substring(2);
          dotRootPrefix = true;
        }
        final int separator = path.indexOf('/');
        if (separator >= 0) {
          result = path.substring(0, separator);
          if (dotRootPrefix) {
            result = "./" + result;
          }
        }
      }

      closeCloseable(archiveInputStream, null);
      closeCloseable(zipFile, null);

      return result;
    } catch (IOException ex) {
      throw new IOException(
          "Can't read file because '" + ex.getMessage() + "' : " + archiveFile.toAbsolutePath(),
          ex);
    }
  }

  @Nonnull
  public static String findArchiveType(@Nonnull final Path file) {
    final String fileName = file.getFileName().toString().toLowerCase(ENGLISH);

    if (fileName.endsWith(".zip")) {
      return "zip";
    }
    if (fileName.endsWith("tar.gz")) {
      return "tar.gz";
    }

    ZipFile zipFile = null;
    try {
      zipFile = new ZipFile(file.toFile());
      return "zip";
    } catch (Exception ex) {
      // do nothing
    } finally {
      IOUtils.closeQuietly(zipFile);
    }
    KamranzafarTarArchiveFacade tarGzFile = null;
    try {
      tarGzFile = new KamranzafarTarArchiveFacade(
          new GZIPInputStream(fileAsInputStream(file)));
      return "tar.gz";
    } catch (Exception ex) {
      // do nothing
    } finally {
      IOUtils.closeQuietly(tarGzFile);
    }
    return "unknown";
  }

  @Nonnull
  private static InputStream fileAsInputStream(@Nonnull final Path file) throws IOException {
    return new BufferedInputStream(newInputStream(file));
  }

  /**
   * Unpack whole archive or some its folders into a folder.
   *
   * @param logger            maven logger for logging, must not be null
   * @param tryMakeExecutable true if to make bin files executable ones
   * @param archiveFile       the archive to be unpacked
   * @param destinationFolder the destination folder for unpacking
   * @param foldersToUnpack   folders which content should be extracted
   * @return number of extracted files
   * @throws IOException it will be thrown for error in unpack process
   */
  public static int unpackArchiveFile(
      @Nonnull final Log logger,
      final boolean tryMakeExecutable,
      @Nonnull final Path archiveFile,
      @Nonnull final Path destinationFolder,
      @Nonnull @MustNotContainNull final String... foldersToUnpack) throws IOException {
    final ArchEntryGetter entryGetter;

    final ZipFile zipFile;
    final ArchiveInputStream<?> archiveInputStream;

    final String detectedArchiveType = findArchiveType(archiveFile);
    logger.info("Detected archive type: " + detectedArchiveType);

    if (detectedArchiveType.equals("zip")) {
      logger.debug("Detected ZIP archive");
      zipFile = new ZipFile(archiveFile.toFile());
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
        if (detectedArchiveType.equals("tar.gz")) {
          logger.debug("Detected TAR.GZ archive");

          archiveInputStream =
              new KamranzafarTarArchiveFacade(new GZIPInputStream(fileAsInputStream(archiveFile)));

          entryGetter = new ArchEntryGetter() {
            @Nullable
            @Override
            public ArchiveEntry getNextEntry() throws IOException {
              return archiveInputStream.getNextEntry();
            }
          };

        } else {
          logger.debug("Detected OTHER archive");
          archiveInputStream = ARCHIVE_STREAM_FACTORY.createArchiveInputStream(
              fileAsInputStream(archiveFile));
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
      final List<String> normalizedFolders =
          of(foldersToUnpack).map(x -> normalize(x, true) + '/').collect(toList());

      int unpackedFilesCounter = 0;

      while (!Thread.currentThread().isInterrupted()) {
        final ArchiveEntry entry = entryGetter.getNextEntry();
        if (entry == null) {
          break;
        }
        logger.debug(
            "Unpacking entry: " + entry.getName() + " size " + entry.getSize() + " byte(s)");

        final String normalizedPath = normalize(entry.getName(), true);

        if (normalizedFolders.isEmpty() ||
            normalizedFolders.stream().anyMatch(normalizedPath::startsWith)) {
          final String normalizedFolder =
              normalizedFolders.stream().filter(normalizedPath::startsWith).findFirst().orElse("");
          final Path targetFile =
              destinationFolder == NOP_PATH ? NOP_PATH : get(destinationFolder.toString(),
                  normalizedPath.substring(normalizedFolder.length()));

          if (entry.isDirectory()) {
            logger.debug("Folder : " + normalizedPath);
            if (!exists(targetFile)) {
              createDirectories(targetFile);
            }
          } else {
            final Path parent = targetFile.getParent();

            if (parent != null && !isDirectory(parent)) {
              createDirectories(parent);
            }

            if (Files.isDirectory(targetFile)) {
              logger.warn("Ignoring artifact because already presented unpacked folder: " + entry);
            } else {

              try (final OutputStream fos = makeFileOutputStream(targetFile)) {
                if (zipFile != null) {
                  logger.debug("Unpacking ZIP entry : " + normalizedPath);

                  try (final InputStream zipEntryInStream = zipFile.getInputStream(
                      (ZipArchiveEntry) entry)) {
                    if (copy(zipEntryInStream, fos) != entry.getSize()) {
                      throw new IOException(
                          "Can't unpack file, illegal unpacked length : " + entry.getName());
                    }
                  }
                } else {
                  logger.debug("Unpacking archive entry : " + normalizedPath);

                  if (!archiveInputStream.canReadEntryData(entry)) {
                    throw new IOException("Can't read archive entry data : " + normalizedPath);
                  }

                  final byte[] readArray = IOUtils.toByteArray(archiveInputStream);
                  if (readArray.length != entry.getSize()) {
                    throw new IOException(
                        "Can't unpack file, illegal unpacked length (" + readArray.length + "!=" +
                            entry.getSize() + "): " + entry.getName());
                  }
                  IOUtils.write(readArray, fos);
                }
              }

              if (tryMakeExecutable) {
                final String name =
                    assertNotNull(targetFile.getFileName()).toString().toLowerCase(ENGLISH);
                if (Files.size(targetFile) > 0 && (name.endsWith(".bat")
                    || name.endsWith(".cmd")
                    || name.endsWith(".exe")
                    || name.endsWith(".sh")
                    || !name.contains("."))) {
                  if (!targetFile.toFile().setExecutable(true, true)) {
                    logger.warn("Can't make executable : " + targetFile);
                  }
                }
              }
            }
            unpackedFilesCounter++;
          }
        } else {
          logger.debug("Archive entry " + normalizedPath + " ignored");
        }
      }

      postProcessUnpackedArchive(logger, destinationFolder.toFile());
      return unpackedFilesCounter;
    } catch (IOException ex) {
      throw new IOException(
          "Can't read file because '" + ex.getMessage() + "' : " + archiveFile.toAbsolutePath(),
          ex);
    } finally {
      closeCloseable(zipFile, logger);
      closeCloseable(archiveInputStream, logger);
    }
  }

  @Nonnull
  private static OutputStream makeFileOutputStream(@Nonnull final Path targetFile)
      throws IOException {
    if (targetFile == NOP_PATH) {
      return new ByteArrayOutputStream();
    } else {
      return newOutputStream(targetFile);
    }
  }

  private static void postProcessUnpackedArchive(@Nonnull final Log logger,
                                                 @Nonnull final File unpackFolder)
      throws IOException {
    final File[] filesInRoot = unpackFolder.listFiles();
    if (filesInRoot != null
        && filesInRoot.length != 0
        && stream(filesInRoot).filter(File::isDirectory)
        .anyMatch(x -> "contents".equalsIgnoreCase(x.getName()))) {
      logger.debug(
          "Detected archive prepared for MacOS, moving its internal JDK folder to the root");
      // it is unpacked mac archive
      final File unpackedHomeFolder = new File(stream(filesInRoot).filter(File::isDirectory)
          .filter(x -> "contents".equalsIgnoreCase(x.getName()))
          .findFirst()
          .orElseThrow(() -> new IOException("Can't find Contents folder")), "Home");
      if (unpackedHomeFolder.isDirectory()) {
        logger.debug("Found Home folder, copying it as JDK root");
        // rename root
        final File renamedDestinationFolder =
            new File(unpackFolder.getParent(), "." + unpackFolder.getName() + "_tmp");
        logger.debug("Renaming file " + unpackFolder + " to " + renamedDestinationFolder);
        if (!unpackFolder.renameTo(renamedDestinationFolder)) {
          throw new IOException("Can't rename " + unpackFolder + " to " + renamedDestinationFolder);
        }
        final File tempHomeFolder =
            new File(new File(renamedDestinationFolder, "Contents"), "Home");
        logger.debug("Moving folder " + tempHomeFolder + " to " + unpackFolder);
        FileUtils.moveDirectory(tempHomeFolder, unpackFolder);
        FileUtils.deleteDirectory(renamedDestinationFolder);
        logger.debug("Temp folder deleted " + renamedDestinationFolder);
      } else {
        throw new IOException("Can't find Contents/Home sub-folder in MacOS archive");
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
