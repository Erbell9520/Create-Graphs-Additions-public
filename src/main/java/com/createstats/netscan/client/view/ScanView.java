package com.createstats.netscan.client.view;

import com.createstats.netscan.client.ScanContext;
import com.createstats.statsmk3.ui.Easing;
import com.createstats.statsmk3.ui.Motion;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class ScanView {
    protected final ScanContext ctx;
    protected Rect area = Rect.EMPTY;

    protected double mouseX;
    protected double mouseY;
    private float entered;

    protected ScanView(ScanContext ctx) {
        this.ctx = ctx;
    }

    public abstract String id();

    public abstract String label();

    public abstract void drawIcon(PanelSurface c, float cx, float cy, int color);

    public void layout(Rect area) {
        this.area = area;
        build();
    }

    protected void build() {
    }

    public void onEnter() {
        this.entered = 0.0f;
    }

    public void hover(double mx, double my) {
        this.mouseX = mx;
        this.mouseY = my;
    }

    public void update(float dt) {
        this.entered = Math.min(1.0f, this.entered + dt);
    }

    public final void draw(PanelSurface c) {
        final float t = Motion.reducedMotion() ? 1.0f
                : Easing.expoOut(Math.min(1.0f, this.entered / Motion.VIEW_TRANSITION));
        if (t >= 1.0f) {
            drawContent(c);
            return;
        }
        c.pushPose();
        c.translate(0.0f, (1.0f - t) * Motion.VIEW_RISE);
        c.setAlpha(t);
        drawContent(c);
        c.setAlpha(1.0f);
        c.popPose();
    }

    protected abstract void drawContent(PanelSurface c);

    public boolean mouseClicked(double mx, double my, int button) {
        return false;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        return false;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    protected static Rect card(PanelSurface c, Rect r, String title) {
        c.card(r, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        c.text(Text.track(title), r.x + Tokens.S3, r.y + 7.0f, Tokens.INK_3, Text.MICRO);
        return r.inset(Tokens.S3, Tokens.S4 + 6.0f, Tokens.S3, Tokens.S3);
    }

    protected static void stat(PanelSurface c, Rect r, String label, String value, int color) {
        c.card(r, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        c.text(Text.track(label), r.x + Tokens.S3, r.y + 7.0f, Tokens.INK_3, Text.MICRO);
        c.text(Text.truncate(c, value, r.w - Tokens.S3 * 2.0f, Text.SUB),
                r.x + Tokens.S3, r.y + 20.0f, color, Text.SUB);
    }

    // locked = named. "needs an Industrial scanner" is an upgrade path, an empty panel is a bug report
    protected static void locked(PanelSurface c, Rect r, String feature, int detailLevel) {
        c.card(r, Tokens.R_CARD, Tokens.alpha(Tokens.SURFACE, 0.6f), Tokens.LINE);
        final String line = feature + " needs " + article(ScanContext.unlockedBy(detailLevel))
                + " " + ScanContext.unlockedBy(detailLevel) + " scanner";
        c.textCenterV(Text.truncate(c, line, r.w - Tokens.S4, Text.META),
                r.centerX(), r.centerY(), Tokens.INK_3, Text.META);
        Shapes.roundedRectOutline(c, r, Tokens.R_CARD, 1.0f, Tokens.alpha(Tokens.LINE_HI, 0.5f));
    }

    private static String article(String word) {
        return "AEIOU".indexOf(Character.toUpperCase(word.charAt(0))) >= 0 ? "an" : "a";
    }
}
