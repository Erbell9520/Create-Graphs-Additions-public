package com.createstats.client.ponder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createmod.catnip.outliner.Outline;
import net.createmod.catnip.render.PonderRenderTypes;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.function.IntFunction;
import java.util.function.IntToDoubleFunction;

public final class Marks {
    public static final int HELD = 0xFFFF55;
    public static final int CEILING = 0xFF5555;
    public static final int PUSH = 0x55FF55;
    public static final int AQUA = 0x55FFFF;
    public static final int GOLD = 0xFFAA00;
    public static final int WEIGHT = 0xE8E8E8;

    public static final int AIR = 0xF0F4F8;
    public static final int HOT_AIR = 0xFF9E2C;

    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    private Marks() {
    }

    static Vec3 eye(PonderScene scene) {
        double xr = Math.toRadians(scene.getTransform().xRotation.getValue());
        double yr = Math.toRadians(scene.getTransform().yRotation.getValue());

        return new Vec3(-Math.cos(xr) * Math.sin(yr), -Math.sin(xr), Math.cos(xr) * Math.cos(yr));
    }

    abstract static class Lerped extends Outline {
        float width = 1 / 16f;

        static Vector3d lerp(Vec3 a, Vec3 b, float pt) {
            return new Vector3d(Mth.lerp(pt, a.x, b.x), Mth.lerp(pt, a.y, b.y), Mth.lerp(pt, a.z, b.z));
        }

        void line(PoseStack ms, VertexConsumer vc, Vector3d a, Vector3d b, float w, Vector4f colour) {
            bufferCuboidLine(ms, vc, Vec3.ZERO, a, b, w, colour, LightTexture.FULL_BRIGHT, false);
        }

        Vector4f colour() {
            params.loadColor(colorTemp);
            return colorTemp;
        }

        float width() {
            return params.getLineWidth() <= 0 ? 0 : width * params.getLineWidth() / (1 / 32f);
        }
    }

    static final class Dashes extends Lerped {
        double x0;
        double x1;
        double z;
        double y;
        double prevY;
        double reach;
        double prevReach;
        double dash = 0.5;
        double gap = 0.35;

        @Override
        public void tick() {
            prevY = y;
            prevReach = reach;
        }

        @Override
        public void render(PoseStack ms, SuperRenderTypeBuffer buffer, Vec3 camera, float pt) {
            float w = width();
            if (w <= 0) return;
            VertexConsumer vc = buffer.getBuffer(PonderRenderTypes.outlineSolid());
            Vector4f c = colour();
            double yy = Mth.lerp(pt, prevY, y);
            double r = Mth.lerp(pt, prevReach, reach);
            double mid = 0.5 * (x0 + x1);
            double half = 0.5 * (x1 - x0) * r;
            for (double a = x0; a < x1; a += dash + gap) {
                double s = Math.max(a, mid - half);
                double e = Math.min(Math.min(a + dash, x1), mid + half);
                if (e - s < 0.02) continue;
                line(ms, vc, new Vector3d(s, yy, z), new Vector3d(e, yy, z), w, c);
            }
        }
    }

    static final class Arrow extends Lerped {
        Vec3 from = Vec3.ZERO;
        Vec3 to = Vec3.ZERO;
        Vec3 prevFrom = Vec3.ZERO;
        Vec3 prevTo = Vec3.ZERO;
        Vec3 eye = new Vec3(-0.470, 0.574, -0.671);
        double head = 0.45;

        @Override
        public void tick() {
            prevFrom = from;
            prevTo = to;
        }

