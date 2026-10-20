package com.createstats.blockentity;

import com.createstats.block.ControlClutchBlock;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.ticks.TickPriority;

import javax.annotation.Nullable;
import java.util.Objects;

public class ControlClutchBE extends SplitShaftBlockEntity {
    private static final int CONTROLLER_TIMEOUT_TICKS = 40;
    private static final int PAIR_FEEDBACK_TICKS = 32;

    private boolean controllerRequestedRun = true;
    private int lastControllerSignal;
    @Nullable
    private BlockPos controllerPos;
    private int controllerRow = -1;
    private int controllerTimeoutTicks;
    private int lastComparatorLevel;
    private int pairFeedbackTicks;

    public float dialTarget;
    public float dialState;
    public float prevDialState;
    public int dialColor;

    public ControlClutchBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        refreshDialTarget();
        dialState = dialTarget;
        prevDialState = dialTarget;
    }

    @Override
    public float getRotationSpeedModifier(Direction face) {
        if (hasSource() && face != getSourceFacing() && !controllerRequestedRun) {
            return 0.0f;
        }
        return 1.0f;
    }

    @Override
    public void tick() {
        super.tick();
        updateDialAnimation();
        if (pairFeedbackTicks > 0) {
            pairFeedbackTicks--;
        }

        if (level == null || level.isClientSide()) {
            return;
        }

        if (isControllerLinked()) {
            if (controllerTimeoutTicks > 0) {
                controllerTimeoutTicks--;
            }
            if (controllerTimeoutTicks <= 0) {
                clearControllerState();
                return;
            }
        }

        updateBlockStateVisuals();
        updateComparatorOutput();
    }

    public void applyControllerState(@Nullable BlockPos controllerPos, boolean shouldRun, int signal) {
        applyControllerState(controllerPos, -1, shouldRun, signal);
    }

    public void applyControllerState(@Nullable BlockPos controllerPos, int row, boolean shouldRun, int signal) {
        BlockPos normalizedControllerPos = controllerPos == null ? null : controllerPos.immutable();
        int normalizedRow = normalizedControllerPos == null ? -1 : row;
        int clampedSignal = Mth.clamp(signal, 0, 15);
        boolean linkChanged = !Objects.equals(this.controllerPos, normalizedControllerPos);
        boolean rowChanged = this.controllerRow != normalizedRow;
        boolean runningChanged = this.controllerRequestedRun != shouldRun;
        boolean signalChanged = this.lastControllerSignal != clampedSignal;

        this.controllerPos = normalizedControllerPos;
        this.controllerRow = normalizedRow;
        this.controllerRequestedRun = shouldRun;
        this.lastControllerSignal = clampedSignal;
        this.controllerTimeoutTicks = normalizedControllerPos == null ? 0 : CONTROLLER_TIMEOUT_TICKS;
        if (normalizedControllerPos == null) {
            this.pairFeedbackTicks = 0;
        } else if (linkChanged || rowChanged) {
            this.pairFeedbackTicks = PAIR_FEEDBACK_TICKS;
        }
        refreshDialTarget();

        if (level == null || level.isClientSide()) {
            return;
        }

        if (runningChanged) {
            if (shouldRun) {
                resumeTransmission();
            } else {
                stopTransmission();
            }
            return;
        }

        if (linkChanged || rowChanged || signalChanged) {
            updateBlockStateVisuals();
            updateComparatorOutput();
            notifyUpdate();
        }
    }

    public void clearControllerState() {
        applyControllerState(null, -1, true, 0);
    }

    public void clearControllerState(@Nullable BlockPos controllerPos, int row) {
        BlockPos normalizedControllerPos = controllerPos == null ? null : controllerPos.immutable();
        if (!Objects.equals(this.controllerPos, normalizedControllerPos)) {
            return;
        }
        if (normalizedControllerPos != null && this.controllerRow != row) {
            return;
        }
        this.clearControllerState();
    }

    public boolean isControllerLinked() {
        return controllerPos != null;
    }

    @Nullable
    public BlockPos getControllerPos() {
        return controllerPos;
    }

    public int getControllerRow() {
        return controllerRow;
    }

    public boolean isControllerRequestingRun() {
        return controllerRequestedRun;
    }

    public boolean isOutputRunning() {
        return controllerRequestedRun && !isOverStressed();
    }

    public int getLastControllerSignal() {
        return lastControllerSignal;
    }

    public int getPairFeedbackTicks() {
        return pairFeedbackTicks;
    }

    public boolean hasKineticNetwork() {
        return hasNetwork();
    }

    public boolean isNetworkRunning() {
        return hasNetwork() && getTheoreticalSpeed() != 0.0f;
    }

    public float getSourceSpeed() {
        return Math.abs(getTheoreticalSpeed());
    }

    public float getTransmittedSpeed() {
        if (!controllerRequestedRun || isOverStressed()) {
            return 0.0f;
        }
        return getSourceSpeed();
    }

    public int getAnalogOutputSignal() {
        return isControllerLinked() ? lastControllerSignal : 0;
    }

    public void syncKineticState() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (hasNetwork()) {
            getOrCreateNetwork().updateNetwork();
        }
        updateBlockStateVisuals();
        updateComparatorOutput();
        notifyUpdate();
    }

    private void stopTransmission() {
        updateBlockStateVisuals();
        if (level != null && !level.isClientSide()) {
            RotationPropagator.handleRemoved(level, worldPosition, this);
        }
        syncKineticState();
    }

    private void resumeTransmission() {
        updateSpeed = true;
        updateBlockStateVisuals();
        if (level != null && !level.isClientSide()) {
            RotationPropagator.handleRemoved(level, worldPosition, this);
            level.scheduleTick(worldPosition, getBlockState().getBlock(), 1, TickPriority.EXTREMELY_HIGH);
        }
        updateComparatorOutput();
        notifyUpdate();
    }

    private void updateDialAnimation() {
        refreshDialTarget();
        prevDialState = dialState;
        dialState += (dialTarget - dialState) * 0.125f;
        if (Math.abs(dialTarget - dialState) < 0.0005f) {
            dialState = dialTarget;
        }
    }

    private void refreshDialTarget() {
        if (isControllerLinked()) {
            if (controllerRequestedRun && lastControllerSignal <= 0) {
                dialTarget = 1.0f;
            } else {
                dialTarget = Mth.clamp(lastControllerSignal / 15.0f, 0.0f, 1.0f);
            }
            dialColor = controllerRequestedRun ? 0x42C7A5 : 0xD8572A;
            return;
        }

        dialTarget = controllerRequestedRun ? 1.0f : 0.0f;
        dialColor = controllerRequestedRun ? 0x8CAAD9 : 0x6E6E6E;
    }

    private void updateBlockStateVisuals() {
        if (level == null || level.isClientSide()) {
            return;
        }
        boolean currentActive = getBlockState().getValue(ControlClutchBlock.ACTIVE);
        if (currentActive != controllerRequestedRun) {
            level.setBlock(worldPosition, getBlockState().setValue(ControlClutchBlock.ACTIVE, controllerRequestedRun), 3);
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
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
        tag.putBoolean("ControllerRequestedRun", controllerRequestedRun);
        tag.putInt("ControllerSignal", lastControllerSignal);
        tag.putFloat("DialTarget", dialTarget);
        tag.putInt("ControllerRow", controllerRow);
        tag.putInt("PairFeedbackTicks", pairFeedbackTicks);
        if (controllerPos != null) {
            tag.putLong("ControllerPos", controllerPos.asLong());
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        controllerRequestedRun = !tag.contains("ControllerRequestedRun") || tag.getBoolean("ControllerRequestedRun");
        lastControllerSignal = Mth.clamp(tag.getInt("ControllerSignal"), 0, 15);
        controllerPos = tag.contains("ControllerPos") ? BlockPos.of(tag.getLong("ControllerPos")) : null;
        controllerRow = tag.contains("ControllerRow") ? tag.getInt("ControllerRow") : -1;
        controllerTimeoutTicks = controllerPos == null ? 0 : CONTROLLER_TIMEOUT_TICKS;
        pairFeedbackTicks = Math.max(0, tag.getInt("PairFeedbackTicks"));
        if (tag.contains("DialTarget")) {
            dialTarget = tag.getFloat("DialTarget");
        } else {
            refreshDialTarget();
        }

        lastComparatorLevel = getAnalogOutputSignal();
    }
}
