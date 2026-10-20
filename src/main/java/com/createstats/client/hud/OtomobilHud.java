package com.createstats.client.hud;

import com.createstats.flight.network.FccGuiSnapshot;
import com.createstats.flight.network.FccTelemetryPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

@OnlyIn(Dist.CLIENT)
public final class OtomobilHud {
    private static final int COL_FRAME       = 0xFFB8FFE8;
    private static final int COL_FRAME_DIM   = 0xC07FE2C8;
    private static final int COL_TEXT        = 0xFFE8FFF4;
    private static final int COL_TEXT_DIM    = 0xC0A8C8C0;
    private static final int COL_AMBER       = 0xFFFFC34D;
    private static final int COL_AMBER_DIM   = 0xC0E2A23D;
    private static final int COL_CYAN        = 0xFF7BE6FF;
    private static final int COL_CYAN_DIM    = 0xC05CC9E2;
    private static final int COL_OK          = 0xFF78FFC4;
    private static final int COL_WARN        = 0xFFFFC965;
    private static final int COL_DANGER      = 0xFFFF6F6F;
    private static final int COL_PANEL_BG    = 0x90050810;
    private static final int COL_PANEL_BG2   = 0x60050810;
    private static final int COL_TICK_DIM    = 0x60B8FFE8;

    private static long lastFrameNanos = 0L;
    private static float smoothedSpeedMps = 0.0f;
    private static float smoothedCommandedRpm = 0.0f;
    private static float smoothedActualRpm = 0.0f;
    private static float smoothedSlipPct = 0.0f;
    private static float smoothedSteerDeg = 0.0f;
    private static float smoothedTotalFuel = 0.0f;
    private static float fuelBarPctSmoothed = 0.0f;
    private static float warningPulse = 0.0f;
    private static int gearDirection = 0;
    private static long gearChangeTimeNanos = 0L;
    private static float gearChangeAnim = 0.0f;
    private static double tripDistanceM = 0.0;
    private static float fuelShimmerPhase = 0.0f;

    private static float topSpeedSessionMps = 0.0f;
    private static double avgSpeedSumMxS = 0.0;
    private static double avgSpeedWindowSec = 0.0;
    private static double driveTimeSessionSec = 0.0;
    private static long topSpeedBrokenNanos = 0L;
    private static float topSpeedBumpAnim = 0.0f;
    private static double pendingTotalTripFlushM = 0.0;
    private static double pendingTotalDriveFlushSec = 0.0;

    private static float prevForwardMps = 0.0f;
    private static float prevLateralMps = 0.0f;
    private static float smoothedForwardAccelG = 0.0f;
    private static float smoothedLateralAccelG = 0.0f;
    private static final float MOVING_EPS_MPS = 0.10f;

    private OtomobilHud() {
    }

