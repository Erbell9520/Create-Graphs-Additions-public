package com.createstats.client;

import com.createstats.CreateStats;
import com.createstats.blockentity.BattleScreenDisplayBE;
import com.createstats.flight.client.FccClientState;
import com.createstats.flight.network.FccGuiControlEntry;
import com.createstats.flight.network.FccGuiSnapshot;
import com.createstats.flight.network.FccTelemetryPacket;
import com.createstats.item.PilotHelmetData;
import com.createstats.item.PilotHelmetItem;
import com.createstats.network.PilotHelmetFccSubscriptionPacket;
import com.createstats.registry.ModItems;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@OnlyIn(Dist.CLIENT)
public final class PilotHelmetHudOverlay {
    private static final double MAX_LINK_DISTANCE_SQR = 256.0D * 256.0D;
    /** Telemetry older than this is treated as a lost stream (dashes + SIGNAL STALE banner). */
    private static final double TELEMETRY_STALE_SECONDS = 1.5D;
    private static final int BATTLE_STRIP_WIDTH = 180;
    private static final int BATTLE_STRIP_HEIGHT = 28;
    private static final int HUD = 0xFFB8FFE8;
    private static final int HUD_DIM = 0xC07FE2C8;
    private static final int HUD_TEXT = 0xFFE8FFF4;
    private static final int TARGET = 0xFFFF5B66;
    private static final int TARGET_DIM = 0xC0FF5B66;
    private static final int WARN = 0xFFFFC965;
    private static final int OK = 0xFF78FFC4;
    private static final int CHIP_BG = 0x80050810;
    private static final float HIT_CHANCE_LOCK_THRESHOLD = 0.90f;

    @Nullable
    private static BlockPos subscribedFccPos;

    // Cached previous velocity sample for G-load derivation.
    private static long gLoadPrevTimeNanos;
    private static float gLoadPrevVelX;
    private static float gLoadPrevVelY;
    private static float gLoadPrevVelZ;
    private static float gLoadSmoothed;

    private PilotHelmetHudOverlay() {
    }

    public static void tick() {
        // Flush staged OTOMOBIL stat deltas from the client tick, not the render loop. The call
        // no-ops unless dirty and is internally throttled to one write per 5 s.
        com.createstats.client.hud.OtomobilPersistedStats.saveThrottled();
        Minecraft minecraft = Minecraft.getInstance();
        HelmetContext context = resolveContext(minecraft);
        BlockPos desiredFccPos = context != null ? context.fccPos() : null;
        if ((subscribedFccPos == null && desiredFccPos == null)
                || (subscribedFccPos != null && subscribedFccPos.equals(desiredFccPos))) {
            FccClientState.pinHelmetHud(desiredFccPos);
            return;
        }
        if (subscribedFccPos != null) {
            PacketDistributor.sendToServer(new PilotHelmetFccSubscriptionPacket(subscribedFccPos, false));
        }
        subscribedFccPos = desiredFccPos == null ? null : desiredFccPos.immutable();
        FccClientState.pinHelmetHud(subscribedFccPos);
        if (subscribedFccPos != null) {
            PacketDistributor.sendToServer(new PilotHelmetFccSubscriptionPacket(subscribedFccPos, true));
        }
    }

    public static void clear() {
        subscribedFccPos = null;
        FccClientState.pinHelmetHud(null);
    }

    public static boolean isActive() {
        return resolveContext(Minecraft.getInstance()) != null;
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        // [1]
        if (minecraft.screen == null) {
            renderNightVisionOverlayIfActive(graphics, minecraft);
        }

        HelmetContext context = resolveContext(minecraft);
        if (context == null || minecraft.screen != null) {
            // [2]
            if (minecraft.screen == null && PilotHelmetVisorState.isLocalVisorDeployed()) {
                renderVisorStandbyBadge(graphics, minecraft);
            }
            return;
        }

        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        int centerX = width / 2;
        int centerY = height / 2 + 8;
        Camera camera = minecraft.gameRenderer.getMainCamera();

        FccClientState.HelmetTelemetryFrame telemetryFrame = FccClientState.helmetTelemetryFrame();
        FccTelemetryPacket telemetry = telemetryFrame != null ? telemetryFrame.packet() : FccClientState.helmetTelemetry();
        FccGuiSnapshot snapshot = FccClientState.helmetSnapshot();
        boolean hasTelemetry = telemetryFrame != null || telemetry != null;
        // [3]
        boolean telemetryStale = hasTelemetry
            && FccClientState.helmetTelemetryAgeSeconds() > TELEMETRY_STALE_SECONDS;
        boolean craftDataLive = hasTelemetry && !telemetryStale;
        float craftHeading = telemetryFrame != null ? telemetryFrame.headingDeg()
            : telemetry != null ? telemetry.headingDeg() : fallbackHeading(context.player());
        float craftPitch = telemetryFrame != null ? -telemetryFrame.pitchDeg()
            : telemetry != null ? -telemetry.pitchDeg() : 0.0f;
        float roll = telemetryFrame != null ? telemetryFrame.rollDeg()
            : telemetry != null ? telemetry.rollDeg() : 0.0f;
        float viewHeading = camera != null ? wrap360(camera.getYRot()) : wrap360(craftHeading);
        float viewPitch = camera != null ? -camera.getXRot() : -context.player().getXRot();
        float displayPitch = craftPitch + viewPitch;
        float displayVelX = telemetryFrame != null ? telemetryFrame.velX() : telemetry != null ? telemetry.velX() : 0.0f;
        float displayVelY = telemetryFrame != null ? telemetryFrame.velY() : telemetry != null ? telemetry.velY() : 0.0f;
        float displayVelZ = telemetryFrame != null ? telemetryFrame.velZ() : telemetry != null ? telemetry.velZ() : 0.0f;
        // [4]
        double displaySpeed = craftDataLive
            ? Math.sqrt(displayVelX * displayVelX + displayVelY * displayVelY + displayVelZ * displayVelZ)
            : Double.NaN;
        double displayAltitude = !craftDataLive ? Double.NaN
            : telemetryFrame != null ? telemetryFrame.altitude() : telemetry.altitude();
        double displayClimb = !craftDataLive ? Double.NaN
            : telemetryFrame != null ? telemetryFrame.climbRate() : telemetry.climbRate();

        float gLoad = updateGLoad(craftDataLive, displayVelX, displayVelY, displayVelZ);

        if (context.vignetteStrength() > 0) {
            renderCanopyVignette(graphics, width, height, context.vignetteStrength());
        }

        // [5]
        float hudScale = Math.max(1.0f, Math.min(3.0f, context.hudScaleFactor()));

        if (context.mode() == PilotHelmetData.Mode.OTOMOBIL || context.mode() == PilotHelmetData.Mode.TANK) {
            // TANK gets the ground-vehicle HUD in tank trim (per-side track power instead of a
            // steering wheel) rather than silently reusing the jet-fighter attitude symbology.
            com.createstats.client.hud.OtomobilHud.render(
                graphics, minecraft, context.statsDisplayPos(),
                width, height, centerX, centerY, hudScale,
                telemetry, snapshot, craftDataLive, telemetryStale,
                context.mode() == PilotHelmetData.Mode.TANK,
                viewHeading, craftHeading,
                displayVelX, displayVelY, displayVelZ,
                deltaTracker);
            return;
        }

        if (context.mode() == PilotHelmetData.Mode.COMMERCIAL) {
            com.createstats.client.hud.CommercialHud.render(
                graphics, minecraft, width, height, centerX, centerY, hudScale,
                telemetry, snapshot, craftDataLive, telemetryStale,
                viewHeading, craftHeading, displayPitch, roll,
                displaySpeed, displayAltitude, displayClimb, gLoad);
            return;
        }

        scaleAt(graphics, centerX, 18, hudScale, () ->
            renderHeadingTape(graphics, minecraft.font, centerX, 18, Math.min(width - 160, 260), viewHeading));
        scaleAt(graphics, centerX, 42, hudScale, () ->
            renderBankPointer(graphics, minecraft.font, centerX, 42, roll));
        scaleAt(graphics, centerX, centerY, hudScale, () -> {
            renderPitchLadder(graphics, minecraft.font, centerX, centerY, displayPitch, roll);
            renderFlightPathMarker(graphics, centerX, centerY, viewHeading, displayPitch, craftDataLive, displayVelX, displayVelY, displayVelZ);
            renderCraftReference(graphics, centerX, centerY);
        });
        if (!craftDataLive) {
            int bannerY = centerY - 46;
            String bannerText = hasTelemetry ? "SIGNAL STALE" : "NO SIGNAL";
            scaleAt(graphics, centerX, bannerY, hudScale, () ->
                renderNoSignalBanner(graphics, minecraft.font, centerX, bannerY, bannerText));
        }
        scaleAt(graphics, 18, 18, hudScale, () ->
            renderFccStrip(graphics, minecraft.font, 18, 18, telemetry, snapshot));
        // [6]
        java.util.List<com.createstats.client.hud.HudWarnings.Entry> figCautions =
            com.createstats.client.hud.HudWarnings.collect(snapshot);
        if (!figCautions.isEmpty()) {
            scaleAt(graphics, 18, 66, hudScale, () ->
                com.createstats.client.hud.HudWarnings.render(graphics, minecraft.font, 18, 66, figCautions, 4));
        }
        // Clamp using the SCALED footprint so the strip stays on-screen at high HUD scale
        // (scaleAt grows it around its top-left anchor by up to 3x).
        int scaledStripW = Math.round(BATTLE_STRIP_WIDTH * hudScale);
        int scaledStripH = Math.round(BATTLE_STRIP_HEIGHT * hudScale);
        int battleStripX = Mth.clamp(centerX + 72, 18, Math.max(18, width - scaledStripW - 18));
        int battleStripY = Mth.clamp(centerY - 72, 18, Math.max(18, height - scaledStripH - 18));
        scaleAt(graphics, battleStripX, battleStripY, hudScale, () ->
            renderBattleStrip(graphics, minecraft.font, battleStripX, battleStripY, context.battleObserver()));
        // Battle symbology stays unscaled, chevrons are world-projected and must stay
        // pinned to the real-world target positions regardless of HUD scale.
        renderBattleSymbology(graphics, minecraft, width, height, context.battleObserver());
        // Side telemetry: split chips so each side grows around its OWN edge anchor.
        int sideLeftX = 18;
        int sideRightX = width - 74;
        int sideY = centerY - 16;
        scaleAt(graphics, sideLeftX, sideY, hudScale, () ->
            renderSpeedClimbChip(graphics, minecraft.font, sideLeftX, sideY, displaySpeed, displayClimb));
        scaleAt(graphics, sideRightX, sideY, hudScale, () ->
            renderAltGChip(graphics, minecraft.font, sideRightX, sideY, displayAltitude, gLoad, craftDataLive));
        scaleAt(graphics, centerX, height - 18, hudScale, () ->
            renderStatusLine(graphics, minecraft.font, centerX, height - 18, telemetry, context.battleObserver()));
    }

