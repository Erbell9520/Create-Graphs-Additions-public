package com.createstats.statsmk3.paint;

import java.util.Locale;

import com.createstats.aerial.AerialAlerts;
import com.createstats.aerial.AerialFrame;
import com.createstats.aerial.AerialSnapshot;
import com.createstats.aerial.AerialText;
import com.createstats.aerial.AirframeMath;
import com.createstats.aerial.AirframeReadout;
import com.createstats.aerial.HelmWords;
import com.createstats.aerial.V3;
import com.createstats.statsmk3.Mk3Settings;
import com.createstats.statsmk3.page.AeroBridge;
import com.createstats.statsmk3.page.AeroInstruments;
import com.createstats.statsmk3.page.AeroSkin;
import com.createstats.statsmk3.page.AeroTabs;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class AeroBridgeFace {
    private AeroBridgeFace() {
    }

    public static void paint(PanelSurface s, Rect area, AerialFrame f, Lod lod, AeroSkin k) {
        paint(s, area, f, lod, k, Mk3Settings.FaceMode.AERO_DECK);
    }

    public static void paint(PanelSurface s, Rect area, AerialFrame f, Lod lod, AeroSkin k, Mk3Settings.FaceMode mode) {
        final float scale = AeroFacePainter.scaleFor(area);
        final PanelSurface v = new ScaledSurface(s, area.x, area.y, scale);
        final Rect va = ScaledSurface.virtual(area, scale);
        final float pad = Math.max(4.0f, Math.min(va.w, va.h) * 0.035f);
        final Rect body = va.inset(pad);
        switch (mode == null ? Mk3Settings.FaceMode.AERO_DECK : mode) {
            case AERO_TELEGRAPH -> AeroJobFaces.telegraph(v, body, f, k);
            case AERO_VOYAGE -> AeroJobFaces.voyage(v, body, f, k);
            case AERO_SCOPE -> AeroJobFaces.scope(v, body, f, k);
            case AERO_MAP -> AeroJobFaces.map(v, body, f, k);
            case AERO_BUDGET -> AeroJobFaces.lift(v, body, f, k);
            default -> deck(v, body, f, k);
        }
        if (f.stale()) {
            AeroFacePainter.stale(v, va, f);
        }
    }

    static void deck(PanelSurface v, Rect body, AerialFrame f, AeroSkin k) {
        final AirframeReadout rd = f.readout();
        final AerialSnapshot.Helm h = rd.snapshot.helm();
        final boolean moored = h.known() ? h.moored() || "OFF".equals(h.mode()) : rd.landed;
        final AerialText.Headline head = AerialText.headline(rd, f.alerts());
        final boolean warning = head != null && head.level() == AerialAlerts.Level.CRITICAL;
        final boolean big = body.w >= 440.0f && body.h >= 280.0f;
        if (body.w < 64.0f || body.h < 50.0f) {
            AeroFacePainter.altitudeOnly(v, body, f);
        } else if (body.w < 150.0f && body.h < 150.0f) {
            AeroFacePainter.altitudeHero(v, body, f);
        } else if (moored) {
            if (body.h >= 110.0f) {
                hangar(v, body, f, k);
            } else {
                hangarStrip(v, body, f, k);
            }
        } else if (warning && !big) {
            warning(v, body, f, k, head);
        } else if (watching(h) && !big && body.h >= 110.0f) {
            watch(v, body, f, k);
        } else if (big) {
            bridge(v, body, f, k);
            if (warning) {
                Shapes.roundedRectOutline(v, body.inset(-2.0f), 6.0f, 2.0f, Tokens.CRITICAL);
            }
        } else if (body.w >= 300.0f && body.h >= 200.0f) {
            wide(v, body, f, k);
        } else if (body.h >= body.w * 1.35f) {
            column(v, body, f, k);
        } else if (body.h < 120.0f) {
            strip(v, body, f, k);
        } else {
            block(v, body, f, k);
        }
    }

    // the warning still says how bad it is
    static void warning(PanelSurface s, Rect b, AerialFrame f, AeroSkin k, AerialText.Headline head) {
        final AirframeReadout rd = f.readout();
        final AerialSnapshot.Helm h = rd.snapshot.helm();
        Shapes.roundedRectOutline(s, b, 6.0f, 2.0f, Tokens.CRITICAL);
        final Rect in = b.inset(8.0f, 6.0f, 8.0f, 6.0f);
        topLine(s, in.top(12.0f), f, k);
        final float figuresH = Math.min(46.0f, in.h * 0.30f);
        final Rect words = new Rect(in.x, in.y + 18.0f, in.w, in.h - 18.0f - figuresH - 6.0f);

        float scale = Text.HERO;
        java.util.List<String> lines = Text.wrap(s, head.text(), words.w, scale);
        while (scale > Text.META && (lines.size() * (Text.lineHeight(scale) + 2.0f) > words.h || widest(s, lines, scale)
                > words.w)) {
            scale = Math.max(Text.META, scale - 0.25f);
            lines = Text.wrap(s, head.text(), words.w, scale);
        }
        float y = words.y + Math.max(0.0f, (words.h - lines.size() * (Text.lineHeight(scale) + 2.0f)) * 0.5f);
        for (final String line : lines) {
            if (y + Text.lineHeight(scale) > words.bottom() + 1.0f) {
                break;
            }
            s.text(Text.truncate(s, line, words.w, scale), words.x, y, Tokens.CRITICAL, scale);
            y += Text.lineHeight(scale) + 2.0f;
        }
        final Rect figures = new Rect(in.x, in.bottom() - figuresH, in.w, figuresH);
        final String alt = AeroBridgeWords.whole(rd.altitude);
        final float hs = AeroFacePainter.fit(s, alt, figures.w * 0.40f, figures.h, Text.HERO);
        s.text(alt, figures.x, figures.bottom() - Text.lineHeight(hs), AeroBridgeWords.heightColor(f, k), hs);
        final float x = figures.x + s.textWidth(alt, hs) + 10.0f;
        final String vs = AeroBridgeWords.signed(rd.verticalSpeed);
        final String hold = h.known() && Double.isFinite(h.heldAltitude()) ? "HOLD " + AeroBridgeWords.whole(h.heldAltitude())
                : "";
        if (x + s.textWidth(vs, Text.SUB) <= figures.right()) {
            s.text(vs, x, figures.bottom() - Text.lineHeight(Text.SUB) - 12.0f, AeroBridgeWords.vsColor(f, k), Text.SUB);
        }
        if (!hold.isEmpty() && x + s.textWidth(hold, Text.META) <= figures.right()) {
            s.text(hold, x, figures.bottom() - Text.lineHeight(Text.META), k.held(), Text.META);
        }
    }

    private static float widest(PanelSurface s, java.util.List<String> lines, float scale) {
        float w = 0.0f;
        for (final String l : lines) {
            w = Math.max(w, s.textWidth(l, scale));
        }
        return w;
    }

    static boolean watching(AerialSnapshot.Helm h) {
        return h.known() && h.unattended() && "ANCHOR".equals(h.mode());
    }

    static void watch(PanelSurface s, Rect b, AerialFrame f, AeroSkin k) {
        final AirframeReadout rd = f.readout();
        final AerialSnapshot snap = rd.snapshot;
        final AerialSnapshot.Helm h = snap.helm();
        topLine(s, b.top(12.0f), f, k);
        final float headH = b.h >= 160.0f ? 26.0f : 13.0f;
        final Rect mid = new Rect(b.x, b.y + 17.0f, b.w, b.h - 17.0f - headH - 4.0f);
        final float side = Math.min(mid.h, mid.w * 0.46f);
        final Rect circle = new Rect(mid.x, mid.y, side, mid.h);
        Shapes.card(s, circle, 5.0f, k.card(), k.cardEdge());
        final V3 anchor = h.anchor();
        final V3 pos = snap.body().position();
        final double drift = anchor.isFinite() ? Math.hypot(pos.x() - anchor.x(), pos.z() - anchor.z()) : Double.NaN;
        final float cx = circle.centerX();
        final float cy = circle.centerY();
        final float radius = Math.max(8.0f, Math.min(circle.w, circle.h) * 0.40f);

        final double ring = Double.isFinite(drift) ? Math.max(4.0, Math.ceil(drift * 1.25 / 2.0) * 2.0) : 4.0;
        final float ppb = (float) (radius / ring);
        Shapes.arc(s, cx, cy, radius, 1.2f, 0.0f, (float) (Math.PI * 2.0), Tokens.alpha(k.ink3(), 0.8f));
        Shapes.arc(s, cx, cy, radius * 0.5f, 1.0f, 0.0f, (float) (Math.PI * 2.0), Tokens.alpha(k.ink3(), 0.45f));
        Shapes.rect(s, cx - 4.0f, cy - 0.5f, 8.0f, 1.0f, k.held());
        Shapes.rect(s, cx - 0.5f, cy - 4.0f, 1.0f, 8.0f, k.held());
        if (anchor.isFinite()) {
            final java.util.List<AerialSnapshot.Fix> fixes = snap.trail().fixes();
            final int from = Math.max(1, fixes.size() - 40);
            for (int i = from; i < fixes.size(); i++) {
                final V3 a = fixes.get(i - 1).position();
                final V3 c = fixes.get(i).position();
                AeroInstruments.clippedLine(s, cx + (float) (a.x() - anchor.x()) * ppb, cy + (float) (a.z() - anchor.z()) * ppb,
                        cx + (float) (c.x() - anchor.x()) * ppb, cy + (float) (c.z() - anchor.z()) * ppb, circle.inset(2.0f), 1.0f,
                        Tokens.alpha(k.ink2(), 0.5f));
            }
            final float sx = (float) clamp(cx + (pos.x() - anchor.x()) * ppb, circle.x + 5.0, circle.right() - 5.0);
            final float sy = (float) clamp(cy + (pos.z() - anchor.z()) * ppb, circle.y + 5.0, circle.bottom() - 5.0);
            Shapes.circle(s, sx, sy, 4.0f, Tokens.CANVAS);
            Shapes.circle(s, sx, sy, 3.0f, drift > ring ? Tokens.CAUTION : k.ink());
        }
        final String ringLabel = Math.round(ring) + " b";
        if (s.textWidth(ringLabel, Text.META) + 6.0f < circle.w * 0.4f) {
            s.text(ringLabel, cx + radius * 0.72f, cy - radius * 0.72f - 10.0f, k.ink3(), Text.META);
        }

        final Rect right = new Rect(circle.right() + 10.0f, mid.y, mid.right() - circle.right() - 10.0f, mid.h);
        final String alt = AeroBridgeWords.whole(rd.altitude);
        final float hs = AeroFacePainter.fit(s, alt, right.w, right.h * 0.42f, Text.HERO * 1.25f);
        s.text(alt, right.x, right.y, AeroBridgeWords.heightColor(f, k), hs);
        float y = right.y + Text.lineHeight(hs) + 4.0f;
        if (Double.isFinite(h.heldAltitude()) && y + 10.0f <= right.bottom()) {
            s.text(Text.truncate(s, "HOLD " + AeroBridgeWords.whole(h.heldAltitude()), right.w, Text.META), right.x, y,
                    k.held(), Text.META);
            y += 13.0f;
        }
        if (y + 10.0f <= right.bottom()) {
            final long d = Double.isFinite(drift) ? Math.round(drift) : 0L;
            final String off = !Double.isFinite(drift) ? AeroTabs.first(s, right.w, "HOLDING ITS PLACE", "HOLDING")
                    : drift < 0.5 ? AeroTabs.first(s, right.w, "ON ITS ANCHOR", "ON ANCHOR")
                    : AeroTabs.first(s, right.w, d + " b OFF ITS ANCHOR", d + " b OFF ANCHOR", d + " b OFF");
            s.text(off, right.x, y, Double.isFinite(drift) && drift > ring
                    ? Tokens.CAUTION : k.ink2(), Text.META);
            y += 13.0f;
        }
        final double fill = AeroBridge.gasFillOf(snap);
        if (Double.isFinite(fill) && y + 10.0f <= right.bottom()) {
            s.text(Text.truncate(s, "GAS " + AeroInstruments.pct(fill), right.w, Text.META), right.x, y, k.gas(), Text.META);
        }
        wrapHeadline(s, new Rect(b.x, b.bottom() - headH, b.w, headH), f, k);
    }

    static void block(PanelSurface s, Rect b, AerialFrame f, AeroSkin k) {
        final AirframeReadout rd = f.readout();
        final AerialSnapshot.Helm h = rd.snapshot.helm();
        final Rect top = b.top(14.0f);
        topLine(s, top, f, k);
        final float headH = 30.0f;
        final Rect mid = new Rect(b.x, top.bottom() + 5.0f, b.w, b.h - top.h - 5.0f - headH - 6.0f);
        final float colW = Math.min(mid.w * 0.50f, mid.h * 1.35f);
        final Rect left = new Rect(mid.x, mid.y, mid.w - colW - 8.0f, mid.h);
        heroColumn(s, left, f, k, false);

        final float liftH = mid.h >= 110.0f ? 40.0f : 0.0f;
        final Rect card = new Rect(left.right() + 8.0f, mid.y, colW, mid.h - (liftH > 0.0f ? liftH + 5.0f : 0.0f));
        dialCard(s, card, f, k);
        if (liftH > 0.0f) {
            AeroBridge.liftGauge(s, new Rect(card.x, card.bottom() + 5.0f, colW, liftH), rd, k);
        }
        wrapHeadline(s, new Rect(b.x, b.bottom() - headH, b.w, headH), f, k);
    }

    static void wide(PanelSurface s, Rect b, AerialFrame f, AeroSkin k) {
        final AerialSnapshot.Helm h = f.readout().snapshot.helm();
        final Rect top = b.top(13.0f);
        topLine(s, top, f, k);
        final float headH = 22.0f;
        final float lowerH = Math.min(96.0f, (b.h - top.h - headH) * 0.46f);
        final Rect picture = new Rect(b.x, top.bottom() + 4.0f, b.w, b.h - top.h - 4.0f - lowerH - 6.0f - headH - 4.0f);
        AeroBridge.portrait(s, picture, f, k, picture.w >= 220.0f ? AeroBridge.DETAIL_LEVELS : AeroBridge.DETAIL_NONE);
        final Rect lower = new Rect(b.x, picture.bottom() + 6.0f, b.w, lowerH);
        final float dialW = Math.min(lower.w * 0.38f, lower.h * 1.6f);
        heroColumn(s, new Rect(lower.x, lower.y, lower.w - dialW - 10.0f, lower.h), f, k, true);
        dialCard(s, new Rect(lower.right() - dialW, lower.y, dialW, lower.h), f, k);
        AeroBridge.headline(s, new Rect(b.x, b.bottom() - headH, b.w, headH), f, k, Text.META * 1.25f);
    }

    static void heroColumn(PanelSurface s, Rect r, AerialFrame f, AeroSkin k, boolean orderBeside) {
        final AirframeReadout rd = f.readout();
        final AerialSnapshot.Helm h = rd.snapshot.helm();
        final String alt = AeroBridgeWords.whole(rd.altitude);
        final float heroScale = AeroFacePainter.fit(s, alt, orderBeside ? r.w * 0.46f : r.w, r.h * 0.56f,
                Text.HERO * 1.25f);
        s.text(alt, r.x, r.y, AeroBridgeWords.heightColor(f, k), heroScale);
        final float under = r.y + Text.lineHeight(heroScale) + 3.0f;
        final String hold = h.known() && Double.isFinite(h.heldAltitude()) ? "HOLD " + AeroBridgeWords.whole(h.heldAltitude())
                : "";
        final String vs = AeroBridgeWords.signed(rd.verticalSpeed);
        if (!hold.isEmpty()) {
            s.text(hold, r.x, under, k.held(), Text.META);
        }

        final boolean sideBySide = s.textWidth(hold, Text.META) + 8.0f + s.textWidth(vs, Text.META) <= r.w;
        final float vsX = orderBeside ? r.x + Math.max(s.textWidth(alt, heroScale), s.textWidth(hold, Text.META)) + 12.0f
                : (sideBySide ? r.right() - s.textWidth(vs, Text.META) : r.x);
        final float vsY = orderBeside ? r.y + 2.0f : (sideBySide ? under : under + 11.0f);
        if (vsX + s.textWidth(vs, Text.META) <= r.right()) {
            s.text(vs, vsX, vsY, AeroBridgeWords.vsColor(f, k), Text.META);
        }
        final String order = h.known() ? HelmWords.order(h.order()) : "";
        if (order.isEmpty()) {
            return;
        }
        if (orderBeside) {
            final float ox = r.x + Math.max(s.textWidth(alt, heroScale), s.textWidth(hold, Text.META)) + 12.0f;
            final float os = AeroFacePainter.fit(s, order, r.right() - ox, Text.lineHeight(Text.SUB) + 1.0f, Text.SUB);
            s.text(order, ox, r.y + 14.0f, k.ink(), os);
            final String answer = "engines " + Math.round(Math.abs(h.engine()) * 100.0) + "%";
            s.text(Text.truncate(s, answer, r.right() - ox, Text.META), ox, r.y + 16.0f + Text.lineHeight(os), k.ink2(),
                    Text.META);
        } else {
            final float oy = under + (sideBySide ? 13.0f : 24.0f);
            if (oy + Text.lineHeight(Text.META) <= r.bottom()) {
                final float os = AeroFacePainter.fit(s, order, r.w, r.bottom() - oy, Text.SUB);
                s.text(order, r.x, oy, k.ink(), Math.max(Text.META * 0.8f, os));
            }
        }
    }

    static void dialCard(PanelSurface s, Rect card, AerialFrame f, AeroSkin k) {
        Shapes.card(s, card, 5.0f, k.card(), k.cardEdge());
        final boolean labels = card.w >= 96.0f;
        final Rect in = card.inset(5.0f, 6.0f, 5.0f, 3.0f);

        final float radius = Math.min(in.w * 0.5f - 4.0f, in.h - (labels ? 16.0f : 6.0f));
        final float dialH = radius + (labels ? 16.0f : 6.0f) + 3.0f;
        AeroBridge.telegraph(s, new Rect(in.x, in.y + Math.max(0.0f, (in.h - dialH) * 0.5f), in.w, dialH), f, k, labels);
    }

    static void strip(PanelSurface s, Rect b, AerialFrame f, AeroSkin k) {
        final AirframeReadout rd = f.readout();
        final AerialSnapshot.Helm h = rd.snapshot.helm();
        final float dialW = Math.min(b.w * 0.34f, b.h * 1.7f);
        final Rect dial = new Rect(b.right() - dialW, b.y, dialW, b.h);
        Shapes.card(s, dial, 5.0f, k.card(), k.cardEdge());
        AeroBridge.telegraph(s, dial.inset(4.0f, 4.0f, 4.0f, 1.0f), f, k, false);
        final Rect left = new Rect(b.x, b.y, b.w - dialW - 8.0f, b.h);
        topLine(s, left.top(12.0f), f, k);
        final String alt = AeroBridgeWords.whole(rd.altitude);
        final float hs = AeroFacePainter.fit(s, alt, left.w * 0.45f, left.h - 28.0f, Text.HERO);
        s.text(alt, left.x, left.y + 14.0f, AeroBridgeWords.heightColor(f, k), hs);
        final String order = h.known() ? HelmWords.order(h.order()) : "";
        final float ox = left.x + s.textWidth(alt, hs) + 10.0f;
        if (!order.isEmpty() && ox < left.right() - 30.0f) {
            s.text(Text.truncate(s, order, left.right() - ox, Text.SUB), ox, left.y + 16.0f, k.ink(), Text.SUB);
            if (h.known() && Double.isFinite(h.heldAltitude())) {
                s.text("HOLD " + AeroBridgeWords.whole(h.heldAltitude()), ox, left.y + 32.0f, k.held(), Text.META);
            }
        }
        AeroBridge.headline(s, new Rect(left.x, left.bottom() - 12.0f, left.w, 12.0f), f, k, Text.META);
    }

    static void column(PanelSurface s, Rect b, AerialFrame f, AeroSkin k) {
        final AirframeReadout rd = f.readout();
        final AerialSnapshot.Helm h = rd.snapshot.helm();
        topLine(s, b.top(12.0f), f, k);
        final String alt = AeroBridgeWords.whole(rd.altitude);
        final float hs = AeroFacePainter.fit(s, alt, b.w, b.h * 0.2f, Text.HERO * 1.25f);
        s.textCenter(alt, b.centerX(), b.y + 16.0f, AeroBridgeWords.heightColor(f, k), hs);
        float y = b.y + 16.0f + Text.lineHeight(hs) + 2.0f;
        if (h.known() && Double.isFinite(h.heldAltitude())) {
            s.textCenter("HOLD " + AeroBridgeWords.whole(h.heldAltitude()), b.centerX(), y, k.held(), Text.META);
        }
        y += 14.0f;
        final float dialH = Math.min(b.w * 0.62f, (b.bottom() - y) * 0.45f);
        final Rect dial = new Rect(b.x, y, b.w, dialH);
        Shapes.card(s, dial, 5.0f, k.card(), k.cardEdge());
        AeroBridge.telegraph(s, dial.inset(4.0f, 5.0f, 4.0f, 1.0f), f, k, b.w >= 110.0f);
        y = dial.bottom() + 4.0f;
        final String order = h.known() ? HelmWords.order(h.order()) : "";
        if (!order.isEmpty()) {
            final float os = AeroFacePainter.fit(s, order, b.w, 20.0f, Text.SUB);
            s.textCenter(order, b.centerX(), y, k.ink(), os);
            y += Text.lineHeight(os) + 6.0f;
        }
        wrapHeadline(s, new Rect(b.x, y, b.w, b.bottom() - y), f, k);
    }

    static void bridge(PanelSurface s, Rect b, AerialFrame f, AeroSkin k) {
        AeroBridge.strip(s, b.top(AeroBridge.STRIP_H), f, k);
        final Rect rest = new Rect(b.x, b.y + AeroBridge.STRIP_H + 6.0f, b.w, b.h - AeroBridge.STRIP_H - 6.0f);
        AeroBridge.bridge(s, rest, f, k);
    }

    static void hangar(PanelSurface s, Rect b, AerialFrame f, AeroSkin k) {
        final AirframeReadout rd = f.readout();
        final AerialSnapshot snap = rd.snapshot;
        final AerialSnapshot.Helm h = snap.helm();
        final boolean wide = b.w >= 300.0f && b.h >= 190.0f;
        Rect card = b;
        if (wide) {
            final Rect picture = new Rect(b.x, b.y, b.w * 0.52f, b.h);
            AeroBridge.portrait(s, picture, f, k, AeroBridge.DETAIL_LEVELS);
            card = new Rect(picture.right() + 8.0f, b.y, b.right() - picture.right() - 8.0f, b.h);
        }
        Shapes.card(s, card, 6.0f, k.card(), k.cardEdge());
        final Rect in = card.inset(8.0f, 6.0f, 8.0f, 6.0f);
        final String state = h.known() ? AeroBridgeWords.modeLine(rd) : (rd.landed ? "On the ground" : "");
        s.text(Text.truncate(s, state.toUpperCase(Locale.ROOT), in.w - 50.0f, Text.META), in.x, in.y, k.title(), Text.META);
        AeroBridge.master(s, new Rect(in.right() - 46.0f, in.y - 1.0f, 46.0f, 11.0f), f);
        final String name = snap.name().isBlank() ? "Aerial" : snap.name();
        final float ns = AeroFacePainter.fit(s, name, in.w, 20.0f, Text.TITLE);
        s.text(name, in.x, in.y + 14.0f, k.ink(), ns);
        float y = in.y + 14.0f + Text.lineHeight(ns) + 6.0f;

        final double fill = AeroBridge.gasFillOf(snap);
        int heaters = 0;
        int lit = 0;
        for (final AerialSnapshot.Balloon bl : snap.balloons()) {
            heaters += Math.max(0, bl.heaters());
            lit += Math.max(0, bl.lit());
        }
        y = row(s, in, y, "GAS", Double.isFinite(fill) ? AeroInstruments.pct(fill) : "--",
                heaters > 0 ? (lit == 0 ? "burners banked" : lit + " of " + heaters + " burners lit") : "", k);
        if (Double.isFinite(fill) && y + 8.0f <= in.bottom()) {
            final Rect bar = new Rect(in.x, y - 2.0f, in.w, 4.0f);
            Shapes.roundedRect(s, bar, 1.5f, Tokens.SURFACE);
            Shapes.roundedRect(s, new Rect(bar.x, bar.y, bar.w * (float) fill, bar.h), 1.5f, k.gas());
            y += 6.0f;
        }
        y = row(s, in, y, "LOAD", Double.isFinite(rd.load) ? (rd.load > 0.5 ? Math.round(rd.load) + " kpg" : "none")
                : "--", Double.isFinite(rd.load) && rd.load > 0.5 ? "weighed aboard" : "", k);
        final AerialSnapshot.Engine e = snap.engine();
        y = row(s, in, y, "FUEL", e.count() == 0 ? "no engines" : Double.isFinite(e.fuelSeconds())
                ? AerialAlerts.duration(e.fuelSeconds()) : "--", e.count() == 0 ? "burner fuel is not told" : "", k);
        final AerialSnapshot.Leg last = snap.voyage().last();
        if (last != null && y + 12.0f <= in.bottom()) {
            final String time = AerialAlerts.duration(last.seconds());
            final String far = time + ", " + AeroBridgeWords.distance(last.distance());
            final String full = far + ", highest " + AeroBridgeWords.whole(last.highest());
            final float room = in.right() - in.x - 34.0f;
            final String leg = s.textWidth(full, Text.META) <= room ? full
                    : s.textWidth(far, Text.META) <= room ? far : time;
            y = row(s, in, y, "LAST", leg, last.alerts() > 0 ? last.alerts() + (last.alerts() == 1 ? " alert" : " alerts")
                    : "", k);
            final float[] trace = snap.voyage().trace();
            if (wide && trace.length >= 2 && y + 24.0f <= in.bottom() - 16.0f) {
                final Rect spark = new Rect(in.x, y, in.w, Math.min(40.0f, in.bottom() - 16.0f - y));
                final double[] range = AeroInstruments.range(trace, 0.1);
                AeroInstruments.spark(s, spark, trace, k.held(), range[0], range[1], Double.NaN, true);
                y = spark.bottom() + 4.0f;
            }
        }

        final AerialText.Headline head = AerialText.headline(rd, f.alerts());
        final boolean trouble = head != null && head.level() != null;
        final String ready = trouble ? head.text() : "READY: SIT BY THE AERIAL TO FLY";
        wrapText(s, new Rect(in.x, Math.max(y + 2.0f, in.bottom() - 26.0f), in.w, in.bottom() - Math.max(y + 2.0f,
                in.bottom() - 26.0f)), ready, trouble ? Tokens.CAUTION : Tokens.GOOD);
    }

    static void hangarStrip(PanelSurface s, Rect b, AerialFrame f, AeroSkin k) {
        final AirframeReadout rd = f.readout();
        final AerialSnapshot snap = rd.snapshot;
        topLine(s, b.top(12.0f), f, k);
        final String name = snap.name().isBlank() ? "Aerial" : snap.name();
        final float ns = AeroFacePainter.fit(s, name, b.w * 0.6f, Math.max(9.0f, b.h - 40.0f), Text.TITLE);
        s.text(name, b.x, b.y + 14.0f, k.ink(), ns);
        final double fill = AeroBridge.gasFillOf(snap);
        int lit = 0;
        int heaters = 0;
        for (final AerialSnapshot.Balloon bl : snap.balloons()) {
            heaters += Math.max(0, bl.heaters());
            lit += Math.max(0, bl.lit());
        }
        final String gas = "GAS " + (Double.isFinite(fill) ? AeroInstruments.pct(fill) : "--")
                + (heaters > 0 && lit == 0 ? ", banked" : "");
        s.textRight(Text.truncate(s, gas, b.w * 0.38f, Text.META), b.right(), b.y + 16.0f, k.ink2(), Text.META);
        final AerialText.Headline head = AerialText.headline(rd, f.alerts());
        final boolean trouble = head != null && head.level() != null;
        final String line = trouble ? head.text() : "READY: SIT BY THE AERIAL TO FLY";
        s.text(Text.truncate(s, line, b.w, Text.META), b.x, b.bottom() - Text.lineHeight(Text.META), trouble
                ? Tokens.CAUTION : Tokens.GOOD, Text.META);
    }

    private static float row(PanelSurface s, Rect in, float y, String label, String value, String note, AeroSkin k) {
        if (y + 11.0f > in.bottom()) {
            return y;
        }
        s.text(label, in.x, y, k.ink3(), Text.META);
        final float vx = in.x + 34.0f;
        s.text(Text.truncate(s, value, in.right() - vx, Text.META), vx, y, k.ink(), Text.META);
        final float vw = s.textWidth(value, Text.META);
        if (!note.isEmpty() && vx + vw + 8.0f + s.textWidth(note, Text.META) <= in.right()) {
            s.textRight(note, in.right(), y, k.ink3(), Text.META);
        }
        return y + 13.0f;
    }

    static void topLine(PanelSurface s, Rect r, AerialFrame f, AeroSkin k) {
        final float lampW = Math.min(52.0f, r.w * 0.34f);
        AeroBridge.master(s, new Rect(r.right() - lampW, r.y, lampW, Math.max(10.0f, r.h - 1.0f)), f);
        final String mode = AeroBridgeWords.modeLine(f.readout()).toUpperCase(Locale.ROOT);
        s.text(Text.truncate(s, mode, r.w - lampW - 6.0f, Text.META), r.x, r.y + 1.0f, k.ink2(), Text.META);
    }

    static void wrapHeadline(PanelSurface s, Rect r, AerialFrame f, AeroSkin k) {
        final AerialText.Headline head = AerialText.headline(f.readout(), f.alerts());
        if (head == null) {
            return;
        }
        final int col = head.level() == null ? k.ink() : head.level() == AerialAlerts.Level.CRITICAL ? Tokens.CRITICAL
                : Tokens.CAUTION;
        wrapText(s, r, head.text(), col);
    }

    static void wrapText(PanelSurface s, Rect r, String text, int color) {
        final float lh = Text.lineHeight(Text.META) + 2.0f;
        final int lines = (int) Math.floor((r.h + 2.0f) / lh);
        if (lines < 1) {
            return;
        }
        final java.util.List<String> wrapped = Text.wrap(s, text, r.w, Text.META);
        float y = r.y;
        for (int i = 0; i < Math.min(lines, wrapped.size()); i++) {
            String line = wrapped.get(i);
            if (i == lines - 1 && wrapped.size() > lines) {
                line = Text.truncate(s, line + " ...", r.w, Text.META);
            }
            s.text(line, r.x, y, color, Text.META);
            y += lh;
        }
    }

    static final class AeroBridgeWords {
        private AeroBridgeWords() {
        }

        static String whole(double v) {
            return Double.isFinite(v) ? Long.toString(Math.round(v)) : "--";
        }

        static String signed(double v) {
            return Double.isFinite(v) ? String.format(Locale.ROOT, "%+.1f b/s", Math.abs(v) < 0.05 ? 0.0 : v) : "--";
        }

        static String distance(double m) {
            if (!Double.isFinite(m)) {
                return "--";
            }
            return m >= 1000.0 ? String.format(Locale.ROOT, "%.1f km", m / 1000.0) : Math.round(m) + " b";
        }

        static String modeLine(AirframeReadout rd) {
            return AeroBridge.modeLineOf(rd);
        }

        static int heightColor(AerialFrame f, AeroSkin k) {
            return AeroBridge.heightColorOf(f, k);
        }

        static int vsColor(AerialFrame f, AeroSkin k) {
            return AeroBridge.vsColorOf(f, k);
        }
    }

    static double clamp(double v, double lo, double hi) {
        return AirframeMath.clamp(v, lo, hi);
    }
}
