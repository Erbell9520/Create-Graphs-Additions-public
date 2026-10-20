package com.createstats.client.ponder.seismic;

import com.createstats.client.ponder.KitInstruction;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createmod.catnip.outliner.Outline;
import net.createmod.catnip.render.PonderRenderTypes;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.createmod.ponder.api.element.ParrotPose;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector4f;

public final class Walker {
    public static final double PACE = 4.317 / 20.0;

    public static final double EYE = 0.5;

    private Walker() {
    }

    public static final class Walk {
        final int[] ticks;
        final double[][] points;
        final double y;

        Vec3 look;
        int lookFrom = Integer.MAX_VALUE;
        int lookTo = Integer.MAX_VALUE;

        public Walk(double y, int[] ticks, double[][] points) {
            this.y = y;
            this.ticks = ticks;
            this.points = points;
        }

        public Walk look(Vec3 at, int from, int to) {
            look = at;
            lookFrom = from;
            lookTo = to;
            return this;
        }

        public boolean looking(int tick) {
            return look != null && tick >= lookFrom && tick < lookTo;
        }

        public Vec3 at(double t) {
            if (t <= ticks[0]) {
                return new Vec3(points[0][0], y, points[0][1]);
            }
            for (int i = 1; i < ticks.length; i++) {
                if (t <= ticks[i]) {
                    final double k = (t - ticks[i - 1]) / Math.max(1e-9, ticks[i] - ticks[i - 1]);
                    return new Vec3(Mth.lerp(k, points[i - 1][0], points[i][0]), y, Mth.lerp(k, points[i - 1][1], points[i][1]));
                }
            }
            final double[] p = points[points.length - 1];
            return new Vec3(p[0], y, p[1]);
        }

        public Vec3 eye(double t) {
            return at(t).add(0, EYE, 0);
        }

        double heading(int tick) {
            final Vec3 here = at(tick);
            if (looking(tick)) {
                return yawTo(here, look);
            }
            final Vec3 next = at(tick + 1);
            if (next.distanceToSqr(here) > 1e-6) {
                return yawTo(here, next);
            }
            return Double.NaN;
        }

        static double yawTo(Vec3 from, Vec3 to) {
            return Mth.wrapDegrees(-(Math.toDegrees(Math.atan2(to.z - from.z, to.x - from.x))) + 90.0);
        }

        public static int ticksFor(double blocks) {
            return (int) Math.round(blocks / PACE);
        }
    }

    public static final class Pose extends ParrotPose {
        final Walk walk;
        final Vec3 origin;
        final double startYaw;
        int ticks;

        Pose(Walk walk, Vec3 origin, double startYaw) {
            this.walk = walk;
            this.origin = origin;
            this.startYaw = startYaw;
        }

        @Override
        public Parrot create(PonderLevel world) {
            final Parrot p = new Parrot(EntityType.PARROT, world);

            p.setVariant(Parrot.Variant.RED_BLUE);
            return p;
        }

        @Override
        public void tick(PonderScene scene, Parrot entity, Vec3 location) {
            final int t = scene.getCurrentTime();
            final Vec3 at = walk.at(t).subtract(origin);
            final double moved = Math.sqrt(Math.pow(at.x - entity.getX(), 2) + Math.pow(at.z - entity.getZ(), 2));
            entity.setPos(at.x, at.y, at.z);
            if (ticks < 2) {
                entity.xo = at.x;
                entity.yo = at.y;
                entity.zo = at.z;
                entity.setYRot((float) startYaw);
                entity.yRotO = (float) startYaw;
            }
            ticks++;
            final double heading = walk.heading(t);
            if (!Double.isNaN(heading)) {
                entity.setYRot((float) Mth.rotLerp(0.4f, entity.getYRot(), (float) heading));
            }
            entity.walkAnimation.update((float) Math.min(1.0, moved * 4.0), 0.4f);
        }
    }

    public static Pose pose(Walk walk, Vec3 origin, double startYaw) {
        return new Pose(walk, origin, startYaw);
    }

    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    public record Ring(double radius, int rgb, float alpha, String text) {
    }