    /**
     * Push a pose that scales drawing around the (anchorX, anchorY) screen pixel, run
     * the draw block, then pop. At scale==1 this is a no-op fast path.
     */
    private static void scaleAt(GuiGraphics graphics, float anchorX, float anchorY, float scale, Runnable draw) {
        if (Math.abs(scale - 1.0f) < 0.001f) {
            draw.run();
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(anchorX, anchorY, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.pose().translate(-anchorX, -anchorY, 0);
        draw.run();
        graphics.pose().popPose();
    }

    /** [7] */
    private static void renderVisorStandbyBadge(GuiGraphics graphics, Minecraft minecraft) {
        final Font font = minecraft.font;
        final String label = "\u25bc VISOR DOWN";
        final int width = minecraft.getWindow().getGuiScaledWidth();
        final int height = minecraft.getWindow().getGuiScaledHeight();
        final int textW = font.width(label);
        final int x = (width - textW) / 2;
        final int y = height - 30;
        graphics.fill(x - 5, y - 4, x + textW + 5, y + 11, CHIP_BG);
        graphics.drawString(font, label, x, y, HUD, false);
    }

    @Nullable
    private static HelmetContext resolveContext(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            return null;
        }
        Player player = minecraft.player;
        ItemStack helmetStack = player.getItemBySlot(EquipmentSlot.HEAD);
        if (helmetStack.isEmpty() || !helmetStack.is(ModItems.PILOT_HELMET.get())) {
            return null;
        }
        PilotHelmetData data = PilotHelmetItem.getData(helmetStack);
        PilotHelmetData.Mode mode = data.getMode();
        // [8]
        if (!PilotHelmetVisorState.effectiveDeployed(player, helmetStack)) {
            return null;
        }

        BlockPos fccPos = resolveFccPos(minecraft.level, player, data.getFccLink());
        BattleScreenDisplayBE battleDisplay = resolveBattleDisplay(minecraft.level, player, data.getBattleBridgeLink());
        BattleScreenDisplayBE.LinkedObserver battleObserver = priorityWeaponObserver(battleDisplay);
        BlockPos statsDisplayPos = isLinkAvailable(minecraft.level, player, data.getStatsDisplayLink())
            ? data.getStatsDisplayLink().getTargetPos() : null;
        if (mode == PilotHelmetData.Mode.FIGHTER) {
            if (fccPos == null && battleDisplay == null) {
                return null;
            }
        } else if (mode == PilotHelmetData.Mode.OTOMOBIL || mode == PilotHelmetData.Mode.TANK) {
            // [9]
            if (fccPos == null && statsDisplayPos == null) {
                return null;
            }
        } else { // COMMERCIAL - airliner glass cockpit, driven by FCC flight telemetry
            if (fccPos == null) {
                return null;
            }
        }
        return new HelmetContext(player, mode, fccPos, battleDisplay, battleObserver, statsDisplayPos,
            data.getVignetteStrength(), data.getHudScaleFactor());
    }

    @Nullable
    private static BlockPos resolveFccPos(Level level, Player player, PilotHelmetData.LinkedNode link) {
        if (!isLinkAvailable(level, player, link)) {
            return null;
        }
        return link.getTargetPos();
    }

    @Nullable
    private static BattleScreenDisplayBE resolveBattleDisplay(Level level, Player player, PilotHelmetData.LinkedNode link) {
        if (!isLinkAvailable(level, player, link)) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(link.getTargetPos());
        return blockEntity instanceof BattleScreenDisplayBE display && !display.isRemoved() ? display : null;
    }

    private static boolean isLinkAvailable(Level level, Player player, PilotHelmetData.LinkedNode link) {
        if (link == null || !link.isLinked() || link.getTargetPos() == null || link.getDimensionId() == null) {
            return false;
        }
        if (!level.dimension().location().equals(link.getDimensionId())) {
            return false;
        }
        BlockPos pos = link.getTargetPos();
        return player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= MAX_LINK_DISTANCE_SQR;
    }

    @Nullable
    private static BattleScreenDisplayBE.LinkedObserver priorityWeaponObserver(@Nullable BattleScreenDisplayBE display) {
        if (display == null) {
            return null;
        }
        List<BattleScreenDisplayBE.LinkedObserver> observers = display.getLinkedObservers();
        if (observers == null || observers.isEmpty()) {
            return null;
        }
        return observers.stream()
            .filter(observer -> observer.weapon().detected())
            .max(Comparator
                .comparingInt((BattleScreenDisplayBE.LinkedObserver observer) -> observer.weapon().autoAim().targetLocked() ? 3
                    : observer.weapon().autoAim().targetPresent() ? 2 : observer.weapon().autoFireEnabled() ? 1 : 0)
                .thenComparingInt(observer -> observer.weapon().ammoRounds())
                .thenComparingDouble(observer -> observer.healthPercent()))
            .orElse(null);
    }

    private static void renderFccStrip(GuiGraphics graphics, Font font, int x, int y,
                                       @Nullable FccTelemetryPacket telemetry, @Nullable FccGuiSnapshot snapshot) {
        drawFrame(graphics, x, y, 184, 41, HUD_DIM);
        graphics.drawString(font, "FCC", x + 6, y + 3, HUD, false);
        if (telemetry == null && snapshot == null) {
            graphics.drawString(font, "LINKING", x + 44, y + 3, WARN, false);
            return;
        }
        String thrust = formatSignedPercent(computeThrustPercent(telemetry));
        String lift = formatOptionalPercent(extractLiftPercent(snapshot));
        String autoMode = snapshot == null ? "--" : trim(font, snapshot.mode().name().replace('_', ' '), 52);
        graphics.drawString(font, "THR " + thrust, x + 6, y + 14, HUD_TEXT, false);
        graphics.drawString(font, "LIFT " + lift, x + 58, y + 14, HUD_TEXT, false);
        graphics.drawString(font, "MODE " + autoMode, x + 116, y + 14, HUD_TEXT, false);
        // [10]
        if (snapshot != null) {
            boolean armed = snapshot.mode() != com.createstats.flight.FlightMode.OFF;
            graphics.drawString(font, armed ? "ARMED" : "SAFE", x + 6, y + 27, armed ? OK : HUD_DIM, false);
            Float trimPct = extractTrimPercent(snapshot);
            String trimText = trimPct == null ? "TRM --"
                : String.format(Locale.ROOT, "TRM %+.0f%%", trimPct);
            graphics.drawString(font, trimText, x + 58, y + 27, HUD_TEXT, false);
            int active = countActiveControls(snapshot);
            graphics.drawString(font, active + " CTL ACT", x + 116, y + 27,
                active > 0 ? HUD_TEXT : HUD_DIM, false);
        }
    }

    /** Signed trim percent from the first control labelled with "trim", or null when absent. */
    @Nullable
    private static Float extractTrimPercent(FccGuiSnapshot snapshot) {
        for (FccGuiControlEntry entry : snapshot.controlEntries()) {
            String label = entry.label() == null ? "" : entry.label().toLowerCase(Locale.ROOT);
            if (label.contains("trim")) {
                return normalizeSigned(entry) * 100.0f;
            }
        }
        return null;
    }

    /** Count of cockpit controls currently deflected off their neutral value. */
    private static int countActiveControls(FccGuiSnapshot snapshot) {
        int active = 0;
        for (FccGuiControlEntry entry : snapshot.controlEntries()) {
            if (Math.abs(normalizeSigned(entry)) > 0.02f) active++;
        }
        return active;
    }

    private static void renderBattleStrip(GuiGraphics graphics, Font font, int x, int y,
                                          @Nullable BattleScreenDisplayBE.LinkedObserver observer) {
        int gap = 6;
        int cellWidth = (BATTLE_STRIP_WIDTH - gap * 2) / 3;
        BattleScreenDisplayBE.WeaponSnapshot weapon = observer != null ? observer.weapon() : null;
        BattleScreenDisplayBE.AutoAimSnapshot autoAim = weapon != null ? weapon.autoAim() : null;

        renderBattleCell(graphics, font, x, y, cellWidth, "HULL",
            observer == null ? "--" : Math.round(observer.healthPercent()) + "%",
            observer == null ? HUD_DIM : battleHullColor(observer));
        renderBattleCell(graphics, font, x + cellWidth + gap, y, cellWidth, "GUN",
            battleGunText(weapon), battleGunColor(weapon));
        renderBattleCell(graphics, font, x + (cellWidth + gap) * 2, y, cellWidth, "TRK",
            battleTrackText(autoAim), battleTrackColor(autoAim));
    }

    private static void renderBattleCell(GuiGraphics graphics, Font font, int x, int y, int width,
                                         String label, String value, int valueColor) {
        graphics.fill(x, y, x + width, y + BATTLE_STRIP_HEIGHT, 0x22040A10);
        graphics.fill(x, y, x + width, y + 1, 0x343A4C54);
        graphics.fill(x, y + BATTLE_STRIP_HEIGHT - 1, x + width, y + BATTLE_STRIP_HEIGHT, 0x22000000);
        graphics.drawString(font, label, x + 4, y + 3, HUD_DIM, false);
        graphics.drawString(font, trim(font, value, width - 8), x + 4, y + 15, valueColor, false);
    }

    private static String battleGunText(@Nullable BattleScreenDisplayBE.WeaponSnapshot weapon) {
        if (weapon == null || !weapon.detected()) {
            return "STBY";
        }
        if (weapon.missingBlocks() > 0) {
            return "DMG";
        }
        if (!weapon.autoAim().blockedReason().isBlank()) {
            return "NO FIRE";
        }
        String readiness = weapon.readiness().toUpperCase(Locale.ROOT);
        if (readiness.contains("READY")) {
            return "READY";
        }
        if (readiness.contains("LOAD")) {
            return "LOAD";
        }
        if (readiness.contains("OFF")) {
            return "OFF";
        }
        if (readiness.contains("DAM")) {
            return "DMG";
        }
        return readiness.isBlank() ? "ARM" : readiness;
    }

    private static int battleGunColor(@Nullable BattleScreenDisplayBE.WeaponSnapshot weapon) {
        if (weapon == null || !weapon.detected()) {
            return HUD_DIM;
        }
        if (weapon.missingBlocks() > 0) {
            return TARGET;
        }
        if (!weapon.autoAim().blockedReason().isBlank()) {
            return WARN;
        }
        return weapon.readiness().equalsIgnoreCase("READY") ? OK : HUD_TEXT;
    }

    private static String battleTrackText(@Nullable BattleScreenDisplayBE.AutoAimSnapshot autoAim) {
        if (autoAim == null || !autoAim.mountAvailable()) {
            return "STBY";
        }
        if (!autoAim.mountRunning()) {
            return "IDLE";
        }
        if (autoAim.targetLocked()) {
            return "LOCK";
        }
        if (autoAim.targetPresent()) {
            return "ALIGN";
        }
        String status = autoAim.statusText().toUpperCase(Locale.ROOT);
        if (status.contains("LOST")) {
            return "LOST";
        }
        if (status.contains("RANGE")) {
            return "RANGE";
        }
        if (status.contains("INVALID")) {
            return "BAD";
        }
        return "SEARCH";
    }

    private static int battleTrackColor(@Nullable BattleScreenDisplayBE.AutoAimSnapshot autoAim) {
        if (autoAim == null || !autoAim.mountAvailable()) {
            return HUD_DIM;
        }
        if (!autoAim.mountRunning()) {
            return WARN;
        }
        if (autoAim.targetLocked()) {
            return OK;
        }
        if (autoAim.targetPresent()) {
            return WARN;
        }
        String status = autoAim.statusText().toUpperCase(Locale.ROOT);
        return status.contains("LOST") || status.contains("INVALID") ? WARN : HUD_TEXT;
    }

    private static int battleHullColor(BattleScreenDisplayBE.LinkedObserver observer) {
        double health = observer.healthPercent();
        if (health <= 35.0f) {
            return TARGET;
        }
        if (health <= 65.0f) {
            return WARN;
        }
        return HUD_TEXT;
    }

    private static void renderHeadingTape(GuiGraphics graphics, Font font, int centerX, int y, int width, float heading) {
        int halfWidth = Math.max(80, width / 2);
        drawLine(graphics, centerX - halfWidth, y + 12, centerX + halfWidth, y + 12, HUD);
        drawLine(graphics, centerX - halfWidth, y + 13, centerX + halfWidth, y + 13, HUD_DIM);
        int base = Mth.floor(((wrap360(heading) - 60.0f) / 10.0f)) * 10;
        for (int mark = base; mark <= base + 120; mark += 10) {
            float wrapped = wrap360(mark);
            float delta = Mth.wrapDegrees(wrapped - heading);
            int tickX = centerX + Math.round(delta * 3.0f);
            if (Math.abs(tickX - centerX) > halfWidth) {
                continue;
            }
            int tickTop = y + ((mark % 30 == 0) ? 0 : 5);
            drawLine(graphics, tickX, tickTop, tickX, y + 13, mark % 30 == 0 ? HUD : HUD_DIM);
            if (mark % 30 == 0) {
                String label = compassLabel(wrapped);
                graphics.drawString(font, label, tickX - font.width(label) / 2, y, HUD_TEXT, false);
            }
        }
        drawLine(graphics, centerX, y + 12, centerX, y + 18, HUD);
        String headingLabel = String.format(Locale.ROOT, "%03d", Math.round(wrap360(heading)) % 360);
        int labelX = centerX - font.width(headingLabel) / 2;
        drawChip(graphics, labelX - 3, y + 19, font.width(headingLabel) + 6, 12);
        graphics.drawString(font, headingLabel, labelX, y + 20, HUD_TEXT, true);
    }

    private static void renderPitchLadder(GuiGraphics graphics, Font font, int centerX, int centerY, float pitchDeg, float rollDeg) {
        float rollRad = (float) Math.toRadians(rollDeg);
        float cos = Mth.cos(rollRad);
        float sin = Mth.sin(rollRad);
        for (int mark = -60; mark <= 60; mark += 10) {
            if (mark == 0) {
                continue;
            }
            float relativePitch = mark - pitchDeg;
            float offsetY = -relativePitch * 3.4f;
            int halfLength = Math.abs(mark) % 20 == 0 ? 32 : 22;
            int x1 = rotateX(-halfLength, offsetY, cos, sin, centerX);
            int y1 = rotateY(-halfLength, offsetY, cos, sin, centerY);
            int x2 = rotateX(halfLength, offsetY, cos, sin, centerX);
            int y2 = rotateY(halfLength, offsetY, cos, sin, centerY);
            int color = Math.abs(mark) % 20 == 0 ? HUD : HUD_DIM;
            drawLine(graphics, x1, y1, x2, y2, color);
            String label = Integer.toString(Math.abs(mark));
            graphics.drawString(font, label, x1 - font.width(label) - 6, y1 - 4, color, false);
            graphics.drawString(font, label, x2 + 6, y2 - 4, color, false);
        }

        int hx1 = rotateX(-42, pitchDeg * 3.4f, cos, sin, centerX);
        int hy1 = rotateY(-42, pitchDeg * 3.4f, cos, sin, centerY);
        int hx2 = rotateX(42, pitchDeg * 3.4f, cos, sin, centerX);
        int hy2 = rotateY(42, pitchDeg * 3.4f, cos, sin, centerY);
        drawLine(graphics, hx1, hy1, hx2, hy2, HUD);
    }

    private static void renderCraftReference(GuiGraphics graphics, int centerX, int centerY) {
        drawLine(graphics, centerX - 30, centerY, centerX - 10, centerY, HUD);
        drawLine(graphics, centerX + 10, centerY, centerX + 30, centerY, HUD);
        drawLine(graphics, centerX - 10, centerY, centerX - 2, centerY + 6, HUD);
        drawLine(graphics, centerX + 10, centerY, centerX + 2, centerY + 6, HUD);
    }

    private static void renderFlightPathMarker(GuiGraphics graphics, int centerX, int centerY, float headingDeg,
                                               float pitchDeg, boolean hasTelemetry,
                                               float velX, float velY, float velZ) {
        if (!hasTelemetry) {
            drawCircle(graphics, centerX, centerY + 22, 8, HUD_DIM);
            return;
        }
        double horizontalSpeed = Math.sqrt(velX * velX + velZ * velZ);
        if (horizontalSpeed < 0.05D && Math.abs(velY) < 0.05D) {
            drawCircle(graphics, centerX, centerY + 22, 8, HUD_DIM);
            return;
        }
        float trackHeading = (float) Math.toDegrees(Math.atan2(velX, velZ));
        float flightPathPitch = (float) Math.toDegrees(Math.atan2(velY, Math.max(horizontalSpeed, 0.001D)));
        int fpmX = centerX + Math.round(Mth.clamp(Mth.wrapDegrees(trackHeading - headingDeg), -28.0f, 28.0f) * 3.0f);
        int fpmY = centerY - Math.round(Mth.clamp(flightPathPitch - pitchDeg, -18.0f, 18.0f) * 3.0f);
        drawCircle(graphics, fpmX, fpmY, 9, HUD);
        drawLine(graphics, fpmX - 14, fpmY, fpmX - 5, fpmY, HUD);
        drawLine(graphics, fpmX + 5, fpmY, fpmX + 14, fpmY, HUD);
        drawLine(graphics, fpmX, fpmY + 5, fpmX, fpmY + 14, HUD);
    }

    private static void renderBattleSymbology(GuiGraphics graphics, Minecraft minecraft, int screenWidth, int screenHeight,
                                               @Nullable BattleScreenDisplayBE.LinkedObserver observer) {
        if (observer == null || !observer.weapon().detected()) {
            return;
        }
        BattleScreenDisplayBE.AutoAimSnapshot autoAim = observer.weapon().autoAim();
        float hitChance = computeHitChance(autoAim);
        boolean helmetLock = autoAim.targetPresent() && hitChance >= HIT_CHANCE_LOCK_THRESHOLD;
        ScreenPoint aimPoint = projectAimCueToScreen(minecraft, screenWidth, screenHeight, autoAim);
        if (aimPoint != null) {
            int aimColor = helmetLock ? OK : autoAim.targetPresent() ? WARN : HUD;
            drawAimMarker(graphics, aimPoint.x(), aimPoint.y(), helmetLock ? 7 : 6, aimColor);
        }
        if (!autoAim.targetPresent()) {
            return;
        }
        Vec3 targetWorld = lockedTargetWorldPoint(autoAim);
        ScreenPoint targetPoint = null;
        if (targetWorld != null) {
            targetPoint = projectWorldPointToScreen(minecraft, screenWidth, screenHeight, targetWorld);
        }
        if (targetPoint == null) {
            targetPoint = projectAnglesToScreen(minecraft, screenWidth, screenHeight, autoAim.targetYawDegrees(), autoAim.targetPitchDegrees());
        }
        Font font = minecraft.font;
        if (targetPoint == null) {
            renderOffScreenTargetChevron(graphics, font, minecraft, screenWidth, screenHeight, autoAim, targetWorld);
            return;
        }
        int baseSize = helmetLock ? 18 : 22;
        int pulseSize = baseSize;
        if (helmetLock) {
            float pulse = (Mth.sin((float) ((System.currentTimeMillis() % 1000L) / 1000.0f * Math.PI * 2.0f)) + 1.0f) * 0.5f;
            pulseSize = baseSize + Math.round(pulse * 6.0f);
        }
        drawSquare(graphics, targetPoint.x(), targetPoint.y(), pulseSize, TARGET);
        if (helmetLock) {
            drawSquare(graphics, targetPoint.x(), targetPoint.y(), pulseSize + 6, TARGET_DIM);
            drawCornerBrackets(graphics, targetPoint.x(), targetPoint.y(), pulseSize + 10, TARGET);
        }
        String label = helmetLock ? "LOCK" : "AIMING";
        int labelColor = helmetLock ? TARGET : WARN;
        int labelX = targetPoint.x() - font.width(label) / 2;
        int labelY = targetPoint.y() - pulseSize / 2 - 14;
        drawChip(graphics, labelX - 3, labelY - 1, font.width(label) + 6, 11);
        graphics.drawString(font, label, labelX, labelY, labelColor, true);

        double rangeMeters = autoAim.targetDistance();
        if (rangeMeters <= 0.0 && targetWorld != null) {
            Vec3 cam = minecraft.gameRenderer.getMainCamera().getPosition();
            rangeMeters = targetWorld.distanceTo(cam);
        }
        if (rangeMeters > 0.0) {
            String rngText = String.format(Locale.ROOT, "RNG %.0fm", rangeMeters);
            int rngX = targetPoint.x() - font.width(rngText) / 2;
            int rngY = targetPoint.y() + pulseSize / 2 + 4;
            drawChip(graphics, rngX - 3, rngY - 1, font.width(rngText) + 6, 11);
            graphics.drawString(font, rngText, rngX, rngY, HUD_TEXT, true);
            if (!helmetLock) {
                String pctText = String.format(Locale.ROOT, "%d%%", Math.round(hitChance * 100.0f));
                int pctX = targetPoint.x() - font.width(pctText) / 2;
                int pctY = rngY + 11;
                drawChip(graphics, pctX - 3, pctY - 1, font.width(pctText) + 6, 11);
                graphics.drawString(font, pctText, pctX, pctY, WARN, true);
            }
        }
    }

    private static float computeHitChance(BattleScreenDisplayBE.AutoAimSnapshot autoAim) {
        if (!autoAim.targetPresent() || !autoAim.mountAvailable()) {
            return 0.0f;
        }
        float yawErr = Math.abs(autoAim.yawErrorDegrees());
        float pitchErr = Math.abs(autoAim.pitchErrorDegrees());
        float err = (float) Math.sqrt(yawErr * yawErr + pitchErr * pitchErr);
        // 0 deg error -> 100%, 3 deg error -> 0%. Sharp drop-off so 90% requires ~0.3 deg.
        return Mth.clamp(1.0f - err / 3.0f, 0.0f, 1.0f);
    }

    @Nullable
    private static Vec3 lockedTargetWorldPoint(BattleScreenDisplayBE.AutoAimSnapshot autoAim) {
        BattleScreenDisplayBE.AutoAimTargetOption locked = lockedTarget(autoAim);
        return locked == null ? null : new Vec3(locked.worldX(), locked.worldY(), locked.worldZ());
    }

    private static void renderOffScreenTargetChevron(GuiGraphics graphics, Font font, Minecraft minecraft,
                                                     int screenWidth, int screenHeight,
                                                     BattleScreenDisplayBE.AutoAimSnapshot autoAim,
                                                     @Nullable Vec3 targetWorld) {
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (camera == null) {
            return;
        }
        float bearingDeg;
        if (targetWorld != null) {
            Vec3 cam = camera.getPosition();
            float yawDeg = (float) Math.toDegrees(Math.atan2(-(targetWorld.x - cam.x), targetWorld.z - cam.z));
            bearingDeg = Mth.wrapDegrees(yawDeg - camera.getYRot());
        } else {
            bearingDeg = Mth.wrapDegrees(autoAim.targetYawDegrees() - camera.getYRot());
        }
        int margin = 28;
        int cx = screenWidth / 2;
        int cy = screenHeight / 2;
        int ringRadiusX = screenWidth / 2 - margin;
        int ringRadiusY = screenHeight / 2 - margin;
        float angleRad = (float) Math.toRadians(bearingDeg);
        float dx = Mth.sin(angleRad);
        float dy = -Mth.cos(angleRad);
        // Limit to elliptical edge of safe area
        float tX = Math.abs(dx) < 1.0E-4f ? Float.POSITIVE_INFINITY : ringRadiusX / Math.abs(dx);
        float tY = Math.abs(dy) < 1.0E-4f ? Float.POSITIVE_INFINITY : ringRadiusY / Math.abs(dy);
        float t = Math.min(tX, tY);
        int x = cx + Math.round(dx * t);
        int y = cy + Math.round(dy * t);
        drawChevron(graphics, x, y, angleRad, TARGET);
        String bearingText = String.format(Locale.ROOT, "%+.0f\u00B0", bearingDeg);
        int bx = x - font.width(bearingText) / 2;
        int by = y + 10;
        drawChip(graphics, bx - 3, by - 1, font.width(bearingText) + 6, 11);
        graphics.drawString(font, bearingText, bx, by, TARGET, true);
    }

    private static void drawChevron(GuiGraphics graphics, int cx, int cy, float angleRad, int color) {
        float cos = Mth.cos(angleRad);
        float sin = Mth.sin(angleRad);
        // Build a triangle pointing along (sin, -cos) i.e. the bearing direction.
        float[][] pts = { {0.0f, -10.0f}, {-7.0f, 6.0f}, {7.0f, 6.0f} };
        int[] xs = new int[3];
        int[] ys = new int[3];
        for (int i = 0; i < 3; i++) {
            float lx = pts[i][0];
            float ly = pts[i][1];
            xs[i] = cx + Math.round(lx * cos - ly * sin);
            ys[i] = cy + Math.round(lx * sin + ly * cos);
        }
        drawLine(graphics, xs[0], ys[0], xs[1], ys[1], color);
        drawLine(graphics, xs[1], ys[1], xs[2], ys[2], color);
        drawLine(graphics, xs[2], ys[2], xs[0], ys[0], color);
    }

    private static void drawCornerBrackets(GuiGraphics graphics, int cx, int cy, int size, int color) {
        int half = size / 2;
        int len = 4;
        // top-left
        drawLine(graphics, cx - half, cy - half, cx - half + len, cy - half, color);
        drawLine(graphics, cx - half, cy - half, cx - half, cy - half + len, color);
        // top-right
        drawLine(graphics, cx + half - len, cy - half, cx + half, cy - half, color);
        drawLine(graphics, cx + half, cy - half, cx + half, cy - half + len, color);
        // bottom-left
        drawLine(graphics, cx - half, cy + half - len, cx - half, cy + half, color);
        drawLine(graphics, cx - half, cy + half, cx - half + len, cy + half, color);
        // bottom-right
        drawLine(graphics, cx + half - len, cy + half, cx + half, cy + half, color);
        drawLine(graphics, cx + half, cy + half - len, cx + half, cy + half, color);
    }

    @Nullable
    private static ScreenPoint projectAimCueToScreen(Minecraft minecraft, int screenWidth, int screenHeight,
                                                     BattleScreenDisplayBE.AutoAimSnapshot autoAim) {
        if (autoAim.mountAvailable()) {
            ScreenPoint projected = projectAnglesToScreen(minecraft, screenWidth, screenHeight,
                autoAim.mountYawDegrees(), autoAim.mountPitchDegrees());
            if (projected != null) {
                return projected;
            }
        }
        if (Double.isFinite(autoAim.aimForwardWorldX())
                && Double.isFinite(autoAim.aimForwardWorldY())
                && Double.isFinite(autoAim.aimForwardWorldZ())) {
            return projectWorldPointToScreen(minecraft, screenWidth, screenHeight,
                new Vec3(autoAim.aimForwardWorldX(), autoAim.aimForwardWorldY(), autoAim.aimForwardWorldZ()));
        }
        return null;
    }

    @Nullable
    private static BattleScreenDisplayBE.AutoAimTargetOption lockedTarget(BattleScreenDisplayBE.AutoAimSnapshot autoAim) {
        for (BattleScreenDisplayBE.AutoAimTargetOption option : autoAim.targetOptions()) {
            if (option.locked()) {
                return option;
            }
        }
        return autoAim.targetOptions().isEmpty() ? null : autoAim.targetOptions().get(0);
    }

    @Nullable
    private static ScreenPoint projectWorldPointToScreen(Minecraft minecraft, int screenWidth, int screenHeight, Vec3 worldPoint) {
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (camera == null) {
            return null;
        }
        Vec3 cameraPos = camera.getPosition();
        double dx = worldPoint.x - cameraPos.x;
        double dy = worldPoint.y - cameraPos.y;
        double dz = worldPoint.z - cameraPos.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0E-6 && Math.abs(dy) < 1.0E-6) {
            return null;
        }
        float yawDeg = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitchDeg = (float) Math.toDegrees(Math.atan2(dy, Math.max(1.0E-6, horizontal)));
        return projectAnglesToScreen(minecraft, screenWidth, screenHeight, yawDeg, pitchDeg);
    }

