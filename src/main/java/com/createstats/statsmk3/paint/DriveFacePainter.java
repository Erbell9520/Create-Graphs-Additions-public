package com.createstats.statsmk3.paint;

import java.util.List;
import java.util.Locale;

import com.createstats.ground.DriveMath;
import com.createstats.ground.DriveReadout;
import com.createstats.ground.GroundAlerts;
import com.createstats.ground.GroundFrame;
import com.createstats.ground.GroundSnapshot;
import com.createstats.ground.GroundText;
import com.createstats.statsmk3.Mk3Settings;
import com.createstats.statsmk3.page.DriveArt;
import com.createstats.statsmk3.page.DriveKit;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// dashboard first, read from the seat at a glance
@OnlyIn(Dist.CLIENT)
public final class DriveFacePainter {
    private DriveFacePainter() {
    }

    public static void paint(PanelSurface s, Rect area, PanelModel model, Lod lod) {
        final DriveKit.Look look = model.driveLook == 1 ? DriveKit.Look.RACING : DriveKit.Look.CLUSTER;
        final int ground = look == DriveKit.Look.RACING ? hud(DriveKit.Look.RACING) : model.style.ground;
        Shapes.rect(s, area, ground);
        s.advanceLayer();
        final GroundFrame f = model.drive;
        if (f == null) {
            noFeed(s, area, model.driveLinked);
            return;
        }
        if (lod.isGlyph()) {
            glyph(s, area, f);
            return;
        }
        final FaceType t = FaceType.of(area);
        final Rect body = area.inset(t.pad);
        switch (model.faceMode) {
            case DRIVE_GARAGE -> garage(s, body, f, t, look);
            case DRIVE_TRAIN -> train(s, body, f, t);
            case DRIVE_PIT -> pit(s, body, f, t, look);
            default -> dash(s, body, f, t, look);
        }
        if (f.stale()) {
            stale(s, area, f, t);
        }
    }

    static int hud(DriveKit.Look look) {
        return 0xFF000000 | (com.createstats.driverhelmet.HudTheme.TELEMETRY.plate & 0xFFFFFF);
    }

    static void dash(PanelSurface s, Rect b, GroundFrame f, FaceType t, DriveKit.Look look) {
        final DriveReadout r = f.readout();
        final DriveKit.Boot boot = DriveKit.Boot.at(f);
        if (boot == DriveKit.Boot.DARK) {
            return;
        }
        final boolean check = boot == DriveKit.Boot.CHECK;
        final GroundAlerts.Alert alert = check ? null : f.headline();
        if (alert != null && alert.level() == GroundAlerts.Level.CRITICAL) {
            Shapes.rect(s, b.inset(-t.pad), Tokens.alpha(Tokens.CRITICAL, 0.18f));
        }
        if (b.w < 64.0f || b.h < 40.0f) {
            speedOnly(s, b, f, look, check);
            return;
        }
        if (r.state != DriveReadout.State.DRIVING && r.state != DriveReadout.State.TOWED
                && r.state != DriveReadout.State.RUNAWAY && !check) {
            standing(s, b, f, t, look);
            return;
        }
        if (b.w < 150.0f) {
            if (b.h >= b.w * 1.6f && b.h >= 150.0f) {
                tallDash(s, b, f, t, look, alert, check);
            } else {
                smallDash(s, b, f, t, look, alert, check);
            }
            return;
        }
        if (b.h < 130.0f) {
            wideDash(s, b, f, t, look, alert, check);
            return;
        }
        fullDash(s, b, f, t, look, alert, check);
    }

    static void speedOnly(PanelSurface s, Rect b, GroundFrame f, DriveKit.Look look, boolean check) {
        DriveKit.speed(s, b, f.readout(), look, check);
    }

    static Rect banner(PanelSurface s, Rect b, GroundAlerts.Alert a, FaceType t) {
        final float h = Math.max(10.0f, FaceType.lh(t.row) + t.gap * 2.0f);
        final Rect band = new Rect(b.x, b.y, b.w, h);
        final int col = DriveKit.alertColor(a.level());
        Shapes.roundedRect(s, band, Math.min(4.0f, h * 0.3f), col);
        final String text = DriveKit.fitting(s, b.w - t.gap * 4.0f, t.row, a.text(), a.name());
        final String shown = text == null ? Text.truncate(s, a.name(), b.w - t.gap * 4.0f, t.micro) : text;
        final float scale = text == null ? t.micro : t.row;
        s.textCenterV(shown, band.centerX(), band.centerY(), Tokens.CANVAS, scale);
        return new Rect(b.x, band.bottom() + t.gap * 2.0f, b.w, b.h - h - t.gap * 2.0f);
    }

