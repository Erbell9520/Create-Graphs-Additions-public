package com.createstats.cinecam.film;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTThreadLocalContext;
import org.lwjgl.openal.SOFTLoopback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

// openal loopback, plays nothing aloud, hands the mix back
public final class LensMixer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Cine Camera sound");

    public static final int RATE = 48000;

    private static final int CHUNK = RATE / 10;
    private static final int AL_SOURCE_DISTANCE_MODEL = 0x200;
    private static final int AL_LINEAR_DISTANCE = 0xD003;

    public record Chunk(short[] pcm, int frames, long at) {
    }

    public record Pcm(ByteBuffer data, int channels, int rate) {
    }

    private final Consumer<Chunk> out;
    private final Function<String, CompletableFuture<Pcm>> decoder;
    private final LinkedBlockingQueue<Runnable> todo = new LinkedBlockingQueue<>();
    private final Thread thread;
    private volatile boolean closing;
    private volatile boolean opened;
    private static final Runnable STOP = () -> {
    };

    private long device;
    private long context;
    private long cursor;
    private final Map<Integer, Integer> sources = new HashMap<>();
    private final Set<Integer> pending = new HashSet<>();
    private final Set<Integer> cancelled = new HashSet<>();
    private final Map<String, Integer> buffers = new HashMap<>();
    private final Map<String, List<Runnable>> waiting = new HashMap<>();

    public LensMixer(String name, Consumer<Chunk> out, Function<String, CompletableFuture<Pcm>> decoder) {
        this.out = out;
        this.decoder = decoder;
        this.thread = new Thread(this::run, name);
        this.thread.setDaemon(true);
        this.thread.start();
    }

    public boolean opened() {
        return opened;
    }

    public void play(int id, String key, double x, double y, double z, float gain, float pitch, boolean linear,
                     float reach, boolean loop, long from) {
        post(() -> start(id, key, (float) x, (float) y, (float) z, gain, pitch, linear, reach, loop, from));
    }

    public void move(int id, double x, double y, double z, float gain, float pitch) {
        post(() -> moveNow(id, (float) x, (float) y, (float) z, gain, pitch));
    }

    public void stop(int id) {
        post(() -> stopNow(id));
    }

    public void listen(double x, double y, double z, float[] orientation) {
        float fx = (float) x;
        float fy = (float) y;
        float fz = (float) z;
        float[] o = orientation.clone();
        post(() -> {
            if (context == 0L) return;
            AL10.alListener3f(AL10.AL_POSITION, fx, fy, fz);
            AL10.alListenerfv(AL10.AL_ORIENTATION, o);
        });
    }

    public void mix(long to) {
        post(() -> mixNow(to));
    }

    public void finish(long end) {
        if (closing) return;
        closing = true;
        todo.add(() -> mixNow(end));
        todo.add(STOP);
        try {
            thread.join(TimeUnit.SECONDS.toMillis(2));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static float[] orientation(double fx, double fy, double fz) {
        double ux = -fx * fy;
        double uy = 1.0 - fy * fy;
        double uz = -fz * fy;
        double l = Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (l < 1e-6) {
            ux = 0.0;
            uy = 0.0;
            uz = fy > 0 ? 1.0 : -1.0;
            l = 1.0;
        }
        return new float[]{(float) fx, (float) fy, (float) fz, (float) (ux / l), (float) (uy / l), (float) (uz / l)};
    }

    private void post(Runnable r) {
        if (closing) return;
        todo.add(r);
    }

    private void run() {
        try {
            open();
            while (true) {
                Runnable r = todo.take();
                if (r == STOP) break;
                try {
                    r.run();
                } catch (RuntimeException e) {
                    LOGGER.warn("Cine Camera: the camera's sound skipped a step", e);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            close();
        }
    }

    private void open() {
        try {
            device = SOFTLoopback.alcLoopbackOpenDeviceSOFT((ByteBuffer) null);
            if (device == 0L) throw new IllegalStateException("no loopback device");
            if (!SOFTLoopback.alcIsRenderFormatSupportedSOFT(device, RATE, SOFTLoopback.ALC_STEREO_SOFT, SOFTLoopback.ALC_SHORT_SOFT)) {
                throw new IllegalStateException("48 kHz stereo 16-bit is not supported");
            }
            IntBuffer attrs = ByteBuffer.allocateDirect(7 * 4).order(ByteOrder.nativeOrder()).asIntBuffer();
            attrs.put(SOFTLoopback.ALC_FORMAT_CHANNELS_SOFT).put(SOFTLoopback.ALC_STEREO_SOFT)
                    .put(SOFTLoopback.ALC_FORMAT_TYPE_SOFT).put(SOFTLoopback.ALC_SHORT_SOFT)
                    .put(ALC10.ALC_FREQUENCY).put(RATE).put(0).flip();
            context = ALC10.alcCreateContext(device, attrs);
            if (context == 0L) throw new IllegalStateException("no loopback context");
            if (!EXTThreadLocalContext.alcSetThreadContext(context)) throw new IllegalStateException("no thread context");
            AL.createCapabilities(ALC.getCapabilities());
            AL10.alEnable(AL_SOURCE_DISTANCE_MODEL);
            AL10.alListenerf(AL10.AL_GAIN, 1.0F);
            opened = true;
        } catch (RuntimeException | LinkageError e) {
            LOGGER.warn("Cine Camera: the camera's sound could not open, so this film is silent", e);
            close();
        }
    }

    private void close() {
        try {
            if (context != 0L) {
                for (int src : sources.values()) AL10.alDeleteSources(src);
                for (int buf : buffers.values()) {
                    if (buf != 0) AL10.alDeleteBuffers(buf);
                }
                EXTThreadLocalContext.alcSetThreadContext(0L);
                ALC10.alcDestroyContext(context);
            }
            if (device != 0L) ALC10.alcCloseDevice(device);
        } catch (RuntimeException | LinkageError e) {
            LOGGER.warn("Cine Camera: the camera's sound did not close cleanly", e);
        } finally {
            sources.clear();
            buffers.clear();
            context = 0L;
            device = 0L;
        }
    }

    private void mixNow(long to) {
        while (cursor < to) {
            int n = (int) Math.min(CHUNK, to - cursor);
            short[] pcm = new short[n * 2];
            if (context != 0L) SOFTLoopback.alcRenderSamplesSOFT(device, pcm, n);
            out.accept(new Chunk(pcm, n, cursor));
            cursor += n;
        }
        if (context == 0L) return;

        for (Iterator<Map.Entry<Integer, Integer>> it = sources.entrySet().iterator(); it.hasNext(); ) {
            int src = it.next().getValue();
            if (AL10.alGetSourcei(src, AL10.AL_SOURCE_STATE) == AL10.AL_STOPPED) {
                AL10.alDeleteSources(src);
                it.remove();
            }
        }
    }

    private void start(int id, String key, float x, float y, float z, float gain, float pitch, boolean linear, float reach,
                       boolean loop, long from) {
        pending.remove(id);
        if (context == 0L || cancelled.remove(id)) return;
        Integer buffer = buffers.get(key);
        if (buffer == null) {
            pending.add(id);
            Runnable later = () -> start(id, key, x, y, z, gain, pitch, linear, reach, loop, from);
            List<Runnable> w = waiting.get(key);
            if (w != null) {
                w.add(later);
                return;
            }
            waiting.put(key, new ArrayList<>(List.of(later)));
            CompletableFuture<Pcm> f;
            try {
                f = decoder.apply(key);
            } catch (RuntimeException e) {
                f = CompletableFuture.completedFuture(null);
            }
            if (f.isDone()) {
                arrived(key, f.isCompletedExceptionally() ? null : f.getNow(null));
                return;
            }
            f.whenComplete((pcm, err) -> {
                if (!closing) todo.add(() -> arrived(key, err == null ? pcm : null));
            });
            return;
        }
        if (buffer == 0) return;
        int src = AL10.alGenSources();
        if (linear) {
            AL10.alSourcei(src, AL10.AL_DISTANCE_MODEL, AL_LINEAR_DISTANCE);
            AL10.alSourcef(src, AL10.AL_MAX_DISTANCE, reach);
            AL10.alSourcef(src, AL10.AL_ROLLOFF_FACTOR, 1.0F);
            AL10.alSourcef(src, AL10.AL_REFERENCE_DISTANCE, 0.0F);
        } else {
            AL10.alSourcei(src, AL10.AL_DISTANCE_MODEL, AL10.AL_NONE);
        }
        AL10.alSourcef(src, AL10.AL_PITCH, pitch);
        AL10.alSourcef(src, AL10.AL_GAIN, gain);
        AL10.alSourcei(src, AL10.AL_LOOPING, loop ? AL10.AL_TRUE : AL10.AL_FALSE);
        AL10.alSource3f(src, AL10.AL_POSITION, x, y, z);
        AL10.alSourcei(src, AL10.AL_BUFFER, buffer);
        long late = cursor - from;
        if (late > 0) {
            float seconds = late / (float) RATE * pitch;
            float length = length(buffer);
            if (!loop && seconds >= length) {
                AL10.alDeleteSources(src);
                return;
            }
            AL10.alSourcef(src, AL11.AL_SEC_OFFSET, loop && length > 0 ? seconds % length : seconds);
        }
        AL10.alSourcePlay(src);
        sources.put(id, src);
    }

    private void arrived(String key, @Nullable Pcm pcm) {
        List<Runnable> w = waiting.remove(key);
        if (context == 0L) return;
        int buffer = 0;
        if (pcm != null && (pcm.channels() == 1 || pcm.channels() == 2) && pcm.data().remaining() > 0) {
            buffer = AL10.alGenBuffers();
            AL10.alBufferData(buffer, pcm.channels() == 2 ? AL10.AL_FORMAT_STEREO16 : AL10.AL_FORMAT_MONO16, pcm.data(), pcm.rate());
        }
        buffers.put(key, buffer);
        if (w != null) w.forEach(Runnable::run);
    }

    private static float length(int buffer) {
        int bytes = AL10.alGetBufferi(buffer, AL10.AL_SIZE);
        int channels = Math.max(1, AL10.alGetBufferi(buffer, AL10.AL_CHANNELS));
        int bits = Math.max(8, AL10.alGetBufferi(buffer, AL10.AL_BITS));
        int rate = Math.max(1, AL10.alGetBufferi(buffer, AL10.AL_FREQUENCY));
        return bytes / (float) (channels * (bits / 8) * rate);
    }

    private void moveNow(int id, float x, float y, float z, float gain, float pitch) {
        Integer src = sources.get(id);
        if (src == null || context == 0L) return;
        AL10.alSource3f(src, AL10.AL_POSITION, x, y, z);
        AL10.alSourcef(src, AL10.AL_GAIN, gain);
        AL10.alSourcef(src, AL10.AL_PITCH, pitch);
    }

    private void stopNow(int id) {
        Integer src = sources.remove(id);
        if (src == null) {
            if (pending.contains(id)) cancelled.add(id);
            return;
        }
        if (context == 0L) return;
        AL10.alSourceStop(src);
        AL10.alDeleteSources(src);
    }
}
