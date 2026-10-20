package com.createstats.battle.world;

import com.createstats.flight.bridge.SableBridge;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

final class Frames {
    private Frames() {
    }

    static double[] toWorld(@Nullable Object body, double x, double y, double z) {
        if (body != null) {
            Vector3d w = SableBridge.localToWorld(body, new Vector3d(x, y, z));
            if (w != null) return new double[]{w.x, w.y, w.z};
        }
        return new double[]{x, y, z};
    }

    static double[] toLocal(@Nullable Object body, double[] world) {
        if (body != null) {
            Vector3d l = SableBridge.worldToLocal(body, new Vector3d(world[0], world[1], world[2]));
            if (l != null) return new double[]{l.x, l.y, l.z};
        }
        return world.clone();
    }

    static double[] dirToWorld(@Nullable Object body, double[] local) {
        Quaterniond q = body == null ? null : SableBridge.bodyOrientation(body);
        if (q == null) return local.clone();
        Vector3d v = q.transform(new Vector3d(local[0], local[1], local[2]));
        return new double[]{v.x, v.y, v.z};
    }

    static double[] dirToLocal(@Nullable Object body, double[] world) {
        Quaterniond q = body == null ? null : SableBridge.bodyOrientation(body);
        if (q == null) return world.clone();
        Vector3d v = new Quaterniond(q).conjugate().transform(new Vector3d(world[0], world[1], world[2]));
        return new double[]{v.x, v.y, v.z};
    }

    static double[] normalize(double[] v) {
        double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return l < 1e-9 ? new double[]{0, 0, 1} : new double[]{v[0] / l, v[1] / l, v[2] / l};
    }
}