    public static void render(GuiGraphics graphics, Minecraft mc, @Nullable BlockPos statsDisplayPos,
                              int width, int height, int centerX, int centerY, float hudScale,
                              @Nullable FccTelemetryPacket telemetry, @Nullable FccGuiSnapshot snapshot,
                              boolean hasTelemetry, boolean signalStale, boolean tankMode,
                              float viewHeading, float craftHeading,
                              float velX, float velY, float velZ,
                              DeltaTracker deltaTracker) {
        long now = System.nanoTime();
        float dtSec = lastFrameNanos == 0L ? 1.0f / 60.0f
            : Math.min(0.25f, (now - lastFrameNanos) / 1_000_000_000.0f);
        lastFrameNanos = now;

        int[] commanded = telemetry != null ? telemetry.vehicleWheelRpm() : new int[6];
        int[] actual    = telemetry != null ? telemetry.actualWheelRpm()  : new int[6];
        int[] steer     = telemetry != null ? telemetry.vehicleWheelSteer() : new int[6];

        int commandedAvg = mean(commanded);
        int actualAvg    = mean(actual);
        int maxAbsCmd    = maxAbs(commanded);
        int maxAbsAct    = maxAbs(actual);

        float groundMps = Math.max(0.0f, smoothedSpeedMps);

        float rpmPerMps = 19.1f;
        float[] wheelLoss = new float[6];
        for (int i = 0; i < 6; i++) {
            int c = i < commanded.length ? commanded[i] : 0;
            int a = i < actual.length    ? actual[i]    : 0;
            float absA = Math.abs(a);
            float absC = Math.abs(c);
            float expected = groundMps * rpmPerMps;

            float spin = 0.0f;
            if (absA > 80.0f && absA > expected * 1.6f + 30.0f) {
                spin = Mth.clamp((absA - (expected * 1.6f + 30.0f)) / 200.0f, 0.0f, 1.0f);
            }

            float lock = 0.0f;
            if (absC > 60.0f && absA < absC * 0.25f) {
                lock = Mth.clamp(1.0f - (absA / Math.max(1.0f, absC * 0.25f)), 0.0f, 1.0f);
            }
            wheelLoss[i] = Math.max(spin, lock);
        }

        float tractionLossPct = 0.0f;
        for (float wl : wheelLoss) tractionLossPct = Math.max(tractionLossPct, wl);
        tractionLossPct *= 100.0f;

        float steerSignal = 0.0f;
        int steerCount = 0;
        for (int i = 0; i < 2 && i < steer.length; i++) {
            steerSignal += steer[i];
            steerCount++;
        }
        if (steerCount > 0) steerSignal /= steerCount;

        float steerTargetDeg = (steerSignal / 15.0f) * 450.0f;
        steerTargetDeg = Mth.clamp(steerTargetDeg, -450.0f, 450.0f);

        // horizontal only, slopes inflated the speedo

        float speedTargetMps = hasTelemetry ? (float) Math.hypot(velX, velZ) : Float.NaN;

        smoothedSpeedMps = HudEasing.expSmooth(smoothedSpeedMps, speedTargetMps, 0.10f, dtSec);
        smoothedCommandedRpm = HudEasing.expSmooth(smoothedCommandedRpm, commandedAvg, 0.08f, dtSec);
        smoothedActualRpm    = HudEasing.expSmooth(smoothedActualRpm,    actualAvg,    0.08f, dtSec);
        smoothedSlipPct      = HudEasing.expSmooth(smoothedSlipPct,      tractionLossPct, 0.20f, dtSec);
        smoothedSteerDeg     = HudEasing.expSmooth(smoothedSteerDeg,     steerTargetDeg, 0.12f, dtSec);

        float yawRad = (float) Math.toRadians(craftHeading);

        float fwdX = -(float) Math.sin(yawRad);
        float fwdZ =  (float) Math.cos(yawRad);
        float forwardMps = velX * fwdX + velZ * fwdZ;

        if (Float.isFinite(speedTargetMps)) {
            double deltaM = speedTargetMps * dtSec;
            tripDistanceM += deltaM;
            if (tripDistanceM > 9_999_999.0) tripDistanceM = 9_999_999.0;
            pendingTotalTripFlushM += deltaM;
        }

        float rightX =  (float) Math.cos(yawRad);
        float rightZ =  (float) Math.sin(yawRad);
        float lateralMps = velX * rightX + velZ * rightZ;

        if (Float.isFinite(forwardMps) && Float.isFinite(lateralMps) && dtSec > 1.0e-4f) {
            float fwdAccelMpss = (forwardMps - prevForwardMps) / dtSec;
            float latAccelMpss = (lateralMps - prevLateralMps) / dtSec;
            float fwdG = fwdAccelMpss / 9.81f;
            float latG = latAccelMpss / 9.81f;

            if (Math.abs(fwdG) < 25.0f) smoothedForwardAccelG = HudEasing.expSmooth(smoothedForwardAccelG, fwdG, 0.18f, dtSec);
            if (Math.abs(latG) < 25.0f) smoothedLateralAccelG = HudEasing.expSmooth(smoothedLateralAccelG, latG, 0.18f, dtSec);
        }
        prevForwardMps = Float.isFinite(forwardMps) ? forwardMps : 0.0f;
        prevLateralMps = Float.isFinite(lateralMps) ? lateralMps : 0.0f;

        float absSpeed = Float.isFinite(speedTargetMps) ? speedTargetMps : 0.0f;
        if (absSpeed > MOVING_EPS_MPS) {
            avgSpeedSumMxS += absSpeed * dtSec;
            avgSpeedWindowSec += dtSec;
            driveTimeSessionSec += dtSec;
            pendingTotalDriveFlushSec += dtSec;
            if (absSpeed > topSpeedSessionMps) topSpeedSessionMps = absSpeed;
            float allTime = OtomobilPersistedStats.getTopSpeedAllTimeMps();
            if (absSpeed > allTime) {
                OtomobilPersistedStats.recordTopSpeedSample(absSpeed);
                topSpeedBrokenNanos = now;
            }
        }

        if (pendingTotalTripFlushM >= 1.0) {
            OtomobilPersistedStats.addTripDistanceM(pendingTotalTripFlushM);
            pendingTotalTripFlushM = 0.0;
        }
        if (pendingTotalDriveFlushSec >= 1.0) {
            OtomobilPersistedStats.addDriveTimeSec(pendingTotalDriveFlushSec);
            pendingTotalDriveFlushSec = 0.0;
        }

        float bumpAge = (now - topSpeedBrokenNanos) / 1_000_000_000.0f;
        topSpeedBumpAnim = topSpeedBrokenNanos == 0L
            ? 0.0f
            : 1.0f - HudEasing.easeOutCubic(Mth.clamp(bumpAge / 0.7f, 0.0f, 1.0f));
        float thrustAvg = vehicleThrustAverage(telemetry);
        int newGear = gearDirection;
        if (forwardMps > 0.25f)                 newGear = 1;
        else if (forwardMps < -0.25f)           newGear = -1;
        else if (Math.abs(forwardMps) < 0.10f) {
            if (thrustAvg > 0.10f)               newGear = 1;
            else if (thrustAvg < -0.10f)         newGear = -1;
            else                                 newGear = 0;
        }
        if (newGear != gearDirection) {
            gearDirection = newGear;
            gearChangeTimeNanos = now;
        }
        float gearAge = (now - gearChangeTimeNanos) / 1_000_000_000.0f;
        gearChangeAnim = 1.0f - HudEasing.easeOutCubic(Mth.clamp(gearAge / 0.35f, 0.0f, 1.0f));

        warningPulse = (float) (0.5 + 0.5 * Math.sin((now / 1_000_000_000.0) * Math.PI * 2.0));

        HelmetFuelSnapshot.Snapshot fuel = HelmetFuelSnapshot.read(statsDisplayPos);
        smoothedTotalFuel = HudEasing.expSmooth(smoothedTotalFuel, fuel.totalMb, 0.25f, dtSec);

        float fuelBarTargetPct = fuel.totalCapacityMb > 0.5f
            ? Mth.clamp(smoothedTotalFuel / fuel.totalCapacityMb, 0.0f, 1.0f)
            : 0.0f;
        fuelBarPctSmoothed = HudEasing.expSmooth(fuelBarPctSmoothed, Mth.clamp(fuelBarTargetPct, 0.0f, 1.0f), 0.20f, dtSec);
        fuelShimmerPhase += dtSec * 0.6f;
        if (fuelShimmerPhase > 1.0f) fuelShimmerPhase -= 1.0f;

        Font font = mc.font;

        int headingY = 18;
        int headingW = Math.min(width - 160, 260);
        scaleAt(graphics, centerX, headingY, hudScale, () ->
            renderHeadingTape(graphics, font, centerX, headingY, headingW, viewHeading));

        int chipX = centerX - 80;
        int chipY = 44;
        scaleAt(graphics, chipX + 80, chipY, hudScale, () ->
            renderLinkStatusChip(graphics, font, chipX, chipY, 160, hasTelemetry, statsDisplayPos != null));

        int speedCX = centerX;
        int speedCY = height - 92;
        final boolean speedLive = hasTelemetry;
        scaleAt(graphics, speedCX, speedCY, hudScale, () ->
            renderSpeedWidget(graphics, font, speedCX, speedCY, smoothedSpeedMps, gearDirection < 0, speedLive));

        int tripCX = centerX;
        int tripCY = speedCY + 36;
        final double tripDistanceCapture = tripDistanceM;
        scaleAt(graphics, tripCX, tripCY, hudScale, () ->
            renderTripLine(graphics, font, tripCX, tripCY, tripDistanceCapture));

        int gearCX = centerX;
        int gearCY = speedCY - 38;
        scaleAt(graphics, gearCX, gearCY, hudScale, () ->
            renderGearChip(graphics, font, gearCX, gearCY, gearDirection, gearChangeAnim));

        int wheelCX = 96;
        int wheelCY = height - 90;
        int wheelR  = 44;
        final float steerSignalFinal = steerSignal;
        if (tankMode) {
            scaleAt(graphics, wheelCX, wheelCY, hudScale, () ->
                renderTrackBars(graphics, font, wheelCX, wheelCY, wheelR, commanded, actual));
        } else {
            scaleAt(graphics, wheelCX, wheelCY, hudScale, () ->
                renderSteeringWheel(graphics, font, wheelCX, wheelCY, wheelR, smoothedSteerDeg, steerSignalFinal));
        }

        int rpmX = 18;
        int rpmY = 70;
        final boolean tankTrim = tankMode;
        scaleAt(graphics, rpmX, rpmY, hudScale, () ->
            renderRpmBlock(graphics, font, rpmX, rpmY, 190, 96, tankTrim,
                smoothedCommandedRpm, smoothedActualRpm, smoothedSlipPct,
                commanded, actual, maxAbsCmd, maxAbsAct, wheelLoss, now));

        int fuelX = rpmX;
        int fuelY = rpmY + 96 + 6;
        int fuelW = 190;
        int fuelH = computeFuelBlockHeight(fuel);
        scaleAt(graphics, fuelX + fuelW, fuelY, hudScale, () ->
            renderFuelBlock(graphics, font, fuelX, fuelY, fuelW, fuel, smoothedTotalFuel, fuelBarPctSmoothed, fuelShimmerPhase, now));

        int statsW = fuelW;
        int statsH = 76;
        int statsX = fuelX;
        int statsY = fuelY + fuelH + 6;
        final float topSpeedSessionCapture = topSpeedSessionMps;
        final float topSpeedAllTimeCapture = OtomobilPersistedStats.getTopSpeedAllTimeMps();
        final float avgSpeedCapture = avgSpeedWindowSec > 0.1
            ? (float) (avgSpeedSumMxS / avgSpeedWindowSec) : 0.0f;
        final double driveSessionCapture = driveTimeSessionSec;
        final double driveTotalCapture = OtomobilPersistedStats.getTotalDriveTimeSec();
        final float topSpeedBumpCapture = topSpeedBumpAnim;
        final float forwardAccelCapture = smoothedForwardAccelG;
        final float lateralAccelCapture = smoothedLateralAccelG;
        scaleAt(graphics, statsX + statsW, statsY, hudScale, () ->
            renderStatsPanel(graphics, font, statsX, statsY, statsW, statsH,
                smoothedSpeedMps, topSpeedSessionCapture, topSpeedAllTimeCapture,
                avgSpeedCapture, driveSessionCapture, driveTotalCapture,
                forwardAccelCapture, lateralAccelCapture,
                topSpeedBumpCapture, now));

        java.util.List<HudWarnings.Entry> warnings =
            collectWarnings(fuel, smoothedSlipPct, smoothedTotalFuel, signalStale, snapshot);
        if (!warnings.isEmpty()) {
            int warnCX = centerX;
            int warnY = headingY + 28;
            scaleAt(graphics, warnCX, warnY, hudScale, () ->
                renderWarningStack(graphics, font, warnCX, warnY, warnings, warningPulse));
        }
    }

