package com.createstats.cinecam.client;

import com.createstats.CreateStats;
import com.createstats.cinecam.CineCameraBE;
import com.createstats.cinecam.core.FilmClock;
import com.createstats.cinecam.core.FilmFormat;
import com.createstats.cinecam.film.MediaFoundationWriter;
import com.createstats.cinecam.film.Mp4Faststart;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// gpu does the nv12 conversion, async readback, hardware encoder. the game barely notices
@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT)
public final class CineRecorder {
    static final long BUFFER_BYTES = 192L * 1024 * 1024;
    private static final int MIN_PBOS = 8;
    private static final int MAX_PBOS = 64;

    private static final long RELEASE_WAIT_NANOS = 200_000_000L;
    static final String FOLDER = "cine_films";

    enum State { IDLE, STARTING, RECORDING, SAVING }

    @Nullable private static Session session;

    private static final List<Session> PARKED = new ArrayList<>();
    private static final AtomicInteger SAVING = new AtomicInteger();
    @Nullable static volatile String savedName;
    static volatile long savedAtMs = -1;

    private static long planned = -1;

    private CineRecorder() {
    }

    public static boolean recording() {
        return session != null;
    }

    static State state() {
        Session s = session;
        if (s != null) return s.readyAt < 0 ? State.STARTING : State.RECORDING;
        return SAVING.get() > 0 ? State.SAVING : State.IDLE;
    }

    static long filmedMs() {
        Session s = session;
        return s == null || s.readyAt < 0 ? 0 : (s.lastFrame + 1) * 1000L / s.fps;
    }

    static int fps() {
        Session s = session;
        return s == null ? 0 : s.fps;
    }

    @Nullable
    static FilmFormat.Speed speed() {
        Session s = session;
        return s == null ? null : s.speed;
    }

    static double aspect() {
        Session s = session;
        return s == null ? 0.0 : s.shape.aspect();
    }

    @Nullable
    static FilmFormat.Shape shape() {
        Session s = session;
        return s == null ? null : s.shape;
    }

    @Nullable
    static int[] size() {
        Session s = session;
        return s == null ? null : new int[]{s.w, s.h};
    }

    static Path folder(Minecraft mc) {
        return mc.gameDirectory.toPath().resolve(FOLDER);
    }

    static boolean start(Minecraft mc, CineCameraBE cam) {
        if (session != null) return true;
        if (!MediaFoundationWriter.available()) {
            say(mc, Component.translatable("create_stats.cine_camera.film.unsupported").withStyle(ChatFormatting.GOLD));
            return false;
        }
        try {
            RenderTarget main = mc.getMainRenderTarget();
            FilmFormat.Shape shape = cam.filmShape();
            int[] size = FilmFormat.size(cam.filmResolution(), shape.aspect(), main.width, main.height);
            int fps = cam.filmRate();
            int bitrate = FilmFormat.bitrate(size[0], size[1], fps, cam.filmQuality());
            Path dir = folder(mc);
            Files.createDirectories(dir);

            Mp4Faststart.clearLeftovers(dir);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss"));

            String subject = cam.targetKind() == CineCameraBE.KIND_NONE ? "" : FilmFormat.fileSafe(cam.targetName());
            String base = subject.isEmpty() ? stamp : stamp + "_" + subject;
            Path file = dir.resolve(base + ".mp4");
            for (int n = 2; Files.exists(file); n++) file = dir.resolve(base + "_" + n + ".mp4");

            boolean sound = cam.filmSound() && cam.filmSpeed() == FilmFormat.Speed.NORMAL;
            session = new Session(mc, file, size[0], size[1], fps, bitrate, shape, cam.filmSpeed(), sound);
            planned = -1;
            return true;
        } catch (IOException | RuntimeException e) {
            CreateStats.LOGGER.error("Cine Camera: the film could not start", e);
            say(mc, Component.translatable("create_stats.cine_camera.film.failed", String.valueOf(e.getMessage()))
                    .withStyle(ChatFormatting.RED));
            session = null;
            return false;
        }
    }

