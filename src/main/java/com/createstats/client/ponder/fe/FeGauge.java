package com.createstats.client.ponder.fe;

import com.createstats.client.ponder.Ease;
import com.createstats.client.ponder.KitInstruction;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createmod.catnip.outliner.Outline;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.function.IntToDoubleFunction;

// never a box round the cable, a flat track beside it
public final class FeGauge {
    static final int TRACK = 0x3A4550;
    static final int OVER = 0xFFB84A;

    private FeGauge() {
    }

    static final class Bar extends Outline {
        Vec3 from = Vec3.ZERO;
        Vec3 to = Vec3.ZERO;
        Vec3 across = new Vec3(0, 0, 1);
        double share;
        double prevShare;
        float grow;
        float prevGrow;

        @Override
        public void render(PoseStack ms, SuperRenderTypeBuffer buffer, Vec3 camera, float pt) {
            final float fade = params.getLineWidth() <= 0 ? 0f : params.getLineWidth() / (1 / 32f);
            if (fade <= 0.01f) return;
            final float g = (float) Ease.OUT_CUBIC.clamped(Mth.lerp(pt, prevGrow, grow));
            final double s = Mth.lerp(pt, prevShare, share);
            final VertexConsumer glass = FeBoxes.translucent(buffer);
            final PoseStack.Pose pose = ms.last();
            final Vec3 run = to.subtract(from);
            final Vec3 end = from.add(run.scale(g));
            slab(pose, glass, from, end, FeBoxes.rgba(TRACK, 0.55f * fade));
            final double carried = Math.min(1.0, s) * g;
            slab(pose, glass, from, from.add(run.scale(carried)), FeBoxes.rgba(FeFlow.GREEN, 0.9f * fade));
            if (s > 1.0) slab(pose, glass, from, end, FeBoxes.rgba(OVER, 0.9f * fade));
        }

        private void slab(PoseStack.Pose pose, VertexConsumer vc, Vec3 a, Vec3 b, org.joml.Vector4f c) {
            if (a.distanceToSqr(b) < 1.0e-6) return;
            final Vec3 w = across.scale(0.07);
            final Vector3f lo = new Vector3f((float) Math.min(a.x - w.x, b.x - w.x), (float) a.y,
                    (float) Math.min(a.z - w.z, b.z - w.z));
            final Vector3f hi = new Vector3f((float) Math.max(a.x + w.x, b.x + w.x), (float) a.y + 0.025f,
                    (float) Math.max(a.z + w.z, b.z + w.z));
            FeBoxes.box(this, pose, vc, lo, hi, c);
        }
    }

    public static KitInstruction bar(Object slot, Vec3 from, Vec3 to, Vec3 across, IntToDoubleFunction share,
                                     int start, int ticks) {
        return new KitInstruction(start, ticks) {
            private Bar bar;

            @Override
            protected void begin(PonderScene scene) {
                bar = new Bar();
                bar.from = from;
                bar.to = to;
                bar.across = across;
                bar.share = bar.prevShare = share.applyAsDouble(start);
                scene.getOutliner().showOutline(slot, bar).lineWidth(1 / 32f);
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                if (bar == null) return;
                bar.prevShare = bar.share;
                bar.prevGrow = bar.grow;
                bar.share = share.applyAsDouble(tick);
                bar.grow = Math.min(1f, (tick - start + 1) / 16f);
                if (!last) scene.getOutliner().keep(slot);
            }
        };
    }
}
