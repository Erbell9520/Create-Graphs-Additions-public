package com.createstats.statsmk3.paint;

import com.createstats.statsmk3.instrument.Instrument;
import com.createstats.statsmk3.instrument.InstrumentCtx;
import com.createstats.statsmk3.instrument.InstrumentRegistry;
import com.createstats.statsmk3.layout.Tile;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class PanelPainter {
    private PanelPainter() {
    }

    private static final InstrumentCtx SHARED = new InstrumentCtx();

    private static final String NO_DATA = "NO DATA";
    private static final String EMPTY_HINT = "Link a machine to this panel";

    private static final Tile FULL_FACE = new Tile(0, 0, 1, 1);

    public static void paint(PanelSurface s, Rect area, PanelModel model, Lod lod) {
        if (area.isEmpty()) {
            return;
        }

        s.clip(area);
        try {
            paintFace(s, area, model, lod);
        } finally {
            s.unclip();
        }
    }

    private static void paintFace(PanelSurface s, Rect area, PanelModel model, Lod lod) {
        final PanelStyle style = model.style;

        if (TrainFacePainter.paint(s, area, model, lod)) {
            if (style.glass) {
                drawGlass(s, area);
            }
            return;
        }

        if (model.faceMode == com.createstats.statsmk3.Mk3Settings.FaceMode.STORAGE) {
            StorageFacePainter.paint(s, area, model, lod);
            if (style.glass) {
                drawGlass(s, area);
            }
            return;
        }

        if (model.faceMode == com.createstats.statsmk3.Mk3Settings.FaceMode.TOPOLOGY) {
            TopologyFacePainter.paint(s, area, model, lod);
            if (style.glass) {
                drawGlass(s, area);
            }
            return;
        }

        if (model.faceMode == com.createstats.statsmk3.Mk3Settings.FaceMode.REFINED_STORAGE) {
            RefinedStorageFacePainter.paint(s, area, model, lod);
            if (style.glass) {
                drawGlass(s, area);
            }
            return;
        }

        if (model.faceMode == com.createstats.statsmk3.Mk3Settings.FaceMode.EFFICIENCY) {
            EfficiencyFacePainter.paint(s, area, model, lod);
            if (style.glass) {
                drawGlass(s, area);
            }
            return;
        }

        if (model.faceMode != null && model.faceMode.isAero()) {
            AeroFacePainter.paint(s, area, model, lod);
            if (style.glass) {
                drawGlass(s, area);
            }
            return;
        }

        if (model.faceMode != null && model.faceMode.isDrive()) {
            DriveFacePainter.paint(s, area, model, lod);
            if (style.glass) {
                drawGlass(s, area);
            }
            return;
        }

        if (model.faceMode != null && model.faceMode.isSurvey() && model.survey != null) {
            SurveyFacePainter.paint(s, area, model.survey, SurveyFacePainter.modeOf(model.faceMode), lod, style.ground);
            if (style.glass) {
                drawGlass(s, area);
            }
            return;
        }

        Shapes.rect(s, area, style.ground);
        drawGlow(s, area, style);
        s.advanceLayer();

        final float pad = style.pad(8.0f) * scaleFor(area);

        if (lod.isGlyph()) {
            paintGlyph(s, area, model, LayoutResolver.primary(model));
            // after the glyph, its status wash would bury the header

            if (lod.showsHeader()) {
                drawHeader(s, area, model, pad, lod.headerAlpha());
            }
            return;
        }
        if (!model.hasData()) {
            paintEmpty(s, area, model);
            return;
        }

        if (lod.showsHeader()) {
            drawHeader(s, area, model, pad, lod.headerAlpha());
        }
        s.advanceLayer();

        final float lampRadius = lampRadius(area);
        Rect body = bodyOf(area, model, lod);
        if (body.isEmpty()) {
            return;
        }

        final float gap = gapOf(area, model);
        final PanelModel.Slot primary = LayoutResolver.primary(model);

        if (lod.showsHero()) {
            final float heroH = Math.min(body.h * 0.44f, 52.0f * scaleFor(area)) * lod.heroAmount();
            if (heroH > 12.0f) {
                drawHero(s, body.top(heroH), model, primary, lod.heroAmount());
                body = body.below(heroH + gap);
                if (body.isEmpty()) {
                    return;
                }
            }
        }

        if (lod.collapsesToPrimary()) {
            if (primary != null) {
                drawSlot(s, body, model, primary, lod);
            }
        } else {
            paintTiles(s, body, model, gap, lod);
        }

        s.advanceLayer();
        if (showLamps(model, area)) {
            final float lampY = area.bottom() - pad - lampRadius * 2.0f;
            drawLamps(s, new Rect(area.x + pad, lampY, area.w - pad * 2.0f, lampRadius * 2.0f),
                    model, lampRadius);
        }
        if (style.glass) {
            drawGlass(s, area);
        }
    }

    public static Rect bodyOf(Rect area, PanelModel model, Lod lod) {
        if (area.isEmpty()) {
            return Rect.EMPTY;
        }
        final float pad = model.style.pad(8.0f) * scaleFor(area);
        final float headerH = lod.showsHeader() ? model.style.pad(13.0f) : 0.0f;

        final float lampBandH = showLamps(model, area)
                ? lampRadius(area) * 2.0f + pad * 0.75f : 0.0f;
        return new Rect(area.x + pad, area.y + pad + headerH,
                area.w - pad * 2.0f, area.h - pad * 2.0f - headerH - lampBandH);
    }

    private static final float GAP_BUDGET = 0.5f;

    public static float gapOf(Rect area, PanelModel model) {
        final float base = model.style.pad(6.0f) * scaleFor(area);
        final Rect body = bodyOf(area, model, Lod.FULL);
        if (body.isEmpty() || model.layout == null) {
            return base;
        }
        final int cols = Math.max(1, model.layout.cols());
        final int rows = Math.max(1, model.layout.rows());
        float capped = base;
        if (cols > 1) {
            capped = Math.min(capped, body.w * GAP_BUDGET / (cols - 1));
        }
        if (rows > 1) {
            capped = Math.min(capped, body.h * GAP_BUDGET / (rows - 1));
        }

        return Math.max(0.5f, capped);
    }

    private static float lampRadius(Rect area) {
        return Math.max(1.4f, 2.4f * scaleFor(area));
    }

    private static void paintTiles(PanelSurface s, Rect body, PanelModel model, float gap, Lod lod) {
        if (model.slots.isEmpty()) {
            final ChannelView ch = model.channelAt(0);
            if (ch != null) {
                drawSlot(s, body, model, new PanelModel.Slot(FULL_FACE, ch, false), lod);
            }
            return;
        }
        for (final PanelModel.Slot slot : model.slots) {
            final Rect r = LayoutResolver.rectOf(slot.tile, model.layout, body, gap);
            if (r.isEmpty() || s.culled(r)) {
                continue;
            }
            drawSlot(s, r, model, slot, lod);
        }
    }

    private static void drawSlot(PanelSurface s, Rect slot, PanelModel model, PanelModel.Slot bound,
                                 Lod lod) {
        final float scale = scaleFor(slot);
        final float radius = Math.max(2.0f, 3.0f * scale);
        final float labelAlpha = lod.labelAlpha();
        Shapes.roundedRect(s, slot, radius, Tokens.alpha(0xFFFFFFFF, 0.018f));
        if (lod.showsLabels()) {
            Shapes.roundedRectOutline(s, slot, radius, 1.0f, Tokens.fade(Tokens.LINE, labelAlpha));
        }

        final float inset = model.style.pad(6.0f) * scale;
        Rect inner = slot.inset(inset);
        if (inner.isEmpty()) {
            return;
        }

        if (bound.lostSignal) {
            drawLostSignal(s, inner, bound.tile.channelId);
            return;
        }
        final ChannelView ch = bound.channel;
        if (ch == null) {
            return;
        }

        if (bound.tile.label && lod.showsLabels() && inner.h > 17.0f) {
            final String label = Text.truncate(s, ch.label.toUpperCase(java.util.Locale.ROOT),
                    inner.w, Text.MICRO);
            s.text(label, inner.x, inner.y, Tokens.fade(Tokens.INK_3, labelAlpha), Text.MICRO);

            final float labelH = Text.lineHeight(Text.MICRO) + 3.0f;
            inner = new Rect(inner.x, inner.y + labelH, inner.w, inner.h - labelH);
        }
        if (inner.isEmpty()) {
            return;
        }

        final String instrumentId = instrumentFor(bound.tile, model, inner);

        if (bound.tile.value && inner.h > 30.0f && !lod.showsHero()
                && !"numeric".equals(instrumentId)) {
            final float bandH = Math.min(inner.h * 0.5f, 34.0f * scale);
            drawValueBand(s, inner.top(bandH), ch);
            inner = inner.below(bandH + 2.0f * scale);
            if (inner.isEmpty()) {
                return;
            }
        }

        Instrument instrument = InstrumentRegistry.get(instrumentId);

        if (Math.min(inner.w, inner.h) < instrument.minSize()) {
            instrument = InstrumentRegistry.get("numeric");
        }

        SHARED.primary = ch;
        SHARED.all = model.channels;
        SHARED.style = model.style;
        SHARED.lod = lod;
        SHARED.time = model.time;
        SHARED.yAxis = bound.tile.yAxis();

        s.clip(inner);
        instrument.draw(s, inner, SHARED);
        s.unclip();
    }

    private static String instrumentFor(Tile tile, PanelModel model, Rect inner) {
        if (tile.instrumentId != null && !tile.instrumentId.isEmpty()
                && InstrumentRegistry.exists(tile.instrumentId)) {
            return tile.instrumentId;
        }
        return switch (autoRuleFor(inner.w, inner.h)) {
            case TINY -> model.autoTiny;
            case WIDE -> model.autoWide;
            case SQUARE -> model.autoSquare;
            case DEFAULT -> model.instrumentId;
        };
    }

    // public so the instruments page can explain AUTO
    public static AutoRule autoRuleFor(float w, float h) {
        final float min = Math.min(w, h);
        if (min < TINY_TILE) {
            return AutoRule.TINY;
        }
        final float aspect = w / Math.max(1.0f, h);
        if (aspect > WIDE_ASPECT) {
            return AutoRule.WIDE;
        }
        if (min < SQUARE_TILE && aspect > 0.7f && aspect < 1.4f) {
            return AutoRule.SQUARE;
        }
        return AutoRule.DEFAULT;
    }

    public static final float TINY_TILE = 26.0f;

    public static final float WIDE_ASPECT = 3.2f;

    public static final float SQUARE_TILE = 46.0f;

    public enum AutoRule {
        TINY("Tiny tile", "under " + (int) TINY_TILE + " px"),
        WIDE("Long and thin", "wider than " + WIDE_ASPECT + " to 1"),
        SQUARE("Small square", "under " + (int) SQUARE_TILE + " px"),
        DEFAULT("Everything else", "room for a plot");

        private final String label;
        private final String detail;

        AutoRule(String label, String detail) {
            this.label = label;
            this.detail = detail;
        }

        public String label() {
            return this.label;
        }

        public String detail() {
            return this.detail;
        }
    }

    private static void drawValueBand(PanelSurface s, Rect band, ChannelView ch) {
        final int color = ch.alerting ? Tokens.CRITICAL : ch.color();
        float unitW = ch.unit.isEmpty() ? 0.0f : s.textWidth(ch.unit, Text.MICRO) + 4.0f;
        if (unitW > band.w * 0.4f) {
            unitW = 0.0f;
        }
        final Text.Fitted fit = Text.fitValue(s, ch.value, band.w - unitW, band.h,
                Text.HERO, Text.MICRO);
        final float y = band.y + Math.max(0.0f, (band.h - Text.lineHeight(fit.scale())) * 0.5f);
        s.text(fit.text(), band.x, y, color, fit.scale());
        if (unitW > 0.0f) {
            s.text(ch.unit, band.x + s.textWidth(fit.text(), fit.scale()) + 4.0f,
                    y + Text.lineHeight(fit.scale()) - Text.lineHeight(Text.MICRO),
                    Tokens.INK_3, Text.MICRO);
        }
    }

    private static void drawLostSignal(PanelSurface s, Rect inner, String channelId) {
        final float cx = inner.centerX();
        final float cy = inner.centerY();
        if (inner.h < 20.0f) {
            s.textCenterV("NO SIGNAL", cx, cy, Tokens.CAUTION, Text.MICRO);
            return;
        }
        s.textCenter("NO SIGNAL", cx, cy - Text.lineHeight(Text.MICRO), Tokens.CAUTION, Text.MICRO);
        final String label = Text.truncate(s, prettyChannel(channelId), inner.w, Text.MICRO);
        s.textCenter(label, cx, cy + 2.0f, Tokens.INK_3, Text.MICRO);
    }

    private static String prettyChannel(String id) {
        if (id == null || id.isEmpty()) {
            return "unbound";
        }
        final int auto = id.indexOf("_auto_");
        final String tail = auto >= 0 ? id.substring(auto + 6) : id;
        return tail.replace('_', ' ');
    }

    private static void drawHero(PanelSurface s, Rect band, PanelModel model,
                                 PanelModel.Slot primary, float amount) {
        final ChannelView ch = primary != null && primary.channel != null
                ? primary.channel : model.channelAt(0);
        if (ch == null || band.isEmpty()) {
            return;
        }
        final int color = Tokens.fade(ch.alerting ? Tokens.CRITICAL : ch.color(), amount);
        final float labelH = Text.lineHeight(Text.MICRO) + 2.0f;

        s.text(Text.truncate(s, ch.label.toUpperCase(java.util.Locale.ROOT), band.w, Text.MICRO),
                band.x, band.y, Tokens.fade(Tokens.INK_3, amount), Text.MICRO);

        final float room = band.h - labelH;
        float unitW = ch.unit.isEmpty() ? 0.0f : s.textWidth(ch.unit, Text.MICRO) + 4.0f;
        if (unitW > band.w * 0.4f) {
            unitW = 0.0f;
        }
        final Text.Fitted fit = Text.fitValue(s, ch.value, band.w - unitW, room,
                Text.HERO, Text.META);
        final float valueY = band.y + labelH
                + Math.max(0.0f, (room - Text.lineHeight(fit.scale())) * 0.5f);
        s.text(fit.text(), band.x, valueY, color, fit.scale());

        if (unitW > 0.0f) {
            s.text(ch.unit, band.x + s.textWidth(fit.text(), fit.scale()) + 4.0f,
                    valueY + Text.lineHeight(fit.scale()) - Text.lineHeight(Text.MICRO),
                    Tokens.fade(Tokens.INK_3, amount), Text.MICRO);
        }
    }

    private static void drawHeader(PanelSurface s, Rect area, PanelModel model, float pad,
                                   float alpha) {
        final float y = area.y + pad;
        float right = area.right() - pad;

        if (model.status != null && !model.status.isEmpty()) {
            s.textRight(model.status, right, y,
                    Tokens.fade(model.statusColor == 0 ? Tokens.GOOD : model.statusColor, alpha),
                    Text.MICRO);

            right -= s.textWidth(model.status, Text.MICRO) + s.textWidth("nn", Text.MICRO);
        }

        if (model.timeRange != null && !model.timeRange.isEmpty()
                && right - area.x > area.w * 0.45f) {
            s.textRight(model.timeRange, right, y, Tokens.fade(Tokens.INK_3, alpha), Text.MICRO);
            right -= s.textWidth(model.timeRange, Text.MICRO) + s.textWidth("nn", Text.MICRO);
        }

        final String title = model.title == null ? "" : model.title.toUpperCase(java.util.Locale.ROOT);
        final float room = right - (area.x + pad);
        if (room > 8.0f) {
            s.text(Text.truncate(s, title, room, Text.MICRO), area.x + pad, y,
                    Tokens.fade(Tokens.INK_3, alpha), Text.MICRO);
        }
    }

    private static void drawGlow(PanelSurface s, Rect area, PanelStyle style) {
        if (style.glow <= 0.02f) {
            return;
        }
        final int center = Tokens.alpha(style.accent, 0.05f * style.glow);
        final int edge = Tokens.alpha(style.accent, 0.0f);
        Shapes.gradientV(s, area.top(area.h * 0.6f), center, edge);
    }

    private static void drawGlass(PanelSurface s, Rect area) {
        final Rect sweep = area.top(area.h * 0.42f);
        Shapes.gradientV(s, sweep,
                Tokens.alpha(0xFFFFFFFF, 0.045f), Tokens.alpha(0xFFFFFFFF, 0.0f));
    }

    private static boolean showLamps(PanelModel model, Rect area) {
        return model.lamps != null && model.lamps.length > 0 && area.h >= 56.0f;
    }

    private static void drawLamps(PanelSurface s, Rect band, PanelModel model, float r) {
        final float gap = r * 3.4f;
        float x = band.x + r;
        final float y = band.centerY();
        for (final int lamp : model.lamps) {
            if (x + r > band.right()) {
                break;
            }
            final int color = switch (lamp) {
                case 2 -> Tokens.CRITICAL;
                case 1 -> Tokens.GOOD;
                default -> Tokens.LINE_HI;
            };
            if (lamp == 2) {
                Shapes.circle(s, x, y, r * 2.2f, Tokens.alpha(color, 0.35f));
            }
            Shapes.circle(s, x, y, r, color);
            x += gap;
        }
    }

    private static void paintGlyph(PanelSurface s, Rect area, PanelModel model,
                                   PanelModel.Slot primary) {
        final ChannelView ch = primary != null && primary.channel != null
                ? primary.channel : model.channelAt(0);
        final float frac = ch == null ? 0.0f : ch.fraction();
        final int color = model.helperFiring ? Tokens.CRITICAL : Tokens.statusFor(frac);

        Shapes.rect(s, area, Tokens.alpha(color, 0.16f));
        s.advanceLayer();
        final Rect bar = new Rect(area.x + area.w * 0.14f, area.y + area.h * 0.62f,
                area.w * 0.72f, area.h * 0.13f);
        Shapes.roundedRect(s, bar, bar.h * 0.5f, Tokens.LINE);
        Shapes.roundedRect(s, bar.withWidth(Math.max(bar.h, bar.w * frac)), bar.h * 0.5f, color);
        s.advanceLayer();
        if (ch != null) {
            final Text.Fitted fit = Text.fitValue(s, ch.value, area.w * 0.8f, area.h * 0.4f,
                    Text.HERO, Text.BODY);
            s.textCenter(fit.text(), area.centerX(), area.y + area.h * 0.24f, color, fit.scale());
        }
    }

    private static void paintEmpty(PanelSurface s, Rect area, PanelModel model) {
        final Rect inner = area.inset(Math.max(2.0f, area.w * 0.06f));
        if (inner.isEmpty()) {
            return;
        }
        final float cx = inner.centerX();

        final float headScale = Text.fitScale(s, NO_DATA, inner.w, inner.h * 0.5f,
                Text.SUB, Text.MICRO);
        final float headH = Text.lineHeight(headScale);

        final float hintGap = headH * 0.45f;
        final float hintRoom = inner.h - headH - hintGap;
        java.util.List<String> hint = java.util.List.of();
        if (hintRoom >= Text.lineHeight(Text.MICRO)) {
            hint = Text.wrap(s, EMPTY_HINT, inner.w, Text.MICRO);

            if (hint.size() > (int) (hintRoom / Text.lineHeight(Text.MICRO))) {
                hint = java.util.List.of();
            }
        }

        final float hintH = hint.isEmpty()
                ? 0.0f : hint.size() * Text.lineHeight(Text.MICRO) + hintGap;
        float y = inner.centerY() - (headH + hintH) * 0.5f;

        s.textCenter(NO_DATA, cx, y, Tokens.INK_2, headScale);
        y += headH + hintGap;
        for (final String line : hint) {
            s.textCenter(line, cx, y, Tokens.INK_3, Text.MICRO);
            y += Text.lineHeight(Text.MICRO);
        }
    }

    private static float scaleFor(Rect area) {
        return Math.max(0.55f, Math.min(2.0f, Math.min(area.w, area.h) / 128.0f));
    }
}
