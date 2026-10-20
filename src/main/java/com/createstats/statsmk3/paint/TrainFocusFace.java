package com.createstats.statsmk3.paint;

import java.util.Locale;

import com.createstats.statsmk3.Mk3Settings;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;
import com.createstats.train.map.TrainConsistArt;
import com.createstats.train.map.TrainGraphArt;
import com.createstats.train.smart.ClientRailNetwork;
import com.createstats.train.smart.RailSnapshot;
import com.createstats.train.smart.TrainIntel;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
final class TrainFocusFace {
    private TrainFocusFace() {
    }

    static void paint(PanelSurface s, Rect area, PanelModel model, Lod lod) {
        final RailSnapshot snap = ClientRailNetwork.get();
        final FaceType ft = FaceType.of(area);
        final RailSnapshot.TrainState t = resolve(snap, model.focusTrain);

        if (t == null) {
            TrainFaceArt.noData(s, area, ft,
                    model.focusTrain.isEmpty() ? "No train chosen" : "Train not found",
                    model.focusTrain.isEmpty()
                            ? "Pick one in the display's Trains tab" : model.focusTrain);
            return;
        }

        final int livery = TrainGraphArt.trainColor(t, t.id);
        final String cause = TrainIntel.cause(snap, t);
        final int causeColor = TrainFaceArt.color(TrainIntel.causeSeverity(t));

        if (lod.isGlyph()) {
            glyph(s, area, ft, t, livery, cause);
            return;
        }

        final TrainFaceArt.Head head = TrainFaceArt.header(s, area, ft, lod, t.name,
                Text.track(phase(t)), TrainFaceArt.health(t), subtitle(t));
        Rect body = new Rect(area.x + ft.pad, head.bottom(), area.w - ft.pad * 2.0f,
                area.bottom() - ft.pad - head.bottom());
        if (body.h < 10.0f || body.w < 24.0f) {
            return;
        }

        final float scheduleW = s.textWidth("MMMMMMMMMMMMMM", ft.micro) + ft.pad * 2.0f;
        if (lod.showsLabels() && body.w > scheduleW * 2.6f && body.h > 60.0f) {
            final Rect right = new Rect(body.right() - scheduleW, body.y, scheduleW, body.h);
            drawSchedule(s, right, ft, snap, t);
            Shapes.rect(s, right.x - ft.pad * 0.5f, body.y, Math.max(1.0f, ft.hair), body.h,
                    Tokens.alpha(Tokens.LINE, 0.9f));
            body = new Rect(body.x, body.y, body.w - scheduleW - ft.pad, body.h);
        }

        if (!cause.isEmpty() && model.faceFlag(Mk3Settings.FACE_CAUSE)) {
            final float h = Math.min(body.h * 0.34f, Text.lineHeight(ft.head) + ft.gap * 3.0f);
            if (h >= Text.lineHeight(ft.micro) + ft.gap) {
                final Rect band = new Rect(body.x, body.y, body.w, h);
                Shapes.roundedRect(s, band, 2.0f * ft.unit, Tokens.alpha(causeColor, 0.15f));
                final float scale = Text.fitScale(s, cause, band.w - ft.pad * 2.0f,
                        band.h - ft.gap, ft.head, ft.nano);
                s.textV(Text.truncate(s, cause, band.w - ft.pad * 2.0f, scale),
                        band.x + ft.pad, band.centerY(), causeColor, scale);
                body = new Rect(body.x, band.bottom() + ft.gap, body.w,
                        body.bottom() - band.bottom() - ft.gap);
            }
        }

        final float speedH = Math.min(body.h * (body.h > 90.0f ? 0.40f : 0.55f),
                Text.lineHeight(ft.hero) + ft.gap * 2.0f);
        if (speedH >= Text.lineHeight(ft.row)) {
            drawSpeed(s, new Rect(body.x, body.y, body.w, speedH), ft, t, livery, model);
            body = new Rect(body.x, body.y + speedH + ft.gap, body.w, body.h - speedH - ft.gap);
        }

        final float legH = Text.lineHeight(ft.micro) * 2.0f + ft.gap * 3.0f;
        if (body.h >= legH && lod.showsLabels()) {
            drawLeg(s, new Rect(body.x, body.y, body.w, legH), ft, snap, t);
            body = new Rect(body.x, body.y + legH + ft.gap, body.w, body.h - legH - ft.gap);
        }

        if (body.h >= 14.0f * ft.unit && lod.showsLabels()) {
            final float cargoH = model.faceFlag(Mk3Settings.FACE_CARGO)
                    ? Text.lineHeight(ft.micro) + ft.gap : 0.0f;
            final float rakeH = Math.min(body.h - cargoH, Math.max(15.0f, 22.0f * ft.unit));
            TrainConsistArt.draw(s, new Rect(body.x, body.y, body.w, rakeH), t, livery);
            if (cargoH > 0.0f) {
                s.text(Text.truncate(s, cargo(t), body.w, ft.micro), body.x,
                        body.y + rakeH + ft.gap * 0.5f, Tokens.INK_3, ft.micro);
            }
            final Rect spare = new Rect(body.x, body.y + rakeH + cargoH + ft.gap, body.w,
                    body.bottom() - body.y - rakeH - cargoH - ft.gap);
            if (spare.h >= Text.lineHeight(ft.micro) * 3.0f) {
                TrainStatusFace.supplement(s, spare, ft, lod, snap);
            }
        }
    }

