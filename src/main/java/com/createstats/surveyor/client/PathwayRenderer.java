package com.createstats.surveyor.client;

import com.createstats.client.AnimationClock;
import com.createstats.surveyor.PathwayChecks;
import com.createstats.surveyor.RouteTrack;
import com.createstats.surveyor.core.PathFairing;
import com.createstats.surveyor.core.PathKind;
import com.createstats.surveyor.core.PathNode;
import com.createstats.surveyor.core.PathSpline;
import com.createstats.surveyor.core.Pathway;
import com.createstats.surveyor.core.RoadPaint;
import com.createstats.surveyor.core.RouteRunner;
import com.createstats.surveyor.core.SegmentCheck;
import com.createstats.surveyor.core.SpeedPlan;
import com.createstats.surveyor.core.Stop;
import com.createstats.surveyor.core.StopCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

// painted like a road
@EventBusSubscriber(modid = "create_stats", value = Dist.CLIENT)
public final class PathwayRenderer {
    public static final double RANGE = 160.0;
    public static final double CHEVRON_PERIOD = 0.6;
    static final float RIBBON_HALF = 0.15f;
    static final float SELECTED_HALF = 0.21f;
    static final float LIFT = 0.035f;
    static final int AMBER = 0xFFFFAA00;
    static final int RED = 0xFFFF3B30;
    static final int GREY = 0xFF9A9A9A;
    static final int CHALK = 0xFF5CE1E6;
    static final int BRASS = 0xFFE4B763;

    static final int FLOOR_STAFF = 8;
    static final int FLOOR_GOGGLES = 4;

    static final double DOTS_NEAR = 6.0;

    static final double SIGN_RANGE = 48.0;

    enum Vis {
        NONE, DIM, FULL
    }

    private PathwayRenderer() {
    }

    static Vis visibility(@Nullable Player p) {
        if (p == null) return Vis.NONE;
        if (StaffInput.holding(p)) return Vis.FULL;
        if ((StaffInput.goggles(p) && SurveyorClientOptions.gogglesShow()) || SurveyorClientOptions.alwaysShow()) return Vis.DIM;
        return Vis.NONE;
    }

    public static int colour(int dye) {
        return 0xFF000000 | (DyeColor.byId(dye & 15).getTextureDiffuseColor() & 0xFFFFFF);
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        Player player = mc.player;
        if (level == null || player == null) return;
        Vis vis = visibility(player);
        boolean sight = RouteSight.wantsPathways(player);
        if (vis == Vis.NONE && !sight && !GhostRun.active()) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double time = AnimationClock.time(level, partial) / 20.0;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = ps.last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        long tick = level.getGameTime();

        UUID selected = StaffInput.selected();
        StaffPicking.Pick hover = StaffInput.hover();
        LIGHTS.clear();
        int floor = vis == Vis.FULL ? FLOOR_STAFF : FLOOR_GOGGLES;
        if (vis != Vis.NONE) {
            VertexConsumer paint = buffers.getBuffer(SurveyorRenderTypes.PAINT);
            for (PathwayClientCache.Entry e : PathwayClientCache.all()) {
                boolean sel = e.pathway.id().equals(selected);
                PathwayChecks.Result checks = e.checks(level, cam.x, cam.z, tick);
                drawRoad(paint, m, level, floor, e, checks, cam, time, vis, sel, sel && vis == Vis.FULL);
            }
            buffers.endBatch(SurveyorRenderTypes.PAINT);
        }
        VertexConsumer ribbon = buffers.getBuffer(SurveyorRenderTypes.RIBBON);
        if (sight) RouteSight.drawRibbon(ribbon, m, cam, time);
        StaffInput.Preview preview = StaffInput.preview();
        if (vis == Vis.FULL) drawStroke(ribbon, m, preview.stroke());
        buffers.endBatch(SurveyorRenderTypes.RIBBON);

        if (vis != Vis.NONE) {
            VertexConsumer things = buffers.getBuffer(SurveyorRenderTypes.THINGS);
            Vec3 aim = hover.kind() == StaffPicking.Kind.NONE ? null : hover.point();
            for (PathwayClientCache.Entry e : PathwayClientCache.all()) {
                drawNodes(things, m, level, floor, e, cam, vis, e.pathway.id().equals(selected), hover, preview, aim);
            }
            buffers.endBatch(SurveyorRenderTypes.THINGS);
        }
        VertexConsumer parts = buffers.getBuffer(SurveyorRenderTypes.PARTS);
        if (vis != Vis.NONE) {
            for (PathwayClientCache.Entry e : PathwayClientCache.all()) {
                drawHandles(parts, m, e, cam, vis, hover);
            }
        }
        if (vis == Vis.FULL) {
            drawPreviewLine(parts, m, preview, cam);
            ChalkLine.draw(parts, m, camera, event.getProjectionMatrix());
            drawThread(parts, m, player, partial);
            GhostRun.draw(parts, m, level, time);
            drawMove(parts, m);
            drawBrush(parts, m, mc);
            if (StaffInput.mode() == StaffMode.EDIT) drawComb(parts, m, cam);
        }
        buffers.endBatch(SurveyorRenderTypes.PARTS);

        if (vis != Vis.NONE) drawSigns(mc, ps, buffers, level, floor, cam);
        if (vis == Vis.FULL) {
            drawTag(mc, ps, buffers, camera, hover);
            if (StaffInput.mode() == StaffMode.WATCH || StaffInput.pinned() != null) drawCards(mc, ps, buffers, camera, partial);
        }
        buffers.endBatch();
        ps.popPose();
    }