    @Nullable
    private static ScreenPoint projectAnglesToScreen(Minecraft minecraft, int screenWidth, int screenHeight,
                                                     float worldYawDeg, float worldPitchDeg) {
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (camera == null || screenWidth <= 0 || screenHeight <= 0) {
            return null;
        }
        float cameraYaw = camera.getYRot();
        float cameraPitch = -camera.getXRot();
        float relativeYaw = Mth.wrapDegrees(worldYawDeg - cameraYaw);
        float relativePitch = Mth.wrapDegrees(worldPitchDeg - cameraPitch);
        // [11]
        if (Math.abs(relativeYaw) >= 85.0f || Math.abs(relativePitch) >= 85.0f) {
            return null;
        }
        double verticalFov = Math.toRadians(currentVerticalFovDegrees(minecraft));
        double halfVerticalTan = Math.tan(verticalFov * 0.5D);
        if (halfVerticalTan <= 1.0E-6D) {
            return null;
        }
        double aspect = (double) screenWidth / (double) screenHeight;
        double halfHorizontalTan = halfVerticalTan * aspect;
        double yawTan = Math.tan(Math.toRadians(relativeYaw));
        double pitchTan = Math.tan(Math.toRadians(relativePitch));
        if (!Double.isFinite(yawTan) || !Double.isFinite(pitchTan)) {
            return null;
        }
        int screenX = (int) Math.round(screenWidth * 0.5D + yawTan / halfHorizontalTan * screenWidth * 0.5D);
        int screenY = (int) Math.round(screenHeight * 0.5D - pitchTan / halfVerticalTan * screenHeight * 0.5D);
        if (screenX < -64 || screenX > screenWidth + 64 || screenY < -64 || screenY > screenHeight + 64) {
            return null;
        }
        return new ScreenPoint(screenX, screenY);
    }

