package com.createstats.client.hud;

import com.createstats.flight.FlightMode;
import com.createstats.flight.NoseAxis;
import com.createstats.flight.SensorRequirement;
import com.createstats.flight.network.FccGuiControlEntry;
import com.createstats.flight.network.FccGuiSnapshot;
import com.createstats.flight.network.FccTelemetryPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** [1] */
@OnlyIn(Dist.CLIENT)
public final class CommercialHud {

    // palette (alpha-leading ARGB), calm cyan/green glass-cockpit
    private static final int COL_FRAME     = 0xFF8FE8FF;
    private static final int COL_FRAME_DIM = 0x9052B6CC;
    private static final int COL_TEXT      = 0xFFEAF6FF;
    private static final int COL_TEXT_DIM  = 0xC0A6C2CE;
    private static final int COL_GREEN     = 0xFF6CFF9E;
    private static final int COL_GREEN_DIM = 0xA049C77A;
    private static final int COL_MAGENTA   = 0xFFE389FF;
    private static final int COL_AMBER     = 0xFFFFC24D;
    private static final int COL_RED       = 0xFFFF6B6B;
    private static final int COL_PANEL     = 0x66060B12;
    private static final int COL_PANEL2    = 0x40060B12;
    private static final int COL_TICK      = 0x66B8FFE8;
    private static final int COL_TICK_DIM  = 0x34B8FFE8;
    private static final int COL_WHITE     = 0xFFFFFFFF;

    private static final float MAX_PROP_RPM = 256.0f;

    // [2]
    private static long  lastFrameNanos     = 0L;
    private static float smoothedSpeed       = 0.0f;
    private static final float[] speedVel    = new float[1];
    private static float smoothedAltitude    = 0.0f;
    private static final float[] altitudeVel = new float[1];
    private static float smoothedClimb       = 0.0f;
    private static final float[] climbVel    = new float[1];
    private static float smoothedPitch       = 0.0f;
    private static final float[] pitchVel    = new float[1];
    private static float smoothedRoll        = 0.0f;
    private static final float[] rollVel     = new float[1];
    private static float smoothedHeading     = 0.0f;
    private static final float[] headingVel  = new float[1];
    private static float smoothedAvgN1       = 0.0f;
    private static float fmaFlash            = 0.0f;
    private static FlightMode lastMode       = null;
    private static float[] smoothedEngActual = new float[0];
    private static float[] smoothedEngCmd    = new float[0];

    private CommercialHud() {
    }

    public static void render(GuiGraphics g, Minecraft mc,
                              int width, int height, int centerX, int centerY, float hudScale,
                              @Nullable FccTelemetryPacket telemetry, @Nullable FccGuiSnapshot snapshot,
                              boolean hasTelemetry, boolean signalStale,
                              float viewHeading, float craftHeading, float displayPitch, float roll,
                              double displaySpeed, double displayAltitude, double displayClimb, float gLoad) {

        final Font font = mc.font;

        // Frame timing for smoothing.
        final long now = System.nanoTime();
        final float dt = lastFrameNanos == 0L ? 1.0f / 60.0f
            : Math.min(0.25f, (now - lastFrameNanos) / 1_000_000_000.0f);
        lastFrameNanos = now;

        // smooth the primary flight values (critically-damped springs)
        smoothedSpeed    = springTo(smoothedSpeed,    (float) displaySpeed,    speedVel,    dt, 0.08f);
        smoothedAltitude = springTo(smoothedAltitude, (float) displayAltitude, altitudeVel, dt, 0.09f);
        smoothedClimb    = springTo(smoothedClimb,    (float) displayClimb,    climbVel,    dt, 0.13f);
        smoothedPitch    = springTo(smoothedPitch,    displayPitch,            pitchVel,    dt, 0.05f);
        smoothedRoll     = springTo(smoothedRoll,     roll,                    rollVel,     dt, 0.05f);
        smoothedHeading  = Float.isFinite(craftHeading)
            ? wrap360(HudEffects.compassNeedleStep(smoothedHeading, craftHeading, headingVel, dt, 0.06f))
            : smoothedHeading;

        // engine telemetry
        final int engCount = telemetry != null ? Math.max(0, telemetry.propBoundCount()) : 0;
        final int[] cmd = telemetry != null ? telemetry.commandedRpm() : new int[0];
        final int[] act = telemetry != null ? telemetry.actualRpm()    : new int[0];
        if (smoothedEngActual.length != engCount) {
            // Preserve existing smoothing state across engine-count changes (new slots zero-fill,
            // extra slots drop) so readouts don't snap to 0 and ramp back up when a prop binds.
            smoothedEngActual = java.util.Arrays.copyOf(smoothedEngActual, engCount);
            smoothedEngCmd    = java.util.Arrays.copyOf(smoothedEngCmd, engCount);
        }
        float sumCmd = 0.0f;
        for (int i = 0; i < engCount; i++) {
            final float a = i < act.length ? act[i] : 0;
            final float c = i < cmd.length ? cmd[i] : 0;
            smoothedEngActual[i] = HudEasing.expSmooth(smoothedEngActual[i], a, 0.10f, dt);
            smoothedEngCmd[i]    = HudEasing.expSmooth(smoothedEngCmd[i],    c, 0.10f, dt);
            sumCmd += Math.abs(c);
        }
        final float avgN1Target = engCount > 0 ? (sumCmd / engCount) / MAX_PROP_RPM * 100.0f : 0.0f;
        smoothedAvgN1 = HudEasing.expSmooth(smoothedAvgN1, avgN1Target, 0.10f, dt);

        // mode-change animation
        final FlightMode mode = snapshot != null ? snapshot.mode() : null;
        if (mode != lastMode) {
            fmaFlash = 1.0f;
            lastMode = mode;
        }
        fmaFlash = Math.max(0.0f, fmaFlash - dt * 1.6f);

        // draw
        // FMA strip (top centre).
        scaleAround(g, centerX, 14, hudScale, () ->
            renderFma(g, font, centerX, 14, mode, snapshot, smoothedAvgN1, fmaFlash));

        // Flight-controls switch panel (top left), mirrors the FCC Operate page.
        if (snapshot != null && !snapshot.controlEntries().isEmpty()) {
            scaleAround(g, 12, 28, hudScale, () ->
                renderControls(g, font, 12, 28, snapshot));
        }

        // Airspeed tape (left of attitude).
        final int tapeH = Math.max(0, Math.min(150, height - 96));
        final int tapeY = centerY - tapeH / 2;
        final int spdX = Math.max(14, centerX - 150);
        scaleAround(g, spdX, centerY, hudScale, () ->
            renderSpeedTape(g, font, spdX, tapeY, tapeH, smoothedSpeed,
                snapshot != null ? snapshot.navTableMaxSpeed() : -1.0f));

        // Altitude tape (right of attitude) + VSI.
        final int altX = Math.min(width - 64, centerX + 110);
        scaleAround(g, altX, centerY, hudScale, () ->
            renderAltTape(g, font, altX, tapeY, tapeH, smoothedAltitude,
                snapshot != null ? snapshot.targetAlt() : Double.NaN));
        final int vsiX = altX + 50;
        scaleAround(g, vsiX, centerY, hudScale, () ->
            renderVsi(g, font, vsiX, tapeY, tapeH, smoothedClimb));

        // Attitude indicator (centre).
        scaleAround(g, centerX, centerY, hudScale, () ->
            renderAttitude(g, font, centerX, centerY, smoothedPitch, smoothedRoll, gLoad));

        // Heading tape (bottom centre).
        scaleAround(g, centerX, height - 30, hudScale, () ->
            renderHeadingTape(g, font, centerX, height - 30, Math.min(width - 200, 280),
                smoothedHeading, mode));

        // EICAS engine stack (lower right).
        final int eicasW = 150;
        final int eicasX = width - eicasW - 12;
        final int eicasY = Math.min(height - 150, centerY + 70);
        scaleAround(g, eicasX, eicasY, hudScale, () ->
            renderEicas(g, font, eicasX, eicasY, eicasW, telemetry, snapshot,
                smoothedEngActual, smoothedEngCmd));

        // Systems annunciator panel (lower left).
        final int sysX = 12;
        final int sysY = Math.min(height - 150, centerY + 70);
        scaleAround(g, sysX, sysY, hudScale, () ->
            renderSystems(g, font, sysX, sysY, snapshot));

        // Navigation strip (lower centre), only when a route is present.
        if (snapshot != null && snapshot.pathfinderRouteWaypointCount() > 0) {
            final int navW = 200;
            final int navX = centerX - navW / 2;
            final int navY = height - 58;
            scaleAround(g, navX, navY, hudScale, () ->
                renderNav(g, font, navX, navY, navW, snapshot));
        }

        // Telemetry loss banner: the tapes freeze on stale data (never the wearer's own motion), so
        // make the loss explicit rather than showing plausible-but-wrong frozen numbers silently.
        if (!hasTelemetry) {
            renderNoSignalBanner(g, font, centerX, centerY - 62, signalStale ? "SIGNAL STALE" : "NO SIGNAL");
        }
    }

