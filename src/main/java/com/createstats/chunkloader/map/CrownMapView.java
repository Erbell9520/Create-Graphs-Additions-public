package com.createstats.chunkloader.map;

import com.createstats.chunkloader.core.CrownRegion;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class CrownMapView {
    public static final int[] ZOOMS = {8, 16, 32, 64};

    public static final int OPEN_ZOOM = 16;

    public static final int SURROUND = CrownRegion.CHUNKS / 2;

    public static final int WINDOW = CrownRegion.CHUNKS + 2 * SURROUND;

    private final int regionX;
    private final int regionZ;
    private int width;
    private int height;
    private int zoom = OPEN_ZOOM;
    private double originX;
    private double originZ;

    public CrownMapView(int regionX, int regionZ, int width, int height) {
        this.regionX = regionX;
        this.regionZ = regionZ;
        resize(width, height);
        centre();
    }

    public void resize(int width, int height) {
        final double midX = originX + this.width / 2.0 / scale();
        final double midZ = originZ + this.height / 2.0 / scale();
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        originX = midX - this.width / 2.0 / scale();
        originZ = midZ - this.height / 2.0 / scale();
        clamp();
    }

    public void centre() {
        final double s = scale();
        originX = CrownRegion.firstBlock(regionX) + 256 - Math.floor((int) (width / s) / 2.0);
        originZ = CrownRegion.firstBlock(regionZ) + 256 - Math.floor((int) (height / s) / 2.0);
        clamp();
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int zoom() {
        return zoom;
    }

    public double scale() {
        return zoom / 16.0;
    }

    public double originX() {
        return originX;
    }

    public double originZ() {
        return originZ;
    }

    public int pixelX(double blockX) {
        return (int) Math.floor((blockX - originX) * scale() + 1e-6);
    }

    public int pixelZ(double blockZ) {
        return (int) Math.floor((blockZ - originZ) * scale() + 1e-6);
    }

    public int chunkPixelX(int chunkX) {
        return pixelX(chunkX * 16.0);
    }

    public int chunkPixelZ(int chunkZ) {
        return pixelZ(chunkZ * 16.0);
    }

    public int blockAtX(double mapX) {
        return (int) Math.floor(originX + mapX / scale() + 1e-9);
    }

    public int blockAtZ(double mapZ) {
        return (int) Math.floor(originZ + mapZ / scale() + 1e-9);
    }

    public long chunkAt(double mapX, double mapZ) {
        return CrownRegion.pack(Math.floorDiv(blockAtX(mapX), 16), Math.floorDiv(blockAtZ(mapZ), 16));
    }

    public int[] regionRect() {
        final int x0 = chunkPixelX(regionX * CrownRegion.CHUNKS);
        final int z0 = chunkPixelZ(regionZ * CrownRegion.CHUNKS);
        return new int[] {x0, z0, chunkPixelX((regionX + 1) * CrownRegion.CHUNKS),
                chunkPixelZ((regionZ + 1) * CrownRegion.CHUNKS)};
    }

    public int windowChunkX() {
        return regionX * CrownRegion.CHUNKS - SURROUND;
    }

    public int windowChunkZ() {
        return regionZ * CrownRegion.CHUNKS - SURROUND;
    }

    public boolean inWindow(int chunkX, int chunkZ) {
        return chunkX >= windowChunkX() && chunkX < windowChunkX() + WINDOW && chunkZ >= windowChunkZ()
                && chunkZ < windowChunkZ() + WINDOW;
    }

    public void pan(double dx, double dz) {
        originX -= dx / scale();
        originZ -= dz / scale();
        clamp();
    }

    public boolean zoomAbout(double mapX, double mapZ, int steps) {
        int index = 0;
        for (int i = 0; i < ZOOMS.length; i++) {
            if (ZOOMS[i] == zoom) {
                index = i;
            }
        }
        final int next = ZOOMS[Math.max(0, Math.min(ZOOMS.length - 1, index + steps))];
        if (next == zoom) {
            return false;
        }
        final double bx = originX + mapX / scale();
        final double bz = originZ + mapZ / scale();
        zoom = next;
        originX = bx - mapX / scale();
        originZ = bz - mapZ / scale();
        clamp();
        return true;
    }

    private void clamp() {
        final double s = scale();
        originX = clampAxis(originX, windowChunkX() * 16.0, width / s, s);
        originZ = clampAxis(originZ, windowChunkZ() * 16.0, height / s, s);
    }

    private static double clampAxis(double origin, double window0, double viewBlocks, double scale) {
        final double windowBlocks = WINDOW * 16.0;
        final double o;
        if (viewBlocks >= windowBlocks) {
            o = window0 - (viewBlocks - windowBlocks) / 2.0;
        } else {
            o = Math.max(window0, Math.min(window0 + windowBlocks - viewBlocks, origin));
        }
        return quantise(o, scale);
    }

    static double quantise(double origin, double scale) {
        return Math.round(origin * scale) / scale;
    }

    public static List<Long> line(int x0, int z0, int x1, int z1) {
        final List<Long> out = new ArrayList<>();
        final int dx = Math.abs(x1 - x0);
        final int dz = -Math.abs(z1 - z0);
        final int sx = x0 < x1 ? 1 : -1;
        final int sz = z0 < z1 ? 1 : -1;
        int err = dx + dz;
        int x = x0;
        int z = z0;
        while (true) {
            out.add(CrownRegion.pack(x, z));
            if (x == x1 && z == z1) {
                return out;
            }
            final int e2 = 2 * err;
            if (e2 >= dz) {
                err += dz;
                x += sx;
            }
            if (e2 <= dx) {
                err += dx;
                z += sz;
            }
        }
    }

    public static List<Long> rectangle(int x0, int z0, int x1, int z1) {
        final List<Long> out = new ArrayList<>();
        final int sx = x0 <= x1 ? 1 : -1;
        final int sz = z0 <= z1 ? 1 : -1;
        for (int z = z0; ; z += sz) {
            for (int x = x0; ; x += sx) {
                out.add(CrownRegion.pack(x, z));
                if (x == x1) {
                    break;
                }
            }
            if (z == z1) {
                return out;
            }
        }
    }

    public static List<Long> path(List<Long> samples) {
        final Set<Long> out = new LinkedHashSet<>();
        Long last = null;
        for (Long c : samples) {
            if (last == null) {
                out.add(c);
            } else {
                out.addAll(line(CrownRegion.x(last), CrownRegion.z(last), CrownRegion.x(c), CrownRegion.z(c)));
            }
            last = c;
        }
        return new ArrayList<>(out);
    }
}
