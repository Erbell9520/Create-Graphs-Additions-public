package com.createstats.client;

import com.createstats.CreateStats;
import com.createstats.blockentity.SmartHomePanelBE;
import com.createstats.blockentity.SwitchBE;
import com.createstats.item.SwitchLinkCardItem;
import com.createstats.power.FixtureLinker;
import com.createstats.security.SecurityDevices;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllItems;
import net.createmod.catnip.outliner.AABBOutline;
import net.createmod.catnip.outliner.LineOutline;
import net.createmod.catnip.render.PonderRenderTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// the dotted link lines. HomeSecurityPanelBE stays off it, nobody gets a map of which keypad opens which door
@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT)
public final class LinkView {
    private LinkView() {
    }

    enum Mode { OFF, CARD, WRENCH }

    static final int SCAN_TICKS = 10;

    private static final int GROW_TICKS = 4;

    private static final int FADE_TICKS = 8;

    private static final float WIDTH = 1 / 16f;

    private static final float XRAY_ALPHA = 0.35f;

    private static final int LIGHT = LightTexture.FULL_BRIGHT;

    static final int MAX_BEADS = 4096;

    private static Mode shown = Mode.OFF;
    private static int untilScan;
    private static float fade;
    private static float lastFade;
    @Nullable
    private static ClientLevel scanned;
    @Nullable
    private static LinkWeb web;

    private static final Map<LinkWeb.Spot, Vec3> ANCHORS = new HashMap<>();

    private static final Map<LinkWeb.Spot, AABB> BOXES = new HashMap<>();