        @Override
        public void render(PoseStack ms, SuperRenderTypeBuffer buffer, Vec3 camera, float pt) {
            float w = width();
            if (w <= 0) return;
            Vector3d a = lerp(prevFrom, from, pt);
            Vector3d b = lerp(prevTo, to, pt);
            Vec3 d = new Vec3(b.x - a.x, b.y - a.y, b.z - a.z);
            double len = d.length();
            if (len < 0.05) return;
            Vec3 u = d.scale(1.0 / len);
            double h = Math.min(head, len * 0.6);
            Vec3 wing = u.cross(eye);
            if (wing.lengthSqr() < 1.0e-6) wing = u.cross(new Vec3(0, 1, 0));
            if (wing.lengthSqr() < 1.0e-6) wing = new Vec3(1, 0, 0);
            wing = wing.normalize().scale(h * 0.55);
            VertexConsumer vc = buffer.getBuffer(PonderRenderTypes.outlineSolid());
            Vector4f c = colour();
            Vector3d neck = new Vector3d(b.x - u.x * h * 0.55, b.y - u.y * h * 0.55, b.z - u.z * h * 0.55);
            line(ms, vc, a, neck, w, c);
            Vector3d base = new Vector3d(b.x - u.x * h, b.y - u.y * h, b.z - u.z * h);
            line(ms, vc, b, new Vector3d(base.x + wing.x, base.y + wing.y, base.z + wing.z), w, c);
            line(ms, vc, b, new Vector3d(base.x - wing.x, base.y - wing.y, base.z - wing.z), w, c);
            line(ms, vc, new Vector3d(base.x + wing.x, base.y + wing.y, base.z + wing.z),
                    new Vector3d(base.x - wing.x, base.y - wing.y, base.z - wing.z), w, c);
        }
    }

    static final class Ring extends Lerped {
        Vec3 centre = Vec3.ZERO;
        Vec3 prevCentre = Vec3.ZERO;
        double radius;
        double prevRadius;
        int segments = 48;

        @Override
        public void tick() {
            prevCentre = centre;
            prevRadius = radius;
        }

        @Override
        public void render(PoseStack ms, SuperRenderTypeBuffer buffer, Vec3 camera, float pt) {
            float w = width();
            double r = Mth.lerp(pt, prevRadius, radius);
            if (w <= 0 || r < 0.05) return;
            Vector3d c0 = lerp(prevCentre, centre, pt);
            VertexConsumer vc = buffer.getBuffer(PonderRenderTypes.outlineSolid());
            Vector4f c = colour();
            for (int i = 0; i < segments; i++) {
                double a0 = Math.PI * 2 * i / segments;
                double a1 = Math.PI * 2 * (i + 1) / segments;
                line(ms, vc, new Vector3d(c0.x + r * Math.cos(a0), c0.y, c0.z + r * Math.sin(a0)),
                        new Vector3d(c0.x + r * Math.cos(a1), c0.y, c0.z + r * Math.sin(a1)), w, c);
            }
        }
    }

    static final class Streaks extends Lerped {
        final Vec3[] a;
        final Vec3[] b;
        final Vec3[] pa;
        final Vec3[] pb;

        Streaks(int n) {
            a = new Vec3[n];
            b = new Vec3[n];
            pa = new Vec3[n];
            pb = new Vec3[n];
            for (int i = 0; i < n; i++) a[i] = b[i] = pa[i] = pb[i] = Vec3.ZERO;
        }

        @Override
        public void tick() {
            System.arraycopy(a, 0, pa, 0, a.length);
            System.arraycopy(b, 0, pb, 0, b.length);
        }

        @Override
        public void render(PoseStack ms, SuperRenderTypeBuffer buffer, Vec3 camera, float pt) {
            float w = width();
            if (w <= 0) return;
            VertexConsumer vc = buffer.getBuffer(PonderRenderTypes.outlineSolid());
            Vector4f c = colour();
            for (int i = 0; i < a.length; i++) {
                Vector3d s = lerp(pa[i], a[i], pt);
                Vector3d e = lerp(pb[i], b[i], pt);
                if (s.distanceSquared(e) < 1.0e-4) continue;
                line(ms, vc, s, e, w, c);
            }
        }
    }

    static final class Body extends Outline {
        Vec3 min = Vec3.ZERO;
        Vec3 max = Vec3.ZERO;
        Vec3 prevMin = Vec3.ZERO;
        Vec3 prevMax = Vec3.ZERO;
        int argb;

        @Override
        public void tick() {
            prevMin = min;
            prevMax = max;
        }