    private static float currentVerticalFovDegrees(Minecraft minecraft) {
        float base = 70.0f;
        Object fovValue = minecraft.options.fov().get();
        if (fovValue instanceof Number number) {
            base = number.floatValue();
        }
        // [12]
        if (minecraft.player != null) {
            float modifier = minecraft.player.getFieldOfViewModifier();
            if (Float.isFinite(modifier) && modifier > 0.1f) {
                base *= modifier;
            }
        }
        return Mth.clamp(base, 30.0f, 170.0f);
    }

    private static void renderSpeedClimbChip(GuiGraphics graphics, Font font, int leftX, int leftY, double speed, double climb) {
        boolean noSpeed = Double.isNaN(speed);
        boolean noClimb = Double.isNaN(climb);
        String spdText = noSpeed ? "SPD ---" : String.format(Locale.ROOT, "SPD %.1f", speed);
        String clmText = noClimb ? "CLM ---" : String.format(Locale.ROOT, "CLM %+.1f", climb);
        drawChip(graphics, leftX - 3, leftY - 2, Math.max(font.width(spdText), font.width(clmText)) + 6, 26);
        graphics.drawString(font, spdText, leftX, leftY, noSpeed ? WARN : HUD_TEXT, true);
        boolean steepSink = !noClimb && climb <= -8.0;
        if (steepSink) {
            // Steep descent gets real size hierarchy, not just a colour swap.
            drawUrgentText(graphics, font, clmText, leftX, leftY + 12, 1.4f, TARGET, -1, true);
        } else {
            graphics.drawString(font, clmText, leftX, leftY + 12, noClimb ? WARN : climb >= 0.0 ? OK : WARN, true);
        }
    }

