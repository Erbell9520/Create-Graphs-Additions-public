package com.createstats.statsmk3.paint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.createstats.statsmk3.page.StorageArt;
import com.createstats.statsmk3.page.StorageReadings;
import com.createstats.statsmk3.page.StorageReadings.Kind;
import com.createstats.statsmk3.page.StorageReadings.Store;
import com.createstats.statsmk3.page.StorageReadings.Vessel;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// worst first, read from the warehouse door
@OnlyIn(Dist.CLIENT)
public final class StorageFacePainter {
    private StorageFacePainter() {
    }

    private static final float ROW_UNIT = 22.0f;

    public static void paint(PanelSurface s, Rect area, PanelModel model, Lod lod) {
        final PanelStyle style = model.style;
        Shapes.rect(s, area, style.ground);
        s.advanceLayer();

        final Kind filter = model.storageKind == null || model.storageKind.isEmpty()
                ? null : Kind.of(model.storageKind);
        final List<Store> stores = ranked(model.storage, filter);

        final float scale = faceScale(area);
        final float pad = 8.0f * scale;

        if (stores.isEmpty()) {
            paintNoData(s, area,
                    model.storage.isEmpty() ? "No store linked" : "No " + name(filter) + " store",
                    model.storage.isEmpty()
                            ? "Link a container to this panel"
                            : model.storage.size() + " other stores are linked");
            return;
        }

        if (lod.isGlyph()) {
            paintGlyph(s, area, stores.get(0));
            return;
        }

        float rowH = ROW_UNIT * scale;
        if (area.h - pad * 2.0f < rowH * 2.0f + 18.0f * scale) {
            paintSolo(s, area, stores, scale);
            return;
        }

        float y = area.y + pad;
        if (lod.showsHeader()) {
            y = drawHeader(s, area, stores, filter, scale, pad, lod);
        }

        final Rect body = new Rect(area.x + pad, y, area.w - pad * 2.0f, area.bottom() - pad - y);
        if (body.h <= 8.0f) {
            return;
        }

        int rows = Math.max(1, (int) (body.h / rowH));
        final float noteH = stores.size() > rows
                ? Text.lineHeight(Text.MICRO * scale) + 3.0f * scale : 0.0f;
        if (noteH > 0.0f && body.h - rows * rowH < noteH) {
            rows = Math.max(1, rows - 1);
        }

        rowH = Math.min(rowH * 1.5f, (body.h - noteH) / rows);

        final boolean stacked = mustStack(s, stores, Math.min(stores.size(), rows), body.w, scale);
        for (int i = 0; i < stores.size() && i < rows; i++) {
            final Rect row = new Rect(body.x, body.y + i * rowH, body.w, rowH - 2.0f * scale);
            if (row.bottom() > area.bottom()) {
                break;
            }
            drawRow(s, row, stores.get(i), scale, lod, model.time, stacked);
        }

        if (stores.size() > rows) {
            s.textRight("+" + (stores.size() - rows) + " more", body.right(),
                    body.bottom() - noteH + 2.0f * scale, Tokens.INK_3, Text.MICRO * scale);
        }
    }

    private static List<Store> ranked(List<Store> all, Kind filter) {
        final List<Store> out = new ArrayList<>();
        for (final Store store : all) {
            if (filter == null || store.vessel().kind() == filter) {
                out.add(store);
            }
        }
        out.sort(Comparator.comparingDouble(store -> -store.vessel().urgency()));
        return out;
    }

    private static float drawHeader(PanelSurface s, Rect area, List<Store> stores, Kind filter,
                                    float scale, float pad, Lod lod) {
        final String title = (filter == null ? "STORAGE" : name(filter).toUpperCase(Locale.ROOT));
        final float titleScale = Text.fitScale(s, title, area.w * 0.55f, area.h * 0.17f,
                Text.SUB * scale, Text.MICRO);
        float y = area.y + pad;
        s.text(title, area.x + pad, y, Tokens.fade(Tokens.INK, lod.headerAlpha()), titleScale);

        int atLimit = 0;
        for (final Store store : stores) {
            if (store.vessel().alarming()) {
                atLimit++;
            }
        }
        final String right = atLimit > 0 ? atLimit + " at a limit" : stores.size() + " stores";
        s.textRight(right, area.right() - pad, y,
                Tokens.fade(atLimit > 0 ? Tokens.CAUTION : Tokens.INK_3, lod.headerAlpha()),
                Text.MICRO * scale);

        y += Text.lineHeight(titleScale) + 2.0f * scale;
        Shapes.rect(s, area.x + pad, y, area.w - pad * 2.0f, 1.0f,
                Tokens.fade(Tokens.LINE_HI, lod.headerAlpha()));
        return y + 4.0f * scale;
    }

