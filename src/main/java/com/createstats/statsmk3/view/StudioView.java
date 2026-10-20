package com.createstats.statsmk3.view;

import java.util.ArrayList;
import java.util.List;

import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Motion;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Widget;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class StudioView {
    protected final StudioContext ctx;
    protected Rect area = Rect.EMPTY;
    protected final List<Widget> widgets = new ArrayList<>();

    private float enterElapsed = 1.0f;

    protected StudioView(StudioContext ctx) {
        this.ctx = ctx;
    }

    public abstract String id();

    public abstract String label();

    public abstract void drawIcon(PanelSurface c, float cx, float cy, int color);

    public void layout(Rect area) {
        this.area = area;
        this.widgets.clear();
        build();
    }

    public boolean usesDock() {
        return true;
    }

    // digits jump tabs, so a focused text field captures them. found the first time someone typed "Kiln 2"
    public boolean capturesTyping() {
        return false;
    }

    protected abstract void build();

    protected abstract void drawContent(PanelSurface c);

    public void onEnter() {
        this.enterElapsed = 0.0f;
    }

    public void settle() {
        this.enterElapsed = 1.0f;
    }

    public void update(float dt) {
        this.enterElapsed += dt;
        for (final Widget w : this.widgets) {
            w.update(dt);
        }
    }

    protected float enterProgress(int rowIndex) {
        return Motion.stagger(this.enterElapsed, rowIndex);
    }

    public void draw(PanelSurface c) {
        final float p = Motion.stagger(this.enterElapsed, 0);
        c.setAlpha(p);
        if (p < 0.999f) {
            c.pushPose();
            c.translate(0.0f, (1.0f - p) * Motion.VIEW_RISE);
            drawContent(c);
            drawWidgets(c);
            c.popPose();
        } else {
            drawContent(c);
            drawWidgets(c);
        }
        c.setAlpha(1.0f);
    }

    private void drawWidgets(PanelSurface c) {
        for (final Widget w : this.widgets) {
            w.draw(c);
        }
    }

    public boolean mouseMoved(double mx, double my) {
        boolean any = false;
        for (final Widget w : this.widgets) {
            any |= w.mouseMoved(mx, my);
        }
        return any;
    }

    public boolean mouseDown(double mx, double my, int button) {
        for (int i = this.widgets.size() - 1; i >= 0; i--) {
            if (this.widgets.get(i).mouseDown(mx, my, button)) {
                return true;
            }
        }
        return onMouseDown(mx, my, button);
    }

    public boolean mouseUp(double mx, double my, int button) {
        boolean handled = false;
        for (int i = this.widgets.size() - 1; i >= 0; i--) {
            handled |= this.widgets.get(i).mouseUp(mx, my, button);
        }
        return handled;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        for (int i = this.widgets.size() - 1; i >= 0; i--) {
            if (this.widgets.get(i).mouseDragged(mx, my, button, dx, dy)) {
                return true;
            }
        }
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        for (int i = this.widgets.size() - 1; i >= 0; i--) {
            if (this.widgets.get(i).mouseScrolled(mx, my, amount)) {
                return true;
            }
        }
        return onScroll(mx, my, amount);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (final Widget w : this.widgets) {
            if (w.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    public boolean charTyped(char c, int modifiers) {
        for (final Widget w : this.widgets) {
            if (w.charTyped(c, modifiers)) {
                return true;
            }
        }
        return false;
    }

    protected boolean onMouseDown(double mx, double my, int button) {
        return false;
    }

    protected boolean onScroll(double mx, double my, double amount) {
        return false;
    }
}