    static void smallDash(PanelSurface s, Rect b, GroundFrame f, FaceType t, DriveKit.Look look, GroundAlerts.Alert alert,
                          boolean check) {
        final DriveReadout r = f.readout();
        Rect rest = b;
        final float stripH = Math.max(7.0f, b.h * 0.16f);
        if (alert != null) {
            rest = banner(s, b, alert, t);
        } else if (look == DriveKit.Look.RACING) {
            DriveKit.shiftBar(s, new Rect(b.x, b.y, b.w, Math.max(3.0f, stripH * 0.5f)), f, f.tick() / 20.0, check);
            rest = new Rect(b.x, b.y + stripH * 0.5f + t.gap * 2.0f, b.w, b.h - stripH * 0.5f - t.gap * 2.0f);
        } else {
            DriveKit.telltaleRow(s, new Rect(b.x, b.y, b.w, stripH), f, look, check);
            rest = new Rect(b.x, b.y + stripH + t.gap, b.w, b.h - stripH - t.gap);
        }
        final float pedalH = Math.max(2.0f, rest.h * 0.06f);
        final Rect hero = new Rect(rest.x, rest.y, rest.w, rest.h - pedalH - t.gap * 3.0f);
        final float gearH = Math.min(hero.h * 0.86f, hero.w * 0.3f * 1.25f);
        final float gearW = gearH / 1.25f;
        DriveKit.gear(s, new Rect(hero.x, hero.y + (hero.h - gearH) * 0.5f, gearW, gearH), f.snapshot().drive().gear(),
                look, check);
        DriveKit.speedWithUnit(s, new Rect(hero.x + gearW + t.gap * 2.0f, hero.y, hero.w - gearW - t.gap * 2.0f, hero.h),
                r, look, check);
        pedals(s, new Rect(rest.x, rest.bottom() - pedalH, rest.w, pedalH), f);
    }

    static void tallDash(PanelSurface s, Rect b, GroundFrame f, FaceType t, DriveKit.Look look, GroundAlerts.Alert alert,
                         boolean check) {
        final float top = Math.min(b.h * 0.5f, b.w * 1.05f);
        smallDash(s, new Rect(b.x, b.y, b.w, top), f, t, look, alert, check);
        DriveKit.top(s, new Rect(b.x, b.y + top + t.gap * 3.0f, b.w, b.h - top - t.gap * 3.0f), f, DriveKit.Plan.DASH);
    }

    static void wideDash(PanelSurface s, Rect b, GroundFrame f, FaceType t, DriveKit.Look look, GroundAlerts.Alert alert,
                         boolean check) {
        final DriveReadout r = f.readout();
        Rect rest = b;
        if (alert != null) {
            rest = banner(s, b, alert, t);
        } else if (look == DriveKit.Look.RACING) {
            final float bar = Math.max(3.0f, b.h * 0.08f);
            DriveKit.shiftBar(s, new Rect(b.x, b.y, b.w, bar), f, f.tick() / 20.0, check);
            rest = new Rect(b.x, b.y + bar + t.gap * 2.0f, b.w, b.h - bar - t.gap * 2.0f);
        }
        final float gearW = Math.min(rest.h * 0.8f, rest.w * 0.16f);
        DriveKit.gear(s, new Rect(rest.x, rest.y + (rest.h - gearW * 1.2f) * 0.5f, gearW, gearW * 1.2f),
                f.snapshot().drive().gear(), look, check);
        final float sideW = rest.w * 0.34f;
        final Rect speed = new Rect(rest.x + gearW + t.gap * 3.0f, rest.y, rest.w - gearW - sideW - t.gap * 6.0f, rest.h);
        DriveKit.speedWithUnit(s, speed, r, look, check);
        final Rect side = new Rect(rest.right() - sideW, rest.y, sideW, rest.h);
        final float tellH = Math.max(7.0f, side.h * 0.24f);
        if (alert == null) {
            DriveKit.telltaleRow(s, new Rect(side.x, side.y, side.w, tellH), f, look, check);
        }
        final float ph = Math.max(2.0f, side.h * 0.08f);
        final float py = side.y + tellH + t.gap * 3.0f;
        pedalLabel(s, side, py, "THR", f.snapshot().drive().throttle(), true, Tokens.SERIES[3], ph, t);
        pedalLabel(s, side, py + ph + FaceType.lh(t.micro) + t.gap * 2.0f, "BRK", f.snapshot().drive().braking() ? 1.0 : 0.0,
                false, Tokens.CRITICAL, ph, t);
    }