    static final class Rings extends Outline {
        Walk walk;
        java.util.List<Ring> rings = java.util.List.of();
        int tick = Integer.MIN_VALUE;
        double top;
        double x0;
        double x1;
        double z0;
        double z1;
        float level;
        float levelOld;
        private final Vector4f colour = new Vector4f();

        @Override
        public void render(PoseStack ms, SuperRenderTypeBuffer buffer, Vec3 camera, float pt) {
            final float a = Mth.lerp(pt, levelOld, level);
            if (tick == Integer.MIN_VALUE || a <= 0.02f) {
                return;
            }
            final VertexConsumer vc = buffer.getBuffer(PonderRenderTypes.outlineTranslucent(WHITE, false));
            final Vec3 c = walk.at(tick - 1 + pt);
            for (final Ring ring : rings) {
                ring(ms, vc, c, ring.radius(), ring.rgb(), a * ring.alpha());
            }
        }

        private void ring(PoseStack ms, VertexConsumer vc, Vec3 c, double r, int rgb, float a) {
            colour.set(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, a * 0.9f);
            final int n = 720;
            final double dash = 0.45;
            final double gap = 0.3;
            for (int i = 0; i < n; i++) {
                final double a0 = 2 * Math.PI * i / n;
                final double a1 = 2 * Math.PI * (i + 1) / n;

                final double s = r * a0;
                if ((s % (dash + gap)) > dash) {
                    continue;
                }
                final double ux = c.x + r * Math.cos(a0);
                final double uz = c.z + r * Math.sin(a0);
                final double vx = c.x + r * Math.cos(a1);
                final double vz = c.z + r * Math.sin(a1);
                if (!inside(ux, uz) || !inside(vx, vz)) {
                    continue;
                }
                bufferCuboidLine(ms, vc, Vec3.ZERO, new Vector3d(ux, top + 0.03, uz), new Vector3d(vx, top + 0.03, vz), 1 / 16f,
                        colour, LightTexture.FULL_BRIGHT, false);
            }
        }

        private boolean inside(double x, double z) {
            return x >= x0 && x <= x1 && z >= z0 && z <= z1;
        }
    }

    static final int NEAR_RGB = 0xE8F2FF;
    static final int FAR_RGB = 0x9AA6B4;
    static final int WITNESS_RGB = 0xF2D99A;

    public static double[][] crossings(Vec3 c, double r, double edge, double x0, double x1) {
        final double dz = edge - c.z;
        if (Math.abs(dz) >= r) {
            return new double[0][];
        }
        final double half = Math.sqrt(r * r - dz * dz);
        final double[][] out = new double[2][];
        for (int i = 0; i < 2; i++) {
            final double x = c.x + (i == 0 ? -half : half);
            final double u = Mth.clamp(Math.min(x - x0, x1 - x), 0.0, 1.0);
            out[i] = new double[] {x, u * u * (3.0 - 2.0 * u)};
        }
        return out;
    }

    static final class RingTags extends SeismicOverlay {
        Walk walk;
        java.util.List<Ring> rings = java.util.List.of();
        int tick = Integer.MIN_VALUE;
        double top;
        double edge;
        double x0;
        double x1;

        @Override
        protected void draw(PonderScene scene, PonderUI screen, GuiGraphics g, float pt, float a) {
            if (walk == null || tick == Integer.MIN_VALUE) {
                return;
            }
            final Vec3 c = walk.at(tick - 1 + pt);
            for (final Ring ring : rings) {
                tags(scene, screen, g, pt, a, c, ring.radius(), ring.text(), ring.rgb());
            }
        }

        private void tags(PonderScene scene, PonderUI screen, GuiGraphics g, float pt, float a, Vec3 c, double r, String text,
                          int rgb) {
            final Font font = screen.getFontRenderer();
            for (final double[] x : crossings(c, r, edge, x0, x1)) {
                final float k = (float) x[1] * a;
                if (k <= 0.02f) {
                    continue;
                }
                final Vec2 at = scene.getTransform().sceneToScreen(new Vec3(x[0], top, edge), pt);
                final PoseStack ps = g.pose();
                ps.pushPose();
                ps.translate(Math.round(at.x), Math.round(at.y) - 8, SeismicOverlays.DEPTH);
                final int w = font.width(text) + 4;
                g.fill(-w / 2, -1, w - w / 2, 9, fill(SeismicOverlays.VEIL, k));
                g.drawString(font, text, -w / 2 + 2, 0, argb(rgb, k), false);
                ps.popPose();
            }
        }
    }

