package com.createstats.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewImagesTest {
    private static BufferedImage sheet(int rgb) {
        final BufferedImage image = new BufferedImage(8, 6, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 6; y++) {
            for (int x = 0; x < 8; x++) {
                image.setRGB(x, y, rgb + x * 0x010101 + y * 0x000300);
            }
        }
        return image;
    }

    @Test
    @DisplayName("the same picture leaves the file alone, byte for byte")
    void samePictureIsNotRewritten(@TempDir Path dir) throws IOException {
        final File file = dir.resolve("sheet.png").toFile();
        assertTrue(PreviewImages.write(sheet(0x203040), "png", file));

        final byte[] committed = Files.readAllBytes(file.toPath());
        final byte[] marked = java.util.Arrays.copyOf(committed, committed.length + 4);
        Files.write(file.toPath(), marked);
        assertTrue(file.setLastModified(1_000_000_000_000L));
        assertTrue(PreviewImages.write(sheet(0x203040), "png", file));
        assertArrayEquals(marked, Files.readAllBytes(file.toPath()), "rewritten with not one pixel changed");
        assertTrue(file.lastModified() == 1_000_000_000_000L, "touched with not one pixel changed");
    }

    @Test
    @DisplayName("a changed picture is written, so the review still shows it")
    void aChangedPictureIsWritten(@TempDir Path dir) throws IOException {
        final File file = dir.resolve("sheet.png").toFile();
        PreviewImages.write(sheet(0x203040), "png", file);
        final BufferedImage moved = sheet(0x203040);
        moved.setRGB(3, 2, 0xFF0000);
        assertFalse(PreviewImages.samePicture(moved, file));
        PreviewImages.write(moved, "png", file);
        assertTrue(PreviewImages.samePicture(moved, file));
    }

    @Test
    @DisplayName("a missing or unreadable file is written")
    void aMissingFileIsWritten(@TempDir Path dir) throws IOException {
        final File file = dir.resolve("absent.png").toFile();
        assertFalse(PreviewImages.samePicture(sheet(0), file));
        Files.writeString(file.toPath(), "not a png", StandardCharsets.UTF_8);
        assertFalse(PreviewImages.samePicture(sheet(0), file));
    }

    @Test
    @DisplayName("no test writes a preview straight through ImageIO")
    void everyArtTestGoesThroughHere() throws IOException {
        final String direct = "ImageIO" + ".write(";
        final StringBuilder offenders = new StringBuilder();
        try (Stream<Path> files = Files.walk(Path.of("src/test/java"))) {
            files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.getFileName().toString().equals("PreviewImages.java"))
                    .forEach(p -> {
                        try {
                            final String code = Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n")
                                    .replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ");
                            if (code.contains(direct)) {
                                offenders.append('\n').append(p);
                            }
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    });
        }
        assertTrue(offenders.isEmpty(), "write previews with PreviewImages.write, or every build churns them:"
                + offenders);
    }
}
