package com.createstats.seismic.client;

import com.createstats.CreateStats;
import com.createstats.client.ThroughWalls;
import com.createstats.client.tuner.TunerPreviews;
import com.createstats.registry.ModBlockEntities;
import com.createstats.seismic.SeismicCatalog;
import com.createstats.seismic.SurveyBowl;
import com.createstats.tuner.TunerStation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createmod.catnip.outliner.LineOutline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class SeismicPreview {
    private static final float WIDTH = 1 / 40f;
    private static final LineOutline LINE = new LineOutline();
    private static final Vector3d START = new Vector3d();
    private static final Vector3d END = new Vector3d();
    private static final Vector4f COLOUR = new Vector4f();

    static final double DEPTH_LABEL = 0.0176;

    private SeismicPreview() {
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> TunerPreviews.register(ModBlockEntities.SEISMIC_SCANNER.get(), SeismicPreview::draw));
    }

    static void draw(PoseStack.Pose pose, VertexConsumer lines, BlockEntity be, @Nullable TunerStation station,
                     double radius, int argb) {
        if (radius <= 0.0) {
            return;
        }
        final int segments = (int) Math.max(32, Math.min(96, Math.round(radius * 1.5)));
        final List<double[]> dashes = SurveyBowl.dashes(radius, segments);
        final PoseStack ps = new PoseStack();
        ps.last().pose().set(pose.pose());
        ps.last().normal().set(pose.normal());
        COLOUR.set(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f,
                ((argb >>> 24) / 255f) * 0.7f);
        final MultiBufferSource.BufferSource buffers = Gpu.BUFFERS;
        final VertexConsumer xray = buffers.getBuffer(ThroughWalls.type());

        for (final double[] d : dashes) {
            START.set(0.5 + d[0], d[1], 0.5 + d[2]);
            END.set(0.5 + d[3], d[4], 0.5 + d[5]);
            LINE.bufferCuboidLine(ps, xray, Vec3.ZERO, START, END, WIDTH, COLOUR, LightTexture.FULL_BRIGHT, false);
        }
        buffers.endBatch(ThroughWalls.type());
        final int r = (int) Math.round(radius);
        final String depth = "Y " + SeismicCatalog.bowlBottom(be.getBlockPos().getY(), r);
        ThroughWalls.always(() -> depthLabel(ps, depth, -radius - 0.75, argb));
    }

    private static void depthLabel(PoseStack ps, String text, double y, int argb) {
        final Minecraft mc = Minecraft.getInstance();
        final Font font = mc.font;
        final Vector3f at = ps.last().pose().transformPosition(new Vector3f(0.5f, (float) y, 0.5f));

        final float scale = (float) (DEPTH_LABEL * Math.max(1.0, at.length() / 7.0));
        ps.pushPose();
        ps.translate(0.5, y, 0.5);
        ps.mulPose(mc.gameRenderer.getMainCamera().rotation());
        ps.scale(scale, -scale, scale);
        final Matrix4f m = ps.last().pose();
        final int alpha = Math.max(8, Math.round((argb >>> 24) * 0.9f));
        final int rgb = argb & 0xFFFFFF;
        final float x = -font.width(text) / 2.0f;
        final MultiBufferSource.BufferSource labels = Gpu.LABELS;
        font.drawInBatch(text, x, -4.0f, (4 << 24) | rgb, false, m, labels, Font.DisplayMode.SEE_THROUGH,
                Math.round(alpha * 0.45f) << 24, LightTexture.FULL_BRIGHT);
        font.drawInBatch(text, x, -4.0f, (alpha << 24) | rgb, false, m, labels, Font.DisplayMode.SEE_THROUGH, 0,
                LightTexture.FULL_BRIGHT);
        ps.popPose();
        labels.endBatch();
    }

    private static final class Gpu {
        static final MultiBufferSource.BufferSource BUFFERS = ThroughWalls.newBuffers();
        static final MultiBufferSource.BufferSource LABELS = ThroughWalls.newBuffers();

        private Gpu() {
        }
    }
}
