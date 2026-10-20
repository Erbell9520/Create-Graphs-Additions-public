package com.createstats.renderer;

import com.createstats.blockentity.SourceConfig;
import com.createstats.display.CustomModuleBinding;
import com.createstats.display.CustomModuleInstance;
import com.createstats.display.CustomScreenModuleCatalog;
import com.createstats.display.CustomScreenModuleContract;
import com.createstats.display.CustomScreenSourceCapability;
import com.createstats.display.CustomScreenSourceFamilyCatalog;
import com.createstats.display.DisplayTheme;
import com.createstats.display.SourceFamilyDefinition;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CustomScreenModuleRenderer {
    private CustomScreenModuleRenderer() {
    }

    public static void renderStudio(GuiGraphics graphics,
                                    Font font,
                                    int x,
                                    int y,
                                    int width,
                                    int height,
                                    DisplayTheme theme,
                                    CustomModuleInstance module,
                                    Set<String> availableSourceIds,
                                    boolean selected) {
        final CustomScreenModuleContract contract = CustomScreenModuleCatalog.getContract(module != null ? module.getModuleType() : "");
        if (graphics == null || font == null || theme == null || module == null) {
            return;
        }
        if (contract == null) {
            renderFallbackCard(graphics, font, x, y, width, height, theme, "Unknown Module", new BindingSummary(BindingStatus.UNRESOLVED, "Repair", module.getModuleType(), 0xFFC92A2A), selected);
            return;
        }
        final BindingSummary binding = summarizeBinding(contract, module, availableSourceIds);
        switch (contract.getRendererStyle()) {
            case TITLE -> renderTitle(graphics, font, x, y, width, height, theme, module, selected, 1.0f);
            case SUBTITLE -> renderTitle(graphics, font, x, y, width, height, theme, module, selected, 0.75f);
            case DIVIDER -> renderDivider(graphics, x, y, width, height, theme, selected);
            case BADGE -> renderBadge(graphics, font, x, y, width, height, theme, module, binding, selected);
            case LOGO -> renderLogo(graphics, font, x, y, width, height, theme, module, selected);
            case BIG_VALUE -> renderBigValue(graphics, font, x, y, width, height, theme, module, binding, selected);
            case ANALOG_GAUGE -> renderGauge(graphics, font, x, y, width, height, theme, module, binding, selected);
            case STATUS_STRIP -> renderStatusStrip(graphics, font, x, y, width, height, theme, module, binding, selected);
            case DUAL_CARD -> renderDualCard(graphics, font, x, y, width, height, theme, module, binding, selected);
            case TRIPLE_CARD -> renderTripleCard(graphics, font, x, y, width, height, theme, module, binding, selected);
            case CHANNEL_LIST -> renderChannelList(graphics, font, x, y, width, height, theme, module, binding, selected);
            case MACRO_SCENE -> renderMacroScene(graphics, font, x, y, width, height, theme, contract, module, binding, selected);
            case CARD -> renderFallbackCard(graphics, font, x, y, width, height, theme, contract.getDisplayName(), binding, selected);
        }
    }

    public static void renderPanel(NativeImage image,
                                   int x,
                                   int y,
                                   int width,
                                   int height,
                                   DisplayTheme theme,
                                   CustomModuleInstance module,
                                   Set<String> availableSourceIds) {
        if (image == null || theme == null || module == null) {
            return;
        }
        fillRect(image, x, y, width, height, theme.graphBg);
        drawRect(image, x, y, width, height, theme.border);
    }

    public static BindingSummary summarizeBinding(CustomScreenModuleContract contract,
                                                  CustomModuleInstance module,
                                                  Set<String> availableSourceIds) {
        return summarizeBinding(contract, module, availableSourceIds, Map.of());
    }

    public static BindingSummary summarizeBinding(CustomScreenModuleContract contract,
                                                  CustomModuleInstance module,
                                                  Set<String> availableSourceIds,
                                                  Map<String, SourceConfig> sourceRegistry) {
        if (contract == null || module == null || contract.getBindingFields().isEmpty()) {
            return new BindingSummary(BindingStatus.NONE, "Decorative", "No binding required", 0xFF8A8A8A);
        }
        for (String field : contract.getBindingFields()) {
            final CustomModuleBinding binding = module.resolveBinding(field);
            final BindingSummary summary = summarizeBindingField(binding, availableSourceIds, sourceRegistry);
            if (summary.status() != BindingStatus.NONE) {
                return summary;
            }
        }
        return new BindingSummary(BindingStatus.UNRESOLVED, "Unbound", "Choose a source or slot", 0xFFC92A2A);
    }

    public static BindingSummary summarizeBindingField(CustomModuleBinding binding, Set<String> availableSourceIds) {
        return summarizeBindingField(binding, availableSourceIds, Map.of());
    }

    public static BindingSummary summarizeBindingField(CustomModuleBinding binding,
                                                       Set<String> availableSourceIds,
                                                       Map<String, SourceConfig> sourceRegistry) {
        if (binding == null) {
            return new BindingSummary(BindingStatus.NONE, "Decorative", "No binding required", 0xFF8A8A8A);
        }
        if (binding.getMode() == CustomModuleBinding.Mode.EXPLICIT_SOURCE) {
            final SourceConfig config = sourceRegistry != null ? sourceRegistry.get(binding.getSourceId()) : null;
            if (config != null
                    && binding.getSourceTypeFilter() != null
                    && !binding.getSourceTypeFilter().isEmpty()
                    && !CustomScreenSourceFamilyCatalog.matchesSourceTypeFilter(config, binding.getSourceTypeFilter())) {
                return new BindingSummary(BindingStatus.INCOMPATIBLE, "Wrong Family", wrongFamilyDetail(config, binding.getSourceTypeFilter()), 0xFFC92A2A);
            }
            final boolean present = binding.getSourceId() != null
                    && !binding.getSourceId().isEmpty()
                    && availableSourceIds != null
                    && availableSourceIds.contains(binding.getSourceId());
            if (present) {
                return new BindingSummary(BindingStatus.RESOLVED, "Direct", binding.getSourceId(), 0xFF37B24D);
            }
            if (binding.getSourceId() != null && !binding.getSourceId().isEmpty()) {
                return new BindingSummary(BindingStatus.OFFLINE, "Offline", binding.getSourceId(), 0xFFB36B00);
            }
            return new BindingSummary(BindingStatus.UNRESOLVED, "Source", "Choose a source", 0xFFC92A2A);
        }
        if (binding.getMode() == CustomModuleBinding.Mode.SEMANTIC_SLOT && binding.getSlot() != null && !binding.getSlot().isEmpty()) {
            return new BindingSummary(BindingStatus.FALLBACK, "Slot", binding.getSlot(), 0xFFF08C00);
        }
        return new BindingSummary(BindingStatus.UNRESOLVED, "Unbound", "Choose a slot", 0xFFC92A2A);
    }

    public static boolean isRepairStatus(BindingStatus status) {
        return status == BindingStatus.INCOMPATIBLE
                || status == BindingStatus.OFFLINE
                || status == BindingStatus.UNRESOLVED;
    }

    public static int repairPriority(BindingStatus status) {
        if (status == null) {
            return 99;
        }
        return switch (status) {
            case INCOMPATIBLE -> 0;
            case OFFLINE -> 1;
            case UNRESOLVED -> 2;
            default -> 99;
        };
    }

    private static String wrongFamilyDetail(SourceConfig config, String sourceTypeFilter) {
        final SourceFamilyDefinition actualFamily = CustomScreenSourceFamilyCatalog.familyForSource(config);
        final SourceFamilyDefinition expectedFamily = CustomScreenSourceFamilyCatalog.familyForSourceType(sourceTypeFilter);
        final String actualLabel = CustomScreenSourceFamilyCatalog.GENERIC_UNKNOWN.equals(actualFamily.getFamilyId())
                ? config.getSourceType()
                : actualFamily.getDisplayName();
        final String expectedLabel = CustomScreenSourceFamilyCatalog.GENERIC_UNKNOWN.equals(expectedFamily.getFamilyId())
                ? sourceTypeFilter
                : expectedFamily.getDisplayName();
        return actualLabel + " != " + expectedLabel;
    }

    private static void renderTitle(GuiGraphics graphics,
                                    Font font,
                                    int x,
                                    int y,
                                    int width,
                                    int height,
                                    DisplayTheme theme,
                                    CustomModuleInstance module,
                                    boolean selected,
                                    float scale) {
        final String text = module.getModuleConfig().getString("text");
        if (selected) {
            drawSelection(graphics, x, y, width, height, theme.accent);
        }
        drawScaledString(graphics, font, text.isEmpty() ? "Untitled" : text, x + 4, y + 3, scale, theme.text);
    }

    private static void renderDivider(GuiGraphics graphics, int x, int y, int width, int height, DisplayTheme theme, boolean selected) {
        final int centerY = y + Math.max(1, height / 2);
        graphics.fill(x + 2, centerY, x + width - 2, centerY + 1, theme.border);
        graphics.fill(x + 2, centerY + 1, x + width - 2, centerY + 2, withAlpha(theme.accent, 120));
        if (selected) {
            drawSelection(graphics, x, y, width, height, theme.accent);
        }
    }

    private static void renderBadge(GuiGraphics graphics,
                                    Font font,
                                    int x,
                                    int y,
                                    int width,
                                    int height,
                                    DisplayTheme theme,
                                    CustomModuleInstance module,
                                    BindingSummary binding,
                                    boolean selected) {
        final String text = module.getModuleConfig().getString("text");
        drawPill(graphics, x + 2, y + 3, width - 4, Math.max(10, height - 6), binding.accentColor(), theme.bg);
        drawCentered(graphics, font, text.isEmpty() ? "Badge" : text, x, y + Math.max(2, (height - font.lineHeight) / 2), width, theme.text);
        if (selected) {
            drawSelection(graphics, x, y, width, height, theme.accent);
        }
    }

    private static void renderLogo(GuiGraphics graphics,
                                   Font font,
                                   int x,
                                   int y,
                                   int width,
                                   int height,
                                   DisplayTheme theme,
                                   CustomModuleInstance module,
                                   boolean selected) {
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, withAlpha(theme.tabSel, 210));
        drawRect(graphics, x, y, width, height, theme.border);
        drawCenteredScaled(graphics, font, module.getModuleConfig().getString("text").isEmpty() ? "SD" : module.getModuleConfig().getString("text"), x, y + Math.max(1, height / 4), width, 1.2f, theme.accent);
        if (selected) {
            drawSelection(graphics, x, y, width, height, theme.accent);
        }
    }

    private static void renderBigValue(GuiGraphics graphics,
                                       Font font,
                                       int x,
                                       int y,
                                       int width,
                                       int height,
                                       DisplayTheme theme,
                                       CustomModuleInstance module,
                                       BindingSummary binding,
                                       boolean selected) {
        final String label = textOr(module.getModuleConfig().getString("label"), "Metric");
        final String value = textOr(module.getModuleConfig().getString("value"), "000");
        final String suffix = module.getModuleConfig().getString("suffix");
        drawModuleCard(graphics, x, y, width, height, theme, selected, binding.accentColor());
        graphics.drawString(font, label, x + 4, y + 4, theme.dim, false);
        drawCenteredScaled(graphics, font, value, x, y + 14, width, 1.35f, theme.text);
        if (!suffix.isEmpty()) {
            drawCentered(graphics, font, suffix, x, y + height - 12, width, theme.dim);
        }
        drawBindingBadge(graphics, font, x + 4, y + height - 12, binding);
    }

    private static void renderGauge(GuiGraphics graphics,
                                    Font font,
                                    int x,
                                    int y,
                                    int width,
                                    int height,
                                    DisplayTheme theme,
                                    CustomModuleInstance module,
                                    BindingSummary binding,
                                    boolean selected) {
        final String mode = gaugeMode(module);
        final float percent = previewPercent(module, mode);
        final int accent = gaugeAccent(mode, binding.accentColor(), theme);
        drawModuleCard(graphics, x, y, width, height, theme, selected, binding.accentColor());
        final String label = textOr(module.getModuleConfig().getString("label"), "Gauge");
        final String value = textOr(module.getModuleConfig().getString("value"), "78%");
        graphics.drawString(font, label, x + 5, y + 5, theme.dim, false);
        drawPill(graphics, x + width - 48, y + 5, 42, 10, withAlpha(accent, 205), theme.bg);
        drawCentered(graphics, font, modeLabel(mode), x + width - 48, y + 7, 42, 0xFFFFFFFF);
        if ("tank".equals(mode)) {
            renderStudioTankGauge(graphics, font, x, y, width, height, theme, value, percent, accent);
        }
        else if ("heat".equals(mode)) {
            renderStudioHeatGauge(graphics, font, x, y, width, height, theme, value, percent, accent);
        }
        else if ("altitude".equals(mode)) {
            renderStudioAltitudeGauge(graphics, font, x, y, width, height, theme, value, percent, accent);
        }
        else if ("accumulator".equals(mode)) {
            renderStudioAccumulatorGauge(graphics, font, x, y, width, height, theme, value, percent, accent);
        }
        else if ("horizon".equals(mode)) {
            renderStudioHorizonGauge(graphics, font, x, y, width, height, theme, value, percent, accent);
        }
        else if ("rpm".equals(mode) || "stress".equals(mode)) {
            renderStudioTachGauge(graphics, font, x, y, width, height, theme, value, percent, accent, "stress".equals(mode));
        }
        else {
            renderStudioDialGauge(graphics, font, x, y, width, height, theme, value, percent, accent);
        }
        drawBindingBadge(graphics, font, x + 5, y + height - 13, binding);
    }

    private static void renderStudioDialGauge(GuiGraphics graphics,
                                              Font font,
                                              int x,
                                              int y,
                                              int width,
                                              int height,
                                              DisplayTheme theme,
                                              String value,
                                              float percent,
                                              int accent) {
        final double startDeg = 205.0D;
        final double endDeg = 335.0D;
        final double sweepDeg = endDeg - startDeg;
        final int cx = x + width / 2;
        final int cy = y + Math.max(26, height - 13);
        final int radius = Math.max(12, Math.min(width / 2 - 9, height - 26));
        drawArcDots(graphics, cx, cy, radius + 2, startDeg, endDeg, withAlpha(theme.border, 220), 2);
        drawArcDots(graphics, cx, cy, radius + 1, startDeg, endDeg, withAlpha(theme.text, 70), 1);
        drawArcDots(graphics, cx, cy, radius - 4, startDeg, endDeg, withAlpha(theme.tabBg, 180), 4);
        drawArcDots(graphics, cx, cy, radius, startDeg + sweepDeg * 0.82D, endDeg, withAlpha(theme.warn, 160), 2);
        drawStudioDialTickMarks(graphics, cx, cy, radius, startDeg, sweepDeg, theme);
        drawArcDots(graphics, cx, cy, radius - 1, startDeg, startDeg + sweepDeg * percent, accent, 2);
        drawArcDots(graphics, cx, cy, radius - 5, startDeg, startDeg + sweepDeg * percent, withAlpha(accent, 120), 1);
        final double shimmerPhase = (System.currentTimeMillis() % 2400L) / 2400.0D;
        final double shimmerStart = startDeg + sweepDeg * 0.08D + sweepDeg * 0.48D * shimmerPhase;
        drawArcDots(graphics, cx, cy, radius - 6, shimmerStart, Math.min(endDeg, shimmerStart + 12.0D), withAlpha(theme.text, 56), 1);
        final double needleAngle = Math.toRadians(startDeg + sweepDeg * percent);
        final int nx = cx + Math.round((float) Math.cos(needleAngle) * (radius - 7));
        final int ny = cy + Math.round((float) Math.sin(needleAngle) * (radius - 7));
        drawGuiLine(graphics, cx + 1, cy + 1, nx + 1, ny + 1, withAlpha(theme.bg, 220));
        drawGuiLine(graphics, cx, cy, nx, ny, accent);
        final int tailX = cx - Math.round((float) Math.cos(needleAngle) * Math.max(6, radius / 7));
        final int tailY = cy - Math.round((float) Math.sin(needleAngle) * Math.max(6, radius / 7));
        drawGuiLine(graphics, cx, cy, tailX, tailY, withAlpha(accent, 150));
        graphics.fill(cx - 4, cy - 4, cx + 5, cy + 5, withAlpha(theme.border, 230));
        graphics.fill(cx - 2, cy - 2, cx + 3, cy + 3, theme.text);
        graphics.fill(cx - 1, cy - 1, cx + 1, cy + 1, withAlpha(0xFFFFFFFF, 180));
        drawCenteredScaled(graphics, font, value, x, y + Math.max(17, height / 2 - 2), width, height < 42 ? 0.85f : 1.0f, theme.text);
    }

    private static void renderStudioTachGauge(GuiGraphics graphics,
                                              Font font,
                                              int x,
                                              int y,
                                              int width,
                                              int height,
                                              DisplayTheme theme,
                                              String value,
                                              float percent,
                                              int accent,
                                              boolean stressStyle) {
        final double startDeg = 205.0D;
        final double endDeg = 335.0D;
        final double sweepDeg = endDeg - startDeg;
        final double redlineStartDeg = 306.0D;
        final int cx = x + width / 2;
                            final int cy = y + Math.max(28, height - 13);
                            final int radius = Math.max(12, Math.min(width / 2 - 9, height - 28));
        final int redlineColor = stressStyle ? withAlpha(theme.warn, 170) : 0xFFFF6B6B;
        drawArcDots(graphics, cx, cy, radius, startDeg, endDeg, withAlpha(theme.border, 185), 2);
        drawArcDots(graphics, cx, cy, radius + 1, redlineStartDeg, endDeg, redlineColor, 2);
        for (int index = 0; index <= 10; ++index) {
            final double angle = Math.toRadians(startDeg + index * (sweepDeg / 10.0D));
            final int outerX = cx + Math.round((float) Math.cos(angle) * (radius + 1));
            final int outerY = cy + Math.round((float) Math.sin(angle) * (radius + 1));
            final int innerRadius = radius - (index % 5 == 0 ? 8 : 5);
            final int innerX = cx + Math.round((float) Math.cos(angle) * innerRadius);
            final int innerY = cy + Math.round((float) Math.sin(angle) * innerRadius);
            drawGuiLine(graphics, innerX, innerY, outerX, outerY, index >= 8 ? redlineColor : theme.dim);
        }
        drawArcDots(graphics, cx, cy, radius - 2, startDeg, startDeg + sweepDeg * percent, accent, 3);
        final double needleAngle = Math.toRadians(startDeg + sweepDeg * percent);
        final int nx = cx + Math.round((float) Math.cos(needleAngle) * (radius - 6));
        final int ny = cy + Math.round((float) Math.sin(needleAngle) * (radius - 6));
        drawGuiLine(graphics, cx, cy, nx, ny, accent);
        graphics.fill(cx - 2, cy - 2, cx + 3, cy + 3, theme.text);
        drawCenteredScaled(graphics, font, value, x, y + Math.max(17, height / 2 - 2), width, height < 42 ? 0.85f : 1.0f, theme.text);
    }

    private static void renderStudioTankGauge(GuiGraphics graphics,
                                              Font font,
                                              int x,
                                              int y,
                                              int width,
                                              int height,
                                              DisplayTheme theme,
                                              String value,
                                              float percent,
                                              int accent) {
        final int tankX = x + 8;
        final int tankY = y + 18;
        final int tankW = Math.max(16, Math.min(28, width / 4));
        final int tankH = Math.max(18, height - 34);
        drawRect(graphics, tankX, tankY, tankW, tankH, withAlpha(theme.border, 180));
        final int fillH = Math.max(1, Math.round((tankH - 4) * percent));
        graphics.fill(tankX + 3, tankY + tankH - 2 - fillH, tankX + tankW - 3, tankY + tankH - 2, withAlpha(accent, 220));
        graphics.fill(tankX + 4, tankY + tankH - 6 - fillH / 2, tankX + tankW - 5, tankY + tankH - 5 - fillH / 2, withAlpha(theme.text, 100));
        drawCenteredScaled(graphics, font, value, x + tankW + 10, y + Math.max(18, height / 2 - 4), width - tankW - 14, 1.0f, theme.text);
    }

    private static void renderStudioHeatGauge(GuiGraphics graphics,
                                              Font font,
                                              int x,
                                              int y,
                                              int width,
                                              int height,
                                              DisplayTheme theme,
                                              String value,
                                              float percent,
                                              int accent) {
        final int barX = x + 8;
        final int barY = y + 22;
        final int barW = Math.max(10, width - 16);
        final int segments = Math.max(3, Math.min(8, barW / 12));
        for (int index = 0; index < segments; ++index) {
            final int sx = barX + index * barW / segments + 1;
            final int sw = Math.max(3, barW / segments - 3);
            final boolean lit = index < Math.round(segments * percent);
            graphics.fill(sx, barY, sx + sw, barY + 8, lit ? withAlpha(accent, 225) : withAlpha(theme.border, 100));
            graphics.fill(sx, barY + 9, sx + sw, barY + 11, lit ? withAlpha(theme.warn, 155) : withAlpha(theme.bg, 80));
        }
        drawCenteredScaled(graphics, font, value, x, y + height - 25, width, 0.95f, theme.text);
    }

    private static void renderStudioAltitudeGauge(GuiGraphics graphics,
                                                  Font font,
                                                  int x,
                                                  int y,
                                                  int width,
                                                  int height,
                                                  DisplayTheme theme,
                                                  String value,
                                                  float percent,
                                                  int accent) {
        final int tapeW = Math.max(16, Math.min(22, width / 4));
        final int tapeX = x + width - tapeW - 8;
        final int tapeY = y + 18;
        final int tapeH = Math.max(18, height - 34);
        final int centerY = tapeY + tapeH / 2;
        drawRect(graphics, tapeX, tapeY, tapeW, tapeH, withAlpha(theme.border, 190));
        graphics.fill(tapeX + 1, tapeY + 1, tapeX + tapeW - 1, tapeY + tapeH - 1, withAlpha(theme.bg, 170));
        final int fillH = Math.max(2, Math.round((tapeH - 6) * percent));
        final int fillY = tapeY + tapeH - 3 - fillH;
        graphics.fill(tapeX + 3, fillY, tapeX + tapeW - 3, tapeY + tapeH - 3, withAlpha(accent, 160));
        for (int index = -3; index <= 3; ++index) {
            final int tickY = centerY + index * 6 + Math.round((0.5f - percent) * 8.0f);
            if (tickY <= tapeY + 2 || tickY >= tapeY + tapeH - 2) {
                continue;
            }
            final int tickW = index == 0 ? tapeW - 6 : tapeW - 10;
            graphics.fill(tapeX + tapeW - tickW - 2, tickY, tapeX + tapeW - 2, tickY + 1, index == 0 ? theme.text : withAlpha(theme.dim, 170));
        }
        final int markerY = tapeY + tapeH - 3 - Math.round((tapeH - 6) * percent);
        drawGuiLine(graphics, tapeX - 5, markerY, tapeX + 1, markerY, accent);
        drawGuiLine(graphics, tapeX - 5, markerY, tapeX - 1, markerY - 3, accent);
        drawGuiLine(graphics, tapeX - 5, markerY, tapeX - 1, markerY + 3, accent);

        final int graphX = x + 8;
        final int graphY = y + height - 24;
        final int graphW = Math.max(20, width - tapeW - 20);
        drawGuiLine(graphics, graphX, graphY + 8, graphX + graphW, graphY + 8, withAlpha(theme.grid, 130));
        final int traceY = graphY + 8 - Math.round(percent * 10.0f);
        drawGuiLine(graphics, graphX, graphY + 8, graphX + graphW / 2, traceY + 2, accent);
        drawGuiLine(graphics, graphX + graphW / 2, traceY + 2, graphX + graphW, traceY - 1, withAlpha(accent, 180));
        graphics.fill(graphX + graphW - 2, traceY - 2, graphX + graphW + 2, traceY + 2, theme.text);
        drawCenteredScaled(graphics, font, value, x + 2, y + height / 2 - 10, Math.max(18, width - tapeW - 10), 0.95f, theme.text);
    }

    private static void renderStudioAccumulatorGauge(GuiGraphics graphics,
                                                     Font font,
                                                     int x,
                                                     int y,
                                                     int width,
                                                     int height,
                                                     DisplayTheme theme,
                                                     String value,
                                                     float percent,
                                                     int accent) {
        final int packX = x + 8;
        final int packY = y + 22;
        final int packW = Math.max(28, width - 22);
        final int packH = Math.max(14, Math.min(22, height - 36));
        drawRect(graphics, packX, packY, packW, packH, withAlpha(theme.border, 190));
        graphics.fill(packX + packW, packY + packH / 3, packX + packW + 3, packY + packH * 2 / 3, theme.border);
        final int cells = 5;
        for (int index = 0; index < cells; ++index) {
            final int cellX = packX + 3 + index * (packW - 6) / cells;
            final int cellW = Math.max(3, (packW - 10) / cells);
            final boolean lit = index < Math.round(percent * cells);
            graphics.fill(cellX, packY + 3, cellX + cellW, packY + packH - 3, lit ? withAlpha(accent, 226) : withAlpha(theme.bg, 130));
        }
        drawCenteredScaled(graphics, font, value, x, y + height - 23, width, 0.9f, theme.text);
    }

    private static void renderStudioHorizonGauge(GuiGraphics graphics,
                                                 Font font,
                                                 int x,
                                                 int y,
                                                 int width,
                                                 int height,
                                                 DisplayTheme theme,
                                                 String value,
                                                 float percent,
                                                 int accent) {
        final int cx = x + width / 2;
        final int cy = y + height / 2 + 3;
        final int radius = Math.max(12, Math.min(width, height) / 3);
        graphics.fill(cx - radius, cy - radius, cx + radius, cy, withAlpha(accent, 95));
        graphics.fill(cx - radius, cy, cx + radius, cy + radius, withAlpha(theme.border, 95));
        drawArcDots(graphics, cx, cy, radius, 0.0D, 360.0D, withAlpha(theme.text, 180), 2);
        final int offset = Math.round((percent - 0.5f) * radius);
        drawGuiLine(graphics, cx - radius + 3, cy + offset, cx + radius - 3, cy - offset, accent);
        drawCenteredScaled(graphics, font, value, x, y + height - 22, width, 0.85f, theme.text);
    }

    private static void renderStatusStrip(GuiGraphics graphics,
                                          Font font,
                                          int x,
                                          int y,
                                          int width,
                                          int height,
                                          DisplayTheme theme,
                                          CustomModuleInstance module,
                                          BindingSummary binding,
                                          boolean selected) {
        drawModuleCard(graphics, x, y, width, height, theme, selected, binding.accentColor());
        final int segmentWidth = Math.max(12, (width - 10) / 3);
        final int chipY = y + Math.max(4, (height - 12) / 2);
        drawPill(graphics, x + 4, chipY, segmentWidth, 10, withAlpha(binding.accentColor(), 210), theme.bg);
        drawPill(graphics, x + 4 + segmentWidth + 2, chipY, segmentWidth, 10, withAlpha(theme.tabSel, 210), theme.bg);
        drawPill(graphics, x + 4 + (segmentWidth + 2) * 2, chipY, segmentWidth, 10, withAlpha(theme.tabHover, 210), theme.bg);
        drawCentered(graphics, font, textOr(module.getModuleConfig().getString("left"), "Live"), x + 4, chipY + 1, segmentWidth, theme.text);
        drawCentered(graphics, font, textOr(module.getModuleConfig().getString("center"), "Nominal"), x + 4 + segmentWidth + 2, chipY + 1, segmentWidth, theme.text);
        drawCentered(graphics, font, textOr(module.getModuleConfig().getString("right"), "Stable"), x + 4 + (segmentWidth + 2) * 2, chipY + 1, segmentWidth, theme.text);
    }

    private static void renderDualCard(GuiGraphics graphics,
                                       Font font,
                                       int x,
                                       int y,
                                       int width,
                                       int height,
                                       DisplayTheme theme,
                                       CustomModuleInstance module,
                                       BindingSummary binding,
                                       boolean selected) {
        drawModuleCard(graphics, x, y, width, height, theme, selected, binding.accentColor());
        final int cardWidth = Math.max(12, (width - 6) / 2);
        renderMiniCard(graphics, font, x + 2, y + 4, cardWidth, height - 8,
                textOr(module.getModuleConfig().getString("leftLabel"), "Left"),
                textOr(module.getModuleConfig().getString("leftValue"), "0"), theme, binding.accentColor());
        renderMiniCard(graphics, font, x + width - cardWidth - 2, y + 4, cardWidth, height - 8,
                textOr(module.getModuleConfig().getString("rightLabel"), "Right"),
                textOr(module.getModuleConfig().getString("rightValue"), "0"), theme, theme.tabSel);
    }

    private static void renderTripleCard(GuiGraphics graphics,
                                         Font font,
                                         int x,
                                         int y,
                                         int width,
                                         int height,
                                         DisplayTheme theme,
                                         CustomModuleInstance module,
                                         BindingSummary binding,
                                         boolean selected) {
        drawModuleCard(graphics, x, y, width, height, theme, selected, binding.accentColor());
        final int cardWidth = Math.max(10, (width - 8) / 3);
        renderMiniCard(graphics, font, x + 2, y + 4, cardWidth, height - 8,
                textOr(module.getModuleConfig().getString("aLabel"), "A"),
                textOr(module.getModuleConfig().getString("aValue"), "0"), theme, binding.accentColor());
        renderMiniCard(graphics, font, x + 4 + cardWidth, y + 4, cardWidth, height - 8,
                textOr(module.getModuleConfig().getString("bLabel"), "B"),
                textOr(module.getModuleConfig().getString("bValue"), "0"), theme, theme.tabSel);
        renderMiniCard(graphics, font, x + 6 + cardWidth * 2, y + 4, cardWidth, height - 8,
                textOr(module.getModuleConfig().getString("cLabel"), "C"),
                textOr(module.getModuleConfig().getString("cValue"), "0"), theme, theme.tabHover);
    }

    private static void renderChannelList(GuiGraphics graphics,
                                          Font font,
                                          int x,
                                          int y,
                                          int width,
                                          int height,
                                          DisplayTheme theme,
                                          CustomModuleInstance module,
                                          BindingSummary binding,
                                          boolean selected) {
        drawModuleCard(graphics, x, y, width, height, theme, selected, binding.accentColor());
        graphics.drawString(font, textOr(module.getModuleConfig().getString("title"), "Channels"), x + 4, y + 4, theme.text, false);
        final int[] rowColors = new int[] { binding.accentColor(), theme.tabSel, theme.tabHover, theme.border };
        final String[] rows = new String[] { "Main feed", "Reserve", "Alert bus", "Remote link" };
        for (int index = 0; index < rows.length; ++index) {
            final int rowY = y + 16 + index * 10;
            if (rowY + 8 >= y + height) {
                break;
            }
            graphics.fill(x + 4, rowY + 2, x + 8, rowY + 6, rowColors[index % rowColors.length]);
            graphics.drawString(font, rows[index], x + 12, rowY, theme.dim, false);
        }
        drawBindingBadge(graphics, font, x + width - 54, y + 4, binding);
    }

    private static void renderMacroScene(GuiGraphics graphics,
                                         Font font,
                                         int x,
                                         int y,
                                         int width,
                                         int height,
                                         DisplayTheme theme,
                                         CustomScreenModuleContract contract,
                                         CustomModuleInstance module,
                                         BindingSummary binding,
                                         boolean selected) {
        drawModuleCard(graphics, x, y, width, height, theme, selected, binding.accentColor());
        final int headerH = Math.max(12, height / 5);
        graphics.fill(x + 1, y + 1, x + width - 1, y + headerH, withAlpha(theme.graphBg, 210));
        graphics.drawString(font, contract.getDisplayName(), x + 6, y + 5, theme.text, false);
        graphics.drawString(font, textOr(module.getModuleConfig().getString("subtitle"), "Hero macro scene"), x + 6, y + 17, theme.dim, false);
        drawPill(graphics, x + width - 58, y + 5, 52, 10, withAlpha(theme.accent, 220), theme.bg);
        drawCentered(graphics, font, contract.getHeroPolicy() == CustomScreenModuleContract.HeroPolicy.PREFER_YES ? "Hero+" : "Hero", x + width - 58, y + 6, 52, theme.text);
        graphics.fill(x + 6, y + headerH + 8, x + width - 6, y + height - 18, withAlpha(theme.bg, 146));
        drawRect(graphics, x + 6, y + headerH + 8, Math.max(12, width - 12), Math.max(18, height - headerH - 26), withAlpha(theme.border, 180));
        drawCentered(graphics, font, "Specialized scene preview", x + 6, y + headerH + 16, width - 12, theme.dim);
        drawCentered(graphics, font, binding.status() == BindingStatus.RESOLVED ? binding.detail() : "Bind a source to render live", x + 6, y + headerH + 30, width - 12, binding.accentColor());
        drawBindingBadge(graphics, font, x + 6, y + height - 14, binding);
    }

    private static void renderFallbackCard(GuiGraphics graphics,
                                           Font font,
                                           int x,
                                           int y,
                                           int width,
                                           int height,
                                           DisplayTheme theme,
                                           String title,
                                           BindingSummary binding,
                                           boolean selected) {
        drawModuleCard(graphics, x, y, width, height, theme, selected, binding.accentColor());
        graphics.drawString(font, title, x + 4, y + 4, theme.text, false);
        drawBindingBadge(graphics, font, x + 4, y + height - 12, binding);
    }

    private static void renderMiniCard(GuiGraphics graphics,
                                       Font font,
                                       int x,
                                       int y,
                                       int width,
                                       int height,
                                       String label,
                                       String value,
                                       DisplayTheme theme,
                                       int accentColor) {
        graphics.fill(x, y, x + width, y + height, withAlpha(theme.tabBg, 210));
        drawRect(graphics, x, y, width, height, theme.border);
        graphics.fill(x, y, x + width, y + 2, accentColor);
        graphics.drawString(font, label, x + 3, y + 4, theme.dim, false);
        drawCenteredScaled(graphics, font, value, x, y + height / 2 - 2, width, 0.95f, theme.text);
    }

    private static void drawModuleCard(GuiGraphics graphics,
                                       int x,
                                       int y,
                                       int width,
                                       int height,
                                       DisplayTheme theme,
                                       boolean selected,
                                       int accentColor) {
        graphics.fill(x, y, x + width, y + height, withAlpha(theme.tabBg, 230));
        graphics.fill(x, y, x + width, y + 3, accentColor);
        drawRect(graphics, x, y, width, height, theme.border);
        if (selected) {
            drawSelection(graphics, x, y, width, height, theme.accent);
        }
    }

    private static void drawBindingBadge(GuiGraphics graphics, Font font, int x, int y, BindingSummary binding) {
        drawPill(graphics, x, y, 50, 10, withAlpha(binding.accentColor(), 220), 0xFF101010);
        graphics.drawString(font, binding.label(), x + 5, y + 2, 0xFFFFFFFF, false);
    }

    private static String gaugeMode(CustomModuleInstance module) {
        final String configured = module.getModuleConfig().getString("visualMode");
        if (!configured.isEmpty()) {
            return configured.toLowerCase(Locale.ROOT);
        }
        return CustomScreenSourceCapability.visualModeFor(module.getModuleConfig().getString("sourceType"),
                module.getModuleConfig().getString("sectionLabel"),
                module.getModuleConfig().getString("primaryMetric"),
                module.getModuleConfig().getString("label"));
    }

    private static float previewPercent(CustomModuleInstance module, String mode) {
        final String value = module.getModuleConfig().getString("value");
        final float parsed = parsePercent(value);
        if (!Float.isNaN(parsed)) {
            return parsed;
        }
        return switch (mode) {
            case "stress" -> 0.72f;
            case "rpm" -> 0.58f;
            case "tank" -> 0.64f;
            case "heat" -> 0.82f;
            case "accumulator" -> 0.76f;
            case "horizon" -> 0.48f;
            default -> 0.66f;
        };
    }

    private static float parsePercent(String value) {
        if (value == null || value.isEmpty()) {
            return Float.NaN;
        }
        final String digitsOnly = value.replace("%", "").replaceAll("[^0-9.-]", "");
        if (digitsOnly.isEmpty()) {
            return Float.NaN;
        }
        try {
            final float parsed = Float.parseFloat(digitsOnly);
            if (value.contains("%") || parsed > 1.0f) {
                return Math.max(0.0f, Math.min(1.0f, parsed / 100.0f));
            }
            return Math.max(0.0f, Math.min(1.0f, parsed));
        }
        catch (NumberFormatException ex) {
            return Float.NaN;
        }
    }

    private static int gaugeAccent(String mode, int fallback, DisplayTheme theme) {
        return switch (mode) {
            case "stress" -> theme.warn;
            case "tank" -> 0xFF2F9EFD;
            case "heat" -> 0xFFFF7A18;
            case "accumulator" -> 0xFF4DFF88;
            case "altitude" -> 0xFF74C0FC;
            case "horizon" -> 0xFF66D9E8;
            default -> fallback;
        };
    }

    private static String modeLabel(String mode) {
        return switch (mode) {
            case "stress" -> "LOAD";
            case "rpm" -> "RPM";
            case "tank" -> "TANK";
            case "heat" -> "HEAT";
            case "accumulator" -> "FE";
            case "altitude" -> "ALT";
            case "horizon" -> "ATT";
            default -> "DIAL";
        };
    }

    private static void drawArcDots(GuiGraphics graphics, int cx, int cy, int radius, double startDeg, double endDeg, int color, int thickness) {
        final int steps = Math.max(16, (int) Math.abs(endDeg - startDeg) / 5);
        for (int step = 0; step <= steps; ++step) {
            final double t = step / (double) steps;
            final double angle = Math.toRadians(startDeg + (endDeg - startDeg) * t);
            final int px = cx + Math.round((float) Math.cos(angle) * radius);
            final int py = cy + Math.round((float) Math.sin(angle) * radius);
            graphics.fill(px - thickness / 2, py - thickness / 2, px + thickness, py + thickness, color);
        }
    }

    private static void drawStudioDialTickMarks(GuiGraphics graphics,
                                                int cx,
                                                int cy,
                                                int radius,
                                                double startDeg,
                                                double sweepDeg,
                                                DisplayTheme theme) {
        final int divisions = 40;
        for (int index = 0; index <= divisions; ++index) {
            final double angle = Math.toRadians(startDeg + sweepDeg * index / divisions);
            final boolean major = index % 5 == 0;
            final int outerRadius = radius + (major ? 1 : 0);
            final int innerRadius = radius - (major ? 13 : 8);
            final int x1 = cx + Math.round((float) Math.cos(angle) * innerRadius);
            final int y1 = cy + Math.round((float) Math.sin(angle) * innerRadius);
            final int x2 = cx + Math.round((float) Math.cos(angle) * outerRadius);
            final int y2 = cy + Math.round((float) Math.sin(angle) * outerRadius);
            final int color = index >= 33 ? withAlpha(theme.warn, major ? 220 : 160)
                    : withAlpha(major ? theme.text : theme.dim, major ? 190 : 140);
            drawGuiLine(graphics, x1, y1, x2, y2, color);
        }
    }

    private static void drawGuiLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        final int sx = x0 < x1 ? 1 : -1;
        final int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        int x = x0;
        int y = y0;
        while (true) {
            graphics.fill(x, y, x + 2, y + 2, color);
            if (x == x1 && y == y1) {
                break;
            }
            final int err2 = 2 * err;
            if (err2 >= dy) {
                err += dy;
                x += sx;
            }
            if (err2 <= dx) {
                err += dx;
                y += sy;
            }
        }
    }

    private static void drawSelection(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        drawRect(graphics, x - 1, y - 1, width + 2, height + 2, color);
    }

    private static void drawRect(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    private static void drawRect(NativeImage image, int x, int y, int width, int height, int color) {
        fillRect(image, x, y, width, 1, color);
        fillRect(image, x, y + height - 1, width, 1, color);
        fillRect(image, x, y, 1, height, color);
        fillRect(image, x + width - 1, y, 1, height, color);
    }

    private static void drawPill(GuiGraphics graphics, int x, int y, int width, int height, int fillColor, int textBackColor) {
        graphics.fill(x + 1, y, x + width - 1, y + height, fillColor);
        graphics.fill(x, y + 1, x + width, y + height - 1, fillColor);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, textBackColor == 0 ? fillColor : blend(fillColor, textBackColor, 0.15f));
    }

    private static void drawCentered(GuiGraphics graphics, Font font, String text, int x, int y, int width, int color) {
        graphics.drawString(font, text, x + Math.max(0, (width - font.width(text)) / 2), y, color, false);
    }

    private static void drawCenteredScaled(GuiGraphics graphics, Font font, String text, int x, int y, int width, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        final int scaledWidth = Math.max(1, Math.round(width / scale));
        graphics.drawString(font, text, Math.max(0, (scaledWidth - font.width(text)) / 2), 0, color, false);
        graphics.pose().popPose();
    }

    private static void drawScaledString(GuiGraphics graphics, Font font, String text, int x, int y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private static void fillRect(NativeImage image, int x, int y, int width, int height, int color) {
        final int maxX = Math.min(image.getWidth(), x + width);
        final int maxY = Math.min(image.getHeight(), y + height);
        for (int drawX = Math.max(0, x); drawX < maxX; ++drawX) {
            for (int drawY = Math.max(0, y); drawY < maxY; ++drawY) {
                image.setPixelRGBA(drawX, drawY, color);
            }
        }
    }

    private static int withAlpha(int color, int alpha) {
        final int clampedAlpha = Math.max(0, Math.min(255, alpha));
        return (clampedAlpha << 24) | (color & 0x00FFFFFF);
    }

    private static int blend(int a, int b, float t) {
        final float clamped = Math.max(0.0f, Math.min(1.0f, t));
        final int ar = (a >> 16) & 0xFF;
        final int ag = (a >> 8) & 0xFF;
        final int ab = a & 0xFF;
        final int br = (b >> 16) & 0xFF;
        final int bg = (b >> 8) & 0xFF;
        final int bb = b & 0xFF;
        final int rr = Math.round(ar * (1.0f - clamped) + br * clamped);
        final int rg = Math.round(ag * (1.0f - clamped) + bg * clamped);
        final int rb = Math.round(ab * (1.0f - clamped) + bb * clamped);
        return (a & 0xFF000000) | (rr << 16) | (rg << 8) | rb;
    }

    private static String textOr(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    public enum BindingStatus {
        NONE,
        RESOLVED,
        FALLBACK,
        INCOMPATIBLE,
        OFFLINE,
        UNRESOLVED
    }

    public record BindingSummary(BindingStatus status, String label, String detail, int accentColor) {
    }
}
