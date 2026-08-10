package com.createstats.renderer;

import com.createstats.blockentity.SourceConfig;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.display.CustomModuleBinding;
import com.createstats.display.CustomModuleInstance;
import com.createstats.display.CustomScreenLayout;
import com.createstats.display.CustomScreenMigrationCoordinator;
import com.createstats.display.CustomScreenModuleCatalog;
import com.createstats.display.CustomScreenModuleContract;
import com.createstats.display.CustomScreenModuleType;
import com.createstats.display.CustomScreenSourceFamilyCatalog;
import com.createstats.display.CustomScreenSourceCapability;
import com.createstats.display.DisplayTheme;
import com.createstats.display.StatsDisplaySettings;
import com.createstats.renderer.CustomScreenModuleRenderer.BindingStatus;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class CustomScreenPanelRenderer {
    private static final String AERO_FLIGHT_DECK_SOURCE_ID = "aero_flight_deck";
    private static final String AEROLINE_DIRECTOR_SOURCE_ID = "aeroline_director";
    private static final CustomScreenModuleContract UNKNOWN_CONTRACT = new CustomScreenModuleContract(
            CustomScreenModuleType.LOGO,
            "Unknown Module",
            CustomScreenModuleContract.Domain.STRUCTURE,
            CustomModuleInstance.ModuleScale.WIDGET,
            4,
            3,
            2,
            2,
            List.of(),
            false,
            List.of("default"),
            new CustomModuleInstance.VisibilityPolicy(),
            CustomScreenModuleContract.HeroPolicy.NO,
            "",
            CustomScreenModuleContract.PlacementPolicy.FREE,
            CustomScreenModuleContract.RendererStyle.CARD);

    private CustomScreenPanelRenderer() {
    }

    public static boolean renderCustomLayout(NativeImage image,
                                             StatsDisplayPanelBE be,
                                             List<StatsDisplayPanelBE.DataChannel> channels) {
        if (image == null || be == null || !be.getSettings().isCustomScreenLayoutEnabled()) {
            return false;
        }
        final CustomScreenLayout layout = CustomScreenMigrationCoordinator.normalizeLayout(be.getSettings().getCustomScreenLayout(), be.getSourceRegistry());
        if (layout == null || layout.getModules().isEmpty()) {
            return false;
        }
        final StatsDisplaySettings settings = be.getSettings();
        final DisplayTheme theme = settings.getDisplayTheme();
        layout.normalize();

        TextureGraphRenderer.fillRect(image, 0, 0, image.getWidth(), image.getHeight(), theme.graphBg);
        TextureGraphRenderer.drawBorder(image, 0, 0, image.getWidth(), image.getHeight(), 2, theme.border);

        final CustomScreenLayout.VariantFamily activeVariant = resolveVariantFamily(image.getWidth(), image.getHeight());
        final CustomScreenLayout.VariantFamily gridFamily = gridFamilyForLayout(layout, activeVariant);
        final GridShape gridShape = GridShape.forVariant(gridFamily);
        final int outerPad = Math.max(4, image.getWidth() / 64);
        final int cellSize = Math.max(8, Math.min((image.getWidth() - outerPad * 2) / gridShape.columns, (image.getHeight() - outerPad * 2) / gridShape.rows));
        final int gridPixelW = gridShape.columns * cellSize;
        final int gridPixelH = gridShape.rows * cellSize;
        final int gridOriginX = (image.getWidth() - gridPixelW) / 2;
        final int gridOriginY = (image.getHeight() - gridPixelH) / 2;
        final int headerH = Math.max(10, cellSize);
        TextureGraphRenderer.fillRect(image, gridOriginX, gridOriginY, gridPixelW, headerH, TextureGraphRenderer.withAlpha(theme.tabBg, 210));
        BitmapFont.drawString(image, TextureGraphRenderer.truncate("Custom Layout", gridPixelW - 10, 1), gridOriginX + 4, gridOriginY + 2, theme.text, 1);
        BitmapFont.drawRight(image, gridFamily.getSerializedName(), gridOriginX + gridPixelW - 4, gridOriginY + 2, theme.dim, 1);

        final CustomScreenLayout.VariantLayout variantLayout = activeVariant == CustomScreenLayout.VariantFamily.BASE ? null : layout.getVariant(activeVariant);
        final Map<String, SourceConfig> sourceRegistry = be.getSourceRegistry();
        final List<ResolvedModule> modules = new ArrayList<ResolvedModule>();
        for (CustomModuleInstance module : layout.getModules()) {
            if (module == null) {
                continue;
            }
            if (variantLayout != null && variantLayout.getOmittedModules().contains(module.getId())) {
                continue;
            }
            final CustomScreenModuleContract contract = moduleContract(module.getModuleType());
            final CustomModuleInstance.VariantOverride override = variantLayout != null ? variantLayout.getModuleOverrides().get(module.getId()) : null;
            final CustomModuleInstance.VisibilityPolicy.VisibilityMode visibility = override != null
                    && override.getVisibility() != null
                    && override.getVisibility() != CustomModuleInstance.VisibilityPolicy.VisibilityMode.INHERIT
                    ? override.getVisibility()
                    : module.getVisibilityPolicy().get(activeVariant);
            if (visibility == CustomModuleInstance.VisibilityPolicy.VisibilityMode.HIDE) {
                continue;
            }
            final CustomModuleInstance.GridBounds bounds = override != null && override.getBounds() != null ? override.getBounds().copy() : module.getBounds().copy();
            final int renderX = gridOriginX + bounds.getX() * cellSize + 1;
            final int renderY = gridOriginY + bounds.getY() * cellSize + 1;
            final int renderW = Math.max(8, bounds.getWidth() * cellSize - 2);
            final int renderH = Math.max(8, bounds.getHeight() * cellSize - 2);
            modules.add(new ResolvedModule(module,
                    contract,
                    bounds,
                    renderX,
                    renderY,
                    renderW,
                    renderH,
                    resolveField(module, CustomModuleBinding.PRIMARY_METRIC, channels, sourceRegistry, contract.allowsSemanticFallback()),
                    resolveField(module, CustomModuleBinding.SECONDARY_METRIC, channels, sourceRegistry, contract.allowsSemanticFallback()),
                    resolveField(module, CustomModuleBinding.STATUS_METRIC, channels, sourceRegistry, contract.allowsSemanticFallback())));
        }
        if (modules.isEmpty()) {
            BitmapFont.drawCentered(image, "No visible modules", gridOriginX, gridOriginY + gridPixelH / 2 - 4, gridPixelW, theme.dim, 1);
            return true;
        }
        for (ResolvedModule module : modules) {
            renderModule(image, be, module, channels, sourceRegistry, theme);
        }
        return true;
    }

    private static void renderModule(NativeImage image,
                                     StatsDisplayPanelBE be,
                                     ResolvedModule module,
                                     List<StatsDisplayPanelBE.DataChannel> channels,
                                     Map<String, SourceConfig> sourceRegistry,
                                     DisplayTheme theme) {
        switch (module.contract.getRendererStyle()) {
            case TITLE -> renderTitle(image, module, theme, 2);
            case SUBTITLE -> renderTitle(image, module, theme, 1);
            case DIVIDER -> renderDivider(image, module, theme);
            case BADGE -> renderBadge(image, module, theme);
            case LOGO -> renderLogo(image, module, theme);
            case BIG_VALUE -> renderBigValue(image, module, theme);
            case ANALOG_GAUGE -> renderGauge(image, be, module, channels, sourceRegistry, theme);
            case STATUS_STRIP -> renderStatusStrip(image, module, theme);
            case DUAL_CARD -> renderDualCard(image, module, theme);
            case TRIPLE_CARD -> renderTripleCard(image, module, theme);
            case CHANNEL_LIST -> renderChannelList(image, module, channels, sourceRegistry, theme);
            case MACRO_SCENE -> renderMacroScene(image, be, module, channels, sourceRegistry, theme);
            case CARD -> renderFallbackCard(image, module, theme);
        }
    }

    private static void renderTitle(NativeImage image, ResolvedModule module, DisplayTheme theme, int scale) {
        final String text = module.module.getModuleConfig().getString("text");
        BitmapFont.drawString(image,
                TextureGraphRenderer.truncate(text.isEmpty() ? "Untitled" : text, module.renderW - 2, scale),
                module.renderX + 2,
                module.renderY + 2,
                scale > 1 ? theme.text : theme.dim,
                scale);
    }

    private static void renderDivider(NativeImage image, ResolvedModule module, DisplayTheme theme) {
        final int y = module.renderY + Math.max(1, module.renderH / 2);
        TextureGraphRenderer.fillRect(image, module.renderX + 2, y, Math.max(4, module.renderW - 4), 1, theme.border);
        TextureGraphRenderer.fillRect(image, module.renderX + 2, y + 1, Math.max(4, module.renderW - 4), 1, TextureGraphRenderer.withAlpha(theme.accent, 136));
    }

    private static void renderBadge(NativeImage image, ResolvedModule module, DisplayTheme theme) {
        drawModuleFrame(image, module, theme, module.statusBinding.accentColor);
        final String text = module.module.getModuleConfig().getString("text");
        BitmapFont.drawCentered(image,
                TextureGraphRenderer.truncate(text.isEmpty() ? module.statusBinding.detail : text, module.renderW - 4, 1),
                module.renderX,
                module.renderY + Math.max(1, (module.renderH - BitmapFont.textHeight(1)) / 2),
                module.renderW,
                theme.text,
                1);
    }

    private static void renderLogo(NativeImage image, ResolvedModule module, DisplayTheme theme) {
        TextureGraphRenderer.fillRect(image, module.renderX, module.renderY, module.renderW, module.renderH, TextureGraphRenderer.withAlpha(theme.tabSel, 218));
        TextureGraphRenderer.drawBorder(image, module.renderX, module.renderY, module.renderW, module.renderH, 1, theme.border);
        BitmapFont.drawCentered(image,
                TextureGraphRenderer.truncate(module.module.getModuleConfig().getString("text").isEmpty() ? "SD" : module.module.getModuleConfig().getString("text"), module.renderW - 4, 2),
                module.renderX,
                module.renderY + Math.max(1, (module.renderH - BitmapFont.textHeight(2)) / 2),
                module.renderW,
                theme.accent,
                2);
    }

    private static void renderBigValue(NativeImage image, ResolvedModule module, DisplayTheme theme) {
        drawModuleFrame(image, module, theme, module.primaryBinding.accentColor);
        final String label = module.primaryBinding.hasChannel() ? module.primaryBinding.label : textOr(module.module.getModuleConfig().getString("label"), "Metric");
        final String value = module.primaryBinding.hasChannel() ? module.primaryBinding.value : textOr(module.module.getModuleConfig().getString("value"), "000");
        BitmapFont.drawString(image, TextureGraphRenderer.truncate(label, module.renderW - 8, 1), module.renderX + 4, module.renderY + 5, theme.dim, 1);
        BitmapFont.drawCentered(image, TextureGraphRenderer.truncate(value, module.renderW - 8, 2), module.renderX, module.renderY + 18, module.renderW, theme.text, 2);
        drawBindingBadge(image, module.renderX + 4, module.renderY + module.renderH - 12, module.primaryBinding, theme);
    }

    private static void renderGauge(NativeImage image,
                                    StatsDisplayPanelBE be,
                                    ResolvedModule module,
                                    List<StatsDisplayPanelBE.DataChannel> channels,
                                    Map<String, SourceConfig> sourceRegistry,
                                    DisplayTheme theme) {
        final String mode = gaugeMode(module);
        final float percent = normalizedMetric(module, module.primaryBinding, mode, channels, sourceRegistry, be != null ? be.getSettings() : null);
        final long gameTime = be != null && be.getLevel() != null ? be.getLevel().getGameTime() : 0L;
        final int accent = pulseColor(gaugeAccent(mode, module.primaryBinding.accentColor, theme), gameTime, mode);
        drawModuleFrame(image, module, theme, accent);
        final String label = module.primaryBinding.hasChannel() ? module.primaryBinding.label : textOr(module.module.getModuleConfig().getString("label"), "Gauge");
        final String value = module.primaryBinding.hasChannel() ? module.primaryBinding.value : textOr(module.module.getModuleConfig().getString("value"), "78%");
        BitmapFont.drawString(image, TextureGraphRenderer.truncate(label, module.renderW - 8, 1), module.renderX + 4, module.renderY + 5, theme.dim, 1);
        drawModePill(image, module.renderX + module.renderW - 32, module.renderY + 4, modeLabel(mode), accent, theme);
        if ("tank".equals(mode)) {
            renderTankInstrument(image, module, theme, value, percent, accent, gameTime);
        }
        else if ("heat".equals(mode)) {
            renderHeatInstrument(image, module, theme, value, percent, accent, gameTime);
        }
        else if ("altitude".equals(mode)) {
            renderAltitudeInstrument(image, module, theme, value, percent, accent, gameTime);
        }
        else if ("accumulator".equals(mode)) {
            renderAccumulatorInstrument(image, module, theme, value, percent, accent, gameTime);
        }
        else if ("horizon".equals(mode)) {
            renderHorizonInstrument(image, module, theme, value, percent, accent, gameTime);
        }
        else if ("rpm".equals(mode) || "stress".equals(mode)) {
            renderTachInstrument(image, module, theme, value, percent, accent, "stress".equals(mode), gameTime);
        }
        else {
            renderDialInstrument(image, module, theme, value, percent, accent, gameTime);
        }
        drawBindingBadge(image, module.renderX + 4, module.renderY + module.renderH - 12, module.primaryBinding, theme);
    }

    private static void renderDialInstrument(NativeImage image,
                                             ResolvedModule module,
                                             DisplayTheme theme,
                                             String value,
                                             float percent,
                                             int accent,
                                             long gameTime) {
        final double startDeg = 205.0D;
        final double endDeg = 335.0D;
        final double sweepDeg = endDeg - startDeg;
        final int cx = module.renderX + module.renderW / 2;
        final int cy = module.renderY + Math.max(28, module.renderH - 14);
        final int radius = Math.max(9, Math.min(module.renderW / 2 - 8, module.renderH - 29));
        drawArcStroke(image, cx, cy, radius + 2, startDeg, endDeg, TextureGraphRenderer.withAlpha(theme.border, 220), 3);
        drawArcStroke(image, cx, cy, radius + 1, startDeg, endDeg, TextureGraphRenderer.withAlpha(theme.text, 64), 1);
        drawArcStroke(image, cx, cy, Math.max(5, radius - 4), startDeg, endDeg, TextureGraphRenderer.withAlpha(theme.tabBg, 175), 6);
        drawArcStroke(image, cx, cy, Math.max(4, radius - 9), startDeg, endDeg, TextureGraphRenderer.withAlpha(theme.bg, 126), 2);
        drawArcStroke(image, cx, cy, radius, startDeg + sweepDeg * 0.82D, endDeg, TextureGraphRenderer.withAlpha(theme.warn, 148), 2);
        drawDialTickMarks(image, cx, cy, radius, startDeg, sweepDeg, theme);
        drawArcStroke(image, cx, cy, radius - 2, startDeg, startDeg + sweepDeg * percent, accent, 3);
        drawArcStroke(image, cx, cy, radius - 6, startDeg, startDeg + sweepDeg * percent, TextureGraphRenderer.withAlpha(accent, 120), 1);
        final double shimmerPhase = Math.floorMod(gameTime, 96L) / 96.0D;
        final double shimmerStart = startDeg + sweepDeg * 0.08D + sweepDeg * 0.48D * shimmerPhase;
        drawArcStroke(image, cx, cy, Math.max(4, radius - 7), shimmerStart, Math.min(endDeg, shimmerStart + 12.0D), TextureGraphRenderer.withAlpha(theme.text, 52), 1);
        final double needleAngle = Math.toRadians(startDeg + sweepDeg * percent + Math.sin(gameTime * 0.12D) * 0.8D);
        final int nx = cx + Math.round((float) Math.cos(needleAngle) * (radius - 7));
        final int ny = cy + Math.round((float) Math.sin(needleAngle) * (radius - 7));
        final int shadowColor = TextureGraphRenderer.withAlpha(theme.bg, 200);
        TextureGraphRenderer.drawLineAA(image, cx + 1, cy + 1, nx + 1, ny + 1, shadowColor);
        TextureGraphRenderer.drawLineAA(image, cx, cy, nx, ny, accent);
        final int tailX = cx - Math.round((float) Math.cos(needleAngle) * Math.max(6, radius / 7));
        final int tailY = cy - Math.round((float) Math.sin(needleAngle) * Math.max(6, radius / 7));
        TextureGraphRenderer.drawLineAA(image, cx, cy, tailX, tailY, TextureGraphRenderer.withAlpha(accent, 150));
        TextureGraphRenderer.fillCircle(image, cx, cy, 4, TextureGraphRenderer.withAlpha(theme.border, 235));
        TextureGraphRenderer.fillCircle(image, cx, cy, 2, theme.text);
        TextureGraphRenderer.fillCircle(image, cx - 1, cy - 1, 1, TextureGraphRenderer.withAlpha(0xFFFFFFFF, 180));
        BitmapFont.drawCentered(image, TextureGraphRenderer.truncate(value, module.renderW - 12, 1), module.renderX, module.renderY + Math.max(17, module.renderH / 2 - 2), module.renderW, theme.text, 1);
    }

    private static void renderTachInstrument(NativeImage image,
                                             ResolvedModule module,
                                             DisplayTheme theme,
                                             String value,
                                             float percent,
                                             int accent,
                                             boolean stressStyle,
                                             long gameTime) {
        final double startDeg = 205.0D;
        final double endDeg = 335.0D;
        final double sweepDeg = endDeg - startDeg;
        final double redlineStartDeg = 306.0D;
        final int cx = module.renderX + module.renderW / 2;
        final int cy = module.renderY + Math.max(30, module.renderH - 14);
        final int radius = Math.max(10, Math.min(module.renderW / 2 - 8, module.renderH - 31));
        final int redlineColor = stressStyle ? TextureGraphRenderer.withAlpha(theme.warn, 150) : TextureGraphRenderer.withAlpha(0xFFFF6B6B, 165);
        drawArcStroke(image, cx, cy, Math.max(6, radius - 7), startDeg, endDeg, TextureGraphRenderer.withAlpha(theme.tabBg, 156), 5);
        drawArcStroke(image, cx, cy, radius, startDeg, endDeg, TextureGraphRenderer.withAlpha(theme.border, 185), 2);
        drawArcStroke(image, cx, cy, radius + 1, redlineStartDeg, endDeg, redlineColor, 3);
        for (int index = 0; index <= 10; ++index) {
            final double angle = Math.toRadians(startDeg + index * (sweepDeg / 10.0D));
            final int outerX = cx + Math.round((float) Math.cos(angle) * (radius + 1));
            final int outerY = cy + Math.round((float) Math.sin(angle) * (radius + 1));
            final int innerRadius = radius - (index % 5 == 0 ? 7 : 4);
            final int innerX = cx + Math.round((float) Math.cos(angle) * innerRadius);
            final int innerY = cy + Math.round((float) Math.sin(angle) * innerRadius);
            TextureGraphRenderer.drawLineAA(image, innerX, innerY, outerX, outerY, index >= 8 ? redlineColor : theme.dim);
        }
        drawArcStroke(image, cx, cy, radius - 2, startDeg, startDeg + sweepDeg * percent, accent, 3);
        drawArcStroke(image, cx, cy, radius - 5, startDeg, startDeg + sweepDeg * percent, TextureGraphRenderer.withAlpha(accent, 120), 1);
        final double needleAngle = Math.toRadians(startDeg + sweepDeg * percent + Math.sin(gameTime * 0.1D) * 1.4D);
        final int nx = cx + Math.round((float) Math.cos(needleAngle) * (radius - 6));
        final int ny = cy + Math.round((float) Math.sin(needleAngle) * (radius - 6));
        TextureGraphRenderer.drawLineAA(image, cx, cy, nx, ny, accent);
        TextureGraphRenderer.fillCircle(image, cx, cy, 3, theme.text);
        BitmapFont.drawCentered(image, TextureGraphRenderer.truncate(value, module.renderW - 12, 1), module.renderX, module.renderY + Math.max(17, module.renderH / 2 - 2), module.renderW, theme.text, 1);
    }

    private static void renderTankInstrument(NativeImage image,
                                             ResolvedModule module,
                                             DisplayTheme theme,
                                             String value,
                                             float percent,
                                             int accent,
                                             long gameTime) {
        final int tankX = module.renderX + 7;
        final int tankY = module.renderY + 18;
        final int tankW = Math.max(14, Math.min(24, module.renderW / 4));
        final int tankH = Math.max(18, module.renderH - 34);
        TextureGraphRenderer.drawBorder(image, tankX, tankY, tankW, tankH, 1, TextureGraphRenderer.withAlpha(theme.border, 190));
        final int fillH = Math.max(1, Math.round((tankH - 4) * percent));
        final int fillY = tankY + tankH - 2 - fillH;
        TextureGraphRenderer.fillRect(image, tankX + 3, fillY, tankW - 6, fillH, TextureGraphRenderer.withAlpha(accent, 220));
        final int wave = Math.floorMod((int) gameTime, 8);
        TextureGraphRenderer.drawLineAA(image, tankX + 4, fillY + Math.min(fillH - 1, wave / 2), tankX + tankW - 5, fillY + Math.max(0, 3 - wave / 2), TextureGraphRenderer.withAlpha(theme.text, 128));
        BitmapFont.drawCentered(image, TextureGraphRenderer.truncate(value, module.renderW - tankW - 14, 1), tankX + tankW + 4, module.renderY + module.renderH / 2 - 4, module.renderW - tankW - 12, theme.text, 1);
    }

    private static void renderHeatInstrument(NativeImage image,
                                             ResolvedModule module,
                                             DisplayTheme theme,
                                             String value,
                                             float percent,
                                             int accent,
                                             long gameTime) {
        final int barX = module.renderX + 7;
        final int barY = module.renderY + 22;
        final int barW = Math.max(12, module.renderW - 14);
        final int segments = Math.max(3, Math.min(8, barW / 12));
        final int litSegments = Math.round(segments * percent);
        for (int index = 0; index < segments; ++index) {
            final int sx = barX + index * barW / segments + 1;
            final int sw = Math.max(3, barW / segments - 3);
            final boolean lit = index < litSegments;
            final int alpha = lit ? 190 + Math.floorMod((int) gameTime + index * 9, 44) : 96;
            TextureGraphRenderer.fillRect(image, sx, barY, sw, 7, lit ? TextureGraphRenderer.withAlpha(accent, alpha) : TextureGraphRenderer.withAlpha(theme.grid, 120));
            TextureGraphRenderer.fillRect(image, sx, barY + 8, sw, 2, lit ? TextureGraphRenderer.withAlpha(theme.warn, 150) : TextureGraphRenderer.withAlpha(theme.bg, 90));
        }
        BitmapFont.drawCentered(image, TextureGraphRenderer.truncate(value, module.renderW - 8, 1), module.renderX, module.renderY + module.renderH - 24, module.renderW, theme.text, 1);
    }

    private static void renderAltitudeInstrument(NativeImage image,
                                                 ResolvedModule module,
                                                 DisplayTheme theme,
                                                 String value,
                                                 float percent,
                                                 int accent,
                                                 long gameTime) {
        final int tapeW = Math.max(16, Math.min(22, module.renderW / 4));
        final int tapeX = module.renderX + module.renderW - tapeW - 7;
        final int tapeY = module.renderY + 18;
        final int tapeH = Math.max(20, module.renderH - 34);
        final int centerY = tapeY + tapeH / 2;
        TextureGraphRenderer.drawBorder(image, tapeX, tapeY, tapeW, tapeH, 1, TextureGraphRenderer.withAlpha(theme.border, 190));
        TextureGraphRenderer.fillRect(image, tapeX + 1, tapeY + 1, tapeW - 2, tapeH - 2, TextureGraphRenderer.withAlpha(theme.bg, 180));
        final int fillH = Math.max(2, Math.round((tapeH - 6) * percent));
        final int fillY = tapeY + tapeH - 3 - fillH;
        TextureGraphRenderer.fillRect(image, tapeX + 3, fillY, tapeW - 6, fillH, TextureGraphRenderer.withAlpha(accent, 165));
        for (int index = -3; index <= 3; ++index) {
            final int tickY = centerY + index * 6 + Math.round((0.5f - percent) * 8.0f);
            if (tickY <= tapeY + 2 || tickY >= tapeY + tapeH - 2) {
                continue;
            }
            final int tickW = index == 0 ? tapeW - 6 : tapeW - 10;
            TextureGraphRenderer.fillRect(image, tapeX + tapeW - tickW - 2, tickY, tickW, 1, index == 0 ? theme.text : TextureGraphRenderer.withAlpha(theme.dim, 170));
        }
        final int bugY = tapeY + tapeH - 3 - Math.round((tapeH - 6) * percent);
        TextureGraphRenderer.drawLineAA(image, tapeX - 5, bugY, tapeX + 1, bugY, accent);
        TextureGraphRenderer.drawLineAA(image, tapeX - 5, bugY, tapeX - 1, bugY - 3, accent);
        TextureGraphRenderer.drawLineAA(image, tapeX - 5, bugY, tapeX - 1, bugY + 3, accent);

        final int graphX = module.renderX + 8;
        final int graphY = module.renderY + module.renderH - 24;
        final int graphW = Math.max(20, module.renderW - tapeW - 20);
        TextureGraphRenderer.drawLineAA(image, graphX, graphY + 8, graphX + graphW, graphY + 8, TextureGraphRenderer.withAlpha(theme.grid, 130));
        final int crest = Math.round((float) Math.sin(gameTime * 0.08D) * 2.0f);
        final int traceY = graphY + 8 - Math.round(percent * 10.0f);
        TextureGraphRenderer.drawLineAA(image, graphX, graphY + 8, graphX + graphW / 2, traceY + crest, accent);
        TextureGraphRenderer.drawLineAA(image, graphX + graphW / 2, traceY + crest, graphX + graphW, traceY - crest / 2, TextureGraphRenderer.withAlpha(accent, 180));
        TextureGraphRenderer.fillCircle(image, graphX + graphW, traceY - crest / 2, 2, theme.text);
        BitmapFont.drawCentered(image, TextureGraphRenderer.truncate(value, graphW + 6, 1), module.renderX + 2, module.renderY + module.renderH / 2 - 6, Math.max(18, module.renderW - tapeW - 10), theme.text, 1);
    }

    private static void renderAccumulatorInstrument(NativeImage image,
                                                    ResolvedModule module,
                                                    DisplayTheme theme,
                                                    String value,
                                                    float percent,
                                                    int accent,
                                                    long gameTime) {
        final int packX = module.renderX + 7;
        final int packY = module.renderY + 22;
        final int packW = Math.max(26, module.renderW - 18);
        final int packH = Math.max(13, Math.min(22, module.renderH - 36));
        TextureGraphRenderer.drawBorder(image, packX, packY, packW, packH, 1, TextureGraphRenderer.withAlpha(theme.border, 190));
        TextureGraphRenderer.fillRect(image, packX + packW, packY + packH / 3, 3, Math.max(3, packH / 3), theme.border);
        final int cells = 5;
        final int activeCells = Math.round(percent * cells);
        for (int index = 0; index < cells; ++index) {
            final int cellX = packX + 3 + index * (packW - 6) / cells;
            final int cellW = Math.max(3, (packW - 10) / cells);
            final boolean lit = index < activeCells;
            final int alpha = lit ? 196 + Math.floorMod((int) gameTime + index * 7, 36) : 120;
            TextureGraphRenderer.fillRect(image, cellX, packY + 3, cellW, packH - 6, lit ? TextureGraphRenderer.withAlpha(accent, alpha) : TextureGraphRenderer.withAlpha(theme.bg, 130));
        }
        BitmapFont.drawCentered(image, TextureGraphRenderer.truncate(value, module.renderW - 8, 1), module.renderX, module.renderY + module.renderH - 24, module.renderW, theme.text, 1);
    }

    private static void renderHorizonInstrument(NativeImage image,
                                                ResolvedModule module,
                                                DisplayTheme theme,
                                                String value,
                                                float percent,
                                                int accent,
                                                long gameTime) {
        final int cx = module.renderX + module.renderW / 2;
        final int cy = module.renderY + module.renderH / 2 + 3;
        final int radius = Math.max(10, Math.min(module.renderW, module.renderH) / 3);
        TextureGraphRenderer.fillCircle(image, cx, cy, radius, TextureGraphRenderer.withAlpha(theme.bg, 210));
        TextureGraphRenderer.fillRect(image, cx - radius + 1, cy - radius + 1, radius * 2 - 2, radius, TextureGraphRenderer.withAlpha(accent, 82));
        TextureGraphRenderer.fillRect(image, cx - radius + 1, cy, radius * 2 - 2, radius - 1, TextureGraphRenderer.withAlpha(theme.border, 76));
        drawArcStroke(image, cx, cy, radius, 0.0D, 360.0D, TextureGraphRenderer.withAlpha(theme.text, 170), 2);
        final int offset = Math.round((percent - 0.5f) * radius);
        final int sway = Math.round((float) Math.sin(gameTime * 0.08D) * 2.0f);
        TextureGraphRenderer.drawLineAA(image, cx - radius + 3, cy + offset + sway, cx + radius - 3, cy - offset + sway, accent);
        BitmapFont.drawCentered(image, TextureGraphRenderer.truncate(value, module.renderW - 8, 1), module.renderX, module.renderY + module.renderH - 20, module.renderW, theme.text, 1);
    }

    private static void renderStatusStrip(NativeImage image, ResolvedModule module, DisplayTheme theme) {
        drawModuleFrame(image, module, theme, module.statusBinding.accentColor);
        final int chipW = Math.max(18, (module.renderW - 10) / 3);
        final int chipY = module.renderY + Math.max(4, (module.renderH - 10) / 2);
        drawChip(image, module.renderX + 4, chipY, chipW, textOr(module.module.getModuleConfig().getString("left"), module.primaryBinding.shortText()), module.statusBinding.accentColor, theme);
        drawChip(image, module.renderX + 6 + chipW, chipY, chipW, textOr(module.module.getModuleConfig().getString("center"), "Nominal"), theme.tabSel, theme);
        drawChip(image, module.renderX + 8 + chipW * 2, chipY, chipW, textOr(module.module.getModuleConfig().getString("right"), module.statusBinding.status.name()), theme.tabHover, theme);
    }

    private static void renderDualCard(NativeImage image, ResolvedModule module, DisplayTheme theme) {
        drawModuleFrame(image, module, theme, module.primaryBinding.accentColor);
        final int cardW = Math.max(18, (module.renderW - 6) / 2);
        drawMiniCard(image, module.renderX + 2, module.renderY + 4, cardW, module.renderH - 8,
                module.primaryBinding.hasChannel() ? module.primaryBinding.label : textOr(module.module.getModuleConfig().getString("leftLabel"), "Left"),
                module.primaryBinding.hasChannel() ? module.primaryBinding.value : textOr(module.module.getModuleConfig().getString("leftValue"), "0"), theme, module.primaryBinding.accentColor);
        drawMiniCard(image, module.renderX + module.renderW - cardW - 2, module.renderY + 4, cardW, module.renderH - 8,
                module.secondaryBinding.hasChannel() ? module.secondaryBinding.label : textOr(module.module.getModuleConfig().getString("rightLabel"), "Right"),
                module.secondaryBinding.hasChannel() ? module.secondaryBinding.value : textOr(module.module.getModuleConfig().getString("rightValue"), "0"), theme, module.secondaryBinding.accentColor);
    }

    private static void renderTripleCard(NativeImage image, ResolvedModule module, DisplayTheme theme) {
        drawModuleFrame(image, module, theme, module.primaryBinding.accentColor);
        final int cardW = Math.max(16, (module.renderW - 8) / 3);
        drawMiniCard(image, module.renderX + 2, module.renderY + 4, cardW, module.renderH - 8,
                module.primaryBinding.hasChannel() ? module.primaryBinding.label : textOr(module.module.getModuleConfig().getString("aLabel"), "A"),
                module.primaryBinding.hasChannel() ? module.primaryBinding.value : textOr(module.module.getModuleConfig().getString("aValue"), "0"), theme, module.primaryBinding.accentColor);
        drawMiniCard(image, module.renderX + 4 + cardW, module.renderY + 4, cardW, module.renderH - 8,
                module.secondaryBinding.hasChannel() ? module.secondaryBinding.label : textOr(module.module.getModuleConfig().getString("bLabel"), "B"),
                module.secondaryBinding.hasChannel() ? module.secondaryBinding.value : textOr(module.module.getModuleConfig().getString("bValue"), "0"), theme, module.secondaryBinding.accentColor);
        drawMiniCard(image, module.renderX + 6 + cardW * 2, module.renderY + 4, cardW, module.renderH - 8,
                module.statusBinding.hasChannel() ? module.statusBinding.label : textOr(module.module.getModuleConfig().getString("cLabel"), "C"),
                module.statusBinding.hasChannel() ? module.statusBinding.value : textOr(module.module.getModuleConfig().getString("cValue"), "0"), theme, module.statusBinding.accentColor);
    }

    private static void renderChannelList(NativeImage image,
                                          ResolvedModule module,
                                          List<StatsDisplayPanelBE.DataChannel> channels,
                                          Map<String, SourceConfig> sourceRegistry,
                                          DisplayTheme theme) {
        drawModuleFrame(image, module, theme, module.primaryBinding.accentColor);
        final List<StatsDisplayPanelBE.DataChannel> entries = resolveChannelList(module.module, channels, sourceRegistry);
        BitmapFont.drawString(image, TextureGraphRenderer.truncate(textOr(module.module.getModuleConfig().getString("title"), "Channels"), module.renderW - 8, 1), module.renderX + 4, module.renderY + 4, theme.text, 1);
        final int startY = module.renderY + 16;
        for (int index = 0; index < Math.min(4, entries.size()); ++index) {
            final StatsDisplayPanelBE.DataChannel channel = entries.get(index);
            final int rowY = startY + index * 10;
            if (rowY + 8 > module.renderY + module.renderH - 2) {
                break;
            }
            final int color = TextureGraphRenderer.CHANNEL_COLORS[index % TextureGraphRenderer.CHANNEL_COLORS.length];
            TextureGraphRenderer.fillRect(image, module.renderX + 4, rowY + 2, 4, 4, color);
            BitmapFont.drawString(image, TextureGraphRenderer.truncate(channel.getLabel(), module.renderW - 70, 1), module.renderX + 12, rowY, theme.dim, 1);
            BitmapFont.drawRight(image, TextureGraphRenderer.truncate(channel.getStore().getMetricType().formatValue(channel.getStore().getLatestValue()), 56, 1), module.renderX + module.renderW - 4, rowY, theme.text, 1);
        }
    }

    private static void renderFallbackCard(NativeImage image, ResolvedModule module, DisplayTheme theme) {
        drawModuleFrame(image, module, theme, module.primaryBinding.accentColor);
        BitmapFont.drawString(image, TextureGraphRenderer.truncate(module.contract.getDisplayName(), module.renderW - 8, 1), module.renderX + 4, module.renderY + 4, theme.text, 1);
        drawBindingBadge(image, module.renderX + 4, module.renderY + module.renderH - 12, module.primaryBinding, theme);
    }

    private static void renderMacroScene(NativeImage image,
                                         StatsDisplayPanelBE be,
                                         ResolvedModule module,
                                         List<StatsDisplayPanelBE.DataChannel> channels,
                                         Map<String, SourceConfig> sourceRegistry,
                                         DisplayTheme theme) {
        final CustomModuleBinding sourceBinding = module.module.resolveBinding(CustomModuleBinding.PRIMARY_SOURCE);
        String sourceId = sourceBinding != null ? sourceBinding.getSourceId() : "";
        final boolean aeroFlightDeckScene = CustomScreenModuleType.AERO_FLIGHT_DECK.getSerializedName().equals(module.module.getModuleType());
        final boolean aerolineDirectorScene = CustomScreenModuleType.AEROLINE_DIRECTOR.getSerializedName().equals(module.module.getModuleType());
        final boolean dieselEngineScene = CustomScreenModuleType.DIESEL_ENGINE_PAGE.getSerializedName().equals(module.module.getModuleType());
        final boolean battleTacticalV2Scene = CustomScreenModuleType.BATTLE_TACTICAL_V2.getSerializedName().equals(module.module.getModuleType());
        final boolean compositeAeroScene = aeroFlightDeckScene || aerolineDirectorScene;
        if (sourceId.isEmpty() && compositeAeroScene) {
            sourceId = aeroFlightDeckScene ? AERO_FLIGHT_DECK_SOURCE_ID : AEROLINE_DIRECTOR_SOURCE_ID;
        }
        if (sourceId == null || sourceId.isEmpty()) {
            renderFallbackCard(image, module, theme);
            BitmapFont.drawCentered(image, "Select a source", module.renderX, module.renderY + module.renderH / 2 - 4, module.renderW, theme.warn, 1);
            return;
        }
        final List<StatsDisplayPanelBE.DataChannel> scopedChannels = new ArrayList<StatsDisplayPanelBE.DataChannel>();
        if (compositeAeroScene) {
            scopedChannels.addAll(channels);
        }
        else {
            for (StatsDisplayPanelBE.DataChannel channel : channels) {
                if (matchesSource(channel.getId(), sourceId, sourceRegistry)
                        || (dieselEngineScene && isDieselFuelReserveSourceType(resolveSourceType(channel.getId(), sourceRegistry)))) {
                    scopedChannels.add(channel);
                }
            }
        }
        if (scopedChannels.isEmpty() && !compositeAeroScene && !SpecializedPanelRenderer.canRenderSourceWithoutChannels(be, sourceId)) {
            renderFallbackCard(image, module, theme);
            final boolean knownSource = sourceRegistry.containsKey(sourceId);
            BitmapFont.drawCentered(image,
                    knownSource ? "Source offline" : "Source missing",
                    module.renderX,
                    module.renderY + module.renderH / 2 - 4,
                    module.renderW,
                    knownSource ? 0xFFB36B00 : theme.warn,
                    1);
            return;
        }

        final NativeImage scratch = new NativeImage(module.renderW, module.renderH, false);
        final StatsDisplaySettings.BattleBoardPreset originalBattlePreset = be.getSettings().getBattleBoardPreset();
        final boolean originalAeroFlightDeckEnabled = be.getSettings().isAeroFlightDeckEnabled();
        final boolean originalAerolineDirectorEnabled = be.getSettings().isAerolineDirectorEnabled();
        boolean rendered = false;
        try {
            if (CustomScreenModuleType.BATTLE_SCREEN_COMPOSITION.getSerializedName().equals(module.module.getModuleType())) {
                be.getSettings().setBattleBoardPreset(parseBattlePreset(module.module.getModuleConfig().getString("boardPreset")));
            }
            if (aeroFlightDeckScene) {
                be.getSettings().setAeroFlightDeckEnabled(true);
            }
            if (aerolineDirectorScene) {
                be.getSettings().setAerolineDirectorEnabled(true);
            }
            if (battleTacticalV2Scene) {
                // Phase 1: route to dedicated Battle Tactical V2 renderer; bypasses the
                // shared source-family dispatch so the legacy battle.bridge composition
                // (BATTLE_SCREEN_COMPOSITION) remains untouched for existing worlds.
                SpecializedPanelRenderer.renderBattleTacticalV2Texture(scratch,
                        be,
                        sourceId,
                        theme,
                        be.getLevel() != null ? be.getLevel().getGameTime() : 0L);
                blit(image, scratch, module.renderX, module.renderY);
                rendered = true;
            }
            else {
                rendered = SpecializedPanelRenderer.renderSpecializedPanel(scratch,
                        be,
                        scopedChannels,
                        sourceId,
                        be.getLevel() != null ? be.getLevel().getGameTime() : 0L);
                if (rendered) {
                    blit(image, scratch, module.renderX, module.renderY);
                }
            }
        }
        finally {
            be.getSettings().setBattleBoardPreset(originalBattlePreset);
            be.getSettings().setAeroFlightDeckEnabled(originalAeroFlightDeckEnabled);
            be.getSettings().setAerolineDirectorEnabled(originalAerolineDirectorEnabled);
            scratch.close();
        }
        if (!rendered) {
            renderFallbackCard(image, module, theme);
            BitmapFont.drawCentered(image, "Scene unavailable", module.renderX, module.renderY + module.renderH / 2 - 4, module.renderW, theme.warn, 1);
        }
    }

    private static void drawModuleFrame(NativeImage image, ResolvedModule module, DisplayTheme theme, int accentColor) {
        TextureGraphRenderer.fillRect(image, module.renderX, module.renderY, module.renderW, module.renderH, TextureGraphRenderer.withAlpha(theme.tabBg, 232));
        TextureGraphRenderer.fillRect(image, module.renderX, module.renderY, module.renderW, 3, accentColor);
        TextureGraphRenderer.drawBorder(image, module.renderX, module.renderY, module.renderW, module.renderH, 1, theme.border);
    }

    private static void drawMiniCard(NativeImage image,
                                     int x,
                                     int y,
                                     int width,
                                     int height,
                                     String label,
                                     String value,
                                     DisplayTheme theme,
                                     int accentColor) {
        TextureGraphRenderer.fillRect(image, x, y, width, height, TextureGraphRenderer.withAlpha(theme.bg, 220));
        TextureGraphRenderer.drawBorder(image, x, y, width, height, 1, theme.border);
        TextureGraphRenderer.fillRect(image, x, y, width, 2, accentColor);
        BitmapFont.drawString(image, TextureGraphRenderer.truncate(label, width - 6, 1), x + 3, y + 4, theme.dim, 1);
        BitmapFont.drawCentered(image, TextureGraphRenderer.truncate(value, width - 6, 1), x, y + height / 2, width, theme.text, 1);
    }

    private static void drawBindingBadge(NativeImage image, int x, int y, ResolvedMetric metric, DisplayTheme theme) {
        final int badgeW = 50;
        TextureGraphRenderer.fillRect(image, x, y, badgeW, 10, TextureGraphRenderer.withAlpha(metric.accentColor, 220));
        TextureGraphRenderer.drawBorder(image, x, y, badgeW, 10, 1, metric.accentColor);
        BitmapFont.drawString(image, TextureGraphRenderer.truncate(metric.badgeText(), badgeW - 6, 1), x + 3, y + 1, 0xFFFFFFFF, 1);
    }

    private static void drawChip(NativeImage image, int x, int y, int width, String text, int color, DisplayTheme theme) {
        TextureGraphRenderer.fillRect(image, x, y, width, 9, TextureGraphRenderer.withAlpha(color, 220));
        TextureGraphRenderer.drawBorder(image, x, y, width, 9, 1, color);
        BitmapFont.drawCentered(image, TextureGraphRenderer.truncate(text, width - 4, 1), x, y + 1, width, theme.text, 1);
    }

    private static List<StatsDisplayPanelBE.DataChannel> resolveChannelList(CustomModuleInstance module,
                                                                            List<StatsDisplayPanelBE.DataChannel> channels,
                                                                            Map<String, SourceConfig> sourceRegistry) {
        final CustomModuleBinding sourceBinding = module.resolveBinding(CustomModuleBinding.PRIMARY_SOURCE);
        if (sourceBinding != null && sourceBinding.getMode() == CustomModuleBinding.Mode.EXPLICIT_SOURCE && !sourceBinding.getSourceId().isEmpty()) {
            final List<StatsDisplayPanelBE.DataChannel> scoped = new ArrayList<StatsDisplayPanelBE.DataChannel>();
            for (StatsDisplayPanelBE.DataChannel channel : channels) {
                if (matchesSource(channel.getId(), sourceBinding.getSourceId(), sourceRegistry)) {
                    scoped.add(channel);
                }
            }
            if (!scoped.isEmpty()) {
                return scoped;
            }
        }
        return channels;
    }

    private static ResolvedMetric resolveField(CustomModuleInstance module,
                                               String key,
                                               List<StatsDisplayPanelBE.DataChannel> channels,
                                               Map<String, SourceConfig> sourceRegistry,
                                               boolean allowFallback) {
        final CustomModuleBinding binding = module.resolveBinding(key);
        if (binding == null) {
            return ResolvedMetric.unresolved("Unbound");
        }
        if (binding.getMode() == CustomModuleBinding.Mode.EXPLICIT_SOURCE) {
            if (bindingSourceFamilyIncompatible(binding, sourceRegistry)) {
                return ResolvedMetric.incompatible(binding.getSourceId());
            }
            final StatsDisplayPanelBE.DataChannel channel = resolveExplicitChannel(binding, channels, sourceRegistry);
            if (channel != null) {
                return ResolvedMetric.fromChannel(BindingStatus.RESOLVED, channel, binding.getSourceId());
            }
            if (!binding.getSourceId().isEmpty() && sourceRegistry.containsKey(binding.getSourceId())) {
                return ResolvedMetric.offline(binding.getSourceId());
            }
            return ResolvedMetric.unresolved(binding.getSourceId().isEmpty() ? "Missing source" : binding.getSourceId());
        }
        if (binding.getMode() == CustomModuleBinding.Mode.SEMANTIC_SLOT) {
            final StatsDisplayPanelBE.DataChannel semanticMatch = resolveSemanticChannel(binding, channels, sourceRegistry);
            if (semanticMatch != null) {
                return ResolvedMetric.fromChannel(BindingStatus.FALLBACK, semanticMatch, binding.getSlot());
            }
            if (bindingRequestsDieselReserve(binding) && isDieselEngineSourceId(binding.getSourceId(), sourceRegistry)) {
                return ResolvedMetric.unresolved("Reserve missing");
            }
        }
        if (allowFallback && !channels.isEmpty()) {
            if (!binding.getSourceId().isEmpty()) {
                if (bindingSourceFamilyIncompatible(binding, sourceRegistry)) {
                    return ResolvedMetric.incompatible(binding.getSourceId());
                }
                for (StatsDisplayPanelBE.DataChannel channel : channels) {
                    if (matchesSource(channel.getId(), binding.getSourceId(), sourceRegistry)) {
                        return ResolvedMetric.fromChannel(BindingStatus.FALLBACK, channel, binding.getSourceId());
                    }
                }
                return sourceRegistry.containsKey(binding.getSourceId())
                        ? ResolvedMetric.offline(binding.getSourceId())
                        : ResolvedMetric.unresolved(binding.getSourceId());
            }
            return ResolvedMetric.fromChannel(BindingStatus.FALLBACK, channels.get(0), "Auto");
        }
        return ResolvedMetric.unresolved(binding.getSlot().isEmpty() ? "Unresolved" : binding.getSlot());
    }

    private static boolean bindingSourceFamilyIncompatible(CustomModuleBinding binding, Map<String, SourceConfig> sourceRegistry) {
        if (binding == null || binding.getSourceId().isEmpty() || binding.getSourceTypeFilter().isEmpty() || sourceRegistry == null) {
            return false;
        }
        final SourceConfig config = sourceRegistry.get(binding.getSourceId());
        return config != null && !CustomScreenSourceFamilyCatalog.matchesSourceTypeFilter(config, binding.getSourceTypeFilter());
    }

    private static StatsDisplayPanelBE.DataChannel resolveExplicitChannel(CustomModuleBinding binding,
                                                                          List<StatsDisplayPanelBE.DataChannel> channels,
                                                                          Map<String, SourceConfig> sourceRegistry) {
        if (binding.getSourceId().isEmpty()) {
            return null;
        }
        StatsDisplayPanelBE.DataChannel best = null;
        int bestScore = Integer.MIN_VALUE;
        for (StatsDisplayPanelBE.DataChannel channel : channels) {
            if (!matchesSource(channel.getId(), binding.getSourceId(), sourceRegistry)) {
                continue;
            }
            final int score = semanticScore(binding.getSlot(), binding.getMetricLabel(), binding.getSourceTypeFilter(), channel, sourceRegistry);
            if (score > bestScore) {
                bestScore = score;
                best = channel;
            }
        }
        return best;
    }

    private static StatsDisplayPanelBE.DataChannel resolveSemanticChannel(CustomModuleBinding binding,
                                                                          List<StatsDisplayPanelBE.DataChannel> channels,
                                                                          Map<String, SourceConfig> sourceRegistry) {
        StatsDisplayPanelBE.DataChannel best = null;
        int bestScore = Integer.MIN_VALUE;
        for (StatsDisplayPanelBE.DataChannel channel : channels) {
            if (!binding.getSourceId().isEmpty()
                    && !matchesSource(channel.getId(), binding.getSourceId(), sourceRegistry)
                    && !isDieselReserveSupportMatch(binding, channel, sourceRegistry)) {
                continue;
            }
            final int score = semanticScore(binding.getSlot(), binding.getMetricLabel(), binding.getSourceTypeFilter(), channel, sourceRegistry);
            if (score > bestScore) {
                bestScore = score;
                best = channel;
            }
        }
        return bestScore > 0 ? best : null;
    }

    private static int semanticScore(String slot,
                                     String metricLabel,
                                     String sourceTypeFilter,
                                     StatsDisplayPanelBE.DataChannel channel,
                                     Map<String, SourceConfig> sourceRegistry) {
        final String channelId = channel.getId().toLowerCase(Locale.ROOT);
        final String channelLabel = channel.getLabel().toLowerCase(Locale.ROOT);
        final String lowerSlot = slot != null ? slot.toLowerCase(Locale.ROOT) : "";
        final String lowerMetricLabel = metricLabel != null ? metricLabel.toLowerCase(Locale.ROOT) : "";
        final String sourceType = resolveSourceType(channel.getId(), sourceRegistry);
        final String lowerSourceFilter = sourceTypeFilter != null ? sourceTypeFilter.toLowerCase(Locale.ROOT) : "";
        final boolean dieselReserveBridge = isDieselEngineSourceType(lowerSourceFilter)
            && isDieselFuelReserveSourceType(sourceType)
            && containsDieselReserveText(lowerSlot + " " + lowerMetricLabel + " " + channelId + " " + channelLabel);
        final boolean sourceFilterMatch = CustomScreenSourceFamilyCatalog.sourceTypeMatchesFamilyFilter(sourceType, sourceTypeFilter);
        if (!lowerSourceFilter.isEmpty() && !sourceFilterMatch && !dieselReserveBridge) {
            return Integer.MIN_VALUE;
        }
        int score = 0;
        if (!lowerSlot.isEmpty() && channelId.contains(lowerSlot)) {
            score += 20;
        }
        if (!lowerSlot.isEmpty() && channelLabel.contains(lowerSlot)) {
            score += 12;
        }
        if (!lowerMetricLabel.isEmpty() && channelLabel.contains(lowerMetricLabel)) {
            score += 8;
        }
        if (!lowerSourceFilter.isEmpty() && sourceFilterMatch) {
            score += 6;
        }
        if (dieselReserveBridge) {
            score += 14;
        }
        return score;
    }

    private static boolean isDieselReserveSupportMatch(CustomModuleBinding binding,
                                                       StatsDisplayPanelBE.DataChannel channel,
                                                       Map<String, SourceConfig> sourceRegistry) {
        if (!bindingRequestsDieselReserve(binding) || !isDieselEngineSourceId(binding.getSourceId(), sourceRegistry)) {
            return false;
        }
        return isDieselFuelReserveSourceType(resolveSourceType(channel.getId(), sourceRegistry));
    }

    private static boolean bindingRequestsDieselReserve(CustomModuleBinding binding) {
        if (binding == null) {
            return false;
        }
        final String haystack = (binding.getSlot() + " " + binding.getMetricLabel() + " "
                + binding.getSourceTypeFilter() + " " + binding.getSourceTypeHint()).toLowerCase(Locale.ROOT);
        return containsDieselReserveText(haystack);
    }

    private static boolean isDieselEngineSourceId(String sourceId, Map<String, SourceConfig> sourceRegistry) {
        if (sourceId == null || sourceId.isEmpty()) {
            return false;
        }
        final SourceConfig config = sourceRegistry.get(sourceId);
        return config != null && isDieselEngineSourceType(config.getSourceType());
    }

    private static boolean isDieselEngineSourceType(String sourceType) {
        return "diesel.engine".equals(CustomScreenSourceFamilyCatalog.familyForSourceType(sourceType).getFamilyId());
    }

    private static boolean isDieselFuelReserveSourceType(String sourceType) {
        return "diesel.reserve".equals(CustomScreenSourceFamilyCatalog.familyForSourceType(sourceType).getFamilyId());
    }

    private static boolean containsDieselReserveText(String text) {
        final String lower = text != null ? text.toLowerCase(Locale.ROOT) : "";
        return lower.contains("reserve") || lower.contains("reserve_fuel") || lower.contains("fuel reserve");
    }

    private static boolean matchesSource(String channelId, String sourceId, Map<String, SourceConfig> sourceRegistry) {
        if (channelId == null || sourceId == null || sourceId.isEmpty()) {
            return false;
        }
        return sourceId.equals(extractSourceId(channelId));
    }

    private static String extractSourceId(String channelId) {
        if (channelId == null || !channelId.startsWith("src_")) {
            return "legacy";
        }
        final String rest = channelId.substring(4);
        int idx = rest.indexOf("_auto_");
        if (idx > 0) {
            return rest.substring(0, idx);
        }
        idx = rest.indexOf("_line");
        if (idx > 0) {
            return rest.substring(0, idx);
        }
        return rest;
    }

    private static void blit(NativeImage target, NativeImage source, int x, int y) {
        for (int drawX = 0; drawX < source.getWidth(); ++drawX) {
            for (int drawY = 0; drawY < source.getHeight(); ++drawY) {
                final int targetX = x + drawX;
                final int targetY = y + drawY;
                if (targetX < 0 || targetY < 0 || targetX >= target.getWidth() || targetY >= target.getHeight()) {
                    continue;
                }
                target.setPixelRGBA(targetX, targetY, source.getPixelRGBA(drawX, drawY));
            }
        }
    }

    private static StatsDisplaySettings.BattleBoardPreset parseBattlePreset(String serialized) {
        if (serialized == null || serialized.isEmpty()) {
            return StatsDisplaySettings.BattleBoardPreset.STRATEGIC_OVERVIEW;
        }
        for (StatsDisplaySettings.BattleBoardPreset preset : StatsDisplaySettings.BattleBoardPreset.values()) {
            if (preset.name().equalsIgnoreCase(serialized)) {
                return preset;
            }
        }
        return StatsDisplaySettings.BattleBoardPreset.STRATEGIC_OVERVIEW;
    }

    private static CustomScreenModuleContract moduleContract(String moduleType) {
        final CustomScreenModuleContract contract = CustomScreenModuleCatalog.getContract(moduleType);
        return contract != null ? contract : UNKNOWN_CONTRACT;
    }

    private static String resolveSourceType(String channelId, Map<String, SourceConfig> sourceRegistry) {
        String matchedSourceId = "";
        for (String sourceId : sourceRegistry.keySet()) {
            if (matchesSource(channelId, sourceId, sourceRegistry) && sourceId.length() > matchedSourceId.length()) {
                matchedSourceId = sourceId;
            }
        }
        final SourceConfig config = sourceRegistry.get(matchedSourceId);
        return config != null && config.getSourceType() != null ? config.getSourceType() : "";
    }

    private static CustomScreenLayout.VariantFamily resolveVariantFamily(int width, int height) {
        final float aspect = height <= 0 ? 1.0f : (float) width / (float) height;
        final int cols = Math.max(1, width / CustomScreenLayout.DEFAULT_GRID_SIZE);
        final int rows = Math.max(1, height / CustomScreenLayout.DEFAULT_GRID_SIZE);
        if (cols < 12 || rows < 10) {
            return CustomScreenLayout.VariantFamily.COMPACT;
        }
        if (aspect >= 1.35f && cols >= 14) {
            return CustomScreenLayout.VariantFamily.WIDE;
        }
        if (aspect < 0.85f && rows >= 14) {
            return CustomScreenLayout.VariantFamily.TALL;
        }
        if (aspect >= 0.85f && aspect < 1.35f && cols >= 12 && rows >= 12) {
            return CustomScreenLayout.VariantFamily.SQUARE;
        }
        return CustomScreenLayout.VariantFamily.BASE;
    }

    static CustomScreenLayout.VariantFamily gridFamilyForLayout(CustomScreenLayout layout, CustomScreenLayout.VariantFamily activeVariant) {
        if (layout == null || activeVariant == null || activeVariant == CustomScreenLayout.VariantFamily.BASE) {
            return CustomScreenLayout.VariantFamily.BASE;
        }
        final CustomScreenLayout.VariantLayout variantLayout = layout.getVariant(activeVariant);
        if (variantLayout != null && !variantLayout.isEmpty()) {
            return activeVariant;
        }
        for (CustomModuleInstance module : layout.getModules()) {
            if (module == null) {
                continue;
            }
            if (module.getVisibilityPolicy().get(activeVariant) != module.getVisibilityPolicy().get(CustomScreenLayout.VariantFamily.BASE)) {
                return activeVariant;
            }
        }
        return CustomScreenLayout.VariantFamily.BASE;
    }

    private static float normalizedPercent(String value) {
        if (value == null || value.isEmpty()) {
            return 0.65f;
        }
        final String digitsOnly = value.replace("%", "").replaceAll("[^0-9.-]", "");
        if (digitsOnly.isEmpty()) {
            return 0.65f;
        }
        try {
            final float parsed = Float.parseFloat(digitsOnly);
            return Math.max(0.0f, Math.min(1.0f, parsed / (value.contains("%") ? 100.0f : Math.max(1.0f, parsed))));
        }
        catch (NumberFormatException ex) {
            return 0.65f;
        }
    }

    private static float normalizedMetric(ResolvedModule module,
                                          ResolvedMetric metric,
                                          String mode,
                                          List<StatsDisplayPanelBE.DataChannel> channels,
                                          Map<String, SourceConfig> sourceRegistry,
                                          StatsDisplaySettings settings) {
        if (metric.hasChannel()) {
            final Float normalized = preferredNormalizedMetric(module, metric, mode, channels, sourceRegistry);
            return normalized != null ? clamp01(normalized) : normalizedChannelMetric(metric.channel, settings);
        }
        return normalizedPercent(metric.value);
    }

    private static Float preferredNormalizedMetric(ResolvedModule module,
                                                   ResolvedMetric metric,
                                                   String mode,
                                                   List<StatsDisplayPanelBE.DataChannel> channels,
                                                   Map<String, SourceConfig> sourceRegistry) {
        if (metric == null || !metric.hasChannel()) {
            return null;
        }
        final String sourceId = resolveMetricSourceId(module, metric);
        final String sourceType = resolveMetricSourceType(metric, sourceId, sourceRegistry);
        final String familyId = CustomScreenSourceFamilyCatalog.familyForSourceType(sourceType).getFamilyId();
        final String channelId = metric.channel.getId().toLowerCase(Locale.ROOT);
        if ("flight.velocity_sensor".equals(familyId)
                && ("rpm".equals(mode) || "dial".equals(mode) || channelId.contains("velocity"))) {
            final StatsDisplayPanelBE.DataChannel strength = findSiblingSourceChannel(sourceId, channels, sourceRegistry, "redstone_strength");
            if (strength != null) {
                return strength.getStore().getLatestValue() / 15.0f;
            }
            final StatsDisplayPanelBE.DataChannel signed = findSiblingSourceChannel(sourceId, channels, sourceRegistry, "signed_redstone");
            if (signed != null) {
                return Math.abs(signed.getStore().getLatestValue()) / 15.0f;
            }
        }
        if ("flight.altitude_sensor".equals(familyId)
                && ("altitude".equals(mode) || channelId.contains("world_height") || channelId.contains("band_"))) {
            final StatsDisplayPanelBE.DataChannel normalizedHeight = findSiblingSourceChannel(sourceId, channels, sourceRegistry, "normalized_height");
            if (normalizedHeight != null) {
                final float raw = normalizedHeight.getStore().getLatestValue();
                return raw > 1.0f ? raw / 100.0f : raw;
            }
            final StatsDisplayPanelBE.DataChannel airPressure = findSiblingSourceChannel(sourceId, channels, sourceRegistry, "air_pressure");
            if (airPressure != null) {
                final float raw = airPressure.getStore().getLatestValue();
                return raw > 1.0f ? raw / 100.0f : raw;
            }
        }
        return null;
    }

    private static String resolveMetricSourceId(ResolvedModule module, ResolvedMetric metric) {
        final CustomModuleBinding binding = module != null && module.module != null
                ? module.module.resolveBinding(CustomModuleBinding.PRIMARY_METRIC)
                : null;
        if (binding != null && binding.getSourceId() != null && !binding.getSourceId().isEmpty()) {
            return binding.getSourceId();
        }
        return metric != null && metric.channel != null ? extractSourceId(metric.channel.getId()) : "";
    }

    private static String resolveMetricSourceType(ResolvedMetric metric,
                                                  String sourceId,
                                                  Map<String, SourceConfig> sourceRegistry) {
        if (sourceRegistry != null && sourceId != null && !sourceId.isEmpty()) {
            final SourceConfig config = sourceRegistry.get(sourceId);
            if (config != null && config.getSourceType() != null && !config.getSourceType().isEmpty()) {
                return config.getSourceType();
            }
        }
        return metric != null && metric.channel != null ? resolveSourceType(metric.channel.getId(), sourceRegistry) : "";
    }

    private static StatsDisplayPanelBE.DataChannel findSiblingSourceChannel(String sourceId,
                                                                            List<StatsDisplayPanelBE.DataChannel> channels,
                                                                            Map<String, SourceConfig> sourceRegistry,
                                                                            String metricKey) {
        if (sourceId == null || sourceId.isEmpty() || channels == null || metricKey == null || metricKey.isEmpty()) {
            return null;
        }
        for (StatsDisplayPanelBE.DataChannel channel : channels) {
            if (channel == null || !matchesSource(channel.getId(), sourceId, sourceRegistry)) {
                continue;
            }
            final String channelId = channel.getId().toLowerCase(Locale.ROOT);
            final String channelLabel = channel.getLabel().toLowerCase(Locale.ROOT);
            if (channelId.contains(metricKey) || channelLabel.contains(metricKey.replace('_', ' '))) {
                return channel;
            }
        }
        return null;
    }

    private static float normalizedChannelMetric(StatsDisplayPanelBE.DataChannel channel, StatsDisplaySettings settings) {
        if (channel == null) {
            return 0.5f;
        }
        final float value = channel.getStore().getLatestValue();
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 0.5f;
        }
        float min = settings != null ? settings.getAnalogGaugeMin() : 0.0f;
        float max = settings != null ? settings.getAnalogGaugeMax() : 0.0f;
        if (min >= max) {
            min = channel.getStore().getMin();
            max = channel.getStore().getMax();
            float range = max - min;
            if (range < 0.001f) {
                range = Math.max(1.0f, Math.abs(min) * 0.1f + 1.0f);
            }
            min = (channel.getStore().getMin() >= 0.0f) ? 0.0f : (min - range * 0.1f);
            max += range * 0.1f;
        }
        final float range = max - min;
        if (range < 0.001f) {
            return 0.5f;
        }
        return clamp01((value - min) / range);
    }

    private static String gaugeMode(ResolvedModule module) {
        final CompoundTag config = module.module.getModuleConfig();
        final String configured = config.getString("visualMode");
        if (!configured.isEmpty()) {
            return configured.toLowerCase(Locale.ROOT);
        }
        return CustomScreenSourceCapability.visualModeFor(config.getString("sourceType"),
                config.getString("sectionLabel"),
                config.getString("primaryMetric"),
                config.getString("label"));
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

    private static int pulseColor(int color, long gameTime, String mode) {
        if (gameTime <= 0L || "dial".equals(mode)) {
            return color;
        }
        final int alpha = 205 + Math.floorMod((int) gameTime * 5, 46);
        return TextureGraphRenderer.withAlpha(color, alpha);
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

    private static void drawModePill(NativeImage image, int x, int y, String label, int accent, DisplayTheme theme) {
        final int width = 28;
        TextureGraphRenderer.fillRect(image, x, y, width, 9, TextureGraphRenderer.withAlpha(accent, 215));
        TextureGraphRenderer.drawBorder(image, x, y, width, 9, 1, accent);
        BitmapFont.drawCentered(image, TextureGraphRenderer.truncate(label, width - 3, 1), x, y + 1, width, theme.text, 1);
    }

    private static void drawArcStroke(NativeImage image, int cx, int cy, int radius, double startDeg, double endDeg, int color, int thickness) {
        final int steps = Math.max(18, (int) Math.abs(endDeg - startDeg) / 4);
        for (int step = 0; step <= steps; ++step) {
            final double t = step / (double) steps;
            final double angle = Math.toRadians(startDeg + (endDeg - startDeg) * t);
            final int px = cx + Math.round((float) Math.cos(angle) * radius);
            final int py = cy + Math.round((float) Math.sin(angle) * radius);
            TextureGraphRenderer.fillCircle(image, px, py, Math.max(1, thickness / 2), color);
        }
    }

    private static void drawDialTickMarks(NativeImage image,
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
            final int color = index >= 33 ? TextureGraphRenderer.withAlpha(theme.warn, major ? 210 : 150)
                    : TextureGraphRenderer.withAlpha(major ? theme.text : theme.dim, major ? 190 : 140);
            TextureGraphRenderer.drawLineAA(image, x1, y1, x2, y2, color);
        }
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static String textOr(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    private record ResolvedModule(CustomModuleInstance module,
                                  CustomScreenModuleContract contract,
                                  CustomModuleInstance.GridBounds bounds,
                                  int renderX,
                                  int renderY,
                                  int renderW,
                                  int renderH,
                                  ResolvedMetric primaryBinding,
                                  ResolvedMetric secondaryBinding,
                                  ResolvedMetric statusBinding) {
    }

    private record GridShape(int columns, int rows) {
        private static GridShape forVariant(CustomScreenLayout.VariantFamily family) {
            return switch (family) {
                case SQUARE -> new GridShape(14, 14);
                case TALL -> new GridShape(10, 16);
                case COMPACT -> new GridShape(10, 8);
                case BASE, WIDE -> new GridShape(18, 12);
            };
        }
    }

    private static final class ResolvedMetric {
        private final BindingStatus status;
        private final StatsDisplayPanelBE.DataChannel channel;
        private final String label;
        private final String value;
        private final String detail;
        private final float rawValue;
        private final int accentColor;

        private ResolvedMetric(BindingStatus status,
                               StatsDisplayPanelBE.DataChannel channel,
                               String label,
                               String value,
                               String detail,
                               float rawValue,
                               int accentColor) {
            this.status = status;
            this.channel = channel;
            this.label = label;
            this.value = value;
            this.detail = detail;
            this.rawValue = rawValue;
            this.accentColor = accentColor;
        }

        private static ResolvedMetric fromChannel(BindingStatus status, StatsDisplayPanelBE.DataChannel channel, String detail) {
            final int accent = switch (status) {
                case RESOLVED -> 0xFF37B24D;
                case FALLBACK -> 0xFFF08C00;
                case INCOMPATIBLE -> 0xFFC92A2A;
                case OFFLINE -> 0xFFB36B00;
                case UNRESOLVED -> 0xFFC92A2A;
                case NONE -> 0xFF8A8A8A;
            };
            return new ResolvedMetric(status,
                    channel,
                    channel != null ? channel.getLabel() : "Unbound",
                    channel != null ? channel.getStore().getMetricType().formatValue(channel.getStore().getLatestValue()) : "--",
                    detail,
                    channel != null ? channel.getStore().getLatestValue() : 0.0f,
                    accent);
        }

        private static ResolvedMetric unresolved(String detail) {
            return new ResolvedMetric(BindingStatus.UNRESOLVED, null, "Unbound", "--", detail, 0.0f, 0xFFC92A2A);
        }

        private static ResolvedMetric offline(String detail) {
            return new ResolvedMetric(BindingStatus.OFFLINE, null, "Offline", "--", detail, 0.0f, 0xFFB36B00);
        }

        private static ResolvedMetric incompatible(String detail) {
            return new ResolvedMetric(BindingStatus.INCOMPATIBLE, null, "Wrong Family", "--", detail, 0.0f, 0xFFC92A2A);
        }

        private boolean hasChannel() {
            return this.channel != null;
        }

        private String badgeText() {
            return switch (this.status) {
                case RESOLVED -> "Bound";
                case FALLBACK -> "Slot";
                case INCOMPATIBLE -> "Wrong";
                case OFFLINE -> "Offline";
                case UNRESOLVED -> "Missing";
                case NONE -> "Static";
            };
        }

        private String shortText() {
            return this.hasChannel() ? this.label : this.detail;
        }
    }
}