    private static void drawSchedule(PanelSurface s, Rect r, FaceType ft, RailSnapshot snap,
                                     RailSnapshot.TrainState t) {
        s.text(Text.track("Calling at"), r.x, r.y, Tokens.INK_3, ft.micro);
        final float headH = Text.lineHeight(ft.micro) + ft.gap;
        Shapes.rect(s, r.x, r.y + headH - ft.gap * 0.5f, r.w, Math.max(1.0f, ft.hair),
                Tokens.alpha(Tokens.LINE, 0.9f));
        final Rect list = new Rect(r.x, r.y + headH, r.w, r.h - headH);
        if (t.stops.isEmpty()) {
            s.textCenterV("No schedule", list.centerX(), list.y + Text.lineHeight(ft.micro),
                    Tokens.INK_3, ft.micro);
            return;
        }
        final int count = t.stops.size();
        final int from = Math.max(0, Math.min(t.currentEntry, count - 1));
        final TrainFaceArt.Rows plan = TrainFaceArt.rows(list.h, count,
                Text.lineHeight(ft.nano) + ft.gap * 0.5f,
                Text.lineHeight(ft.micro) + ft.gap * 0.75f);

        for (int i = 0; i < plan.count() && i < count; i++) {
            final String stop = t.stops.get((from + i) % count);
            final Rect row = new Rect(list.x, list.y + i * plan.height(), list.w, plan.height());
            final boolean next = i == 0;

            final float dotX = row.x + Math.max(2.0f, 2.5f * ft.unit);
            if (i + 1 < plan.count()) {
                Shapes.rect(s, dotX - Math.max(0.5f, ft.hair * 0.5f), row.centerY(),
                        Math.max(1.0f, ft.hair), plan.height(), Tokens.alpha(Tokens.LINE_HI, 0.9f));
            }
            Shapes.circle(s, dotX, row.centerY(), Math.max(1.6f, 2.2f * ft.unit),
                    next ? Tokens.GOOD : Tokens.INK_3);

            final float left = dotX + Math.max(3.0f, 4.0f * ft.unit);
            final String eta = etaAt(snap, t, stop);
            final float etaW = eta.isEmpty() ? 0.0f : s.textWidth(eta, ft.nano) + ft.gap;
            s.textV(Text.truncate(s, stop, row.w - (left - row.x) - etaW, ft.micro), left,
                    row.centerY(), next ? Tokens.INK : Tokens.INK_2, ft.micro);
            if (!eta.isEmpty()) {
                s.textRightV(eta, row.right(), row.centerY(),
                        next ? Tokens.GOOD : Tokens.INK_3, ft.nano);
            }
        }
        if (plan.count() < count) {
            s.textRight("+" + (count - plan.count()), list.right(),
                    list.bottom() - Text.lineHeight(ft.nano), Tokens.INK_3, ft.nano);
        }
    }

    private static String etaAt(RailSnapshot snap, RailSnapshot.TrainState t, String stop) {
        for (final RailSnapshot.Arrival a : snap.arrivals()) {
            if (a.trainId.equals(t.id) && RailSnapshot.matches(stop, a.station)) {
                return a.ticks < 0 ? "" : TrainIntel.eta(a.ticks);
            }
        }
        return "";
    }

