package com.createstats.cinecam.client;

import com.createstats.CreateStats;
import com.createstats.battle.world.SablePlots;
import com.createstats.cinecam.CineCameraBE;
import com.createstats.cinecam.CineShips;
import com.createstats.cinecam.core.CineHead;
import com.createstats.cinecam.core.CineMode;
import com.createstats.cinecam.core.CinePower;
import com.createstats.cinecam.core.CineRig;
import com.createstats.cinecam.core.AutoZoom;
import com.createstats.cinecam.core.FilmClock;
import com.createstats.cinecam.core.FilmFormat;
import com.createstats.cinecam.core.FocusPull;
import com.createstats.cinecam.core.PhotoPrint;
import com.createstats.cinecam.net.CineControlPacket;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.power.FeConsumer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderBlockScreenEffectEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

// client only marker as camera entity, never added to the level
@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT)
public final class CineView {
    static final double ZOOM_MAX = 8.0;

    static final double ZOOM_STEP = 1.25;

    static final double GEARING = 0.55;
    static final double FILM_TAU = 0.33;

    static final double FADE = 0.28;

    static final double BAND_TAU = 0.12;

    static final long IRIS_SHOWN_MS = 1600;

    private static final boolean SABLE = ModList.get().isLoaded("sable");
    private static final boolean IRIS = ModList.get().isLoaded("iris") || ModList.get().isLoaded("oculus");

    @Nullable static BlockPos camera;

    static boolean inView;

    static boolean remotePass;
    @Nullable private static CineCameraBE cached;
    @Nullable private static Marker eye;
    @Nullable private static double[] viewDir;
    static double viewYaw, viewPitch;
    private static double handYaw, handPitch;
    private static float baseYRot, baseXRot;
    static double zoom = 1.0;
    private static double zoomWant = 1.0;
    private static long lastNanos;

    private static long ticks;

    private static double lastNow = Double.NaN, servoAt = Double.NaN, drawnAt = Double.NaN;

    private static boolean playerEyeDue;

    static final double PLAYABLE_FPS = 30.0;

    private static final int MAX_DRAWS = 2;

    private static final int MAX_CATCH_UP = 12;
    private static final long CATCH_UP_BUDGET_NANOS = 50_000_000L;

    private static double frameSeconds = 1.0 / 60.0;

    private static long catchUpNanos;

    private static double framing;
    @Nullable private static String framingFor;

    static boolean autoHolding;
    private static int aimCooldown;
    private static boolean aimDirty;
    private static boolean photoPending;
    private static long lastReleaseMs;
    static float flash;
    static long lockedAtMs = -1;
    private static int lastStatus = -1;

    static double tStop = FocusPull.OFF;

    private static double lastOn = FocusPull.DEFAULT_STOP;
    static long irisAtMs = -1;

    static long aspectDeniedAtMs = -1;

    static boolean filmHud = true;

    static float lockDistance, lockRadius;

    static double band;
    static double aspect = 2.39;

    private static boolean fading;
    private static long fadeAtNanos;
    @Nullable private static CameraType cameraTypeBefore;
    private static boolean filmHeld;
    private static boolean hideGuiBefore;

    private static final CineShips SHIPS = new CineShips();
    @Nullable private static UUID radiusFor;
    private static double shipRadius;
    private static double shipOwnRadius;
    private static long shipRadiusAt;

    @Nullable private static AwayCull awayCull;
    @Nullable private static int[] awayScissor;

    private static int[] paletteRgb;
    private static byte[] paletteIds;

    private CineView() {
    }

    public static boolean active() {
        return camera != null;
    }

    static boolean inView() {
        return camera != null && inView;
    }

    static boolean away() {
        return camera != null && !inView;
    }

    static boolean drawingLens() {
        return camera != null && (inView || remotePass);
    }

    public static boolean lookingThrough(BlockPos pos) {
        return drawingLens() && camera.equals(pos);
    }

    @Nullable
    static CineCameraBE cameraBE() {
        Minecraft mc = Minecraft.getInstance();
        if (camera == null || mc.level == null) return null;
        CineCameraBE c = cached;
        if (c != null && !c.isRemoved() && c.getLevel() == mc.level && c.getBlockPos().equals(camera)) return c;
        cached = mc.level.getBlockEntity(camera) instanceof CineCameraBE be ? be : null;
        return cached;
    }