    private static boolean mustStack(PanelSurface s, List<Store> stores, int shown, float rowW,
                                     float scale) {
        final float textScale = Text.META * scale;
        final float iconLane = 11.0f * scale;
        for (int i = 0; i < shown; i++) {
            final Vessel v = stores.get(i).vessel();
            final String reading = v.hasCapacity()
                    ? Math.round(v.pct()) + "%" : v.compact(v.level());
            final float readingW = s.textWidth(reading, textScale) + 6.0f * scale;
            final float barW = Math.max(24.0f, (rowW - iconLane - readingW) * 0.46f);
            final float nameW = rowW - iconLane - readingW - barW - 8.0f * scale;
            if (nameW < s.textWidth(stores.get(i).name(), textScale)) {
                return true;
            }
        }
        return false;
    }

    private static void paintSolo(PanelSurface s, Rect area, List<Store> stores, float scale) {
        final Store store = stores.get(0);
        final Vessel v = store.vessel();
        final int status = v.statusColor();
        final float pad = 4.0f * scale;
        final Rect in = area.inset(pad);

        s.text(Text.truncate(s, store.name(), in.w, Text.MICRO * scale), in.x, in.y,
                Tokens.INK_2, Text.MICRO * scale);

        final float iconR = Math.min(in.h * 0.20f, in.w * 0.12f);
        final float top = in.y + Text.lineHeight(Text.MICRO * scale) + 2.0f * scale;
        final float barH = Math.max(4.0f, 7.0f * scale);
        final float readY = (top + in.bottom() - barH - 4.0f * scale) * 0.5f;
        StorageArt.kindIcon(s, in.x + iconR, readY, iconR, v.kind(), status);

        final String reading = v.hasCapacity()
                ? Math.round(v.pct()) + "%" : v.compact(v.level());
        final float fitted = Text.fitScale(s, reading, in.w - iconR * 2.4f,
                in.bottom() - barH - 6.0f * scale - top, Text.TITLE * scale, Text.MICRO);
        s.textRightV(reading, in.right(), readY, status, fitted);

        drawBar(s, new Rect(in.x, in.bottom() - barH, in.w, barH), v, status, scale, 0.0f);
        if (stores.size() > 1) {
            s.textRight("+" + (stores.size() - 1), in.right(), in.y, Tokens.INK_3,
                    Text.MICRO * scale);
        }
    }

    private static void drawRow(PanelSurface s, Rect r, Store store, float scale, Lod lod,
                                float time, boolean stacked) {
        final Vessel v = store.vessel();
        final int status = v.statusColor();
        final float textScale = Text.META * scale;

        if (v.alarming()) {
            Shapes.roundedRect(s, r, 2.0f * scale, Tokens.alpha(status, 0.09f));
        }
        final float iconR = 4.0f * scale;
        final float x = r.x + iconR * 2.0f + 7.0f * scale;

        final String reading = v.hasCapacity()
                ? Math.round(v.pct()) + "%" : v.compact(v.level());
        final float readingW = s.textWidth(reading, textScale) + 6.0f * scale;

        final float barW = Math.max(24.0f, (r.w - (x - r.x) - readingW) * 0.46f);
        final float nameW = r.w - (x - r.x) - readingW - barW - 8.0f * scale;
        final float barH = 8.0f * scale;

        if (stacked) {
            final float top = r.y + 1.0f * scale;

            StorageArt.kindIcon(s, r.x + iconR + 2.0f * scale,
                    top + Text.lineHeight(textScale) * 0.5f, iconR, v.kind(), status);
            s.text(Text.truncate(s, store.name(), r.w - (x - r.x) - readingW, textScale), x, top,
                    Tokens.INK, textScale);
            s.textRight(reading, r.right() - 1.0f * scale, top, status, textScale);
            final float barY = top + Text.lineHeight(textScale) + 2.0f * scale;
            if (barY + barH <= r.bottom()) {
                drawBar(s, new Rect(r.x + 2.0f * scale, barY, r.w - 4.0f * scale, barH),
                        v, status, scale, time);
            }
            return;
        }

        StorageArt.kindIcon(s, r.x + iconR + 2.0f * scale, r.centerY(), iconR, v.kind(), status);
        s.textV(Text.truncate(s, store.name(), nameW, textScale), x, r.centerY(),
                Tokens.INK, textScale);
        final Rect bar = new Rect(r.right() - readingW - barW, r.centerY() - barH * 0.5f,
                barW, barH);
        drawBar(s, bar, v, status, scale, time);

        s.textRightV(reading, r.right() - 1.0f * scale, r.centerY(), status, textScale);

        if (lod.showsLabels() && r.h > Text.lineHeight(textScale) * 2.2f) {
            final String detail = detailOf(v);
            if (!detail.isEmpty() && nameW > 0.0f) {
                s.text(Text.truncate(s, detail, nameW, Text.MICRO * scale), x,
                        r.centerY() + Text.lineHeight(textScale) * 0.5f,
                        Tokens.INK_3, Text.MICRO * scale);
            }
        }
    }

