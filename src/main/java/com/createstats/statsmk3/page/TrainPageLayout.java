package com.createstats.statsmk3.page;

import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// pure layout so it can be checked without launching
@OnlyIn(Dist.CLIENT)
public final class TrainPageLayout {
    private TrainPageLayout() {
    }

    public static final String[] MODES =
            {"Network", "Diagram", "Board", "Fleet", "Service", "Alerts"};

    public static final float HEADER_H = 44.0f;

    public static final float KPI_H = 46.0f;

    public static final float KPI_H_TIGHT = 22.0f;

    public static final float TIGHT_BELOW = 340.0f;

    public static final float CONTROL_H = 26.0f;

    public static final float TWO_PANE_MIN = 520.0f;

    public static final float INSPECTOR_MIN = 660.0f;
    public static final float INSPECTOR_W = 208.0f;

    public static final float CONTROL_CHIPS_W = 190.0f;

    public record Density(float step, float rowH, float listRowH, float kpiH, float inspectorW,
                          float gap, float primary, float secondary) {
    }

    public static final float ROOMY_W = 900.0f;
    public static final float ROOMY_H = 480.0f;

    public static final float WIDE_W = 1200.0f;
    public static final float WIDE_H = 640.0f;

    public static Density density(Rect area) {
        if (area.w >= WIDE_W && area.h >= WIDE_H) {
            return new Density(1.30f, 50.0f, 34.0f, 60.0f, 272.0f, Tokens.S3, 1.25f, 1.0f);
        }
        if (area.w >= ROOMY_W && area.h >= ROOMY_H) {
            return new Density(1.15f, 44.0f, 30.0f, 53.0f, 240.0f, Tokens.S2 + 2.0f, 1.125f, 0.875f);
        }
        return new Density(1.0f, 38.0f, 26.0f, KPI_H, INSPECTOR_W, Tokens.S2, 1.0f, 0.75f);
    }

    public record Frame(Rect kpi, Rect control, Rect modes, Rect content, Rect list, Rect detail,
                        Density density) {
        public boolean singlePane() {
            return this.detail.isEmpty();
        }
    }

    public static float modesWidth(Rect control, Density d) {
        return Math.min(Math.min(384.0f * d.step(),
                Math.max(252.0f, control.w - CONTROL_CHIPS_W * d.step())), control.w);
    }

    public static Frame of(Rect area) {
        final Density d = density(area);
        final Rect body = area.inset(0.0f, HEADER_H, 0.0f, 0.0f);
        final float kpiH = body.h < TIGHT_BELOW ? KPI_H_TIGHT : d.kpiH();
        final Rect kpi = body.top(kpiH);

        final float controlH = body.h < TIGHT_BELOW ? CONTROL_H : CONTROL_H * d.step();
        final Rect afterKpi = body.below(kpiH + d.gap());
        final Rect control = afterKpi.top(controlH);
        final Rect modes = new Rect(afterKpi.x, afterKpi.y,
                modesWidth(new Rect(afterKpi.x, afterKpi.y, afterKpi.w, controlH), d), controlH);

        final Rect content = afterKpi.below(controlH + d.gap());
        final float listW = content.w >= TWO_PANE_MIN
                ? Math.max(190.0f, Math.min(content.w * 0.36f, 300.0f * d.step()))
                : content.w;
        final Rect list = content.left(listW);
        final Rect detail = listW >= content.w
                ? Rect.EMPTY : content.afterLeft(listW + d.gap());
        return new Frame(kpi, control, modes, content, list, detail, d);
    }

    public static float modeSegmentWidth(Rect modes) {
        return (modes.w - 6.0f) / Math.max(1, MODES.length);
    }

    public static int kpiColumns(Rect kpi) {
        return kpi.w >= 620.0f ? 5 : kpi.w >= 420.0f ? 4 : 3;
    }

    public static boolean hasInspector(Rect content) {
        return content.w >= INSPECTOR_MIN;
    }

    public static Rect mapArea(Rect content, Density d) {
        return hasInspector(content)
                ? content.left(content.w - d.inspectorW() - d.gap()) : content;
    }

    public static Rect inspectorArea(Rect content, Density d) {
        return hasInspector(content)
                ? content.afterLeft(mapArea(content, d).w + d.gap()) : Rect.EMPTY;
    }

    public static Rect overlay(Rect area, Rect control, Rect content, float width, float height) {
        final float w = Math.min(width, Math.max(80.0f, area.w - 20.0f));
        final float h = Math.min(height, Math.max(60.0f, content.h));
        return new Rect(area.right() - w, control.bottom() + 4.0f, w, h);
    }
}
