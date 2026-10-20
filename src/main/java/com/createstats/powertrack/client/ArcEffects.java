package com.createstats.powertrack.client;

import com.createstats.CreateStats;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT)
public final class ArcEffects {
    public static final int LIFE = 4;

    public static final int FLASH_LIGHT = 13;
    private static final int MAX = 64;

    private record Arc(Vec3 top, Vec3 bottom, int seed, long start) {
    }

    private static final List<Arc> ARCS = new ArrayList<>();

    private ArcEffects() {
    }

    public static void add(Vec3 top, Vec3 bottom, int seed) {
        final Level level = Minecraft.getInstance().level;
        if (level == null || ARCS.size() >= MAX) {
            return;
        }
        final Arc arc = new Arc(top, bottom, seed, level.getGameTime());
        ARCS.add(arc);

        com.createstats.client.dynlight.DynamicLights.submit(arc, com.createstats.dynlight.PointLight.at(bottom, FLASH_LIGHT));
    }

    @SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        final Level level = Minecraft.getInstance().level;
        if (level == null || ARCS.isEmpty()) {
            return;
        }
        final long now = level.getGameTime();
        for (Arc arc : ARCS) {
            if (now - arc.start() < LIFE) {
                com.createstats.client.dynlight.DynamicLights.submit(arc,
                        com.createstats.dynlight.PointLight.at(arc.bottom(), FLASH_LIGHT));
            }
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ARCS.isEmpty()) {
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        final Level level = mc.level;
        if (level == null) {
            ARCS.clear();
            return;
        }
        final long now = level.getGameTime();
        final float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        final Vec3 cam = event.getCamera().getPosition();
        final PoseStack ps = event.getPoseStack();
        final MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        final VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        for (Iterator<Arc> it = ARCS.iterator(); it.hasNext(); ) {
            final Arc arc = it.next();
            final double age = now - arc.start() + partial;
            if (age >= LIFE || age < -1) {
                it.remove();
                continue;
            }
            final float fade = (float) Math.max(0.0, 1.0 - age / LIFE);
            ps.pushPose();
            ps.translate(arc.bottom().x - cam.x, arc.bottom().y - cam.y, arc.bottom().z - cam.z);
            bolt(ps.last().pose(), vc, arc.top().subtract(arc.bottom()), arc.seed(), (int) (now - arc.start()), fade);
            ps.popPose();
        }
        buffers.endBatch(RenderType.lightning());
    }

    public static void bolt(Matrix4f m, VertexConsumer vc, Vec3 up, int seed, int tickAge, float fade) {
        final double hx = up.x;
        final double hy = up.y;
        final double hz = up.z;
        final java.util.Random r = new java.util.Random(seed * 31L + tickAge);
        final int points = 5;
        final float[][] p = new float[points][3];
        for (int i = 0; i < points; i++) {
            final double f = i / (double) (points - 1);
            final double jitter = (i == 0 || i == points - 1) ? 0.0 : 0.06;
            p[i][0] = (float) (hx * f + (r.nextDouble() - 0.5) * 2 * jitter);
            p[i][1] = (float) (hy * f);
            p[i][2] = (float) (hz * f + (r.nextDouble() - 0.5) * 2 * jitter);
        }
        for (int i = 0; i + 1 < points; i++) {
            ribbon(m, vc, p[i], p[i + 1], 0.07f, 0x7F, 0xC8, 0xFF, (int) (150 * fade));
            ribbon(m, vc, p[i], p[i + 1], 0.025f, 0xF4, 0xFB, 0xFF, (int) (235 * fade));
        }

        final float core = fade * fade;
        star(m, vc, p[0], 0.22f, 0x8C, 0xC8, 0xFF, (int) (90 * fade));
        star(m, vc, p[0], 0.10f, 0xFF, 0xFF, 0xFF, (int) (230 * core));
    }

    private static void star(Matrix4f m, VertexConsumer vc, float[] c, float r, int red, int green, int blue, int alpha) {
        final float x = c[0];
        final float y = c[1];
        final float z = c[2];

        final float[][][] faces = {
                {{x - r, y - r, z}, {x + r, y - r, z}, {x + r, y + r, z}, {x - r, y + r, z}},
                {{x, y - r, z - r}, {x, y - r, z + r}, {x, y + r, z + r}, {x, y + r, z - r}},
                {{x - r, y, z - r}, {x + r, y, z - r}, {x + r, y, z + r}, {x - r, y, z + r}}};
        for (float[][] f : faces) {
            for (int i = 0; i < 4; i++) {
                vc.addVertex(m, f[i][0], f[i][1], f[i][2]).setColor(red, green, blue, alpha);
            }
            for (int i = 3; i >= 0; i--) {
                vc.addVertex(m, f[i][0], f[i][1], f[i][2]).setColor(red, green, blue, alpha);
            }
        }
    }

    private static void ribbon(Matrix4f m, VertexConsumer vc, float[] a, float[] b, float w, int red, int green,
                               int blue, int alpha) {
        for (int k = 0; k < 2; k++) {
            final float dx = k == 0 ? w : 0;
            final float dz = k == 0 ? 0 : w;
            vc.addVertex(m, a[0] - dx, a[1], a[2] - dz).setColor(red, green, blue, alpha);
            vc.addVertex(m, a[0] + dx, a[1], a[2] + dz).setColor(red, green, blue, alpha);
            vc.addVertex(m, b[0] + dx, b[1], b[2] + dz).setColor(red, green, blue, alpha);
            vc.addVertex(m, b[0] - dx, b[1], b[2] - dz).setColor(red, green, blue, alpha);
            vc.addVertex(m, b[0] - dx, b[1], b[2] - dz).setColor(red, green, blue, alpha);
            vc.addVertex(m, b[0] + dx, b[1], b[2] + dz).setColor(red, green, blue, alpha);
            vc.addVertex(m, a[0] + dx, a[1], a[2] + dz).setColor(red, green, blue, alpha);
            vc.addVertex(m, a[0] - dx, a[1], a[2] - dz).setColor(red, green, blue, alpha);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ARCS.clear();
    }
}
