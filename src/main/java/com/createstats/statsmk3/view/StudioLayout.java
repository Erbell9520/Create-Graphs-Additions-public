package com.createstats.statsmk3.view;

import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class StudioLayout {
    public static final float TITLEBAR_H = 30.0f;

    public static final float RAIL_W = 152.0f;

    public static final float RAIL_W_ICON = 44.0f;

    public static final float DOCK_W = 300.0f;

    public static final float DOCK_MIN_W = 168.0f;

    public static final float MIN_WORK_W = 360.0f;
    public static final float RAIL_ITEM_H = 30.0f;

    public static final float DOCK_MIN_BODY_W = RAIL_W_ICON + MIN_WORK_W + DOCK_MIN_W;

    private static final float DOCK_FACTS_H = 16.0f + 13.0f * 4.0f + Tokens.S3;

    public static final float MIN_BEZEL_H = 26.0f;

    public final float width;
    public final float height;
    public final Rect root;
    public final Rect rail;

    public final Rect work;

    public final Rect wideWork;

    public final Rect dock;

    public final boolean railLabels;

    private StudioLayout(float width, float height) {
        this.width = width;
        this.height = height;

        final float margin = Math.max(16.0f, Math.min(width, height) / 14.0f);
        this.root = new Rect(margin, margin, width - margin * 2.0f, height - margin * 2.0f);

        final Rect body = this.root.inset(0.0f, TITLEBAR_H, 0.0f, 0.0f);

        final boolean labels = body.w - RAIL_W - MIN_WORK_W >= DOCK_MIN_W
                || body.w - RAIL_W_ICON - MIN_WORK_W < DOCK_MIN_W;
        final float railW = labels ? RAIL_W : RAIL_W_ICON;
        this.railLabels = labels;

        final float spare = body.w - railW - MIN_WORK_W;
        final boolean showDock = spare >= DOCK_MIN_W;
        final float dockW = showDock ? Math.min(DOCK_W, spare) : 0.0f;

        this.rail = body.left(railW);
        this.work = new Rect(body.x + railW, body.y, body.w - railW - dockW, body.h);
        this.wideWork = new Rect(body.x + railW, body.y, body.w - railW, body.h);
        this.dock = showDock ? new Rect(this.work.right(), body.y, dockW, body.h) : Rect.EMPTY;
    }

    public static StudioLayout of(float width, float height) {
        return new StudioLayout(width, height);
    }

    public boolean hasDock() {
        return !this.dock.isEmpty();
    }

    public Rect titlebar() {
        return this.root.top(TITLEBAR_H);
    }

    public Rect viewArea(boolean usesDock) {
        return (usesDock ? this.work : this.wideWork)
                .inset(Tokens.S5, Tokens.S4, Tokens.S5, Tokens.S4);
    }

    public float railTop() {
        return this.rail.y + Tokens.S3 + 16.0f;
    }

    public Rect railItem(int index) {
        final float pad = this.railLabels ? Tokens.S3 : Tokens.S1;
        return new Rect(this.rail.x + pad, railTop() + index * RAIL_ITEM_H,
                this.rail.w - pad * 2.0f, RAIL_ITEM_H - 2.0f);
    }

    public int railCapacity() {
        return Math.max(1, (int) Math.floor((this.rail.bottom() - railTop()) / RAIL_ITEM_H));
    }

    public Rect dockInner() {
        return this.dock.isEmpty() ? Rect.EMPTY : this.dock.inset(Tokens.S3);
    }

    public Rect bezelFrame() {
        final Rect inner = dockInner();
        if (inner.isEmpty()) {
            return Rect.EMPTY;
        }
        final float top = inner.y + 16.0f;
        return new Rect(inner.x, top, inner.w, Math.max(0.0f, inner.bottom() - top - DOCK_FACTS_H));
    }

    public boolean hasPreview() {
        return bezelFrame().h >= MIN_BEZEL_H;
    }

    public float dockFactsTop() {
        final Rect frame = bezelFrame();
        return frame.isEmpty()
                ? dockInner().y + 16.0f + Text.lineHeight(Text.MICRO) + Tokens.S3
                : frame.bottom() + Tokens.S3;
    }

    public static Rect fitAspect(Rect frame, float aspect) {
        if (frame.isEmpty()) {
            return Rect.EMPTY;
        }
        float w = frame.w;
        float h = w / Math.max(0.05f, aspect);
        if (h > frame.h) {
            h = frame.h;
            w = h * aspect;
        }
        return new Rect(frame.centerX() - w * 0.5f, frame.y, w, h);
    }
}