    private static void drawRoad(VertexConsumer vc, Matrix4f m, Level level, int floor, PathwayClientCache.Entry e,
                                 @Nullable PathwayChecks.Result checks, Vec3 cam, double time, Vis vis, boolean selected,
                                 boolean warnings) {
        PathSpline sp = e.spline();
        int n = sp.sampleCount();
        if (n < 2) return;
        double[] height = new double[n];
        int[] levels = new int[n];
        for (int i = 0; i < n; i++) {
            height[i] = height(sp, checks, i);
            levels[i] = checks == null || i >= checks.levels().length ? 0 : checks.levels()[i].ordinal();
        }
        float[] bay = bayOf(e);
        List<RoadPaint.StopMark> stops = new ArrayList<>();
        List<RoadPaint.LimitMark> limits = new ArrayList<>();
        Pathway p = e.pathway;
        for (int k = 0; k < p.nodes().size() && k < sp.nodeCount(); k++) {
            PathNode node = p.nodes().get(k);
            if (node.isStop()) stops.add(new RoadPaint.StopMark(sp.nodeS(k), bay[0], bay[1]));
            if (node.speedLimit() > 0.0) limits.add(new RoadPaint.LimitMark(sp.nodeS(k), node.speedLimit()));
        }
        int alpha = vis == Vis.DIM ? 0x70 : (selected ? 0xE0 : 0xA8);
        RoadPaint.Scene sc = new RoadPaint.Scene(sp, height, levels, e.flow(), time, colour(p.colour()) & 0xFFFFFF, alpha, bay[2],
                warnings, cam.x, cam.z, RANGE, stops, limits, CHEVRON_PERIOD);
        RoadPaint.paint(sc, (x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, argb) -> {
            int light = light(level, (x0 + x2) * 0.5, (y0 + y2) * 0.5, (z0 + z2) * 0.5, floor);
            vc.addVertex(m, x0, y0, z0).setColor(argb).setLight(light);
            vc.addVertex(m, x1, y1, z1).setColor(argb).setLight(light);
            vc.addVertex(m, x2, y2, z2).setColor(argb).setLight(light);
            vc.addVertex(m, x3, y3, z3).setColor(argb).setLight(light);
        });
    }

    private static double height(PathSpline sp, @Nullable PathwayChecks.Result checks, int i) {
        if (checks != null && i < checks.ground().length && Double.isFinite(checks.ground()[i])) return checks.ground()[i] + LIFT;
        return sp.sampleY(i) + LIFT;
    }

    static float[] bayOf(PathwayClientCache.Entry e) {
        float front = 3.0f;
        float rear = 2.0f;
        float half = 1.5f;
        boolean any = false;
        for (PathwayClientCache.Vehicle v : PathwayClientCache.vehiclesOn(e.pathway.id())) {
            if (v.now == null) continue;
            front = any ? Math.max(front, v.now.front()) : v.now.front();
            rear = any ? Math.max(rear, v.now.rear()) : v.now.rear();
            half = any ? Math.max(half, v.now.halfWidth()) : v.now.halfWidth();
            any = true;
        }
        return new float[]{Math.max(0.5f, Math.min(12.0f, front)), Math.max(0.5f, Math.min(24.0f, rear)),
                Math.max(0.75f, Math.min(4.0f, half))};
    }

    static int light(Level level, double x, double y, double z, int floor) {
        long key = BlockPos.asLong((int) Math.floor(x), (int) Math.floor(y + 0.1), (int) Math.floor(z)) * 31L + floor;
        Integer hit = LIGHTS.get(key);
        if (hit != null) return hit;
        int packed = LevelRenderer.getLightColor(level, BlockPos.containing(x, y + 0.1, z));
        int lit = LightTexture.pack(Math.max(floor, LightTexture.block(packed)), LightTexture.sky(packed));
        LIGHTS.put(key, lit);
        return lit;
    }

    private static final java.util.HashMap<Long, Integer> LIGHTS = new java.util.HashMap<>();

