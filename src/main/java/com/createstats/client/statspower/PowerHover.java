package com.createstats.client.statspower;

import java.util.ArrayList;
import java.util.List;

import com.createstats.gui.pdu.PduRect;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class PowerHover {
    private final int mx;
    private final int my;
    private final List<String> lines = new ArrayList<>();

    public PowerHover(int mx, int my) {
        this.mx = mx;
        this.my = my;
    }

    public int mx() {
        return this.mx;
    }

    public int my() {
        return this.my;
    }

    public boolean over(PduRect r) {
        return r != null && r.contains(this.mx, this.my);
    }

    public boolean over(int x, int y, int w, int h) {
        return this.mx >= x && this.mx < x + w && this.my >= y && this.my < y + h;
    }

    public void set(String... text) {
        if (!this.lines.isEmpty()) {
            return;
        }
        for (final String line : text) {
            if (line != null) {
                this.lines.add(line);
            }
        }
    }

    public boolean tip(PduRect r, String... text) {
        if (!over(r)) {
            return false;
        }
        set(text);
        return true;
    }

    public boolean tip(int x, int y, int w, int h, String... text) {
        if (!over(x, y, w, h)) {
            return false;
        }
        set(text);
        return true;
    }

    public boolean has() {
        return !this.lines.isEmpty();
    }

    public List<String> lines() {
        return this.lines;
    }
}
