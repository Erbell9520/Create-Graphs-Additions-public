package com.createstats.gui;

import com.createstats.battle.BattleLightSnapshot;
import com.createstats.blockentity.BattleScreenDisplayBE;
import com.createstats.blockentity.DamageObserverSettings;
import com.createstats.blockentity.StageLightBE;
import com.createstats.client.BattleScreenPipHudOverlay;
import com.createstats.compat.CreateBigCannonsCompat;
import com.createstats.network.BattleScreenHologramTogglePacket;
import com.createstats.network.BattleScreenLightActionPacket;
import com.createstats.network.BattleScreenWeaponActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

public class BattleScreenDisplayScreen extends Screen {

    private static final int BACKDROP_TOP = 0xFF071018;
    private static final int BACKDROP_BOTTOM = 0xFF15100E;
    private static final int SHELL = 0xFF101821;
    private static final int PANEL = 0xFF0C151D;
    private static final int PANEL_DEEP = 0xFF081018;
    private static final int STROKE = 0xFF315064;
    private static final int STROKE_DIM = 0xFF223241;
    private static final int TEXT = 0xFFE7F3F5;
    private static final int TEXT_DIM = 0xFF8FA9B4;
    private static final int TEXT_DARK = 0xFF566D78;
    private static final int CYAN = 0xFF4AD9FF;
    private static final int GREEN = 0xFF62E8A3;
    private static final int AMBER = 0xFFFFC15E;
    private static final int RED = 0xFFFF5D69;
    private static final int VIOLET = 0xFFA985FF;
    private static final int SHADOW = 0x8A000000;
    private static final int LINK_REFERENCE_DISTANCE = 48;
    private static final int HISTORY_CAPACITY = 80;
    private static final float WEAPON_RESPONSE_MIN = 1.0f;
    private static final float WEAPON_RESPONSE_MAX = 5.0f;
    private static final float WEAPON_RESPONSE_DEFAULT = 1.0f;
    private static final float BIG_CANNON_RESPONSE_MIN = 0.25f;
    private static final float BIG_CANNON_RESPONSE_MAX = 1.0f;
    private static final float BIG_CANNON_RESPONSE_DEFAULT = 0.25f;
    private static final float ADVANCED_RESPONSE_BOOST = 2.0f;

    private final BattleScreenDisplayBE display;
    private final List<TelemetrySample> telemetryHistory = new ArrayList<>();
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int selectedIndex;
    private BlockPos selectedObserverPos;
    private ScreenMode screenMode = ScreenMode.DAMAGE_MAP;
    private long lastHistoryTick = Long.MIN_VALUE;
    private int lastHistoryHash;
    private Button pipHudButton;
    private Button hologramButton;
    private Button damageMapTabButton;
    private Button weaponsTabButton;
    private Button weaponFireButton;
    private Button weaponPredictButton;
    private Button weaponHoldButton;
    private Button weaponLockButton;
    private Button weaponAutoFireButton;
    private Button previousZoneButton;
    private Button nextZoneButton;
    private final List<ZoneClickTarget> zoneClickTargets = new ArrayList<>();
    private final List<TargetPickerClickTarget> targetPickerClickTargets = new ArrayList<>();
    private boolean targetPickerOpen;
    private int targetPickerScroll;
    private WeaponResponseSliderTarget weaponResponseSliderTarget;
    private boolean weaponResponseSliderDragging;
    private boolean weaponResponseSliderPreviewActive;
    private BlockPos weaponResponseSliderObserverPos;
    private float weaponResponseSliderPreview = WEAPON_RESPONSE_DEFAULT;

    // weapons workbench (redesigned WEAPONS view)
    private WeaponTab weaponTab = WeaponTab.OVERVIEW;
    private final List<WeaponHitTarget> weaponHitTargets = new ArrayList<>();
    private int weaponPickerScroll;
    // integrity workbench (redesigned DAMAGE_MAP view)
    private IntegrityTab integrityTab = IntegrityTab.OVERVIEW;
    private long integrityTabSwitchMillis;
    private int integrityRailScroll;
    // lighting workbench
    private Long focusedLightPos;
    private BlockPos focusedLightObserverPos;
    private boolean lightTargetPickerOpen;
    private int lightTargetPickerScroll;
    private int lightRailScroll;
    private final List<LightStepperTarget> lightStepperTargets = new ArrayList<>();
    private int weaponPickerMaxScroll;
    private long weaponTabSwitchMillis;
    private BlockPos easedAimFor;
    private float easedYawError;
    private float easedPitchError;
    private final java.util.Map<String, Float> easedBars = new java.util.HashMap<>();
    private final java.util.Map<Long, Integer> lastFireCount = new java.util.HashMap<>();
    private final java.util.Map<Long, Long> fireFlashAt = new java.util.HashMap<>();

