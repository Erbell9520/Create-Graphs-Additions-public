package com.createstats.surveyor.client;

import com.createstats.surveyor.PathwayChecks;
import com.createstats.surveyor.core.PathSpline;
import com.createstats.surveyor.core.SpeedPlan;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.UUID;

public final class GhostRun {
    public static final double MAX_SECONDS = 60.0;

    @Nullable private static UUID pathway;
    private static double s;
    private static double travelled;
    private static double lastTime = Double.NaN;
    private static double started;

    private GhostRun() {
    }

    public static void start(@Nullable UUID id, int nodeId) {
        PathwayClientCache.Entry e = PathwayClientCache.get(id);
        if (e == null) return;
        int index = e.pathway.indexOf(nodeId);
        pathway = id;
        s = index < 0 ? 0.0 : e.spline().nodeS(index);
        travelled = 0.0;
        lastTime = Double.NaN;
        Minecraft mc = Minecraft.getInstance();
        started = mc.level == null ? 0 : mc.level.getGameTime() / 20.0;
    }

    public static boolean active() {
        return pathway != null;
    }

    public static void stop() {
        pathway = null;
    }

    public static void draw(VertexConsumer vc, Matrix4f m, Level level, double time) {
        PathwayClientCache.Entry e = PathwayClientCache.get(pathway);
        if (e == null) {
            pathway = null;
            return;
        }
        PathSpline sp = e.spline();
        SpeedPlan plan = e.plan();
        if (!Double.isNaN(lastTime)) {
            double dt = Math.max(0.0, Math.min(0.25, time - lastTime));
            double v = plan.at(s);
            s += v * dt;
            travelled += v * dt;
        }
        lastTime = time;
        if (travelled > sp.length() || (!sp.closed() && s >= sp.length()) || time - started > MAX_SECONDS) {
            pathway = null;
            return;
        }
        s = sp.wrap(s);
        double front = 2.5;
        double rear = 2.5;
        double half = StaffInput.checkHalfWidth();
        for (PathwayClientCache.Vehicle v : PathwayClientCache.vehiclesOn(e.pathway.id())) {
            if (v.now != null && v.now.front() > 0) {
                front = v.now.front();
                rear = v.now.rear();
                break;
            }
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int argb = 0x5070E0FF;
        for (double d = -rear; d <= front; d += 0.5) {
            double at = sp.closed() ? s + d : Math.max(0.0, Math.min(sp.length(), s + d));
            double x = sp.x(at);
            double z = sp.z(at);
            double g = PathwayChecks.groundAt(level, x, sp.y(at), z, pos);
            if (Double.isNaN(g)) g = sp.y(at);
            double[] t = sp.tangent(at);
            double nx = -t[1] * half;
            double nz = t[0] * half;
            double tx = t[0] * 0.25;
            double tz = t[1] * 0.25;
            float y0 = (float) g + 0.15f;
            float y1 = (float) g + 1.7f;
            vc.addVertex(m, (float) (x - nx - tx), y1, (float) (z - nz - tz)).setColor(argb);
            vc.addVertex(m, (float) (x + nx - tx), y1, (float) (z + nz - tz)).setColor(argb);
            vc.addVertex(m, (float) (x + nx + tx), y1, (float) (z + nz + tz)).setColor(argb);
            vc.addVertex(m, (float) (x - nx + tx), y1, (float) (z - nz + tz)).setColor(argb);
            if (Math.abs(d + rear) < 0.26 || Math.abs(d - front) < 0.26) {
                vc.addVertex(m, (float) (x - nx), y0, (float) (z - nz)).setColor(argb);
                vc.addVertex(m, (float) (x + nx), y0, (float) (z + nz)).setColor(argb);
                vc.addVertex(m, (float) (x + nx), y1, (float) (z + nz)).setColor(argb);
                vc.addVertex(m, (float) (x - nx), y1, (float) (z - nz)).setColor(argb);
            }

            boolean flash = ((long) (time * 4)) % 2 == 0;
            for (double w = -half + 0.5; w <= half - 0.5 + 1.0e-6; w += 1.0) {
                int bx = (int) Math.floor(x + (-t[1]) * w);
                int bz = (int) Math.floor(z + t[0] * w);
                for (int h = 1; h <= 2; h++) {
                    int by = (int) Math.floor(g + 0.01) + h;
                    pos.set(bx, by, bz);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || state.getCollisionShape(level, pos).isEmpty()) continue;
                    if (flash) {
                        PathwayRenderer.box(vc, m, bx - 0.01, by - 0.01, bz - 0.01, bx + 1.01, by + 1.01, bz + 1.01, 0x90FF2020, 0x90FF2020);
                    }
                }
            }
        }
    }
}
