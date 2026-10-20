package com.createstats.chunkloader.look;

import com.createstats.chunkloader.core.CrownPlan;

import java.util.ArrayList;
import java.util.List;

final class MeshProbe implements CrownMesh.Sink {
    record Quad(float[] p, float[] uv, float nx, float ny, float nz, CrownMaterial material, boolean glow) {
    }

    final List<Quad> quads = new ArrayList<>();
    double minX = Double.MAX_VALUE;
    double minY = Double.MAX_VALUE;
    double minZ = Double.MAX_VALUE;
    double maxX = -Double.MAX_VALUE;
    double maxY = -Double.MAX_VALUE;
    double maxZ = -Double.MAX_VALUE;

    @Override
    public void quad(float[] p, float[] uv, float nx, float ny, float nz, CrownMaterial material, boolean glow) {
        quads.add(new Quad(p.clone(), uv.clone(), nx, ny, nz, material, glow));
        for (int i = 0; i < 4; i++) {
            minX = Math.min(minX, p[i * 3]);
            minY = Math.min(minY, p[i * 3 + 1]);
            minZ = Math.min(minZ, p[i * 3 + 2]);
            maxX = Math.max(maxX, p[i * 3]);
            maxY = Math.max(maxY, p[i * 3 + 1]);
            maxZ = Math.max(maxZ, p[i * 3 + 2]);
        }
    }

    static MeshProbe of(CrownPose p) {
        return of(p, new CrownAnchors(), CrownMesh.LOD_FULL);
    }

    static MeshProbe of(CrownPose p, CrownAnchors a, int lod) {
        final MeshProbe probe = new MeshProbe();
        new CrownMesh().build(p, a, probe, lod);
        return probe;
    }

    String outside(List<CrownPlan.Cell> cells) {
        final double tol = 1e-4;
        for (Quad q : quads) {
            for (int i = 0; i < 4; i++) {
                final double x = q.p()[i * 3];
                final double y = q.p()[i * 3 + 1];
                final double z = q.p()[i * 3 + 2];
                boolean in = false;
                for (CrownPlan.Cell c : cells) {
                    final double x0 = -16.0 * c.right();
                    final double y0 = 16.0 * c.up();
                    final double z0 = 16.0 * c.front();
                    if (x >= x0 - tol && x <= x0 + 16 + tol && y >= y0 - tol && y <= y0 + 16 + tol
                            && z >= z0 - tol && z <= z0 + 16 + tol) {
                        in = true;
                        break;
                    }
                }
                if (!in) {
                    return String.format(java.util.Locale.ROOT, "(%.3f, %.3f, %.3f) of a %s quad", x, y, z,
                            q.material());
                }
            }
        }
        return null;
    }
}