    /**
     * Spring step that holds the current value on NaN targets (telemetry loss shows dashes and
     * freezes needles instead of poisoning the smoothing state).
     */
    private static float springTo(float current, float target, float[] vel, float dt, float halfLife) {
        if (!Float.isFinite(target)) {
            vel[0] = 0.0f;
            return current;
        }
        if (!Float.isFinite(current)) {
            vel[0] = 0.0f;
            return target;
        }
        return HudAnim.spring(current, target, vel, dt, halfLife);
    }

    private static void renderNoSignalBanner(GuiGraphics g, Font font, int centerX, int y, String txt) {
        int w = font.width(txt) + 14;
        int x = centerX - w / 2;
        g.fill(x, y, x + w, y + 13, 0xC0260000);
        frame(g, x, y, w, 13, 0xFFFF4D4D);
        textC(g, font, txt, centerX, y + 3, 0xFFFF4D4D);
    }

    // =====================================================================
    //  FMA, Flight Mode Annunciator (top-centre 3-cell strip)
    // =====================================================================
    private static void renderFma(GuiGraphics g, Font font, int cx, int top,
                                  @Nullable FlightMode mode, @Nullable FccGuiSnapshot snap,
                                  float avgN1, float flash) {
        final int cellW = 78;
        final int cellH = 18;
        final int gap = 2;
        final int totalW = cellW * 3 + gap * 2;
        final int x = cx - totalW / 2;

        final boolean engaged = mode != null && mode != FlightMode.OFF;

        // Plain-language captions above each cell so the airliner-style strip is readable.
        textC(g, font, "THROTTLE", x + cellW / 2, top - 9, COL_FRAME_DIM);
        textC(g, font, "STEERING", x + cellW + gap + cellW / 2, top - 9, COL_FRAME_DIM);
        textC(g, font, "ALTITUDE", x + (cellW + gap) * 2 + cellW / 2, top - 9, COL_FRAME_DIM);

        // Throttle / engine cell.
        final String thrTxt = avgN1 >= 1.0f ? String.format(Locale.ROOT, "%2.0f%% PWR", avgN1) : "IDLE";
        fmaCell(g, font, x, top, cellW, cellH, thrTxt, avgN1 >= 1.0f ? COL_GREEN : COL_TEXT_DIM, flash);

        // Lateral mode cell.
        final String latTxt = lateralModeText(mode);
        fmaCell(g, font, x + cellW + gap, top, cellW, cellH, latTxt, engaged ? COL_MAGENTA : COL_TEXT_DIM, flash);

        // Vertical mode cell.
        String vertTxt = "MANUAL";
        if (engaged && snap != null && Double.isFinite(snap.targetAlt()) && snap.targetAlt() > 0) {
            vertTxt = "HOLD " + Math.round(snap.targetAlt());
        }
        fmaCell(g, font, x + (cellW + gap) * 2, top, cellW, cellH, vertTxt, engaged ? COL_MAGENTA : COL_TEXT_DIM, flash);

        // Sub-line: armed state + nose axis + trim + sensor warm state. Armed and trim used to be
        // GUI-only; a pilot can now confirm both without leaving the seat.
        if (snap != null) {
            final int subY = top + cellH + 3;
            final boolean armed = snap.mode() != null && snap.mode() != FlightMode.OFF;
            textC(g, font, armed ? "ARMED" : "SAFE", x + totalW / 8, subY,
                armed ? COL_GREEN : COL_TEXT_DIM);
            textC(g, font, "NOSE " + noseAxisText(snap.noseAxis()), x + totalW * 3 / 8, subY, COL_TEXT_DIM);
            final Float trimPct = trimPercent(snap);
            textC(g, font, trimPct == null ? "TRM --"
                : String.format(Locale.ROOT, "TRM %+.0f%%", trimPct), x + totalW * 5 / 8, subY, COL_TEXT_DIM);
            final boolean warm = snap.sensorsWarmedUp();
            textC(g, font, warm ? "SENS RDY" : "SENS WARM", x + totalW * 7 / 8, subY,
                warm ? COL_GREEN_DIM : COL_AMBER);
        }
    }

