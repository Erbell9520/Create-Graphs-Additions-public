package com.createstats.battle.core;

public final class Projection {
    public static final byte EMPTY = 0;
    public static final byte LIFT = 1;
    public static final byte STRUCTURE = 2;
    public static final byte LOST = 3;
    public static final byte RECENT = 4;
    public static final byte BURNING = 5;

    public static final int FACE_W = 64;
    public static final int FACE_H = 24;

    public static final int MAX_W = 256;
    public static final int MAX_H = 128;

    public record Frame(int minFore, int minStbd, int maxUp, int length, int beam, int height, int k) {
        public int planW() {
            return ceilDiv(length, k);
        }

        public int planH() {
            return ceilDiv(beam, k);
        }

        public int profileW() {
            return planW();
        }

        public int profileH() {
            return ceilDiv(height, k);
        }

        public int x(int fore) {
            return (fore - minFore) / k;
        }

        public int planY(int stbd) {
            return (stbd - minStbd) / k;
        }

        public int profileY(int up) {
            return (maxUp - up) / k;
        }

        public boolean inside(int fore, int stbd, int up) {
            return fore >= minFore && fore < minFore + length && stbd >= minStbd && stbd < minStbd + beam
                    && up <= maxUp && up > maxUp - height;
        }
    }

    private Projection() {
    }

    public static Frame frame(int minFore, int maxFore, int minStbd, int maxStbd, int minUp, int maxUp, int maxW, int maxH) {
        if (maxFore < minFore || maxStbd < minStbd || maxUp < minUp) return new Frame(0, 0, 0, 1, 1, 1, 1);
        int length = maxFore - minFore + 1;
        int beam = maxStbd - minStbd + 1;
        int height = maxUp - minUp + 1;
        int k = Math.max(1, Math.max(ceilDiv(length, maxW), Math.max(ceilDiv(beam, maxH), ceilDiv(height, maxH))));
        return new Frame(minFore, minStbd, maxUp, length, beam, height, k);
    }

    public static void put(byte[] cells, int w, int x, int y, byte state) {
        if (x < 0 || y < 0 || x >= w) return;
        int i = y * w + x;
        if (i >= cells.length) return;
        if (state > cells[i]) cells[i] = state;
    }

    public static byte[] pool(byte[] cells, int w, int h, int f) {
        if (f <= 1) return cells.clone();
        int pw = ceilDiv(w, f);
        int ph = ceilDiv(h, f);
        byte[] out = new byte[pw * ph];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                put(out, pw, x / f, y / f, cells[y * w + x]);
            }
        }
        return out;
    }

    public static byte[] face(byte[] plan, int planW, int planH) {
        byte[] out = new byte[FACE_W * FACE_H];
        if (planW <= 0 || planH <= 0) return out;
        int up = 1;
        int pool = 1;
        if (planW <= FACE_W && planH <= FACE_H) {
            up = Math.max(1, Math.min(FACE_W / planW, FACE_H / planH));
        } else {
            pool = Math.max(ceilDiv(planW, FACE_W), ceilDiv(planH, FACE_H));
        }
        byte[] src = pool > 1 ? pool(plan, planW, planH, pool) : plan;
        int sw = ceilDiv(planW, pool);
        int sh = ceilDiv(planH, pool);
        int ox = (FACE_W - sw * up) / 2;
        int oy = (FACE_H - sh * up) / 2;
        for (int y = 0; y < sh; y++) {
            for (int x = 0; x < sw; x++) {
                byte s = faceState(src[y * sw + x]);
                if (s == EMPTY) continue;
                for (int j = 0; j < up; j++) {
                    for (int i = 0; i < up; i++) {
                        put(out, FACE_W, ox + x * up + i, oy + y * up + j, s);
                    }
                }
            }
        }
        return out;
    }

    static byte faceState(byte s) {
        return s >= LOST ? LOST : s;
    }

    public static byte[] pack2(byte[] cells) {
        byte[] out = new byte[ceilDiv(cells.length, 4)];
        for (int i = 0; i < cells.length; i++) {
            out[i >> 2] |= (byte) ((cells[i] & 3) << ((i & 3) * 2));
        }
        return out;
    }

    public static byte[] unpack2(byte[] packed, int count) {
        byte[] out = new byte[count];
        for (int i = 0; i < count && (i >> 2) < packed.length; i++) {
            out[i] = (byte) ((packed[i >> 2] >> ((i & 3) * 2)) & 3);
        }
        return out;
    }

    public static int ceilDiv(int a, int b) {
        return b <= 0 ? a : (a + b - 1) / b;
    }
}
