package com.createstats.client.ponder.seismic;

import com.createstats.client.ThroughWalls;
import com.createstats.client.ponder.KitInstruction;
import com.createstats.seismic.SeismicWave;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createmod.catnip.outliner.Outline;
import net.createmod.catnip.render.PonderRenderTypes;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector4f;

import java.util.List;

public final class Wave {
    static final int FRONT = 0xF2F1EA;
    static final float FRONT_WIDTH = 1 / 18f;
    static final float ECHO_WIDTH = 1 / 11f;
    static final double PROUD = 0.03;
    static final int SEGMENTS = 120;
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    private Wave() {
    }

    public record Face(int axis, double at, double u0, double u1, double v0, double v1, double[] hole, int facing) {
        boolean keeps(double u, double v) {
            if (u < u0 - 1e-6 || u > u1 + 1e-6 || v < v0 - 1e-6 || v > v1 + 1e-6) {
                return false;
            }
            return hole == null || u <= hole[0] || u >= hole[1] || v <= hole[2] || v >= hole[3];
        }

        Vector3d point(double u, double v) {
            final double p = at + facing * PROUD;
            return switch (axis) {
                case 0 -> new Vector3d(p, v, u);
                case 1 -> new Vector3d(u, p, v);
                default -> new Vector3d(u, v, p);
            };
        }
    }

    static final class Front extends Outline {
        Vec3 strike = Vec3.ZERO;
        List<Face> faces = List.of();

        double fadeAt = 12.0;
        SeismicPonderSurvey.Survey survey;
        int impact;
        int tick = Integer.MIN_VALUE;
        private final Vector4f colour = new Vector4f();

        @Override
        public void render(PoseStack ms, SuperRenderTypeBuffer buffer, Vec3 camera, float pt) {
            if (tick == Integer.MIN_VALUE) {
                return;
            }
            final double t = tick + pt - impact;
            if (t <= 0.0) {
                return;
            }
            final double front = SeismicWave.frontAt(t);
            final double grow = smooth(t / 1.5);
            final double alpha = grow * (1.0 - smooth(front / fadeAt));
            if (alpha > 0.02) {
                final VertexConsumer vc = buffer.getBuffer(PonderRenderTypes.outlineTranslucent(WHITE, false));
                colour.set(((FRONT >> 16) & 0xFF) / 255f, ((FRONT >> 8) & 0xFF) / 255f, (FRONT & 0xFF) / 255f,
                        (float) (0.95 * alpha));
                final float w = (float) (FRONT_WIDTH * (1.0 - 0.4 * smooth(front / fadeAt)));
                for (final Face f : faces) {
                    ring(ms, vc, f, front, w);
                }
            }
            if (survey != null) {
                echoes(ms, buffer.getBuffer(ThroughWalls.type()), t);
            }
        }

        private void ring(PoseStack ms, VertexConsumer vc, Face f, double front, float w) {
            final double cu;
            final double cv;
            final double d;
            switch (f.axis()) {
                case 0 -> {
                    cu = strike.z;
                    cv = strike.y;
                    d = Math.abs(f.at() - strike.x);
                }
                case 1 -> {
                    cu = strike.x;
                    cv = strike.z;
                    d = Math.abs(f.at() - strike.y);
                }
                default -> {
                    cu = strike.x;
                    cv = strike.y;
                    d = Math.abs(f.at() - strike.z);
                }
            }
            if (front <= d) {
                return;
            }
            final double r = Math.sqrt(front * front - d * d);

            final boolean wall = f.axis() != 1;
            final double a0 = wall ? Math.PI : 0.0;
            final double a1 = wall ? 2.0 * Math.PI : 2.0 * Math.PI;
            double pu = Double.NaN;
            double pv = Double.NaN;
            boolean pin = false;
            for (int i = 0; i <= SEGMENTS; i++) {
                final double a = a0 + (a1 - a0) * i / SEGMENTS;
                final double u = cu + r * Math.cos(a);
                final double v = cv + r * Math.sin(a);
                final boolean in = f.keeps(u, v) && (!wall || v <= cv + 1e-6);
                if (in && pin) {
                    bufferCuboidLine(ms, vc, Vec3.ZERO, f.point(pu, pv), f.point(u, v), w, colour, LightTexture.FULL_BRIGHT,
                            false);
                }
                pu = u;
                pv = v;
                pin = in;
            }
        }

        private void echoes(PoseStack ms, VertexConsumer vc, double t) {
            for (final SeismicPonderSurvey.Vein v : survey.veins()) {
                final double out = v.nearest() / SeismicWave.BLOCKS_PER_TICK;
                final double back = v.echo();
                if (t < out || t > back + 0.5 || back - out < 1e-6) {
                    continue;
                }
                final double k = Math.min(1.0, (t - out) / (back - out));
                final double[] n = v.near();
                final Vec3 from = new Vec3(n[0], n[1], n[2]);
                final Vec3 path = strike.subtract(from);
                final double len = path.length();
                final Vec3 dir = path.scale(1.0 / Math.max(1e-6, len));
                final Vec3 head = from.add(path.scale(k));
                final double tail = Math.min(0.55, len * k);
                final Vec3 back0 = head.subtract(dir.scale(tail));
                final int rgb = v.colour();

                final double a = smooth((t - out) / 0.6) * (1.0 - smooth((t - back + 0.3) / 0.8));
                if (a <= 0.02) {
                    continue;
                }
                colour.set(Math.min(1f, ((rgb >> 16) & 0xFF) / 255f * 0.6f + 0.4f),
                        Math.min(1f, ((rgb >> 8) & 0xFF) / 255f * 0.6f + 0.4f), Math.min(1f, (rgb & 0xFF) / 255f * 0.6f + 0.4f),
                        (float) a);
                bufferCuboidLine(ms, vc, Vec3.ZERO, new Vector3d(back0.x, back0.y, back0.z), new Vector3d(head.x, head.y, head.z),
                        ECHO_WIDTH, colour, LightTexture.FULL_BRIGHT, false);
            }
        }
    }

    static double smooth(double u) {
        final double v = Math.max(0.0, Math.min(1.0, u));
        return v * v * (3.0 - 2.0 * v);
    }

    public static KitInstruction wave(Object slot, Vec3 strike, List<Face> faces, double fadeAt,
                                      SeismicPonderSurvey.Survey survey, int impact, int start, int ticks) {
        return new KitInstruction(start, ticks) {
            private Front front;

            @Override
            protected void begin(PonderScene scene) {
                front = new Front();
                front.strike = strike;
                front.faces = faces;
                front.fadeAt = fadeAt;
                front.survey = survey;
                front.impact = impact;
                scene.getOutliner().showOutline(slot, front).lineWidth(1 / 32f);
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                if (front == null) {
                    return;
                }
                front.tick = tick;
                if (!last) {
                    scene.getOutliner().keep(slot);
                }
            }
        };
    }
}
