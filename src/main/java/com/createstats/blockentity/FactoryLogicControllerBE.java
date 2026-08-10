package com.createstats.blockentity;

import com.createstats.antenna.AntennaRange;
import com.createstats.antenna.AntennaTier;
import com.createstats.antenna.RangeAttachment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

import com.createstats.factorylogic.MachineSelectionCardData;
import com.createstats.factorylogic.SafetyClutchLinkData;
import com.createstats.item.MachineSelectionCardItem;
import com.createstats.item.SafetyClutchLinkCardItem;
import com.createstats.menu.FactoryLogicControllerMenu;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelBlockEntity;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlock;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity;
import com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity;
import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.content.processing.basin.BasinOperatingBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.minecraft.core.registries.BuiltInRegistries;

public class FactoryLogicControllerBE extends BlockEntity implements MenuProvider, RangeAttachment, com.createstats.power.IFeConsumer {

    /** FE Dependent Mode buffer. Receive-only; exposed as a block capability via FeCapabilities. */
    private final com.createstats.power.FeConsumer feConsumer = com.createstats.power.FeConsumer
            .forBlockEntity(this, com.createstats.power.FeProfiles.settings(com.createstats.power.FeProfiles.KEY_FACTORY_LOGIC));

    public com.createstats.power.FeConsumer feConsumer() {
        return feConsumer;
    }

    public static final int RULE_COUNT = 16;
    public static final int MIN_VISIBLE_ROWS = 3;
    public static final int SLOT_COUNT = RULE_COUNT * 2;
    public static final int IDLE_TIMEOUT_GRACE_SECONDS = 5;
    private static final int MECHANICAL_SAW_INPUT_BUFFER_TICKS = 30;
    private static final int EVALUATION_INTERVAL_TICKS = 10;
    private static final int CLIENT_SYNC_INTERVAL_TICKS = 10;
    private static final int IDLE_TIMEOUT_GRACE_TICKS = IDLE_TIMEOUT_GRACE_SECONDS * 20;
    private static final ResourceLocation MECHANICAL_SAW_ID = ResourceLocation.fromNamespaceAndPath("create", "mechanical_saw");
    private static final int MAX_SCAN_VOLUME = MachineSelectionCardData.MAX_AREA_BLOCKS;
    private static final int RUN_SIGNAL = 15;
    private static final int STOP_SIGNAL = 0;
    /** Sequential bank: hand off to the next unit once the active one drops to this charge percent. */
    private static final float SEQUENTIAL_ADVANCE_PCT = 2.0f;
    private static final int TARGET_RPM_MIN = 16;
    private static final int TARGET_RPM_MAX = 256;
    private static final int TARGET_RPM_STEP = 16;

    private final ItemStackHandler inventory;
    private final RuleRuntime[] runtimes;
    private int evaluationTimer;
    private int clientSyncTimer;
    private boolean needsClientSync;
    private AntennaTier antennaTier;
    private BankStrategy bankStrategy = BankStrategy.PARALLEL;
    private int targetRpm = 64;
    /** Rebuilt each evaluation pass: the flywheel rows currently running (not manually paused). */
    private final List<FlywheelMember> flywheelMembers = new ArrayList<>();

