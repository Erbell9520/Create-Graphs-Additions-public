package com.createstats.flight.client;

import com.createstats.flight.network.FccGuiSnapshot;
import com.createstats.flight.network.FccGuiControlEntry;
import com.createstats.flight.network.FccTelemetryPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import com.createstats.flight.SensorRequirement;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class FccHudOverlay {
    private static final int HUD_MARGIN = 8;
    private static final int HUD_W = 214;
    private static final int HUD_H = 150;

    private FccHudOverlay() {
    }

    public static void render(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.screen != null) {
            return;
        }
        FccTelemetryPacket telemetry = FccClientState.pinnedTelemetry();
        if (telemetry == null) {
            return;
        }
        BlockPos controllerPos = BlockPos.of(telemetry.controllerPosLong());
        if (!FccClientState.isHudPinned(controllerPos)) {
            return;
        }
        FccGuiSnapshot snapshot = FccClientState.snapshotForPinned(telemetry.containerId());

        int x = HUD_MARGIN;
        int y = minecraft.getWindow().getGuiScaledHeight() - HUD_H - HUD_MARGIN;
        graphics.fill(x, y, x + HUD_W, y + HUD_H, 0xE6101620);
        graphics.fill(x + 1, y + 1, x + HUD_W - 1, y + 18, 0xF0202A36);
        graphics.drawString(minecraft.font, "FCC PIN HUD", x + 8, y + 6, 0xFF6EE7A8, false);
        graphics.drawString(minecraft.font, FccHud.formatPos(controllerPos.getX(), controllerPos.getY(), controllerPos.getZ()), x + HUD_W - 94, y + 6, 0xFF8CA1B4, false);

        double speed = Math.sqrt(telemetry.velX() * telemetry.velX() + telemetry.velY() * telemetry.velY() + telemetry.velZ() * telemetry.velZ());
        FccHud.Theme theme = FccHud.Theme.NAVY;
        if (snapshot != null) {
            drawStatusChip(graphics, minecraft.font, x + 8, y + 22, 46, "MODE",
                snapshot.mode() == com.createstats.flight.FlightMode.OFF ? "OFF" : "STBY", theme);
            drawStatusChip(graphics, minecraft.font, x + 58, y + 22, 46, "AUTO", isControlActive(snapshot, "Auto Drive Switch") ? "ON" : "OFF", theme);
            drawStatusChip(graphics, minecraft.font, x + 108, y + 22, 44, "INV", isControlActive(snapshot, "Steering Invert Switch") ? "ON" : "OFF", theme);
            drawStatusChip(graphics, minecraft.font, x + 156, y + 22, 50, "SENS", snapshot.availableSensors().size() + "/" + SensorRequirement.values().length, theme);
        } else {
            drawStatusChip(graphics, minecraft.font, x + 8, y + 22, 46, "MODE", "N/A", theme);
            drawStatusChip(graphics, minecraft.font, x + 58, y + 22, 46, "AUTO", "N/A", theme);
            drawStatusChip(graphics, minecraft.font, x + 108, y + 22, 44, "INV", "N/A", theme);
            drawStatusChip(graphics, minecraft.font, x + 156, y + 22, 50, "SENS", "N/A", theme);
        }

        FccHud.drawCompass(graphics, minecraft.font, x + 8, y + 42, HUD_W - 16, 16, telemetry.headingDeg(), theme);
        FccHud.drawTelemetryStrip(graphics, minecraft.font, x + 8, y + 60, telemetry.altitude(), telemetry.climbRate(), telemetry.headingDeg(), speed, theme);

        double tripDistance = FccClientState.distanceTravelledMeters(telemetry.containerId());
        String targetDistanceText = Float.isFinite(telemetry.autoTargetDistance())
                ? String.format(java.util.Locale.ROOT, "%.1f m", telemetry.autoTargetDistance())
                : "N/A";
        graphics.drawString(minecraft.font, "TRIP", x + 118, y + 60, theme.textDim(), false);
        graphics.drawString(minecraft.font, String.format(java.util.Locale.ROOT, "%.1f m", tripDistance), x + 146, y + 60, theme.text(), false);
        graphics.drawString(minecraft.font, "TARGET", x + 118, y + 71, theme.textDim(), false);
        graphics.drawString(minecraft.font, targetDistanceText, x + 156, y + 71, theme.text(), false);

        boolean gimbal = snapshot != null && snapshot.availableSensors().contains(SensorRequirement.GIMBAL);
        boolean alt = snapshot != null && snapshot.availableSensors().contains(SensorRequirement.ALTITUDE);
        boolean vel = snapshot != null && snapshot.availableSensors().contains(SensorRequirement.VELOCITY);
        boolean nav = snapshot != null && snapshot.availableSensors().contains(SensorRequirement.NAV_TABLE);
        FccHud.drawSensorLeds(graphics, minecraft.font, x + 8, y + 108, gimbal, alt, vel, nav, theme);

        int rpmRows = Math.min(3, Math.max(1, telemetry.propBoundCount()));
        FccHud.drawRpmBars(graphics, minecraft.font, x + 8, y + 120, HUD_W - 16, 24,
                telemetry.commandedRpm(), telemetry.actualRpm(), rpmRows, theme);
    }

    private static void drawStatusChip(GuiGraphics graphics, net.minecraft.client.gui.Font font, int x, int y, int w,
                                       String label, String value, FccHud.Theme theme) {
        graphics.fill(x, y, x + w, y + 14, 0xFF0E2A4D);
        graphics.fill(x, y, x + w, y + 1, theme.frame());
        graphics.fill(x, y + 13, x + w, y + 14, theme.frame());
        graphics.fill(x, y, x + 1, y + 14, theme.frame());
        graphics.fill(x + w - 1, y, x + w, y + 14, theme.frame());
        graphics.drawString(font, label, x + 4, y + 2, theme.textDim(), false);
        graphics.drawString(font, value, x + 4, y + 8, theme.text(), false);
    }

    private static boolean isControlActive(FccGuiSnapshot snapshot, String label) {
        for (FccGuiControlEntry entry : snapshot.controlEntries()) {
            if (entry != null && entry.label() != null && entry.label().equalsIgnoreCase(label)) {
                return entry.value() >= entry.inputMin() + (entry.inputMax() - entry.inputMin()) * 0.5;
            }
        }
        return false;
    }
}