    public static void enter(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (camera != null && !inView) {
            if (camera.equals(pos) && mc.player != null) {
                resume(mc);
                return;
            }
            end();
        }
        if (mc.level == null || mc.player == null || camera != null) return;
        if (!(mc.level.getBlockEntity(pos) instanceof CineCameraBE cam)) return;
        forgetRecordPresses();
        if (!cam.runs()) {
            noPower(mc, cam);
            return;
        }
        camera = pos.immutable();
        inView = true;
        cached = cam;
        viewYaw = handYaw = cam.dispYaw;
        viewPitch = handPitch = cam.dispPitch;
        baseYRot = mc.player.getYRot();
        baseXRot = mc.player.getXRot();
        zoom = zoomWant = 1.0;
        tStop = cam.tStop();
        if (FocusPull.on(tStop)) lastOn = tStop;
        filmHud = true;
        lastStatus = -1;
        lockedAtMs = -1;
        irisAtMs = -1;
        lockDistance = lockRadius = 0f;
        SHIPS.forget();
        radiusFor = null;
        band = cam.cineMode() == CineMode.FILM ? 1.0 : 0.0;
        aspect = shownAspect(cam);
        cameraTypeBefore = mc.options.getCameraType();
        if (!cameraTypeBefore.isFirstPerson()) mc.options.setCameraType(CameraType.FIRST_PERSON);
        holdFilm(mc, cam);
        eye = new Marker(EntityType.MARKER, mc.level);
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(true);
        viewDir = unit(worldDir(cam, CineHead.sight(cam.facingDegrees(), viewYaw, viewPitch), partial));
        placeEye(trunnionAt(cam, partial), viewDir);
        mc.setCameraEntity(eye);

        playerEyeDue = false;
        lastNow = servoAt = drawnAt = Double.NaN;
        lastNanos = System.nanoTime();
        CineFocus.snap();
        fade();
        mc.player.displayClientMessage(Component.translatable("create_stats.cine_camera.hint")
                .withStyle(ChatFormatting.GRAY), true);
    }

