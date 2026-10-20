package com.createstats.client;

import com.createstats.registry.ModParticles;
import com.createstats.vacuum.SwirlPath;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@EventBusSubscriber(modid = "create_stats", value = Dist.CLIENT)
public final class VacuumFlights {
    private VacuumFlights() {
    }

    public static final float DURATION = 7.0f;

    private record Flight(Vec3 from, Vec3 to, ItemStack stack, int seed, float radius, long start) {
    }

    private static final List<Flight> FLIGHTS = new ArrayList<>();

    public static void add(Vec3 from, Vec3 to, ItemStack stack, int seed, float radius) {
        final Level level = Minecraft.getInstance().level;
        if (level == null || stack.isEmpty() || FLIGHTS.size() > 256) {
            return;
        }
        FLIGHTS.add(new Flight(from, to, stack.copyWithCount(1), seed, radius, level.getGameTime()));
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || FLIGHTS.isEmpty()) {
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        final Level level = mc.level;
        if (level == null) {
            FLIGHTS.clear();
            return;
        }
        final float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        final double now = level.getGameTime() + partial;
        final Vec3 cam = event.getCamera().getPosition();
        final PoseStack ps = event.getPoseStack();
        final MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        final Iterator<Flight> it = FLIGHTS.iterator();
        while (it.hasNext()) {
            final Flight f = it.next();
            final double t = (now - f.start) / DURATION;
            if (t >= 1.0) {
                puff(level, f.to);
                it.remove();
                continue;
            }
            final double phase = (f.seed & 0xFF) / 255.0 * Math.PI * 2.0;
            final double[] p = SwirlPath.at(new double[] {f.from.x, f.from.y, f.from.z},
                    new double[] {f.to.x, f.to.y, f.to.z}, f.radius, 1.25, phase, Math.max(0.0, t));
            final int light = net.minecraft.client.renderer.LevelRenderer.getLightColor(level,
                    BlockPos.containing(p[0], p[1], p[2]));
            ps.pushPose();
            ps.translate(p[0] - cam.x, p[1] - cam.y, p[2] - cam.z);
            final float s = 0.45f * SwirlPath.scale(t);
            ps.scale(s, s, s);
            final float tumble = SwirlPath.tumble(t, (f.seed >>> 8 & 0xFF) / 255.0);
            ps.mulPose(Axis.YP.rotationDegrees(tumble));
            ps.mulPose(Axis.XP.rotationDegrees(tumble * 0.7f));
            mc.getItemRenderer().renderStatic(f.stack, ItemDisplayContext.GROUND, light == 0 ? LightTexture.FULL_BRIGHT : light,
                    OverlayTexture.NO_OVERLAY, ps, buffers, level, f.seed);
            ps.popPose();
        }
        buffers.endBatch();
    }

    private static void puff(Level level, Vec3 at) {
        level.playLocalSound(at.x, at.y, at.z, com.createstats.registry.ModSounds.VACUUM_GULP.get(),
                net.minecraft.sounds.SoundSource.BLOCKS, 0.3f, 0.85f + level.random.nextFloat() * 0.3f, false);
        for (int i = 0; i < 3; i++) {
            final double ox = (level.random.nextDouble() - 0.5) * 0.3;
            final double oy = (level.random.nextDouble() - 0.5) * 0.3;
            final double oz = (level.random.nextDouble() - 0.5) * 0.3;
            level.addParticle(ModParticles.VACUUM_AIR.get(), at.x + ox, at.y + oy, at.z + oz, at.x, at.y, at.z);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        FLIGHTS.clear();
    }
}