    private static void drawBar(PanelSurface s, Rect bar, Vessel v, int status, float scale,
                                float time) {
        Shapes.roundedRect(s, bar, bar.h * 0.5f, Tokens.LINE);
        if (!v.hasCapacity()) {
            s.textCenterV("amount only", bar.centerX(), bar.centerY(), Tokens.INK_3,
                    Text.MICRO * scale);
            return;
        }
        final float w = bar.w * v.fill();
        if (w > 0.5f) {
            Shapes.roundedRect(s, bar.withWidth(Math.max(bar.h, w)), bar.h * 0.5f, status);
        }
        if (v.hasThresholds()) {
            Shapes.rect(s, bar.x + bar.w * v.lowerPct() / 100.0f, bar.y - 1.5f * scale,
                    Math.max(1.0f, scale), bar.h + 3.0f * scale, Tokens.GOOD);
            Shapes.rect(s, bar.x + bar.w * v.upperPct() / 100.0f, bar.y - 1.5f * scale,
                    Math.max(1.0f, scale), bar.h + 3.0f * scale, Tokens.CAUTION);
        }

        final Float rate = v.slopePerSecond();
        if (rate != null && !v.steady() && w > 2.0f) {
            final float pulse = 0.45f + 0.55f * (float) Math.abs(Math.sin(time * 2.2));
            Shapes.rect(s, bar.x + w - Math.max(1.0f, scale), bar.y, Math.max(1.0f, scale) * 2.0f,
                    bar.h, Tokens.alpha(rate > 0.0f ? Tokens.GOOD : Tokens.CAUTION, pulse));
        }
    }

    private static String detailOf(Vessel v) {
        final StringBuilder sb = new StringBuilder(v.amountText());
        if (v.hasCapacity()) {
            sb.append(" / ").append(v.capacityText());
        }
        if (!v.content().isEmpty()) {
            sb.append(" · ").append(v.content());
        } else if (v.uniqueTypes() > 1) {
            sb.append(" · ").append(v.uniqueTypes()).append(" types");
        }
        return sb.toString();
    }

    private static void paintGlyph(PanelSurface s, Rect area, Store worst) {
        final Vessel v = worst.vessel();
        final int status = v.statusColor();
        Shapes.rect(s, area, Tokens.alpha(status, 0.10f));

        final float r = Math.min(area.w, area.h) * 0.18f;
        StorageArt.kindIcon(s, area.centerX(), area.centerY() - r * 0.9f, r, v.kind(), status);

        final Rect bar = new Rect(area.x + area.w * 0.14f, area.centerY() + r * 0.45f,
                area.w * 0.72f, Math.max(3.0f, area.h * 0.10f));
        Shapes.roundedRect(s, bar, bar.h * 0.5f, Tokens.LINE);
        if (v.hasCapacity() && v.fill() > 0.001f) {
            Shapes.roundedRect(s, bar.withWidth(Math.max(bar.h, bar.w * v.fill())),
                    bar.h * 0.5f, status);
        }
    }

    private static String name(Kind kind) {
        return kind == null ? "storage" : kind.label.toLowerCase(Locale.ROOT);
    }

    private static void paintNoData(PanelSurface s, Rect area, String title, String detail) {
        final float cx = area.centerX();
        final float cy = area.centerY();
        s.textCenter(title, cx, cy - Text.lineHeight(Text.SUB), Tokens.INK_3, Text.SUB);
        if (!detail.isEmpty()) {
            s.textCenter(Text.truncate(s, detail, area.w * 0.9f, Text.MICRO), cx, cy + 4.0f,
                    Tokens.INK_3, Text.MICRO);
        }
    }

    private static float faceScale(Rect area) {
        final float shortSide = Math.min(area.w, area.h);
        return Math.max(0.6f, Math.min(2.4f, (float) Math.sqrt(shortSide / 92.0f)));
    }
}
