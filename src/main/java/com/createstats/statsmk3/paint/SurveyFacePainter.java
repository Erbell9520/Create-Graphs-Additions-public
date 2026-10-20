package com.createstats.statsmk3.paint;

import java.util.ArrayList;
import java.util.List;

import com.createstats.seismic.SeismicCatalog;
import com.createstats.seismic.SeismicWave;
import com.createstats.seismic.archive.SurveyFinds;
import com.createstats.seismic.archive.SurveyScene;
import com.createstats.statsmk3.page.SurveyArt;
import com.createstats.statsmk3.page.SurveyKit;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class SurveyFacePainter {
    private SurveyFacePainter() {
    }

    public enum Mode {
        SCAN, FINDS, DEPTH, ECHO
    }

    public static Mode modeOf(com.createstats.statsmk3.Mk3Settings.FaceMode faceMode) {
        if (faceMode == null) {
            return Mode.SCAN;
        }
        return switch (faceMode) {
            case SURVEY_FINDS -> Mode.FINDS;
            case SURVEY_DEPTH -> Mode.DEPTH;
            case SURVEY_ECHO -> Mode.ECHO;
            default -> Mode.SCAN;
        };
    }

    static final int GROUND = 0xFF05070A;

    public static void paint(PanelSurface s, Rect area, SurveyArt.Model m, Mode mode, Lod lod, int ground) {
        Shapes.rect(s, area, ground);
        s.advanceLayer();
        final FaceType t = FaceType.of(area);
        final Rect b = area.inset(t.pad);
        if (m.scene.isEmpty()) {
            empty(s, b, m, t);
            return;
        }
        if (!lod.isGlyph() && b.h < 104.0f && b.w >= b.h * 2.6f && b.w >= 220.0f) {
            strip(s, b, m, t);
            return;
        }
        if (lod.isGlyph() || b.w < 150.0f || b.h < 104.0f) {
            glance(s, b, m, t);
            return;
        }
        switch (mode) {
            case FINDS -> board(s, b, m, t);
            case DEPTH -> depth(s, b, m, t, ground);
            case ECHO -> echo(s, b, m, t, ground);
            default -> scan(s, b, m, t, ground);
        }
    }

    static void empty(PanelSurface s, Rect b, SurveyArt.Model m, FaceType t) {
        s.text(Text.truncate(s, "SURVEY", b.w, t.micro), b.x, b.y, Tokens.INK_3, t.micro);
        final String head = m.waiting ? "Reading the record"
                : m.since != Long.MIN_VALUE ? "Blows hidden"
                : m.known > 0 || m.linked ? "No blow yet" : "No rig linked";
        final float y = b.y + FaceType.lh(t.micro) + t.gap;
        s.text(Text.truncate(s, head, b.w, t.row), b.x, y, Tokens.INK_2, t.row);
        if (m.since != Long.MIN_VALUE && !m.waiting && y + FaceType.lh(t.row) + t.gap + FaceType.lh(t.micro) <= b.bottom()) {
            s.text(Text.truncate(s, "before " + SurveyFinds.ago(m.now, m.since) + " ago, on this panel", b.w, t.micro),
                    b.x, y + FaceType.lh(t.row) + t.gap, Tokens.INK_3, t.micro);
        }
    }

    static void glance(PanelSurface s, Rect b, SurveyArt.Model m, FaceType t) {
        final SurveyArt.Headline h = SurveyArt.headline(m);
        final String target = h.target() == null ? "" : h.target();
        final SurveyFinds.Find f = h.find();
        final boolean pinned = m.pin != Long.MIN_VALUE && f != null && h.label().startsWith("PINNED");
        final String name = target.isEmpty() ? "SURVEY" : SurveyArt.upper(target, h.kind());
        final String where = f == null ? "none found" : f.horiz() < 1.5 ? "below, Y " + f.y()
                : Math.round(f.horiz()) + " m " + SurveyFinds.compass(f.bearing()) + ", Y " + f.y();
        float sc = Math.max(t.row, t.hero * 0.8f);
        while (sc > t.row && (s.textWidth(name, sc) + FaceType.lh(sc) > b.w || s.textWidth(where, sc) > b.w
                || FaceType.lh(sc) * 2.0f + t.gap > b.h)) {
            sc *= 0.92f;
        }
        sc = Math.max(t.row, sc);
        final float sw = FaceType.lh(sc) * 0.5f;
        if (!target.isEmpty()) {
            SurveyKit.swatch(s, new Rect(b.x, b.y + (FaceType.lh(sc) - sw) * 0.5f, sw, sw), target, h.kind());
        }
        final float nx = target.isEmpty() ? b.x : b.x + sw + t.gap * 2;
        s.text(Text.truncate(s, name, b.right() - nx, sc), nx, b.y, Tokens.INK, sc);
        s.text(Text.truncate(s, where, b.w, sc), b.x, b.y + FaceType.lh(sc) + t.gap, f == null ? Tokens.INK_3 : Tokens.INK,
                sc);
        if (pinned && b.y + FaceType.lh(sc) * 2.0f + t.gap * 2 + FaceType.lh(t.micro) <= b.bottom()) {
            s.text("PINNED", b.x, b.y + FaceType.lh(sc) * 2.0f + t.gap * 2, Tokens.INK_3, t.micro);
        }
    }

    static float headline(PanelSurface s, Rect b, SurveyArt.Model m, FaceType t, boolean full) {
        final SurveyArt.Headline h = SurveyArt.headline(m);
        final String target = h.target() == null ? "" : h.target();
        final SurveyFinds.Find f = h.find();
        final int kind = h.kind();
        final int col = target.isEmpty() ? Tokens.INK_3 : SurveyKit.colour(target, kind);
        final float sw = Math.max(3.0f, FaceType.lh(t.micro) * 0.62f);
        final String name = target.isEmpty() ? "SURVEY" : h.label();
        if (!target.isEmpty()) {
            SurveyKit.swatch(s, new Rect(b.x, b.y + (FaceType.lh(t.micro) - sw) * 0.5f, sw, sw), target, kind);
        }
        s.text(Text.truncate(s, name, b.w - sw - t.gap * 2, t.micro), b.x + sw + t.gap * 2, b.y, Tokens.INK_2, t.micro);
        final float y1 = b.y + FaceType.lh(t.micro) + t.gap;
        if (f == null) {
            s.text(Text.truncate(s, "none found", b.w, t.row), b.x, y1, Tokens.INK_3, t.row);
            return y1 + FaceType.lh(t.row) - b.y;
        }
        final String along = f.horiz() < 1.5 ? "below" : Math.round(f.horiz()) + " m " + SurveyFinds.compass(f.bearing());
        final String deep = "Y " + f.y();
        if (!full) {
            final String line = along + ", " + deep;
            final float sc = s.textWidth(line, t.title) <= b.w ? t.title : t.row;
            s.text(Text.truncate(s, line, b.w, sc), b.x, y1, Tokens.INK, sc);
            return y1 + FaceType.lh(sc) - b.y;
        }
        float big = t.hero * 0.7f;
        while (big > t.row && s.textWidth(along, big) > b.w) {
            big *= 0.9f;
        }
        s.text(Text.truncate(s, along, b.w, big), b.x, y1, Tokens.INK, big);
        final float y2 = y1 + FaceType.lh(big) + t.gap;
        float mid = Math.max(t.title, big * 0.62f);
        while (mid > t.row && s.textWidth(deep, mid) > b.w) {
            mid *= 0.9f;
        }
        s.text(deep, b.x, y2, Tokens.INK_2, mid);
        return y2 + FaceType.lh(mid) - b.y;
    }

    static void scan(PanelSurface s, Rect b, SurveyArt.Model m, FaceType t, int ground) {
        final float headH = headline(s, b, m, t, false);
        final Rect rest = new Rect(b.x, b.y + headH + t.gap * 2, b.w, b.h - headH - t.gap * 2);
        final boolean legend = rest.w >= 360.0f && rest.h >= 180.0f;
        final float legendW = legend ? Math.min(rest.w * 0.30f, 210.0f) : 0.0f;
        final SurveyKit.Scan o = options(m, t, ground);
        o.maxCallouts = legend ? 2 : 3;
        o.terrainStep = rest.w < 300.0f ? 2 : 1;
        SurveyKit.scan(s, new Rect(rest.x, rest.y, rest.w - legendW - (legend ? t.gap * 3 : 0), rest.h), m.scene, o);
        if (legend) {
            legend(s, new Rect(rest.right() - legendW, rest.y, legendW, rest.h), m, t);
        }
    }

    static SurveyKit.Scan options(SurveyArt.Model m, FaceType t, int ground) {
        final SurveyKit.Scan o = new SurveyKit.Scan();
        o.now = m.now;
        o.refX = m.refX;
        o.refY = m.refY;
        o.refZ = m.refZ;
        o.focus = m.focus;
        o.hidden = m.hidden;
        o.yaw = m.yaw;
        o.pitch = m.pitch;
        o.zoom = m.zoom;

        o.centre = m.centre;

        final SurveyScene.Body pinned = m.pin == Long.MIN_VALUE ? null : SurveyArt.bodyAt(m.scene, m.pin);
        o.dig = pinned == null ? null : new int[] {pinned.nearX, pinned.nearY, pinned.nearZ};
        o.rigNumbers = m.rigNumbers;
        o.sliceLo = m.sliceLo;
        o.sliceHi = m.sliceHi;
        o.micro = t.micro;
        o.label = t.meta;
        o.ground = ground;
        o.minPoint = Math.max(1.8f, t.unit * 1.6f);
        o.budget = 2_000;
        o.cloud = 1_100;
        return o;
    }

    static void legend(PanelSurface s, Rect r, SurveyArt.Model m, FaceType t) {
        final boolean two = r.h / Math.max(1, SurveyArt.legendOrder(m.scene).size()) >= FaceType.lh(t.row)
                + FaceType.lh(t.micro) + t.gap * 2;
        final float rowH = two ? FaceType.lh(t.row) + FaceType.lh(t.micro) + t.gap * 2 : FaceType.lh(t.row) + t.gap * 2;
        float y = r.y;
        for (final String target : SurveyArt.legendOrder(m.scene)) {
            if (y + FaceType.lh(t.row) > r.bottom()) {
                break;
            }
            final SurveyScene.Layer l = m.scene.layer(target);
            final boolean hidden = m.hidden.contains(target);
            final int kind = SeismicCatalog.LIQUIDS.equals(target) ? SeismicCatalog.KIND_LAVA : 0;
            final float sw = Math.max(3.0f, FaceType.lh(t.row) * 0.55f);
            final float sy = y + (FaceType.lh(t.row) - sw) * 0.5f;
            SurveyKit.swatch(s, new Rect(r.x, sy, sw, sw), target, kind);
            final String count = l == null ? "0" : String.valueOf(l.bodies.size());
            final float cw = s.textWidth(count, t.meta);

            final String name = SurveyArt.station(target);
            final boolean focus = target.equals(m.focus);
            s.text(Text.truncate(s, name, r.w - sw - cw - t.gap * 4, t.row), r.x + sw + t.gap * 2, y,
                    hidden ? Tokens.INK_3 : focus || m.focus.isEmpty() ? Tokens.INK : Tokens.INK_2, t.row);
            s.textRight(count, r.right(), y + FaceType.lh(t.row) - FaceType.lh(t.meta), Tokens.INK_3, t.meta);
            if (two && l != null && !hidden) {
                final SurveyFinds.Find f = SurveyFinds.nearest(m.scene, target, -1, m.refX, m.refZ);
                if (f != null) {
                    s.text(Text.truncate(s, f.where(), r.w - sw - t.gap * 2, t.micro), r.x + sw + t.gap * 2,
                            y + FaceType.lh(t.row) + t.gap * 0.5f, Tokens.INK_2, t.micro);
                }
            }
            y += rowH;
        }
    }

    record Entry(String target, SurveyFinds.Find find, int kind, boolean pinned) {
        String name() {
            return SurveyArt.upper(target, kind);
        }

        String where() {
            if (find == null) {
                return "none";
            }
            return find.horiz() < 1.5 ? "below" : Math.round(find.horiz()) + " m " + SurveyFinds.compass(find.bearing());
        }
    }

    static List<Entry> entries(SurveyArt.Model m) {
        final List<Entry> out = new ArrayList<>();
        final SurveyFinds.Find pin = m.pin == Long.MIN_VALUE ? null : SurveyArt.pinned(m);
        if (pin != null) {
            out.add(new Entry(pin.body().target, pin, pin.body().kind, true));
        }
        final List<String> targets = new ArrayList<>(SurveyArt.legendOrder(m.scene));
        targets.removeIf(m.hidden::contains);
        final String first = SurveyArt.headlineTarget(m);
        if (targets.remove(first)) {
            targets.add(0, first);
        }
        for (final String target : targets) {
            final SurveyFinds.Find f = SurveyArt.nearestOpen(m, target);
            if (pin != null && f != null && f.body() == pin.body()) {
                continue;
            }
            final int kind = f == null ? (SeismicCatalog.LIQUIDS.equals(target) ? SeismicCatalog.KIND_LAVA : 0)
                    : f.body().kind;
            out.add(new Entry(target, f, kind, false));
        }
        return out;
    }

    private static float lineWidth(PanelSurface s, Entry e, float sc, FaceType t) {
        return FaceType.lh(sc) * 0.45f + t.gap * 2 + s.textWidth(e.name() + " " + e.where(), sc);
    }

    static void strip(PanelSurface s, Rect b, SurveyArt.Model m, FaceType t) {
        final List<Entry> list = entries(m);
        if (list.isEmpty()) {
            empty(s, b, m, t);
            return;
        }
        int cols = Math.min(list.size(), Math.max(1, (int) (b.w / Math.max(110.0f, b.h * 1.55f))));
        while (cols > 1) {
            final float w = b.w / cols - t.gap * 3;
            boolean fits = true;
            for (int i = 0; i < cols; i++) {
                fits &= lineWidth(s, list.get(i), t.row, t) <= w;
            }
            if (fits) {
                break;
            }
            cols--;
        }
        final float w = b.w / cols;

        final float lineRoom = (b.h - t.gap * 2) / (Text.LINE_HEIGHT * 2.0f);
        float sc = Math.max(t.row, lineRoom);
        for (int i = 0; i < cols; i++) {
            while (sc > t.row && lineWidth(s, list.get(i), sc, t) > w - t.gap * 3) {
                sc *= 0.92f;
            }
        }
        sc = Math.max(t.row, sc);
        for (int i = 0; i < cols; i++) {
            final Entry e = list.get(i);
            final Rect c = new Rect(b.x + i * w, b.y, w - t.gap * 3, b.h);
            final float sw = FaceType.lh(sc) * 0.45f;
            SurveyKit.swatch(s, new Rect(c.x, c.y + (FaceType.lh(sc) - sw) * 0.5f, sw, sw), e.target(), e.kind());
            final float nx = c.x + sw + t.gap * 2;
            s.text(Text.truncate(s, e.name() + " " + e.where(), c.right() - nx, sc), nx, c.y,
                    e.find() == null ? Tokens.INK_3 : Tokens.INK, sc);
            final float y2 = c.y + FaceType.lh(sc) + t.gap * 0.5f;
            if (e.find() != null && y2 + FaceType.lh(t.micro) <= c.bottom()) {
                final float small = Math.max(t.micro, Math.min(sc, (c.bottom() - y2) / Text.LINE_HEIGHT));
                s.text(Text.truncate(s, "Y " + e.find().y() + (e.pinned() ? "  PINNED" : ""), c.w, small), c.x, y2,
                        Tokens.INK_2, small);
            }
            if (i > 0) {
                Shapes.rect(s, c.x - t.gap * 1.5f, c.y, Math.max(0.6f, t.hair), c.h, Tokens.alpha(Tokens.LINE, 0.9f));
            }
        }
    }

    static void board(PanelSurface s, Rect b, SurveyArt.Model m, FaceType t) {
        s.text(Text.truncate(s, "NEAREST FINDS, ALONG THE GROUND", b.w * 0.6f, t.micro), b.x, b.y, Tokens.INK_3,
                t.micro);
        final String since = "last blow " + SurveyFinds.ago(m.now, m.scene.newestBlow());
        s.textRight(Text.truncate(s, since, b.w * 0.4f, t.micro), b.right(), b.y, Tokens.INK_3, t.micro);
        final List<Entry> rows = entries(m);
        float y = b.y + FaceType.lh(t.micro) + t.gap * 2;
        final float rowH = Math.max(FaceType.lh(t.row) + t.gap * 2, (b.bottom() - y) / Math.max(4, rows.size()));

        float scale = Math.min(t.title * 1.25f, Math.max(t.row, (rowH - t.gap * 2) / Text.LINE_HEIGHT));
        float nameW = 0.0f;
        float whereW = 0.0f;
        float yW = 0.0f;
        for (final Entry e : rows) {
            nameW = Math.max(nameW, s.textWidth(e.name(), 1.0f));
            if (e.find() != null) {
                whereW = Math.max(whereW, s.textWidth(e.where(), 1.0f));
                yW = Math.max(yW, s.textWidth("Y " + e.find().y(), 1.0f));
            }
        }
        if (!Double.isNaN(m.heading)) {
            whereW += Text.LINE_HEIGHT * 0.64f + 2.0f;
        }
        final float sw0 = FaceType.lh(scale) * 0.55f;
        final float need = nameW + whereW + yW;
        final float spare = b.w - sw0 - t.gap * 10;
        if (need * scale > spare) {
            scale = Math.max(t.micro, spare / need);
        }
        final float swW = Math.max(3.0f, FaceType.lh(scale) * 0.55f) + t.gap * 2;
        final float gapW = (b.w - swW - need * scale) / 2.0f;
        final float[] col = {b.x, b.x + swW + nameW * scale + gapW, b.x + swW + (nameW + whereW) * scale + gapW * 2};
        for (final Entry e : rows) {
            if (y + FaceType.lh(scale) > b.bottom()) {
                break;
            }
            final SurveyFinds.Find f = e.find();
            final float sw = Math.max(3.0f, FaceType.lh(scale) * 0.55f);
            if (e.pinned()) {
                Shapes.rect(s, b.x - t.gap, y - t.gap * 0.5f, b.w + t.gap * 2, rowH - t.gap, Tokens.RAISED);
            }
            SurveyKit.swatch(s, new Rect(col[0], y + (FaceType.lh(scale) - sw) * 0.5f, sw, sw), e.target(), e.kind());
            final boolean focus = e.target().equals(m.focus) || e.pinned();
            s.text(Text.truncate(s, e.name(), col[1] - col[0] - sw - t.gap * 4, scale),
                    col[0] + sw + t.gap * 2, y, focus || m.focus.isEmpty() ? Tokens.INK : Tokens.INK_2, scale);
            if (f == null) {
                s.text("none", col[1], y, Tokens.INK_3, scale);
            } else {
                float wx = col[1];
                if (!Double.isNaN(m.heading) && f.horiz() >= 1.5) {
                    // up = ahead of whoever stands in front of the wall

                    final float ar = FaceType.lh(scale) * 0.32f;
                    SurveyArt.arrow(s, wx + ar, y + FaceType.lh(scale) * 0.5f - 0.5f, ar, f.bearing() - m.heading,
                            Tokens.INK);
                    wx += ar * 2.0f + t.gap * 2;
                }
                s.text(Text.truncate(s, e.where(), col[2] - wx - t.gap * 2, scale), wx, y, Tokens.INK, scale);
                s.textRight("Y " + f.y(), b.right(), y, Tokens.INK_2, scale);
            }
            Shapes.rect(s, b.x, y + rowH - t.gap, b.w, Math.max(0.6f, t.hair), Tokens.alpha(Tokens.LINE, 0.9f));
            y += rowH;
        }
    }

    static void depth(PanelSurface s, Rect b, SurveyArt.Model m, FaceType t, int ground) {
        final float headH = headline(s, b, m, t, false);
        final Rect rest = new Rect(b.x, b.y + headH + t.gap * 2, b.w, b.h - headH - t.gap * 2);
        final boolean both = rest.w >= 300.0f;
        final float logW = both ? rest.w * 0.42f : rest.w;
        SurveyArt.strataLog(s, new Rect(rest.x, rest.y, logW, rest.h), m, t.micro);
        if (both) {
            final SurveyKit.Scan o = options(m, t, ground);
            o.callouts = false;
            o.pitch = Math.min(o.pitch, 18.0);
            o.terrainStep = 2;

            o.budget = 1_000;
            o.cloud = 600;
            SurveyKit.scan(s, new Rect(rest.x + logW + t.gap * 3, rest.y, rest.w - logW - t.gap * 3, rest.h), m.scene, o);
        }
    }

    static void echo(PanelSurface s, Rect b, SurveyArt.Model m, FaceType t, int ground) {
        final SurveyScene.Bowl last = m.scene.bowls.get(m.scene.bowls.size() - 1);
        final java.util.Set<String> others = new java.util.HashSet<>(m.scene.targetsSurveyed());
        others.remove(last.target);
        final long age = m.now - last.at;
        final boolean live = age >= 0 && age <= SeismicWave.listenTicks(last.reach);
        final int kind = SeismicCatalog.LIQUIDS.equals(last.target) ? SeismicCatalog.KIND_LAVA : 0;
        final String head = (live ? "LISTENING  " : "LAST BLOW  ") + SurveyArt.station(last.target) + "  "
                + last.reach + " m";
        s.text(Text.truncate(s, head, b.w * 0.7f, t.meta), b.x, b.y, live ? Tokens.INK : Tokens.INK_2, t.meta);
        final String tail = last.veins == 0 ? "nothing found" : last.veins + " " + SurveyFinds.plural(last.target,
                last.veins);

        final com.createstats.blockentity.SeismicLinks.RigState rig = m.rigStates.get(
                net.minecraft.core.BlockPos.asLong(last.x, last.y, last.z));
        final String right = live ? "" : rig != null ? "rig " + SurveyArt.stateWords(rig)
                : tail + ", " + SurveyFinds.ago(m.now, last.at);
        s.textRight(Text.truncate(s, right, b.w * 0.3f, t.meta), b.right(), b.y, Tokens.INK_3, t.meta);
        final float stripH = Math.max(14.0f, b.h * 0.15f);
        final Rect dome = new Rect(b.x, b.y + FaceType.lh(t.meta) + t.gap * 2, b.w,
                b.h - FaceType.lh(t.meta) - stripH - t.gap * 5);
        final SurveyKit.Scan o = options(m, t, ground);
        o.room = false;
        o.bowls = false;
        o.callouts = false;
        o.pitch = 22.0;
        o.frame = SurveyKit.boxOf(last);
        o.dome = last;

        o.dig = null;

        o.terrain = false;
        o.hidden = others;
        o.focus = "";
        SurveyKit.scan(s, dome, m.scene, o);
        final Rect strip = new Rect(b.x, b.bottom() - stripH, b.w, stripH);
        SurveyArt.Model view = m;
        echoStrip(s, strip, view, last, t);
    }

    private static void echoStrip(PanelSurface s, Rect strip, SurveyArt.Model m, SurveyScene.Bowl b, FaceType t) {
        Shapes.rect(s, strip.x, strip.bottom() - 1.0f, strip.w, Math.max(0.8f, t.hair), Tokens.LINE_HI);
        final float listen = SeismicWave.listenTicks(b.reach);
        final long age = m.now - b.at;

        final float pen = age >= 0 && age <= listen ? strip.x + (float) (age / listen) * strip.w : Float.MAX_VALUE;
        for (final SurveyScene.Layer l : m.scene.layers) {
            if (!l.target.equals(b.target)) {
                continue;
            }
            for (final SurveyScene.Body body : l.bodies) {
                if (body.newest != b.at) {
                    continue;
                }
                final float x = strip.x + Math.min(1.0f, body.echo / listen) * (strip.w - 2.0f);
                if (x > pen) {
                    continue;
                }
                final float h = (float) Math.min(strip.h - 2.0f, 2.0 + Math.log(1 + body.count) * strip.h * 0.18);
                Shapes.rect(s, x, strip.bottom() - 1.0f - h, Math.max(1.0f, t.hair * 1.4f), h,
                        SurveyKit.colour(l.target, body.kind));
            }
        }
        if (pen != Float.MAX_VALUE) {
            Shapes.rect(s, pen, strip.y, Math.max(1.0f, t.hair * 1.6f), strip.h, Tokens.INK);
        }
    }
}
