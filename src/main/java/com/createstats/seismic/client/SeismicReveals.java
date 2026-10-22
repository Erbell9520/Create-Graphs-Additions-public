package com.createstats.seismic.client;

import com.createstats.CreateStats;
import com.createstats.block.SeismicScannerBlock;
import com.createstats.blockentity.SeismicScannerBE;
import com.createstats.client.AnimationClock;
import com.createstats.client.ThroughWalls;
import com.createstats.network.SeismicRereadPacket;
import com.createstats.network.SeismicVeinsPacket;
import com.createstats.seismic.SeismicCatalog;
import com.createstats.seismic.SeismicGeometry;
import com.createstats.seismic.VeinOutline;
import com.createstats.seismic.VeinVisibility;
import com.createstats.seismic.archive.SurveyReread;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createmod.catnip.outliner.LineOutline;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT)
public final class SeismicReveals {
    static final int MAX_RUNS = 9_000;

    static final int MAX_LABELS = 24;

    static final float WIDTH = 1 / 14f;

    static final float LINE_ALPHA = 0.8f;

    static final float FLASH_WHITE = 0.6f;

    static final double LOOK_REACH = 64.0;

    static final int DWELL_TICKS = 10;

    static final double HIDDEN_CONE_DEG = 4.0;

    static final double MINED_REACH = 6.0;

    static final int MINED_EVERY = 20;
    private static final int LIGHT = LightTexture.FULL_BRIGHT;

    private static final class Vein {
        final String kind;
        final String depth;
        final int colour;

        long revealAt;

        final boolean flashes;
        final double cx;
        final double cy;
        final double cz;
        final BlockPos rig;
        final int cell;
        final int blocks;
        int[] packed;
        String name;
        float[] runs;
        float[] cells;
        double[] bounds;

        boolean mined;
        double distance = Double.MAX_VALUE;
        float shown;
        float shownOld;
        float label;
        float labelOld;
        boolean placed;

        boolean leads;

        Vein(String kind, int blocks, String depth, int colour, long revealAt, boolean flashes, double cx, double cy,
             double cz, BlockPos rig, int[] packed, int cell) {
            this.kind = kind;
            this.blocks = blocks;
            this.depth = depth;
            this.colour = colour;
            this.revealAt = revealAt;
            this.flashes = flashes;
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.rig = rig;
            this.cell = cell;
            outline(packed);
        }

