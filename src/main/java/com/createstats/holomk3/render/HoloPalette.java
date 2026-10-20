package com.createstats.holomk3.render;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.createstats.statsmk3.ui.Tokens;

// additive, alpha is intensity, black is invisible
@OnlyIn(Dist.CLIENT)
public final class HoloPalette {
    private HoloPalette() {
    }

    public static final int STRUCTURE = 0xFF6FC7E4;

    public static final int STRUCTURE_DIM = 0xFF3E7C92;

    public static final int LABEL = 0xFFBFE6F2;

    public static final int EMPHASIS = 0xFFFFF4E2;

    public static final int MARK = 0xFFFF7FA8;

    public static final int EMITTER = 0xFF8FE3FF;

    public static final int GOOD = 0xFF54E3AE;
    public static final int CAUTION = 0xFFFFC94A;
    public static final int CRITICAL = 0xFFFF7A66;

    public static int series(String channelId) {
        return lift(Tokens.seriesFor(channelId), 0.30f);
    }

    public static int lift(int argb, float t) {
        return HoloDraw.mix(argb, (argb & 0xFF000000) | 0x00FFFFFF, t);
    }

    public static int status(float frac) {
        if (frac >= 0.85f) {
            return CRITICAL;
        }
        return frac >= 0.70f ? CAUTION : GOOD;
    }
}