    static void pedalLabel(PanelSurface s, Rect side, float y, String label, double value, boolean signed, int color,
                           float h, FaceType t) {
        if (y + h + FaceType.lh(t.micro) > side.bottom() + 0.5f) {
            return;
        }
        final float lw = s.textWidth("THR", t.micro) + t.gap * 2.0f;
        s.textV(label, side.x, y + h * 0.5f, Tokens.INK_3, t.micro);
        DriveKit.pedal(s, new Rect(side.x + lw, y, side.w - lw, h), value, signed, color);
    }

    static void fullDash(PanelSurface s, Rect b, GroundFrame f, FaceType t, DriveKit.Look look, GroundAlerts.Alert alert,
                         boolean check) {
        final DriveReadout r = f.readout();
        Rect rest = b;
        final float stripH = Math.max(8.0f, Math.min(b.h * 0.1f, 22.0f * t.unit));
        if (alert != null) {
            rest = banner(s, b, alert, t);
        } else {
            if (look == DriveKit.Look.RACING) {
                DriveKit.shiftBar(s, new Rect(b.x, b.y, b.w, stripH * 0.55f), f, f.tick() / 20.0, check);
                DriveKit.telltaleRow(s, new Rect(b.x, b.y + stripH * 0.55f + t.gap * 2.0f, b.w, stripH * 0.8f), f, look,
                        check);
                rest = new Rect(b.x, b.y + stripH * 1.35f + t.gap * 4.0f, b.w, b.h - stripH * 1.35f - t.gap * 4.0f);
            } else {
                DriveKit.telltaleRow(s, new Rect(b.x, b.y, b.w, stripH), f, look, check);
                rest = new Rect(b.x, b.y + stripH + t.gap * 2.0f, b.w, b.h - stripH - t.gap * 2.0f);
            }
        }
        final float heroH = rest.h * 0.34f;
        final float gearW = Math.min(heroH * 0.8f, rest.w * 0.2f);
        DriveKit.gear(s, new Rect(rest.x, rest.y, gearW, heroH), f.snapshot().drive().gear(), look, check);
        DriveKit.speedWithUnit(s, new Rect(rest.x + gearW + t.gap * 3.0f, rest.y, rest.w - gearW - t.gap * 3.0f, heroH), r,
                look, check);
        final float below = rest.y + heroH + t.gap * 3.0f;
        final float footH = FaceType.lh(t.micro) + t.gap;
        final Rect lower = new Rect(rest.x, below, rest.w, rest.bottom() - below - footH);
        final boolean side = lower.w >= lower.h * 1.5f && lower.w >= 180.0f;
        final Rect plan = side ? new Rect(lower.x, lower.y, lower.w * 0.58f, lower.h) : lower;
        if (plan.h >= 24.0f) {
            DriveKit.top(s, plan, f, DriveKit.Plan.DASH);
        }
        if (side) {
            final Rect right = new Rect(plan.right() + t.gap * 3.0f, lower.y, lower.right() - plan.right() - t.gap * 3.0f,
                    lower.h);
            final float rad = Math.min(right.w * 0.5f, right.h * 0.38f);
            DriveKit.frictionCircle(s, right.centerX(), right.y + rad, rad, f);
            final float py = right.y + rad * 2.0f + t.gap * 3.0f;
            final float ph = Math.max(2.0f, right.h * 0.05f);
            pedalLabel(s, right, py, "THR", f.snapshot().drive().throttle(), true, Tokens.SERIES[3], ph, t);
            pedalLabel(s, right, py + ph + FaceType.lh(t.micro) + t.gap * 2.0f, "BRK",
                    f.snapshot().drive().braking() ? 1.0 : 0.0, false, Tokens.CRITICAL, ph, t);
        } else {
            pedals(s, new Rect(lower.x, lower.bottom() - Math.max(2.0f, lower.h * 0.04f), lower.w,
                    Math.max(2.0f, lower.h * 0.04f)), f);
        }
        final String foot = alert != null ? alert.detail() : r.state == DriveReadout.State.TOWED ? towedLine(f) : footLine(f);
        s.text(Text.truncate(s, foot, rest.w, t.micro), rest.x, rest.bottom() - FaceType.lh(t.micro), Tokens.INK_3, t.micro);
    }

