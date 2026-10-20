package com.createstats.client.ponder.seismic;

import com.createstats.client.ThroughWalls;
import com.createstats.client.ponder.KitInstruction;
import com.createstats.seismic.VeinVisibility;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createmod.catnip.outliner.Outline;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

public final class VeinView {
    static final float WIDTH = 1 / 14f;
    static final float LINE_ALPHA = 0.8f;
    static final float FLASH_WHITE = 0.6f;
    static final int MAX_LABELS = 24;

    private VeinView() {
    }

    @FunctionalInterface
    public interface Eye {
        Vec3 at(int tick);
    }

    @FunctionalInterface
    public interface Clear {
        int[] at(int tick, Font font, int width, int height);
    }

    public static final class Light {
        public final SeismicPonderSurvey.Survey survey;

        public final int impact;
        public final float[] shown;
        public final float[] shownOld;
        public final float[] label;
        public final float[] labelOld;
        public final double[] distance;

        public double overviewUntil;
        public int tick = Integer.MIN_VALUE;

        public Light(SeismicPonderSurvey.Survey survey, int impact) {
            this.survey = survey;
            this.impact = impact;
            final int n = survey.veins().size();
            shown = new float[n];
            shownOld = new float[n];
            label = new float[n];
            labelOld = new float[n];
            distance = new double[n];
            overviewUntil = impact;
            for (final SeismicPonderSurvey.Vein v : survey.veins()) {
                overviewUntil = Math.max(overviewUntil, impact + v.echo() + VeinVisibility.OVERVIEW_TICKS);
            }
        }

        public int revealAt(int i) {
            return impact + survey.veins().get(i).echo();
        }

        public void look(int tick) {
            overviewUntil = Math.max(overviewUntil, tick + VeinVisibility.OVERVIEW_TICKS);
        }

        public void step(int tick, double ex, double ey, double ez) {
            this.tick = tick;
            final double sinceEnd = tick - overviewUntil;
            for (int i = 0; i < shown.length; i++) {
                final SeismicPonderSurvey.Vein v = survey.veins().get(i);
                distance[i] = SeismicPonderSurvey.distance(v, ex, ey, ez);
                final double target = VeinVisibility.target(tick - revealAt(i), sinceEnd, distance[i], Double.NaN);
                shownOld[i] = shown[i];
                shown[i] = (float) VeinVisibility.ease(shown[i], target);
                if (shown[i] <= VeinVisibility.GONE && shownOld[i] <= VeinVisibility.GONE && target <= 0.0) {
                    shown[i] = 0f;
                }
            }
        }

        public boolean lit(int i) {
            return shown[i] > VeinVisibility.GONE || shownOld[i] > VeinVisibility.GONE;
        }

        public void labels(boolean[] placed) {
            for (int i = 0; i < label.length; i++) {
                labelOld[i] = label[i];
                label[i] = lit(i) ? (float) VeinVisibility.ease(label[i], placed[i] ? shown[i] : 0.0) : 0f;
            }
        }
    }

    static final class Lines extends Outline {
        Light light;
        private final Vector3f origin = new Vector3f();
        private final Vector4f colour = new Vector4f();

        @Override
        public void render(PoseStack ms, SuperRenderTypeBuffer buffer, Vec3 camera, float pt) {
            if (light == null || light.tick == Integer.MIN_VALUE) {
                return;
            }
            final float fade = params.getLineWidth() <= 0 ? 0f : Math.min(1f, params.getLineWidth() / (1 / 32f));
            if (fade <= 0f) {
                return;
            }
            final VertexConsumer xray = buffer.getBuffer(ThroughWalls.type());
            final PoseStack.Pose pose = ms.last();
            final double now = light.tick + pt;
            for (int i = 0; i < light.shown.length; i++) {
                final float a = Mth.lerp(pt, light.shownOld[i], light.shown[i]) * fade;
                if (a <= VeinVisibility.GONE) {
                    continue;
                }
                final SeismicPonderSurvey.Vein v = light.survey.veins().get(i);
                final float white = (float) VeinVisibility.flash(now - light.revealAt(i)) * FLASH_WHITE;
                final int rgb = v.colour();
                colour.set(Mth.lerp(white, ((rgb >> 16) & 0xFF) / 255f, 1f), Mth.lerp(white, ((rgb >> 8) & 0xFF) / 255f, 1f),
                        Mth.lerp(white, (rgb & 0xFF) / 255f, 1f), a * LINE_ALPHA);
                final int[] r = v.runs();
                for (int k = 0; k + 4 < r.length; k += 5) {
                    origin.set(r[k], r[k + 1], r[k + 2]);
                    final Direction along = r[k + 3] == 0 ? Direction.EAST : r[k + 3] == 1 ? Direction.UP : Direction.SOUTH;
                    bufferCuboidLine(pose, xray, origin, along, r[k + 4], WIDTH, colour, LightTexture.FULL_BRIGHT, false);
                }
            }
        }
    }

    static final class Labels extends SeismicOverlay {
        Light light;

