package com.createstats.surveyor.client;

import com.createstats.driverhelmet.DriverHelmetItem;
import com.createstats.driverhelmet.DriverHelmets;
import com.createstats.driverhelmet.HelmetVisor;
import com.createstats.driverhelmet.HudTheme;
import com.createstats.surveyor.PathwayChecks;
import com.createstats.surveyor.RouteTrack;
import com.createstats.surveyor.core.PathSpline;
import com.createstats.surveyor.core.RouteRunner;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import javax.annotation.Nullable;

public final class RouteSight {
    public static final double AHEAD = 60.0;

    static final double RIDE_REACH = 8.0;

    private RouteSight() {
    }

    public static boolean wantsPathways(@Nullable Player p) {
        if (p == null || !p.isPassenger()) return false;
        ItemStack helmet = DriverHelmets.worn(p);
        return !helmet.isEmpty() && !DriverHelmetItem.visor(helmet).open();
    }

    @Nullable
    static RouteTrack riding(@Nullable Player p) {
        if (!wantsPathways(p)) return null;
        RouteTrack best = null;
        double bestD = RIDE_REACH * RIDE_REACH;
        for (PathwayClientCache.Vehicle v : PathwayClientCache.vehicles()) {
            if (v.now == null || !RouteRunner.State.byOrdinal(v.now.state()).drives()) continue;
            double dx = v.now.x() - p.getX();
            double dz = v.now.z() - p.getZ();
            double d = dx * dx + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = v.now;
            }
        }
        return best;
    }

    public static void drawRibbon(VertexConsumer vc, Matrix4f m, Vec3 cam, double time) {
        Minecraft mc = Minecraft.getInstance();
        RouteTrack t = riding(mc.player);
        if (t == null || mc.level == null) return;
        PathwayClientCache.Entry e = PathwayClientCache.get(t.pathway());
        if (e == null) return;
        PathSpline sp = e.spline();
        HelmetVisor visor = DriverHelmetItem.visor(DriverHelmets.worn(mc.player));
        int rgb = HudTheme.of(visor.theme()).primary & 0xFFFFFF;
        PathwayChecks.Result checks = e.lastChecks();
        double from = t.s() + Math.max(0.0, t.front());
        for (double d = 0.0; d < AHEAD; d += sp.step()) {
            double a = from + d;
            double b = a + sp.step();
            if (!sp.closed() && b > sp.length()) break;
            int alpha = (int) (0xC0 * (1.0 - d / AHEAD));
            int argb = (alpha << 24) | rgb;
            double ya = sp.y(a) + 0.07;
            double yb = sp.y(b) + 0.07;
            if (checks != null) {
                int ia = (int) (sp.wrap(a) / sp.step());
                int ib = (int) (sp.wrap(b) / sp.step());
                if (ia < checks.ground().length && Double.isFinite(checks.ground()[ia])) ya = checks.ground()[ia] + 0.07;
                if (ib < checks.ground().length && Double.isFinite(checks.ground()[ib])) yb = checks.ground()[ib] + 0.07;
            }
            double[] ta = sp.tangent(a);
            double[] tb = sp.tangent(b);
            float w = 0.12f;
            vc.addVertex(m, (float) (sp.x(a) + ta[1] * w), (float) ya, (float) (sp.z(a) - ta[0] * w)).setColor(argb);
            vc.addVertex(m, (float) (sp.x(a) - ta[1] * w), (float) ya, (float) (sp.z(a) + ta[0] * w)).setColor(argb);
            vc.addVertex(m, (float) (sp.x(b) - tb[1] * w), (float) yb, (float) (sp.z(b) + tb[0] * w)).setColor(argb);
            vc.addVertex(m, (float) (sp.x(b) + tb[1] * w), (float) yb, (float) (sp.z(b) - tb[0] * w)).setColor(argb);
        }
    }
}