    private static void renderSpeedWidget(GuiGraphics graphics, Font font, int cx, int cy, float mps, boolean reverse, boolean live) {
        int boxW = 144;
        int boxH = 56;
        int x = cx - boxW / 2;
        int y = cy - boxH / 2;

        graphics.fill(x, y, x + boxW, y + boxH, COL_PANEL_BG);
        drawFrame(graphics, x, y, boxW, boxH, live ? COL_AMBER_DIM : COL_WARN);

        String header = "SPEED  m/s";
        graphics.drawString(font, header, x + 8, y + 4, COL_AMBER_DIM, false);

        int displayInt = (int) Math.floor(Math.max(0, Math.abs(mps)));
        String big = live ? String.format(Locale.ROOT, "%3d", Math.min(displayInt, 999)) : "---";
        float dec = Math.max(0.0f, Math.abs(mps)) - displayInt;
        String small = live ? String.format(Locale.ROOT, ".%d", (int) Math.floor(dec * 10.0f)) : ".-";

        float scale = 2.6f;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        int digitX = x + boxW - 8;
        int digitY = y + 18;

        int bigW = (int) Math.ceil(font.width(big) * scale);
        pose.translate(digitX - bigW, digitY, 0);
        pose.scale(scale, scale, 1.0f);
        graphics.drawString(font, big, 0, 0, COL_AMBER, false);
        pose.popPose();

        int smallW = font.width(small);
        graphics.drawString(font, small, x + boxW - 6 - smallW, y + boxH - 14, COL_AMBER_DIM, false);

        if (reverse) {
            int dotX = x + 8;
            int dotY = y + boxH - 12;
            graphics.fill(dotX, dotY, dotX + 6, dotY + 6, COL_WARN);
        }
    }

    private static void renderGearChip(GuiGraphics graphics, Font font, int cx, int cy, int direction, float bumpAnim) {
        String text;
        int color;
        switch (direction) {
            case 1 -> { text = "▲  FWD"; color = COL_OK; }
            case -1 -> { text = "▼  REV"; color = COL_WARN; }
            default -> { text = "■  IDLE"; color = COL_TEXT_DIM; }
        }
        int textW = font.width(text);
        int padX = 10;
        int padY = 3;
        int w = textW + padX * 2;
        int h = font.lineHeight + padY * 2;
        int x = cx - w / 2;
        int y = cy - h / 2;

        float bump = 1.0f + bumpAnim * 0.12f;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.scale(bump, bump, 1.0f);
        pose.translate(-cx, -cy, 0);
        graphics.fill(x, y, x + w, y + h, COL_PANEL_BG);
        drawFrame(graphics, x, y, w, h, color);
        graphics.drawString(font, text, x + padX, y + padY, color, false);
        pose.popPose();
    }

