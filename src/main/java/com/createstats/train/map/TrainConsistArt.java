package com.createstats.train.map;

import java.util.List;
import java.util.Locale;

import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;
import com.createstats.train.smart.RailSnapshot;

public final class TrainConsistArt {
    private TrainConsistArt() {
    }

    private static final float MIN_CAR_W = 7.0f;

    private static final float COUPLING = 2.0f;

    public static void draw(PanelSurface c, Rect area, RailSnapshot.TrainState t, int color) {
        if (area.isEmpty() || t == null) {
            return;
        }
        final List<RailSnapshot.Car> cars = t.cars;
        if (cars.isEmpty()) {
            drawUnknown(c, area, t);
            return;
        }

        final Rect body = area.inset(0.0f, 0.0f, 0.0f, 11.0f);
        final float noseW = 7.0f;
        final float usable = body.w - noseW - COUPLING * Math.max(0, cars.size() - 1);
        if (usable <= cars.size() * MIN_CAR_W) {
            drawCompact(c, area, t, color);
            return;
        }

        float span = 0.0f;
        for (final RailSnapshot.Car car : cars) {
            span += Math.max(1, car.spacing);
        }
        final float scale = span <= 0.0f ? 1.0f : usable / span;

        final boolean reversed = t.backwards;
        float x = reversed ? body.right() - noseW : body.x + noseW;
        drawNose(c, reversed ? body.right() : body.x, body.centerY(), body.h * 0.62f, reversed,
                color);

        for (int i = 0; i < cars.size(); i++) {
            final RailSnapshot.Car car = cars.get(i);
            final float w = Math.max(MIN_CAR_W, Math.max(1, car.spacing) * scale);
            final Rect box = new Rect(reversed ? x - w : x, body.y, w, body.h);
            drawCar(c, box, car, color, i == 0);
            x += reversed ? -(w + COUPLING) : w + COUPLING;
        }

        drawCaption(c, area, t);
    }

    private static void drawCar(PanelSurface c, Rect box, RailSnapshot.Car car, int color,
                                boolean leading) {
        final int border = car.stalled ? Tokens.CRITICAL
                : car.blocked ? Tokens.CAUTION
                : car.portal ? Tokens.SERIES[4]
                : Tokens.LINE_HI;
        c.card(box, 2.0f, Tokens.SURFACE, border);

        final float fill = car.fill();
        if (fill > 0.001f) {
            final float h = Math.max(1.5f, (box.h - 3.0f) * Math.min(1.0f, fill));
            final int cargo = car.fluidCapacity > 0 && car.fluid > 0
                    ? Tokens.SERIES[0] : Tokens.SERIES[2];
            Shapes.rect(c, box.x + 1.5f, box.bottom() - 1.5f - h, box.w - 3.0f, h,
                    Tokens.alpha(cargo, 0.55f));
        } else if (car.carriesAnything()) {
            c.hairlineH(box.x + 2.0f, box.bottom() - 2.5f, box.w - 4.0f,
                    Tokens.alpha(Tokens.INK_3, 0.5f));
        }

        if (car.conductor) {
            // "which car has the conductor" is the question

            Shapes.circle(c, box.centerX(), box.y + 3.0f, 1.6f, Tokens.alpha(Tokens.GOOD, 0.95f));
        }
        if (leading) {
            Shapes.rect(c, box.x + 1.0f, box.y + 1.0f, 1.5f, box.h - 2.0f,
                    Tokens.alpha(color, 0.9f));
        }
    }

    private static void drawNose(PanelSurface c, float tipX, float cy, float h, boolean reversed,
                                 int color) {
        final float back = reversed ? tipX - 7.0f : tipX + 7.0f;
        final int fill = Tokens.alpha(color, 0.85f);
        c.tri(tipX, cy, fill, back, cy - h * 0.5f, fill, back, cy + h * 0.5f, fill);
    }

    private static void drawUnknown(PanelSurface c, Rect area, RailSnapshot.TrainState t) {
        final int count = Math.max(1, Math.min(12, t.carriages));
        final Rect body = area.inset(0.0f, 0.0f, 0.0f, 11.0f);
        final float w = (body.w - COUPLING * (count - 1)) / count;
        for (int i = 0; i < count; i++) {
            final Rect box = new Rect(body.x + i * (w + COUPLING), body.y, w, body.h);
            c.card(box, 2.0f, Tokens.alpha(Tokens.SURFACE, 0.6f), Tokens.alpha(Tokens.LINE, 0.7f));
        }
        c.text(t.carriages + (t.carriages == 1 ? " carriage" : " carriages")
                        + "  .  not loaded", area.x, area.bottom() - 10.0f,
                Tokens.INK_3, Text.MICRO);
    }

    private static void drawCompact(PanelSurface c, Rect area, RailSnapshot.TrainState t,
                                    int color) {
        final Rect bar = area.inset(0.0f, 0.0f, 0.0f, 11.0f);
        c.card(bar, 2.0f, Tokens.SURFACE, Tokens.LINE_HI);
        float loaded = 0.0f;
        for (final RailSnapshot.Car car : t.cars) {
            loaded += car.fill();
        }
        final float fraction = t.cars.isEmpty() ? 0.0f : loaded / t.cars.size();
        Shapes.rect(c, bar.x + 1.0f, bar.y + 1.0f, (bar.w - 2.0f) * fraction, bar.h - 2.0f,
                Tokens.alpha(color, 0.4f));
        drawCaption(c, area, t);
    }

    private static void drawCaption(PanelSurface c, Rect area, RailSnapshot.TrainState t) {
        final StringBuilder sb = new StringBuilder();
        sb.append(t.cars.isEmpty() ? t.carriages : t.cars.size());
        sb.append(t.carriages == 1 ? " carriage" : " carriages");
        if (t.length > 0.5f) {
            sb.append("  .  ").append(String.format(Locale.ROOT, "%.0f blocks long", t.length));
        }
        int loaded = 0;
        for (final RailSnapshot.Car car : t.cars) {
            if (car.fill() > 0.01f) {
                loaded++;
            }
        }
        if (loaded > 0) {
            sb.append("  .  ").append(loaded).append(" loaded");
        }
        c.text(Text.truncate(c, sb.toString(), area.w, Text.MICRO), area.x, area.bottom() - 10.0f,
                Tokens.INK_3, Text.MICRO);
    }

    public static String summary(RailSnapshot.TrainState t) {
        if (t == null) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        sb.append(t.cars.isEmpty() ? t.carriages : t.cars.size()).append(" car");
        if (t.length > 0.5f) {
            sb.append("  .  ").append(Math.round(t.length)).append('m');
        }
        if (t.itemCount > 0) {
            sb.append("  .  ").append(Text.group(t.itemCount));
            if (!t.topItem.isEmpty()) {
                sb.append(' ').append(t.topItem);
            }
        } else if (t.fluidAmount > 0) {
            sb.append("  .  ").append(Text.group(t.fluidAmount)).append(" mB");
        }
        if (t.feCapacity > 0) {
            sb.append("  .  FE ").append(t.charge().percent()).append('%');
        }
        return sb.toString();
    }
}