        @Override
        protected void draw(PonderScene scene, PonderUI screen, GuiGraphics g, float pt, float a) {
            if (light == null) {
                return;
            }
            final Font font = screen.getFontRenderer();
            final PoseStack ps = g.pose();
            for (int i = 0; i < light.label.length; i++) {
                final float la = Mth.lerp(pt, light.labelOld[i], light.label[i]) * a;
                if (la <= 0.05f) {
                    continue;
                }
                final SeismicPonderSurvey.Vein v = light.survey.veins().get(i);
                final Vec2 at = scene.getTransform().sceneToScreen(new Vec3(v.centre()[0], v.centre()[1], v.centre()[2]), pt);
                ps.pushPose();
                ps.translate(Math.round(at.x), Math.round(at.y), SeismicOverlays.DEPTH - 10);
                plate(g, font, v.label(), v.depth(), v.colour(), la);
                ps.popPose();
            }
        }

        static void plate(GuiGraphics g, Font font, String name, String depth, int colour, float a) {
            final int nw = font.width(name);
            final int dw = Math.round(font.width(depth) * 0.75f);
            final int w = Math.max(nw, dw) + 4;
            g.fill(-w / 2, -10, w - w / 2, 7, fill(0xFF000000, a * 0.45f));
            g.drawString(font, name, -nw / 2, -9, argb(colour, a), false);
            final PoseStack ps = g.pose();
            ps.pushPose();
            ps.translate(0, 0.5f, 0);
            ps.scale(0.75f, 0.75f, 1f);
            g.drawString(font, depth, -font.width(depth) / 2, 1, argb(mix(colour, 0xB8B8B8, 0.55f), a * 0.9f), false);
            ps.popPose();
        }

        static int mix(int a, int b, float t) {
            int out = 0;
            for (int shift = 0; shift <= 16; shift += 8) {
                final int ca = (a >> shift) & 0xFF;
                final int cb = (b >> shift) & 0xFF;
                out |= Math.round(ca + (cb - ca) * t) << shift;
            }
            return out;
        }
    }

    public static KitInstruction show(Object slot, SeismicPonderSurvey.Survey survey, int impact, Eye eye,
                                      IntPredicate looking, Clear clear, int start, int ticks) {
        return new KitInstruction(start, ticks) {
            private Light light;
            private Lines lines;
            private Labels labels;
            private int dwell;

            @Override
            protected void begin(PonderScene scene) {
                light = new Light(survey, impact);
                dwell = 0;
                lines = new Lines();
                lines.light = light;
                scene.getOutliner().showOutline(slot, lines).lineWidth(1 / 32f);
                labels = new Labels();
                labels.light = light;
                labels.setVisible(true);
                scene.addElement(labels);
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                if (light == null) {
                    return;
                }
                dwell = looking.test(tick) ? dwell + 1 : 0;
                if (dwell >= SeismicPonderBeats.DWELL_TICKS) {
                    light.look(tick);
                }
                final Vec3 e = eye.at(tick);
                light.step(tick, e.x, e.y, e.z);
                light.labels(place(scene, light, e, clear, tick));
                final int left = start + totalTicks - tick - 1;
                labels.fadeToward(left < SeismicOverlays.FADE_OUT ? 0f : 1f);
                if (!last) {
                    scene.getOutliner().keep(slot);
                } else {
                    labels.setVisible(false);
                }
            }
        };
    }

    static boolean[] place(PonderScene scene, Light light, Vec3 eye, Clear clear, int tick) {
        final int n = light.shown.length;
        final boolean[] placed = new boolean[n];
        final List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (light.lit(i)) {
                order.add(i);
            }
        }
        order.sort((a, b) -> Double.compare(light.distance[a], light.distance[b]));
        final java.util.Set<String> named = new java.util.HashSet<>();
        final List<Integer> leads = new ArrayList<>();
        for (final int i : order) {
            if (light.shown[i] > 0.05f && named.add(light.survey.veins().get(i).kind())) {
                leads.add(i);
            }
        }
        order.removeAll(leads);
        order.addAll(0, leads);
        final List<float[]> taken = new ArrayList<>();
        final net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        final net.minecraft.client.gui.Font font = mc.font;
        final int[] free = clear == null ? null
                : clear.at(tick, font, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        for (final int i : order) {
            if (taken.size() >= MAX_LABELS || light.shown[i] <= 0.05f) {
                continue;
            }
            final SeismicPonderSurvey.Vein v = light.survey.veins().get(i);
            final Vec2 at = scene.getTransform().sceneToScreen(new Vec3(v.centre()[0], v.centre()[1], v.centre()[2]), 1f);
            final float hw = (Math.max(font.width(v.label()), font.width(v.depth()) * 0.75f) + 4) / 2f + 1f;
            final float top = at.y - 10f - 1f;
            final float bottom = at.y + 7f + 1f;
            boolean ok = clearOf(free, at.x, hw, top, bottom);
            for (final float[] t : taken) {
                if (Math.abs(at.x - t[0]) < hw + t[1] && top < t[3] && bottom > t[2]) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                taken.add(new float[] {at.x, hw, top, bottom});
                placed[i] = true;
            }
        }
        return placed;
    }

    static boolean clearOf(int[] free, float x, float hw, float top, float bottom) {
        return free == null || x + hw <= free[0] || x - hw >= free[2] || bottom <= free[1] || top >= free[3];
    }

    static int argb(int rgb, float a) {
        return SeismicOverlay.argb(rgb, a);
    }

    static int fill(int argb, float a) {
        return SeismicOverlay.fill(argb, a);
    }

    public static Eye fixed(Vec3 p) {
        return t -> p;
    }

    public static IntPredicate never() {
        return t -> false;
    }

    public static Clear nothingKept() {
        return (tick, font, width, height) -> null;
    }
}