    public static KitInstruction rings(Object slot, Walk walk, double top, double x0, double x1, double z0, double z1,
                                       java.util.List<Ring> rings, int start, int ticks) {
        return new KitInstruction(start, ticks) {
            private Rings r;
            private RingTags tags;

            @Override
            protected void begin(PonderScene scene) {
                r = new Rings();
                r.walk = walk;
                r.rings = rings;
                r.top = top;
                r.x0 = x0;
                r.x1 = x1;
                r.z0 = z0;
                r.z1 = z1;
                scene.getOutliner().showOutline(slot, r).lineWidth(1 / 32f);
                tags = new RingTags();
                tags.walk = walk;
                tags.rings = rings;
                tags.top = top;
                tags.edge = z1;
                tags.x0 = x0;
                tags.x1 = x1;
                tags.setVisible(true);
                scene.addElement(tags);
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                if (r == null) {
                    return;
                }
                r.tick = tick;
                r.levelOld = r.level;
                final int left = start + totalTicks - tick - 1;
                final float target = left < 10 ? 0f : 1f;
                r.level = r.level < target ? Math.min(target, r.level + 0.1f) : Math.max(target, r.level - 0.1f);
                tags.tick = tick;
                tags.fadeToward(target);
                if (!last) {
                    scene.getOutliner().keep(slot);
                } else {
                    tags.setVisible(false);
                }
            }
        };
    }

    static final class Sight extends Outline {
        Walk walk;
        int tick = Integer.MIN_VALUE;
        float level;
        float levelOld;
        private final Vector4f colour = new Vector4f();

        @Override
        public void render(PoseStack ms, SuperRenderTypeBuffer buffer, Vec3 camera, float pt) {
            final float a = Mth.lerp(pt, levelOld, level);
            if (walk == null || walk.look == null || a <= 0.02f) {
                return;
            }
            final VertexConsumer vc = buffer.getBuffer(PonderRenderTypes.outlineTranslucent(WHITE, false));
            colour.set(1f, 0.96f, 0.82f, a * 0.85f);
            final Vec3 from = walk.eye(tick - 1 + pt).add(0, 0.05, 0);
            final Vec3 to = walk.look;
            final Vec3 d = to.subtract(from);
            final double len = d.length();
            final Vec3 dir = d.scale(1.0 / Math.max(1e-6, len));

            final double step = 0.6;
            final double phase = ((tick + pt) * 0.08) % step;
            for (double s = phase + 0.4; s + 0.2 < len - 0.3; s += step) {
                final Vec3 a0 = from.add(dir.scale(s));
                final Vec3 a1 = from.add(dir.scale(s + 0.2));
                bufferCuboidLine(ms, vc, Vec3.ZERO, new Vector3d(a0.x, a0.y, a0.z), new Vector3d(a1.x, a1.y, a1.z), 1 / 14f,
                        colour, LightTexture.FULL_BRIGHT, false);
            }
        }
    }

    public static KitInstruction sight(Object slot, Walk walk, int start, int ticks) {
        return new KitInstruction(start, ticks) {
            private Sight s;

            @Override
            protected void begin(PonderScene scene) {
                s = new Sight();
                s.walk = walk;
                scene.getOutliner().showOutline(slot, s).lineWidth(1 / 32f);
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                if (s == null) {
                    return;
                }
                s.tick = tick;
                s.levelOld = s.level;
                final float target = walk.looking(tick) ? 1f : 0f;
                s.level = s.level < target ? Math.min(target, s.level + 0.125f) : Math.max(target, s.level - 0.125f);
                if (!last) {
                    scene.getOutliner().keep(slot);
                }
            }
        };
    }
}