    private static void renderAltGChip(GuiGraphics graphics, Font font, int rightX, int rightY, double altitude, float gLoad, boolean hasTelemetry) {
        boolean noAlt = Double.isNaN(altitude);
        String altText = noAlt ? "ALT ---" : String.format(Locale.ROOT, "ALT %.1f", altitude);
        String gText = hasTelemetry ? String.format(Locale.ROOT, "G %.1f", gLoad) : null;
        int altX = rightX - font.width(altText);
        drawChip(graphics, altX - 3, rightY - 2, font.width(altText) + 6, gText != null ? 26 : 14);
        graphics.drawString(font, altText, altX, rightY, noAlt ? WARN : HUD_TEXT, true);
        if (gText != null) {
            if (gLoad >= 5.0f) {
                drawUrgentText(graphics, font, gText, rightX, rightY + 12, 1.6f, TARGET, 1, true);
            } else if (gLoad >= 3.0f) {
                drawUrgentText(graphics, font, gText, rightX, rightY + 12, 1.3f, WARN, 1, false);
            } else {
                graphics.drawString(font, gText, rightX - font.width(gText), rightY + 12, HUD_TEXT, true);
            }
        }
    }

    /** [13] */
    private static void drawUrgentText(GuiGraphics graphics, Font font, String text,
                                       float anchorX, float anchorY, float scale, int color,
                                       int align, boolean pulse) {
        int alpha = 255;
        if (pulse) {
            alpha = 170 + Math.round(85 * com.createstats.client.hud.HudAnim.pulse(System.currentTimeMillis(), 500L));
        }
        int argb = (alpha << 24) | (color & 0x00FFFFFF);
        graphics.pose().pushPose();
        graphics.pose().translate(anchorX, anchorY, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        int textX = align > 0 ? -font.width(text) : align == 0 ? -font.width(text) / 2 : 0;
        graphics.drawString(font, text, textX, 0, argb, true);
        graphics.pose().popPose();
    }

    private static void renderNoSignalBanner(GuiGraphics graphics, Font font, int centerX, int y, String text) {
        int w = font.width(text) + 14;
        int x = centerX - w / 2;
        graphics.fill(x, y, x + w, y + 13, 0xC0260000);
        drawFrame(graphics, x, y, w, 13, TARGET);
        graphics.drawCenteredString(font, text, centerX, y + 3, TARGET);
    }

    private static void renderStatusLine(GuiGraphics graphics, Font font, int centerX, int y,
                                         @Nullable FccTelemetryPacket telemetry,
                                         @Nullable BattleScreenDisplayBE.LinkedObserver observer) {
        String left = telemetry != null ? "FCC LINK" : "FCC STBY";
        String right;
        if (observer == null) {
            right = "BATTLE STBY";
        } else if (observer.weapon().autoAim().targetLocked()) {
            right = "TARGET LOCKED";
        } else if (observer.weapon().autoAim().targetPresent()) {
            right = "TARGET TRACKING";
        } else {
            right = trim(font, observer.weapon().autoAim().statusText().isBlank() ? observer.weapon().readiness() : observer.weapon().autoAim().statusText(), 120);
        }
        String line = left + "   |   " + right;
        graphics.drawString(font, line, centerX - font.width(line) / 2, y, observer != null && observer.weapon().autoAim().targetLocked() ? TARGET : HUD_DIM, false);
    }

    private static float computeThrustPercent(@Nullable FccTelemetryPacket telemetry) {
        if (telemetry == null) {
            return 0.0f;
        }
        if (telemetry.thrustSigned().length > 0) {
            float sum = 0.0f;
            for (float thrust : telemetry.thrustSigned()) {
                sum += Math.abs(thrust);
            }
            return Mth.clamp(sum / telemetry.thrustSigned().length * 100.0f, 0.0f, 100.0f);
        }
        if (telemetry.commandedRpm().length > 0) {
            float sum = 0.0f;
            for (int rpm : telemetry.commandedRpm()) {
                sum += Math.abs(rpm);
            }
            return Mth.clamp(sum / telemetry.commandedRpm().length / 256.0f * 100.0f, 0.0f, 100.0f);
        }
        return 0.0f;
    }

    private static float computeAuthorityPercent(FccGuiSnapshot snapshot) {
        if (snapshot.boundRowCount() <= 0) {
            return 0.0f;
        }
        return Mth.clamp(snapshot.resolvedBindingCount() * 100.0f / snapshot.boundRowCount(), 0.0f, 100.0f);
    }

    @Nullable
    private static Float extractLiftPercent(@Nullable FccGuiSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        for (FccGuiControlEntry entry : snapshot.controlEntries()) {
            String label = entry.label() == null ? "" : entry.label().toLowerCase(Locale.ROOT);
            if (label.contains("levitation") || label.contains("lift")) {
                return normalizeSigned(entry) * 100.0f;
            }
        }
        return null;
    }

    private static float normalizeSigned(FccGuiControlEntry entry) {
        double neutral = entry.neutralValue();
        double value = entry.value();
        if (value >= neutral) {
            double span = Math.max(0.0001D, entry.inputMax() - neutral);
            return (float) Mth.clamp((value - neutral) / span, 0.0D, 1.0D);
        }
        double span = Math.max(0.0001D, neutral - entry.inputMin());
        return (float) -Mth.clamp((neutral - value) / span, 0.0D, 1.0D);
    }

    private static float fallbackHeading(Player player) {
        return Mth.wrapDegrees(180.0f - player.getYRot());
    }

    private static float wrap360(float degrees) {
        float wrapped = degrees % 360.0f;
        return wrapped < 0.0f ? wrapped + 360.0f : wrapped;
    }

    private static String compassLabel(float heading) {
        int rounded = Math.round(wrap360(heading)) % 360;
        return switch (rounded) {
            case 0 -> "N";
            case 90 -> "E";
            case 180 -> "S";
            case 270 -> "W";
            default -> String.format(Locale.ROOT, "%02d", rounded / 10);
        };
    }

    private static String formatSignedPercent(float value) {
        return String.format(Locale.ROOT, "%+.0f%%", value);
    }

    private static String formatOptionalPercent(@Nullable Float value) {
        return value == null ? "--" : formatSignedPercent(value);
    }

    private static String trim(Font font, String text, int width) {
        if (text == null || text.isBlank()) {
            return "--";
        }
        return font.plainSubstrByWidth(text, width);
    }

    private static void drawFrame(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        drawLine(graphics, x, y, x + 18, y, color);
        drawLine(graphics, x, y, x, y + height, color);
        drawLine(graphics, x + width - 18, y, x + width, y, color);
        drawLine(graphics, x + width, y, x + width, y + height, color);
        drawLine(graphics, x, y + height, x + 18, y + height, color);
        drawLine(graphics, x + width - 18, y + height, x + width, y + height, color);
    }

    private static void drawCircle(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        for (int angle = 0; angle < 360; angle += 12) {
            int x = cx + Math.round(Mth.cos((float) Math.toRadians(angle)) * radius);
            int y = cy + Math.round(Mth.sin((float) Math.toRadians(angle)) * radius);
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static void drawDiamond(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        drawLine(graphics, cx, cy - radius, cx + radius, cy, color);
        drawLine(graphics, cx + radius, cy, cx, cy + radius, color);
        drawLine(graphics, cx, cy + radius, cx - radius, cy, color);
        drawLine(graphics, cx - radius, cy, cx, cy - radius, color);
    }

    private static void drawAimMarker(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        int diamondRadius = Math.max(4, radius);
        drawDiamond(graphics, cx, cy, diamondRadius, color);
        drawLine(graphics, cx - diamondRadius - 5, cy, cx - diamondRadius - 1, cy, color);
        drawLine(graphics, cx + diamondRadius + 1, cy, cx + diamondRadius + 5, cy, color);
        drawLine(graphics, cx, cy - diamondRadius - 5, cx, cy - diamondRadius - 1, color);
        drawLine(graphics, cx, cy + diamondRadius + 1, cx, cy + diamondRadius + 5, color);
    }

    private static void drawSquare(GuiGraphics graphics, int cx, int cy, int size, int color) {
        int half = size / 2;
        drawLine(graphics, cx - half, cy - half, cx + half, cy - half, color);
        drawLine(graphics, cx + half, cy - half, cx + half, cy + half, color);
        drawLine(graphics, cx + half, cy + half, cx - half, cy + half, color);
        drawLine(graphics, cx - half, cy + half, cx - half, cy - half, color);
    }

    private static int rotateX(float x, float y, float cos, float sin, int centerX) {
        return centerX + Math.round(x * cos - y * sin);
    }

    private static int rotateY(float x, float y, float cos, float sin, int centerY) {
        return centerY + Math.round(x * sin + y * cos);
    }

    private static void drawChip(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, CHIP_BG);
    }

    private static float updateGLoad(boolean hasTelemetry, float velX, float velY, float velZ) {
        long now = System.nanoTime();
        if (!hasTelemetry || gLoadPrevTimeNanos == 0L) {
            gLoadPrevTimeNanos = now;
            gLoadPrevVelX = velX;
            gLoadPrevVelY = velY;
            gLoadPrevVelZ = velZ;
            gLoadSmoothed = 1.0f;
            return gLoadSmoothed;
        }
        float dt = (now - gLoadPrevTimeNanos) / 1.0E9f;
        if (dt < 0.02f) {
            return gLoadSmoothed;
        }
        float ax = (velX - gLoadPrevVelX) / dt;
        float ay = (velY - gLoadPrevVelY) / dt;
        float az = (velZ - gLoadPrevVelZ) / dt;
        // Account for gravity baseline along Y.
        float aMag = (float) Math.sqrt(ax * ax + (ay + 9.81f) * (ay + 9.81f) + az * az);
        float gNow = Mth.clamp(aMag / 9.81f, 0.0f, 9.9f);
        // Exponential smoothing
        float alpha = Mth.clamp(dt * 3.0f, 0.05f, 1.0f);
        gLoadSmoothed = gLoadSmoothed + (gNow - gLoadSmoothed) * alpha;
        gLoadPrevTimeNanos = now;
        gLoadPrevVelX = velX;
        gLoadPrevVelY = velY;
        gLoadPrevVelZ = velZ;
        return gLoadSmoothed;
    }

    private static void renderCanopyVignette(GuiGraphics graphics, int width, int height, int strengthPercent) {
        int strength = Math.max(0, Math.min(100, strengthPercent));
        if (strength == 0) {
            return;
        }
        ResourceLocation tex = vignetteTexture(width, height, strength);
        if (tex == null) {
            return;
        }
        // Single textured quad - one draw call, GPU does all the work.
        graphics.blit(tex, 0, 0, 0.0f, 0.0f, width, height, width, height);
    }

    private static int cachedVignetteWidth = -1;
    private static int cachedVignetteHeight = -1;
    private static int cachedVignetteStrength = -1;
    @Nullable
    private static DynamicTexture cachedVignetteTexture = null;
    @Nullable
    private static ResourceLocation cachedVignetteTextureId = null;

    @Nullable
    private static ResourceLocation vignetteTexture(int width, int height, int strength) {
        if (width <= 0 || height <= 0) {
            return null;
        }
        if (cachedVignetteTextureId != null
            && cachedVignetteWidth == width
            && cachedVignetteHeight == height
            && cachedVignetteStrength == strength) {
            return cachedVignetteTextureId;
        }
        // Release the previous backing texture before allocating a new one.
        if (cachedVignetteTexture != null) {
            try {
                Minecraft.getInstance().getTextureManager().release(cachedVignetteTextureId);
            } catch (Throwable ignored) {
                // best effort
            }
            cachedVignetteTexture.close();
            cachedVignetteTexture = null;
            cachedVignetteTextureId = null;
        }
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, height, true);
        bakeVignettePixels(image, width, height, strength);
        DynamicTexture dyn = new DynamicTexture(image);
        // [14]
        dyn.setFilter(false, false);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "vignette_dynamic");
        Minecraft.getInstance().getTextureManager().register(id, dyn);
        cachedVignetteTexture = dyn;
        cachedVignetteTextureId = id;
        cachedVignetteWidth = width;
        cachedVignetteHeight = height;
        cachedVignetteStrength = strength;
        return id;
    }

