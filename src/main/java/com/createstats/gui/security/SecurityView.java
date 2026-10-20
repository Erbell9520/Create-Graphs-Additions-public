package com.createstats.gui.security;

import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public abstract class SecurityView {
    protected record Hit(Rect bounds, Rect clip, Runnable action) {
    }

    protected final SecurityContext ctx;
    protected Rect area = Rect.EMPTY;

    private final List<Hit> hits = new ArrayList<>();

    private Rect hitClip;

    protected SecurityView(SecurityContext ctx) {
        this.ctx = ctx;
    }

    public abstract String id();

    public abstract String label();

    public String group() {
        return "control";
    }

    public abstract void drawIcon(PanelSurface s, float cx, float cy, int color);

    public int badge() {
        return -1;
    }

    public boolean usesDock() {
        return true;
    }

    public void layout(Rect area) {
        this.area = area;
    }

    public abstract void draw(PanelSurface s, float mx, float my);

    public void drawDock(PanelSurface s, Rect r, float mx, float my) {
    }

    public final void beginFrame() {
        this.hits.clear();
        this.hitClip = null;
    }

    protected final void pushHitClip(Rect pane) {
        this.hitClip = pane;
    }

    protected final void popHitClip() {
        this.hitClip = null;
    }

    protected final void hit(Rect bounds, Runnable action) {
        this.hits.add(new Hit(bounds, this.hitClip, action));
    }

    protected final boolean hot(Rect r, float mx, float my) {
        return r.contains(mx, my);
    }

    public boolean click(double mx, double my) {
        for (int i = this.hits.size() - 1; i >= 0; i--) {
            final Hit h = this.hits.get(i);
            if (h.bounds().contains(mx, my)
                    && (h.clip() == null || h.clip().contains(mx, my))) {
                h.action().run();
                return true;
            }
        }
        return false;
    }

    public boolean scroll(double mx, double my, double amount) {
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    public boolean charTyped(char c, int modifiers) {
        return false;
    }

    public void update(float dt) {
    }

    public void onEnter() {
    }
}
