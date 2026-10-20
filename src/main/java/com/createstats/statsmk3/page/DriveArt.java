package com.createstats.statsmk3.page;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.createstats.ground.DriveMath;
import com.createstats.ground.DriveReadout;
import com.createstats.ground.GroundAlerts;
import com.createstats.ground.GroundFrame;
import com.createstats.ground.GroundSnapshot;
import com.createstats.ground.GroundText;
import com.createstats.ground.VehicleKind;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class DriveArt {
    public static final String[] TABS = {"Cluster", "Powertrain", "Wheels", "Train", "Telemetry", "Journey"};
    public static final float HEADER_H = 46.0f;
    public static final float TABS_H = 24.0f;
    static final float GAP = Tokens.S3;

    private DriveArt() {
    }

    public static void header(PanelSurface s, Rect r, GroundFrame f, float rightReserved) {
        final GroundSnapshot snap = f.snapshot();
        final DriveReadout rd = f.readout();
        float right = r.right() - rightReserved;
        if (f.stale()) {
            right = pillRight(s, right, r.y + 2.0f, String.format(Locale.ROOT, "NO FEED %.0f s", Math.min(999.0, f.age())),
                    Tokens.CRITICAL, true) - 6.0f;
        } else {
            right = pillRight(s, right, r.y + 2.0f, stateWord(rd), stateColor(rd), rd.state == DriveReadout.State.RUNAWAY)
                    - 6.0f;
        }
        final GroundAlerts.Level worst = f.worst();
        if (worst != null && worst != GroundAlerts.Level.ADVISORY) {
            int n = 0;
            for (final GroundAlerts.Alert a : f.alerts()) {
                if (a.level() != GroundAlerts.Level.ADVISORY) {
                    n++;
                }
            }
            right = pillRight(s, right, r.y + 2.0f, n == 1 ? "1 ALERT" : n + " ALERTS", DriveKit.alertColor(worst), true)
                    - 6.0f;
        }
        final String name = snap.identity().name().isBlank() ? "Vehicle" : snap.identity().name();
        s.text(Text.truncate(s, name, right - r.x - 8.0f, Text.TITLE), r.x, r.y, Tokens.INK, Text.TITLE);
        s.text(Text.truncate(s, subtitle(f), r.w - rightReserved - 8.0f, Text.META), r.x, r.y + 22.0f, Tokens.INK_2,
                Text.META);
    }

    public static final float LOOK_W = 100.0f;
    public static final float FACE_W = 104.0f;
    public static final float COMMAND_W = 96.0f;
    public static final float BUTTON_H = 22.0f;
    public static final float STRIP_H = 28.0f;
    static final float WIDGET_GAP = 6.0f;

    public record StudioLayout(Rect tabs, Rect look, Rect face, Rect commands, float headerReserved, Rect content) {
    }

    // at 1080p gui scale 3 the studio is 497 wide
    public static StudioLayout studioLayout(Rect area, float tabsNeed, int commands) {
        final float rowY = area.y + HEADER_H;
        final float buttons = LOOK_W + WIDGET_GAP + FACE_W;
        final boolean oneRow = area.w >= tabsNeed + WIDGET_GAP * 2.0f + buttons;
        final float tabsW = oneRow ? Math.min(Math.max(tabsNeed, Math.min(480.0f, area.w - buttons - WIDGET_GAP * 2.0f)),
                area.w - buttons - WIDGET_GAP * 2.0f) : Math.min(area.w, Math.max(tabsNeed, Math.min(480.0f, area.w)));
        final Rect tabs = new Rect(area.x, rowY, tabsW, TABS_H);
        final float by = oneRow ? rowY + 1.0f : area.y + (HEADER_H - BUTTON_H) * 0.5f;
        final Rect face = new Rect(area.right() - FACE_W, by, FACE_W, BUTTON_H);
        final Rect look = new Rect(face.x - WIDGET_GAP - LOOK_W, by, LOOK_W, BUTTON_H);
        float top = rowY + TABS_H + 10.0f;
        Rect strip = Rect.EMPTY;
        if (commands > 0) {
            final float w = commands * COMMAND_W + (commands - 1) * WIDGET_GAP;
            strip = new Rect(area.right() - w, top, w, BUTTON_H);
            top += STRIP_H;
        }
        return new StudioLayout(tabs, look, face, strip, oneRow ? 0.0f : buttons + WIDGET_GAP,
                new Rect(area.x, top, area.w, Math.max(0.0f, area.bottom() - top)));
    }

    static String subtitle(GroundFrame f) {
        final GroundSnapshot snap = f.snapshot();
        final StringBuilder b = new StringBuilder(capital(snap.identity().kind().noun));
        if (!snap.identity().profile().isBlank()) {
            b.append("   ").append(snap.identity().profile());
        }
        if (Double.isFinite(snap.mass().mass())) {
            b.append("   ").append(Text.group(Math.round(snap.mass().mass()))).append(" kpg");
        }
        final GroundSnapshot.Drive d = snap.drive();
        if (d.driving() && !d.driver().isBlank()) {
            b.append("   ").append(d.driver()).append(" at the wheel");
        } else if (d.towed()) {
            b.append("   towed").append(snap.identity().towedBy().isBlank() ? "" : " by " + snap.identity().towedBy());
        }
        return b.toString();
    }

    static String stateWord(DriveReadout r) {
        return switch (r.state) {
            case DRIVING -> "DRIVING";
            case TOWED -> "TOWED";
            case PARKED -> "PARKED";
            case IDLE -> "STANDING";
            case RUNAWAY -> "RUNAWAY";
            case OFF_CRAFT -> "NOT ON A CRAFT";
        };
    }

    static int stateColor(DriveReadout r) {
        return switch (r.state) {
            case DRIVING -> Tokens.GOOD;
            case RUNAWAY, OFF_CRAFT -> Tokens.CRITICAL;
            case TOWED -> Tokens.SERIES[0];
            default -> Tokens.INK_2;
        };
    }

    static float pillRight(PanelSurface s, float right, float y, String text, int color, boolean filled) {
        final float scale = Text.META;
        final float w = s.textWidth(text, scale) + 14.0f;
        final Rect r = new Rect(right - w, y, w, 18.0f);
        if (filled) {
            Shapes.roundedRect(s, r, 9.0f, color);
            s.textCenterV(text, r.centerX(), r.centerY(), Tokens.CANVAS, scale);
        } else {
            Shapes.roundedRect(s, r, 9.0f, Tokens.alpha(color, 0.16f));
            Shapes.roundedRectOutline(s, r, 9.0f, 1.0f, Tokens.alpha(color, 0.6f));
            s.textCenterV(text, r.centerX(), r.centerY(), color, scale);
        }
        return r.x;
    }

    static String capital(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static void empty(PanelSurface s, Rect r, boolean linked) {
        final String head = linked ? "Waiting for the vehicle" : "No vehicle linked";
        final String hint = linked ? "The console reports five times a second once its chunk is loaded."
                : "Display Link a VCC Mk IV Ground to this panel, set to Stats Link.";
        s.textCenter(head, r.centerX(), r.centerY() - 16.0f, Tokens.INK_2, Text.SUB);
        s.textCenter(Text.truncate(s, hint, r.w - 24.0f, Text.META), r.centerX(), r.centerY() + 6.0f, Tokens.INK_3,
                Text.META);
    }

    public static void staleVeil(PanelSurface s, Rect r, GroundFrame f) {
        Shapes.rect(s, r, Tokens.alpha(Tokens.CANVAS, 0.62f));
        final String text = String.format(Locale.ROOT, "NO FEED FOR %.0f s: the last report, held", Math.min(999.0, f.age()));
        final float w = Math.min(r.w - 20.0f, s.textWidth(text, Text.BODY) + 24.0f);
        final Rect box = new Rect(r.centerX() - w * 0.5f, r.centerY() - 12.0f, w, 24.0f);
        Shapes.roundedRect(s, box, 6.0f, Tokens.CRITICAL);
        s.textCenterV(Text.truncate(s, text, w - 12.0f, Text.BODY), box.centerX(), box.centerY(), Tokens.CANVAS, Text.BODY);
    }

    public static void paint(PanelSurface s, Rect content, GroundFrame f, int tab, DriveKit.Look look) {
        switch (tab) {
            case 1 -> powertrain(s, content, f);
            case 2 -> wheels(s, content, f);
            case 3 -> train(s, content, f);
            case 4 -> telemetry(s, content, f);
            case 5 -> journey(s, content, f);
            default -> cluster(s, content, f, look);
        }
        if (f.stale()) {
            staleVeil(s, content, f);
        }
    }

    public static void cluster(PanelSurface s, Rect c, GroundFrame f, DriveKit.Look look) {
        final DriveReadout r = f.readout();
        final float planW = Math.max(200.0f, c.w * 0.56f);
        final Rect plan = new Rect(c.x, c.y, planW, c.h);
        final Rect dash = new Rect(plan.right() + GAP, c.y, c.right() - plan.right() - GAP, c.h);
        planCard(s, plan, f);
        dashColumn(s, dash, f, look);
    }

    static void planCard(PanelSurface s, Rect r, GroundFrame f) {
        final DriveReadout rd = f.readout();
        Shapes.card(s, r, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        final float cap = 22.0f;
        final Rect inner = new Rect(r.x + Tokens.S3, r.y + Tokens.S3, r.w - Tokens.S3 * 2, r.h - Tokens.S3 * 2 - cap);
        final boolean train = rd.trailers > 0 || rd.snapshot.setup().articulated();
        DriveKit.top(s, inner, f, train ? DriveKit.Plan.FULL : new DriveKit.Plan(false, true, true, false));
        s.text(Text.truncate(s, planCaption(f), r.w - Tokens.S3 * 2, Text.META), r.x + Tokens.S3,
                r.bottom() - cap + 2.0f, Tokens.INK_2, Text.META);
        s.textRight("FORWARD", r.right() - Tokens.S3, r.y + Tokens.S2, Tokens.INK_3, Text.MICRO);
    }

    static String planCaption(GroundFrame f) {
        final DriveReadout r = f.readout();
        final GroundSnapshot snap = r.snapshot;
        final VehicleKind kind = snap.identity().kind();
        if (kind == VehicleKind.TRACKED && Double.isFinite(r.leftSpeed)) {
            final String what = Math.abs(r.leftSpeed - r.rightSpeed) < 0.2 ? "tracks together"
                    : (r.leftSpeed * r.rightSpeed < 0 ? "pivoting " + (r.rightSpeed > r.leftSpeed ? "left" : "right")
                    : "turning " + (r.rightSpeed > r.leftSpeed ? "left" : "right"));
            return String.format(Locale.ROOT, "Left track %s b/s, right %s b/s: %s", DriveKit.signed(r.leftSpeed, 1),
                    DriveKit.signed(r.rightSpeed, 1), what);
        }
        if (snap.setup().articulated() && Double.isFinite(r.jointAngle)) {
            return String.format(Locale.ROOT, "Joint %.0f deg %s", Math.abs(r.jointAngle), r.jointAngle >= 0 ? "right" : "left");
        }
        if (r.trailers > 0) {
            return GroundText.train(r).replace("TOWING", "Towing");
        }
        if (Double.isFinite(r.pathRadius)) {
            return String.format(Locale.ROOT, "Turning %s about a point %.0f blocks away; outer tyre circle %.0f",
                    r.centreX > 0 ? "right" : "left", r.pathRadius, r.kerbRadius);
        }
        if (Double.isFinite(r.crabAngle)) {
            return String.format(Locale.ROOT, "Crab steering, every wheel %.0f deg %s", Math.abs(r.crabAngle),
                    r.crabAngle >= 0 ? "right" : "left");
        }
        final int all = r.touching + r.lifted;
        if (all == 0) {
            return snap.jets().isEmpty() ? "No wheels found on the vehicle" : "No wheels: the jets drive it";
        }
        if (r.airborne) {
            return "Every tyre is off the ground";
        }
        return r.lifted > 0 ? String.format(Locale.ROOT, "%d of %d tyres on the ground", r.touching, all)
                : "Straight ahead, every tyre on the ground";
    }

    static void dashColumn(PanelSurface s, Rect r, GroundFrame f, DriveKit.Look look) {
        final DriveReadout rd = f.readout();
        final GroundSnapshot snap = rd.snapshot;
        final float heroH = Math.min(96.0f, r.h * 0.26f);
        final float gearW = heroH * 0.82f;
        final Rect card = new Rect(r.x, r.y, r.w, heroH + 58.0f);
        Shapes.card(s, card, Tokens.R_CARD, look == DriveKit.Look.RACING ? DriveKit.hud(DriveKit.THEME.plate) : Tokens.SURFACE,
                Tokens.LINE);
        final DriveKit.Boot boot = DriveKit.Boot.at(f);
        final boolean check = boot == DriveKit.Boot.CHECK;
        if (boot != DriveKit.Boot.DARK) {
            final float pad = Tokens.S3;
            DriveKit.gear(s, new Rect(card.x + pad, card.y + pad, gearW, heroH), snap.drive().gear(), look, check);
            final Rect speed = new Rect(card.x + pad + gearW + Tokens.S3, card.y + pad, card.w - gearW - pad * 2 - Tokens.S3,
                    heroH);
            final DriveKit.SpeedLayout at = DriveKit.speedWithUnit(s, speed, rd, look, check);

            final String bps = DriveKit.num(Math.abs(rd.speed), 1) + " b/s";
            if (at.unitX() + s.textWidth(bps, Text.MICRO) <= card.right() - pad) {
                s.text(bps, at.unitX(), at.unitY() - Text.lineHeight(Text.MICRO) - 2.0f, Tokens.INK_3, Text.MICRO);
            }
            final Rect tell = new Rect(card.x + pad, card.bottom() - 18.0f - pad * 0.5f - 22.0f, card.w - pad * 2, 16.0f);
            DriveKit.telltaleRow(s, tell, f, look, check);
            if (boot == DriveKit.Boot.WELCOME) {
                final String hello = snap.drive().driver().isBlank() ? "WELCOME" : "WELCOME " + snap.drive().driver();
                s.text(Text.truncate(s, hello, card.w - pad * 2, Text.META), card.x + pad, card.bottom() - 18.0f,
                        Tokens.INK_2, Text.META);
            } else {
                s.text(Text.truncate(s, driveLine(f), card.w - pad * 2, Text.META), card.x + pad, card.bottom() - 18.0f,
                        Tokens.INK_3, Text.META);
            }
        }
        float y = card.bottom() + GAP;

        final GroundSnapshot.Drive d = snap.drive();
        y = pedalRow(s, r.x, y, r.w, "THR", d.throttle(), true, Tokens.SERIES[3]);
        y = pedalRow(s, r.x, y, r.w, "BRK", d.braking() ? 1.0 : 0.0, false, Tokens.CRITICAL);
        y = pedalRow(s, r.x, y, r.w, "STR", d.steer(), true, Tokens.SERIES[0]);
        y += Tokens.S2;

        final float ggR = Math.min(46.0f, (r.bottom() - y) * 0.3f);
        if (ggR >= 18.0f) {
            DriveKit.frictionCircle(s, r.x + ggR, y + ggR, ggR, f);
            final float fx = r.x + ggR * 2.0f + Tokens.S4;
            figure(s, fx, y, "LATERAL", DriveKit.signed(rd.latG, 2) + " g");
            figure(s, fx, y + 26.0f, "LONG", DriveKit.signed(rd.lonG, 2) + " g");
            figure(s, fx, y + 52.0f, "PEAK 10 S", DriveKit.num(snap.log().peak(), 2) + " g");
            final float fx2 = fx + Math.max(96.0f, (r.right() - fx) * 0.5f);
            if (fx2 + 70.0f <= r.right()) {
                figure(s, fx2, y, "HEADING", String.format(Locale.ROOT, "%03d", Math.round(rd.heading) % 360));
                figure(s, fx2, y + 26.0f, "GRADE", DriveKit.signed(rd.grade, 0) + "%");
                figure(s, fx2, y + 52.0f, "ALTITUDE", "Y " + Math.round(rd.altitude));
            }
            y += ggR * 2.0f + GAP;
        }

        final Rect rest = new Rect(r.x, y, r.w, r.bottom() - y);
        if (rest.h >= 30.0f) {
            if (!f.alerts().isEmpty()) {
                alertList(s, rest, f.alerts());
            } else {
                storyCard(s, rest, f);
            }
        }
    }

    static String driveLine(GroundFrame f) {
        final DriveReadout r = f.readout();
        final GroundSnapshot snap = r.snapshot;
        return switch (r.state) {
            case DRIVING -> {
                final StringBuilder b = new StringBuilder();
                if (r.model.known()) {
                    b.append(String.format(Locale.ROOT, "cruise %.0f km/h at full throttle (modelled)",
                            r.model.cruise() * DriveMath.KMH));
                }
                yield b.toString();
            }
            case PARKED -> "Parked: the park brake holds every wheel";
            case TOWED -> "Towed: the towing vehicle drives these wheels";
            case RUNAWAY -> "Moving with nobody at the helm";
            case OFF_CRAFT -> "The console is not on a Sable craft";
            default -> snap.setup().options().parkBrake() ? "Standing" : "Standing, park brake off";
        };
    }

    static float pedalRow(PanelSurface s, float x, float y, float w, String label, double value, boolean signed, int color) {
        s.textV(label, x, y + 6.0f, Tokens.INK_3, Text.META);
        final float bx = x + 34.0f;
        final float bw = w - 34.0f - 48.0f;
        DriveKit.pedal(s, new Rect(bx, y + 3.0f, bw, 6.0f), value, signed, color);
        final String v = signed ? DriveKit.signed(value * 100.0, 0) + "%" : (value > 0.5 ? "ON" : "off");
        s.textRightV(v, x + w, y + 6.0f, Tokens.INK_2, Text.META);
        return y + 17.0f;
    }

    static void figure(PanelSurface s, float x, float y, String label, String value) {
        s.text(label, x, y, Tokens.INK_3, Text.MICRO);
        s.text(value, x, y + 9.0f, Tokens.INK, Text.BODY);
    }

    static void alertList(PanelSurface s, Rect r, List<GroundAlerts.Alert> alerts) {
        Shapes.card(s, r, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        float y = r.y + Tokens.S2;
        for (final GroundAlerts.Alert a : alerts) {
            if (y + 26.0f > r.bottom()) {
                break;
            }
            final int col = DriveKit.alertColor(a.level());
            Shapes.roundedRect(s, new Rect(r.x + Tokens.S2, y + 2.0f, 3.0f, 20.0f), 1.5f, col);
            s.text(a.name(), r.x + Tokens.S4, y, col, Text.BODY);
            s.text(Text.truncate(s, a.detail(), r.w - Tokens.S4 - Tokens.S2, Text.META), r.x + Tokens.S4, y + 12.0f,
                    Tokens.INK_2, Text.META);
            y += 28.0f;
        }
    }

    static void storyCard(PanelSurface s, Rect r, GroundFrame f) {
        final GroundSnapshot snap = f.snapshot();
        final GroundSnapshot.Odometer o = snap.odometer();
        Shapes.card(s, r, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        final boolean four = r.w >= 300.0f;
        final float col = (r.w - Tokens.S3 * 2) / (four ? 4.0f : 3.0f);
        final float y = r.y + Tokens.S2;
        int i = 0;
        figure(s, r.x + Tokens.S3 + col * i++, y, "ODOMETER", GroundText.distance(o.odometer()));
        figure(s, r.x + Tokens.S3 + col * i++, y, "TRIP", GroundText.distance(o.trip()));
        if (four) {
            figure(s, r.x + Tokens.S3 + col * i++, y, "TRIP TIME", duration(o.tripTicks() / 20.0));
        }
        figure(s, r.x + Tokens.S3 + col * i, y, "TOP", Double.isFinite(o.topSpeed())
                ? Math.round(o.topSpeed() * DriveMath.KMH) + " km/h" : "--");
        final GroundSnapshot.History h = snap.history();
        final List<GroundSnapshot.Event> events = snap.events().list();
        final float eventH = events.isEmpty() ? 0.0f : 14.0f;
        final Rect trace = new Rect(r.x + Tokens.S3 + 28.0f, y + 34.0f, r.w - Tokens.S3 * 2 - 28.0f,
                r.bottom() - y - 34.0f - Tokens.S2 - eventH - 12.0f);
        if (h.size() >= 2 && trace.h >= 18.0f) {
            trace(s, trace, kmh(h.speed()), Tokens.SERIES[0]);
            s.text(String.format(Locale.ROOT, "km/h, last %s", duration(h.size() * h.periodTicks() / 20.0)), trace.x,
                    trace.bottom() + 2.0f, Tokens.INK_3, Text.MICRO);
        }
        if (!events.isEmpty() && r.h >= 58.0f) {
            final GroundSnapshot.Event e = events.get(events.size() - 1);
            final long ago = Math.max(0L, f.tick() - e.tick());
            final String line = eventText(e) + ", " + duration(ago / 20.0) + " ago";
            s.text(Text.truncate(s, line, r.w - Tokens.S3 * 2, Text.META), r.x + Tokens.S3, r.bottom() - Tokens.S2 - 10.0f,
                    Tokens.INK_2, Text.META);
        }
    }

    static float[] kmh(float[] bps) {
        final float[] out = new float[bps.length];
        for (int i = 0; i < bps.length; i++) {
            out[i] = Float.isFinite(bps[i]) ? (float) (Math.abs(bps[i]) * DriveMath.KMH) : Float.NaN;
        }
        return out;
    }

    public static void powertrain(PanelSurface s, Rect c, GroundFrame f) {
        final DriveReadout r = f.readout();
        final DriveReadout.Model m = r.model;
        final Rect chart = new Rect(c.x, c.y, c.w * 0.58f, c.h * 0.62f);
        Shapes.card(s, chart, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        s.text("WHERE DRIVE MEETS DRAG", chart.x + Tokens.S3, chart.y + Tokens.S2, Tokens.INK_3, Text.MICRO);
        if (m.known()) {
            forceChart(s, chart.inset(Tokens.S3, 24.0f, Tokens.S3, 28.0f), f);
            s.text(Text.truncate(s, String.format(Locale.ROOT,
                    "Modelled from Offroad's wheel law on ordinary ground: cruise %.1f b/s, %.0f km/h",
                    m.cruise(), m.cruise() * DriveMath.KMH), chart.w - Tokens.S3 * 2, Text.META), chart.x + Tokens.S3,
                    chart.bottom() - 18.0f, Tokens.INK_2, Text.META);
        } else {
            s.text("No driven wheel: nothing to model", chart.x + Tokens.S3, chart.y + 30.0f, Tokens.INK_2, Text.BODY);
        }
        final Rect facts = new Rect(chart.right() + GAP, c.y, c.right() - chart.right() - GAP, chart.h);
        Shapes.card(s, facts, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        float y = facts.y + Tokens.S2;
        final GroundSnapshot snap = r.snapshot;
        y = fact(s, facts, y, "Cruise, full throttle", m.known() ? Math.round(m.cruise() * DriveMath.KMH) + " km/h" : "--");
        y = fact(s, facts, y, "Reverse", m.known() ? Math.round(m.reverse() * DriveMath.KMH) + " km/h" : "--");
        y = fact(s, facts, y, "Rest to 90%", Double.isFinite(m.toNinety()) ? DriveKit.num(m.toNinety(), 1) + " s" : "no mass");
        y = fact(s, facts, y, "Stop from here", Double.isFinite(m.stopDistance()) ? DriveKit.num(m.stopDistance(), 1)
                + " blocks" : "no mass");
        y = fact(s, facts, y, "Steepest climb", Double.isFinite(m.hill()) ? (m.hill() >= 90.0 ? "any slope"
                : Math.round(m.hill()) + " deg") : "no mass");
        y = fact(s, facts, y, "Top seen, held 1 s", Double.isFinite(snap.envelope().topSpeed())
                ? Math.round(snap.envelope().topSpeed() * DriveMath.KMH) + " km/h" : "not yet");
        if (y + 20.0f <= facts.bottom()) {
            s.text(Text.truncate(s, "Modelled figures, from the wheels' law; the last is measured.", facts.w - Tokens.S3 * 2,
                    Text.MICRO), facts.x + Tokens.S3, y + 4.0f, Tokens.INK_3, Text.MICRO);
        }
        final Rect lower = new Rect(c.x, chart.bottom() + GAP, c.w, c.bottom() - chart.bottom() - GAP);
        final boolean split = lower.w >= 520.0f;
        final Rect gov = split ? new Rect(lower.x, lower.y, lower.w * 0.55f, lower.h) : lower;
        governors(s, gov, f);
        if (split) {
            learned(s, new Rect(gov.right() + GAP, lower.y, lower.right() - gov.right() - GAP, lower.h), f);
        }
    }

    static void learned(PanelSurface s, Rect r, GroundFrame f) {
        if (r.h < 40.0f) {
            return;
        }
        Shapes.card(s, r, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        final GroundSnapshot.Envelope e = f.snapshot().envelope();
        s.text("LEARNED FROM DRIVING (MEASURED)", r.x + Tokens.S3, r.y + Tokens.S2, Tokens.INK_3, Text.MICRO);
        if (!e.known()) {
            s.text(Text.truncate(s, "Nothing yet: it learns while someone drives", r.w - Tokens.S3 * 2, Text.META),
                    r.x + Tokens.S3, r.y + 24.0f, Tokens.INK_2, Text.META);
            return;
        }
        final double g = DriveMath.gravity(f.snapshot());
        final float colW = (r.w - Tokens.S3 * 2) / 2.0f;
        final float y = r.y + 22.0f;
        figure(s, r.x + Tokens.S3, y, "TOP, HELD 1 S", Double.isFinite(e.topSpeed())
                ? Math.round(e.topSpeed() * DriveMath.KMH) + " km/h" : "--");
        figure(s, r.x + Tokens.S3 + colW, y, "CORNERING", Double.isFinite(e.maxLatG()) ? DriveKit.num(e.maxLatG(), 2) + " g"
                : "--");
        figure(s, r.x + Tokens.S3, y + 26.0f, "ACCELERATING", accel(e.maxAccel(), g));
        figure(s, r.x + Tokens.S3 + colW, y + 26.0f, "BRAKING", accel(e.maxDecel(), g));

        final Rect bars = new Rect(r.x + Tokens.S3, y + 58.0f, r.w - Tokens.S3 * 2, r.bottom() - y - 58.0f - 22.0f);
        if (bars.h < 16.0f) {
            return;
        }
        final float[] radius = e.turnRadius();
        float most = 0.0f;
        int any = 0;
        for (final float x : radius) {
            if (Float.isFinite(x)) {
                most = Math.max(most, x);
                any++;
            }
        }
        final float bw = bars.w / radius.length;
        for (int i = 0; i < radius.length; i++) {
            final float bx = bars.x + i * bw;
            Shapes.rect(s, bx + 1.0f, bars.bottom(), Math.max(1.0f, bw - 2.0f), 1.0f, Tokens.LINE);
            if (Float.isFinite(radius[i]) && most > 0.0f) {
                final float h = Math.max(2.0f, bars.h * radius[i] / most);
                Shapes.rect(s, bx + 1.0f, bars.bottom() - h, Math.max(1.0f, bw - 2.0f), h, Tokens.alpha(Tokens.SERIES[0], 0.8f));
            }
        }
        final String caption = any == 0 ? "Tightest turn by speed: no steady turn held yet"
                : String.format(Locale.ROOT, "Tightest turn held, by speed 0 to %.0f km/h (tallest %.0f blocks)",
                radius.length * e.bucketWidth() * DriveMath.KMH, most);
        s.text(Text.truncate(s, caption, r.w - Tokens.S3 * 2, Text.MICRO), r.x + Tokens.S3, r.bottom() - 16.0f,
                Tokens.INK_3, Text.MICRO);
    }

    // plain ascii, no superscript two in the vanilla font
    static String accel(double a, double g) {
        if (!Double.isFinite(a) || !Double.isFinite(g) || g <= 0.0) {
            return "--";
        }
        return DriveKit.num(a / g, 2) + " g";
    }

    static float fact(PanelSurface s, Rect card, float y, String label, String value) {
        if (y + 16.0f > card.bottom()) {
            return y;
        }
        s.text(Text.truncate(s, label, card.w * 0.6f, Text.META), card.x + Tokens.S3, y + 2.0f, Tokens.INK_2, Text.META);
        s.textRight(value, card.right() - Tokens.S3, y, Tokens.INK, Text.BODY);
        return y + 20.0f;
    }

    static void forceChart(PanelSurface s, Rect r, GroundFrame f) {
        final DriveReadout rd = f.readout();
        final DriveReadout.Model m = rd.model;
        final double vMax = Math.max(4.0, Math.ceil(m.cruise() * 1.35 / 2.0) * 2.0);
        final double fMax = Math.max(m.driveForce(), m.dragPerSpeed() * vMax) * 1.1;
        final float x0 = r.x + 34.0f;
        final float y0 = r.bottom() - 14.0f;
        final float w = r.right() - x0;
        final float h = y0 - r.y;
        Shapes.line(s, x0, y0, x0 + w, y0, 1.0f, Tokens.LINE_HI);
        Shapes.line(s, x0, y0, x0, r.y, 1.0f, Tokens.LINE_HI);
        for (int i = 0; i <= 4; i++) {
            final double v = vMax * i / 4.0;
            final float x = x0 + (float) (v / vMax * w);
            s.textCenter(Long.toString(Math.round(v)), x, y0 + 3.0f, Tokens.INK_3, Text.MICRO);
        }
        s.text("b/s", x0 + w - 14.0f, y0 - 10.0f, Tokens.INK_3, Text.MICRO);
        s.text("pN", r.x, r.y, Tokens.INK_3, Text.MICRO);
        final float yDrive = y0 - (float) (m.driveForce() / fMax * h);
        Shapes.line(s, x0, yDrive, x0 + w, yDrive, 2.0f, Tokens.SERIES[3]);
        s.text("drive", x0 + 4.0f, yDrive - 10.0f, Tokens.INK_2, Text.MICRO);
        final float xEnd = x0 + w;
        final float yEnd = y0 - (float) (m.dragPerSpeed() * vMax / fMax * h);
        Shapes.line(s, x0, y0, xEnd, Math.max(r.y, yEnd), 2.0f, Tokens.SERIES[1]);
        s.textRight("drag", xEnd - 2.0f, Math.max(r.y, yEnd) + 2.0f, Tokens.INK_2, Text.MICRO);
        final float xc = x0 + (float) (m.cruise() / vMax * w);
        DriveKit.dashed(s, xc, y0, xc, yDrive, 1.0f, Tokens.INK_2, 3.0f);
        Shapes.circle(s, xc, yDrive, 3.5f, Tokens.INK);
        s.text(Math.round(m.cruise() * DriveMath.KMH) + " km/h", xc + 5.0f, yDrive + 3.0f, Tokens.INK, Text.META);
        final double now = Math.abs(rd.speed);
        if (now > 0.05 && now < vMax) {
            final float xn = x0 + (float) (now / vMax * w);
            Shapes.line(s, xn, y0, xn, r.y + 6.0f, 1.0f, Tokens.alpha(Tokens.INK, 0.5f));
            s.textCenter("now", xn, r.y - 2.0f, Tokens.INK_3, Text.MICRO);
        }
    }

    static void governors(PanelSurface s, Rect r, GroundFrame f) {
        if (r.h < 40.0f) {
            return;
        }
        Shapes.card(s, r, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        s.text("GOVERNORS", r.x + Tokens.S3, r.y + Tokens.S2, Tokens.INK_3, Text.MICRO);
        float y = r.y + 24.0f;
        final List<GroundSnapshot.Governor> list = f.snapshot().governors();
        if (list.isEmpty()) {
            s.text("No governors: nothing drives the wheels through one", r.x + Tokens.S3, y, Tokens.INK_2, Text.META);
            return;
        }
        for (final GroundSnapshot.Governor g : list) {
            if (y + 16.0f > r.bottom()) {
                break;
            }
            final String supply = switch (g.supply()) {
                case OK -> "turning";
                case IDLE -> "not read";
                case NO_ROTATION -> "no rotation";
                case OVERSTRESSED -> "overstressed";
                case FOREIGN -> "held by another";
                case EXCLUDED -> "excluded";
            };
            final int col = g.starved() ? Tokens.CAUTION : (g.supply() == GroundSnapshot.Supply.OK ? Tokens.INK : Tokens.INK_2);
            s.text(g.name(), r.x + Tokens.S3, y, Tokens.INK, Text.BODY);

            final float barX = r.x + 40.0f;
            final float barW = Math.max(40.0f, Math.min(160.0f, r.w * 0.28f));
            DriveKit.pedal(s, new Rect(barX, y + 3.0f, barW, 5.0f), Math.abs(g.applied()) / DriveMath.GOVERNOR_MAX, false,
                    g.supply() == GroundSnapshot.Supply.OK ? Tokens.SERIES[3] : Tokens.CAUTION);
            final float tick = barX + (float) (Math.min(1.0, Math.abs(g.commanded()) / DriveMath.GOVERNOR_MAX) * barW);
            Shapes.rect(s, tick - 0.5f, y + 1.0f, 1.0f, 9.0f, Tokens.INK);
            final float tx = barX + barW + 8.0f;
            final String detail = String.format(Locale.ROOT, "%d of %d RPM   %s   %s", g.applied(), g.commanded(),
                    Float.isFinite(g.ratio()) ? String.format(Locale.ROOT, "gear x%.2f", g.ratio()) : "gear not learned",
                    g.machine() ? "machine" : g.wheels() + " wheels" + (g.jets() > 0 ? ", " + g.jets() + " jets" : ""));
            final float supplyW = s.textWidth(supply, Text.META) + Tokens.S3;
            s.text(Text.truncate(s, detail, r.right() - tx - supplyW - Tokens.S3, Text.META), tx, y, Tokens.INK_2, Text.META);
            s.textRight(supply, r.right() - Tokens.S3, y, col, Text.META);
            y += 18.0f;
        }
    }

    public static void wheels(PanelSurface s, Rect c, GroundFrame f) {
        final Rect plan = new Rect(c.x, c.y, c.w * 0.34f, c.h);
        Shapes.card(s, plan, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        DriveKit.top(s, plan.inset(Tokens.S3), f, new DriveKit.Plan(true, false, false, true));
        final Rect grid = new Rect(plan.right() + GAP, c.y, c.right() - plan.right() - GAP, c.h);
        Shapes.card(s, grid, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);

        final float[] share = {0.14f, 0.12f, 0.12f, 0.1f, 0.1f, 0.3f, 0.12f};
        final float inner = grid.w - Tokens.S3 * 2;
        final float[] at = new float[share.length];
        float acc = grid.x + Tokens.S3;
        for (int i = 0; i < share.length; i++) {
            at[i] = acc;
            acc += share[i] * inner;
        }
        final String[] heads = {"WHEEL", "APPLIED", "ROLLING", "SLIP", "GRIP", "LOAD (SPRING)", "BRAKE"};
        for (int i = 0; i < heads.length; i++) {
            s.text(Text.truncate(s, heads[i], share[i] * inner - 4.0f, Text.MICRO), at[i], grid.y + Tokens.S2, Tokens.INK_3,
                    Text.MICRO);
        }
        final float rowH = 19.0f;
        final float bottom = grid.bottom() - 34.0f;
        float y = grid.y + 22.0f;
        final List<DriveReadout.WheelRead> all = f.readout().wheels;
        for (int n = 0; n < all.size(); n++) {
            final DriveReadout.WheelRead wr = all.get(n);
            if (y + rowH > bottom) {
                s.text((all.size() - n) + " more wheels", at[0], y, Tokens.INK_3, Text.META);
                break;
            }
            final GroundSnapshot.Wheel w = wr.wheel();
            if (n % 2 == 1) {
                Shapes.rect(s, grid.x + 2.0f, y - 3.0f, grid.w - 4.0f, rowH, Tokens.alpha(Tokens.RAISED, 0.6f));
            }
            final String name = "B" + (w.unit() + 1) + " A" + (w.axle() + 1) + (w.x() < 0 ? " L" : " R");
            final String[] cells = {name, Math.round(w.applied()) + "", Float.isFinite(w.actual()) ? Math.round(w.actual()) + "" : "--",
                    wr.touching() ? (Float.isFinite(w.slip()) ? Math.round(w.slip() * 100f) + "%" : "--") : "air",
                    Float.isFinite(w.grip()) ? Math.round(w.grip() * 100f) + "%" : "--", null,
                    w.brake() > 0 ? "on" : ""};
            for (int i = 0; i < cells.length; i++) {
                if (cells[i] == null) {
                    continue;
                }
                final int col = i == 3 && (wr.spinning() || wr.locking()) ? Tokens.CAUTION
                        : i == 6 ? Tokens.CRITICAL : (i == 0 ? Tokens.INK_2 : Tokens.INK);
                s.text(Text.truncate(s, cells[i], share[i] * inner - 4.0f, Text.BODY), at[i], y, col, Text.BODY);
            }

            final float loadW = share[5] * inner - 44.0f;
            if (loadW >= 16.0f) {
                final double load = Float.isFinite(w.load()) ? w.load() : Double.NaN;
                DriveKit.pedal(s, new Rect(at[5], y + 2.0f, loadW, 5.0f), Double.isFinite(load) ? load / 1.5 : Double.NaN,
                        false, !wr.touching() ? Tokens.INK_3 : load > 1.2 ? Tokens.CAUTION : Tokens.SERIES[0]);
                s.text(Double.isFinite(load) ? Math.round(load * 100.0) + "%" : "--", at[5] + loadW + 6.0f, y, Tokens.INK_2,
                        Text.META);
            }
            y += rowH;
        }
        final String why = "Offroad has no rolling tyre: applied and rolling RPM settle at a ratio, and slip reads 0% on "
                + "nearly every block. It is real only on slippery ground and in the air.";
        final List<String> lines = Text.wrap(s, why, grid.w - Tokens.S3 * 2, Text.MICRO);
        float wy = grid.bottom() - 6.0f - lines.size() * Text.lineHeight(Text.MICRO);
        for (final String l : lines) {
            s.text(l, grid.x + Tokens.S3, wy, Tokens.INK_3, Text.MICRO);
            wy += Text.lineHeight(Text.MICRO);
        }
    }

    public static void train(PanelSurface s, Rect c, GroundFrame f) {
        final DriveReadout r = f.readout();
        final Rect plan = new Rect(c.x, c.y, c.w, c.h * 0.6f);
        Shapes.card(s, plan, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        DriveKit.top(s, plan.inset(Tokens.S3), f, DriveKit.Plan.TRAIN);
        final Rect list = new Rect(c.x, plan.bottom() + GAP, c.w, c.bottom() - plan.bottom() - GAP);
        Shapes.card(s, list, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        float y = list.y + Tokens.S2;
        if (r.units.size() <= 1) {
            s.text("Nothing is towed", list.x + Tokens.S3, y, Tokens.INK_2, Text.BODY);
            return;
        }
        for (final DriveReadout.UnitRead ur : r.units) {
            final GroundSnapshot.Unit u = ur.unit();
            if (u.isTractor() || y + 16.0f > list.bottom()) {
                continue;
            }
            final int col = ur.link() == DriveReadout.LinkState.GONE || ur.link() == DriveReadout.LinkState.NEAR_SNAP
                    ? Tokens.CRITICAL : Tokens.INK;
            s.text(u.name(), list.x + Tokens.S3, y, Tokens.INK, Text.BODY);
            s.text(capital(u.role().key()), list.x + 44.0f, y, Tokens.INK_2, Text.META);
            s.text(GroundText.linkWords(ur), list.x + 120.0f, y, col, Text.META);
            s.text(u.role().takesMode() ? capital(u.mode().key()) : "", list.x + 280.0f, y, Tokens.INK_2, Text.META);
            s.textRight(Float.isFinite(u.mass()) ? Math.round(u.mass()) + " kpg" : "", list.right() - Tokens.S3, y,
                    Tokens.INK_3, Text.META);
            y += 18.0f;
        }
    }

    public static void telemetry(PanelSurface s, Rect c, GroundFrame f) {
        final GroundSnapshot.History h = f.snapshot().history();
        Shapes.card(s, c, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        if (h.size() < 2) {
            s.text(Text.truncate(s, "No history yet: the vehicle records twice a second once it moves",
                    c.w - Tokens.S3 * 2.0f, Text.BODY), c.x + Tokens.S3, c.y + Tokens.S3, Tokens.INK_2, Text.BODY);
            return;
        }
        final String[] names = {"SPEED b/s", "THROTTLE", "BRAKE", "STEER", "LATERAL g", "LONG g"};
        final float[][] series = {h.speed(), h.throttle(), h.brake(), h.steer(), h.latG(), h.lonG()};
        final int[] colors = {Tokens.SERIES[0], Tokens.SERIES[3], Tokens.SERIES[1], Tokens.SERIES[4], Tokens.SERIES[2],
                Tokens.SERIES[5]};
        final float top = c.y + Tokens.S3;
        final float laneH = (c.h - Tokens.S3 * 2 - 16.0f) / names.length;
        for (int i = 0; i < names.length; i++) {
            final Rect lane = new Rect(c.x + 96.0f, top + i * laneH, c.w - 96.0f - Tokens.S3, laneH - 4.0f);
            s.text(names[i], c.x + Tokens.S3, lane.y + 2.0f, Tokens.INK_2, Text.META);
            trace(s, lane, series[i], colors[i]);
        }
        final double seconds = h.size() * h.periodTicks() / 20.0;
        s.text(String.format(Locale.ROOT, "last %.0f s, one sample every %.1f s", seconds, h.periodTicks() / 20.0),
                c.x + 96.0f, c.bottom() - 16.0f, Tokens.INK_3, Text.MICRO);
    }

    static void trace(PanelSurface s, Rect r, float[] v, int color) {
        double lo = Double.POSITIVE_INFINITY;
        double hi = Double.NEGATIVE_INFINITY;
        for (final float x : v) {
            if (Float.isFinite(x)) {
                lo = Math.min(lo, x);
                hi = Math.max(hi, x);
            }
        }
        Shapes.rect(s, r.x, r.bottom(), r.w, 1.0f, Tokens.LINE);
        if (!Double.isFinite(lo)) {
            return;
        }
        if (hi - lo < 1.0e-3) {
            hi = lo + 1.0;
        }
        final float[] pts = new float[v.length * 2];
        int n = 0;
        for (int i = 0; i < v.length; i++) {
            if (!Float.isFinite(v[i])) {
                continue;
            }
            pts[n * 2] = r.x + r.w * i / Math.max(1, v.length - 1);
            pts[n * 2 + 1] = r.bottom() - (float) ((v[i] - lo) / (hi - lo)) * r.h;
            n++;
        }
        if (n >= 2) {
            Shapes.polyline(s, pts, n, Tokens.DATA_STROKE * 0.75f, color);
        }
        s.textRight(DriveKit.num(hi, hi > 20 ? 0 : 2), r.x - 4.0f, r.y, Tokens.INK_3, Text.MICRO);
    }

    public static void journey(PanelSurface s, Rect c, GroundFrame f) {
        final GroundSnapshot snap = f.snapshot();
        final Rect map = new Rect(c.x, c.y, c.w * 0.62f, c.h);
        Shapes.card(s, map, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        trailMap(s, map.inset(Tokens.S3), f);
        final Rect log = new Rect(map.right() + GAP, c.y, c.right() - map.right() - GAP, c.h);
        Shapes.card(s, log, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        float y = log.y + Tokens.S2;
        y = fact(s, log, y, "Odometer", GroundText.distance(snap.odometer().odometer()));
        y = fact(s, log, y, "Trip", GroundText.distance(snap.odometer().trip()));
        y = fact(s, log, y, "Trip time", duration(snap.odometer().tripTicks() / 20.0));
        y = fact(s, log, y, "Top this trip", Double.isFinite(snap.odometer().tripTop())
                ? Math.round(snap.odometer().tripTop() * DriveMath.KMH) + " km/h" : "--");
        y += Tokens.S2;
        for (final Folded e : fold(snap.events().list())) {
            if (y + 15.0f > log.bottom()) {
                break;
            }
            final long ago = Math.max(0L, f.tick() - e.latest().tick());
            final String when = duration(ago / 20.0) + " ago";
            final float whenW = s.textWidth(when, Text.MICRO) + Tokens.S2;
            final String what = eventText(e.latest()) + (e.count() > 1 ? "  x" + e.count() : "");
            final int col = e.latest().kind() == GroundSnapshot.EventKind.PROBLEM ? Tokens.CAUTION
                    : e.latest().kind() == GroundSnapshot.EventKind.IMPACT ? Tokens.INK : Tokens.INK_2;
            s.text(Text.truncate(s, what, log.w - Tokens.S3 * 2 - whenW, Text.META), log.x + Tokens.S3, y, col, Text.META);
            s.textRight(when, log.right() - Tokens.S3, y + 1.0f, Tokens.INK_3, Text.MICRO);
            y += 15.0f;
        }
    }

    record Folded(GroundSnapshot.Event latest, int count) {
    }

    static List<Folded> fold(List<GroundSnapshot.Event> events) {
        final java.util.LinkedHashMap<String, int[]> counts = new java.util.LinkedHashMap<>();
        final java.util.Map<String, GroundSnapshot.Event> latest = new java.util.HashMap<>();
        for (int i = events.size() - 1; i >= 0; i--) {
            final GroundSnapshot.Event e = events.get(i);
            final String key = e.kind().name() + '|' + eventText(e);
            counts.computeIfAbsent(key, k -> new int[1])[0]++;
            latest.putIfAbsent(key, e);
        }
        final List<Folded> out = new ArrayList<>();
        counts.forEach((key, n) -> out.add(new Folded(latest.get(key), n[0])));
        return out;
    }

    static String eventText(GroundSnapshot.Event e) {
        return switch (e.kind()) {
            case HELM -> e.text() + " took the helm";
            case LEFT -> e.text() + " left the helm";
            case IMPACT -> String.format(Locale.ROOT, "Impact, %.1f g", e.value());
            case AIRTIME -> String.format(Locale.ROOT, "Airborne %.1f s", e.value());
            case COUPLED -> "Coupled: " + e.text();
            case LOST -> "Lost: " + e.text();
            case PROBLEM -> "! " + e.text();
            case CLEARED -> "Cleared: " + e.text();
            case TOP_SPEED -> String.format(Locale.ROOT, "Top speed %.0f km/h", e.value() * DriveMath.KMH);
            case TRIP -> "Trip reset";
        };
    }

    static String duration(double seconds) {
        return GroundText.duration(seconds);
    }

    static void trailMap(PanelSurface s, Rect r, GroundFrame f) {
        final GroundSnapshot snap = f.snapshot();
        final List<GroundSnapshot.Fix> fixes = snap.trail().fixes();
        final com.createstats.aerial.V3 here = snap.body().position();
        double minX = here.x() - 16.0;
        double maxX = here.x() + 16.0;
        double minZ = here.z() - 16.0;
        double maxZ = here.z() + 16.0;
        for (final GroundSnapshot.Fix fx : fixes) {
            minX = Math.min(minX, fx.position().x());
            maxX = Math.max(maxX, fx.position().x());
            minZ = Math.min(minZ, fx.position().z());
            maxZ = Math.max(maxZ, fx.position().z());
        }
        final double k = Math.min(r.w / (maxX - minX), r.h / (maxZ - minZ));
        final double mx = (minX + maxX) * 0.5;
        final double mz = (minZ + maxZ) * 0.5;
        s.clip(r);
        try {
            final GroundSnapshot.Terrain t = snap.terrain();
            if (t.size() > 0) {
                double lo = Double.POSITIVE_INFINITY;
                double hi = Double.NEGATIVE_INFINITY;
                for (int iz = 0; iz < t.size(); iz++) {
                    for (int ix = 0; ix < t.size(); ix++) {
                        final double hgt = t.sample(ix, iz);
                        if (Double.isFinite(hgt)) {
                            lo = Math.min(lo, hgt);
                            hi = Math.max(hi, hgt);
                        }
                    }
                }
                for (int iz = 0; iz < t.size(); iz++) {
                    for (int ix = 0; ix < t.size(); ix++) {
                        final double hgt = t.sample(ix, iz);
                        if (!Double.isFinite(hgt)) {
                            continue;
                        }
                        final double shade = hi > lo ? (hgt - lo) / (hi - lo) : 0.5;
                        final float x0 = r.centerX() + (float) ((t.originX() + ix * t.cell() - mx) * k);
                        final float y0 = r.centerY() + (float) ((t.originZ() + iz * t.cell() - mz) * k);
                        final float w = (float) (t.cell() * k) + 0.5f;
                        Shapes.rect(s, x0, y0, w, w, Tokens.mix(Tokens.RAISED, Tokens.LINE_HI, (float) shade));
                    }
                }
            }
            float px = Float.NaN;
            float py = Float.NaN;
            for (final GroundSnapshot.Fix fx : fixes) {
                final float x = r.centerX() + (float) ((fx.position().x() - mx) * k);
                final float y = r.centerY() + (float) ((fx.position().z() - mz) * k);
                if (!Float.isNaN(px)) {
                    Shapes.line(s, px, py, x, y, 2.0f, Tokens.SERIES[0]);
                }
                px = x;
                py = y;
            }
            for (final GroundSnapshot.Event e : snap.events().list()) {
                if (e.kind() != GroundSnapshot.EventKind.IMPACT || !e.position().isFinite()) {
                    continue;
                }
                final float x = r.centerX() + (float) ((e.position().x() - mx) * k);
                final float y = r.centerY() + (float) ((e.position().z() - mz) * k);
                Shapes.circle(s, x, y, 3.5f, Tokens.CAUTION);
            }
            final float hx = r.centerX() + (float) ((here.x() - mx) * k);
            final float hy = r.centerY() + (float) ((here.z() - mz) * k);
            final DriveReadout rd = f.readout();
            final double hdg = Math.toRadians(rd.heading);
            final float fx = (float) Math.sin(hdg) * 8.0f;
            final float fy = -(float) Math.cos(hdg) * 8.0f;
            s.tri(hx + fx, hy + fy, Tokens.INK, hx - fy * 0.5f - fx * 0.5f, hy + fx * 0.5f - fy * 0.5f, Tokens.INK,
                    hx + fy * 0.5f - fx * 0.5f, hy - fx * 0.5f - fy * 0.5f, Tokens.INK);
        } finally {
            s.unclip();
        }
        s.text("N", r.right() - 10.0f, r.y + 2.0f, Tokens.INK_3, Text.META);
    }
}