    private static void bakeVignettePixels(NativeImage image, int width, int height, int strengthPercent) {
        float s = strengthPercent / 100.0f;
        float cx = width / 2.0f;
        float cy = height / 2.0f;
        float visorRx = width * (0.95f - 0.35f * s);
        float visorRy = height * (0.92f - 0.37f * s);
        float feather = Math.max(8.0f, Math.min(width, height) * 0.10f);
        int maxAlpha = Math.round(0xF0 * s);
        int browAlpha = Math.round(0x60 * s);
        float browHeight = height * 0.08f * s;
        for (int y = 0; y < height; y++) {
            float dy = (y - cy) / visorRy;
            for (int x = 0; x < width; x++) {
                float dx = (x - cx) / visorRx;
                // Elliptical distance: 0 at center, 1 on visor edge, >1 outside.
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                int alpha;
                if (d <= 1.0f) {
                    alpha = 0;
                } else {
                    // Linear feather from 0 at visor edge to maxAlpha at feather/min radii past edge.
                    float over = (d - 1.0f) * Math.min(visorRx, visorRy) / feather;
                    alpha = Math.round(maxAlpha * Math.min(1.0f, over));
                }
                // Brow shadow add-on across the top of the screen.
                if (y < browHeight) {
                    int brow = Math.round(browAlpha * (1.0f - y / browHeight));
                    alpha = Math.min(0xFF, alpha + brow);
                }
                // NativeImage in RGBA format packs as (A<<24)|(B<<16)|(G<<8)|R little-endian, i.e. ABGR int.
                int abgr = (alpha << 24);
                image.setPixelRGBA(x, y, abgr);
            }
        }
    }