    static String footLine(GroundFrame f) {
        final DriveReadout r = f.readout();
        final GroundSnapshot snap = r.snapshot;
        final StringBuilder b = new StringBuilder();
        b.append(String.format(Locale.ROOT, "%03d", Math.round(r.heading) % 360)).append("   Y ").append(Math.round(r.altitude));
        if (Math.abs(r.grade) >= 1.0) {
            b.append(String.format(Locale.ROOT, "   %+.0f%%", r.grade));
        }
        b.append("   ").append(GroundText.distance(snap.odometer().trip())).append(" trip");
        return b.toString();
    }

    static String towedLine(GroundFrame f) {
        final GroundSnapshot snap = f.snapshot();
        final String by = snap.identity().towedBy();
        return "TOWED" + (by.isBlank() ? "" : " by " + by) + ", " + GroundText.distance(snap.odometer().trip()) + " trip";
    }

    static void pedals(PanelSurface s, Rect r, GroundFrame f) {
        final GroundSnapshot.Drive d = f.snapshot().drive();
        if (d.braking()) {
            DriveKit.pedal(s, r, 1.0, false, Tokens.CRITICAL);
        } else {
            DriveKit.pedal(s, r, d.throttle(), true, Tokens.SERIES[3]);
        }
    }

    static void standing(PanelSurface s, Rect b, GroundFrame f, FaceType t, DriveKit.Look look) {
        final DriveReadout r = f.readout();
        final String word = switch (r.state) {
            case PARKED -> "PARKED";
            case OFF_CRAFT -> "NO CRAFT";
            default -> "READY";
        };
        final float gearW = Math.min(b.h * 0.5f, b.w * 0.3f);
        DriveKit.gear(s, new Rect(b.x, b.y, gearW, gearW * 1.15f), f.snapshot().drive().gear(), look, false);
        final float scale = DriveKit.fit(s, word, b.w - gearW - t.gap * 3.0f, gearW * 0.6f, t.title * 1.6f);
        s.text(word, b.x + gearW + t.gap * 3.0f, b.y + (gearW * 1.15f - FaceType.lh(scale)) * 0.5f,
                r.state == DriveReadout.State.PARKED ? Tokens.INK_2 : Tokens.INK, scale);
        float y = b.y + gearW * 1.15f + t.gap * 3.0f;
        final GroundSnapshot.Odometer o = f.snapshot().odometer();
        final String odo = "ODO " + GroundText.distance(o.odometer());
        if (y + FaceType.lh(t.row) <= b.bottom()) {
            s.text(Text.truncate(s, odo, b.w, t.row), b.x, y, Tokens.INK_2, t.row);
            y += FaceType.lh(t.row) + t.gap;
        }
        final String ready = readiness(f);
        if (y + FaceType.lh(t.micro) <= b.bottom()) {
            s.text(Text.truncate(s, ready, b.w, t.micro), b.x, y, readyColor(f), t.micro);
            y += FaceType.lh(t.micro) + t.gap * 3.0f;
        }

        if (b.bottom() - y >= 48.0f && b.w >= 80.0f) {
            DriveKit.top(s, new Rect(b.x, y, b.w, b.bottom() - y), f, new DriveKit.Plan(true, false, true, false));
        }
    }

