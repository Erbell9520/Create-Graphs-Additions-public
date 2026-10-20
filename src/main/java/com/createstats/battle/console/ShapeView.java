package com.createstats.battle.console;

import com.createstats.battle.core.PackedPos;
import com.createstats.battle.core.Projection;
import com.createstats.battle.core.ShipFrame;

public record ShapeView(String key, int version, boolean ground, int bow, long origin, Projection.Frame frame,
                        byte[] plan, byte[] profile, int[] props, int[] bridges) {
    public ShipFrame shipFrame() {
        return ground ? ShipFrame.ground() : new ShipFrame(bow);
    }

    public int[] planCell(int packed) {
        ShipFrame f = shipFrame();
        int dx = PackedPos.dx(packed);
        int dz = PackedPos.dz(packed);
        return new int[]{frame.x(f.fore(dx, dz)), frame.planY(f.stbd(dx, dz))};
    }

    public int[] profileCell(int packed) {
        ShipFrame f = shipFrame();
        return new int[]{frame.x(f.fore(PackedPos.dx(packed), PackedPos.dz(packed))), frame.profileY(PackedPos.dy(packed))};
    }

    public int packAbsolute(long blockPos) {
        int dx = BlockPosBits.x(blockPos) - BlockPosBits.x(origin);
        int dy = BlockPosBits.y(blockPos) - BlockPosBits.y(origin);
        int dz = BlockPosBits.z(blockPos) - BlockPosBits.z(origin);
        return PackedPos.fits(dx, dy, dz) ? PackedPos.pack(dx, dy, dz) : -1;
    }

    public static final class BlockPosBits {
        private BlockPosBits() {
        }

        public static int x(long packed) {
            return (int) (packed >> 38);
        }

        public static int y(long packed) {
            return (int) (packed << 52 >> 52);
        }

        public static int z(long packed) {
            return (int) (packed << 26 >> 38);
        }

        public static long of(int x, int y, int z) {
            return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
        }
    }
}