        @Override
        public void render(PoseStack ms, SuperRenderTypeBuffer buffer, Vec3 camera, float pt) {
            Vector3d lo = Lerped.lerp(prevMin, min, pt);
            Vector3d hi = Lerped.lerp(prevMax, max, pt);
            if (hi.y - lo.y < 0.01) return;
            float alpha = ((argb >>> 24) / 255f) * params.getLineWidth() / (1 / 32f);
            Vector4f c = new Vector4f(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f,
                    Mth.clamp(alpha, 0f, 1f));

            VertexConsumer vc = buffer.getBuffer(PonderRenderTypes.outlineTranslucent(WHITE, false));
            PoseStack.Pose pose = ms.last();
            Vector3f a = new Vector3f((float) lo.x, (float) lo.y, (float) lo.z);
            Vector3f b = new Vector3f((float) hi.x, (float) hi.y, (float) hi.z);

            quad(pose, vc, c, a.x, a.y, a.z, b.x, a.y, a.z, b.x, a.y, b.z, a.x, a.y, b.z, 0, -1, 0);
            quad(pose, vc, c, a.x, b.y, b.z, b.x, b.y, b.z, b.x, b.y, a.z, a.x, b.y, a.z, 0, 1, 0);
            quad(pose, vc, c, a.x, a.y, a.z, a.x, b.y, a.z, b.x, b.y, a.z, b.x, a.y, a.z, 0, 0, -1);
            quad(pose, vc, c, b.x, a.y, b.z, b.x, b.y, b.z, a.x, b.y, b.z, a.x, a.y, b.z, 0, 0, 1);
            quad(pose, vc, c, a.x, a.y, b.z, a.x, b.y, b.z, a.x, b.y, a.z, a.x, a.y, a.z, -1, 0, 0);
            quad(pose, vc, c, b.x, a.y, a.z, b.x, b.y, a.z, b.x, b.y, b.z, b.x, a.y, b.z, 1, 0, 0);
        }

        private void quad(PoseStack.Pose pose, VertexConsumer vc, Vector4f c, float x0, float y0, float z0, float x1,
                          float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3,
                          float nx, float ny, float nz) {
            bufferQuad(pose, vc, new Vector3f(x0, y0, z0), new Vector3f(x1, y1, z1), new Vector3f(x2, y2, z2),
                    new Vector3f(x3, y3, z3), c, LightTexture.FULL_BRIGHT, new Vector3f(nx, ny, nz));
        }
    }

    public static KitInstruction dashes(Object slot, IntToDoubleFunction y, double x0, double x1, double z, int rgb,
                                        int start, int ticks) {
        return new KitInstruction(start, ticks) {
            private Dashes d;

            @Override
            protected void begin(PonderScene scene) {
                d = new Dashes();
                d.x0 = x0;
                d.x1 = x1;
                d.z = z;
                d.y = d.prevY = y.applyAsDouble(start);
                d.width = 1 / 20f;
                scene.getOutliner().showOutline(slot, d).colored(rgb).lineWidth(1 / 32f);
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                if (d == null) return;
                d.y = y.applyAsDouble(tick);
                d.reach = Ease.OUT_CUBIC.clamped((tick - start + 1) / 16.0);
                if (!last) scene.getOutliner().keep(slot);
            }
        };
    }

    public static KitInstruction arrow(Object slot, IntFunction<Vec3[]> ends, int rgb, float width, int start, int ticks) {
        return new KitInstruction(start, ticks) {
            private Arrow a;

            @Override
            protected void begin(PonderScene scene) {
                a = new Arrow();
                a.width = width;
                Vec3[] e = ends.apply(start);
                a.from = a.prevFrom = e[0];
                a.to = a.prevTo = e[0];
                scene.getOutliner().showOutline(slot, a).colored(rgb).lineWidth(1 / 32f);
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                if (a == null) return;
                Vec3[] e = ends.apply(tick);
                double grow = Ease.OUT_CUBIC.clamped((tick - start + 1) / 10.0);
                a.from = e[0];
                a.to = e[0].add(e[1].subtract(e[0]).scale(grow));
                a.eye = eye(scene);
                if (!last) scene.getOutliner().keep(slot);
            }
        };
    }

