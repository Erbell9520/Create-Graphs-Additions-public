package com.createstats.cinecam.film;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@EnabledOnOs(OS.WINDOWS)
class MediaFoundationWriterTest {
    @TempDir
    Path dir;

    @Test
    void aFilmWithSoundHasBothTracks() throws IOException {
        assumeTrue(MediaFoundationWriter.available(), "no Media Foundation here");
        String keep = System.getProperty("cine.keep");
        Path file = (keep == null ? dir : Path.of(keep)).resolve("sound.mp4");
        int w = 256;
        int h = 144;
        int rate = 48000;
        byte[] frame = new byte[w * h * 3 / 2];
        MediaFoundationWriter mw = new MediaFoundationWriter(file, w, h, 30, 2_000_000, rate);
        try {
            assertTrue(mw.hasAudio(), "a sound stream was added");
            long at = 0;
            for (int i = 0; i < 30; i++) {
                Arrays.fill(frame, 0, w * h, (byte) (16 + i * 6));
                Arrays.fill(frame, w * h, frame.length, (byte) 128);
                mw.write(frame);
                int n = rate / 30;
                short[] pcm = new short[n * 2];
                for (int k = 0; k < n; k++) {
                    short v = (short) (Math.sin(2 * Math.PI * 440 * (at + k) / rate) * 8000);
                    pcm[2 * k] = v;
                    pcm[2 * k + 1] = (short) (v / 4);
                }
                mw.writeAudio(pcm, n, at);
                at += n;
            }
            assertEquals(rate, mw.audioFrames(), "a second of sound");
            assertEquals(30, mw.frames());
        } finally {
            mw.close();
        }
        String bytes = new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(bytes.contains("mp4a"), "an AAC sound track in the file");
        assertTrue(bytes.contains("avc1"), "beside the H.264 picture");
    }

    @Test
    void aFilmWithoutSoundHasNoSoundTrack() throws IOException {
        assumeTrue(MediaFoundationWriter.available(), "no Media Foundation here");
        Path file = dir.resolve("silent.mp4");
        MediaFoundationWriter mw = new MediaFoundationWriter(file, 256, 144, 30, 2_000_000, 0);
        try {
            assertFalse(mw.hasAudio());
            mw.writeAudio(new short[64], 32, 0);
            assertEquals(0, mw.audioFrames(), "sound for a film without a track is dropped, not an error");
            mw.write(new byte[256 * 144 * 3 / 2]);
        } finally {
            mw.close();
        }
        assertFalse(new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.ISO_8859_1).contains("mp4a"));
    }

    @Test
    void aShortFilmIsWrittenAndClosed() throws IOException {
        assumeTrue(MediaFoundationWriter.available(), "no Media Foundation here");
        Path file = dir.resolve("test.mp4");
        int w = 256;
        int h = 144;
        byte[] frame = new byte[w * h * 3 / 2];
        MediaFoundationWriter mw = new MediaFoundationWriter(file, w, h, 60, 2_000_000);
        try {
            assertNotNull(mw.encoder(), "Media Foundation names the encoder it put on the stream");
            for (int i = 0; i < 30; i++) {
                Arrays.fill(frame, 0, w * h, (byte) (16 + i * 6));
                Arrays.fill(frame, w * h, frame.length, (byte) 128);
                mw.write(frame);
            }
            assertEquals(30, mw.frames());
        } finally {
            mw.close();
        }
        byte[] head = Arrays.copyOf(Files.readAllBytes(file), 12);
        assertTrue(Files.size(file) > 1000, "a file with frames in it");
        assertEquals("ftyp", new String(head, 4, 4, java.nio.charset.StandardCharsets.US_ASCII), "an MP4 box first");
    }

    @Test
    void aMappedFrameIsCopiedOnceAndHeldFramesCopyNothing() throws IOException {
        assumeTrue(MediaFoundationWriter.available(), "no Media Foundation here");
        int w = 320;
        int h = 180;
        java.nio.ByteBuffer mapped = java.nio.ByteBuffer.allocateDirect(w * h * 3 / 2);
        int[] given = new int[1];
        MediaFoundationWriter mw = new MediaFoundationWriter(dir.resolve("mapped.mp4"), w, h, 60, 2_000_000);
        try {
            mw.repeat();
            assertEquals(0, mw.frames(), "nothing to hold before the first frame");
            for (int i = 0; i < 10; i++) {
                mapped.clear();
                while (mapped.hasRemaining()) mapped.put((byte) (16 + i * 20));
                mapped.position(17);
                mw.write(mapped, () -> given[0]++);
                mw.repeat();
                mw.repeat();
            }
            assertEquals(10, given[0], "every buffer given back, once");
            assertEquals(30, mw.frames(), "ten frames, each held for two more slots");
        } finally {
            mw.close();
        }
        assertThrows(IOException.class, () -> mw.write(mapped, () -> given[0]++), "a closed writer refuses");
        assertEquals(11, given[0], "and still gives the buffer back");
    }

    @Test
    void anOddFrameIsRefusedBeforeAnythingNativeRuns() {
        assertThrows(IOException.class, () -> new MediaFoundationWriter(dir.resolve("odd.mp4"), 255, 144, 60, 1_000_000));
    }
}