    private static void renderSteeringWheel(GuiGraphics graphics, Font font, int cx, int cy, int r,
                                            float steerDeg, float rawSignal) {
        int boxR = r + 18;
        graphics.fill(cx - boxR, cy - boxR, cx + boxR, cy + boxR, COL_PANEL_BG2);

        drawDottedCircle(graphics, cx, cy, r, COL_CYAN_DIM, 5);
        drawDottedCircle(graphics, cx, cy, r - 4, COL_TICK_DIM, 9);

        for (int ang = 0; ang < 360; ang += 30) {
            float rad = (float) Math.toRadians(ang - 90);
            int x1 = cx + Math.round(Mth.cos(rad) * (r - 2));
            int y1 = cy + Math.round(Mth.sin(rad) * (r - 2));
            int x2 = cx + Math.round(Mth.cos(rad) * (r + 3));
            int y2 = cy + Math.round(Mth.sin(rad) * (r + 3));
            drawLine(graphics, x1, y1, x2, y2, COL_FRAME_DIM);
        }

        float rad = (float) Math.toRadians(steerDeg - 90);
        int px = cx + Math.round(Mth.cos(rad) * (r - 6));
        int py = cy + Math.round(Mth.sin(rad) * (r - 6));

        drawLine(graphics, cx, cy, px, py, COL_AMBER);

        drawFilledDiamond(graphics, px, py, 3, COL_AMBER);

        graphics.fill(cx - 3, cy - 3, cx + 4, cy + 4, COL_CYAN);

        String label = String.format(Locale.ROOT, "STEER  %+4d°  (%+d/15)",
            Math.round(steerDeg), Math.round(rawSignal));
        int lw = font.width(label);
        graphics.drawString(font, label, cx - lw / 2, cy + r + 6, COL_TEXT_DIM, false);

        graphics.drawString(font, "L", cx - r - 14, cy - 4, COL_FRAME_DIM, false);
        graphics.drawString(font, "R", cx + r + 8,  cy - 4, COL_FRAME_DIM, false);
    }

    private static void renderRpmBlock(GuiGraphics graphics, Font font, int x, int y, int w, int h,
                                       boolean tankMode,
                                       float comAvg, float actAvg, float slipPct,
                                       int[] commanded, int[] actual,
                                       int maxAbsCmd, int maxAbsAct,
                                       float[] wheelLoss, long nowNanos) {
        graphics.fill(x, y, x + w, y + h, COL_PANEL_BG);
        drawFrame(graphics, x, y, w, h, COL_CYAN_DIM);
        graphics.drawString(font, tankMode ? "TRACK RPM" : "WHEEL RPM", x + 8, y + 4, COL_CYAN, false);

        String comStr = String.format(Locale.ROOT, "COM  %+5d", Math.round(comAvg));
        graphics.drawString(font, comStr, x + 8, y + 18, COL_AMBER, false);

        String actStr = String.format(Locale.ROOT, "ACT  %+5d", Math.round(actAvg));
        graphics.drawString(font, actStr, x + 8, y + 30, COL_OK, false);

        int slipColor = slipPct < 10.0f ? COL_OK : slipPct < 30.0f ? COL_WARN : COL_DANGER;
        String slipStr = String.format(Locale.ROOT, "SLIP %4.0f%%", Math.min(slipPct, 999.0f));
        graphics.drawString(font, slipStr, x + 8, y + 42, slipColor, false);

        int dotsX = x + 110;
        int dotsY = y + 18;
        int dotSize = 10;
        int gap = 6;
        String[] labels = {"FL", "FR", "ML", "MR", "RL", "RR"};
        int peak = Math.max(maxAbsAct, Math.max(maxAbsCmd, 64));
        for (int i = 0; i < 6; i++) {
            int col = i % 2;
            int row = i / 2;
            int dx = dotsX + col * (dotSize + gap + 18);
            int dy = dotsY + row * (dotSize + gap + 2);
            float ratio = peak <= 0 ? 0.0f : Math.min(1.0f, Math.abs((float) actual[i]) / (float) peak);
            int dotColor = ratio < 0.05f ? COL_TEXT_DIM
                : ratio < 0.35f ? HudEasing.blendArgb(COL_TEXT_DIM, COL_OK, ratio * 2.0f)
                : ratio < 0.75f ? HudEasing.blendArgb(COL_OK, COL_AMBER, (ratio - 0.35f) / 0.40f)
                : HudEasing.blendArgb(COL_AMBER, COL_DANGER, Math.min(1.0f, (ratio - 0.75f) / 0.25f));
            graphics.fill(dx, dy, dx + dotSize, dy + dotSize, dotColor);

            if (commanded[i] != 0 && Math.abs(actual[i]) < Math.abs(commanded[i]) / 4) {
                drawRect(graphics, dx - 1, dy - 1, dotSize + 2, dotSize + 2, COL_DANGER);
            }
            graphics.drawString(font, labels[i], dx + dotSize + 2, dy + 1, COL_TEXT_DIM, false);

            float lossI = wheelLoss != null && i < wheelLoss.length ? wheelLoss[i] : 0.0f;
            if (lossI > 0.05f) {
                drawTractionWarning(graphics, dx + dotSize + 14, dy - 1, lossI, nowNanos);
            }
        }
    }

    private static void drawTractionWarning(GuiGraphics graphics, int x, int y, float intensity, long nowNanos) {
        float t = (nowNanos / 1_000_000_000.0f);

        float blinkHz = 3.0f + intensity * 5.0f;
        float phase = (float)(0.5 + 0.5 * Math.sin(t * blinkHz * Math.PI * 2.0));

        int baseCol = HudEasing.blendArgb(COL_WARN, COL_DANGER, Mth.clamp(intensity, 0.0f, 1.0f));

        float alpha = 0.55f + 0.45f * phase * intensity;
        int colWithAlpha = (Math.round(alpha * 255.0f) << 24) | (baseCol & 0x00FFFFFF);

        int tw = 9;
        int th = 8;
        for (int row = 0; row < th; row++) {
            int span = Math.max(1, Math.round((row + 1) * tw / (float) th));
            int sx = x + (tw - span) / 2;
            graphics.fill(sx, y + row, sx + span, y + row + 1, colWithAlpha);
        }

        graphics.fill(x + tw / 2, y, x + tw / 2 + 1, y + 1, 0xFF000000);

        if (phase > 0.4f) {
            int dotCol = 0xFFFFFFFF;
            graphics.fill(x + tw / 2, y + th - 3, x + tw / 2 + 1, y + th - 2, dotCol);
            graphics.fill(x + tw / 2, y + th - 5, x + tw / 2 + 1, y + th - 4, dotCol);
        }
    }