    private static void drawSpeed(PanelSurface s, Rect r, FaceType ft, RailSnapshot.TrainState t,
                                  int livery, PanelModel model) {
        final float mps = t.metresPerSecond();
        final String figure = TrainIntel.speed(mps);
        final int color = t.moving() ? Tokens.INK : Tokens.INK_2;

        final float unitW = s.textWidth(TrainIntel.SPEED_UNIT, ft.micro) + ft.gap;
        // one decimal for speed, the second one is noise

        final float scale = Text.fitScale(s, figure, r.w * 0.62f - unitW, r.h, ft.hero, ft.row);
        s.textV(figure, r.x, r.centerY(), color, scale);
        final float figW = s.textWidth(figure, scale);
        s.text(TrainIntel.SPEED_UNIT, r.x + figW + ft.gap,
                r.centerY() + Text.lineHeight(scale) * 0.5f - Text.lineHeight(ft.micro),
                Tokens.INK_3, ft.micro);

        if (!model.faceFlag(Mk3Settings.FACE_SPEED)) {
            return;
        }

        final float colX = r.x + Math.max(figW + unitW + ft.pad, r.w * 0.55f);
        final float colW = r.right() - colX;
        if (colW < s.textWidth("MMMMMM", ft.micro)) {
            return;
        }
        final float top = r.centerY() - Text.lineHeight(ft.micro) * 1.6f;
        final float max = Math.abs(t.maxSpeed) * TrainIntel.TICKS_PER_SECOND;
        if (max > 0.01f) {
            final float frac = mps / max;
            s.textRight(Math.round(frac * 100.0f) + "% of " + TrainIntel.speed(max), r.right(),
                    top, Tokens.INK_3, ft.micro);
            TrainFaceArt.meter(s, new Rect(colX, top + Text.lineHeight(ft.micro) + ft.gap * 0.5f,
                            colW, Math.max(2.0f, 3.0f * ft.unit)), frac,
                    t.moving() ? Tokens.GOOD : Tokens.INK_3);
        }
        final StringBuilder flags = new StringBuilder();
        if (t.manual) {
            flags.append("manual");
        }
        if (t.backwards) {
            flags.append(flags.length() > 0 ? " · " : "").append("reversed");
        }
        if (t.doubleEnded) {
            flags.append(flags.length() > 0 ? " · " : "").append("double ended");
        }
        if (t.fuelTicks > 0) {
            flags.append(flags.length() > 0 ? " · " : "").append("fuel ")
                    .append(TrainIntel.duration(t.fuelTicks / TrainIntel.TICKS_PER_SECOND));
        }
        if (flags.length() > 0) {
            s.textRight(Text.truncate(s, flags.toString(), colW, ft.nano), r.right(),
                    top + Text.lineHeight(ft.micro) * 2.0f + ft.gap,
                    t.manual ? Tokens.CAUTION : Tokens.INK_3, ft.nano);
        }
    }

    private static void drawLeg(PanelSurface s, Rect r, FaceType ft, RailSnapshot snap,
                                RailSnapshot.TrainState t) {
        final RailSnapshot.Station from = t.currentStation == null ? null
                : snap.station(t.currentStation);
        final String origin = from != null ? from.name
                : t.stops.isEmpty() ? "" : t.stops.get(Math.max(0,
                        Math.min(t.currentEntry, t.stops.size() - 1)));
        final String dest = t.destinationName.isEmpty() ? "next stop" : t.destinationName;

        final float labelY = r.y;
        final float leftW = s.textWidth(origin, ft.micro);
        final float rightW = s.textWidth(dest, ft.micro);
        if (leftW + rightW + ft.pad * 2.0f < r.w) {
            s.text(origin, r.x, labelY, Tokens.INK_3, ft.micro);
            s.textRight(dest, r.right(), labelY, Tokens.INK_2, ft.micro);
        } else {
            s.text(Text.truncate(s, "to " + dest, r.w, ft.micro), r.x, labelY, Tokens.INK_2,
                    ft.micro);
        }

        final float railY = labelY + Text.lineHeight(ft.micro) + ft.gap;
        final float railH = Math.max(2.0f, 3.0f * ft.unit);
        final float progress = t.legProgress();
        TrainFaceArt.meter(s, new Rect(r.x, railY, r.w, railH), progress,
                t.moving() ? Tokens.GOOD : Tokens.CAUTION);
        Shapes.circle(s, r.x + r.w * Math.max(0.02f, Math.min(progress, 0.98f)),
                railY + railH * 0.5f, Math.max(2.0f, 3.0f * ft.unit), Tokens.INK);

        final float footY = railY + railH + ft.gap;
        if (footY + Text.lineHeight(ft.micro) > r.bottom() + 1.0f) {
            return;
        }
        s.text(TrainIntel.distance(t.distanceToDestination) + " to go", r.x, footY, Tokens.INK_3,
                ft.micro);
        final float late = t.secondsLate();
        if (!Float.isNaN(late) && !t.manual) {
            s.textRight(TrainIntel.lateness(late), r.right(), footY,
                    TrainFaceArt.latenessColor(late), ft.micro);
        } else if (t.legPlannedTicks > 0) {
            s.textRight("booked " + TrainIntel.duration(
                            t.legPlannedTicks / TrainIntel.TICKS_PER_SECOND),
                    r.right(), footY, Tokens.INK_3, ft.micro);
        }
    }