    private static void quadLine(VertexConsumer vc, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz,
                                 float tx, float tz, float w, int argb) {
        float dx = bx - ax;
        float dz = bz - az;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-4f) return;
        float px = -dz / len * w * 0.5f;
        float pz = dx / len * w * 0.5f;
        vc.addVertex(m, ax - px, ay, az - pz).setColor(argb);
        vc.addVertex(m, ax + px, ay, az + pz).setColor(argb);
        vc.addVertex(m, bx + px, by, bz + pz).setColor(argb);
        vc.addVertex(m, bx - px, by, bz - pz).setColor(argb);
    }

    private static void drawStroke(VertexConsumer vc, Matrix4f m, List<double[]> stroke) {
        if (stroke.size() < 2) return;
        int argb = 0xE0000000 | (CHALK & 0xFFFFFF);
        for (int i = 1; i < stroke.size(); i++) {
            double[] a = stroke.get(i - 1);
            double[] b = stroke.get(i);
            quadLine(vc, m, (float) a[0], (float) a[1] + 0.06f, (float) a[2], (float) b[0], (float) b[1] + 0.06f, (float) b[2], 0, 0, 0.18f, argb);
        }
    }

    private static void drawNodes(VertexConsumer vc, Matrix4f m, Level level, int floor, PathwayClientCache.Entry e, Vec3 cam,
                                  Vis vis, boolean selected, StaffPicking.Pick hover, StaffInput.Preview preview, @Nullable Vec3 aim) {
        Pathway p = e.pathway;
        int colour = colour(p.colour());
        double range2 = RANGE * RANGE;
        boolean full = vis == Vis.FULL;
        boolean allDots = full && selected && StaffInput.mode() == StaffMode.EDIT;
        for (int k = 0; k < p.nodes().size(); k++) {
            PathNode n = p.nodes().get(k);
            if ((n.x() - cam.x) * (n.x() - cam.x) + (n.z() - cam.z) * (n.z() - cam.z) > range2) continue;
            boolean dragged = p.id().equals(preview.pathway()) && n.id() == preview.node() && preview.dragTo() != null && !preview.insert();
            boolean hot = hover.kind() == StaffPicking.Kind.NODE && p.id().equals(hover.pathway()) && hover.nodeId() == n.id();
            double x = dragged ? preview.dragTo().x : n.x();
            double y = dragged ? preview.dragTo().y : n.y();
            double z = dragged ? preview.dragTo().z : n.z();
            float lift = hot || dragged ? 0.18f : 0.0f;
            LIT.on(vc, light(level, x, y + 0.5, z, floor));
            if (n.isStop()) {
                if (!n.name().isEmpty() && !dragged && k < e.spline().nodeCount()) {
                    signpost(LIT, m, e, e.spline().nodeS(k), colour, hot);

                    post(LIT, m, x, y + lift, z, colour, full ? 0xFF : 0xB0, hot || dragged, 1.1f);
                } else {
                    post(LIT, m, x, y + lift, z, colour, full ? 0xFF : 0xB0, hot || dragged, 1.8f);
                }
                continue;
            }
            if (!full) continue;
            if (PathFairing.anchor(p, k) || hot || dragged) {
                int shade = selected ? colour : darken(colour, 0.35f);
                stake(LIT, m, x, y + lift, z, shade, k == 0, hot || dragged);
            } else if (allDots || (aim != null && Math.hypot(x - aim.x, z - aim.z) <= DOTS_NEAR)) {
                ring(LIT, m, x, y + LIFT + 0.02, z, 0.0f, 0.16f, 1.0, 0xFF1A1A1A);
                ring(LIT, m, x, y + LIFT + 0.025, z, 0.0f, 0.11f, 1.0, selected ? 0xFFF2F2EE : 0xFFBDBDB8);
            }
        }
    }

    private static void drawHandles(VertexConsumer vc, Matrix4f m, PathwayClientCache.Entry e, Vec3 cam, Vis vis, StaffPicking.Pick hover) {
        Pathway p = e.pathway;
        if (vis != Vis.FULL || StaffInput.mode() != StaffMode.EDIT || p.nodes().size() < 2) return;
        PathSpline sp = e.spline();
        int segments = p.closed() ? p.nodes().size() : p.nodes().size() - 1;
        for (int k = 0; k < segments; k++) {
            double a = sp.nodeS(k);
            double b = k + 1 < sp.nodeCount() ? sp.nodeS(k + 1) : sp.length();
            if (p.closed() && k == segments - 1) b = sp.length();
            double mid = (a + b) * 0.5;
            double hx = sp.x(mid);
            double hz = sp.z(mid);
            if ((hx - cam.x) * (hx - cam.x) + (hz - cam.z) * (hz - cam.z) > 64.0 * 64.0) continue;
            boolean hot = hover.kind() == StaffPicking.Kind.HANDLE && p.id().equals(hover.pathway()) && hover.nodeId() == p.nodes().get(k).id();
            diamond(vc, m, hx, sp.y(mid) + 0.35, hz, hot ? 0.26f : 0.17f, hot ? 0xFFFFFFFF : (0xD0000000 | (BRASS & 0xFFFFFF)));
        }
    }

    static final class Lit implements VertexConsumer {
        private VertexConsumer inner;
        private int light;

        Lit on(VertexConsumer vc, int packedLight) {
            inner = vc;
            light = packedLight;
            return this;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            inner.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            inner.setColor(r, g, b, a);
            inner.setUv2(light & 0xFFFF, (light >>> 16) & 0xFFFF);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            inner.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            inner.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            inner.setNormal(x, y, z);
            return this;
        }
    }

    private static final Lit LIT = new Lit();

    static final float SIGN_W = 1.8f;
    static final float SIGN_H = 0.8f;
    static final float SIGN_T = 0.08f;
    static final float SIGN_TOP = 2.75f;

    static double[] signAt(PathwayClientCache.Entry e, double s) {
        PathSpline sp = e.spline();
        double[] t = sp.tangent(s);
        float half = bayOf(e)[2];
        double off = half + 1.2;
        double x = sp.x(s) - t[1] * off;
        double z = sp.z(s) + t[0] * off;
        double y = sp.y(s);
        return new double[]{x, z, y + SIGN_TOP - SIGN_H / 2, t[0], t[1], y};
    }

    private static void signpost(VertexConsumer vc, Matrix4f m, PathwayClientCache.Entry e, double s, int colour, boolean hot) {
        double[] at = signAt(e, s);
        double x = at[0];
        double z = at[1];
        double foot = at[5];
        int wood = hot ? 0xFFD8C090 : 0xFF6E5436;
        box(vc, m, x - 0.07, foot, z - 0.07, x + 0.07, foot + SIGN_TOP, z + 0.07, wood, darken(wood, 0.15f));

        double tx = at[3];
        double tz = at[4];
        double rx = -tz;
        double rz = tx;
        double cy = at[2];
        obox(vc, m, x, cy, z, tx, tz, rx, rz, SIGN_W + 0.12f, SIGN_H + 0.12f, SIGN_T, colour);
        obox(vc, m, x, cy, z, tx, tz, rx, rz, SIGN_W, SIGN_H, SIGN_T + 0.02f, 0xFF22262B);
    }

    private static void obox(VertexConsumer vc, Matrix4f m, double x, double cy, double z, double rx, double rz, double tx, double tz,
                             float w, float h, float t, int colour) {
        double hw = w / 2.0;
        double hh = h / 2.0;
        double ht = t / 2.0;
        float[][] c = new float[8][3];
        int i = 0;
        for (int a = -1; a <= 1; a += 2) {
            for (int b = -1; b <= 1; b += 2) {
                for (int d = -1; d <= 1; d += 2) {
                    c[i][0] = (float) (x + rx * hw * a + tx * ht * d);
                    c[i][1] = (float) (cy + hh * b);
                    c[i][2] = (float) (z + rz * hw * a + tz * ht * d);
                    i++;
                }
            }
        }
        int side = darken(colour, 0.25f);

        quad(vc, m, c[0], c[4], c[6], c[2], colour);
        quad(vc, m, c[1], c[3], c[7], c[5], colour);
        quad(vc, m, c[2], c[6], c[7], c[3], side);
        quad(vc, m, c[0], c[1], c[5], c[4], darken(colour, 0.4f));
        quad(vc, m, c[0], c[2], c[3], c[1], side);
        quad(vc, m, c[4], c[5], c[7], c[6], side);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float[] a, float[] b, float[] c, float[] d, int argb) {
        vc.addVertex(m, a[0], a[1], a[2]).setColor(argb);
        vc.addVertex(m, b[0], b[1], b[2]).setColor(argb);
        vc.addVertex(m, c[0], c[1], c[2]).setColor(argb);
        vc.addVertex(m, d[0], d[1], d[2]).setColor(argb);
    }

    private static void stake(VertexConsumer vc, Matrix4f m, double x, double y, double z, int colour, boolean first, boolean hot) {
        float h = 1.05f;
        int wood = hot ? 0xFFF0D9A8 : 0xFFB08A58;
        box(vc, m, x - 0.045, y, z - 0.045, x + 0.045, y + h, z + 0.045, wood, 0xFF7A5A34);
        float fw = first ? 0.42f : 0.3f;
        float fh = first ? 0.26f : 0.18f;
        int flag = hot ? lighten(colour, 0.4f) : colour;
        box(vc, m, x + 0.045, y + h - fh, z - 0.012, x + 0.045 + fw, y + h, z + 0.012, flag, darken(flag, 0.3f));
        if (first) box(vc, m, x + 0.045, y + h - fh * 0.62, z - 0.014, x + 0.045 + fw, y + h - fh * 0.38, z + 0.014, 0xFFFFFFFF, 0xFFDDDDDD);

        box(vc, m, x - 0.06, y - 0.02, z - 0.06, x + 0.06, y + 0.12, z + 0.06, 0xFF6B6B6B, 0xFF4A4A4A);
    }

    private static void post(VertexConsumer vc, Matrix4f m, double x, double y, double z, int colour, int alpha, boolean hot, float h) {
        int iron = hot ? 0xFFE8E8E8 : 0xFF8C8C8C;
        box(vc, m, x - 0.06, y, z - 0.06, x + 0.06, y + h, z + 0.06, iron, 0xFF5A5A5A);
        box(vc, m, x - 0.3, y + h - 0.05, z - 0.3, x + 0.3, y + h + 0.02, z + 0.3, colour, darken(colour, 0.3f));
        ring(vc, m, x, y + 0.03, z, 0.62f, 0.78f, 1.0, (alpha << 24) | (colour & 0xFFFFFF));
    }

    static void ring(VertexConsumer vc, Matrix4f m, double x, double y, double z, float r0, float r1, double fill, int argb) {
        int segs = 24;
        int drawn = (int) Math.round(segs * Math.max(0.0, Math.min(1.0, fill)));
        for (int s = 0; s < drawn; s++) {
            double a0 = 2 * Math.PI * s / segs;
            double a1 = 2 * Math.PI * (s + 1) / segs;
            float sx0 = (float) Math.sin(a0);
            float sz0 = (float) -Math.cos(a0);
            float sx1 = (float) Math.sin(a1);
            float sz1 = (float) -Math.cos(a1);
            vc.addVertex(m, (float) x + sx0 * r0, (float) y, (float) z + sz0 * r0).setColor(argb);
            vc.addVertex(m, (float) x + sx0 * r1, (float) y, (float) z + sz0 * r1).setColor(argb);
            vc.addVertex(m, (float) x + sx1 * r1, (float) y, (float) z + sz1 * r1).setColor(argb);
            vc.addVertex(m, (float) x + sx1 * r0, (float) y, (float) z + sz1 * r0).setColor(argb);
        }
    }

    private static void diamond(VertexConsumer vc, Matrix4f m, double x, double y, double z, float r, int argb) {
        float cx = (float) x;
        float cy = (float) y;
        float cz = (float) z;
        int dark = darken(argb, 0.25f);

        float[][] ring = {{r, 0}, {0, r}, {-r, 0}, {0, -r}};
        for (int i = 0; i < 4; i++) {
            float[] a = ring[i];
            float[] b = ring[(i + 1) % 4];
            vc.addVertex(m, cx, cy + r, cz).setColor(argb);
            vc.addVertex(m, cx + a[0], cy, cz + a[1]).setColor(argb);
            vc.addVertex(m, cx + b[0], cy, cz + b[1]).setColor(argb);
            vc.addVertex(m, cx, cy + r, cz).setColor(argb);
            vc.addVertex(m, cx, cy - r, cz).setColor(dark);
            vc.addVertex(m, cx + b[0], cy, cz + b[1]).setColor(dark);
            vc.addVertex(m, cx + a[0], cy, cz + a[1]).setColor(dark);
            vc.addVertex(m, cx, cy - r, cz).setColor(dark);
        }
    }

    static void box(VertexConsumer vc, Matrix4f m, double x0, double y0, double z0, double x1, double y1, double z1, int top, int side) {
        float a = (float) x0;
        float b = (float) y0;
        float c = (float) z0;
        float d = (float) x1;
        float e = (float) y1;
        float f = (float) z1;
        quad(vc, m, a, e, c, d, e, c, d, e, f, a, e, f, top);
        quad(vc, m, a, b, c, a, b, f, d, b, f, d, b, c, darken(side, 0.3f));
        quad(vc, m, a, b, c, d, b, c, d, e, c, a, e, c, side);
        quad(vc, m, a, b, f, a, e, f, d, e, f, d, b, f, darken(side, 0.1f));
        quad(vc, m, a, b, c, a, e, c, a, e, f, a, b, f, darken(side, 0.18f));
        quad(vc, m, d, b, c, d, b, f, d, e, f, d, e, c, side);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3, int argb) {
        vc.addVertex(m, x0, y0, z0).setColor(argb);
        vc.addVertex(m, x1, y1, z1).setColor(argb);
        vc.addVertex(m, x2, y2, z2).setColor(argb);
        vc.addVertex(m, x3, y3, z3).setColor(argb);
    }

    private static void drawPreviewLine(VertexConsumer vc, Matrix4f m, StaffInput.Preview pv, Vec3 cam) {
        Pathway shape = null;
        if (pv.pathway() != null && pv.dragTo() != null) {
            PathwayClientCache.Entry e = PathwayClientCache.get(pv.pathway());
            if (e != null) {
                Vec3 to = pv.dragTo();
                if (pv.insert()) {
                    int at = e.pathway.indexOf(pv.node());
                    shape = at < 0 ? null : e.pathway.insertAfter(at, to.x, to.y, to.z);
                } else {
                    shape = e.pathway.move(pv.node(), to.x, to.y, to.z);
                }
            }
        } else if (StaffInput.mode() == StaffMode.SURVEY && pv.stroke().isEmpty()) {
            PathwayClientCache.Entry e = PathwayClientCache.get(StaffInput.selected());
            StaffPicking.Pick h = StaffInput.hover();
            if (e != null && h.kind() == StaffPicking.Kind.GROUND && !e.pathway.nodes().isEmpty() && e.pathway.kind() != PathKind.LOOP) {
                List<PathNode> tail = e.pathway.nodes().subList(Math.max(0, e.pathway.nodes().size() - 3), e.pathway.nodes().size());
                shape = Pathway.create(e.pathway.id(), "", e.pathway.colour(), e.pathway.owner()).withNodeList(tail, 1000)
                        .append(h.point().x, h.point().y, h.point().z, false);
            }
        }
        if (shape == null) return;
        PathSpline sp = PathSpline.of(shape);
        int n = sp.sampleCount();
        int argb = 0xD0000000 | (BRASS & 0xFFFFFF);
        int last = sp.closed() ? n : n - 1;
        for (int i = 0; i < last; i++) {
            if ((i / 3) % 2 == 1) continue;
            int j = (i + 1) % n;
            quadLine(vc, m, (float) sp.sampleX(i), (float) sp.sampleY(i) + 0.08f, (float) sp.sampleZ(i),
                    (float) sp.sampleX(j), (float) sp.sampleY(j) + 0.08f, (float) sp.sampleZ(j), 0, 0, 0.12f, argb);
        }
    }

    private static void drawMove(VertexConsumer vc, Matrix4f m) {
        StaffInput.Move mv = StaffInput.move();
        if (mv == null) return;
        PathwayClientCache.Entry e = PathwayClientCache.get(mv.pathway());
        if (e == null || mv.toIndex() >= e.pathway.nodes().size()) return;
        Pathway moved = com.createstats.surveyor.core.PathEdits.translate(e.pathway, mv.fromIndex(), mv.toIndex(),
                mv.offset().x, mv.offset().y, mv.offset().z);
        PathSpline sp = PathSpline.of(moved);
        int n = sp.sampleCount();
        int argb = 0xE0000000 | (CHALK & 0xFFFFFF);
        int last = sp.closed() ? n : n - 1;
        for (int i = 0; i < last; i++) {
            if ((i / 3) % 2 == 1) continue;
            int j = (i + 1) % n;
            quadLine(vc, m, (float) sp.sampleX(i), (float) sp.sampleY(i) + 0.1f, (float) sp.sampleZ(i),
                    (float) sp.sampleX(j), (float) sp.sampleY(j) + 0.1f, (float) sp.sampleZ(j), 0, 0, 0.14f, argb);
        }
        float ax = (float) (mv.anchor().x + mv.offset().x);
        float ay = (float) (mv.anchor().y + mv.offset().y) + 0.12f;
        float az = (float) (mv.anchor().z + mv.offset().z);
        quadLine(vc, m, ax - 0.6f, ay, az - 0.6f, ax + 0.6f, ay, az + 0.6f, 0, 0, 0.1f, argb);
        quadLine(vc, m, ax - 0.6f, ay, az + 0.6f, ax + 0.6f, ay, az - 0.6f, 0, 0, 0.1f, argb);
    }

    private static void drawBrush(VertexConsumer vc, Matrix4f m, Minecraft mc) {
        List<double[]> sweep = StaffInput.brush();
        boolean ready = StaffInput.mode() == StaffMode.EDIT && mc.options.keySprint.isDown();
        if (sweep.isEmpty() && !ready) return;
        Vec3 at = StaffPicking.groundAhead(mc, 1.0f);
        if (at != null) {
            float r = (float) com.createstats.surveyor.core.PathEdits.BRUSH_RADIUS;
            int argb = 0xB0000000 | (BRASS & 0xFFFFFF);
            int seg = 48;
            for (int k = 0; k < seg; k++) {
                double a0 = 2 * Math.PI * k / seg;
                double a1 = 2 * Math.PI * (k + 1) / seg;
                quadLine(vc, m, (float) (at.x + Math.cos(a0) * r), (float) at.y + 0.08f, (float) (at.z + Math.sin(a0) * r),
                        (float) (at.x + Math.cos(a1) * r), (float) at.y + 0.08f, (float) (at.z + Math.sin(a1) * r), 0, 0, 0.08f, argb);
            }
        }
        if (sweep.size() >= 2) {
            int argb = 0x90000000 | (BRASS & 0xFFFFFF);
            for (int i = 1; i < sweep.size(); i++) {
                double[] a = sweep.get(i - 1);
                double[] b = sweep.get(i);
                quadLine(vc, m, (float) a[0], (float) a[1] + 0.07f, (float) a[2], (float) b[0], (float) b[1] + 0.07f, (float) b[2],
                        0, 0, 0.35f, argb);
            }
        }
    }

    static final double COMB_SCALE = 12.0;

    static final double COMB_MAX = 3.0;

    private static void drawComb(VertexConsumer vc, Matrix4f m, Vec3 cam) {
        PathwayClientCache.Entry e = PathwayClientCache.get(StaffInput.selected());
        if (e == null) return;
        PathSpline sp = e.spline();
        int n = sp.sampleCount();
        if (n < 3) return;
        int every = Math.max(1, (int) Math.round(0.5 / Math.max(1e-6, sp.step())));
        double range2 = (RANGE * 0.5) * (RANGE * 0.5);
        int tooth = 0x70000000 | (BRASS & 0xFFFFFF);
        int tips = 0xD0000000 | (BRASS & 0xFFFFFF);
        float px = Float.NaN, py = 0, pz = 0;
        int last = sp.closed() ? n : n - 1;
        for (int i = 0; i <= last; i += every) {
            int k = i % n;
            double x = sp.sampleX(k);
            double z = sp.sampleZ(k);
            if ((x - cam.x) * (x - cam.x) + (z - cam.z) * (z - cam.z) > range2) {
                px = Float.NaN;
                continue;
            }
            double kappa = sp.curvature(k);

            double len = Math.max(-COMB_MAX, Math.min(COMB_MAX, kappa * COMB_SCALE));
            double nx = sp.tangentZ(k);
            double nz = -sp.tangentX(k);
            float y = (float) sp.sampleY(k) + LIFT + 0.05f;
            float tx = (float) (x + nx * len);
            float tz = (float) (z + nz * len);
            if (Math.abs(len) > 0.02) quadLine(vc, m, (float) x, y, (float) z, tx, y, tz, 0, 0, 0.035f, tooth);
            if (!Float.isNaN(px)) quadLine(vc, m, px, py, pz, tx, y, tz, 0, 0, 0.05f, tips);
            px = tx;
            py = y;
            pz = tz;
        }
    }

    private static void drawThread(VertexConsumer vc, Matrix4f m, Player player, float partial) {
        if (StaffInput.mode() != StaffMode.LINK || StaffInput.linkVehicle() == null) return;
        Vec3 to = StaffInput.linkAt();
        PathwayClientCache.Vehicle v = PathwayClientCache.vehicle(StaffInput.linkVehicle());
        if (v != null && v.now != null) {
            double[] at = v.at(PathwayClientCache.clientTime() + partial);
            to = new Vec3(at[0], at[1] + 1.2, at[2]);
        }
        if (to == null) return;
        Vec3 look = player.getViewVector(partial);
        Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
        Vec3 from = player.getEyePosition(partial).add(look.scale(0.6)).add(right.scale(0.35)).add(0, -0.25, 0);
        int argb = 0xE0000000 | (StaffMode.LINK.colour & 0xFFFFFF);
        int segs = 24;
        Vec3 prev = from;
        double sag = Math.min(3.0, from.distanceTo(to) * 0.06);
        for (int s = 1; s <= segs; s++) {
            double t = s / (double) segs;
            Vec3 p = from.lerp(to, t).add(0, -Math.sin(Math.PI * t) * sag, 0);
            beam(vc, m, prev, p, 0.035f, argb);
            prev = p;
        }
    }

    static void beam(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, float w, int argb) {
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1.0e-8) return;
        Vec3 u = Math.abs(d.normalize().y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 s1 = d.cross(u).normalize().scale(w);
        Vec3 s2 = d.cross(s1).normalize().scale(w);
        Vec3[] o = {s1.add(s2), s1.subtract(s2), s1.scale(-1).subtract(s2), s1.scale(-1).add(s2)};
        for (int i = 0; i < 4; i++) {
            Vec3 p = o[i];
            Vec3 q = o[(i + 1) % 4];
            vc.addVertex(m, (float) (a.x + p.x), (float) (a.y + p.y), (float) (a.z + p.z)).setColor(argb);
            vc.addVertex(m, (float) (b.x + p.x), (float) (b.y + p.y), (float) (b.z + p.z)).setColor(argb);
            vc.addVertex(m, (float) (b.x + q.x), (float) (b.y + q.y), (float) (b.z + q.z)).setColor(argb);
            vc.addVertex(m, (float) (a.x + q.x), (float) (a.y + q.y), (float) (a.z + q.z)).setColor(argb);
        }
    }

    private static void drawSigns(Minecraft mc, PoseStack ps, MultiBufferSource buffers, Level level, int floor, Vec3 cam) {
        Font font = mc.font;
        double range2 = SIGN_RANGE * SIGN_RANGE;
        for (PathwayClientCache.Entry e : PathwayClientCache.all()) {
            Pathway p = e.pathway;
            PathSpline sp = e.spline();
            for (int k = 0; k < p.nodes().size() && k < sp.nodeCount(); k++) {
                PathNode n = p.nodes().get(k);
                if (!n.isStop() || n.name().isEmpty()) continue;
                double[] at = signAt(e, sp.nodeS(k));
                if ((at[0] - cam.x) * (at[0] - cam.x) + (at[1] - cam.z) * (at[1] - cam.z) > range2) continue;
                String second = "";
                float fraction = -1f;
                for (PathwayClientCache.Vehicle v : PathwayClientCache.vehiclesOn(p.id())) {
                    if (v.now == null || v.now.nextStop() != n.id()) continue;
                    if (RouteRunner.State.byOrdinal(v.now.state()) == RouteRunner.State.WAITING) {
                        second = Component.translatable("create_stats.surveyor.sign.waiting", v.now.name()).getString();
                        fraction = v.now.waitFraction();
                        break;
                    }
                    if (v.now.eta() >= 0 && second.isEmpty()) {
                        second = Component.translatable("create_stats.surveyor.sign.due", v.now.name(),
                                com.createstats.surveyor.RouteWords.seconds(Math.round(v.now.eta()))).getString();
                    }
                }
                int light = light(level, at[0], at[5] + SIGN_TOP, at[1], 0);
                for (int face = -1; face <= 1; face += 2) {
                    signFace(ps, buffers, font, at, face, n.name(), second, fraction, light);
                }
            }
        }
    }

    private static void signFace(PoseStack ps, MultiBufferSource buffers, Font font, double[] at, int face, String name,
                                 String second, float fraction, int light) {
        double tx = at[3];
        double tz = at[4];

        float nx = (float) (-tz * face);
        float nz = (float) (tx * face);
        float rx = nz;
        float rz = -nx;
        double out = SIGN_T / 2 + 0.02;
        ps.pushPose();
        ps.translate(at[0] + nx * out, at[2], at[1] + nz * out);
        Matrix4f frame = new Matrix4f(rx, 0f, rz, 0f, 0f, 1f, 0f, 0f, nx, 0f, nz, 0f, 0f, 0f, 0f, 1f);
        ps.mulPose(frame);
        float usable = (SIGN_W - 0.16f);
        int nameW = Math.max(1, font.width(name));
        float k = Math.min(0.03f, usable / nameW);
        ps.pushPose();
        ps.translate(0, second.isEmpty() ? 0.0 : 0.13, 0);
        ps.scale(k, -k, k);
        font.drawInBatch(name, -nameW / 2.0f, -4.0f, 0xFFF2F2EE, false, ps.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET,
                0, light);
        ps.popPose();
        if (!second.isEmpty()) {
            float k2 = Math.min(0.016f, usable / Math.max(1, font.width(second)));
            ps.pushPose();
            ps.translate(0, -0.12, 0);
            ps.scale(k2, -k2, k2);
            font.drawInBatch(second, -font.width(second) / 2.0f, -4.0f, fraction >= 0 ? 0xFF7FE07F : 0xFFBFC6CC, false, ps.last().pose(),
                    buffers, Font.DisplayMode.POLYGON_OFFSET, 0, light);
            ps.popPose();
            if (fraction >= 0) {
                VertexConsumer vc = buffers.getBuffer(SurveyorRenderTypes.THINGS);
                Matrix4f m = ps.last().pose();
                float w = usable;
                float y0 = -0.3f;
                float y1 = -0.24f;
                float z = 0.005f;
                LIT.on(vc, light);
                quadXY(LIT, m, -w / 2, y0, w / 2, y1, z, 0xFF0E1012);
                quadXY(LIT, m, -w / 2, y0, -w / 2 + w * Math.max(0f, Math.min(1f, fraction)), y1, z + 0.002f, 0xFF55C455);
            }
        }
        ps.popPose();
    }

    private static void quadXY(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float z, int argb) {
        vc.addVertex(m, x0, y0, z).setColor(argb);
        vc.addVertex(m, x1, y0, z).setColor(argb);
        vc.addVertex(m, x1, y1, z).setColor(argb);
        vc.addVertex(m, x0, y1, z).setColor(argb);
    }

    private static void drawTag(Minecraft mc, PoseStack ps, MultiBufferSource buffers, Camera camera, StaffPicking.Pick hover) {
        List<String> lines = new ArrayList<>();
        Vec3 at = null;
        if (hover.kind() == StaffPicking.Kind.NODE) {
            PathwayClientCache.Entry e = PathwayClientCache.get(hover.pathway());
            PathNode n = e == null ? null : e.pathway.node(hover.nodeId());
            if (n == null) return;
            Pathway p = e.pathway;
            lines.add(p.label(n.id()) + (n.name().isEmpty() ? "" : " (N" + p.numberOf(n.id()) + ")") + "  " + p.name());
            String speed = n.speedLimit() > 0 ? fmt(n.speedLimit()) + " b/s" : "cruise " + fmt(p.cruise()) + " b/s";
            lines.add(speed + "  " + Component.translatable("create_stats.surveyor.corner." + n.corner().key).getString());
            if (n.isStop()) lines.add(RouteText.rules(n.stop()));
            if (StaffInput.mode() == StaffMode.EDIT) lines.add(Component.translatable("create_stats.surveyor.tag_edit_hint").getString());
            at = new Vec3(n.x(), n.y() + (n.isStop() ? 2.9 : 1.7), n.z());
        } else if (hover.kind() == StaffPicking.Kind.SEGMENT) {
            PathwayClientCache.Entry e = PathwayClientCache.get(hover.pathway());
            if (e == null) return;
            PathwayChecks.Result c = e.lastChecks();
            String why = null;
            SegmentCheck.Level worst = SegmentCheck.Level.FINE;
            if (c != null) {
                for (SegmentCheck.Finding f : c.findings()) {
                    if (hover.s() >= f.from() - 0.5 && hover.s() <= f.to() + 0.5 && f.level().ordinal() >= worst.ordinal()) {
                        worst = f.level();
                        why = f.why();
                    }
                }
            }
            lines.add(e.pathway.name() + "  " + fmt(e.plan().at(hover.s())) + " b/s planned");
            if (why != null) lines.add((worst == SegmentCheck.Level.RED ? "Cannot drive: " : "Take care: ") + why);
            at = hover.point().add(0, 1.0, 0);
        } else if (hover.kind() == StaffPicking.Kind.HANDLE) {
            lines.add(Component.translatable("create_stats.surveyor.handle_hint").getString());
            at = hover.point().add(0, 0.8, 0);
        }
        if (at == null || lines.isEmpty()) return;
        billboard(mc, ps, buffers, camera, at, lines, 0xFFFFFFFF, 0xA0000000, Font.DisplayMode.NORMAL);
    }

    private static void drawCards(Minecraft mc, PoseStack ps, MultiBufferSource buffers, Camera camera, float partial) {
        Vec3 cam = camera.getPosition();
        for (PathwayClientCache.Vehicle v : PathwayClientCache.vehicles()) {
            if (v.now == null) continue;
            boolean pinned = v.now.console().equals(StaffInput.pinned());
            if (StaffInput.mode() != StaffMode.WATCH && !pinned) continue;
            double[] at = v.at(PathwayClientCache.clientTime() + partial);
            double d = Math.sqrt((at[0] - cam.x) * (at[0] - cam.x) + (at[2] - cam.z) * (at[2] - cam.z));
            if (d > 128 && !pinned) continue;

            billboard(mc, ps, buffers, camera, new Vec3(at[0], at[1] + 3.2, at[2]), RouteText.card(v.now),
                    pinned ? 0xFFFFFF80 : 0xFFFFFFFF, 0xB0101418, Font.DisplayMode.SEE_THROUGH);
        }
    }

    static void billboard(Minecraft mc, PoseStack ps, MultiBufferSource buffers, Camera camera, Vec3 at, List<String> lines,
                          int colour, int background, Font.DisplayMode mode) {
        Font font = mc.font;
        Vec3 cam = camera.getPosition();
        double dist = at.distanceTo(cam);
        float scale = (float) (0.025 * Math.max(1.0, dist / 12.0));
        ps.pushPose();
        ps.translate(at.x, at.y, at.z);
        ps.mulPose(camera.rotation());
        ps.scale(scale, -scale, scale);
        Matrix4f m = ps.last().pose();
        int y = -lines.size() * 10;
        for (String line : lines) {
            float x = -font.width(line) / 2.0f;
            font.drawInBatch(line, x, y, colour, false, m, buffers, mode, background, LightTexture.FULL_BRIGHT);
            y += 10;
        }
        ps.popPose();
    }

    static String fmt(double v) {
        return Math.abs(v - Math.rint(v)) < 0.05 ? String.valueOf((long) Math.rint(v)) : String.format(Locale.ROOT, "%.1f", v);
    }

    static int lighten(int argb, float f) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        r = r + (int) ((255 - r) * f);
        g = g + (int) ((255 - g) * f);
        b = b + (int) ((255 - b) * f);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    static int darken(int argb, float f) {
        int r = (int) (((argb >> 16) & 0xFF) * (1 - f));
        int g = (int) (((argb >> 8) & 0xFF) * (1 - f));
        int b = (int) ((argb & 0xFF) * (1 - f));
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    static int speedColour(double v, double cruise) {
        double t = Math.max(0.0, Math.min(1.0, (v - SpeedPlan.CREEP) / Math.max(0.5, cruise - SpeedPlan.CREEP)));
        int r = (int) (255 * (1 - t) + 80 * t);
        int g = (int) (80 * (1 - t) + 230 * t);
        return 0xFF000000 | (r << 16) | (g << 8) | 70;
    }

    static String stateWord(int state) {
        return Component.translatable("create_stats.surveyor.state." + RouteRunner.State.byOrdinal(state).key).getString();
    }

    @SuppressWarnings("unused")
    private static void unused(RouteTrack t) {
    }
}