    private static void renderTrackBars(GuiGraphics graphics, Font font, int cx, int cy, int r,
                                        int[] commanded, int[] actual) {
        int boxR = r + 18;
        graphics.fill(cx - boxR, cy - boxR, cx + boxR, cy + boxR, COL_PANEL_BG2);
        float left = sideMeanRpm(actual, 0);
        float right = sideMeanRpm(actual, 1);
        float leftCmd = sideMeanRpm(commanded, 0);
        float rightCmd = sideMeanRpm(commanded, 1);
        float peak = Math.max(64.0f, Math.max(
            Math.max(Math.abs(left), Math.abs(right)),
            Math.max(Math.abs(leftCmd), Math.abs(rightCmd))));
        int barW = 14;
        int gap = 24;
        drawTrackBar(graphics, font, cx - gap - barW, cy, barW, r, left, leftCmd, peak, "L");
        drawTrackBar(graphics, font, cx + gap, cy, barW, r, right, rightCmd, peak, "R");

        float diff = right - left;
        if (Math.abs(diff) > peak * 0.12f) {
            String chevron = diff > 0 ? "<<" : ">>";
            graphics.drawString(font, chevron, cx - font.width(chevron) / 2, cy - 4, COL_AMBER, false);
        }
        String label = String.format(Locale.ROOT, "TRACKS  L %+d  R %+d", Math.round(left), Math.round(right));
        graphics.drawString(font, label, cx - font.width(label) / 2, cy + r + 6, COL_TEXT_DIM, false);
    }

    private static void drawTrackBar(GuiGraphics graphics, Font font, int x, int cy, int w, int halfH,
                                     float actualRpm, float commandedRpm, float peak, String label) {
        graphics.fill(x, cy - halfH, x + w, cy + halfH, COL_PANEL_BG);
        drawRect(graphics, x, cy - halfH, w, halfH * 2, COL_CYAN_DIM);
        graphics.fill(x, cy, x + w, cy + 1, COL_FRAME_DIM);
        float frac = Mth.clamp(actualRpm / peak, -1.0f, 1.0f);
        int fillH = Math.round(Math.abs(frac) * (halfH - 2));
        int color = Math.abs(frac) > 0.75f ? COL_AMBER : COL_OK;
        if (frac >= 0.0f) {
            graphics.fill(x + 2, cy - fillH, x + w - 2, cy, color);
        } else {
            graphics.fill(x + 2, cy, x + w - 2, cy + fillH, color);
        }
        float cFrac = Mth.clamp(commandedRpm / peak, -1.0f, 1.0f);
        int tickY = cy - Math.round(cFrac * (halfH - 2));
        graphics.fill(x, tickY, x + w, tickY + 1, 0xFFFFFFFF);
        graphics.drawString(font, label, x + w / 2 - font.width(label) / 2, cy + halfH + 3, COL_TEXT_DIM, false);
    }

    private static float sideMeanRpm(int[] arr, int side) {
        if (arr == null) return 0.0f;
        long sum = 0;
        int n = 0;
        for (int i = side; i < Math.min(6, arr.length); i += 2) {
            sum += arr[i];
            n++;
        }
        return n == 0 ? 0.0f : (float) sum / n;
    }

    private static void renderFuelBlock(GuiGraphics graphics, Font font, int x, int y, int w,
                                        HelmetFuelSnapshot.Snapshot fuel,
                                        float smoothedTotal, float barPct,
                                        float shimmerPhase, long nowNanos) {
        int h = computeFuelBlockHeight(fuel);
        graphics.fill(x, y, x + w, y + h, COL_PANEL_BG);
        drawFrame(graphics, x, y, w, h, COL_AMBER_DIM);

        float dotPulse = (float)(0.5 + 0.5 * Math.sin((nowNanos / 1_000_000_000.0) * Math.PI * 2.0 * 0.9));
        int dotColorBase = fuel.hasFuel() ? fuel.dominantKind.color : COL_FRAME_DIM;
        int dotColor = fuel.hasFuel()
            ? HudEasing.blendArgb(dotColorBase, 0xFFFFFFFF, 0.15f + 0.25f * dotPulse)
            : dotColorBase;
        graphics.fill(x + 6, y + 6, x + 12, y + 12, dotColor);
        String title = fuel.linked ? (fuel.hasFuel() ? fuel.dominantKind.displayName.toUpperCase(Locale.ROOT) : "NO FUEL") : "FUEL LINK?";
        graphics.drawString(font, title, x + 18, y + 4, fuel.linked ? COL_AMBER : COL_TEXT_DIM, false);

        String headerStr;
        if (!fuel.linked) {
            headerStr = ",";
        } else if (fuel.totalCapacityMb > 0.5f) {
            float pct = 100.0f * fuel.fillPct();
            headerStr = String.format(Locale.ROOT, "%.0f / %.0f mB  (%.0f%%)", smoothedTotal, fuel.totalCapacityMb, pct);
        } else {
            headerStr = String.format(Locale.ROOT, "%6.0f mB", smoothedTotal);
        }
        int totW = font.width(headerStr);
        graphics.drawString(font, headerStr, x + w - 8 - totW, y + 4, COL_TEXT, false);

        int barX = x + 8;
        int barY = y + 18;
        int barW = w - 16;
        int barH = 4;
        graphics.fill(barX, barY, barX + barW, barY + barH, COL_PANEL_BG2);
        if (fuel.linked && barPct > 0.0f) {
            int fillW = Math.max(1, Math.round(barW * Mth.clamp(barPct, 0.0f, 1.0f)));
            int barColor = barPct < 0.15f ? COL_DANGER : barPct < 0.35f ? COL_WARN : COL_AMBER;
            graphics.fill(barX, barY, barX + fillW, barY + barH, barColor);

            int shimmerX = barX + Math.round(shimmerPhase * fillW);
            int shimmerW = Math.max(2, fillW / 8);
            int shimmerEnd = Math.min(barX + fillW, shimmerX + shimmerW);
            if (shimmerEnd > shimmerX) {
                int shineCol = 0x80FFFFFF;
                graphics.fill(shimmerX, barY, shimmerEnd, barY + barH, shineCol);
            }
        }

        if (fuel.linked && !fuel.entries.isEmpty()) {
            int listY = y + 28;
            int rows = Math.min(5, fuel.entries.size());
            float maxAmt = 0.0f;
            for (int i = 0; i < rows; i++) maxAmt = Math.max(maxAmt, fuel.entries.get(i).amountMb);
            if (maxAmt < 1.0f) maxAmt = 1.0f;
            float dropPhase = (float)((nowNanos / 1_000_000_000.0) * 1.5);
            for (int i = 0; i < rows; i++) {
                HelmetFuelSnapshot.Entry e = fuel.entries.get(i);
                int rowY = listY + i * 11;

                graphics.fill(barX, rowY + 1, barX + 4, rowY + 5, e.kind.color);

                float ratio = e.amountMb / maxAmt;
                int dropY = rowY + 2 + ((int)(Math.sin(dropPhase + i * 0.8f) * 1.5f));
                int dropW = Math.max(2, Math.round(3.0f * (0.4f + 0.6f * ratio)));
                graphics.fill(barX + 6, dropY, barX + 6 + dropW, dropY + 2, e.kind.color);
                String name = e.label;
                if (font.width(name) > barW - 70) {
                    while (name.length() > 1 && font.width(name + "…") > barW - 70) {
                        name = name.substring(0, name.length() - 1);
                    }
                    name = name + "…";
                }
                graphics.drawString(font, name, barX + 14, rowY, COL_TEXT_DIM, false);
                String amt = String.format(Locale.ROOT, "%5.0f", e.amountMb);
                int aw = font.width(amt);
                graphics.drawString(font, amt, x + w - 8 - aw, rowY, COL_TEXT, false);
            }
            if (fuel.entries.size() > rows) {
                String more = "+ " + (fuel.entries.size() - rows) + " more";
                graphics.drawString(font, more, barX, listY + rows * 11, COL_TEXT_DIM, false);
            }
        } else if (!fuel.linked) {
            graphics.drawString(font, "Right-click a Stats Display panel to link.",
                x + 8, y + 26, COL_TEXT_DIM, false);
        }
    }