    private static final LineOutline LINE = new LineOutline();
    private static final Box BOX = new Box();
    private static final Vector3d START = new Vector3d();
    private static final Vector3d END = new Vector3d();
    private static final Vector4f COLOUR = new Vector4f();

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        final Minecraft mc = Minecraft.getInstance();
        final Mode held = mc.player == null || mc.level == null ? Mode.OFF : modeOf(mc.player);
        if (held == Mode.OFF && web == null) {
            return;
        }
        lastFade = fade;
        if (held == Mode.OFF) {
            fade = Math.max(0f, fade - 1f / FADE_TICKS);
            if (fade <= 0f) {
                clear();
            }
            return;
        }
        fade = Math.min(1f, fade + 1f / GROW_TICKS);
        shown = held;
        if (web == null || mc.level != scanned || --untilScan <= 0) {
            scan(mc.level, mc.player);
            untilScan = SCAN_TICKS;
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            clear();
        }
    }

    private static Mode modeOf(Player player) {
        if (holds(player, true)) {
            return Mode.CARD;
        }
        return holds(player, false) ? Mode.WRENCH : Mode.OFF;
    }

    private static boolean holds(Player player, boolean card) {
        return isTool(player.getMainHandItem(), card) || isTool(player.getOffhandItem(), card);
    }

    private static boolean isTool(ItemStack stack, boolean card) {
        return card ? stack.getItem() instanceof SwitchLinkCardItem : AllItems.WRENCH.isIn(stack);
    }

    private static void scan(ClientLevel level, Player player) {
        scanned = level;
        ANCHORS.clear();
        BOXES.clear();
        final Vec3 eye = player.getEyePosition();
        final int reach = Mth.ceil(LinkWeb.REACH / 16.0);
        final int cx = SectionPos.blockToSectionCoord(eye.x);
        final int cz = SectionPos.blockToSectionCoord(eye.z);
        final List<BlockEntity> hosts = new ArrayList<>();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                final LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (chunk == null) {
                    continue;
                }
                for (final BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof FixtureLinker && drawnSource(be)) {
                        hosts.add(be);
                    }
                }
            }
        }
        final List<LinkWeb.Link> found = new ArrayList<>();
        for (final BlockEntity host : hosts) {
            final BlockPos at = host.getBlockPos();
            for (final BlockPos fixture : ((FixtureLinker) host).linkedFixtures()) {
                if (fixture.equals(at) || !level.isLoaded(fixture)) {
                    continue;
                }
                final BlockEntity target = level.getBlockEntity(fixture);

                if (target == null || SecurityDevices.isDevice(target)) {
                    continue;
                }
                found.add(new LinkWeb.Link(spot(at), spot(fixture)));
            }
        }
        web = LinkWeb.near(found, eye.x, eye.y, eye.z);
        for (final LinkWeb.Link link : web.links()) {
            measure(level, link.controller());
            measure(level, link.fixture());
        }
    }

    private static boolean drawnSource(BlockEntity be) {
        return (be instanceof SwitchBE || be instanceof SmartHomePanelBE) && !SecurityDevices.isDevice(be);
    }

    private static void measure(ClientLevel level, LinkWeb.Spot spot) {
        if (BOXES.containsKey(spot)) {
            return;
        }
        final BlockPos pos = new BlockPos(spot.x(), spot.y(), spot.z());
        final VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
        final AABB box = shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
        BOXES.put(spot, box);
        ANCHORS.put(spot, box.getCenter());
    }

    private static LinkWeb.Spot spot(BlockPos pos) {
        return new LinkWeb.Spot(pos.getX(), pos.getY(), pos.getZ());
    }

    private static void clear() {
        web = null;
        scanned = null;
        shown = Mode.OFF;
        fade = 0f;
        lastFade = 0f;
        untilScan = 0;
        ANCHORS.clear();
        BOXES.clear();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || web == null || shown == Mode.OFF) {
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != scanned) {
            return;
        }
        final float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        final float grown = Mth.lerp(partial, lastFade, fade);

        final float width = WIDTH * grown * grown * grown;
        if (width < 1.0e-4f) {
            return;
        }
        final LinkWeb.Selection selection = web.select(looked(mc));
        if (shown == Mode.WRENCH && selection.isEmpty()) {
            return;
        }
        final double march = LinkWeb.marchAt(mc.level.getGameTime() + partial);
        final Vec3 camera = event.getCamera().getPosition();
        final PoseStack ms = event.getPoseStack();
        final MultiBufferSource.BufferSource buffers = Gpu.BUFFERS;

        if (!selection.isEmpty()) {
            final VertexConsumer xray = buffers.getBuffer(ThroughWalls.type());
            for (final LinkWeb.Link link : selection.links()) {
                line(ms, xray, camera, link, march, width, XRAY_ALPHA, true);
            }
            for (final LinkWeb.Mark mark : selection.outlined()) {
                box(ms, xray, camera, mark, width, XRAY_ALPHA);
            }
            buffers.endBatch(ThroughWalls.type());
        }

        final VertexConsumer solid = buffers.getBuffer(PonderRenderTypes.outlineSolid());
        int beads = 0;
        for (final LinkWeb.Link link : selection.links()) {
            beads += line(ms, solid, camera, link, march, width, 1f, true);
        }
        if (shown == Mode.CARD) {
            for (final LinkWeb.Link link : web.links()) {
                if (!selection.contains(link)) {
                    beads += line(ms, solid, camera, link, 0.0, width, 1f, beads < MAX_BEADS);
                }
            }
        }
        for (final LinkWeb.Mark mark : selection.outlined()) {
            box(ms, solid, camera, mark, width, 1f);
        }
        buffers.endBatch(PonderRenderTypes.outlineSolid());
    }

    @Nullable
    private static LinkWeb.Spot looked(Minecraft mc) {
        return mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                ? spot(hit.getBlockPos()) : null;
    }

    private static int line(PoseStack ms, VertexConsumer consumer, Vec3 camera, LinkWeb.Link link, double march,
                            float width, float alpha, boolean dotted) {
        final Vec3 from = ANCHORS.get(link.controller());
        final Vec3 to = ANCHORS.get(link.fixture());
        if (from == null || to == null) {
            return 0;
        }
        final double dx = to.x - from.x;
        final double dy = to.y - from.y;
        final double dz = to.z - from.z;
        final double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0e-4) {
            return 0;
        }
        colour(LinkWeb.colourOf(link.controller()), alpha);
        if (!dotted) {
            START.set(from.x, from.y, from.z);
            END.set(to.x, to.y, to.z);
            LINE.bufferCuboidLine(ms, consumer, camera, START, END, width, COLOUR, LIGHT, false);
            return 1;
        }
        final double[] beads = LinkWeb.beads(length, march);
        for (int i = 0; i < beads.length; i += 2) {
            final double a = beads[i] / length;
            final double b = beads[i + 1] / length;
            START.set(from.x + dx * a, from.y + dy * a, from.z + dz * a);
            END.set(from.x + dx * b, from.y + dy * b, from.z + dz * b);
            LINE.bufferCuboidLine(ms, consumer, camera, START, END, width, COLOUR, LIGHT, false);
        }
        return beads.length / 2;
    }

    private static void box(PoseStack ms, VertexConsumer consumer, Vec3 camera, LinkWeb.Mark mark, float width,
                            float alpha) {
        final AABB bounds = BOXES.get(mark.spot());
        if (bounds != null) {
            colour(mark.colour(), alpha);
            BOX.edges(ms, consumer, camera, bounds, width, COLOUR);
        }
    }

    private static void colour(int rgb, float alpha) {
        COLOUR.set(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, alpha);
    }

    private static final class Box extends AABBOutline {
        private final Vector3f min = new Vector3f();
        private final Vector3f max = new Vector3f();

        Box() {
            super(new AABB(0, 0, 0, 1, 1, 1));
        }

        void edges(PoseStack ms, VertexConsumer consumer, Vec3 camera, AABB bounds, float width, Vector4f colour) {
            final float grow = 1 / 128f;
            min.set((float) (bounds.minX - camera.x) - grow, (float) (bounds.minY - camera.y) - grow,
                    (float) (bounds.minZ - camera.z) - grow);
            max.set((float) (bounds.maxX - camera.x) + grow, (float) (bounds.maxY - camera.y) + grow,
                    (float) (bounds.maxZ - camera.z) + grow);
            renderBoxEdges(ms, consumer, min, max, width, colour, LIGHT, false);
        }
    }

    private static final class Gpu {
        static final MultiBufferSource.BufferSource BUFFERS = ThroughWalls.newBuffers();

        private Gpu() {
        }
    }
}