    static void garage(PanelSurface s, Rect b, GroundFrame f, FaceType t, DriveKit.Look look) {
        final DriveReadout r = f.readout();
        final GroundSnapshot snap = r.snapshot;
        final String word = readyWord(f);
        final int wordColor = readyColor(f);
        if (b.w < 80.0f || b.h < 60.0f) {
            s.textCenterV(word, b.centerX(), b.centerY(), wordColor, DriveKit.fit(s, word, b.w, b.h, t.hero));
            return;
        }
        final String name = snap.identity().name().isBlank() ? "Vehicle" : snap.identity().name();
        if (b.h < 120.0f && b.w < b.h * 1.6f) {
            compactGarage(s, b, f, t, name, word, wordColor);
            return;
        }

        final float pillScale = t.row;
        final float pillH = FaceType.lh(pillScale) + t.gap * 2.0f;
        final float pillW = Math.min(b.w * 0.5f, s.textWidth(word, pillScale) + t.gap * 6.0f);
        final Rect pill = new Rect(b.right() - pillW, b.y, pillW, pillH);
        Shapes.roundedRect(s, pill, pillH * 0.5f, Tokens.alpha(wordColor, 0.16f));
        Shapes.roundedRectOutline(s, pill, pillH * 0.5f, 1.0f, Tokens.alpha(wordColor, 0.7f));
        s.textCenterV(Text.truncate(s, word, pillW - t.gap * 4.0f, pillScale), pill.centerX(), pill.centerY(), wordColor,
                pillScale);
        final float nameW = b.w - pillW - t.gap * 3.0f;
        final float nameScale = Math.max(t.row, Math.min(t.title, DriveKit.fit(s, name, nameW, FaceType.lh(t.title), t.title)));
        s.text(Text.truncate(s, name, nameW, nameScale), b.x, b.y, Tokens.INK, nameScale);
        float y = b.y + Math.max(FaceType.lh(nameScale), pillH) + t.gap;
        final String kind = snap.identity().profile();
        if (!kind.isBlank()) {
            s.text(Text.truncate(s, kind, b.w, t.micro), b.x, y, Tokens.INK_3, t.micro);
            y += FaceType.lh(t.micro);
        }
        y += t.gap * 3.0f;
        final String[][] rows = facts(f);
        final float factH = FaceType.lh(t.micro) + FaceType.lh(t.row) + t.gap;
        final float detailH = FaceType.lh(t.micro) + t.gap * 2.0f;
        final boolean wide = b.w >= b.h * 1.4f;
        // never cut "(MODEL)", that's the honest part
        final boolean twoCols = !wide && s.textWidth("CRUISE (MODEL)", t.micro) <= b.w * 0.5f - t.gap * 2.0f;
        final int cols = twoCols ? 2 : 1;
        final Rect portrait;
        final Rect facts;
        if (wide) {
            portrait = new Rect(b.x, y, b.w * 0.52f, b.bottom() - y);
            facts = new Rect(portrait.right() + t.gap * 4.0f, y, b.right() - portrait.right() - t.gap * 4.0f, b.bottom() - y);
        } else {
            final int factRows = (rows.length + cols - 1) / cols;
            final float factsH = detailH + (factH + t.gap) * factRows;
            portrait = new Rect(b.x, y, b.w, b.bottom() - y - factsH - t.gap * 2.0f);
            facts = new Rect(b.x, portrait.bottom() + t.gap * 2.0f, b.w, b.bottom() - portrait.bottom() - t.gap * 2.0f);
        }
        if (portrait.h >= 30.0f) {
            DriveKit.top(s, portrait, f, new DriveKit.Plan(true, false, true, false));
        }
        final String detail = readiness(f);
        s.text(Text.truncate(s, detail, facts.w, t.micro), facts.x, facts.y, wordColor == Tokens.INK ? Tokens.INK_2
                : Tokens.mix(wordColor, Tokens.INK_2, 0.35f), t.micro);
        final float top = facts.y + detailH;
        final float colW = facts.w / cols;
        for (int i = 0; i < rows.length; i++) {
            final float fx = facts.x + (i % cols) * colW;
            final float fy = top + (i / cols) * (factH + t.gap);
            if (fy + factH > facts.bottom() + 0.5f) {
                break;
            }
            if (s.textWidth(rows[i][0], t.micro) > colW - t.gap) {
                continue;
            }
            s.text(rows[i][0], fx, fy, Tokens.INK_3, t.micro);
            s.text(Text.truncate(s, rows[i][1], colW - t.gap * 2.0f, t.row), fx, fy + FaceType.lh(t.micro), Tokens.INK,
                    t.row);
        }
    }

