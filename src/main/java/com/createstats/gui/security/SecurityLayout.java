package com.createstats.gui.security;

import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Tokens;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// copied from StudioLayout including its scars
@OnlyIn(Dist.CLIENT)
public final class SecurityLayout {
    public static final float TITLEBAR_H = 30.0f;

    public static final float RAIL_W = 152.0f;

    public static final float RAIL_W_ICON = 44.0f;
    public static final float RAIL_ITEM_H = 30.0f;

    public static final float RAIL_GROUP_H = 18.0f;

    public static final float RAIL_ITEM_MIN = 20.0f;

    public static final float RAIL_GROUP_TIGHT = 12.0f;

    public static final float RAIL_GROUP_ICON = 6.0f;

    public static final float DOCK_W = 300.0f;

    public static final float DOCK_MIN_W = 168.0f;

    public static final float MIN_WORK_W = 360.0f;

    public static final float COLUMNS_MIN_W = 520.0f;

    public static final float DOCK_MIN_BODY_W = RAIL_W_ICON + MIN_WORK_W + DOCK_MIN_W;

    public final float width;
    public final float height;
    public final Rect root;
    public final Rect rail;
    public final Rect work;

    public final Rect wideWork;

    public final Rect dock;
    public final boolean railLabels;

    public final float railPitch;
    public final float railGroupH;

    public final float railContent;

    public final boolean railScrolls;

    private SecurityLayout(float width, float height, int items, int groups) {
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

        final float avail = railAvail(this.rail);
        final float full = groups * RAIL_GROUP_H + items * RAIL_ITEM_H;
        if (items <= 0 || full <= avail) {
            this.railPitch = RAIL_ITEM_H;
            this.railGroupH = RAIL_GROUP_H;
        } else {
            final float floor = labels ? RAIL_GROUP_TIGHT : RAIL_GROUP_ICON;
            final float captions = groups <= 0 ? floor
                    : (float) Math.floor((avail - items * RAIL_ITEM_H) / groups);
            if (captions >= floor) {
                this.railGroupH = Math.min(RAIL_GROUP_H, captions);
                this.railPitch = RAIL_ITEM_H;
            } else {
                this.railGroupH = floor;
                this.railPitch = Math.max(RAIL_ITEM_MIN, Math.min(RAIL_ITEM_H,
                        (float) Math.floor((avail - groups * this.railGroupH) / items)));
            }
        }
        this.railContent = groups * this.railGroupH + items * this.railPitch;
        this.railScrolls = items > 0 && this.railContent > avail;
    }

    public static SecurityLayout of(float width, float height) {
        return new SecurityLayout(width, height, 0, 0);
    }

    public static SecurityLayout of(float width, float height, int items, int groups) {
        return new SecurityLayout(width, height, items, groups);
    }

    private static float railAvail(Rect rail) {
        return Math.max(0.0f, rail.h - Tokens.S3 - Tokens.S2);
    }

    public Rect railViewport() {
        return new Rect(this.rail.x, this.rail.y, this.rail.w, Math.max(0.0f, this.rail.h - Tokens.S2));
    }

    public float railScrollMax() {
        return this.railScrolls ? Math.max(0.0f, this.railContent - railAvail(this.rail)) : 0.0f;
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

    public static final float WIZARD_DONE_W = 110.0f;
    public static final float WIZARD_DONE_H = 22.0f;

    public static Rect wizardDone(Rect area) {
        final float w = Math.min(WIZARD_DONE_W, Math.max(0.0f, area.w));
        return new Rect(area.right() - w, area.bottom() - WIZARD_DONE_H, w, WIZARD_DONE_H);
    }

    public boolean foldedRows() {
        return viewArea(true).w < COLUMNS_MIN_W;
    }

    public Rect dockInner() {
        return this.dock.isEmpty() ? Rect.EMPTY : this.dock.inset(Tokens.S3);
    }

    public float railTop() {
        return this.rail.y + Tokens.S3;
    }

    public Rect railItem(int index, int groupsBefore) {
        final float pad = this.railLabels ? Tokens.S3 : Tokens.S1;
        final float y = railTop() + groupsBefore * this.railGroupH + index * this.railPitch;
        return new Rect(this.rail.x + pad, y, this.rail.w - pad * 2.0f, this.railPitch - 2.0f);
    }

    public Rect railGroup(int index, int groupsBefore) {
        final float pad = this.railLabels ? Tokens.S3 : Tokens.S1;
        final float y = railTop() + groupsBefore * this.railGroupH + index * this.railPitch;
        return new Rect(this.rail.x + pad, y, this.rail.w - pad * 2.0f, this.railGroupH);
    }
}