    private static void renderBankPointer(GuiGraphics graphics, Font font, int centerX, int y, float rollDeg) {
        // Horizontal scale tick marks at -45, -30, -15, 0, 15, 30, 45 deg roll.
        int halfWidth = 60;
        drawLine(graphics, centerX - halfWidth, y, centerX + halfWidth, y, HUD_DIM);
        for (int mark = -45; mark <= 45; mark += 15) {
            int tickX = centerX + Math.round(mark / 45.0f * halfWidth);
            int tickH = mark == 0 ? 6 : 3;
            drawLine(graphics, tickX, y, tickX, y + tickH, HUD_DIM);
        }
        float clampedRoll = Mth.clamp(rollDeg, -45.0f, 45.0f);
        int ptrX = centerX + Math.round(clampedRoll / 45.0f * halfWidth);
        // Triangle pointing down from the scale line.
        boolean extremeBank = Math.abs(rollDeg) >= 45.0f;
        int ptrColor = extremeBank ? TARGET : Math.abs(rollDeg) >= 30.0f ? WARN : HUD;
        drawLine(graphics, ptrX - 4, y - 6, ptrX + 4, y - 6, ptrColor);
        drawLine(graphics, ptrX - 4, y - 6, ptrX, y - 1, ptrColor);
        drawLine(graphics, ptrX + 4, y - 6, ptrX, y - 1, ptrColor);
        String label = String.format(Locale.ROOT, "BNK %+.0f\u00B0", rollDeg);
        int labelX = centerX - font.width(label) / 2;
        drawChip(graphics, labelX - 3, y - 18, font.width(label) + 6, 11);
        if (extremeBank) {
            // Past the scale's own limit: the readout grows and flashes instead of only recoloring.
            drawUrgentText(graphics, font, label, centerX, y - 20, 1.5f, TARGET, 0, true);
        } else {
            graphics.drawString(font, label, labelX, y - 17, HUD_TEXT, true);
        }
    }