    public FactoryLogicControllerBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.inventory = new ItemStackHandler(SLOT_COUNT) {
            @Override
            protected void onContentsChanged(int slot) {
                Level level = FactoryLogicControllerBE.this.level;
                if (level != null && !level.isClientSide()) {
                    FactoryLogicControllerBE.this.setChanged();
                    resetRuntimeForSlot(slot);
                    markForClientSync();
                }
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                if (slot % 2 == 0) {
                    return stack.getItem() instanceof MachineSelectionCardItem;
                }
                return stack.getItem() instanceof SafetyClutchLinkCardItem;
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }
        };
        this.runtimes = new RuleRuntime[RULE_COUNT];
        for (int i = 0; i < RULE_COUNT; i++) {
            this.runtimes[i] = new RuleRuntime();
        }
        this.antennaTier = AntennaTier.NONE;
    }

    @Override
    public AntennaTier getAntennaTier() {
        return this.antennaTier;
    }

    @Override
    public void setAntennaTier(AntennaTier tier) {
        AntennaTier resolvedTier = tier == null ? AntennaTier.NONE : tier;
        if (this.antennaTier == resolvedTier) {
            return;
        }
        this.antennaTier = resolvedTier;
        this.setChanged();
        this.markForClientSync();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public void tick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        // FE Dependent Mode gate: a dead controller stops evaluating rules and coordinating
        // flywheel banks until the buffer is charged again.
        final boolean fePowered = this.feConsumer.serviceTick(true);
        com.createstats.power.FePowerFx.handleTransition(this.level, this.worldPosition, this.feConsumer);
        if (!fePowered) {
            return;
        }
        if (++this.evaluationTimer >= EVALUATION_INTERVAL_TICKS) {
            this.evaluationTimer = 0;
            this.flywheelMembers.clear();
            for (int row = 0; row < RULE_COUNT; row++) {
                this.evaluateRow(row);
            }
            this.coordinateFlywheelBanks();
        }
        if (this.needsClientSync && ++this.clientSyncTimer >= CLIENT_SYNC_INTERVAL_TICKS) {
            this.syncToClient();
        }
    }

    public ItemStackHandler getInventory() {
        return this.inventory;
    }

    public ItemStack getMachineCardStack(int row) {
        return this.inventory.getStackInSlot(getMachineSlotIndex(row));
    }

    public ItemStack getClutchLinkStack(int row) {
        return this.inventory.getStackInSlot(getLinkSlotIndex(row));
    }

    public RuleRuntime getRuntime(int row) {
        return this.runtimes[row];
    }

    public ScreenCellState getScreenCellState(int row) {
        if (row < 0 || row >= RULE_COUNT || !this.isRowUsed(row)) {
            return ScreenCellState.UNUSED;
        }
        return switch (this.runtimes[row].status) {
            case RUNNING -> ScreenCellState.RUNNING;
            case COUNTDOWN -> ScreenCellState.COUNTDOWN;
            case STOPPED -> ScreenCellState.STOPPED;
            case LINK_ERROR, AREA_ERROR -> ScreenCellState.ERROR;
            case NO_MACHINES -> ScreenCellState.NO_MACHINES;
            case INCOMPLETE -> ScreenCellState.INCOMPLETE;
        };
    }

    public boolean isRowUsed(int row) {
        if (row < 0 || row >= RULE_COUNT) {
            return false;
        }
        return !this.getMachineCardStack(row).isEmpty() || !this.getClutchLinkStack(row).isEmpty();
    }

    public int getVisibleRowCount() {
        int highestUsedRow = -1;
        for (int row = 0; row < RULE_COUNT; row++) {
            if (this.isRowUsed(row)) {
                highestUsedRow = row;
            }
        }
        return Math.max(MIN_VISIBLE_ROWS, Math.min(RULE_COUNT, highestUsedRow + 2));
    }

    public void adjustRowTimeout(int row, int deltaSeconds) {
        if (row < 0 || row >= RULE_COUNT) {
            return;
        }
        ItemStack stack = this.getMachineCardStack(row);
        if (!(stack.getItem() instanceof MachineSelectionCardItem)) {
            return;
        }
        MachineSelectionCardData data = MachineSelectionCardItem.getData(stack);
        data.adjustIdleTimeoutSeconds(deltaSeconds);
        MachineSelectionCardItem.setData(stack, data);
        this.setChanged();
        this.markForClientSync();
    }

    public void manualTripRow(int row) {
        SafetyClutchLinkData linkData = this.getLinkedTargetData(row);
        if (linkData == null || !this.applyLinkedState(row, linkData, false, STOP_SIGNAL)) {
            this.setRuntime(row, RuleStatus.LINK_ERROR, "Manual pause failed, clutch missing");
            return;
        }
        RuleRuntime runtime = this.runtimes[row];
        runtime.rememberLink(linkData);
        runtime.manualStopLatched = true;
        runtime.controllerHeldStopped = true;
        runtime.idleTicks = 0;
        runtime.status = RuleStatus.STOPPED;
        runtime.detail = "Manual pause latched until Reset";
        this.setChanged();
        this.markForClientSync();
    }

    public void manualResetRow(int row) {
        RuleRuntime runtime = this.runtimes[row];
        runtime.manualStopLatched = false;
        SafetyClutchLinkData linkData = this.getLinkedTargetData(row);
        if (linkData == null || !this.applyLinkedState(row, linkData, true, RUN_SIGNAL)) {
            this.setRuntime(row, RuleStatus.LINK_ERROR, "Manual reset failed, clutch missing");
            return;
        }
        runtime.rememberLink(linkData);
        runtime.controllerHeldStopped = false;
        runtime.idleTicks = 0;
        if (this.level != null) {
            runtime.lastActivityGameTime = this.level.getGameTime();
        }
        runtime.status = RuleStatus.RUNNING;
        runtime.detail = "Branch resumed";
        this.setChanged();
        this.markForClientSync();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.create_stats.factory_logic_controller");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new FactoryLogicControllerMenu(containerId, playerInventory, this);
    }

    public void dropContents() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        for (int slot = 0; slot < this.inventory.getSlots(); slot++) {
            ItemStack stack = this.inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(this.level, this.worldPosition.getX(), this.worldPosition.getY(),
                        this.worldPosition.getZ(), stack);
            }
        }
        for (int slot = 0; slot < this.inventory.getSlots(); slot++) {
            this.inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    public String getRowMachineLabel(int row) {
        if (this.isRowFlywheelLinked(row)) {
            return "Flywheel control (no machine)";
        }
        ItemStack stack = this.getMachineCardStack(row);
        if (!(stack.getItem() instanceof MachineSelectionCardItem)) {
            return "Insert machine card";
        }
        MachineSelectionCardData data = MachineSelectionCardItem.getData(stack);
        ResourceLocation blockId = data.getMachineBlockId();
        if (blockId == null) {
            return "Pick watched machine";
        }
        Block block = BuiltInRegistries.BLOCK.get(blockId);
        if (block == null) {
            return blockId.toString();
        }
        return Component.translatable(block.getDescriptionId()).getString();
    }

    public String getRowInfoLabel(int row) {
        if (this.isRowFlywheelLinked(row)) {
            return "Stands alone, use Pause/Run to hold or arm it";
        }
        ItemStack stack = this.getMachineCardStack(row);
        if (!(stack.getItem() instanceof MachineSelectionCardItem)) {
            return "Add a Machine Selection Card";
        }
        MachineSelectionCardData data = MachineSelectionCardItem.getData(stack);
        if (!data.hasMachineType()) {
            return "Sneak-right-click a machine with the card";
        }
        if (!data.hasArea()) {
            if (data.getFirstCorner() == null) {
                return "Use the card on a block to set corner A";
            }
            return "Use the card on another block to set corner B";
        }
        return data.getAreaSizeLabel() + "  |  timeout " + data.getIdleTimeoutSeconds() + "s";
    }

    public String getRowLinkLabel(int row) {
        ItemStack stack = this.getClutchLinkStack(row);
        if (!(stack.getItem() instanceof SafetyClutchLinkCardItem)) {
            return "No link card";
        }
        SafetyClutchLinkData data = SafetyClutchLinkCardItem.getData(stack);
        if (!data.isLinked()) {
            return "Link a clutch or flywheel";
        }
        BlockPos pos = data.getClutchPos();
        return data.getTargetDisplayName() + " @ " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    /** True when this row's link card targets a Kinetic Flywheel (so it skips the machine slot). */
    public boolean isRowFlywheelLinked(int row) {
        ItemStack stack = this.getClutchLinkStack(row);
        if (!(stack.getItem() instanceof SafetyClutchLinkCardItem)) {
            return false;
        }
        return SafetyClutchLinkCardItem.getData(stack).getTargetType() == SafetyClutchLinkData.TargetType.KINETIC_FLYWHEEL;
    }

    public BankStrategy getBankStrategy() {
        return this.bankStrategy;
    }

    public void cycleBankStrategy() {
        this.bankStrategy = this.bankStrategy.next();
        this.setChanged();
        this.markForClientSync();
    }

    /** True when at least one row's link card targets a Kinetic Flywheel (drives the bank UI). */
    public boolean hasFlywheelRows() {
        for (int row = 0; row < RULE_COUNT; row++) {
            if (this.isRowFlywheelLinked(row)) {
                return true;
            }
        }
        return false;
    }

    public int getTargetRpm() {
        return this.targetRpm;
    }

    public void adjustTargetRpm(int steps) {
        int next = Mth.clamp(this.targetRpm + steps * TARGET_RPM_STEP, TARGET_RPM_MIN, TARGET_RPM_MAX);
        if (next != this.targetRpm) {
            this.targetRpm = next;
            this.setChanged();
            this.markForClientSync();
        }
    }

    public String getRowStatusLabel(int row) {
        RuleRuntime runtime = this.runtimes[row];
        return runtime.status.displayName;
    }

    public String getRowDetailLabel(int row) {
        return this.runtimes[row].detail;
    }

    /** Pause is valid only when the row is actively running or counting down. */
    public boolean canManualPause(int row) {
        if (row < 0 || row >= RULE_COUNT) return false;
        RuleStatus s = this.runtimes[row].status;
        return s == RuleStatus.RUNNING || s == RuleStatus.COUNTDOWN;
    }

    /** Reset is valid when the row is stopped, or when NO_MACHINES is holding a stop. */
    public boolean canManualReset(int row) {
        if (row < 0 || row >= RULE_COUNT) return false;
        RuleRuntime rt = this.runtimes[row];
        return rt.status == RuleStatus.STOPPED
                || (rt.status == RuleStatus.NO_MACHINES && rt.isControllerHeldStopped());
    }

    public long getLastActivityTicksAgo(int row) {
        if (this.level == null) {
            return -1L;
        }
        long lastActivity = this.runtimes[row].lastActivityGameTime;
        if (lastActivity <= 0L) {
            return -1L;
        }
        return Math.max(0L, this.level.getGameTime() - lastActivity);
    }

    private void evaluateRow(int row) {
        ItemStack machineCardStack = this.getMachineCardStack(row);
        ItemStack clutchLinkStack = this.getClutchLinkStack(row);
        RuleRuntime runtime = this.runtimes[row];

        if (runtime.manualStopLatched) {
            SafetyClutchLinkData manualLink = this.getLinkedTargetData(row);
            if (manualLink == null) {
                this.releaseLinkedTarget(row);
                this.setRuntime(row, RuleStatus.LINK_ERROR, "Manual trip lost its clutch link");
                runtime.resetObservation();
                return;
            }
            runtime.rememberLink(manualLink);
            if (this.applyLinkedState(row, manualLink, false, STOP_SIGNAL)) {
                runtime.controllerHeldStopped = true;
                this.setRuntime(row, RuleStatus.STOPPED, "Manual pause latched until Reset");
            } else {
                this.releaseLinkedTarget(row);
                this.setRuntime(row, RuleStatus.LINK_ERROR, "Linked clutch is missing");
            }
            return;
        }

        // [1]
        if (clutchLinkStack.getItem() instanceof SafetyClutchLinkCardItem) {
            SafetyClutchLinkData linkProbe = SafetyClutchLinkCardItem.getData(clutchLinkStack);
            if (linkProbe.getTargetType() == SafetyClutchLinkData.TargetType.KINETIC_FLYWHEEL) {
                this.handleFlywheelRow(row, linkProbe);
                return;
            }
        }

        if (!(machineCardStack.getItem() instanceof MachineSelectionCardItem)) {
            this.releaseLinkedTarget(row);
            this.setRuntime(row, RuleStatus.INCOMPLETE, "Insert a machine card to define this row");
            runtime.resetObservation();
            return;
        }
        MachineSelectionCardData selection = MachineSelectionCardItem.getData(machineCardStack);
        if (!selection.hasMachineType()) {
            this.releaseLinkedTarget(row);
            this.setRuntime(row, RuleStatus.INCOMPLETE, "Sneak-right-click a machine with the card");
            runtime.resetObservation();
            return;
        }
        if (!selection.hasArea()) {
            this.releaseLinkedTarget(row);
            String detail = selection.getFirstCorner() == null
                    ? "Use the machine card on a block to set corner A"
                    : "Use the machine card on another block to set corner B";
            this.setRuntime(row, RuleStatus.INCOMPLETE, detail);
            runtime.resetObservation();
            return;
        }
        if (selection.hasDimensionMismatch(this.level.dimension().location())) {
            this.releaseLinkedTarget(row);
            this.setRuntime(row, RuleStatus.AREA_ERROR, "Marked area is in another dimension");
            runtime.resetObservation();
            return;
        }
        if (selection.isAreaTooLarge(MAX_SCAN_VOLUME)) {
            this.releaseLinkedTarget(row);
            this.setRuntime(row, RuleStatus.AREA_ERROR, "Marked area is too large");
            runtime.resetObservation();
            return;
        }

        if (!(clutchLinkStack.getItem() instanceof SafetyClutchLinkCardItem)) {
            this.releaseLinkedTarget(row);
            this.setRuntime(row, RuleStatus.LINK_ERROR, "Insert a clutch link card");
            runtime.resetObservation();
            return;
        }
        SafetyClutchLinkData clutchLink = SafetyClutchLinkCardItem.getData(clutchLinkStack);
        if (!clutchLink.isLinked()) {
            this.releaseLinkedTarget(row);
            this.setRuntime(row, RuleStatus.LINK_ERROR, "Link a clutch");
            runtime.resetObservation();
            return;
        }
        runtime.rememberLink(clutchLink);

        List<BlockPos> machines = this.collectMatchingMachines(selection);
        if (machines.isEmpty()) {
            runtime.idleTicks = 0;
            if (runtime.controllerHeldStopped) {
                // Re-apply the stop signal so the clutch is not silently resumed while the
                // area is reconfigured (e.g. machines temporarily absent during a rebuild).
                if (this.applyLinkedState(row, clutchLink, false, STOP_SIGNAL)) {
                    this.setRuntime(row, RuleStatus.NO_MACHINES, "No machines in area, clutch held stopped");
                } else {
                    this.releaseLinkedTarget(row);
                    this.setRuntime(row, RuleStatus.LINK_ERROR, "Linked clutch is missing");
                }
            } else {
                this.releaseLinkedTarget(row);
                this.setRuntime(row, RuleStatus.NO_MACHINES, "No matching machines in area");
            }
            return;
        }

        MachineObservation observation = this.collectObservation(machines);
        boolean firstObservation = !runtime.hasObservation;
        boolean fingerprintChanged = runtime.hasObservation && runtime.lastFingerprint != observation.fingerprint;
        boolean contentChanged = runtime.hasObservation && runtime.lastContentFingerprint != observation.contentFingerprint;
        runtime.hasObservation = true;
        runtime.lastFingerprint = observation.fingerprint;
        runtime.lastContentFingerprint = observation.contentFingerprint;

        boolean sawBufferedActive = this.updateMechanicalSawInputBuffer(runtime, selection, observation);
        boolean activitySignal = observation.processingActive || fingerprintChanged || sawBufferedActive;
        boolean readySignal = observation.processingActive || sawBufferedActive
                || (observation.inputAvailable && (firstObservation || contentChanged));

        if (activitySignal) {
            runtime.idleTicks = 0;
            runtime.lastActivityGameTime = this.level.getGameTime();
        } else {
            runtime.idleTicks += EVALUATION_INTERVAL_TICKS;
        }

        if (runtime.controllerHeldStopped) {
            if (readySignal) {
                if (this.applyLinkedState(row, clutchLink, true, RUN_SIGNAL)) {
                    runtime.controllerHeldStopped = false;
                    runtime.idleTicks = 0;
                    runtime.lastActivityGameTime = this.level.getGameTime();
                    this.setRuntime(row, RuleStatus.RUNNING, "Restarted from fresh input/activity");
                } else {
                    this.releaseLinkedTarget(row);
                    this.setRuntime(row, RuleStatus.LINK_ERROR, "Linked clutch is missing");
                }
            } else {
                if (this.applyLinkedState(row, clutchLink, false, STOP_SIGNAL)) {
                    this.setRuntime(row, RuleStatus.STOPPED, "Paused after idle timeout");
                } else {
                    this.releaseLinkedTarget(row);
                    this.setRuntime(row, RuleStatus.LINK_ERROR, "Linked clutch is missing");
                }
            }
            return;
        }

        if (activitySignal) {
            if (this.applyLinkedState(row, clutchLink, true, RUN_SIGNAL)) {
                this.setRuntime(row, RuleStatus.RUNNING, machines.size() + " machine(s) active");
            } else {
                this.releaseLinkedTarget(row);
                this.setRuntime(row, RuleStatus.LINK_ERROR, "Linked clutch is missing");
            }
            return;
        }

        int timeoutTicks = selection.getIdleTimeoutSeconds() * 20;
        int effectiveIdleTicks = Math.max(0, runtime.idleTicks - IDLE_TIMEOUT_GRACE_TICKS);
        if (effectiveIdleTicks <= 0) {
            if (this.applyLinkedState(row, clutchLink, true, RUN_SIGNAL)) {
                this.setRuntime(row, RuleStatus.COUNTDOWN, "Grace window, idle countdown pending");
            } else {
                this.releaseLinkedTarget(row);
                this.setRuntime(row, RuleStatus.LINK_ERROR, "Linked clutch is missing");
            }
            return;
        }

        if (effectiveIdleTicks >= timeoutTicks) {
            if (this.applyLinkedState(row, clutchLink, false, STOP_SIGNAL)) {
                runtime.controllerHeldStopped = true;
                this.setRuntime(row, RuleStatus.STOPPED, "Paused after idle timeout");
            } else {
                this.releaseLinkedTarget(row);
                this.setRuntime(row, RuleStatus.LINK_ERROR, "Linked clutch is missing");
            }
            return;
        }

        int secondsLeft = Math.max(0, (timeoutTicks - effectiveIdleTicks + 19) / 20);
        if (this.applyLinkedState(row, clutchLink, true, RUN_SIGNAL)) {
            this.setRuntime(row, RuleStatus.COUNTDOWN, "Idle timeout in " + secondsLeft + "s");
        } else {
            this.releaseLinkedTarget(row);
            this.setRuntime(row, RuleStatus.LINK_ERROR, "Linked clutch is missing");
        }
    }

    /** [2] */
    private void handleFlywheelRow(int row, SafetyClutchLinkData linkData) {
        RuleRuntime runtime = this.runtimes[row];
        if (!linkData.isLinked()) {
            this.releaseLinkedTarget(row);
            this.setRuntime(row, RuleStatus.LINK_ERROR, "Link a Kinetic Flywheel");
            runtime.resetObservation();
            return;
        }
        KineticFlywheelBE flywheel = this.resolveKineticFlywheel(linkData);
        if (flywheel == null) {
            this.releaseLinkedTarget(row);
            this.setRuntime(row, RuleStatus.LINK_ERROR, "Linked flywheel is missing");
            runtime.resetObservation();
            return;
        }
        runtime.rememberLink(linkData);
        runtime.controllerHeldStopped = false;
        runtime.idleTicks = 0;
        // Defer the actual arm/standby decision to coordinateFlywheelBanks(), which groups all running
        // flywheel rows by kinetic network and applies the bank strategy. This status is provisional.
        this.flywheelMembers.add(new FlywheelMember(row, flywheel));
        this.setRuntime(row, RuleStatus.RUNNING,
                String.format("Armed: %.0f%% charge, %s", flywheel.getChargePercent(), flywheelModeLabel(flywheel)));
    }

    @Nullable
    private KineticFlywheelBE resolveKineticFlywheel(SafetyClutchLinkData linkData) {
        BlockEntity be = this.resolveLinkedBlockEntity(linkData);
        return be instanceof KineticFlywheelBE flywheel ? flywheel : null;
    }

    /** [3] */
    private static void setFlywheelArmed(KineticFlywheelBE flywheel, boolean armed) {
        if (flywheel.isDischargeArmed() == armed) {
            return;
        }
        KineticFlywheelSettings settings = KineticFlywheelSettings.load(flywheel.getSettings().save());
        settings.setDischargeArmed(armed);
        flywheel.setSettings(settings);
    }

    private static String flywheelModeLabel(KineticFlywheelBE flywheel) {
        return switch (flywheel.getMode()) {
            case DISCHARGING -> "discharging";
            case CHARGING -> "charging";
            case IDLE -> "idle";
        };
    }

    /** [4] */
    private void coordinateFlywheelBanks() {
        if (this.flywheelMembers.isEmpty()) {
            return;
        }
        Map<Long, List<FlywheelMember>> banks = new HashMap<>();
        for (FlywheelMember member : this.flywheelMembers) {
            Long key = member.flywheel().getNetworkKey();
            if (key == null) {
                member.flywheel().clearOutputCommand();
                setFlywheelArmed(member.flywheel(), true);
                this.setRuntime(member.row(), RuleStatus.RUNNING, String.format("Armed: %.0f%% charge, %s",
                        member.flywheel().getChargePercent(), flywheelModeLabel(member.flywheel())));
                continue;
            }
            banks.computeIfAbsent(key, k -> new ArrayList<>()).add(member);
        }
        for (List<FlywheelMember> bank : banks.values()) {
            this.applyBankStrategy(bank);
        }
    }

    private void applyBankStrategy(List<FlywheelMember> bank) {
        int size = bank.size();
        float total = 0f;
        for (FlywheelMember member : bank) {
            total += member.flywheel().getChargePercent();
        }
        float avg = total / size;
        String suffix = size > 1 ? String.format(" (bank %.0f%%, %d units)", avg, size) : "";

        if (this.bankStrategy == BankStrategy.PARALLEL) {
            for (FlywheelMember member : bank) {
                member.flywheel().clearOutputCommand();
                setFlywheelArmed(member.flywheel(), true);
                float charge = member.flywheel().getChargePercent();
                String detail = size > 1
                        ? String.format("Sharing load: %.0f%%, %s%s", charge, flywheelModeLabel(member.flywheel()), suffix)
                        : String.format("Armed: %.0f%% charge, %s", charge, flywheelModeLabel(member.flywheel()));
                this.setRuntime(member.row(), RuleStatus.RUNNING, detail);
            }
            return;
        }

        // [5]
        boolean smart = this.bankStrategy == BankStrategy.SMART;
        FlywheelMember active = this.chooseSequentialActive(bank);
        for (FlywheelMember member : bank) {
            boolean isActive = member == active;
            setFlywheelArmed(member.flywheel(), isActive);
            if (isActive && smart) {
                member.flywheel().commandOutputRpm(this.targetRpm);
            } else {
                member.flywheel().clearOutputCommand();
            }
            float charge = member.flywheel().getChargePercent();
            if (isActive) {
                String detail = smart
                        ? String.format("Smart: holding %d RPM, %.0f%%%s", this.targetRpm, charge, suffix)
                        : String.format("Active: %.0f%%, %s%s", charge, flywheelModeLabel(member.flywheel()), suffix);
                this.setRuntime(member.row(), RuleStatus.RUNNING, detail);
            } else {
                this.setRuntime(member.row(), RuleStatus.COUNTDOWN, String.format("Standby: %.0f%% held%s", charge, suffix));
            }
        }
    }

    /** Sequential bank active unit: keep a still-charged armed unit (hysteresis), else the fullest one. */
    private FlywheelMember chooseSequentialActive(List<FlywheelMember> bank) {
        FlywheelMember fullest = null;
        float fullestCharge = -1f;
        for (FlywheelMember member : bank) {
            float charge = member.flywheel().getChargePercent();
            if (member.flywheel().isDischargeArmed() && charge > SEQUENTIAL_ADVANCE_PCT) {
                return member;
            }
            if (charge > fullestCharge) {
                fullestCharge = charge;
                fullest = member;
            }
        }
        return fullest;
    }

    private List<BlockPos> collectMatchingMachines(MachineSelectionCardData selection) {
        List<BlockPos> matches = new ArrayList<>();
        if (this.level == null || selection.getMachineBlockId() == null) {
            return matches;
        }
        BlockPos min = selection.getMinCorner();
        BlockPos max = selection.getMaxCorner();
        if (min == null || max == null) {
            return matches;
        }
        Block targetBlock = BuiltInRegistries.BLOCK.get(selection.getMachineBlockId());
        if (targetBlock == null) {
            return matches;
        }
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (this.level.getBlockState(pos).is(targetBlock)) {
                matches.add(pos.immutable());
            }
        }
        return matches;
    }

    private MachineObservation collectObservation(List<BlockPos> machines) {
        MachineObservation aggregate = new MachineObservation();
        for (BlockPos machinePos : machines) {
            MachineObservation single = this.observeMachine(machinePos);
            aggregate.processingActive |= single.processingActive;
            aggregate.inputAvailable |= single.inputAvailable;
            aggregate.fingerprint = 31L * aggregate.fingerprint + machinePos.asLong();
            aggregate.fingerprint = 31L * aggregate.fingerprint + single.fingerprint;
            aggregate.contentFingerprint = 31L * aggregate.contentFingerprint + machinePos.asLong();
            aggregate.contentFingerprint = 31L * aggregate.contentFingerprint + single.contentFingerprint;
        }
        return aggregate;
    }

    private MachineObservation observeMachine(BlockPos machinePos) {
        MachineObservation observation = new MachineObservation();
        if (this.level == null) {
            return observation;
        }
        BlockEntity machine = this.level.getBlockEntity(machinePos);
        if (machine instanceof BasinOperatingBlockEntity) {
            if (machine instanceof MechanicalPressBlockEntity press) {
                observation.processingActive = press.pressingBehaviour != null && press.pressingBehaviour.running;
            } else if (machine instanceof MechanicalMixerBlockEntity mixer) {
                observation.processingActive = mixer.running;
            }
            this.observeBasinWorkspace(machinePos, observation);
            return observation;
        }
        if (machine instanceof CrushingWheelControllerBlockEntity controller) {
            this.observeCrushingWheelController(machinePos, controller, observation);
            return observation;
        }
        if (machine instanceof CrushingWheelBlockEntity) {
            this.observeCrushingWheelWorkspace(machinePos, observation);
            return observation;
        }
        if (machine instanceof MillstoneBlockEntity millstone) {
            this.observeMillstoneWorkspace(machinePos, millstone, observation);
            return observation;
        }
        if (machine instanceof SawBlockEntity saw) {
            this.observeSawWorkspace(machinePos, saw, observation);
            return observation;
        }
        if (machine instanceof DeployerBlockEntity) {
            this.observeDeployerWorkspace(machinePos, machine.getBlockState(), observation);
            return observation;
        }
        if (machine instanceof EncasedFanBlockEntity fan) {
            this.observeFanWorkspace(machinePos, fan, observation);
            return observation;
        }
        this.observeNearbyInventories(machinePos, observation);
        this.observeNearbyItemEntities(machinePos, observation);
        return observation;
    }

    private void observeBasinWorkspace(BlockPos machinePos, MachineObservation observation) {
        BlockPos workPos = machinePos.below(2);
        this.observeInventories(List.of(machinePos, machinePos.below(), workPos), observation);
        this.observeItemEntities(this.createBlockBounds(workPos, machinePos.below()).inflate(0.25), observation);
    }

    private void observeCrushingWheelWorkspace(BlockPos machinePos, MachineObservation observation) {
        boolean foundController = false;
        Direction.Axis machineAxis = this.level != null && this.level.getBlockState(machinePos).hasProperty(BlockStateProperties.AXIS)
                ? this.level.getBlockState(machinePos).getValue(BlockStateProperties.AXIS)
                : null;
        for (Direction direction : Direction.values()) {
            if (machineAxis != null && direction.getAxis() == machineAxis) {
                continue;
            }
            BlockPos controllerPos = machinePos.relative(direction);
            BlockPos pairedWheelPos = machinePos.relative(direction, 2);
            if (this.level == null || !(this.level.getBlockEntity(pairedWheelPos) instanceof CrushingWheelBlockEntity)) {
                continue;
            }
            BlockEntity controller = this.level.getBlockEntity(controllerPos);
            if (!(controller instanceof CrushingWheelControllerBlockEntity crusherController)) {
                continue;
            }
            if (this.observeCrushingWheelController(controllerPos, crusherController, observation)) {
                foundController = true;
                break;
            }
        }
        if (!foundController) {
            this.observeNearbyInventories(machinePos, observation);
            this.observeNearbyItemEntities(machinePos, observation);
        }
    }

    private boolean observeCrushingWheelController(BlockPos controllerPos, CrushingWheelControllerBlockEntity controller,
            MachineObservation observation) {
        if (this.level == null) {
            return false;
        }
        BlockState controllerState = this.level.getBlockState(controllerPos);
        if (!controllerState.hasProperty(CrushingWheelControllerBlock.VALID)
                || !controllerState.getValue(CrushingWheelControllerBlock.VALID)) {
            return false;
        }
        observation.processingActive |= controller.isOccupied();
        this.observeItemHandler(controllerPos, controller.inventory, observation);
        this.observeItemEntities(new AABB(controllerPos).inflate(0.5), observation);
        return true;
    }

    private void observeMillstoneWorkspace(BlockPos machinePos, MillstoneBlockEntity millstone,
            MachineObservation observation) {
        observation.processingActive = millstone.timer > 0;
        this.observeItemHandler(machinePos, millstone.inputInv, observation);
        this.observeItemEntities(this.createBlockBounds(machinePos, machinePos.above()).inflate(0.2), observation);
    }

    private void observeDeployerWorkspace(BlockPos machinePos, BlockState state, MachineObservation observation) {
        Direction facing = this.resolveFacingDirection(state);
        if (facing == null) {
            this.observeNearbyInventories(machinePos, observation);
            this.observeNearbyItemEntities(machinePos, observation);
            return;
        }
        BlockPos targetPos = machinePos.relative(facing);
        List<BlockPos> candidates = new ArrayList<>(List.of(machinePos, machinePos.below(), targetPos, targetPos.below()));
        this.observeInventories(candidates, observation);
        this.observeItemEntities(this.createBlockBounds(targetPos, targetPos.below()).inflate(0.35), observation);
    }

    private void observeSawWorkspace(BlockPos machinePos, SawBlockEntity saw, MachineObservation observation) {
        Direction facing = this.resolveFacingDirection(saw.getBlockState());
        List<BlockPos> candidates = new ArrayList<>(List.of(machinePos, machinePos.below()));
        if (facing != null) {
            BlockPos front = machinePos.relative(facing);
            BlockPos back = machinePos.relative(facing.getOpposite());
            candidates.add(front);
            candidates.add(front.below());
            candidates.add(back);
            candidates.add(back.below());
        }
        this.observeItemHandler(machinePos, saw.inventory, observation);
        this.observeInventories(candidates, observation);

        AABB itemBounds = new AABB(machinePos).inflate(0.55);
        if (facing != null) {
            itemBounds = itemBounds.expandTowards(facing.getStepX() * 0.75, facing.getStepY() * 0.75, facing.getStepZ() * 0.75);
        }
        this.observeItemEntities(itemBounds.inflate(0.2), observation);
    }

    private void observeFanWorkspace(BlockPos machinePos, EncasedFanBlockEntity fan, MachineObservation observation) {
        if (this.level == null) {
            return;
        }
        Direction direction = fan.getAirCurrent().direction;
        if (direction == null) {
            direction = this.resolveFacingDirection(fan.getBlockState());
        }
        if (direction == null) {
            this.observeNearbyItemEntities(machinePos, observation);
            return;
        }
        float maxReach = Math.max(4.0F, fan.getAirCurrent().maxDistance);
        int reach = Math.max(1, Mth.ceil(AirCurrent.getFlowLimit(this.level, machinePos, Math.min(16.0F, maxReach), direction)));
        AABB airflowBounds = this.createBlockBounds(machinePos.relative(direction), machinePos.relative(direction, reach))
                .inflate(0.15);
        this.observeInventoriesInBounds(airflowBounds, observation);
        this.observeItemEntities(airflowBounds, observation);
    }

    private void observeNearbyInventories(BlockPos machinePos, MachineObservation observation) {
        this.observeInventories(List.of(machinePos, machinePos.below()), observation);
    }

    private void observeInventories(List<BlockPos> candidatePositions, MachineObservation observation) {
        if (this.level == null) {
            return;
        }
        Set<BlockPos> visited = new HashSet<>();
        for (BlockPos candidatePos : candidatePositions) {
            this.observeInventoryAtPos(candidatePos, observation, visited);
        }
    }

    private void observeInventoriesInBounds(AABB bounds, MachineObservation observation) {
        if (this.level == null) {
            return;
        }
        int minX = Mth.floor(bounds.minX);
        int minY = Mth.floor(bounds.minY);
        int minZ = Mth.floor(bounds.minZ);
        int maxX = Mth.floor(bounds.maxX - 1.0E-6D);
        int maxY = Mth.floor(bounds.maxY - 1.0E-6D);
        int maxZ = Mth.floor(bounds.maxZ - 1.0E-6D);
        Set<BlockPos> visited = new HashSet<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    this.observeInventoryAtPos(new BlockPos(x, y, z), observation, visited);
                }
            }
        }
    }

    private void observeInventoryAtPos(BlockPos candidatePos, MachineObservation observation, Set<BlockPos> visited) {
        if (this.level == null) {
            return;
        }
        BlockPos immutablePos = candidatePos.immutable();
        if (!visited.add(immutablePos)) {
            return;
        }
        BlockEntity candidate = this.level.getBlockEntity(immutablePos);
        if (candidate == null) {
            return;
        }
        if (candidate instanceof BeltBlockEntity belt) {
            BeltBlockEntity controller = belt.getControllerBE();
            BlockPos beltPos = controller != null ? controller.getBlockPos() : belt.getBlockPos();
            if (!visited.add(beltPos)) {
                return;
            }
            List<TransportedItemStack> transportedItems = (controller != null ? controller : belt).getInventory()
                    .getTransportedItems();
            for (TransportedItemStack transported : transportedItems) {
                if (transported == null || transported.stack.isEmpty()) {
                    continue;
                }
                observation.inputAvailable = true;
                observation.fingerprint = 31L * observation.fingerprint + Objects.hash(
                        beltPos.asLong(),
                        BuiltInRegistries.ITEM.getKey(transported.stack.getItem()),
                        transported.stack.getCount(),
                        transported.beltPosition);
                observation.contentFingerprint = 31L * observation.contentFingerprint + Objects.hash(
                        BuiltInRegistries.ITEM.getKey(transported.stack.getItem()),
                        transported.stack.getCount());
            }
            return;
        }
        IItemHandler itemHandler = candidate.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, candidate.getBlockPos(), null);
        if (itemHandler != null) {
            this.observeItemHandler(immutablePos, itemHandler, observation);
        }
    }

    private void observeItemHandler(BlockPos sourcePos, IItemHandler handler, MachineObservation observation) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            observation.inputAvailable = true;
            observation.fingerprint = 31L * observation.fingerprint + Objects.hash(
                    sourcePos.asLong(),
                    slot,
                    BuiltInRegistries.ITEM.getKey(stack.getItem()),
                    stack.getCount());
            observation.contentFingerprint = 31L * observation.contentFingerprint + Objects.hash(
                    BuiltInRegistries.ITEM.getKey(stack.getItem()),
                    stack.getCount());
        }
    }

    private void observeNearbyItemEntities(BlockPos machinePos, MachineObservation observation) {
        this.observeItemEntities(new AABB(machinePos).inflate(0.4), observation);
    }

    private boolean updateMechanicalSawInputBuffer(RuleRuntime runtime, MachineSelectionCardData selection,
            MachineObservation observation) {
        if (!MECHANICAL_SAW_ID.equals(selection.getMachineBlockId())) {
            runtime.mechanicalSawInputBufferTicks = 0;
            return false;
        }
        if (observation.processingActive || observation.inputAvailable) {
            runtime.mechanicalSawInputBufferTicks = MECHANICAL_SAW_INPUT_BUFFER_TICKS;
            return true;
        }
        runtime.mechanicalSawInputBufferTicks = Math.max(0,
                runtime.mechanicalSawInputBufferTicks - EVALUATION_INTERVAL_TICKS);
        return runtime.mechanicalSawInputBufferTicks > 0;
    }

    private void observeItemEntities(AABB bounds, MachineObservation observation) {
        if (this.level == null) {
            return;
        }
        for (ItemEntity entity : this.level.getEntitiesOfClass(ItemEntity.class, bounds)) {
            ItemStack stack = entity.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            observation.inputAvailable = true;
            observation.fingerprint = 31L * observation.fingerprint + Objects.hash(
                    BuiltInRegistries.ITEM.getKey(stack.getItem()),
                    stack.getCount(),
                    Math.round(entity.getX() * 10.0),
                    Math.round(entity.getY() * 10.0),
                    Math.round(entity.getZ() * 10.0));
            observation.contentFingerprint = 31L * observation.contentFingerprint + Objects.hash(
                    BuiltInRegistries.ITEM.getKey(stack.getItem()),
                    stack.getCount());
        }
    }

    private Direction resolveFacingDirection(BlockState state) {
        if (state.hasProperty(BlockStateProperties.FACING)) {
            return state.getValue(BlockStateProperties.FACING);
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        }
        return null;
    }

    private AABB createBlockBounds(BlockPos first, BlockPos second) {
        return new AABB(
                Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()),
                Math.max(first.getX(), second.getX()) + 1,
                Math.max(first.getY(), second.getY()) + 1,
                Math.max(first.getZ(), second.getZ()) + 1);
    }

    @Nullable
    private SafetyClutchLinkData getLinkedTargetData(int row) {
        ItemStack linkStack = this.getClutchLinkStack(row);
        if (!(linkStack.getItem() instanceof SafetyClutchLinkCardItem)) {
            return null;
        }
        SafetyClutchLinkData data = SafetyClutchLinkCardItem.getData(linkStack);
        return data.isLinked() ? data : null;
    }

    @Nullable
    private SafetyClutchBE resolveSafetyClutch(SafetyClutchLinkData linkData) {
        BlockEntity be = this.resolveLinkedBlockEntity(linkData);
        return be instanceof SafetyClutchBE clutch ? clutch : null;
    }

    @Nullable
    private ControlClutchBE resolveControlClutch(SafetyClutchLinkData linkData) {
        BlockEntity be = this.resolveLinkedBlockEntity(linkData);
        return be instanceof ControlClutchBE clutch ? clutch : null;
    }

    @Nullable
    private BlockEntity resolveLinkedBlockEntity(SafetyClutchLinkData linkData) {
        if (this.level == null || !linkData.isLinked() || linkData.getDimensionId() == null || linkData.getClutchPos() == null) {
            return null;
        }
        if (Objects.equals(this.level.dimension().location(), linkData.getDimensionId())) {
            return this.level.getBlockEntity(linkData.getClutchPos());
        }
        if (!(this.level instanceof ServerLevel sourceLevel) || this.level.getServer() == null || this.antennaTier != AntennaTier.TRT) {
            return null;
        }
        ResourceKey<Level> targetKey = ResourceKey.create(Registries.DIMENSION, linkData.getDimensionId());
        ServerLevel targetLevel = this.level.getServer().getLevel(targetKey);
        if (targetLevel == null || !AntennaRange.canReach(sourceLevel, this.worldPosition, targetLevel, linkData.getClutchPos(), this.antennaTier)) {
            return null;
        }
        return targetLevel.getBlockEntity(linkData.getClutchPos());
    }

    private boolean applyLinkedState(int row, SafetyClutchLinkData linkData, boolean shouldRun, int signal) {
        if (linkData.getTargetType() == SafetyClutchLinkData.TargetType.KINETIC_FLYWHEEL) {
            KineticFlywheelBE flywheel = this.resolveKineticFlywheel(linkData);
            if (flywheel == null) {
                return false;
            }
            setFlywheelArmed(flywheel, shouldRun);
            return true;
        }
        if (linkData.getTargetType() == SafetyClutchLinkData.TargetType.CONTROL_CLUTCH) {
            ControlClutchBE clutch = this.resolveControlClutch(linkData);
            if (clutch == null) {
                return false;
            }
            clutch.applyControllerState(this.worldPosition, row, shouldRun, signal);
            return true;
        }
        SafetyClutchBE clutch = this.resolveSafetyClutch(linkData);
        if (clutch == null) {
            return false;
        }
        if (shouldRun) {
            clutch.manualReset();
        } else {
            clutch.manualTrip();
        }
        return true;
    }

    private void setRuntime(int row, RuleStatus status, String detail) {
        RuleRuntime runtime = this.runtimes[row];
        if (runtime.status != status || !Objects.equals(runtime.detail, detail)) {
            runtime.status = status;
            runtime.detail = detail;
            this.markForClientSync();
        }
    }

    private void resetRuntimeForSlot(int slot) {
        int row = slot / 2;
        this.releaseLinkedTarget(row);
        this.runtimes[row].clearForCardChange();
    }

    private void releaseLinkedTarget(int row) {
        RuleRuntime runtime = this.runtimes[row];
        if (!runtime.hasRememberedLink()) {
            runtime.controllerHeldStopped = false;
            runtime.manualStopLatched = false;
            return;
        }
        SafetyClutchLinkData linkData = runtime.getRememberedLink();
        BlockEntity be = this.resolveLinkedBlockEntity(linkData);
        boolean wasStopped = runtime.controllerHeldStopped;
        runtime.clearRememberedLink();
        runtime.controllerHeldStopped = false;
        runtime.manualStopLatched = false;
        if (be == null) {
            return;
        }
        if (linkData.getTargetType() == SafetyClutchLinkData.TargetType.KINETIC_FLYWHEEL) {
            if (wasStopped && be instanceof KineticFlywheelBE flywheel) {
                setFlywheelArmed(flywheel, true);
            }
            return;
        }
        if (linkData.getTargetType() == SafetyClutchLinkData.TargetType.CONTROL_CLUTCH) {
            if (be instanceof ControlClutchBE clutch) {
                clutch.clearControllerState(this.worldPosition, row);
            }
            return;
        }
        if (wasStopped && be instanceof SafetyClutchBE clutch) {
            clutch.manualReset();
        }
    }

    private void markForClientSync() {
        this.needsClientSync = true;
        this.clientSyncTimer = 0;
    }

    private void syncToClient() {
        if (this.level == null) {
            return;
        }
        this.needsClientSync = false;
        this.clientSyncTimer = 0;
        this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        this.feConsumer.save(tag);
        tag.put("Inventory", this.inventory.serializeNBT(registries));
        tag.putString("AntennaTier", this.antennaTier.name());
        tag.putString("BankStrategy", this.bankStrategy.name());
        tag.putInt("TargetRpm", this.targetRpm);
        ListTag runtimeList = new ListTag();
        for (RuleRuntime runtime : this.runtimes) {
            runtimeList.add(runtime.save());
        }
        tag.put("Runtimes", runtimeList);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.feConsumer.load(tag);
        this.inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        this.antennaTier = AntennaTier.byName(tag.getString("AntennaTier"));
        this.bankStrategy = BankStrategy.fromName(tag.getString("BankStrategy"));
        if (tag.contains("TargetRpm")) {
            this.targetRpm = Mth.clamp(tag.getInt("TargetRpm"), TARGET_RPM_MIN, TARGET_RPM_MAX);
        }
        for (RuleRuntime runtime : this.runtimes) {
            runtime.clearForCardChange();
        }
        if (tag.contains("Runtimes")) {
            ListTag runtimeList = tag.getList("Runtimes", 10);
            for (int i = 0; i < Math.min(runtimeList.size(), this.runtimes.length); i++) {
                this.runtimes[i] = RuleRuntime.load(runtimeList.getCompound(i));
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        this.saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        this.loadAdditional(tag, registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            this.loadAdditional(tag, registries);
        }
    }

    public static int getMachineSlotIndex(int row) {
        return row * 2;
    }

    public static int getLinkSlotIndex(int row) {
        return row * 2 + 1;
    }

    public enum RuleStatus {
        INCOMPLETE("Need setup"),
        AREA_ERROR("Area issue"),
        LINK_ERROR("Link issue"),
        NO_MACHINES("No machines"),
        RUNNING("Running"),
        COUNTDOWN("Idle"),
        STOPPED("Paused");

        private final String displayName;

        RuleStatus(String displayName) {
            this.displayName = displayName;
        }
    }

    public enum BankStrategy {
        PARALLEL("Parallel"),
        SEQUENTIAL("Sequential"),
        SMART("Smart");

        private final String displayName;

        BankStrategy(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public BankStrategy next() {
            return switch (this) {
                case PARALLEL -> SEQUENTIAL;
                case SEQUENTIAL -> SMART;
                case SMART -> PARALLEL;
            };
        }

        public static BankStrategy fromName(String name) {
            try {
                return BankStrategy.valueOf(name);
            } catch (IllegalArgumentException ignored) {
                return PARALLEL;
            }
        }
    }

    private record FlywheelMember(int row, KineticFlywheelBE flywheel) {
    }

    public enum ScreenCellState {
        UNUSED,
        INCOMPLETE,
        RUNNING,
        COUNTDOWN,
        STOPPED,
        ERROR,
        NO_MACHINES
    }

    public static class RuleRuntime {
        private RuleStatus status = RuleStatus.INCOMPLETE;
        private String detail = "Insert both cards to start this rule";
        private int idleTicks;
        private long lastActivityGameTime;
        private long lastFingerprint;
        private long lastContentFingerprint;
        private boolean hasObservation;
        private boolean controllerHeldStopped;
        private boolean manualStopLatched;
        private int mechanicalSawInputBufferTicks;
        @Nullable
        private ResourceLocation linkedDimensionId;
        @Nullable
        private BlockPos linkedClutchPos;
        private SafetyClutchLinkData.TargetType linkedTargetType = SafetyClutchLinkData.TargetType.SAFETY_CLUTCH;

        public RuleStatus getStatus() {
            return this.status;
        }

        public String getDetail() {
            return this.detail;
        }

        public int getIdleTicks() {
            return this.idleTicks;
        }

        public boolean isControllerHeldStopped() {
            return this.controllerHeldStopped;
        }

        public boolean isManualStopLatched() {
            return this.manualStopLatched;
        }

        public void rememberLink(SafetyClutchLinkData linkData) {
            this.linkedDimensionId = linkData.getDimensionId();
            this.linkedClutchPos = linkData.getClutchPos();
            this.linkedTargetType = linkData.getTargetType();
        }

        public boolean hasRememberedLink() {
            return this.linkedDimensionId != null && this.linkedClutchPos != null;
        }

        public SafetyClutchLinkData getRememberedLink() {
            SafetyClutchLinkData linkData = new SafetyClutchLinkData();
            linkData.setTargetType(this.linkedTargetType);
            linkData.setDimensionId(this.linkedDimensionId);
            linkData.setClutchPos(this.linkedClutchPos);
            return linkData;
        }

        public void clearRememberedLink() {
            this.linkedDimensionId = null;
            this.linkedClutchPos = null;
            this.linkedTargetType = SafetyClutchLinkData.TargetType.SAFETY_CLUTCH;
        }

        private void clearForCardChange() {
            this.status = RuleStatus.INCOMPLETE;
            this.detail = "Insert both cards to start this rule";
            this.idleTicks = 0;
            this.lastActivityGameTime = 0L;
            this.lastFingerprint = 0L;
            this.lastContentFingerprint = 0L;
            this.hasObservation = false;
            this.controllerHeldStopped = false;
            this.manualStopLatched = false;
            this.mechanicalSawInputBufferTicks = 0;
            this.clearRememberedLink();
        }

        private void resetObservation() {
            this.idleTicks = 0;
            this.lastFingerprint = 0L;
            this.lastContentFingerprint = 0L;
            this.hasObservation = false;
            this.mechanicalSawInputBufferTicks = 0;
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Status", this.status.name());
            tag.putString("Detail", this.detail);
            tag.putInt("IdleTicks", this.idleTicks);
            tag.putLong("LastActivityGameTime", this.lastActivityGameTime);
            tag.putLong("LastFingerprint", this.lastFingerprint);
            tag.putLong("LastContentFingerprint", this.lastContentFingerprint);
            tag.putBoolean("HasObservation", this.hasObservation);
            tag.putBoolean("ControllerHeldStopped", this.controllerHeldStopped);
            tag.putBoolean("ManualStopLatched", this.manualStopLatched);
            if (this.linkedDimensionId != null) {
                tag.putString("LinkedDimensionId", this.linkedDimensionId.toString());
            }
            if (this.linkedClutchPos != null) {
                tag.putLong("LinkedClutchPos", this.linkedClutchPos.asLong());
            }
            tag.putString("LinkedTargetType", this.linkedTargetType.name());
            return tag;
        }

        private static RuleRuntime load(CompoundTag tag) {
            RuleRuntime runtime = new RuleRuntime();
            try {
                runtime.status = RuleStatus.valueOf(tag.getString("Status"));
            } catch (IllegalArgumentException ignored) {
            }
            runtime.detail = tag.getString("Detail");
            runtime.idleTicks = tag.getInt("IdleTicks");
            runtime.lastActivityGameTime = tag.getLong("LastActivityGameTime");
            runtime.lastFingerprint = tag.getLong("LastFingerprint");
            runtime.lastContentFingerprint = tag.getLong("LastContentFingerprint");
            runtime.hasObservation = tag.getBoolean("HasObservation");
            runtime.controllerHeldStopped = tag.getBoolean("ControllerHeldStopped");
            runtime.manualStopLatched = tag.getBoolean("ManualStopLatched");
            if (tag.contains("LinkedDimensionId")) {
                runtime.linkedDimensionId = ResourceLocation.tryParse(tag.getString("LinkedDimensionId"));
            }
            if (tag.contains("LinkedClutchPos")) {
                runtime.linkedClutchPos = BlockPos.of(tag.getLong("LinkedClutchPos"));
            }
            runtime.linkedTargetType = SafetyClutchLinkData.TargetType.fromName(tag.getString("LinkedTargetType"));
            return runtime;
        }
    }

    private static class MachineObservation {
        private boolean processingActive;
        private boolean inputAvailable;
        private long fingerprint = 1L;
        private long contentFingerprint = 1L;
    }
}
