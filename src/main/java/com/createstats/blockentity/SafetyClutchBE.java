package com.createstats.blockentity;

import com.createstats.block.SafetyClutchBlock;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.IRotate.StressImpact;
import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;
import net.createmod.catnip.theme.Color;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.ticks.TickPriority;

public class SafetyClutchBE extends SplitShaftBlockEntity {

    private static final int CHECK_INTERVAL = 2;
    private static final int TRIP_COOLDOWN_TICKS = 40;
    private static final int AUTO_RESET_RETRY_TICKS = 100;
    private static final int RESET_SETTLE_TICKS = 10;

    private SafetyClutchSettings settings;
    private int checkTimer;
    private boolean tripped;
    private int tripCooldown;
    private int resetSettleTicks;
    private int lastComparatorLevel;
    private boolean inNetworkCallback;
    private boolean pendingTripDisconnect;
    private boolean pendingResetReconnect;

    private float lastStressPercent;
    private float lastStressSU;
    private float lastCapacitySU;
    private boolean lastOverstressed;

    public float dialTarget;
    public float dialState;
    public float prevDialState;
    public int dialColor;

    public SafetyClutchBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.settings = new SafetyClutchSettings();
    }

    @Override
    public float getRotationSpeedModifier(Direction face) {
        if (hasSource() && face != getSourceFacing()
                && getBlockState().getValue(SafetyClutchBlock.TRIPPED)) {
            return 0.0f;
        }
        return 1.0f;
    }

    @Override
    public void tick() {
        super.tick();

        prevDialState = dialState;
        dialState += (dialTarget - dialState) * 0.125f;
        if (dialState > 1.0f && level != null && level.random.nextFloat() < 0.5f) {
            dialState -= (dialState - 1.0f) * level.random.nextFloat();
        }

        if (level == null || level.isClientSide()) return;

        processPendingKineticMutation();

        if (++checkTimer < CHECK_INTERVAL) return;
        checkTimer = 0;

        refreshNetworkSnapshot();

        if (tripCooldown > 0) {
            tripCooldown = Math.max(0, tripCooldown - CHECK_INTERVAL);
            if (tripCooldown > 0) {
                updateComparatorOutput();
                return;
            }
        }

        if (resetSettleTicks > 0) {
            resetSettleTicks = Math.max(0, resetSettleTicks - CHECK_INTERVAL);
            updateComparatorOutput();
            return;
        }

        if (!settings.isEnabled()) {
            if (tripped) resetClutch();
            updateComparatorOutput();
            return;
        }

        if (tripped) {
            if (settings.getMode() == SafetyClutchSettings.ClutchMode.AUTO_RESET) {
                resetClutch();
                refreshNetworkSnapshot();
            }
            updateComparatorOutput();
            return;
        }

        evaluateThreshold();
        refreshNetworkSnapshot();
        updateComparatorOutput();
    }

    public SafetyClutchSettings getSettings() {
        return this.settings;
    }

    public boolean isTripped() {
        return this.tripped;
    }

    public float getLastStressPercent() {
        return this.lastStressPercent;
    }

    public float getLastStressSU() {
        return this.lastStressSU;
    }

    public float getLastCapacitySU() {
        return this.lastCapacitySU;
    }

    public float getAutoResetRetrySeconds() {
        return AUTO_RESET_RETRY_TICKS / 20.0f;
    }

    public boolean isLastOverstressed() {
        return this.lastOverstressed;
    }

    public boolean hasStressNetwork() {
        return hasNetwork();
    }

    public boolean isNetworkRunning() {
        return hasNetwork() && getTheoreticalSpeed() != 0.0f;
    }

    public int getAnalogOutputSignal() {
        return Math.min(15, Math.round(this.lastStressPercent / 100.0f * 15.0f));
    }

    public void setSettings(SafetyClutchSettings settings) {
        this.settings = settings;
        if (level != null && !level.isClientSide()) {
            refreshNetworkSnapshot();
            if (!this.settings.isEnabled()) {
                if (tripped) resetClutch();
            } else {
                syncTripCooldownToMode();
                if (!tripped) {
                    evaluateThreshold();
                    refreshNetworkSnapshot();
                }
                updateComparatorOutput();
            }
        }
        notifyUpdate();
    }

    public void manualReset() {
        if (!tripped) return;
        resetClutch();
    }

    public void manualTrip() {
        if (tripped) return;
        tripClutch();
    }

    public void syncNetworkState() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (hasNetwork()) {
            getOrCreateNetwork().updateNetwork();
        } else {
            dialTarget = 0.0f;
        }
        refreshNetworkSnapshot();
        updateComparatorOutput();
    }

    private void tripClutch() {
        if (tripped) return;
        tripped = true;
        tripCooldown = settings.getMode() == SafetyClutchSettings.ClutchMode.AUTO_RESET
                ? AUTO_RESET_RETRY_TICKS
                : TRIP_COOLDOWN_TICKS;
        resetSettleTicks = 0;
        updateBlockState();
        if (level != null && !level.isClientSide()) {
            if (inNetworkCallback) {
                pendingTripDisconnect = true;
                pendingResetReconnect = false;
            } else {
                performTripDisconnect();
            }
        }
        notifyUpdate();
    }

    private void resetClutch() {
        if (!tripped) return;
        tripped = false;
        tripCooldown = 0;
        resetSettleTicks = RESET_SETTLE_TICKS;
        updateSpeed = true;
        updateBlockState();
        if (level != null && !level.isClientSide()) {
            if (inNetworkCallback) {
                pendingResetReconnect = true;
                pendingTripDisconnect = false;
            } else {
                performResetReconnect();
            }
        }
        notifyUpdate();
    }

    private void processPendingKineticMutation() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (pendingTripDisconnect) {
            pendingTripDisconnect = false;
            performTripDisconnect();
            notifyUpdate();
            return;
        }
        if (pendingResetReconnect) {
            pendingResetReconnect = false;
            performResetReconnect();
            notifyUpdate();
        }
    }

    private void performTripDisconnect() {
        RotationPropagator.handleRemoved(level, worldPosition, this);
        syncNetworkState();
    }

    private void performResetReconnect() {
        RotationPropagator.handleRemoved(level, worldPosition, this);
        level.scheduleTick(worldPosition, getBlockState().getBlock(), 1, TickPriority.EXTREMELY_HIGH);
    }

    private void updateBlockState() {
        if (level == null || level.isClientSide()) return;

        boolean currentTripped = getBlockState().getValue(SafetyClutchBlock.TRIPPED);
        if (currentTripped != tripped) {
            level.setBlock(worldPosition,
                    getBlockState().setValue(SafetyClutchBlock.TRIPPED, tripped), 3);
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
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

    private void syncTripCooldownToMode() {
        if (!tripped) {
            return;
        }
        if (settings.getMode() == SafetyClutchSettings.ClutchMode.AUTO_RESET) {
            if (tripCooldown <= 0) {
                tripCooldown = AUTO_RESET_RETRY_TICKS;
            }
            return;
        }
        tripCooldown = 0;
    }

    private void evaluateThreshold() {
        float threshold = settings.getThreshold();
        if (!tripped && (lastStressPercent >= threshold || lastOverstressed)) {
            tripClutch();
        }
    }

    private void storeSnapshot(float stress, float capacity, boolean overstressed) {
        lastStressSU = stress;
        lastCapacitySU = capacity;
        lastOverstressed = overstressed;
        lastStressPercent = capacity > 0.0f ? (stress / capacity) * 100.0f : 0.0f;
    }

    @Override
    public void updateFromNetwork(float maxStress, float currentStress, int networkSize) {
        inNetworkCallback = true;
        try {
            super.updateFromNetwork(maxStress, currentStress, networkSize);
            boolean overstressed = isOverStressed();
            storeSnapshot(currentStress, maxStress, overstressed);
            updateDialTarget(maxStress, currentStress, overstressed);
            if (!settings.isEnabled()) {
                if (tripped) {
                    resetClutch();
                }
            } else {
                evaluateThreshold();
            }
            updateComparatorOutput();
            if (level != null && !level.isClientSide()) {
                sendData();
            }
            setChanged();
        } finally {
            inNetworkCallback = false;
        }
    }

    @Override
    public void onSpeedChanged(float prevSpeed) {
        super.onSpeedChanged(prevSpeed);
        if (getSpeed() == 0.0f) {
            dialTarget = 0.0f;
            setChanged();
            if (level != null && !level.isClientSide()) {
                sendData();
            }
            return;
        }

        if (level != null && !level.isClientSide() && hasNetwork()) {
            updateDialTarget(capacity, stress, isOverStressed());
            sendData();
            setChanged();
        }
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
        tag.put("ClutchSettings", settings.save());
        tag.putBoolean("Tripped", tripped);
        tag.putFloat("LastStressPct", lastStressPercent);
        tag.putFloat("LastStressSU", lastStressSU);
        tag.putFloat("LastCapacitySU", lastCapacitySU);
        tag.putBoolean("LastOverstressed", lastOverstressed);
        tag.putFloat("DialTarget", dialTarget);
        tag.putInt("DialColor", dialColor);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("ClutchSettings")) {
            settings = SafetyClutchSettings.load(tag.getCompound("ClutchSettings"));
        }
        tripped = tag.getBoolean("Tripped");
        lastStressPercent = tag.getFloat("LastStressPct");
        lastStressSU = tag.getFloat("LastStressSU");
        lastCapacitySU = tag.getFloat("LastCapacitySU");
        lastOverstressed = tag.getBoolean("LastOverstressed");
        dialTarget = tag.getFloat("DialTarget");
        dialColor = tag.getInt("DialColor");
        lastComparatorLevel = getAnalogOutputSignal();
    }
}