        void outline(int[] cellsLeft) {
            packed = cellsLeft;

            name = kind + " " + (cell == 1 ? cellsLeft.length : blocks);
            final int[] edges = VeinOutline.edges(cellsLeft);
            final int n = VeinOutline.runCount(edges);
            runs = new float[n * 5];
            for (int i = 0; i < n; i++) {
                runs[i * 5] = rig.getX() + edges[i * 5] * cell;
                runs[i * 5 + 1] = rig.getY() + edges[i * 5 + 1] * cell;
                runs[i * 5 + 2] = rig.getZ() + edges[i * 5 + 2] * cell;
                runs[i * 5 + 3] = edges[i * 5 + 3];
                runs[i * 5 + 4] = edges[i * 5 + 4] * cell;
            }
            cells = new float[cellsLeft.length * 3];
            for (int i = 0; i < cellsLeft.length; i++) {
                cells[i * 3] = rig.getX() + (((cellsLeft[i] >>> 16) & 0xFF) - 128) * cell;
                cells[i * 3 + 1] = rig.getY() + (((cellsLeft[i] >>> 8) & 0xFF) - 128) * cell;
                cells[i * 3 + 2] = rig.getZ() + ((cellsLeft[i] & 0xFF) - 128) * cell;
            }
            final double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE,
                    -Double.MAX_VALUE, -Double.MAX_VALUE};
            for (int i = 0; i + 2 < cells.length; i += 3) {
                for (int k = 0; k < 3; k++) {
                    b[k] = Math.min(b[k], cells[i + k]);
                    b[k + 3] = Math.max(b[k + 3], cells[i + k] + cell);
                }
            }
            bounds = b;
        }
    }

    private record Key(BlockPos rig, String target) {
    }

    private static final long UNSEEN = Long.MAX_VALUE / 2;

    private static final class Survey {
        final BlockPos rig;

        final String target;
        final long impactAt;

        final boolean replayed;
        final List<Vein> veins = new ArrayList<>();

        long overviewUntil;

        long retiredAt = Long.MIN_VALUE;

        long revealAt = UNSEEN;

        Survey(BlockPos rig, String target, long impactAt, boolean replayed) {
            this.rig = rig;
            this.target = target;
            this.impactAt = impactAt;
            this.replayed = replayed;
            this.overviewUntil = impactAt;
        }
    }

    private record AtRig(long lastAt, int lastHow, boolean listening, long seenAt) {
    }

    private static final class Batch {
        final List<Survey> sheets = new ArrayList<>();
        long lastArrival;
    }

    private static final Map<Key, Survey> SURVEYS = new HashMap<>();
    private static final List<Survey> RETIRING = new ArrayList<>();
    private static final Map<BlockPos, AtRig> AT_RIG = new HashMap<>();

    private static final Map<BlockPos, long[]> ASKED = new HashMap<>();

    private static final Set<BlockPos> HINTED = new HashSet<>();
    private static final Map<BlockPos, Batch> BATCHES = new HashMap<>();

    private static final List<Vein> LIT = new ArrayList<>();
    private static final LineOutline LINE = new LineOutline();
    private static final Vector3f ORIGIN = new Vector3f();
    private static final Vector4f COLOUR = new Vector4f();

    private static BlockPos dwellRig;
    private static int dwellTicks;

    private static boolean onRecorder;

    private static double viewTanV;
    private static double viewTanH;

    private SeismicReveals() {
    }

    public static void accept(SeismicVeinsPacket packet) {
        final ClientLevel level = Minecraft.getInstance().level;
        final long now = level == null ? packet.impactAt() : level.getGameTime();
        final BlockPos rig = packet.rig().immutable();
        final long at = packet.impactAt();
        final Key key = new Key(rig, targetOf(level, rig, packet));
        final Survey held = SURVEYS.get(key);

        final SurveyReread.Route route = SurveyReread.route(held == null ? SurveyReread.NEVER : held.impactAt,
                held != null && held.replayed, at, packet.replay(), packet.opens());
        final Survey s;
        switch (route) {
            case DROP -> {
                return;
            }
            case JOIN -> s = held;
            case RESTART -> {
                held.veins.clear();
                s = held;
            }
            default -> s = open(key, held, at, packet.replay(), now);
        }
        if (packet.replay() && packet.opens()) {
            s.revealAt = UNSEEN;
            final Batch batch = BATCHES.computeIfAbsent(rig, r -> new Batch());
            if (!batch.sheets.contains(s)) {
                batch.sheets.add(s);
            }
            batch.lastArrival = now;
        }
        for (final SeismicVeinsPacket.Vein v : packet.veins()) {
            final long revealAt = s.replayed ? s.revealAt : at + v.echo();
            final double y = rig.getY() + v.cy();
            s.veins.add(new Vein(v.label(), v.count(), "Y " + (int) Math.floor(y), v.colour(), revealAt, !s.replayed,
                    rig.getX() + v.cx(), y, rig.getZ() + v.cz(), rig, v.cells(), Math.max(1, v.cell())));
            if (!s.replayed) {
                s.overviewUntil = Math.max(s.overviewUntil, revealAt + VeinVisibility.OVERVIEW_TICKS);
            }
        }
    }

    private static Survey open(Key key, Survey old, long at, boolean replayed, long now) {
        if (old != null) {
            old.retiredAt = now;
            RETIRING.add(old);
        }
        final Survey s = new Survey(key.rig(), key.target(), at, replayed);
        SURVEYS.put(key, s);
        return s;
    }

    private static String targetOf(ClientLevel level, BlockPos rig, SeismicVeinsPacket packet) {
        if (!packet.station().isEmpty()) {
            return packet.station();
        }
        if (level != null && level.getBlockEntity(rig) instanceof SeismicScannerBE be && !be.tunedStation().isEmpty()) {
            return be.tunedStation();
        }
        return packet.veins().isEmpty() ? "" : "kind:" + packet.veins().get(0).label();
    }

    private static void finishSheets(long now) {
        for (final Iterator<Map.Entry<BlockPos, Batch>> it = BATCHES.entrySet().iterator(); it.hasNext(); ) {
            final Map.Entry<BlockPos, Batch> e = it.next();
            final Batch b = e.getValue();
            if (b.lastArrival >= now) {
                continue;
            }
            final int n = b.sheets.size();
            for (int k = 0; k < n; k++) {
                final Survey s = b.sheets.get(k);
                s.revealAt = now + VeinVisibility.sheetDelay(k, n);
                for (final Vein v : s.veins) {
                    v.revealAt = s.revealAt;
                }
            }
            final long until = now + VeinVisibility.sheetDelay(n - 1, n) + (long) Math.ceil(VeinVisibility.RISE_TICKS)
                    + VeinVisibility.OVERVIEW_TICKS;
            for (final Survey s : SURVEYS.values()) {
                if (s.rig.equals(e.getKey())) {
                    s.overviewUntil = Math.max(s.overviewUntil, until);
                }
            }
            it.remove();
        }
    }

    public static void atRig(SeismicScannerBE be, long now) {
        final BlockPos rig = be.getBlockPos().immutable();
        AT_RIG.put(rig, new AtRig(be.lastAt(), be.lastHow(), SurveyReread.listening(be.lastAt(), be.lastReach(), now),
                now));
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !mc.player.isSpectator() && lacks(be, now) && HINTED.add(rig)) {
            mc.player.displayClientMessage(
                    Component.translatable("block.create_stats.seismic_scanner.report.reread_hint"), true);
        }
    }

    public static boolean lacks(SeismicScannerBE be, long now) {
        final long lastAt = be.lastAt();
        return lastAt != SurveyReread.NEVER && be.lastHow() == 0
                && !SurveyReread.listening(lastAt, be.lastReach(), now) && newestHeld(be.getBlockPos()) < lastAt;
    }

    private static long newestHeld(BlockPos rig) {
        long newest = SurveyReread.NEVER;
        for (final Survey s : SURVEYS.values()) {
            if (s.rig.equals(rig)) {
                newest = Math.max(newest, s.impactAt);
            }
        }
        return newest;
    }

    private static List<SeismicRereadPacket.Held> heldOf(BlockPos rig) {
        final List<SeismicRereadPacket.Held> out = new ArrayList<>();
        for (final Survey s : SURVEYS.values()) {
            if (s.rig.equals(rig) && !s.target.isEmpty() && !s.target.startsWith("kind:")
                    && out.size() < SurveyReread.MAX_HELD) {
                out.add(new SeismicRereadPacket.Held(s.target, s.impactAt));
            }
        }
        return out;
    }

    private static void maybeAsk(BlockPos rig, long now) {
        final AtRig at = AT_RIG.get(rig);
        final Player player = Minecraft.getInstance().player;

        if (at == null || player == null || player.isSpectator()) {
            return;
        }
        final long[] asked = ASKED.get(rig);
        if (!SurveyReread.shouldAsk(at.lastAt(), at.lastHow(), at.listening(), newestHeld(rig),
                asked == null ? SurveyReread.NEVER : asked[0], asked == null ? SurveyReread.NEVER : asked[1], now)) {
            return;
        }
        ASKED.put(rig, new long[] {at.lastAt(), now});
        PacketDistributor.sendToServer(new SeismicRereadPacket(rig, heldOf(rig)));
    }

    public static boolean active() {
        return !SURVEYS.isEmpty() || !RETIRING.isEmpty();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!active() && AT_RIG.isEmpty() && BATCHES.isEmpty()) {
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        final ClientLevel level = mc.level;
        if (level == null) {
            forget();
            return;
        }
        final long now = level.getGameTime();

        AT_RIG.values().removeIf(a -> a.seenAt() < now - 1 || a.seenAt() > now);
        finishSheets(now);
        final Camera camera = mc.gameRenderer.getMainCamera();
        final Vec3 eye = camera.getPosition();
        look(mc, level, camera, now);
        LIT.clear();
        final boolean check = now % MINED_EVERY == 0;
        for (final Survey s : SURVEYS.values()) {
            step(s, eye, now);
            if (check) {
                strikeMined(level, s, eye);
            }
        }
        for (final Iterator<Survey> it = RETIRING.iterator(); it.hasNext(); ) {
            final Survey s = it.next();
            if (!step(s, eye, now) && now - s.retiredAt > VeinVisibility.RETIRE_TICKS) {
                it.remove();
            }
        }
        LIT.sort(Comparator.comparingDouble(v -> v.distance));
        place(mc, camera, eye);
    }

    private static void look(Minecraft mc, ClientLevel level, Camera camera, long now) {
        final BlockPos rig = mc.player == null || mc.screen != null || holdsWrench(mc.player) ? null
                : aimedRig(mc.player, level, camera);
        if (rig == null || !rig.equals(dwellRig)) {
            dwellRig = rig;
            dwellTicks = 0;
        }
        if (rig == null || ++dwellTicks < DWELL_TICKS) {
            return;
        }
        if (onRecorder) {
            maybeAsk(rig, now);
        }
        for (final Survey s : SURVEYS.values()) {
            if (s.rig.equals(rig)) {
                s.overviewUntil = Math.max(s.overviewUntil, now + VeinVisibility.OVERVIEW_TICKS);
            }
        }
    }

    private static boolean holdsWrench(Player player) {
        return player.getMainHandItem().is(com.simibubi.create.AllItems.WRENCH.get());
    }

    private static BlockPos aimedRig(Player player, ClientLevel level, Camera camera) {
        onRecorder = false;
        final Vec3 from = camera.getPosition();
        final Vector3f dir = camera.getLookVector();
        final Vec3 to = from.add(dir.x() * LOOK_REACH, dir.y() * LOOK_REACH, dir.z() * LOOK_REACH);
        final BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE,
                player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            final BlockState state = level.getBlockState(hit.getBlockPos());
            if (state.getBlock() instanceof SeismicScannerBlock) {
                final BlockPos base = SeismicScannerBlock.base(state, hit.getBlockPos());
                final boolean held = held(base);
                if (!held && !AT_RIG.containsKey(base)) {
                    return null;
                }
                final Vec3 at = hit.getLocation();
                final int quarter = SeismicScannerBE.quarter(state.getValue(SeismicScannerBlock.FACING));
                onRecorder = SeismicGeometry.onRecorder(quarter, (at.x - base.getX()) * 16.0,
                        (at.y - base.getY()) * 16.0, (at.z - base.getZ()) * 16.0);
                final Vec3 mid = middleOf(level, base);
                final double r = SeismicScannerBE.WITNESS_RADIUS;
                if ((!held || mid == null || from.distanceToSqr(mid) <= r * r) && !onRecorder) {
                    return null;
                }
                return base;
            }
        }

        final double open = hit.getType() == HitResult.Type.MISS ? LOOK_REACH : hit.getLocation().distanceTo(from);
        BlockPos best = null;
        double bestCos = -1.0;
        for (final Survey s : SURVEYS.values()) {
            final Vec3 mid = middleOf(level, s.rig);
            if (mid == null) {
                continue;
            }
            final double dx = mid.x - from.x;
            final double dy = mid.y - from.y;
            final double dz = mid.z - from.z;
            final double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d > LOOK_REACH || d <= open) {
                continue;
            }
            final double cos = (dx * dir.x() + dy * dir.y() + dz * dir.z()) / d;
            final double cone = Math.max(Math.toRadians(HIDDEN_CONE_DEG), Math.atan(1.5 / d));
            if (cos >= Math.cos(cone) && cos > bestCos) {
                bestCos = cos;
                best = s.rig;
            }
        }
        return best;
    }

    private static boolean held(BlockPos rig) {
        for (final Survey s : SURVEYS.values()) {
            if (s.rig.equals(rig)) {
                return true;
            }
        }
        return false;
    }

    private static Vec3 middleOf(ClientLevel level, BlockPos rig) {
        if (level.isLoaded(rig) && level.getBlockEntity(rig) instanceof SeismicScannerBE be) {
            return SeismicClient.middle(be);
        }
        return new Vec3(rig.getX() + 0.5, rig.getY() + 1.0, rig.getZ() + 0.5);
    }

    private static void strikeMined(ClientLevel level, Survey s, Vec3 eye) {
        if (s.target.isEmpty() || s.target.startsWith("kind:")) {
            return;
        }
        final TagKey<Block> ore = SeismicCatalog.isOre(s.target)
                ? TagKey.create(Registries.BLOCK, ResourceLocation.parse(s.target)) : null;
        final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (final Vein v : s.veins) {
            if (v.mined || v.distance > MINED_REACH) {
                continue;
            }
            final int[] keep = new int[v.packed.length];
            int kept = 0;
            for (int i = 0; i < v.packed.length; i++) {
                final float x0 = v.cells[i * 3];
                final float y0 = v.cells[i * 3 + 1];
                final float z0 = v.cells[i * 3 + 2];
                final double dx = Math.max(Math.max(x0 - eye.x, 0.0), eye.x - (x0 + v.cell));
                final double dy = Math.max(Math.max(y0 - eye.y, 0.0), eye.y - (y0 + v.cell));
                final double dz = Math.max(Math.max(z0 - eye.z, 0.0), eye.z - (z0 + v.cell));
                boolean still = dx * dx + dy * dy + dz * dz > MINED_REACH * MINED_REACH;
                for (int k = 0; !still && k < v.cell * v.cell * v.cell; k++) {
                    at.set((int) x0 + k % v.cell, (int) y0 + (k / v.cell) % v.cell, (int) z0 + k / (v.cell * v.cell));
                    still = stillTarget(level.getBlockState(at), s.target, ore);
                }
                if (still) {
                    keep[kept++] = v.packed[i];
                }
            }
            if (kept == v.packed.length) {
                continue;
            }
            if (kept == 0) {
                v.mined = true;
            } else {
                v.outline(java.util.Arrays.copyOf(keep, kept));
            }
        }
    }

    private static boolean stillTarget(BlockState state, String target, TagKey<Block> ore) {
        if (ore != null) {
            return state.is(ore);
        }
        if (SeismicCatalog.VOIDS.equals(target)) {
            return state.isAir();
        }
        if (SeismicCatalog.LIQUIDS.equals(target)) {
            return !state.getFluidState().isEmpty();
        }
        return true;
    }

    private static boolean step(Survey s, Vec3 eye, long now) {
        boolean any = false;
        final double sinceEnd = now - s.overviewUntil;
        final double sinceRetired = s.retiredAt == Long.MIN_VALUE ? Double.NaN : now - s.retiredAt;
        for (final Vein v : s.veins) {
            v.distance = distance(v, eye);
            final double target = v.mined ? 0.0
                    : VeinVisibility.target(now - v.revealAt, sinceEnd, v.distance, sinceRetired);
            v.shownOld = v.shown;
            v.shown = (float) VeinVisibility.ease(v.shown, target);
            if (v.shown <= VeinVisibility.GONE && v.shownOld <= VeinVisibility.GONE && target <= 0.0) {
                v.shown = 0.0f;
                v.labelOld = v.label = 0.0f;
                v.placed = false;
                continue;
            }
            any = true;
            LIT.add(v);
        }
        return any;
    }

    private static double distance(Vein v, Vec3 p) {
        final double[] b = v.bounds;
        final double bx = Math.max(Math.max(b[0] - p.x, 0.0), p.x - b[3]);
        final double by = Math.max(Math.max(b[1] - p.y, 0.0), p.y - b[4]);
        final double bz = Math.max(Math.max(b[2] - p.z, 0.0), p.z - b[5]);
        final double box = Math.sqrt(bx * bx + by * by + bz * bz);
        if (box > VeinVisibility.FAR + 2.0) {
            return box;
        }
        double best = Double.MAX_VALUE;
        final float[] c = v.cells;
        final int size = v.cell;
        for (int i = 0; i + 2 < c.length; i += 3) {
            final double dx = Math.max(Math.max(c[i] - p.x, 0.0), p.x - (c[i] + size));
            final double dy = Math.max(Math.max(c[i + 1] - p.y, 0.0), p.y - (c[i + 1] + size));
            final double dz = Math.max(Math.max(c[i + 2] - p.z, 0.0), p.z - (c[i + 2] + size));
            best = Math.min(best, dx * dx + dy * dy + dz * dz);
        }
        return Math.sqrt(best);
    }

    private static void place(Minecraft mc, Camera camera, Vec3 eye) {
        final Vector3f look = camera.getLookVector();
        final Vector3f up = camera.getUpVector();
        final Vector3f left = camera.getLeftVector();

        final double tanV = viewTanV > 0.0 ? viewTanV : Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
        final double tanH = viewTanH > 0.0 ? viewTanH
                : tanV * mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        final Set<String> named = new HashSet<>();
        for (final Vein v : LIT) {
            final double dx = v.cx - eye.x;
            final double dy = v.cy - eye.y;
            final double dz = v.cz - eye.z;
            final double z = dx * look.x() + dy * look.y() + dz * look.z();
            v.leads = v.shown > 0.05f && z > 0.25
                    && Math.abs((dx * left.x() + dy * left.y() + dz * left.z()) / z) <= tanH
                    && Math.abs((dx * up.x() + dy * up.y() + dz * up.z()) / z) <= tanV
                    && named.add(v.kind);
        }
        final List<double[]> taken = new ArrayList<>();
        for (final Vein v : LIT) {
            if (v.leads) {
                place(mc, camera, eye, v, taken);
            }
        }
        for (final Vein v : LIT) {
            if (!v.leads) {
                place(mc, camera, eye, v, taken);
            }
        }
    }

    private static void place(Minecraft mc, Camera camera, Vec3 eye, Vein v, List<double[]> taken) {
        final Vector3f look = camera.getLookVector();
        final Vector3f up = camera.getUpVector();
        final Vector3f left = camera.getLeftVector();
        final double dx = v.cx - eye.x;
        final double dy = v.cy - eye.y;
        final double dz = v.cz - eye.z;
        final double z = dx * look.x() + dy * look.y() + dz * look.z();
        if (z > 0.25) {
            boolean ok = taken.size() < MAX_LABELS && v.shown > 0.05f;
            if (ok) {
                final double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                final double scale = labelScale(dist);
                final double x = (dx * left.x() + dy * left.y() + dz * left.z()) / z;
                final double y = (dx * up.x() + dy * up.y() + dz * up.z()) / z;
                final double hw = (Math.max(mc.font.width(v.name), mc.font.width(v.depth)) * scale / 2.0 + 0.15) / z;
                final double hh = (17.0 * scale / 2.0 + 0.1) / z;
                for (final double[] t : taken) {
                    if (Math.abs(x - t[0]) < hw + t[2] && Math.abs(y - t[1]) < hh + t[3]) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    taken.add(new double[] {x, y, hw, hh});
                }
            }
            v.placed = ok;
        }
        v.labelOld = v.label;
        v.label = (float) VeinVisibility.ease(v.label, v.placed ? v.shown : 0.0);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            forget();
        }
    }

    private static void forget() {
        SURVEYS.clear();
        RETIRING.clear();
        LIT.clear();
        dwellRig = null;
        dwellTicks = 0;
        onRecorder = false;

        AT_RIG.clear();
        ASKED.clear();
        HINTED.clear();
        BATCHES.clear();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        final Matrix4f projection = event.getProjectionMatrix();
        viewTanV = projection.m11() != 0.0f ? Math.abs(1.0 / projection.m11()) : 0.0;
        viewTanH = projection.m00() != 0.0f ? Math.abs(1.0 / projection.m00()) : 0.0;
        if (LIT.isEmpty()) {
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        final float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        final double now = AnimationClock.time(mc.level, partial);
        final Vec3 camera = event.getCamera().getPosition();
        final PoseStack ms = event.getPoseStack();
        final MultiBufferSource.BufferSource buffers = Gpu.BUFFERS;
        final VertexConsumer xray = buffers.getBuffer(ThroughWalls.type());
        final PoseStack.Pose pose = ms.last();
        int drawn = 0;
        for (final Vein v : LIT) {
            final float a = Mth.lerp(partial, v.shownOld, v.shown);
            if (a <= VeinVisibility.GONE) {
                continue;
            }
            colour(v.colour, a * LINE_ALPHA, v.flashes ? (float) VeinVisibility.flash(now - v.revealAt) * FLASH_WHITE : 0.0f);
            final float[] r = v.runs;
            for (int i = 0; i + 4 < r.length && drawn < MAX_RUNS; i += 5, drawn++) {
                ORIGIN.set((float) (r[i] - camera.x), (float) (r[i + 1] - camera.y), (float) (r[i + 2] - camera.z));
                final Direction along = r[i + 3] == 0 ? Direction.EAST : r[i + 3] == 1 ? Direction.UP : Direction.SOUTH;
                LINE.bufferCuboidLine(pose, xray, ORIGIN, along, r[i + 4], WIDTH, COLOUR, LIGHT, false);
            }
        }
        buffers.endBatch(ThroughWalls.type());
        ThroughWalls.always(() -> labels(mc, ms, event, camera, partial));
    }

    private static void labels(Minecraft mc, PoseStack ms, RenderLevelStageEvent event, Vec3 camera, float partial) {
        final Font font = mc.font;
        final MultiBufferSource.BufferSource buffers = Gpu.LABELS;
        for (final Vein v : LIT) {
            final float a = Mth.lerp(partial, v.labelOld, v.label);
            if (a <= 0.05f) {
                continue;
            }
            final double dx = v.cx - camera.x;
            final double dy = v.cy - camera.y;
            final double dz = v.cz - camera.z;
            final float scale = (float) labelScale(Math.sqrt(dx * dx + dy * dy + dz * dz));
            ms.pushPose();
            ms.translate(dx, dy, dz);
            ms.mulPose(event.getCamera().rotation());
            ms.scale(scale, -scale, scale);
            final int alpha = Math.max(8, Math.round(255 * Math.min(1.0f, a)));
            final int back = Math.round(alpha * 0.45f) << 24;

            Matrix4f m = ms.last().pose();
            float x = -font.width(v.name) / 2.0f;
            font.drawInBatch(v.name, x, -9.0f, (4 << 24) | (v.colour & 0xFFFFFF), false, m, buffers,
                    Font.DisplayMode.SEE_THROUGH, back, LIGHT);
            font.drawInBatch(v.name, x, -9.0f, (alpha << 24) | (v.colour & 0xFFFFFF), false, m, buffers,
                    Font.DisplayMode.SEE_THROUGH, 0, LIGHT);

            ms.scale(0.75f, 0.75f, 0.75f);
            m = ms.last().pose();
            x = -font.width(v.depth) / 2.0f;
            final int dim = mix(v.colour, 0xB8B8B8, 0.55f);
            font.drawInBatch(v.depth, x, 1.0f, (4 << 24) | dim, false, m, buffers, Font.DisplayMode.SEE_THROUGH, back, LIGHT);
            font.drawInBatch(v.depth, x, 1.0f, (Math.round(alpha * 0.9f) << 24) | dim, false, m, buffers,
                    Font.DisplayMode.SEE_THROUGH, 0, LIGHT);
            ms.popPose();
        }
        buffers.endBatch();
    }

    static double labelScale(double dist) {
        return 0.022 * Math.max(1.0, Math.min(5.0, dist / 7.0));
    }

    private static int mix(int a, int b, float t) {
        int out = 0;
        for (int shift = 0; shift <= 16; shift += 8) {
            final int ca = (a >> shift) & 0xFF;
            final int cb = (b >> shift) & 0xFF;
            out |= Math.round(ca + (cb - ca) * t) << shift;
        }
        return out;
    }

    private static void colour(int rgb, float alpha, float white) {
        COLOUR.set(Mth.lerp(white, ((rgb >> 16) & 0xFF) / 255f, 1.0f), Mth.lerp(white, ((rgb >> 8) & 0xFF) / 255f, 1.0f),
                Mth.lerp(white, (rgb & 0xFF) / 255f, 1.0f), alpha);
    }

    private static final class Gpu {
        static final MultiBufferSource.BufferSource BUFFERS = ThroughWalls.newBuffers();
        static final MultiBufferSource.BufferSource LABELS = ThroughWalls.newBuffers();

        private Gpu() {
        }
    }
}