    private static void renderTripLine(GuiGraphics graphics, Font font, int cx, int y, double tripMetres) {
        String label;
        if (tripMetres < 1000.0) {
            label = String.format(Locale.ROOT, "TRIP  %4.0f m", tripMetres);
        } else {
            label = String.format(Locale.ROOT, "TRIP  %5.2f km", tripMetres / 1000.0);
        }
        int tw = font.width(label);
        graphics.drawString(font, label, cx - tw / 2, y, COL_TEXT_DIM, false);
    }

    private static int computeFuelBlockHeight(HelmetFuelSnapshot.Snapshot fuel) {
        return 18 + (fuel.linked ? Math.min(72, 14 + fuel.entries.size() * 11) : 14);
    }

    private static void renderStatsPanel(GuiGraphics graphics, Font font, int x, int y, int w, int h,
                                         float currentSpeedMps,
                                         float topSpeedSessionMps,
                                         float topSpeedAllTimeMps,
                                         float avgSpeedMps,
                                         double driveTimeSessionSec,
                                         double driveTimeTotalSec,
                                         float forwardAccelG,
                                         float lateralAccelG,
                                         float topSpeedBump,
                                         long nowNanos) {
        graphics.fill(x, y, x + w, y + h, COL_PANEL_BG);
        drawFrame(graphics, x, y, w, h, COL_FRAME_DIM);

        graphics.fill(x + 1, y + 1, x + w - 1, y + 11, COL_PANEL_BG2);
        graphics.drawString(font, "STATS", x + 6, y + 2, COL_AMBER_DIM, false);
        int pipColor = currentSpeedMps > MOVING_EPS_MPS ? COL_OK : COL_TEXT_DIM;
        int pipX = x + w - 8;
        int pipY = y + 5;
        graphics.fill(pipX, pipY, pipX + 3, pipY + 3, pipColor);

        int rowY = y + 14;
        graphics.drawString(font, "TOP", x + 6, rowY, COL_TEXT_DIM, false);
        String sess = String.format(Locale.ROOT, "%4.1f", topSpeedSessionMps);
        int sessColor = topSpeedBump > 0.01f
            ? HudEasing.blendArgb(COL_AMBER, COL_CYAN, topSpeedBump)
            : COL_AMBER;
        float sessScale = 1.0f + 0.12f * topSpeedBump;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        int sessXAnchor = x + 36;
        pose.translate(sessXAnchor, rowY, 0);
        pose.scale(sessScale, sessScale, 1.0f);
        graphics.drawString(font, sess, 0, 0, sessColor, false);
        pose.popPose();
        graphics.drawString(font, "m/s", x + 36 + (int) (font.width(sess) * sessScale) + 2, rowY, COL_TEXT_DIM, false);
        String allTime = String.format(Locale.ROOT, "\u25b2 %.1f", topSpeedAllTimeMps);
        int allW = font.width(allTime);
        graphics.drawString(font, allTime, x + w - 6 - allW, rowY, COL_CYAN_DIM, false);

        rowY += 12;
        graphics.drawString(font, "AVG", x + 6, rowY, COL_TEXT_DIM, false);
        String avg = String.format(Locale.ROOT, "%.1f m/s", avgSpeedMps);
        graphics.drawString(font, avg, x + 36, rowY, COL_TEXT, false);

        rowY += 12;
        graphics.drawString(font, "DRIVE", x + 6, rowY, COL_TEXT_DIM, false);
        String sessTime = formatDuration(driveTimeSessionSec, false);
        graphics.drawString(font, sessTime, x + 42, rowY, COL_TEXT, false);
        String totalTime = "\u03a3 " + formatDuration(driveTimeTotalSec, true);
        int totW = font.width(totalTime);
        graphics.drawString(font, totalTime, x + w - 6 - totW, rowY, COL_CYAN_DIM, false);

        rowY += 12;
        graphics.drawString(font, "CLOCK", x + 6, rowY, COL_TEXT_DIM, false);
        Minecraft mc = Minecraft.getInstance();
        long mcTime = (mc != null && mc.level != null) ? mc.level.getDayTime() % 24000L : 0L;
        if (mcTime < 0) mcTime += 24000L;
        double mcHourFloat = (mcTime / 1000.0 + 6.0) % 24.0;
        int mcHour = (int) Math.floor(mcHourFloat);
        int mcMin  = (int) Math.floor((mcHourFloat - mcHour) * 60.0);
        String mcStr = String.format(Locale.ROOT, "%02d:%02d", mcHour, mcMin);
        graphics.drawString(font, mcStr, x + 42, rowY, COL_AMBER, false);
        java.time.LocalTime irl = java.time.LocalTime.now();
        String irlStr = String.format(Locale.ROOT, "%02d:%02d", irl.getHour(), irl.getMinute());
        int irlW = font.width(irlStr);
        graphics.drawString(font, irlStr, x + w - 6 - irlW, rowY, COL_CYAN, false);

        rowY += 12;
        graphics.drawString(font, "ACCEL", x + 6, rowY, COL_TEXT_DIM, false);
        String accelStr = String.format(Locale.ROOT, "%+.2fg", forwardAccelG);
        int accelColor = colorForG(Math.abs(forwardAccelG));
        graphics.drawString(font, accelStr, x + 42, rowY, accelColor, false);

        int barCX = x + 100;
        drawCenteredAccelBar(graphics, barCX, rowY + 2, 26, forwardAccelG, 1.0f);

        String latStr = String.format(Locale.ROOT, "LAT %+.2fg", lateralAccelG);
        int latColor = colorForG(Math.abs(lateralAccelG));
        int latW = font.width(latStr);
        graphics.drawString(font, latStr, x + w - 6 - latW, rowY, latColor, false);

        if (nowNanos == Long.MIN_VALUE) return;
    }