    public static KitInstruction ring(Object slot, IntFunction<Vec3> centre, double radius, Ease ease, int rgb,
                                      int start, int ticks) {
        return new KitInstruction(start, ticks) {
            private Ring r;

            @Override
            protected void begin(PonderScene scene) {
                r = new Ring();
                r.centre = r.prevCentre = centre.apply(start);
                scene.getOutliner().showOutline(slot, r).colored(rgb).lineWidth(1 / 32f);
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                if (r == null) return;
                double k = ease.clamped((tick - start + 1) / (double) totalTicks);
                r.centre = centre.apply(tick);
                r.radius = 0.3 + (radius - 0.3) * k;
                r.width = (float) (1 / 14.0 * (1.0 - 0.7 * k));
                if (!last) scene.getOutliner().keep(slot);
            }
        };
    }

    public static KitInstruction airflow(Object slot, IntFunction<Vec3> origin, IntFunction<Vec3> flow, Vec3 across1,
                                         Vec3 across2, double disc, int count, int start, int ticks) {
        return new KitInstruction(start, ticks) {
            private Streaks s;
            private double[] phase;
            private Vec3[] dir;
            private double[] strength;

            @Override
            protected void begin(PonderScene scene) {
                s = new Streaks(count);
                s.width = 1 / 28f;
                phase = new double[count];
                dir = new Vec3[count];
                strength = new double[count];
                for (int i = 0; i < count; i++) {
                    phase[i] = i / (double) count;
                    dir[i] = Vec3.ZERO;
                }
                scene.getOutliner().showOutline(slot, s).colored(AIR).lineWidth(1 / 32f);
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                if (s == null) return;
                Vec3 o = origin.apply(tick);
                Vec3 f = flow.apply(tick);
                double push = f.length();
                for (int i = 0; i < count; i++) {
                    double speed = 0.04 + 0.10 * strength[i];
                    phase[i] += speed;
                    if (phase[i] >= 1.0 || dir[i].lengthSqr() < 1.0e-9) {
                        phase[i] = phase[i] >= 1.0 ? phase[i] - 1.0 : phase[i];
                        dir[i] = push > 0.03 ? f.scale(1.0 / push) : Vec3.ZERO;
                        strength[i] = Math.min(1.0, push);
                    }
                    if (dir[i].lengthSqr() < 1.0e-9) {
                        s.a[i] = s.b[i] = o;
                        continue;
                    }
                    double angle = (i * 2.399963) % (Math.PI * 2);
                    double rr = disc * (0.35 + 0.65 * ((i * 0.618034) % 1.0));
                    Vec3 lat = across1.scale(Math.cos(angle) * rr).add(across2.scale(Math.sin(angle) * rr));
                    double reach = 0.6 + 3.2 * strength[i];
                    double len = 0.35 + 0.9 * strength[i];
                    double head = phase[i] * reach;
                    double tail = Math.max(0.0, head - len);

                    double fadeIn = Math.min(1.0, phase[i] / 0.15);
                    s.a[i] = o.add(lat).add(dir[i].scale(tail));
                    s.b[i] = o.add(lat).add(dir[i].scale(tail + (head - tail) * fadeIn));
                }
                if (!last) scene.getOutliner().keep(slot);
            }
        };
    }

    public static KitInstruction gas(Object slot, Vec3 insideMin, Vec3 insideMax, IntFunction<Vec3> lift,
                                     IntToDoubleFunction fill, int argb, int start, int ticks) {
        return new KitInstruction(start, ticks) {
            private Body b;

            @Override
            protected void begin(PonderScene scene) {
                b = new Body();
                b.argb = argb;
                place(start);
                b.prevMin = b.min;
                b.prevMax = b.max;
                scene.getOutliner().showOutline(slot, b).lineWidth(1 / 32f);
            }

            private void place(int tick) {
                double f = Mth.clamp(fill.applyAsDouble(tick), 0.0, 1.0);

                f *= Ease.OUT_CUBIC.clamped((tick - start + 1) / 10.0);
                Vec3 up = lift.apply(tick);
                double top = insideMax.y;
                double bottom = top - (insideMax.y - insideMin.y) * f;
                b.min = new Vec3(insideMin.x, bottom, insideMin.z).add(up);
                b.max = new Vec3(insideMax.x, top, insideMax.z).add(up);
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                if (b == null) return;
                place(tick);
                if (!last) scene.getOutliner().keep(slot);
            }
        };
    }
}
