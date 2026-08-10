package com.createstats.blockentity;

import com.createstats.CreateStats;
import com.createstats.block.AutoTransmissionClutchBlock;
import com.simibubi.create.content.kinetics.base.IRotate.StressImpact;
import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;
import net.createmod.catnip.theme.Color;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
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
    private static final int DOWN_STEP_COOLDOWN_TICKS = 20; // 1 second between downregulation jumps
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

    // True while applyModifierChange is executing. Prevents re-entrant calls from
    // onSpeedChanged (which fires during propagation) and freezes governor logic.
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
        // Orient from the LIVE source facing first so the ratio always lands on the real output,
        // even the instant the source flips to the opposite side (reverse drive).
        Direction inputFace = getEffectiveInputFacing();
        if (inputFace != null && face != inputFace) {
            // [1]
            float src = Math.abs(getTheoreticalSpeed());
            if (src > 0.0f) {
                return Math.min(currentModifier, getEffectiveMaxRPM() / src);
            }
            return currentModifier; // Output face
        }
        return 1.0f; // Input face or unknown
    }

    /** [2] */
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

        // [3]
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

        // [4]
        if (hasSource()) {
            Direction liveInput = getSourceFacing();
            if (liveInput != null) {
                if (lastKnownInputFacing == null) {
                    lastKnownInputFacing = liveInput;
                } else if (liveInput != lastKnownInputFacing) {
                    // [5]
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

        // Decrement step cooldown (prevents flicker accumulation from rapid handleRemoved/handleAdded cycles)
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

        // Step current modifier toward target (gated by cooldown)
        if (currentModifier != targetModifier) {
            if (stepCooldown <= 0) {
                float prev = currentModifier;
                boolean isDownregulating = currentModifier > targetModifier;

                if (isDownregulating) {
                    // [6]
                    currentModifier = targetModifier;
                } else {
                    // Upregulation: smooth stepping (already stable)
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
        // When overstressed, getSpeed() returns 0 even though the network is still running.
        // Skip the early-exit so the metric logic can proportionally reduce the modifier.
        if (!hasNetwork() || (getSpeed() == 0.0f && !isOverStressed())) {
            targetModifier = MODIFIER_NEUTRAL;
            alarmState = AlarmState.NORMAL;
            return;
        }

        // Use theoretical speed (unaffected by overstress flag) to compute the modifier ceiling.
        float sourceSpeed = Math.abs(getTheoreticalSpeed());
        if (sourceSpeed == 0.0f) {
            targetModifier = MODIFIER_NEUTRAL;
            alarmState = AlarmState.NORMAL;
            return;
        }

        // Read current stress metric and configured bounds
        float metric;
        float lower = settings.getLowerBound();
        float upper = settings.getUpperBound();

        if (settings.getLimitMode() == AutoTransmissionClutchSettings.LimitMode.PERCENTAGE) {
            metric = lastStressPercent;
        } else {
            metric = lastStressSU;
        }

        // Dead-band governor with proportional response
        float idealModifier;
        if (metric <= 0.0f) {
            // No/stale stress data, hold current position to avoid oscillation during reconnect cycles
            idealModifier = currentModifier;
        } else if (metric > upper && upper > 0.0f) {
            // Over upper bound, slow down proportionally
            float base = Math.max(currentModifier, MODIFIER_EPSILON);
            idealModifier = base * (upper / metric);
        } else if (metric < lower) {
            // Under lower bound, speed up proportionally
            float base = Math.max(currentModifier, MODIFIER_EPSILON);
            idealModifier = base * (lower / metric);
        } else {
            // Within the dead-band, hold steady
            idealModifier = currentModifier;
        }

        idealModifier = Math.max(0.0f, Math.min(getMaxAllowedModifier(sourceSpeed), idealModifier));
        idealModifier = snapToIntegerRPM(idealModifier, sourceSpeed);

        CreateStats.LOGGER.debug("[AVC] recalc: metric={} [{}-{}] cur={} ideal={} overStressed={}",
                String.format("%.2f", metric), String.format("%.1f", lower), String.format("%.1f", upper),
                String.format("%.3f", currentModifier), String.format("%.3f", idealModifier), isOverStressed());

        // Apply minimum RPM alarm logic
        float idealRPM = sourceSpeed * idealModifier;
        float minRPM = settings.getMinimumRPM();

        if (idealRPM >= minRPM) {
            alarmState = AlarmState.NORMAL;
            targetModifier = idealModifier;
        } else if (idealRPM >= minRPM / 2.0f) {
            // Concerning: clamp to minimum RPM, raise alarm
            alarmState = AlarmState.CONCERNING;
            targetModifier = Math.max(0.0f, Math.min(getMaxAllowedModifier(sourceSpeed), minRPM / sourceSpeed));
        } else {
            // Very concerning: allow emergency underspeed
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
        // Use floor to guarantee sourceSpeed * modifier never exceeds maxRPM in float math.
        float rpmCap = (float) Math.floor(getEffectiveMaxRPM() / sourceSpeed);

        // Stress-safe cap: limit modifier so output stress stays under 95% of capacity.
        if (lastCapacitySU > 0 && lastStressSU > 0 && currentModifier > MODIFIER_EPSILON) {
            float stressCap = currentModifier * (lastCapacitySU / lastStressSU) * STRESS_SAFETY_MARGIN;
            rpmCap = Math.min(rpmCap, stressCap);
        }

        // Allow sub-1.0 modifiers (throttling below source RPM), but not below a sensible floor
        return Math.max(MODIFIER_EPSILON, rpmCap);
    }

    /**
     * Snaps a modifier so that sourceSpeed * modifier produces an integer RPM.
     * Prevents float-precision issues in Create's getConveyedSpeed/propagateNewSource.
     */
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
                // Immediately clamp modifiers to new effective max RPM
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

    /** [7] */
    private void applyModifierChange(float oldModifier) {
        if (level == null || level.isClientSide() || !isLiveClutch()) return;

        // Re-entrancy guard: onSpeedChanged (fired during propagation) can try to call
        // applyModifierChange again. Ignore the nested call.
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
            // [8]
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
                    // Transient zero during SpeedController-style re-propagation, hold modifier.
                    return;
                }
                if (isOverStressed()) {
                    // Network overstressed, do NOT reset modifier; let the governor reduce it
                    markDirty();
                    return;
                }
                // [9]
                markDirty();
                return;
            }

            // During modifier change propagation, skip all modifier mutation.
            // Create calls onSpeedChanged on the AVC during handleAdded's propagation walk.
            // If we mutate currentModifier here, different edges see different ratios → destruction.
            if (inModifierChange) {
                return;
            }

            // Clamp modifiers to the new source speed's max allowed
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
