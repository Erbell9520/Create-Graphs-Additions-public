package com.createstats.blockentity;

import com.createstats.ModConfiguration;
import com.createstats.block.LoadBalancerBlock;
import com.createstats.block.LoadBalancerValueBoxTransform;
import com.createstats.topology.NetworkDiscovery;
import com.createstats.topology.NetworkGraph;
import com.createstats.topology.NodeType;
import com.createstats.topology.TopologyEdge;
import com.createstats.topology.TopologyNode;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class LoadBalancerBE extends SplitShaftBlockEntity implements com.createstats.util.CopyableSettings {
    private static final int FALLBACK_CHECK_INTERVAL = 20;
    private static final int RECALC_INTERVAL_DIRTY = 4;
    private static final int SOURCE_DIRECTION_CONFIRM_TICKS = 10;
    private static final float MAX_OUTPUT_RPM = 256.0f;
    private static final float MODIFIER_NEUTRAL = 1.0f;
    private static final float MODIFIER_EPSILON = 0.0001f;
    private static final float STEP_SIZE = 0.12f;
    private static final int STEP_COOLDOWN_TICKS = 10;
    private static final int DOWN_STEP_COOLDOWN_TICKS = 20;
    private static final float PILOT_RPM = 8.0f;

    private LoadBalancerSettings settings;

    private ScrollValueBehaviour splitDial;

    private boolean syncingSplitDial;
    private float currentModifierLeft;
    private float currentModifierRight;
    private float targetModifierLeft;
    private float targetModifierRight;
    private boolean dirty;
    private int dirtyTimer;
    private int fallbackTimer;
    private int stepCooldown;
    private int lastComparatorLevel;
    private boolean inModifierChange;
    private boolean inNetworkCallback;
    private boolean pendingModifierRebuild;
    private Direction observedSourceFacing;
    private int observedSourceTicks;
    private float lastKnownOutputRPMLeft;
    private float lastKnownOutputRPMRight;

    private float lastStressPercent;
    private float lastStressSU;
    private float lastCapacitySU;
    private boolean lastOverstressed;

    private float lastLeftStressSU;
    private float lastRightStressSU;
    private float lastBaseStressSU;
    private float lastLeftBudgetSU;
    private float lastRightBudgetSU;
    private boolean leftBranchAttached;
    private boolean rightBranchAttached;
    private int lastTopologyNodeCount;
    private int topologyNodeLimit;
    private TopologyStatus topologyStatus;

    public LoadBalancerBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.settings = new LoadBalancerSettings();
        this.currentModifierLeft = MODIFIER_NEUTRAL;
        this.currentModifierRight = MODIFIER_NEUTRAL;
        this.targetModifierLeft = MODIFIER_NEUTRAL;
        this.targetModifierRight = MODIFIER_NEUTRAL;
        this.lastTopologyNodeCount = 0;
        this.topologyNodeLimit = ModConfiguration.COMMON.topologyMaxNodes.get();
        this.topologyStatus = TopologyStatus.NO_SOURCE;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        this.splitDial = new ScrollValueBehaviour(
                Component.translatable("create_stats.load_balancer.split_dial"), this,
                new LoadBalancerValueBoxTransform());
        this.splitDial.between(0, 100);
        this.splitDial.withFormatter(v -> "L " + v + " / R " + (100 - v));
        this.splitDial.withCallback(this::onSplitDialChanged);
        behaviours.add(this.splitDial);
    }

    private void onSplitDialChanged(int leftSharePct) {
        if (this.syncingSplitDial || this.settings == null) {
            return;
        }
        if (Math.round(this.settings.getLeftSharePct()) == leftSharePct) {
            return;
        }
        this.settings.setLeftSharePct(leftSharePct);
        if (level != null && !level.isClientSide()) {
            refreshNetworkSnapshot();
            markDirty();
        }
        notifyUpdate();
    }

    // settings win. old worlds showed 0 on the box while splitting 50/50
    private void syncSplitDial() {
        if (this.splitDial == null || this.settings == null) {
            return;
        }
        this.syncingSplitDial = true;
        try {
            this.splitDial.setValue(Math.round(this.settings.getLeftSharePct()));
        } finally {
            this.syncingSplitDial = false;
        }
    }

    @Override
    public float getRotationSpeedModifier(Direction face) {
        if (face == getInputFacing()) {
            return 1.0f;
        }

        if (face == getLeftOutputFacing()) {
            return clampOutputModifierLive(this.currentModifierLeft);
        }
        if (face == getRightOutputFacing()) {
            return clampOutputModifierLive(this.currentModifierRight);
        }
        return 0.0f;
    }

    private float clampOutputModifierLive(float modifier) {
        float src = Math.abs(getTheoreticalSpeed());
        if (src > 0.0f) {
            return Math.min(modifier, getEffectiveMaxRPM() / src);
        }
        return modifier;
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null || level.isClientSide()) {
            return;
        }

        trackObservedSourceFacing();
        processPendingModifierRebuild();

        if (!this.settings.isEnabled()) {
            this.topologyStatus = hasSource() ? TopologyStatus.OK : TopologyStatus.NO_SOURCE;
            this.leftBranchAttached = hasPhysicalBranch(getLeftOutputFacing());
            this.rightBranchAttached = hasPhysicalBranch(getRightOutputFacing());
            if (Float.compare(this.currentModifierLeft, MODIFIER_NEUTRAL) != 0
                    || Float.compare(this.currentModifierRight, MODIFIER_NEUTRAL) != 0
                    || Float.compare(this.targetModifierLeft, MODIFIER_NEUTRAL) != 0
                    || Float.compare(this.targetModifierRight, MODIFIER_NEUTRAL) != 0) {
                float oldLeft = this.currentModifierLeft;
                float oldRight = this.currentModifierRight;
                this.currentModifierLeft = MODIFIER_NEUTRAL;
                this.currentModifierRight = MODIFIER_NEUTRAL;
                this.targetModifierLeft = MODIFIER_NEUTRAL;
                this.targetModifierRight = MODIFIER_NEUTRAL;
                applyModifierChange(oldLeft, oldRight);
            }
            updateComparatorOutput();
            return;
        }

        this.fallbackTimer++;
        boolean shouldRecalculate = false;

        if (this.dirty) {
            this.dirtyTimer++;
            if (this.dirtyTimer >= RECALC_INTERVAL_DIRTY) {
                shouldRecalculate = true;
                this.dirtyTimer = 0;
            }
        }

        if (this.fallbackTimer >= FALLBACK_CHECK_INTERVAL) {
            shouldRecalculate = true;
            this.fallbackTimer = 0;
        }

        if (shouldRecalculate) {
            TopologyStatus previousTopologyStatus = this.topologyStatus;
            boolean previousLeftAttached = this.leftBranchAttached;
            boolean previousRightAttached = this.rightBranchAttached;
            refreshNetworkSnapshot();
            recalculateTargets();
            if (previousTopologyStatus != this.topologyStatus
                    || previousLeftAttached != this.leftBranchAttached
                    || previousRightAttached != this.rightBranchAttached) {
                sendData();
            }
            updateVisualStatus();
        }

        if (this.stepCooldown > 0) {
            this.stepCooldown--;
        }

        float sourceSpeed = getSourceSpeed();
        float maxAllowedModifier = getMaxAllowedModifier(sourceSpeed);
        float leftMaxAllowedModifier = getGuardedMaxAllowedModifier(sourceSpeed, maxAllowedModifier,
                resolveGuardReferenceOutputRPM(this.lastKnownOutputRPMLeft, sourceSpeed, this.currentModifierLeft));
        float rightMaxAllowedModifier = getGuardedMaxAllowedModifier(sourceSpeed, maxAllowedModifier,
                resolveGuardReferenceOutputRPM(this.lastKnownOutputRPMRight, sourceSpeed, this.currentModifierRight));

        float clampedCurrentLeft = clampModifier(this.currentModifierLeft, sourceSpeed, leftMaxAllowedModifier);
        float clampedCurrentRight = clampModifier(this.currentModifierRight, sourceSpeed, rightMaxAllowedModifier);
        float clampedTargetLeft = clampModifier(this.targetModifierLeft, sourceSpeed, leftMaxAllowedModifier);
        float clampedTargetRight = clampModifier(this.targetModifierRight, sourceSpeed, rightMaxAllowedModifier);
        if (Float.compare(clampedCurrentLeft, this.currentModifierLeft) != 0
                || Float.compare(clampedCurrentRight, this.currentModifierRight) != 0
                || Float.compare(clampedTargetLeft, this.targetModifierLeft) != 0
                || Float.compare(clampedTargetRight, this.targetModifierRight) != 0) {
            float oldLeft = this.currentModifierLeft;
            float oldRight = this.currentModifierRight;
            this.currentModifierLeft = clampedCurrentLeft;
            this.currentModifierRight = clampedCurrentRight;
            this.targetModifierLeft = clampedTargetLeft;
            this.targetModifierRight = clampedTargetRight;
            if (Float.compare(oldLeft, this.currentModifierLeft) != 0 || Float.compare(oldRight, this.currentModifierRight) != 0) {
                applyModifierChange(oldLeft, oldRight);
                this.stepCooldown = STEP_COOLDOWN_TICKS;
            }
        }

        if ((Float.compare(this.currentModifierLeft, this.targetModifierLeft) != 0
                || Float.compare(this.currentModifierRight, this.targetModifierRight) != 0)
                && this.stepCooldown <= 0) {
            float oldLeft = this.currentModifierLeft;
            float oldRight = this.currentModifierRight;
            boolean downregulating = this.currentModifierLeft > this.targetModifierLeft
                    || this.currentModifierRight > this.targetModifierRight;

            this.currentModifierLeft = stepTowardsModifier(this.currentModifierLeft, this.targetModifierLeft, sourceSpeed, leftMaxAllowedModifier);
            this.currentModifierRight = stepTowardsModifier(this.currentModifierRight, this.targetModifierRight, sourceSpeed, rightMaxAllowedModifier);

            if (Float.compare(oldLeft, this.currentModifierLeft) != 0 || Float.compare(oldRight, this.currentModifierRight) != 0) {
                applyModifierChange(oldLeft, oldRight);
                this.stepCooldown = downregulating ? DOWN_STEP_COOLDOWN_TICKS : STEP_COOLDOWN_TICKS;
            }

            if (Float.compare(this.currentModifierLeft, this.targetModifierLeft) == 0
                    && Float.compare(this.currentModifierRight, this.targetModifierRight) == 0) {
                this.dirty = false;
                this.dirtyTimer = 0;
            }
        }

        rememberLastKnownOutputRPMs(sourceSpeed);
        updateComparatorOutput();
    }

    public LoadBalancerSettings getSettings() {
        return this.settings;
    }

    public void setSettings(LoadBalancerSettings settings) {
        if (this.settings.save().equals(settings.save())) {
            return;
        }
        this.settings = settings;
        if (level != null && !level.isClientSide()) {
            refreshNetworkSnapshot();
            markDirty();
            if (!this.settings.isEnabled()) {
                float oldLeft = this.currentModifierLeft;
                float oldRight = this.currentModifierRight;
                this.currentModifierLeft = MODIFIER_NEUTRAL;
                this.currentModifierRight = MODIFIER_NEUTRAL;
                this.targetModifierLeft = MODIFIER_NEUTRAL;
                this.targetModifierRight = MODIFIER_NEUTRAL;
                applyModifierChange(oldLeft, oldRight);
            }
        }
        syncSplitDial();
        notifyUpdate();
    }

    @Override
    public String settingsKey() {
        return "create_stats:load_balancer";
    }

    @Override
    public void writeSettings(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("LoadBalancerSettings", this.settings.save());
    }

    @Override
    public boolean applySettings(CompoundTag tag, HolderLookup.Provider registries,
                                 @org.jetbrains.annotations.Nullable net.minecraft.world.entity.player.Player player,
                                 boolean simulate) {
        if (!tag.contains("LoadBalancerSettings")) {
            return false;
        }
        if (!simulate) {
            setSettings(LoadBalancerSettings.load(tag.getCompound("LoadBalancerSettings")));
        }
        return true;
    }

    // spelled out, SmartBlockEntity.writeSafe beats the interface default and drops the settings from schematics
    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        writeFreshDefaults(tag);
        writeSettings(tag, registries);
    }

    public float getCurrentModifierLeft() {
        return this.currentModifierLeft;
    }

    public float getCurrentModifierRight() {
        return this.currentModifierRight;
    }

    public float getTargetModifierLeft() {
        return this.targetModifierLeft;
    }

    public float getTargetModifierRight() {
        return this.targetModifierRight;
    }

    public float getSourceSpeed() {
        return Math.abs(getTheoreticalSpeed());
    }

    public float getTransmittedSpeedLeft() {
        return Math.min(getEffectiveMaxRPM(), getSourceSpeed() * this.currentModifierLeft);
    }

    public float getTransmittedSpeedRight() {
        return Math.min(getEffectiveMaxRPM(), getSourceSpeed() * this.currentModifierRight);
    }

    public boolean hasStressNetwork() {
        return hasNetwork();
    }

    public boolean isNetworkRunning() {
        return hasNetwork() && getTheoreticalSpeed() != 0.0f;
    }

    public boolean isBalancing() {
        if (!this.settings.isEnabled()) return false;

        boolean leftActive = this.leftBranchAttached
                && Math.abs(this.currentModifierLeft - MODIFIER_NEUTRAL) > MODIFIER_EPSILON;
        boolean rightActive = this.rightBranchAttached
                && Math.abs(this.currentModifierRight - MODIFIER_NEUTRAL) > MODIFIER_EPSILON;
        return leftActive || rightActive;
    }

    public int getAnalogOutputSignal() {
        return Mth.clamp(Math.round(this.lastStressPercent / 100.0f * 15.0f), 0, 15);
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

    public boolean isLastOverstressed() {
        return this.lastOverstressed;
    }

    public float getLastLeftStressSU() {
        return this.lastLeftStressSU;
    }

    public float getLastRightStressSU() {
        return this.lastRightStressSU;
    }

    public float getLastLeftBudgetSU() {
        return this.lastLeftBudgetSU;
    }

    public float getLastRightBudgetSU() {
        return this.lastRightBudgetSU;
    }

    public float getLastBaseStressSU() {
        return this.lastBaseStressSU;
    }

    public boolean isLeftBranchAttached() {
        return this.leftBranchAttached;
    }

    public boolean isRightBranchAttached() {
        return this.rightBranchAttached;
    }

    public TopologyStatus getTopologyStatus() {
        return this.topologyStatus;
    }

    public int getLastTopologyNodeCount() {
        return this.lastTopologyNodeCount;
    }

    public int getTopologyNodeLimit() {
        return this.topologyNodeLimit;
    }

    public ReportedState getReportedState() {
        if (!this.settings.isEnabled()) {
            return ReportedState.DISABLED;
        }
        return switch (this.topologyStatus) {
            case TRUNCATED -> ReportedState.TOPOLOGY_LIMIT;
            case SIDE_INPUT -> ReportedState.WRONG_INPUT;
            case MERGED_BRANCHES -> ReportedState.MERGED;
            case BRANCH_SOURCE -> ReportedState.BRANCH_SOURCE;
            case NO_BRANCHES -> ReportedState.NO_BRANCHES;
            case NO_SOURCE -> ReportedState.IDLE;

            case OK -> !this.leftBranchAttached || !this.rightBranchAttached
                    ? ReportedState.ONE_BRANCH
                    : isBalancing() ? ReportedState.BALANCING : ReportedState.PASS_THROUGH;
        };
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        final ReportedState state = getReportedState();
        tooltip.add(Component.literal("  ").append(
                Component.translatable("block.create_stats.load_balancer").withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.literal("    ").append(Component.translatable(state.stateKey())));

        tooltip.add(Component.literal("    ").append(Component.translatable(
                "create_stats.load_balancer.display.split",
                fmt0(this.settings.getLeftSharePct()),
                fmt0(this.settings.getRightSharePct()),
                this.settings.getSharingMode().getDisplayName())));

        if (this.leftBranchAttached || this.rightBranchAttached) {
            tooltip.add(Component.literal("    ").append(Component.translatable(
                    "create_stats.load_balancer.display.lr",
                    fmt0(getTransmittedSpeedLeft()), fmt0(getTransmittedSpeedRight()),
                    fmt0(this.lastLeftStressSU), fmt0(this.lastRightStressSU))));
            tooltip.add(Component.literal("    ").append(Component.translatable(
                    "create_stats.load_balancer.display.load",
                    String.format(Locale.ROOT, "%.1f", this.lastStressPercent),
                    fmt0(this.lastLeftBudgetSU), fmt0(this.lastRightBudgetSU))));
        }

        if (state.severity() != Severity.OK) {
            tooltip.add(Component.literal("    ").append(
                    Component.translatable(state.hintKey()).withStyle(state.severity().formatting())));
        }
        return true;
    }

    private static String fmt0(float value) {
        return String.format(Locale.ROOT, "%.0f", value);
    }

    private void updateVisualStatus() {
        if (this.level == null || this.level.isClientSide()) return;
        BlockState state = getBlockState();
        if (!state.hasProperty(LoadBalancerBlock.VISUAL_STATUS)) return;
        int desired = computeVisualStatus();
        if (state.getValue(LoadBalancerBlock.VISUAL_STATUS) != desired) {
            this.level.setBlock(this.worldPosition,
                    state.setValue(LoadBalancerBlock.VISUAL_STATUS, desired), 2);
        }
    }

    private int computeVisualStatus() {
        return getReportedState().severity().casingVariant();
    }

    private void recalculateTargets() {
        this.leftBranchAttached = hasPhysicalBranch(getLeftOutputFacing());
        this.rightBranchAttached = hasPhysicalBranch(getRightOutputFacing());
        this.topologyNodeLimit = ModConfiguration.COMMON.topologyMaxNodes.get();

        if (!hasNetwork() || (getSpeed() == 0.0f && !isOverStressed())) {
            this.topologyStatus = TopologyStatus.NO_SOURCE;
            this.lastLeftStressSU = 0.0f;
            this.lastRightStressSU = 0.0f;
            this.lastBaseStressSU = 0.0f;
            this.lastLeftBudgetSU = 0.0f;
            this.lastRightBudgetSU = 0.0f;
            this.lastTopologyNodeCount = 0;
            return;
        }

        float sourceSpeed = getSourceSpeed();
        if (sourceSpeed <= 0.0f) {
            this.topologyStatus = TopologyStatus.NO_SOURCE;
            this.lastLeftStressSU = 0.0f;
            this.lastRightStressSU = 0.0f;
            this.lastBaseStressSU = 0.0f;
            this.lastLeftBudgetSU = 0.0f;
            this.lastRightBudgetSU = 0.0f;
            this.lastTopologyNodeCount = 0;
            return;
        }

        if (hasConfirmedSideInput()) {
            this.topologyStatus = TopologyStatus.SIDE_INPUT;
            this.targetModifierLeft = MODIFIER_NEUTRAL;
            this.targetModifierRight = MODIFIER_NEUTRAL;
            this.lastLeftBudgetSU = 0.0f;
            this.lastRightBudgetSU = 0.0f;
            this.lastTopologyNodeCount = 0;
            return;
        }

        BranchSnapshot snapshot = measureBranches();
        this.topologyStatus = snapshot.status;
        this.leftBranchAttached = snapshot.leftAttached;
        this.rightBranchAttached = snapshot.rightAttached;
        this.lastLeftStressSU = snapshot.leftStress;
        this.lastRightStressSU = snapshot.rightStress;
        this.lastBaseStressSU = snapshot.baseStress;

        if (snapshot.status == TopologyStatus.NO_BRANCHES) {
            this.targetModifierLeft = 0.0f;
            this.targetModifierRight = 0.0f;
            this.lastLeftBudgetSU = 0.0f;
            this.lastRightBudgetSU = 0.0f;
            return;
        }

        if (!snapshot.status.isControllable()) {
            this.targetModifierLeft = MODIFIER_NEUTRAL;
            this.targetModifierRight = MODIFIER_NEUTRAL;
            this.lastLeftBudgetSU = 0.0f;
            this.lastRightBudgetSU = 0.0f;
            return;
        }

        float totalCapacity = this.lastCapacitySU > 0.0f ? this.lastCapacitySU : snapshot.totalCapacity;
        float totalLowSU;
        float totalHighSU;
        if (this.settings.getLimitMode() == LoadBalancerSettings.LimitMode.PERCENTAGE) {
            if (totalCapacity <= 0.0f) {
                this.targetModifierLeft = this.currentModifierLeft;
                this.targetModifierRight = this.currentModifierRight;
                return;
            }
            totalLowSU = totalCapacity * this.settings.getLowerBound() / 100.0f;
            totalHighSU = totalCapacity * this.settings.getUpperBound() / 100.0f;
        } else {
            totalLowSU = this.settings.getLowerBound();
            totalHighSU = this.settings.getUpperBound();
        }

        float shareLeft = this.settings.getLeftSharePct() / 100.0f;
        float shareRight = 1.0f - shareLeft;
        float leftLow = totalLowSU * shareLeft;
        float rightLow = totalLowSU * shareRight;
        float leftHigh = totalHighSU * shareLeft;
        float rightHigh = totalHighSU * shareRight;

        if (this.settings.isBorrowingEnabled()) {
            leftHigh = Math.max(leftHigh, Math.max(0.0f, totalHighSU - snapshot.baseStress - snapshot.rightStress));
            rightHigh = Math.max(rightHigh, Math.max(0.0f, totalHighSU - snapshot.baseStress - snapshot.leftStress));
        }

        this.lastLeftBudgetSU = leftHigh;
        this.lastRightBudgetSU = rightHigh;

        float maxAllowedModifier = getMaxAllowedModifier(sourceSpeed);
        float leftMaxAllowedModifier = getGuardedMaxAllowedModifier(sourceSpeed, maxAllowedModifier,
                resolveGuardReferenceOutputRPM(this.lastKnownOutputRPMLeft, sourceSpeed, this.currentModifierLeft));
        float rightMaxAllowedModifier = getGuardedMaxAllowedModifier(sourceSpeed, maxAllowedModifier,
                resolveGuardReferenceOutputRPM(this.lastKnownOutputRPMRight, sourceSpeed, this.currentModifierRight));
        boolean preferHighFill = this.settings.isBorrowingEnabled();
        float idealLeft = solveBranchTarget(this.currentModifierLeft, snapshot.leftAttached, snapshot.leftMeasured,
                snapshot.leftStress, leftLow, leftHigh, sourceSpeed, preferHighFill);
        float idealRight = solveBranchTarget(this.currentModifierRight, snapshot.rightAttached, snapshot.rightMeasured,
                snapshot.rightStress, rightLow, rightHigh, sourceSpeed, preferHighFill);

        idealLeft = clampRawModifier(idealLeft, leftMaxAllowedModifier);
        idealRight = clampRawModifier(idealRight, rightMaxAllowedModifier);

        float predictedLeft = predictBranchStress(snapshot.leftMeasured, snapshot.leftStress, this.currentModifierLeft, idealLeft);
        float predictedRight = predictBranchStress(snapshot.rightMeasured, snapshot.rightStress, this.currentModifierRight, idealRight);
        float availableStress = Math.max(0.0f, totalHighSU - snapshot.baseStress);
        if (predictedLeft + predictedRight > availableStress && predictedLeft + predictedRight > 0.0f) {
            float scale = availableStress / (predictedLeft + predictedRight);
            idealLeft *= scale;
            idealRight *= scale;
        }

        this.targetModifierLeft = snapshot.leftAttached ? clampModifier(idealLeft, sourceSpeed, leftMaxAllowedModifier) : 0.0f;
        this.targetModifierRight = snapshot.rightAttached ? clampModifier(idealRight, sourceSpeed, rightMaxAllowedModifier) : 0.0f;
    }

    private float solveBranchTarget(float currentModifier, boolean attached, boolean measured, float metric,
                                    float lowBudget, float highBudget, float sourceSpeed, boolean preferHighFill) {
        if (!attached || highBudget <= MODIFIER_EPSILON) {
            return 0.0f;
        }

        if (!measured) {
            return getPilotModifier(sourceSpeed);
        }

        float base = currentModifier > MODIFIER_EPSILON ? currentModifier : getPilotModifier(sourceSpeed);
        if (metric <= MODIFIER_EPSILON) {
            return Math.max(base, getPilotModifier(sourceSpeed));
        }
        if (metric > highBudget && highBudget > 0.0f) {
            return base * (highBudget / metric);
        }
        if (preferHighFill && metric < highBudget && highBudget > 0.0f) {
            return base * (highBudget / metric);
        }
        if (metric < lowBudget && lowBudget > 0.0f) {
            return base * (lowBudget / metric);
        }
        return base;
    }

    private float predictBranchStress(boolean measured, float metric, float currentModifier, float nextModifier) {
        if (!measured || metric <= MODIFIER_EPSILON || nextModifier <= MODIFIER_EPSILON) {
            return 0.0f;
        }
        if (currentModifier <= MODIFIER_EPSILON) {
            return metric;
        }
        return metric * (nextModifier / currentModifier);
    }

    private BranchSnapshot measureBranches() {
        this.topologyNodeLimit = ModConfiguration.COMMON.topologyMaxNodes.get();
        if (!this.leftBranchAttached && !this.rightBranchAttached) {
            this.lastTopologyNodeCount = 0;
            return new BranchSnapshot(false, false, false, false, 0.0f, 0.0f, 0.0f, 0.0f, TopologyStatus.NO_BRANCHES);
        }

        NetworkGraph graph = NetworkDiscovery.discover(level, worldPosition);
        this.lastTopologyNodeCount = graph.getNodes().size();
        if (graph.isEmpty()) {
            return new BranchSnapshot(this.leftBranchAttached, this.rightBranchAttached, false, false,
                    0.0f, 0.0f, Math.max(0.0f, this.lastStressSU), this.lastCapacitySU, TopologyStatus.OK);
        }

        if (graph.isTruncated()) {
            return new BranchSnapshot(this.leftBranchAttached, this.rightBranchAttached, false, false,
                    0.0f, 0.0f, Math.max(0.0f, this.lastStressSU), graph.getTotalCapacity(), TopologyStatus.TRUNCATED);
        }

        BlockPos leftRoot = worldPosition.relative(getLeftOutputFacing());
        BlockPos rightRoot = worldPosition.relative(getRightOutputFacing());
        Map<BlockPos, Set<BlockPos>> adjacency = buildAdjacency(graph.getEdges());

        Set<BlockPos> leftNodes = this.leftBranchAttached ? collectBranchNodes(leftRoot, graph.getNodes(), adjacency) : Collections.emptySet();
        Set<BlockPos> rightNodes = this.rightBranchAttached ? collectBranchNodes(rightRoot, graph.getNodes(), adjacency) : Collections.emptySet();

        Set<BlockPos> overlap = new LinkedHashSet<>(leftNodes);
        overlap.retainAll(rightNodes);
        if (!overlap.isEmpty()) {
            return new BranchSnapshot(this.leftBranchAttached, this.rightBranchAttached,
                    graph.getNodes().containsKey(leftRoot), graph.getNodes().containsKey(rightRoot),
                    0.0f, 0.0f, Math.max(0.0f, this.lastStressSU), graph.getTotalCapacity(), TopologyStatus.MERGED_BRANCHES);
        }

        if (containsForbiddenNode(leftNodes, graph.getNodes()) || containsForbiddenNode(rightNodes, graph.getNodes())) {
            return new BranchSnapshot(this.leftBranchAttached, this.rightBranchAttached,
                    graph.getNodes().containsKey(leftRoot), graph.getNodes().containsKey(rightRoot),
                    0.0f, 0.0f, Math.max(0.0f, this.lastStressSU), graph.getTotalCapacity(), TopologyStatus.BRANCH_SOURCE);
        }

        float leftStress = sumStress(leftNodes, graph.getNodes());
        float rightStress = sumStress(rightNodes, graph.getNodes());
        float totalStress = this.lastStressSU > 0.0f ? this.lastStressSU : graph.getTotalStress();
        float baseStress = Math.max(0.0f, totalStress - leftStress - rightStress);

        return new BranchSnapshot(this.leftBranchAttached, this.rightBranchAttached,
                graph.getNodes().containsKey(leftRoot), graph.getNodes().containsKey(rightRoot),
                leftStress, rightStress, baseStress, graph.getTotalCapacity(), TopologyStatus.OK);
    }

    private Map<BlockPos, Set<BlockPos>> buildAdjacency(List<TopologyEdge> edges) {
        Map<BlockPos, Set<BlockPos>> adjacency = new HashMap<>();
        for (TopologyEdge edge : edges) {
            adjacency.computeIfAbsent(edge.getFrom(), key -> new LinkedHashSet<>()).add(edge.getTo());
            adjacency.computeIfAbsent(edge.getTo(), key -> new LinkedHashSet<>()).add(edge.getFrom());
        }
        return adjacency;
    }

    private Set<BlockPos> collectBranchNodes(BlockPos root, Map<BlockPos, TopologyNode> nodes, Map<BlockPos, Set<BlockPos>> adjacency) {
        if (!nodes.containsKey(root)) {
            return Collections.emptySet();
        }

        Set<BlockPos> visited = new LinkedHashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.addLast(root);
        visited.add(root);

        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            for (BlockPos next : adjacency.getOrDefault(current, Collections.emptySet())) {
                if (next.equals(worldPosition) || !nodes.containsKey(next) || !visited.add(next)) {
                    continue;
                }
                queue.addLast(next);
            }
        }

        return visited;
    }

    private boolean containsForbiddenNode(Set<BlockPos> branchNodes, Map<BlockPos, TopologyNode> nodes) {
        for (BlockPos pos : branchNodes) {
            TopologyNode node = nodes.get(pos);
            if (node == null) {
                continue;
            }
            if (node.isSourceLike() || node.getType() == NodeType.SPEED_CONTROLLER) {
                return true;
            }
        }
        return false;
    }

    private float sumStress(Set<BlockPos> branchNodes, Map<BlockPos, TopologyNode> nodes) {
        float total = 0.0f;
        for (BlockPos pos : branchNodes) {
            TopologyNode node = nodes.get(pos);
            if (node != null) {
                total += node.getStressImpact();
            }
        }
        return total;
    }

    private boolean hasPhysicalBranch(Direction face) {
        if (level == null) {
            return false;
        }

        BlockPos neighbourPos = worldPosition.relative(face);
        BlockEntity neighbour = level.getBlockEntity(neighbourPos);
        if (!(neighbour instanceof KineticBlockEntity)) {
            return false;
        }

        BlockState selfState = getBlockState();
        BlockState neighbourState = neighbour.getBlockState();
        if (!(selfState.getBlock() instanceof IRotate selfRotate)
                || !(neighbourState.getBlock() instanceof IRotate neighbourRotate)) {
            return false;
        }

        return selfRotate.hasShaftTowards(level, worldPosition, selfState, face)
                && neighbourRotate.hasShaftTowards(level, neighbourPos, neighbourState, face.getOpposite());
    }

    private float getMaxAllowedModifier(float sourceSpeed) {
        if (sourceSpeed <= 0.0f) {
            return MODIFIER_NEUTRAL;
        }
        return Math.max(0.0f, getEffectiveMaxRPM() / sourceSpeed);
    }

    private float resolveGuardReferenceOutputRPM(float rememberedOutputRPM, float sourceSpeed, float currentModifier) {
        if (rememberedOutputRPM > MODIFIER_EPSILON) {
            return rememberedOutputRPM;
        }
        return getOutputRPMForSource(currentModifier, sourceSpeed);
    }

    private float getGuardedMaxAllowedModifier(float sourceSpeed, float maxAllowedModifier, float referenceOutputRPM) {
        if (!this.lastOverstressed || sourceSpeed <= 0.0f) {
            return maxAllowedModifier;
        }

        float guardedOutputRPM = Mth.clamp((float) Math.ceil(referenceOutputRPM * 0.5f), 0.0f, getEffectiveMaxRPM());
        if (guardedOutputRPM <= 0.0f) {
            return 0.0f;
        }
        return Math.min(maxAllowedModifier, guardedOutputRPM / sourceSpeed);
    }

    private void rememberLastKnownOutputRPMs(float sourceSpeed) {
        this.lastKnownOutputRPMLeft = getOutputRPMForSource(this.currentModifierLeft, sourceSpeed);
        this.lastKnownOutputRPMRight = getOutputRPMForSource(this.currentModifierRight, sourceSpeed);
    }

    private float clampRawModifier(float modifier, float maxAllowedModifier) {
        return Math.max(0.0f, Math.min(maxAllowedModifier, modifier));
    }

    private float clampModifier(float modifier, float sourceSpeed, float maxAllowedModifier) {
        return snapToIntegerRPM(clampRawModifier(modifier, maxAllowedModifier), sourceSpeed);
    }

    private float getOutputRPMForSource(float modifier, float sourceSpeed) {
        if (modifier <= MODIFIER_EPSILON || sourceSpeed <= 0.0f) {
            return 0.0f;
        }
        return Mth.clamp(sourceSpeed * modifier, 0.0f, getEffectiveMaxRPM());
    }

    private float preserveOutputModifier(float modifier, float previousSourceSpeed, float newSourceSpeed, float maxAllowedModifier) {
        if (newSourceSpeed <= 0.0f) {
            return 0.0f;
        }
        if (previousSourceSpeed <= 0.0f) {
            return clampModifier(modifier, newSourceSpeed, maxAllowedModifier);
        }
        float preservedOutputRPM = getOutputRPMForSource(modifier, previousSourceSpeed);
        if (preservedOutputRPM <= MODIFIER_EPSILON) {
            return 0.0f;
        }
        return clampModifier(preservedOutputRPM / newSourceSpeed, newSourceSpeed, maxAllowedModifier);
    }

    private float stepTowardsModifier(float currentModifier, float targetModifier, float sourceSpeed, float maxAllowedModifier) {
        float next = currentModifier;
        if (currentModifier > targetModifier) {
            next = targetModifier;
        } else if (currentModifier < targetModifier) {
            if (Math.abs(targetModifier - currentModifier) <= STEP_SIZE) {
                next = targetModifier;
            } else {
                next = currentModifier + STEP_SIZE;
            }
        }
        return clampModifier(next, sourceSpeed, maxAllowedModifier);
    }

    private float snapToIntegerRPM(float modifier, float sourceSpeed) {
        if (modifier <= MODIFIER_EPSILON) {
            return 0.0f;
        }
        if (sourceSpeed < 1.0f) {
            return modifier;
        }
        float outputRPM = Math.round(sourceSpeed * modifier);
        outputRPM = Mth.clamp(outputRPM, 0.0f, getEffectiveMaxRPM());
        if (outputRPM <= 0.0f) {
            return 0.0f;
        }
        return outputRPM / sourceSpeed;
    }

    private float getPilotModifier(float sourceSpeed) {
        if (sourceSpeed <= 0.0f) {
            return 0.0f;
        }
        return snapToIntegerRPM(Math.min(PILOT_RPM, getEffectiveMaxRPM()) / sourceSpeed, sourceSpeed);
    }

    private void trackObservedSourceFacing() {
        Direction currentObservedSource = null;
        if (hasSource() && getSpeed() != 0.0f) {
            currentObservedSource = getSourceFacing();
        }

        if (currentObservedSource == this.observedSourceFacing) {
            if (currentObservedSource == null) {
                this.observedSourceTicks = 0;
            } else if (this.observedSourceTicks < SOURCE_DIRECTION_CONFIRM_TICKS) {
                this.observedSourceTicks++;
            }
            return;
        }

        this.observedSourceFacing = currentObservedSource;
        this.observedSourceTicks = currentObservedSource == null ? 0 : 1;
    }

    private boolean hasConfirmedSideInput() {
        return this.observedSourceFacing != null
                && this.observedSourceFacing != getInputFacing()
                && this.observedSourceTicks >= SOURCE_DIRECTION_CONFIRM_TICKS;
    }

    public float getEffectiveMaxRPM() {
        return Math.min(MAX_OUTPUT_RPM, this.settings.getMaximumRPM());
    }

    public void syncNetworkState() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (hasNetwork()) {
            getOrCreateNetwork().updateNetwork();
        }
        refreshNetworkSnapshot();
        updateComparatorOutput();
    }

    private void markDirty() {
        this.dirty = true;
        this.dirtyTimer = 0;
    }

    private void applyModifierChange(float oldLeftModifier, float oldRightModifier) {
        if (level == null || level.isClientSide() || inModifierChange) {
            return;
        }
        if (inNetworkCallback) {
            this.pendingModifierRebuild = true;
            setChanged();
            notifyUpdate();
            return;
        }
        performModifierRebuild();
    }

    private void processPendingModifierRebuild() {
        if (this.pendingModifierRebuild) {
            this.pendingModifierRebuild = false;
            performModifierRebuild();
        }
    }

    private void performModifierRebuild() {
        this.inModifierChange = true;
        try {
            detachKinetics();
            attachKinetics();

            this.observedSourceFacing = null;
            this.observedSourceTicks = 0;
            syncNetworkState();
            recalculateTargets();
            notifyUpdate();
        } finally {
            this.inModifierChange = false;
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
        this.lastStressSU = stress;
        this.lastCapacitySU = capacity;
        this.lastOverstressed = overstressed;
        this.lastStressPercent = capacity > 0.0f ? (stress / capacity) * 100.0f : 0.0f;
    }

    private void updateComparatorOutput() {
        if (level == null || level.isClientSide()) {
            return;
        }
        int newLevel = getAnalogOutputSignal();
        if (newLevel != this.lastComparatorLevel) {
            this.lastComparatorLevel = newLevel;
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    private Direction getInputFacing() {
        return LoadBalancerBlock.getInputFacing(getBlockState());
    }

    private Direction getLeftOutputFacing() {
        return LoadBalancerBlock.getLeftOutputFacing(getBlockState());
    }

    private Direction getRightOutputFacing() {
        return LoadBalancerBlock.getRightOutputFacing(getBlockState());
    }

    @Override
    public void updateFromNetwork(float maxStress, float currentStress, int networkSize) {
        float prevStress = this.lastStressSU;
        float prevCapacity = this.lastCapacitySU;
        boolean prevOverstressed = this.lastOverstressed;

        super.updateFromNetwork(maxStress, currentStress, networkSize);
        storeSnapshot(currentStress, maxStress, isOverStressed());
        markDirty();
        updateComparatorOutput();

        boolean snapshotChanged = Float.compare(prevStress, this.lastStressSU) != 0
                || Float.compare(prevCapacity, this.lastCapacitySU) != 0
                || prevOverstressed != this.lastOverstressed;
        if (snapshotChanged) {
            if (level != null && !level.isClientSide() && !inModifierChange) {
                sendData();
            }
            setChanged();
        }
    }

    @Override
    public void onSpeedChanged(float prevSpeed) {
        this.inNetworkCallback = true;
        try {
            super.onSpeedChanged(prevSpeed);

            if (inModifierChange) {
                return;
            }

            if (level != null && !level.isClientSide() && getSpeed() == 0.0f && !isOverStressed()) {
                this.topologyStatus = TopologyStatus.NO_SOURCE;
            }

            float previousSourceSpeed = Math.abs(prevSpeed);
            float sourceSpeed = getSourceSpeed();
            if (sourceSpeed > 0.0f) {
                float maxAllowedModifier = getMaxAllowedModifier(sourceSpeed);
                float leftMaxAllowedModifier = getGuardedMaxAllowedModifier(sourceSpeed, maxAllowedModifier,
                        resolveGuardReferenceOutputRPM(this.lastKnownOutputRPMLeft, sourceSpeed, this.currentModifierLeft));
                float rightMaxAllowedModifier = getGuardedMaxAllowedModifier(sourceSpeed, maxAllowedModifier,
                        resolveGuardReferenceOutputRPM(this.lastKnownOutputRPMRight, sourceSpeed, this.currentModifierRight));
                float oldLeft = this.currentModifierLeft;
                float oldRight = this.currentModifierRight;

                this.currentModifierLeft = preserveOutputModifier(this.currentModifierLeft, previousSourceSpeed, sourceSpeed, leftMaxAllowedModifier);
                this.currentModifierRight = preserveOutputModifier(this.currentModifierRight, previousSourceSpeed, sourceSpeed, rightMaxAllowedModifier);
                this.targetModifierLeft = preserveOutputModifier(this.targetModifierLeft, previousSourceSpeed, sourceSpeed, leftMaxAllowedModifier);
                this.targetModifierRight = preserveOutputModifier(this.targetModifierRight, previousSourceSpeed, sourceSpeed, rightMaxAllowedModifier);

                if (Float.compare(oldLeft, this.currentModifierLeft) != 0
                        || Float.compare(oldRight, this.currentModifierRight) != 0) {
                    applyModifierChange(oldLeft, oldRight);
                    this.stepCooldown = STEP_COOLDOWN_TICKS;
                }
            }

            rememberLastKnownOutputRPMs(sourceSpeed);

            setChanged();
        } finally {
            this.inNetworkCallback = false;
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("LoadBalancerSettings", this.settings.save());
        tag.putFloat("CurrentModifierLeft", this.currentModifierLeft);
        tag.putFloat("CurrentModifierRight", this.currentModifierRight);
        tag.putFloat("TargetModifierLeft", this.targetModifierLeft);
        tag.putFloat("TargetModifierRight", this.targetModifierRight);
        tag.putFloat("LastStressPct", this.lastStressPercent);
        tag.putFloat("LastStressSU", this.lastStressSU);
        tag.putFloat("LastCapacitySU", this.lastCapacitySU);
        tag.putBoolean("LastOverstressed", this.lastOverstressed);
        tag.putFloat("LastLeftStressSU", this.lastLeftStressSU);
        tag.putFloat("LastRightStressSU", this.lastRightStressSU);
        tag.putFloat("LastBaseStressSU", this.lastBaseStressSU);
        tag.putFloat("LastLeftBudgetSU", this.lastLeftBudgetSU);
        tag.putFloat("LastRightBudgetSU", this.lastRightBudgetSU);
        tag.putBoolean("LeftBranchAttached", this.leftBranchAttached);
        tag.putBoolean("RightBranchAttached", this.rightBranchAttached);
        tag.putInt("LastTopologyNodeCount", this.lastTopologyNodeCount);
        tag.putInt("TopologyNodeLimit", this.topologyNodeLimit);
        // client packets only, persisting it flickered on every chunk reload

        if (clientPacket) {
            tag.putString("TopologyStatus", this.topologyStatus.name());
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("LoadBalancerSettings")) {
            this.settings = LoadBalancerSettings.load(tag.getCompound("LoadBalancerSettings"));
        }
        this.currentModifierLeft = tag.contains("CurrentModifierLeft") ? tag.getFloat("CurrentModifierLeft") : MODIFIER_NEUTRAL;
        this.currentModifierRight = tag.contains("CurrentModifierRight") ? tag.getFloat("CurrentModifierRight") : MODIFIER_NEUTRAL;
        this.targetModifierLeft = tag.contains("TargetModifierLeft") ? tag.getFloat("TargetModifierLeft") : MODIFIER_NEUTRAL;
        this.targetModifierRight = tag.contains("TargetModifierRight") ? tag.getFloat("TargetModifierRight") : MODIFIER_NEUTRAL;
        this.lastStressPercent = tag.getFloat("LastStressPct");
        this.lastStressSU = tag.getFloat("LastStressSU");
        this.lastCapacitySU = tag.getFloat("LastCapacitySU");
        this.lastOverstressed = tag.getBoolean("LastOverstressed");
        this.lastLeftStressSU = tag.getFloat("LastLeftStressSU");
        this.lastRightStressSU = tag.getFloat("LastRightStressSU");
        this.lastBaseStressSU = tag.getFloat("LastBaseStressSU");
        this.lastLeftBudgetSU = tag.getFloat("LastLeftBudgetSU");
        this.lastRightBudgetSU = tag.getFloat("LastRightBudgetSU");
        this.leftBranchAttached = tag.getBoolean("LeftBranchAttached");
        this.rightBranchAttached = tag.getBoolean("RightBranchAttached");
        this.lastTopologyNodeCount = tag.getInt("LastTopologyNodeCount");
        this.topologyNodeLimit = tag.contains("TopologyNodeLimit") ? tag.getInt("TopologyNodeLimit") : ModConfiguration.COMMON.topologyMaxNodes.get();
        if (clientPacket) {
            try {
                this.topologyStatus = TopologyStatus.valueOf(tag.getString("TopologyStatus"));
            } catch (Exception ignored) {
                this.topologyStatus = TopologyStatus.NO_SOURCE;
            }
        }
        this.lastComparatorLevel = getAnalogOutputSignal();

        syncSplitDial();
    }

    public enum Severity {
        OK(1, ChatFormatting.GREEN),

        NOTICE(1, ChatFormatting.GRAY),

        INCOMPLETE(2, ChatFormatting.YELLOW),

        FAULT(3, ChatFormatting.RED),

        LIMIT(4, ChatFormatting.RED);

        private final int casingVariant;
        private final ChatFormatting formatting;

        Severity(int casingVariant, ChatFormatting formatting) {
            this.casingVariant = casingVariant;
            this.formatting = formatting;
        }

        public int casingVariant() {
            return this.casingVariant;
        }

        public ChatFormatting formatting() {
            return this.formatting;
        }
    }

    public enum ReportedState {
        DISABLED("disabled", Severity.NOTICE),
        IDLE("idle", Severity.NOTICE),
        PASS_THROUGH("pass_through", Severity.OK),
        BALANCING("balancing", Severity.OK),
        NO_BRANCHES("no_branches", Severity.INCOMPLETE),
        ONE_BRANCH("one_branch", Severity.INCOMPLETE),
        TOPOLOGY_LIMIT("topology_limit", Severity.LIMIT),
        WRONG_INPUT("wrong_input", Severity.FAULT),
        MERGED("merged", Severity.FAULT),
        BRANCH_SOURCE("branch_source", Severity.FAULT);

        private final String id;
        private final Severity severity;

        ReportedState(String id, Severity severity) {
            this.id = id;
            this.severity = severity;
        }

        public Severity severity() {
            return this.severity;
        }

        public String stateKey() {
            return "create_stats.load_balancer.display.state." + this.id;
        }

        public String hintKey() {
            return "create_stats.load_balancer.hint." + this.id;
        }
    }

    public enum TopologyStatus {
        OK("Ready", true),
        NO_SOURCE("Idle", false),
        NO_BRANCHES("No branches", true),
        SIDE_INPUT("Wrong input", false),
        MERGED_BRANCHES("Merged branches", false),
        BRANCH_SOURCE("Branch source", false),
        TRUNCATED("Topology limit", false);

        private final String displayName;
        private final boolean controllable;

        TopologyStatus(String displayName, boolean controllable) {
            this.displayName = displayName;
            this.controllable = controllable;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public boolean isControllable() {
            return this.controllable;
        }
    }

    private static final class BranchSnapshot {
        private final boolean leftAttached;
        private final boolean rightAttached;
        private final boolean leftMeasured;
        private final boolean rightMeasured;
        private final float leftStress;
        private final float rightStress;
        private final float baseStress;
        private final float totalCapacity;
        private final TopologyStatus status;

        private BranchSnapshot(boolean leftAttached, boolean rightAttached, boolean leftMeasured, boolean rightMeasured,
                               float leftStress, float rightStress, float baseStress, float totalCapacity,
                               TopologyStatus status) {
            this.leftAttached = leftAttached;
            this.rightAttached = rightAttached;
            this.leftMeasured = leftMeasured;
            this.rightMeasured = rightMeasured;
            this.leftStress = leftStress;
            this.rightStress = rightStress;
            this.baseStress = baseStress;
            this.totalCapacity = totalCapacity;
            this.status = status;
        }
    }
}
