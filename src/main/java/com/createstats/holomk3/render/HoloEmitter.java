package com.createstats.holomk3.render;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class HoloEmitter {
    public static final float DECK_Y = 9.5f / 16.0f;

    private static final float LENS_Y = 10.2f / 16.0f;
    private static final float LENS_R = 3.0f / 16.0f;

    private static final float POST_XZ = 6.0f / 16.0f;
    private static final float POST_TIP_Y = 15.0f / 16.0f;

    private HoloEmitter() {
    }

    public static void paint(HoloSink s, float halfW, float halfD, float floorY, boolean live,
                             float pulse, float glow, float seconds) {
        final float hw = Math.max(0.008f, s.pixel() * 0.8f);

        final float idle = 0.45f + 0.10f * (float) Math.sin(seconds * 1.6f);
        final float lensGlow = glow * (live ? Math.min(1.0f, 0.75f + 0.45f * pulse) : idle * 0.55f);

        HoloDraw.disc(s, DECK_Y + 0.004f, LENS_R * 2.1f, 12,
                HoloDraw.fade(HoloPalette.EMITTER, 0.16f * lensGlow),
                HoloDraw.alpha(HoloPalette.EMITTER, 0.0f));
        HoloDraw.disc(s, LENS_Y, LENS_R, 12,
                HoloDraw.fade(HoloPalette.EMITTER, 0.95f * lensGlow),
                HoloDraw.fade(HoloPalette.EMITTER, 0.10f * lensGlow));

        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                HoloDraw.point(s, sx * POST_XZ, POST_TIP_Y, sz * POST_XZ, 0.035f,
                        HoloDraw.fade(HoloPalette.EMITTER, 0.9f * lensGlow));
            }
        }

        if (!live) {
            return;
        }

        HoloDraw.cone(s, LENS_Y, LENS_R * 0.9f, floorY, Math.min(halfW, halfD) * 0.94f, 14,
                HoloDraw.fade(HoloPalette.EMITTER, 0.13f * glow),
                HoloDraw.fade(HoloPalette.EMITTER, 0.0f));

        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                HoloDraw.beam(s, sx * POST_XZ, POST_TIP_Y, sz * POST_XZ,
                        sx * halfW, floorY, sz * halfD, hw,
                        HoloDraw.fade(HoloPalette.EMITTER, 0.7f * glow),
                        HoloDraw.fade(HoloPalette.EMITTER, 0.22f * glow));
            }
        }
    }
}