    private static void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        if (x1 == x2) {
            int top = Math.min(y1, y2);
            int bottom = Math.max(y1, y2);
            graphics.fill(x1, top, x1 + 1, bottom + 1, color);
            return;
        }
        if (y1 == y2) {
            int left = Math.min(x1, x2);
            int right = Math.max(x1, x2);
            graphics.fill(left, y1, right + 1, y1 + 1, color);
            return;
        }
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;
        int x = x1;
        int y = y1;
        while (true) {
            graphics.fill(x, y, x + 1, y + 1, color);
            if (x == x2 && y == y2) {
                return;
            }
            int e2 = err * 2;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }

    // [15]

    /**
     * Top-left animated NV badge: a small pulsing ring + "NV" text in green,
     * sized about 28 px high. Always drawn when NV is enabled.
     */
    private static void renderNightVisionLogo(GuiGraphics graphics, Font font,
                                              PilotHelmetData.NightVisionMode mode) {
        int x = 12;
        int y = 12;
        int w = 56;
        int h = 26;
        // Slow 2 Hz sine pulse based on wall-clock for smooth visuals regardless
        // of game tick rate.
        float t = (System.currentTimeMillis() % 100000L) / 1000.0f;
        float pulse = 0.55f + 0.45f * (float) Math.sin(t * 3.0f);
        int alpha = (int) (160 * pulse);
        int rim = (alpha << 24) | 0x00C8FFC8;
        int body = (Math.max(40, alpha / 3) << 24) | 0x00041808;
        graphics.fill(x, y, x + w, y + h, body);
        graphics.fill(x, y, x + w, y + 1, rim);
        graphics.fill(x, y + h - 1, x + w, y + h, rim);
        graphics.fill(x, y, x + 1, y + h, rim);
        graphics.fill(x + w - 1, y, x + w, y + h, rim);
        String label = "NV";
        String sub = mode == PilotHelmetData.NightVisionMode.TACTICAL ? "TAC" : "CLR";
        int labelColor = (Math.min(255, (int) (220 * pulse + 35)) << 24) | 0x00B6FFB6;
        int subColor = (Math.min(255, (int) (160 * pulse + 30)) << 24) | 0x0078E078;
        graphics.drawString(font, label, x + 6, y + 5, labelColor, false);
        graphics.drawString(font, sub, x + 6 + font.width(label) + 4, y + 7, subColor, false);
        // Pulsing dot indicator on the right edge.
        int dotAlpha = (int) (255 * pulse);
        int dotColor = (dotAlpha << 24) | 0x0042E84A;
        graphics.fill(x + w - 8, y + h / 2 - 2, x + w - 4, y + h / 2 + 2, dotColor);
    }

    /** [16] */
    private static void renderTacticalNvgOverlay(GuiGraphics graphics, int width, int height) {
        ResourceLocation staticLayers = nvgStaticTexture(width, height);
        if (staticLayers != null) {
            graphics.blit(staticLayers, 0, 0, 0.0f, 0.0f, width, height, width, height);
        }
        // Sparse green noise speckle: a few hundred random-looking pixels per
        // frame using a time-seeded pseudo-random sequence. Avoids per-pixel
        // alloc by reusing the same int constants.
        long seed = System.nanoTime() >>> 18; // shifts roughly per-frame
        long s = seed;
        int speckleCount = Math.min(600, (width * height) / 600);
        for (int i = 0; i < speckleCount; i++) {
            s = (s * 6364136223846793005L) + 1442695040888963407L;
            int rx = (int) ((s >>> 33) % width);
            s = (s * 6364136223846793005L) + 1442695040888963407L;
            int ry = (int) ((s >>> 33) % height);
            // Two brightness tiers: bright (rare) and dim (common).
            int color = ((s & 0x1F) == 0) ? 0x9098FFA0 : 0x4060D070;
            graphics.fill(rx, ry, rx + 1, ry + 1, color);
        }
    }

    private static int cachedNvgWidth = -1;
    private static int cachedNvgHeight = -1;
    @Nullable
    private static DynamicTexture cachedNvgTexture = null;
    @Nullable
    private static ResourceLocation cachedNvgTextureId = null;

    @Nullable
    private static ResourceLocation nvgStaticTexture(int width, int height) {
        if (width <= 0 || height <= 0) {
            return null;
        }
        if (cachedNvgTextureId != null && cachedNvgWidth == width && cachedNvgHeight == height) {
            return cachedNvgTextureId;
        }
        if (cachedNvgTexture != null) {
            try {
                Minecraft.getInstance().getTextureManager().release(cachedNvgTextureId);
            } catch (Throwable ignored) {
                // best effort
            }
            cachedNvgTexture.close();
            cachedNvgTexture = null;
            cachedNvgTextureId = null;
        }
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, height, true);
        bakeNvgStaticPixels(image, width, height);
        DynamicTexture dyn = new DynamicTexture(image);
        // Nearest-neighbour, same reason as the canopy vignette: bilinear upscaling to the
        // framebuffer bleeds the translucent pixels into a uniform blur.
        dyn.setFilter(false, false);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "nvg_static_dynamic");
        Minecraft.getInstance().getTextureManager().register(id, dyn);
        cachedNvgTexture = dyn;
        cachedNvgTextureId = id;
        cachedNvgWidth = width;
        cachedNvgHeight = height;
        return id;
    }

    /** [17] */
    private static void bakeNvgStaticPixels(NativeImage image, int width, int height) {
        int cx = width / 2;
        int cy = height / 2;
        int maxR = Math.max(cx, cy);
        // Vignette ring radii and alphas (band 0..3, inner to outer, rising alpha).
        int[] bandR = new int[4];
        int[] bandA = new int[4];
        for (int band = 0; band < 4; band++) {
            bandR[band] = maxR - (4 - band) * maxR / 8;
            bandA[band] = 0x18 + band * 0x16;
        }
        for (int y = 0; y < height; y++) {
            boolean scanline = (y % 2) == 0;
            for (int x = 0; x < width; x++) {
                // Layer 1: faint green tint (ARGB 0x18103808).
                float a = 0x18 / 255.0f;
                float r = (0x10 / 255.0f);
                float g = (0x38 / 255.0f);
                float b = (0x08 / 255.0f);
                // Layer 2: scanline rows, translucent black.
                if (scanline) {
                    float sa = 0x22 / 255.0f;
                    float outA = sa + a * (1.0f - sa);
                    r = r * a * (1.0f - sa) / outA;
                    g = g * a * (1.0f - sa) / outA;
                    b = b * a * (1.0f - sa) / outA;
                    a = outA;
                }
                // Layers 3-6: vignette bands (translucent black), one fill per band the pixel
                // falls outside of.
                for (int band = 0; band < 4; band++) {
                    int r0 = bandR[band];
                    boolean outside = y < cy - r0 || y >= cy + r0 || x < cx - r0 || x >= cx + r0;
                    if (!outside) continue;
                    float sa = bandA[band] / 255.0f;
                    float outA = sa + a * (1.0f - sa);
                    r = r * a * (1.0f - sa) / outA;
                    g = g * a * (1.0f - sa) / outA;
                    b = b * a * (1.0f - sa) / outA;
                    a = outA;
                }
                int ia = Math.min(255, Math.round(a * 255.0f));
                int ir = Math.min(255, Math.round(r * 255.0f));
                int ig = Math.min(255, Math.round(g * 255.0f));
                int ib = Math.min(255, Math.round(b * 255.0f));
                // NativeImage RGBA packs little-endian as ABGR ints.
                image.setPixelRGBA(x, y, (ia << 24) | (ib << 16) | (ig << 8) | ir);
            }
        }
    }

    private static void renderNightVisionOverlayIfActive(GuiGraphics graphics, Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null) return;
        ItemStack helmet = minecraft.player.getItemBySlot(EquipmentSlot.HEAD);
        if (!(helmet.getItem() instanceof PilotHelmetItem)) return;
        PilotHelmetData data = PilotHelmetItem.getData(helmet);
        if (!data.isNightVisionEnabled()) return;
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        if (data.getNightVisionMode() == PilotHelmetData.NightVisionMode.TACTICAL) {
            renderTacticalNvgOverlay(graphics, width, height);
        }
        renderNightVisionLogo(graphics, minecraft.font, data.getNightVisionMode());
    }

    private record HelmetContext(
        Player player,
        PilotHelmetData.Mode mode,
        @Nullable BlockPos fccPos,
        @Nullable BattleScreenDisplayBE battleDisplay,
        @Nullable BattleScreenDisplayBE.LinkedObserver battleObserver,
        @Nullable BlockPos statsDisplayPos,
        int vignetteStrength,
        float hudScaleFactor
    ) {
    }

    private record ScreenPoint(int x, int y) {
    }
}
