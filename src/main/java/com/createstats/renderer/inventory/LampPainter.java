package com.createstats.renderer.inventory;

import net.minecraft.network.chat.Component;

public final class LampPainter {
    private LampPainter() {
    }

    public static final int TILE_W = 49;
    public static final int TILE_H = 26;
    public static final int CELL_W = 64;
    public static final int CELL_H = 32;
    public static final int TEX_W = CELL_W * 4;
    public static final int TEX_H = CELL_H * 2;
    public static final int MAX_LEVEL = 4;

    public static final int[] FILMS = {0xFFFFB040, 0xFFE6ECF2, 0xFFFF5440, 0xFF60E878};

    private static int mul(int rgb, double f) {
        final int r = (int) Math.max(0, Math.min(255, Math.rint(((rgb >> 16) & 255) * f)));
        final int g = (int) Math.max(0, Math.min(255, Math.rint(((rgb >> 8) & 255) * f)));
        final int b = (int) Math.max(0, Math.min(255, Math.rint((rgb & 255) * f)));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static int unlitLegend(int film) {
        final int r = (film >> 16) & 255;
        final int g = (film >> 8) & 255;
        final int b = film & 255;
        final int lr = (int) (r + (255 - r) * 0.35);
        final int lg = (int) (g + (255 - g) * 0.35);
        final int lb = (int) (b + (255 - b) * 0.35);
        return mul((lr << 16) | (lg << 8) | lb, 0.78);
    }

    private static String word(int i) {
        return FacePainter.glassSafe(switch (i) {
            case 0 -> Component.translatable("create_stats.inventory_display.lamp.low").getString();
            case 1 -> Component.translatable("create_stats.inventory_display.lamp.full").getString();
            case 2 -> Component.translatable("create_stats.inventory_display.lamp.stall").getString();
            default -> Component.translatable("create_stats.inventory_display.lamp.power").getString();
        });
    }

    public static int[] paint() {
        final int[] out = new int[TEX_W * TEX_H];
        for (int i = 0; i < 4; i++) {
            for (int row = 0; row < 2; row++) {
                final GlassCanvas t = tile(i, row == 1, word(i));
                MipChain.blit(MipChain.pad(t.px, TILE_W, TILE_H, CELL_W, CELL_H), CELL_W, CELL_H, out, TEX_W,
                        i * CELL_W, row * CELL_H);
            }
        }
        return out;
    }

    public static GlassCanvas tile(int i, boolean lit, String legend) {
        final GlassCanvas c = new GlassCanvas(TILE_W, TILE_H);
        final int film = FILMS[i];
        for (int y = 0; y < TILE_H; y++) {
            for (int x = 0; x < TILE_W; x++) {
                int col;
                if (lit) {
                    final double d = Math.hypot((x - TILE_W / 2.0) / (TILE_W / 2.0), (y - TILE_H / 2.0) / (TILE_H / 2.0));
                    col = mul(film, 1.08 - 0.30 * d);
                } else {
                    double k = 0.17 + 0.03 * (((x / 3 + y / 3) % 2 != 0) ? 1 : 0);
                    if (y <= 2) {
                        k *= 1.18;
                    }
                    col = mul(film, k);
                }
                final int edge = Math.min(Math.min(x, y), Math.min(TILE_W - 1 - x, TILE_H - 1 - y));
                if (edge == 0) {
                    col = mul(col, 0.55);
                }
                c.put(x, y, col);
            }
        }
        final int ink = lit ? mul(film, 0.18) : unlitLegend(film);
        c.text(legend, (TILE_W - GlassCanvas.width(legend, 1)) / 2, (TILE_H - 7) / 2, ink, 1);
        return c;
    }

    public static float[] uv(int i, boolean lit) {
        final float u0 = i * CELL_W / (float) TEX_W;
        final float v0 = (lit ? CELL_H : 0) / (float) TEX_H;
        return new float[]{u0, v0, u0 + TILE_W / (float) TEX_W, v0 + TILE_H / (float) TEX_H};
    }
}