    static void compactGarage(PanelSurface s, Rect b, GroundFrame f, FaceType t, String name, String word, int wordColor) {
        final float nameH = FaceType.lh(t.micro);
        s.text(Text.truncate(s, name, b.w, t.micro), b.x, b.y, Tokens.INK_2, t.micro);
        final String odo = "ODO " + GroundText.distance(f.snapshot().odometer().odometer());
        final float odoH = FaceType.lh(t.micro);
        final Rect mid = new Rect(b.x, b.y + nameH + t.gap, b.w, b.h - nameH - odoH - t.gap * 2.0f);
        final float scale = DriveKit.fit(s, word, mid.w, mid.h, t.hero);
        s.textCenterV(word, mid.centerX(), mid.centerY(), wordColor, scale);
        s.text(Text.truncate(s, odo, b.w, t.micro), b.x, b.bottom() - odoH, Tokens.INK_3, t.micro);
    }

    static String[][] facts(GroundFrame f) {
        final DriveReadout r = f.readout();
        final GroundSnapshot.Odometer o = r.snapshot.odometer();
        final String trip = GroundText.distance(o.trip())
                + (o.tripTicks() >= 20L ? ", " + GroundText.duration(o.tripTicks() / 20.0) : "");
        return new String[][]{
                {"ODOMETER", GroundText.distance(o.odometer())},
                {"TRIP", trip},
                {"TOP SPEED", Double.isFinite(o.topSpeed()) ? Math.round(o.topSpeed() * DriveMath.KMH) + " km/h" : "--"},
                {"CRUISE (MODEL)", r.model.known() ? Math.round(r.model.cruise() * DriveMath.KMH) + " km/h" : "--"}};
    }

    static String readyWord(GroundFrame f) {
        final DriveReadout r = f.readout();
        final GroundSnapshot snap = r.snapshot;
        if (r.state == DriveReadout.State.RUNAWAY) {
            return "RUNAWAY";
        }
        if (r.state == DriveReadout.State.DRIVING) {
            return Math.round(r.speedKmh) + " km/h";
        }
        if (r.state == DriveReadout.State.TOWED) {
            return "TOWED";
        }
        if (!snap.identity().brakesAvailable()) {
            return "NO BRAKES";
        }
        if (r.noRotation > 0 || r.overstressed > 0) {
            return "ENGINE OFF";
        }
        if (!snap.problems().isEmpty()) {
            return snap.problems().size() == 1 ? "1 PROBLEM" : snap.problems().size() + " PROBLEMS";
        }
        return "READY";
    }

    static int readyColor(GroundFrame f) {
        final String w = readyWord(f);
        if (w.equals("READY")) {
            return Tokens.GOOD;
        }
        if (w.equals("RUNAWAY") || w.equals("NO BRAKES")) {
            return Tokens.CRITICAL;
        }
        if (w.equals("ENGINE OFF") || w.endsWith("PROBLEM") || w.endsWith("PROBLEMS")) {
            return Tokens.CAUTION;
        }
        return Tokens.INK;
    }

    static String readiness(GroundFrame f) {
        final DriveReadout r = f.readout();
        final GroundSnapshot snap = r.snapshot;
        if (r.state == DriveReadout.State.DRIVING) {
            return snap.drive().driver().isBlank() ? "Someone is driving" : snap.drive().driver() + " is driving";
        }
        if (!snap.problems().isEmpty()) {
            return snap.problems().get(0);
        }
        for (final GroundSnapshot.Governor g : snap.governors()) {
            if (!g.machine() && g.supply() == GroundSnapshot.Supply.NO_ROTATION) {
                return g.name() + " gets no rotation: the engine is off, dry, or disconnected";
            }
        }
        if (r.state == DriveReadout.State.PARKED) {
            return r.engines > 0 ? "Parked, engine turning" : "Parked";
        }
        return r.engines > 0 ? "Engine turning, nobody at the helm" : "Nobody at the helm";
    }

    static void pit(PanelSurface s, Rect b, GroundFrame f, FaceType t, DriveKit.Look look) {
        final float k = Math.max(1.0f, (float) Math.sqrt(Math.min(b.w, b.h) / 180.0f));
        final PanelSurface v = new ScaledSurface(s, b.x, b.y, k);
        final Rect vb = ScaledSurface.virtual(b, k);
        if (vb.w < 260.0f || vb.h < 160.0f) {
            dash(s, b, f, t, look);
            return;
        }
        DriveArt.telemetry(v, vb, f);
    }