    /** Signed trim percent from the first control labelled with "trim", or null when absent. */
    @Nullable
    private static Float trimPercent(FccGuiSnapshot snap) {
        for (FccGuiControlEntry e : snap.controlEntries()) {
            final String label = e.label() == null ? "" : e.label().toLowerCase(Locale.ROOT);
            if (label.contains("trim")) {
                return normalizeSigned(e) * 100.0f;
            }
        }
        return null;
    }

    private static void fmaCell(GuiGraphics g, Font font, int x, int y, int w, int h,
                                String txt, int col, float flash) {
        g.fill(x, y, x + w, y + h, COL_PANEL);
        frame(g, x, y, w, h, COL_FRAME_DIM);
        if (flash > 0.01f) {
            // Box highlight on a recent mode change.
            final int a = (int) (flash * 0xC0) << 24;
            frame(g, x, y, w, h, a | 0x00FFFFFF);
        }
        textC(g, font, txt, x + w / 2, y + (h - 8) / 2 + 1, col);
    }

    private static String lateralModeText(@Nullable FlightMode mode) {
        // Only OFF and STANDBY are reachable in the shipped build; the autonomous nav modes
        // (GO_N/S/E/W, NAV_TARGET) are permanently disabled, so anything armed reads as STABILIZE.
        if (mode == null || mode == FlightMode.OFF) return "MANUAL";
        return "STABILIZE";
    }

    // =====================================================================
    //  Flight-controls switch panel (top left), FCC Operate-page controls
    // =====================================================================
    private static void renderControls(GuiGraphics g, Font font, int x, int y, FccGuiSnapshot snap) {
        // [3]
        final java.util.List<FccGuiControlEntry> all = snap.controlEntries();
        final java.util.List<FccGuiControlEntry> entries = new java.util.ArrayList<>();
        for (FccGuiControlEntry e : all) {
            if (Math.abs(normalizeSigned(e)) > 0.02f) entries.add(e);
        }
        final int w = 146;
        final int rowH = 13;
        final int headH = 12;
        final int shown = Math.min(entries.size(), 5);
        final int extra = entries.size() - shown;
        final int h = headH + Math.max(1, shown) * rowH + (extra > 0 ? 9 : 0) + 4;
        g.fill(x, y, x + w, y + h, COL_PANEL);
        frame(g, x, y, w, h, COL_FRAME_DIM);
        g.fill(x, y, x + w, y + 1, COL_FRAME);
        text(g, font, "CONTROLS", x + 5, y + 2, COL_FRAME);
        textR(g, font, entries.size() + "/" + all.size() + " ACT", x + w - 5, y + 2,
            entries.isEmpty() ? COL_TEXT_DIM : COL_GREEN_DIM);

        if (entries.isEmpty()) {
            textC(g, font, "ALL NEUTRAL", x + w / 2, y + headH + 2, COL_TEXT_DIM);
            return;
        }

        for (int i = 0; i < shown; i++) {
            final FccGuiControlEntry e = entries.get(i);
            final int ry = y + headH + i * rowH;
            final String type = e.widgetType() == null ? "" : e.widgetType();
            final boolean discrete = type.equals("TOGGLE") || type.equals("BUTTON");
            // Label.
            final String lbl = e.label() == null || e.label().isBlank() ? type : e.label();
            text(g, font, trimTo(font, lbl, 70), x + 5, ry + 1, COL_TEXT);
            // Control visualisation on the right.
            final int ctlX = x + 80;
            final int ctlW = w - 88;
            if (discrete) {
                final boolean on = e.value() > (e.neutralValue() + 0.001);
                // Switch track + knob (slides right when ON).
                g.fill(ctlX, ry + 2, ctlX + ctlW, ry + 9, 0x60000000);
                frame(g, ctlX, ry + 2, ctlW, 7, COL_FRAME_DIM);
                final int knobW = ctlW / 2;
                final int knobX = on ? ctlX + ctlW - knobW : ctlX;
                g.fill(knobX, ry + 2, knobX + knobW, ry + 9, on ? COL_GREEN : COL_FRAME_DIM);
                textR(g, font, on ? "ON" : "OFF", x + w - 5, ry + 1, on ? COL_GREEN : COL_TEXT_DIM);
            } else {
                // Slider/lever/dial: knob at normalised signed position, neutral centre mark.
                final float n = normalizeSigned(e); // -1..1
                g.fill(ctlX, ry + 4, ctlX + ctlW, ry + 5, COL_FRAME_DIM);
                final int midX = ctlX + ctlW / 2;
                g.fill(midX, ry + 2, midX + 1, ry + 8, COL_TICK); // neutral mark
                final int knobX = Math.round(midX + n * (ctlW / 2 - 2));
                g.fill(knobX - 1, ry + 1, knobX + 2, ry + 9, COL_MAGENTA);
                final int pct = Math.round(n * 100);
                textR(g, font, (pct > 0 ? "+" : "") + pct, x + w - 5, ry + 1, COL_TEXT_DIM);
            }
        }
        if (extra > 0) {
            textC(g, font, "+" + extra + " MORE", x + w / 2, y + headH + shown * rowH + 1, COL_TEXT_DIM);
        }
    }

