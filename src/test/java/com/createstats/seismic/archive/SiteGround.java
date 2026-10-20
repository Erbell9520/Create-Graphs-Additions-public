package com.createstats.seismic.archive;

import com.createstats.seismic.SeismicCatalog;
import com.createstats.seismic.SeismicWave;
import com.createstats.seismic.ShellScan;
import com.createstats.seismic.VeinBook;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.zip.GZIPInputStream;

final class SiteGround implements ShellScan.Ground<int[]> {
    static final String[] ORES = {"coal", "iron", "copper", "gold", "redstone", "lapis", "diamond", "emerald",
            "quartz", "zinc", "netherite_scrap"};

    static final int[] OWNER_IRON_RIG = {158, 69, -359};
    static final int[] OWNER_DIAMOND_RIG = {161, 69, -360};

    static final int[] EAST_RIG = {185, 65, -340};
    static final int[] WEST_RIG = {140, 68, -385};
    static final int[] NORTH_RIG = {190, 69, -380};
    static final int[] DEEP_RIG = {164, -8, -353};

    final int x0;
    final int y0;
    final int z0;
    final int nx;
    final int ny;
    final int nz;
    private final short[] motion;
    private final short[] floor;
    private final byte[] grid;
    private static final int[] SECTION = new int[0];

    private SiteGround(int x0, int y0, int z0, int nx, int ny, int nz, short[] motion, short[] floor, byte[] grid) {
        this.x0 = x0;
        this.y0 = y0;
        this.z0 = z0;
        this.nx = nx;
        this.ny = ny;
        this.nz = nz;
        this.motion = motion;
        this.floor = floor;
        this.grid = grid;
    }

    static SiteGround owner() {
        try (InputStream raw = SiteGround.class.getResourceAsStream("/seismic/owner_site.sgnd.gz")) {
            if (raw == null) {
                throw new IllegalStateException("src/test/resources/seismic/owner_site.sgnd.gz is missing");
            }
            final DataInputStream in = new DataInputStream(new GZIPInputStream(raw, 1 << 16));
            if (in.readInt() != 0x53474E44) {
                throw new IllegalStateException("not a site ground file");
            }
            in.readInt();
            final int x0 = in.readInt();
            final int y0 = in.readInt();
            final int z0 = in.readInt();
            final int nx = in.readInt();
            final int ny = in.readInt();
            final int nz = in.readInt();
            final short[] motion = new short[nx * nz];
            final short[] floor = new short[nx * nz];
            for (int i = 0; i < motion.length; i++) {
                motion[i] = in.readShort();
            }
            for (int i = 0; i < floor.length; i++) {
                floor[i] = in.readShort();
            }
            final byte[] grid = new byte[nx * ny * nz];
            in.readFully(grid);
            return new SiteGround(x0, y0, z0, nx, ny, nz, motion, floor, grid);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    boolean inside(int x, int y, int z) {
        return x >= x0 && y >= y0 && z >= z0 && x < x0 + nx && y < y0 + ny && z < z0 + nz;
    }

    int classAt(int x, int y, int z) {
        if (!inside(x, y, z)) {
            return 0;
        }
        return grid[((y - y0) * nz + (z - z0)) * nx + (x - x0)] & 0xFF;
    }

    void mine(int x, int y, int z) {
        if (inside(x, y, z)) {
            grid[((y - y0) * nz + (z - z0)) * nx + (x - x0)] = 1;
        }
    }

    private int heightAt(short[] map, int x, int z) {
        if (x < x0 || z < z0 || x >= x0 + nx || z >= z0 + nz) {
            return Integer.MIN_VALUE;
        }
        return map[(z - z0) * nx + (x - x0)];
    }

    private String target = "";
    private int oreClass = -1;

    void tune(String id) {
        this.target = id;
        this.oreClass = -1;
        if (SeismicCatalog.isOre(id)) {
            final String path = SeismicCatalog.orePath(id);
            for (int i = 0; i < ORES.length; i++) {
                if (ORES[i].equals(path)) {
                    this.oreClass = 10 + i;
                }
            }
        }
    }

    @Override
    public int[] section(int sx, int sy, int sz) {
        final int bx = sx << 4;
        final int by = sy << 4;
        final int bz = sz << 4;
        if (bx + 15 < x0 || by + 15 < y0 || bz + 15 < z0 || bx >= x0 + nx || by >= y0 + ny || bz >= z0 + nz) {
            return null;
        }
        return SECTION;
    }

    @Override
    public int kindAt(int[] s, int x, int y, int z) {
        if (!inside(x, y, z)) {
            return -1;
        }
        final int c = classAt(x, y, z);
        if (SeismicCatalog.VOIDS.equals(target)) {
            return c == 1 ? 0 : -1;
        }
        if (SeismicCatalog.LIQUIDS.equals(target)) {
            return c == 3 ? SeismicCatalog.KIND_LAVA : c == 2 ? SeismicCatalog.KIND_WATER : -1;
        }
        return c == oreClass && oreClass >= 0 ? 0 : -1;
    }

    @Override
    public boolean covered(int x, int y, int z) {
        if (SeismicCatalog.VOIDS.equals(target)) {
            return y < heightAt(motion, x, z);
        }
        if (SeismicCatalog.LIQUIDS.equals(target)) {
            return y < heightAt(floor, x, z);
        }
        return true;
    }

    ArchiveBlow strike(SeismicArchive archive, int[] rig, String target, int reach, long at) {
        return strike(archive, rig, target, reach, at, 0L, 0L, Integer.MAX_VALUE);
    }

    ArchiveBlow strike(SeismicArchive archive, int[] rig, String target, int reach, long at, long holeLo, long holeHi,
                       int ticks) {
        tune(target);
        final boolean diagonal = SeismicCatalog.isOre(target);
        final ArchiveBlow b = archive.begin(rig[0], rig[1], rig[2], target, reach, at, -64, holeLo, holeHi);
        b.recordSurface((x, z) -> {
            final int h = heightAt(motion, x, z);
            return h == Integer.MIN_VALUE || b.hole(x, z) ? ArchiveBlow.NO_SURFACE : h;
        });
        final ShellScan<int[]> scan = new ShellScan<>(holeLo == 0L && holeHi == 0L ? this : holed(b), rig[0], rig[1],
                rig[2], reach, new VeinBook(32_768, diagonal));
        int t = 0;
        while (!scan.done() && t < ticks) {
            final List<VeinBook.Vein> closed = scan.step();
            t++;
            final double front = Math.min(scan.radius(), SeismicWave.frontAt(scan.tick()));
            archive.step(b, front, scan.full(), closed, scan.done());
        }
        return b;
    }

    private ShellScan.Ground<int[]> holed(ArchiveBlow b) {
        final SiteGround self = this;
        return new ShellScan.Ground<>() {
            @Override
            public int[] section(int sx, int sy, int sz) {
                return b.hole(sx << 4, sz << 4) ? null : self.section(sx, sy, sz);
            }

            @Override
            public int kindAt(int[] s, int x, int y, int z) {
                return self.kindAt(s, x, y, z);
            }

            @Override
            public boolean covered(int x, int y, int z) {
                return self.covered(x, y, z);
            }
        };
    }
}