    static void train(PanelSurface s, Rect b, GroundFrame f, FaceType t) {
        final DriveReadout r = f.readout();
        final float listH = r.trailers > 0 && b.h >= 90.0f ? Math.min(b.h * 0.34f, (FaceType.lh(t.micro) + t.gap) * 4.0f) : 0.0f;
        DriveKit.top(s, new Rect(b.x, b.y, b.w, b.h - listH), f, DriveKit.Plan.TRAIN);
        float y = b.bottom() - listH + t.gap;
        for (final DriveReadout.UnitRead ur : r.units) {
            if (ur.unit().isTractor() || !ur.unit().role().takesMode()) {
                continue;
            }
            if (y + FaceType.lh(t.micro) > b.bottom()) {
                break;
            }
            final int col = ur.link() == DriveReadout.LinkState.GONE || ur.link() == DriveReadout.LinkState.NEAR_SNAP
                    ? Tokens.CRITICAL : Tokens.INK_2;
            s.text(Text.truncate(s, ur.unit().name() + "  " + GroundText.linkWords(ur), b.w, t.micro), b.x, y, col, t.micro);
            y += FaceType.lh(t.micro) + t.gap;
        }
    }

    static void noFeed(PanelSurface s, Rect area, boolean linked) {
        final String head = linked ? "WAITING" : "NO VEHICLE";
        final float room = area.w - 8.0f;
        final float scale = Math.max(0.5f, Math.min(Math.min(Text.TITLE, area.h / 60.0f),
                room / Math.max(1.0f, s.textWidth(head, 1.0f))));
        final String hint = DriveKit.fitting(s, room, Text.MICRO, linked
                ? new String[]{"for the vehicle's first report", "for its first report", "no report yet"}
                : new String[]{"Display Link a VCC Mk IV Ground", "link a VCC Mk IV", "no link"});
        final float lh = Text.lineHeight(scale);
        final float hh = hint == null ? 0.0f : Text.lineHeight(Text.MICRO) + 3.0f;
        final float top = area.centerY() - (lh + hh) * 0.5f;
        s.textCenter(head, area.centerX(), top, Tokens.INK_3, scale);
        if (hint != null && lh + hh <= area.h - 4.0f) {
            s.textCenter(hint, area.centerX(), top + lh + 3.0f, Tokens.INK_3, Text.MICRO);
        }
    }

    static void stale(PanelSurface s, Rect area, GroundFrame f, FaceType t) {
        Shapes.rect(s, area, Tokens.alpha(Tokens.CANVAS, 0.55f));
        final String text = DriveKit.fitting(s, area.w - 12.0f, t.meta,
                String.format(Locale.ROOT, "NO FEED  %.0f s", Math.min(999.0, f.age())), "NO FEED");
        if (text == null) {
            return;
        }
        final float w = s.textWidth(text, t.meta) + t.gap * 6.0f;
        final float h = FaceType.lh(t.meta) + t.gap * 3.0f;
        final Rect box = new Rect(area.centerX() - w * 0.5f, area.centerY() - h * 0.5f, w, h);
        Shapes.roundedRect(s, box, Math.min(4.0f, h * 0.3f), Tokens.CRITICAL);
        s.textCenterV(text, box.centerX(), box.centerY(), Tokens.CANVAS, t.meta);
    }

    static void glyph(PanelSurface s, Rect area, GroundFrame f) {
        final GroundAlerts.Level worst = f.worst();
        if (worst == GroundAlerts.Level.CRITICAL || worst == GroundAlerts.Level.CAUTION) {
            Shapes.rect(s, area, Tokens.alpha(DriveKit.alertColor(worst), 0.25f));
        }
        final DriveReadout r = f.readout();
        final String text = r.state == DriveReadout.State.DRIVING ? DriveKit.speedText(r) : readyWord(f);
        s.textCenterV(text, area.centerX(), area.centerY(), Tokens.INK, DriveKit.fit(s, text, area.w * 0.8f, area.h * 0.5f,
                Text.HERO * 2.0f));
    }

    static final List<Mk3Settings.FaceMode> MODES = List.of(Mk3Settings.FaceMode.DRIVE_DASH,
            Mk3Settings.FaceMode.DRIVE_GARAGE, Mk3Settings.FaceMode.DRIVE_PIT, Mk3Settings.FaceMode.DRIVE_TRAIN);
}
