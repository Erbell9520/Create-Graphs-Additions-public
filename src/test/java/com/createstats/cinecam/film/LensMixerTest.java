package com.createstats.cinecam.film;

import org.junit.jupiter.api.Test;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALCCapabilities;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LensMixerTest {
    private static final int RATE = LensMixer.RATE;

    private static LensMixer.Pcm tone(double seconds) {
        int n = (int) (RATE * seconds);
        ByteBuffer b = ByteBuffer.allocateDirect(n * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < n; i++) b.putShort((short) (Math.sin(2 * Math.PI * 440 * i / RATE) * 12000));
        b.flip();
        return new LensMixer.Pcm(b, 1, RATE);
    }

    private static boolean openAl() {
        try {
            ALC.getFunctionProvider();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static double[] listen(LensMixer.Pcm pcm, double x, double y, double z, float reach, long playFrom, long mixTo)
            throws InterruptedException {
        List<LensMixer.Chunk> got = new CopyOnWriteArrayList<>();
        LensMixer m = new LensMixer("test mixer", got::add, key -> CompletableFuture.completedFuture(pcm));
        m.listen(0, 0, 0, LensMixer.orientation(0, 0, -1));
        if (playFrom > 0) m.mix(playFrom);
        m.play(1, "tone", x, y, z, 1.0F, 1.0F, true, reach, false, playFrom);
        m.mix(mixTo);
        m.finish(mixTo);
        assumeTrue(m.opened(), "no loopback device here");
        List<short[]> all = new ArrayList<>();
        long frames = 0;
        for (LensMixer.Chunk c : got) {
            assertEquals(frames, c.at(), "pieces in order, back to back");
            frames += c.frames();
            all.add(c.pcm());
        }
        assertEquals(mixTo, frames, "mixed exactly as far as asked");
        double l = 0;
        double r = 0;
        long n = 0;
        long at = 0;
        for (short[] p : all) {
            for (int i = 0; i + 1 < p.length; i += 2, at++) {
                if (at < playFrom + (mixTo - playFrom) / 2) continue;
                l += p[i] * (double) p[i];
                r += p[i + 1] * (double) p[i + 1];
                n++;
            }
        }
        return new double[]{Math.sqrt(l / Math.max(1, n)), Math.sqrt(r / Math.max(1, n))};
    }

    private static void awaitMixed(List<LensMixer.Chunk> got, long frames) throws InterruptedException {
        long until = System.nanoTime() + 2_000_000_000L;
        while (System.nanoTime() < until) {
            long n = 0;
            for (LensMixer.Chunk c : got) n += c.frames();
            if (n >= frames) return;
            Thread.sleep(5);
        }
    }

    @Test
    void aSoundLandsWhereItIs() throws InterruptedException {
        assumeTrue(openAl(), "no OpenAL here");
        double[] left = listen(tone(1), -4, 0, 0, 16f, 0, RATE / 2);
        assertTrue(left[0] > 3000 && left[1] < left[0] / 4, "four blocks to the left: the left channel");
        double[] right = listen(tone(1), 4, 0, 0, 16f, 0, RATE / 2);
        assertTrue(right[1] > 3000 && right[0] < right[1] / 4, "to the right: the right channel");
        double[] ahead = listen(tone(1), 0, 0, -8, 16f, 0, RATE / 2);
        assertEquals(ahead[0], ahead[1], ahead[0] * 0.1, "straight ahead: both alike");
    }

    @Test
    void itFallsOffAsTheGamesDoes() throws InterruptedException {
        assumeTrue(openAl(), "no OpenAL here");
        double near = listen(tone(1), 0, 0, -2, 16f, 0, RATE / 2)[0];
        double half = listen(tone(1), 0, 0, -8, 16f, 0, RATE / 2)[0];
        double past = listen(tone(1), 0, 0, -20, 16f, 0, RATE / 2)[0];
        assertTrue(near > half && half > past, "nearer is louder");
        assertEquals(0.5, half / (near * 16 / 14), 0.1, "linear: half way out, half the level (of 2 blocks' 14/16)");
        assertTrue(past < 50, "past its reach: silence");
    }

    @Test
    void aLateSoundStartsFromWhereItWouldBe() throws InterruptedException {
        assumeTrue(openAl(), "no OpenAL here");
        List<LensMixer.Chunk> got = new CopyOnWriteArrayList<>();
        CompletableFuture<LensMixer.Pcm> later = new CompletableFuture<>();
        LensMixer m = new LensMixer("test mixer", got::add, key -> later);
        m.listen(0, 0, 0, LensMixer.orientation(0, 0, -1));

        m.play(1, "short", 0, 0, -2, 1.0F, 1.0F, true, 16f, false, 0);
        m.mix(RATE / 2);

        awaitMixed(got, RATE / 2);
        later.complete(tone(0.3));
        m.mix(RATE);
        m.finish(RATE);
        assumeTrue(m.opened(), "no loopback device here");
        assertTrue(rms(got) < NOISE, "a sound already over when its data came is not played late: rms " + rms(got));
    }

    private static final double NOISE = 4.0;

    private static double rms(List<LensMixer.Chunk> got) {
        double sum = 0;
        long n = 0;
        for (LensMixer.Chunk c : got) {
            for (short s : c.pcm()) {
                sum += s * (double) s;
                n++;
            }
        }
        return Math.sqrt(sum / Math.max(1, n));
    }

    @Test
    void aSoundStoppedBeforeItsDataComesNeverPlays() throws InterruptedException {
        assumeTrue(openAl(), "no OpenAL here");
        List<LensMixer.Chunk> got = new CopyOnWriteArrayList<>();
        CompletableFuture<LensMixer.Pcm> later = new CompletableFuture<>();
        LensMixer m = new LensMixer("test mixer", got::add, key -> later);
        m.listen(0, 0, 0, LensMixer.orientation(0, 0, -1));
        m.play(1, "loop", 0, 0, -2, 1.0F, 1.0F, true, 16f, true, 0);
        m.stop(1);
        m.mix(RATE / 10);
        awaitMixed(got, RATE / 10);
        later.complete(tone(1));
        m.mix(RATE / 2);
        m.finish(RATE / 2);
        assumeTrue(m.opened(), "no loopback device here");
        assertTrue(rms(got) < NOISE, "stopped first, so it never starts: rms " + rms(got));
    }

    @Test
    void aSoundArrivingLateButInTimePlays() throws InterruptedException {
        assumeTrue(openAl(), "no OpenAL here");
        List<LensMixer.Chunk> got = new CopyOnWriteArrayList<>();
        CompletableFuture<LensMixer.Pcm> later = new CompletableFuture<>();
        LensMixer m = new LensMixer("test mixer", got::add, key -> later);
        m.listen(0, 0, 0, LensMixer.orientation(0, 0, -1));
        m.play(1, "long", 0, 0, -2, 1.0F, 1.0F, true, 16f, false, 0);
        m.mix(RATE / 10);
        awaitMixed(got, RATE / 10);
        later.complete(tone(1));
        m.mix(RATE / 2);
        m.finish(RATE / 2);
        assumeTrue(m.opened(), "no loopback device here");
        assertTrue(rms(got) > 1000, "a second long, a tenth of it gone when it came: the rest plays");
    }

    @Test
    void theGamesOwnSoundIsLeftAlone() throws InterruptedException {
        assumeTrue(openAl(), "no OpenAL here");
        long dev = ALC10.alcOpenDevice((ByteBuffer) null);
        assumeTrue(dev != 0L, "no playback device here");
        long ctx = 0L;
        try {
            ALCCapabilities caps = ALC.createCapabilities(dev);
            ctx = ALC10.alcCreateContext(dev, (IntBuffer) null);
            ALC10.alcMakeContextCurrent(ctx);
            AL.createCapabilities(caps);
            int gameSource = AL10.alGenSources();
            double[] mixed = listen(tone(1), -4, 0, 0, 16f, 0, RATE / 4);
            assertTrue(mixed[0] > 1000, "the camera mixed");
            assertEquals(ctx, ALC10.alcGetCurrentContext(), "the game's context is still the current one");
            assertTrue(AL10.alIsSource(gameSource), "and its sources are still its own");
            AL10.alDeleteSources(gameSource);
        } finally {
            ALC10.alcMakeContextCurrent(0L);
            if (ctx != 0L) ALC10.alcDestroyContext(ctx);
            ALC10.alcCloseDevice(dev);
        }
    }

    @Test
    void theListenerLooksAlongTheLens() {
        float[] o = LensMixer.orientation(0, 0, -1);
        assertEquals(0f, o[0], 1e-6f);
        assertEquals(-1f, o[2], 1e-6f);
        assertEquals(1f, o[4], 1e-6f, "level: up is up");
        float[] down = LensMixer.orientation(0, -1, 0);
        assertEquals(1f, Math.abs(down[5]), 1e-6f, "straight down: up is north or south, never nothing");
        double c = Math.sqrt(0.5);
        float[] tilted = LensMixer.orientation(0, c, -c);
        double dot = tilted[0] * tilted[3] + tilted[1] * tilted[4] + tilted[2] * tilted[5];
        assertEquals(0.0, dot, 1e-6, "up square to forward");
    }
}