    private static float normalizeSigned(FccGuiControlEntry e) {
        final double neutral = e.neutralValue();
        final double value = e.value();
        if (value >= neutral) {
            final double span = Math.max(0.0001, e.inputMax() - neutral);
            return (float) Mth.clamp((value - neutral) / span, 0.0, 1.0);
        }
        final double span = Math.max(0.0001, neutral - e.inputMin());
        return (float) -Mth.clamp((neutral - value) / span, 0.0, 1.0);
    }

    private static String noseAxisText(@Nullable NoseAxis axis) {
        if (axis == null) return "?";
        return switch (axis) {
            case POS_X -> "+X (E)";
            case NEG_X -> "-X (W)";
            case POS_Z -> "+Z (S)";
            case NEG_Z -> "-Z (N)";
        };
    }

    // =====================================================================
    //  Airspeed tape (left)
    // =====================================================================
    private static void renderSpeedTape(GuiGraphics g, Font font, int x, int y, int h,
                                        float speed, float selSpeed) {
        final int w = 42;
        g.fill(x, y, x + w, y + h, COL_PANEL2);
        frame(g, x, y, w, h, COL_FRAME_DIM);
        final int midY = y + h / 2;
        final float pxPerUnit = 4.0f;  // 1 m/s = 4 px
        // Moving ticks every 1 (minor) and 5 (major+label).
        final int range = (int) (h / pxPerUnit) + 4;
        final int base = Math.round(speed);
        for (int v = base - range; v <= base + range; v++) {
            if (v < 0) continue;
            final int ty = Math.round(midY - (v - speed) * pxPerUnit);
            if (ty < y + 2 || ty > y + h - 2) continue;
            if (v % 5 == 0) {
                g.fill(x + w - 12, ty, x + w - 2, ty + 1, COL_TICK);
                text(g, font, Integer.toString(v), x + 4, ty - 4, COL_TEXT_DIM);
            } else {
                g.fill(x + w - 7, ty, x + w - 2, ty + 1, COL_TICK_DIM);
            }
        }
        // Selected-speed bug (nav-table cap).
        if (selSpeed > 0) {
            final int by = Math.round(Mth.clamp(midY - (selSpeed - speed) * pxPerUnit, y + 1, y + h - 1));
            triangleRight(g, x + w - 2, by, 4, COL_MAGENTA);
        }
        // Current-value readout box.
        final int boxH = 14;
        g.fill(x - 2, midY - boxH / 2, x + w + 6, midY + boxH / 2, 0xE0060B12);
        frame(g, x - 2, midY - boxH / 2, w + 8, boxH, COL_FRAME);
        textC(g, font, String.format(Locale.ROOT, "%3.0f", Math.max(0.0f, speed)), x + w / 2 + 2, midY - 3, COL_TEXT);
        textC(g, font, "SPD m/s", x + w / 2, y - 10, COL_FRAME_DIM);
    }

    // =====================================================================
    //  Altitude tape (right)
    // =====================================================================
    private static void renderAltTape(GuiGraphics g, Font font, int x, int y, int h,
                                      float alt, double targetAlt) {
        final int w = 46;
        g.fill(x, y, x + w, y + h, COL_PANEL2);
        frame(g, x, y, w, h, COL_FRAME_DIM);
        final int midY = y + h / 2;
        final float pxPerUnit = 2.0f; // 1 block = 2 px
        final int range = (int) (h / pxPerUnit) + 6;
        final int base = Math.round(alt);
        for (int v = base - range; v <= base + range; v++) {
            final int ty = Math.round(midY - (v - alt) * pxPerUnit);
            if (ty < y + 2 || ty > y + h - 2) continue;
            if (v % 10 == 0) {
                g.fill(x + 2, ty, x + 12, ty + 1, COL_TICK);
                text(g, font, Integer.toString(v), x + 14, ty - 4, COL_TEXT_DIM);
            } else if (v % 5 == 0) {
                g.fill(x + 2, ty, x + 8, ty + 1, COL_TICK_DIM);
            }
        }
        // Target-altitude bug.
        if (Double.isFinite(targetAlt) && targetAlt > 0) {
            final int by = (int) Math.round(Mth.clamp(midY - ((float) targetAlt - alt) * pxPerUnit, y + 1, y + h - 1));
            triangleLeft(g, x + 2, by, 4, COL_MAGENTA);
        }
        // Current-value readout box.
        final int boxH = 14;
        g.fill(x - 6, midY - boxH / 2, x + w + 2, midY + boxH / 2, 0xE0060B12);
        frame(g, x - 6, midY - boxH / 2, w + 8, boxH, COL_FRAME);
        textC(g, font, String.format(Locale.ROOT, "%4.0f", alt), x + w / 2 - 2, midY - 3, COL_TEXT);
        textC(g, font, "ALT", x + w / 2, y - 10, COL_FRAME_DIM);
    }