    static void stop() {
        Session s = session;
        session = null;
        planned = -1;
        if (s != null) s.finish();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeGui(RenderGuiEvent.Pre event) {
        if (!PARKED.isEmpty()) PARKED.removeIf(Session::tryFree);
        Session s = session;
        if (s == null) return;
        if (s.failed) {
            session = null;
            s.finish();
            return;
        }
        s.pump();
        long frame = planned;
        planned = -1;
        if (frame >= 0 && CineView.inView()) s.captureFrame(Minecraft.getInstance(), frame);
    }

    @Nullable
    static FilmClock.Plan plan(long tick, float partial, double nextNow, boolean inView) {
        List<FilmClock.Plan> d = draws(tick, partial, nextNow, inView, 1);
        return d.isEmpty() ? null : d.get(0);
    }

    static List<FilmClock.Plan> draws(long tick, float partial, double nextNow, boolean inView, int max) {
        Session s = session;
        planned = -1;
        if (s == null || s.failed || s.readyAt < 0) return List.of();
        if (Double.isNaN(s.start)) s.start = tick - 1 + partial;
        List<FilmClock.Plan> d = FilmClock.draws(s.start, s.tpf, s.lastFrame, tick, partial, nextNow, max);
        if (d.size() > s.free.size()) {
            s.starved += d.size();
            return List.of();
        }
        s.count(d);
        if (!d.isEmpty() && inView) planned = d.get(d.size() - 1).frame();
        return d;
    }

    static List<FilmClock.Plan> catchUp(long tick, int max) {
        Session s = session;
        if (s == null || s.failed || s.readyAt < 0 || Double.isNaN(s.start)) return List.of();
        List<FilmClock.Plan> d = FilmClock.catchUp(s.start, s.tpf, s.lastFrame, tick, Math.min(max, s.free.size()));
        s.count(d);
        return d;
    }

    static void caughtUp(int n) {
        Session s = session;
        if (s != null) s.caughtUp += n;
    }

    static void extraPasses(int n) {
        Session s = session;
        if (s != null) s.extra += n;
    }

    static void soundFrame(Vec3 eye, double[] forward, double moment) {
        Session s = session;
        if (s == null || s.sound == null || Double.isNaN(s.start)) return;
        double seconds = (moment - s.start) / 20.0;
        if (seconds > 0) s.sound.frame(eye, forward, Math.round(seconds * CineSound.RATE));
    }

    static void soundTick() {
        Session s = session;
        if (s != null && s.sound != null) s.sound.tick();
    }

    @Nullable
    static int[] filmBox(int sw, int sh, boolean blur) {
        Session s = session;
        if (s == null) return null;

        float k = Math.min(sw / (float) s.w, sh / (float) s.h);
        float cw = s.w * k;
        float ch = s.h * k;
        int grow = 1 + (blur ? (int) Math.ceil(com.createstats.cinecam.core.FocusPull.maxCircle(sh)) + 1 : 0);
        int x0 = Math.max(0, (int) Math.floor((sw - cw) / 2f) - 1);
        int x1 = Math.min(sw, (int) Math.ceil((sw + cw) / 2f) + 1);
        int y0 = Math.max(0, (int) Math.floor((sh - ch) / 2f) - grow);
        int y1 = Math.min(sh, (int) Math.ceil((sh + ch) / 2f) + grow);
        return new int[]{x0, y0, x1 - x0, y1 - y0};
    }

    static void captureAway(Minecraft mc, long frame) {
        Session s = session;
        if (s != null && !s.failed) s.captureFrame(mc, frame);
    }

    private static void say(Minecraft mc, Component c) {
        if (mc.player != null) mc.player.displayClientMessage(c, false);
    }

    private sealed interface Item permits Frame, Sound, End {
    }

    private record Frame(int pbo, ByteBuffer data, long slot) implements Item {
    }

    private record Sound(short[] pcm, int frames, long at) implements Item {
    }

    private record End(long slot) implements Item {
    }

    private static final class Session {
        final Path file;
        final int w;
        final int h;
        final int fps;
        final FilmFormat.Shape shape;
        final FilmFormat.Speed speed;

        final double tpf;

        double start = Double.NaN;
        final int frameBytes;
        final FloatTarget target;
        final EffectInstance nv12;

        final int pbos;
        final int[] pbo;
        final long[] fence;
        final long[] pboSlot;
        final boolean[] mapped;
        final ArrayDeque<Integer> free = new ArrayDeque<>();
        final ArrayDeque<Integer> pending = new ArrayDeque<>();
        final ConcurrentLinkedQueue<Integer> released = new ConcurrentLinkedQueue<>();

        final BlockingQueue<Item> queue = new LinkedBlockingQueue<>();
        final CountDownLatch done = new CountDownLatch(1);
        final Thread hook;

        @Nullable final CineSound sound;
        volatile long readyAt = -1;
        volatile boolean failed;

        volatile boolean closing;

        volatile long lastFrame = -1;
        boolean finished;
        boolean freed;

        long held;
        long late;
        long extra;
        long caughtUp;
        long starved;

        Session(Minecraft mc, Path file, int w, int h, int fps, int bitrate, FilmFormat.Shape shape, FilmFormat.Speed speed,
                boolean withSound) throws IOException {
            this.file = file;
            this.w = w;
            this.h = h;
            this.fps = fps;
            this.shape = shape;
            this.speed = speed;
            this.tpf = FilmClock.ticksPerFrame(fps, speed.factor());
            this.frameBytes = w * h * 3 / 2;
            this.pbos = (int) Math.max(MIN_PBOS, Math.min(MAX_PBOS, BUFFER_BYTES / Math.max(1, frameBytes)));
            this.pbo = new int[pbos];
            this.fence = new long[pbos];
            this.pboSlot = new long[pbos];
            this.mapped = new boolean[pbos];
            this.nv12 = new EffectInstance(mc.getResourceManager(), CreateStats.MOD_ID + ":cine_nv12");
            this.target = FloatTarget.bytes(w, h * 3 / 2);
            this.sound = withSound ? CineSound.open(mc, c -> queue.offer(new Sound(c.pcm(), c.frames(), c.at()))) : null;
            for (int i = 0; i < pbos; i++) {
                pbo[i] = GL15.glGenBuffers();
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo[i]);
                GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, frameBytes, GL15.GL_STREAM_READ);
                free.add(i);
            }
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            Thread encoder = new Thread(() -> encode(bitrate), "Cine Camera film");
            encoder.setDaemon(true);

            hook = new Thread(() -> {
                closing = true;
                queue.clear();
                queue.offer(new End(lastFrame));
                try {
                    done.await(3, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }, "Cine Camera film shutdown");
            Runtime.getRuntime().addShutdownHook(hook);
            SAVING.incrementAndGet();
            encoder.start();
        }

        void pump() {
            unmapReleased();
            while (!pending.isEmpty() && signalled(pending.peekFirst())) handOver(pending.pollFirst());
        }

        void count(List<FilmClock.Plan> d) {
            for (FilmClock.Plan p : d) {
                held += p.missed();
                if (p.late()) late++;
            }
        }

        void captureFrame(Minecraft mc, long frame) {
            if (readyAt < 0 || frame <= lastFrame || free.isEmpty()) return;
            int i = free.pollFirst();
            capture(mc, i);
            fence[i] = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            pboSlot[i] = frame;
            pending.addLast(i);
            lastFrame = frame;
        }

        private boolean signalled(int i) {
            return GL32.glGetSynci(fence[i], GL32.GL_SYNC_STATUS, null) == GL32.GL_SIGNALED;
        }

        private void capture(Minecraft mc, int i) {
            RenderTarget main = mc.getMainRenderTarget();
            float sw = main.width;
            float sh = main.height;

            float k = Math.min(sw / w, sh / h);
            float cw = w * k;
            float ch = h * k;
            int colour = main.getColorTextureId();

            int filter = main.filterMode;
            if (filter != GL11.GL_LINEAR) main.setFilterMode(GL11.GL_LINEAR);
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.colorMask(true, true, true, true);
            nv12.setSampler("DiffuseSampler", () -> colour);
            nv12.safeGetUniform("SrcSize").set(sw, sh);
            nv12.safeGetUniform("Crop").set((float) Math.floor((sw - cw) / 2f), (float) Math.floor((sh - ch) / 2f), cw, ch);
            nv12.safeGetUniform("OutSize").set((float) w, (float) h);
            CineFocus.draw(nv12, target);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
            GlStateManager._pixelStore(GL11.GL_PACK_ALIGNMENT, 4);
            GlStateManager._pixelStore(GL11.GL_PACK_ROW_LENGTH, 0);
            GlStateManager._pixelStore(GL11.GL_PACK_SKIP_PIXELS, 0);
            GlStateManager._pixelStore(GL11.GL_PACK_SKIP_ROWS, 0);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo[i]);
            GL11.glReadPixels(0, 0, w, h * 3 / 2, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, 0L);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            if (filter != GL11.GL_LINEAR) main.setFilterMode(filter);
            main.bindWrite(true);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        }

        private void handOver(int i) {
            GL32.glDeleteSync(fence[i]);
            fence[i] = 0;
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo[i]);
            ByteBuffer data = GL15.glMapBuffer(GL21.GL_PIXEL_PACK_BUFFER, GL15.GL_READ_ONLY, frameBytes, null);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            if (data == null) {
                free.addLast(i);
                return;
            }
            mapped[i] = true;
            queue.add(new Frame(i, data, pboSlot[i]));
        }

