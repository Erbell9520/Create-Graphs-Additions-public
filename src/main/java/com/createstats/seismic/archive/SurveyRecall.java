package com.createstats.seismic.archive;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.createstats.seismic.SeismicCatalog;
import com.createstats.seismic.SurveyShells;
import com.createstats.seismic.VeinBook;

public record SurveyRecall(int x, int y, int z, String target, long at, int reach, double recorded, boolean full,
                           boolean cutShort, int veinsFound, int blocksFound, List<VeinBook.Vein> veins) {
    public int kept() {
        int n = 0;
        for (final VeinBook.Vein v : this.veins) {
            n += v.count();
        }
        return n;
    }

    public boolean complete() {
        return kept() == this.blocksFound;
    }

    static SurveyRecall of(ArchiveBlow b) {
        return new SurveyRecall(b.x, b.y, b.z, b.target, b.at, b.reach, b.recorded(), b.full(), b.cutShort(),
                b.veinsFound(), b.blocksFound(), List.copyOf(veins(b.cells(), diagonal(b.target))));
    }

    static boolean diagonal(String target) {
        return !SeismicCatalog.VOIDS.equals(target) && !SeismicCatalog.LIQUIDS.equals(target);
    }

    static List<VeinBook.Vein> veins(int[] cells, boolean diagonal) {
        final int n = cells.length;
        if (n == 0) {
            return List.of();
        }

        final int[] sorted = cells.clone();
        Arrays.sort(sorted);
        final int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        for (int i = 0; i < n; i++) {
            final int kind = ArchiveBlow.kindOf(sorted[i]);
            final int p = ArchiveBlow.packedOf(sorted[i]);
            final int x = SurveyShells.dx(p);
            final int y = SurveyShells.dy(p);
            final int z = SurveyShells.dz(p);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        final int axes = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                        if (axes == 0 || (!diagonal && axes > 1)) {
                            continue;
                        }
                        final int nx = x + dx;
                        final int ny = y + dy;
                        final int nz = z + dz;
                        if (nx < -128 || nx > 127 || ny < -128 || ny > 127 || nz < -128 || nz > 127) {
                            continue;
                        }
                        final int j = Arrays.binarySearch(sorted,
                                ArchiveBlow.cell(kind, SurveyShells.pack(nx, ny, nz)));
                        if (j > i) {
                            union(parent, i, j);
                        }
                    }
                }
            }
        }

        final int[] root = new int[n];
        final int[] members = new int[n];
        for (int i = 0; i < n; i++) {
            root[i] = find(parent, i);
            members[root[i]]++;
        }
        final List<VeinBook.Vein> out = new ArrayList<>();
        final int[][] blocks = new int[n][];
        final int[] fill = new int[n];
        for (int i = 0; i < n; i++) {
            final int r = root[i];
            if (blocks[r] == null) {
                blocks[r] = new int[members[r]];
            }
            blocks[r][fill[r]++] = ArchiveBlow.packedOf(sorted[i]);
        }
        for (int r = 0; r < n; r++) {
            if (blocks[r] == null) {
                continue;
            }
            final int[] b = blocks[r];
            final float[] d = new float[b.length];
            float near = Float.MAX_VALUE;
            float far = 0.0f;
            double sx = 0.0;
            double sy = 0.0;
            double sz = 0.0;
            for (int k = 0; k < b.length; k++) {
                final int x = SurveyShells.dx(b[k]);
                final int y = SurveyShells.dy(b[k]);
                final int z = SurveyShells.dz(b[k]);
                d[k] = (float) SurveyShells.distanceOf(x, y, z);
                near = Math.min(near, d[k]);
                far = Math.max(far, d[k]);
                sx += x;
                sy += y;
                sz += z;
            }

            final Integer[] order = new Integer[b.length];
            for (int k = 0; k < b.length; k++) {
                order[k] = k;
            }
            Arrays.sort(order, (p, q) -> d[p] != d[q] ? Float.compare(d[p], d[q]) : Integer.compare(b[p], b[q]));
            final int[] ordered = new int[b.length];
            for (int k = 0; k < b.length; k++) {
                ordered[k] = b[order[k]];
            }
            final double c = b.length;
            out.add(new VeinBook.Vein(ArchiveBlow.kindOf(sorted[r]), b.length, near, far, ordered, sx / c + 0.5,
                    sy / c + 0.5, sz / c + 0.5));
        }
        out.sort((a, b) -> a.nearest() != b.nearest() ? Float.compare(a.nearest(), b.nearest())
                : Integer.compare(a.blocks()[0], b.blocks()[0]));
        return out;
    }

    private static int find(int[] parent, int i) {
        int r = i;
        while (parent[r] != r) {
            r = parent[r];
        }
        while (parent[i] != r) {
            final int up = parent[i];
            parent[i] = r;
            i = up;
        }
        return r;
    }

    private static void union(int[] parent, int a, int b) {
        final int ra = find(parent, a);
        final int rb = find(parent, b);
        if (ra != rb) {
            if (ra < rb) {
                parent[rb] = ra;
            } else {
                parent[ra] = rb;
            }
        }
    }
}
