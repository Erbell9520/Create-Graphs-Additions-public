package com.createstats.flight.blockentity;

import com.createstats.CreateStats;
import com.createstats.DebugLogging;
import com.createstats.flight.FlightConfig;
import com.createstats.flight.block.RpmGovernorBlock;
import com.createstats.flight.bridge.SableBridge;
import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public class RpmGovernorBE extends SplitShaftBlockEntity {
    private int commandedRpm;
    private int appliedRpm;
    @Nullable private UUID commanderUuid;
    @Nullable private BlockPos commanderPos;
    private int ticksSinceHeartbeat;
    private boolean failSafeActive;
    private boolean inModifierRebuild;
    private boolean inNetworkCallback;
    private boolean pendingModifierRebuild;
    private long lastModifierRebuildTick = Long.MIN_VALUE;
    @Nullable private Direction lastKnownInputFacing;
    private float currentModifier;
    private float targetModifier;

    public RpmGovernorBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public float getRotationSpeedModifier(Direction face) {
        Direction input = getInputFacing();
        if (input != null && face != input) {
            return currentModifier;
        }
        return 1.0f;
    }

    private static Direction directionFromState(BlockState state) {
        for (var prop : state.getProperties()) {
            if (prop instanceof DirectionProperty dp && "facing".equals(dp.getName())) {
                return state.getValue(dp);
            }
        }
        return null;
    }

    @Nullable
    private Direction getInputFacing() {
        if (hasSource()) {
            Direction live = getSourceFacing();
            if (live != null) return live;
        }
        return lastKnownInputFacing;
    }

    @Nullable
    private Direction getOutputFacing() {
        Direction input = getInputFacing();
        return input == null ? null : input.getOpposite();
    }

    public boolean setCommand(BlockPos fccPos, UUID fccId, int targetRpm) {
        if (level == null || level.isClientSide) return false;
        if (fccId == null) return false;

        if (commanderUuid != null && !commanderUuid.equals(fccId) && !isOwnerStale()) {
            return false;
        }

        boolean clientStateDirty = false;
        if (!fccId.equals(commanderUuid)) {
            commanderUuid = fccId;
            clientStateDirty = true;
        }
        if (!fccPos.equals(commanderPos)) {
            commanderPos = fccPos;
            clientStateDirty = true;
        }
        ticksSinceHeartbeat = 0;
        if (failSafeActive) {
            failSafeActive = false;
            clientStateDirty = true;
        }

        int clamped = clamp(targetRpm);
        if (clamped != commandedRpm) {
            commandedRpm = clamped;
            clientStateDirty = true;
            DebugLogging.rpmGovernorInfo("[RPMGov] command pos={} owner={} ownerPos={} target={} applied={} speed={} sourceFace={} network={}",
                worldPosition, shortId(fccId), fccPos, clamped, appliedRpm, fmt(getSpeed()), getInputFacing(), network);
        }
        if (clientStateDirty) {
            sendData();
            setChanged();
        }
        return true;
    }

    public boolean heartbeat(BlockPos fccPos, UUID fccId) {
        if (level == null || level.isClientSide) return false;
        if (fccId == null || !fccId.equals(commanderUuid)) return false;
        commanderPos = fccPos;
        ticksSinceHeartbeat = 0;
        return true;
    }

    public void releaseOwnership(UUID fccId) {
        if (level == null || level.isClientSide) return;
        if (fccId == null || !fccId.equals(commanderUuid)) return;
        clearOwnershipAndZero();
    }

    public int getCommandedRpm()  { return commandedRpm; }
    public int getAppliedRpm()    { return appliedRpm; }
    public boolean isRamping()    { return commandedRpm != appliedRpm; }
    @Nullable public UUID getCommanderUuid() { return commanderUuid; }
    @Nullable public BlockPos getCommanderPos() { return commanderPos; }
    public boolean isFailSafeActive() { return failSafeActive; }

    private boolean isOwnerStale() {
        return ticksSinceHeartbeat >= FlightConfig.GOVERNOR_FCC_MISSING_FAILSAFE_TICKS;
    }

    private static int clamp(int v) {
        return Math.max(-FlightConfig.RPM_CLAMP, Math.min(FlightConfig.RPM_CLAMP, v));
    }

    private void applyRpmStepIfNeeded() {
        float sourceSpeed = getSpeed();
        if (hasSource()) {
            lastKnownInputFacing = getSourceFacing();
        }
        if (Math.abs(sourceSpeed) < 1.0e-4f || !hasSource()) {
            if (appliedRpm != 0 || currentModifier != 0.0f || targetModifier != 0.0f) {
                int oldRpm = appliedRpm;
                float oldMod = currentModifier;
                if (!canRebuildNow()) {
                    pendingModifierRebuild = true;
                    return;
                }
                if (rebuildOutputBranch(oldRpm, 0, oldMod, 0.0f, "no-source")) {
                    updateVisualState();
                    sendData();
                    setChanged();
                }
            }
            return;
        }

        int rampTarget = commandedRpm;
        float newModifier = computeModifierForOutput(rampTarget, sourceSpeed);
        int newApplied = Math.round(sourceSpeed * newModifier);
        if (newApplied == appliedRpm && newModifier == currentModifier) return;
        if (!canRebuildNow()) {
            pendingModifierRebuild = true;
            return;
        }

        int oldRpm = appliedRpm;
        float oldMod = currentModifier;
        if (rebuildOutputBranch(oldRpm, newApplied, oldMod, newModifier, "step")) {
            updateVisualState();
            sendData();
            setChanged();
        }
    }

    private boolean canRebuildNow() {
        if (level == null) return false;
        long now = level.getGameTime();
        return lastModifierRebuildTick == Long.MIN_VALUE
                || now - lastModifierRebuildTick >= FlightConfig.GOVERNOR_REBUILD_INTERVAL_TICKS;
    }

    private static int stepTowards(int current, int target, int maxStep) {
        int step = Math.max(1, maxStep);
        if (current < target) return Math.min(target, current + step);
        if (current > target) return Math.max(target, current - step);
        return current;
    }

    private static float computeModifierForOutput(int targetRpm, float sourceSpeed) {
        if (Math.abs(sourceSpeed) < 1.0e-4f) return 0.0f;
        float modifier = targetRpm / sourceSpeed;
        if (!Float.isFinite(modifier)) return 0.0f;

        if (modifier > 32.0f) return 32.0f;
        if (modifier < -32.0f) return -32.0f;
        return modifier;
    }

    private boolean rebuildOutputBranch(int oldRpm, int newRpm, float oldMod, float newMod, String reason) {
        if (level == null || level.isClientSide) return false;
        if (inNetworkCallback) {
            pendingModifierRebuild = true;
            return false;
        }
        if (inModifierRebuild) {
            pendingModifierRebuild = true;
            return false;
        }
        inModifierRebuild = true;
        try {
            Direction input = getInputFacing();
            Direction output = getOutputFacing();
            Long previousNetwork = network;
            float previousSpeed = getSpeed();
            detachKinetics();
            removeSource();
            appliedRpm = newRpm;
            currentModifier = newMod;
            targetModifier = newMod;
            attachKinetics();
            lastModifierRebuildTick = level.getGameTime();
                DebugLogging.rpmGovernorInfo("[RPMGov] rebuild pos={} reason={} rpm {}->{} mod {}->{} speed={} sourceFace={} outputFace={} network={}",
                    worldPosition, reason, oldRpm, newRpm, fmt(oldMod), fmt(newMod), fmt(previousSpeed),
                    input, output, previousNetwork);
            return true;
        } catch (RuntimeException ex) {
            commandedRpm = 0;
            appliedRpm = 0;
            currentModifier = 0.0f;
            targetModifier = 0.0f;
            failSafeActive = true;
            lastModifierRebuildTick = level.getGameTime();
            CreateStats.LOGGER.error("[RPMGov] rebuild failed pos={} reason={} oldRpm={} newRpm={} oldMod={} newMod={}, zeroing governor",
                    worldPosition, reason, oldRpm, newRpm, fmt(oldMod), fmt(newMod), ex);
            try {
                attachKinetics();
            } catch (RuntimeException attachEx) {
                CreateStats.LOGGER.error("[RPMGov] zero reattach failed pos={}", worldPosition, attachEx);
            }
            updateVisualState();
            sendData();
            setChanged();
            return false;
        } finally {
            inModifierRebuild = false;
        }
    }

    private void clearOwnershipAndZero() {
        boolean ownerWasSet = commanderUuid != null || commanderPos != null;
        commanderUuid = null;
        commanderPos = null;
        boolean rpmChanged = false;
        if (commandedRpm != 0 || appliedRpm != 0) {
            commandedRpm = 0;
            rpmChanged = true;
        }
        applyRpmStepIfNeeded();
        ticksSinceHeartbeat = 0;
        failSafeActive = false;
        if (rpmChanged || ownerWasSet) {
            updateVisualState();
            sendData();
        }
        setChanged();
    }

    private void updateVisualState() {
        if (level == null || level.isClientSide) return;
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof RpmGovernorBlock)) return;
        RpmGovernorBlock.VisualState desired =
            (commanderUuid != null && appliedRpm != 0)
                ? RpmGovernorBlock.VisualState.ACTIVE
                : RpmGovernorBlock.VisualState.IDLE;
        if (state.getValue(RpmGovernorBlock.VISUAL_STATE) != desired) {
            level.setBlock(worldPosition,
                state.setValue(RpmGovernorBlock.VISUAL_STATE, desired),
                Block.UPDATE_CLIENTS);
        }
    }

    // ---------------------------- Tick / failsafe ----------------------------

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;

        if (commanderUuid != null) {
            ticksSinceHeartbeat++;
            if (ticksSinceHeartbeat >= FlightConfig.GOVERNOR_FCC_MISSING_FAILSAFE_TICKS) {
                boolean wasFailSafe = failSafeActive;
                failSafeActive = true;
                boolean rpmChanged = false;
                if (commandedRpm != 0) {
                    commandedRpm = 0;
                    rpmChanged = true;
                }
                if (!rpmChanged && !wasFailSafe) {
                    sendData();
                    setChanged();
                }
            }
        }
        applyRpmStepIfNeeded();
        if (pendingModifierRebuild && canRebuildNow()) {
            pendingModifierRebuild = false;
            applyRpmStepIfNeeded();
        }
    }

    @Override
    public void initialize() {
        super.initialize();

        if (level != null && !level.isClientSide) {
            updateVisualState();
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (commanderUuid != null) tag.putUUID("CommanderUuid", commanderUuid);
        if (commanderPos != null) {
            tag.putInt("CommanderX", commanderPos.getX());
            tag.putInt("CommanderY", commanderPos.getY());
            tag.putInt("CommanderZ", commanderPos.getZ());
        }
        if (clientPacket) {
            tag.putInt("AppliedRpm", appliedRpm);
            tag.putBoolean("FailSafe", failSafeActive);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        commanderUuid = tag.hasUUID("CommanderUuid") ? tag.getUUID("CommanderUuid") : null;
        commanderPos = tag.contains("CommanderX")
            ? new BlockPos(tag.getInt("CommanderX"), tag.getInt("CommanderY"), tag.getInt("CommanderZ"))
            : null;
        if (clientPacket) {
            appliedRpm = tag.getInt("AppliedRpm");
            failSafeActive = tag.getBoolean("FailSafe");
        } else {
            commandedRpm = 0;
            appliedRpm = 0;
            ticksSinceHeartbeat = 0;
            failSafeActive = false;
            currentModifier = 0.0f;
            targetModifier = 0.0f;
        }
    }

    @Override
    public void onSpeedChanged(float previousSpeed) {
        inNetworkCallback = true;
        try {
            super.onSpeedChanged(previousSpeed);
            if (level == null || level.isClientSide) return;
            if (hasSource()) {
                lastKnownInputFacing = getSourceFacing();
            }
            DebugLogging.rpmGovernorInfo("[RPMGov] speed changed pos={} prev={} now={} sourceFace={} command={} applied={} mod={} network={}",
                    worldPosition, fmt(previousSpeed), fmt(getSpeed()), getInputFacing(), commandedRpm, appliedRpm,
                    fmt(currentModifier), network);
            if (!inModifierRebuild) {
                pendingModifierRebuild = true;
            }
        } finally {
            inNetworkCallback = false;
        }
    }

    private static String shortId(UUID id) {
        return id == null ? "null" : id.toString().substring(0, 8);
    }

    private static String fmt(float value) {
        return String.format("%.3f", value);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean added = super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        tooltip.add(Component.literal(" ").append(
            Component.translatable("block.create_stats.rpm_governor.goggle.title")
                .withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.literal("    ").append(
            Component.translatable("block.create_stats.rpm_governor.goggle.applied", appliedRpm)
                .withStyle(ChatFormatting.AQUA)));
        if (commanderUuid != null) {
            String shortId = commanderUuid.toString().substring(0, 8);
            tooltip.add(Component.literal("    ").append(
                Component.translatable("block.create_stats.rpm_governor.goggle.owned_by", shortId)
                    .withStyle(ChatFormatting.DARK_AQUA)));
        } else {
            tooltip.add(Component.literal("    ").append(
                Component.translatable("block.create_stats.rpm_governor.goggle.unowned")
                    .withStyle(ChatFormatting.DARK_GRAY)));
        }
        if (failSafeActive) {
            tooltip.add(Component.literal("    ").append(
                Component.translatable("block.create_stats.rpm_governor.goggle.failsafe")
                    .withStyle(ChatFormatting.RED)));
        }
        return true;
    }
}