    public static void leave() {
        CineCameraBE cam = cameraBE();
        Minecraft mc = Minecraft.getInstance();
        if (inView && CineRecorder.recording() && cam != null && cam.cineMode() == CineMode.FILM) {
            if (!shaderPackInUse()) {
                stepAway(mc);
                return;
            }
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.translatable("create_stats.cine_camera.film.shaders")
                        .withStyle(ChatFormatting.GOLD), false);
            }
        }
        end();
    }

    private static boolean shaderPackInUse() {
        if (!IRIS) return false;
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object iris = api.getMethod("getInstance").invoke(null);
            return Boolean.TRUE.equals(api.getMethod("isShaderPackInUse").invoke(iris));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return false;
        }
    }

    static void end() {
        Minecraft mc = Minecraft.getInstance();
        stopFilm();
        boolean wasInView = inView;
        camera = null;
        inView = false;
        cached = null;
        eye = null;
        viewDir = null;
        photoPending = false;
        flash = 0f;
        lockDistance = lockRadius = 0f;
        autoHolding = false;
        framingFor = null;
        closeAwayCull();
        SHIPS.forget();
        CineFocus.close();
        if (wasInView) {
            releasePlayerView(mc);
            fade();
        }
    }

    private static void stepAway(Minecraft mc) {
        inView = false;
        photoPending = false;
        closeAwayCull();
        awayCull = new AwayCull();
        awayCull.open();
        releasePlayerView(mc);
        fade();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("create_stats.cine_camera.film.away",
                    CineClientSetup.RECORD.getTranslatedKeyMessage()).withStyle(ChatFormatting.GRAY), true);
        }
    }

    private static void resume(Minecraft mc) {
        CineCameraBE cam = cameraBE();
        if (cam == null || eye == null || mc.player == null) {
            end();
            return;
        }
        inView = true;
        closeAwayCull();
        baseYRot = mc.player.getYRot();
        baseXRot = mc.player.getXRot();
        filmHud = true;
        band = cam.cineMode() == CineMode.FILM ? 1.0 : 0.0;
        cameraTypeBefore = mc.options.getCameraType();
        if (!cameraTypeBefore.isFirstPerson()) mc.options.setCameraType(CameraType.FIRST_PERSON);
        holdFilm(mc, cam);
        mc.setCameraEntity(eye);
        playerEyeDue = false;
        drawnAt = Double.NaN;
        lastNanos = System.nanoTime();
        fade();
    }

    private static void releasePlayerView(Minecraft mc) {
        if (filmHeld) {
            mc.options.hideGui = hideGuiBefore;
            filmHeld = false;
        }
        if (cameraTypeBefore != null) {
            mc.options.setCameraType(cameraTypeBefore);
            cameraTypeBefore = null;
        }
        if (mc.player != null) {
            mc.setCameraEntity(mc.player);

            playerEyeDue = true;
        }
    }

    private static void closeAwayCull() {
        if (awayCull != null) {
            awayCull.close();
            awayCull = null;
        }
    }

    static double shownAspect(CineCameraBE cam) {
        double a = CineRecorder.aspect();
        return a > 0.0 ? a : cam.filmShape().aspect();
    }

    private static void holdFilm(Minecraft mc, CineCameraBE cam) {
        boolean film = cam.cineMode() == CineMode.FILM;
        if (film == filmHeld) return;
        if (film) {
            hideGuiBefore = mc.options.hideGui;
            mc.options.hideGui = true;
        } else {
            mc.options.hideGui = hideGuiBefore;
        }
        filmHeld = film;
    }

    private static void fade() {
        fading = true;
        fadeAtNanos = System.nanoTime();
    }

    static float fadeAlpha() {
        if (!fading) return 0f;
        double s = (System.nanoTime() - fadeAtNanos) / 1e9 / FADE;
        if (s >= 1.0 || s < 0.0) {
            fading = false;
            return 0f;
        }
        double k = 1.0 - s;
        return (float) (k * k * (3.0 - 2.0 * k));
    }

    static double[] sight(CineCameraBE cam) {
        return viewDir != null ? viewDir : unit(cam.toWorldDir(CineHead.sight(cam.facingDegrees(), viewYaw, viewPitch)));
    }

    @Nullable
    private static Object ownShip(CineCameraBE cam) {
        return SABLE ? SableBridge.subLevelAccessOf(cam) : null;
    }

    @Nullable
    static Vec3 trunnionAt(CineCameraBE cam, float partial) {
        Object own = ownShip(cam);
        if (own != null) {
            Vec3 p = CinePose.point(own, cam.trunnionLocal(), partial);
            if (p != null) return p;
        }
        return cam.trunnionWorld();
    }

    static double[] worldDir(CineCameraBE cam, double[] local, float partial) {
        Object own = ownShip(cam);
        if (own != null) {
            Vec3 v = CinePose.normal(own, new Vec3(local[0], local[1], local[2]), partial);
            if (v != null) return new double[]{v.x, v.y, v.z};
        }
        return cam.toWorldDir(local);
    }

    static double[] localDir(CineCameraBE cam, double[] world, float partial) {
        Object own = ownShip(cam);
        if (own != null) {
            Vec3 v = CinePose.normalInverse(own, new Vec3(world[0], world[1], world[2]), partial);
            if (v != null) return new double[]{v.x, v.y, v.z};
        }
        return cam.toLocalDir(world);
    }

    private static double[] unit(double[] v) {
        double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return l < 1e-9 ? new double[]{0.0, 0.0, -1.0} : new double[]{v[0] / l, v[1] / l, v[2] / l};
    }

    private static void placeEye(@Nullable Vec3 t, double[] d) {
        if (eye == null || t == null) return;
        double a = CineRig.LENS_AHEAD / 16.0;
        double x = t.x + d[0] * a;
        double y = t.y + d[1] * a;
        double z = t.z + d[2] * a;
        eye.setPos(x, y, z);
        eye.xo = eye.xOld = x;
        eye.yo = eye.yOld = y;
        eye.zo = eye.zOld = z;
        float yRot = (float) Math.toDegrees(Math.atan2(-d[0], d[2]));
        float xRot = (float) -Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, d[1]))));
        eye.setYRot(yRot);
        eye.setXRot(xRot);
        eye.yRotO = yRot;
        eye.xRotO = xRot;
    }

    record Aim(Vec3 at, double radius, double size) {
    }

    @Nullable
    static Aim clientTarget(ClientLevel level, CineCameraBE cam, float partial) {
        if (cam.targetKind() == CineCameraBE.KIND_ENTITY && cam.targetEntityId() >= 0) {
            Entity e = level.getEntity(cam.targetEntityId());
            if (e != null) {
                net.minecraft.world.entity.EntityDimensions d = e.getType().getDimensions();
                return new Aim(e.getPosition(partial).add(0, e.getBbHeight() / 2.0, 0),
                        Math.max(e.getBbWidth(), e.getBbHeight()) / 2.0, Math.max(d.width(), d.height()) / 2.0);
            }
        } else if (cam.targetKind() == CineCameraBE.KIND_SHIP) {
            try {
                long now = level.getGameTime();
                Object sub = SHIPS.find(level, cam.targetId(), now);
                if (sub == null) return null;
                Vec3 at = CinePose.position(sub, partial);
                if (at == null) {
                    Vector3d c = SableBridge.subLevelWorldCenter(sub);
                    if (c == null) return null;
                    at = new Vec3(c.x, c.y, c.z);
                }
                if (!cam.targetId().equals(radiusFor) || now - shipRadiusAt >= 20 || now < shipRadiusAt) {
                    AABB box = SablePlots.worldBounds(sub);
                    shipRadius = box == null ? 4.0
                            : 0.5 * Math.sqrt(box.getXsize() * box.getXsize() + box.getYsize() * box.getYsize()
                            + box.getZsize() * box.getZsize());

                    double own = CineShips.ownRadius(sub);
                    shipOwnRadius = own > 0.0 ? own : shipRadius;
                    radiusFor = cam.targetId();
                    shipRadiusAt = now;
                }
                return new Aim(at, shipRadius, shipOwnRadius);
            } catch (RuntimeException ignored) {
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Pre event) {
        if (camera == null) {
            forgetRecordPresses();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        CineCameraBE cam = cameraBE();
        if (!inView) {
            awayTick(mc, player, cam);
            return;
        }
        if (player == null || cam == null || !player.isAlive()) {
            end();
            return;
        }
        if (mc.options.keyShift.isDown()) {
            leave();
            return;
        }
        Vec3 t = cam.trunnionWorld();
        if (t != null && player.getEyePosition().distanceTo(t) > CineCameraBE.REACH + 1.0
                && !SableBridge.isPlayerInInteractionRangeFor(cam, player)) {
            leave();
            return;
        }
        if (!cam.runs()) {
            player.displayClientMessage(Component.translatable("create_stats.cine_camera.power_lost")
                    .withStyle(ChatFormatting.GOLD), false);
            end();
            return;
        }
        for (int i = 0; i < 9; i++) {
            while (mc.options.keyHotbarSlots[i].consumeClick()) {
                if (i >= CineMode.values().length) continue;
                if (i == cam.mode() && CineMode.byIndex(i) == CineMode.FILM) {
                    if (CineRecorder.recording()) {
                        aspectDeniedAtMs = System.currentTimeMillis();
                    } else {
                        send(CineControlPacket.format(camera, FilmFormat.pack(cam.filmRate(), cam.filmResolution(),
                                cam.filmQuality(), cam.filmShape().next(), cam.filmSpeed())));
                    }
                } else {
                    send(CineControlPacket.of(camera, CineControlPacket.MODE, i, 0f));
                }
            }
        }

        while (mc.options.keyInventory.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new CineMenuScreen(camera));
        }
        while (CineClientSetup.RECORD.consumeClick()) {
            if (cam.cineMode() == CineMode.FILM) toggleFilm(mc, cam);
        }
        if (flash > 0f) flash = Math.max(0f, flash - 0.25f);
        if (cam.status() != lastStatus) {
            if (cam.status() == CineCameraBE.TRACKING && lastStatus != CineCameraBE.TRACKING) lockedAtMs = System.currentTimeMillis();
            if (cam.status() == CineCameraBE.IDLE) lockedAtMs = -1;
            lastStatus = cam.status();
        }
        if (aimCooldown > 0) aimCooldown--;
        if (aimDirty && aimCooldown == 0 && cam.targetKind() == CineCameraBE.KIND_NONE) {
            send(CineControlPacket.of(camera, CineControlPacket.AIM, (float) handYaw, (float) handPitch));
            aimDirty = false;
            aimCooldown = 2;
        }
        if (cam.cineMode() != CineMode.FILM) stopFilm();
        holdFilm(mc, cam);
        if (!mc.options.getCameraType().isFirstPerson()) mc.options.setCameraType(CameraType.FIRST_PERSON);
    }

    @SubscribeEvent
    public static void loggingOut(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        if (camera != null) end();
    }

    private static void awayTick(Minecraft mc, @Nullable LocalPlayer player, @Nullable CineCameraBE cam) {
        if (player == null || cam == null || !player.isAlive() || !CineRecorder.recording() || cam.cineMode() != CineMode.FILM) {
            end();
            return;
        }
        if (CineClientSetup.RECORD.consumeClick()) {
            end();
            return;
        }
        if (!cam.runs()) {
            player.displayClientMessage(Component.translatable("create_stats.cine_camera.power_lost")
                    .withStyle(ChatFormatting.GOLD), false);
            end();
            return;
        }
        Vec3 t = cam.trunnionWorld();
        if (t != null && player.getEyePosition().distanceTo(t) > CineCameraBE.AWAY_RANGE) {
            player.displayClientMessage(Component.translatable("create_stats.cine_camera.film.too_far")
                    .withStyle(ChatFormatting.GOLD), false);
            end();
        }
    }

    @SubscribeEvent
    public static void counted(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) return;
        ticks++;
        CineRecorder.soundTick();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void closing(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || camera == null || eye == null || !CineRecorder.recording()) return;
        CineCameraBE cam = cameraBE();
        if (cam == null) return;
        boolean film = cam.cineMode() == CineMode.FILM;
        int drawn = 0;
        for (FilmClock.Plan p : CineRecorder.catchUp(ticks, MAX_CATCH_UP)) {
            if (catchUpNanos > CATCH_UP_BUDGET_NANOS) break;
            long t0 = System.nanoTime();
            filmPass(mc, cam, film, ticks, p);
            catchUpNanos += System.nanoTime() - t0;
            drawn++;
        }
        CineRecorder.caughtUp(drawn);
    }

    static double nowTicks() {
        return lastNow;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void frame(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        holdEyeHeight(mc);
        float own = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        catchUpNanos = 0L;
        if (camera == null) return;
        CineCameraBE cam = cameraBE();
        if (cam == null || mc.player == null || mc.level == null) return;
        long nanos = System.nanoTime();
        double dt = Math.min(0.1, Math.max(0.0, (nanos - lastNanos) / 1e9));
        lastNanos = nanos;
        frameSeconds += (dt - frameSeconds) * 0.1;
        long tick = ticks;
        double now = tick - 1 + own;

        double next = Double.isNaN(lastNow) || now < lastNow ? now : now + (now - lastNow);
        lastNow = now;

        if (inView) playerHands(mc);
        boolean film = cam.cineMode() == CineMode.FILM;

        int max = frameSeconds < 1.0 / PLAYABLE_FPS ? MAX_DRAWS : 1;
        List<FilmClock.Plan> draws = CineRecorder.recording() ? CineRecorder.draws(tick, own, next, inView, max) : List.of();
        if (draws.size() > 1) CineRecorder.extraPasses(draws.size() - 1);

        if (inView) {
            for (int i = 0; i + 1 < draws.size(); i++) filmPass(mc, cam, film, tick, draws.get(i));
            FilmClock.Plan plan = draws.isEmpty() ? null : draws.get(draws.size() - 1);
            float partial = own;
            if (plan != null) {
                partial = plan.partial();
            } else if (drawnAt > now && drawnAt <= tick) {
                partial = (float) (drawnAt - (tick - 1));
            }
            if (partial != own && !FilmTime.shift(mc, partial)) partial = own;
            drawnAt = tick - 1 + partial;
            lens(mc, cam, film, partial, drawnAt);
        } else {
            for (FilmClock.Plan p : draws) filmPass(mc, cam, film, tick, p);
        }

        if (eye != null && viewDir != null) CineRecorder.soundFrame(eye.position(), viewDir, inView ? drawnAt : now);

        double k = FocusPull.ease(dt, BAND_TAU);
        band += ((film ? 1.0 : 0.0) - band) * k;
        aspect += (shownAspect(cam) - aspect) * k;
    }

    @SubscribeEvent
    public static void frameEnd(RenderFrameEvent.Post event) {
        FilmTime.restore(Minecraft.getInstance());
    }

    static void holdEyeHeight(Minecraft mc) {
        if (playerEyeDue) {
            playerEyeDue = false;
            if (mc.player != null && !inView()) FilmTime.eyeHeight(mc.gameRenderer.getMainCamera(), mc.player.getEyeHeight());
        }
        if (inView() && eye != null) FilmTime.eyeHeight(mc.gameRenderer.getMainCamera(), eye.getEyeHeight());
    }

    private static void lens(Minecraft mc, CineCameraBE cam, boolean film, float partial, double at) {
        double dt = Double.isNaN(servoAt) || at < servoAt ? 0.0 : Math.min(1.0, (at - servoAt) / 20.0);
        if (Double.isNaN(servoAt) || at > servoAt) servoAt = at;
        Vec3 t = trunnionAt(cam, partial);
        Aim aim = null;
        double wantYaw = handYaw;
        double wantPitch = handPitch;
        double tau = CineHead.TAU_HAND;
        if (cam.targetKind() != CineCameraBE.KIND_NONE) {
            aim = clientTarget(mc.level, cam, partial);
            if (aim != null && t != null) {
                double[] local = localDir(cam, new double[]{aim.at().x - t.x, aim.at().y - t.y, aim.at().z - t.z}, partial);
                wantYaw = CineHead.yawFor(CineHead.bearing(local[0], local[2]), cam.facingDegrees());
                wantPitch = CineHead.clampPitch(CineHead.elevation(local[0], local[1], local[2]));
            } else {
                wantYaw = cam.dispYaw;
                wantPitch = cam.dispPitch;
            }
            handYaw = wantYaw;
            handPitch = wantPitch;
            tau = film ? FILM_TAU : CineHead.TAU_TRACK;
        }
        viewYaw = CineHead.wrap180(CineHead.follow(viewYaw, wantYaw, dt, tau, film ? CineHead.AZ_RATE * 0.6 : CineHead.AZ_RATE, true));
        viewPitch = CineHead.follow(viewPitch, wantPitch, dt, tau, film ? CineHead.EL_RATE * 0.6 : CineHead.EL_RATE, false);
        double[] d = unit(worldDir(cam, CineHead.sight(cam.facingDegrees(), viewYaw, viewPitch), partial));
        viewDir = d;
        placeEye(t, d);

        lockDistance = 0f;
        lockRadius = 0f;
        if (aim != null && eye != null) {
            Vec3 e = eye.position();
            double along = (aim.at().x - e.x) * d[0] + (aim.at().y - e.y) * d[1] + (aim.at().z - e.z) * d[2];
            if (along > FocusPull.NEAREST) {
                lockDistance = (float) along;
                lockRadius = (float) (aim.radius() + CineFocus.LOCK_MARGIN);
            }
        }

        AutoZoom.Framing preset = cam.autoZoom();
        autoHolding = preset.on() && aim != null && eye != null && aim.size() > 0.0;
        if (autoHolding) {
            String key = preset.key() + ":" + cam.targetKind() + ":" + cam.targetId() + ":" + cam.targetEntityId();
            if (!key.equals(framingFor)) {
                framing = preset.share();
                framingFor = key;
            }
            double window = mc.getWindow().getHeight() > 0 ? mc.getWindow().getWidth() / (double) mc.getWindow().getHeight() : 16.0 / 9.0;
            double tanHalf = AutoZoom.tanHalf(mc.options.fov().get(), window, film ? shownAspect(cam) : 0.0);
            zoomWant = AutoZoom.zoomFor(aim.at().distanceTo(eye.position()), aim.size(), framing, tanHalf, ZOOM_MAX);
        }
        zoom = FocusPull.easeZoom(zoom, zoomWant, dt, autoHolding ? AutoZoom.TAU : FocusPull.TAU_ZOOM);
    }

    // F1 in film: hold the hud down, otherwise the minimap pops back over the picture
    private static void playerHands(Minecraft mc) {
        if (filmHeld && !mc.options.hideGui) {
            mc.options.hideGui = true;
            filmHud = !filmHud;
        }
        LocalPlayer p = mc.player;
        double dYaw = p.getYRot() - baseYRot;
        double dPitch = p.getXRot() - baseXRot;
        p.setYRot(baseYRot);
        p.setXRot(baseXRot);
        p.yRotO = baseYRot;
        p.xRotO = baseXRot;
        if (dYaw != 0.0 || dPitch != 0.0) {
            double g = GEARING / zoom;

            handYaw = CineHead.wrap180(handYaw - dYaw * g);
            handPitch = CineHead.clampPitch(handPitch - dPitch * g);
            aimDirty = true;
        }
    }

    // second world pass from the lens per film frame. you asked to film, you pay for it
    private static void filmPass(Minecraft mc, CineCameraBE cam, boolean film, long tick, FilmClock.Plan plan) {
        if (eye == null) return;
        lens(mc, cam, film, plan.partial(), tick - 1 + plan.partial());
        RenderTarget main = mc.getMainRenderTarget();
        int[] box = CineRecorder.filmBox(main.width, main.height, FocusPull.on(tStop));
        CameraType type = mc.options.getCameraType();
        Entity before = mc.cameraEntity;
        Camera shared = mc.gameRenderer.getMainCamera();
        remotePass = true;
        awayScissor = box;
        if (awayCull != null) awayCull.begin(mc.levelRenderer);
        boolean moved = FilmTime.shift(mc, plan.partial());
        float[] eyeWas = FilmTime.eyeHeight(shared, eye.getEyeHeight());
        try {
            if (!type.isFirstPerson()) mc.options.setCameraType(CameraType.FIRST_PERSON);

            mc.cameraEntity = eye;
            main.bindWrite(true);
            if (box != null) RenderSystem.enableScissor(box[0], box[1], box[2], box[3]);
            mc.gameRenderer.renderLevel(mc.getTimer());

            mc.levelRenderer.doEntityOutline();
        } finally {
            RenderSystem.disableScissor();
            awayScissor = null;
            if (awayCull != null) awayCull.end(mc.levelRenderer);
            mc.cameraEntity = inView ? eye : before == null || before == eye ? mc.player : before;
            if (!type.isFirstPerson()) mc.options.setCameraType(type);
            FilmTime.eyeHeight(shared, eyeWas);
            if (moved) FilmTime.restore(mc);
            remotePass = false;
        }

        if (awayCull != null && awayCull.warming()) return;
        CineRecorder.captureAway(mc, plan.frame());
    }

    @Nullable
    static int[] awayScissor() {
        return awayScissor;
    }

    @SubscribeEvent
    public static void angles(ViewportEvent.ComputeCameraAngles event) {
        double[] d = viewDir;
        if (!drawingLens() || d == null) return;
        event.setYaw((float) Math.toDegrees(Math.atan2(-d[0], d[2])));
        event.setPitch((float) -Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, d[1])))));
        event.setRoll(0f);
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov event) {
        if (!drawingLens() || !event.usedConfiguredFov()) return;
        event.setFOV(FocusPull.zoomFov(lensFov(event.getCamera()), zoom));
    }

    static double lensFov(Camera camera) {
        Minecraft mc = Minecraft.getInstance();
        double fov = mc.options.fov().get();
        FogType fluid = camera.getFluidInCamera();
        if (fluid == FogType.LAVA || fluid == FogType.WATER) fov *= Mth.lerp(mc.options.fovEffectScale().get(), 1.0, 0.85714287F);
        return fov;
    }

    @SubscribeEvent
    public static void hand(RenderHandEvent event) {
        if (drawingLens()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void screenEffect(RenderBlockScreenEffectEvent event) {
        if (drawingLens()) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void scroll(InputEvent.MouseScrollingEvent event) {
        if (!inView() || event.getScrollDeltaY() == 0.0) return;
        event.setCanceled(true);
        boolean up = event.getScrollDeltaY() > 0;
        CineCameraBE cam = cameraBE();
        if (cam != null && cam.cineMode() == CineMode.FILM && Screen.hasControlDown()) {
            setIris(FocusPull.stepStop(tStop, up ? 1 : -1));
            return;
        }
        if (autoHolding) {
            framing = AutoZoom.nudge(framing, up ? 1 : -1);
            return;
        }
        zoomWant = Math.max(1.0, Math.min(ZOOM_MAX, zoomWant * (up ? ZOOM_STEP : 1.0 / ZOOM_STEP)));
    }

    private static void toggleFilm(Minecraft mc, CineCameraBE cam) {
        if (CineRecorder.recording()) {
            stopFilm();
        } else if (!cam.runs()) {
            noPower(mc, cam);
        } else if (CineRecorder.start(mc, cam)) {
            send(CineControlPacket.of(camera, CineControlPacket.RECORD, 1f, 0f));
        }
    }

    private static void noPower(Minecraft mc, CineCameraBE cam) {
        if (mc.player == null) return;
        FeConsumer c = cam.feConsumer();
        mc.player.displayClientMessage(Component.translatable("create_stats.cine_camera.no_power",
                String.format(Locale.ROOT, "%,d", c.getEnergyStored()), String.format(Locale.ROOT, "%,d", c.getMaxEnergyStored()),
                c.rebootThresholdPct()).withStyle(ChatFormatting.GOLD), true);
    }

    private static int emptyMaps(LocalPlayer p) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (p.getInventory().getItem(i).is(Items.MAP)) n += p.getInventory().getItem(i).getCount();
        }
        return n;
    }

    private static void forgetRecordPresses() {
        while (CineClientSetup.RECORD.consumeClick()) {
        }
    }

    private static void stopFilm() {
        if (!CineRecorder.recording()) return;
        CineRecorder.stop();
        Minecraft mc = Minecraft.getInstance();
        try {
            if (camera != null && mc.getConnection() != null) send(CineControlPacket.of(camera, CineControlPacket.RECORD, 0f, 0f));
        } catch (RuntimeException ignored) {
        }
    }

    private static void setIris(double next) {
        irisAtMs = System.currentTimeMillis();
        if (next == tStop || camera == null) return;
        tStop = next;
        if (FocusPull.on(next)) lastOn = next;
        send(CineControlPacket.of(camera, CineControlPacket.IRIS, (float) next, 0f));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void click(InputEvent.InteractionKeyMappingTriggered event) {
        if (!inView()) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        CineCameraBE cam = cameraBE();
        Minecraft mc = Minecraft.getInstance();
        if (cam == null || mc.player == null) return;
        if (event.isUseItem()) {
            if (cam.targetKind() != CineCameraBE.KIND_NONE) {
                send(CineControlPacket.of(camera, CineControlPacket.RELEASE, 0f, 0f));
            } else if (!pickAndLock(mc, cam)) {
                mc.player.displayClientMessage(Component.translatable("create_stats.cine_camera.nothing")
                        .withStyle(ChatFormatting.GRAY), true);
            }
        } else if (event.isAttack()) {
            long ms = System.currentTimeMillis();
            if (ms - lastReleaseMs < (cam.cineMode() == CineMode.PHOTO ? 1000 : 250)) return;
            lastReleaseMs = ms;
            if (cam.cineMode() == CineMode.PHOTO) {
                int need = cam.photoTarget().maps() ? PhotoPrint.mapsFor(cam.photoSide()) : 0;
                if (!cam.runs()) {
                    noPower(mc, cam);
                } else if (need > 0 && !mc.player.getAbilities().instabuild && emptyMaps(mc.player) < need) {
                    mc.player.displayClientMessage(Component.translatable("create_stats.cine_camera.photo.no_map", need)
                            .withStyle(ChatFormatting.GOLD), true);
                } else {
                    photoPending = true;
                }
            } else if (cam.cineMode() == CineMode.FILM) {
                toggleFilm(mc, cam);
            } else {
                send(CineControlPacket.of(camera, CineControlPacket.MARK, 0f, 0f));
            }
        } else if (event.isPickBlock() && cam.cineMode() == CineMode.FILM) {
            setIris(FocusPull.on(tStop) ? FocusPull.OFF : lastOn);
        }
    }

    private static boolean pickAndLock(Minecraft mc, CineCameraBE cam) {
        if (eye == null || mc.level == null || mc.player == null) return false;
        Vec3 from = eye.position();
        double[] d = sight(cam);
        Vec3 dir = new Vec3(d[0], d[1], d[2]);
        Vec3 far = from.add(dir.scale(CineCameraBE.MAX_RANGE));
        BlockHitResult block = mc.level.clip(new ClipContext(from, far, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
                CollisionContext.empty()));
        UUID own = null;
        UUID hitShip = null;
        double limit = CineCameraBE.MAX_RANGE;
        try {
            Object ownSub = ownShip(cam);
            own = ownSub == null ? null : SableBridge.subLevelId(ownSub);
            if (block.getType() != HitResult.Type.MISS) {
                hitShip = SablePlots.shipAt(mc.level, block.getBlockPos());
                Vec3 at = hitShip == null ? block.getLocation() : SableBridge.projectOutOfSubLevel(mc.level, block.getLocation());
                limit = Math.min(CineCameraBE.MAX_RANGE, at.distanceTo(from));

                if (hitShip != null && hitShip.equals(own)) hitShip = null;
            }
        } catch (RuntimeException ignored) {
            if (block.getType() != HitResult.Type.MISS) limit = Math.min(CineCameraBE.MAX_RANGE, block.getLocation().distanceTo(from));
        }
        Vec3 end = from.add(dir.scale(limit));
        LocalPlayer player = mc.player;
        EntityHitResult ent = ProjectileUtil.getEntityHitResult(mc.level, player, from, end,
                new AABB(from, end).inflate(1.0), e -> e != player && !e.isSpectator() && e.isPickable(), 0.3f);
        if (ent != null) {
            send(CineControlPacket.lock(camera, CineCameraBE.KIND_ENTITY, ent.getEntity().getUUID()));
            return true;
        }
        if (hitShip != null) {
            send(CineControlPacket.lock(camera, CineCameraBE.KIND_SHIP, hitShip));
            return true;
        }
        UUID ship = null;
        double best = limit;
        try {
            for (Object sub : SableBridge.listSubLevels(mc.level)) {
                UUID id = SableBridge.subLevelId(sub);
                if (id == null || id.equals(own)) continue;
                AABB box = SablePlots.worldBounds(sub);
                if (box == null || box.contains(from)) continue;
                var hit = box.clip(from, end);
                if (hit.isPresent() && hit.get().distanceTo(from) < best) {
                    best = hit.get().distanceTo(from);
                    ship = id;
                }
            }
        } catch (RuntimeException ignored) {
        }
        if (ship != null) {
            send(CineControlPacket.lock(camera, CineCameraBE.KIND_SHIP, ship));
            return true;
        }
        return false;
    }

    private static final Set<ResourceLocation> KEEP = Set.of(VanillaGuiLayers.CHAT, VanillaGuiLayers.OVERLAY_MESSAGE,
            VanillaGuiLayers.TITLE, VanillaGuiLayers.SUBTITLE_OVERLAY, VanillaGuiLayers.DEBUG_OVERLAY,
            VanillaGuiLayers.TAB_LIST, VanillaGuiLayers.SAVING_INDICATOR, VanillaGuiLayers.BOSS_OVERLAY);

    @SubscribeEvent
    public static void layer(RenderGuiLayerEvent.Pre event) {
        if (!inView() || event.getName().equals(CineClientSetup.LAYER)) return;
        CineCameraBE cam = cameraBE();
        if (cam == null) return;
        if (cam.cineMode() == CineMode.FILM || !KEEP.contains(event.getName())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void photo(RenderGuiEvent.Pre event) {
        if (!photoPending || !inView()) return;
        photoPending = false;
        Minecraft mc = Minecraft.getInstance();
        CineCameraBE cam = cameraBE();
        if (cam == null) return;
        try {
            buildPalette();
            String subject = cam.targetKind() == CineCameraBE.KIND_NONE ? "" : cam.targetName();
            CinePhotoTaker.take(mc, camera, cam.photoTarget(), cam.photoSide(), subject, paletteRgb, paletteIds);
            flash = 1f;
            if (mc.player != null) mc.player.playSound(SoundEvents.SPYGLASS_USE, 0.8f, 1.8f);
        } catch (RuntimeException e) {
            CreateStats.LOGGER.warn("Cine Camera: the photograph could not be read from the frame", e);
        }
    }

    private static void buildPalette() {
        if (paletteRgb != null) return;
        int[] mods = {180, 220, 255, 135};
        List<Integer> rgb = new ArrayList<>();
        List<Byte> ids = new ArrayList<>();
        for (int id = 1; id < 64; id++) {
            MapColor mc = MapColor.byId(id);
            if (mc == null || mc == MapColor.NONE || mc.col == 0) continue;
            for (int b = 0; b < 4; b++) {
                int r = ((mc.col >> 16) & 0xFF) * mods[b] / 255;
                int g = ((mc.col >> 8) & 0xFF) * mods[b] / 255;
                int bl = (mc.col & 0xFF) * mods[b] / 255;
                rgb.add(r << 16 | g << 8 | bl);
                ids.add((byte) (id * 4 + b));
            }
        }
        paletteRgb = rgb.stream().mapToInt(Integer::intValue).toArray();
        paletteIds = new byte[ids.size()];
        for (int i = 0; i < ids.size(); i++) paletteIds[i] = ids.get(i);
    }

    private static void send(net.minecraft.network.protocol.common.custom.CustomPacketPayload p) {
        PacketDistributor.sendToServer(p);
    }
}
