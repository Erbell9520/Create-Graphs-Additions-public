package com.createstats.cinecam.client;

import com.createstats.cinecam.CineCameraBE;
import com.createstats.cinecam.core.AutoZoom;
import com.createstats.cinecam.core.CinePower;
import com.createstats.cinecam.core.FilmClock;
import com.createstats.cinecam.core.FilmFormat;
import com.createstats.cinecam.core.PhotoPrint;
import com.createstats.cinecam.net.CineControlPacket;
import com.createstats.power.FeConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CineMenuScreen extends Screen {
    private static final int BUTTON_W = 160;
    private static final int GAP = 8;
    private static final int ROW = 24;
    private static final int LINE = 12;
    private static final int SPACE = 6;

    private static final int FILM_ROWS = 3;

    private static final int HEIGHT = LINE + SPACE + ROW * FILM_ROWS + LINE * 2 + SPACE + ROW + ROW + LINE + SPACE + LINE * 3 + SPACE + 20;
    private static final int GREY = 0xFFA0A0A0;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GOLD = 0xFFFFAA00;
    private static final int GREEN = 0xFF55FF55;
    private static final int RED = 0xFFFF5555;

    private final BlockPos camera;
    private int rate;
    private FilmFormat.Resolution resolution;
    private FilmFormat.Quality quality;
    private FilmFormat.Shape shape;
    private FilmFormat.Speed speed;
    private boolean sound;
    private AutoZoom.Framing autoZoom;
    private PhotoPrint.Target target;
    private int side;
    private final List<CycleButton<?>> filmSettings = new ArrayList<>();
    private CycleButton<Integer> sideButton;
    private CycleButton<Boolean> soundButton;
    private int top;

    public CineMenuScreen(BlockPos camera) {
        super(Component.translatable("create_stats.cine_camera.menu.title"));
        this.camera = camera;
        CineCameraBE cam = CineView.cameraBE();
        rate = cam == null ? FilmFormat.DEFAULT_RATE : cam.filmRate();
        resolution = cam == null ? FilmFormat.DEFAULT_RESOLUTION : cam.filmResolution();
        quality = cam == null ? FilmFormat.DEFAULT_QUALITY : cam.filmQuality();
        shape = cam == null ? FilmFormat.DEFAULT_SHAPE : cam.filmShape();
        speed = cam == null ? FilmFormat.DEFAULT_SPEED : cam.filmSpeed();
        sound = cam == null || cam.filmSound();
        autoZoom = cam == null ? AutoZoom.DEFAULT : cam.autoZoom();
        target = cam == null ? PhotoPrint.DEFAULT_TARGET : cam.photoTarget();
        side = cam == null ? PhotoPrint.DEFAULT_SIDE : cam.photoSide();
    }

    @Override
    protected void init() {
        filmSettings.clear();
        int left = width / 2 - BUTTON_W - GAP / 2;
        int right = width / 2 + GAP / 2;
        top = Math.max(4, (height - HEIGHT) / 2);
        int y = top + LINE + SPACE;

        List<Integer> rates = new ArrayList<>();
        for (int r : FilmFormat.RATES) rates.add(r);
        filmSettings.add(addRenderableWidget(CycleButton.<Integer>builder(r -> Component.translatable("create_stats.cine_camera.menu.fps", r))
                .withValues(rates)
                .withInitialValue(rate)
                .withTooltip(r -> Tooltip.create(Component.translatable("create_stats.cine_camera.menu.rate." + r)))
                .create(left, y, BUTTON_W, 20, Component.translatable("create_stats.cine_camera.menu.rate"), (b, r) -> {
                    rate = r;
                    sendFilm();
                })));
        filmSettings.add(addRenderableWidget(CycleButton.<FilmFormat.Resolution>builder(r -> Component.translatable("create_stats.cine_camera.menu.res." + r.key()))
                .withValues(FilmFormat.Resolution.values())
                .withInitialValue(resolution)
                .withTooltip(this::resolutionTip)
                .create(right, y, BUTTON_W, 20, Component.translatable("create_stats.cine_camera.menu.resolution"), (b, r) -> {
                    resolution = r;
                    sendFilm();
                })));
        y += ROW;
        filmSettings.add(addRenderableWidget(CycleButton.<FilmFormat.Quality>builder(q -> Component.translatable("create_stats.cine_camera.menu.quality." + q.key()))
                .withValues(FilmFormat.Quality.values())
                .withInitialValue(quality)
                .withTooltip(this::qualityTip)
                .create(left, y, BUTTON_W, 20, Component.translatable("create_stats.cine_camera.menu.quality"), (b, q) -> {
                    quality = q;
                    sendFilm();
                })));
        filmSettings.add(addRenderableWidget(CycleButton.<FilmFormat.Shape>builder(s -> Component.translatable("create_stats.cine_camera.menu.shape." + s.key()))
                .withValues(FilmFormat.Shape.values())
                .withInitialValue(shape)
                .withTooltip(s -> Tooltip.create(Component.translatable("create_stats.cine_camera.menu.shape." + s.key() + ".tip")))
                .create(right, y, BUTTON_W, 20, Component.translatable("create_stats.cine_camera.menu.shape"), (b, s) -> {
                    shape = s;
                    sendFilm();
                })));
        y += ROW;
        filmSettings.add(addRenderableWidget(CycleButton.<FilmFormat.Speed>builder(v -> Component.translatable("create_stats.cine_camera.menu.speed." + v.key()))
                .withValues(java.util.Arrays.asList(FilmFormat.SPEED_CYCLE))
                .withInitialValue(speed)
                .withTooltip(this::speedTip)
                .create(left, y, BUTTON_W, 20, Component.translatable("create_stats.cine_camera.menu.speed"), (b, v) -> {
                    speed = v;
                    sendFilm();
                })));

        soundButton = addRenderableWidget(CycleButton.booleanBuilder(
                        Component.translatable("create_stats.cine_camera.menu.sound.on"), Component.translatable("create_stats.cine_camera.menu.sound.off"))
                .withInitialValue(sound)
                .withTooltip(on -> Tooltip.create(Component.translatable(on ? "create_stats.cine_camera.menu.sound.on.tip"
                        : "create_stats.cine_camera.menu.sound.off.tip")))
                .create(right, y, BUTTON_W, 20, Component.translatable("create_stats.cine_camera.menu.sound"), (b, on) -> {
                    sound = on;
                    send(CineControlPacket.setting(camera, CineControlPacket.SOUND, on ? 1 : 0));
                }));

        y = lensRow();
        addRenderableWidget(CycleButton.<AutoZoom.Framing>builder(f -> Component.translatable("create_stats.cine_camera.menu.zoom." + f.key()))
                .withValues(AutoZoom.Framing.values())
                .withInitialValue(autoZoom)
                .withTooltip(f -> Tooltip.create(Component.translatable(f.on() ? "create_stats.cine_camera.menu.zoom.on.tip"
                        : "create_stats.cine_camera.menu.zoom.off.tip")))
                .create(left, y, BUTTON_W * 2 + GAP, 20, Component.translatable("create_stats.cine_camera.menu.zoom"), (b, f) -> {
                    autoZoom = f;
                    send(CineControlPacket.setting(camera, CineControlPacket.ZOOM, f.ordinal()));
                }));

        y = photoRow();
        addRenderableWidget(CycleButton.<PhotoPrint.Target>builder(t -> Component.translatable("create_stats.cine_camera.menu.print." + t.key()))
                .withValues(PhotoPrint.Target.values())
                .withInitialValue(target)
                .withTooltip(t -> Tooltip.create(Component.translatable("create_stats.cine_camera.menu.print." + t.key() + ".tip")))
                .create(left, y, BUTTON_W, 20, Component.translatable("create_stats.cine_camera.menu.print"), (b, t) -> {
                    target = t;
                    sendPhoto();
                }));
        List<Integer> sides = new ArrayList<>();
        for (int s = PhotoPrint.MIN_SIDE; s <= PhotoPrint.MAX_SIDE; s++) sides.add(s);
        sideButton = addRenderableWidget(CycleButton.<Integer>builder(s -> Component.translatable("create_stats.cine_camera.menu.side_value", s, s))
                .withValues(sides)
                .withInitialValue(side)
                .withTooltip(s -> Tooltip.create(Component.translatable("create_stats.cine_camera.menu.side.tip",
                        PhotoPrint.mapsFor(s), PhotoPrint.pixelsFor(s), PhotoPrint.pixelsFor(s), s, s)))
                .create(right, y, BUTTON_W, 20, Component.translatable("create_stats.cine_camera.menu.side"), (b, s) -> {
                    side = s;
                    sendPhoto();
                }));

        int bottom = top + HEIGHT - 20;
        int third = (BUTTON_W * 2 + GAP - GAP * 2) / 3;
        Minecraft mc = Minecraft.getInstance();

        addRenderableWidget(Button.builder(Component.translatable("create_stats.cine_camera.menu.folder"),
                        b -> openFolder(CineRecorder.folder(mc)))
                .bounds(left, bottom, third, 20)
                .tooltip(Tooltip.create(Component.literal(CineRecorder.folder(mc).toAbsolutePath().toString())))
                .build());
        addRenderableWidget(Button.builder(Component.translatable("create_stats.cine_camera.menu.photo_folder"),
                        b -> openFolder(CinePhotoTaker.folder(mc)))
                .bounds(left + third + GAP, bottom, third, 20)
                .tooltip(Tooltip.create(Component.literal(CinePhotoTaker.folder(mc).toAbsolutePath().toString())))
                .build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(left + (third + GAP) * 2, bottom, BUTTON_W * 2 + GAP - (third + GAP) * 2, 20).build());
        lock();
    }

    private int lensRow() {
        return top + LINE + SPACE + ROW * FILM_ROWS + LINE * 2 + SPACE;
    }

    private int photoRow() {
        return lensRow() + ROW;
    }

    private static void send(net.minecraft.network.protocol.common.custom.CustomPacketPayload p) {
        if (Minecraft.getInstance().getConnection() == null) return;
        PacketDistributor.sendToServer(p);
    }

    private static boolean worldSlows() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.hasPermissions(2)) return true;
        return mc.getSingleplayerServer() != null && mc.getSingleplayerServer().getPlayerCount() == 1;
    }

    private Tooltip speedTip(FilmFormat.Speed v) {
        if (v.slow()) {
            return Tooltip.create(Component.translatable("create_stats.cine_camera.menu.speed.slow.tip",
                    Math.round(1.0 / v.factor()), "1/" + Math.round(1.0 / v.factor())));
        }
        if (v.lapse()) {
            return Tooltip.create(Component.translatable("create_stats.cine_camera.menu.speed.lapse.tip",
                    seconds(60.0 / v.factor()), seconds(v.factor() / rate)));
        }
        return Tooltip.create(Component.translatable("create_stats.cine_camera.menu.speed.normal.tip"));
    }

    private static String seconds(double s) {
        if (s >= 60.0) return String.format(Locale.ROOT, "%.0f min", s / 60.0);
        if (s >= 10.0) return String.format(Locale.ROOT, "%.0f s", s);
        if (s >= 1.0) return String.format(Locale.ROOT, "%.1f s", s).replace(".0 s", " s");
        return String.format(Locale.ROOT, "%.2f s", s);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (!CineView.inView() || CineView.cameraBE() == null) {
            onClose();
            return;
        }
        lock();
    }

    private void lock() {
        boolean free = !CineRecorder.recording();
        for (CycleButton<?> b : filmSettings) b.active = free;
        if (sideButton != null) sideButton.active = target.maps();

        if (soundButton != null) soundButton.active = free && speed == FilmFormat.Speed.NORMAL;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(g);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int cx = width / 2;
        g.drawCenteredString(font, title, cx, top, WHITE);
        int[] window = window();

        int y = top + LINE + SPACE + ROW * FILM_ROWS;
        int[] film = FilmFormat.size(resolution, shape.aspect(), window[0], window[1]);
        int bitrate = FilmFormat.bitrate(film[0], film[1], rate, quality);
        g.drawCenteredString(font, Component.translatable("create_stats.cine_camera.menu.summary", film[0], film[1], rate,
                String.format(Locale.ROOT, "%.0f", FilmFormat.megabytesPerMinute(bitrate))), cx, y, WHITE);
        y += LINE;
        int game = Minecraft.getInstance().getFps();

        boolean slows = worldSlows();
        int need = FilmClock.smoothFps(rate, speed.factor(), speed.slow() && slows);
        if (CineRecorder.recording()) {
            g.drawCenteredString(font, Component.translatable("create_stats.cine_camera.menu.locked"), cx, y, GOLD);
        } else if (!FilmFormat.fits(resolution, shape.aspect(), window[0], window[1])) {
            g.drawCenteredString(font, Component.translatable("create_stats.cine_camera.menu.too_wide",
                    resolution.width(), film[0], film[1]), cx, y, GOLD);
        } else if (game < need) {
            g.drawCenteredString(font, Component.translatable("create_stats.cine_camera.menu.game_slow", game, need), cx, y, GOLD);
        } else if (speed.slow()) {
            g.drawCenteredString(font, slows
                    ? Component.translatable("create_stats.cine_camera.menu.slow_world", "1/" + Math.round(1.0 / speed.factor()))
                    : Component.translatable("create_stats.cine_camera.menu.slow_frames", need), cx, y, slows ? GREY : GOLD);
        } else if (speed.lapse()) {
            g.drawCenteredString(font, Component.translatable("create_stats.cine_camera.menu.lapse",
                    seconds(60.0 / speed.factor())), cx, y, GREY);
        } else {
            g.drawCenteredString(font, Component.translatable("create_stats.cine_camera.menu.game_fps", game), cx, y, GREY);
        }

        y = photoRow() + ROW;
        g.drawCenteredString(font, photoSummary(Math.min(window[0], window[1])), cx, y, WHITE);

        y += LINE + SPACE;
        CineCameraBE cam = CineView.cameraBE();
        if (cam != null) {
            FeConsumer c = cam.feConsumer();
            String held = String.format(Locale.ROOT, "%,d", c.getEnergyStored());
            String size = String.format(Locale.ROOT, "%,d", c.getMaxEnergyStored());
            Component power;
            int colour;
            if (!c.requiresPower()) {
                power = Component.translatable("create_stats.cine_camera.menu.power.free");
                colour = GREY;
            } else if (cam.runs()) {
                power = Component.translatable("create_stats.cine_camera.menu.power.on", held, size,
                        CinePower.duration(CinePower.ticksAt(c.getEnergyStored(), c.settings().activeDrain())));
                colour = c.isBrownout() ? GOLD : GREEN;
            } else {
                power = Component.translatable("create_stats.cine_camera.menu.power.off", held, size,
                        c.rebootThresholdPct());
                colour = RED;
            }
            g.drawCenteredString(font, power, cx, y, colour);
        }
        y += LINE;
        g.drawCenteredString(font, Component.translatable("create_stats.cine_camera.menu.how"), cx, y, GREY);
        y += LINE;
        g.drawCenteredString(font, Component.translatable("create_stats.cine_camera.menu.away",
                CineClientSetup.RECORD.getTranslatedKeyMessage()), cx, y, GREY);
    }

    private Component photoSummary(int square) {
        int px = PhotoPrint.pixelsFor(side);
        int maps = PhotoPrint.mapsFor(side);
        return switch (target) {
            case MAP -> maps == 1
                    ? Component.translatable("create_stats.cine_camera.menu.photo.one_map")
                    : Component.translatable("create_stats.cine_camera.menu.photo.maps", px, px, maps);
            case FILE -> Component.translatable("create_stats.cine_camera.menu.photo.file", square, square);
            case BOTH -> maps == 1
                    ? Component.translatable("create_stats.cine_camera.menu.photo.both_one", square, square)
                    : Component.translatable("create_stats.cine_camera.menu.photo.both", px, px, maps, square, square);
        };
    }

    private Tooltip resolutionTip(FilmFormat.Resolution r) {
        int[] window = window();
        int[] film = FilmFormat.size(r, shape.aspect(), window[0], window[1]);
        if (r == FilmFormat.Resolution.NATIVE) {
            return Tooltip.create(Component.translatable("create_stats.cine_camera.menu.res.native.tip", film[0], film[1]));
        }
        if (!FilmFormat.fits(r, shape.aspect(), window[0], window[1])) {
            return Tooltip.create(Component.translatable("create_stats.cine_camera.menu.too_wide", r.width(), film[0], film[1]));
        }
        return Tooltip.create(Component.translatable("create_stats.cine_camera.menu.res.tip", film[0], film[1]));
    }

    private Tooltip qualityTip(FilmFormat.Quality q) {
        int[] window = window();
        int[] film = FilmFormat.size(resolution, shape.aspect(), window[0], window[1]);
        int bitrate = FilmFormat.bitrate(film[0], film[1], rate, q);
        return Tooltip.create(Component.translatable("create_stats.cine_camera.menu.quality." + q.key() + ".tip",
                String.format(Locale.ROOT, "%.1f", bitrate / 1_000_000.0),
                String.format(Locale.ROOT, "%.0f", FilmFormat.megabytesPerMinute(bitrate))));
    }

    private int[] window() {
        Minecraft mc = Minecraft.getInstance();
        return new int[]{mc.getMainRenderTarget().width, mc.getMainRenderTarget().height};
    }

    private void sendFilm() {
        if (Minecraft.getInstance().getConnection() == null) return;
        PacketDistributor.sendToServer(CineControlPacket.format(camera, FilmFormat.pack(rate, resolution, quality, shape, speed)));
    }

    private void sendPhoto() {
        lock();
        if (Minecraft.getInstance().getConnection() == null) return;
        PacketDistributor.sendToServer(CineControlPacket.photo(camera, PhotoPrint.pack(target, side)));
    }

    private void openFolder(Path dir) {
        Minecraft mc = Minecraft.getInstance();
        try {
            Files.createDirectories(dir);
            Util.getPlatform().openPath(dir);
        } catch (IOException | RuntimeException e) {
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.translatable("create_stats.cine_camera.film.failed",
                        String.valueOf(e.getMessage())).withStyle(ChatFormatting.RED), false);
            }
        }
    }
}