    // =====================================================================
    //  Vertical-speed indicator (right of altitude tape)
    // =====================================================================
    private static void renderVsi(GuiGraphics g, Font font, int x, int y, int h, float climb) {
        final int w = 26;
        g.fill(x, y, x + w, y + h, COL_PANEL2);
        frame(g, x, y, w, h, COL_FRAME_DIM);
        final int midY = y + h / 2;
        g.fill(x, midY, x + w, midY + 1, COL_TICK); // zero line
        // Scale: ±10 m/s mapped to half-height (log-ish but keep linear for clarity).
        final float maxRate = 10.0f;
        final float pxPer = (h / 2.0f - 6.0f) / maxRate;
        for (int v = -10; v <= 10; v += 2) {
            final int ty = Math.round(midY - v * pxPer);
            if (ty < y + 2 || ty > y + h - 2) continue;
            g.fill(x + w - 6, ty, x + w - 2, ty + 1, COL_TICK_DIM);
        }
        // Needle.
        final float c = Mth.clamp(climb, -maxRate, maxRate);
        final int ny = Math.round(midY - c * pxPer);
        final int col = Math.abs(climb) > 6.0f ? COL_AMBER : COL_GREEN;
        g.fill(x + 2, Math.min(midY, ny), x + w - 2, Math.max(midY, ny) + 1, (col & 0x00FFFFFF) | 0x66000000);
        g.fill(x + 1, ny - 1, x + w - 1, ny + 1, col);
        textC(g, font, "VS", x + w / 2, y - 10, COL_FRAME_DIM);
        textC(g, font, String.format(Locale.ROOT, "%+.1f", climb), x + w / 2, y + h + 2, col);
    }

    // =====================================================================
    //  Attitude indicator (centre): pitch ladder + roll arc + reference
    // =====================================================================
    private static void renderAttitude(GuiGraphics g, Font font, int cx, int cy,
                                       float pitch, float roll, float gLoad) {
        final float pxPerDeg = 2.6f;
        final int halfW = 54;

        final PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(-roll));

        // Horizon line.
        g.fill(-halfW - 16, Math.round(pitch * pxPerDeg), halfW + 16, Math.round(pitch * pxPerDeg) + 1, COL_FRAME);

        // Pitch ladder rungs every 10°.
        for (int p = -30; p <= 30; p += 10) {
            if (p == 0) continue;
            final int ry = Math.round((pitch + p) * pxPerDeg);
            if (ry < -64 || ry > 64) continue;
            final boolean up = p > 0;
            // Two short dashes either side, climb rungs angle gently down at the inner ends.
            g.fill(-halfW, ry, -halfW + 22, ry + 1, COL_FRAME_DIM);
            g.fill(halfW - 22, ry, halfW, ry + 1, COL_FRAME_DIM);
            // small vertical end caps (down for sky, up for ground)
            final int cap = up ? 3 : -3;
            g.fill(-halfW + 22 - 1, ry, -halfW + 22, ry + cap, COL_FRAME_DIM);
            g.fill(halfW - 22, ry, halfW - 22 + 1, ry + cap, COL_FRAME_DIM);
            text(g, font, Integer.toString(Math.abs(p)), -halfW - 12, ry - 4, COL_TEXT_DIM);
            text(g, font, Integer.toString(Math.abs(p)), halfW + 4, ry - 4, COL_TEXT_DIM);
        }
        pose.popPose();

        // Roll arc + pointer (fixed, above centre).
        final int arcR = 50;
        for (int a = -60; a <= 60; a += 15) {
            final double rad = Math.toRadians(a - 90);
            final int tx = cx + (int) Math.round(Math.cos(rad) * arcR);
            final int ty = cy + (int) Math.round(Math.sin(rad) * arcR);
            g.fill(tx, ty, tx + 1, ty + 1, COL_TICK);
        }
        // Roll pointer.
        final double prad = Math.toRadians(-roll - 90);
        final int px = cx + (int) Math.round(Math.cos(prad) * (arcR - 4));
        final int py = cy + (int) Math.round(Math.sin(prad) * (arcR - 4));
        g.fill(px - 1, py - 1, px + 2, py + 2, COL_AMBER);

        // Fixed aircraft reference (yellow wings + dot).
        g.fill(cx - 26, cy, cx - 8, cy + 1, COL_AMBER);
        g.fill(cx + 8, cy, cx + 26, cy + 1, COL_AMBER);
        g.fill(cx - 8, cy, cx - 7, cy + 4, COL_AMBER);
        g.fill(cx + 7, cy, cx + 8, cy + 4, COL_AMBER);
        g.fill(cx - 1, cy - 1, cx + 1, cy + 1, COL_AMBER);

