package com.createstats.statsmk3.paint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.createstats.netscan.scan.ScanGraph;
import com.createstats.netscan.scan.ScanLayout;
import com.createstats.netscan.scan.ScanLayoutMode;
import com.createstats.netscan.scan.ScanNode;
import com.createstats.statsmk3.page.TopoArt;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class TopologyFacePainter {
    private TopologyFacePainter() {
    }

    private static final float ROW_UNIT = 15.0f;

    public static void paint(PanelSurface s, Rect area, PanelModel model, Lod lod) {
        final PanelStyle style = model.style;
        Shapes.rect(s, area, style.ground);
        s.advanceLayer();

        final List<TopoArt.Reading> surveys = ranked(model.topology);
        final float scale = faceScale(area);
        final float pad = 8.0f * scale;

        if (surveys.isEmpty()) {
            paintNoData(s, area, "No Topology Scanner linked",
                    "Point a Display Link at a scanner and target this panel");
            return;
        }

        final TopoArt.Reading worst = surveys.get(0);

        if (lod.isGlyph()) {
            paintGlyph(s, area, worst, surveys.size());
            return;
        }

        final boolean roomy = area.h - pad * 2.0f >= 130.0f * scale && area.w - pad * 2.0f >= 150.0f * scale;
        if (!roomy || !lod.showsLabels()) {
            paintSolo(s, area, worst, surveys.size(), scale);
            return;
        }

        paintBoard(s, area, surveys, worst, model.time, scale, pad, lod);
    }

    private static void paintBoard(PanelSurface s, Rect area, List<TopoArt.Reading> surveys,
                                   TopoArt.Reading worst, float time, float scale, float pad, Lod lod) {
        float y = area.y + pad;
        if (lod.showsHeader()) {
            final String title = surveys.size() > 1 ? surveys.size() + " SURVEYS" : "TOPOLOGY";
            final float titleScale = Text.fitScale(s, title, area.w * 0.5f, area.h * 0.16f,
                    Text.SUB * scale, Text.MICRO);
            s.text(title, area.x + pad, y, Tokens.fade(Tokens.INK, lod.headerAlpha()), titleScale);
            final String right = worst.live()
                    ? String.format(Locale.ROOT, "%.0f%%  %s", worst.pct(), stateWord(worst))
                    : worst.excuse();
            s.textRight(right, area.right() - pad, y,
                    Tokens.fade(TopoArt.statusColor(worst), lod.headerAlpha()), Text.META * scale);
            y += Text.lineHeight(titleScale) + 3.0f * scale;
            Shapes.rect(s, area.x + pad, y, area.w - pad * 2.0f, 1.0f,
                    Tokens.fade(Tokens.LINE_HI, lod.headerAlpha()));
            y += 4.0f * scale;
        }

        final Rect bar = new Rect(area.x + pad, y, area.w - pad * 2.0f, 9.0f * scale);
        if (surveys.size() > 1) {
            fleetBar(s, bar, surveys, scale);
        } else {
            loadBar(s, bar, worst, scale);
        }
        y = bar.bottom() + 6.0f * scale;

        final ScanGraph g = worst.graph();
        if (worst.live() && area.bottom() - y > 40.0f * scale) {
            final float cw = (area.w - pad * 2.0f) / 3.0f;
            count(s, area.x + pad, y, cw, scale, "ELEMENTS",
                    g.truncated() ? g.size() + "+" : Integer.toString(g.size()));
            count(s, area.x + pad + cw, y, cw, scale, "MACHINES", Integer.toString(g.machineCount()));
            count(s, area.x + pad + cw * 2.0f, y, cw, scale, "SOURCES",
                    Integer.toString(g.sourceCount()));
            y += 30.0f * scale;
        }

        final Rect below = new Rect(area.x + pad, y, area.w - pad * 2.0f, area.bottom() - pad - y);
        if (below.h < 30.0f * scale || !worst.live() || g.isEmpty()) {
            if (!worst.live()) {
                s.textCenter(worst.remedy(), area.centerX(), below.centerY(), Tokens.INK_3,
                        Text.MICRO * scale);
            }
            return;
        }

        final boolean sideBySide = below.w >= 260.0f * scale && below.h >= 120.0f * scale;
        final Rect plot = sideBySide
                ? new Rect(below.x, below.y, below.w * 0.62f, below.h)
                : below;
        final ScanLayout.Result layout = ScanLayout.layout(g, ScanLayoutMode.DRIVE);

        TopoArt.diagram(s, g, layout, plot, ScanLayoutMode.DRIVE, time, lod.showsLabels() ? 0.5f : 0.3f);
        if (sideBySide) {
            consumers(s, new Rect(plot.right() + 8.0f * scale, below.y,
                    below.right() - plot.right() - 8.0f * scale, below.h), g, scale);
        }
    }

    private static void consumers(PanelSurface s, Rect a, ScanGraph g, float scale) {
        if (a.w < 70.0f * scale || a.h < 30.0f * scale) {
            return;
        }
        s.card(a, Tokens.R_CARD, Tokens.RAISED, Tokens.LINE);
        final float pad = 6.0f * scale;
        s.text(Text.track("Heaviest"), a.x + pad, a.y + pad, Tokens.INK_3, Text.MICRO * scale);
        final List<ScanNode> load = new ArrayList<>();
        for (final ScanNode n : g.byLoad()) {
            if (n.drawnStress() > 0.0f) {
                load.add(n);
            }
        }
        if (load.isEmpty()) {
            s.textCenterV("No measured load", a.centerX(), a.centerY(), Tokens.INK_3,
                    Text.MICRO * scale);
            return;
        }
        final float rowH = ROW_UNIT * scale;
        final float top = a.y + pad + Text.lineHeight(Text.MICRO * scale) + 3.0f * scale;
        final float peak = load.get(0).drawnStress();
        final int rows = Math.max(1, (int) ((a.bottom() - pad - top) / rowH));
        for (int i = 0; i < rows && i < load.size(); i++) {
            final ScanNode n = load.get(i);
            final Rect row = new Rect(a.x + pad, top + i * rowH, a.w - pad * 2.0f, rowH - 2.0f * scale);
            final int col = n.isOverStressed() ? Tokens.CRITICAL : Tokens.seriesFor(n.name());
            Shapes.roundedRect(s, new Rect(row.x, row.y, Math.max(2.0f, row.w * (n.drawnStress() / peak)),
                    row.h), Tokens.R_CHIP, Tokens.alpha(col, 0.12f));
            s.textV(Text.truncate(s, n.name(), row.w - 42.0f * scale, Text.MICRO * scale),
                    row.x + 4.0f * scale, row.centerY(), Tokens.INK_2, Text.MICRO * scale);
            s.textRightV(Text.compact(n.drawnStress()), row.right() - 3.0f * scale, row.centerY(),
                    n.isOverStressed() ? Tokens.CRITICAL : Tokens.INK_2, Text.MICRO * scale);
        }
        if (load.size() > rows) {
            s.textV("+" + (load.size() - rows), a.x + pad, top + rows * rowH + 4.0f * scale,
                    Tokens.INK_3, Text.MICRO * scale);
        }
    }

    private static void paintSolo(PanelSurface s, Rect area, TopoArt.Reading r, int count, float scale) {
        final float pad = 6.0f * scale;
        final Rect in = area.inset(pad);
        final int status = TopoArt.statusColor(r);

        final String name = count > 1 ? count + " surveys" : "Topology";
        s.text(name, in.x, in.y, Tokens.INK_2, Text.MICRO * scale);
        if (count > 1) {
            s.textRight("worst", in.right(), in.y, Tokens.INK_3, Text.MICRO * scale);
        }

        final float barH = Math.max(4.0f, 8.0f * scale);
        final float top = in.y + Text.lineHeight(Text.MICRO * scale) + 3.0f * scale;
        final String reading = r.live() ? String.format(Locale.ROOT, "%.0f%%", r.pct()) : "—";
        final float readY = (top + in.bottom() - barH - 5.0f * scale) * 0.5f;
        final float fitted = Text.fitScale(s, reading, in.w, in.bottom() - barH - 8.0f * scale - top,
                Text.TITLE * scale, Text.MICRO);
        s.textCenterV(reading, in.centerX(), readY, r.live() ? status : Tokens.INK_3, fitted);

        final String sub = r.live()
                ? String.format(Locale.ROOT, "%s / %s su  ·  %s", Text.compact(r.graph().totalStress()),
                        Text.compact(r.graph().totalCapacity()), stateWord(r))
                : r.excuse();
        s.textCenterV(Text.truncate(s, sub, in.w, Text.MICRO * scale), in.centerX(),
                in.bottom() - barH - 4.0f * scale, r.live() ? Tokens.INK_3 : Tokens.CAUTION,
                Text.MICRO * scale);

        loadBar(s, new Rect(in.x, in.bottom() - barH, in.w, barH), r, scale);
    }

    private static void paintGlyph(PanelSurface s, Rect area, TopoArt.Reading r, int count) {
        final int status = TopoArt.statusColor(r);
        Shapes.rect(s, area, Tokens.alpha(status, 0.10f));

        final float radius = Math.min(area.w, area.h) * 0.24f;
        final float cx = area.centerX();
        final float cy = area.centerY() - radius * 0.15f;
        Shapes.arc(s, cx, cy, radius, radius * 0.16f, 0.0f, (float) (Math.PI * 2.0), Tokens.LINE);
        if (r.live()) {
            Shapes.arc(s, cx, cy, radius, radius * 0.16f, (float) (-Math.PI * 0.5),
                    (float) (Math.PI * 2.0 * Math.min(1.0f, r.pct() / 100.0f)), status);
            s.textCenterV(Math.round(r.pct()) + "%", cx, cy, status, Text.SUB);
        } else {
            s.textCenterV("—", cx, cy, Tokens.INK_3, Text.SUB);
        }
        final String below = r.live()
                ? (r.graph().truncated() ? r.graph().size() + "+" : Integer.toString(r.graph().size()))
                        + " elements" + (count > 1 ? "  ·  " + count + " surveys" : "")
                : r.excuse();
        s.textCenterV(below, cx, cy + radius + Text.lineHeight(Text.MICRO), Tokens.INK_3, Text.MICRO);
    }

    private static void loadBar(PanelSurface s, Rect bar, TopoArt.Reading r, float scale) {
        Shapes.roundedRect(s, bar, bar.h * 0.5f, Tokens.LINE);
        if (!r.live()) {
            s.textCenterV(r.excuse(), bar.centerX(), bar.centerY(), Tokens.CAUTION, Text.MICRO * scale);
            return;
        }
        final float frac = Math.min(1.0f, r.graph().loadFraction());
        if (frac > 0.001f) {
            Shapes.roundedRect(s, new Rect(bar.x, bar.y, Math.max(bar.h, bar.w * frac), bar.h),
                    bar.h * 0.5f, TopoArt.statusColor(r));
        }
    }

    private static void fleetBar(PanelSurface s, Rect bar, List<TopoArt.Reading> surveys, float scale) {
        Shapes.roundedRect(s, bar, bar.h * 0.5f, Tokens.LINE);
        float total = 0.0f;
        for (final TopoArt.Reading r : surveys) {
            total += Math.max(1.0f, r.graph().totalCapacity());
        }
        float x = bar.x;
        for (final TopoArt.Reading r : surveys) {
            final float cap = Math.max(1.0f, r.graph().totalCapacity());
            final float w = (bar.w - 2.0f) * (cap / total);
            final float fill = Math.min(w, w * Math.min(1.0f, r.live() ? r.graph().loadFraction() : 0.0f));
            Shapes.roundedRect(s, new Rect(x, bar.y, Math.max(1.0f, w - 1.0f), bar.h), bar.h * 0.4f,
                    Tokens.alpha(Tokens.LINE_HI, 0.4f));
            if (fill > 0.5f) {
                Shapes.roundedRect(s, new Rect(x, bar.y, Math.max(1.0f, fill), bar.h), bar.h * 0.4f,
                        TopoArt.statusColor(r));
            }
            x += w;
        }
    }

    private static void count(PanelSurface s, float x, float y, float w, float scale,
                              String label, String value) {
        s.text(label, x, y, Tokens.INK_3, Text.MICRO * scale);
        s.text(value, x, y + Text.lineHeight(Text.MICRO * scale) + 1.0f * scale, Tokens.INK,
                Text.META * scale);
    }

    private static String stateWord(TopoArt.Reading r) {
        if (!r.live()) {
            return r.excuse();
        }
        if (r.over()) {
            return "OVERSTRESSED";
        }
        if (!r.measured()) {
            return "CAP UNKNOWN";
        }
        final float pct = r.pct();
        return pct >= TopoArt.CRITICAL_AT ? "CRITICAL" : pct >= TopoArt.CAUTION_AT ? "HEAVY" : "HEALTHY";
    }

    private static List<TopoArt.Reading> ranked(List<TopoArt.Reading> all) {
        final List<TopoArt.Reading> out = new ArrayList<>(all);
        out.sort(Comparator.comparingDouble(r -> -(r.live() ? r.pct() : 85.0f)));
        return out;
    }

    private static void paintNoData(PanelSurface s, Rect area, String title, String detail) {
        s.textCenter(title, area.centerX(), area.centerY() - Text.lineHeight(Text.SUB), Tokens.INK_3,
                Text.SUB);
        s.textCenter(Text.truncate(s, detail, area.w * 0.9f, Text.MICRO), area.centerX(),
                area.centerY() + 4.0f, Tokens.INK_3, Text.MICRO);
    }

    private static float faceScale(Rect area) {
        final float shortSide = Math.min(area.w, area.h);
        return Math.max(0.6f, Math.min(2.4f, (float) Math.sqrt(shortSide / 92.0f)));
    }
}
