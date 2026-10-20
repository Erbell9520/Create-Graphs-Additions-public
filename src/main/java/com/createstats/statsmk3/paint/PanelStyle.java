package com.createstats.statsmk3.paint;

import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class PanelStyle {
    public int ground = 0xFF05070A;

    public int accent = Tokens.ACCENT;

    public boolean grid = true;

    public boolean glass = true;

    public float glow = 0.62f;

    public float density = 1.0f;

    public float faceScale = com.createstats.statsmk3.Mk3Settings.DEFAULT_FACE_SCALE;

    public PanelStyle copy() {
        final PanelStyle s = new PanelStyle();
        s.ground = this.ground;
        s.accent = this.accent;
        s.grid = this.grid;
        s.glass = this.glass;
        s.glow = this.glow;
        s.density = this.density;
        s.faceScale = this.faceScale;
        return s;
    }

    public float pad(float base) {
        return base * this.density;
    }
}
