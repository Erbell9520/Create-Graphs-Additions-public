package com.createstats.vccmk4.air;

import com.createstats.aerial.AerialSnapshot;
import com.createstats.flight.bridge.SableBridge;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

final class AerialTraffic {
    static final double RANGE = 512.0;

    static final double ABOARD = 2.0;

    static final double MIN_VOLUME = 8.0;

    private AerialTraffic() {
    }

    static List<AerialSnapshot.Traffic> read(Level level, @Nullable UUID own, Vector3d centre, @Nullable double[] ownBox) {
        List<AerialSnapshot.Traffic> out = new ArrayList<>();
        for (Object s : SableBridge.listSubLevels(level)) {
            UUID id = SableBridge.subLevelId(s);
            if (id != null && id.equals(own)) continue;
            Vector3d at = SableBridge.subLevelWorldCenter(s);
            if (at == null || !Double.isFinite(at.x) || !Double.isFinite(at.z)) continue;
            double dx = at.x - centre.x;
            double dz = at.z - centre.z;
            if (dx * dx + dz * dz > RANGE * RANGE) continue;
            double[] box = AerialReflect.worldBounds(s);
            double radius = 8.0;
            if (box != null) {
                double sx = box[3] - box[0];
                double sy = box[4] - box[1];
                double sz = box[5] - box[2];
                if (sx * sy * sz < MIN_VOLUME) continue;
                if (ownBox != null && touches(box, ownBox)) continue;
                radius = 0.5 * Math.hypot(sx, sz);
            }
            Vector3d v = SableBridge.linearVelocityWorldOf(s);
            out.add(new AerialSnapshot.Traffic(SableBridge.subLevelName(s), id,
                    new com.createstats.aerial.V3(at.x, at.y, at.z),
                    v == null ? com.createstats.aerial.V3.ZERO : new com.createstats.aerial.V3(v.x, v.y, v.z), radius));
        }
        out.sort(Comparator.comparingDouble(t -> Math.hypot(t.position().x() - centre.x, t.position().z() - centre.z)));
        return out.size() > AerialSnapshot.MAX_TRAFFIC ? out.subList(0, AerialSnapshot.MAX_TRAFFIC) : out;
    }

    static boolean touches(double[] a, double[] b) {
        return a[0] <= b[3] + ABOARD && a[3] >= b[0] - ABOARD
                && a[1] <= b[4] + ABOARD && a[4] >= b[1] - ABOARD
                && a[2] <= b[5] + ABOARD && a[5] >= b[2] - ABOARD;
    }
}