    private static void drawCenteredAccelBar(GuiGraphics graphics, int cx, int y, int halfW, float valueG, float maxG) {
        graphics.fill(cx - halfW, y, cx + halfW, y + 1, COL_PANEL_BG2);
        graphics.fill(cx, y - 1, cx + 1, y + 2, COL_FRAME_DIM);
        float clamped = Mth.clamp(valueG / maxG, -1.0f, 1.0f);
        int fillW = Math.round(Math.abs(clamped) * halfW);
        int col = colorForG(Math.abs(valueG));
        if (clamped >= 0) {
            graphics.fill(cx, y, cx + fillW, y + 1, col);
        } else {
            graphics.fill(cx - fillW, y, cx, y + 1, col);
        }
    }

    private static int colorForG(float absG) {
        if (absG < 0.20f) return COL_CYAN_DIM;
        if (absG < 0.50f) return COL_OK;
        if (absG < 0.90f) return COL_AMBER;
        return COL_DANGER;
    }

    private static String formatDuration(double seconds, boolean compact) {
        long s = (long) Math.max(0.0, seconds);
        long days = s / 86400L;
        long hours = (s / 3600L) % 24L;
        long minutes = (s / 60L) % 60L;
        long secs = s % 60L;
        if (compact && days > 0) {
            return String.format(Locale.ROOT, "%dd %02d:%02d", days, hours, minutes);
        }
        if (s >= 3600L) {
            return String.format(Locale.ROOT, "%02d:%02d:%02d", hours + days * 24L, minutes, secs);
        }
        return String.format(Locale.ROOT, "%02d:%02d", minutes, secs);
    }

    private static void renderWarningChip(GuiGraphics graphics, Font font, int cx, int y, String message,
                                          float pulse, int baseColor) {
        int padX = 10;
        int padY = 3;
        int textW = font.width(message);
        int w = textW + padX * 2;
        int h = font.lineHeight + padY * 2;
        int x = cx - w / 2;
        int bgAlpha = Math.round(0x60 + pulse * 0x40);
        int bg = (bgAlpha << 24) | 0x180000;
        graphics.fill(x, y, x + w, y + h, bg);
        int frameColor = pulse > 0.0f ? HudEasing.blendArgb(baseColor, 0xFFFFFFFF, pulse * 0.35f) : baseColor;
        drawFrame(graphics, x, y, w, h, frameColor);
        graphics.drawString(font, message, x + padX, y + padY, frameColor, true);
    }

    private static void renderLinkStatusChip(GuiGraphics graphics, Font font, int x, int y, int w,
                                             boolean hasTelemetry, boolean fuelLinked) {
        int h = 14;
        graphics.fill(x, y, x + w, y + h, COL_PANEL_BG2);
        drawFrame(graphics, x, y, w, h, COL_FRAME_DIM);
        String fcc = hasTelemetry ? "FCC ●" : "FCC ○";
        String fuel = fuelLinked ? "FUEL ●" : "FUEL ○";
        graphics.drawString(font, fcc, x + 6, y + 3, hasTelemetry ? COL_OK : COL_TEXT_DIM, false);
        int fuelW = font.width(fuel);
        graphics.drawString(font, fuel, x + w - 6 - fuelW, y + 3, fuelLinked ? COL_OK : COL_TEXT_DIM, false);
    }

    private static void renderHeadingTape(GuiGraphics graphics, Font font, int centerX, int y, int width, float heading) {
        int half = width / 2;
        int x0 = centerX - half;

        graphics.fill(x0, y, x0 + width, y + 14, COL_PANEL_BG2);
        drawFrame(graphics, x0, y, width, 14, COL_FRAME_DIM);

        float hdg = ((heading % 360.0f) + 360.0f) % 360.0f;
        for (int tick = -90; tick <= 90; tick += 10) {
            float angle = hdg + tick;
            float tx = centerX + (tick / 90.0f) * half;
            int height10 = (tick % 30 == 0) ? 8 : 4;
            int ix = Math.round(tx);
            graphics.fill(ix, y + 1, ix + 1, y + 1 + height10, COL_FRAME);
            if (tick % 30 == 0) {
                String label = cardinalOrAngle(angle);
                graphics.drawString(font, label, ix - font.width(label) / 2, y + 14, COL_TEXT_DIM, false);
            }
        }

        int cx = centerX;
        drawLine(graphics, cx - 4, y - 2, cx, y + 2, COL_AMBER);
        drawLine(graphics, cx + 4, y - 2, cx, y + 2, COL_AMBER);

        String hLabel = String.format(Locale.ROOT, "%03d°", (int) Math.round(hdg));
        graphics.drawString(font, hLabel, cx - font.width(hLabel) / 2, y - 12, COL_AMBER, true);
    }

    private static String cardinalOrAngle(float angle) {
        float h = ((angle % 360.0f) + 360.0f) % 360.0f;
        if (Math.abs(h - 0) < 1.0f || Math.abs(h - 360.0f) < 1.0f) return "N";
        if (Math.abs(h - 90) < 1.0f) return "E";
        if (Math.abs(h - 180) < 1.0f) return "S";
        if (Math.abs(h - 270) < 1.0f) return "W";
        return String.format(Locale.ROOT, "%03d", (int) Math.round(h));
    }

    private static int mean(int[] arr) {
        if (arr == null || arr.length == 0) return 0;
        long sum = 0;
        int count = 0;
        for (int v : arr) { sum += v; count++; }
        return count == 0 ? 0 : (int) Math.round((double) sum / count);
    }

