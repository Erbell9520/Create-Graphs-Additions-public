package com.createstats.blockentity;

import com.createstats.block.SmartGearshiftBlock;
import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;
import net.createmod.catnip.theme.Color;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class SmartGearshiftBE extends SplitShaftBlockEntity {

    private static final float MAX_STAGE_RPM = 256.0f;
    private static final float MODIFIER_NEUTRAL = 1.0f;
    private static final float MODIFIER_EPSILON = 0.0001f;
    private static final int STAGE_COOLDOWN_TICKS = 10;
    private static final int[] STAGE_TARGET_RPMS = {
            8, 16, 32, 48, 64,
            80, 96, 112, 128, 144,
            160, 176, 192, 224, 256
    };

    private int targetStage;
    private int currentStage;
    private int lastRedstoneSignal;
    private float currentModifier;
    private Direction lastKnownInputFacing;
    private int stageCooldown;
    private int lastComparatorLevel;
    private boolean inModifierChange;
    private boolean inNetworkCallback;
    private boolean pendingModifierRebuild;

    public float dialTarget;
    public float dialState;
    public float prevDialState;
    public int dialColor;

    public SmartGearshiftBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.currentModifier = MODIFIER_NEUTRAL;
    }

    @Override
    public float getRotationSpeedModifier(Direction face) {
        // [1]
        Direction inputFace = getEffectiveInputFacing();
        if (inputFace != null && face != inputFace) {
            // [2]
            float src = Math.abs(getTheoreticalSpeed());
            if (src > 0.0f) {
                return Math.min(currentModifier, MAX_STAGE_RPM / src);
            }
            return currentModifier;
        }
        return 1.0f;
    }

    /** [3] */
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
        super.tick();

        updateDialVisuals();

        if (level == null || level.isClientSide()) {
            return;
        }

        if (hasSource()) {
            Direction liveInput = getSourceFacing();
            if (liveInput != null) {
                if (lastKnownInputFacing == null) {
                    // First orientation (or after a clean stop): adopt the live source side.
                    lastKnownInputFacing = liveInput;
                } else if (liveInput != lastKnownInputFacing) {
                    // Source side flipped: the input was removed and a source now drives the old
                    // output side. Re-orient and re-target the ratio for the new input/output.
                    lastKnownInputFacing = liveInput;
                    recomputeModifierForCurrentSource();
                }
            }
        }

        if (stageCooldown > 0) {
            stageCooldown--;
        }

        processPendingModifierRebuild();

        int redstoneSignal = Mth.clamp(level.getBestNeighborSignal(worldPosition), 0, 15);
        if (redstoneSignal != lastRedstoneSignal) {
            lastRedstoneSignal = redstoneSignal;
            if (targetStage != redstoneSignal) {
                targetStage = redstoneSignal;
                setChanged();
                sendData();
            }
        }

        if (targetStage != currentStage && stageCooldown <= 0) {
            applyTargetStage();
        }

        updateBlockStateVisuals();
        updateComparatorOutput();
    }

    private void updateDialVisuals() {
        float desiredTarget = computeDialTarget();
        dialTarget = desiredTarget;
        dialColor = computeDialColor(desiredTarget);

        prevDialState = dialState;
        dialState += (desiredTarget - dialState) * 0.125f;
        if (Math.abs(dialTarget - dialState) < 0.0005f) {
            dialState = dialTarget;
        }
    }

    private float computeDialTarget() {
        return Mth.clamp(getDisplayedOutputSpeed() / MAX_STAGE_RPM, 0.0f, 1.0f);
    }

    private int computeDialColor(float progress) {
        if (progress <= 0.0f) {
            return 0;
        }
        if (progress < 0.5f) {
            return Color.mixColors(0x3F7BFF, 0x42C7A5, progress * 2.0f);
        }
        return Color.mixColors(0xD9A441, 0xD8572A, progress * 2.0f - 1.0f);
    }

    private void applyTargetStage() {
        float sourceSpeed = getSourceSpeed();
        float oldModifier = currentModifier;

        currentStage = targetStage;
        currentModifier = getModifierForStage(currentStage, sourceSpeed);

        if (sourceSpeed > 0.0f && hasSource() && Float.compare(oldModifier, currentModifier) != 0) {
            applyModifierChange(oldModifier);
            stageCooldown = STAGE_COOLDOWN_TICKS;
        } else {
            setChanged();
            sendData();
        }
    }

    /**
     * Re-target the ratio for the current (possibly newly-flipped) source. Guarded so a stalled or
     * overstressed source (speed 0) never wipes the configured ratio.
     */
    private void recomputeModifierForCurrentSource() {
        if (level == null || level.isClientSide()) {
            return;
        }
        float sourceSpeed = getSourceSpeed();
        if (sourceSpeed <= 0.0f) {
            return;
        }
        float desired = getModifierForStage(currentStage, sourceSpeed);
        if (Float.compare(desired, currentModifier) != 0) {
            float oldModifier = currentModifier;
            currentModifier = desired;
            applyModifierChange(oldModifier);
            stageCooldown = STAGE_COOLDOWN_TICKS;
        }
    }

    public int getTargetStage() {
        return targetStage;
    }

    public int getCurrentStage() {
        return currentStage;
    }

    public float getCurrentModifier() {
        return currentModifier;
    }

    public float getSourceSpeed() {
        return Math.abs(getTheoreticalSpeed());
    }

    public float getTransmittedSpeed() {
        float sourceSpeed = getSourceSpeed();
        if (sourceSpeed <= 0.0f || isOverStressed()) {
            return 0.0f;
        }
        if (currentStage == 0) {
            return sourceSpeed;
        }
        return Math.min(MAX_STAGE_RPM, sourceSpeed * currentModifier);
    }

    public float getDisplayedOutputSpeed() {
        return getTransmittedSpeed();
    }

    public boolean isBoosting() {
        return currentModifier > MODIFIER_NEUTRAL + MODIFIER_EPSILON;
    }

    public boolean isThrottling() {
        return currentModifier < MODIFIER_NEUTRAL - MODIFIER_EPSILON;
    }

    public int getAnalogOutputSignal() {
        return Mth.clamp(currentStage, 0, 15);
    }

    public void syncKineticState() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (hasNetwork()) {
            getOrCreateNetwork().updateNetwork();
        }
        updateComparatorOutput();
        setChanged();
        sendData();
    }

    @Override
    public void onSpeedChanged(float prevSpeed) {
        inNetworkCallback = true;
        try {
            super.onSpeedChanged(prevSpeed);

            if (getSpeed() == 0.0f) {
                if (inModifierChange) {
                    return;
                }
                if (isOverStressed()) {
                    if (level != null && !level.isClientSide()) {
                        sendData();
                    }
                    setChanged();
                    return;
                }
                lastKnownInputFacing = null;
                if (level != null && !level.isClientSide()) {
                    sendData();
                }
                setChanged();
                return;
            }

            if (inModifierChange) {
                return;
            }

            float sourceSpeed = getSourceSpeed();
            float desiredModifier = getModifierForStage(currentStage, sourceSpeed);
            if (Float.compare(desiredModifier, currentModifier) != 0) {
                float oldModifier = currentModifier;
                currentModifier = desiredModifier;
                applyModifierChange(oldModifier);
                stageCooldown = STAGE_COOLDOWN_TICKS;
            }

            if (level != null && !level.isClientSide()) {
                sendData();
            }
            setChanged();
        } finally {
            inNetworkCallback = false;
        }
    }

    public static int getTargetRPMForStage(int stage) {
        if (stage <= 0) {
            return 0;
        }
        int clampedStage = Mth.clamp(stage, 1, STAGE_TARGET_RPMS.length);
        return STAGE_TARGET_RPMS[clampedStage - 1];
    }

    private float getModifierForStage(int stage, float sourceSpeed) {
        if (stage <= 0 || sourceSpeed <= 0.0f) {
            return MODIFIER_NEUTRAL;
        }

        float targetRpm = getTargetRPMForStage(stage);
        float maxAllowed = getMaxAllowedModifier(stage, sourceSpeed);
        float modifier = Mth.clamp(targetRpm / sourceSpeed, MODIFIER_EPSILON, maxAllowed);
        return snapToIntegerRPM(modifier, sourceSpeed);
    }

    private float getMaxAllowedModifier(int stage, float sourceSpeed) {
        if (stage <= 0 || sourceSpeed <= 0.0f) {
            return MODIFIER_NEUTRAL;
        }
        float rpmCap = MAX_STAGE_RPM / sourceSpeed;
        return Math.max(MODIFIER_EPSILON, rpmCap);
    }

    private float snapToIntegerRPM(float modifier, float sourceSpeed) {
        if (sourceSpeed < 1.0f) {
            return modifier;
        }
        float outputRPM = Math.round(sourceSpeed * modifier);
        outputRPM = Mth.clamp(outputRPM, 1.0f, MAX_STAGE_RPM);
        return outputRPM / sourceSpeed;
    }

    private void applyModifierChange(float oldModifier) {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (inModifierChange) {
            return;
        }
        if (inNetworkCallback) {
            pendingModifierRebuild = true;
            setChanged();
            notifyUpdate();
            return;
        }
        performModifierRebuild();
    }

    private void processPendingModifierRebuild() {
        if (pendingModifierRebuild) {
            pendingModifierRebuild = false;
            performModifierRebuild();
        }
    }

    private void performModifierRebuild() {
        inModifierChange = true;
        try {
            detachKinetics();
            attachKinetics();
            syncKineticState();
            notifyUpdate();
        } finally {
            inModifierChange = false;
        }
    }

    private Direction getOutputFacing() {
        Direction inputFace = getEffectiveInputFacing();
        return inputFace != null ? inputFace.getOpposite() : null;
    }

    private void updateBlockStateVisuals() {
        if (level == null || level.isClientSide()) {
            return;
        }

        SmartGearshiftBlock.VisualState visualState = getVisualState();
        SmartGearshiftBlock.VisualState currentVisualState = getBlockState().getValue(SmartGearshiftBlock.VISUAL_STATE);
        if (currentVisualState != visualState) {
            level.setBlock(worldPosition, getBlockState().setValue(SmartGearshiftBlock.VISUAL_STATE, visualState), 3);
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    private SmartGearshiftBlock.VisualState getVisualState() {
        if (isBoosting()) {
            return SmartGearshiftBlock.VisualState.BOOST;
        }
        if (isThrottling()) {
            return SmartGearshiftBlock.VisualState.THROTTLE;
        }
        return SmartGearshiftBlock.VisualState.NEUTRAL;
    }

    private void updateComparatorOutput() {
        if (level == null || level.isClientSide()) {
            return;
        }

        int newLevel = getAnalogOutputSignal();
        if (newLevel != lastComparatorLevel) {
            lastComparatorLevel = newLevel;
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("TargetStage", targetStage);
        tag.putInt("CurrentStage", currentStage);
        tag.putInt("LastRedstoneSignal", lastRedstoneSignal);
        tag.putFloat("CurrentModifier", currentModifier);
        tag.putFloat("DialTarget", dialTarget);
        tag.putInt("DialColor", dialColor);
        if (lastKnownInputFacing != null) {
            tag.putInt("InputFacing", lastKnownInputFacing.get3DDataValue());
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        targetStage = Mth.clamp(tag.getInt("TargetStage"), 0, 15);
        currentStage = Mth.clamp(tag.getInt("CurrentStage"), 0, 15);
        lastRedstoneSignal = Mth.clamp(tag.getInt("LastRedstoneSignal"), 0, 15);
        currentModifier = tag.contains("CurrentModifier") ? tag.getFloat("CurrentModifier") : MODIFIER_NEUTRAL;
        if (currentStage > 0) {
            currentModifier = Math.max(MODIFIER_EPSILON, currentModifier);
        } else {
            currentModifier = MODIFIER_NEUTRAL;
        }
        dialTarget = tag.getFloat("DialTarget");
        dialColor = tag.getInt("DialColor");
        if (tag.contains("InputFacing")) {
            lastKnownInputFacing = Direction.from3DDataValue(tag.getInt("InputFacing"));
        }
        lastComparatorLevel = getAnalogOutputSignal();
    }
}