    static RailSnapshot.TrainState resolve(RailSnapshot snap, String name) {
        if (name != null && !name.isBlank()) {
            for (final RailSnapshot.TrainState t : snap.trains()) {
                if (t.name.equalsIgnoreCase(name)) {
                    return t;
                }
            }
            for (final RailSnapshot.TrainState t : snap.trains()) {
                if (RailSnapshot.matches(name, t.name)) {
                    return t;
                }
            }
            return null;
        }
        RailSnapshot.TrainState best = null;
        int bestScore = Integer.MIN_VALUE;
        for (final RailSnapshot.TrainState t : snap.trains()) {
            final int score = t.unhealthy() ? 300
                    : t.waitingForSignal ? 200 + Math.min(99, t.ticksWaitingForSignal / 20)
                    : Math.round(t.metresPerSecond() * 10.0f);
            if (score > bestScore) {
                bestScore = score;
                best = t;
            }
        }
        return best;
    }

    private static String phase(RailSnapshot.TrainState t) {
        if (t.derailed) {
            return "derailed";
        }
        if (t.manual) {
            return "manual";
        }
        if (t.docked()) {
            return "at platform";
        }
        return switch (t.phase) {
            case IN_TRANSIT -> t.moving() ? "running" : "stopped";
            case NONE -> t.hasSchedule ? "idle" : "no schedule";
            default -> t.phase.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }

    private static String subtitle(RailSnapshot.TrainState t) {
        final StringBuilder sb = new StringBuilder();
        if (!t.scheduleTitle.isEmpty()) {
            sb.append(t.scheduleTitle);
        }
        if (t.carriages > 0) {
            sb.append(sb.length() > 0 ? " · " : "").append(t.carriages)
                    .append(t.carriages == 1 ? " car" : " cars");
        }
        if (t.length > 0.5f) {
            sb.append(sb.length() > 0 ? " · " : "").append(Math.round(t.length)).append(" m");
        }
        return sb.toString();
    }

    private static String cargo(RailSnapshot.TrainState t) {
        final StringBuilder sb = new StringBuilder();
        if (t.itemCount > 0) {
            sb.append(Text.group(t.itemCount));
            if (!t.topItem.isEmpty()) {
                sb.append(' ').append(t.topItem);
            }
        }
        if (t.fluidAmount > 0) {
            sb.append(sb.length() > 0 ? " · " : "").append(Text.compact(t.fluidAmount)).append(" mB");
            if (!t.topFluid.isEmpty()) {
                sb.append(' ').append(t.topFluid);
            }
        }
        if (sb.length() == 0) {
            sb.append("empty");
        }
        if (t.occupiedBlocks + t.reservedBlocks > 0) {
            sb.append(" · ").append(t.occupiedBlocks).append('+').append(t.reservedBlocks)
                    .append(" blocks");
        }
        return sb.toString();
    }

    private static void glyph(PanelSurface s, Rect area, FaceType ft, RailSnapshot.TrainState t,
                              int livery, String cause) {
        final int color = TrainFaceArt.health(t);
        Shapes.rect(s, area, Tokens.alpha(color, 0.16f));
        s.advanceLayer();
        final String word = t.moving() ? TrainIntel.speed(t.metresPerSecond())
                : cause.isEmpty() ? "IDLE" : Text.track(cause.split(" ")[0]);
        final float scale = Text.fitScale(s, word, area.w * 0.84f, area.h * 0.5f, ft.hero, ft.row);
        s.textCenter(word, area.centerX(), area.y + area.h * 0.26f,
                t.moving() ? Tokens.INK : color, scale);
        if (t.moving() && area.h > 30.0f) {
            s.textCenter(TrainIntel.SPEED_UNIT, area.centerX(),
                    area.y + area.h * 0.26f + Text.lineHeight(scale), Tokens.INK_3, ft.micro);
        }
    }
}