    private static int maxAbs(int[] arr) {
        int m = 0;
        if (arr == null) return 0;
        for (int v : arr) {
            int a = Math.abs(v);
            if (a > m) m = a;
        }
        return m;
    }

    private static float vehicleThrustAverage(com.createstats.flight.network.FccTelemetryPacket telemetry) {
        if (telemetry == null) return 0.0f;
        float[] thrust = telemetry.thrustSigned();
        if (thrust == null || thrust.length == 0) return 0.0f;
        float sum = 0.0f;
        int count = 0;
        for (float v : thrust) {
            if (Float.isFinite(v)) { sum += v; count++; }
        }
        return count == 0 ? 0.0f : sum / count;
    }

    private static java.util.List<HudWarnings.Entry> collectWarnings(HelmetFuelSnapshot.Snapshot fuel,
                                                                     float tractionLossPct, float totalFuel,
                                                                     boolean signalStale,
                                                                     @Nullable FccGuiSnapshot snapshot) {
        java.util.List<HudWarnings.Entry> list = new java.util.ArrayList<>();
        if (signalStale) {
            list.add(new HudWarnings.Entry(HudWarnings.Severity.ALARM, "FCC SIGNAL STALE"));
        }
        // linked but panel unreachable, say so instead of stale fuel

        if (fuel.linked && !fuel.panelPresent) {
            list.add(new HudWarnings.Entry(HudWarnings.Severity.WARNING, "FUEL LINK LOST"));
        }
        if (fuel.linked && fuel.panelPresent) {
            if (!fuel.hasFuel()) {
                list.add(new HudWarnings.Entry(HudWarnings.Severity.WARNING, "NO FUEL SOURCES"));
            } else {
                boolean capacityKnown = fuel.totalCapacityMb > 0.5f;
                float fill = fuel.fillPct();
                if (capacityKnown ? fill <= 0.08f : totalFuel < 50.0f) {
                    list.add(new HudWarnings.Entry(HudWarnings.Severity.ALARM, "FUEL CRITICAL"));
                } else if (capacityKnown && fill <= 0.20f) {
                    list.add(new HudWarnings.Entry(HudWarnings.Severity.CAUTION,
                        "FUEL LOW " + (int) Math.round(fill * 100.0f) + "%"));
                }
            }
        }
        if (tractionLossPct >= 40.0f) {
            list.add(new HudWarnings.Entry(HudWarnings.Severity.WARNING,
                "WHEEL SLIP " + (int) Math.round(tractionLossPct) + "%"));
        }
        if (snapshot != null) {
            String fccWarning = snapshot.lastWarning();
            if (fccWarning != null && !fccWarning.isBlank()) {
                list.add(new HudWarnings.Entry(HudWarnings.Severity.WARNING,
                    fccWarning.length() > 26 ? fccWarning.substring(0, 26) : fccWarning));
            }

            if (!snapshot.fePowered()) {
                list.add(new HudWarnings.Entry(HudWarnings.Severity.ALARM, "NO POWER"));
            } else if (snapshot.feBufferPct() < com.createstats.power.FeConsumer.LOW_POWER_PCT) {
                list.add(new HudWarnings.Entry(HudWarnings.Severity.CAUTION,
                    "LOW POWER " + Math.round(snapshot.feBufferPct()) + "%"));
            }
            if (snapshot.mode() == com.createstats.flight.FlightMode.OFF) {
                list.add(new HudWarnings.Entry(HudWarnings.Severity.INFO, "DISARMED"));
            }
        }
        list.sort((a, b) -> b.severity().ordinal() - a.severity().ordinal());
        return list;
    }

    private static void renderWarningStack(GuiGraphics graphics, Font font, int cx, int y,
                                           java.util.List<HudWarnings.Entry> entries, float pulse) {
        int rowY = y;
        int shown = Math.min(3, entries.size());
        for (int i = 0; i < shown; i++) {
            HudWarnings.Entry e = entries.get(i);
            boolean pulsing = e.severity().ordinal() >= HudWarnings.Severity.WARNING.ordinal();
            renderWarningChip(graphics, font, cx, rowY, "\u26a0 " + e.text(),
                pulsing ? pulse : 0.0f, e.severity().color);
            rowY += font.lineHeight + 9;
        }
        if (entries.size() > shown) {
            String more = "+" + (entries.size() - shown) + " more";
            graphics.drawString(font, more, cx - font.width(more) / 2, rowY, COL_TEXT_DIM, true);
        }
    }

    private static void scaleAt(GuiGraphics graphics, float anchorX, float anchorY, float scale, Runnable draw) {
        if (Math.abs(scale - 1.0f) < 0.001f) {
            draw.run();
            return;
        }
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(anchorX, anchorY, 0);
        pose.scale(scale, scale, 1.0f);
        pose.translate(-anchorX, -anchorY, 0);
        draw.run();
        pose.popPose();
    }

    private static void drawFrame(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        drawLine(graphics, x, y, x + Math.min(w, 18), y, color);
        drawLine(graphics, x, y, x, y + h, color);
        drawLine(graphics, x + w - Math.min(w, 18), y, x + w, y, color);
        drawLine(graphics, x + w, y, x + w, y + h, color);
        drawLine(graphics, x, y + h, x + Math.min(w, 18), y + h, color);
        drawLine(graphics, x + w - Math.min(w, 18), y + h, x + w, y + h, color);
    }

    private static void drawRect(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;
        int x = x1, y = y1;
        int safety = 0;
        while (true) {
            graphics.fill(x, y, x + 1, y + 1, color);
            if (x == x2 && y == y2) return;
            if (safety++ > 4096) return;
            int e2 = err * 2;
            if (e2 > -dy) { err -= dy; x += sx; }
            if (e2 <  dx) { err += dx; y += sy; }
        }
    }

    private static void drawDottedCircle(GuiGraphics graphics, int cx, int cy, int r, int color, int stepDeg) {
        for (int a = 0; a < 360; a += stepDeg) {
            float rad = (float) Math.toRadians(a - 90);
            int x = cx + Math.round(Mth.cos(rad) * r);
            int y = cy + Math.round(Mth.sin(rad) * r);
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static void drawFilledDiamond(GuiGraphics graphics, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int w = r - Math.abs(dy);
            graphics.fill(cx - w, cy + dy, cx + w + 1, cy + dy + 1, color);
        }
    }
}
