package com.createstats.client.tuner;

import com.createstats.tuner.PreviewLinger;
import com.createstats.tuner.TunerStation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// only drawn while tuning plus a few seconds, never every frame
@EventBusSubscriber(modid = "create_stats", value = Dist.CLIENT)
public final class TunerPreviews {
    @FunctionalInterface
    public interface Drawer {
        void draw(PoseStack.Pose pose, VertexConsumer lines, BlockEntity be, @Nullable TunerStation station,
                  double radius, int argb);
    }

    private static final Map<BlockEntityType<?>, Drawer> DRAWERS = new HashMap<>();
    private static final double EASE_MS = 70.0;

    @Nullable
    private static Object owner;
    @Nullable
    private static BlockPos pos;
    @Nullable
    private static TunerStation station;
    private static int range;
    private static double shown = -1.0;
    private static long lastFrame;

    private static long releasedAt;

    private TunerPreviews() {
    }

    public static void register(BlockEntityType<?> type, Drawer drawer) {
        DRAWERS.put(type, drawer);
    }

    public static void show(Object by, BlockPos at, @Nullable TunerStation previewed, int previewRange, int startRange) {
        if (!at.equals(pos)) {
            shown = startRange;
        }
        owner = by;
        pos = at.immutable();
        station = previewed;
        range = previewRange;
        releasedAt = 0L;
    }

    public static void release(Object by) {
        if (owner == by) {
            owner = null;
            releasedAt = Math.max(1L, Util.getMillis());
        }
    }

    public static void release(Object by, @Nullable TunerStation settled, int settledRange) {
        if (owner == by) {
            station = settled;
            range = settledRange;
            release(by);
        }
    }

    private static void forget() {
        owner = null;
        pos = null;
        station = null;
        shown = -1.0;
        releasedAt = 0L;
    }

    public static boolean showing(Object by) {
        return owner == by;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || pos == null) {
            return;
        }
        final long now = Util.getMillis();
        final long since = releasedAt == 0L ? 0L : now - releasedAt;
        if (PreviewLinger.gone(since)) {
            forget();
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        final BlockEntity be = mc.level.getBlockEntity(pos);
        final Drawer drawer = be == null ? null : DRAWERS.get(be.getType());
        if (drawer == null) {
            return;
        }
        final double dt = Math.min(250.0, Math.max(0.0, now - lastFrame));
        lastFrame = now;
        if (shown < 0.0) {
            shown = range;
        }
        shown += (range - shown) * (1.0 - Math.exp(-dt / EASE_MS));
        if (Math.abs(range - shown) < 1.0e-3) {
            shown = range;
        }
        final int argb = PreviewLinger.fade(station == null ? 0xC0B0B0B0 : 0xE0000000 | (station.colour() & 0xFFFFFF),
                PreviewLinger.strength(since));

        final Vec3 camera = event.getCamera().getPosition();
        final PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
        final VertexConsumer lines = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        drawer.draw(pose.last(), lines, be, station, shown, argb);
        mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
        pose.popPose();
    }

    public static void polylines(PoseStack.Pose pose, VertexConsumer lines, List<double[]> polys, double ox, double oy,
                                 double oz, int argb) {
        final float a = (argb >>> 24) / 255.0f;
        final float r = ((argb >> 16) & 0xFF) / 255.0f;
        final float g = ((argb >> 8) & 0xFF) / 255.0f;
        final float b = (argb & 0xFF) / 255.0f;
        for (double[] p : polys) {
            for (int i = 0; i + 5 < p.length; i += 3) {
                final float x0 = (float) (ox + p[i]);
                final float y0 = (float) (oy + p[i + 1]);
                final float z0 = (float) (oz + p[i + 2]);
                final float x1 = (float) (ox + p[i + 3]);
                final float y1 = (float) (oy + p[i + 4]);
                final float z1 = (float) (oz + p[i + 5]);
                float nx = x1 - x0;
                float ny = y1 - y0;
                float nz = z1 - z0;
                final float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len < 1.0e-6f) {
                    continue;
                }
                nx /= len;
                ny /= len;
                nz /= len;
                lines.addVertex(pose, x0, y0, z0).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
                lines.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
            }
        }
    }
}
