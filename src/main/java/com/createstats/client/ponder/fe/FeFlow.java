package com.createstats.client.ponder.fe;

import com.createstats.client.ponder.KitInstruction;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createmod.catnip.outliner.Outline;
import net.createmod.catnip.render.PonderRenderTypes;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

public final class FeFlow {
    public static final int GREEN = 0x58DC7A;

    static final double SPEED = 0.22;

    static final double PER_ROOT_FE = 0.0216;

    static final double HALF = 0.085;

    public static final double WIDTH = 1 / 13.0;

    static final double PROUD = 0.015;

    private FeFlow() {
    }

    @FunctionalInterface
    public interface Rate {
        double at(int sceneTick);
    }

    public record Stream(FePaths.Path path, Rate rate, double width, java.util.function.IntPredicate present, int then) {
        public Stream(FePaths.Path path, Rate rate) {
            this(path, rate, WIDTH, t -> true, -1);
        }

        public static Stream wire(FePaths.Path path, Rate rate, com.createstats.power.WireTier tier,
                                  java.util.function.IntPredicate present) {
            return new Stream(path, rate, onWire(tier), present, -1);
        }

        public Stream then(int next) {
            return new Stream(path, rate, width, present, next);
        }
    }

    public static double onWire(com.createstats.power.WireTier tier) {
        return 2.0 * (tier.halfThickness() * 1.15 + PROUD);
    }

    public static double beadsPerTick(double fePerTick) {
        return fePerTick <= 0.0 ? 0.0 : PER_ROOT_FE * Math.sqrt(fePerTick);
    }

    public static double taper(double s, double length) {
        return Math.min(1.0, Math.min(s / 0.3, (length - s) / 0.3));
    }

    static final class Emitter {
        final List<Stream> streams;

        final List<double[]> beads = new ArrayList<>();
        private final double[] owed;

        Emitter(List<Stream> streams) {
            this.streams = streams;
            owed = new double[streams.size()];

            for (int i = 0; i < owed.length; i++) owed[i] = 0.93 - 0.19 * i % 0.9;
        }

        void step(int tick) {
            for (int i = beads.size() - 1; i >= 0; i--) {
                final double[] b = beads.get(i);
                Stream on = streams.get((int) b[0]);
                if (!on.present().test(tick) && on.then() >= 0 && streams.get(on.then()).present().test(tick)) {
                    final double along = b[1] / Math.max(1.0e-6, on.path().length());
                    b[0] = on.then();
                    on = streams.get(on.then());
                    b[1] = along * on.path().length();
                }
                b[2] = b[1];
                b[1] += SPEED;
                if (b[1] > on.path().length() || !on.present().test(tick)) beads.remove(i);
            }

            for (int i = 0; i < streams.size(); i++) {
                owed[i] += beadsPerTick(Math.max(0.0, streams.get(i).rate().at(tick)));
                while (owed[i] >= 1.0) {
                    owed[i] -= 1.0;
                    // offset by how late in the tick, or the flow bunches on tick boundaries

                    final double s = owed[i] * SPEED;
                    beads.add(new double[] {i, s, Math.max(0.0, s - SPEED)});
                }
            }
        }
    }

    static final class Beads extends Outline {
        Emitter emitter;

        @Override
        public void render(PoseStack ms, SuperRenderTypeBuffer buffer, Vec3 camera, float pt) {
            final float scale = params.getLineWidth() <= 0 ? 0 : params.getLineWidth() / (1 / 32f);
            if (scale <= 0 || emitter == null || emitter.beads.isEmpty()) return;
            final VertexConsumer vc = buffer.getBuffer(PonderRenderTypes.outlineSolid());
            params.loadColor(colorTemp);
            final Vector4f c = colorTemp;
            for (double[] b : emitter.beads) {
                final Stream stream = emitter.streams.get((int) b[0]);
                final FePaths.Path path = stream.path();
                final float w = (float) (stream.width() * scale);
                final double s = Mth.lerp(pt, b[2], b[1]);

                final double taper = taper(s, path.length());
                if (taper <= 0.02) continue;
                final Vec3 a = path.at(s - HALF * taper);
                final Vec3 e = path.at(s + HALF * taper);
                bufferCuboidLine(ms, vc, Vec3.ZERO, new Vector3d(a.x, a.y, a.z), new Vector3d(e.x, e.y, e.z),
                        (float) (w * (0.55 + 0.45 * taper)), c, LightTexture.FULL_BRIGHT, false);
            }
        }
    }

    public static KitInstruction beads(Object slot, List<Stream> streams, int rgb, int start, int ticks) {
        return new KitInstruction(start, ticks) {
            private Beads outline;

            @Override
            protected void begin(PonderScene scene) {
                outline = new Beads();
                outline.emitter = new Emitter(streams);
                scene.getOutliner().showOutline(slot, outline).colored(rgb).lineWidth(1 / 32f);
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                if (outline == null) return;
                outline.emitter.step(tick);
                if (!last) scene.getOutliner().keep(slot);
            }
        };
    }
}