        private void unmapReleased() {
            Integer i;
            while ((i = released.poll()) != null) {
                unmap(i);
                free.addLast(i);
            }
        }

        private void unmap(int i) {
            if (!mapped[i]) return;
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo[i]);
            GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            mapped[i] = false;
        }

        private boolean anyMapped() {
            for (boolean m : mapped) {
                if (m) return true;
            }
            return false;
        }

        void finish() {
            if (finished) return;
            finished = true;
            while (!pending.isEmpty()) {
                int i = pending.pollFirst();
                GL32.glClientWaitSync(fence[i], GL32.GL_SYNC_FLUSH_COMMANDS_BIT, 100_000_000L);
                handOver(i);
            }

            long end = Double.isNaN(start) ? lastFrame
                    : Math.max(lastFrame, FilmClock.frameAt(start, CineView.nowTicks(), tpf));

            if (sound != null) sound.finish(Math.max(0L, Math.round((end + 1) * (double) CineSound.RATE / fps)));
            queue.add(new End(end));
            long until = System.nanoTime() + RELEASE_WAIT_NANOS;
            while (anyMapped() && System.nanoTime() < until && done.getCount() > 0) {
                unmapReleased();
                java.util.concurrent.locks.LockSupport.parkNanos(100_000L);
            }
            if (!tryFree()) PARKED.add(this);
        }

        boolean tryFree() {
            if (freed) return true;
            unmapReleased();
            if (anyMapped() && done.getCount() > 0) return false;
            for (int i = 0; i < pbos; i++) {
                unmap(i);
                if (fence[i] != 0) GL32.glDeleteSync(fence[i]);
                GL15.glDeleteBuffers(pbo[i]);
            }
            target.destroyBuffers();
            nv12.close();
            freed = true;
            return true;
        }

        private void encode(int bitrate) {
            MediaFoundationWriter mw = null;
            String result = null;
            boolean ok = false;
            boolean indexed = false;
            long frames = 0;
            String encoderName = null;
            try {
                int audioRate = sound != null ? CineSound.RATE : 0;
                try {
                    mw = new MediaFoundationWriter(file, w, h, fps, bitrate, audioRate);
                } catch (IOException e) {
                    if (audioRate == 0) throw e;

                    CreateStats.LOGGER.warn("Cine Camera: the film's sound track could not be opened, so this take is silent", e);
                    mw = new MediaFoundationWriter(file, w, h, fps, bitrate, 0);
                }
                encoderName = mw.encoder() + (mw.hardware() ? "" : " (software)");
                CreateStats.LOGGER.info("Cine Camera: filming {}x{} at {} fps, {} kbit/s{}, {} buffers, into {} with {}", w, h,
                        fps, bitrate / 1000, mw.hasAudio() ? ", sound 48 kHz stereo AAC 192 kbit/s" : ", no sound", pbos,
                        file, encoderName);
                readyAt = System.nanoTime();
                long lastSlot = -1;
                while (true) {
                    Item item = queue.take();
                    if (item instanceof End e) {
                        if (lastSlot >= 0 && e.slot() >= 0) {
                            for (long r = FilmClock.repeatsBefore(lastSlot, e.slot() + 1, fps); r > 0; r--) mw.repeat();
                        }
                        break;
                    }
                    if (item instanceof Sound s) {
                        mw.writeAudio(s.pcm(), s.frames(), s.at());
                        continue;
                    }
                    Frame f = (Frame) item;
                    for (long r = FilmClock.repeatsBefore(lastSlot, f.slot(), fps); r > 0; r--) mw.repeat();
                    int index = f.pbo();
                    mw.write(f.data(), () -> released.add(index));
                    lastSlot = f.slot();
                }
                frames = mw.frames();
                boolean fragmented = mw.fragmented();
                mw.close();
                mw = null;
                ok = true;
                if (fragmented && frames > 0 && !closing) indexed = index();
            } catch (IOException | RuntimeException | InterruptedException e) {
                CreateStats.LOGGER.error("Cine Camera: the film could not be written", e);
                result = e.getMessage();
                failed = true;
            } finally {
                for (Item it; (it = queue.poll()) != null; ) {
                    if (it instanceof Frame f) released.add(f.pbo());
                }
                if (mw != null) {
                    try {
                        mw.close();
                    } catch (IOException ignored) {
                    }
                }
                try {
                    Runtime.getRuntime().removeShutdownHook(hook);
                } catch (IllegalStateException ignored) {
                }
                SAVING.decrementAndGet();
                done.countDown();
                report(ok, frames, result, encoderName, indexed);
            }
        }

        // filmed fragmented so a crash still leaves a film, rewritten indexed because media player jumps every 2-3s on fragmented mp4. that one cost a whole evening
        private boolean index() {
            long t0 = System.nanoTime();
            try {
                Mp4Faststart.rewriteInPlace(file, () -> closing);
                CreateStats.LOGGER.info("Cine Camera: {} indexed for every player in {} ms", file.getFileName(),
                        (System.nanoTime() - t0) / 1_000_000);
                return true;
            } catch (IOException | RuntimeException e) {
                CreateStats.LOGGER.warn("Cine Camera: {} stays fragmented (it plays, but Media Player may stutter on it)", file, e);
                return false;
            }
        }

        private void report(boolean ok, long frames, @Nullable String error, @Nullable String encoderName, boolean indexed) {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                if (!ok) {
                    say(mc, Component.translatable("create_stats.cine_camera.film.failed", String.valueOf(error))
                            .withStyle(ChatFormatting.RED));
                    return;
                }
                if (frames == 0) {
                    try {
                        Files.deleteIfExists(file);
                    } catch (IOException ignored) {
                    }
                    say(mc, Component.translatable("create_stats.cine_camera.film.empty").withStyle(ChatFormatting.GRAY));
                    return;
                }
                long bytes = 0;
                try {
                    bytes = Files.size(file);
                } catch (IOException ignored) {
                }
                double s = FilmClock.seconds(frames, fps);
                String time = String.format(Locale.ROOT, "%d:%04.1f", (int) (s / 60), s % 60);
                String size = String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0);
                String name = file.getFileName().toString();
                MutableComponent link = Component.literal(name).withStyle(ChatFormatting.UNDERLINE)
                        .withStyle(st -> st.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, file.toAbsolutePath().toString())));
                MutableComponent folder = Component.translatable("create_stats.cine_camera.film.folder")
                        .withStyle(ChatFormatting.UNDERLINE)
                        .withStyle(st -> st.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, file.getParent().toAbsolutePath().toString())));
                say(mc, Component.translatable("create_stats.cine_camera.film.saved", link, time, size, folder)
                        .withStyle(ChatFormatting.GREEN));
                savedName = FOLDER + "/" + name;
                savedAtMs = System.currentTimeMillis();
                CreateStats.LOGGER.info("Cine Camera: saved {} ({} frames, {}, {}, {})", file, frames, size, encoderName,
                        indexed ? "plain MP4" : "fragmented MP4");

                CreateStats.LOGGER.info("Cine Camera: that take held {} frames for the game, drew {} late, {} by extra passes, "
                        + "{} between ticks after a stall; {} starved of buffers", held, late, extra, caughtUp, starved);
            });
        }
    }
}
