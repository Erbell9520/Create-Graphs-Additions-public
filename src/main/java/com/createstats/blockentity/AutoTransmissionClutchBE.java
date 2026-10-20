package com.createstats.blockentity;

import com.createstats.CreateStats;
import com.createstats.block.AutoTransmissionClutchBlock;
import com.createstats.block.SplitShaftValueBoxTransform;
import com.simibubi.create.content.kinetics.base.IRotate.StressImpact;
import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings;
import net.createmod.catnip.theme.Color;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.List;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class AutoTransmissionClutchBE extends SplitShaftBlockEntity {
    private static final int FALLBACK_CHECK_INTERVAL = 20;
    private static final int RECALC_INTERVAL_DIRTY = 3;
    private static final int MAX_SETTLE_TICKS = 20;
    private static final float MAX_OUTPUT_RPM = 256.0f;
    private static final float STEP_SIZE = 0.12f;
    private static final float MODIFIER_NEUTRAL = 1.0f;
    private static final float MODIFIER_EPSILON = 0.01f;
    private static final int STEP_COOLDOWN_TICKS = 10;
    private static final int DOWN_STEP_COOLDOWN_TICKS = 20;
    private static final float STRESS_SAFETY_MARGIN = 0.95f;

    public enum AlarmState {
        NORMAL, CONCERNING, VERY_CONCERNING
    }

    private AutoTransmissionClutchSettings settings;
    private float currentModifier;
    private float targetModifier;
    private AlarmState alarmState;
    private boolean dirty;
    private int dirtyTimer;
    private int fallbackTimer;
    private int settleTicksRemaining;
    private int stepCooldown;
    private int lastComparatorLevel;
    private Direction lastKnownInputFacing;

    private boolean inModifierChange;
    private boolean inNetworkCallback;
    private boolean pendingModifierRebuild;
    private boolean beingRemoved;

    private float lastStressPercent;
    private float lastStressSU;
    private float lastCapacitySU;
    private boolean lastOverstressed;
    private float lastSourceSpeed;

    public float dialTarget;
    public float dialState;
    public float prevDialState;
    public int dialColor;

    private static final BehaviourType<SettingsValueBox> LOAD_BOX = new BehaviourType<>();
    private static final BehaviourType<SettingsValueBox> MIN_BOX = new BehaviourType<>();
    private static final BehaviourType<SettingsValueBox> MAX_BOX = new BehaviourType<>();

    private static final int[] BAND_HALF_WIDTH = {5, 10, 15};
    private static final int ROW_OFF = BAND_HALF_WIDTH.length;

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        behaviours.add(new SettingsValueBox(
                Component.translatableWithFallback("create_stats.auto_transmission_clutch.box.load", "Target Load"),
                this, new SplitShaftValueBoxTransform(SplitShaftValueBoxTransform.Slot.VARIATOR_LOAD),
                LOAD_BOX, 1, "VariableClutchLoad", 95, 10,
                List.of(Component.translatableWithFallback("create_stats.auto_transmission_clutch.box.tight", "Tight"),
                        Component.translatableWithFallback("create_stats.auto_transmission_clutch.box.normal", "Normal"),
                        Component.translatableWithFallback("create_stats.auto_transmission_clutch.box.wide", "Wide"),
                        Component.translatableWithFallback("create_stats.auto_transmission_clutch.box.off", "Off (1:1)")),
                this::loadBoxSettings,
                (player, v) -> editSettings(s -> {
                    s.setEnabled(v.row() != ROW_OFF);
                    if (v.row() != ROW_OFF) {
                        int half = BAND_HALF_WIDTH[Math.min(v.row(), BAND_HALF_WIDTH.length - 1)];
                        int centre = Math.max(10, v.value());
                        s.setPercentageBand(centre - half, centre + half);
                    }
                }),
                v -> v.row() == ROW_OFF
                        ? Component.translatableWithFallback("create_stats.auto_transmission_clutch.box.off", "Off (1:1)")
                        : Component.literal(bandText(Math.max(10, v.value()),
                                BAND_HALF_WIDTH[Math.min(v.row(), BAND_HALF_WIDTH.length - 1)])),
                this::loadBoxLabel));
        behaviours.add(new SettingsValueBox(
                Component.translatableWithFallback("create_stats.auto_transmission_clutch.box.min", "Min RPM"),
                this, new SplitShaftValueBoxTransform(SplitShaftValueBoxTransform.Slot.VARIATOR_MIN),
                MIN_BOX, 2, "VariableClutchMinRpm", 256, 32,
                List.of(Component.translatableWithFallback("create_stats.auto_transmission_clutch.box.floor", "Floor")),
                () -> new ValueSettings(0, Math.round(settings.getMinimumRPM())),
                (player, v) -> editSettings(s -> s.setMinimumRPM(Math.max(1, v.value()))),
                v -> Component.literal(Math.max(1, v.value()) + " RPM"),
                () -> settings == null ? "" : Math.round(settings.getMinimumRPM()) + " RPM"));
        behaviours.add(new SettingsValueBox(
                Component.translatableWithFallback("create_stats.auto_transmission_clutch.box.max", "Max RPM"),
                this, new SplitShaftValueBoxTransform(SplitShaftValueBoxTransform.Slot.VARIATOR_MAX),
                MAX_BOX, 3, "VariableClutchMaxRpm", 256, 32,
                List.of(Component.translatableWithFallback("create_stats.auto_transmission_clutch.box.cap", "Cap")),
                () -> new ValueSettings(0, Math.round(settings.getMaximumRPM())),
                (player, v) -> editSettings(s -> s.setMaximumRPM(Math.max(1, v.value()))),
                v -> Component.literal(Math.max(1, v.value()) + " RPM"),
                () -> settings == null ? "" : Math.round(settings.getMaximumRPM()) + " RPM"));
    }

    private ValueSettings loadBoxSettings() {
        float lo = settings.getPercentageLowerBound();
        float hi = settings.getPercentageUpperBound();
        int centre = Math.round((lo + hi) / 2f);
        if (!settings.isEnabled()) {
            return new ValueSettings(ROW_OFF, centre);
        }
        float half = (hi - lo) / 2f;
        int row = 0;
        for (int i = 1; i < BAND_HALF_WIDTH.length; i++) {
            if (Math.abs(BAND_HALF_WIDTH[i] - half) < Math.abs(BAND_HALF_WIDTH[row] - half)) {
                row = i;
            }
        }
        return new ValueSettings(row, centre);
    }

    private String loadBoxLabel() {
        if (settings == null) {
            return "";
        }
        if (!settings.isEnabled()) {
            return Component.translatableWithFallback("create_stats.auto_transmission_clutch.box.off", "Off (1:1)").getString();
        }
        if (settings.getLimitMode() == AutoTransmissionClutchSettings.LimitMode.SU) {
            return Math.round(settings.getLowerBound()) + "-" + Math.round(settings.getUpperBound()) + " SU";
        }
        return Math.round(settings.getPercentageLowerBound()) + "-" + Math.round(settings.getPercentageUpperBound()) + "%";
    }

    private static String bandText(int centre, int half) {
        return Math.max(1, centre - half) + "-" + Math.min(100, centre + half) + "%";
    }

    private void editSettings(java.util.function.Consumer<AutoTransmissionClutchSettings> edit) {
        AutoTransmissionClutchSettings next = AutoTransmissionClutchSettings.load(settings.save());
        edit.accept(next);
        setSettings(next);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ").append(Component.translatable("block.create_stats.auto_transmission_clutch")
                .withStyle(ChatFormatting.GRAY)));
        Component state;
        if (!settings.isEnabled()) {
            state = Component.translatable("create_stats.auto_transmission_clutch.display.state.disabled");
        } else if (alarmState == AlarmState.CONCERNING) {
            state = Component.translatable("create_stats.auto_transmission_clutch.display.state.min_rpm");
        } else if (alarmState == AlarmState.VERY_CONCERNING) {
            state = Component.translatable("create_stats.auto_transmission_clutch.display.state.emergency");
        } else if (!isNetworkRunning()) {
            state = Component.translatable("create_stats.auto_transmission_clutch.display.state.idle");
        } else if (isBoosting()) {
            state = Component.translatable("create_stats.auto_transmission_clutch.display.state.boosting");
        } else if (isActive()) {
            state = Component.translatable("create_stats.auto_transmission_clutch.display.state.throttling");
        } else {
            state = Component.translatable("create_stats.auto_transmission_clutch.display.state.pass_through");
        }
        tooltip.add(Component.literal("    ").append(state));
        tooltip.add(Component.literal("    ").append(Component.translatable("create_stats.auto_transmission_clutch.display.io",
                String.format("%.0f", getSourceSpeed()), String.format("%.0f", getTransmittedSpeed()),
                String.format("%.0f", currentModifier * 100.0f))));
        if (isPlayerSneaking) {
            tooltip.add(Component.literal("    ").append(Component.translatable("create_stats.auto_transmission_clutch.display.band",
                    String.format("%.0f", settings.getLowerBound()), String.format("%.0f", settings.getUpperBound()),
                    settings.getLimitMode().getDisplayName(),
                    String.format("%.0f", settings.getMinimumRPM()), String.format("%.0f", settings.getMaximumRPM()))));
        }
        return true;
    }

    public AutoTransmissionClutchBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.settings = new AutoTransmissionClutchSettings();
        this.currentModifier = MODIFIER_NEUTRAL;
        this.targetModifier = MODIFIER_NEUTRAL;
        this.alarmState = AlarmState.NORMAL;
        this.dirty = false;
        this.dirtyTimer = 0;
        this.fallbackTimer = 0;
        this.settleTicksRemaining = 0;
    }

    @Override
    public float getRotationSpeedModifier(Direction face) {
        Direction inputFace = getEffectiveInputFacing();
        if (inputFace != null && face != inputFace) {
            // clamp live, a back driving flywheel can beat the governor by a tick and create breaks us

            float src = Math.abs(getTheoreticalSpeed());
            if (src > 0.0f) {
                return Math.min(currentModifier, getEffectiveMaxRPM() / src);
            }
            return currentModifier;
        }
        return 1.0f;
    }

    private Direction getEffectiveInputFacing() {
        if (hasSource()) {
            Direction live = getSourceFacing();
            if (live != null) {
                return live;
            }
        }
        return lastKnownInputFacing;
    }

    @Override
    public void tick() {
        if (level != null && !level.isClientSide() && !isLiveClutch()) return;
        super.tick();

        prevDialState = dialState;
        dialState += (dialTarget - dialState) * 0.125f;
        if (dialState > 1.0f && level != null && level.random.nextFloat() < 0.5f) {
            dialState -= (dialState - 1.0f) * level.random.nextFloat();
        }

        if (level == null || level.isClientSide()) return;

        processPendingModifierRebuild();

        if (!hasSource()) {
            boolean wasActive = currentModifier != MODIFIER_NEUTRAL || targetModifier != MODIFIER_NEUTRAL
                    || lastKnownInputFacing != null || alarmState != AlarmState.NORMAL;
            if (wasActive) {
                currentModifier = MODIFIER_NEUTRAL;
                targetModifier = MODIFIER_NEUTRAL;
                alarmState = AlarmState.NORMAL;
                lastKnownInputFacing = null;
                dialTarget = 0.0f;
                setChanged();
                sendData();
            }
            updateBlockStateVisuals();
            updateComparatorOutput();
            return;
        }

        if (hasSource()) {
            Direction liveInput = getSourceFacing();
            if (liveInput != null) {
                if (lastKnownInputFacing == null) {
                    lastKnownInputFacing = liveInput;
                } else if (liveInput != lastKnownInputFacing) {
                    lastKnownInputFacing = liveInput;
                    refreshNetworkSnapshot();
                    markDirty();
                }
            }
        }

        if (!settings.isEnabled()) {
            if (currentModifier != MODIFIER_NEUTRAL) {
                float oldMod = currentModifier;
                currentModifier = MODIFIER_NEUTRAL;
                targetModifier = MODIFIER_NEUTRAL;
                alarmState = AlarmState.NORMAL;
                applyModifierChange(oldMod);
            }
            updateBlockStateVisuals();
            updateComparatorOutput();
            return;
        }

        fallbackTimer++;
        boolean shouldRecalc = false;

        if (dirty) {
            dirtyTimer++;
            if (dirtyTimer >= RECALC_INTERVAL_DIRTY) {
                shouldRecalc = true;
                dirtyTimer = 0;
            }
        }

        if (fallbackTimer >= FALLBACK_CHECK_INTERVAL) {
            shouldRecalc = true;
            fallbackTimer = 0;
        }

        if (shouldRecalc) {
            refreshNetworkSnapshot();
            recalculateTarget();
        }

        if (stepCooldown > 0) {
            stepCooldown--;
        }

        float sourceSpeed = getSourceSpeed();
        float maxAllowedModifier = getMaxAllowedModifier(sourceSpeed);
        float clampedCurrent = snapToIntegerRPM(Math.max(0.0f, Math.min(maxAllowedModifier, currentModifier)), sourceSpeed);
        float clampedTarget = snapToIntegerRPM(Math.max(0.0f, Math.min(maxAllowedModifier, targetModifier)), sourceSpeed);
        if (Float.compare(clampedCurrent, currentModifier) != 0 || Float.compare(clampedTarget, targetModifier) != 0) {
            float prev = currentModifier;
            currentModifier = clampedCurrent;
            targetModifier = clampedTarget;
            if (Float.compare(prev, currentModifier) != 0) {
                applyModifierChange(prev);
                stepCooldown = STEP_COOLDOWN_TICKS;
            }
        }

        if (currentModifier != targetModifier) {
            if (stepCooldown <= 0) {
                float prev = currentModifier;
                boolean isDownregulating = currentModifier > targetModifier;

                if (isDownregulating) {
                    currentModifier = targetModifier;
                } else {
                    if (Math.abs(currentModifier - targetModifier) <= STEP_SIZE) {
                        currentModifier = targetModifier;
                    } else {
                        currentModifier += STEP_SIZE;
                    }
                }
                currentModifier = Math.max(0.0f, Math.min(maxAllowedModifier, currentModifier));
                currentModifier = snapToIntegerRPM(currentModifier, sourceSpeed);

                if (prev != currentModifier) {
                    applyModifierChange(prev);
                    stepCooldown = isDownregulating ? DOWN_STEP_COOLDOWN_TICKS : STEP_COOLDOWN_TICKS;
                }

                if (currentModifier == targetModifier) {
                    dirty = false;
                    dirtyTimer = 0;
                }
            }

            if (settleTicksRemaining > 0) {
                settleTicksRemaining--;
            }
        }

        updateBlockStateVisuals();
        updateComparatorOutput();
    }

    private void recalculateTarget() {
        if (!hasNetwork() || (getSpeed() == 0.0f && !isOverStressed())) {
            targetModifier = MODIFIER_NEUTRAL;
            alarmState = AlarmState.NORMAL;
            return;
        }

        float sourceSpeed = Math.abs(getTheoreticalSpeed());
        if (sourceSpeed == 0.0f) {
            targetModifier = MODIFIER_NEUTRAL;
            alarmState = AlarmState.NORMAL;
            return;
        }

        float metric;
        float lower = settings.getLowerBound();
        float upper = settings.getUpperBound();

        if (settings.getLimitMode() == AutoTransmissionClutchSettings.LimitMode.PERCENTAGE) {
            metric = lastStressPercent;
        } else {
            metric = lastStressSU;
        }

        float idealModifier;
        if (metric <= 0.0f) {
            idealModifier = currentModifier;
        } else if (metric > upper && upper > 0.0f) {
            float base = Math.max(currentModifier, MODIFIER_EPSILON);
            idealModifier = base * (upper / metric);
        } else if (metric < lower) {
            float base = Math.max(currentModifier, MODIFIER_EPSILON);
            idealModifier = base * (lower / metric);
        } else {
            idealModifier = currentModifier;
        }

        idealModifier = Math.max(0.0f, Math.min(getMaxAllowedModifier(sourceSpeed), idealModifier));
        idealModifier = snapToIntegerRPM(idealModifier, sourceSpeed);

        CreateStats.LOGGER.debug("[AVC] recalc: metric={} [{}-{}] cur={} ideal={} overStressed={}",
                String.format("%.2f", metric), String.format("%.1f", lower), String.format("%.1f", upper),
                String.format("%.3f", currentModifier), String.format("%.3f", idealModifier), isOverStressed());

        float idealRPM = sourceSpeed * idealModifier;
        float minRPM = settings.getMinimumRPM();

        if (idealRPM >= minRPM) {
            alarmState = AlarmState.NORMAL;
            targetModifier = idealModifier;
        } else if (idealRPM >= minRPM / 2.0f) {
            alarmState = AlarmState.CONCERNING;
            targetModifier = Math.max(0.0f, Math.min(getMaxAllowedModifier(sourceSpeed), minRPM / sourceSpeed));
        } else {
            alarmState = AlarmState.VERY_CONCERNING;
            targetModifier = idealModifier;
        }

        targetModifier = Math.max(0.0f, Math.min(getMaxAllowedModifier(sourceSpeed), targetModifier));
        targetModifier = snapToIntegerRPM(targetModifier, sourceSpeed);

        if (targetModifier != currentModifier) {
            settleTicksRemaining = MAX_SETTLE_TICKS;
        }
    }

    private void markDirty() {
        this.dirty = true;
        this.dirtyTimer = 0;
    }

    public AutoTransmissionClutchSettings getSettings() {
        return this.settings;
    }

    public float getCurrentModifier() {
        return this.currentModifier;
    }

    public float getTargetModifier() {
        return this.targetModifier;
    }

    public AlarmState getAlarmState() {
        return this.alarmState;
    }

    public float getSourceSpeed() {
        return Math.abs(getTheoreticalSpeed());
    }

    public float getTransmittedSpeed() {
        return Math.min(getEffectiveMaxRPM(), getSourceSpeed() * currentModifier);
    }

    private float getMaxAllowedModifier(float sourceSpeed) {
        if (sourceSpeed <= 0.0f) {
            return MODIFIER_NEUTRAL;
        }

        float rpmCap = VariableClutchPlan.rpmCapModifier(getEffectiveMaxRPM(), sourceSpeed);

        if (lastCapacitySU > 0 && lastStressSU > 0 && currentModifier > MODIFIER_EPSILON) {
            float stressCap = currentModifier * (lastCapacitySU / lastStressSU) * STRESS_SAFETY_MARGIN;
            rpmCap = Math.min(rpmCap, stressCap);
        }

        return Math.max(MODIFIER_EPSILON, rpmCap);
    }

    private float snapToIntegerRPM(float modifier, float sourceSpeed) {
        if (sourceSpeed < 1.0f) return modifier;
        float outputRPM = Math.round(sourceSpeed * modifier);
        return outputRPM / sourceSpeed;
    }

    public float getEffectiveMaxRPM() {
        return Math.min(MAX_OUTPUT_RPM, settings.getMaximumRPM());
    }

    public float getLastStressPercent() { return this.lastStressPercent; }
    public float getLastStressSU() { return this.lastStressSU; }
    public float getLastCapacitySU() { return this.lastCapacitySU; }
    public boolean isLastOverstressed() { return this.lastOverstressed; }
    public boolean hasStressNetwork() { return hasNetwork(); }

    public boolean isNetworkRunning() {
        return hasNetwork() && getTheoreticalSpeed() != 0.0f;
    }

    public boolean isActive() {
        return settings.isEnabled() && Math.abs(currentModifier - MODIFIER_NEUTRAL) > MODIFIER_EPSILON;
    }

    public boolean isBoosting() {
        return settings.isEnabled() && currentModifier > MODIFIER_NEUTRAL + MODIFIER_EPSILON;
    }

    public boolean isThrottling() {
        return settings.isEnabled() && currentModifier < MODIFIER_NEUTRAL - MODIFIER_EPSILON;
    }

    public int getAnalogOutputSignal() {
        return Math.max(0, Math.min(15, Math.round(currentModifier * 15.0f)));
    }

    public void setSettings(AutoTransmissionClutchSettings settings) {
        this.settings = settings;
        if (level != null && !level.isClientSide()) {
            refreshNetworkSnapshot();
            markDirty();
            if (!this.settings.isEnabled()) {
                float oldMod = currentModifier;
                currentModifier = MODIFIER_NEUTRAL;
                targetModifier = MODIFIER_NEUTRAL;
                alarmState = AlarmState.NORMAL;
                applyModifierChange(oldMod);
            } else {
                float sourceSpeed = getSourceSpeed();
                if (sourceSpeed > 0.0f) {
                    float maxMod = getMaxAllowedModifier(sourceSpeed);
                    boolean changed = false;
                    float oldMod = currentModifier;
                    if (currentModifier > maxMod) {
                        currentModifier = maxMod;
                        changed = true;
                    }
                    if (targetModifier > maxMod) {
                        targetModifier = maxMod;
                    }
                    if (changed) {
                        applyModifierChange(oldMod);
                    }
                }
            }
        }
        notifyUpdate();
    }

    public void syncNetworkState() {
        if (level == null || level.isClientSide() || !isLiveClutch()) return;
        if (hasNetwork()) {
            getOrCreateNetwork().updateNetwork();
        } else {
            dialTarget = 0.0f;
        }
        refreshNetworkSnapshot();
        updateComparatorOutput();
    }

    private void applyModifierChange(float oldModifier) {
        if (level == null || level.isClientSide() || !isLiveClutch()) return;

        if (inModifierChange) return;
        if (inNetworkCallback) {
            pendingModifierRebuild = true;
            notifyUpdate();
            return;
        }
        performModifierRebuild();
    }

    private void processPendingModifierRebuild() {
        if (pendingModifierRebuild && isLiveClutch()) {
            pendingModifierRebuild = false;
            performModifierRebuild();
        }
    }

    private void performModifierRebuild() {
        inModifierChange = true;
        try {
            detachKinetics();
            attachKinetics();
            syncNetworkState();
            notifyUpdate();
        } finally {
            inModifierChange = false;
        }
    }

    private void updateBlockStateVisuals() {
        if (level == null || level.isClientSide()) return;
        boolean shouldBeActive = isActive();
        boolean currentActive = getBlockState().getValue(AutoTransmissionClutchBlock.ACTIVE);
        AutoTransmissionClutchBlock.VisualState visualState = getVisualState();
        AutoTransmissionClutchBlock.VisualState currentVisualState = getBlockState().getValue(AutoTransmissionClutchBlock.VISUAL_STATE);
        if (currentActive != shouldBeActive || currentVisualState != visualState) {
            level.setBlock(worldPosition,
                    getBlockState()
                            .setValue(AutoTransmissionClutchBlock.ACTIVE, shouldBeActive)
                            .setValue(AutoTransmissionClutchBlock.VISUAL_STATE, visualState),
                    3);
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    private AutoTransmissionClutchBlock.VisualState getVisualState() {
        if (isBoosting()) {
            return AutoTransmissionClutchBlock.VisualState.BOOST;
        }
        if (isThrottling()) {
            return AutoTransmissionClutchBlock.VisualState.THROTTLE;
        }
        return AutoTransmissionClutchBlock.VisualState.NEUTRAL;
    }

    private void updateComparatorOutput() {
        if (level == null || level.isClientSide()) return;
        int newLevel = getAnalogOutputSignal();
        if (newLevel != lastComparatorLevel) {
            lastComparatorLevel = newLevel;
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    private void refreshNetworkSnapshot() {
        if (!hasStressNetwork()) {
            storeSnapshot(0.0f, 0.0f, false);
            return;
        }
        storeSnapshot(this.stress, this.capacity, isOverStressed());
    }

    private void storeSnapshot(float stress, float capacity, boolean overstressed) {
        lastStressSU = stress;
        lastCapacitySU = capacity;
        lastOverstressed = overstressed;
        lastStressPercent = capacity > 0.0f ? (stress / capacity) * 100.0f : 0.0f;
    }

    @Override
    public void updateFromNetwork(float maxStress, float currentStress, int networkSize) {
        super.updateFromNetwork(maxStress, currentStress, networkSize);
        if (!isLiveClutch()) {
            return;
        }
        boolean overstressed = isOverStressed();
        storeSnapshot(currentStress, maxStress, overstressed);
        updateDialTarget(maxStress, currentStress, overstressed);
        updateComparatorOutput();
        markDirty();
        if (level != null && !level.isClientSide()) {
            sendData();
        }
        setChanged();
    }

    @Override
    public void onSpeedChanged(float prevSpeed) {
        inNetworkCallback = true;
        try {
            super.onSpeedChanged(prevSpeed);
            if (!isLiveClutch()) {
                return;
            }
            if (getSpeed() == 0.0f) {
                if (inModifierChange) {
                    return;
                }
                if (isOverStressed()) {
                    markDirty();
                    return;
                }
                // zero speed can be a 1 tick split-shaft blip. don't reset here, chained governors knocked each other to neutral

                markDirty();
                return;
            }

            if (inModifierChange) {
                return;
            }

            float sourceSpeed = getSourceSpeed();
            if (sourceSpeed > 0.0f) {
                float maxMod = getMaxAllowedModifier(sourceSpeed);
                boolean changed = false;
                float oldMod = currentModifier;
                if (currentModifier > maxMod) {
                    currentModifier = maxMod;
                    changed = true;
                }
                if (targetModifier > maxMod) {
                    targetModifier = maxMod;
                }
                if (changed) {
                    applyModifierChange(oldMod);
                }
            }

            markDirty();
            if (level != null && !level.isClientSide() && hasNetwork()) {
                updateDialTarget(capacity, stress, isOverStressed());
                sendData();
                setChanged();
            }
        } finally {
            inNetworkCallback = false;
        }
    }

    @Override
    public void remove() {
        beingRemoved = true;
        super.remove();
    }

    private boolean isLiveClutch() {
        return !beingRemoved
                && !isRemoved()
                && level != null
                && level.getBlockEntity(worldPosition) == this
                && level.getBlockState(worldPosition).getBlock() instanceof AutoTransmissionClutchBlock;
    }

    private void updateDialTarget(float maxStress, float currentStress, boolean overstressed) {
        if (!StressImpact.isEnabled()) {
            dialTarget = 0.0f;
            dialColor = 0;
            return;
        }

        if (overstressed) {
            dialTarget = 1.125f;
        } else if (maxStress == 0.0f) {
            dialTarget = 0.0f;
        } else {
            dialTarget = currentStress / maxStress;
        }

        if (dialTarget <= 0.0f) {
            dialColor = 0;
            return;
        }

        if (dialTarget < 0.5f) {
            dialColor = Color.mixColors(0x00FF00, 0xFFFF00, dialTarget * 2.0f);
        } else if (dialTarget < 1.0f) {
            dialColor = Color.mixColors(0xFFFF00, 0xFF0000, dialTarget * 2.0f - 1.0f);
        } else {
            dialColor = 0xFF0000;
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("TransmissionSettings", settings.save());
        tag.putFloat("CurrentModifier", currentModifier);
        tag.putFloat("TargetModifier", targetModifier);
        tag.putString("AlarmState", alarmState.name());
        tag.putFloat("LastStressPct", lastStressPercent);
        tag.putFloat("LastStressSU", lastStressSU);
        tag.putFloat("LastCapacitySU", lastCapacitySU);
        tag.putBoolean("LastOverstressed", lastOverstressed);
        tag.putFloat("DialTarget", dialTarget);
        tag.putInt("DialColor", dialColor);
        if (lastKnownInputFacing != null) {
            tag.putInt("InputFacing", lastKnownInputFacing.get3DDataValue());
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("TransmissionSettings")) {
            settings = AutoTransmissionClutchSettings.load(tag.getCompound("TransmissionSettings"));
        }
        currentModifier = tag.getFloat("CurrentModifier");
        if (currentModifier == 0.0f && !tag.contains("CurrentModifier")) currentModifier = 1.0f;
        targetModifier = tag.getFloat("TargetModifier");
        if (targetModifier == 0.0f && !tag.contains("TargetModifier")) targetModifier = 1.0f;
        try { alarmState = AlarmState.valueOf(tag.getString("AlarmState")); } catch (Exception e) { alarmState = AlarmState.NORMAL; }
        lastStressPercent = tag.getFloat("LastStressPct");
        lastStressSU = tag.getFloat("LastStressSU");
        lastCapacitySU = tag.getFloat("LastCapacitySU");
        lastOverstressed = tag.getBoolean("LastOverstressed");
        dialTarget = tag.getFloat("DialTarget");
        dialColor = tag.getInt("DialColor");
        if (tag.contains("InputFacing")) {
            lastKnownInputFacing = Direction.from3DDataValue(tag.getInt("InputFacing"));
        }
        lastComparatorLevel = getAnalogOutputSignal();
    }
}
