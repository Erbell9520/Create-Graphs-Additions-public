package com.createstats.client;

import com.createstats.blockentity.SourceConfig;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.data.MetricType;
import com.createstats.display.DisplayTheme;
import com.createstats.display.StressometerHudState;
import com.createstats.renderer.BrassTrackerRenderer;
import com.createstats.renderer.FluidPalette;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class StressometerHudWidgetRenderer {
    private static final int SAFE_GREEN = 0xFF33E07A;
    private static final int ALERT_AMBER = 0xFFFFB84A;
    private static final int DANGER_RED = 0xFFFF4D4D;
    private static final int PANEL_BG = 0xCC221A10;
    private static final int INNER_BG = 0x521A120A;
    private static final int BORDER = 0xB09B8658;
    private static final int DIM_TEXT = 0xFFC4B793;

    private static float CURRENT_RENDER_SCALE = 1.0f;

    private StressometerHudWidgetRenderer() {
    }

    public static boolean renderLive(GuiGraphics g, StatsDisplayPanelBE panel, StressometerHudState state, int x, int y, int w, int h, long tick) {
        final StressometerHudReadout readout = StressometerHudDataResolver.resolve(panel, state, false);
        if (!readout.hasLiveData()) {
            return false;
        }
        render(g, Minecraft.getInstance().font, panel, panel.getSettings().getDisplayTheme(), state, readout, x, y, w, h, tick);
        return true;
    }

    public static boolean renderPreview(GuiGraphics g, StatsDisplayPanelBE panel, StressometerHudState state, int x, int y, int w, int h, long tick) {
        final DisplayTheme theme = panel != null ? panel.getSettings().getDisplayTheme() : DisplayTheme.CREATE;
        final StressometerHudReadout readout = StressometerHudDataResolver.resolve(panel, state, true);
        render(g, Minecraft.getInstance().font, panel, theme, state, readout, x, y, w, h, tick);
        return readout.hasLiveData();
    }

    public static boolean renderLayoutLive(GuiGraphics g, StatsDisplayPanelBE panel, StressometerHudState state, int originX, int originY, int screenW, int screenH, long tick) {
        return renderLayoutLive(g, panel, state, originX, originY, screenW, screenH, tick, 0f);
    }

    public static boolean renderLayoutLive(GuiGraphics g, StatsDisplayPanelBE panel, StressometerHudState state, int originX, int originY, int screenW, int screenH, long tick, float partialTick) {
        final StressometerHudReadout readout = StressometerHudDataResolver.resolve(panel, state, false);
        final DisplayTheme theme = panel != null ? panel.getSettings().getDisplayTheme() : DisplayTheme.CREATE;
        renderLayout(g, Minecraft.getInstance().font, panel, theme, state, readout, false, originX, originY, screenW, screenH, tick, partialTick);
        return true;
    }

    public static boolean renderLayoutPreview(GuiGraphics g, StatsDisplayPanelBE panel, StressometerHudState state, int originX, int originY, int screenW, int screenH, long tick) {
        return renderLayoutPreview(g, panel, state, originX, originY, screenW, screenH, tick, 0f);
    }

    public static boolean renderLayoutPreview(GuiGraphics g, StatsDisplayPanelBE panel, StressometerHudState state, int originX, int originY, int screenW, int screenH, long tick, float partialTick) {
        final DisplayTheme theme = panel != null ? panel.getSettings().getDisplayTheme() : DisplayTheme.CREATE;
        final StressometerHudReadout readout = StressometerHudDataResolver.resolve(panel, state, true);
        renderLayout(g, Minecraft.getInstance().font, panel, theme, state, readout, true, originX, originY, screenW, screenH, tick, partialTick);
        return readout.hasLiveData();
    }

    private static void renderLayout(GuiGraphics g, Font font, StatsDisplayPanelBE panel, DisplayTheme theme, StressometerHudState state, StressometerHudReadout readout, boolean preview, int originX, int originY, int screenW, int screenH, long tick, float partialTick) {
        if (state == null || state.getWidgets().isEmpty()) {
            return;
        }
        for (StressometerHudState.PartWidget widget : state.getWidgets()) {
            final StressometerHudState.Bounds.ResolvedBounds bounds = widget.resolveBounds(screenW, screenH, state);
            if (widget.isStressometerPart() && (widget.getSourceType().isEmpty() || "Stressometer".equals(widget.getSourceType()))) {
                renderPart(g, font, panel, theme, widget, readout, originX + bounds.x(), originY + bounds.y(), bounds.width(), bounds.height(), tick);
            } else {
                renderSourcePart(g, font, panel, theme, state, widget, preview, originX + bounds.x(), originY + bounds.y(), bounds.width(), bounds.height(), tick);
            }
        }
    }

    public static void renderPart(GuiGraphics g, Font font, StatsDisplayPanelBE panel, DisplayTheme theme, StressometerHudState.PartWidget widget, StressometerHudReadout readout, int x, int y, int w, int h, long tick) {
        final int accent = statusColor(readout);
        if (widget.hasBackground()) {
            widgetPanel(g, x, y, w, h, theme, accent);
        }
        final int pad = Math.max(4, Math.min(8, Math.min(w, h) / 6));
        final boolean shadow = widget.hasTextShadow();
        switch (widget.getPart()) {
            case HEADER -> {
                final String title = readout.status() == StressometerHudReadout.Status.SAMPLE ? "STRESS HUD - SAMPLE" : "STRESS HUD";
                drawCentered(g, font, truncate(font, title, w - pad * 2), x + w / 2, y + Math.max(3, (h - font.lineHeight) / 2), theme.text, shadow);
            }
            case SOURCE_LABEL -> drawCentered(g, font, truncate(font, readout.sourceLabel().isEmpty() ? statusText(readout) : readout.sourceLabel(), w - pad * 2), x + w / 2, y + Math.max(3, (h - font.lineHeight) / 2), theme.dim, shadow);
            case LOAD_GAUGE -> {
                if ("reactor".equals(widget.getRenderVariant())) {
                    renderReactorLoadGauge(g, font, theme, widget, readout, x, y, w, h, tick, shadow);
                    break;
                }
                final int barH = Math.max(12, Math.min(28, h - pad * 2 - 12));
                final float smoothLoad = com.createstats.client.hud.HudInterpolation.smooth(
                        widget.getId() + ":stress_pct", readout.stressPercent());
                drawCentered(g, font, String.format(Locale.ROOT, "%.1f%% LOAD", smoothLoad), x + w / 2, y + pad, accent, shadow);
                final BlockPos pos = panel != null ? panel.getBlockPos() : BlockPos.ZERO;
                BrassTrackerRenderer.drawHorizontalGui(g, x + pad, y + h - pad - barH, Math.max(8, w - pad * 2), barH, pos, readout.animationSlot(), clamp01(smoothLoad / 100.0f), 0.0f, 0.6f, SAFE_GREEN, 0.6f, ALERT_AMBER, 0.85f, DANGER_RED, accent, readout.overstressed(), tick);
            }
            case USED_CAPACITY -> renderValueWidget(g, font, "STRESS", buildStressLine(null, readout), x, y, w, h, theme, accent, shadow);
            case STRESS_PERCENT -> {
                final float smoothPct = com.createstats.client.hud.HudInterpolation.smooth(
                        widget.getId() + ":stress_pct", readout.stressPercent());
                renderValueWidget(g, font, "LOAD", String.format(Locale.ROOT, "%.1f%%", smoothPct), x, y, w, h, theme, accent, shadow);
            }
            case REMAINING_STRESS -> renderValueWidget(g, font, "REMAINING", formatCompact(readout.remainingStress()) + " SU", x, y, w, h, theme, SAFE_GREEN, shadow);
            case RPM -> renderValueWidget(g, font, "RPM", formatCompact(readout.rpm()), x, y, w, h, theme, theme.accent, shadow);
            case STATUS -> drawCentered(g, font, truncate(font, statusLabel(readout), w - pad * 2), x + w / 2, y + Math.max(4, (h - font.lineHeight) / 2), accent, shadow);
            case WARNING -> drawCentered(g, font, truncate(font, readout.overstressed() ? "OVERSTRESSED" : statusLabel(readout), w - pad * 2), x + w / 2, y + Math.max(4, (h - font.lineHeight) / 2), readout.overstressed() ? DANGER_RED : accent, shadow);
        }
    }

    private static void renderSourcePart(GuiGraphics g, Font font, StatsDisplayPanelBE panel, DisplayTheme theme, StressometerHudState state, StressometerHudState.PartWidget widget, boolean preview, int x, int y, int w, int h, long tick) {
        final HudSourceWidgetReadout readout = StressometerHudDataResolver.resolveSourceWidget(panel, widget, preview);
        final SourceConfig source = StressometerHudDataResolver.resolveWidgetSource(panel, widget);
        final List<StatsDisplayPanelBE.DataChannel> channels = source != null ? StressometerHudDataResolver.channelsForSource(panel, source.getSourceId()) : List.of();
        final int accent = readout.color();
        if (widget.hasBackground()) {
            widgetPanel(g, x, y, w, h, theme, accent);
        }

        if (renderVariantSourcePart(g, font, theme, widget, source, channels, accent, x, y, w, h, tick)) {
            return;
        }

        final float globalScale = state != null ? state.getGlobalTextScale() : 1.0f;
        final int globalPad = state != null ? state.getGlobalPaddingOffset() : 0;
        final float effectiveScale = widget.getEffectiveTextScale(globalScale);
        final int padExtra = widget.getEffectivePaddingOffset(globalPad);
        final boolean isHorizon = widget.getPart() == StressometerHudState.Part.AERO_HORIZON;

        final int cx = x + padExtra;
        final int cy = y + padExtra;
        final int cw = Math.max(20, w - 2 * padExtra);
        final int ch = Math.max(20, h - 2 * padExtra);

        CURRENT_RENDER_SCALE = (!isHorizon && Math.abs(effectiveScale - 1.0f) > 0.01f) ? effectiveScale : 1.0f;

        if (renderStructuredSourcePart(g, font, theme, widget, source, channels, cx, cy, cw, ch)) {
            CURRENT_RENDER_SCALE = 1.0f;
            return;
        }
        CURRENT_RENDER_SCALE = 1.0f;

        final boolean shadow = widget.hasTextShadow();
        final int pad = Math.max(3, Math.min(6, Math.min(w, h) / 7));
        if (readout.status() != HudSourceWidgetReadout.Status.OK && readout.status() != HudSourceWidgetReadout.Status.SAMPLE) {
            if (h >= 44) {
                drawCentered(g, font, truncate(font, readout.title(), w - pad * 2), x + w / 2, y + pad, theme.dim, shadow);
                g.fill(x + 6, y + pad + font.lineHeight + 1, x + w - 6, y + pad + font.lineHeight + 2, dim(theme.border, 0.55f));
                drawCentered(g, font, truncate(font, readout.value(), w - pad * 2), x + w / 2, Math.max(y + pad + 11, y + h / 2 - 2), accent, shadow);
                if (!readout.detail().isEmpty() && h >= 58) {
                    final int ruleY = y + h - font.lineHeight - pad - 2;
                    g.fill(x + 6, ruleY, x + w - 6, ruleY + 1, dim(theme.border, 0.45f));
                    drawCentered(g, font, truncate(font, readout.detail(), w - pad * 2), x + w / 2, y + h - font.lineHeight - pad, theme.text, shadow);
                }
            } else {
                drawCentered(g, font, truncate(font, readout.value(), w - pad * 2), x + w / 2, y + Math.max(4, (h - font.lineHeight) / 2), accent, shadow);
            }
            return;
        }
        if (h >= 44) {
            drawCentered(g, font, truncate(font, readout.title(), w - pad * 2), x + w / 2, y + pad, theme.dim, shadow);
            g.fill(x + 6, y + pad + font.lineHeight + 1, x + w - 6, y + pad + font.lineHeight + 2, dim(theme.border, 0.55f));
            int valueY = Math.max(y + pad + 11, y + h / 2 - 2);
            if (!readout.label().isEmpty() && h >= 60) {
                drawCentered(g, font, truncate(font, readout.label(), w - pad * 2), x + w / 2, y + pad + 12, theme.text, shadow);
                valueY = Math.max(y + pad + 24, y + h / 2 - 2);
            }
            drawCentered(g, font, truncate(font, readout.value(), w - pad * 2), x + w / 2, valueY, accent, shadow);
            if (!readout.detail().isEmpty() && h >= 58) {
                final int ruleY = y + h - font.lineHeight - pad - 2;
                g.fill(x + 6, ruleY, x + w - 6, ruleY + 1, dim(theme.border, 0.45f));
                drawCentered(g, font, truncate(font, readout.detail(), w - pad * 2), x + w / 2, y + h - font.lineHeight - pad, theme.text, shadow);
            }
            return;
        }
        drawCentered(g, font, truncate(font, readout.value(), w - pad * 2), x + w / 2, y + Math.max(4, (h - font.lineHeight) / 2), accent, shadow);
    }

    private static boolean renderStructuredSourcePart(GuiGraphics g, Font font, DisplayTheme theme, StressometerHudState.PartWidget widget, SourceConfig source, List<StatsDisplayPanelBE.DataChannel> channels, int x, int y, int w, int h) {
        if (source == null || channels.isEmpty()) {
            return false;
        }
        return switch (source.getSourceType()) {
            case "Gimbal Sensor" -> renderGimbalStructuredWidget(g, font, theme, widget, channels, x, y, w, h);
            case "Contraption Info" -> renderContraptionStructuredWidget(g, font, theme, widget, channels, x, y, w, h);
            case "Aero Flight Deck" -> renderAeroFlightDeckStructuredWidget(g, font, theme, widget, channels, x, y, w, h);
            case "Aeroline Director" -> renderAerolineStructuredWidget(g, font, theme, widget, channels, x, y, w, h);
            case "Content Observer", "Smart Observer" -> renderObserverStructuredWidget(g, font, theme, widget, channels, x, y, w, h);
            case "Fluid Tank", "Steam Boiler" -> renderFluidTankStructuredWidget(g, font, theme, widget, channels, x, y, w, h);
            case "Production Detector", "Inventory Display" -> renderProductionStructuredWidget(g, font, theme, widget, channels, x, y, w, h);
            default -> false;
        };
    }

    private static boolean renderGimbalStructuredWidget(GuiGraphics g, Font font, DisplayTheme theme, StressometerHudState.PartWidget widget, List<StatsDisplayPanelBE.DataChannel> channels, int x, int y, int w, int h) {
        switch (widget.getPart()) {
            case GIMBAL_ANGLES -> {
                final float xAngle = StressometerHudDataResolver.latestValue(StressometerHudDataResolver.findWidgetChannel(channels, "x_angle"), 0.0f);
                final float zAngle = StressometerHudDataResolver.latestValue(StressometerHudDataResolver.findWidgetChannel(channels, "z_angle"), 0.0f);
                renderTripleCards(g, font, theme, widget, x, y, w, h, widgetTitle(widget), "X", angleValue(xAngle), "Z", angleValue(zAngle), "TILT", angleValue(Math.max(Math.abs(xAngle), Math.abs(zAngle))), theme.accent, theme.accent, ALERT_AMBER);
                return true;
            }
            case GIMBAL_ATTITUDE -> {
                final float pitch = StressometerHudDataResolver.latestValue(StressometerHudDataResolver.findWidgetChannel(channels, "attitude_pitch"), 0.0f);
                final float bank = StressometerHudDataResolver.latestValue(StressometerHudDataResolver.findWidgetChannel(channels, "attitude_bank"), 0.0f);
                final float yaw = StressometerHudDataResolver.latestValue(StressometerHudDataResolver.findWidgetChannel(channels, "attitude_yaw"), 0.0f);
                renderTripleCards(g, font, theme, widget, x, y, w, h, widgetTitle(widget), "PITCH", angleValue(pitch), "BANK", angleValue(bank), "YAW", angleValue(yaw), theme.accent, ALERT_AMBER, SAFE_GREEN);
                return true;
            }
            case GIMBAL_POWER -> {
                renderSixPackCards(g, font, theme, widget, x, y, w, h, widgetTitle(widget),
                        "UP", powerValue(channels, "power_up"),
                        "NORTH", powerValue(channels, "power_north"),
                        "EAST", powerValue(channels, "power_east"),
                        "DOWN", powerValue(channels, "power_down"),
                        "SOUTH", powerValue(channels, "power_south"),
                        "WEST", powerValue(channels, "power_west"));
                return true;
            }
            case AERO_HORIZON -> {
                renderArtificialHorizon(g, font, theme, widget, channels, x, y, w, h);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static boolean renderContraptionStructuredWidget(GuiGraphics g, Font font, DisplayTheme theme, StressometerHudState.PartWidget widget, List<StatsDisplayPanelBE.DataChannel> channels, int x, int y, int w, int h) {
        switch (widget.getPart()) {
            case CONTRAPTION_SOLVER -> {
                renderTripleCards(g, font, theme, widget, x, y, w, h, widgetTitle(widget),
                        "STATE", stateText(channels),
                        "AXIS", shortText(textValue(channels, "axis_mode", "AXIS"), 6),
                        "MASS", aeroValue(channels, "total_mass"),
                        stateColor(channels, theme), theme.accent, SAFE_GREEN);
                return true;
            }
            case CONTRAPTION_FORCES -> {
                renderQuadCards(g, font, theme, widget, x, y, w, h, widgetTitle(widget),
                        "THRUST", forceValue(channels, "forward_thrust"), SAFE_GREEN,
                        "BRAKE", forceValue(channels, "reverse_force"), ALERT_AMBER,
                        "WEIGHT", forceValue(channels, "gravity"), DANGER_RED,
                        "SUPPORT", forceValue(channels, "support"), SAFE_GREEN);
                return true;
            }
            case CONTRAPTION_SUPPORT -> {
                renderQuadCards(g, font, theme, widget, x, y, w, h, widgetTitle(widget),
                        "LEVIT", forceValue(channels, "levitation_support"), 0xFF41C0FF,
                        "BALLOON", forceValue(channels, "balloon_support"), theme.accent,
                        "AERO", forceValue(channels, "aero_support"), 0xFF00C87A,
                        "PROP", forceValue(channels, "propulsion_support"), SAFE_GREEN);
                return true;
            }
            case CONTRAPTION_MARGINS -> {
                renderDualCards(g, font, theme, widget, x, y, w, h, widgetTitle(widget),
                        "VERT", signedForceValue(channels, "vertical_margin"), theme.accent,
                        "FWD", signedForceValue(channels, "forward_margin"), SAFE_GREEN);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static boolean renderAeroFlightDeckStructuredWidget(GuiGraphics g, Font font, DisplayTheme theme, StressometerHudState.PartWidget widget, List<StatsDisplayPanelBE.DataChannel> channels, int x, int y, int w, int h) {
        switch (widget.getPart()) {
            case AERO_HEALTH_STRIP -> {
                renderStatusStrip(g, font, theme, widget, x, y, w, h, widgetTitle(widget),
                        new String[]{"ENG", "VEL", "TRM", "ALT", "NAV", "OBS", "SUP"},
                        new boolean[]{
                                hasAnyChannel(channels, "burn_pct", "generated_speed"),
                                hasAnyChannel(channels, "adjusted_velocity", "horizontal_velocity"),
                                hasAnyChannel(channels, "attitude_pitch", "attitude_bank", "attitude_yaw"),
                                hasAnyChannel(channels, "world_height", "air_pressure"),
                                hasAnyChannel(channels, "distance_to_target", "relative_angle"),
                                hasAnyChannel(channels, "ray_distance", "closest_hit_distance", "hit_present"),
                                hasAnyChannel(channels, "vertical_margin", "support", "balloon_lift")
                        },
                        new int[]{SAFE_GREEN, theme.accent, ALERT_AMBER, theme.accent, SAFE_GREEN, ALERT_AMBER, SAFE_GREEN});
                return true;
            }
            case AERO_DRIVE, VELOCITY_AXIS -> {
                final StatsDisplayPanelBE.DataChannel velCh = StressometerHudDataResolver.findWidgetChannel(channels, "adjusted_velocity", "horizontal_velocity");
                final String speedStr = velCh == null ? "--" : formatCompact(StressometerHudDataResolver.latestValue(velCh, 0.0f));
                final String accelStr;
                final int accelColor;
                if (velCh != null && velCh.getStore().getSampleCount() >= 2) {
                    final float accel = velCh.getStore().getRateOfChange();
                    accelStr = (accel > 0.5f ? "+" : "") + formatCompact(accel);
                    accelColor = accel > 0.5f ? SAFE_GREEN : accel < -0.5f ? DANGER_RED : DIM_TEXT;
                } else {
                    accelStr = "--";
                    accelColor = DIM_TEXT;
                }
                renderDualCards(g, font, theme, widget, x, y, w, h, widgetTitle(widget),
                        "SPEED", speedStr, theme.accent,
                        "ACCEL", accelStr, accelColor);
                return true;
            }
            case AERO_ORIENTATION, GIMBAL_ATTITUDE -> {
                final float pitch = StressometerHudDataResolver.latestValue(StressometerHudDataResolver.findWidgetChannel(channels, "attitude_pitch"), 0.0f);
                final float bank = StressometerHudDataResolver.latestValue(StressometerHudDataResolver.findWidgetChannel(channels, "attitude_bank"), 0.0f);
                final float yaw = StressometerHudDataResolver.latestValue(StressometerHudDataResolver.findWidgetChannel(channels, "attitude_yaw"), 0.0f);
                renderTripleCards(g, font, theme, widget, x, y, w, h, widgetTitle(widget), "PITCH", angleValue(pitch), "BANK", angleValue(bank), "YAW", angleValue(yaw), theme.accent, ALERT_AMBER, SAFE_GREEN);
                return true;
            }
            case AERO_ALTITUDE, ALTITUDE_HEIGHT -> {
                renderDualCards(g, font, theme, widget, x, y, w, h, widgetTitle(widget),
                        "ALT", aeroValue(channels, "world_height"), theme.accent,
                        "AIR", percentValue(channels, "air_pressure"), SAFE_GREEN);
                return true;
            }
            case AERO_RANGE -> {
                renderDualCards(g, font, theme, widget, x, y, w, h, widgetTitle(widget),
                        "DIST", aeroValue(channels, "distance_to_target"), theme.accent,
                        "DELTA", signedValue(channels, "distance_delta"), SAFE_GREEN);
                return true;
            }
            case AERO_VERTICAL_MARGIN, CONTRAPTION_MARGINS -> {
                renderDualCards(g, font, theme, widget, x, y, w, h, widgetTitle(widget),
                        "VERT", signedForceValue(channels, "vertical_margin"), theme.accent,
                        "SUP", forceValue(channels, "support"), SAFE_GREEN);
                return true;
            }
            case AERO_FORWARD_MARGIN -> {
                renderDualCards(g, font, theme, widget, x, y, w, h, widgetTitle(widget),
                        "FWD", signedForceValue(channels, "forward_margin"), SAFE_GREEN,
                        "THRUST", forceValue(channels, "forward_thrust"), theme.accent);
                return true;
            }
            case AERO_BRIDGE_ORDERS -> {
                renderDualCards(g, font, theme, widget, x, y, w, h, widgetTitle(widget),
                        "HELM", angleValue(StressometerHudDataResolver.latestValue(StressometerHudDataResolver.findWidgetChannel(channels, "target_angle"), 0.0f)), ALERT_AMBER,
                        "NAV", aeroValue(channels, "distance_to_target"), SAFE_GREEN);
                return true;
            }
            case AERO_ENGINE_STRIP -> {
                renderTripleCards(g, font, theme, widget, x, y, w, h, widgetTitle(widget),
                        "BURN", percentValue(channels, "burn_pct"),
                        "RPM", aeroValue(channels, "generated_speed"),
                        "HEAT", boolValue(channels, "superheated") ? "SUPER" : "NORMAL",
                        ALERT_AMBER, theme.accent, boolValue(channels, "superheated") ? DANGER_RED : SAFE_GREEN);
                return true;
            }
            case AERO_SHIP_STATUS, STATUS -> {
                renderTripleCards(g, font, theme, widget, x, y, w, h, widgetTitle(widget),
                        "STATE", stateText(channels),
                        "MASS", aeroValue(channels, "total_mass"),
                        "SUP", forceValue(channels, "support"),
                        stateColor(channels, theme), theme.accent, SAFE_GREEN);
                return true;
            }
            case AERO_HORIZON -> {
                renderArtificialHorizon(g, font, theme, widget, channels, x, y, w, h);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static boolean renderAerolineStructuredWidget(GuiGraphics g, Font font, DisplayTheme theme, StressometerHudState.PartWidget widget, List<StatsDisplayPanelBE.DataChannel> channels, int x, int y, int w, int h) {
        switch (widget.getPart()) {
            case AEROLINE_MODE_BADGE, STATUS -> {
                renderValueWidget(g, font, widgetTitle(widget), stateText(channels), x, y, w, h, theme, stateColor(channels, theme), widget.hasTextShadow());
                return true;
            }
            case AEROLINE_STATUS_STRIP, VELOCITY_AXIS -> {
                renderStatusStrip(g, font, theme, widget, x, y, w, h, widgetTitle(widget),
                        new String[]{"ATT", "NAV", "DOCK", "HELM", "RNG", "LIFT"},
                        new boolean[]{
                                hasAnyChannel(channels, "attitude_pitch", "attitude_bank"),
                                hasAnyChannel(channels, "distance_to_target", "relative_angle"),
                                hasAnyChannel(channels, "connector_state", "locked"),
                                hasAnyChannel(channels, "target_angle", "logical_speed"),
                                hasAnyChannel(channels, "ray_distance", "closest_hit_distance", "distance_to_target"),
                                hasAnyChannel(channels, "vertical_margin", "air_pressure", "balloon_lift")
                        },
                        new int[]{theme.accent, SAFE_GREEN, ALERT_AMBER, SAFE_GREEN, ALERT_AMBER, SAFE_GREEN});
                return true;
            }
            case AEROLINE_DIRECTOR_WINDOW, GIMBAL_ATTITUDE -> {
                renderQuadCards(g, font, theme, widget, x, y, w, h, widgetTitle(widget),
                        "PITCH", angleValue(StressometerHudDataResolver.latestValue(StressometerHudDataResolver.findWidgetChannel(channels, "attitude_pitch"), 0.0f)), theme.accent,
                        "BANK", angleValue(StressometerHudDataResolver.latestValue(StressometerHudDataResolver.findWidgetChannel(channels, "attitude_bank"), 0.0f)), ALERT_AMBER,
                        "PLAN", angleValue(StressometerHudDataResolver.latestValue(StressometerHudDataResolver.findWidgetChannel(channels, "relative_angle"), 0.0f)), SAFE_GREEN,
                        "RANGE", aeroValue(channels, "distance_to_target"), theme.text);
                return true;
            }
            case AEROLINE_TARGET_SCOPE -> {
                renderDualCards(g, font, theme, widget, x, y, w, h, widgetTitle(widget),
                        "DIST", aeroValue(channels, "distance_to_target"), theme.accent,
                        "ANGLE", angleValue(StressometerHudDataResolver.latestValue(StressometerHudDataResolver.findWidgetChannel(channels, "relative_angle"), 0.0f)), SAFE_GREEN);
                return true;
            }
            case AEROLINE_VELOCITY_SCOPE -> {
                renderDualCards(g, font, theme, widget, x, y, w, h, widgetTitle(widget),
                        "DRIVE", aeroValue(channels, "adjusted_velocity"), theme.accent,
                        "HELM", aeroValue(channels, "generated_speed"), SAFE_GREEN);
                return true;
            }
            case AEROLINE_ALTITUDE_SCOPE, ALTITUDE_HEIGHT -> {
                renderDualCards(g, font, theme, widget, x, y, w, h, widgetTitle(widget),
                        "ALT", aeroValue(channels, "world_height"), theme.accent,
                        "CLIMB", signedForceValue(channels, "vertical_margin"), SAFE_GREEN);
                return true;
            }
            case AEROLINE_FOOTER, CONTRAPTION_MARGINS -> {
                renderTripleCards(g, font, theme, widget, x, y, w, h, widgetTitle(widget),
                        "MODE", stateText(channels),
                        "TURN", angleValue(StressometerHudDataResolver.latestValue(StressometerHudDataResolver.findWidgetChannel(channels, "relative_angle"), 0.0f)),
                        "CLIMB", signedForceValue(channels, "vertical_margin"),
                        stateColor(channels, theme), ALERT_AMBER, SAFE_GREEN);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static boolean renderObserverStructuredWidget(GuiGraphics g, Font font, DisplayTheme theme, StressometerHudState.PartWidget widget, List<StatsDisplayPanelBE.DataChannel> channels, int x, int y, int w, int h) {
        return switch (widget.getPart()) {
            case OBSERVER_ITEMS -> {
                final List<StatsDisplayPanelBE.DataChannel> items = new ArrayList<>();
                for (final StatsDisplayPanelBE.DataChannel ch : channels) {
                    if (ch.getId().contains("_auto_item_")) {
                        items.add(ch);
                    }
                }
                items.sort(Comparator.comparingDouble(ch -> -StressometerHudDataResolver.latestValue(ch, 0f)));
                renderChannelList(g, font, theme, widget, items, x, y, w, h);
                yield true;
            }
            case OBSERVER_FLUIDS -> {
                final List<StatsDisplayPanelBE.DataChannel> fluids = new ArrayList<>();
                for (final StatsDisplayPanelBE.DataChannel ch : channels) {
                    if (ch.getId().contains("_auto_fluid_")) {
                        fluids.add(ch);
                    }
                }
                fluids.sort(Comparator.comparingDouble(ch -> -StressometerHudDataResolver.latestValue(ch, 0f)));
                renderChannelList(g, font, theme, widget, fluids, x, y, w, h);
                yield true;
            }
            default -> false;
        };
    }

    private static void renderChannelList(GuiGraphics g, Font font, DisplayTheme theme, StressometerHudState.PartWidget widget, List<StatsDisplayPanelBE.DataChannel> rows, int x, int y, int w, int h) {
        final boolean shadow = widget.hasTextShadow();
        final int pad = Math.max(3, Math.min(6, Math.min(w, h) / 7));
        int contentY = y;
        if (h >= 42) {
            drawCentered(g, font, truncate(font, widgetTitle(widget), w - pad * 2), x + w / 2, y + pad, theme.dim, shadow);
            g.fill(x + 6, y + pad + font.lineHeight + 1, x + w - 6, y + pad + font.lineHeight + 2, dim(theme.border, 0.55f));
            contentY = y + pad + font.lineHeight + 4;
        }
        if (rows.isEmpty()) {
            drawCentered(g, font, ",", x + w / 2, contentY + (y + h - contentY - font.lineHeight) / 2, theme.dim, shadow);
            return;
        }
        final int rowH = font.lineHeight + 2;
        int rowY = contentY + 2;
        int shown = 0;
        for (final StatsDisplayPanelBE.DataChannel ch : rows) {
            if (rowY + rowH > y + h - pad - (rows.size() > shown + 1 ? rowH : 0)) {
                break;
            }
            final String valueStr = formatCompact(StressometerHudDataResolver.latestValue(ch, 0f));
            final int valueW = font.width(valueStr);
            final int labelMaxW = w - pad * 2 - valueW - 6;
            g.fill(x + pad, rowY, x + w - pad, rowY + rowH - 1, 0x18FFFFFF);
            drawText(g, font, truncate(font, ch.getLabel(), labelMaxW), x + pad + 2, rowY + 1, theme.text, shadow);
            drawText(g, font, valueStr, x + w - pad - valueW - 2, rowY + 1, theme.accent, shadow);
            rowY += rowH;
            shown++;
        }
        final int overflow = rows.size() - shown;
        if (overflow > 0) {
            drawCentered(g, font, "+" + overflow + " more", x + w / 2, y + h - font.lineHeight - pad, theme.dim, shadow);
        }
    }

    private static boolean renderFluidTankStructuredWidget(GuiGraphics g, Font font, DisplayTheme theme, StressometerHudState.PartWidget widget, List<StatsDisplayPanelBE.DataChannel> channels, int x, int y, int w, int h) {
        if (widget.getPart() != StressometerHudState.Part.FLUID_COMPACT) {
            return false;
        }
        final String fill = percentValue(channels, "tank_fill");
        final String amount = aeroValue(channels, "fluid_amount") + " mB";
        final String cap = aeroValue(channels, "tank_capacity") + " mB";
        renderTripleCards(g, font, theme, widget, x, y, w, h, widgetTitle(widget),
                "FILL", fill, "AMOUNT", amount, "CAPACITY", cap,
                theme.accent, theme.accent, theme.dim);
        return true;
    }

    private static void renderTripleCards(GuiGraphics g, Font font, DisplayTheme theme, StressometerHudState.PartWidget widget, int x, int y, int w, int h, String title,
                                          String aLabel, String aValue, String bLabel, String bValue, String cLabel, String cValue,
                                          int aAccent, int bAccent, int cAccent) {
        final int pad = drawStructuredTitle(g, font, theme, widget, x, y, w, h, title);
        final int gap = 6;
        final int cardW = Math.max(28, (w - pad * 2 - gap * 2) / 3);
        final int cardH = Math.max(22, h - pad * 2 - 18);
        final int cardY = y + h - pad - cardH;
        metricCard(g, font, null, x + pad, cardY, cardW, cardH, aLabel, aValue, theme, aAccent);
        metricCard(g, font, null, x + pad + cardW + gap, cardY, cardW, cardH, bLabel, bValue, theme, bAccent);
        metricCard(g, font, null, x + pad + (cardW + gap) * 2, cardY, w - pad * 2 - (cardW + gap) * 2, cardH, cLabel, cValue, theme, cAccent);
    }

    private static void renderDualCards(GuiGraphics g, Font font, DisplayTheme theme, StressometerHudState.PartWidget widget, int x, int y, int w, int h, String title,
                                        String aLabel, String aValue, int aAccent, String bLabel, String bValue, int bAccent) {
        final int pad = drawStructuredTitle(g, font, theme, widget, x, y, w, h, title);
        final int gap = 8;
        final int cardW = Math.max(36, (w - pad * 2 - gap) / 2);
        final int cardH = Math.max(24, h - pad * 2 - 18);
        final int cardY = y + h - pad - cardH;
        metricCard(g, font, null, x + pad, cardY, cardW, cardH, aLabel, aValue, theme, aAccent);
        metricCard(g, font, null, x + pad + cardW + gap, cardY, w - pad * 2 - cardW - gap, cardH, bLabel, bValue, theme, bAccent);
    }

    private static void renderQuadCards(GuiGraphics g, Font font, DisplayTheme theme, StressometerHudState.PartWidget widget, int x, int y, int w, int h, String title,
                                        String aLabel, String aValue, int aAccent, String bLabel, String bValue, int bAccent,
                                        String cLabel, String cValue, int cAccent, String dLabel, String dValue, int dAccent) {
        final int pad = drawStructuredTitle(g, font, theme, widget, x, y, w, h, title);
        final int gap = 6;
        final int cardW = Math.max(34, (w - pad * 2 - gap) / 2);
        final int cardH = Math.max(18, (h - pad * 2 - 22 - gap) / 2);
        final int row1Y = y + pad + 14;
        final int row2Y = row1Y + cardH + gap;
        metricCard(g, font, null, x + pad, row1Y, cardW, cardH, aLabel, aValue, theme, aAccent);
        metricCard(g, font, null, x + pad + cardW + gap, row1Y, w - pad * 2 - cardW - gap, cardH, bLabel, bValue, theme, bAccent);
        metricCard(g, font, null, x + pad, row2Y, cardW, cardH, cLabel, cValue, theme, cAccent);
        metricCard(g, font, null, x + pad + cardW + gap, row2Y, w - pad * 2 - cardW - gap, cardH, dLabel, dValue, theme, dAccent);
    }

    private static void renderSixPackCards(GuiGraphics g, Font font, DisplayTheme theme, StressometerHudState.PartWidget widget, int x, int y, int w, int h, String title,
                                           String aLabel, String aValue, String bLabel, String bValue, String cLabel, String cValue,
                                           String dLabel, String dValue, String eLabel, String eValue, String fLabel, String fValue) {
        final int pad = drawStructuredTitle(g, font, theme, widget, x, y, w, h, title);
        final int gap = 6;
        final int cardW = Math.max(24, (w - pad * 2 - gap * 2) / 3);
        final int cardH = Math.max(16, (h - pad * 2 - 22 - gap) / 2);
        final int row1Y = y + pad + 14;
        final int row2Y = row1Y + cardH + gap;
        metricCard(g, font, null, x + pad, row1Y, cardW, cardH, aLabel, aValue, theme, SAFE_GREEN);
        metricCard(g, font, null, x + pad + cardW + gap, row1Y, cardW, cardH, bLabel, bValue, theme, theme.accent);
        metricCard(g, font, null, x + pad + (cardW + gap) * 2, row1Y, w - pad * 2 - (cardW + gap) * 2, cardH, cLabel, cValue, theme, ALERT_AMBER);
        metricCard(g, font, null, x + pad, row2Y, cardW, cardH, dLabel, dValue, theme, SAFE_GREEN);
        metricCard(g, font, null, x + pad + cardW + gap, row2Y, cardW, cardH, eLabel, eValue, theme, theme.accent);
        metricCard(g, font, null, x + pad + (cardW + gap) * 2, row2Y, w - pad * 2 - (cardW + gap) * 2, cardH, fLabel, fValue, theme, ALERT_AMBER);
    }

    private static void renderArtificialHorizon(GuiGraphics g, Font font, DisplayTheme theme,
                                                StressometerHudState.PartWidget widget,
                                                List<StatsDisplayPanelBE.DataChannel> channels,
                                                int x, int y, int w, int h) {
        final boolean shadow = widget.hasTextShadow();
        final int pad = Math.max(3, Math.min(6, Math.min(w, h) / 7));

        float bank  = Math.max(-90.0f, Math.min(90.0f,
                StressometerHudDataResolver.latestValue(StressometerHudDataResolver.findWidgetChannel(channels, "attitude_bank"), 0.0f)));
        float pitch = Math.max(-90.0f, Math.min(90.0f,
                StressometerHudDataResolver.latestValue(StressometerHudDataResolver.findWidgetChannel(channels, "attitude_pitch"), 0.0f)));
        float yaw   = StressometerHudDataResolver.latestValue(
                StressometerHudDataResolver.findWidgetChannel(channels, "attitude_yaw"), 0.0f);
        yaw = ((yaw % 360.0f) + 360.0f) % 360.0f;

        final int bankBarH = 10;
        final int yawBarH  = font.lineHeight + 2;
        final int adiX = x + pad;
        final int adiY = y + pad + bankBarH;
        final int adiW = w - pad * 2;
        final int adiH = h - pad * 2 - bankBarH - yawBarH;
        if (adiH < 8 || adiW < 8) return;

        final int cx = adiX + adiW / 2;
        final int cy = adiY + adiH / 2;
        final int bigSize = Math.max(adiW, adiH) * 2;
        final int pitchPx = (int) ((pitch / 90.0f) * (adiH * 0.45f));

        g.fill(adiX, adiY, adiX + adiW, adiY + adiH, 0xFF0A0A14);

        g.enableScissor(adiX, adiY, adiX + adiW, adiY + adiH);
        g.pose().pushPose();
        g.pose().translate(cx, cy + pitchPx, 0.0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(-bank));

        g.fill(-bigSize, -bigSize, bigSize, 0, 0xFF2A6899);
        g.fill(-bigSize, 0, bigSize, bigSize, 0xFF6E4C0F);
        g.fill(-bigSize, -1, bigSize, 1, 0xFFFFFFFF);

        final int p10 = (int) ((10.0f / 90.0f) * (adiH * 0.45f));
        final int p20 = (int) ((20.0f / 90.0f) * (adiH * 0.45f));
        final int lw  = adiW / 4;
        if (p10 > 2) {
            g.fill(-lw, p10, lw, p10 + 1, 0xAAFFFFFF);
            g.fill(-lw, -p10 - 1, lw, -p10, 0xAAFFFFFF);
        }
        if (p20 > p10 + 2) {
            g.fill(-lw * 3 / 4, p20, lw * 3 / 4, p20 + 1, 0xAAFFFFFF);
            g.fill(-lw * 3 / 4, -p20 - 1, lw * 3 / 4, -p20, 0xAAFFFFFF);
        }
        g.pose().popPose();
        g.disableScissor();

        final int armW = adiW / 6;
        g.fill(cx - armW - 2, cy - 1, cx - 2, cy + 1, 0xFFFFFFFF);
        g.fill(cx + 2, cy - 1, cx + armW + 2, cy + 1, 0xFFFFFFFF);
        g.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFFFD700);

        final int bsCX  = x + w / 2;
        final int bsHW  = (adiW / 2) - 4;
        final float bsScale = (float) bsHW / 45.0f;
        final int ruleY = y + pad + bankBarH - 3;
        g.fill(bsCX - bsHW, ruleY, bsCX + bsHW, ruleY + 1, 0x60FFFFFF);
        for (int deg : new int[]{0, 15, 30}) {
            final int tickH = (deg == 0) ? 5 : 3;
            final int px = (int) (deg * bsScale);
            g.fill(bsCX + px, ruleY - tickH, bsCX + px + 1, ruleY, 0xCCFFFFFF);
            if (deg != 0) g.fill(bsCX - px, ruleY - tickH, bsCX - px + 1, ruleY, 0xCCFFFFFF);
        }
        final int pointerX = bsCX + (int) (Math.max(-45.0f, Math.min(45.0f, bank)) * bsScale);
        g.fill(pointerX - 1, y + pad, pointerX + 1, ruleY, 0xCCFFD700);

        final String yawStr = String.format(Locale.ROOT, "%03.0f\u00b0", yaw);
        drawCentered(g, font, yawStr, x + w / 2, y + h - pad - font.lineHeight, SAFE_GREEN, shadow);
    }

    private static void renderStatusStrip(GuiGraphics g, Font font, DisplayTheme theme, StressometerHudState.PartWidget widget, int x, int y, int w, int h, String title,
                                          String[] labels, boolean[] active, int[] colors) {
        final int pad = drawStructuredTitle(g, font, theme, widget, x, y, w, h, title);
        final int gap = 4;
        final int cellCount = Math.min(labels.length, Math.min(active.length, colors.length));
        final int cardW = Math.max(24, (w - pad * 2 - gap * Math.max(0, cellCount - 1)) / Math.max(1, cellCount));
        final int cardH = Math.max(18, h - pad * 2 - 18);
        final int cardY = y + h - pad - cardH;
        int drawX = x + pad;
        for (int i = 0; i < cellCount; ++i) {
            final int drawW = (i == cellCount - 1) ? (x + w - pad - drawX) : cardW;
            metricCard(g, font, null, drawX, cardY, drawW, cardH, labels[i], active[i] ? "LIVE" : "--", theme, active[i] ? colors[i] : DIM_TEXT);
            drawX += cardW + gap;
        }
    }

    private static int drawStructuredTitle(GuiGraphics g, Font font, DisplayTheme theme, StressometerHudState.PartWidget widget, int x, int y, int w, int h, String title) {
        final int pad = Math.max(4, Math.min(8, Math.min(w, h) / 7));
        if (h >= 42) {
            drawCentered(g, font, truncate(font, title, w - pad * 2), x + w / 2, y + pad, theme.dim, widget.hasTextShadow());
            g.fill(x + 6, y + pad + font.lineHeight + 1, x + w - 6, y + pad + font.lineHeight + 2, dim(theme.border, 0.55f));
        }
        return pad;
    }

    private static String widgetTitle(StressometerHudState.PartWidget widget) {
        return widget.getMetricLabel().isEmpty() ? widget.getPart().label() : widget.getMetricLabel();
    }

    private static String angleValue(float value) {
        return formatCompact(value) + "°";
    }

    private static String forceValue(List<StatsDisplayPanelBE.DataChannel> channels, String... keys) {
        final StatsDisplayPanelBE.DataChannel channel = StressometerHudDataResolver.findWidgetChannel(channels, keys);
        return channel == null ? "--" : formatCompact(StressometerHudDataResolver.latestValue(channel, 0.0f));
    }

    private static String signedForceValue(List<StatsDisplayPanelBE.DataChannel> channels, String... keys) {
        final StatsDisplayPanelBE.DataChannel channel = StressometerHudDataResolver.findWidgetChannel(channels, keys);
        if (channel == null) {
            return "--";
        }
        final float value = StressometerHudDataResolver.latestValue(channel, 0.0f);
        return (value > 0.0f ? "+" : "") + formatCompact(value);
    }

    private static String signedValue(List<StatsDisplayPanelBE.DataChannel> channels, String... keys) {
        final StatsDisplayPanelBE.DataChannel channel = StressometerHudDataResolver.findWidgetChannel(channels, keys);
        if (channel == null) {
            return "--";
        }
        final float value = StressometerHudDataResolver.latestValue(channel, 0.0f);
        return (value > 0.0f ? "+" : "") + formatCompact(value);
    }

    private static String powerValue(List<StatsDisplayPanelBE.DataChannel> channels, String key) {
        final float value = StressometerHudDataResolver.latestValue(StressometerHudDataResolver.findWidgetChannel(channels, key), 0.0f);
        return Integer.toString(Math.round(value));
    }

    private static String percentValue(List<StatsDisplayPanelBE.DataChannel> channels, String... keys) {
        final StatsDisplayPanelBE.DataChannel channel = StressometerHudDataResolver.findWidgetChannel(channels, keys);
        if (channel == null) {
            return "--";
        }
        final float value = StressometerHudDataResolver.latestValue(channel, 0.0f);
        return String.format(Locale.ROOT, "%.0f%%", value);
    }

    private static String aeroValue(List<StatsDisplayPanelBE.DataChannel> channels, String... keys) {
        final StatsDisplayPanelBE.DataChannel channel = StressometerHudDataResolver.findWidgetChannel(channels, keys);
        return channel == null ? "--" : formatCompact(StressometerHudDataResolver.latestValue(channel, 0.0f));
    }

    private static boolean hasAnyChannel(List<StatsDisplayPanelBE.DataChannel> channels, String... keys) {
        return StressometerHudDataResolver.findWidgetChannel(channels, keys) != null;
    }

    private static boolean boolValue(List<StatsDisplayPanelBE.DataChannel> channels, String key) {
        return StressometerHudDataResolver.latestValue(StressometerHudDataResolver.findWidgetChannel(channels, key), 0.0f) > 0.5f;
    }

    private static String textValue(List<StatsDisplayPanelBE.DataChannel> channels, String key, String fallback) {
        final StatsDisplayPanelBE.DataChannel channel = StressometerHudDataResolver.findWidgetChannel(channels, key);
        if (channel == null) {
            return fallback;
        }
        if (channel.getLastText() != null && !channel.getLastText().isEmpty()) {
            return channel.getLastText();
        }
        return formatCompact(StressometerHudDataResolver.latestValue(channel, 0.0f));
    }

    private static String stateText(List<StatsDisplayPanelBE.DataChannel> channels) {
        final String state = textValue(channels, "state", "");
        if (!state.isEmpty()) {
            return shortText(state.toUpperCase(Locale.ROOT), 10);
        }
        if (boolValue(channels, "running")) {
            return "RUNNING";
        }
        return "STANDBY";
    }

    private static int stateColor(List<StatsDisplayPanelBE.DataChannel> channels, DisplayTheme theme) {
        final String state = textValue(channels, "state", "").toLowerCase(Locale.ROOT);
        if (state.contains("fail") || state.contains("stall") || state.contains("stop")) {
            return DANGER_RED;
        }
        if (state.contains("warn") || state.contains("hold") || state.contains("wait")) {
            return ALERT_AMBER;
        }
        if (boolValue(channels, "running")) {
            return SAFE_GREEN;
        }
        return theme.accent;
    }

    private static String shortText(String value, int maxLength) {
        if (value == null || value.isEmpty()) {
            return "--";
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private static void render(GuiGraphics g, Font font, StatsDisplayPanelBE panel, DisplayTheme theme, StressometerHudState state, StressometerHudReadout readout, int x, int y, int w, int h, long tick) {
        final int clampedW = Math.max(1, w);
        final int clampedH = Math.max(1, h);
        final int accent = statusColor(readout);
        if (clampedW < 120 || clampedH < 74) {
            renderTiny(g, font, theme, state, readout, x, y, clampedW, clampedH, accent);
            return;
        }
        if (state != null && state.isCompact()) {
            renderCompact(g, font, panel, theme, state, readout, x, y, clampedW, clampedH, tick, accent);
            return;
        }
        if (state == null || state.isBackgroundVisible()) {
            panel(g, x, y, clampedW, clampedH, theme, accent);
        }

        final int pad = Math.max(8, Math.min(14, clampedW / 24));
        final int rightBadgeW = Math.min(92, Math.max(62, clampedW / 3));
        final String title = readout.status() == StressometerHudReadout.Status.SAMPLE ? "STRESS HUD - SAMPLE" : "STRESS HUD";
        final boolean shadow = state == null || state.hasTextShadow();
        if (state == null || state.isHeaderVisible()) {
            drawText(g, font, title, x + pad, y + pad, theme.text, shadow);
        }
        if (state == null || state.isNetworkNameVisible()) {
            drawText(g, font, truncate(font, readout.sourceLabel().isEmpty() ? statusText(readout) : readout.sourceLabel(), clampedW - pad * 2 - rightBadgeW - 8), x + pad, y + pad + 11, theme.dim, shadow);
        }
        if (state == null || state.isStatusVisible() || (state.isWarningVisible() && readout.overstressed())) {
            badge(g, font, state, x + clampedW - pad - rightBadgeW, y + pad - 1, rightBadgeW, 16, statusLabel(readout), accent);
        }

        final int barX = x + pad;
        final int barY = y + pad + 32;
        final int barW = clampedW - pad * 2;
        final int barH = Math.max(20, Math.min(30, clampedH / 5));
        final BlockPos pos = panel != null ? panel.getBlockPos() : BlockPos.ZERO;
        if (state == null || state.isLoadBarVisible()) {
            BrassTrackerRenderer.drawHorizontalGui(g, barX, barY, barW, barH, pos, readout.animationSlot(), clamp01(readout.stressPercent() / 100.0f), 0.0f, 0.6f, SAFE_GREEN, 0.6f, ALERT_AMBER, 0.85f, DANGER_RED, accent, readout.overstressed(), tick);
        }

        final String stressLine = buildStressLine(state, readout);
        if (!stressLine.isEmpty()) {
            drawCentered(g, font, stressLine, x + clampedW / 2, barY + barH + 5, theme.text, shadow);
        }
        if (state == null || state.isStressPercentVisible()) {
            drawCentered(g, font, String.format(Locale.ROOT, "%.1f%% LOAD", readout.stressPercent()), x + clampedW / 2, barY - 12, accent, shadow);
        }

        final int infoY = barY + barH + 19;
        if (clampedH >= 128) {
            final int gap = 6;
            final int cardW = (barW - gap * 2) / 3;
            final int cardH = Math.max(26, Math.min(36, clampedH - infoY - pad));
            int cardIndex = 0;
            if (state == null || state.isRpmVisible()) {
                metricCard(g, font, state, barX + (cardW + gap) * cardIndex++, infoY, cardW, cardH, "RPM", formatCompact(readout.rpm()), theme, theme.accent);
            }
            if (state == null || state.isRemainingStressVisible()) {
                metricCard(g, font, state, barX + (cardW + gap) * cardIndex++, infoY, cardW, cardH, "REMAINING", formatCompact(readout.remainingStress()), theme, SAFE_GREEN);
            }
            if (state == null || state.isStatusVisible()) {
                metricCard(g, font, state, barX + (cardW + gap) * cardIndex, infoY, cardW, cardH, "STATE", statusLabel(readout), theme, accent);
            }
        } else {
            if (state == null || state.isRpmVisible()) {
                drawText(g, font, "RPM " + formatCompact(readout.rpm()), barX, infoY, theme.dim, shadow);
            }
            if (state == null || state.isRemainingStressVisible()) {
                drawText(g, font, "REMAINING " + formatCompact(readout.remainingStress()), barX + Math.max(72, barW / 3), infoY, theme.dim, shadow);
            }
        }
    }

    private static void renderTiny(GuiGraphics g, Font font, DisplayTheme theme, StressometerHudState state, StressometerHudReadout readout, int x, int y, int w, int h, int accent) {
        if (state == null || state.isBackgroundVisible()) {
            g.fill(x, y, x + w, y + h, PANEL_BG);
        }
        if (w >= 4 && h >= 4) {
            border(g, x, y, w, h, accent);
        }
        if (w >= 48 && h >= 18) {
            drawText(g, font, truncate(font, statusLabel(readout), w - 8), x + 4, y + Math.max(4, (h - font.lineHeight) / 2), theme.text, state == null || state.hasTextShadow());
        }
    }

    private static void renderCompact(GuiGraphics g, Font font, StatsDisplayPanelBE panel, DisplayTheme theme, StressometerHudState state, StressometerHudReadout readout, int x, int y, int w, int h, long tick, int accent) {
        if (state.isBackgroundVisible()) {
            g.fill(x, y, x + w, y + h, PANEL_BG);
            border(g, x, y, w, h, BORDER);
            g.fill(x + 1, y + 1, x + 4, y + h - 1, dim(accent, 0.75f));
            g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x16FFFFFF);
        }
        final boolean shadow = state.hasTextShadow();
        final int pad = Math.max(5, Math.min(8, w / 32));
        final int textX = x + pad + 3;
        final int textY = y + Math.max(4, (h - font.lineHeight) / 2);
        final StringBuilder line = new StringBuilder();
        if (state.isNetworkNameVisible() && !readout.sourceLabel().isEmpty()) {
            line.append(readout.sourceLabel()).append("  ");
        }
        if (state.isStressUsedVisible() || state.isStressCapacityVisible()) {
            line.append(buildStressLine(state, readout)).append("  ");
        }
        if (state.isStressPercentVisible()) {
            line.append(String.format(Locale.ROOT, "%.1f%%", readout.stressPercent())).append("  ");
        }
        if (state.isStatusVisible()) {
            line.append(statusLabel(readout));
        }
        String rendered = line.toString().trim();
        if (rendered.isEmpty()) {
            rendered = statusLabel(readout);
        }
        drawText(g, font, truncate(font, rendered, w - pad * 2 - 3), textX, textY, accent, shadow);
        if (state.isLoadBarVisible() && h >= 34) {
            final BlockPos pos = panel != null ? panel.getBlockPos() : BlockPos.ZERO;
            g.fill(x + pad + 3, y + h - 20, x + w - pad, y + h - 19, dim(BORDER, 0.7f));
            BrassTrackerRenderer.drawHorizontalGui(g, x + pad + 3, y + h - 18, w - pad * 2 - 3, 12, pos, readout.animationSlot(), clamp01(readout.stressPercent() / 100.0f), 0.0f, 0.6f, SAFE_GREEN, 0.6f, ALERT_AMBER, 0.85f, DANGER_RED, accent, readout.overstressed(), tick);
        }
    }

    private static void panel(GuiGraphics g, int x, int y, int w, int h, DisplayTheme theme, int accent) {
        g.fill(x, y, x + w, y + h, PANEL_BG);
        border(g, x, y, w, h, BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x18FFFFFF);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, 0x10FFFFFF);
        cornerRivets(g, x, y, w, h, dim(theme.border, 1.25f));
        g.fill(x + 1, y + 1, x + w - 1, y + 23, 0xCC1B1508);
        g.fill(x + 1, y + 23, x + w - 1, y + 24, dim(theme.border, 0.8f));
        g.fill(x + 1, y + 1, x + w - 1, y + 3, dim(accent, 0.85f));
        insetWell(g, x + 4, y + 26, w - 8, h - 30, INNER_BG);
    }

    private static void badge(GuiGraphics g, Font font, StressometerHudState state, int x, int y, int w, int h, String label, int color) {
        g.fill(x, y, x + w, y + h, dim(color, 0.20f));
        border(g, x, y, w, h, dim(color, 0.80f));
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x22FFFFFF);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0x33000000);
        final int mid = y + h / 2;
        g.fill(x + 2, mid - 1, x + 4, mid + 1, dim(color, 0.55f));
        g.fill(x + w - 4, mid - 1, x + w - 2, mid + 1, dim(color, 0.55f));
        drawCentered(g, font, truncate(font, label, w - 14), x + w / 2, y + (h - font.lineHeight) / 2, color, state == null || state.hasTextShadow());
    }

    private static void metricCard(GuiGraphics g, Font font, StressometerHudState state, int x, int y, int w, int h, String label, String value, DisplayTheme theme, int accent) {
        g.fill(x, y, x + w, y + h, dim(theme.border, 0.28f));
        g.fill(x, y, x + w, y + 1, 0x22FFFFFF);
        g.fill(x, y, x + 1, y + h, 0x18FFFFFF);
        g.fill(x, y + h - 1, x + w, y + h, 0x44000000);
        g.fill(x + w - 1, y, x + w, y + h, 0x38000000);
        final int nameH = font.lineHeight + 4;
        drawCentered(g, font, label, x + w / 2, y + 3, theme.dim, state == null || state.hasTextShadow());
        g.fill(x + 3, y + nameH, x + w - 3, y + nameH + 1, dim(accent, 0.35f));
        insetWell(g, x + 2, y + nameH + 2, w - 4, h - nameH - 4, 0x88000000);
        final int wellY = y + nameH + 2;
        final int wellH = h - nameH - 4;
        final int valueY = wellY + (wellH - font.lineHeight) / 2;
        drawCentered(g, font, truncate(font, value, w - 10), x + w / 2, valueY, accent, state == null || state.hasTextShadow());
    }

    private static void renderValueWidget(GuiGraphics g, Font font, String label, String value, int x, int y, int w, int h, DisplayTheme theme, int accent, boolean shadow) {
        final int labelY = y + Math.max(4, Math.min(8, h / 5));
        if (h >= 28) {
            g.fill(x + 1, y + 1, x + w - 1, y + labelY + font.lineHeight + 2, com.createstats.client.hud.HudAnim.withAlpha(theme != null ? theme.bg : 0xFF000000, 90));
            drawCentered(g, font, truncate(font, label, w - 8), x + w / 2, labelY, theme.dim, shadow);

            final int rule = com.createstats.client.hud.HudAnim.blendArgb(dim(theme.border, 0.5f), accent, 0.25f);
            g.fill(x + 4, labelY + font.lineHeight + 1, x + w - 4, labelY + font.lineHeight + 2, rule);
        }

        final int valueColor = isDangerAccent(accent)
                ? com.createstats.client.hud.HudAnim.blendArgb(dim(accent, 0.65f), accent,
                        0.6f + 0.4f * com.createstats.client.hud.HudAnim.pulse(System.currentTimeMillis(), 750L))
                : accent;
        final int valueY = Math.max(labelY + font.lineHeight + 4, y + (h + font.lineHeight) / 2 - 2);
        final int clampedValueY = Math.min(y + h - font.lineHeight - 4, valueY);
        drawCentered(g, font, truncate(font, value, w - 8), x + w / 2, clampedValueY, valueColor, shadow);

        if (h >= 32) {
            final int ruleY = clampedValueY + font.lineHeight + 1;
            final int ruleStart = x + w / 4;
            final int ruleEnd = x + (3 * w) / 4;
            g.fill(ruleStart, ruleY, ruleEnd, ruleY + 1, dim(accent, 0.55f));

            g.fill(ruleStart - 1, ruleY - 1, ruleStart + 1, ruleY + 2, dim(accent, 0.4f));
            g.fill(ruleEnd - 1, ruleY - 1, ruleEnd + 1, ruleY + 2, dim(accent, 0.4f));
        }
    }

    private static void widgetPanel(GuiGraphics g, int x, int y, int w, int h, DisplayTheme theme, int accent) {
        final int bg = theme != null ? com.createstats.client.hud.HudAnim.withAlpha(theme.bg, 220) : PANEL_BG;
        final int borderTop = theme != null ? dim(theme.border, 0.95f) : BORDER;
        final int borderShadow = theme != null ? dim(theme.border, 0.55f) : 0x60000000;

        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);

        g.fill(x, y + 1, x + 1, y + h - 1, borderTop);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, borderTop);
        g.fill(x + 1, y, x + w - 1, y + 1, borderTop);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, borderTop);

        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x18FFFFFF);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, 0x10FFFFFF);

        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0x30000000);
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, 0x24000000);

        if (w > 8 && h > 5) {
            final boolean danger = isDangerAccent(accent);
            int rim = dim(accent, 0.85f);
            if (danger) {
                final float pulse = com.createstats.client.hud.HudAnim.pulse(System.currentTimeMillis(), 900L);
                rim = com.createstats.client.hud.HudAnim.blendArgb(dim(accent, 0.7f), accent, 0.5f + 0.5f * pulse);
            }
            g.fill(x + 1, y + 1, x + w - 1, y + 3, rim);

            g.fill(x + 1, y + 1, x + 2, y + 4, dim(accent, 0.6f));
            g.fill(x + w - 2, y + 1, x + w - 1, y + 4, dim(accent, 0.6f));
        }

        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, borderShadow);
    }

    private static boolean isDangerAccent(int accent) {
        final int dr = (DANGER_RED >> 16) & 0xFF;
        final int dg = (DANGER_RED >> 8) & 0xFF;
        final int db = DANGER_RED & 0xFF;
        final int r = (accent >> 16) & 0xFF;
        final int gC = (accent >> 8) & 0xFF;
        final int b = accent & 0xFF;
        return Math.abs(r - dr) < 24 && Math.abs(gC - dg) < 24 && Math.abs(b - db) < 24;
    }

    private static String statusLabel(StressometerHudReadout readout) {
        return switch (readout.status()) {
            case SAMPLE -> "SAMPLE";
            case NO_SOURCE -> "NO SOURCE";
            case NO_DATA -> "NO DATA";
            case SOURCE_MISSING -> "MISSING";
            case OK -> {
                if (readout.overstressed()) {
                    yield "OVERSTRESSED";
                }
                if (readout.stressPercent() >= 85.0f) {
                    yield "NEAR LIMIT";
                }
                if (readout.stressPercent() >= 60.0f) {
                    yield "LOADED";
                }
                yield "STABLE";
            }
        };
    }

    private static String statusText(StressometerHudReadout readout) {
        return switch (readout.status()) {
            case SAMPLE -> "Sample data";
            case NO_SOURCE -> "No linked stressometer";
            case NO_DATA -> "Waiting for stress data";
            case SOURCE_MISSING -> "Stress source missing";
            case OK -> readout.sourceLabel();
        };
    }

    private static int statusColor(StressometerHudReadout readout) {
        if (readout.status() != StressometerHudReadout.Status.OK && readout.status() != StressometerHudReadout.Status.SAMPLE) {
            return ALERT_AMBER;
        }
        if (readout.overstressed() || readout.stressPercent() >= 100.0f) {
            return DANGER_RED;
        }
        if (readout.stressPercent() >= 85.0f) {
            return ALERT_AMBER;
        }
        return SAFE_GREEN;
    }

    private static void drawText(GuiGraphics g, Font font, String text, int x, int y, int color, boolean shadow) {
        if (shadow) {
            g.drawString(font, text, x + 1, y + 1, 0x80000000);
        }
        g.drawString(font, text, x, y, color);
    }

    private static void drawCentered(GuiGraphics g, Font font, String text, int centerX, int y, int color, boolean shadow) {
        final float scale = CURRENT_RENDER_SCALE;
        if (Math.abs(scale - 1.0f) < 0.01f) {
            drawText(g, font, text, centerX - font.width(text) / 2, y, color, shadow);
        } else {
            g.pose().pushPose();
            g.pose().translate(centerX, y, 0);
            g.pose().scale(scale, scale, 1.0f);
            drawText(g, font, text, -font.width(text) / 2, 0, color, shadow);
            g.pose().popPose();
        }
    }

    private static String buildStressLine(StressometerHudState state, StressometerHudReadout readout) {
        final boolean showUsed = state == null || state.isStressUsedVisible();
        final boolean showCapacity = state == null || state.isStressCapacityVisible();
        if (showUsed && showCapacity) {
            return formatCompact(readout.stressUsed()) + " / " + formatCompact(readout.stressCapacity()) + " SU";
        }
        if (showUsed) {
            return formatCompact(readout.stressUsed()) + " SU used";
        }
        if (showCapacity) {
            return formatCompact(readout.stressCapacity()) + " SU capacity";
        }
        return "";
    }

    private static String truncate(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        final String ellipsis = "..";
        String result = text;
        while (result.length() > 1 && font.width(result + ellipsis) > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + ellipsis;
    }

    private static String formatCompact(float value) {
        final float abs = Math.abs(value);
        if (abs >= 1_000_000.0f) {
            return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0f);
        }
        if (abs >= 10_000.0f) {
            return String.format(Locale.ROOT, "%.1fk", value / 1_000.0f);
        }
        if (abs >= 100.0f) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        if (abs >= 10.0f) {
            return String.format(Locale.ROOT, "%.1f", value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static float clamp01(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static void insetWell(GuiGraphics g, int x, int y, int w, int h, int bg) {
        if (w <= 0 || h <= 0) {
            return;
        }
        g.fill(x, y, x + w, y + h, bg);
        g.fill(x, y, x + w, y + 1, 0x55000000);
        g.fill(x, y, x + 1, y + h, 0x33000000);
        g.fill(x, y + h - 1, x + w, y + h, 0x18FFFFFF);
        g.fill(x + w - 1, y, x + w, y + h, 0x14FFFFFF);
    }

    private static void cornerRivets(GuiGraphics g, int x, int y, int w, int h, int color) {
        final int ri = 2;
        final int rv = 2;
        g.fill(x + ri, y + ri, x + ri + rv, y + ri + rv, color);
        g.fill(x + w - ri - rv, y + ri, x + w - ri, y + ri + rv, color);
        g.fill(x + ri, y + h - ri - rv, x + ri + rv, y + h - ri, color);
        g.fill(x + w - ri - rv, y + h - ri - rv, x + w - ri, y + h - ri, color);
    }

    private static int dim(int color, float factor) {
        final int alpha = color >>> 24;
        final int a = alpha == 0 ? 255 : alpha;
        final int r = Math.max(0, Math.min(255, Math.round(((color >> 16) & 0xFF) * factor)));
        final int g = Math.max(0, Math.min(255, Math.round(((color >> 8) & 0xFF) * factor)));
        final int b = Math.max(0, Math.min(255, Math.round((color & 0xFF) * factor)));
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static void renderReactorLoadGauge(GuiGraphics g, Font font, DisplayTheme theme,
                                                StressometerHudState.PartWidget widget,
                                                StressometerHudReadout readout,
                                                int x, int y, int w, int h, long tick, boolean shadow) {
        final float pct = clamp01(readout.stressPercent() / 100.0f);
        final boolean overstressed = readout.overstressed();
        final int accent = overstressed ? DANGER_RED : (pct > 0.85f ? DANGER_RED : pct > 0.6f ? ALERT_AMBER : SAFE_GREEN);

        final int footerH = h >= 56 ? font.lineHeight + 6 : 0;
        final int dialW = w - 8;
        final int dialH = Math.max(20, h - footerH - 6);
        final int cx = x + w / 2;
        final int cy = y + 4 + dialH / 2;
        final int ringRadius = Math.max(8, Math.min(dialW, dialH) / 2 - 2);

        ring(g, cx, cy, ringRadius, ringRadius - 1, dim(theme.border, 0.7f));
        ring(g, cx, cy, ringRadius - 1, ringRadius - 2, com.createstats.client.hud.HudAnim.withAlpha(theme.bg, 200));

        final int segments = 24;
        final double startAngle = Math.toRadians(135.0);
        final double sweep = Math.toRadians(270.0);
        final int activeSegments = (int) Math.round(pct * segments);
        for (int i = 0; i < segments; i++) {
            final double a0 = startAngle + sweep * (i / (double) segments);
            final double a1 = startAngle + sweep * ((i + 0.85) / (double) segments);
            final boolean lit = i < activeSegments;
            final float t = i / (float) (segments - 1);
            final int segColor = lit
                    ? (t > 0.85f ? DANGER_RED : t > 0.6f ? ALERT_AMBER : SAFE_GREEN)
                    : dim(theme.border, 0.45f);
            final int rOuter = ringRadius - 3;
            final int rInner = ringRadius - 8;
            arcSegment(g, cx, cy, rInner, rOuter, a0, a1, segColor);
        }

        if (ringRadius > 14) {
            final int innerR = ringRadius - 11;
            final double rot = (tick % 80L) / 80.0 * Math.PI * 2.0;
            for (int i = 0; i < 16; i++) {
                final double ang = rot + i * (Math.PI * 2 / 16);
                final int tx = cx + (int) Math.round(Math.cos(ang) * innerR);
                final int ty = cy + (int) Math.round(Math.sin(ang) * innerR);
                final boolean major = i % 4 == 0;
                final int tc = major ? dim(theme.text, 0.85f) : dim(theme.dim, 0.7f);
                g.fill(tx, ty, tx + 1, ty + 1, tc);
            }
        }

        if (overstressed || pct > 0.9f) {
            final float pulse = com.createstats.client.hud.HudAnim.pulse(System.currentTimeMillis(), overstressed ? 380L : 700L);
            final int haloAlpha = Math.round(40 + 90 * pulse);
            final int halo = (Math.min(255, haloAlpha) << 24) | (DANGER_RED & 0x00FFFFFF);
            ring(g, cx, cy, ringRadius + 1, ringRadius, halo);
            ring(g, cx, cy, ringRadius + 2, ringRadius + 1, dim(halo, 0.55f));
        }

        final String pctText = String.format(Locale.ROOT, "%.0f%%", readout.stressPercent());
        final int textY = cy - font.lineHeight / 2;
        drawCentered(g, font, pctText, cx, textY, accent, shadow);

        if (footerH > 0) {
            final int footerY = y + h - footerH + 1;
            g.fill(x + 6, footerY, x + w - 6, footerY + 1, dim(accent, 0.55f));
            final String rpmStr = formatCompact(readout.rpm()) + " RPM";
            drawCentered(g, font, rpmStr, cx, footerY + 3, theme.text, shadow);
        }
    }

    private static void ring(GuiGraphics g, int cx, int cy, int rOuter, int rInner, int color) {
        final int rOut2 = rOuter * rOuter;
        final int rIn2 = rInner * rInner;
        for (int dy = -rOuter; dy <= rOuter; dy++) {
            for (int dx = -rOuter; dx <= rOuter; dx++) {
                final int d2 = dx * dx + dy * dy;
                if (d2 <= rOut2 && d2 >= rIn2) {
                    g.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
                }
            }
        }
    }

    private static void arcSegment(GuiGraphics g, int cx, int cy, int rInner, int rOuter, double a0, double a1, int color) {
        final int rOut2 = rOuter * rOuter;
        final int rIn2 = rInner * rInner;
        final double cosA0 = Math.cos(a0);
        final double sinA0 = Math.sin(a0);
        final double cosA1 = Math.cos(a1);
        final double sinA1 = Math.sin(a1);
        for (int dy = -rOuter; dy <= rOuter; dy++) {
            for (int dx = -rOuter; dx <= rOuter; dx++) {
                final int d2 = dx * dx + dy * dy;
                if (d2 > rOut2 || d2 < rIn2) continue;

                final double crossA = dx * sinA0 - dy * cosA0;
                final double crossB = dx * sinA1 - dy * cosA1;
                if (crossA <= 0 && crossB >= 0) {
                    g.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
                }
            }
        }
    }

    private static boolean renderVariantSourcePart(GuiGraphics g, Font font, DisplayTheme theme,
                                                   StressometerHudState.PartWidget widget,
                                                   SourceConfig source,
                                                   List<StatsDisplayPanelBE.DataChannel> channels,
                                                   int accent, int x, int y, int w, int h, long tick) {
        final String variant = widget.getRenderVariant();
        if (variant == null || variant.isEmpty() || "classic".equals(variant)) {
            return false;
        }
        if (source == null) {
            return false;
        }
        final String type = source.getSourceType();

        if ("lamp".equals(variant) && "Stockpile Switch".equals(type)
                && (widget.getPart() == StressometerHudState.Part.STOCKPILE_GAUGE
                 || widget.getPart() == StressometerHudState.Part.STOCKPILE_OUTPUT)) {
            renderStockpileLamp(g, font, theme, widget, channels, accent, x, y, w, h, tick);
            return true;
        }

        if ("droplet".equals(variant)
                && (widget.getPart() == StressometerHudState.Part.FLUID_LEVEL
                 || widget.getPart() == StressometerHudState.Part.BOILER_WATER
                 || widget.getPart() == StressometerHudState.Part.ENGINE_FUEL)) {
            renderDroplet(g, font, theme, widget, channels, accent, x, y, w, h, tick);
            return true;
        }

        if ("flame".equals(variant) && widget.getPart() == StressometerHudState.Part.BOILER_HEAT) {
            renderFlame(g, font, theme, widget, channels, accent, x, y, w, h, tick);
            return true;
        }

        if ("streak".equals(variant)
                && (widget.getPart() == StressometerHudState.Part.VELOCITY_AXIS
                 || widget.getPart() == StressometerHudState.Part.AERO_DRIVE)) {
            renderSpeedStreak(g, font, theme, widget, channels, accent, x, y, w, h, tick);
            return true;
        }

        if ("marquee".equals(variant) && widget.getPart() == StressometerHudState.Part.INBOUND_TRAINS) {
            renderInboundMarquee(g, font, theme, widget, channels, accent, x, y, w, h, tick);
            return true;
        }

        if ("attitude_ladder".equals(variant) && widget.getPart() == StressometerHudState.Part.AERO_HORIZON) {
            if (renderAttitudeLadder(g, font, theme, widget, channels, accent, x, y, w, h, tick)) return true;
        }

        if ("pulse".equals(variant) && widget.getPart() == StressometerHudState.Part.PRODUCTION_RATE) {
            if (renderFactoryPulse(g, font, theme, widget, channels, accent, x, y, w, h, tick)) return true;
        }

        if ("bottleneck".equals(variant) && widget.getPart() == StressometerHudState.Part.FACTORY_SHORTFALL) {
            if (renderFactoryBottleneck(g, font, theme, widget, channels, accent, x, y, w, h, tick)) return true;
        }

        if ("compass".equals(variant) && widget.getPart() == StressometerHudState.Part.AERO_ORIENTATION) {
            if (renderShipCompass(g, font, theme, widget, channels, accent, x, y, w, h, tick)) return true;
        }

        if ("proximity".equals(variant) && widget.getPart() == StressometerHudState.Part.DOCKING_PAIR) {
            if (renderShipDocking(g, font, theme, widget, channels, accent, x, y, w, h, tick)) return true;
        }

        if ("signalbox".equals(variant) && widget.getPart() == StressometerHudState.Part.TRAIN_STATION_STATUS) {
            if (renderPlatformOccupancy(g, font, theme, widget, channels, accent, x, y, w, h, tick)) return true;
        }
        return false;
    }

    private static void renderStockpileLamp(GuiGraphics g, Font font, DisplayTheme theme,
                                            StressometerHudState.PartWidget widget,
                                            List<StatsDisplayPanelBE.DataChannel> channels,
                                            int accent, int x, int y, int w, int h, long tick) {
        final float fill = clamp01(StressometerHudDataResolver.latestValue(
                StressometerHudDataResolver.findWidgetChannel(channels, "fill_pct"), 0f) / 100f);

        final float capacity = Math.max(1f, StressometerHudDataResolver.latestValue(
                StressometerHudDataResolver.findWidgetChannel(channels, "stock_capacity"), 1f));
        final float lower = clamp01(StressometerHudDataResolver.latestValue(
                StressometerHudDataResolver.findWidgetChannel(channels, "lower_thresh"), 0f) / capacity);
        final float upper = clamp01(StressometerHudDataResolver.latestValue(
                StressometerHudDataResolver.findWidgetChannel(channels, "upper_thresh"), capacity) / capacity);
        final boolean powered = StressometerHudDataResolver.latestValue(
                StressometerHudDataResolver.findWidgetChannel(channels, "powered"), 0f) > 0.5f;

        final boolean danger = fill < Math.max(0.05f, lower * 0.5f) || fill > 0.97f;
        final int healthy = SAFE_GREEN;
        final int warn = ALERT_AMBER;
        final int alarm = DANGER_RED;
        final int liquidColor = danger ? alarm : (fill < lower ? warn : (fill > upper ? warn : healthy));

        final float flicker = danger ? (((tick * 1103515245L + 12345L) >>> 16) & 0x3F) / 63.0f : 0f;
        final float bloom = (1f - flicker * 0.4f) * (0.6f + 0.4f * com.createstats.client.hud.HudAnim.pulse(System.currentTimeMillis(), 1100L));

        final boolean compact = h < 48;
        final boolean shadow = widget.hasTextShadow();

        final int titlePad = 3;
        final int titleY = y + titlePad;
        final String title = !widget.getMetricLabel().isEmpty() ? widget.getMetricLabel() : "Stockpile";
        if (h >= 36) {
            drawCentered(g, font, truncate(font, title, w - 8), x + w / 2, titleY, theme.dim, shadow);
        }

        if (compact) {
            final int barTop = h >= 28 ? titleY + font.lineHeight + 2 : y + 4;
            final int barH = Math.max(8, y + h - barTop - 4);
            final int barX = x + 6;
            final int barW = w - 12;

            g.fill(barX - 2, barTop - 1, barX + barW + 2, barTop, dim(theme.border, 0.85f));
            g.fill(barX - 2, barTop + barH, barX + barW + 2, barTop + barH + 1, dim(theme.border, 0.85f));
            g.fill(barX - 2, barTop, barX, barTop + barH, dim(theme.border, 0.85f));
            g.fill(barX + barW, barTop, barX + barW + 2, barTop + barH, dim(theme.border, 0.85f));

            g.fill(barX, barTop, barX + barW, barTop + barH, com.createstats.client.hud.HudAnim.withAlpha(theme.bg, 240));

            final int liquidW = Math.max(0, Math.round(barW * fill));
            g.fill(barX, barTop, barX + liquidW, barTop + barH, dim(liquidColor, 0.85f));

            g.fill(barX, barTop, barX + barW, barTop + 1, 0x33FFFFFF);
            g.fill(barX, barTop + barH - 1, barX + barW, barTop + barH, 0x22000000);

            final int lowerX = barX + Math.round(barW * lower);
            final int upperX = barX + Math.round(barW * upper);
            g.fill(lowerX, barTop - 2, lowerX + 1, barTop + barH + 2, 0xCCFFFFFF);
            g.fill(upperX, barTop - 2, upperX + 1, barTop + barH + 2, 0xCCFFFFFF);

            final int dotR = 2;
            final int dotX = x + w - dotR - 3;
            final int dotY = barTop - 4 < y + 1 ? y + 2 : barTop - 4;
            final int dotColor = powered ? dim(SAFE_GREEN, 0.9f) : dim(theme.dim, 0.6f);
            g.fill(dotX - dotR, dotY - dotR, dotX + dotR + 1, dotY + dotR + 1, dotColor);
            return;
        }

        final int bodyTop = titleY + font.lineHeight + 4;
        final int bodyH = y + h - bodyTop - 6;
        final int bulbR = Math.max(10, Math.min(bodyH, w / 2 - 8) / 2);
        final int bulbCx = x + w / 2;
        final int bulbCy = bodyTop + bulbR + 2;

        final int armW = bulbR + 6;
        final int armH = 4;
        g.fill(bulbCx - armW, bodyTop, bulbCx + armW, bodyTop + armH, dim(theme.border, 0.95f));
        g.fill(bulbCx - armW, bodyTop, bulbCx - armW + 3, bodyTop + bulbR + 4, dim(theme.border, 0.85f));
        g.fill(bulbCx + armW - 3, bodyTop, bulbCx + armW, bodyTop + bulbR + 4, dim(theme.border, 0.85f));

        g.fill(bulbCx - armW + 1, bodyTop + 1, bulbCx - armW + 3, bodyTop + 3, 0xFFB89868);
        g.fill(bulbCx + armW - 3, bodyTop + 1, bulbCx + armW - 1, bodyTop + 3, 0xFFB89868);

        disk(g, bulbCx, bulbCy, bulbR, com.createstats.client.hud.HudAnim.withAlpha(theme.bg, 235));

        final int fillRows = Math.max(0, Math.round(bulbR * 2 * fill));
        final int liquidLight = com.createstats.client.hud.HudAnim.blendArgb(dim(liquidColor, 0.55f), liquidColor, 0.4f + 0.6f * bloom);
        diskBandClipped(g, bulbCx, bulbCy, bulbR, bulbCy + bulbR - fillRows, bulbCy + bulbR, liquidLight);

        ring(g, bulbCx, bulbCy, bulbR, bulbR - 1, dim(theme.border, 0.9f));

        for (int i = 0; i < bulbR / 2; i++) {
            final int gx = bulbCx - bulbR / 2 + i;
            final int gy = bulbCy - bulbR / 2 - i / 3;
            g.fill(gx, gy, gx + 1, gy + 1, 0x55FFFFFF);
        }

        final int lowerY = bulbCy + bulbR - Math.round(bulbR * 2 * lower);
        final int upperY = bulbCy + bulbR - Math.round(bulbR * 2 * upper);
        g.fill(bulbCx - bulbR - 2, lowerY, bulbCx - bulbR + 2, lowerY + 1, 0xCCFFFFFF);
        g.fill(bulbCx + bulbR - 2, upperY, bulbCx + bulbR + 2, upperY + 1, 0xCCFFFFFF);

        final int plateY = y + h - font.lineHeight - 6;
        g.fill(x + 8, plateY - 2, x + w - 8, plateY - 1, dim(theme.border, 0.5f));
        final String pctText = String.format(Locale.ROOT, "%.0f%%", fill * 100f);
        drawCentered(g, font, pctText, bulbCx, plateY, accent, shadow);
        final int ledR = 2;
        final int ledX = x + w - 10;
        final int ledY = plateY + font.lineHeight / 2;
        final int ledColor = powered ? com.createstats.client.hud.HudAnim.blendArgb(dim(SAFE_GREEN, 0.5f), SAFE_GREEN, bloom) : dim(theme.dim, 0.55f);
        g.fill(ledX - ledR, ledY - ledR, ledX + ledR + 1, ledY + ledR + 1, ledColor);
    }

    private static void disk(GuiGraphics g, int cx, int cy, int r, int color) {
        final int r2 = r * r;
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                if (dx * dx + dy * dy <= r2) {
                    g.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
                }
            }
        }
    }

    private static void diskBandClipped(GuiGraphics g, int cx, int cy, int r, int yMin, int yMax, int color) {
        final int r2 = r * r;
        for (int dy = -r; dy <= r; dy++) {
            final int ay = cy + dy;
            if (ay < yMin || ay > yMax) continue;
            for (int dx = -r; dx <= r; dx++) {
                if (dx * dx + dy * dy <= r2) {
                    g.fill(cx + dx, ay, cx + dx + 1, ay + 1, color);
                }
            }
        }
    }

    private static void renderDroplet(GuiGraphics g, Font font, DisplayTheme theme,
                                      StressometerHudState.PartWidget widget,
                                      List<StatsDisplayPanelBE.DataChannel> channels,
                                      int accent, int x, int y, int w, int h, long tick) {
        widgetPanel(g, x, y, w, h, theme, accent);

        float pct;
        String unit = "%";
        switch (widget.getPart()) {
            case FLUID_LEVEL -> pct = com.createstats.client.hud.HudAnim.clamp01(StressometerHudDataResolver.latestValue(
                    StressometerHudDataResolver.findWidgetChannel(channels, "tank_fill"), 0f) / 100f);
            case BOILER_WATER -> pct = com.createstats.client.hud.HudAnim.clamp01(StressometerHudDataResolver.latestValue(
                    StressometerHudDataResolver.findWidgetChannel(channels, "water_supply"), 0f) / 100f);
            case ENGINE_FUEL -> pct = StressometerHudDataResolver.latestValue(
                    StressometerHudDataResolver.findWidgetChannel(channels, "fuel_infinite"), 0f) > 0.5f ? 1f : 0.5f;
            default -> pct = 0f;
        }
        final long ms = System.currentTimeMillis();

        final int dropW = Math.min(w - 6, h - 4);
        final int dropX = x + 3;
        final int dropY = y + 2;
        final int cx = dropX + dropW / 2;
        final int cyBase = dropY + dropW / 2;
        final int r = Math.max(4, dropW / 2 - 2);

        ring(g, cx, cyBase, r + 1, r, theme.border);
        disk(g, cx, cyBase, r, com.createstats.client.hud.HudAnim.withAlpha(theme.bg, 200));

        final int liquidColor = com.createstats.client.hud.HudAnim.blendArgb(theme.accent, accent, 0.4f);
        final float wave = com.createstats.client.hud.HudAnim.triangle(ms, 1700) - 0.5f;
        final int yLevel = cyBase + r - Math.round(pct * (2 * r));
        for (int dx = -r; dx <= r; dx++) {
            final int sliceTop = yLevel + Math.round((float) Math.sin((dx + ms * 0.003f)) * 1.2f + wave * 1.3f);
            for (int dy = -r; dy <= r; dy++) {
                if (dx * dx + dy * dy > r * r) continue;
                final int py = cyBase + dy;
                if (py >= sliceTop) g.fill(cx + dx, py, cx + dx + 1, py + 1, liquidColor);
            }
        }

        g.fill(cx - r / 3, cyBase - r / 2, cx - r / 3 + 2, cyBase - r / 2 + 2, com.createstats.client.hud.HudAnim.withAlpha(0xFFFFFFFF, 160));

        final int textX = dropX + dropW + 6;
        if (textX < x + w - 4) {
            final String big = Math.round(pct * 100f) + unit;
            g.drawString(font, big, textX, y + h / 2 - 4, theme.text, widget.hasTextShadow());
        }
    }

    private static void renderFlame(GuiGraphics g, Font font, DisplayTheme theme,
                                    StressometerHudState.PartWidget widget,
                                    List<StatsDisplayPanelBE.DataChannel> channels,
                                    int accent, int x, int y, int w, int h, long tick) {
        widgetPanel(g, x, y, w, h, theme, accent);
        final float gauge = com.createstats.client.hud.HudAnim.clamp01(StressometerHudDataResolver.latestValue(
                StressometerHudDataResolver.findWidgetChannel(channels, "boiler_gauge"), 0f) / 100f);
        final float heatRaw = StressometerHudDataResolver.latestValue(
                StressometerHudDataResolver.findWidgetChannel(channels, "heat_level"), 0f);
        final float heat = com.createstats.client.hud.HudAnim.clamp01(heatRaw / 4f);
        final long ms = System.currentTimeMillis();

        final int flameW = Math.min(w - 4, h - 4);
        final int fx = x + 4;
        final int fy = y + 2;
        final int baseY = fy + flameW;
        final int peakBase = Math.max(8, Math.round(flameW * (0.25f + 0.7f * gauge)));

        final int outer = com.createstats.client.hud.HudAnim.blendArgb(0xFFFFB84A, 0xFFFF4D4D, heat);
        final int inner = com.createstats.client.hud.HudAnim.blendArgb(0xFFFFEFA8, outer, 0.5f);
        for (int col = 0; col < flameW; col++) {
            final float u = (col - flameW / 2f) / (flameW / 2f);
            final float wob = (float) Math.sin((ms * 0.012f) + col * 0.4f) * 0.18f;
            final int hPeak = Math.max(1, Math.round(peakBase * (1f - u * u) + wob * peakBase));
            for (int dy = 0; dy < hPeak; dy++) {
                final int py = baseY - dy;
                final float t = (float) dy / hPeak;
                final int color = com.createstats.client.hud.HudAnim.blendArgb(outer, inner, com.createstats.client.hud.HudAnim.clamp01(t * 1.4f));
                g.fill(fx + col, py, fx + col + 1, py + 1, com.createstats.client.hud.HudAnim.withAlpha(color, 220 - (int)(t * 80f)));
            }
        }

        if (heat > 0.85f) {
            final int sx = fx + flameW / 2 + (int)(Math.sin(ms * 0.005f) * 3);
            final int sy = baseY - peakBase - 4 - (int)((ms / 80) % 6);
            disk(g, sx, sy, 2, com.createstats.client.hud.HudAnim.withAlpha(0xFF888888, 140));
        }

        final int textX = fx + flameW + 6;
        if (textX < x + w - 4) {
            g.drawString(font, Math.round(gauge * 100f) + "%", textX, y + 4, theme.text, widget.hasTextShadow());
            g.drawString(font, "Heat " + Math.round(heatRaw), textX, y + 14, theme.border, widget.hasTextShadow());
        }
    }

    private static void renderSpeedStreak(GuiGraphics g, Font font, DisplayTheme theme,
                                          StressometerHudState.PartWidget widget,
                                          List<StatsDisplayPanelBE.DataChannel> channels,
                                          int accent, int x, int y, int w, int h, long tick) {
        widgetPanel(g, x, y, w, h, theme, accent);
        final float vel = StressometerHudDataResolver.latestValue(
                StressometerHudDataResolver.findWidgetChannel(channels, "adjusted_velocity"), 0f);
        final float absV = Math.abs(vel);

        final float intensity = com.createstats.client.hud.HudAnim.clamp01(absV / 256f);
        final long ms = System.currentTimeMillis();
        final int streakColor = com.createstats.client.hud.HudAnim.blendArgb(theme.accent, accent, 0.5f);

        final int rows = Math.max(3, (h - 16) / 4);
        final int rowGap = Math.max(3, (h - 16) / Math.max(1, rows));
        final int dir = vel >= 0 ? 1 : -1;
        for (int i = 0; i < rows; i++) {
            final int rowY = y + 8 + i * rowGap;

            final float phase = ((ms * 0.06f) + i * 17f) % 100f;
            final int len = Math.max(2, Math.round((6 + (i * 3 % 9)) * (0.4f + intensity)));
            final int xStart;
            if (dir > 0) {
                xStart = x + 4 + (int)((phase / 100f) * (w - 12 - len));
            } else {
                xStart = x + w - 4 - len - (int)((phase / 100f) * (w - 12 - len));
            }
            g.fill(xStart, rowY, xStart + len, rowY + 1, com.createstats.client.hud.HudAnim.withAlpha(streakColor, 150 + (int)(intensity * 80)));
        }

        final String label = String.format(Locale.ROOT, "%.1f", vel);
        g.drawString(font, label, x + 4, y + 2, theme.text, widget.hasTextShadow());

        final int cx = x + w - 8;
        final int cy = y + h / 2;
        final int chev = com.createstats.client.hud.HudAnim.blendArgb(theme.text, accent, 0.4f);
        for (int i = 0; i < 4; i++) {
            if (dir > 0) {
                g.fill(cx - i, cy - i, cx - i + 1, cy - i + 1, chev);
                g.fill(cx - i, cy + i, cx - i + 1, cy + i + 1, chev);
            } else {
                g.fill(cx + i - 4, cy - i, cx + i - 4 + 1, cy - i + 1, chev);
                g.fill(cx + i - 4, cy + i, cx + i - 4 + 1, cy + i + 1, chev);
            }
        }
    }

    private static void renderInboundMarquee(GuiGraphics g, Font font, DisplayTheme theme,
                                             StressometerHudState.PartWidget widget,
                                             List<StatsDisplayPanelBE.DataChannel> channels,
                                             int accent, int x, int y, int w, int h, long tick) {
        widgetPanel(g, x, y, w, h, theme, accent);
        final int inbound = Math.round(StressometerHudDataResolver.latestValue(
                StressometerHudDataResolver.findWidgetChannel(channels, "inbound_train"), 0f));
        final long ms = System.currentTimeMillis();

        final int headerH = 9;
        g.fill(x + 2, y + 2, x + w - 2, y + 2 + headerH, com.createstats.client.hud.HudAnim.withAlpha(0xFF000000, 110));
        g.drawString(font, "INBOUND", x + 5, y + 3, theme.accent, widget.hasTextShadow());
        final String countTxt = String.valueOf(inbound);
        final int cw = font.width(countTxt);
        g.drawString(font, countTxt, x + w - 5 - cw, y + 3, theme.text, widget.hasTextShadow());

        final int rowsAvail = (h - headerH - 6) / 10;
        final int rows = Math.max(1, Math.min(rowsAvail, Math.max(1, inbound)));

        for (int i = 0; i < rows; i++) {
            final int ry = y + 2 + headerH + 2 + i * 10;
            final int rh = 9;

            g.fill(x + 4, ry, x + w - 4, ry + rh, com.createstats.client.hud.HudAnim.withAlpha(0xFF111111, 180));

            final String plat = "P" + ((i + 1));
            g.fill(x + 5, ry + 1, x + 5 + 14, ry + rh - 1, com.createstats.client.hud.HudAnim.withAlpha(theme.accent, 200));
            g.drawString(font, plat, x + 7, ry + 1, 0xFF000000, false);

            final int etaSec = Math.max(0, 30 - (int)((ms / 1000 + i * 7) % 60));
            final String txt = String.format(Locale.ROOT, "%02d:%02ds", etaSec / 60, etaSec % 60);

            int tx = x + 24;
            for (int ci = 0; ci < txt.length(); ci++) {
                char c = txt.charAt(ci);
                final boolean flipping = ci == txt.length() - 2 && ((ms / 80) % 12) < 2;
                if (flipping) {
                    c = (char) ('0' + (int)((ms / 60 + ci) % 10));
                }
                g.drawString(font, String.valueOf(c), tx, ry + 1, theme.text, widget.hasTextShadow());
                tx += font.width(String.valueOf(c));
            }
        }
        if (inbound == 0) {
            g.drawString(font, "(no trains expected)", x + 6, y + headerH + 6, theme.border, widget.hasTextShadow());
        }
    }

    private static boolean renderAttitudeLadder(GuiGraphics g, Font font, DisplayTheme theme,
                                             StressometerHudState.PartWidget widget,
                                             List<StatsDisplayPanelBE.DataChannel> channels,
                                             int accent, int x, int y, int w, int h, long tick) {
        final var pitchCh = StressometerHudDataResolver.findWidgetChannel(channels, "attitude_pitch");
        if (pitchCh == null) return false;
        final float pitch = StressometerHudDataResolver.latestValue(pitchCh, 0f);
        final float bank = StressometerHudDataResolver.latestValue(
                StressometerHudDataResolver.findWidgetChannel(channels, "attitude_bank"), 0f);
        final boolean shadow = widget.hasTextShadow();

        final int ix = x + 3, iy = y + 12, iw = w - 6, ih = h - 16;
        if (iw < 12 || ih < 16) {
            g.drawString(font, String.format(Locale.ROOT, "P %.0f", pitch), x + 4, y + 4, theme.text, shadow);
            g.drawString(font, String.format(Locale.ROOT, "B %.0f", bank), x + 4, y + 14, theme.text, shadow);
            return true;
        }

        final int cx = ix + iw / 2;
        final int cy = iy + ih / 2;

        final int pitchOffset = Math.max(-ih, Math.min(ih, Math.round(pitch * 3f)));
        final int horizonY = cy + pitchOffset;
        final int sky = 0xFF335A8C;
        final int ground = 0xFF6E4A28;

        g.fill(ix, iy, ix + iw, Math.min(iy + ih, Math.max(iy, horizonY)), sky);

        g.fill(ix, Math.max(iy, Math.min(iy + ih, horizonY)), ix + iw, iy + ih, ground);

        if (horizonY > iy && horizonY < iy + ih) {
            g.fill(ix, horizonY, ix + iw, horizonY + 1, com.createstats.client.hud.HudAnim.withAlpha(theme.text, 220));
        }

        final int tickColor = com.createstats.client.hud.HudAnim.withAlpha(theme.text, 200);
        for (int p = -30; p <= 30; p += 10) {
            if (p == 0) continue;
            final int ly = cy + Math.round((pitch - p) * 3f);
            if (ly < iy + 2 || ly > iy + ih - 2) continue;
            final int tickW = (Math.abs(p) % 20 == 0) ? iw / 4 : iw / 7;
            g.fill(cx - tickW, ly, cx + tickW, ly + 1, tickColor);

            final String lab = String.valueOf(Math.abs(p));
            g.drawString(font, lab, cx + tickW + 2, ly - 3, tickColor, false);
        }

        final int bankCx = cx;
        final int bankCy = iy + 4;
        final int bankR = Math.min(iw / 4, 10);
        final double bankRad = Math.toRadians(bank);
        final int btx = bankCx + (int) (Math.sin(bankRad) * bankR);
        final int bty = bankCy + (int) (-Math.cos(bankRad) * bankR);
        com.createstats.client.hud.HudEffects.drawLine(g, bankCx, bankCy, btx, bty, accent);
        g.fill(btx - 1, bty - 1, btx + 2, bty + 2, accent);

        g.fill(cx - 6, cy, cx - 2, cy + 1, accent);
        g.fill(cx + 2, cy, cx + 6, cy + 1, accent);
        g.fill(cx - 1, cy - 1, cx + 1, cy + 2, accent);

        g.drawString(font, "ATT", x + 4, y + 2, theme.dim, shadow);
        g.drawString(font, String.format(Locale.ROOT, "P%+.0f° B%+.0f°", pitch, bank), x + 24, y + 2, theme.text, shadow);
        return true;
    }

    private static boolean renderFactoryPulse(GuiGraphics g, Font font, DisplayTheme theme,
                                           StressometerHudState.PartWidget widget,
                                           List<StatsDisplayPanelBE.DataChannel> channels,
                                           int accent, int x, int y, int w, int h, long tick) {
        final StatsDisplayPanelBE.DataChannel primary = findPulsePrimaryChannel(widget, channels);
        if (primary == null) return false;
        final boolean shadow = widget.hasTextShadow();
        final String pid = primary.getId() == null ? "" : primary.getId();
        final boolean isRate = pid.contains("per_min") || pid.contains("per_sec") || pid.contains("per_hr") || pid.contains("throughput");
        final float rawValue = StressometerHudDataResolver.latestValue(primary, 0f);
        final float perMin;
        if (isRate) {
            if (pid.contains("per_sec")) perMin = rawValue * 60f;
            else if (pid.contains("per_hr")) perMin = rawValue / 60f;
            else perMin = rawValue;
        } else {
            perMin = (float)(Math.sqrt(Math.max(0f, rawValue)) * 10f);
        }
        final float perSec = perMin / 60f;
        final int health = perMin >= 60f ? SAFE_GREEN : (perMin > 12f ? ALERT_AMBER : DANGER_RED);

        g.drawString(font, "PULSE", x + 4, y + 3, theme.dim, shadow);
        final String rateText = isRate
                ? String.format(Locale.ROOT, "%.1f/min", perMin)
                : formatCompact(rawValue);
        g.drawString(font, rateText, x + w - 4 - font.width(rateText), y + 3, health, shadow);

        final int waveX = x + 4;
        final int waveY = y + 14;
        final int waveW = w - 8;
        final int waveH = Math.max(12, h - 28);
        if (waveW > 6 && waveH > 4) {
            g.fill(waveX, waveY, waveX + waveW, waveY + waveH, com.createstats.client.hud.HudAnim.withAlpha(0xFF000000, 90));
            final int beatPeriod = Math.max(20, Math.round(1200f / Math.max(1f, perMin)));
            com.createstats.client.hud.HudEffects.heartbeatWave(g, waveX, waveY, waveW, waveH, tick, com.createstats.client.hud.HudAnim.clamp01(perSec / 4f), beatPeriod, health);
            final float orbitPeriod = Math.max(20f, 200f / Math.max(0.1f, perSec));

            final float orbitT = (float) (tick % (double) orbitPeriod / orbitPeriod);
            final int orbitX, orbitY;
            int dist = (int) (orbitT * (2 * (waveW + waveH)));
            if (dist < waveW) { orbitX = waveX + dist; orbitY = waveY; }
            else if (dist < waveW + waveH) { orbitX = waveX + waveW; orbitY = waveY + (dist - waveW); }
            else if (dist < 2 * waveW + waveH) { orbitX = waveX + waveW - (dist - waveW - waveH); orbitY = waveY + waveH; }
            else { orbitX = waveX; orbitY = waveY + waveH - (dist - 2 * waveW - waveH); }
            g.fill(orbitX - 1, orbitY - 1, orbitX + 2, orbitY + 2, accent);
            final int markX = waveX + (int) (waveW * 0.75f);
            g.fill(markX, waveY, markX + 1, waveY + waveH, com.createstats.client.hud.HudAnim.withAlpha(theme.dim, 160));
        }

        final StatsDisplayPanelBE.DataChannel uniqueCh = StressometerHudDataResolver.findWidgetChannel(channels, "unique_types");
        final StatsDisplayPanelBE.DataChannel totalCh = StressometerHudDataResolver.findWidgetChannel(channels, "total_items");
        String footer = null;
        if (uniqueCh != null && uniqueCh != primary) {
            footer = "Types " + Math.round(StressometerHudDataResolver.latestValue(uniqueCh, 0f));
        } else if (totalCh != null && totalCh != primary) {
            footer = "Total " + Math.round(StressometerHudDataResolver.latestValue(totalCh, 0f));
        }
        if (footer != null) {
            g.drawString(font, footer, x + 4, y + h - 10, theme.border, shadow);
        }
        return true;
    }

    private static StatsDisplayPanelBE.DataChannel findPulsePrimaryChannel(StressometerHudState.PartWidget widget,
                                                                            List<StatsDisplayPanelBE.DataChannel> channels) {
        if (widget != null) {
            final String key = widget.getMetricKey();
            if (key != null && !key.isEmpty()) {
                final StatsDisplayPanelBE.DataChannel ch = StressometerHudDataResolver.findWidgetChannel(channels, key);
                if (ch != null) return ch;
            }
        }
        final String[] fallbacks = { "throughput", "items_per_min", "items_per_sec", "items_per_hr", "total_items" };
        for (final String key : fallbacks) {
            final StatsDisplayPanelBE.DataChannel ch = StressometerHudDataResolver.findWidgetChannel(channels, key);
            if (ch != null) return ch;
        }
        return null;
    }

    private static boolean renderFactoryBottleneck(GuiGraphics g, Font font, DisplayTheme theme,
                                                StressometerHudState.PartWidget widget,
                                                List<StatsDisplayPanelBE.DataChannel> channels,
                                                int accent, int x, int y, int w, int h, long tick) {
        final var targetCh = StressometerHudDataResolver.findWidgetChannel(channels, "target");
        if (targetCh == null) return false;
        final boolean shadow = widget.hasTextShadow();

        g.fill(x + 2, y + 2, x + w - 2, y + 14, com.createstats.client.hud.HudAnim.withAlpha(0xFFB8943A, 220));
        g.drawString(font, "BOTTLENECK", x + 6, y + 4, 0xFF1A1209, false);
        final float target = StressometerHudDataResolver.latestValue(targetCh, 0f);
        final float stock = StressometerHudDataResolver.latestValue(
                StressometerHudDataResolver.findWidgetChannel(channels, "stock"), 0f);
        final float promised = StressometerHudDataResolver.latestValue(
                StressometerHudDataResolver.findWidgetChannel(channels, "promised"), 0f);
        final float shortfall = Math.max(0f, target - (stock + promised));
        final int sevColor = shortfall > target * 0.5f ? DANGER_RED : (shortfall > 0f ? ALERT_AMBER : SAFE_GREEN);
        final int rowY = y + 16;
        g.fill(x + 4, rowY, x + w - 4, rowY + 11, com.createstats.client.hud.HudAnim.withAlpha(sevColor, 80));
        com.createstats.client.hud.HudEffects.warningRimFlash(g, x + 4, rowY, w - 8, 11, shortfall > 0f, sevColor);
        final String warn = shortfall > 0f
                ? String.format(Locale.ROOT, "SHORT %d / %d", Math.round(shortfall), Math.round(target))
                : "ON TARGET";
        g.drawString(font, warn, x + 8, rowY + 2, theme.text, shadow);
        final int ticketsY = rowY + 14;
        final String[] labels = { "Stock", "Target", "Promised" };
        final float[] values = { stock, target, promised };
        for (int i = 0; i < 3; i++) {
            final int ty = ticketsY + i * 9;
            if (ty + 9 > y + h - 2) break;
            final int dotColor = i == 0 ? sevColor : (i == 1 ? accent : SAFE_GREEN);
            g.fill(x + 6, ty + 2, x + 9, ty + 5, dotColor);
            g.drawString(font, labels[i], x + 12, ty, theme.dim, shadow);
            final String v = String.valueOf(Math.round(values[i]));
            g.drawString(font, v, x + w - 4 - font.width(v), ty, theme.text, shadow);
        }
        return true;
    }

    private static boolean renderShipCompass(GuiGraphics g, Font font, DisplayTheme theme,
                                          StressometerHudState.PartWidget widget,
                                          List<StatsDisplayPanelBE.DataChannel> channels,
                                          int accent, int x, int y, int w, int h, long tick) {
        final var yawCh = StressometerHudDataResolver.findWidgetChannel(channels, "attitude_yaw");
        if (yawCh == null) return false;
        final boolean shadow = widget.hasTextShadow();
        final float yaw = StressometerHudDataResolver.latestValue(yawCh, 0f);

        final var relCh = StressometerHudDataResolver.findWidgetChannel(channels, "relative_angle");
        final var absCh = StressometerHudDataResolver.findWidgetChannel(channels, "target_angle");
        final boolean hasTarget = relCh != null || absCh != null;
        final float targetBearing;
        if (relCh != null) {
            targetBearing = yaw + StressometerHudDataResolver.latestValue(relCh, 0f);
        } else if (absCh != null) {
            targetBearing = StressometerHudDataResolver.latestValue(absCh, yaw);
        } else {
            targetBearing = yaw;
        }

        final var distCh = StressometerHudDataResolver.findWidgetChannel(channels, "distance_to_target");
        final int dialTop = y + 12;
        final int dialBottom = y + h - 14;
        if (dialBottom - dialTop < 16) {
            g.drawString(font, String.format(Locale.ROOT, "HDG %.0f°", yaw), x + 4, y + 4, theme.text, shadow);
            return true;
        }
        final int cx = x + w / 2;
        final int cy = (dialTop + dialBottom) / 2;
        final int r = Math.min(w / 2 - 4, (dialBottom - dialTop) / 2 - 2);
        com.createstats.client.hud.HudEffects.drawCircleOutline(g, cx, cy, r + 1, theme.border);
        com.createstats.client.hud.HudEffects.drawCircleOutline(g, cx, cy, r, com.createstats.client.hud.HudAnim.withAlpha(theme.bg, 230));
        final String[] cards = { "N", "E", "S", "W" };
        for (int i = 0; i < 4; i++) {
            final double ang = Math.toRadians(i * 90 - yaw - 90);
            final int lx = cx + (int) (Math.cos(ang) * (r - 5)) - 2;
            final int ly = cy + (int) (Math.sin(ang) * (r - 5)) - 4;
            final int color = i == 0 ? accent : theme.text;
            g.drawString(font, cards[i], lx, ly, color, false);
        }
        for (int t = 0; t < 360; t += 30) {
            final double ang = Math.toRadians(t - yaw - 90);
            final int x1 = cx + (int) (Math.cos(ang) * r);
            final int y1 = cy + (int) (Math.sin(ang) * r);
            final int x0 = cx + (int) (Math.cos(ang) * (r - (t % 90 == 0 ? 4 : 2)));
            final int y0 = cy + (int) (Math.sin(ang) * (r - (t % 90 == 0 ? 4 : 2)));
            com.createstats.client.hud.HudEffects.drawLine(g, x0, y0, x1, y1, theme.border);
        }
        if (hasTarget) {
            final double tgtRad = Math.toRadians((targetBearing - yaw) - 90);
            final int tx = cx + (int) (Math.cos(tgtRad) * (r - 6));
            final int ty = cy + (int) (Math.sin(tgtRad) * (r - 6));
            com.createstats.client.hud.HudEffects.drawLine(g, cx, cy, tx, ty, com.createstats.client.hud.HudAnim.withAlpha(accent, 180));
        }

        g.fill(cx, dialTop, cx + 1, dialTop + 4, accent);

        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, theme.text);
        if (distCh != null) {
            final float dist = StressometerHudDataResolver.latestValue(distCh, 0f);
            final String distTxt = String.format(Locale.ROOT, "%.1fm", dist);
            g.drawString(font, distTxt, x + w / 2 - font.width(distTxt) / 2, y + h - 10, theme.text, shadow);
        }
        final String hdg = String.format(Locale.ROOT, "%03d°", ((int) yaw % 360 + 360) % 360);
        g.drawString(font, hdg, x + 4, y + 2, theme.text, shadow);
        return true;
    }

    private static boolean renderShipDocking(GuiGraphics g, Font font, DisplayTheme theme,
                                          StressometerHudState.PartWidget widget,
                                          List<StatsDisplayPanelBE.DataChannel> channels,
                                          int accent, int x, int y, int w, int h, long tick) {
        final var distCh = StressometerHudDataResolver.findWidgetChannel(channels, "closest_pair_distance");
        final var pairCh = StressometerHudDataResolver.findWidgetChannel(channels, "paired");
        if (distCh == null && pairCh == null) return false;
        final boolean shadow = widget.hasTextShadow();
        final float distance = StressometerHudDataResolver.latestValue(distCh, 999f);
        final boolean paired = StressometerHudDataResolver.latestValue(pairCh, 0f) > 0.5f;
        g.drawString(font, "PROX", x + 4, y + 3, theme.dim, shadow);
        final String distTxt = String.format(Locale.ROOT, "%.1fm", distance);
        g.drawString(font, distTxt, x + w - 4 - font.width(distTxt), y + 3, theme.text, shadow);
        final int sx = x + 4;
        final int sy = y + 14;
        final int sw = w - 8;
        final int sh = h - 18;
        if (sw < 16 || sh < 16) return true;
        g.fill(sx, sy, sx + sw, sy + sh, com.createstats.client.hud.HudAnim.withAlpha(0xFF000000, 110));
        final int cx = sx + sw / 2;
        final int cy = sy + sh / 2;
        final int maxR = Math.min(sw, sh) / 2 - 2;
        final int sev = paired ? (distance < 4f ? DANGER_RED : (distance < 16f ? ALERT_AMBER : SAFE_GREEN)) : theme.dim;
        com.createstats.client.hud.HudEffects.expandingRings(g, cx, cy, maxR, tick, 3, 32, sev);
        for (int i = 0; i < 3; i++) {
            final int cyOff = cy - maxR + 4 + i * 4;
            final int span = 6 + i * 2;
            final int alpha = 220 - i * 60;
            com.createstats.client.hud.HudEffects.drawLine(g, cx - span, cyOff, cx, cyOff + 3, com.createstats.client.hud.HudAnim.withAlpha(sev, alpha));
            com.createstats.client.hud.HudEffects.drawLine(g, cx, cyOff + 3, cx + span, cyOff, com.createstats.client.hud.HudAnim.withAlpha(sev, alpha));
        }
        final float distNorm = com.createstats.client.hud.HudAnim.clamp01(distance / 32f);
        final int tr = Math.max(2, (int) (distNorm * maxR));
        g.fill(cx + tr - 1, cy - 1, cx + tr + 2, cy + 2, sev);
        if (paired && distance < 4f) {
            com.createstats.client.hud.HudEffects.warningRimFlash(g, sx, sy, sw, sh, true, DANGER_RED);
        }
        return true;
    }

    private static boolean renderPlatformOccupancy(GuiGraphics g, Font font, DisplayTheme theme,
                                                StressometerHudState.PartWidget widget,
                                                List<StatsDisplayPanelBE.DataChannel> channels,
                                                int accent, int x, int y, int w, int h, long tick) {
        final var statusCh = StressometerHudDataResolver.findWidgetChannel(channels, "station_status");
        final var waitCh = StressometerHudDataResolver.findWidgetChannel(channels, "signal_wait");
        if (statusCh == null && waitCh == null) return false;
        final boolean shadow = widget.hasTextShadow();
        final float stationStatus = StressometerHudDataResolver.latestValue(statusCh, 0f);
        final float signalWait = StressometerHudDataResolver.latestValue(waitCh, 0f);
        final String state;
        if (signalWait > 0.5f) state = "blocked";
        else if (stationStatus >= 1.5f) state = "clear";
        else if (stationStatus >= 0.5f) state = "waiting";
        else state = "idle";
        g.drawString(font, "SIGNAL BOX", x + 4, y + 3, theme.dim, shadow);
        final int lampX = x + w - 18;
        final int lampY = y + 14;
        final int lampSize = 8;
        final int gap = 2;
        final int red = com.createstats.client.hud.HudEffects.signalLampColor(state.equals("blocked") ? "blocked" : "off", SAFE_GREEN, ALERT_AMBER, DANGER_RED);
        final int amber = com.createstats.client.hud.HudEffects.signalLampColor(state.equals("waiting") ? "waiting" : "off", SAFE_GREEN, ALERT_AMBER, DANGER_RED);
        final int green = com.createstats.client.hud.HudEffects.signalLampColor(state.equals("clear") ? "clear" : "off", SAFE_GREEN, ALERT_AMBER, DANGER_RED);
        com.createstats.client.hud.HudEffects.drawCircleOutline(g, lampX + lampSize / 2, lampY + lampSize / 2, lampSize / 2, theme.border);
        g.fill(lampX + 1, lampY + 1, lampX + lampSize - 1, lampY + lampSize - 1, red);
        com.createstats.client.hud.HudEffects.drawCircleOutline(g, lampX + lampSize / 2, lampY + lampSize + gap + lampSize / 2, lampSize / 2, theme.border);
        g.fill(lampX + 1, lampY + lampSize + gap + 1, lampX + lampSize - 1, lampY + 2 * lampSize + gap - 1, amber);
        com.createstats.client.hud.HudEffects.drawCircleOutline(g, lampX + lampSize / 2, lampY + 2 * (lampSize + gap) + lampSize / 2, lampSize / 2, theme.border);
        g.fill(lampX + 1, lampY + 2 * (lampSize + gap) + 1, lampX + lampSize - 1, lampY + 3 * lampSize + 2 * gap - 1, green);
        final int trackX = x + 4;
        final int trackTop = y + 14;
        final int trackW = w - 28;
        final int trackH = Math.max(6, (h - 24) / 4);
        if (trackW < 12) return true;
        for (int i = 0; i < 4; i++) {
            final int by = trackTop + i * (trackH + 1);
            if (by + trackH > y + h - 4) break;
            final boolean inbound = (i == 0 && state.equals("clear")) || (i == 1 && state.equals("waiting"));
            com.createstats.client.hud.HudEffects.trackPulse(g, trackX, by, trackW, trackH, tick, inbound,
                    com.createstats.client.hud.HudAnim.withAlpha(0xFF000000, 140),
                    com.createstats.client.hud.HudAnim.withAlpha(accent, 200));
            g.drawString(font, "B" + (i + 1), trackX + 2, by + (trackH - 8) / 2, theme.dim, false);
        }
        final String banner = switch (state) {
            case "blocked" -> "WAITING ON SIGNAL";
            case "waiting" -> "BOARDING";
            case "clear" -> "CLEAR";
            default -> "IDLE";
        };
        g.drawString(font, banner, x + 4, y + h - 10, accent, shadow);
        return true;
    }

    private static boolean renderProductionStructuredWidget(GuiGraphics g, Font font, DisplayTheme theme,
                                                            StressometerHudState.PartWidget widget,
                                                            List<StatsDisplayPanelBE.DataChannel> channels,
                                                            int x, int y, int w, int h) {
        if (widget.getPart() != StressometerHudState.Part.PRODUCTION_CONTENT) {
            return false;
        }
        final List<StatsDisplayPanelBE.DataChannel> items = collectMetricRows(channels, MetricType.ITEM_COUNT, "_item_", "auto_item_");
        if (!items.isEmpty()) {
            renderChannelList(g, font, theme, widget, items, x, y, w, h);
            return true;
        }
        final List<StatsDisplayPanelBE.DataChannel> fluids = collectMetricRows(channels, MetricType.FLUID_MB, "_fluid_", "auto_fluid_", "fluid_amount");
        if (!fluids.isEmpty()) {
            return renderProductionFluidWidget(g, font, theme, widget, channels, fluids, x, y, w, h);
        }
        final boolean shadow = widget.hasTextShadow();
        drawCentered(g, font, "No content", x + w / 2, y + Math.max(4, (h - font.lineHeight) / 2), theme.dim, shadow);
        return true;
    }

    private static boolean renderProductionFluidWidget(GuiGraphics g, Font font, DisplayTheme theme,
                                                       StressometerHudState.PartWidget widget,
                                                       List<StatsDisplayPanelBE.DataChannel> channels,
                                                       List<StatsDisplayPanelBE.DataChannel> rows,
                                                       int x, int y, int w, int h) {
        final boolean shadow = widget.hasTextShadow();
        final boolean compact = w < 170 || h < 72;
        final int pad = Math.max(3, Math.min(6, Math.min(w, h) / 7));
        final StatsDisplayPanelBE.DataChannel primary = rows.get(0);
        final float maxVal = maxMetricValue(rows);
        final float primaryVal = StressometerHudDataResolver.latestValue(primary, 0f);
        final int primaryColor = contentColor(primary.getId());

        if (compact) {
            drawCentered(g, font, truncate(font, widgetTitle(widget), w - pad * 2), x + w / 2, y + pad, theme.dim, shadow);
            final int barY = y + pad + font.lineHeight + 3;
            final int barH = Math.max(6, h - (barY - y) - pad - font.lineHeight - 2);
            final int barX = x + pad;
            final int barW = w - pad * 2;
            g.fill(barX, barY, barX + barW, barY + barH, withAlpha(0xFF000000, 110));
            final float frac = maxVal > 0f ? Math.min(1f, primaryVal / maxVal) : 0f;
            final int fillW = Math.max(0, Math.round(barW * frac));
            g.fill(barX, barY, barX + fillW, barY + barH, primaryColor);
            g.fill(barX, barY, barX + barW, barY + 1, withAlpha(lighten(primaryColor, 0.3f), 200));
            final String label = truncate(font, cleanContentLabel(primary), barW - 6);
            drawText(g, font, label, barX + 3, barY + (barH - font.lineHeight) / 2, theme.text, shadow);
            final String val = formatChannelValue(primary);
            drawText(g, font, val, barX + barW - font.width(val) - 3, barY + (barH - font.lineHeight) / 2, theme.accent, shadow);
            return true;
        }

        drawCentered(g, font, truncate(font, widgetTitle(widget), w - pad * 2), x + w / 2, y + pad, theme.dim, shadow);
        g.fill(x + 6, y + pad + font.lineHeight + 1, x + w - 6, y + pad + font.lineHeight + 2, dim(theme.border, 0.55f));

        final int contentY = y + pad + font.lineHeight + 4;
        final int badgeReserve = 14;
        final int tankH = Math.max(28, h - (contentY - y) - pad - badgeReserve - 4);
        final int tankW = Math.max(20, Math.min(48, w / 4));
        final int tankX = x + pad;
        final int tankY = contentY;

        g.fill(tankX, tankY, tankX + tankW, tankY + tankH, withAlpha(0xFF000000, 140));
        g.fill(tankX, tankY, tankX + tankW, tankY + 1, withAlpha(theme.border, 200));
        g.fill(tankX, tankY + tankH - 1, tankX + tankW, tankY + tankH, withAlpha(theme.border, 200));
        g.fill(tankX, tankY, tankX + 1, tankY + tankH, withAlpha(theme.border, 200));
        g.fill(tankX + tankW - 1, tankY, tankX + tankW, tankY + tankH, withAlpha(theme.border, 200));

        final float frac = maxVal > 0f ? Math.min(1f, primaryVal / maxVal) : 0f;
        final int fillH = Math.max(0, Math.round((tankH - 2) * frac));
        final int fluidTop = tankY + tankH - 1 - fillH;
        if (fillH > 0) {
            g.fill(tankX + 1, fluidTop, tankX + tankW - 1, tankY + tankH - 1, primaryColor);

            final long tick = (System.currentTimeMillis() / 50L);
            final int waveOff = (int) ((tick / 2) % Math.max(1, tankW));
            for (int wx = 1; wx < tankW - 1; wx++) {
                final int wy = (int) (Math.sin((wx + waveOff) * 0.5) * 1.0);
                final int yy = fluidTop + wy;
                g.fill(tankX + wx, yy, tankX + wx + 1, yy + 1, withAlpha(lighten(primaryColor, 0.4f), 220));
            }

            final int bubbles = 3;
            for (int i = 0; i < bubbles; i++) {
                final float phase = ((tick + i * 30L) % 120L) / 120f;
                final int bx = tankX + 2 + ((i * 7 + (int)(tick / 8)) % Math.max(1, tankW - 4));
                final int by = (tankY + tankH - 2) - (int)(phase * Math.max(1, fillH - 2));
                if (by > fluidTop + 1 && by < tankY + tankH - 1) {
                    g.fill(bx, by, bx + 1, by + 1, withAlpha(lighten(primaryColor, 0.55f), 200));
                }
            }
        }

        final int infoX = tankX + tankW + 6;
        final int infoW = x + w - pad - infoX;
        if (infoW > 20) {
            int row = contentY;
            final String label = truncate(font, cleanContentLabel(primary), infoW);
            drawText(g, font, label, infoX, row, theme.text, shadow);
            row += font.lineHeight + 1;
            final String val = formatChannelValue(primary);
            drawText(g, font, truncate(font, val, infoW), infoX, row, theme.accent, shadow);
            row += font.lineHeight + 1;
            if (maxVal > 0f) {
                final String pct = String.format(Locale.ROOT, "%.0f%%", Math.min(100f, frac * 100f));
                drawText(g, font, pct, infoX, row, theme.dim, shadow);
                row += font.lineHeight + 2;
            }

            int shownSecondary = 0;
            for (int i = 1; i < rows.size() && shownSecondary < 3; i++) {
                if (row + font.lineHeight > tankY + tankH) break;
                final StatsDisplayPanelBE.DataChannel sec = rows.get(i);
                final String secLabel = truncate(font, cleanContentLabel(sec), infoW - 30);
                final String secVal = formatChannelValue(sec);
                drawText(g, font, secLabel, infoX, row, theme.text, shadow);
                drawText(g, font, secVal, infoX + infoW - font.width(secVal), row, contentColor(sec.getId()), shadow);
                row += font.lineHeight + 1;
                shownSecondary++;
            }
        }

        int badgeX = x + pad;
        final int badgeY = y + h - pad - (font.lineHeight + 2);
        if (StressometerHudDataResolver.findWidgetChannel(channels, "boiler_heat") != null) {
            drawProductionBadge(g, font, badgeX, badgeY, "HEAT", DANGER_RED);
            badgeX += font.width("HEAT") + 12;
        }
        if (StressometerHudDataResolver.findWidgetChannel(channels, "water_supply") != null) {
            drawProductionBadge(g, font, badgeX, badgeY, "WTR", 0xFF2E8FE0);
            badgeX += font.width("WTR") + 12;
        }
        if (StressometerHudDataResolver.findWidgetChannel(channels, "engines") != null) {
            drawProductionBadge(g, font, badgeX, badgeY, "ENG", ALERT_AMBER);
            badgeX += font.width("ENG") + 12;
        }
        if (StressometerHudDataResolver.findWidgetChannel(channels, "whistles") != null) {
            drawProductionBadge(g, font, badgeX, badgeY, "WHS", SAFE_GREEN);
        }
        return true;
    }

    private static void drawProductionBadge(GuiGraphics g, Font font, int x, int y, String label, int color) {
        final int wp = font.width(label) + 8;
        final int hp = font.lineHeight + 4;
        g.fill(x, y, x + wp, y + hp, withAlpha(color, 80));
        g.fill(x, y, x + wp, y + 1, withAlpha(lighten(color, 0.3f), 200));
        g.fill(x, y + hp - 1, x + wp, y + hp, withAlpha(color, 200));
        g.drawString(font, label, x + 4, y + 2, 0xFFFFFFFF, false);
    }

    private static List<StatsDisplayPanelBE.DataChannel> collectMetricRows(List<StatsDisplayPanelBE.DataChannel> channels,
                                                                            MetricType type, String... idFragments) {
        final List<StatsDisplayPanelBE.DataChannel> out = new ArrayList<>();
        for (final StatsDisplayPanelBE.DataChannel ch : channels) {
            if (ch == null) continue;
            if (ch.getStore() == null || ch.getStore().getMetricType() != type) continue;
            final String id = ch.getId() == null ? "" : ch.getId();
            if (id.contains("total_items") || id.contains("unique_types")
                    || id.contains("slot_capacity") || id.contains("tank_capacity")) continue;
            if (!matchesAnyIdFragment(id, idFragments)) continue;
            if (StressometerHudDataResolver.latestValue(ch, 0f) <= 0f) continue;
            out.add(ch);
        }
        out.sort((a, b) -> {
            final float va = StressometerHudDataResolver.latestValue(a, 0f);
            final float vb = StressometerHudDataResolver.latestValue(b, 0f);
            if (va != vb) return Float.compare(vb, va);
            final String la = a.getLabel() == null ? "" : a.getLabel();
            final String lb = b.getLabel() == null ? "" : b.getLabel();
            return la.compareToIgnoreCase(lb);
        });
        return out;
    }

    private static boolean matchesAnyIdFragment(String id, String[] fragments) {
        if (id == null || fragments == null) return false;
        for (final String f : fragments) {
            if (f != null && !f.isEmpty() && id.contains(f)) return true;
        }
        return false;
    }

    private static float maxMetricValue(List<StatsDisplayPanelBE.DataChannel> rows) {
        if (rows == null || rows.isEmpty()) return 1f;
        final float v = StressometerHudDataResolver.latestValue(rows.get(0), 0f);
        return v > 0f ? v : 1f;
    }

    private static String cleanContentLabel(StatsDisplayPanelBE.DataChannel ch) {
        if (ch == null) return "";
        final String raw = ch.getLabel();
        return cleanContentLabel(raw != null ? raw : ch.getId());
    }

    private static String cleanContentLabel(String s) {
        if (s == null) return "";
        String out = s;
        final String[] prefixes = { "Items ", "Items: ", "Items • ", "Item ", "Fluids ", "Fluids: ", "Fluids • ", "Fluid " };
        for (final String p : prefixes) {
            if (out.startsWith(p)) { out = out.substring(p.length()); break; }
        }
        if (out.endsWith(" (mB)")) out = out.substring(0, out.length() - 5);
        if (out.endsWith("(mB)")) out = out.substring(0, out.length() - 4);
        return out.trim();
    }

    private static String formatChannelValue(StatsDisplayPanelBE.DataChannel ch) {
        final float v = StressometerHudDataResolver.latestValue(ch, 0f);
        final String base = formatCompact(v);
        if (ch == null || ch.getStore() == null) return base;
        final MetricType t = ch.getStore().getMetricType();
        if (t == MetricType.FLUID_MB) return base + " mB";
        return base;
    }

    private static int contentColor(String channelId) {
        if (channelId == null || channelId.isEmpty()) return 0xFF6CC1E5;
        final String tail = extractMetricTail(channelId);
        if (tail == null || tail.isEmpty()) return 0xFF6CC1E5;
        final String fluidId = restoreFluidId(tail);
        try {
            return FluidPalette.colorForFluidId(fluidId);
        } catch (Throwable t) {
            return 0xFF6CC1E5;
        }
    }

    private static String extractMetricTail(String id) {
        if (id == null) return "";
        final String[] markers = { "_auto_item_", "_auto_fluid_", "_item_", "_fluid_" };
        int best = -1;
        int bestEnd = 0;
        for (final String m : markers) {
            int idx = id.lastIndexOf(m);
            if (idx > best) { best = idx; bestEnd = idx + m.length(); }
        }
        if (best < 0) return "";
        return id.substring(bestEnd);
    }

    private static String restoreFluidId(String tail) {
        if (tail == null || tail.isEmpty()) return "";
        final int us = tail.indexOf('_');
        if (us < 0) return tail;
        return tail.substring(0, us) + ":" + tail.substring(us + 1);
    }

    private static int lighten(int argb, float amount) {
        final int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        r = Math.min(255, Math.round(r + (255 - r) * amount));
        g = Math.min(255, Math.round(g + (255 - g) * amount));
        b = Math.min(255, Math.round(b + (255 - b) * amount));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int withAlpha(int argb, int a) {
        return ((a & 0xFF) << 24) | (argb & 0x00FFFFFF);
    }
}