    public BattleScreenDisplayScreen(BattleScreenDisplayBE display) {
        super(Component.translatable("block.create_stats.battle_screen_display"));
        this.display = display;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        this.panelWidth = Math.min(1120, Math.max(760, this.width - 24));
        this.panelHeight = Math.min(620, Math.max(430, this.height - 18));
        this.panelLeft = (this.width - this.panelWidth) / 2;
        this.panelTop = (this.height - this.panelHeight) / 2;

        int buttonY = this.panelTop + this.panelHeight - 32;
        int tabY = this.panelTop + 72;
        this.damageMapTabButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> this.selectScreenMode(ScreenMode.DAMAGE_MAP))
            .bounds(this.panelLeft + 18, tabY, 104, 20)
            .build());
        this.weaponsTabButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> this.selectScreenMode(ScreenMode.WEAPONS))
            .bounds(this.panelLeft + 128, tabY, 104, 20)
            .build());
        this.weaponPredictButton = this.addRenderableWidget(Button.builder(Component.literal("Linked Ctrl"), button -> this.sendWeaponAction(BattleScreenWeaponActionPacket.Action.OPEN_LINKED_CONTROLLER))
            .bounds(this.panelLeft + this.panelWidth - 760, buttonY - 24, 86, 20)
            .build());
        this.weaponHoldButton = this.addRenderableWidget(Button.builder(Component.literal("Keep Lock"), button -> this.sendWeaponAction(BattleScreenWeaponActionPacket.Action.TOGGLE_AUTO_AIM_HOLD))
            .bounds(this.panelLeft + this.panelWidth - 760, buttonY, 86, 20)
            .build());
        this.weaponLockButton = this.addRenderableWidget(Button.builder(Component.literal("Targets"), button -> this.handleWeaponLockButton())
            .bounds(this.panelLeft + this.panelWidth - 666, buttonY, 86, 20)
            .build());
        this.weaponAutoFireButton = this.addRenderableWidget(Button.builder(Component.literal("Auto Fire"), button -> this.sendWeaponAction(BattleScreenWeaponActionPacket.Action.TOGGLE_AUTO_FIRE))
            .bounds(this.panelLeft + this.panelWidth - 572, buttonY, 86, 20)
            .build());
        this.weaponFireButton = this.addRenderableWidget(Button.builder(Component.literal("FIRE"), button -> this.sendWeaponAction(BattleScreenWeaponActionPacket.Action.FIRE))
            .bounds(this.panelLeft + this.panelWidth - 478, buttonY, 86, 20)
            .build());
        this.pipHudButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> this.togglePipHud())
            .bounds(this.panelLeft + this.panelWidth - 384, buttonY, 86, 20)
            .build());
        this.hologramButton = this.addRenderableWidget(Button.builder(Component.literal("Holo On"), button -> this.sendHologramToggle())
            .bounds(this.panelLeft + this.panelWidth - 384, buttonY - 24, 86, 20)
            .build());
        this.previousZoneButton = this.addRenderableWidget(Button.builder(Component.literal("Prev Zone"), button -> this.selectZone(-1))
                .bounds(this.panelLeft + this.panelWidth - 290, buttonY, 86, 20)
                .build());
        this.nextZoneButton = this.addRenderableWidget(Button.builder(Component.literal("Next Zone"), button -> this.selectZone(1))
                .bounds(this.panelLeft + this.panelWidth - 196, buttonY, 86, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Close"), button -> this.onClose())
                .bounds(this.panelLeft + this.panelWidth - 102, buttonY, 84, 20)
                .build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        this.weaponResponseSliderDragging = false;
        this.weaponResponseSliderPreviewActive = false;
        this.weaponResponseSliderTarget = null;
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float time = this.animationTime(partialTick);
        this.renderBackdrop(g, time);
        // FE Dependent Mode panel in the free screen corner (self-gating).
        com.createstats.power.client.FeStatusWidget.draw(g, this.font, this.width - 130, 8, 120, this.display.feConsumer());
        this.weaponResponseSliderTarget = null;

        List<NearbyObserver> observers = this.nearbyObservers();
        this.recordTelemetrySample(observers);
        this.ensureModeSelection(observers);
        NearbyObserver focusObserver = this.focusedObserver(observers);

        if (this.screenMode == ScreenMode.LIGHTING) {
            this.hideManagedButtons();
            g.fill(this.panelLeft + 5, this.panelTop + 7, this.panelLeft + this.panelWidth + 5, this.panelTop + this.panelHeight + 7, SHADOW);
            g.fill(this.panelLeft, this.panelTop, this.panelLeft + this.panelWidth, this.panelTop + this.panelHeight, SHELL);
            border(g, this.panelLeft, this.panelTop, this.panelWidth, this.panelHeight, STROKE);
            g.fill(this.panelLeft + 1, this.panelTop + 1, this.panelLeft + this.panelWidth - 1, this.panelTop + 3, AMBER);
            g.fill(this.panelLeft + 1, this.panelTop + 3, this.panelLeft + this.panelWidth - 1, this.panelTop + 4, 0x55315B70);
            this.renderLightingWorkbench(g, mouseX, mouseY, time, observers, focusObserver);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        boolean workbench = this.screenMode == ScreenMode.WEAPONS && this.hasWeaponsObserver(observers);
        if (workbench) {
            this.hideManagedButtons();
            g.fill(this.panelLeft + 5, this.panelTop + 7, this.panelLeft + this.panelWidth + 5, this.panelTop + this.panelHeight + 7, SHADOW);
            g.fill(this.panelLeft, this.panelTop, this.panelLeft + this.panelWidth, this.panelTop + this.panelHeight, SHELL);
            border(g, this.panelLeft, this.panelTop, this.panelWidth, this.panelHeight, STROKE);
            g.fill(this.panelLeft + 1, this.panelTop + 1, this.panelLeft + this.panelWidth - 1, this.panelTop + 3, CYAN);
            g.fill(this.panelLeft + 1, this.panelTop + 3, this.panelLeft + this.panelWidth - 1, this.panelTop + 4, 0x55315B70);
            this.renderWeaponsWorkbench(g, mouseX, mouseY, time, observers, focusObserver);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }
        if (this.screenMode == ScreenMode.DAMAGE_MAP) {
            this.hideManagedButtons();
            if (this.hologramButton != null) {
                this.hologramButton.visible = true;
                this.hologramButton.setMessage(Component.literal(this.display.isHologramEnabled() ? "Holo On" : "Holo Off"));
            }
            g.fill(this.panelLeft + 5, this.panelTop + 7, this.panelLeft + this.panelWidth + 5, this.panelTop + this.panelHeight + 7, SHADOW);
            g.fill(this.panelLeft, this.panelTop, this.panelLeft + this.panelWidth, this.panelTop + this.panelHeight, SHELL);
            border(g, this.panelLeft, this.panelTop, this.panelWidth, this.panelHeight, STROKE);
            g.fill(this.panelLeft + 1, this.panelTop + 1, this.panelLeft + this.panelWidth - 1, this.panelTop + 3, CYAN);
            g.fill(this.panelLeft + 1, this.panelTop + 3, this.panelLeft + this.panelWidth - 1, this.panelTop + 4, 0x55315B70);
            this.renderIntegrityWorkbench(g, mouseX, mouseY, time, observers, focusObserver);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }
        if (this.damageMapTabButton != null) {
            this.damageMapTabButton.visible = true;
        }
        if (this.weaponsTabButton != null) {
            this.weaponsTabButton.visible = true;
        }
        this.syncZoneButtons(observers);
        this.syncModeButtons(observers);

        int inset = 18;
        int headerHeight = 68;
        int tabTop = this.panelTop + headerHeight + 4;
        int tabHeight = 24;
        int statusTop = tabTop + tabHeight + 6;
        int statusHeight = 42;
        int contentTop = statusTop + statusHeight + 12;
        int footerTop = this.panelTop + this.panelHeight - 44;
        int contentHeight = footerTop - contentTop - 10;
        int leftWidth = Math.min(250, Math.max(202, this.panelWidth / 5));
        int rightWidth = Math.min(274, Math.max(226, this.panelWidth / 4));
        int leftX = this.panelLeft + inset;
        int rightX = this.panelLeft + this.panelWidth - inset - rightWidth;
        int centerX = leftX + leftWidth + 12;
        int centerWidth = rightX - centerX - 12;
        NearbyObserver weaponFocus = this.focusWeaponObserver(observers, focusObserver);
        int footerReserve = this.screenMode == ScreenMode.WEAPONS && weaponFocus != null && this.isAutocannon(weaponFocus.weapon()) ? 490 : 396;

        g.fill(this.panelLeft + 5, this.panelTop + 7, this.panelLeft + this.panelWidth + 5, this.panelTop + this.panelHeight + 7, SHADOW);
        g.fill(this.panelLeft, this.panelTop, this.panelLeft + this.panelWidth, this.panelTop + this.panelHeight, SHELL);
        border(g, this.panelLeft, this.panelTop, this.panelWidth, this.panelHeight, STROKE);
        g.fill(this.panelLeft + 1, this.panelTop + 1, this.panelLeft + this.panelWidth - 1, this.panelTop + 3, CYAN);
        g.fill(this.panelLeft + 1, this.panelTop + 3, this.panelLeft + this.panelWidth - 1, this.panelTop + 4, 0x55315B70);

        this.renderHeader(g, observers, focusObserver, time, this.panelLeft + inset, this.panelTop + 10, this.panelWidth - inset * 2);
        this.renderModeRail(g, leftX, tabTop, this.panelWidth - inset * 2, tabHeight, observers);
        this.renderStatusBand(g, leftX, statusTop, this.panelWidth - inset * 2, statusHeight, observers, focusObserver, time);
        this.renderZoneStack(g, leftX, contentTop, leftWidth, contentHeight, observers, focusObserver, time);
        if (this.screenMode == ScreenMode.WEAPONS) {
            this.renderWeaponsTacticalDiagram(g, centerX, contentTop, centerWidth, contentHeight, observers, focusObserver, time);
            this.renderWeaponDetailConsole(g, rightX, contentTop, rightWidth, contentHeight, observers, focusObserver, time);
        } else {
            this.renderTacticalDiagram(g, centerX, contentTop, centerWidth, contentHeight, observers, focusObserver, time);
            this.renderObserverConsole(g, rightX, contentTop, rightWidth, contentHeight, observers, focusObserver, time);
        }
        this.renderAlertRail(g, leftX, footerTop, this.panelWidth - inset * 2 - footerReserve, 24, observers, focusObserver, time);

        super.render(g, mouseX, mouseY, partialTick);
        this.renderWeaponResponseSlider(g, mouseX, mouseY, time, weaponFocus);
        if (this.targetPickerOpen) {
            this.renderTargetPicker(g, mouseX, mouseY);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.targetPickerOpen) {
            return this.handleTargetPickerClick(mouseX, mouseY, button);
        }
        if (this.handleWeaponResponseSliderClick(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && this.screenMode == ScreenMode.WEAPONS && this.weaponTab == WeaponTab.LOADOUT
                && com.createstats.client.battle.WeaponCannonHologram.handleClick(mouseX, mouseY)) {
            return true;
        }
        if ((this.screenMode == ScreenMode.WEAPONS && this.hasWeaponsObserver(this.nearbyObservers()))
                || this.screenMode == ScreenMode.DAMAGE_MAP
                || this.screenMode == ScreenMode.LIGHTING) {
            for (WeaponHitTarget target : this.weaponHitTargets) {
                if (target.contains(mouseX, mouseY)) {
                    target.action().run();
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        for (ZoneClickTarget target : this.zoneClickTargets) {
            if (!target.contains(mouseX, mouseY)) {
                continue;
            }
            if (target.observerPos() != null) {
                this.selectedObserverPos = target.observerPos();
                if (target.category() == DamageObserverSettings.ObserverCategory.WEAPONS) {
                    this.screenMode = ScreenMode.WEAPONS;
                }
                return true;
            }
            if (target.category() == DamageObserverSettings.ObserverCategory.WEAPONS) {
                this.screenMode = ScreenMode.WEAPONS;
                this.selectFirstWeaponObserver(this.nearbyObservers());
            } else {
                this.screenMode = ScreenMode.DAMAGE_MAP;
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.lightTargetPickerOpen) {
            // Render-time clamp bounds this against the candidate count next frame.
            this.lightTargetPickerScroll = Math.max(0, this.lightTargetPickerScroll + (scrollY < 0.0 ? 1 : -1));
            return true;
        }
        if (this.targetPickerOpen) {
            NearbyObserver weaponObserver = this.focusWeaponObserver(this.nearbyObservers(), this.focusedObserver(this.nearbyObservers()));
            int targetCount = weaponObserver == null ? 0 : weaponObserver.weapon().autoAim().targetOptions().size();
            int visibleRows = this.targetPickerVisibleRows();
            int maxScroll = Math.max(0, targetCount - visibleRows);
            this.targetPickerScroll = Math.max(0, Math.min(maxScroll, this.targetPickerScroll + (scrollY < 0.0 ? 1 : -1)));
            return true;
        }
        if (this.screenMode == ScreenMode.WEAPONS && this.weaponTab == WeaponTab.LOADOUT
                && com.createstats.client.battle.WeaponCannonHologram.handleScroll(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (this.screenMode == ScreenMode.WEAPONS && this.hasWeaponsObserver(this.nearbyObservers())) {
            this.weaponPickerScroll = Math.max(0, Math.min(this.weaponPickerMaxScroll, this.weaponPickerScroll + (scrollY < 0.0 ? 1 : -1)));
            return true;
        }
        if (this.screenMode == ScreenMode.DAMAGE_MAP) {
            this.integrityRailScroll = Math.max(0, this.integrityRailScroll + (scrollY < 0.0 ? 1 : -1));
            return true;
        }
        if (this.screenMode == ScreenMode.LIGHTING) {
            // A hovered stepper row claims the wheel (nudge by one), else the channel rail scrolls.
            for (LightStepperTarget target : this.lightStepperTargets) {
                if (target.contains(mouseX, mouseY)) {
                    this.sendLightAction(target.observerPos(), target.lightPos(), scrollY > 0.0 ? target.up() : target.down());
                    return true;
                }
            }
            this.lightRailScroll = Math.max(0, this.lightRailScroll + (scrollY < 0.0 ? 1 : -1));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && this.weaponResponseSliderDragging && this.weaponResponseSliderTarget != null) {
            this.weaponResponseSliderPreview = this.responseMultiplierFromMouse(mouseX, this.weaponResponseSliderTarget);
            this.weaponResponseSliderPreviewActive = true;
            return true;
        }
        if (button == 0 && this.screenMode == ScreenMode.WEAPONS && this.weaponTab == WeaponTab.LOADOUT
                && com.createstats.client.battle.WeaponCannonHologram.handleDrag(mouseX, mouseY, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.weaponResponseSliderDragging) {
            if (this.weaponResponseSliderTarget != null) {
                this.weaponResponseSliderPreview = this.responseMultiplierFromMouse(mouseX, this.weaponResponseSliderTarget);
            }
            this.weaponResponseSliderDragging = false;
            this.commitWeaponResponseSlider();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.lightTargetPickerOpen && keyCode == 256) {
            this.lightTargetPickerOpen = false;
            return true;
        }
        if (this.targetPickerOpen && keyCode == 256) {
            this.targetPickerOpen = false;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ===================================================================================
    // Weapons workbench (redesigned WEAPONS presentation)
    // ===================================================================================

    private static void setButtonVisible(Button button, boolean visible) {
        if (button != null) {
            button.visible = visible;
        }
    }

    private void hideManagedButtons() {
        setButtonVisible(this.damageMapTabButton, false);
        setButtonVisible(this.weaponsTabButton, false);
        setButtonVisible(this.weaponPredictButton, false);
        setButtonVisible(this.weaponHoldButton, false);
        setButtonVisible(this.weaponLockButton, false);
        setButtonVisible(this.weaponAutoFireButton, false);
        setButtonVisible(this.weaponFireButton, false);
        setButtonVisible(this.pipHudButton, false);
        setButtonVisible(this.hologramButton, false);
        setButtonVisible(this.previousZoneButton, false);
        setButtonVisible(this.nextZoneButton, false);
    }

    private void setWeaponTab(WeaponTab tab) {
        if (this.weaponTab != tab) {
            this.weaponTab = tab;
            this.weaponTabSwitchMillis = System.currentTimeMillis();
            this.weaponPickerScroll = 0;
        }
    }

    private void selectWeaponObserver(NearbyObserver observer) {
        if (observer != null) {
            this.selectedObserverPos = observer.observerPos();
        }
    }

    private void renderWeaponsWorkbench(GuiGraphics g, int mouseX, int mouseY, float time, List<NearbyObserver> observers, NearbyObserver focusObserver) {
        this.weaponHitTargets.clear();
        int inset = 20;
        int left = this.panelLeft + inset;
        int right = this.panelLeft + this.panelWidth - inset;
        this.drawConsoleBackdrop(g, this.panelLeft + 2, this.panelTop + 5, this.panelWidth - 4, this.panelHeight - 7, time);

        List<NearbyObserver> weapons = new ArrayList<>();
        for (NearbyObserver observer : observers) {
            if (observer.category() == DamageObserverSettings.ObserverCategory.WEAPONS) {
                weapons.add(observer);
            }
        }
        NearbyObserver selected = this.focusWeaponObserver(observers, focusObserver);

        // header
        int headerY = this.panelTop + 14;
        this.drawScaledString(g, "BATTLE BRIDGE", left, headerY, TEXT, 2);
        g.drawString(this.font, "WEAPON SYSTEMS CONTROL", left, headerY + 18, CYAN, false);

        int online = 0;
        int damaged = 0;
        int targets = 0;
        for (NearbyObserver observer : weapons) {
            BattleScreenDisplayBE.WeaponSnapshot weapon = observer.weapon();
            if ("READY".equals(weapon.readiness())) {
                online++;
            }
            if (weapon.missingBlocks() > 0 || "DAMAGED".equals(weapon.readiness()) || "OFFLINE".equals(weapon.readiness())) {
                damaged++;
            }
            if (weapon.autoAim().targetPresent()) {
                targets++;
            }
        }
        int backW = 124;
        int backX = right - backW;
        int backY = headerY - 2;
        this.drawWorkbenchButton(g, mouseX, mouseY, backX, backY, backW, 22, "Integrity Map", false, true, () -> this.selectScreenMode(ScreenMode.DAMAGE_MAP));
        if (this.hasLightingObserver(observers)) {
            this.drawWorkbenchButton(g, mouseX, mouseY, backX - backW - 8, backY, backW, 22, "Lighting", false, true, () -> this.selectScreenMode(ScreenMode.LIGHTING));
        }
        int chipW = 96;
        int chipGap = 6;
        int chipY = headerY + 24;
        int chipX = right - (chipW * 3 + chipGap * 2);
        this.drawWorkbenchChip(g, chipX, chipY, chipW, "ONLINE", online + " / " + weapons.size(), online > 0 ? GREEN : TEXT_DIM);
        this.drawWorkbenchChip(g, chipX + chipW + chipGap, chipY, chipW, "DAMAGED", Integer.toString(damaged), damaged > 0 ? RED : TEXT_DIM);
        this.drawWorkbenchChip(g, chipX + (chipW + chipGap) * 2, chipY, chipW, "TARGETS", Integer.toString(targets), targets > 0 ? AMBER : TEXT_DIM);

        // tab bar
        int tabY = this.panelTop + 52;
        int tabH = 26;
        int tabW = 152;
        int tabGap = 8;
        boolean detailEnabled = selected != null && selected.weapon().detected();
        WeaponTab[] tabs = WeaponTab.values();
        for (int i = 0; i < tabs.length; i++) {
            WeaponTab tab = tabs[i];
            boolean enabled = tab == WeaponTab.OVERVIEW || detailEnabled;
            int tx = left + i * (tabW + tabGap);
            this.drawWorkbenchTab(g, mouseX, mouseY, tx, tabY, tabW, tabH, tab.label, this.weaponTab == tab, enabled, () -> this.setWeaponTab(tab));
        }

        // body
        int bodyTop = this.panelTop + 88;
        int bodyBottom = this.panelTop + this.panelHeight - 42;
        int bodyH = bodyBottom - bodyTop;
        int railW = 234;
        this.renderWeaponRail(g, mouseX, mouseY, time, left, bodyTop, railW, bodyH, weapons, selected);

        int mainX = left + railW + 14;
        int mainW = right - mainX;
        float intro = clamp((System.currentTimeMillis() - this.weaponTabSwitchMillis) / 150.0f, 0.0f, 1.0f);
        int slide = Math.round((1.0f - intro) * 12.0f);
        g.pose().pushPose();
        g.pose().translate(slide, 0, 0);
        WeaponTab activeTab = (this.weaponTab != WeaponTab.OVERVIEW && !detailEnabled) ? WeaponTab.OVERVIEW : this.weaponTab;
        switch (activeTab) {
            case OVERVIEW -> this.renderWeaponOverviewTab(g, mouseX - slide, mouseY, time, mainX, bodyTop, mainW, bodyH, weapons, selected);
            case LOADOUT -> this.renderWeaponLoadoutTab(g, mouseX - slide, mouseY, time, mainX, bodyTop, mainW, bodyH, selected);
            case FIRE_CONTROL -> this.renderWeaponFireControlTab(g, mouseX - slide, mouseY, time, mainX, bodyTop, mainW, bodyH, selected);
        }
        g.pose().popPose();

        String hint = switch (activeTab) {
            case OVERVIEW -> "Click a weapon card to inspect its loadout. Use the rail to switch weapons at any time.";
            case LOADOUT -> "Structure integrity and ammunition for the selected weapon.";
            case FIRE_CONTROL -> "Pick a target, tune tracking response, and fire. Auto-fire engages when aligned.";
        };
        g.drawString(this.font, hint, left, bodyBottom + 13, TEXT_DARK, false);
    }

    // rail
    private void renderWeaponRail(GuiGraphics g, int mouseX, int mouseY, float time, int x, int y, int width, int height, List<NearbyObserver> weapons, NearbyObserver selected) {
        g.fill(x, y, x + width, y + height, PANEL_DEEP);
        border(g, x, y, width, height, STROKE_DIM);
        g.fill(x, y, x + width, y + 22, PANEL);
        g.drawString(this.font, "WEAPON ROSTER", x + 10, y + 7, TEXT_DIM, false);
        g.drawString(this.font, Integer.toString(weapons.size()), x + width - 10 - this.font.width(Integer.toString(weapons.size())), y + 7, CYAN, false);

        int rowH = 56;
        int gap = 6;
        int rowX = x + 8;
        int rowW = width - 16;
        int rowY = y + 30;
        int maxRows = Math.max(1, (height - 38) / (rowH + gap));
        int shown = Math.min(weapons.size(), maxRows);
        for (int i = 0; i < shown; i++) {
            NearbyObserver observer = weapons.get(i);
            BattleScreenDisplayBE.WeaponSnapshot weapon = observer.weapon();
            int cy = rowY + i * (rowH + gap);
            boolean sel = selected != null && selected.observerPos().equals(observer.observerPos());
            boolean hover = this.inside(mouseX, mouseY, rowX, cy, rowW, rowH);
            int fill = sel ? 0x2629D982 : hover ? 0x1F315B70 : PANEL;
            int stroke = sel ? GREEN : hover ? CYAN : STROKE_DIM;
            g.fill(rowX, cy, rowX + rowW, cy + rowH, fill);
            border(g, rowX, cy, rowW, rowH, stroke);
            if (sel) {
                g.fill(rowX, cy, rowX + 3, cy + rowH, GREEN);
            }
            int statusColor = this.weaponReadinessColor(weapon);
            this.drawWeaponKindIcon(g, rowX + 8, cy + 8, weapon);
            g.drawString(this.font, this.trimToWidth(observer.name(), rowW - 56), rowX + 34, cy + 8, TEXT, false);
            g.fill(rowX + 34, cy + 21, rowX + 40, cy + 27, statusColor);
            g.drawString(this.font, this.trimToWidth(weapon.readiness() + " / " + weapon.loadedState(), rowW - 78), rowX + 44, cy + 20, statusColor, false);
            float integrity = weapon.baselineBlocks() > 0 ? clamp(weapon.presentBlocks() / (float) weapon.baselineBlocks(), 0.0f, 1.0f) : 0.0f;
            this.drawConsoleBar(g, "rail:" + observer.observerPos().asLong(), rowX + 8, cy + 36, rowW - 16, 6, integrity, integrity > 0.66f ? GREEN : integrity > 0.33f ? AMBER : RED);
            String sub = this.isAutocannon(weapon) ? "Ammo " + this.weaponAmmoText(weapon) : this.weaponKindText(weapon);
            g.drawString(this.font, this.trimToWidth(sub, rowW - 16), rowX + 8, cy + 45, TEXT_DARK, false);
            this.weaponHitTargets.add(new WeaponHitTarget(rowX, cy, rowW, rowH, () -> this.selectWeaponObserver(observer)));
        }
        if (weapons.size() > shown) {
            g.drawString(this.font, "+" + (weapons.size() - shown) + " more", rowX, y + height - 14, TEXT_DARK, false);
        }
    }

    // overview tab
    private void renderWeaponOverviewTab(GuiGraphics g, int mouseX, int mouseY, float time, int x, int y, int width, int height, List<NearbyObserver> weapons, NearbyObserver selected) {
        g.fill(x, y, x + width, y + 22, PANEL);
        border(g, x, y, width, 22, STROKE_DIM);
        g.drawString(this.font, "FLEET OVERVIEW", x + 10, y + 7, TEXT_DIM, false);

        int gridTop = y + 32;
        int cols = width >= 470 ? 2 : 1;
        int gap = 12;
        int cardW = (width - gap * (cols - 1)) / cols;
        int cardH = 108;
        for (int i = 0; i < weapons.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int cx = x + col * (cardW + gap);
            int cy = gridTop + row * (cardH + gap);
            if (cy + cardH > y + height) {
                break;
            }
            this.drawOverviewCard(g, mouseX, mouseY, time, cx, cy, cardW, cardH, weapons.get(i), selected);
        }
    }

    private void drawOverviewCard(GuiGraphics g, int mouseX, int mouseY, float time, int x, int y, int width, int height, NearbyObserver observer, NearbyObserver selected) {
        BattleScreenDisplayBE.WeaponSnapshot weapon = observer.weapon();
        boolean sel = selected != null && selected.observerPos().equals(observer.observerPos());
        boolean hover = this.inside(mouseX, mouseY, x, y, width, height);
        g.fill(x + 3, y + 4, x + width + 3, y + height + 4, SHADOW);
        g.fill(x, y, x + width, y + height, hover || sel ? 0x141B2531 : PANEL);
        border(g, x, y, width, height, sel ? GREEN : hover ? CYAN : STROKE);
        int statusColor = this.weaponReadinessColor(weapon);
        g.fill(x, y, x + width, y + 2, statusColor);

        this.drawWeaponKindIcon(g, x + 10, y + 10, weapon);
        g.drawString(this.font, this.trimToWidth(observer.name(), width - 130), x + 36, y + 10, TEXT, false);
        g.drawString(this.font, this.trimToWidth(this.weaponKindText(weapon), width - 130), x + 36, y + 22, TEXT_DIM, false);

        String badge = weapon.readiness();
        int badgeW = Math.max(70, this.font.width(badge) + 16);
        int badgeX = x + width - badgeW - 8;
        g.fill(badgeX, y + 9, badgeX + badgeW, y + 25, 0x33000000 | (statusColor & 0x00FFFFFF));
        border(g, badgeX, y + 9, badgeW, 16, statusColor);
        g.drawString(this.font, badge, badgeX + (badgeW - this.font.width(badge)) / 2, y + 13, statusColor, false);

        float integrity = weapon.baselineBlocks() > 0 ? clamp(weapon.presentBlocks() / (float) weapon.baselineBlocks(), 0.0f, 1.0f) : 0.0f;
        int integrityColor = integrity > 0.66f ? GREEN : integrity > 0.33f ? AMBER : RED;
        g.drawString(this.font, "INTEGRITY", x + 10, y + 42, TEXT_DARK, false);
        String pct = Math.round(integrity * 100) + "%";
        g.drawString(this.font, pct, x + width - 10 - this.font.width(pct), y + 42, integrityColor, false);
        this.drawConsoleBar(g, "ovI:" + observer.observerPos().asLong(), x + 10, y + 54, width - 20, 7, integrity, integrityColor);

        if (this.isAutocannon(weapon)) {
            float ammo = weapon.ammoCreative() ? 1.0f : (this.weaponAmmoCapacity(weapon) > 0 ? clamp(weapon.ammoRounds() / (float) this.weaponAmmoCapacity(weapon), 0.0f, 1.0f) : 0.0f);
            int ammoColor = weapon.ammoCreative() ? VIOLET : ammo > 0.25f ? CYAN : RED;
            g.drawString(this.font, "AMMUNITION", x + 10, y + 68, TEXT_DARK, false);
            g.drawString(this.font, this.weaponAmmoText(weapon), x + width - 10 - this.font.width(this.weaponAmmoText(weapon)), y + 68, ammoColor, false);
            this.drawConsoleBar(g, "ovA:" + observer.observerPos().asLong(), x + 10, y + 80, width - 20, 7, ammo, ammoColor);
        } else {
            int loaded = weapon.barrelPayloadBlocks() + weapon.chamberPayloadBlocks() + weapon.breechPayloadBlocks();
            g.drawString(this.font, "PAYLOAD", x + 10, y + 68, TEXT_DARK, false);
            g.drawString(this.font, weapon.loadedState(), x + width - 10 - this.font.width(weapon.loadedState()), y + 68, this.weaponLoadColor(weapon), false);
            g.drawString(this.font, this.trimToWidth("Loaded " + loaded + "  Proj " + weapon.projectileBlocks() + "  Charge " + weapon.propellantBlocks(), width - 20), x + 10, y + 82, TEXT_DIM, false);
        }

        BattleScreenDisplayBE.AutoAimSnapshot autoAim = weapon.autoAim();
        if (autoAim.mountAvailable()) {
            int dotColor = autoAim.targetLocked() ? GREEN : autoAim.targetPresent() ? AMBER : TEXT_DARK;
            g.fill(x + width - 14, y + 30, x + width - 8, y + 36, dotColor);
        }

        this.weaponHitTargets.add(new WeaponHitTarget(x, y, width, height, () -> {
            this.selectWeaponObserver(observer);
            this.setWeaponTab(WeaponTab.LOADOUT);
        }));
    }

    // loadout tab
    private void renderWeaponLoadoutTab(GuiGraphics g, int mouseX, int mouseY, float time, int x, int y, int width, int height, NearbyObserver selected) {
        if (selected == null) {
            this.drawWorkbenchEmpty(g, x, y, width, height, "NO WEAPON SELECTED");
            return;
        }
        BattleScreenDisplayBE.WeaponSnapshot weapon = selected.weapon();

        g.fill(x, y, x + width, y + 30, PANEL);
        border(g, x, y, width, 30, STROKE);
        this.drawWeaponKindIcon(g, x + 10, y + 8, weapon);
        g.drawString(this.font, this.trimToWidth(selected.name(), width - 220), x + 36, y + 6, TEXT, false);
        g.drawString(this.font, this.trimToWidth(this.weaponKindText(weapon) + (weapon.material().equals("Unknown") ? "" : " / " + weapon.material()), width - 220), x + 36, y + 18, TEXT_DIM, false);
        int statusColor = this.weaponReadinessColor(weapon);
        String badge = weapon.readiness() + " / " + weapon.loadedState();
        int badgeW = this.font.width(badge) + 16;
        int badgeX = x + width - badgeW - 8;
        g.fill(badgeX, y + 7, badgeX + badgeW, y + 23, 0x33000000 | (statusColor & 0x00FFFFFF));
        border(g, badgeX, y + 7, badgeW, 16, statusColor);
        g.drawString(this.font, badge, badgeX + 8, y + 11, statusColor, false);

        if (com.createstats.client.battle.WeaponCannonHologram.isExpanded()) {
            int hTop = y + 38;
            com.createstats.client.battle.WeaponCannonHologram.render(g, this.font, x, hTop, width, y + height - hTop, selected.observerPos(), time);
            return;
        }

        int schY = y + 38;
        int schH = Math.max(120, (height - 38) * 9 / 20);
        g.fill(x, schY, x + width, schY + schH, PANEL_DEEP);
        border(g, x, schY, width, schH, STROKE_DIM);
        g.drawString(this.font, "STRUCTURE SCHEMATIC", x + 10, schY + 7, TEXT_DIM, false);
        // Live 3D holographic structure of the actual cannon (right inset), reusing the Battle Bridge
        // voxel feed; the labelled flat schematic stays on the left for part identification.
        int holoW = Math.max(120, Math.min(232, (width - 24) * 2 / 5));
        int holoH = schH - 28;
        int holoX = x + width - holoW - 8;
        int holoY = schY + 20;
        int schematicW = Math.max(80, holoX - (x + 12) - 8);
        this.drawCannonSchematic(g, x + 12, schY + 22, schematicW, schH - 30, weapon, time, selected.observerPos().asLong());
        com.createstats.client.battle.WeaponCannonHologram.render(g, this.font, holoX, holoY, holoW, holoH, selected.observerPos(), time);

        int panelTop = schY + schH + 10;
        int panelH = y + height - panelTop;
        int colGap = 10;
        int colW = (width - colGap) / 2;
        this.drawIntegrityPanel(g, x, panelTop, colW, panelH, weapon);
        this.drawAmmoPayloadPanel(g, x + colW + colGap, panelTop, width - colW - colGap, panelH, weapon);
    }

    private void drawIntegrityPanel(GuiGraphics g, int x, int y, int width, int height, BattleScreenDisplayBE.WeaponSnapshot weapon) {
        g.fill(x, y, x + width, y + height, PANEL_DEEP);
        border(g, x, y, width, height, STROKE_DIM);
        g.fill(x, y, x + width, y + 20, PANEL);
        g.drawString(this.font, "STRUCTURE INTEGRITY", x + 10, y + 6, TEXT_DIM, false);

        int rowY = y + 28;
        int step = 18;
        rowY = this.drawIntegrityRow(g, x + 10, rowY, width - 20, "Barrel", weapon.barrelBlocks(), weapon.missingBarrelBlocks(), step);
        if (!this.isAutocannon(weapon)) {
            rowY = this.drawIntegrityRow(g, x + 10, rowY, width - 20, "Chamber", weapon.chamberBlocks(), 0, step);
        }
        rowY = this.drawIntegrityRow(g, x + 10, rowY, width - 20, "Breech", weapon.breechBlocks(), weapon.missingBreechBlocks(), step);
        rowY = this.drawIntegrityRow(g, x + 10, rowY, width - 20, "Muzzle", weapon.muzzleBlocks(), 0, step);
        rowY = this.drawIntegrityRow(g, x + 10, rowY, width - 20, "Mount", weapon.mountBlocks(), weapon.missingMountBlocks(), step);
        rowY = this.drawIntegrityRow(g, x + 10, rowY, width - 20, "Loader", weapon.loaderBlocks(), weapon.missingLoaderBlocks(), step);
        if (this.isAutocannon(weapon)) {
            rowY = this.drawIntegrityRow(g, x + 10, rowY, width - 20, "Recoil spring", weapon.recoilBlocks(), 0, step);
        }
        if (rowY + 14 < y + height) {
            String warn = this.weaponWarningLine(weapon);
            int warnColor = this.missingCriticalWeaponParts(weapon) > 0 ? RED : weapon.missingBlocks() > 0 ? AMBER : GREEN;
            g.drawString(this.font, this.trimToWidth(warn, width - 20), x + 10, y + height - 14, warnColor, false);
        }
    }

    private int drawIntegrityRow(GuiGraphics g, int x, int y, int width, String label, int present, int missing, int step) {
        int total = present + missing;
        int color = missing > 0 ? RED : present > 0 ? GREEN : TEXT_DARK;
        g.drawString(this.font, label, x, y, present > 0 || missing > 0 ? TEXT : TEXT_DARK, false);
        String value = missing > 0 ? present + " / " + total + "  (-" + missing + ")" : present > 0 ? present + " ok" : "none";
        g.drawString(this.font, value, x + width - this.font.width(value), y, color, false);
        return y + step;
    }

    private void drawAmmoPayloadPanel(GuiGraphics g, int x, int y, int width, int height, BattleScreenDisplayBE.WeaponSnapshot weapon) {
        g.fill(x, y, x + width, y + height, PANEL_DEEP);
        border(g, x, y, width, height, STROKE_DIM);
        g.fill(x, y, x + width, y + 20, PANEL);
        g.drawString(this.font, this.isAutocannon(weapon) ? "AMMUNITION FEED" : "MUNITIONS LOADED", x + 10, y + 6, TEXT_DIM, false);

        int cy = y + 30;
        if (this.isAutocannon(weapon)) {
            float ammo = weapon.ammoCreative() ? 1.0f : (this.weaponAmmoCapacity(weapon) > 0 ? clamp(weapon.ammoRounds() / (float) this.weaponAmmoCapacity(weapon), 0.0f, 1.0f) : 0.0f);
            int ammoColor = weapon.ammoCreative() ? VIOLET : weapon.ammoRounds() > 0 ? CYAN : RED;
            g.drawString(this.font, "Rounds", x + 10, cy, TEXT, false);
            g.drawString(this.font, this.weaponAmmoText(weapon), x + width - 10 - this.font.width(this.weaponAmmoText(weapon)), cy, ammoColor, false);
            this.drawConsoleBar(g, "loA", x + 10, cy + 12, width - 20, 7, ammo, ammoColor);
            cy += 28;
            String tracers = Integer.toString(weapon.tracerRounds());
            g.drawString(this.font, "Tracers", x + 10, cy, TEXT, false);
            g.drawString(this.font, tracers, x + width - 10 - this.font.width(tracers), cy, weapon.tracerRounds() > 0 ? AMBER : TEXT_DARK, false);
            cy += 16;
            String mags = weapon.ammoCreative() ? "Creative" : Integer.toString(weapon.ammoContainers());
            g.drawString(this.font, "Magazines", x + 10, cy, TEXT, false);
            g.drawString(this.font, mags, x + width - 10 - this.font.width(mags), cy, weapon.ammoContainers() > 0 || weapon.ammoCreative() ? GREEN : RED, false);
            cy += 16;
            String rpm = this.weaponFireRateText(weapon) + " RPM";
            g.drawString(this.font, "Fire rate", x + 10, cy, TEXT, false);
            g.drawString(this.font, rpm, x + width - 10 - this.font.width(rpm), cy, CYAN, false);
            cy += 16;
            String mode = weapon.autoFireEnabled() ? "AUTO" : "SEMI";
            g.drawString(this.font, "Fire mode", x + 10, cy, TEXT, false);
            g.drawString(this.font, mode, x + width - 10 - this.font.width(mode), cy, weapon.autoFireEnabled() ? GREEN : TEXT_DIM, false);
        } else {
            g.drawString(this.font, "State", x + 10, cy, TEXT, false);
            g.drawString(this.font, weapon.loadedState(), x + width - 10 - this.font.width(weapon.loadedState()), cy, this.weaponLoadColor(weapon), false);
            cy += 16;
            g.drawString(this.font, "Shell / Charge", x + 10, cy, TEXT, false);
            String pcText = weapon.projectileBlocks() + " / " + weapon.propellantBlocks();
            g.drawString(this.font, pcText, x + width - 10 - this.font.width(pcText), cy, weapon.projectileBlocks() > 0 ? VIOLET : TEXT_DARK, false);
            cy += 14;
            // Munition stack: shell block(s) in violet, powder charges as amber pips.
            int sx = x + 10;
            int shells = Math.max(0, Math.min(3, weapon.projectileBlocks()));
            for (int s = 0; s < shells; s++) {
                g.fill(sx, cy, sx + 13, cy + 8, VIOLET);
                border(g, sx, cy, 13, 8, 0xFF1A1030);
                sx += 16;
            }
            int charges = Math.max(0, Math.min(12, weapon.propellantBlocks()));
            for (int ci = 0; ci < charges; ci++) {
                g.fill(sx, cy + 2, sx + 7, cy + 8, AMBER);
                sx += 9;
            }
            if (shells == 0 && charges == 0) {
                g.drawString(this.font, "EMPTY", x + 10, cy, TEXT_DARK, false);
            }
            cy += 14;
            g.drawString(this.font, "Charge power", x + 10, cy, TEXT_DIM, false);
            cy += 11;
            float chargePower = clamp(weapon.propellantBlocks() / 6.0f, 0.0f, 1.0f);
            this.drawConsoleBar(g, "loC", x + 10, cy, width - 20, 7, chargePower, AMBER);
            cy += 14;
            int loaded = weapon.barrelPayloadBlocks() + weapon.chamberPayloadBlocks() + weapon.breechPayloadBlocks();
            int totalCells = Math.max(loaded, weapon.inspectedPayloadBlocks());
            g.drawString(this.font, "Loaded cells", x + 10, cy, TEXT_DIM, false);
            String cellsStr = loaded + " / " + totalCells;
            g.drawString(this.font, cellsStr, x + width - 10 - this.font.width(cellsStr), cy, loaded > 0 ? CYAN : TEXT_DARK, false);
            cy += 11;
            this.drawConsoleBar(g, "loL", x + 10, cy, width - 20, 6, totalCells > 0 ? loaded / (float) totalCells : 0.0f, CYAN);
        }
    }

    // cannon schematic
    private void drawCannonSchematic(GuiGraphics g, int x, int y, int width, int height, BattleScreenDisplayBE.WeaponSnapshot weapon, float time, long obsKey) {
        boolean auto = this.isAutocannon(weapon);
        int centerY = y + height / 2 - 6;
        int colMissing = lerpColor(RED, 0xFF3A1820, 0.4f);
        int loadGlow = lerpColor(0x33A985FF, 0x99A985FF, (float) (Math.sin(time * 0.2f) * 0.5 + 0.5));
        float flash = this.fireFlash(obsKey, auto ? weapon.ammoRounds() : weapon.projectileBlocks() + weapon.propellantBlocks());

        int mountX = x + 4;
        int mountW = 30;
        int mountTop = centerY - 4;
        int mountBottom = y + height - 4;
        int mountColor = weapon.missingMountBlocks() > 0 ? RED : weapon.mountBlocks() > 0 ? lerpColor(STROKE, CYAN, 0.3f) : TEXT_DARK;
        g.fill(mountX, mountTop, mountX + mountW, mountBottom, PANEL);
        border(g, mountX, mountTop, mountW, mountBottom - mountTop, mountColor);
        g.fill(mountX + mountW / 2 - 2, mountTop, mountX + mountW / 2 + 2, mountBottom, weapon.mountBlocks() > 0 ? mountColor : TEXT_DARK);

        int cursorX = mountX + mountW + 6;

        int breechW = 36;
        int breechH = 30;
        int breechTop = centerY - breechH / 2;
        int breechColor = weapon.missingBreechBlocks() > 0 ? RED : weapon.breechBlocks() > 0 ? GREEN : TEXT_DARK;
        g.fill(cursorX, breechTop, cursorX + breechW, breechTop + breechH, PANEL);
        border(g, cursorX, breechTop, breechW, breechH, breechColor);
        if (weapon.breechPayloadBlocks() > 0) {
            g.fill(cursorX + 4, breechTop + 4, cursorX + breechW - 4, breechTop + breechH - 4, loadGlow);
        }
        if (auto) {
            int boxW = 28;
            int boxH = 16;
            int boxX = cursorX + 4;
            int boxY = breechTop - boxH - 4;
            float ammo = weapon.ammoCreative() ? 1.0f : (this.weaponAmmoCapacity(weapon) > 0 ? clamp(weapon.ammoRounds() / (float) this.weaponAmmoCapacity(weapon), 0.0f, 1.0f) : 0.0f);
            g.fill(boxX, boxY, boxX + boxW, boxY + boxH, PANEL);
            border(g, boxX, boxY, boxW, boxH, weapon.ammoContainers() > 0 || weapon.ammoCreative() ? CYAN : RED);
            int fillH = Math.round((boxH - 4) * ammo);
            g.fill(boxX + 2, boxY + boxH - 2 - fillH, boxX + boxW - 2, boxY + boxH - 2, weapon.ammoCreative() ? VIOLET : CYAN);
            g.fill(boxX + boxW / 2 - 1, boxY + boxH, boxX + boxW / 2 + 1, breechTop, STROKE);
        }
        cursorX += breechW + 2;

        if (!auto) {
            int chW = 22;
            int chH = 24;
            int chTop = centerY - chH / 2;
            int chColor = weapon.chamberBlocks() > 0 ? GREEN : TEXT_DARK;
            g.fill(cursorX, chTop, cursorX + chW, chTop + chH, PANEL);
            border(g, cursorX, chTop, chW, chH, chColor);
            if (weapon.chamberPayloadBlocks() > 0) {
                g.fill(cursorX + 3, chTop + 3, cursorX + chW - 3, chTop + chH - 3, loadGlow);
            }
            cursorX += chW + 2;
        }

        int muzzleW = 16;
        int barrelEndX = x + width - muzzleW - 6;
        int barrelStartX = cursorX;
        int barrelSpan = Math.max(20, barrelEndX - barrelStartX);
        int totalBarrel = Math.max(1, weapon.barrelBlocks());
        int segs = Math.min(12, totalBarrel);
        int missingSegs = totalBarrel > 0 ? Math.min(segs, Math.round(segs * (weapon.missingBarrelBlocks() / (float) totalBarrel))) : 0;
        int barrelH = 18;
        int barrelTop = centerY - barrelH / 2;
        int segGap = 2;
        int segW = Math.max(4, (barrelSpan - segGap * (segs - 1)) / segs);
        boolean loadedBarrel = weapon.barrelPayloadBlocks() > 0;
        for (int i = 0; i < segs; i++) {
            int sx = barrelStartX + i * (segW + segGap);
            boolean missing = i >= segs - missingSegs;
            int color = missing ? RED : GREEN;
            g.fill(sx, barrelTop, sx + segW, barrelTop + barrelH, missing ? colMissing : PANEL);
            border(g, sx, barrelTop, segW, barrelH, color);
            if (!missing && loadedBarrel && i < Math.min(segs, weapon.barrelPayloadBlocks())) {
                g.fill(sx + 2, barrelTop + 3, sx + segW - 2, barrelTop + barrelH - 3, loadGlow);
            }
        }
        g.fill(barrelStartX, centerY - 1, barrelEndX, centerY + 1, missingSegs > 0 ? AMBER : 0x66000000);

        int muzzleTop = centerY - 12;
        int muzzleColor = weapon.muzzleBlocks() > 0 ? GREEN : weapon.barrelBlocks() > 0 ? AMBER : TEXT_DARK;
        g.fill(barrelEndX, muzzleTop, barrelEndX + muzzleW, muzzleTop + 24, PANEL);
        border(g, barrelEndX, muzzleTop, muzzleW, 24, muzzleColor);
        if ("READY".equals(weapon.readiness()) && ("LOADED".equals(weapon.loadedState()) || weapon.ammoRounds() > 0 || weapon.ammoCreative())) {
            float pulse = (float) (Math.sin(time * 0.18f) * 0.5f + 0.5f);
            int glow = lerpColor(0x00FFC15E, 0x88FFC15E, pulse);
            g.fill(barrelEndX + muzzleW, centerY - 2, barrelEndX + muzzleW + 6, centerY + 2, glow);
        }
        if (flash > 0.02f) {
            int fa = Math.min(220, (int) (flash * 220));
            int gx = barrelEndX + muzzleW;
            int ext = (int) (flash * 12);
            g.fill(gx, centerY - 3 - (int) (flash * 3), gx + 8 + ext, centerY + 3 + (int) (flash * 3), (fa << 24) | 0x00FFE6A8);
            g.fill(gx + 8 + ext, centerY - 1, gx + 13 + ext, centerY + 1, (Math.min(255, (int) (flash * 255)) << 24) | 0x00FFFFFF);
        }

        if (weapon.loaderBlocks() > 0) {
            int la = mountX + mountW + 6;
            int loaderColor = weapon.missingLoaderBlocks() > 0 ? RED : VIOLET;
            g.fill(la, mountBottom - 10, la + breechW, mountBottom - 6, 0x33000000 | (loaderColor & 0x00FFFFFF));
            border(g, la, mountBottom - 10, breechW, 4, loaderColor);
            if ("LOADING".equals(weapon.loadedState())) {
                int travel = (int) ((time * 1.5f) % Math.max(1, breechW));
                g.fill(la + travel, mountBottom - 11, la + Math.min(breechW, travel + 6), mountBottom - 5, AMBER);
            }
        }

        int labelY = y + height - 10;
        g.drawString(this.font, "Mount", mountX, labelY, weapon.missingMountBlocks() > 0 ? RED : TEXT_DIM, false);
        g.drawString(this.font, "Breech", mountX + mountW + 6, labelY, weapon.missingBreechBlocks() > 0 ? RED : TEXT_DIM, false);
        String barrelLabel = "Barrel x" + weapon.barrelBlocks();
        g.drawString(this.font, barrelLabel, barrelStartX + (barrelSpan - this.font.width(barrelLabel)) / 2, labelY, missingSegs > 0 ? RED : TEXT_DIM, false);
        g.drawString(this.font, "Muzzle", barrelEndX - 6, labelY, muzzleColor, false);
    }

    // fire control tab
    private void renderWeaponFireControlTab(GuiGraphics g, int mouseX, int mouseY, float time, int x, int y, int width, int height, NearbyObserver selected) {
        if (selected == null) {
            this.drawWorkbenchEmpty(g, x, y, width, height, "NO WEAPON SELECTED");
            return;
        }
        BattleScreenDisplayBE.WeaponSnapshot weapon = selected.weapon();
        BattleScreenDisplayBE.AutoAimSnapshot autoAim = weapon.autoAim();

        int colGap = 12;
        int leftW = Math.max(220, width * 42 / 100);
        int rightX = x + leftW + colGap;
        int rightW = x + width - rightX;

        g.fill(x, y, x + leftW, y + height, PANEL_DEEP);
        border(g, x, y, leftW, height, STROKE_DIM);
        g.fill(x, y, x + leftW, y + 20, PANEL);
        g.drawString(this.font, "TARGETING", x + 10, y + 6, TEXT_DIM, false);
        int reticleSize = Math.min(leftW - 24, height - 96);
        int rcx = x + leftW / 2;
        int rcy = y + 26 + reticleSize / 2;
        this.drawAimReticle(g, rcx, rcy, reticleSize / 2, selected, autoAim, time);
        int readY = y + 36 + reticleSize;
        if (autoAim.mountAvailable()) {
            this.drawReadoutLine(g, x + 12, readY, leftW - 24, "Yaw error", String.format(java.util.Locale.ROOT, "%+.1f deg", autoAim.yawErrorDegrees()), Math.abs(autoAim.yawErrorDegrees()) < 2.5f ? GREEN : AMBER);
            this.drawReadoutLine(g, x + 12, readY + 14, leftW - 24, "Pitch error", String.format(java.util.Locale.ROOT, "%+.1f deg", autoAim.pitchErrorDegrees()), Math.abs(autoAim.pitchErrorDegrees()) < 2.5f ? GREEN : AMBER);
            if (autoAim.targetPresent()) {
                this.drawReadoutLine(g, x + 12, readY + 28, leftW - 24, "Range", String.format(java.util.Locale.ROOT, "%.0f m", autoAim.targetDistance()), CYAN);
            }
            if (autoAim.predictionActive() && !autoAim.predictionStatusText().isEmpty()) {
                this.drawReadoutLine(g, x + 12, readY + 42, leftW - 24, "Lead", autoAim.predictionStatusText(), VIOLET);
            } else if (autoAim.predictionEnabled() && autoAim.targetPresent() && !autoAim.predictionStatusText().isEmpty()) {
                this.drawReadoutLine(g, x + 12, readY + 42, leftW - 24, "Lead", autoAim.predictionStatusText(), TEXT_DARK);
            }
        } else {
            g.drawString(this.font, this.trimToWidth("Mount offline / no cannon mount linked", leftW - 24), x + 12, readY, TEXT_DARK, false);
        }

        g.fill(rightX, y, rightX + rightW, y + 30, PANEL);
        border(g, rightX, y, rightW, 30, STROKE);
        int aimColor = this.weaponAutoAimColor(weapon);
        g.drawString(this.font, autoAim.targetLocked() ? "LOCKED TARGET" : autoAim.targetPresent() ? "TRACKING" : "NO TARGET", rightX + 10, y + 6, aimColor, false);
        String tdesc = autoAim.targetPresent() ? this.trimToWidth(autoAim.targetLabel().isEmpty() ? autoAim.targetType() : autoAim.targetLabel(), rightW - 100) : autoAim.statusText().isEmpty() ? "Scanning" : autoAim.statusText();
        g.drawString(this.font, tdesc, rightX + 10, y + 18, TEXT, false);
        if (autoAim.predictionActive()) {
            g.drawString(this.font, "LEAD", rightX + rightW - 40, y + 11, VIOLET, false);
        }

        int cY = y + 38;
        int btnH = 20;
        int gap = 6;
        int halfW = (rightW - gap) / 2;
        boolean lockable = this.supportsAimLock(weapon);
        boolean structureOk = weapon.missingBlocks() == 0;
        this.drawWorkbenchButton(g, mouseX, mouseY, rightX, cY, halfW, btnH, autoAim.holdEnabled() ? "Keep Lock: ON" : "Keep Lock: OFF", autoAim.holdEnabled(), lockable && structureOk, () -> this.sendWeaponAction(BattleScreenWeaponActionPacket.Action.TOGGLE_AUTO_AIM_HOLD));
        this.drawWorkbenchButton(g, mouseX, mouseY, rightX + halfW + gap, cY, halfW, btnH, autoAim.predictionEnabled() ? "Predict: ON" : "Predict: OFF", autoAim.predictionEnabled(), lockable && structureOk, () -> this.sendWeaponAction(BattleScreenWeaponActionPacket.Action.TOGGLE_AUTO_AIM_PREDICTION));
        cY += btnH + gap;
        if (this.isAutocannon(weapon)) {
            this.drawWorkbenchButton(g, mouseX, mouseY, rightX, cY, halfW, btnH, autoAim.advancedPredictionEnabled() ? "Advanced: ON" : "Advanced: OFF", autoAim.advancedPredictionEnabled(), lockable && structureOk, () -> this.sendWeaponAction(BattleScreenWeaponActionPacket.Action.TOGGLE_AUTO_AIM_ADVANCED_PREDICTION));
            String modeHint = autoAim.advancedPredictionEnabled() ? "PHYSICS SIM" : autoAim.predictionEnabled() ? "KINEMATIC" : "OFF";
            int modeColor = autoAim.advancedPredictionEnabled() ? VIOLET : autoAim.predictionEnabled() ? CYAN : TEXT_DARK;
            g.drawString(this.font, "Lead model", rightX + halfW + gap + 4, cY + 2, TEXT_DARK, false);
            g.drawString(this.font, modeHint, rightX + halfW + gap + 4, cY + 11, modeColor, false);
            cY += btnH + gap;
        }
        this.drawWorkbenchButton(g, mouseX, mouseY, rightX, cY, halfW, btnH, "Lock Facing", false, lockable && structureOk, () -> this.sendWeaponAction(BattleScreenWeaponActionPacket.Action.LOCK_FACING_TARGET));
        this.drawWorkbenchButton(g, mouseX, mouseY, rightX + halfW + gap, cY, halfW, btnH, "Release Lock", false, lockable && autoAim.targetLocked(), () -> this.sendWeaponAction(BattleScreenWeaponActionPacket.Action.CLEAR_AUTO_AIM_LOCK));
        cY += btnH + gap;
        if (this.isAutocannon(weapon)) {
            this.drawWorkbenchButton(g, mouseX, mouseY, rightX, cY, halfW, btnH, weapon.autoFireEnabled() ? "Auto Fire: ON" : "Auto Fire: OFF", weapon.autoFireEnabled(), structureOk, () -> this.sendWeaponAction(BattleScreenWeaponActionPacket.Action.TOGGLE_AUTO_FIRE));
            this.drawWorkbenchButton(g, mouseX, mouseY, rightX + halfW + gap, cY, halfW, btnH, "Linked Ctrl", false, lockable, () -> this.sendWeaponAction(BattleScreenWeaponActionPacket.Action.OPEN_LINKED_CONTROLLER));
        } else {
            this.drawWorkbenchButton(g, mouseX, mouseY, rightX, cY, rightW, btnH, "Linked Controller", false, lockable, () -> this.sendWeaponAction(BattleScreenWeaponActionPacket.Action.OPEN_LINKED_CONTROLLER));
        }
        cY += btnH + gap;

        if (lockable) {
            cY = this.drawWorkbenchResponseSlider(g, mouseX, mouseY, time, rightX, cY, rightW, selected) + gap;
        }

        boolean canFire = structureOk && "READY".equals(weapon.readiness());
        String fireLabel = canFire ? "FIRE" : this.trimToWidth(this.weaponCommandStatus(weapon), rightW - 20);
        this.drawFireButton(g, mouseX, mouseY, rightX, cY, rightW, 26, fireLabel, canFire);
        cY += 26 + gap;

        int pickerTop = cY;
        int pickerH = y + height - pickerTop;
        if (pickerH > 50) {
            this.drawInlineTargetPicker(g, mouseX, mouseY, rightX, pickerTop, rightW, pickerH, autoAim);
        }
    }

    private void drawAimReticle(GuiGraphics g, int cx, int cy, int radius, NearbyObserver selected, BattleScreenDisplayBE.AutoAimSnapshot autoAim, float time) {
        radius = Math.max(34, radius);
        // Glassy backdrop disc.
        g.fill(cx - radius - 5, cy - radius - 5, cx + radius + 5, cy + radius + 5, 0x33040A12);

        // Rotating graduated bezel: spins gently while searching, holds steady once tracking.
        int bezel = radius + 3;
        this.ring(g, cx, cy, bezel, bezel, STROKE_DIM);
        float bezelSpin = autoAim.targetPresent() ? 0.0f : (time * 0.5f);
        int ticks = 36;
        for (int i = 0; i < ticks; i++) {
            double a = Math.toRadians(i * (360.0 / ticks) + bezelSpin);
            int len = i % 3 == 0 ? 6 : 3;
            int ox = cx + (int) Math.round(Math.cos(a) * bezel);
            int oy = cy + (int) Math.round(Math.sin(a) * bezel);
            int ix = cx + (int) Math.round(Math.cos(a) * (bezel - len));
            int iy = cy + (int) Math.round(Math.sin(a) * (bezel - len));
            this.drawLine(g, ix, iy, ox, oy, i % 9 == 0 ? CYAN : STROKE_DIM);
        }

        // Range rings + a center-gapped crosshair.
        this.ring(g, cx, cy, radius * 2 / 3, radius * 2 / 3, 0x33315B70);
        this.ring(g, cx, cy, radius / 3, radius / 3, 0x33315B70);
        int gap = 6;
        g.fill(cx - radius, cy, cx - gap, cy + 1, 0x55315B70);
        g.fill(cx + gap, cy, cx + radius, cy + 1, 0x55315B70);
        g.fill(cx, cy - radius, cx + 1, cy - gap, 0x55315B70);
        g.fill(cx, cy + gap, cx + 1, cy + radius, 0x55315B70);

        if (!autoAim.mountAvailable()) {
            String msg = "MOUNT OFFLINE";
            g.drawString(this.font, msg, cx - this.font.width(msg) / 2, cy - 4, TEXT_DARK, false);
            return;
        }

        if (this.easedAimFor == null || !this.easedAimFor.equals(selected.observerPos())) {
            this.easedAimFor = selected.observerPos();
            this.easedYawError = autoAim.yawErrorDegrees();
            this.easedPitchError = autoAim.pitchErrorDegrees();
        } else {
            this.easedYawError += (autoAim.yawErrorDegrees() - this.easedYawError) * 0.25f;
            this.easedPitchError += (autoAim.pitchErrorDegrees() - this.easedPitchError) * 0.25f;
        }

        if (autoAim.targetPresent()) {
            float maxDeg = 30.0f;
            float nx = clamp(this.easedYawError / maxDeg, -1.0f, 1.0f);
            float ny = clamp(this.easedPitchError / maxDeg, -1.0f, 1.0f);
            int px = cx + Math.round(nx * radius);
            int py = cy - Math.round(ny * radius);
            float err = (float) Math.sqrt(autoAim.yawErrorDegrees() * autoAim.yawErrorDegrees()
                    + autoAim.pitchErrorDegrees() * autoAim.pitchErrorDegrees());
            boolean aligned = Math.abs(autoAim.yawErrorDegrees()) < 2.5f && Math.abs(autoAim.pitchErrorDegrees()) < 2.5f;
            int pColor = aligned ? GREEN : autoAim.targetLocked() ? AMBER : CYAN;

            // Sight line from boresight to the target marker.
            this.drawLine(g, cx, cy, px, py, (pColor & 0x00FFFFFF) | 0x44000000);

            // Lock brackets that ease inward as the firing solution converges, snapping tight when aligned.
            float lockT = 1.0f - clamp(err / 14.0f, 0.0f, 1.0f);
            int bracket = Math.round(14 - lockT * 8);
            this.drawAimBrackets(g, px, py, bracket, pColor);
            g.fill(px - 1, py - 1, px + 2, py + 2, pColor);

            // Lead vector when prediction is engaged: a violet offset arrow with a label.
            if (autoAim.predictionActive()) {
                int lx = px + 16;
                this.drawLine(g, px + bracket + 2, py, lx, py, VIOLET);
                g.fill(lx - 3, py - 3, lx, py + 4, VIOLET);
                g.drawString(this.font, "LEAD", lx + 2, py - 3, VIOLET, false);
            }

            // Alignment: a breathing green confirmation ring + boresight flare.
            if (aligned) {
                int pulse = 2 + (int) ((Math.sin(time * 0.4f) * 0.5 + 0.5) * 4);
                this.ring(g, cx, cy, radius / 3 + pulse, radius / 3 + pulse, GREEN);
                g.fill(cx - 2, cy - 2, cx + 3, cy + 3, GREEN);
            }

            String state = aligned ? "FIRING SOLUTION" : autoAim.targetLocked() ? "LOCKED / TRACKING" : "TRACKING";
            int sc = aligned ? GREEN : pColor;
            g.drawString(this.font, state, cx - this.font.width(state) / 2, cy + radius - 10, sc, false);
        } else {
            // Searching: a radar sweep with a short fading trail.
            for (int t = 0; t < 5; t++) {
                double a = (time * 0.06f) - t * 0.16;
                int sx = cx + (int) Math.round(Math.cos(a) * radius);
                int sy = cy + (int) Math.round(Math.sin(a) * radius);
                int alpha = Math.max(0, 0x66 - t * 0x14);
                this.drawLine(g, cx, cy, sx, sy, (alpha << 24) | 0x0062E8A3);
            }
            String msg = "SEARCHING";
            g.drawString(this.font, msg, cx - this.font.width(msg) / 2, cy + radius / 2, TEXT_DARK, false);
        }
    }

    private void drawAimBrackets(GuiGraphics g, int px, int py, int half, int color) {
        int len = Math.max(3, half / 2);
        g.fill(px - half, py - half, px - half + len, py - half + 1, color);
        g.fill(px - half, py - half, px - half + 1, py - half + len, color);
        g.fill(px + half - len, py - half, px + half, py - half + 1, color);
        g.fill(px + half - 1, py - half, px + half, py - half + len, color);
        g.fill(px - half, py + half - 1, px - half + len, py + half, color);
        g.fill(px - half, py + half - len, px - half + 1, py + half, color);
        g.fill(px + half - len, py + half - 1, px + half, py + half, color);
        g.fill(px + half - 1, py + half - len, px + half, py + half, color);
    }

    private void drawInlineTargetPicker(GuiGraphics g, int mouseX, int mouseY, int x, int y, int width, int height, BattleScreenDisplayBE.AutoAimSnapshot autoAim) {
        g.fill(x, y, x + width, y + height, PANEL_DEEP);
        border(g, x, y, width, height, STROKE_DIM);
        g.fill(x, y, x + width, y + 18, PANEL);
        List<BattleScreenDisplayBE.AutoAimTargetOption> options = autoAim.targetOptions();
        g.drawString(this.font, "TARGETS", x + 8, y + 5, TEXT_DIM, false);
        g.drawString(this.font, Integer.toString(options.size()), x + width - 8 - this.font.width(Integer.toString(options.size())), y + 5, options.isEmpty() ? AMBER : GREEN, false);

        int rowH = 30;
        int listTop = y + 22;
        int visible = Math.max(1, (height - 24) / rowH);
        this.weaponPickerMaxScroll = Math.max(0, options.size() - visible);
        this.weaponPickerScroll = Math.max(0, Math.min(this.weaponPickerMaxScroll, this.weaponPickerScroll));
        if (options.isEmpty()) {
            g.drawString(this.font, "No lockable targets in range", x + 8, listTop + 8, TEXT_DARK, false);
            return;
        }
        int end = Math.min(options.size(), this.weaponPickerScroll + visible);
        for (int i = this.weaponPickerScroll; i < end; i++) {
            BattleScreenDisplayBE.AutoAimTargetOption option = options.get(i);
            int cy = listTop + (i - this.weaponPickerScroll) * rowH;
            boolean hover = this.inside(mouseX, mouseY, x + 4, cy, width - 8, rowH - 2);
            int fill = option.locked() ? 0x2629D982 : hover ? 0x1F315B70 : PANEL;
            int stroke = option.locked() ? GREEN : hover ? CYAN : STROKE_DIM;
            g.fill(x + 4, cy, x + width - 4, cy + rowH - 2, fill);
            border(g, x + 4, cy, width - 8, rowH - 2, stroke);
            int typeColor = this.targetTypeColor(option.type());
            g.fill(x + 8, cy + 5, x + 14, cy + 11, typeColor);
            g.drawString(this.font, this.trimToWidth(option.label().isEmpty() ? option.type() : option.label(), width - 90), x + 20, cy + 4, TEXT, false);
            String dist = String.format(java.util.Locale.ROOT, "%.0fm", option.distance());
            g.drawString(this.font, dist, x + width - 10 - this.font.width(dist), cy + 4, CYAN, false);
            String sub = option.speed() > 0.05 ? String.format(java.util.Locale.ROOT, "%.1f m/s", option.speed()) : option.type();
            g.drawString(this.font, this.trimToWidth(sub, width - 70), x + 20, cy + 16, TEXT_DIM, false);
            if (option.locked()) {
                g.drawString(this.font, "LOCKED", x + width - 10 - this.font.width("LOCKED"), cy + 16, GREEN, false);
            }
            UUID id = option.targetId();
            this.weaponHitTargets.add(new WeaponHitTarget(x + 4, cy, width - 8, rowH - 2, () -> this.sendWeaponAction(BattleScreenWeaponActionPacket.Action.SELECT_AUTO_AIM_TARGET, id)));
        }
        if (this.weaponPickerMaxScroll > 0) {
            int trackX = x + width - 5;
            int trackH = visible * rowH;
            g.fill(trackX, listTop, trackX + 2, listTop + trackH, STROKE_DIM);
            int thumbH = Math.max(16, trackH * visible / options.size());
            int thumbY = listTop + (trackH - thumbH) * this.weaponPickerScroll / this.weaponPickerMaxScroll;
            g.fill(trackX - 1, thumbY, trackX + 3, thumbY + thumbH, CYAN);
        }
    }

    private int drawWorkbenchResponseSlider(GuiGraphics g, int mouseX, int mouseY, float time, int x, int y, int width, NearbyObserver weaponObserver) {
        int height = 22;
        boolean active = weaponObserver.weapon().missingBlocks() == 0;
        float minResponse = this.weaponResponseMin(weaponObserver.weapon());
        float maxResponse = this.weaponResponseMax(weaponObserver.weapon());
        float response = this.currentWeaponResponseMultiplier(weaponObserver);
        int labelW = 96;
        int valueW = 44;
        int trackX = x + labelW;
        int trackW = width - labelW - valueW;
        int trackY = y + height / 2;
        g.fill(x, y, x + width, y + height, PANEL_DEEP);
        boolean hover = this.inside(mouseX, mouseY, trackX - 6, y, trackW + 12, height);
        int frame = !active ? STROKE_DIM : this.weaponResponseSliderDragging ? GREEN : hover ? CYAN : STROKE;
        border(g, x, y, width, height, frame);
        g.drawString(this.font, this.isAutocannon(weaponObserver.weapon()) ? "Track resp." : "Heavy track", x + 8, y + 7, TEXT_DIM, false);
        float normalized = clamp((response - minResponse) / Math.max(0.001f, maxResponse - minResponse), 0.0f, 1.0f);
        g.fill(trackX, trackY - 2, trackX + trackW, trackY + 2, 0xFF071018);
        int fillW = Math.round(trackW * normalized);
        if (fillW > 0) {
            g.fill(trackX, trackY - 2, trackX + fillW, trackY + 2, lerpColor(CYAN, VIOLET, normalized));
        }
        int knobX = trackX + fillW;
        int knobColor = !active ? TEXT_DARK : this.weaponResponseSliderDragging ? GREEN : hover ? CYAN : TEXT;
        g.fill(knobX - 3, trackY - 6, knobX + 4, trackY + 7, PANEL);
        border(g, knobX - 3, trackY - 6, 7, 13, knobColor);
        String value = this.formatWeaponResponse(weaponObserver.weapon(), response);
        g.drawString(this.font, value, x + width - this.font.width(value) - 8, y + 7, active ? CYAN : TEXT_DARK, false);
        this.weaponResponseSliderTarget = new WeaponResponseSliderTarget(trackX, y, trackW, height, weaponObserver.observerPos(), active, minResponse, maxResponse);
        return y + height;
    }

    private void drawFireButton(GuiGraphics g, int mouseX, int mouseY, int x, int y, int width, int height, String label, boolean enabled) {
        boolean hover = enabled && this.inside(mouseX, mouseY, x, y, width, height);
        int fill = !enabled ? 0x22301015 : hover ? 0x55FF5D69 : 0x33FF5D69;
        int stroke = enabled ? RED : STROKE_DIM;
        int textColor = enabled ? (hover ? 0xFFFFFFFF : RED) : TEXT_DARK;
        g.fill(x, y, x + width, y + height, fill);
        border(g, x, y, width, height, stroke);
        if (enabled) {
            this.drawScaledString(g, "FIRE", x + (width - this.font.width("FIRE") * 2) / 2, y + (height - 14) / 2, textColor, 2);
            this.weaponHitTargets.add(new WeaponHitTarget(x, y, width, height, () -> this.sendWeaponAction(BattleScreenWeaponActionPacket.Action.FIRE)));
        } else {
            g.drawString(this.font, label, x + (width - this.font.width(label)) / 2, y + (height - 8) / 2, textColor, false);
        }
    }

    private void drawReadoutLine(GuiGraphics g, int x, int y, int width, String label, String value, int color) {
        g.drawString(this.font, label, x, y, TEXT_DIM, false);
        g.drawString(this.font, value, x + width - this.font.width(value), y, color, false);
    }

    // shared workbench widgets
    private void drawWorkbenchButton(GuiGraphics g, int mouseX, int mouseY, int x, int y, int width, int height, String label, boolean active, boolean enabled, Runnable action) {
        boolean hover = enabled && this.inside(mouseX, mouseY, x, y, width, height);
        int fill = !enabled ? PANEL_DEEP : active ? 0x2629D982 : hover ? 0x22315B70 : PANEL;
        int stroke = !enabled ? STROKE_DIM : active ? GREEN : hover ? CYAN : STROKE;
        int textColor = !enabled ? TEXT_DARK : active ? GREEN : TEXT;
        g.fill(x, y, x + width, y + height, fill);
        border(g, x, y, width, height, stroke);
        String shown = this.trimToWidth(label, width - 8);
        g.drawString(this.font, shown, x + (width - this.font.width(shown)) / 2, y + (height - 8) / 2, textColor, false);
        if (enabled) {
            this.weaponHitTargets.add(new WeaponHitTarget(x, y, width, height, action));
        }
    }

    private void drawWorkbenchTab(GuiGraphics g, int mouseX, int mouseY, int x, int y, int width, int height, String label, boolean active, boolean enabled, Runnable action) {
        boolean hover = enabled && this.inside(mouseX, mouseY, x, y, width, height);
        int fill = active ? SHELL : hover ? 0x1F315B70 : PANEL_DEEP;
        int stroke = active ? CYAN : enabled ? STROKE_DIM : 0xFF1A2530;
        int textColor = !enabled ? TEXT_DARK : active ? CYAN : hover ? TEXT : TEXT_DIM;
        g.fill(x, y, x + width, y + height, fill);
        border(g, x, y, width, height, stroke);
        if (active) {
            g.fill(x + 1, y + height - 2, x + width - 1, y + height, CYAN);
        }
        g.drawString(this.font, label, x + (width - this.font.width(label)) / 2, y + (height - 8) / 2, textColor, false);
        if (enabled) {
            this.weaponHitTargets.add(new WeaponHitTarget(x, y, width, height, action));
        }
    }

    private void drawWorkbenchChip(GuiGraphics g, int x, int y, int width, String label, String value, int color) {
        g.fill(x, y, x + width, y + 18, PANEL);
        border(g, x, y, width, 18, STROKE_DIM);
        g.drawString(this.font, label, x + 6, y + 5, TEXT_DARK, false);
        g.drawString(this.font, value, x + width - 6 - this.font.width(value), y + 5, color, false);
    }

    private void drawWorkbenchBar(GuiGraphics g, int x, int y, int width, int height, float fraction, int color) {
        g.fill(x, y, x + width, y + height, 0xFF071018);
        border(g, x, y, width, height, STROKE_DIM);
        int fillW = Math.round((width - 2) * clamp(fraction, 0.0f, 1.0f));
        if (fillW > 0) {
            g.fill(x + 1, y + 1, x + 1 + fillW, y + height - 1, color);
        }
    }

    // Eased display value (smoothstep-ish lerp toward target), keyed so each bar tracks its own value.
    private float eased(String key, float target) {
        Float cur = this.easedBars.get(key);
        float c = cur == null ? target : cur + (target - cur) * 0.22f;
        if (Math.abs(target - c) < 0.0015f) {
            c = target;
        }
        this.easedBars.put(key, c);
        return c;
    }

    // [1]
    private float fireFlash(long key, int fireCount) {
        long now = System.currentTimeMillis();
        Integer prev = this.lastFireCount.get(key);
        if (prev != null && fireCount < prev) {
            this.fireFlashAt.put(key, now);
        }
        this.lastFireCount.put(key, fireCount);
        Long at = this.fireFlashAt.get(key);
        if (at == null) {
            return 0.0f;
        }
        float t = (now - at) / 350.0f;
        return t >= 1.0f ? 0.0f : 1.0f - t;
    }

    // Animated "fire-control console" bar: eased fill + bright leading edge + quarter ticks.
    private void drawConsoleBar(GuiGraphics g, String key, int x, int y, int width, int height, float target, int color) {
        float frac = clamp(this.eased(key, clamp(target, 0.0f, 1.0f)), 0.0f, 1.0f);
        g.fill(x, y, x + width, y + height, 0xFF071018);
        border(g, x, y, width, height, STROKE_DIM);
        int innerW = width - 2;
        int fillW = Math.round(innerW * frac);
        if (fillW > 0) {
            g.fill(x + 1, y + 1, x + 1 + fillW, y + height - 1, color);
            int edge = Math.min(x + 1 + fillW, x + width - 1);
            g.fill(edge - 1, y + 1, edge, y + height - 1, 0x66FFFFFF);
        }
        for (int t = 1; t < 4; t++) {
            int tx = x + 1 + innerW * t / 4;
            g.fill(tx, y + 1, tx + 1, y + height - 1, 0x22000000);
        }
    }

    // Subtle animated backdrop for the weapons console: faint vertical grid + a slow scanline sweep.
    private void drawConsoleBackdrop(GuiGraphics g, int x, int y, int width, int height, float time) {
        for (int gx = x + 48; gx < x + width; gx += 64) {
            g.fill(gx, y, gx + 1, y + height, 0x0A4AD9FF);
        }
        int sweep = Math.max(1, height);
        int sy = y + (int) ((time * 0.8f) % sweep);
        g.fill(x, sy, x + width, sy + 1, 0x124AD9FF);
    }

    private void drawWorkbenchEmpty(GuiGraphics g, int x, int y, int width, int height, String message) {
        g.fill(x, y, x + width, y + height, PANEL_DEEP);
        border(g, x, y, width, height, STROKE_DIM);
        g.drawString(this.font, message, x + (width - this.font.width(message)) / 2, y + height / 2 - 4, TEXT_DARK, false);
    }

    private void drawWeaponKindIcon(GuiGraphics g, int x, int y, BattleScreenDisplayBE.WeaponSnapshot weapon) {
        int w = 20;
        int h = 16;
        int color = switch (weapon.kind()) {
            case "AUTOCANNON" -> CYAN;
            case "DROP_MORTAR" -> AMBER;
            case "BIG_CANNON" -> GREEN;
            default -> TEXT_DIM;
        };
        g.fill(x, y, x + w, y + h, PANEL);
        border(g, x, y, w, h, color);
        if ("DROP_MORTAR".equals(weapon.kind())) {
            g.fill(x + 4, y + h - 5, x + 9, y + h - 3, color);
            this.drawLine(g, x + 5, y + h - 4, x + 15, y + 4, color);
        } else if ("AUTOCANNON".equals(weapon.kind())) {
            g.fill(x + 3, y + 6, x + 6, y + 10, color);
            g.fill(x + 6, y + 7, x + w - 3, y + 9, color);
            g.fill(x + 5, y + 3, x + 9, y + 6, color);
        } else {
            g.fill(x + 3, y + 5, x + 7, y + 11, color);
            g.fill(x + 7, y + 6, x + w - 3, y + 9, color);
        }
    }

    // ===================================================================================
    // Integrity workbench (redesigned DAMAGE_MAP presentation)
    // ===================================================================================

    private void setIntegrityTab(IntegrityTab tab) {
        if (this.integrityTab != tab) {
            this.integrityTab = tab;
            this.integrityTabSwitchMillis = System.currentTimeMillis();
        }
    }

    private void renderIntegrityWorkbench(GuiGraphics g, int mouseX, int mouseY, float time, List<NearbyObserver> observers, NearbyObserver focusObserver) {
        this.weaponHitTargets.clear();
        int inset = 20;
        int left = this.panelLeft + inset;
        int right = this.panelLeft + this.panelWidth - inset;

        double health = this.overallHealth(observers);
        int missing = this.totalMissing(observers);
        int added = this.totalNew(observers);
        int healthColor = this.healthColor(health, missing);
        boolean weaponsAvailable = this.hasWeaponsObserver(observers);

        // header
        int headerY = this.panelTop + 14;
        this.drawScaledString(g, "BATTLE BRIDGE", left, headerY, TEXT, 2);
        g.drawString(this.font, "HULL INTEGRITY MONITOR", left, headerY + 18, CYAN, false);

        int backW = 124;
        int backX = right - backW;
        this.drawWorkbenchButton(g, mouseX, mouseY, backX, headerY - 2, backW, 22, "Weapons", false, weaponsAvailable, () -> this.selectScreenMode(ScreenMode.WEAPONS));
        if (this.hasLightingObserver(observers)) {
            this.drawWorkbenchButton(g, mouseX, mouseY, backX - backW - 8, headerY - 2, backW, 22, "Lighting", false, true, () -> this.selectScreenMode(ScreenMode.LIGHTING));
        }
        int chipW = 96;
        int chipGap = 6;
        int chipY = headerY + 24;
        int chipX = right - (chipW * 3 + chipGap * 2);
        this.drawWorkbenchChip(g, chipX, chipY, chipW, "INTEGRITY", Math.round(health) + "%", healthColor);
        this.drawWorkbenchChip(g, chipX + chipW + chipGap, chipY, chipW, "BREACHES", Integer.toString(missing), missing > 0 ? RED : GREEN);
        this.drawWorkbenchChip(g, chipX + (chipW + chipGap) * 2, chipY, chipW, "REGIONS", Integer.toString(observers.size()), observers.isEmpty() ? AMBER : CYAN);

        // tab bar
        int tabY = this.panelTop + 52;
        int tabH = 26;
        int tabW = 152;
        int tabGap = 8;
        NearbyObserver selected = focusObserver;
        boolean detailEnabled = selected != null;
        IntegrityTab[] tabs = IntegrityTab.values();
        for (int i = 0; i < tabs.length; i++) {
            IntegrityTab tab = tabs[i];
            boolean enabled = tab != IntegrityTab.DETAIL || detailEnabled;
            int tx = left + i * (tabW + tabGap);
            this.drawWorkbenchTab(g, mouseX, mouseY, tx, tabY, tabW, tabH, tab.label, this.integrityTab == tab, enabled, () -> this.setIntegrityTab(tab));
        }

        // body
        int bodyTop = this.panelTop + 88;
        int bodyBottom = this.panelTop + this.panelHeight - 42;
        int bodyH = bodyBottom - bodyTop;
        int railW = 234;
        this.renderIntegrityRail(g, mouseX, mouseY, time, left, bodyTop, railW, bodyH, observers, selected);

        int mainX = left + railW + 14;
        int mainW = right - mainX;
        float intro = clamp((System.currentTimeMillis() - this.integrityTabSwitchMillis) / 150.0f, 0.0f, 1.0f);
        int slide = Math.round((1.0f - intro) * 12.0f);
        g.pose().pushPose();
        g.pose().translate(slide, 0, 0);
        IntegrityTab activeTab = (this.integrityTab == IntegrityTab.DETAIL && !detailEnabled) ? IntegrityTab.OVERVIEW : this.integrityTab;
        switch (activeTab) {
            case OVERVIEW -> this.renderIntegrityOverviewTab(g, mouseX - slide, mouseY, time, mainX, bodyTop, mainW, bodyH, observers, selected);
            case SHIP_MAP -> this.renderIntegrityShipMapTab(g, mouseX - slide, mouseY, time, mainX, bodyTop, mainW, bodyH, observers, selected);
            case DETAIL -> this.renderIntegrityDetailTab(g, mouseX - slide, mouseY, time, mainX, bodyTop, mainW, bodyH, observers, selected);
        }
        g.pose().popPose();

        String hint = switch (activeTab) {
            case OVERVIEW -> "Click a region card to inspect it. Use the roster to switch focus at any time.";
            case SHIP_MAP -> "Live hull map shaded by integrity. Critical breaches pulse red.";
            case DETAIL -> "Full breakdown and integrity history for the focused region.";
        };
        g.drawString(this.font, hint, left, bodyBottom + 13, TEXT_DARK, false);
    }

    private boolean hasLightingObserver(List<NearbyObserver> observers) {
        for (NearbyObserver observer : observers) {
            if (!observer.lights().lights().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void renderLightingWorkbench(GuiGraphics g, int mouseX, int mouseY, float time, List<NearbyObserver> observers, NearbyObserver focusObserver) {
        this.weaponHitTargets.clear();
        this.lightStepperTargets.clear();
        int inset = 20;
        int left = this.panelLeft + inset;
        int right = this.panelLeft + this.panelWidth - inset;

        int headerY = this.panelTop + 14;
        this.drawScaledString(g, "BATTLE BRIDGE", left, headerY, TEXT, 2);
        g.drawString(this.font, "STAGE LIGHTING CONTROL", left, headerY + 18, AMBER, false);

        int backW = 124;
        int backX = right - backW;
        this.drawWorkbenchButton(g, mouseX, mouseY, backX, headerY - 2, backW, 22, "Integrity Map", false, true, () -> this.selectScreenMode(ScreenMode.DAMAGE_MAP));
        if (this.hasWeaponsObserver(observers)) {
            this.drawWorkbenchButton(g, mouseX, mouseY, backX - backW - 8, headerY - 2, backW, 22, "Weapons", false, true, () -> this.selectScreenMode(ScreenMode.WEAPONS));
        }

        int total = 0;
        int on = 0;
        int tracking = 0;
        for (NearbyObserver observer : observers) {
            for (BattleLightSnapshot.Entry entry : observer.lights().lights()) {
                total++;
                if (entry.lit()) {
                    on++;
                }
                if (entry.tracking()) {
                    tracking++;
                }
            }
        }
        int chipW = 84;
        int chipGap = 6;
        int chipY = headerY + 24;
        int chipX = right - (chipW * 3 + chipGap * 2);
        this.drawWorkbenchChip(g, chipX, chipY, chipW, "FIXTURES", Integer.toString(total), total > 0 ? CYAN : TEXT_DIM);
        this.drawWorkbenchChip(g, chipX + chipW + chipGap, chipY, chipW, "ON", Integer.toString(on), on > 0 ? GREEN : TEXT_DIM);
        this.drawWorkbenchChip(g, chipX + (chipW + chipGap) * 2, chipY, chipW, "TRACKING", Integer.toString(tracking), tracking > 0 ? AMBER : TEXT_DIM);

        int bodyTop = this.panelTop + 88;
        int bodyBottom = this.panelTop + this.panelHeight - 42;

        BattleLightSnapshot.Entry focused = null;
        String focusedObserverName = "";
        for (NearbyObserver observer : observers) {
            for (BattleLightSnapshot.Entry entry : observer.lights().lights()) {
                if (this.focusedLightPos != null && entry.pos() == this.focusedLightPos) {
                    focused = entry;
                    this.focusedLightObserverPos = observer.observerPos();
                    focusedObserverName = observer.name();
                }
            }
        }
        if (focused == null) {
            outer:
            for (NearbyObserver observer : observers) {
                for (BattleLightSnapshot.Entry entry : observer.lights().lights()) {
                    focused = entry;
                    this.focusedLightPos = entry.pos();
                    this.focusedLightObserverPos = observer.observerPos();
                    focusedObserverName = observer.name();
                    break outer;
                }
            }
        }

        // Three columns: channel strips | live beam diagram | sectioned controls.
        int avail = right - left;
        int gap = 10;
        int listW = Math.max(196, (avail - gap * 2) * 30 / 100);
        int ctrlW = Math.max(212, (avail - gap * 2) * 32 / 100);
        int diagW = avail - gap * 2 - listW - ctrlW;
        int listLeft = left;
        int listRight = listLeft + listW;
        int diagLeft = listRight + gap;
        int diagRight = diagLeft + diagW;
        int ctrlLeft = diagRight + gap;
        int ctrlRight = right;

        g.drawString(this.font, "CHANNELS", listLeft + 2, bodyTop, TEXT_DIM, false);
        int masterW = 60;
        this.drawWorkbenchButton(g, mouseX, mouseY, listRight - masterW, bodyTop - 4, masterW, 18, "All Off", false, total > 0, () -> this.sendLightMaster(BattleScreenLightActionPacket.Action.ALL_OFF));
        this.drawWorkbenchButton(g, mouseX, mouseY, listRight - masterW * 2 - 6, bodyTop - 4, masterW, 18, "All On", false, total > 0, () -> this.sendLightMaster(BattleScreenLightActionPacket.Action.ALL_ON));

        // Scene slots above the control column: click recalls, shift-click saves the whole rig.
        g.drawString(this.font, "SCENES", ctrlLeft, bodyTop, TEXT_DIM, false);
        int sceneW = 30;
        int sceneX = ctrlRight - (sceneW + 4) * BattleScreenDisplayBE.LIGHT_SCENE_SLOTS + 4;
        for (int i = 0; i < BattleScreenDisplayBE.LIGHT_SCENE_SLOTS; i++) {
            final int slot = i;
            boolean saved = this.display.isLightSceneSaved(i);
            this.drawWorkbenchButton(g, mouseX, mouseY, sceneX + i * (sceneW + 4), bodyTop - 4, sceneW, 18,
                    Integer.toString(i + 1), saved, total > 0 || saved,
                    () -> this.sendLightScene(slot, hasShiftDown()));
        }

        int colTop = bodyTop + 20;

        g.fill(listLeft, colTop, listRight, bodyBottom, PANEL_DEEP);
        border(g, listLeft, colTop, listRight - listLeft, bodyBottom - colTop, STROKE_DIM);
        int rowLeft = listLeft + 6;
        int rowRight = listRight - 6;
        int rowW = rowRight - rowLeft;
        if (total == 0) {
            g.drawString(this.font, "No Stage Lights", rowLeft, colTop + 8, TEXT_DIM, false);
        }
        // Flatten headers + fixtures into one row-slot list so the rail can scroll across group boundaries.
        List<LightRailRow> railRows = new ArrayList<>();
        int channel = 1;
        for (NearbyObserver observer : observers) {
            List<BattleLightSnapshot.Entry> lights = observer.lights().lights();
            if (lights.isEmpty()) {
                continue;
            }
            railRows.add(new LightRailRow(observer.name(), null, null, 0));
            for (BattleLightSnapshot.Entry entry : lights) {
                railRows.add(new LightRailRow(null, entry, observer.observerPos(), channel));
                channel++;
            }
        }
        int listTop = colTop + 8;
        int availH = bodyBottom - 4 - listTop;
        int totalRowH = 0;
        for (LightRailRow row : railRows) {
            totalRowH += row.height();
        }
        if (totalRowH > availH) {
            availH -= 12;
        }
        int maxScroll = 0;
        int acc = 0;
        for (int i = railRows.size() - 1; i >= 0; i--) {
            acc += railRows.get(i).height();
            if (acc > availH) {
                maxScroll = i + 1;
                break;
            }
        }
        this.lightRailScroll = Math.max(0, Math.min(this.lightRailScroll, maxScroll));
        int y = listTop;
        int lastShown = this.lightRailScroll;
        for (int i = this.lightRailScroll; i < railRows.size(); i++) {
            LightRailRow row = railRows.get(i);
            if (y + row.height() > listTop + availH) {
                break;
            }
            if (row.header() != null) {
                g.drawString(this.font, this.trimToWidth(row.header(), rowW), rowLeft, y + 3, AMBER, false);
            } else {
                BattleLightSnapshot.Entry entry = row.entry();
                boolean isFocused = this.focusedLightPos != null && entry.pos() == this.focusedLightPos;
                this.drawLightChannelStrip(g, mouseX, mouseY, rowLeft, y, rowW, entry, row.channel(), isFocused, time);
                final long lp = entry.pos();
                final BlockPos op = row.observerPos();
                this.weaponHitTargets.add(new WeaponHitTarget(rowLeft, y, rowW, 30, () -> {
                    this.focusedLightPos = lp;
                    this.focusedLightObserverPos = op;
                }));
            }
            y += row.height();
            lastShown = i + 1;
        }
        if (maxScroll > 0) {
            String more = lastShown + "/" + railRows.size();
            g.drawString(this.font, more, rowLeft, bodyBottom - 13, TEXT_DARK, false);
            g.drawString(this.font, "scroll", rowRight - this.font.width("scroll"), bodyBottom - 13, TEXT_DARK, false);
        }

        if (focused != null) {
            this.drawStageLightBeamDiagram(g, diagLeft, colTop, diagRight - diagLeft, bodyBottom - colTop, focused, time);
        } else {
            g.fill(diagLeft, colTop, diagRight, bodyBottom, PANEL_DEEP);
            border(g, diagLeft, colTop, diagRight - diagLeft, bodyBottom - colTop, STROKE_DIM);
            this.drawWorkbenchEmpty(g, diagLeft + 8, colTop + 8, diagRight - diagLeft - 16, bodyBottom - colTop - 16, "NO FIXTURE SELECTED");
        }

        g.fill(ctrlLeft, colTop, ctrlRight, bodyBottom, PANEL_DEEP);
        border(g, ctrlLeft, colTop, ctrlRight - ctrlLeft, bodyBottom - colTop, STROKE_DIM);
        if (focused != null && this.focusedLightObserverPos != null) {
            this.renderLightControls(g, mouseX, mouseY, ctrlLeft + 10, colTop + 10, ctrlRight - ctrlLeft - 20, focused, this.focusedLightObserverPos, focusedObserverName);
        } else {
            g.drawString(this.font, "Select a fixture.", ctrlLeft + 10, colTop + 10, TEXT_DIM, false);
        }

        g.drawString(this.font, "Click a channel to control it. Scene chips: click recalls, shift-click saves the rig.", left, bodyBottom + 13, TEXT_DARK, false);

        if (this.lightTargetPickerOpen) {
            this.renderLightTargetPicker(g, mouseX, mouseY);
        }
    }

    private void renderLightTargetPicker(GuiGraphics g, int mouseX, int mouseY) {
        this.weaponHitTargets.clear();
        int w = 340;
        int h = 300;
        int x = this.panelLeft + (this.panelWidth - w) / 2;
        int y = this.panelTop + (this.panelHeight - h) / 2;
        g.fill(x - 4, y - 4, x + w + 4, y + h + 4, SHADOW);
        g.fill(x, y, x + w, y + h, SHELL);
        border(g, x, y, w, h, CYAN);
        g.drawString(this.font, "SELECT TARGET TO TRACK", x + 12, y + 10, TEXT, false);
        this.drawWorkbenchButton(g, mouseX, mouseY, x + w - 70, y + 6, 60, 18, "Close", false, true, () -> this.lightTargetPickerOpen = false);

        int range = this.focusedLightTrackingRange();
        java.util.List<LightTarget> candidates = this.nearbyLightTargets(range);
        int listTop = y + 32;
        int rowH = 20;
        int visibleRows = Math.max(3, (h - 40) / rowH);
        int maxScroll = Math.max(0, candidates.size() - visibleRows);
        this.lightTargetPickerScroll = Math.max(0, Math.min(maxScroll, this.lightTargetPickerScroll));
        if (candidates.isEmpty()) {
            g.drawString(this.font, "No targets within " + range + " blocks.", x + 12, listTop, TEXT_DIM, false);
        }
        BlockPos op = this.focusedLightObserverPos;
        long lp = this.focusedLightPos == null ? 0L : this.focusedLightPos;
        int end = Math.min(candidates.size(), this.lightTargetPickerScroll + visibleRows);
        for (int index = this.lightTargetPickerScroll; index < end; index++) {
            LightTarget target = candidates.get(index);
            int ry = listTop + (index - this.lightTargetPickerScroll) * rowH;
            g.fill(x + 8, ry, x + w - 8, ry + rowH - 3, 0xFF13202A);
            border(g, x + 8, ry, w - 16, rowH - 3, STROKE_DIM);
            g.drawString(this.font, this.trimToWidth(target.name() + "  (" + Math.round(target.distance()) + "m)", w - 28), x + 14, ry + 5, TEXT, false);
            final java.util.UUID id = target.id();
            this.weaponHitTargets.add(new WeaponHitTarget(x + 8, ry, w - 16, rowH - 3, () -> {
                if (op != null) {
                    this.sendLightTarget(op, lp, id);
                }
                this.lightTargetPickerOpen = false;
            }));
        }
        if (maxScroll > 0) {
            int trackX = x + w - 7;
            int trackH = visibleRows * rowH - 4;
            g.fill(trackX, listTop, trackX + 3, listTop + trackH, STROKE_DIM);
            int thumbH = Math.max(20, trackH * visibleRows / candidates.size());
            int thumbY = listTop + (trackH - thumbH) * this.lightTargetPickerScroll / maxScroll;
            g.fill(trackX - 1, thumbY, trackX + 4, thumbY + thumbH, CYAN);
        }
    }

    private int focusedLightTrackingRange() {
        if (this.focusedLightPos != null) {
            for (NearbyObserver observer : this.nearbyObservers()) {
                for (BattleLightSnapshot.Entry entry : observer.lights().lights()) {
                    if (entry.pos() == this.focusedLightPos) {
                        return Math.max(16, entry.trackingRange());
                    }
                }
            }
        }
        return 96;
    }

    /** A pickable light target: a living entity (players/mobs) or a Sable ship, by stable UUID. */
    private record LightTarget(java.util.UUID id, String name, double distance) {
    }

    private java.util.List<LightTarget> nearbyLightTargets(int rangeBlocks) {
        java.util.List<LightTarget> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return out;
        }
        final Vec3 eye = mc.player.position();
        // Living entities (players + mobs). A null exclusion is what lets players show up at all -
        // getEntities(player, ...) would hide every player including the viewer.
        AABB box = mc.player.getBoundingBox().inflate(rangeBlocks);
        for (Entity entity : mc.level.getEntities((Entity) null, box, candidate -> candidate instanceof LivingEntity)) {
            out.add(new LightTarget(entity.getUUID(), entity.getName().getString(), Math.sqrt(entity.distanceToSqr(mc.player))));
        }
        // Ships (Aeronautics / Sable sub-levels) within range.
        for (Object subLevel : com.createstats.flight.bridge.SableBridge.listSubLevels(mc.level)) {
            java.util.UUID id = com.createstats.flight.bridge.SableBridge.subLevelId(subLevel);
            org.joml.Vector3d center = com.createstats.flight.bridge.SableBridge.subLevelWorldCenter(subLevel);
            if (id == null || center == null) {
                continue;
            }
            double dist = eye.distanceTo(new Vec3(center.x, center.y, center.z));
            if (dist <= rangeBlocks) {
                out.add(new LightTarget(id, com.createstats.flight.bridge.SableBridge.subLevelName(subLevel) + " (ship)", dist));
            }
        }
        out.sort(Comparator.comparingDouble(LightTarget::distance));
        // Perf sanity cap only; the picker scrolls, so this is not a display limit.
        return out.size() > 40 ? out.subList(0, 40) : out;
    }

    private void sendLightTarget(BlockPos observerPos, long lightPos, java.util.UUID targetId) {
        PacketDistributor.sendToServer(new BattleScreenLightActionPacket(this.display.getBlockPos(), observerPos, lightPos, BattleScreenLightActionPacket.Action.SET_TARGET, targetId));
    }

    private void renderLightControls(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, BattleLightSnapshot.Entry e, BlockPos observerPos, String observerName) {
        long lp = e.pos();
        String fixtureLabel = "FIXTURE " + BlockPos.of(lp).toShortString();
        g.drawString(this.font, fixtureLabel, x, y, TEXT, false);
        String obsLabel = this.trimToWidth(observerName, w - this.font.width(fixtureLabel) - 8);
        g.drawString(this.font, obsLabel, x + w - this.font.width(obsLabel), y, TEXT_DIM, false);
        int ry = y + 13;

        ry = this.lightSection(g, x, ry, w, "POWER");
        this.drawWorkbenchButton(g, mouseX, mouseY, x, ry, w, 18, e.lit() ? "Turn Off" : "Turn On", e.lit(), true, () -> this.sendLightAction(observerPos, lp, BattleScreenLightActionPacket.Action.TOGGLE));
        ry += 22;

        ry = this.lightSection(g, x, ry, w, "BEAM SHAPE");
        ry = this.lightStepper(g, mouseX, mouseY, x, ry, w, "lr" + lp, "Range", e.range() + " blk", e.range() / (float) Math.max(1, e.maxRange()), CYAN, observerPos, lp, BattleScreenLightActionPacket.Action.RANGE_DOWN, BattleScreenLightActionPacket.Action.RANGE_UP);
        String rangeHint = e.range() <= e.peakRange()
                ? "Full brightness (peak at " + e.peakRange() + ")"
                : "Beyond " + e.peakRange() + ": wider but dimmer";
        g.drawString(this.font, this.trimToWidth(rangeHint, w), x, ry, TEXT_DARK, false);
        ry += 10;
        ry = this.lightStepper(g, mouseX, mouseY, x, ry, w, "lb" + lp, "Bright", e.brightness() + " /15", e.brightness() / 15.0f, AMBER, observerPos, lp, BattleScreenLightActionPacket.Action.BRIGHT_DOWN, BattleScreenLightActionPacket.Action.BRIGHT_UP);
        ry = this.lightStepper(g, mouseX, mouseY, x, ry, w, "lf" + lp, "Focus", e.focus() + (e.focus() <= 3 ? " tight" : e.focus() >= 8 ? " wide" : ""), e.focus() / 10.0f, VIOLET, observerPos, lp, BattleScreenLightActionPacket.Action.FOCUS_DOWN, BattleScreenLightActionPacket.Action.FOCUS_UP);
        ry = this.lightStepper(g, mouseX, mouseY, x, ry, w, "lw" + lp, "Width", e.beamWidth() + " blk", (e.beamWidth() - 2) / 14.0f, CYAN, observerPos, lp, BattleScreenLightActionPacket.Action.WIDTH_DOWN, BattleScreenLightActionPacket.Action.WIDTH_UP);
        ry = this.lightStepper(g, mouseX, mouseY, x, ry, w, "ls" + lp, "Spread", e.lightSpread() + " blk", (e.lightSpread() - 2) / 14.0f, GREEN, observerPos, lp, BattleScreenLightActionPacket.Action.SPREAD_DOWN, BattleScreenLightActionPacket.Action.SPREAD_UP);

        int half = (w - 6) / 2;
        this.drawWorkbenchButton(g, mouseX, mouseY, x, ry, half, 18, e.beamVisible() ? "Beam: On" : "Beam: Off", e.beamVisible(), true, () -> this.sendLightAction(observerPos, lp, BattleScreenLightActionPacket.Action.TOGGLE_BEAM));
        this.drawWorkbenchButton(g, mouseX, mouseY, x + half + 6, ry, half, 18, e.volumetric() ? "Fog: On" : "Fog: Off", e.volumetric(), true, () -> this.sendLightAction(observerPos, lp, BattleScreenLightActionPacket.Action.TOGGLE_FOG));
        ry += 22;

        // Effect program (< name >) plus tempo (- Spd n +) packed into one row; both nudge on scroll.
        int fxBtn = 16;
        boolean fxOn = e.effect() != StageLightBE.EFFECT_NONE;
        this.drawWorkbenchButton(g, mouseX, mouseY, x, ry, fxBtn, 18, "<", false, true, () -> this.sendLightAction(observerPos, lp, BattleScreenLightActionPacket.Action.EFFECT_PREV));
        int fxBoxX = x + fxBtn + 4;
        int fxBoxW = half - (fxBtn + 4) * 2 + 4;
        g.fill(fxBoxX, ry, fxBoxX + fxBoxW, ry + 18, fxOn ? 0x2629D982 : PANEL);
        border(g, fxBoxX, ry, fxBoxW, 18, fxOn ? GREEN : STROKE_DIM);
        String fxName = StageLightScreen.effectName(e.effect());
        g.drawString(this.font, fxName, fxBoxX + (fxBoxW - this.font.width(fxName)) / 2, ry + 5, fxOn ? GREEN : TEXT_DIM, false);
        this.drawWorkbenchButton(g, mouseX, mouseY, x + half - fxBtn, ry, fxBtn, 18, ">", false, true, () -> this.sendLightAction(observerPos, lp, BattleScreenLightActionPacket.Action.EFFECT_NEXT));
        this.lightStepperTargets.add(new LightStepperTarget(x, ry, half, 18, observerPos, lp,
                BattleScreenLightActionPacket.Action.EFFECT_PREV, BattleScreenLightActionPacket.Action.EFFECT_NEXT));
        int spX = x + half + 6;
        this.drawWorkbenchButton(g, mouseX, mouseY, spX, ry, fxBtn, 18, "-", false, true, () -> this.sendLightAction(observerPos, lp, BattleScreenLightActionPacket.Action.EFFECT_SPEED_DOWN));
        int spBoxX = spX + fxBtn + 4;
        int spBoxW = half - (fxBtn + 4) * 2 + 4;
        g.fill(spBoxX, ry, spBoxX + spBoxW, ry + 18, PANEL);
        border(g, spBoxX, ry, spBoxW, 18, STROKE_DIM);
        String fxSpeed = "Spd " + e.effectSpeed();
        g.drawString(this.font, fxSpeed, spBoxX + (spBoxW - this.font.width(fxSpeed)) / 2, ry + 5, TEXT, false);
        this.drawWorkbenchButton(g, mouseX, mouseY, x + w - fxBtn, ry, fxBtn, 18, "+", false, true, () -> this.sendLightAction(observerPos, lp, BattleScreenLightActionPacket.Action.EFFECT_SPEED_UP));
        this.lightStepperTargets.add(new LightStepperTarget(spX, ry, half, 18, observerPos, lp,
                BattleScreenLightActionPacket.Action.EFFECT_SPEED_DOWN, BattleScreenLightActionPacket.Action.EFFECT_SPEED_UP));
        ry += 22;

        ry = this.lightSection(g, x, ry, w, "COLOR");
        int btn = 22;
        this.drawWorkbenchButton(g, mouseX, mouseY, x, ry, btn, 18, "<", false, true, () -> this.sendLightAction(observerPos, lp, BattleScreenLightActionPacket.Action.COLOR_PREV));
        int swX = x + btn + 6;
        int swW = w - (btn + 6) * 2;
        g.fill(swX, ry, swX + swW, ry + 18, 0xFF000000 | e.tint());
        border(g, swX, ry, swW, 18, STROKE_DIM);
        String dye = this.lightColorName(e.tint());
        g.drawString(this.font, dye, swX + (swW - this.font.width(dye)) / 2, ry + 5, this.contrastText(e.tint()), false);
        this.drawWorkbenchButton(g, mouseX, mouseY, x + w - btn, ry, btn, 18, ">", false, true, () -> this.sendLightAction(observerPos, lp, BattleScreenLightActionPacket.Action.COLOR_NEXT));
        ry += 22;

        ry = this.lightSection(g, x, ry, w, "TARGETING");
        this.drawWorkbenchButton(g, mouseX, mouseY, x, ry, half, 18, "Track Target", e.tracking(), true, () -> {
            this.lightTargetPickerOpen = true;
            this.lightTargetPickerScroll = 0;
        });
        this.drawWorkbenchButton(g, mouseX, mouseY, x + half + 6, ry, half, 18, "Clear Track", false, e.tracking(), () -> this.sendLightAction(observerPos, lp, BattleScreenLightActionPacket.Action.CLEAR_TARGET));
        ry += 20;
        String trk = e.tracking()
                ? "Tracking: " + (e.trackingName().isEmpty() ? "target" : e.trackingName()) + " (" + e.trackingRange() + " blk)"
                : "Status: idle";
        g.drawString(this.font, this.trimToWidth(trk, w), x, ry, e.tracking() ? AMBER : TEXT_DARK, false);
    }

    private int lightSection(GuiGraphics g, int x, int y, int w, String label) {
        g.drawString(this.font, label, x, y, TEXT_DIM, false);
        int lw = this.font.width(label);
        if (lw + 6 < w) {
            g.fill(x + lw + 6, y + 4, x + w, y + 5, STROKE_DIM);
        }
        return y + 12;
    }

    private int lightStepper(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, String key, String label, String value,
                             float frac, int color, BlockPos observerPos, long lp,
                             BattleScreenLightActionPacket.Action down, BattleScreenLightActionPacket.Action up) {
        this.lightStepperTargets.add(new LightStepperTarget(x, y, w, 16, observerPos, lp, down, up));
        g.drawString(this.font, label, x, y + 5, TEXT_DIM, false);
        int btn = 18;
        int minusX = x + 44;
        this.drawWorkbenchButton(g, mouseX, mouseY, minusX, y, btn, 16, "-", false, true, () -> this.sendLightAction(observerPos, lp, down));
        int barX = minusX + btn + 4;
        int barW = (x + w) - barX - (btn + 4);
        this.drawConsoleBar(g, key, barX, y, barW, 16, frac, color);
        g.drawString(this.font, value, barX + (barW - this.font.width(value)) / 2, y + 4, TEXT, false);
        this.drawWorkbenchButton(g, mouseX, mouseY, barX + barW + 4, y, btn, 16, "+", false, true, () -> this.sendLightAction(observerPos, lp, up));
        return y + 20;
    }

    private String lightColorName(int tint) {
        if ((tint & 0xFFFFFF) == 0xFFFFFF) {
            return "No Filter";
        }
        net.minecraft.world.item.DyeColor dye = com.createstats.util.StageLightColors.fromRgb(tint);
        if (dye == null) {
            return String.format("#%06X", tint & 0xFFFFFF);
        }
        String name = dye.getName().replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private int contrastText(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int gg = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (r * 299 + gg * 587 + b * 114) / 1000 > 140 ? 0xFF101820 : TEXT;
    }

    private static int argb(int alpha, int rgb) {
        int a = Math.max(0, Math.min(255, alpha));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private void drawLightChannelStrip(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, BattleLightSnapshot.Entry e, int channel, boolean focused, float time) {
        int h = 30;
        boolean hover = this.inside(mouseX, mouseY, x, y, w, h);
        int fill = focused ? 0x2629D982 : hover ? 0x1F315B70 : (e.lit() ? 0xFF13202A : 0xFF0E1820);
        g.fill(x, y, x + w, y + h, fill);
        border(g, x, y, w, h, focused ? CYAN : hover ? STROKE : STROKE_DIM);
        g.fill(x, y, x + 3, y + h, 0xFF000000 | e.tint());
        int ledX = x + 11;
        int ledY = y + 8;
        if (e.lit()) {
            float pulse = 0.55f + 0.45f * (float) Math.sin(time * 0.22f + channel * 0.7f);
            g.fill(ledX - 2, ledY - 2, ledX + 6, ledY + 6, lerpColor(0xFF12281C, GREEN, pulse));
        }
        g.fill(ledX, ledY, ledX + 4, ledY + 4, e.lit() ? GREEN : TEXT_DARK);
        String ch = "CH" + channel;
        g.drawString(this.font, ch, x + 22, y + 4, focused ? CYAN : TEXT, false);
        int badgeX = x + 24 + this.font.width(ch);
        if (e.tracking()) {
            g.drawString(this.font, "TRK", badgeX, y + 4, AMBER, false);
            badgeX += this.font.width("TRK") + 3;
        }
        if (e.effect() != StageLightBE.EFFECT_NONE) {
            g.drawString(this.font, "FX", badgeX, y + 4, VIOLET, false);
        }
        g.drawString(this.font, this.trimToWidth(BlockPos.of(e.pos()).toShortString(), 72), x + 22, y + 16, TEXT_DARK, false);
        int barW = 44;
        int barX = x + w - barW - 18;
        this.drawWorkbenchBar(g, barX, y + 7, barW, 4, e.range() / (float) Math.max(1, e.maxRange()), e.lit() ? CYAN : STROKE);
        this.drawWorkbenchBar(g, barX, y + 16, barW, 4, e.brightness() / 15.0f, e.lit() ? AMBER : STROKE);
        g.drawString(this.font, "F" + e.focus(), x + w - 16, y + 11, TEXT_DIM, false);
    }

    private void drawStageLightBeamDiagram(GuiGraphics g, int x, int y, int w, int h, BattleLightSnapshot.Entry e, float time) {
        g.fill(x, y, x + w, y + h, PANEL_DEEP);
        this.drawConsoleBackdrop(g, x, y, w, h, time);
        border(g, x, y, w, h, STROKE_DIM);
        g.fill(x, y, x + w, y + 16, PANEL);
        g.drawString(this.font, "BEAM PROJECTION", x + 8, y + 5, TEXT_DIM, false);
        String st = e.lit() ? "LIVE" : "OFF";
        g.drawString(this.font, st, x + w - 8 - this.font.width(st), y + 5, e.lit() ? GREEN : TEXT_DARK, false);

        // Diagram previews the live effect (strobe/pulse/rainbow) with the same math the world uses.
        long previewTime = System.currentTimeMillis() / 50L;
        int rgb = StageLightBE.effectTint(e.effect(), e.effectSpeed(), e.tint() & 0xFFFFFF, previewTime, 0.0f);
        int beamCol = rgb == 0xFFFFFF ? 0xFFE8B0 : rgb;
        float lf = e.lit() ? 1.0f : 0.28f;
        float fx = e.lit() ? Math.max(0.08f, StageLightBE.effectBrightnessFactor(e.effect(), e.effectSpeed(), previewTime, 0.0f)) : 1.0f;
        float bright = clamp(e.brightness() / 15.0f, 0.12f, 1.0f) * lf * fx;

        int ox = x + w / 2;
        int oy = y + 44;
        int areaLeft = x + 10;
        int areaRight = x + w - 10;
        int areaBottom = y + h - 26;

        // yoke + arms
        g.fill(ox - 22, y + 24, ox + 22, y + 29, 0xFF16232E);
        border(g, ox - 22, y + 24, 44, 5, STROKE);
        g.fill(ox - 13, y + 28, ox - 10, oy, STROKE);
        g.fill(ox + 10, y + 28, ox + 13, oy, STROKE);

        int tiltDeg = Math.max(-58, Math.min(58, e.tilt()));
        double slant = Math.tan(Math.toRadians(tiltDeg * 0.7));
        double halfDeg = 4.0 + e.focus() * 2.4;
        double tan = Math.tan(Math.toRadians(halfDeg));
        double maxHalf = w * 0.26;
        double capScale = maxHalf / 16.0;
        double coneCap = e.beamWidth() * capScale;
        double poolCap = e.lightSpread() * capScale;

        int len = Math.max(18, (int) ((areaBottom - oy) * clamp(e.range() / (float) Math.max(1, e.maxRange()), 0.3f, 1.0f)));
        int ex = ox + (int) (len * slant);
        int ey = oy + len;

        int steps = 26;
        int halo = argb((int) (70 * bright), beamCol);
        int core = argb((int) (150 * bright), beamCol);
        for (int i = 1; i <= steps; i++) {
            double a = (double) i / steps;
            int cy = oy + (int) (len * a);
            int cx = ox + (int) (len * a * slant);
            double hw = Math.min(len * a * tan, coneCap);
            if (hw < 0.6) {
                continue;
            }
            int l = Math.max(areaLeft, cx - (int) hw);
            int r = Math.min(areaRight, cx + (int) hw);
            if (r > l) {
                g.fill(l, cy, r, cy + 1, halo);
            }
            int hwc = (int) (hw * 0.5);
            int lc = Math.max(areaLeft, cx - hwc);
            int rc = Math.min(areaRight, cx + hwc);
            if (rc > lc) {
                g.fill(lc, cy, rc, cy + 1, core);
            }
        }
        this.drawLine(g, ox, oy, Math.max(areaLeft, Math.min(areaRight, ex)), ey, argb((int) (210 * bright), beamCol));

        // lens
        g.fill(ox - 7, oy - 6, ox + 7, oy + 6, 0xFF222F3A);
        border(g, ox - 7, oy - 6, 14, 12, e.lit() ? (0xFF000000 | beamCol) : STROKE);
        g.fill(ox - 3, oy - 2, ox + 3, oy + 4, e.lit() ? (0xFF000000 | beamCol) : 0xFF2A3A46);

        // lit ground pool
        double poolHalf = Math.min(len * tan, poolCap);
        int rp = Math.max(3, (int) poolHalf);
        int ry2 = Math.max(2, rp * 2 / 5);
        int pcx = Math.max(areaLeft + rp, Math.min(areaRight - rp, ex));
        int pcy = Math.min(ey, areaBottom - ry2);
        this.ring(g, pcx, pcy, rp, ry2, argb((int) (120 * bright), beamCol));
        this.ring(g, pcx, pcy, Math.max(2, rp * 3 / 5), Math.max(1, ry2 * 3 / 5), argb((int) (80 * bright), beamCol));

        // floating dust when fog is on
        if (e.volumetric() && e.lit()) {
            for (int k = 0; k < 6; k++) {
                double a = (time * 0.012 + k / 6.0) % 1.0;
                int cy = oy + (int) (len * a);
                int cx = ox + (int) (len * a * slant);
                double hwc = Math.min(len * a * tan, coneCap);
                int jx = (int) (Math.sin(time * 0.1 + k * 1.7) * hwc * 0.6);
                int mx = Math.max(areaLeft, Math.min(areaRight, cx + jx));
                g.fill(mx, cy, mx + 1, cy + 1, argb((int) (160 * bright * (1.0 - a)), 0xFFFFFF));
            }
        }

        // tracking pip
        if (e.tracking()) {
            float p = 0.5f + 0.5f * (float) Math.sin(time * 0.3f);
            this.ring(g, pcx, pcy, rp + 4, Math.max(2, (rp + 4) * 2 / 5), lerpColor(0xFF5A1A20, RED, p));
            this.drawLine(g, pcx - 5, pcy, pcx + 5, pcy, RED);
            this.drawLine(g, pcx, pcy - 3, pcx, pcy + 3, RED);
        }

        // pan compass (top-right)
        int cxp = x + w - 22;
        int cyp = y + 34;
        int cr = 12;
        g.fill(cxp - cr - 2, cyp - cr - 2, cxp + cr + 3, cyp + cr + 13, 0xCC0A141C);
        this.ring(g, cxp, cyp, cr, cr, STROKE);
        double pr = Math.toRadians(e.pan());
        this.drawLine(g, cxp, cyp, cxp + (int) (Math.sin(pr) * cr), cyp - (int) (Math.cos(pr) * cr), e.lit() ? CYAN : TEXT_DARK);
        g.fill(cxp - 1, cyp - 1, cxp + 1, cyp + 1, TEXT);
        g.drawString(this.font, "PAN", cxp - this.font.width("PAN") / 2, cyp + cr + 2, TEXT_DARK, false);

        // captions
        int capY = y + h - 22;
        String l1 = "TILT " + e.tilt() + "\u00B0   RANGE " + e.range() + "   FOCUS " + e.focus();
        String l2 = "WIDTH " + e.beamWidth() + "   SPREAD " + e.lightSpread();
        g.drawString(this.font, this.trimToWidth(l1, w - 16), x + 8, capY, TEXT_DIM, false);
        g.drawString(this.font, this.trimToWidth(l2, w - 16), x + 8, capY + 10, TEXT_DARK, false);
    }

    private void sendLightAction(BlockPos observerPos, long lightPos, BattleScreenLightActionPacket.Action action) {
        PacketDistributor.sendToServer(new BattleScreenLightActionPacket(this.display.getBlockPos(), observerPos, lightPos, action));
    }

    private void sendLightMaster(BattleScreenLightActionPacket.Action action) {
        PacketDistributor.sendToServer(new BattleScreenLightActionPacket(this.display.getBlockPos(), this.display.getBlockPos(), 0L, action));
    }

    private void sendLightScene(int slot, boolean save) {
        BattleScreenLightActionPacket.Action[] saves = {
                BattleScreenLightActionPacket.Action.SCENE_SAVE_1,
                BattleScreenLightActionPacket.Action.SCENE_SAVE_2,
                BattleScreenLightActionPacket.Action.SCENE_SAVE_3 };
        BattleScreenLightActionPacket.Action[] recalls = {
                BattleScreenLightActionPacket.Action.SCENE_RECALL_1,
                BattleScreenLightActionPacket.Action.SCENE_RECALL_2,
                BattleScreenLightActionPacket.Action.SCENE_RECALL_3 };
        this.sendLightMaster((save ? saves : recalls)[slot]);
    }

    private void drawCategoryTag(GuiGraphics g, int x, int y, DamageObserverSettings.ObserverCategory category) {
        int color = this.categoryColor(category);
        g.fill(x, y, x + 22, y + 16, 0x33000000 | (color & 0x00FFFFFF));
        border(g, x, y, 22, 16, color);
        String tag = this.categoryShort(category);
        g.drawString(this.font, tag, x + (22 - this.font.width(tag)) / 2, y + 4, color, false);
    }

    // rail
    private void renderIntegrityRail(GuiGraphics g, int mouseX, int mouseY, float time, int x, int y, int width, int height, List<NearbyObserver> observers, NearbyObserver selected) {
        g.fill(x, y, x + width, y + height, PANEL_DEEP);
        border(g, x, y, width, height, STROKE_DIM);
        g.fill(x, y, x + width, y + 22, PANEL);
        g.drawString(this.font, "REGION ROSTER", x + 10, y + 7, TEXT_DIM, false);
        String count = Integer.toString(observers.size());
        g.drawString(this.font, count, x + width - 10 - this.font.width(count), y + 7, CYAN, false);

        if (observers.isEmpty()) {
            this.drawWorkbenchEmpty(g, x + 8, y + 30, width - 16, height - 38, "NO LINKED REGIONS");
            return;
        }

        int rowH = 50;
        int gap = 6;
        int rowX = x + 8;
        int rowW = width - 16;
        int rowY = y + 30;
        int maxRows = Math.max(1, (height - 38) / (rowH + gap));
        int maxScroll = Math.max(0, observers.size() - maxRows);
        this.integrityRailScroll = Math.max(0, Math.min(this.integrityRailScroll, maxScroll));
        int start = this.integrityRailScroll;
        int shown = Math.min(observers.size() - start, maxRows);
        for (int i = 0; i < shown; i++) {
            NearbyObserver observer = observers.get(start + i);
            int cy = rowY + i * (rowH + gap);
            boolean sel = selected != null && selected.observerPos().equals(observer.observerPos());
            boolean hover = this.inside(mouseX, mouseY, rowX, cy, rowW, rowH);
            int health = this.healthColor(observer);
            boolean critical = observer.healthPercent() < 55.0 || observer.missingBlocks() > 0;
            int fill = sel ? 0x2629D982 : hover ? 0x1F315B70 : PANEL;
            int stroke = sel ? GREEN : hover ? CYAN : STROKE_DIM;
            g.fill(rowX, cy, rowX + rowW, cy + rowH, fill);
            border(g, rowX, cy, rowW, rowH, stroke);
            if (sel) {
                g.fill(rowX, cy, rowX + 3, cy + rowH, GREEN);
            }
            this.drawCategoryTag(g, rowX + 7, cy + 8, observer.category());
            g.drawString(this.font, this.trimToWidth(observer.name(), rowW - 90), rowX + 34, cy + 8, TEXT, false);
            String pct = Math.round(observer.healthPercent()) + "%";
            g.drawString(this.font, pct, rowX + rowW - 8 - this.font.width(pct), cy + 8, health, false);
            float integrity = clamp((float) (observer.healthPercent() / 100.0), 0.0f, 1.0f);
            this.drawWorkbenchBar(g, rowX + 8, cy + 24, rowW - 16, 6, integrity, health);
            String sub = observer.missingBlocks() > 0 ? observer.missingBlocks() + " breaches" : "Intact";
            g.drawString(this.font, sub, rowX + 8, cy + 34, observer.missingBlocks() > 0 ? RED : TEXT_DARK, false);
            if (critical && observer.missingBlocks() > 0) {
                int pulse = (int) (Math.abs(Math.sin(time * 0.12f)) * 90) + 40;
                g.fill(rowX + rowW - 10, cy + 33, rowX + rowW - 8, cy + 41, (pulse << 24) | (RED & 0x00FFFFFF));
            }
            final BlockPos pos = observer.observerPos();
            this.weaponHitTargets.add(new WeaponHitTarget(rowX, cy, rowW, rowH, () -> this.selectedObserverPos = pos));
        }
        if (maxScroll > 0) {
            String more = (start + shown) + "/" + observers.size();
            g.drawString(this.font, more, rowX, y + height - 13, TEXT_DARK, false);
            g.drawString(this.font, "scroll", rowX + rowW - this.font.width("scroll"), y + height - 13, TEXT_DARK, false);
        }
    }

    // overview tab
    private void renderIntegrityOverviewTab(GuiGraphics g, int mouseX, int mouseY, float time, int x, int y, int width, int height, List<NearbyObserver> observers, NearbyObserver selected) {
        g.fill(x, y, x + width, y + 22, PANEL);
        border(g, x, y, width, 22, STROKE_DIM);
        g.drawString(this.font, "REGION OVERVIEW", x + 10, y + 7, TEXT_DIM, false);

        if (observers.isEmpty()) {
            this.drawWorkbenchEmpty(g, x, y + 32, width, height - 32, "LINK A DAMAGE OBSERVER TO BEGIN");
            return;
        }

        int gridTop = y + 32;
        int cols = width >= 470 ? 2 : 1;
        int gap = 12;
        int cardW = (width - gap * (cols - 1)) / cols;
        int cardH = 96;
        for (int i = 0; i < observers.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int cx = x + col * (cardW + gap);
            int cy = gridTop + row * (cardH + gap);
            if (cy + cardH > y + height) {
                break;
            }
            this.drawIntegrityCard(g, mouseX, mouseY, time, cx, cy, cardW, cardH, observers.get(i), selected);
        }
    }

    private void drawIntegrityCard(GuiGraphics g, int mouseX, int mouseY, float time, int x, int y, int width, int height, NearbyObserver observer, NearbyObserver selected) {
        boolean sel = selected != null && selected.observerPos().equals(observer.observerPos());
        boolean hover = this.inside(mouseX, mouseY, x, y, width, height);
        int health = this.healthColor(observer);
        g.fill(x + 3, y + 4, x + width + 3, y + height + 4, SHADOW);
        g.fill(x, y, x + width, y + height, hover || sel ? 0x141B2531 : PANEL);
        border(g, x, y, width, height, sel ? GREEN : hover ? CYAN : STROKE);
        g.fill(x, y, x + width, y + 2, health);

        this.drawCategoryTag(g, x + 10, y + 10, observer.category());
        g.drawString(this.font, this.trimToWidth(observer.name(), width - 120), x + 38, y + 9, TEXT, false);
        g.drawString(this.font, this.trimToWidth(observer.category().getDisplayName(), width - 120), x + 38, y + 21, TEXT_DIM, false);

        String badge = observer.missingBlocks() > 0 ? "DAMAGED" : "INTACT";
        int badgeColor = observer.missingBlocks() > 0 ? RED : GREEN;
        int badgeW = Math.max(70, this.font.width(badge) + 16);
        int badgeX = x + width - badgeW - 8;
        g.fill(badgeX, y + 9, badgeX + badgeW, y + 25, 0x33000000 | (badgeColor & 0x00FFFFFF));
        border(g, badgeX, y + 9, badgeW, 16, badgeColor);
        g.drawString(this.font, badge, badgeX + (badgeW - this.font.width(badge)) / 2, y + 13, badgeColor, false);

        float integrity = clamp((float) (observer.healthPercent() / 100.0), 0.0f, 1.0f);
        g.drawString(this.font, "INTEGRITY", x + 10, y + 42, TEXT_DARK, false);
        String pct = Math.round(observer.healthPercent()) + "%";
        g.drawString(this.font, pct, x + width - 10 - this.font.width(pct), y + 42, health, false);
        this.drawWorkbenchBar(g, x + 10, y + 54, width - 20, 7, integrity, health);

        String line = this.trimToWidth(this.categoryDetailLine(observer), width - 20);
        g.drawString(this.font, line, x + 10, y + 68, TEXT_DIM, false);

        final BlockPos pos = observer.observerPos();
        this.weaponHitTargets.add(new WeaponHitTarget(x, y, width, height, () -> {
            this.selectedObserverPos = pos;
            this.setIntegrityTab(IntegrityTab.DETAIL);
        }));
    }

    // ship Map tab
    private void renderIntegrityShipMapTab(GuiGraphics g, int mouseX, int mouseY, float time, int x, int y, int width, int height, List<NearbyObserver> observers, NearbyObserver selected) {
        g.fill(x, y, x + width, y + 22, PANEL);
        border(g, x, y, width, 22, STROKE_DIM);
        g.drawString(this.font, "SHIP DAMAGE MAP", x + 10, y + 7, CYAN, false);
        String detail = selected == null ? "NO FOCUS" : this.trimToWidth(this.categoryDetailLine(selected), 240);
        g.drawString(this.font, detail, x + width - 10 - this.font.width(detail), y + 7, selected == null ? TEXT_DIM : this.healthColor(selected), false);

        int legendH = 24;
        int viewX = x;
        int viewY = y + 30;
        int viewW = width;
        int viewH = height - 30 - legendH - 6;
        g.fill(viewX, viewY, viewX + viewW, viewY + viewH, PANEL_DEEP);
        border(g, viewX, viewY, viewW, viewH, STROKE);
        this.drawGrid(g, viewX + 1, viewY + 1, viewW - 2, viewH - 2, 18, 0x182D5965);
        if (observers.isEmpty()) {
            this.renderRadarSearch(g, viewX, viewY, viewW, viewH, time);
        } else {
            this.renderVehicleDamageMap(g, viewX, viewY, viewW, viewH, observers, selected, time);
        }

        // legend
        int legendY = viewY + viewH + 6;
        g.fill(x, legendY, x + width, legendY + legendH, PANEL);
        border(g, x, legendY, width, legendH, STROKE_DIM);
        DamageObserverSettings.ObserverCategory[] cats = DamageObserverSettings.ObserverCategory.values();
        int slot = width / cats.length;
        for (int i = 0; i < cats.length; i++) {
            int lx = x + 8 + i * slot;
            int color = this.categoryColor(cats[i]);
            g.fill(lx, legendY + 8, lx + 8, legendY + 16, color);
            g.drawString(this.font, cats[i].getDisplayName(), lx + 12, legendY + 8, TEXT_DIM, false);
        }
    }

    // detail tab
    private void renderIntegrityDetailTab(GuiGraphics g, int mouseX, int mouseY, float time, int x, int y, int width, int height, List<NearbyObserver> observers, NearbyObserver selected) {
        if (selected == null) {
            this.drawWorkbenchEmpty(g, x, y, width, height, "NO REGION SELECTED");
            return;
        }
        int health = this.healthColor(selected);

        // header card
        g.fill(x, y, x + width, y + 34, PANEL);
        border(g, x, y, width, 34, STROKE);
        this.drawCategoryTag(g, x + 10, y + 9, selected.category());
        g.drawString(this.font, this.trimToWidth(selected.name(), width - 240), x + 38, y + 7, TEXT, false);
        g.drawString(this.font, selected.category().getDisplayName() + " region", x + 38, y + 19, TEXT_DIM, false);
        String pct = Math.round(selected.healthPercent()) + "%";
        this.drawScaledString(g, pct, x + width - 12 - this.font.width(pct) * 2, y + 8, health, 2);

        // integrity bar
        int barY = y + 42;
        float integrity = clamp((float) (selected.healthPercent() / 100.0), 0.0f, 1.0f);
        this.drawWorkbenchBar(g, x, barY, width, 9, integrity, health);

        // stat grid
        int statTop = barY + 18;
        int colGap = 10;
        int colW = (width - colGap) / 2;
        int rowH = 26;
        int gap = 6;
        this.drawConsoleStat(g, x, statTop, colW, "Integrity", Math.round(selected.healthPercent()) + "%", health);
        this.drawConsoleStat(g, x + colW + colGap, statTop, colW, "Breaches", Integer.toString(selected.missingBlocks()), selected.missingBlocks() > 0 ? RED : GREEN);
        this.drawConsoleStat(g, x, statTop + rowH + gap, colW, "New blocks", Integer.toString(selected.newBlocks()), selected.newBlocks() > 0 ? AMBER : TEXT_DIM);
        this.drawConsoleStat(g, x + colW + colGap, statTop + rowH + gap, colW, "Baseline", Integer.toString(selected.baselineBlocks()), TEXT);
        this.drawConsoleStat(g, x, statTop + (rowH + gap) * 2, colW, "Link", Math.round(this.linkStrength(selected) * 100) + "%", CYAN);
        this.drawConsoleStat(g, x + colW + colGap, statTop + (rowH + gap) * 2, colW, "Distance", selected.distance() + "m", TEXT);

        // category-specific panel
        int panelTop = statTop + (rowH + gap) * 3 + 6;
        int panelH = y + height - panelTop;
        if (panelH < 40) {
            return;
        }
        g.fill(x, panelTop, x + width, panelTop + panelH, PANEL_DEEP);
        border(g, x, panelTop, width, panelH, STROKE_DIM);
        if (selected.category() == DamageObserverSettings.ObserverCategory.CARGO) {
            g.drawString(this.font, "CARGO RESERVES", x + 10, panelTop + 7, TEXT_DIM, false);
            this.drawFillMeter(g, x + 12, panelTop + 24, width - 24, "ITEM", selected.cargoItems(), selected.cargoItemCapacity(), CYAN);
            this.drawFillMeter(g, x + 12, panelTop + 40, width - 24, "FLUID", selected.cargoFluidAmount(), selected.cargoFluidCapacity(), VIOLET);
            this.drawFillMeter(g, x + 12, panelTop + 56, width - 24, "POWER", selected.cargoEnergyStored(), selected.cargoEnergyCapacity(), AMBER);
        } else if (selected.category() == DamageObserverSettings.ObserverCategory.WEAPONS && selected.weapon().detected()) {
            g.drawString(this.font, "WEAPON STATUS", x + 10, panelTop + 7, TEXT_DIM, false);
            g.drawString(this.font, this.trimToWidth(this.weaponStatusLine(selected), width - 20), x + 12, panelTop + 24, this.weaponReadinessColor(selected.weapon()), false);
            this.drawWorkbenchButton(g, mouseX, mouseY, x + 12, panelTop + 40, 150, 20, "Open Fire Control", false, true, () -> this.selectScreenMode(ScreenMode.WEAPONS));
        } else {
            g.drawString(this.font, "INTEGRITY HISTORY", x + 10, panelTop + 7, TEXT_DIM, false);
            this.renderIntegrityHistory(g, x + 10, panelTop + 22, width - 20, panelH - 30, observers, selected);
        }
    }

    private void openTargetPicker() {
        NearbyObserver weaponObserver = this.focusWeaponObserver(this.nearbyObservers(), this.focusedObserver(this.nearbyObservers()));
        if (weaponObserver == null || !this.supportsAimLock(weaponObserver.weapon())) {
            return;
        }
        this.targetPickerScroll = 0;
        this.targetPickerOpen = true;
    }

    private void handleWeaponLockButton() {
        this.openTargetPicker();
    }

    private boolean handleTargetPickerClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return true;
        }
        for (TargetPickerClickTarget target : this.targetPickerClickTargets) {
            if (!target.contains(mouseX, mouseY)) {
                continue;
            }
            if (target.action() == TargetPickerAction.CLOSE) {
                this.targetPickerOpen = false;
                return true;
            }
            if (target.action() == TargetPickerAction.CLEAR) {
                this.sendWeaponAction(BattleScreenWeaponActionPacket.Action.CLEAR_AUTO_AIM_LOCK);
                this.targetPickerOpen = false;
                return true;
            }
            if (target.action() == TargetPickerAction.SELECT && target.targetId() != null) {
                this.sendWeaponAction(BattleScreenWeaponActionPacket.Action.SELECT_AUTO_AIM_TARGET, target.targetId());
                this.targetPickerOpen = false;
                return true;
            }
        }
        int pickerWidth = Math.min(650, this.width - 48);
        int pickerHeight = Math.min(390, this.height - 48);
        int pickerLeft = (this.width - pickerWidth) / 2;
        int pickerTop = (this.height - pickerHeight) / 2;
        if (mouseX < pickerLeft || mouseX >= pickerLeft + pickerWidth || mouseY < pickerTop || mouseY >= pickerTop + pickerHeight) {
            this.targetPickerOpen = false;
        }
        return true;
    }

    private boolean handleWeaponResponseSliderClick(double mouseX, double mouseY, int button) {
        if (button != 0 || this.weaponResponseSliderTarget == null || !this.weaponResponseSliderTarget.active() || !this.weaponResponseSliderTarget.contains(mouseX, mouseY)) {
            return false;
        }
        this.weaponResponseSliderDragging = true;
        this.weaponResponseSliderPreviewActive = true;
        this.weaponResponseSliderObserverPos = this.weaponResponseSliderTarget.observerPos();
        this.weaponResponseSliderPreview = this.responseMultiplierFromMouse(mouseX, this.weaponResponseSliderTarget);
        return true;
    }

    private void commitWeaponResponseSlider() {
        if (this.weaponResponseSliderObserverPos == null) {
            return;
        }
        this.sendWeaponResponse(this.weaponResponseSliderObserverPos, this.weaponResponseSliderPreview);
    }

    private float responseMultiplierFromMouse(double mouseX, WeaponResponseSliderTarget target) {
        float normalized = clamp((float) ((mouseX - target.x()) / Math.max(1.0, target.width())), 0.0f, 1.0f);
        return target.minResponse() + normalized * (target.maxResponse() - target.minResponse());
    }

    private float currentWeaponResponseMultiplier(NearbyObserver weaponObserver) {
        float minResponse = this.weaponResponseMin(weaponObserver.weapon());
        float maxResponse = this.weaponResponseMax(weaponObserver.weapon());
        float serverValue = clamp(weaponObserver.weapon().autoAim().responseMultiplier(), minResponse, maxResponse);
        if (this.weaponResponseSliderPreviewActive && weaponObserver.observerPos().equals(this.weaponResponseSliderObserverPos)) {
            float previewValue = clamp(this.weaponResponseSliderPreview, minResponse, maxResponse);
            if (!this.weaponResponseSliderDragging && Math.abs(serverValue - previewValue) <= 0.02f) {
                this.weaponResponseSliderPreviewActive = false;
                return serverValue;
            }
            return previewValue;
        }
        return serverValue;
    }

    private void renderWeaponResponseSlider(GuiGraphics g, int mouseX, int mouseY, float time, NearbyObserver weaponObserver) {
        if (this.screenMode != ScreenMode.WEAPONS || weaponObserver == null || !this.supportsAimLock(weaponObserver.weapon())) {
            return;
        }

        int buttonY = this.panelTop + this.panelHeight - 32;
        int x = this.panelLeft + this.panelWidth - 666;
        int y = buttonY - 24;
        int width = 648;
        int height = 20;
        int labelWidth = 84;
        int valueWidth = 56;
        int trackX = x + labelWidth;
        int trackWidth = width - labelWidth - valueWidth;
        int trackY = y + 8;
        int trackHeight = 5;

        boolean active = weaponObserver.weapon().missingBlocks() == 0;
        float minResponse = this.weaponResponseMin(weaponObserver.weapon());
        float maxResponse = this.weaponResponseMax(weaponObserver.weapon());
        float response = this.currentWeaponResponseMultiplier(weaponObserver);
        float normalized = clamp((response - minResponse) / Math.max(0.001f, maxResponse - minResponse), 0.0f, 1.0f);
        int fillWidth = Math.max(0, Math.round(trackWidth * normalized));
        boolean hover = this.inside(mouseX, mouseY, trackX - 6, y, trackWidth + 12, height);
        int frameColor = !active ? STROKE_DIM : this.weaponResponseSliderDragging ? GREEN : hover ? CYAN : STROKE;
        int textColor = active ? CYAN : TEXT_DARK;

        g.fill(x, y, x + width, y + height, PANEL_DEEP);
        border(g, x, y, width, height, frameColor);
        g.drawString(this.font, this.isAutocannon(weaponObserver.weapon()) ? "TRACK RESPONSE" : "HEAVY TRACK", x + 8, y + 6, TEXT_DIM, false);
        String valueText = this.formatWeaponResponse(weaponObserver.weapon(), response);
        g.drawString(this.font, valueText, x + width - this.font.width(valueText) - 8, y + 6, textColor, false);

        g.fill(trackX, trackY, trackX + trackWidth, trackY + trackHeight, 0xFF071018);
        g.fill(trackX, trackY - 2, trackX + trackWidth, trackY - 1, 0x22111826);
        if (fillWidth > 0) {
            int segments = Math.max(1, Math.min(14, fillWidth / 10 + 1));
            for (int index = 0; index < segments; index++) {
                int start = trackX + fillWidth * index / segments;
                int end = trackX + fillWidth * (index + 1) / segments;
                g.fill(start, trackY, end, trackY + trackHeight, lerpColor(CYAN, VIOLET, segments == 1 ? 0.0f : index / (float) (segments - 1)));
            }
        }
        if (active) {
            int shimmerWidth = Math.max(12, trackWidth / 8);
            int shimmerX = trackX + Math.round(((float) Math.sin(time * 0.08f) * 0.5f + 0.5f) * Math.max(0, trackWidth - shimmerWidth));
            g.fill(shimmerX, trackY - 1, shimmerX + shimmerWidth, trackY + trackHeight + 1, 0x224AD9FF);
        }

        int knobX = trackX + Math.round(trackWidth * normalized);
        int knobY = trackY + trackHeight / 2;
        int knobColor = !active ? TEXT_DARK : this.weaponResponseSliderDragging ? GREEN : hover ? CYAN : TEXT;
        g.fill(knobX - 4, knobY - 5, knobX + 5, knobY + 6, PANEL);
        border(g, knobX - 4, knobY - 5, 9, 11, knobColor);
        g.fill(knobX - 1, knobY - 2, knobX + 2, knobY + 3, knobColor);
        if (active && (hover || this.weaponResponseSliderDragging)) {
            int glow = 5 + (int) ((Math.sin(time * 0.22f) + 1.0f) * 1.5f);
            this.ring(g, knobX, knobY, glow, glow, 0x334AD9FF);
        }

        g.drawString(this.font, this.formatWeaponResponse(weaponObserver.weapon(), minResponse), trackX - 28, y + 6, TEXT_DARK, false);
        g.drawString(this.font, this.formatWeaponResponse(weaponObserver.weapon(), maxResponse), trackX + trackWidth + 6, y + 6, TEXT_DARK, false);

        this.weaponResponseSliderTarget = new WeaponResponseSliderTarget(trackX, y, trackWidth, height, weaponObserver.observerPos(), active, minResponse, maxResponse);
    }

    private void renderTargetPicker(GuiGraphics g, int mouseX, int mouseY) {
        this.targetPickerClickTargets.clear();
        NearbyObserver weaponObserver = this.focusWeaponObserver(this.nearbyObservers(), this.focusedObserver(this.nearbyObservers()));
        int pickerWidth = Math.min(650, this.width - 48);
        int pickerHeight = Math.min(390, this.height - 48);
        int x = (this.width - pickerWidth) / 2;
        int y = (this.height - pickerHeight) / 2;
        g.fill(0, 0, this.width, this.height, 0xB0000000);
        g.fill(x + 5, y + 7, x + pickerWidth + 5, y + pickerHeight + 7, SHADOW);
        g.fill(x, y, x + pickerWidth, y + pickerHeight, PANEL_DEEP);
        border(g, x, y, pickerWidth, pickerHeight, STROKE);
        g.fill(x + 1, y + 1, x + pickerWidth - 1, y + 4, CYAN);

        int closeX = x + pickerWidth - 28;
        int closeY = y + 10;
        g.fill(closeX, closeY, closeX + 18, closeY + 18, this.inside(mouseX, mouseY, closeX, closeY, 18, 18) ? 0x44FF5D69 : PANEL);
        border(g, closeX, closeY, 18, 18, RED);
        g.drawString(this.font, "X", closeX + 6, closeY + 5, RED, false);
        this.targetPickerClickTargets.add(new TargetPickerClickTarget(closeX, closeY, 18, 18, TargetPickerAction.CLOSE, null));

        this.drawScaledString(g, "TARGET LOCK", x + 18, y + 12, TEXT, 2);
        String observerName = weaponObserver == null ? "NO WEAPON" : this.trimToWidth(weaponObserver.name(), pickerWidth - 250);
        g.drawString(this.font, observerName, x + 20, y + 36, CYAN, false);

        BattleScreenDisplayBE.AutoAimSnapshot autoAim = weaponObserver == null ? BattleScreenDisplayBE.AutoAimSnapshot.none() : weaponObserver.weapon().autoAim();
        List<BattleScreenDisplayBE.AutoAimTargetOption> targets = autoAim.targetOptions();
        int clearX = x + pickerWidth - 150;
        int clearY = y + 42;
        if (autoAim.targetLocked()) {
            g.fill(clearX, clearY, clearX + 124, clearY + 20, this.inside(mouseX, mouseY, clearX, clearY, 124, 20) ? 0x55FFC15E : PANEL);
            border(g, clearX, clearY, 124, 20, AMBER);
            g.drawString(this.font, "Release Lock", clearX + 26, clearY + 6, AMBER, false);
            this.targetPickerClickTargets.add(new TargetPickerClickTarget(clearX, clearY, 124, 20, TargetPickerAction.CLEAR, null));
        }

        int chipY = y + 58;
        this.drawTargetChip(g, x + 20, chipY, 118, "TRACKS", Integer.toString(targets.size()), targets.isEmpty() ? AMBER : GREEN);
        this.drawTargetChip(g, x + 146, chipY, 118, "LOCK", autoAim.targetLocked() ? "ACTIVE" : "OPEN", autoAim.targetLocked() ? GREEN : CYAN);
        this.drawTargetChip(g, x + 272, chipY, 152, "MODE", autoAim.advancedPredictionEnabled() ? "ADV PREDICT" : autoAim.predictionEnabled() ? "PREDICT" : "DIRECT", autoAim.predictionEnabled() ? VIOLET : TEXT_DIM);

        int listTop = y + 88;
        int rowHeight = 42;
        int visibleRows = this.targetPickerVisibleRows();
        int maxScroll = Math.max(0, targets.size() - visibleRows);
        this.targetPickerScroll = Math.max(0, Math.min(maxScroll, this.targetPickerScroll));
        if (targets.isEmpty()) {
            int boxY = listTop + 28;
            g.fill(x + 20, boxY, x + pickerWidth - 20, boxY + 86, PANEL);
            border(g, x + 20, boxY, pickerWidth - 40, 86, STROKE_DIM);
            g.drawString(this.font, "NO LOCKABLE TARGETS IN RANGE", x + 42, boxY + 26, AMBER, false);
            g.drawString(this.font, "SCAN VOLUME EMPTY / ENTITIES AND SUB-LEVELS NOT PRESENT", x + 42, boxY + 44, TEXT_DIM, false);
            return;
        }

        int end = Math.min(targets.size(), this.targetPickerScroll + visibleRows);
        for (int index = this.targetPickerScroll; index < end; index++) {
            BattleScreenDisplayBE.AutoAimTargetOption target = targets.get(index);
            int rowY = listTop + (index - this.targetPickerScroll) * rowHeight;
            this.renderTargetRow(g, x + 20, rowY, pickerWidth - 40, rowHeight - 4, target, mouseX, mouseY);
        }
        if (maxScroll > 0) {
            int trackX = x + pickerWidth - 14;
            int trackY = listTop;
            int trackHeight = visibleRows * rowHeight - 4;
            g.fill(trackX, trackY, trackX + 3, trackY + trackHeight, STROKE_DIM);
            int thumbHeight = Math.max(24, trackHeight * visibleRows / targets.size());
            int thumbY = trackY + (trackHeight - thumbHeight) * this.targetPickerScroll / maxScroll;
            g.fill(trackX - 1, thumbY, trackX + 4, thumbY + thumbHeight, CYAN);
        }
    }

    private void renderTargetRow(GuiGraphics g, int x, int y, int width, int height, BattleScreenDisplayBE.AutoAimTargetOption target, int mouseX, int mouseY) {
        boolean hover = this.inside(mouseX, mouseY, x, y, width, height);
        int rowColor = target.locked() ? 0x3329D982 : hover ? 0x22315B70 : PANEL;
        int rowStroke = target.locked() ? GREEN : hover ? CYAN : STROKE_DIM;
        g.fill(x, y, x + width, y + height, rowColor);
        border(g, x, y, width, height, rowStroke);
        int typeColor = this.targetTypeColor(target.type());
        g.fill(x + 8, y + 8, x + 76, y + 20, 0x33000000 | (typeColor & 0x00FFFFFF));
        border(g, x + 8, y + 8, 68, 12, typeColor);
        g.drawString(this.font, this.trimToWidth(target.type(), 60), x + 12, y + 11, typeColor, false);
        g.drawString(this.font, this.trimToWidth(target.label(), width - 280), x + 86, y + 8, TEXT, false);

        String range = String.format(java.util.Locale.ROOT, "%.0fm", target.distance());
        String speed = String.format(java.util.Locale.ROOT, "%.1fm/s", target.speed());
        g.drawString(this.font, range, x + width - 160, y + 8, CYAN, false);
        g.drawString(this.font, speed, x + width - 104, y + 8, target.speed() > 0.05 ? VIOLET : TEXT_DIM, false);
        if (target.locked()) {
            g.drawString(this.font, "LOCKED", x + width - 54, y + 8, GREEN, false);
        }

        int coordMaxWidth = target.maxHealth() > 0.0 ? width - 270 : width - 96;
        String coords = String.format(java.util.Locale.ROOT, "WORLD  X %.1f   Y %.1f   Z %.1f", target.worldX(), target.worldY(), target.worldZ());
        g.drawString(this.font, this.trimToWidth(coords, coordMaxWidth), x + 86, y + 24, TEXT_DIM, false);
        if (target.maxHealth() > 0.0) {
            int barX = x + width - 162;
            int barY = y + 26;
            int barW = 118;
            float health = clamp((float) (target.health() / target.maxHealth()), 0.0f, 1.0f);
            g.fill(barX, barY, barX + barW, barY + 5, 0x66000000);
            g.fill(barX, barY, barX + Math.round(barW * health), barY + 5, health > 0.55f ? GREEN : health > 0.25f ? AMBER : RED);
            String healthText = String.format(java.util.Locale.ROOT, "%.0f/%.0f", target.health(), target.maxHealth());
            g.drawString(this.font, this.trimToWidth(healthText, 42), x + width - 42, y + 23, TEXT_DIM, false);
        }
        this.targetPickerClickTargets.add(new TargetPickerClickTarget(x, y, width, height, TargetPickerAction.SELECT, target.targetId()));
    }

    private int targetPickerVisibleRows() {
        int pickerHeight = Math.min(390, this.height - 48);
        return Math.max(3, (pickerHeight - 116) / 42);
    }

    private void drawTargetChip(GuiGraphics g, int x, int y, int width, String label, String value, int color) {
        g.fill(x, y, x + width, y + 20, PANEL);
        border(g, x, y, width, 20, STROKE_DIM);
        g.drawString(this.font, label, x + 7, y + 4, TEXT_DIM, false);
        g.drawString(this.font, this.trimToWidth(value, width - 58), x + 58, y + 4, color, false);
    }

    private int targetTypeColor(String type) {
        return switch (type) {
            case "PLAYER" -> CYAN;
            case "SUBLEVEL", "CONTRAPTION" -> VIOLET;
            case "LIVING" -> GREEN;
            default -> AMBER;
        };
    }

    private boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void renderHeader(GuiGraphics g, List<NearbyObserver> observers, NearbyObserver focusObserver, float time, int x, int y, int width) {
        g.fill(x, y, x + width, y + 56, PANEL);
        border(g, x, y, width, 56, STROKE_DIM);
        this.drawScaledString(g, "BATTLE BRIDGE", x + 13, y + 9, TEXT, 2);
        g.drawString(this.font, "BATTLE OBSERVER COMMAND SURFACE", x + 16, y + 35, CYAN, false);
        String weaponsBanner = this.weaponsCompatibilityBanner(observers);
        g.drawString(this.font, weaponsBanner.isEmpty() ? "DISPLAY " + this.display.getBlockPos().toShortString() : this.trimToWidth(weaponsBanner, width / 2), x + 16, y + 47, weaponsBanner.isEmpty() ? TEXT_DIM : this.weaponsCompatibilityColor(), false);

        int chipY = y + 10;
        int chipX = x + width - 500;
        double integrity = this.overallHealth(observers);
        int integrityColor = this.healthColor(integrity, this.totalMissing(observers));
        this.drawMetricChip(g, chipX, chipY, 112, "INTEGRITY", observers.isEmpty() ? "NO FEED" : Math.round(integrity) + "%", observers.isEmpty() ? AMBER : integrityColor);
        this.drawMetricChip(g, chipX + 122, chipY, 112, "BREACHES", Integer.toString(this.totalMissing(observers)), this.totalMissing(observers) > 0 ? RED : GREEN);
        this.drawMetricChip(g, chipX + 244, chipY, 118, "CARGO", this.cargoSummaryText(observers), this.cargoFillColor(this.cargoFillPercent(observers)));
        this.drawMetricChip(g, chipX + 372, chipY, 118, "FOCUS", focusObserver == null ? "ACQUIRING" : focusObserver.name(), focusObserver == null ? AMBER : this.healthColor(focusObserver));

        int pulseX = x + width - 24;
        int pulse = 4 + (int) (Math.sin(time * 0.25f) * 2.0f + 2.0f);
        this.ring(g, pulseX, y + 46, pulse + 5, pulse + 5, observers.isEmpty() ? AMBER : GREEN);
        g.fill(pulseX - 2, y + 44, pulseX + 3, y + 49, observers.isEmpty() ? AMBER : GREEN);
    }

    private String weaponsCompatibilityBanner(List<NearbyObserver> observers) {
        if (!this.hasWeaponsObserver(observers)) {
            return CreateBigCannonsCompat.isAvailable() ? "C: Big Cannons detected / weapon screen idle" : "";
        }
        return CreateBigCannonsCompat.isAvailable()
                ? "C: Big Cannons detected! Weapon screen activated!"
                : "Create Big Cannons not detected. Weapon screen deactivated!";
    }

    private int weaponsCompatibilityColor() {
        return CreateBigCannonsCompat.isAvailable() ? GREEN : AMBER;
    }

    private boolean hasWeaponsObserver(List<NearbyObserver> observers) {
        for (NearbyObserver observer : observers) {
            if (observer.category() == DamageObserverSettings.ObserverCategory.WEAPONS) {
                return true;
            }
        }
        return false;
    }

    private boolean weaponsTabEnabled(List<NearbyObserver> observers) {
        return CreateBigCannonsCompat.isAvailable() && this.hasWeaponsObserver(observers);
    }

    private void renderModeRail(GuiGraphics g, int x, int y, int width, int height, List<NearbyObserver> observers) {
        g.fill(x, y, x + width, y + height, PANEL_DEEP);
        border(g, x, y, width, height, STROKE_DIM);
        int color = this.screenMode == ScreenMode.WEAPONS ? this.weaponsCompatibilityColor() : CYAN;
        String modeText = this.screenMode == ScreenMode.WEAPONS
                ? (this.weaponsTabEnabled(observers) ? "WEAPON FIRE-CONTROL ONLINE" : "WEAPON FIRE-CONTROL STANDBY")
                : "VEHICLE INTEGRITY MAP ONLINE";
        g.drawString(this.font, modeText, x + 228, y + 8, color, false);
    }

    private void renderStatusBand(GuiGraphics g, int x, int y, int width, int height, List<NearbyObserver> observers, NearbyObserver focusObserver, float time) {
        g.fill(x, y, x + width, y + height, PANEL_DEEP);
        border(g, x, y, width, height, STROKE_DIM);

        double integrity = this.overallHealth(observers);
        int missing = this.totalMissing(observers);
        float coverage = observers.isEmpty() ? 0.0f : (float) (integrity / 100.0);
        int barX = x + 112;
        int barY = y + 14;
        int barW = Math.min(248, width / 3);
        int integrityColor = this.healthColor(integrity, missing);
        g.drawString(this.font, "VEHICLE INTEGRITY", x + 12, y + 9, TEXT_DIM, false);
        g.fill(barX, barY, barX + barW, barY + 9, 0xFF17232D);
        int fill = Math.max(3, (int) (barW * coverage));
        g.fill(barX, barY, barX + fill, barY + 9, observers.isEmpty() ? AMBER : integrityColor);
        for (int marker = 0; marker <= 8; marker++) {
            int markerX = barX + marker * barW / 8;
            g.fill(markerX, barY - 2, markerX + 1, barY + 12, 0x663B5866);
        }
        String integrityText = observers.isEmpty() ? "NO OBSERVER FEED" : Math.round(integrity) + "% / " + missing + " lost / +" + this.totalNew(observers);
        g.drawString(this.font, integrityText, barX + barW + 10, y + 10, observers.isEmpty() ? AMBER : integrityColor, false);

        int categoryX = x + width / 2 + 34;
        EnumMap<DamageObserverSettings.ObserverCategory, Integer> counts = this.categoryCounts(observers);
        int chipX = categoryX;
        for (DamageObserverSettings.ObserverCategory category : DamageObserverSettings.ObserverCategory.values()) {
            int count = counts.getOrDefault(category, 0);
            if (count <= 0) {
                continue;
            }
            CategorySummary summary = this.categorySummary(category, observers);
            int color = this.healthColor(summary.healthPercent(), summary.missingBlocks());
            g.fill(chipX, y + 10, chipX + 48, y + 28, 0xFF13202A);
            border(g, chipX, y + 10, 48, 18, color);
            g.drawString(this.font, this.categoryShort(category) + " " + Math.round(summary.healthPercent()), chipX + 5, y + 15, color, false);
            chipX += 54;
        }
        if (chipX == categoryX) {
            g.drawString(this.font, "ROLE BUS IDLE", categoryX, y + 15, TEXT_DIM, false);
        }

        String focusLine = focusObserver == null
                ? "No Display Link feed connected"
                : focusObserver.name() + " -> " + this.categoryDetailLine(focusObserver);
            g.drawString(this.font, this.trimToWidth(focusLine, width - 36), x + 12, y + height - 14, focusObserver == null ? AMBER : this.healthColor(focusObserver), false);
    }

    private void renderZoneStack(GuiGraphics g, int x, int y, int width, int height, List<NearbyObserver> observers, NearbyObserver focusObserver, float time) {
        this.zoneClickTargets.clear();
        g.fill(x, y, x + width, y + height, PANEL);
        border(g, x, y, width, height, STROKE_DIM);
        g.drawString(this.font, "ROLE STACK", x + 12, y + 10, AMBER, false);
        int activeCategories = this.activeCategoryCount(observers);
        String stackCount = observers.isEmpty() ? "NO LINKED OBSERVERS" : observers.size() + " REG / " + activeCategories + " ROLE";
        g.drawString(this.font, stackCount, x + width - this.font.width(stackCount) - 12, y + 10, observers.isEmpty() ? TEXT_DIM : GREEN, false);

        if (observers.isEmpty()) {
            this.renderEmptyZoneStack(g, x + 12, y + 36, width - 24, height - 48, time);
            return;
        }

        int cursorY = y + 34;
        int bottomY = y + height - 16;
        int headerHeight = 18;
        int entryHeight = 38;
        int displayedEntries = 0;
        int entryIndex = 1;
        boolean truncated = false;

        categoryLoop:
        for (DamageObserverSettings.ObserverCategory category : DamageObserverSettings.ObserverCategory.values()) {
            CategorySummary summary = this.categorySummary(category, observers);
            if (summary.count() <= 0) {
                continue;
            }
            if (cursorY + headerHeight > bottomY) {
                truncated = true;
                break;
            }
            int categoryColor = this.categoryColor(category);
            int summaryColor = this.healthColor(summary.healthPercent(), summary.missingBlocks());
            g.fill(x + 8, cursorY, x + width - 8, cursorY + headerHeight, 0xFF09131B);
            g.fill(x + 8, cursorY, x + 11, cursorY + headerHeight, categoryColor);
            border(g, x + 8, cursorY, width - 16, headerHeight, STROKE_DIM);
            this.zoneClickTargets.add(new ZoneClickTarget(x + 8, cursorY, width - 16, headerHeight, category, null));
            g.drawString(this.font, category.getDisplayName().toUpperCase(), x + 16, cursorY + 5, categoryColor, false);
            String summaryText = summary.count() + " entries  " + Math.round(summary.healthPercent()) + "%";
            g.drawString(this.font, summaryText, x + width - this.font.width(summaryText) - 13, cursorY + 5, summaryColor, false);
            cursorY += headerHeight + 4;

            for (NearbyObserver observer : observers) {
                if (observer.category() != category) {
                    continue;
                }
                int rowHeight = this.screenMode == ScreenMode.WEAPONS && category == DamageObserverSettings.ObserverCategory.WEAPONS ? 54 : entryHeight;
                if (cursorY + rowHeight > bottomY) {
                    truncated = true;
                    break categoryLoop;
                }
                boolean selected = focusObserver != null && observer.observerPos().equals(focusObserver.observerPos());
                int color = observer.category() == DamageObserverSettings.ObserverCategory.WEAPONS && observer.weapon().detected() ? this.weaponReadinessColor(observer.weapon()) : observer.missingBlocks() > 0 ? this.healthColor(observer) : categoryColor;
                g.fill(x + 14, cursorY, x + width - 8, cursorY + rowHeight - 4, selected ? 0xFF172B37 : 0xFF0E1A23);
                border(g, x + 14, cursorY, width - 22, rowHeight - 4, selected ? color : STROKE_DIM);
                this.zoneClickTargets.add(new ZoneClickTarget(x + 14, cursorY, width - 22, rowHeight - 4, category, observer.observerPos()));
                g.fill(x + 18, cursorY + 5, x + 20, cursorY + rowHeight - 10, color);
                String indexText = entryIndex < 10 ? "0" + entryIndex : Integer.toString(entryIndex);
                g.drawString(this.font, indexText, x + 25, cursorY + 5, selected ? TEXT : TEXT_DIM, false);
                g.drawString(this.font, this.trimToWidth(observer.name(), width - 108), x + 44, cursorY + 5, TEXT, false);
                String damageLine = observer.category() == DamageObserverSettings.ObserverCategory.WEAPONS && observer.weapon().detected()
                        ? this.weaponListPrimaryLine(observer.weapon())
                        : observer.category() == DamageObserverSettings.ObserverCategory.CARGO
                        ? this.compactCargoText(observer)
                        : Math.round(observer.healthPercent()) + "%  M" + observer.missingBlocks() + "/" + observer.baselineBlocks() + "  +" + observer.newBlocks();
                g.drawString(this.font, this.trimToWidth(damageLine, width - 74), x + 44, cursorY + 18, color, false);
                if (rowHeight > entryHeight) {
                    g.drawString(this.font, this.trimToWidth(this.weaponListSecondaryLine(observer.weapon()), width - 74), x + 44, cursorY + 31, TEXT_DIM, false);
                }
                int healthBarX = x + 44;
                int healthBarY = cursorY + rowHeight - 10;
                int healthBarW = width - 82;
                g.fill(healthBarX, healthBarY, healthBarX + healthBarW, healthBarY + 4, 0xFF081018);
                g.fill(healthBarX, healthBarY, healthBarX + Math.max(2, (int) (healthBarW * observer.healthPercent() / 100.0)), healthBarY + 4, color);
                this.drawSignalBars(g, x + width - 42, cursorY + 10, this.linkStrength(observer), color);
                if (selected) {
                    int pulse = 2 + (int) (Math.sin(time * 0.35f) * 1.5f + 1.5f);
                    this.ring(g, x + width - 16, cursorY + 16, pulse + 4, pulse + 4, color);
                }
                cursorY += rowHeight;
                entryIndex++;
                displayedEntries++;
            }
            cursorY += 2;
        }

        int remaining = observers.size() - displayedEntries;
        if (truncated && remaining > 0) {
            g.drawString(this.font, "+" + remaining + " more linked entries", x + 12, y + height - 15, TEXT_DIM, false);
        }
    }

    private void renderEmptyZoneStack(GuiGraphics g, int x, int y, int width, int height, float time) {
        int centerX = x + width / 2;
        int centerY = y + height / 2 - 8;
        for (int ring = 0; ring < 4; ring++) {
            int radius = 16 + ring * 15 + (int) ((time * 0.4f + ring * 4) % 8);
            this.ring(g, centerX, centerY, radius, radius, ring == 0 ? AMBER : STROKE_DIM);
        }
        g.fill(centerX - 3, centerY - 3, centerX + 4, centerY + 4, AMBER);
        g.drawCenteredString(this.font, "SEARCHING", centerX, centerY + 48, AMBER);
        g.drawCenteredString(this.font, "OBSERVER BUS", centerX, centerY + 62, TEXT_DIM);
    }

    private void renderTacticalDiagram(GuiGraphics g, int x, int y, int width, int height, List<NearbyObserver> observers, NearbyObserver focusObserver, float time) {
        g.fill(x, y, x + width, y + height, PANEL);
        border(g, x, y, width, height, STROKE_DIM);
        g.drawString(this.font, "VEHICLE INTEGRITY MAP", x + 12, y + 10, CYAN, false);
        g.drawString(this.font, focusObserver == null ? "NO FOCUS" : this.trimToWidth(this.categoryDetailLine(focusObserver), 214), x + width - 224, y + 10, focusObserver == null ? TEXT_DIM : this.healthColor(focusObserver), false);

        int viewX = x + 12;
        int viewY = y + 32;
        int viewW = width - 24;
        int viewH = height - 44;
        g.fill(viewX, viewY, viewX + viewW, viewY + viewH, PANEL_DEEP);
        border(g, viewX, viewY, viewW, viewH, STROKE);
        this.drawGrid(g, viewX + 1, viewY + 1, viewW - 2, viewH - 2, 18, 0x182D5965);

        if (observers.isEmpty()) {
            this.renderRadarSearch(g, viewX, viewY, viewW, viewH, time);
            return;
        }

        this.renderVehicleDamageMap(g, viewX, viewY, viewW, viewH, observers, focusObserver, time);

        this.renderIntegrityHistory(g, viewX + 10, viewY + viewH - 54, viewW - 20, 44, observers, focusObserver);
    }

    private void renderWeaponsTacticalDiagram(GuiGraphics g, int x, int y, int width, int height, List<NearbyObserver> observers, NearbyObserver focusObserver, float time) {
        g.fill(x, y, x + width, y + height, PANEL);
        border(g, x, y, width, height, STROKE_DIM);
        NearbyObserver weaponObserver = this.focusWeaponObserver(observers, focusObserver);
        boolean enabled = this.weaponsTabEnabled(observers);
        g.drawString(this.font, "WEAPONS TACTICAL DIAGRAM", x + 12, y + 10, enabled ? this.weaponsCompatibilityColor() : AMBER, false);
        String titleRight = weaponObserver == null ? "NO WEAPON FOCUS" : this.trimToWidth(this.weaponStatusLine(weaponObserver), 228);
        g.drawString(this.font, titleRight, x + width - this.font.width(titleRight) - 12, y + 10, weaponObserver == null ? TEXT_DIM : this.weaponReadinessColor(weaponObserver.weapon()), false);

        int viewX = x + 12;
        int viewY = y + 32;
        int viewW = width - 24;
        int viewH = height - 44;
        g.fill(viewX, viewY, viewX + viewW, viewY + viewH, PANEL_DEEP);
        border(g, viewX, viewY, viewW, viewH, STROKE);
        this.drawGrid(g, viewX + 1, viewY + 1, viewW - 2, viewH - 2, 18, 0x182D5965);

        if (!enabled || weaponObserver == null) {
            this.renderWeaponsDisabledState(g, viewX, viewY, viewW, viewH, observers, time);
            return;
        }

        this.drawWeaponCannonDiagram(g, viewX, viewY, viewW, viewH, weaponObserver, time);
        this.renderWeaponIntegrityTrace(g, viewX + 10, viewY + viewH - 54, viewW - 20, 44, weaponObserver, time);
    }

    private void renderWeaponsDisabledState(GuiGraphics g, int x, int y, int width, int height, List<NearbyObserver> observers, float time) {
        int cx = x + width / 2;
        int cy = y + height / 2 - 8;
        boolean cbcAvailable = CreateBigCannonsCompat.isAvailable();
        int color = cbcAvailable ? AMBER : RED;
        for (int ring = 0; ring < 5; ring++) {
            int radiusX = 36 + ring * 26 + (int) ((time * 0.45f + ring * 3) % 9);
            int radiusY = 18 + ring * 13;
            this.ring(g, cx, cy, radiusX, radiusY, ring == 0 ? color : STROKE_DIM);
        }
        g.fill(cx - 28, cy - 4, cx + 29, cy + 5, 0xFF111D25);
        border(g, cx - 28, cy - 4, 57, 9, color);
        String top = cbcAvailable ? "NO WEAPONS FEED" : "WEAPON SCREEN DEACTIVATED";
        String bottom = cbcAvailable && !this.hasWeaponsObserver(observers) ? "LINK WEAPONS OBSERVER" : "CBC NOT DETECTED";
        g.drawCenteredString(this.font, top, cx, cy + 30, color);
        g.drawCenteredString(this.font, bottom, cx, cy + 44, TEXT_DIM);
    }

    private void drawWeaponCannonDiagram(GuiGraphics g, int x, int y, int width, int height, NearbyObserver observer, float time) {
        BattleScreenDisplayBE.WeaponSnapshot weapon = observer.weapon();
        boolean autocannon = this.isAutocannon(weapon);
        int centerY = y + height / 2 - 18;
        int left = x + Math.max(34, width / 12);
        int right = x + width - Math.max(34, width / 12);
        int mountX = left + Math.max(34, width / 10);
        int breechX = mountX + Math.max(46, width / 12);
        int chamberX = breechX + Math.max(42, width / 13);
        int muzzleX = right - 18;
        int barrelStart = chamberX + 34;
        int barrelEnd = Math.max(barrelStart + 54, muzzleX - 28);
        int mountColor = this.weaponPartColor(weapon.mountBlocks(), weapon.missingMountBlocks());
        int breechColor = this.weaponPartColor(weapon.breechBlocks(), weapon.missingBreechBlocks());
        int barrelColor = this.weaponPartColor(weapon.barrelBlocks() + weapon.chamberBlocks() + weapon.muzzleBlocks(), weapon.missingBarrelBlocks());
        int loaderColor = this.weaponPartColor(weapon.loaderBlocks(), weapon.missingLoaderBlocks());
        int loadColor = this.weaponLoadColor(weapon);

        this.drawWeaponRangeGrid(g, x, y, width, height, centerY, time, weapon);
        g.fill(left, centerY + 32, right, centerY + 35, 0x88315064);
        g.fill(mountX - 36, centerY + 20, mountX + 42, centerY + 42, 0xFF0F202A);
        border(g, mountX - 36, centerY + 20, 78, 22, mountColor);
        g.fill(mountX - 20, centerY + 3, mountX + 26, centerY + 24, 0xFF132936);
        border(g, mountX - 20, centerY + 3, 46, 21, mountColor);
        this.ring(g, mountX + 2, centerY + 13, 21, 10, mountColor);

        g.fill(breechX - 14, centerY - 18, breechX + 36, centerY + 18, 0xFF101923);
        border(g, breechX - 14, centerY - 18, 50, 36, breechColor);
        g.fill(breechX + 36, centerY - 11, chamberX + 28, centerY + 12, 0xFF102633);
        border(g, breechX + 36, centerY - 11, chamberX - breechX - 8, 23, barrelColor);

        int detectedTubeBlocks = weapon.barrelBlocks() + weapon.muzzleBlocks();
        int segmentCount = Math.max(1, Math.min(12, Math.max(detectedTubeBlocks, 1)));
        int segmentWidth = Math.max(12, (barrelEnd - barrelStart) / segmentCount);
        int missingSegments = Math.min(segmentCount, weapon.missingBarrelBlocks());
        int loadedBarrelSegments = Math.min(segmentCount, weapon.barrelPayloadBlocks());
        for (int segment = 0; segment < segmentCount; segment++) {
            int sx = barrelStart + segment * segmentWidth;
            int ex = segment == segmentCount - 1 ? barrelEnd : sx + segmentWidth - 3;
            int color = detectedTubeBlocks <= 0 ? AMBER : segment >= segmentCount - missingSegments ? RED : barrelColor;
            g.fill(sx, centerY - 8, ex, centerY + 9, 0xFF10202B);
            border(g, sx, centerY - 8, ex - sx, 17, color);
            if (segment < loadedBarrelSegments) {
                g.fill(sx + 3, centerY - 3, ex - 3, centerY + 4, this.weaponLoadColor(weapon));
            }
            if (segment % 2 == 0) {
                g.fill(sx + 2, centerY - 5, ex - 2, centerY - 4, 0x554AD9FF);
            }
        }
        if (weapon.chamberPayloadBlocks() > 0 || weapon.breechPayloadBlocks() > 0) {
            int chamberLoadColor = this.weaponLoadColor(weapon);
            if (weapon.breechPayloadBlocks() > 0) {
                g.fill(breechX - 7, centerY - 10, breechX + 27, centerY + 11, chamberLoadColor);
            }
            if (weapon.chamberPayloadBlocks() > 0) {
                g.fill(breechX + 43, centerY - 6, chamberX + 21, centerY + 7, chamberLoadColor);
            }
        }
        g.fill(barrelEnd, centerY - 13, muzzleX + 18, centerY + 14, 0xFF101923);
        border(g, barrelEnd, centerY - 13, muzzleX + 18 - barrelEnd, 27, weapon.missingBarrelBlocks() > 0 ? RED : barrelColor);

        if (weapon.loaderBlocks() > 0 || weapon.missingLoaderBlocks() > 0) {
            int loaderX = chamberX + 22;
            int loaderY = centerY - 48;
            g.fill(loaderX - 6, loaderY, loaderX + 62, loaderY + 9, 0xFF101923);
            border(g, loaderX - 6, loaderY, 68, 9, loaderColor);
            this.drawLine(g, loaderX + 36, loaderY + 9, chamberX + 28, centerY - 9, loaderColor);
            g.fill(loaderX + 54, loaderY - 4, loaderX + 68, loaderY + 14, weapon.loadedState().equals("LOADING") ? AMBER : 0xFF142632);
            border(g, loaderX + 54, loaderY - 4, 14, 18, loaderColor);
        }
        if (autocannon) {
            this.drawAutocannonFeedOverlay(g, breechX + 18, centerY - 52, weapon, chamberX + 12, centerY - 8);
        }

        int lockColor = weapon.missingBreechBlocks() > 0 ? RED : weapon.loadedState().equals("EMPTY") ? AMBER : GREEN;
        g.fill(breechX - 3, centerY - 28, breechX + 17, centerY - 21, 0xFF081018);
        border(g, breechX - 3, centerY - 28, 20, 7, lockColor);
        g.drawString(this.font, "LOCK", breechX + 22, centerY - 29, lockColor, false);

        if (weapon.loadedState().equals("LOADED") || weapon.loadedState().equals("LOADING")) {
            int traceY = centerY + 1;
            int travel = Math.floorMod((int) (time * 4.0f), Math.max(1, barrelEnd - breechX));
            g.fill(breechX + 4, traceY, barrelEnd, traceY + 2, 0x444AD9FF);
            g.fill(breechX + travel, traceY - 3, breechX + travel + 10, traceY + 5, loadColor);
            int pulse = 8 + (int) ((Math.sin(time * 0.42f) + 1.0f) * 5.0f);
            this.ring(g, chamberX + 8, centerY, pulse, pulse, loadColor);
        }

        this.drawWeaponRecoilGauge(g, left + 14, y + 22, 112, weapon, time);
        if (width >= 420) {
            this.drawWeaponAlertLamps(g, left + 14, y + 50, 146, weapon, time);
        }
        this.drawWeaponPayloadStrip(g, breechX + 4, centerY + 18, Math.max(72, barrelEnd - breechX - 8), weapon, time);
        this.drawWeaponSectionReadouts(g, breechX - 10, centerY + 36, Math.max(160, barrelEnd - breechX + 34), weapon);
        this.drawWeaponStatusPlate(g, x + width - 154, y + 22, 140, 42, observer, weapon);
        if (weapon.missingBlocks() > 0 || weapon.readiness().equals("DAMAGED")) {
            this.drawWeaponDamageSparks(g, breechX, centerY, barrelEnd, weapon, time);
        }
    }

    private void drawWeaponSectionReadouts(GuiGraphics g, int x, int y, int width, BattleScreenDisplayBE.WeaponSnapshot weapon) {
        if (width < 150) {
            return;
        }
        int slot = Math.max(46, width / 3);
        if (this.isAutocannon(weapon)) {
            this.drawSectionReadout(g, x, y, slot - 4, "BREECH", weapon.breechBlocks(), weapon.breechPayloadBlocks());
            this.drawSectionReadout(g, x + slot, y, slot - 4, "FEED", weapon.ammoContainers(), weapon.ammoRounds());
            this.drawSectionReadout(g, x + slot * 2, y, slot - 4, "BARREL", weapon.barrelBlocks() + weapon.muzzleBlocks(), weapon.barrelPayloadBlocks());
            return;
        }
        this.drawSectionReadout(g, x, y, slot - 4, "BREECH", weapon.breechBlocks(), weapon.breechPayloadBlocks());
        this.drawSectionReadout(g, x + slot, y, slot - 4, "CHAMBER", weapon.chamberBlocks(), weapon.chamberPayloadBlocks());
        this.drawSectionReadout(g, x + slot * 2, y, slot - 4, "BARREL", weapon.barrelBlocks() + weapon.muzzleBlocks(), weapon.barrelPayloadBlocks());
    }

    private void drawSectionReadout(GuiGraphics g, int x, int y, int width, String label, int detected, int payload) {
        int color = payload > 0 ? VIOLET : detected > 0 ? GREEN : AMBER;
        g.fill(x, y, x + width, y + 20, 0xAA081018);
        border(g, x, y, width, 20, color);
        g.drawString(this.font, this.trimToWidth(label, width - 8), x + 4, y + 3, TEXT_DIM, false);
        String value = payload > 0 ? "IN " + payload : detected > 0 ? "EMPTY?" : "NONE";
        g.drawString(this.font, this.trimToWidth(value, width - 8), x + 4, y + 11, color, false);
    }

    private void drawWeaponAlertLamps(GuiGraphics g, int x, int y, int width, BattleScreenDisplayBE.WeaponSnapshot weapon, float time) {
        g.fill(x, y, x + width, y + 26, 0xCC081018);
        border(g, x, y, width, 26, STROKE_DIM);
        boolean autocannon = this.isAutocannon(weapon);
        String[] labels = autocannon ? new String[]{ "BRL", "BRCH", "SPR", "MAG" } : new String[]{ "BRL", "BRCH", "MNT", "LDR" };
        int[] present = autocannon
            ? new int[]{ weapon.barrelBlocks() + weapon.chamberBlocks() + weapon.muzzleBlocks(), weapon.breechBlocks(), weapon.recoilBlocks(), weapon.ammoContainers() }
            : new int[]{ weapon.barrelBlocks() + weapon.chamberBlocks() + weapon.muzzleBlocks(), weapon.breechBlocks(), weapon.mountBlocks(), weapon.loaderBlocks() };
        int[] missing = autocannon
            ? new int[]{ weapon.missingBarrelBlocks(), weapon.missingBreechBlocks(), 0, 0 }
            : new int[]{ weapon.missingBarrelBlocks(), weapon.missingBreechBlocks(), weapon.missingMountBlocks(), weapon.missingLoaderBlocks() };
        int slot = Math.max(28, (width - 10) / labels.length);
        for (int i = 0; i < labels.length; i++) {
            int lampX = x + 7 + i * slot;
            int lampColor = this.weaponPartColor(present[i], missing[i]);
            if (missing[i] > 0 && ((int) (time * 5.0f + i) & 1) == 0) {
                lampColor = RED;
            }
            g.fill(lampX, y + 6, lampX + 8, y + 14, lampColor);
            border(g, lampX, y + 6, 8, 8, 0xFF081018);
            g.drawString(this.font, labels[i], lampX + 11, y + 6, missing[i] > 0 ? RED : TEXT_DIM, false);
            if (missing[i] > 0) {
                String count = Integer.toString(missing[i]);
                g.drawString(this.font, count, lampX + 11, y + 16, RED, false);
            }
        }
    }

    private void drawWeaponPayloadStrip(GuiGraphics g, int x, int y, int width, BattleScreenDisplayBE.WeaponSnapshot weapon, float time) {
        int stripWidth = Math.max(48, width);
        g.fill(x, y, x + stripWidth, y + 7, 0xFF081018);
        border(g, x, y, stripWidth, 7, STROKE_DIM);
        if (this.isAutocannon(weapon)) {
            int ammoCapacity = this.weaponAmmoCapacity(weapon);
            if (weapon.ammoCreative()) {
                g.fill(x + 1, y + 1, x + stripWidth - 1, y + 6, VIOLET);
                if (weapon.tracerRounds() > 0) {
                    g.fill(x + stripWidth - Math.min(stripWidth - 2, 10), y + 1, x + stripWidth - 1, y + 6, AMBER);
                }
                int pulseX = x + Math.floorMod((int) (time * 3.0f), Math.max(1, stripWidth - 4));
                g.fill(pulseX, y - 2, pulseX + 4, y + 9, 0xAAE7F3F5);
                return;
            }
            if (ammoCapacity > 0 && weapon.ammoRounds() > 0) {
                int tracerRounds = Math.min(weapon.tracerRounds(), weapon.ammoRounds());
                int mainRounds = Math.max(0, weapon.ammoRounds() - tracerRounds);
                int mainWidth = Math.max(mainRounds > 0 ? 8 : 0, stripWidth * mainRounds / ammoCapacity);
                int tracerWidth = Math.max(tracerRounds > 0 ? 4 : 0, stripWidth * tracerRounds / ammoCapacity);
                if (mainWidth > 0) {
                    g.fill(x + 1, y + 1, x + mainWidth, y + 6, VIOLET);
                }
                if (tracerWidth > 0) {
                    g.fill(x + Math.max(1, mainWidth), y + 1, x + Math.min(stripWidth - 1, mainWidth + tracerWidth), y + 6, AMBER);
                }
                int pulseX = x + Math.floorMod((int) (time * 3.0f), Math.max(1, stripWidth - 4));
                g.fill(pulseX, y - 2, pulseX + 4, y + 9, 0xAAE7F3F5);
                return;
            }
            int emptyColor = weapon.ammoContainers() > 0 ? AMBER : TEXT_DARK;
            g.fill(x + 2, y + 3, x + stripWidth - 2, y + 4, emptyColor);
            return;
        }
        int payload = weapon.projectileBlocks() + weapon.propellantBlocks();
        if (payload > 0) {
            int projectileWidth = weapon.projectileBlocks() > 0 ? Math.max(8, stripWidth * weapon.projectileBlocks() / Math.max(1, payload)) : 0;
            int chargeWidth = Math.max(0, stripWidth - projectileWidth);
            if (projectileWidth > 0) {
                g.fill(x + 1, y + 1, x + projectileWidth, y + 6, VIOLET);
            }
            if (chargeWidth > 0) {
                g.fill(x + projectileWidth, y + 1, x + stripWidth - 1, y + 6, AMBER);
            }
            int pulseX = x + Math.floorMod((int) (time * 3.0f), Math.max(1, stripWidth - 4));
            g.fill(pulseX, y - 2, pulseX + 4, y + 9, 0xAAE7F3F5);
            return;
        }
        int emptyColor = "EMPTY".equals(weapon.loadedState()) ? AMBER : TEXT_DARK;
        g.fill(x + 2, y + 3, x + stripWidth - 2, y + 4, emptyColor);
    }

    private void drawWeaponRangeGrid(GuiGraphics g, int x, int y, int width, int height, int centerY, float time, BattleScreenDisplayBE.WeaponSnapshot weapon) {
        int color = weapon.missingBlocks() > 0 ? 0x66FF5D69 : 0x554AD9FF;
        int sweepX = x + Math.floorMod((int) (time * 2.0f), Math.max(1, width));
        g.fill(sweepX, y + 5, sweepX + 1, y + height - 62, color);
        for (int band = 0; band < 4; band++) {
            int rx = Math.max(38, width / 5 + band * width / 11);
            this.ring(g, x + width / 2, centerY, rx, Math.max(18, rx / 4), band == 0 ? 0x55315064 : 0x22315064);
        }
    }

    private void drawWeaponRecoilGauge(GuiGraphics g, int x, int y, int width, BattleScreenDisplayBE.WeaponSnapshot weapon, float time) {
        int color = weapon.recoilBlocks() > 0 ? GREEN : AMBER;
        int fill = weapon.recoilBlocks() > 0 ? 72 : 34;
        if (weapon.readiness().equals("DAMAGED")) {
            color = RED;
            fill = 88;
        }
        g.fill(x, y, x + width, y + 22, 0xCC081018);
        border(g, x, y, width, 22, STROKE_DIM);
        g.drawString(this.font, "RECOIL", x + 7, y + 7, TEXT_DIM, false);
        int barX = x + 48;
        int barW = width - 58;
        g.fill(barX, y + 8, barX + barW, y + 13, 0xFF101B24);
        g.fill(barX, y + 8, barX + Math.max(2, barW * fill / 100), y + 13, color);
        if (weapon.loadedState().equals("LOADED")) {
            int pulse = 2 + (int) ((Math.sin(time * 0.5f) + 1.0f) * 2.0f);
            this.ring(g, x + width - 12, y + 11, pulse + 3, pulse + 3, VIOLET);
        }
    }

    private void drawAutocannonFeedOverlay(GuiGraphics g, int x, int y, BattleScreenDisplayBE.WeaponSnapshot weapon, int chamberX, int chamberY) {
        int frameColor = weapon.ammoContainers() > 0 ? GREEN : AMBER;
        int ammoColor = weapon.ammoRounds() > 0 ? VIOLET : frameColor;
        g.fill(x, y, x + 82, y + 18, 0xCC081018);
        border(g, x, y, 82, 18, frameColor);
        g.drawString(this.font, "FEED", x + 5, y + 5, TEXT_DIM, false);
        String ammoText = this.weaponAmmoText(weapon);
        g.drawString(this.font, ammoText, x + 77 - this.font.width(ammoText), y + 5, ammoColor, false);
        if (weapon.tracerRounds() > 0) {
            int tracerW = Math.min(18, 4 + weapon.tracerRounds() * 2);
            g.fill(x + 56, y + 12, x + 56 + tracerW, y + 14, AMBER);
        }
        this.drawLine(g, x + 30, y + 18, chamberX, chamberY, ammoColor);
    }

    private void drawWeaponStatusPlate(GuiGraphics g, int x, int y, int width, int height, NearbyObserver observer, BattleScreenDisplayBE.WeaponSnapshot weapon) {
        int statusColor = this.weaponReadinessColor(weapon);
        g.fill(x, y, x + width, y + height, 0xCC081018);
        border(g, x, y, width, height, statusColor);
        g.drawString(this.font, this.trimToWidth(weapon.readiness(), width - 14), x + 7, y + 7, statusColor, false);
        g.drawString(this.font, this.trimToWidth(weapon.loadedState(), width - 14), x + 7, y + 20, this.weaponLoadColor(weapon), false);
        String integrity = this.isAutocannon(weapon)
                ? "A" + this.weaponAmmoText(weapon) + "  M" + weapon.missingBlocks()
                : Math.round(observer.healthPercent()) + "%  M" + weapon.missingBlocks();
        g.drawString(this.font, integrity, x + width - this.font.width(integrity) - 7, y + height - 11, TEXT_DIM, false);
    }

    private void drawWeaponDamageSparks(GuiGraphics g, int breechX, int centerY, int barrelEnd, BattleScreenDisplayBE.WeaponSnapshot weapon, float time) {
        int sparks = Math.min(18, 5 + weapon.missingBlocks() + this.missingCriticalWeaponParts(weapon));
        int span = Math.max(1, barrelEnd - breechX);
        for (int spark = 0; spark < sparks; spark++) {
            int sx = breechX + Math.floorMod((int) (spark * 37 + time * 5.0f), span);
            int sy = centerY - 22 + Math.floorMod(spark * 17 + (int) time, 45);
            this.drawLine(g, sx, sy, sx + 5, sy - 5, spark % 2 == 0 ? RED : AMBER);
            g.fill(sx + 6, sy - 6, sx + 8, sy - 4, RED);
        }
    }

    private void renderWeaponIntegrityTrace(GuiGraphics g, int x, int y, int width, int height, NearbyObserver observer, float time) {
        BattleScreenDisplayBE.WeaponSnapshot weapon = observer.weapon();
        boolean autocannon = this.isAutocannon(weapon);
        g.fill(x, y, x + width, y + height, 0xCC071018);
        border(g, x, y, width, height, STROKE_DIM);
        String title = autocannon ? "AMMO SUMMARY" : "PAYLOAD SUMMARY";
        String right = autocannon
                ? "TRCR " + weapon.tracerRounds() + " / RPM " + this.weaponFireRateText(weapon)
                : "BRL " + weapon.barrelPayloadBlocks() + " / CH " + weapon.chamberPayloadBlocks() + " / BR " + weapon.breechPayloadBlocks() + " / U " + weapon.unknownPayloadBlocks();
        g.drawString(this.font, title, x + 7, y + 5, TEXT_DIM, false);
        g.drawString(this.font, this.trimToWidth(right, width - 160), x + width - this.font.width(this.trimToWidth(right, width - 160)) - 7, y + 5, this.weaponReadinessColor(weapon), false);

        int metricsY = y + 19;
        int metricsX = x + 8;
        int metricsW = width - 16;
        int gap = 6;
        int columnW = Math.max(72, (metricsW - gap * 2) / 3);
        if (autocannon) {
            this.drawWeaponLoadoutMetric(g, metricsX, metricsY, columnW, "ROUNDS", this.weaponAmmoText(weapon), VIOLET);
            this.drawWeaponLoadoutMetric(g, metricsX + columnW + gap, metricsY, columnW, "BOXES", weapon.ammoCreative() ? "CREATIVE" : Integer.toString(weapon.ammoContainers()), GREEN);
            this.drawWeaponLoadoutMetric(g, metricsX + (columnW + gap) * 2, metricsY, columnW, "MODE", this.weaponFeedModeText(weapon), weapon.autoFireEnabled() ? AMBER : CYAN);
            return;
        }
        this.drawWeaponLoadoutMetric(g, metricsX, metricsY, columnW, "SHELL", Integer.toString(weapon.projectileBlocks()), VIOLET);
        this.drawWeaponLoadoutMetric(g, metricsX + columnW + gap, metricsY, columnW, "CHARGE", Integer.toString(weapon.propellantBlocks()), AMBER);
        this.drawWeaponLoadoutMetric(g, metricsX + (columnW + gap) * 2, metricsY, columnW, "STATE", weapon.loadedState(), this.weaponLoadColor(weapon));
    }

    private void drawWeaponLoadoutMetric(GuiGraphics g, int x, int y, int width, String label, String value, int valueColor) {
        g.fill(x, y, x + width, y + 16, 0xFF0B141C);
        border(g, x, y, width, 16, STROKE_DIM);
        g.drawString(this.font, label, x + 6, y + 4, TEXT_DIM, false);
        String display = this.trimToWidth(value, Math.max(18, width - 42));
        g.drawString(this.font, display, x + width - this.font.width(display) - 6, y + 4, valueColor, false);
    }

    private void renderRadarSearch(GuiGraphics g, int x, int y, int width, int height, float time) {
        int cx = x + width / 2;
        int cy = y + height / 2 - 8;
        int radius = Math.min(width, height) / 3;
        for (int ring = 0; ring < 4; ring++) {
            this.ring(g, cx, cy, radius - ring * 18, radius - ring * 18, ring == 0 ? STROKE : STROKE_DIM);
        }
        double sweep = time * 0.09f;
        this.drawLine(g, cx, cy, cx + (int) (Math.cos(sweep) * radius), cy + (int) (Math.sin(sweep) * radius), CYAN);
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, AMBER);
        g.drawCenteredString(this.font, "NO DISPLAY LINK FEED", cx, cy + radius + 16, AMBER);
        g.drawCenteredString(this.font, "LINK BATTLE OBSERVER -> BATTLE BRIDGE", cx, cy + radius + 30, TEXT_DIM);
    }

    private void renderVehicleDamageMap(GuiGraphics g, int viewX, int viewY, int viewW, int viewH, List<NearbyObserver> observers, NearbyObserver focusObserver, float time) {
        int cx = viewX + viewW / 2;
        int mapTop = viewY + 28;
        int mapBottom = viewY + viewH - 76;
        int mapH = Math.max(150, mapBottom - mapTop);
        int bodyW = Math.min(126, Math.max(82, viewW / 6));
        int bodyH = Math.min(mapH - 8, Math.max(178, mapH));
        int bodyTop = mapTop + (mapH - bodyH) / 2;
        int bodyBottom = bodyTop + bodyH;
        int bodyLeft = cx - bodyW / 2;
        int bodyRight = cx + bodyW / 2;
        int wingW = Math.min(142, Math.max(76, viewW / 6));
        int wingH = Math.min(58, Math.max(38, bodyH / 4));
        int midY = bodyTop + bodyH / 2;

        this.drawTacticalScanEffects(g, viewX, viewY, viewW, viewH, cx, midY, time, observers);
        int radiusX = Math.max(86, Math.min(viewW / 2 - 28, bodyW + wingW + 36));
        int radiusY = Math.max(70, Math.min(viewH / 2 - 44, bodyH / 2 + 28));
        this.ring(g, cx, midY, radiusX, radiusY, 0x55315064);
        this.ring(g, cx, midY, radiusX - 28, Math.max(32, radiusY - 24), 0x33315064);
        this.ring(g, cx, midY, radiusX + 8 + (int) ((Math.sin(time * 0.11f) + 1.0f) * 4.0f), radiusY + 5, this.totalMissing(observers) > 0 ? 0x88FF5D69 : 0x664AD9FF);

        for (int step = 0; step < 30; step += 3) {
            int half = 8 + step * bodyW / 52;
            g.fill(cx - half, bodyTop + step, cx + half, bodyTop + step + 2, 0xFF123244);
        }
        for (int step = 0; step < 28; step += 3) {
            int half = bodyW / 2 + 18 - step * bodyW / 58;
            g.fill(cx - Math.max(8, half), bodyBottom - 28 + step, cx + Math.max(8, half), bodyBottom - 26 + step, 0xFF123244);
        }

        CategorySummary hull = this.categorySummary(DamageObserverSettings.ObserverCategory.HULL, observers);
        CategorySummary lift = this.categorySummary(DamageObserverSettings.ObserverCategory.LIFT, observers);
        CategorySummary power = this.categorySummary(DamageObserverSettings.ObserverCategory.POWER, observers);
        CategorySummary weapons = this.categorySummary(DamageObserverSettings.ObserverCategory.WEAPONS, observers);
        CategorySummary control = this.categorySummary(DamageObserverSettings.ObserverCategory.CONTROL, observers);
        CategorySummary cargo = this.categorySummary(DamageObserverSettings.ObserverCategory.CARGO, observers);
        this.drawDamagedSection(g, bodyLeft, bodyTop + 28, bodyW, bodyH - 54, "HULL", DamageObserverSettings.ObserverCategory.HULL, hull, focusObserver != null && focusObserver.category() == DamageObserverSettings.ObserverCategory.HULL, time);
        this.drawShipRibs(g, bodyLeft, bodyTop + 32, bodyW, bodyH - 62, time);
        this.drawDamagedSection(g, bodyLeft - wingW, midY - wingH / 2, wingW, wingH, "LIFT", DamageObserverSettings.ObserverCategory.LIFT, lift, focusObserver != null && focusObserver.category() == DamageObserverSettings.ObserverCategory.LIFT, time + 1.0f);
        this.drawDamagedSection(g, bodyRight, midY - wingH / 2, wingW, wingH, "LIFT", DamageObserverSettings.ObserverCategory.LIFT, lift, focusObserver != null && focusObserver.category() == DamageObserverSettings.ObserverCategory.LIFT, time + 2.0f);
        this.drawDamagedSection(g, bodyLeft + 14, bodyTop + 42, bodyW - 28, 34, "CTL", DamageObserverSettings.ObserverCategory.CONTROL, control, focusObserver != null && focusObserver.category() == DamageObserverSettings.ObserverCategory.CONTROL, time + 3.0f);
        this.drawDamagedSection(g, bodyLeft + bodyW / 2 - 18, midY - 28, 36, 56, "PWR", DamageObserverSettings.ObserverCategory.POWER, power, focusObserver != null && focusObserver.category() == DamageObserverSettings.ObserverCategory.POWER, time + 4.0f);
        this.drawDamagedSection(g, bodyLeft + 18, bodyBottom - 78, bodyW - 36, 48, "CRG", DamageObserverSettings.ObserverCategory.CARGO, cargo, focusObserver != null && focusObserver.category() == DamageObserverSettings.ObserverCategory.CARGO, time + 5.0f);
        this.drawDamagedSection(g, bodyLeft - wingW - 22, midY - wingH / 2 - 18, 28, 36, "W", DamageObserverSettings.ObserverCategory.WEAPONS, weapons, focusObserver != null && focusObserver.category() == DamageObserverSettings.ObserverCategory.WEAPONS, time + 6.0f);
        this.drawDamagedSection(g, bodyRight + wingW - 6, midY - wingH / 2 - 18, 28, 36, "W", DamageObserverSettings.ObserverCategory.WEAPONS, weapons, focusObserver != null && focusObserver.category() == DamageObserverSettings.ObserverCategory.WEAPONS, time + 7.0f);

        int scanY = bodyTop + 14 + (int) ((bodyH - 28) * ((time * 0.021f) % 1.0f));
        g.fill(bodyLeft - wingW - 24, scanY, bodyRight + wingW + 24, scanY + 1, 0xCC4AD9FF);
        g.fill(bodyLeft - wingW - 10, scanY + 2, bodyRight + wingW + 10, scanY + 3, 0x554AD9FF);

        this.drawObserverPins(g, cx, bodyTop, bodyW, bodyH, wingW, observers, focusObserver, time);
        this.drawObserverCallouts(g, viewX, viewY, viewW, viewH, cx, bodyTop, bodyW, bodyH, wingW, observers, focusObserver, time);
    }

    private void drawTacticalScanEffects(GuiGraphics g, int x, int y, int width, int height, int cx, int cy, float time, List<NearbyObserver> observers) {
        int alertColor = this.totalMissing(observers) > 0 ? RED : CYAN;
        int radiusX = Math.max(92, width / 3);
        int radiusY = Math.max(66, height / 3);
        for (int pulseIndex = 0; pulseIndex < 3; pulseIndex++) {
            int pulse = (int) ((time * 1.65f + pulseIndex * 19) % 46);
            this.ring(g, cx, cy, Math.max(12, radiusX - pulse), Math.max(10, radiusY - pulse / 2), pulseIndex == 0 ? 0x44315064 : 0x22315064);
        }
        double sweep = time * 0.055f;
        for (int trail = 0; trail < 5; trail++) {
            double angle = sweep - trail * 0.16;
            int endX = cx + (int) (Math.cos(angle) * radiusX);
            int endY = cy + (int) (Math.sin(angle) * radiusY);
            int color = trail == 0 ? alertColor : 0x224AD9FF;
            this.drawLine(g, cx, cy, endX, endY, color);
        }
        int bandX = x + Math.floorMod((int) (time * 2.2f), Math.max(1, width));
        g.fill(bandX, y + 4, bandX + 1, y + height - 76, 0x664AD9FF);
        int dataColor = this.totalMissing(observers) > 0 ? 0x88FF5D69 : 0x664AD9FF;
        for (int packet = 0; packet < 18; packet++) {
            int packetX = x + 18 + Math.floorMod((int) (time * 3.0f + packet * 53), Math.max(1, width - 36));
            int packetY = y + 34 + Math.floorMod(packet * 37 + observers.size() * 11, Math.max(1, height - 132));
            g.fill(packetX, packetY, packetX + 1, packetY + 1, packet % 3 == 0 ? dataColor : 0x33315064);
        }
    }

    private void drawObserverCallouts(GuiGraphics g, int viewX, int viewY, int viewW, int viewH, int cx, int bodyTop, int bodyW, int bodyH, int wingW, List<NearbyObserver> observers, NearbyObserver focusObserver, float time) {
        int plateW = Math.min(158, Math.max(124, viewW / 5));
        int laneTop = viewY + 44;
        int laneBottom = viewY + viewH - 106;
        int leftTotal = this.calloutSideCount(observers, false);
        int rightTotal = this.calloutSideCount(observers, true);
        int leftIndex = 0;
        int rightIndex = 0;
        int[] categorySlots = new int[DamageObserverSettings.ObserverCategory.values().length];
        for (int observerIndex = 0; observerIndex < observers.size(); observerIndex++) {
            NearbyObserver observer = observers.get(observerIndex);
            int slot = categorySlots[observer.category().ordinal()]++;
            boolean rightSide = this.calloutOnRight(observer.category(), slot);
            int sideIndex = rightSide ? rightIndex++ : leftIndex++;
            int sideTotal = rightSide ? rightTotal : leftTotal;
            int plateH = observer.category() == DamageObserverSettings.ObserverCategory.CARGO ? 42 : 38;
            int usableHeight = Math.max(0, laneBottom - laneTop - plateH);
            int plateY = laneTop + (sideTotal <= 1 ? usableHeight / 2 : sideIndex * usableHeight / Math.max(1, sideTotal - 1));
            int plateX = rightSide ? viewX + viewW - plateW - 16 : viewX + 16;
            int pinX = this.categoryPinX(observer.category(), cx, bodyW, wingW) + (slot % 3 - 1) * 12;
            int pinY = this.categoryPinY(observer.category(), bodyTop, bodyH) + (slot / 3) * 12;
            boolean selected = focusObserver != null && focusObserver.observerPos().equals(observer.observerPos());
            int color = this.healthColor(observer);
            int connectorX = rightSide ? plateX : plateX + plateW;
            int connectorY = plateY + plateH / 2;
            this.drawLine(g, pinX, pinY, connectorX, connectorY, selected ? color : 0x66315064);
            float packetTravel = (time * 0.08f + observerIndex * 0.17f) % 1.0f;
            int packetX = pinX + (int) ((connectorX - pinX) * packetTravel);
            int packetY = pinY + (int) ((connectorY - pinY) * packetTravel);
            g.fill(packetX - 1, packetY - 1, packetX + 2, packetY + 2, color);
            this.drawObserverZonePlate(g, plateX, plateY, plateW, plateH, observer, selected, time + observerIndex * 3.0f);
        }
    }

    private int calloutSideCount(List<NearbyObserver> observers, boolean rightSide) {
        int count = 0;
        int[] categorySlots = new int[DamageObserverSettings.ObserverCategory.values().length];
        for (NearbyObserver observer : observers) {
            int slot = categorySlots[observer.category().ordinal()]++;
            if (this.calloutOnRight(observer.category(), slot) == rightSide) {
                count++;
            }
        }
        return count;
    }

    private boolean calloutOnRight(DamageObserverSettings.ObserverCategory category, int slot) {
        return switch (category) {
            case WEAPONS, CONTROL, CARGO -> true;
            case HULL, POWER -> slot % 2 == 1;
            case LIFT -> false;
        };
    }

    private void drawVehicleHullFrame(GuiGraphics g, int x, int y, int width, int height, int cx, int midY, int wingW, int wingH) {
        g.fill(x, y, x + width, y + height, 0xFF0E2230);
        border(g, x, y, width, height, 0x884AD9FF);
        g.fill(x - wingW, midY - wingH / 2, x, midY + wingH / 2, 0xFF102532);
        g.fill(x + width, midY - wingH / 2, x + width + wingW, midY + wingH / 2, 0xFF102532);
        border(g, x - wingW, midY - wingH / 2, wingW, wingH, 0x66315064);
        border(g, x + width, midY - wingH / 2, wingW, wingH, 0x66315064);
        for (int rib = 0; rib <= 6; rib++) {
            int ribY = y + 8 + rib * Math.max(1, (height - 16) / 6);
            g.fill(x + 7, ribY, x + width - 7, ribY + 1, rib % 2 == 0 ? 0x554AD9FF : 0x33315064);
        }
        for (int mark = 0; mark < 10; mark++) {
            int half = 5 + mark * 3;
            g.fill(cx - half, y - 24 + mark * 2, cx + half, y - 23 + mark * 2, 0xFF0E2230);
        }
    }

    private void drawObserverZonePlate(GuiGraphics g, int x, int y, int width, int height, NearbyObserver observer, boolean selected, float time) {
        int color = this.healthColor(observer);
        g.fill(x, y, x + width, y + height, observer.missingBlocks() > 0 ? 0xFF1D1218 : 0xFF0B1821);
        g.fill(x + 2, y + 2, x + width - 2, y + 12, 0x22000000 | (this.categoryColor(observer.category()) & 0x00FFFFFF));
        border(g, x, y, width, height, selected ? TEXT : color);
        g.drawString(this.font, this.trimToWidth(observer.name(), width - 44), x + 6, y + 5, TEXT, false);
        String health = Math.round(observer.healthPercent()) + "%";
        g.drawString(this.font, health, x + width - this.font.width(health) - 6, y + 5, color, false);
        String line = observer.category() == DamageObserverSettings.ObserverCategory.CARGO
                ? this.compactCargoText(observer)
                : observer.category().getDisplayName() + "  M" + observer.missingBlocks() + "/" + observer.baselineBlocks();
        g.drawString(this.font, this.trimToWidth(line, width - 12), x + 6, y + 17, color, false);
        int barX = x + 6;
        int barY = y + height - 8;
        int barW = width - 12;
        g.fill(barX, barY, barX + barW, barY + 4, 0xFF071018);
        g.fill(barX, barY, barX + Math.max(2, (int) (barW * observer.healthPercent() / 100.0)), barY + 4, color);
        if (observer.missingBlocks() > 0) {
            int cracks = Math.min(5, observer.missingBlocks());
            for (int crack = 0; crack < cracks; crack++) {
                int sx = x + 10 + Math.floorMod(crack * 19 + observer.category().ordinal() * 9, Math.max(1, width - 22));
                int sy = y + 15 + Math.floorMod(crack * 11 + observer.category().ordinal() * 5, Math.max(1, height - 24));
                this.drawLine(g, sx, sy, Math.min(x + width - 5, sx + 9), Math.max(y + 4, sy - 5), RED);
            }
            if (((int) (time * 4.0f)) % 2 == 0) {
                g.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0x20FF1C2E);
            }
        }
    }

    private int zonePlateX(DamageObserverSettings.ObserverCategory category, int cx, int bodyW, int wingW, int width, int slot) {
        int offset = (slot % 2) * 14;
        return switch (category) {
            case LIFT -> cx - bodyW / 2 - wingW - width / 2 + offset;
            case WEAPONS, CARGO -> cx + bodyW / 2 + wingW / 3 - offset;
            default -> cx - width / 2 + offset;
        };
    }

    private int zonePlateY(DamageObserverSettings.ObserverCategory category, int bodyTop, int bodyH, int height, int slot) {
        int row = slot / 2;
        return switch (category) {
            case WEAPONS -> bodyTop + 2 + row * (height + 6);
            case CONTROL -> bodyTop + 38 + row * (height + 6);
            case POWER -> bodyTop + bodyH / 2 - height / 2 + row * (height + 6);
            case CARGO -> bodyTop + bodyH / 2 + 42 + row * (height + 6);
            case LIFT -> bodyTop + bodyH / 2 - height / 2 + row * (height + 6);
            case HULL -> bodyTop + bodyH - height - 18 - row * (height + 6);
        };
    }

    private void drawDamagedSection(GuiGraphics g, int x, int y, int width, int height, String label, DamageObserverSettings.ObserverCategory category, CategorySummary summary, boolean selected, float time) {
        int categoryColor = this.categoryColor(category);
        int color = summary.count() == 0 ? STROKE_DIM : this.healthColor(summary.healthPercent(), summary.missingBlocks());
        int fillColor = summary.count() == 0 ? 0xFF0A131B : 0xFF102633;
        g.fill(x, y, x + width, y + height, fillColor);
        g.fill(x + 2, y + 2, x + width - 2, Math.max(y + 4, y + height / 3), 0x22000000 | (categoryColor & 0x00FFFFFF));
        border(g, x, y, width, height, selected ? TEXT : color);
        for (int line = y + 8; line < y + height - 4; line += 10) {
            g.fill(x + 3, line, x + width - 3, line + 1, 0x332D5965);
        }
        int barW = Math.max(8, width - 10);
        int barFill = summary.count() == 0 ? 0 : Math.max(2, (int) (barW * summary.healthPercent() / 100.0));
        g.fill(x + 5, y + height - 8, x + 5 + barW, y + height - 4, 0xFF071018);
        g.fill(x + 5, y + height - 8, x + 5 + barFill, y + height - 4, color);
        g.drawString(this.font, this.trimToWidth(label, width - 10), x + 5, y + 5, summary.count() == 0 ? TEXT_DARK : TEXT, false);
        if (summary.count() > 0 && width > 44) {
            String health = Math.round(summary.healthPercent()) + "%";
            g.drawString(this.font, health, x + width - this.font.width(health) - 5, y + 5, color, false);
        }
        if (summary.missingBlocks() <= 0) {
            return;
        }
        if (((int) (time * 5.0f + category.ordinal())) % 2 == 0) {
            g.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0x22FF1C2E);
        }
        int crackCount = Math.min(7, 2 + summary.missingBlocks() / 3);
        for (int crack = 0; crack < crackCount; crack++) {
            int sx = x + 6 + Math.floorMod(crack * 17 + category.ordinal() * 11, Math.max(1, width - 14));
            int sy = y + 12 + Math.floorMod(crack * 13 + category.ordinal() * 7, Math.max(1, height - 22));
            this.drawLine(g, sx, sy, Math.min(x + width - 4, sx + 8), Math.max(y + 4, sy - 6), RED);
            this.drawLine(g, sx + 2, sy, Math.min(x + width - 4, sx + 12), Math.min(y + height - 4, sy + 5), AMBER);
        }
        int sparks = Math.min(12, 3 + summary.missingBlocks());
        for (int spark = 0; spark < sparks; spark++) {
            double sx = Math.sin(time * 0.23f + spark * 12.989 + category.ordinal() * 4.7);
            double sy = Math.cos(time * 0.19f + spark * 7.17 + category.ordinal() * 2.3);
            int px = x + 4 + (int) ((sx * 0.5 + 0.5) * Math.max(1, width - 8));
            int py = y + 8 + (int) ((sy * 0.5 + 0.5) * Math.max(1, height - 14));
            g.fill(px, py, px + 2, py + 2, spark % 2 == 0 ? RED : AMBER);
        }
        if (summary.healthPercent() < 55.0) {
            int pulse = 8 + (int) ((Math.sin(time * 0.33f + category.ordinal()) + 1.0f) * 7.0f);
            this.ring(g, x + width / 2, y + height / 2, pulse, pulse, RED);
        }
    }

    private void drawShipRibs(GuiGraphics g, int x, int y, int width, int height, float time) {
        for (int rib = 0; rib <= 5; rib++) {
            int ribY = y + 8 + rib * Math.max(1, (height - 18) / 5);
            int color = rib % 2 == 0 ? 0x664AD9FF : 0x44315064;
            g.fill(x + 6, ribY, x + width - 6, ribY + 1, color);
        }
        int scanY = y + 4 + (int) ((height - 8) * ((time * 0.018f) % 1.0f));
        g.fill(x + 4, scanY, x + width - 4, scanY + 1, 0xAA4AD9FF);
    }

    private void drawObserverPins(GuiGraphics g, int cx, int bodyTop, int bodyW, int bodyH, int wingW, List<NearbyObserver> observers, NearbyObserver focusObserver, float time) {
        int[] categorySlots = new int[DamageObserverSettings.ObserverCategory.values().length];
        for (int i = 0; i < observers.size(); i++) {
            NearbyObserver observer = observers.get(i);
            int slot = categorySlots[observer.category().ordinal()]++;
            int pinX = this.categoryPinX(observer.category(), cx, bodyW, wingW) + (slot % 3 - 1) * 12;
            int pinY = this.categoryPinY(observer.category(), bodyTop, bodyH) + (slot / 3) * 12;
            boolean selected = focusObserver != null && focusObserver.observerPos().equals(observer.observerPos());
            int color = this.healthColor(observer);
            int anchorX = cx;
            int anchorY = bodyTop + bodyH / 2;
            this.drawLine(g, anchorX, anchorY, pinX, pinY, selected ? color : 0x55315064);
            int half = selected ? 8 : 6;
            g.fill(pinX - half, pinY - half, pinX + half + 1, pinY + half + 1, observer.missingBlocks() > 0 ? 0xFF301116 : 0xFF112835);
            border(g, pinX - half, pinY - half, half * 2 + 1, half * 2 + 1, color);
            g.drawCenteredString(this.font, Integer.toString(i + 1), pinX, pinY - 3, TEXT);
            if (observer.missingBlocks() > 0 || selected) {
                int pulse = 12 + (int) ((Math.sin(time * 0.31f + i) + 1.0f) * (observer.missingBlocks() > 0 ? 7.0f : 3.0f));
                this.ring(g, pinX, pinY, pulse, pulse, color);
                if (observer.missingBlocks() > 0) {
                    g.drawString(this.font, "M" + observer.missingBlocks(), pinX + 10, pinY - 5, color, false);
                }
            }
        }
    }

    private int categoryPinX(DamageObserverSettings.ObserverCategory category, int cx, int bodyW, int wingW) {
        return switch (category) {
            case LIFT -> cx - bodyW / 2 - wingW / 2;
            case WEAPONS -> cx + bodyW / 2 + 42;
            case CARGO -> cx + bodyW / 2 + 32;
            default -> cx;
        };
    }

    private int categoryPinY(DamageObserverSettings.ObserverCategory category, int bodyTop, int bodyH) {
        return switch (category) {
            case WEAPONS -> bodyTop + 22;
            case CONTROL -> bodyTop + 52;
            case POWER -> bodyTop + bodyH / 2;
            case CARGO -> bodyTop + bodyH / 2 + 64;
            case LIFT -> bodyTop + bodyH / 2;
            case HULL -> bodyTop + bodyH - 34;
        };
    }

    private void renderSelectedReadout(GuiGraphics g, int x, int y, int width, NearbyObserver focusObserver, float time) {
        g.fill(x, y, x + width, y + 26, 0xCC0B141B);
        border(g, x, y, width, 26, STROKE_DIM);
        if (focusObserver == null) {
            g.drawString(this.font, "ZONE FOCUS: ACQUIRING", x + 8, y + 9, AMBER, false);
            return;
        }
        int color = this.healthColor(focusObserver);
        g.fill(x + 7, y + 6, x + 11, y + 20, color);
        g.drawString(this.font, this.trimToWidth(focusObserver.name(), width - 150), x + 18, y + 6, TEXT, false);
        g.drawString(this.font, this.trimToWidth(this.categoryDetailLine(focusObserver), width - 148), x + 18, y + 16, color, false);
        String right = focusObserver.category() == DamageObserverSettings.ObserverCategory.CARGO
            ? this.compactCargoText(focusObserver)
            : Math.round(focusObserver.healthPercent()) + "% / M" + focusObserver.missingBlocks() + " / +" + focusObserver.newBlocks();
        g.drawString(this.font, right, x + width - this.font.width(right) - 8, y + 10, TEXT_DIM, false);
        int scanX = x + width - 18 + (int) (Math.sin(time * 0.4f) * 3.0f);
        g.fill(scanX, y + 4, scanX + 1, y + 22, color);
    }

    private void renderObserverConsole(GuiGraphics g, int x, int y, int width, int height, List<NearbyObserver> observers, NearbyObserver focusObserver, float time) {
        g.fill(x, y, x + width, y + height, PANEL);
        border(g, x, y, width, height, STROKE_DIM);
        g.drawString(this.font, "OBSERVER LINK", x + 12, y + 10, GREEN, false);

        int statTop = y + 32;
        this.drawConsoleStat(g, x + 12, statTop, width - 24, "REGIONS", Integer.toString(observers.size()), observers.isEmpty() ? AMBER : GREEN);
        this.drawConsoleStat(g, x + 12, statTop + 31, width - 24, "BREACH BLOCKS", Integer.toString(this.totalMissing(observers)), this.totalMissing(observers) > 0 ? RED : GREEN);

        int barsTop = statTop + 72;
        g.drawString(this.font, "SYSTEM INTEGRITY", x + 12, barsTop, TEXT_DIM, false);
        this.renderCategoryBars(g, x + 12, barsTop + 14, width - 24, 94, observers);

        int focusTop = barsTop + 118;
        g.drawString(this.font, "FOCUS CHANNEL", x + 12, focusTop, TEXT_DIM, false);
        if (focusObserver == null) {
            g.drawString(this.font, "No observer selected", x + 12, focusTop + 18, AMBER, false);
            g.drawString(this.font, "Awaiting Display Link refresh", x + 12, focusTop + 32, TEXT_DIM, false);
        } else {
            int color = this.healthColor(focusObserver);
            int focusHeight = Math.min(100, Math.max(72, height - (focusTop - y) - 74));
            g.fill(x + 12, focusTop + 17, x + width - 12, focusTop + 17 + focusHeight, 0xFF0B141C);
            border(g, x + 12, focusTop + 17, width - 24, focusHeight, color);
            g.drawString(this.font, this.trimToWidth(focusObserver.name(), width - 78), x + 20, focusTop + 24, TEXT, false);
            g.drawString(this.font, this.trimToWidth(this.categoryDetailLine(focusObserver), width - 96), x + 20, focusTop + 38, color, false);
            String detail = focusObserver.category() == DamageObserverSettings.ObserverCategory.CARGO
                    ? "CONTAINERS " + focusObserver.cargoContainers()
                    : focusObserver.category() == DamageObserverSettings.ObserverCategory.WEAPONS
                    ? this.weaponStatusLine(focusObserver)
                    : "PRESENT " + focusObserver.presentBaselineBlocks() + "/" + focusObserver.baselineBlocks() + "  NEW " + focusObserver.newBlocks();
            g.drawString(this.font, this.trimToWidth(detail, width - 44), x + 20, focusTop + 52, TEXT_DIM, false);
            if (focusObserver.category() == DamageObserverSettings.ObserverCategory.CARGO) {
                this.drawFillMeter(g, x + 20, focusTop + 66, width - 46, "ITEM", focusObserver.cargoItems(), focusObserver.cargoItemCapacity(), VIOLET);
                this.drawFillMeter(g, x + 20, focusTop + 78, width - 46, "FLUID", focusObserver.cargoFluidAmount(), focusObserver.cargoFluidCapacity(), CYAN);
                this.drawFillMeter(g, x + 20, focusTop + 90, width - 46, "ENERGY", focusObserver.cargoEnergyStored(), focusObserver.cargoEnergyCapacity(), AMBER);
            } else if (focusObserver.category() == DamageObserverSettings.ObserverCategory.WEAPONS && focusObserver.weapon().detected()) {
                this.drawFillMeter(g, x + 20, focusTop + 66, width - 46, "PARTS", focusObserver.weapon().presentBlocks(), Math.max(1, focusObserver.weapon().baselineBlocks()), color);
                String parts = "BRL " + focusObserver.weapon().barrelBlocks() + "  BRCH " + focusObserver.weapon().breechBlocks() + "  MNT " + focusObserver.weapon().mountBlocks() + "  LDR " + focusObserver.weapon().loaderBlocks();
                g.drawString(this.font, this.trimToWidth(parts, width - 44), x + 20, focusTop + 80, TEXT_DIM, false);
            } else {
                this.drawFillMeter(g, x + 20, focusTop + 68, width - 46, "INTEGRITY", Math.round(focusObserver.healthPercent()), 100, color);
            }
            this.drawSignalBars(g, x + width - 48, focusTop + 29, this.linkStrength(focusObserver), color);
        }

        int streamTop = y + height - 64;
        g.drawString(this.font, "EVENT SUMMARY", x + 12, streamTop - 10, TEXT_DIM, false);
        this.drawEventRail(g, x + 12, streamTop + 4, width - 24, 44, observers, focusObserver, time);
    }

    private void renderWeaponDetailConsole(GuiGraphics g, int x, int y, int width, int height, List<NearbyObserver> observers, NearbyObserver focusObserver, float time) {
        g.fill(x, y, x + width, y + height, PANEL);
        border(g, x, y, width, height, STROKE_DIM);
        g.drawString(this.font, "WEAPON LINK", x + 12, y + 10, this.weaponsTabEnabled(observers) ? GREEN : AMBER, false);

        NearbyObserver weaponObserver = this.focusWeaponObserver(observers, focusObserver);
        if (!this.weaponsTabEnabled(observers) || weaponObserver == null) {
            int top = y + 42;
            this.drawConsoleStat(g, x + 12, top, width - 24, "CBC", CreateBigCannonsCompat.isAvailable() ? "DETECTED" : "ABSENT", CreateBigCannonsCompat.isAvailable() ? GREEN : RED);
            this.drawConsoleStat(g, x + 12, top + 31, width - 24, "WEAPON FEED", this.hasWeaponsObserver(observers) ? "WAITING" : "NONE", this.hasWeaponsObserver(observers) ? AMBER : TEXT_DIM);
            g.drawString(this.font, "STATUS", x + 12, top + 82, TEXT_DIM, false);
            String disabled = CreateBigCannonsCompat.isAvailable() ? "Link a WEAPONS observer" : "Install/load Create Big Cannons";
            g.drawString(this.font, this.trimToWidth(disabled, width - 24), x + 12, top + 100, AMBER, false);
            this.drawEventRail(g, x + 12, y + height - 60, width - 24, 44, observers, focusObserver, time);
            return;
        }

        BattleScreenDisplayBE.WeaponSnapshot weapon = weaponObserver.weapon();
        int statusColor = this.weaponReadinessColor(weapon);
        boolean autocannon = this.isAutocannon(weapon);
        if (height < 360) {
            this.renderCompactWeaponDetailConsole(g, x, y, width, height, observers, weaponObserver, time);
            return;
        }
        int statTop = y + 32;
        this.drawConsoleStat(g, x + 12, statTop, width - 24, "STATUS", weapon.readiness(), statusColor);
        this.drawConsoleStat(g, x + 12, statTop + 31, width - 24, "LOAD", weapon.loadedState(), this.weaponLoadColor(weapon));
        this.drawConsoleStat(g, x + 12, statTop + 62, width - 24, "KIND", this.weaponKindText(weapon), statusColor);

        int focusTop = statTop + 104;
        g.drawString(this.font, "FIRE-CONTROL CHANNEL", x + 12, focusTop, TEXT_DIM, false);
        int plateH = Math.min(128, Math.max(104, height - (focusTop - y) - 116));
        g.fill(x + 12, focusTop + 16, x + width - 12, focusTop + 16 + plateH, 0xFF0B141C);
        border(g, x + 12, focusTop + 16, width - 24, plateH, statusColor);
        g.drawString(this.font, this.trimToWidth(weaponObserver.name(), width - 50), x + 20, focusTop + 24, TEXT, false);
        g.drawString(this.font, this.trimToWidth(this.weaponStatusLine(weaponObserver), width - 50), x + 20, focusTop + 38, statusColor, false);
        String modeLine = weaponObserver.mode() == DamageObserverSettings.WatchMode.SINGLE_BLOCK && weapon.blocks() > 1
                ? "Single Block + inferred cannon chain"
                : weaponObserver.mode().getDisplayName();
        boolean lockCapable = this.supportsAimLock(weapon);
        String previewLine = lockCapable && weapon.autoAim().mountAvailable() ? this.weaponAutoAimLine(weapon) : modeLine;
        int previewColor = lockCapable && weapon.autoAim().mountAvailable() ? this.weaponAutoAimColor(weapon) : TEXT_DIM;
        g.drawString(this.font, this.trimToWidth(previewLine, width - 50), x + 20, focusTop + 52, previewColor, false);
        this.drawFillMeter(g, x + 20, focusTop + 68, width - 46, "PARTS", weapon.presentBlocks(), Math.max(1, weapon.baselineBlocks()), statusColor);
        this.drawWeaponPartLine(g, x + 20, focusTop + 84, width - 46, "BARREL", weapon.barrelBlocks() + weapon.chamberBlocks() + weapon.muzzleBlocks(), weapon.missingBarrelBlocks());
        this.drawWeaponPartLine(g, x + 20, focusTop + 98, width - 46, "BREECH", weapon.breechBlocks(), weapon.missingBreechBlocks());
        this.drawWeaponPartLine(g, x + 20, focusTop + 112, width - 46, "MOUNT", weapon.mountBlocks(), weapon.missingMountBlocks());
        if (plateH > 122) {
            this.drawWeaponPartLine(g, x + 20, focusTop + 126, width - 46, autocannon ? "SPRING" : "LOADER", autocannon ? weapon.recoilBlocks() : weapon.loaderBlocks(), autocannon ? 0 : weapon.missingLoaderBlocks());
        }

        int payloadTop = focusTop + 26 + plateH;
        g.drawString(this.font, autocannon ? "AUTOCANNON FEED" : "PAYLOAD", x + 12, payloadTop, TEXT_DIM, false);
        int payloadY = payloadTop + 15;
        boolean showAutoAimLine = lockCapable && weapon.autoAim().mountAvailable() && payloadY + 49 < y + height - 94;
        if (autocannon) {
            this.drawMiniMeter(g, x + 12, payloadY, width - 24, "AMMO", weapon.ammoCreative() ? this.weaponAmmoCapacity(weapon) : weapon.ammoRounds(), this.weaponAmmoCapacity(weapon), weapon.ammoRounds() > 0 || weapon.ammoCreative() ? VIOLET : TEXT_DARK);
            this.drawMiniMeter(g, x + 12, payloadY + 11, width - 24, "TRCR", weapon.tracerRounds(), Math.max(1, weapon.ammoRounds()), weapon.tracerRounds() > 0 ? AMBER : TEXT_DARK);
            String insideLine = this.weaponFeedBoxText(weapon) + "  RPM " + this.weaponFireRateText(weapon) + "  MODE " + this.weaponFeedModeText(weapon);
            g.drawString(this.font, this.trimToWidth(insideLine, width - 24), x + 12, payloadY + 25, weapon.ammoRounds() > 0 || weapon.ammoCreative() ? VIOLET : TEXT_DIM, false);
            if (showAutoAimLine) {
                g.drawString(this.font, this.trimToWidth(this.weaponAutoAimLine(weapon), width - 24), x + 12, payloadY + 39, this.weaponAutoAimColor(weapon), false);
            }
        } else {
            this.drawMiniMeter(g, x + 12, payloadY, width - 24, "SHELL", weapon.projectileBlocks(), Math.max(1, weapon.projectileBlocks() + weapon.propellantBlocks()), weapon.projectileBlocks() > 0 ? VIOLET : TEXT_DARK);
            this.drawMiniMeter(g, x + 12, payloadY + 11, width - 24, "CHARGE", weapon.propellantBlocks(), Math.max(1, weapon.projectileBlocks() + weapon.propellantBlocks()), weapon.propellantBlocks() > 0 ? AMBER : TEXT_DARK);
            String insideLine = "IN: BRL " + weapon.barrelPayloadBlocks() + "  CH " + weapon.chamberPayloadBlocks() + "  BR " + weapon.breechPayloadBlocks() + "  U " + weapon.unknownPayloadBlocks();
            g.drawString(this.font, this.trimToWidth(insideLine, width - 24), x + 12, payloadY + 25, weapon.inspectedPayloadBlocks() > 0 ? VIOLET : TEXT_DIM, false);
            if (showAutoAimLine) {
                g.drawString(this.font, this.trimToWidth(this.weaponAutoAimLine(weapon), width - 24), x + 12, payloadY + 39, this.weaponAutoAimColor(weapon), false);
            }
        }

        int warningTop = y + height - 88;
        int commandTop = warningTop - 26;
        int payloadBottom = payloadY + (showAutoAimLine ? 52 : 38);
        if (payloadBottom < commandTop) {
            String command = this.weaponCommandStatus(weapon);
            int commandColor = "READY TO FIRE".equals(command) ? GREEN : AMBER;
            g.fill(x + 12, commandTop, x + width - 12, commandTop + 20, 0xFF0B141C);
            border(g, x + 12, commandTop, width - 24, 20, commandColor);
            g.drawString(this.font, this.trimToWidth(command, width - 40), x + 20, commandTop + 6, commandColor, false);
        }
        String warning = this.weaponWarningLine(weapon);
        g.fill(x + 12, warningTop, x + width - 12, warningTop + 20, 0xFF0B141C);
        border(g, x + 12, warningTop, width - 24, 20, this.missingCriticalWeaponParts(weapon) > 0 ? RED : STROKE_DIM);
        g.drawString(this.font, this.trimToWidth(warning, width - 40), x + 20, warningTop + 6, this.missingCriticalWeaponParts(weapon) > 0 ? RED : GREEN, false);

        int streamTop = y + height - 60;
        g.drawString(this.font, "EVENT SUMMARY", x + 12, streamTop - 10, TEXT_DIM, false);
        this.drawEventRail(g, x + 12, streamTop + 4, width - 24, 44, observers, weaponObserver, time);
    }

    private void renderCompactWeaponDetailConsole(GuiGraphics g, int x, int y, int width, int height, List<NearbyObserver> observers, NearbyObserver weaponObserver, float time) {
        BattleScreenDisplayBE.WeaponSnapshot weapon = weaponObserver.weapon();
        int statusColor = this.weaponReadinessColor(weapon);
        boolean autocannon = this.isAutocannon(weapon);
        int plateTop = y + 32;
        g.fill(x + 12, plateTop, x + width - 12, plateTop + 62, 0xFF0B141C);
        border(g, x + 12, plateTop, width - 24, 62, statusColor);
        g.drawString(this.font, this.trimToWidth(weaponObserver.name(), width - 46), x + 20, plateTop + 8, TEXT, false);
        g.drawString(this.font, this.trimToWidth(weapon.readiness() + " / " + weapon.loadedState(), width - 46), x + 20, plateTop + 22, statusColor, false);
        g.drawString(this.font, this.trimToWidth(this.weaponKindText(weapon), width - 46), x + 20, plateTop + 36, TEXT_DIM, false);
        String chain = weaponObserver.mode() == DamageObserverSettings.WatchMode.SINGLE_BLOCK && weapon.blocks() > 1 ? "Inferred chain " + weapon.blocks() : "Parts " + weapon.blocks();
        if (autocannon) {
            chain += " / A" + this.weaponAmmoText(weapon);
        }
        g.drawString(this.font, this.trimToWidth(chain, width - 46), x + 20, plateTop + 50, this.weaponLoadColor(weapon), false);

        int rowsTop = plateTop + 74;
        this.drawWeaponPartLine(g, x + 14, rowsTop, width - 28, "BARREL", weapon.barrelBlocks() + weapon.chamberBlocks() + weapon.muzzleBlocks(), weapon.missingBarrelBlocks());
        this.drawWeaponPartLine(g, x + 14, rowsTop + 14, width - 28, "BREECH", weapon.breechBlocks(), weapon.missingBreechBlocks());
        this.drawWeaponPartLine(g, x + 14, rowsTop + 28, width - 28, "MOUNT", weapon.mountBlocks(), weapon.missingMountBlocks());
        this.drawWeaponPartLine(g, x + 14, rowsTop + 42, width - 28, autocannon ? "SPRING" : "LOADER", autocannon ? weapon.recoilBlocks() : weapon.loaderBlocks(), autocannon ? 0 : weapon.missingLoaderBlocks());

        int streamTop = y + height - 56;
        if (rowsTop + 56 < streamTop) {
            String warning = this.weaponWarningLine(weapon);
            g.drawString(this.font, this.trimToWidth(warning, width - 28), x + 14, streamTop - 12, this.missingCriticalWeaponParts(weapon) > 0 ? RED : GREEN, false);
        }
        this.drawEventRail(g, x + 12, streamTop + 4, width - 24, 44, observers, weaponObserver, time);
    }

    private void drawWeaponPartLine(GuiGraphics g, int x, int y, int width, String label, int present, int missing) {
        int color = missing > 0 ? RED : present > 0 ? GREEN : AMBER;
        g.drawString(this.font, label, x, y, TEXT_DIM, false);
        String value = present + (missing > 0 ? " / M" + missing : " / OK");
        g.drawString(this.font, value, x + width - this.font.width(value), y, color, false);
        int barX = x + 52;
        int barW = Math.max(14, width - 106);
        int capacity = Math.max(1, present + missing);
        g.fill(barX, y + 3, barX + barW, y + 7, 0xFF071018);
        g.fill(barX, y + 3, barX + Math.max(present > 0 ? 2 : 0, barW * present / capacity), y + 7, color);
        if (missing > 0) {
            int missingW = Math.max(2, barW * missing / capacity);
            g.fill(barX + barW - missingW, y + 3, barX + barW, y + 7, RED);
        }
    }

    private void drawConsoleStat(GuiGraphics g, int x, int y, int width, String label, String value, int color) {
        g.fill(x, y, x + width, y + 24, 0xFF0B141C);
        border(g, x, y, width, 24, STROKE_DIM);
        g.drawString(this.font, label, x + 8, y + 8, TEXT_DIM, false);
        g.drawString(this.font, value, x + width - this.font.width(value) - 9, y + 8, color, false);
    }

    private void renderCategoryBars(GuiGraphics g, int x, int y, int width, int height, List<NearbyObserver> observers) {
        int lineY = y;
        for (DamageObserverSettings.ObserverCategory category : DamageObserverSettings.ObserverCategory.values()) {
            CategorySummary summary = this.categorySummary(category, observers);
            int color = summary.count() == 0 ? TEXT_DARK : this.healthColor(summary.healthPercent(), summary.missingBlocks());
            g.drawString(this.font, this.categoryShort(category), x, lineY + 2, color, false);
            int barX = x + 42;
            int barW = width - 74;
            g.fill(barX, lineY + 3, barX + barW, lineY + 9, 0xFF101B24);
            if (summary.count() > 0) {
                g.fill(barX, lineY + 3, barX + Math.max(3, (int) (barW * summary.healthPercent() / 100.0)), lineY + 9, color);
                if (summary.missingBlocks() > 0) {
                    int breachW = Math.max(2, Math.min(barW, barW * summary.missingBlocks() / Math.max(1, summary.baselineBlocks())));
                    g.fill(barX + barW - breachW, lineY + 3, barX + barW, lineY + 9, RED);
                }
            }
            String right = summary.count() == 0 ? "--" : summary.missingBlocks() + "M";
            g.drawString(this.font, right, x + width - this.font.width(right), lineY + 2, color, false);
            lineY += 15;
            if (lineY > y + height - 10) {
                break;
            }
        }
    }

    private void renderAlertRail(GuiGraphics g, int x, int y, int width, int height, List<NearbyObserver> observers, NearbyObserver focusObserver, float time) {
        g.fill(x, y, x + width, y + height, PANEL_DEEP);
        border(g, x, y, width, height, STROKE_DIM);
        int color = observers.isEmpty() ? AMBER : GREEN;
        String message = observers.isEmpty()
                ? "DISPLAY LINK REQUIRED - link Battle Observer source to Battle Bridge target"
                : "OBSERVER BUS ONLINE - " + observers.size() + " linked region" + (observers.size() == 1 ? "" : "s") + (focusObserver == null ? "" : " / focus " + focusObserver.name());
        int ledX = x + 10;
        int pulse = 2 + (int) ((Math.sin(time * 0.38f) + 1.0f) * 2.0f);
        this.ring(g, ledX + 4, y + 12, pulse + 3, pulse + 3, color);
        g.fill(ledX + 2, y + 10, ledX + 7, y + 15, color);
        g.drawString(this.font, this.trimToWidth(message, width - 34), x + 24, y + 8, color, false);
    }

    private void drawEventRail(GuiGraphics g, int x, int y, int width, int height, List<NearbyObserver> observers, NearbyObserver focusObserver, float time) {
        g.fill(x, y, x + width, y + height, 0xFF081018);
        border(g, x, y, width, height, STROKE_DIM);
        if (observers.isEmpty()) {
            g.drawString(this.font, "NO FEED", x + 8, y + 8, AMBER, false);
            return;
        }
        NearbyObserver target = this.priorityObserver(observers, focusObserver);
        if (target == null) {
            return;
        }
        int color = this.healthColor(target);
        String headline = this.eventHeadline(target);
        int pulse = 2 + (int) ((Math.sin(time * 0.42f) + 1.0f) * 2.0f);
        this.ring(g, x + 9, y + 11, pulse + 2, pulse + 2, color);
        g.fill(x + 7, y + 9, x + 12, y + 14, color);
        g.drawString(this.font, this.trimToWidth(headline, width - 26), x + 19, y + 6, color, false);
        if (target.category() == DamageObserverSettings.ObserverCategory.CARGO) {
            this.drawMiniMeter(g, x + 8, y + 18, width - 16, "ITEM", target.cargoItems(), target.cargoItemCapacity(), VIOLET);
            this.drawMiniMeter(g, x + 8, y + 29, width - 16, "FLUID", target.cargoFluidAmount(), target.cargoFluidCapacity(), CYAN);
        } else if (target.category() == DamageObserverSettings.ObserverCategory.WEAPONS && target.weapon().detected()) {
            this.drawMiniMeter(g, x + 8, y + 18, width - 16, "PARTS", target.weapon().presentBlocks(), Math.max(1, target.weapon().baselineBlocks()), color);
            this.drawMiniMeter(g, x + 8, y + 29, width - 16, "MISSING", target.weapon().missingBlocks(), Math.max(1, target.weapon().baselineBlocks()), target.weapon().missingBlocks() > 0 ? RED : GREEN);
        } else {
            this.drawMiniMeter(g, x + 8, y + 18, width - 16, "PRESENT", target.presentBaselineBlocks(), target.baselineBlocks(), color);
            this.drawMiniMeter(g, x + 8, y + 29, width - 16, "MISSING", target.missingBlocks(), Math.max(1, target.baselineBlocks()), target.missingBlocks() > 0 ? RED : GREEN);
        }
    }

    private NearbyObserver priorityObserver(List<NearbyObserver> observers, NearbyObserver focusObserver) {
        if (focusObserver != null && (focusObserver.missingBlocks() > 0 || focusObserver.newBlocks() > 0 || focusObserver.category() == DamageObserverSettings.ObserverCategory.CARGO
            || (focusObserver.category() == DamageObserverSettings.ObserverCategory.WEAPONS && !"READY".equals(focusObserver.weapon().readiness())))) {
            return focusObserver;
        }
        java.util.Optional<NearbyObserver> weaponAlert = observers.stream()
            .filter(observer -> observer.category() == DamageObserverSettings.ObserverCategory.WEAPONS && observer.weapon().detected())
            .max(Comparator
                .comparingInt((NearbyObserver observer) -> this.missingCriticalWeaponParts(observer.weapon()) * 8 + observer.weapon().missingBlocks() * 3)
                .thenComparingInt(observer -> "LOADING".equals(observer.weapon().loadedState()) ? 1 : 0));
        if (weaponAlert.isPresent() && (this.missingCriticalWeaponParts(weaponAlert.get().weapon()) > 0 || !"READY".equals(weaponAlert.get().weapon().readiness()))) {
            return weaponAlert.get();
        }
        return observers.stream()
                .max(Comparator
                        .comparingInt((NearbyObserver observer) -> observer.missingBlocks() * 4 + observer.newBlocks())
                        .thenComparingDouble(observer -> 100.0 - observer.healthPercent())
                        .thenComparingLong(observer -> observer.cargoItems() + observer.cargoFluidAmount() / 1000L + observer.cargoEnergyStored() / 1000L))
                .orElse(focusObserver);
    }

    private String eventHeadline(NearbyObserver observer) {
        if (observer.category() == DamageObserverSettings.ObserverCategory.WEAPONS && observer.weapon().detected()) {
            return "WEAPON " + observer.name() + "  " + observer.weapon().readiness() + " / " + observer.weapon().loadedState() + "  M" + observer.weapon().missingBlocks() + "/" + Math.max(1, observer.weapon().baselineBlocks());
        }
        if (observer.missingBlocks() > 0) {
            return "BREACH " + observer.name() + "  M" + observer.missingBlocks() + "/" + observer.baselineBlocks() + "  +" + observer.newBlocks();
        }
        if (observer.newBlocks() > 0) {
            return "NEW MASS " + observer.name() + "  +" + observer.newBlocks() + " blocks";
        }
        if (observer.category() == DamageObserverSettings.ObserverCategory.CARGO) {
            return "CARGO FLOW " + observer.name() + "  " + this.compactCargoText(observer);
        }
        return "NOMINAL " + observer.name() + "  " + Math.round(observer.healthPercent()) + "% integrity";
    }

    private void renderIntegrityHistory(GuiGraphics g, int x, int y, int width, int height, List<NearbyObserver> observers, NearbyObserver focusObserver) {
        g.fill(x, y, x + width, y + height, 0xCC071018);
        border(g, x, y, width, height, STROKE_DIM);
        g.drawString(this.font, "LIVE INTEGRITY TRACE", x + 7, y + 5, TEXT_DIM, false);
        if (this.telemetryHistory.size() < 2) {
            g.drawString(this.font, "COLLECTING", x + width - 64, y + 5, AMBER, false);
            return;
        }

        int graphLeft = x + 8;
        int graphTop = y + 17;
        int graphWidth = width - 16;
        int graphHeight = height - 22;
        int maxDamage = 1;
        for (TelemetrySample sample : this.telemetryHistory) {
            maxDamage = Math.max(maxDamage, sample.missingBlocks() + sample.newBlocks());
        }

        int previousHealthX = graphLeft;
        int previousHealthY = graphTop + graphHeight;
        int previousDamageX = graphLeft;
        int previousDamageY = graphTop + graphHeight;
        for (int index = 0; index < this.telemetryHistory.size(); index++) {
            TelemetrySample sample = this.telemetryHistory.get(index);
            int sampleX = graphLeft + index * graphWidth / Math.max(1, this.telemetryHistory.size() - 1);
            int healthY = graphTop + graphHeight - (int) (graphHeight * clamp((float) sample.healthPercent(), 0.0f, 100.0f) / 100.0f);
            int damageY = graphTop + graphHeight - (int) (graphHeight * (sample.missingBlocks() + sample.newBlocks()) / (float) maxDamage);
            if (index > 0) {
                this.drawLine(g, previousHealthX, previousHealthY, sampleX, healthY, GREEN);
                this.drawLine(g, previousDamageX, previousDamageY, sampleX, damageY, sample.missingBlocks() > 0 ? RED : AMBER);
            }
            if (sample.missingBlocks() > 0) {
                g.fill(sampleX, damageY, sampleX + 1, graphTop + graphHeight, 0x66FF5D69);
            }
            previousHealthX = sampleX;
            previousHealthY = healthY;
            previousDamageX = sampleX;
            previousDamageY = damageY;
        }

        String latest = observers.isEmpty()
                ? "NO FEED"
                : Math.round(this.overallHealth(observers)) + "%  M" + this.totalMissing(observers) + "  +" + this.totalNew(observers);
        int color = observers.isEmpty() ? AMBER : this.healthColor(this.overallHealth(observers), this.totalMissing(observers));
        g.drawString(this.font, latest, x + width - this.font.width(latest) - 8, y + 5, color, false);
        if (focusObserver != null) {
            g.drawString(this.font, this.trimToWidth(focusObserver.name(), width / 2), x + 8, y + height - 11, this.healthColor(focusObserver), false);
        }
    }

    private void drawMiniMeter(GuiGraphics g, int x, int y, int width, String label, long value, long capacity, int color) {
        int labelWidth = 48;
        String text = capacity > 0 ? this.formatCompactLong(value) + "/" + this.formatCompactLong(capacity) : "--";
        int valueWidth = this.font.width(text);
        int barX = x + labelWidth;
        int barWidth = Math.max(12, width - labelWidth - valueWidth - 8);
        g.drawString(this.font, label, x, y, TEXT_DIM, false);
        g.fill(barX, y + 2, barX + barWidth, y + 7, 0xFF101B24);
        if (capacity > 0) {
            int fill = Math.max(value > 0 ? 2 : 0, (int) Math.min(barWidth, barWidth * value / capacity));
            g.fill(barX, y + 2, barX + fill, y + 7, color);
            g.drawString(this.font, text, x + width - valueWidth, y - 1, TEXT_DIM, false);
        } else {
            g.drawString(this.font, text, x + width - valueWidth, y - 1, TEXT_DARK, false);
        }
    }

    private void drawMetricChip(GuiGraphics g, int x, int y, int width, String label, String value, int color) {
        g.fill(x, y, x + width, y + 32, 0xFF0A121A);
        border(g, x, y, width, 32, color);
        g.drawString(this.font, label, x + 7, y + 6, TEXT_DIM, false);
        g.drawString(this.font, this.trimToWidth(value, width - 14), x + 7, y + 18, color, false);
    }

    private void drawFillMeter(GuiGraphics g, int x, int y, int width, String label, long value, long capacity, int color) {
        int labelW = 42;
        String text = capacity > 0 ? this.formatCompactLong(value) + "/" + this.formatCompactLong(capacity) : "--";
        int valueW = this.font.width(text);
        int barX = x + labelW;
        int barW = Math.max(20, width - labelW - valueW - 8);
        g.drawString(this.font, label, x, y, TEXT_DIM, false);
        g.fill(barX, y + 2, barX + barW, y + 7, 0xFF071018);
        if (capacity > 0) {
            int fill = Math.max(2, (int) Math.min(barW, barW * value / capacity));
            g.fill(barX, y + 2, barX + fill, y + 7, color);
            g.drawString(this.font, text, x + width - valueW, y - 1, TEXT_DIM, false);
        } else {
            g.drawString(this.font, text, x + width - valueW, y - 1, TEXT_DARK, false);
        }
    }

    private void drawSignalBars(GuiGraphics g, int x, int y, float strength, int color) {
        for (int i = 0; i < 4; i++) {
            int h = 4 + i * 3;
            int barColor = strength >= (i + 1) / 4.0f ? color : 0xFF263643;
            g.fill(x + i * 6, y + 13 - h, x + i * 6 + 4, y + 13, barColor);
        }
    }

    private void drawWaveform(GuiGraphics g, int x, int y, int width, int height, float time, int seed, int color) {
        int lastX = x;
        int lastY = y + height / 2;
        for (int px = 1; px < width; px += 3) {
            int nextX = x + px;
            int nextY = y + height / 2 + (int) (Math.sin(time * 0.21f + px * 0.17f + seed) * (height / 2.0f));
            this.drawLine(g, lastX, lastY, nextX, nextY, color);
            lastX = nextX;
            lastY = nextY;
        }
    }

    private void drawGrid(GuiGraphics g, int x, int y, int width, int height, int spacing, int color) {
        for (int gridX = x; gridX <= x + width; gridX += spacing) {
            g.fill(gridX, y, gridX + 1, y + height, color);
        }
        for (int gridY = y; gridY <= y + height; gridY += spacing) {
            g.fill(x, gridY, x + width, gridY + 1, color);
        }
    }

    private void renderBackdrop(GuiGraphics g, float time) {
        g.fillGradient(0, 0, this.width, this.height, BACKDROP_TOP, BACKDROP_BOTTOM);
        int offset = (int) (time * 0.35f) % 28;
        for (int x = -offset; x < this.width; x += 28) {
            g.fill(x, 0, x + 1, this.height, 0x1530525E);
        }
        for (int y = offset - 28; y < this.height; y += 28) {
            g.fill(0, y, this.width, y + 1, 0x1030525E);
        }
        for (int y = 0; y < this.height; y += 4) {
            g.fill(0, y, this.width, y + 1, 0x08000000);
        }
    }

    private void selectZone(int delta) {
        List<NearbyObserver> observers = this.nearbyObservers();
        if (observers.isEmpty()) {
            this.selectedObserverPos = null;
            this.selectedIndex = 0;
            return;
        }
        NearbyObserver focus = this.focusedObserver(observers);
        int current = focus == null ? this.selectedIndex : observers.indexOf(focus);
        this.selectedIndex = Math.floorMod(current + delta, observers.size());
        this.selectedObserverPos = observers.get(this.selectedIndex).observerPos();
    }

    private void selectScreenMode(ScreenMode mode) {
        this.screenMode = mode;
        List<NearbyObserver> observers = this.nearbyObservers();
        if (mode == ScreenMode.WEAPONS) {
            this.selectFirstWeaponObserver(observers);
        }
        this.syncModeButtons(observers);
    }

    private void ensureModeSelection(List<NearbyObserver> observers) {
        if (this.screenMode == ScreenMode.WEAPONS && this.weaponsTabEnabled(observers)) {
            NearbyObserver focus = this.focusedObserver(observers);
            if (focus == null || focus.category() != DamageObserverSettings.ObserverCategory.WEAPONS) {
                this.selectFirstWeaponObserver(observers);
            }
        }
    }

    private void selectFirstWeaponObserver(List<NearbyObserver> observers) {
        for (int i = 0; i < observers.size(); i++) {
            NearbyObserver observer = observers.get(i);
            if (observer.category() == DamageObserverSettings.ObserverCategory.WEAPONS) {
                this.selectedIndex = i;
                this.selectedObserverPos = observer.observerPos();
                return;
            }
        }
    }

    private void recordTelemetrySample(List<NearbyObserver> observers) {
        long tick = this.display.getLevel() == null ? System.currentTimeMillis() / 50L : this.display.getLevel().getGameTime();
        if (tick == this.lastHistoryTick || (this.lastHistoryTick != Long.MIN_VALUE && tick - this.lastHistoryTick < 5L)) {
            return;
        }
        int missing = this.totalMissing(observers);
        int added = this.totalNew(observers);
        double health = this.overallHealth(observers);
        double cargoFill = this.cargoFillPercent(observers);
        int hash = Math.round((float) health * 10.0f);
        hash = hash * 31 + missing;
        hash = hash * 31 + added;
        hash = hash * 31 + Math.round((float) cargoFill);
        hash = hash * 31 + observers.size();
        if (hash == this.lastHistoryHash && !this.telemetryHistory.isEmpty() && tick - this.lastHistoryTick < 20L) {
            return;
        }
        this.telemetryHistory.add(new TelemetrySample(tick, health, missing, added, cargoFill));
        while (this.telemetryHistory.size() > HISTORY_CAPACITY) {
            this.telemetryHistory.remove(0);
        }
        this.lastHistoryTick = tick;
        this.lastHistoryHash = hash;
    }

    private NearbyObserver focusedObserver(List<NearbyObserver> observers) {
        if (observers.isEmpty()) {
            this.selectedIndex = 0;
            this.selectedObserverPos = null;
            return null;
        }
        if (this.selectedObserverPos != null) {
            for (int i = 0; i < observers.size(); i++) {
                NearbyObserver observer = observers.get(i);
                if (observer.observerPos().equals(this.selectedObserverPos)) {
                    this.selectedIndex = i;
                    return observer;
                }
            }
        }
        this.selectedIndex = Math.max(0, Math.min(this.selectedIndex, observers.size() - 1));
        NearbyObserver observer = observers.get(this.selectedIndex);
        this.selectedObserverPos = observer.observerPos();
        return observer;
    }

    private void syncZoneButtons(List<NearbyObserver> observers) {
        boolean active = observers.size() > 1;
        if (this.pipHudButton != null) {
            this.pipHudButton.setMessage(Component.literal(BattleScreenPipHudOverlay.isPinned(this.display) ? "Unpin PiP" : "Pin PiP"));
        }
        if (this.hologramButton != null) {
            this.hologramButton.visible = true;
            this.hologramButton.setMessage(Component.literal(this.display.isHologramEnabled() ? "Holo On" : "Holo Off"));
        }
        if (this.previousZoneButton != null) {
            this.previousZoneButton.active = active;
        }
        if (this.nextZoneButton != null) {
            this.nextZoneButton.active = active;
        }
    }

    private void syncModeButtons(List<NearbyObserver> observers) {
        if (this.damageMapTabButton != null) {
            this.damageMapTabButton.setMessage(Component.literal(this.screenMode == ScreenMode.DAMAGE_MAP ? "[Integrity]" : "Integrity"));
        }
        if (this.weaponsTabButton != null) {
            boolean enabled = this.weaponsTabEnabled(observers);
            String label = enabled ? "Weapons" : "Weapons Off";
            this.weaponsTabButton.setMessage(Component.literal(this.screenMode == ScreenMode.WEAPONS ? "[" + label + "]" : label));
            this.weaponsTabButton.active = true;
        }
        NearbyObserver weaponObserver = this.focusWeaponObserver(observers, this.focusedObserver(observers));
        boolean commandVisible = this.screenMode == ScreenMode.WEAPONS;
        boolean commandActive = commandVisible && this.weaponsTabEnabled(observers) && weaponObserver != null && weaponObserver.weapon().detected();
        boolean autocannonCommand = commandActive && this.isAutocannon(weaponObserver.weapon());
        boolean lockCommand = commandActive && this.supportsAimLock(weaponObserver.weapon());
        if (this.weaponPredictButton != null) {
            this.weaponPredictButton.visible = commandVisible && lockCommand;
            this.weaponPredictButton.setMessage(Component.literal("Linked Ctrl"));
            this.weaponPredictButton.active = lockCommand;
        }
        if (this.weaponHoldButton != null) {
            this.weaponHoldButton.visible = commandVisible && lockCommand;
            if (lockCommand) {
                this.weaponHoldButton.setMessage(Component.literal(weaponObserver.weapon().autoAim().holdEnabled() ? "Keep Lock" : "Drop Lost"));
            }
            this.weaponHoldButton.active = lockCommand && weaponObserver.weapon().missingBlocks() == 0;
        }
        if (this.weaponLockButton != null) {
            this.weaponLockButton.visible = commandVisible && lockCommand;
            if (lockCommand) {
                this.weaponLockButton.setMessage(Component.literal(weaponObserver.weapon().autoAim().targetLocked() ? "Locked" : "Targets"));
            }
            this.weaponLockButton.active = lockCommand && weaponObserver.weapon().missingBlocks() == 0;
        }
        if (this.weaponFireButton != null) {
            this.weaponFireButton.visible = commandVisible;
            this.weaponFireButton.active = commandActive && "READY".equals(weaponObserver.weapon().readiness()) && weaponObserver.weapon().missingBlocks() == 0;
        }
        if (this.weaponAutoFireButton != null) {
            this.weaponAutoFireButton.visible = commandVisible && autocannonCommand;
            if (autocannonCommand) {
                this.weaponAutoFireButton.setMessage(Component.literal(weaponObserver.weapon().autoFireEnabled() ? "Auto Stop" : "Auto Fire"));
            }
            this.weaponAutoFireButton.active = autocannonCommand && weaponObserver.weapon().missingBlocks() == 0;
        }
    }

    private void sendWeaponAction(BattleScreenWeaponActionPacket.Action action) {
        this.sendWeaponAction(action, null);
    }

    private void sendWeaponAction(BattleScreenWeaponActionPacket.Action action, UUID targetId) {
        NearbyObserver weaponObserver = this.focusWeaponObserver(this.nearbyObservers(), this.focusedObserver(this.nearbyObservers()));
        if (weaponObserver == null) {
            return;
        }
        this.sendWeaponAction(weaponObserver.observerPos(), action, targetId, null);
    }

    private void sendWeaponResponse(BlockPos observerPos, float responseMultiplier) {
        this.sendWeaponAction(observerPos, BattleScreenWeaponActionPacket.Action.SET_AUTO_AIM_RESPONSE_MULTIPLIER, null, responseMultiplier);
    }

    private void sendWeaponAction(BlockPos observerPos, BattleScreenWeaponActionPacket.Action action, UUID targetId, Float responseMultiplier) {
        PacketDistributor.sendToServer(new BattleScreenWeaponActionPacket(this.display.getBlockPos(), observerPos, action, targetId, responseMultiplier));
    }

    private void sendHologramToggle() {
        PacketDistributor.sendToServer(new BattleScreenHologramTogglePacket(this.display.getBlockPos()));
    }

    private void togglePipHud() {
        if (BattleScreenPipHudOverlay.isPinned(this.display)) {
            BattleScreenPipHudOverlay.clear();
            if (this.pipHudButton != null) {
                this.pipHudButton.setMessage(Component.literal("Pin PiP"));
            }
            return;
        }
        BattleScreenPipHudOverlay.pin(this.display);
        this.onClose();
    }

    private List<NearbyObserver> nearbyObservers() {
        List<NearbyObserver> observers = new ArrayList<>();
        for (BattleScreenDisplayBE.LinkedObserver observer : this.display.getLinkedObservers()) {
            observers.add(new NearbyObserver(
                    observer.name(),
                    observer.category(),
                    observer.mode(),
                    observer.sourcePos(),
                    observer.anchor(),
                    observer.diameter(),
                    observer.volume(),
                    observer.distance(),
                    observer.healthPercent(),
                    observer.watchedBlocks(),
                    observer.baselineBlocks(),
                    observer.presentBaselineBlocks(),
                    observer.missingBlocks(),
                    observer.newBlocks(),
                    observer.cargoContainers(),
                    observer.cargoItems(),
                    observer.cargoItemCapacity(),
                    observer.cargoFluidAmount(),
                    observer.cargoFluidCapacity(),
                    observer.cargoEnergyStored(),
                    observer.cargoEnergyCapacity(),
                    observer.weapon(),
                    observer.lights()));
        }

        observers.sort(Comparator
                .comparingInt(NearbyObserver::distance)
                .thenComparing(NearbyObserver::name, String.CASE_INSENSITIVE_ORDER));
        return observers;
    }

    private EnumMap<DamageObserverSettings.ObserverCategory, Integer> categoryCounts(List<NearbyObserver> observers) {
        EnumMap<DamageObserverSettings.ObserverCategory, Integer> counts = new EnumMap<>(DamageObserverSettings.ObserverCategory.class);
        for (NearbyObserver observer : observers) {
            counts.merge(observer.category(), 1, Integer::sum);
        }
        return counts;
    }

    private int activeCategoryCount(List<NearbyObserver> observers) {
        int active = 0;
        for (DamageObserverSettings.ObserverCategory category : DamageObserverSettings.ObserverCategory.values()) {
            if (this.categorySummary(category, observers).count() > 0) {
                active++;
            }
        }
        return active;
    }

    private String categoryObserverNames(DamageObserverSettings.ObserverCategory category, List<NearbyObserver> observers, int maxWidth) {
        StringBuilder names = new StringBuilder();
        int added = 0;
        for (NearbyObserver observer : observers) {
            if (observer.category() != category) {
                continue;
            }
            String next = added == 0 ? observer.name() : ", " + observer.name();
            if (this.font.width(names + next) > maxWidth) {
                int remaining = this.categorySummary(category, observers).count() - added;
                if (remaining > 0) {
                    names.append(" +").append(remaining);
                }
                break;
            }
            names.append(next);
            added++;
        }
        return names.isEmpty() ? category.getDisplayName() : names.toString();
    }

    private CategorySummary categorySummary(DamageObserverSettings.ObserverCategory category, List<NearbyObserver> observers) {
        int count = 0;
        int watched = 0;
        int baseline = 0;
        int present = 0;
        int missing = 0;
        int added = 0;
        int containers = 0;
        long items = 0L;
        long itemCapacity = 0L;
        long fluidAmount = 0L;
        long fluidCapacity = 0L;
        long energyStored = 0L;
        long energyCapacity = 0L;
        double healthSum = 0.0;
        for (NearbyObserver observer : observers) {
            if (observer.category() != category) {
                continue;
            }
            count++;
            watched += observer.watchedBlocks();
            baseline += observer.baselineBlocks();
            present += observer.presentBaselineBlocks();
            missing += observer.missingBlocks();
            added += observer.newBlocks();
            containers += observer.cargoContainers();
            items += observer.cargoItems();
            itemCapacity += observer.cargoItemCapacity();
            fluidAmount += observer.cargoFluidAmount();
            fluidCapacity += observer.cargoFluidCapacity();
            energyStored += observer.cargoEnergyStored();
            energyCapacity += observer.cargoEnergyCapacity();
            healthSum += observer.healthPercent();
        }
        double health = count == 0 ? 100.0 : baseline > 0 ? present * 100.0 / baseline : healthSum / count;
        return new CategorySummary(count, watched, baseline, present, missing, added, containers, items, itemCapacity, fluidAmount, fluidCapacity, energyStored, energyCapacity, clamp((float) health, 0.0f, 100.0f));
    }

    private double overallHealth(List<NearbyObserver> observers) {
        if (observers.isEmpty()) {
            return 100.0;
        }
        int baseline = 0;
        int present = 0;
        double sum = 0.0;
        for (NearbyObserver observer : observers) {
            baseline += observer.baselineBlocks();
            present += observer.presentBaselineBlocks();
            sum += observer.healthPercent();
        }
        return baseline > 0 ? clamp((float) (present * 100.0 / baseline), 0.0f, 100.0f) : sum / observers.size();
    }

    private int totalMissing(List<NearbyObserver> observers) {
        int missing = 0;
        for (NearbyObserver observer : observers) {
            missing += observer.missingBlocks();
        }
        return missing;
    }

    private int totalNew(List<NearbyObserver> observers) {
        int added = 0;
        for (NearbyObserver observer : observers) {
            added += observer.newBlocks();
        }
        return added;
    }

    private double cargoFillPercent(List<NearbyObserver> observers) {
        CategorySummary cargo = this.categorySummary(DamageObserverSettings.ObserverCategory.CARGO, observers);
        double total = 0.0;
        int channels = 0;
        if (cargo.itemCapacity() > 0) {
            total += cargo.items() * 100.0 / cargo.itemCapacity();
            channels++;
        }
        if (cargo.fluidCapacity() > 0) {
            total += cargo.fluidAmount() * 100.0 / cargo.fluidCapacity();
            channels++;
        }
        if (cargo.energyCapacity() > 0) {
            total += cargo.energyStored() * 100.0 / cargo.energyCapacity();
            channels++;
        }
        return channels == 0 ? 0.0 : total / channels;
    }

    private String cargoSummaryText(List<NearbyObserver> observers) {
        CategorySummary cargo = this.categorySummary(DamageObserverSettings.ObserverCategory.CARGO, observers);
        if (cargo.count() == 0) {
            return "NO BAY";
        }
        if (cargo.itemCapacity() > 0) {
            return this.formatCompactLong(cargo.items()) + "/" + this.formatCompactLong(cargo.itemCapacity()) + " I";
        }
        if (cargo.fluidCapacity() > 0) {
            return this.formatCompactLong(cargo.fluidAmount()) + "mB";
        }
        if (cargo.energyCapacity() > 0) {
            return this.formatCompactLong(cargo.energyStored()) + "FE";
        }
        return cargo.containers() + " BOX";
    }

    private int cargoFillColor(double percent) {
        if (percent <= 0.0) {
            return TEXT_DIM;
        }
        if (percent >= 85.0) {
            return AMBER;
        }
        return VIOLET;
    }

    private float linkStrength(NearbyObserver observer) {
        return clamp(1.0f - observer.distance() / (float) LINK_REFERENCE_DISTANCE, 0.18f, 1.0f);
    }

    private String categoryShort(DamageObserverSettings.ObserverCategory category) {
        return switch (category) {
            case HULL -> "HUL";
            case LIFT -> "LFT";
            case POWER -> "PWR";
            case WEAPONS -> "WPN";
            case CONTROL -> "CTL";
            case CARGO -> "CRG";
        };
    }

    private int categoryColor(DamageObserverSettings.ObserverCategory category) {
        return switch (category) {
            case HULL -> RED;
            case LIFT -> CYAN;
            case POWER -> AMBER;
            case WEAPONS -> 0xFFFF7F50;
            case CONTROL -> GREEN;
            case CARGO -> VIOLET;
        };
    }

    private int healthColor(NearbyObserver observer) {
        return this.healthColor(observer.healthPercent(), observer.missingBlocks());
    }

    private int healthColor(double healthPercent, int missingBlocks) {
        if (healthPercent >= 85.0 && missingBlocks == 0) {
            return GREEN;
        }
        if (healthPercent >= 55.0) {
            return AMBER;
        }
        return RED;
    }

    private String categoryDetailLine(NearbyObserver observer) {
        String health = Math.round(observer.healthPercent()) + "%";
        return switch (observer.category()) {
            case HULL -> "Hull armor " + health + " / M" + observer.missingBlocks() + "/" + observer.baselineBlocks();
            case LIFT -> "Lift cells " + health + " / M" + observer.missingBlocks() + "/" + observer.baselineBlocks();
            case POWER -> observer.cargoEnergyCapacity() > 0
                    ? "Power " + health + " / " + this.formatCompactLong(observer.cargoEnergyStored()) + "/" + this.formatCompactLong(observer.cargoEnergyCapacity()) + "FE"
                    : "Power grid " + health + " / M" + observer.missingBlocks() + "/" + observer.baselineBlocks();
                case WEAPONS -> observer.weapon().detected()
                    ? this.weaponKindText(observer.weapon()) + " " + observer.weapon().readiness() + " / " + observer.weapon().loadedState() + " / " + observer.weapon().blocks() + " parts / M" + observer.weapon().missingBlocks() + "/" + Math.max(1, observer.weapon().baselineBlocks())
                    : "Weapon mounts " + health + " / M" + observer.missingBlocks() + "/" + observer.baselineBlocks();
            case CONTROL -> "Control nodes " + health + " / M" + observer.missingBlocks() + "/" + observer.baselineBlocks();
            case CARGO -> "Cargo " + observer.cargoContainers() + " boxes / " + this.compactCargoText(observer);
        };
    }

    private String compactCargoText(NearbyObserver observer) {
        String item = "I " + this.formatCompactLong(observer.cargoItems()) + "/" + this.formatCompactLong(observer.cargoItemCapacity());
        String fluid = "F " + this.formatCompactLong(observer.cargoFluidAmount()) + "/" + this.formatCompactLong(observer.cargoFluidCapacity());
        String energy = "E " + this.formatCompactLong(observer.cargoEnergyStored()) + "/" + this.formatCompactLong(observer.cargoEnergyCapacity());
        if (observer.cargoItemCapacity() > 0) {
            return item;
        }
        if (observer.cargoFluidCapacity() > 0) {
            return fluid;
        }
        if (observer.cargoEnergyCapacity() > 0) {
            return energy;
        }
        return item;
    }

    private String weaponKindText(BattleScreenDisplayBE.WeaponSnapshot weapon) {
        return switch (weapon.kind()) {
            case "AUTOCANNON" -> "Autocannon";
            case "DROP_MORTAR" -> "Drop mortar";
            case "MOUNT" -> "Cannon mount";
            case "LOADER" -> "Cannon loader";
            case "BIG_CANNON" -> weapon.material().equals("Unknown") ? "Big cannon" : weapon.material() + " cannon";
            default -> "Weapon";
        };
    }

    private String weaponStatusLine(NearbyObserver observer) {
        BattleScreenDisplayBE.WeaponSnapshot weapon = observer.weapon();
        if (!weapon.detected()) {
            return "No CBC cannon signature";
        }
        if (this.isAutocannon(weapon)) {
            return this.weaponKindText(weapon) + " / " + weapon.readiness() + " / " + weapon.loadedState() + " / A" + this.weaponAmmoText(weapon) + " / " + this.weaponFireRateText(weapon) + "RPM";
        }
        return this.weaponKindText(weapon) + " / " + weapon.readiness() + " / " + weapon.loadedState();
    }

    private String weaponListPrimaryLine(BattleScreenDisplayBE.WeaponSnapshot weapon) {
        if (this.isAutocannon(weapon)) {
            return weapon.readiness() + " / " + weapon.loadedState() + " / A" + this.weaponAmmoText(weapon) + (weapon.autoFireEnabled() ? " / AUTO" : "");
        }
        return weapon.readiness() + " / " + weapon.loadedState() + " / M" + weapon.missingBlocks() + "/" + Math.max(1, weapon.baselineBlocks());
    }

    private String weaponListSecondaryLine(BattleScreenDisplayBE.WeaponSnapshot weapon) {
        if (this.isAutocannon(weapon)) {
            return "BRL " + (weapon.barrelBlocks() + weapon.muzzleBlocks()) + " BR " + weapon.breechBlocks() + " MNT " + weapon.mountBlocks() + " SPR " + weapon.recoilBlocks() + " / MAG " + weapon.ammoContainers();
        }
        return "BRL " + (weapon.barrelBlocks() + weapon.muzzleBlocks()) + " CH " + weapon.chamberBlocks() + " BR " + weapon.breechBlocks() + " MNT " + weapon.mountBlocks() + " / IN " + (weapon.barrelPayloadBlocks() + weapon.chamberPayloadBlocks() + weapon.breechPayloadBlocks() + weapon.unknownPayloadBlocks());
    }

    private boolean isAutocannon(BattleScreenDisplayBE.WeaponSnapshot weapon) {
        return "AUTOCANNON".equals(weapon.kind());
    }

    private boolean supportsAimLock(BattleScreenDisplayBE.WeaponSnapshot weapon) {
        return this.isAutocannon(weapon) || "BIG_CANNON".equals(weapon.kind());
    }

    private float weaponResponseMin(BattleScreenDisplayBE.WeaponSnapshot weapon) {
        return this.isAutocannon(weapon) ? WEAPON_RESPONSE_MIN : BIG_CANNON_RESPONSE_MIN;
    }

    private float weaponResponseMax(BattleScreenDisplayBE.WeaponSnapshot weapon) {
        float base = this.isAutocannon(weapon) ? WEAPON_RESPONSE_MAX : BIG_CANNON_RESPONSE_MAX;
        // ADVANCED PREDICT doubles the response ceiling (autocannon 5x -> 10x, big cannon 1x -> 2x) so the
        // slider can dial the higher governor authority the server unlocks in advanced mode.
        return weapon.autoAim().advancedPredictionEnabled() ? base * ADVANCED_RESPONSE_BOOST : base;
    }

    private String formatWeaponResponse(BattleScreenDisplayBE.WeaponSnapshot weapon, float response) {
        if (this.isAutocannon(weapon)) {
            return String.format(java.util.Locale.ROOT, "%.1fx", response);
        }
        return String.format(java.util.Locale.ROOT, "%.2fx", response);
    }

    private int weaponAmmoCapacity(BattleScreenDisplayBE.WeaponSnapshot weapon) {
        if (weapon.ammoCreative()) {
            return Math.max(1, weapon.ammoCapacity());
        }
        return weapon.ammoCapacity() > 0 ? weapon.ammoCapacity() : Math.max(1, weapon.ammoRounds());
    }

    private String weaponAmmoText(BattleScreenDisplayBE.WeaponSnapshot weapon) {
        if (weapon.ammoCreative()) {
            return weapon.ammoCapacity() > 0 ? "INF/" + weapon.ammoCapacity() : "INF";
        }
        return weapon.ammoCapacity() > 0 ? weapon.ammoRounds() + "/" + weapon.ammoCapacity() : Integer.toString(weapon.ammoRounds());
    }

    private String weaponFeedBoxText(BattleScreenDisplayBE.WeaponSnapshot weapon) {
        return weapon.ammoCreative() ? "BOX CREATIVE" : "BOX " + weapon.ammoContainers();
    }

    private String weaponFireRateText(BattleScreenDisplayBE.WeaponSnapshot weapon) {
        return weapon.fireRateRpm() > 0 ? Integer.toString(weapon.fireRateRpm()) : "--";
    }

    private String weaponFeedModeText(BattleScreenDisplayBE.WeaponSnapshot weapon) {
        return weapon.autoFireEnabled() ? "AUTO" : "SEMI";
    }

    private String weaponAutoAimLine(BattleScreenDisplayBE.WeaponSnapshot weapon) {
        BattleScreenDisplayBE.AutoAimSnapshot autoAim = weapon.autoAim();
        if (!autoAim.mountAvailable()) {
            return "LOCK MOUNT UNKNOWN";
        }
        if (!autoAim.mountRunning()) {
            return "LOCK MOUNT IDLE";
        }
        if (!autoAim.targetPresent()) {
            String status = autoAim.statusText().isEmpty() ? "NO TARGET" : autoAim.statusText();
            return autoAim.targetLocked()
                    ? "LOCK HOLD / " + status
                    : !autoAim.holdEnabled() && !"NO TARGET".equals(status)
                        ? "LOCK OPEN / " + status
                        : autoAim.fixedMount() ? "LOCK FIXED / " + status : "LOCK KINETIC / " + status;
        }
        String leadText = autoAim.predictionActive()
            ? " / LEAD"
            : !autoAim.predictionStatusText().isEmpty()
                ? " / " + autoAim.predictionStatusText()
                : autoAim.predictionEnabled() ? " / PRED" : "";
        return String.format(java.util.Locale.ROOT, "%s %s %.0fm  Y%+.1f  P%+.1f%s",
                autoAim.targetLocked() ? "LOCKED" : "PREVIEW",
                autoAim.targetType(),
                autoAim.targetDistance(),
                autoAim.yawErrorDegrees(),
            autoAim.pitchErrorDegrees(),
            leadText);
    }

    private int weaponAutoAimColor(BattleScreenDisplayBE.WeaponSnapshot weapon) {
        BattleScreenDisplayBE.AutoAimSnapshot autoAim = weapon.autoAim();
        if (!autoAim.mountAvailable() || !autoAim.mountRunning()) {
            return RED;
        }
        if (!autoAim.targetPresent()) {
            return autoAim.targetLocked() ? AMBER : TEXT_DIM;
        }
        return autoAim.targetLocked() ? GREEN : CYAN;
    }

    private NearbyObserver focusWeaponObserver(List<NearbyObserver> observers, NearbyObserver focusObserver) {
        if (focusObserver != null && focusObserver.category() == DamageObserverSettings.ObserverCategory.WEAPONS) {
            return focusObserver;
        }
        for (NearbyObserver observer : observers) {
            if (observer.category() == DamageObserverSettings.ObserverCategory.WEAPONS) {
                return observer;
            }
        }
        return null;
    }

    private int weaponPartColor(int present, int missing) {
        if (missing > 0) {
            return RED;
        }
        if (present > 0) {
            return GREEN;
        }
        return AMBER;
    }

    private int weaponReadinessColor(BattleScreenDisplayBE.WeaponSnapshot weapon) {
        return switch (weapon.readiness()) {
            case "READY" -> GREEN;
            case "LOADING", "UNKNOWN" -> AMBER;
            case "DAMAGED", "OFFLINE" -> RED;
            default -> TEXT_DIM;
        };
    }

    private int weaponLoadColor(BattleScreenDisplayBE.WeaponSnapshot weapon) {
        return switch (weapon.loadedState()) {
            case "LOADED" -> VIOLET;
            case "PARTIAL", "LOADING" -> AMBER;
            case "EMPTY", "UNKNOWN" -> TEXT_DIM;
            default -> TEXT_DARK;
        };
    }

    private int missingCriticalWeaponParts(BattleScreenDisplayBE.WeaponSnapshot weapon) {
        return weapon.missingBarrelBlocks() + weapon.missingBreechBlocks() + weapon.missingMountBlocks();
    }

    private String weaponWarningLine(BattleScreenDisplayBE.WeaponSnapshot weapon) {
        int critical = this.missingCriticalWeaponParts(weapon);
        if (critical > 0) {
            return "CRITICAL PART LOSS  B" + weapon.missingBarrelBlocks() + "  BR" + weapon.missingBreechBlocks() + "  M" + weapon.missingMountBlocks();
        }
        if (this.isAutocannon(weapon)) {
            if (!weapon.ammoCreative() && weapon.ammoContainers() <= 0) {
                return "AUTOCANNON FEED / NO AMMO BOX";
            }
            if (!weapon.ammoCreative() && weapon.ammoRounds() <= 0) {
                return "AUTOCANNON READY / AMMO BOX EMPTY";
            }
            if (weapon.autoFireEnabled()) {
                return weapon.autoAim().blockedReason().isEmpty()
                        ? "AUTO FIRE CHANNEL ACTIVE"
                        : "AUTO FIRE HOLD / " + weapon.autoAim().blockedReason();
            }
        }
        if (weapon.missingLoaderBlocks() > 0) {
            return "LOADER SERVICE REQUIRED  M" + weapon.missingLoaderBlocks();
        }
        if ("EMPTY".equals(weapon.loadedState())) {
            return "STRUCTURE READY / PAYLOAD EMPTY";
        }
        if ("LOADING".equals(weapon.loadedState())) {
            return "LOADER MOTION DETECTED";
        }
        return "WEAPON CHANNEL NOMINAL";
    }

    private String weaponCommandStatus(BattleScreenDisplayBE.WeaponSnapshot weapon) {
        if (!"READY".equals(weapon.readiness())) {
            return "FIRE LOCKED / " + weapon.readiness();
        }
        if (weapon.missingBlocks() > 0 || this.missingCriticalWeaponParts(weapon) > 0) {
            return "FIRE LOCKED / STRUCTURE DAMAGE";
        }
        if (this.isAutocannon(weapon)) {
            if (weapon.autoFireEnabled()) {
                if (!weapon.autoAim().blockedReason().isEmpty()) {
                    return "AUTO HOLD / " + weapon.autoAim().blockedReason();
                }
                return weapon.ammoCreative() || weapon.ammoRounds() > 0 || "LOADED".equals(weapon.loadedState()) ? "AUTO FIRE ENGAGED" : "AUTO HOLD / FEED EMPTY";
            }
            if (!weapon.ammoCreative() && weapon.ammoContainers() > 0 && weapon.ammoRounds() <= 0 && !"LOADED".equals(weapon.loadedState())) {
                return "READY CHECK / AMMO BOX EMPTY";
            }
        }
        if ("EMPTY".equals(weapon.loadedState())) {
            return "READY CHECK / PAYLOAD EMPTY";
        }
        return "READY TO FIRE";
    }

    private String formatCompactLong(long value) {
        if (value >= 1_000_000L) {
            return value / 1_000_000L + "M";
        }
        if (value >= 1_000L) {
            return value / 1_000L + "k";
        }
        return Long.toString(value);
    }

    private float animationTime(float partialTick) {
        Level level = this.display.getLevel();
        if (level != null) {
            return level.getGameTime() + partialTick;
        }
        return (System.currentTimeMillis() % 100000L) / 50.0f;
    }

    private void drawScaledString(GuiGraphics g, String text, int x, int y, int color, int scale) {
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1.0f);
        g.drawString(this.font, text, x / scale, y / scale, color, false);
        g.pose().popPose();
    }

    private String trimToWidth(String text, int maxWidth) {
        String trimmed = this.font.plainSubstrByWidth(text, maxWidth);
        if (trimmed.length() == text.length()) {
            return text;
        }
        return trimmed.substring(0, Math.max(0, trimmed.length() - 1)) + "...";
    }

    private void ring(GuiGraphics g, int cx, int cy, int rx, int ry, int color) {
        if (rx <= 0 || ry <= 0) {
            return;
        }
        for (int degree = 0; degree < 360; degree += 5) {
            double radians = Math.toRadians(degree);
            int x = cx + (int) Math.round(Math.cos(radians) * rx);
            int y = cy + (int) Math.round(Math.sin(radians) * ry);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    private void drawLine(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        int x = x0;
        int y = y0;
        while (true) {
            g.fill(x, y, x + 1, y + 1, color);
            if (x == x1 && y == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y += sy;
            }
        }
    }

    private static int lerp(int a, int b, float t) {
        return a + Math.round((b - a) * t);
    }

    private static int lerpColor(int start, int end, float t) {
        t = clamp(t, 0.0f, 1.0f);
        int a = lerp((start >>> 24) & 0xFF, (end >>> 24) & 0xFF, t);
        int r = lerp((start >>> 16) & 0xFF, (end >>> 16) & 0xFF, t);
        int g = lerp((start >>> 8) & 0xFF, (end >>> 8) & 0xFF, t);
        int b = lerp(start & 0xFF, end & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void border(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private record CategorySummary(
            int count,
            int watchedBlocks,
            int baselineBlocks,
            int presentBaselineBlocks,
            int missingBlocks,
            int newBlocks,
            int containers,
            long items,
            long itemCapacity,
            long fluidAmount,
            long fluidCapacity,
            long energyStored,
            long energyCapacity,
            double healthPercent) {
    }

    private record TelemetrySample(
            long tick,
            double healthPercent,
            int missingBlocks,
            int newBlocks,
            double cargoFillPercent) {
    }

    private enum ScreenMode {
        DAMAGE_MAP,
        WEAPONS,
        LIGHTING
    }

    private enum WeaponTab {
        OVERVIEW("Overview"),
        LOADOUT("Loadout"),
        FIRE_CONTROL("Fire Control");

        private final String label;

        WeaponTab(String label) {
            this.label = label;
        }
    }

    private enum IntegrityTab {
        OVERVIEW("Overview"),
        SHIP_MAP("Ship Map"),
        DETAIL("Detail");

        private final String label;

        IntegrityTab(String label) {
            this.label = label;
        }
    }

    private record WeaponHitTarget(int x, int y, int width, int height, Runnable action) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
        }
    }

    /** One scrollable slot in the lighting channel rail: an observer-group header or a fixture row. */
    private record LightRailRow(String header, BattleLightSnapshot.Entry entry, BlockPos observerPos, int channel) {
        private int height() {
            return this.header != null ? 16 : 34;
        }
    }

    /** A lighting stepper row's bounds + its actions, so hovering it and scrolling nudges the value. */
    private record LightStepperTarget(int x, int y, int width, int height, BlockPos observerPos, long lightPos,
                                      BattleScreenLightActionPacket.Action down, BattleScreenLightActionPacket.Action up) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
        }
    }

    private enum TargetPickerAction {
        CLOSE,
        CLEAR,
        SELECT
    }

    private record ZoneClickTarget(
            int x,
            int y,
            int width,
            int height,
            DamageObserverSettings.ObserverCategory category,
            BlockPos observerPos) {

        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
        }
    }

    private record TargetPickerClickTarget(
            int x,
            int y,
            int width,
            int height,
            TargetPickerAction action,
            UUID targetId) {

        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
        }
    }

    private record WeaponResponseSliderTarget(
            int x,
            int y,
            int width,
            int height,
            BlockPos observerPos,
            boolean active,
            float minResponse,
            float maxResponse) {

        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
        }
    }

    private record NearbyObserver(
            String name,
            DamageObserverSettings.ObserverCategory category,
            DamageObserverSettings.WatchMode mode,
            BlockPos observerPos,
            BlockPos anchor,
            int diameter,
            int volume,
            int distance,
            double healthPercent,
            int watchedBlocks,
            int baselineBlocks,
            int presentBaselineBlocks,
            int missingBlocks,
            int newBlocks,
            int cargoContainers,
            long cargoItems,
            long cargoItemCapacity,
            long cargoFluidAmount,
            long cargoFluidCapacity,
            long cargoEnergyStored,
            long cargoEnergyCapacity,
            BattleScreenDisplayBE.WeaponSnapshot weapon,
            BattleLightSnapshot lights) {
    }
}
