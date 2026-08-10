package com.createstats.client;

import com.createstats.blockentity.BattleScreenDisplayBE;
import com.createstats.blockentity.DamageObserverSettings;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class BattleScreenPipHudOverlay {
    private static final int HUD_WIDTH = 244;
    private static final int HUD_HEIGHT = 82;
    private static final int HUD_MARGIN = 8;
    private static final int LINE_STEP = 13;
    private static final int TITLE_BASELINE = 9;
    private static final int BODY_TOP = TITLE_BASELINE + LINE_STEP + 2;

    private static final int PANEL = 0xEA071018;
    private static final int PANEL_DARK = 0xFF101821;
    private static final int TEXT = 0xFFE7F3F5;
    private static final int TEXT_DIM = 0xFF8FA9B4;
    private static final int GREEN = 0xFF62E8A3;
    private static final int AMBER = 0xFFFFC15E;
    private static final int RED = 0xFFFF5D69;
    private static final int CYAN = 0xFF4AD9FF;
    private static final int VIOLET = 0xFFA985FF;

    @Nullable
    private static BlockPos pinnedDisplayPos;
    @Nullable
    private static ResourceKey<Level> pinnedDimension;

    private BattleScreenPipHudOverlay() {
    }

    public static void pin(BattleScreenDisplayBE display) {
        if (display == null || display.getLevel() == null) {
            return;
        }
        pinnedDisplayPos = display.getBlockPos().immutable();
        pinnedDimension = display.getLevel().dimension();
    }

    public static void clear() {
        pinnedDisplayPos = null;
        pinnedDimension = null;
    }

    public static boolean isPinned(BattleScreenDisplayBE display) {
        return display != null
                && display.getLevel() != null
                && pinnedDisplayPos != null
                && pinnedDimension != null
                && pinnedDimension.equals(display.getLevel().dimension())
                && pinnedDisplayPos.equals(display.getBlockPos());
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.level == null || pinnedDisplayPos == null || pinnedDimension == null) {
            return;
        }
        BattleScreenDisplayBE display = resolvePinnedDisplay(minecraft);
        if (display == null) {
            return;
        }
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int hudX = screenWidth - HUD_WIDTH - HUD_MARGIN;
        int hudY = screenHeight - HUD_HEIGHT - HUD_MARGIN;
        long tick = minecraft.level.getGameTime();
        float partialTick = deltaTracker != null ? deltaTracker.getGameTimeDeltaPartialTick(false) : 0.0f;
        renderHud(graphics, minecraft.font, hudX, hudY, HUD_WIDTH, HUD_HEIGHT, display, tick + partialTick);
    }

    @Nullable
    private static BattleScreenDisplayBE resolvePinnedDisplay(Minecraft minecraft) {
        if (minecraft.level == null || pinnedDisplayPos == null || pinnedDimension == null || !pinnedDimension.equals(minecraft.level.dimension())) {
            return null;
        }
        BlockEntity blockEntity = minecraft.level.getBlockEntity(pinnedDisplayPos);
        return blockEntity instanceof BattleScreenDisplayBE display && !display.isRemoved() ? display : null;
    }

    private static void renderHud(GuiGraphics graphics, Font font, int x, int y, int width, int height, BattleScreenDisplayBE display, float time) {
        List<BattleScreenDisplayBE.LinkedObserver> observers = display.getLinkedObservers();
        BattleScreenDisplayBE.LinkedObserver target = priorityObserver(observers);
        int color = target == null ? AMBER : healthColor(target.healthPercent(), target.missingBlocks());
        graphics.fill(x + 3, y + 4, x + width + 3, y + height + 4, 0x66000000);
        graphics.fill(x, y, x + width, y + height, PANEL);
        border(graphics, x, y, width, height, color);
        // Symmetric accents: 2px top + 2px bottom in the active color
        graphics.fill(x + 1, y + 1, x + width - 1, y + 3, color);
        graphics.fill(x + 1, y + height - 3, x + width - 1, y + height - 1, color);

        if (target == null) {
            graphics.drawString(font, "BATTLE PiP", x + 10, y + TITLE_BASELINE, TEXT_DIM, false);
            divider(graphics, x, y + TITLE_BASELINE + 11, width, color);
            graphics.drawString(font, "NO OBSERVER FEED", x + 10, y + BODY_TOP, AMBER, false);
            graphics.drawString(font, "Open Battle Bridge / link Display Link", x + 10, y + BODY_TOP + LINE_STEP, TEXT_DIM, false);
            return;
        }

        if (target.category() == DamageObserverSettings.ObserverCategory.WEAPONS && target.weapon().detected()) {
            renderWeaponHud(graphics, font, x, y, width, height, observers, target, time);
            return;
        }

        double overall = overallHealth(observers);
        String title = target.missingBlocks() > 0 ? "LOSS PiP" : "BATTLE PiP";
        graphics.drawString(font, title, x + 10, y + TITLE_BASELINE, TEXT_DIM, false);
        String integrity = Math.round(target.healthPercent()) + "%";
        graphics.drawString(font, integrity, x + width - font.width(integrity) - 10, y + TITLE_BASELINE, color, false);
        divider(graphics, x, y + TITLE_BASELINE + 11, width, color);

        graphics.drawString(font, trimToWidth(font, target.name(), width - 20), x + 10, y + BODY_TOP, TEXT, false);
        String lossLine = target.missingBlocks() <= 0
                ? "No block loss / ship " + Math.round(overall) + "%"
                : "Lost " + target.missingBlocks() + " of " + target.baselineBlocks() + " blocks / ship " + Math.round(overall) + "%";
        graphics.drawString(font, trimToWidth(font, lossLine, width - 20), x + 10, y + BODY_TOP + LINE_STEP, target.missingBlocks() > 0 ? RED : GREEN, false);

        int barX = x + 10;
        int barY = y + height - 11;
        int barWidth = width - 20;
        graphics.fill(barX, barY, barX + barWidth, barY + 4, PANEL_DARK);
        graphics.fill(barX, barY, barX + Math.max(2, (int) (barWidth * target.healthPercent() / 100.0)), barY + 4, color);
        if (target.missingBlocks() > 0) {
            int lossWidth = Math.max(2, Math.min(barWidth, barWidth * target.missingBlocks() / Math.max(1, target.baselineBlocks())));
            graphics.fill(barX + barWidth - lossWidth, barY, barX + barWidth, barY + 4, RED);
        }
        int scanTop = y + BODY_TOP - 2;
        int scanBot = y + height - 13;
        int scanX = x + 6 + (int) ((width - 12) * ((time * 0.035f) % 1.0f));
        graphics.fill(scanX, scanTop, scanX + 1, scanBot, 0x884AD9FF);
    }

    private static void renderWeaponHud(GuiGraphics graphics, Font font, int x, int y, int width, int height, List<BattleScreenDisplayBE.LinkedObserver> observers, BattleScreenDisplayBE.LinkedObserver target, float time) {
        BattleScreenDisplayBE.WeaponSnapshot weapon = target.weapon();
        int statusColor = weaponColor(weapon);
        String title = weaponRiskScore(target) > 0 ? "WEAPON ALERT" : "WEAPON PiP";
        graphics.drawString(font, title, x + 10, y + TITLE_BASELINE, TEXT_DIM, false);
        String integrity = Math.round(target.healthPercent()) + "%";
        graphics.drawString(font, integrity, x + width - font.width(integrity) - 10, y + TITLE_BASELINE, statusColor, false);
        divider(graphics, x, y + TITLE_BASELINE + 11, width, statusColor);

        graphics.drawString(font, trimToWidth(font, target.name(), width - 20), x + 10, y + BODY_TOP, TEXT, false);
        String statusLine = weapon.readiness() + " / " + weapon.loadedState() + " / ship " + Math.round(overallHealth(observers)) + "%";
        graphics.drawString(font, trimToWidth(font, statusLine, width - 20), x + 10, y + BODY_TOP + LINE_STEP, statusColor, false);
        graphics.drawString(font, trimToWidth(font, weaponWarningLine(weapon), width - 20), x + 10, y + BODY_TOP + LINE_STEP * 2, weaponRiskScore(target) > 0 ? RED : GREEN, false);

        int barX = x + 10;
        int barY = y + height - 11;
        int barWidth = width - 20;
        int baseline = Math.max(1, weapon.baselineBlocks());
        graphics.fill(barX, barY, barX + barWidth, barY + 4, PANEL_DARK);
        graphics.fill(barX, barY, barX + Math.max(2, Math.min(barWidth, barWidth * weapon.presentBlocks() / baseline)), barY + 4, statusColor);
        if (weapon.projectileBlocks() > 0 || weapon.propellantBlocks() > 0) {
            int payloadWidth = Math.max(2, Math.min(barWidth, barWidth * (weapon.projectileBlocks() + weapon.propellantBlocks()) / Math.max(1, weapon.blocks())));
            // payload pip sits in the 3px gutter between text and the bar
            graphics.fill(barX, barY - 4, barX + payloadWidth, barY - 2, weapon.projectileBlocks() > 0 ? VIOLET : AMBER);
        }
        int critical = weapon.missingBarrelBlocks() + weapon.missingBreechBlocks() + weapon.missingMountBlocks();
        if (critical > 0 || weapon.missingBlocks() > 0) {
            int lossWidth = Math.max(2, Math.min(barWidth, barWidth * Math.max(critical, weapon.missingBlocks()) / baseline));
            graphics.fill(barX + barWidth - lossWidth, barY, barX + barWidth, barY + 4, RED);
        }
        int scanTop = y + BODY_TOP - 2;
        int scanBot = y + height - 13;
        int scanX = x + 6 + (int) ((width - 12) * ((time * 0.04f) % 1.0f));
        graphics.fill(scanX, scanTop, scanX + 1, scanBot, 0x884AD9FF);
    }

    @Nullable
    private static BattleScreenDisplayBE.LinkedObserver priorityObserver(List<BattleScreenDisplayBE.LinkedObserver> observers) {
        if (observers == null || observers.isEmpty()) {
            return null;
        }
        BattleScreenDisplayBE.LinkedObserver weaponAlert = observers.stream()
                .filter(observer -> observer.category() == DamageObserverSettings.ObserverCategory.WEAPONS && observer.weapon().detected())
                .max(Comparator
                        .comparingInt(BattleScreenPipHudOverlay::weaponRiskScore)
                        .thenComparingDouble(observer -> 100.0 - observer.healthPercent()))
                .orElse(null);
        if (weaponAlert != null && weaponRiskScore(weaponAlert) > 0) {
            return weaponAlert;
        }
        return observers.stream()
                .max(Comparator
                        .comparingInt((BattleScreenDisplayBE.LinkedObserver observer) -> observer.missingBlocks() * 4 + observer.newBlocks())
                        .thenComparingDouble(observer -> 100.0 - observer.healthPercent())
                        .thenComparingLong(observer -> observer.cargoItems() + observer.cargoFluidAmount() / 1000L + observer.cargoEnergyStored() / 1000L))
                .orElse(null);
    }

    private static int weaponRiskScore(BattleScreenDisplayBE.LinkedObserver observer) {
        BattleScreenDisplayBE.WeaponSnapshot weapon = observer.weapon();
        int score = weapon.missingBarrelBlocks() * 18 + weapon.missingBreechBlocks() * 22 + weapon.missingMountBlocks() * 18 + weapon.missingLoaderBlocks() * 8;
        score += weapon.missingBlocks() * 5 + observer.missingBlocks() * 3;
        score += switch (weapon.readiness()) {
            case "OFFLINE" -> 36;
            case "DAMAGED" -> 28;
            case "LOADING" -> 8;
            case "UNKNOWN" -> 4;
            default -> 0;
        };
        if ("EMPTY".equals(weapon.loadedState())) {
            score += 3;
        }
        return score;
    }

    private static int weaponColor(BattleScreenDisplayBE.WeaponSnapshot weapon) {
        return switch (weapon.readiness()) {
            case "READY" -> GREEN;
            case "LOADING", "UNKNOWN" -> AMBER;
            case "DAMAGED", "OFFLINE" -> RED;
            default -> TEXT_DIM;
        };
    }

    private static String weaponWarningLine(BattleScreenDisplayBE.WeaponSnapshot weapon) {
        if (weapon.missingBreechBlocks() > 0) {
            return "Breech missing / M" + weapon.missingBreechBlocks();
        }
        if (weapon.missingBarrelBlocks() > 0) {
            return "Barrel loss / M" + weapon.missingBarrelBlocks();
        }
        if (weapon.missingMountBlocks() > 0) {
            return "Mount offline / M" + weapon.missingMountBlocks();
        }
        if (weapon.missingLoaderBlocks() > 0) {
            return "Loader service / M" + weapon.missingLoaderBlocks();
        }
        if ("EMPTY".equals(weapon.loadedState())) {
            return "Structure ready / payload empty";
        }
        if ("LOADING".equals(weapon.loadedState())) {
            return "Loader motion detected";
        }
        return "Weapon channel nominal";
    }

    private static double overallHealth(List<BattleScreenDisplayBE.LinkedObserver> observers) {
        if (observers == null || observers.isEmpty()) {
            return 100.0;
        }
        int baseline = 0;
        int present = 0;
        double sum = 0.0;
        for (BattleScreenDisplayBE.LinkedObserver observer : observers) {
            baseline += observer.baselineBlocks();
            present += observer.presentBaselineBlocks();
            sum += observer.healthPercent();
        }
        return baseline > 0 ? clamp(present * 100.0 / baseline, 0.0, 100.0) : sum / observers.size();
    }

    private static int healthColor(double healthPercent, int missingBlocks) {
        if (healthPercent >= 85.0 && missingBlocks == 0) {
            return GREEN;
        }
        if (healthPercent >= 55.0) {
            return AMBER;
        }
        return RED;
    }

    private static String trimToWidth(Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty() || font.width(text) <= maxWidth) {
            return text == null ? "" : text;
        }
        final int ellipsisWidth = font.width("...");
        String trimmed = font.plainSubstrByWidth(text, Math.max(0, maxWidth - ellipsisWidth));
        return trimmed + "...";
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void border(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private static void divider(GuiGraphics graphics, int panelX, int dividerY, int panelWidth, int accent) {
        // Subtle horizontal rule below the header row.
        graphics.fill(panelX + 8, dividerY, panelX + panelWidth - 8, dividerY + 1, 0x33FFFFFF);
        graphics.fill(panelX + 8, dividerY, panelX + 8 + Math.min(28, panelWidth - 16), dividerY + 1, (accent & 0x00FFFFFF) | 0x66000000);
    }
}