        // Bank/G readouts under the reference.
        text(g, font, String.format(Locale.ROOT, "BNK %+.0f", -roll), cx - 26, cy + 8, COL_TEXT_DIM);
        text(g, font, String.format(Locale.ROOT, "G %.1f", gLoad), cx + 4, cy + 8,
            gLoad >= 3.0f ? COL_AMBER : COL_TEXT_DIM);
    }

    // =====================================================================
    //  Heading tape (bottom centre)
    // =====================================================================
    private static void renderHeadingTape(GuiGraphics g, Font font, int cx, int y, int w,
                                          float heading, @Nullable FlightMode mode) {
        final int h = 16;
        final int x = cx - w / 2;
        g.fill(x, y, x + w, y + h, COL_PANEL);
        frame(g, x, y, w, h, COL_FRAME_DIM);
        final float pxPerDeg = w / 90.0f; // show ~90° span
        final float hdg = wrap360(heading);
        for (int d = -50; d <= 50; d += 5) {
            final int deg = Math.round(hdg) + d;
            final int tx = Math.round(cx + d * pxPerDeg);
            if (tx < x + 2 || tx > x + w - 2) continue;
            final int dd = ((deg % 360) + 360) % 360;
            if (dd % 45 == 0) {
                g.fill(tx, y + 2, tx + 1, y + 8, COL_TICK);
                final String lbl = cardinal(dd);
                textC(g, font, lbl, tx, y + 9, COL_TEXT_DIM);
            } else if (dd % 15 == 0) {
                g.fill(tx, y + 2, tx + 1, y + 6, COL_TICK_DIM);
            }
        }
        // Selected-heading bug (cardinal from a GO mode).
        final int sel = selectedHeading(mode);
        if (sel >= 0) {
            float rel = wrap180(sel - hdg);
            if (Math.abs(rel) <= 50) {
                final int bx = Math.round(cx + rel * pxPerDeg);
                triangleDown(g, bx, y - 1, 4, COL_MAGENTA);
            }
        }
        // Centre index + current heading box.
        g.fill(cx, y - 3, cx + 1, y + h, COL_AMBER);
        g.fill(cx - 14, y - 13, cx + 15, y - 1, 0xE0060B12);
        frame(g, cx - 14, y - 13, 29, 12, COL_FRAME);
        textC(g, font, String.format(Locale.ROOT, "%03d", ((Math.round(hdg) % 360) + 360) % 360), cx, y - 11, COL_TEXT);
    }

    // =====================================================================
    //  EICAS engine stack (lower right), one row per bound propeller
    // =====================================================================
    private static void renderEicas(GuiGraphics g, Font font, int x, int y, int w,
                                    @Nullable FccTelemetryPacket tel, @Nullable FccGuiSnapshot snap,
                                    float[] engAct, float[] engCmd) {
        final int count = tel != null ? Math.max(0, tel.propBoundCount()) : 0;
        final int[] rows = tel != null ? tel.bindingRows() : new int[0];
        final int resolvedMask = snap != null ? snap.resolvedRowMask() : -1;
        final int[] cmdArr = tel != null ? tel.commandedRpm() : new int[0];
        final int[] actArr = tel != null ? tel.actualRpm() : new int[0];

        // [4]
        final int maxRows = 4;
        int[] display;
        if (count <= maxRows) {
            display = new int[count];
            for (int i = 0; i < count; i++) display[i] = i;
        } else {
            Integer[] byScore = new Integer[count];
            final float[] score = new float[count];
            for (int i = 0; i < count; i++) {
                byScore[i] = i;
                final int row = i < rows.length ? rows[i] : i;
                final boolean rowResolved = resolvedMask < 0 || row < 0 || (resolvedMask & (1 << row)) != 0;
                final float a = i < actArr.length ? Math.abs(actArr[i]) : 0.0f;
                final float c = i < cmdArr.length ? Math.abs(cmdArr[i]) : 0.0f;
                score[i] = (rowResolved ? 0.0f : 10_000.0f) + Math.abs(a - c);
            }
            java.util.Arrays.sort(byScore, (ia, ib) -> {
                final int cmp = Float.compare(score[ib], score[ia]);
                return cmp != 0 ? cmp : Integer.compare(ia, ib);
            });
            display = new int[maxRows];
            for (int i = 0; i < maxRows; i++) display[i] = byScore[i];
            java.util.Arrays.sort(display); // stable top-to-bottom ordering by engine index
        }
        final int shown = display.length;
        final int hidden = count - shown;

        final int rowH = 14;
        final int headH = 14;
        final int h = headH + Math.max(1, shown) * rowH + (hidden > 0 ? 10 : 0) + 6;
        g.fill(x, y, x + w, y + h, COL_PANEL);
        frame(g, x, y, w, h, COL_FRAME_DIM);
        g.fill(x, y, x + w, y + 1, COL_FRAME);
        // Header.
        final int resolved = tel != null ? tel.propResolvedCount() : 0;
        text(g, font, "ENGINES", x + 5, y + 3, COL_FRAME);
        textR(g, font, resolved + "/" + count + " RES", x + w - 5, y + 3,
            resolved < count ? COL_AMBER : COL_GREEN_DIM);

        if (count == 0) {
            textC(g, font, "NO PROPELLERS BOUND", x + w / 2, y + headH + 4, COL_TEXT_DIM);
            return;
        }

        final float[] thrustSigned = tel.thrustSigned();
        final float[] thrustVert = tel.thrustVertical();
        final int gyroMask = tel.gyroMask();

        for (int d = 0; d < shown; d++) {
            final int i = display[d];
            final int ry = y + headH + d * rowH;
            final int row = i < rows.length ? rows[i] : i;
            final boolean gyro = (gyroMask & (1 << i)) != 0;
            final boolean rowResolved = resolvedMask < 0 || row < 0 || (resolvedMask & (1 << row)) != 0;
            final float a = i < engAct.length ? engAct[i] : 0.0f;
            final float c = i < engCmd.length ? engCmd[i] : 0.0f;

            // Label "E#" (1-based, gyro marked with °).
            final String lbl = (gyro ? "G" : "E") + (row >= 0 ? (row + 1) : (i + 1));
            text(g, font, lbl, x + 5, ry + 2, rowResolved ? COL_TEXT : COL_RED);

            // N1 bar.
            final int barX = x + 26;
            final int barW = w - 64;
            g.fill(barX, ry + 2, barX + barW, ry + 9, 0x60000000);
            frame(g, barX, ry + 2, barW, 7, COL_FRAME_DIM);
            final float frac = Mth.clamp(Math.abs(a) / MAX_PROP_RPM, 0.0f, 1.0f);
            final boolean mismatch = Math.abs(Math.abs(a) - Math.abs(c)) > 24.0f;
            final int barCol = !rowResolved ? COL_RED : mismatch ? COL_AMBER : COL_GREEN;
            g.fill(barX + 1, ry + 3, barX + 1 + (int) ((barW - 2) * frac), ry + 8, barCol);
            // Commanded marker.
            final float cFrac = Mth.clamp(Math.abs(c) / MAX_PROP_RPM, 0.0f, 1.0f);
            final int cmx = barX + 1 + (int) ((barW - 2) * cFrac);
            g.fill(cmx, ry + 1, cmx + 1, ry + 10, COL_WHITE);

            // Thrust direction + RPM number.
            final float ts = i < thrustSigned.length ? thrustSigned[i] : 0.0f;
            final float tv = i < thrustVert.length ? thrustVert[i] : 0.0f;
            final String dir = Math.abs(tv) < 0.05f ? (ts >= 0 ? "\u2192" : "\u2190")
                : (tv > 0 ? "\u2191" : "\u2193");
            textR(g, font, dir + Math.round(Math.abs(a)), x + w - 5, ry + 2, rowResolved ? COL_TEXT_DIM : COL_RED);
        }

        // Summary for the engines not shown (they are the least abnormal ones by construction).
        if (hidden > 0) {
            float sum = 0.0f;
            for (int i = 0; i < count; i++) {
                boolean isShown = false;
                for (int d : display) {
                    if (d == i) {
                        isShown = true;
                        break;
                    }
                }
                if (!isShown) sum += Math.abs(i < engAct.length ? engAct[i] : 0.0f);
            }
            final int avg = Math.round(sum / hidden);
            textC(g, font, "+" + hidden + " MORE, AVG " + avg, x + w / 2, y + headH + shown * rowH + 1, COL_TEXT_DIM);
        }
    }

    // =====================================================================
    //  Systems annunciator panel (lower left)
    // =====================================================================
    private static void renderSystems(GuiGraphics g, Font font, int x, int y,
                                      @Nullable FccGuiSnapshot snap) {
        final int w = 150;
        final int h = 92;
        g.fill(x, y, x + w, y + h, COL_PANEL);
        frame(g, x, y, w, h, COL_FRAME_DIM);
        g.fill(x, y, x + w, y + 1, COL_FRAME);
        text(g, font, "SYSTEMS", x + 5, y + 3, COL_FRAME);

        if (snap == null) {
            textC(g, font, "FCC OFFLINE", x + w / 2, y + h / 2 - 4, COL_AMBER);
            return;
        }

        // Sensor annunciators.
        final boolean warm = snap.sensorsWarmedUp();
        int lx = x + 6;
        final int ly = y + 16;
        lx = sensorLight(g, font, lx, ly, "GIM", snap.availableSensors().contains(SensorRequirement.GIMBAL),
            snap.effectiveGimbalPos() != null, warm);
        lx = sensorLight(g, font, lx, ly, "ALT", snap.availableSensors().contains(SensorRequirement.ALTITUDE),
            snap.effectiveAltitudePos() != null, warm);
        lx = sensorLight(g, font, lx, ly, "VEL", snap.availableSensors().contains(SensorRequirement.VELOCITY),
            snap.effectiveVelocityPos() != null, warm);
        sensorLight(g, font, lx, ly, "NAV", snap.availableSensors().contains(SensorRequirement.NAV_TABLE),
            snap.effectiveNavPos() != null, warm);

        // Linked-controller channels.
        final int cfg = snap.linkedControllerConfiguredMask();
        final int in = snap.linkedControllerInputMask();
        final int cy2 = y + 32;
        int cxp = x + 6;
        cxp = channelLight(g, font, cxp, cy2, "THR", cfg, in, 0, 1);
        cxp = channelLight(g, font, cxp, cy2, "STR", cfg, in, 2, 3);
        cxp = channelLight(g, font, cxp, cy2, "LEV", cfg, in, 4, 5);
        cxp = channelLight(g, font, cxp, cy2, "BAY", cfg, in, 6, 6);
        channelLight(g, font, cxp, cy2, "DRP", cfg, in, 7, 7);

        // Redstone + autopilot speed cap.
        final int redActive = Integer.bitCount(snap.redstoneActiveRowMask());
        final int redCfg = Integer.bitCount(snap.redstoneRowMask());
        text(g, font, "RED " + redActive + "/" + redCfg, x + 6, y + 46,
            redActive > 0 ? COL_GREEN_DIM : COL_TEXT_DIM);
        final double eff = snap.airshipAutoEffectiveSpeedCap();
        final double req = snap.airshipAutoRequestedSpeedCap();
        final boolean capped = eff > 0 && req > 0 && eff < req - 0.05;
        if (eff > 0) {
            textR(g, font, String.format(Locale.ROOT, "ASPD %.1f", eff), x + w - 6, y + 46,
                capped ? COL_AMBER : COL_GREEN_DIM);
        }

        // Altitude hold targets.
        if (Double.isFinite(snap.targetAlt()) && snap.targetAlt() > 0) {
            text(g, font, "ALT HOLD " + Math.round(snap.targetAlt()), x + 6, y + 58, COL_TEXT_DIM);
        }

        // FE Dependent Mode buffer readout. Self-gating: with the mode off the snapshot always
        // reports powered at exactly 100 percent, so this line never draws.
        final float fePct = snap.feBufferPct();
        if (!snap.fePowered() || fePct < 99.5f) {
            final int feColor = !snap.fePowered() ? 0xFFFF4D4D
                : fePct < com.createstats.power.FeConsumer.LOW_POWER_PCT ? COL_AMBER : COL_GREEN_DIM;
            textR(g, font, snap.fePowered() ? "PWR " + Math.round(fePct) + "%" : "PWR DEAD",
                x + w - 6, y + 58, feColor);
        }

        // [5]
        java.util.List<HudWarnings.Entry> cautions = HudWarnings.collect(snap);
        if (!cautions.isEmpty()) {
            g.fill(x + 4, y + 68, x + w - 4, y + h - 4, 0x30FFC24D);
            HudWarnings.render(g, font, x + 6, y + 70, cautions, 2);
            HudWarnings.Severity top = HudWarnings.topSeverity(cautions);
            if (top.ordinal() >= HudWarnings.Severity.WARNING.ordinal()) {
                HudEffects.warningRimFlash(g, x, y, w, h, true, top.color);
            }
        } else if (capped && snap.airshipAutoReachabilityReason() != null
            && !snap.airshipAutoReachabilityReason().isBlank()) {
            text(g, font, trimTo(font, snap.airshipAutoReachabilityReason(), w - 12), x + 6, y + 72, COL_TEXT_DIM);
        } else {
            text(g, font, "NO CAUTIONS", x + 6, y + 72, COL_GREEN_DIM);
        }
    }

    private static int sensorLight(GuiGraphics g, Font font, int x, int y, String lbl,
                                   boolean available, boolean bound, boolean warm) {
        final int col = (available && warm) ? COL_GREEN : (bound ? COL_AMBER : COL_TEXT_DIM);
        g.fill(x, y + 1, x + 5, y + 6, col);
        frame(g, x, y + 1, 5, 5, 0x60000000);
        text(g, font, lbl, x + 7, y, col);
        return x + 7 + font.width(lbl) + 6;
    }

    private static int channelLight(GuiGraphics g, Font font, int x, int y, String lbl,
                                    int cfgMask, int inMask, int bitA, int bitB) {
        final boolean configured = ((cfgMask >> bitA) & 1) != 0 || ((cfgMask >> bitB) & 1) != 0;
        final boolean active = ((inMask >> bitA) & 1) != 0 || ((inMask >> bitB) & 1) != 0;
        final int col = active ? COL_GREEN : (configured ? COL_FRAME_DIM : COL_TEXT_DIM);
        text(g, font, lbl, x, y, col);
        return x + font.width(lbl) + 6;
    }

    // =====================================================================
    //  Navigation strip (lower centre)
    // =====================================================================
    private static void renderNav(GuiGraphics g, Font font, int x, int y, int w, FccGuiSnapshot snap) {
        final int h = 26;
        g.fill(x, y, x + w, y + h, COL_PANEL);
        frame(g, x, y, w, h, COL_FRAME_DIM);
        g.fill(x, y, x + w, y + 1, COL_MAGENTA);
        final String name = snap.pathfinderRouteName().isBlank() ? "ROUTE" : snap.pathfinderRouteName();
        text(g, font, trimTo(font, name, w - 60), x + 5, y + 3, COL_MAGENTA);
        textR(g, font, "WPT " + (snap.pathfinderRouteWaypointIndex() + 1) + "/" + snap.pathfinderRouteWaypointCount(),
            x + w - 5, y + 3, COL_TEXT_DIM);
        textR(g, font, String.format(Locale.ROOT, "%.0fm", snap.pathfinderRouteDistance()), x + w - 5, y + 14, COL_TEXT_DIM);
        final String status = snap.pathfinderRouteAccepted() ? "ENGAGED" : "ARMED";
        text(g, font, status, x + 5, y + 14, snap.pathfinderRouteAccepted() ? COL_GREEN : COL_AMBER);
        // Confidence bar.
        final int barX = x + 56;
        final int barW = w - 130;
        if (barW > 10) {
            g.fill(barX, y + 16, barX + barW, y + 20, 0x60000000);
            g.fill(barX, y + 16, barX + (int) (barW * Mth.clamp(snap.pathfinderRouteConfidence(), 0f, 1f)), y + 20, COL_GREEN_DIM);
        }
    }

    // =====================================================================
    //  Small drawing + math helpers
    // =====================================================================
    private static void frame(GuiGraphics g, int x, int y, int w, int h, int col) {
        g.fill(x, y, x + w, y + 1, col);
        g.fill(x, y + h - 1, x + w, y + h, col);
        g.fill(x, y, x + 1, y + h, col);
        g.fill(x + w - 1, y, x + w, y + h, col);
    }

    private static void text(GuiGraphics g, Font font, String s, int x, int y, int col) {
        g.drawString(font, s, x, y, col, true);
    }

    private static void textC(GuiGraphics g, Font font, String s, int cx, int y, int col) {
        g.drawString(font, s, cx - font.width(s) / 2, y, col, true);
    }

    private static void textR(GuiGraphics g, Font font, String s, int rx, int y, int col) {
        g.drawString(font, s, rx - font.width(s), y, col, true);
    }

    private static void triangleRight(GuiGraphics g, int x, int y, int s, int col) {
        for (int i = 0; i < s; i++) g.fill(x - s + i, y - (s - i), x - s + i + 1, y + (s - i) + 1, col);
    }

    private static void triangleLeft(GuiGraphics g, int x, int y, int s, int col) {
        for (int i = 0; i < s; i++) g.fill(x + i, y - i, x + i + 1, y + i + 1, col);
    }

    private static void triangleDown(GuiGraphics g, int cx, int y, int s, int col) {
        for (int i = 0; i < s; i++) g.fill(cx - (s - i), y - i, cx + (s - i) + 1, y - i + 1, col);
    }

    private static String cardinal(int deg) {
        return switch (deg % 360) {
            case 0 -> "N";
            case 45 -> "NE";
            case 90 -> "E";
            case 135 -> "SE";
            case 180 -> "S";
            case 225 -> "SW";
            case 270 -> "W";
            case 315 -> "NW";
            default -> Integer.toString(deg);
        };
    }

    private static int selectedHeading(@Nullable FlightMode mode) {
        // [6]
        return -1;
    }

    private static String trimTo(Font font, String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        final StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (font.width(b.toString() + s.charAt(i) + "…") > maxW) break;
            b.append(s.charAt(i));
        }
        return b + "…";
    }

    private static float wrap360(float deg) {
        float d = deg % 360.0f;
        return d < 0 ? d + 360.0f : d;
    }

    private static float wrap180(float deg) {
        float d = (deg + 180.0f) % 360.0f;
        if (d < 0) d += 360.0f;
        return d - 180.0f;
    }

    private static void scaleAround(GuiGraphics g, float ax, float ay, float scale, Runnable draw) {
        if (Math.abs(scale - 1.0f) < 0.001f) {
            draw.run();
            return;
        }
        g.pose().pushPose();
        g.pose().translate(ax, ay, 0);
        g.pose().scale(scale, scale, 1.0f);
        g.pose().translate(-ax, -ay, 0);
        draw.run();
        g.pose().popPose();
    }
}
