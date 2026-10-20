package com.createstats.statsmk3.page;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.createstats.seismic.SeismicCatalog;
import com.createstats.seismic.SeismicWave;
import com.createstats.seismic.archive.ScanCamera;
import com.createstats.seismic.archive.SurveyFinds;
import com.createstats.seismic.archive.SurveyScene;
import com.createstats.statsmk3.paint.FaceType;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class SurveyConcepts {
    private SurveyConcepts() {
    }

    public static final String[] TABS = {"Scan", "Depth", "Finds", "History"};
    static final float STRIP_H = 40.0f;
    static final float TABS_H = 18.0f;

    public record Ctx(SurveyScene scene, long now, double refX, double refY, double refZ, String focus) {
    }

    static void frame(PanelSurface s, Rect area, Ctx c, int tab) {
        strip(s, area.top(STRIP_H), c);
        final float y = area.y + STRIP_H + 4.0f;
        Shapes.rect(s, area.x, y + TABS_H - 1.0f, area.w, 1.0f, Tokens.LINE);
        float x = area.x + 2.0f;
        for (int i = 0; i < TABS.length; i++) {
            final float w = s.textWidth(TABS[i], Text.META) + 16.0f;
            final Rect t = new Rect(x, y, w, TABS_H);
            final boolean on = i == tab;
            s.textCenterV(TABS[i], t.centerX(), t.centerY() - 1.0f, on ? Tokens.INK : Tokens.INK_3, Text.META);
            if (on) {
                Shapes.rect(s, t.x + 4.0f, t.bottom() - 2.0f, t.w - 8.0f, 2.0f, Tokens.ACCENT);
            }
            x += w + 2.0f;
        }
    }

    static Rect content(Rect area) {
        return new Rect(area.x, area.y + STRIP_H + TABS_H + 10.0f, area.w, area.h - STRIP_H - TABS_H - 10.0f);
    }

    static void strip(PanelSurface s, Rect r, Ctx c) {
        Shapes.card(s, r, 6.0f, Tokens.RAISED, Tokens.LINE);
        final Rect in = r.inset(10.0f, 5.0f, 10.0f, 5.0f);
        final SurveyScene sc = c.scene();
        int linked = 0;
        for (final SurveyScene.Rig rig : sc.rigs) {
            if (rig.linked) {
                linked++;
            }
        }
        s.text("Survey", in.x, in.y + 1.0f, Tokens.INK, Text.SUB);
        s.text(sc.rigs.size() + (sc.rigs.size() == 1 ? " rig" : " rigs") + ", " + linked + " linked", in.x, in.y + 18.0f,
                Tokens.INK_2, Text.META);

        final String target = c.focus().isEmpty() ? lastTarget(c) : c.focus();
        final SurveyFinds.Find f = SurveyFinds.nearest(sc, target, -1, c.refX(), c.refZ());
        final int kind = f == null ? (SeismicCatalog.LIQUIDS.equals(target) ? SeismicCatalog.KIND_LAVA : 0) : f.body().kind;
        final float x0 = in.x + 112.0f;
        final float wide = Math.max(120.0f, (in.right() - x0 - 92.0f) * 0.52f);
        final float lh = Text.lineHeight(Text.MICRO);
        final float sw = 5.0f;
        Shapes.roundedRect(s, new Rect(x0, in.y + 2.0f + (lh - sw) * 0.5f, sw, sw), 1.0f, SurveyKit.colour(target, kind));
        s.text("NEAREST " + SeismicCatalog.veinLabel(target, kind), x0 + sw + 4.0f, in.y + 2.0f, Tokens.INK_3,
                Text.MICRO);
        s.text(Text.truncate(s, f == null ? "none found" : f.where(), wide - 6.0f, Text.SUB), x0, in.y + 13.0f,
                f == null ? Tokens.INK_3 : Tokens.INK, Text.SUB);
        final float cell = Math.max(56.0f, (in.right() - x0 - wide - 92.0f) / 2.0f);
        figure(s, x0 + wide, in, cell, "BLOWS", String.valueOf(sc.bowls.size()));
        figure(s, x0 + wide + cell, in, cell, "LAST BLOW", SurveyFinds.ago(c.now(), sc.newestBlow()));

        final Rect chip = new Rect(in.right() - 84.0f, in.y + 6.0f, 84.0f, in.h - 12.0f);
        final SurveyScene.Bowl last = sc.bowls.isEmpty() ? null : sc.bowls.get(sc.bowls.size() - 1);
        final boolean live = last != null && c.now() - last.at <= SeismicWave.listenTicks(last.reach);
        if (live) {
            Shapes.roundedRect(s, chip, 4.0f, Tokens.INK);
            s.textCenterV("LISTENING", chip.centerX(), chip.centerY(), Tokens.CANVAS, Text.MICRO);
        } else {
            final int fresh = SurveyFinds.newest(sc, sc.newestBlow()).size();
            Shapes.roundedRectOutline(s, chip, 4.0f, 1.0f, Tokens.LINE_HI);
            s.textCenterV(fresh + " NEW", chip.centerX(), chip.centerY(), Tokens.INK, Text.MICRO);
        }
    }

    private static void figure(PanelSurface s, float x, Rect in, float w, String label, String value) {
        s.text(label, x, in.y + 2.0f, Tokens.INK_3, Text.MICRO);
        s.text(Text.truncate(s, value, w - 6.0f, Text.SUB), x, in.y + 13.0f, Tokens.INK, Text.SUB);
    }

    static SurveyKit.Scan options(Ctx c, float micro) {
        final SurveyKit.Scan o = new SurveyKit.Scan();
        o.now = c.now();
        o.refX = c.refX();
        o.refY = c.refY();
        o.refZ = c.refZ();
        o.focus = c.focus();
        o.micro = micro;
        o.label = Math.max(micro, Text.META * 0.9f);
        return o;
    }

    public static void specimenPage(PanelSurface s, Rect area, Ctx c) {
        frame(s, area, c, 0);
        final Rect body = content(area);
        final float railW = Math.min(196.0f, body.w * 0.34f);
        final Rect scan = new Rect(body.x, body.y, body.w - railW - 8.0f, body.h - 18.0f);
        Shapes.card(s, scan, 8.0f, Tokens.SURFACE, Tokens.LINE);
        final SurveyKit.Scan o = options(c, Text.MICRO);
        o.maxCallouts = 3;
        SurveyKit.scan(s, scan.inset(6.0f), c.scene(), o);
        legend(s, new Rect(scan.right() + 8.0f, body.y, railW, body.h - 18.0f), c, Text.MICRO, Text.META, true);
        coverageLine(s, new Rect(body.x, body.bottom() - 12.0f, body.w, 12.0f), c, Text.MICRO);
    }

    static void legend(PanelSurface s, Rect r, Ctx c, float micro, float name, boolean card) {
        if (card) {
            Shapes.card(s, r, 8.0f, Tokens.SURFACE, Tokens.LINE);
        }
        final Rect in = card ? r.inset(8.0f) : r;
        s.text("TARGETS", in.x, in.y, Tokens.INK_3, micro);
        float y = in.y + Text.lineHeight(micro) + 5.0f;
        final float rowH = Text.lineHeight(name) + Text.lineHeight(micro) + 7.0f;
        final List<String> targets = new ArrayList<>(c.scene().targetsSurveyed());
        targets.sort((a, b) -> SeismicCatalog.compare(b, a));
        for (final String t : targets) {
            if (y + rowH > in.bottom()) {
                break;
            }
            final SurveyScene.Layer l = c.scene().layer(t);
            final boolean focus = t.equals(c.focus());
            final Rect row = new Rect(in.x - 3.0f, y - 2.0f, in.w + 6.0f, rowH - 2.0f);
            if (focus) {
                Shapes.roundedRect(s, row, 4.0f, Tokens.RAISED);
            }
            final int kind = SeismicCatalog.LIQUIDS.equals(t) ? SeismicCatalog.KIND_LAVA : 0;
            Shapes.roundedRect(s, new Rect(in.x, y + 1.0f, 6.0f, 6.0f), 1.5f, SurveyKit.colour(t, kind));
            if (SeismicCatalog.LIQUIDS.equals(t)) {
                Shapes.roundedRect(s, new Rect(in.x + 3.0f, y + 4.0f, 6.0f, 6.0f), 1.5f,
                        SurveyKit.colour(t, SeismicCatalog.KIND_WATER));
            }
            final String label = cap(SurveyText2.name(t));
            final String count = l == null ? "none" : l.bodies.size() + " " + SurveyFinds.plural(t, l.bodies.size());
            final float countW = s.textWidth(count, micro);
            s.text(Text.truncate(s, label, in.w - 13.0f - countW - 6.0f, name), in.x + 13.0f, y,
                    focus ? Tokens.INK : Tokens.INK_2, name);
            s.textRight(count, in.right(), y + (Text.lineHeight(name) - Text.lineHeight(micro)), Tokens.INK_3, micro);
            final String near;
            if (l == null) {
                near = "none in the ground read";
            } else {
                final SurveyFinds.Find f = SurveyFinds.nearest(c.scene(), t, -1, c.refX(), c.refZ());
                near = f == null ? "" : "nearest " + f.where();
            }
            s.text(Text.truncate(s, near, in.w - 13.0f, micro), in.x + 13.0f, y + Text.lineHeight(name) + 2.0f,
                    l == null ? Tokens.INK_3 : Tokens.INK_2, micro);
            y += rowH;
        }
    }

    static void coverageLine(PanelSurface s, Rect r, Ctx c, float micro) {
        final SurveyScene sc = c.scene();
        long oldest = Long.MAX_VALUE;
        for (final SurveyScene.Bowl b : sc.bowls) {
            oldest = Math.min(oldest, b.at);
        }
        final String text = sc.bowls.size() + " bowls read, the oldest " + SurveyFinds.ago(c.now(), oldest)
                + " ago.  Ground outside every bowl is unknown, not empty.";
        s.text(Text.truncate(s, text, r.w, micro), r.x + 2.0f, r.y + 2.0f, Tokens.INK_3, micro);
    }

    public static void strataPage(PanelSurface s, Rect area, Ctx c, double bandLo, double bandHi) {
        frame(s, area, c, 1);
        final Rect body = content(area);
        final float logW = Math.min(232.0f, body.w * 0.40f);
        final Rect log = new Rect(body.x, body.y, logW, body.h);
        Shapes.card(s, log, 8.0f, Tokens.SURFACE, Tokens.LINE);
        strataLog(s, log.inset(8.0f), c, bandLo, bandHi, Text.MICRO);
        final Rect right = new Rect(log.right() + 8.0f, body.y, body.w - logW - 8.0f, body.h);
        final float bandH = 56.0f;
        final Rect scan = new Rect(right.x, right.y, right.w, right.h - bandH - 8.0f);
        Shapes.card(s, scan, 8.0f, Tokens.SURFACE, Tokens.LINE);
        final SurveyKit.Scan o = options(c, Text.MICRO);
        o.sliceLo = bandLo;
        o.sliceHi = bandHi;
        o.maxCallouts = 2;
        o.callouts = false;
        SurveyKit.scan(s, scan.inset(6.0f), c.scene(), o);
        bandCard(s, new Rect(right.x, scan.bottom() + 8.0f, right.w, bandH), c, bandLo, bandHi, Text.MICRO);
    }

    static final int[][] LEVELS = {{63, 0}, {0, 1}, {-54, 2}, {-64, 3}};
    static final String[] LEVEL_NAMES = {"sea level", "deepslate", "lava level", "bedrock"};

    static void strataLog(PanelSurface s, Rect r, Ctx c, double bandLo, double bandHi, float micro) {
        final SurveyScene sc = c.scene();
        final int[] e = sc.extent();
        final int top = e[4];
        final int bottom = e[1];
        final List<String> targets = new ArrayList<>(sc.targetsSurveyed());
        targets.sort((a, b) -> SeismicCatalog.compare(b, a));
        final float lh = Text.lineHeight(micro);
        final Rect plot = new Rect(r.x + 30.0f, r.y + lh * 2.0f + 6.0f, r.w - 30.0f, r.h - lh * 2.0f - 8.0f);
        final float span = Math.max(1, top - bottom);

        if (!Double.isNaN(bandLo)) {
            final float y0 = yOf(plot, top, span, bandHi + 1.0);
            final float y1 = yOf(plot, top, span, bandLo);
            Shapes.rect(s, r.x, y0, r.w, y1 - y0, Tokens.alpha(Tokens.ACCENT_DIM, 0.85f));
            Shapes.rect(s, r.x, y0, r.w, 1.0f, Tokens.alpha(Tokens.ACCENT, 0.75f));
            Shapes.rect(s, r.x, y1 - 1.0f, r.w, 1.0f, Tokens.alpha(Tokens.ACCENT, 0.75f));
        }

        for (int y = (int) Math.ceil(bottom / 16.0) * 16; y <= top; y += 16) {
            final float py = yOf(plot, top, span, y);
            Shapes.rect(s, plot.x, py, plot.w, 1.0f, Tokens.alpha(Tokens.LINE, 0.9f));
            s.textRight(String.valueOf(y), plot.x - 4.0f, py - lh * 0.5f, Tokens.INK_3, micro);
        }
        for (int i = 0; i < LEVELS.length; i++) {
            final int y = LEVELS[i][0];
            if (y < bottom || y > top) {
                continue;
            }
            final float py = yOf(plot, top, span, y);
            for (float x = plot.x; x < plot.right(); x += 6.0f) {
                Shapes.rect(s, x, py, 3.0f, 1.0f, Tokens.alpha(Tokens.INK_3, 0.8f));
            }
            s.textRight(LEVEL_NAMES[i], plot.right(), py - lh - 1.0f, Tokens.INK_3, micro);
        }

        final int lanes = Math.max(1, targets.size());
        final float laneW = plot.w / lanes;
        for (int i = 0; i < targets.size(); i++) {
            final String t = targets.get(i);
            final float lx = plot.x + i * laneW;
            final int kind = SeismicCatalog.LIQUIDS.equals(t) ? SeismicCatalog.KIND_LAVA : 0;
            final int col = SurveyKit.colour(t, kind);
            final String head = SeismicCatalog.veinLabel(t, kind);
            final String shortHead = head.length() > 4 ? head.substring(0, 4) : head;
            Shapes.rect(s, lx + 2.0f, r.y + 1.0f, laneW - 4.0f, 2.0f, col);
            s.textCenter(Text.truncate(s, shortHead, laneW, micro), lx + laneW * 0.5f, r.y + 5.0f, Tokens.INK_2, micro);
            final SurveyScene.Layer l = sc.layer(t);
            if (l == null) {
                s.textCenter("none", lx + laneW * 0.5f, r.y + 5.0f + lh, Tokens.INK_3, micro);
                continue;
            }
            s.textCenter(String.valueOf(l.bodies.size()), lx + laneW * 0.5f, r.y + 5.0f + lh, Tokens.INK_3, micro);
            for (final SurveyScene.Body b : l.bodies) {
                final int bc = SurveyKit.colour(b.target, b.kind);
                final float y0 = yOf(plot, top, span, b.maxY + 1.0);
                final float y1 = Math.max(y0 + 1.2f, yOf(plot, top, span, b.minY));
                final float w = (float) Math.min(laneW - 4.0f, 2.0 + Math.log(1 + b.count) * 1.6);
                final boolean inBand = Double.isNaN(bandLo) || (b.maxY >= bandLo && b.minY <= bandHi);
                Shapes.rect(s, lx + (laneW - w) * 0.5f, y0, w, y1 - y0, Tokens.alpha(bc, inBand ? 0.9f : 0.28f));
            }
        }
    }

    private static float yOf(Rect plot, int top, float span, double y) {
        return (float) (plot.y + (top - y) / span * plot.h);
    }

    static void bandCard(PanelSurface s, Rect r, Ctx c, double lo, double hi, float micro) {
        Shapes.card(s, r, 8.0f, Tokens.SURFACE, Tokens.LINE);
        final Rect in = r.inset(8.0f);
        s.text(String.format(Locale.ROOT, "Y %d TO %d", (int) lo, (int) hi), in.x, in.y, Tokens.INK_3, micro);
        float x = in.x;
        final float y = in.y + Text.lineHeight(micro) + 5.0f;
        final List<String> targets = new ArrayList<>(c.scene().targetsSurveyed());
        targets.sort((a, b) -> SeismicCatalog.compare(b, a));
        for (final String t : targets) {
            final SurveyScene.Layer l = c.scene().layer(t);
            if (l == null) {
                continue;
            }
            int n = 0;
            SurveyScene.Body near = null;
            for (final SurveyScene.Body b : l.bodies) {
                if (b.maxY >= lo && b.minY <= hi) {
                    n++;
                    if (near == null || b.nearDist < near.nearDist) {
                        near = b;
                    }
                }
            }
            if (n == 0) {
                continue;
            }
            final SurveyFinds.Find f = SurveyFinds.place(near, near.nearX, near.nearY, near.nearZ, c.refX(), c.refZ());
            final String a = n + " " + SurveyFinds.noun(t, near.kind);
            final String b = f.where();
            final float w = Math.max(s.textWidth(a, Text.META), s.textWidth(b, micro)) + 16.0f;
            if (x + w + 8.0f > in.right()) {
                break;
            }
            Shapes.roundedRect(s, new Rect(x, y + 2.0f, 5.0f, 5.0f), 1.0f, SurveyKit.colour(t, near.kind));
            s.text(a, x + 9.0f, y, Tokens.INK, Text.META);
            s.text(b, x + 9.0f, y + Text.lineHeight(Text.META) + 2.0f, Tokens.INK_2, micro);
            x += w + 8.0f;
        }
    }

    public static void echoPage(PanelSurface s, Rect area, Ctx c, int rig) {
        frame(s, area, c, 0);
        final Rect body = content(area);
        final float logW = Math.min(250.0f, body.w * 0.42f);
        final Rect dome = new Rect(body.x, body.y, body.w - logW - 8.0f, body.h);
        Shapes.card(s, dome, 8.0f, Tokens.SURFACE, Tokens.LINE);
        final SurveyScene.Rig r = c.scene().rigs.get(Math.max(0, Math.min(c.scene().rigs.size() - 1, rig)));
        SurveyScene.Bowl last = null;
        for (final SurveyScene.Bowl b : c.scene().bowls) {
            if (b.x == r.x && b.y == r.y && b.z == r.z) {
                last = b;
            }
        }
        final SurveyKit.Scan o = options(c, Text.MICRO);
        o.maxCallouts = 2;
        o.room = false;
        o.bowls = false;
        o.pitch = 24.0;
        if (last != null) {
            o.frame = SurveyKit.boxOf(last);
            o.dome = last;
        }
        SurveyKit.scan(s, dome.inset(6.0f), c.scene(), o);
        s.text("R" + (rig + 1) + " sounding", dome.x + 8.0f, dome.y + 6.0f, Tokens.INK_2, Text.META);
        final Rect right = new Rect(dome.right() + 8.0f, body.y, logW, body.h);
        final float profH = Math.min(132.0f, right.h * 0.42f);
        final Rect log = new Rect(right.x, right.y, right.w, right.h - profH - 8.0f);
        Shapes.card(s, log, 8.0f, Tokens.SURFACE, Tokens.LINE);
        echoLog(s, log.inset(8.0f), c, Text.MICRO);
        final Rect prof = new Rect(right.x, log.bottom() + 8.0f, right.w, profH);
        Shapes.card(s, prof, 8.0f, Tokens.SURFACE, Tokens.LINE);
        profile(s, prof.inset(8.0f), c, r, Text.MICRO);
    }

    static void echoLog(PanelSurface s, Rect r, Ctx c, float micro) {
        s.text("ECHO LOG", r.x, r.y, Tokens.INK_3, micro);
        s.textRight("two-way time, ticks", r.right(), r.y, Tokens.INK_3, micro);
        final float lh = Text.lineHeight(micro);
        final float rowH = lh + 18.0f;
        float y = r.y + lh + 6.0f;
        final List<SurveyScene.Bowl> bowls = new ArrayList<>(c.scene().bowls);
        java.util.Collections.reverse(bowls);
        for (final SurveyScene.Bowl b : bowls) {
            if (y + rowH > r.bottom()) {
                break;
            }
            final int kind = SeismicCatalog.LIQUIDS.equals(b.target) ? SeismicCatalog.KIND_LAVA : 0;
            final int col = SurveyKit.colour(b.target, kind);
            final int rigNo = rigIndex(c.scene(), b) + 1;
            final String head = "R" + rigNo + "  " + SeismicCatalog.veinLabel(b.target, kind) + "  r" + b.reach;
            Shapes.roundedRect(s, new Rect(r.x, y + 1.5f, 4.5f, 4.5f), 1.0f, col);
            s.text(head, r.x + 8.0f, y, Tokens.INK_2, micro);
            s.textRight(SurveyFinds.ago(c.now(), b.at), r.right(), y, Tokens.INK_3, micro);
            final Rect strip = new Rect(r.x, y + lh + 2.0f, r.w, 13.0f);
            Shapes.rect(s, strip.x, strip.bottom() - 1.0f, strip.w, 1.0f, Tokens.LINE_HI);
            final float listen = SeismicWave.listenTicks(b.reach);
            for (final SurveyScene.Layer l : c.scene().layers) {
                if (!l.target.equals(b.target)) {
                    continue;
                }
                for (final SurveyScene.Body body : l.bodies) {
                    if (body.newest != b.at) {
                        continue;
                    }
                    final float x = strip.x + Math.min(1.0f, body.echo / listen) * (strip.w - 2.0f);
                    final float h = (float) Math.min(strip.h - 1.0f, 2.0 + Math.log(1 + body.count) * 2.2);
                    Shapes.rect(s, x, strip.bottom() - 1.0f - h, 1.2f, h, Tokens.alpha(SurveyKit.colour(l.target, body.kind), 0.9f));
                }
            }
            if (b.veins == 0) {
                s.text("no echo", strip.x + 2.0f, strip.y + 2.0f, Tokens.INK_3, micro);
            }
            y += rowH;
        }
    }

    static int rigIndex(SurveyScene sc, SurveyScene.Bowl b) {
        for (int i = 0; i < sc.rigs.size(); i++) {
            final SurveyScene.Rig r = sc.rigs.get(i);
            if (r.x == b.x && r.y == b.y && r.z == b.z) {
                return i;
            }
        }
        return 0;
    }

    static void profile(PanelSurface s, Rect r, Ctx c, SurveyScene.Rig rig, float micro) {
        s.text("RANGE AND DEPTH, R" + (c.scene().rigs.indexOf(rig) + 1), r.x, r.y, Tokens.INK_3, micro);
        final float lh = Text.lineHeight(micro);
        final Rect plot = new Rect(r.x + 18.0f, r.y + lh + 4.0f, r.w - 18.0f, r.h - lh * 2.0f - 6.0f);
        final float reach = 64.0f;
        final float k = Math.min(plot.w / reach, plot.h / reach);
        final float ox = plot.x;
        final float oy = plot.y;

        SurveyKit.ellipse(s, r, ox, oy, reach * k, reach * k, 0.0, Math.PI * 0.5, 1.0f, Tokens.alpha(Tokens.INK_3, 0.6f), 2);
        for (int d = 16; d < reach; d += 16) {
            Shapes.rect(s, ox, oy + d * k, reach * k, 1.0f, Tokens.alpha(Tokens.LINE, 0.8f));
            s.textRight(String.valueOf(rig.y - d), ox - 3.0f, oy + d * k - lh * 0.5f, Tokens.INK_3, micro);
        }
        for (final SurveyScene.Layer l : c.scene().layers) {
            for (final SurveyScene.Body b : l.bodies) {
                final double dx = b.cx - (rig.x + 0.5);
                final double dz = b.cz - (rig.z + 0.5);
                final double range = Math.sqrt(dx * dx + dz * dz);
                final double depth = rig.y - b.cy;
                if (range > reach || depth < 0 || depth > reach) {
                    continue;
                }
                final float x = (float) (ox + range * k);
                final float y = (float) (oy + depth * k);
                final float size = (float) Math.min(6.0, 1.4 + Math.cbrt(b.count) * 0.8);
                final int cls = SurveyKit.classOf(b.target, b.kind);
                final float a = cls == SurveyKit.ORE ? 0.9f : 0.35f;
                Shapes.rect(s, x - size * 0.5f, y - size * 0.5f, size, size, Tokens.alpha(SurveyKit.colour(b.target, b.kind), a));
            }
        }
        s.text("range from the plate, blocks", plot.x, plot.bottom() + 2.0f, Tokens.INK_3, micro);
    }

    public static void echoFace(PanelSurface s, Rect area, Ctx c) {
        Shapes.rect(s, area, 0xFF05070A);
        final FaceType t = FaceType.of(area);
        final Rect b = area.inset(t.pad);
        if (b.w < 160 || b.h < 120) {
            headline(s, b, c, t);
            return;
        }
        final float headH = FaceType.lh(t.title) + FaceType.lh(t.micro) + t.gap * 2;
        headline(s, b.top(headH), c, t);
        final float stripH = Math.max(22.0f, b.h * 0.16f);
        final Rect dome = new Rect(b.x, b.y + headH + t.gap, b.w, b.h - headH - stripH - t.gap * 3);
        final SurveyKit.Scan o = options(c, t.micro);
        o.room = false;
        o.callouts = false;
        o.ground = 0xFF05070A;
        SurveyKit.scan(s, dome, c.scene(), o);
        final Rect strip = new Rect(b.x, b.bottom() - stripH, b.w, stripH);
        final List<SurveyScene.Bowl> bowls = c.scene().bowls;
        if (!bowls.isEmpty()) {
            final SurveyScene.Bowl last = bowls.get(bowls.size() - 1);
            Shapes.rect(s, strip.x, strip.bottom() - 1.0f, strip.w, 1.0f, Tokens.LINE_HI);
            final float listen = SeismicWave.listenTicks(last.reach);
            for (final SurveyScene.Layer l : c.scene().layers) {
                for (final SurveyScene.Body body : l.bodies) {
                    if (body.newest != last.at) {
                        continue;
                    }
                    final float x = strip.x + Math.min(1.0f, body.echo / listen) * (strip.w - 2.0f);
                    final float h = (float) Math.min(strip.h - 2.0f, 3.0 + Math.log(1 + body.count) * 4.0);
                    Shapes.rect(s, x, strip.bottom() - 1.0f - h, Math.max(1.0f, t.hair * 1.6f), h,
                            SurveyKit.colour(l.target, body.kind));
                }
            }
        }
    }

    public static void strataFace(PanelSurface s, Rect area, Ctx c, double lo, double hi) {
        Shapes.rect(s, area, 0xFF05070A);
        final FaceType t = FaceType.of(area);
        final Rect b = area.inset(t.pad);
        if (b.w < 160 || b.h < 120) {
            headline(s, b, c, t);
            return;
        }
        final float headH = FaceType.lh(t.title) + FaceType.lh(t.micro) + t.gap * 2;
        headline(s, b.top(headH), c, t);
        final Rect rest = new Rect(b.x, b.y + headH + t.gap, b.w, b.h - headH - t.gap);
        final float logW = rest.w * 0.38f;
        strataLog(s, rest.left(logW), c, lo, hi, t.micro);
        final SurveyKit.Scan o = options(c, t.micro);
        o.sliceLo = lo;
        o.sliceHi = hi;
        o.callouts = false;
        o.ground = 0xFF05070A;
        SurveyKit.scan(s, new Rect(rest.x + logW + t.gap * 2, rest.y, rest.w - logW - t.gap * 2, rest.h), c.scene(), o);
    }

    public static void specimenFace(PanelSurface s, Rect area, Ctx c) {
        Shapes.rect(s, area, 0xFF05070A);
        final FaceType t = FaceType.of(area);
        final Rect b = area.inset(t.pad);
        if (b.w < 160 || b.h < 120) {
            headline(s, b, c, t);
            return;
        }
        final float railW = b.w * 0.33f;
        final SurveyKit.Scan o = options(c, t.micro);
        o.callouts = true;
        o.maxCallouts = 2;
        o.label = t.meta;
        o.ground = 0xFF05070A;
        SurveyKit.scan(s, new Rect(b.x, b.y, b.w - railW - t.gap * 2, b.h), c.scene(), o);
        legend(s, new Rect(b.right() - railW, b.y, railW, b.h), c, t.micro, t.row, false);
    }

    static void headline(PanelSurface s, Rect b, Ctx c, FaceType t) {
        final String target = c.focus().isEmpty() ? lastTarget(c) : c.focus();
        final SurveyFinds.Find f = SurveyFinds.nearest(c.scene(), target, -1, c.refX(), c.refZ());
        final int kind = SeismicCatalog.LIQUIDS.equals(target) ? SeismicCatalog.KIND_LAVA : 0;
        final int col = SurveyKit.colour(target, f == null ? kind : f.body().kind);
        final String name = "NEAREST " + SeismicCatalog.veinLabel(target, f == null ? kind : f.body().kind);
        final float sw = Math.max(3.0f, FaceType.lh(t.micro) * 0.6f);
        Shapes.roundedRect(s, new Rect(b.x, b.y + (FaceType.lh(t.micro) - sw) * 0.5f, sw, sw), 1.0f, col);
        s.text(Text.truncate(s, name, b.w - sw - t.gap * 2, t.micro), b.x + sw + t.gap * 2, b.y, Tokens.INK_2, t.micro);
        if (f == null) {
            s.text("none found", b.x, b.y + FaceType.lh(t.micro) + t.gap, Tokens.INK_3, t.row);
            return;
        }

        final String along = f.horiz() < 1.5 ? "below" : Math.round(f.horiz()) + " m " + SurveyFinds.compass(f.bearing());
        final String deep = "Y " + f.y();
        final float big = s.textWidth(along, t.hero * 0.6f) <= b.w ? t.hero * 0.6f : t.title;
        final float y1 = b.y + FaceType.lh(t.micro) + t.gap;
        s.text(Text.truncate(s, along, b.w, big), b.x, y1, Tokens.INK, big);
        s.text(deep, b.x, y1 + FaceType.lh(big) + t.gap, Tokens.INK_2, t.title);
    }

    static String lastTarget(Ctx c) {
        final List<SurveyScene.Bowl> b = c.scene().bowls;
        return b.isEmpty() ? "" : b.get(b.size() - 1).target;
    }

    static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static final class SurveyText2 {
        static String name(String target) {
            return com.createstats.seismic.archive.SurveyText.name(target);
        }
    }

    static ScanCamera cameraFor(SurveyScene scene, Rect r, SurveyKit.Scan o) {
        return SurveyKit.camera(scene, r, o);
    }
}
