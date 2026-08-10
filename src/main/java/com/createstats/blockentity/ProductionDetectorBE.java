package com.createstats.blockentity;

import com.createstats.CreateStats;
import com.createstats.block.ProductionDetectorBlock;
import com.createstats.compat.DieselCompatHelper;
import com.createstats.data.MetricType;
import com.createstats.util.ClientScreenAccess;
import com.createstats.util.InstrumentSourceHelper;
import com.createstats.util.InventoryLookupHelper;
import com.simibubi.create.content.fluids.tank.BoilerData;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.BeltInventory;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

public class ProductionDetectorBE extends BlockEntity implements com.createstats.power.IFeConsumer {

    /** FE Dependent Mode buffer (lives on the ticking lower half of the 2-tall block). */
    private final com.createstats.power.FeConsumer feConsumer = com.createstats.power.FeConsumer
            .forBlockEntity(this, com.createstats.power.FeProfiles.settings(com.createstats.power.FeProfiles.KEY_PRODUCTION_DETECTOR));

    public com.createstats.power.FeConsumer feConsumer() {
        return feConsumer;
    }
    private static final int SCAN_INTERVAL_TICKS = 5;
    public static final int MAX_DISPLAY_LINK_LINES = 8;
    private static final int DISPLAY_LINK_LINE_TIMEOUT_TICKS = 200;

    // existing data
    private final LinkedHashMap<String, Integer> itemCounts;
    private final LinkedHashMap<String, String> itemDisplayNames;
    private long totalItemsSeen;
    private int tickCounter;
    private boolean needsSync;
    private long startTick;
    @Nullable private BlockPos targetPos;
    @Nullable private String monitoredTargetName;
    private TargetState targetState;
    private int detectionRange;

    // round 2 additions
    private final LinkedHashMap<String, FluidEntry> fluidEntries;
    private final LinkedHashMap<String, SyntheticContentEntry> syntheticEntries;
    @Nullable private BoilerSnapshot boilerSnapshot;
    @Nullable private EnergySnapshot energySnapshot;
    @Nullable private DieselSnapshot dieselSnapshot;
    private final List<DisplayLinkLine> displayLinkLines;
    private boolean displayLinkBatchActive;
    private boolean displayLinkBatchChanged;
    private DisplayMode displayMode;

    // round 3 additions: bridge / source-mode
    private SourceMode sourceMode = SourceMode.DIRECT;
    private boolean dieselMultiStructureMode;
    private boolean dieselStructureModeConfigured;
    private BridgeStatus bridgeStatus = BridgeStatus.NONE;
    private BridgeKind bridgeCandidateKind = BridgeKind.NONE;
    @Nullable private BlockPos bridgeCandidatePos;
    @Nullable private BlockPos bridgeResolvedPos;
    @Nullable private String bridgeResolvedName;
    private int bridgeDiscoveryCooldown = 0;

    public enum TargetState { MONITORING, IDLE, BLOCKED, NO_TARGET }
    public enum DisplayMode { EMPTY, INVENTORY, FLUID, ENERGY, DIESEL, DISPLAY_LINK_FEED }
    public enum SourceMode { DIRECT, BRIDGE }
    public enum BridgeKind { NONE, DISPLAY_LINK, SMART_OBSERVER }
    public enum BridgeStatus { NONE, OK, NO_BRIDGE_FOUND, OUT_OF_RANGE, UNLOADED, NO_CAPABILITY, CYCLE, INVALID_LINK }

    public ProductionDetectorBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.itemCounts = new LinkedHashMap<>();
        this.itemDisplayNames = new LinkedHashMap<>();
        this.fluidEntries = new LinkedHashMap<>();
        this.syntheticEntries = new LinkedHashMap<>();
        this.energySnapshot = null;
        this.dieselSnapshot = null;
        this.displayLinkLines = new ArrayList<>(MAX_DISPLAY_LINK_LINES);
        this.totalItemsSeen = 0L;
        this.tickCounter = 0;
        this.needsSync = false;
        this.startTick = -1L;
        this.targetPos = null;
        this.monitoredTargetName = null;
        this.targetState = TargetState.NO_TARGET;
        this.displayMode = DisplayMode.EMPTY;
        this.detectionRange = 1;
        this.dieselMultiStructureMode = false;
        this.dieselStructureModeConfigured = false;
    }

    // ====================================================================
    // Tick + scan
    // ====================================================================

    public void tick() {
        if (this.level == null || this.level.isClientSide()) return;
        // FE Dependent Mode gate: a dead detector stops scanning belts and inventories.
        final boolean fePowered = this.feConsumer.serviceTick(this.isMonitoringBelt());
        com.createstats.power.FePowerFx.handleTransition(this.level, this.worldPosition, this.feConsumer);
        if (!fePowered) {
            return;
        }
        if (this.startTick < 0L) this.startTick = this.level.getGameTime();
        ++this.tickCounter;

        // Round 3: in BRIDGE mode, refresh candidate + status periodically.
        if (this.sourceMode == SourceMode.BRIDGE) {
            if (this.bridgeDiscoveryCooldown <= 0) {
                this.refreshBridgeCandidate();
                this.resolveBridge();
                this.bridgeDiscoveryCooldown = 20;
            } else {
                --this.bridgeDiscoveryCooldown;
            }
        } else if (this.bridgeStatus != BridgeStatus.NONE
                || this.bridgeCandidatePos != null
                || this.bridgeResolvedPos != null) {
            this.bridgeStatus = BridgeStatus.NONE;
            this.bridgeCandidatePos = null;
            this.bridgeCandidateKind = BridgeKind.NONE;
            this.bridgeResolvedPos = null;
            this.bridgeResolvedName = null;
            this.needsSync = true;
            this.setChanged();
        }

        if (this.tickCounter == 1 || this.isMonitoringBelt() || this.tickCounter % SCAN_INTERVAL_TICKS == 0) {
            this.scanInventory();
        }
        // expire stale display-link lines
        if (this.expireDisplayLinkLines()) {
            this.recomputeDisplayMode();
            this.needsSync = true;
            this.setChanged();
        }
        if (this.needsSync && this.tickCounter % 20 == 0) {
            this.needsSync = false;
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    private void scanInventory() {
        if (this.level == null) return;

        final TargetContext ctx = this.resolveTargetContext();
        this.itemCounts.clear();
        this.itemDisplayNames.clear();
        this.fluidEntries.clear();
        this.syntheticEntries.clear();
        this.boilerSnapshot = null;
        this.energySnapshot = null;
        this.dieselSnapshot = null;

        if (ctx == null) {
            this.setMissingTargetState();
            this.recomputeDisplayMode();
            this.needsSync = true;
            this.setChanged();
            return;
        }

        this.targetPos = ctx.pos();
        this.monitoredTargetName = ctx.state().getBlock().getName().getString();

        // Belt branch
        if (ctx.be() instanceof BeltBlockEntity beltBE) {
            this.scanBeltInventory(beltBE);
            this.recomputeDisplayMode();
            return;
        }

        boolean readAnything = false;
        int totalUnits = 0;

        // Items
        if (ctx.be() != null) {
            final IItemHandler items = this.resolveTargetItemHandler(ctx.be(), ctx.facing());
            if (items != null) {
                totalUnits = this.readItems(items);
                readAnything = true;
            }
            if (this.readEnergyStorage(ctx.be(), ctx.facing())) {
                readAnything = true;
            }
            if (this.readDieselSource(ctx.be(), ctx.facing())) {
                readAnything = true;
            }
        }

        // Fluids (general capability)
        final IFluidHandler fluids = this.level.getCapability(Capabilities.FluidHandler.BLOCK, ctx.pos(), null);
        if (fluids != null) {
            for (int t = 0; t < fluids.getTanks(); ++t) {
                final FluidStack stack = fluids.getFluidInTank(t);
                if (!stack.isEmpty()) {
                    this.recordFluid(stack, fluids.getTankCapacity(t));
                    readAnything = true;
                }
            }
        }

        // Boiler special-case (cap returns empty during boiler mode)
        if (ctx.be() instanceof FluidTankBlockEntity tank) {
            FluidTankBlockEntity ctrl = tank.getControllerBE();
            if (ctrl == null) ctrl = tank;
            final BoilerData boiler = ctrl.boiler;
            if (boiler != null && boiler.isActive()) {
                this.recordBoilerStats(ctrl, boiler);
                final FluidStack water = ctrl.getTankInventory().getFluid();
                if (!water.isEmpty() && this.fluidEntries.isEmpty()) {
                    this.recordFluid(water, ctrl.getTankInventory().getCapacity());
                }
                readAnything = true;
            }
        }

        // Cauldron (no BE, no cap)
        if (!readAnything) {
            readAnything = this.scanCauldron(ctx);
        }

        this.totalItemsSeen = totalUnits;
        this.targetState = readAnything
            ? (totalUnits > 0 || !this.fluidEntries.isEmpty() || !this.syntheticEntries.isEmpty() || this.boilerSnapshot != null || this.energySnapshot != null || this.dieselSnapshot != null
                        ? TargetState.MONITORING : TargetState.IDLE)
                : TargetState.BLOCKED;
        this.setPoweredState(this.dieselSnapshot == null && this.targetState == TargetState.MONITORING
            && (this.energySnapshot == null || this.energySnapshot.stored() > 0.0f || this.energySnapshot.hasEnergyFlow()));
        this.recomputeDisplayMode();
        this.needsSync = true;
        this.setChanged();
    }

    private boolean readEnergyStorage(BlockEntity targetBE, Direction facing) {
        final Direction accessFace = this.sourceMode == SourceMode.BRIDGE ? null : facing.getOpposite();
        final InstrumentSourceHelper.SourceReadout readout = InstrumentSourceHelper.readSource(targetBE, accessFace);
        if (readout.sourceKind() != InstrumentSourceHelper.SourceKind.ENERGY_STORAGE || readout.metrics().isEmpty()) {
            return false;
        }
        this.energySnapshot = this.buildEnergySnapshot(readout);
        return this.energySnapshot != null;
    }

    private boolean readDieselSource(BlockEntity targetBE, Direction facing) {
        final Direction accessFace = this.sourceMode == SourceMode.BRIDGE ? null : facing.getOpposite();
        final boolean multiStructure = this.dieselStructureModeConfigured
            ? this.dieselMultiStructureMode
            : DieselCompatHelper.isMultiStructureMode(null, targetBE);
        final InstrumentSourceHelper.SourceReadout readout = DieselCompatHelper.readSource(targetBE, accessFace, multiStructure);
        final DieselSnapshot snapshot = this.buildDieselSnapshot(readout);
        if (snapshot == null) {
            return false;
        }
        this.dieselSnapshot = snapshot;
        return true;
    }

    public void acceptEnergyReadout(BlockPos sourcePos, InstrumentSourceHelper.SourceReadout readout) {
        final EnergySnapshot snapshot = this.buildEnergySnapshot(readout);
        if (snapshot == null) {
            return;
        }
        this.itemCounts.clear();
        this.itemDisplayNames.clear();
        this.fluidEntries.clear();
        this.syntheticEntries.clear();
        this.boilerSnapshot = null;
        this.displayLinkLines.clear();
        this.energySnapshot = snapshot;
        this.dieselSnapshot = null;
        this.totalItemsSeen = 0L;
        this.targetPos = sourcePos;
        this.monitoredTargetName = snapshot.sourceType();
        this.targetState = TargetState.MONITORING;
        this.recomputeDisplayMode();
        this.setPoweredState(snapshot.stored() > 0.0f || snapshot.hasEnergyFlow());
        this.needsSync = true;
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    @Nullable
    private EnergySnapshot buildEnergySnapshot(InstrumentSourceHelper.SourceReadout readout) {
        final Map<String, InstrumentSourceHelper.MetricSample> metrics = readout.metrics();
        final InstrumentSourceHelper.MetricSample storedSample = this.sampleEnergyMetric(metrics, "energy_stored", "network_buffer");
        final InstrumentSourceHelper.MetricSample capacitySample = this.sampleEnergyMetric(metrics, "energy_capacity");
        final InstrumentSourceHelper.MetricSample fillSample = this.sampleEnergyMetric(metrics, "energy_fill_pct", "fill_pct");
        final InstrumentSourceHelper.MetricSample netSample = this.sampleEnergyMetric(metrics, "energy_net_rate", "network_pushed", "network_pulled");
        final InstrumentSourceHelper.MetricSample outputSample = this.sampleEnergyMetric(metrics, "energy_output_rate", "network_pushed");
        final InstrumentSourceHelper.MetricSample inputSample = this.sampleEnergyMetric(metrics, "energy_input_rate", "network_pulled", "network_demand");
        final float stored = valueOf(storedSample, 0.0f);
        final float capacity = Math.max(0.0f, valueOf(capacitySample, Math.max(stored, 0.0f)));
        final float fillPercent = fillSample != null
                ? Math.max(0.0f, Math.min(100.0f, fillSample.value()))
                : (capacity > 0.0f ? Math.max(0.0f, Math.min(100.0f, stored * 100.0f / capacity)) : 0.0f);
        float inputRate = inputSample != null ? Math.max(0.0f, inputSample.value()) : 0.0f;
        float outputRate = outputSample != null ? Math.max(0.0f, outputSample.value()) : 0.0f;
        final float netRate = netSample != null ? netSample.value() : inputRate - outputRate;
        if (netSample != null) {
            if (inputSample == null && outputSample == null) {
                inputRate = Math.max(0.0f, netRate);
                outputRate = Math.max(0.0f, -netRate);
            } else if (inputSample == null) {
                inputRate = Math.max(0.0f, netRate + outputRate);
            } else if (outputSample == null) {
                outputRate = Math.max(0.0f, inputRate - netRate);
            }
        }
        final float totalIn = valueOf(this.sampleEnergyMetric(metrics, "energy_total_in"), 0.0f);
        final float totalOut = valueOf(this.sampleEnergyMetric(metrics, "energy_total_out"), 0.0f);
        final float connectors = valueOf(this.sampleEnergyMetric(metrics, "connector_count", "wire_nodes"), 0.0f);
        final float blocks = valueOf(this.sampleEnergyMetric(metrics, "accumulator_size"), 0.0f);
        final boolean canReceive = valueOf(this.sampleEnergyMetric(metrics, "energy_can_receive"), 0.0f) > 0.5f;
        final boolean canExtract = valueOf(this.sampleEnergyMetric(metrics, "energy_can_extract"), 0.0f) > 0.5f;
        final boolean hasEnergyData = storedSample != null || capacitySample != null || fillSample != null || netSample != null || outputSample != null || inputSample != null;
        if (!hasEnergyData) {
            return null;
        }
        return new EnergySnapshot(readout.sourceType(), stored, capacity, fillPercent, netRate, inputRate, outputRate, totalIn, totalOut, connectors, blocks, canReceive, canExtract);
    }

    @Nullable
    private DieselSnapshot buildDieselSnapshot(InstrumentSourceHelper.SourceReadout readout) {
        if (readout == null || readout.metrics().isEmpty() || !this.isDieselSourceType(readout.sourceType())) {
            return null;
        }
        final Map<String, InstrumentSourceHelper.MetricSample> metrics = readout.metrics();
        final float fluidAmount = valueOf(this.sampleMetric(metrics, "fuel_amount", "fluid_amount", "oil_remaining_mb"), 0.0f);
        final float fluidCapacity = valueOf(this.sampleMetric(metrics, "fuel_capacity", "fluid_capacity", "tank_capacity"), Math.max(0.0f, fluidAmount));
        final InstrumentSourceHelper.MetricSample fillSample = this.sampleMetric(metrics, "fuel_fill_pct", "fluid_fill_pct", "tank_fill", "burn_pct");
        final float fillPercent = fillSample != null
                ? Math.max(0.0f, Math.min(100.0f, fillSample.value()))
                : (fluidCapacity > 0.0f ? Math.max(0.0f, Math.min(100.0f, fluidAmount * 100.0f / fluidCapacity)) : 0.0f);
        final float processPercent = Math.max(0.0f, Math.min(100.0f, valueOf(this.sampleMetric(metrics, "process_progress_pct"), 0.0f)));
        final float generatedSpeed = valueOf(this.sampleMetric(metrics, "rpm", "generated_speed", "generated_rpm"), 0.0f);
        final float generatedStress = valueOf(this.sampleMetric(metrics, "generated_stress_su"), 0.0f);
        final float networkStress = valueOf(this.sampleMetric(metrics, "stress_su", "network_stress_su"), 0.0f);
        final float engineCount = Math.max(1.0f, valueOf(this.sampleMetric(metrics, "engine_count", "tower_blocks", "barrel_blocks"), 1.0f));
        final float okEngines = valueOf(this.sampleMetric(metrics, "ok_engines"), 0.0f);
        final float stallingEngines = valueOf(this.sampleMetric(metrics, "stalling_engines"), 0.0f);
        final float offEngines = valueOf(this.sampleMetric(metrics, "off_engines"), 0.0f);
        final float oilRemaining = valueOf(this.sampleMetric(metrics, "oil_remaining_mb"), 0.0f);
        final float heatLevel = valueOf(this.sampleMetric(metrics, "heat_level", "burner_heat"), 0.0f);
        final float pipeLength = valueOf(this.sampleMetric(metrics, "pipe_length"), 0.0f);
        final float valvePercent = valueOf(this.sampleMetric(metrics, "valve_pct"), 0.0f);
        final float redstoneOutput = 0.0f;
        final boolean running = valueOf(this.sampleMetric(metrics, "running", "burner_lit"), 0.0f) > 0.5f || Math.abs(generatedSpeed) > 0.01f;
        final boolean processing = valueOf(this.sampleMetric(metrics, "processing", "pumpjack_started"), 0.0f) > 0.5f || processPercent > 0.0f;
        final boolean validFuel = valueOf(this.sampleMetric(metrics, "valid_fuel", "pumpjack_valid"), fillPercent > 0.0f ? 1.0f : 0.0f) > 0.5f;
        final boolean redstoneDisabled = valueOf(this.sampleMetric(metrics, "redstone_disabled"), 0.0f) > 0.5f;
        return new DieselSnapshot(readout.sourceType(), fluidAmount, fluidCapacity, fillPercent, processPercent, generatedSpeed, generatedStress, networkStress,
            engineCount, okEngines, stallingEngines, offEngines, oilRemaining, heatLevel, pipeLength, valvePercent, redstoneOutput,
            running, processing, validFuel, redstoneDisabled);
    }

    private boolean isDieselSourceType(String sourceType) {
        return sourceType != null && (sourceType.contains("Diesel") || "Oil Pumpjack".equals(sourceType) || "Oil Barrel".equals(sourceType));
    }

    @Nullable
    private InstrumentSourceHelper.MetricSample sampleMetric(Map<String, InstrumentSourceHelper.MetricSample> metrics, String... ids) {
        for (String id : ids) {
            final InstrumentSourceHelper.MetricSample sample = metrics.get(id);
            if (sample != null) {
                return sample;
            }
        }
        return null;
    }

    @Nullable
    private InstrumentSourceHelper.MetricSample sampleEnergyMetric(Map<String, InstrumentSourceHelper.MetricSample> metrics, String... ids) {
        for (String id : ids) {
            final InstrumentSourceHelper.MetricSample sample = metrics.get(id);
            if (sample != null) {
                return sample;
            }
        }
        return null;
    }

    private static float valueOf(@Nullable InstrumentSourceHelper.MetricSample sample, float fallback) {
        return sample != null ? sample.value() : fallback;
    }

    private int readItems(IItemHandler handler) {
        int totalUnits = 0;
        for (int slot = 0; slot < handler.getSlots(); ++slot) {
            final ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            final ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (itemKey == null) continue;
            final String itemId = itemKey.toString();
            this.itemCounts.merge(itemId, stack.getCount(), Integer::sum);
            this.itemDisplayNames.putIfAbsent(itemId, stack.getHoverName().getString());
            totalUnits += stack.getCount();
        }
        return totalUnits;
    }

    private void recordFluid(FluidStack stack, int capacity) {
        final Fluid fluid = stack.getFluid();
        final ResourceLocation key = BuiltInRegistries.FLUID.getKey(fluid);
        if (key == null) return;
        final String fluidId = key.toString();
        final String name = stack.getHoverName().getString();
        final FluidEntry existing = this.fluidEntries.get(fluidId);
        if (existing != null) {
            this.fluidEntries.put(fluidId,
                    new FluidEntry(fluidId, name, existing.amount() + stack.getAmount(), existing.capacity() + capacity));
        } else {
            this.fluidEntries.put(fluidId, new FluidEntry(fluidId, name, stack.getAmount(), capacity));
        }
    }

    private void recordBoilerStats(FluidTankBlockEntity controller, BoilerData boiler) {
        final int activeHeat = boiler.activeHeat;
        final int passiveHeat = boiler.passiveHeat ? 1 : 0;
        final int waterSupply = (int) boiler.waterSupply;
        final int engines = boiler.attachedEngines;
        final int whistles = boiler.attachedWhistles;
        this.boilerSnapshot = new BoilerSnapshot(activeHeat, passiveHeat, waterSupply, engines, whistles, boiler.isActive());
    }

    private boolean scanCauldron(TargetContext ctx) {
        final Block b = ctx.state().getBlock();
        if (b == Blocks.LAVA_CAULDRON) {
            this.recordSyntheticFluid("minecraft:lava",
                    Component.translatable("block.minecraft.lava").getString(), 1000, 1000);
            return true;
        } else if (b == Blocks.WATER_CAULDRON) {
            final int lvl = ctx.state().getValue(LayeredCauldronBlock.LEVEL);
            this.recordSyntheticFluid("minecraft:water",
                    Component.translatable("block.minecraft.water").getString(), lvl * 333, 999);
            return true;
        } else if (b == Blocks.POWDER_SNOW_CAULDRON) {
            final int lvl = ctx.state().getValue(LayeredCauldronBlock.LEVEL);
            this.recordSyntheticEntry("minecraft:powder_snow",
                    Component.translatable("block.minecraft.powder_snow").getString(), lvl * 333, 999);
            return true;
        } else if (b == Blocks.CAULDRON) {
            this.recordSyntheticEntry("minecraft:cauldron",
                    Component.translatable("block.minecraft.cauldron").getString(), 0, 1000);
            return true;
        }
        return false;
    }

    private void recordSyntheticFluid(String id, String label, int amount, int capacity) {
        // synthetic fluids piggyback the fluidEntries collection so renderer can treat them uniformly
        this.fluidEntries.put(id, new FluidEntry(id, label, amount, capacity));
    }

    private void recordSyntheticEntry(String id, String label, int amount, int capacity) {
        this.syntheticEntries.put(id, new SyntheticContentEntry(id, label, amount, capacity));
    }

    // ====================================================================
    // Target resolution (fresh scan, no cached pos)
    // ====================================================================

    @Nullable
    private TargetContext resolveTargetContext() {
        if (this.level == null) return null;
        final Direction facing = this.getBlockState().getValue(ProductionDetectorBlock.FACING);

        // Bridge mode short-circuit
        if (this.sourceMode == SourceMode.BRIDGE) {
            if (this.bridgeStatus == BridgeStatus.OK && this.bridgeResolvedPos != null) {
                final BlockPos rp = this.bridgeResolvedPos;
                if (!this.isLoadedAt(rp)) {
                    return null;
                }
                final BlockState rs = this.level.getBlockState(rp);
                final BlockEntity rbe = this.level.getBlockEntity(rp);
                return new TargetContext(rp, rs, rbe, facing);
            }
            // No valid bridge, emit empty (the BridgeStatus surfaces the issue in the GUI).
            return null;
        }

        // Adjacent belt always wins
        final BlockPos adjacentPos = this.worldPosition.relative(facing);
        final BlockEntity adjacent = this.level.getBlockEntity(adjacentPos);
        if (adjacent instanceof BeltBlockEntity) {
            return new TargetContext(adjacentPos, this.level.getBlockState(adjacentPos), adjacent, facing);
        }

        for (int i = 1; i <= this.detectionRange; ++i) {
            final BlockPos checkPos = this.worldPosition.relative(facing, i);
            final BlockState checkState = this.level.getBlockState(checkPos);
            if (checkState.isAir()) continue;
            final BlockEntity be = this.level.getBlockEntity(checkPos);
            return new TargetContext(checkPos, checkState, be, facing);
        }
        return null;
    }

    private void scanBeltInventory(BeltBlockEntity segmentBE) {
        try {
            final BeltBlockEntity controller = segmentBE.getControllerBE() != null ? segmentBE.getControllerBE() : segmentBE;
            final BeltInventory inventory = controller.getInventory();
            if (inventory == null) {
                this.totalItemsSeen = 0L;
                this.targetState = TargetState.BLOCKED;
                this.setPoweredState(false);
                this.needsSync = true;
                this.setChanged();
                return;
            }

            int totalUnits = 0;
            for (TransportedItemStack transported : inventory.getTransportedItems()) {
                final ItemStack stack = transported.stack;
                if (stack.isEmpty()) continue;
                final ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (itemKey == null) continue;
                final String itemId = itemKey.toString();
                this.itemCounts.merge(itemId, stack.getCount(), Integer::sum);
                this.itemDisplayNames.putIfAbsent(itemId, stack.getHoverName().getString());
                totalUnits += stack.getCount();
            }

            this.totalItemsSeen = totalUnits;
            this.targetState = totalUnits > 0 ? TargetState.MONITORING : TargetState.IDLE;
            this.setPoweredState(totalUnits > 0);
            this.needsSync = true;
            this.setChanged();
        } catch (Exception e) {
            this.totalItemsSeen = 0L;
            this.targetState = TargetState.BLOCKED;
            this.setPoweredState(false);
            this.needsSync = true;
            this.setChanged();
            CreateStats.LOGGER.warn("Inventory display scan failed: {}", e.getMessage());
        }
    }

    private void setMissingTargetState() {
        this.totalItemsSeen = 0L;
        if (this.sourceMode == SourceMode.BRIDGE) {
            // In bridge mode never call level.getBlockState on bridgeResolvedPos, it may be in an
            // unloaded chunk. Use the cached name stored during resolveBridge() instead.
            if (this.bridgeStatus == BridgeStatus.OK && this.bridgeResolvedPos != null) {
                this.targetPos = this.bridgeResolvedPos;
                this.monitoredTargetName = this.bridgeResolvedName;
                this.targetState = TargetState.BLOCKED;
            } else {
                this.targetPos = null;
                this.monitoredTargetName = null;
                this.targetState = TargetState.NO_TARGET;
            }
            this.setPoweredState(false);
            return;
        }
        // Direct mode: resolved pos is always adjacent/nearby (same or adjacent chunk, always loaded).
        final BlockPos resolvedPos = this.getResolvedTargetPos();
        final BlockState resolvedState = this.level != null ? this.level.getBlockState(resolvedPos) : null;
        if (resolvedState == null || resolvedState.isAir()) {
            this.targetPos = null;
            this.monitoredTargetName = null;
            this.targetState = TargetState.NO_TARGET;
        } else {
            this.targetPos = resolvedPos;
            this.monitoredTargetName = resolvedState.getBlock().getName().getString();
            this.targetState = TargetState.BLOCKED;
        }
        this.setPoweredState(false);
    }

    @Nullable
    private IItemHandler resolveTargetItemHandler(BlockEntity targetBE, Direction facing) {
        // In bridge mode the detector's own facing has no spatial relationship to the remote target,
        // so query omnidirectional access first.
        final Direction accessFace = this.sourceMode == SourceMode.BRIDGE ? null : facing.getOpposite();
        if (targetBE instanceof ItemVaultBlockEntity vault) {
            final ItemVaultBlockEntity controller = vault.getControllerBE() != null ? vault.getControllerBE() : vault;
            if (controller.getLevel() == null) return null;
            IItemHandler controllerHandler = controller.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, controller.getBlockPos(), null);
            if (controllerHandler == null && controller == vault && accessFace != null) {
                controllerHandler = controller.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, controller.getBlockPos(), accessFace);
            }
            return controllerHandler;
        }
        if (targetBE.getLevel() == null) return null;
        return InventoryLookupHelper.resolveItemHandler(targetBE.getLevel(), targetBE.getBlockPos(), targetBE, accessFace);
    }

    private BlockEntity resolveDisplayTargetBlockEntity(BlockEntity targetBE) {
        if (targetBE instanceof ItemVaultBlockEntity vault) {
            return vault.getControllerBE() != null ? vault.getControllerBE() : vault;
        }
        return targetBE;
    }

    public boolean isMonitoringBelt() {
        if (this.level == null) return false;
        final Direction facing = this.getBlockState().getValue(ProductionDetectorBlock.FACING);
        final BlockEntity targetBE = this.level.getBlockEntity(this.worldPosition.relative(facing));
        return targetBE instanceof BeltBlockEntity;
    }

    private void setPoweredState(boolean powered) {
        if (this.level == null) return;
        final BlockState lowerState = this.level.getBlockState(this.worldPosition);
        if (!lowerState.hasProperty(ProductionDetectorBlock.POWERED)) return;

        boolean changed = false;
        if (lowerState.getValue(ProductionDetectorBlock.POWERED) != powered) {
            this.level.setBlock(this.worldPosition, lowerState.setValue(ProductionDetectorBlock.POWERED, powered), 3);
            changed = true;
        }

        final BlockPos upperPos = this.worldPosition.above();
        final BlockState upperState = this.level.getBlockState(upperPos);
        if (upperState.is(lowerState.getBlock())
                && upperState.hasProperty(ProductionDetectorBlock.POWERED)
                && upperState.hasProperty(ProductionDetectorBlock.HALF)
                && upperState.getValue(ProductionDetectorBlock.HALF) == DoubleBlockHalf.UPPER
                && upperState.getValue(ProductionDetectorBlock.POWERED) != powered) {
            this.level.setBlock(upperPos, upperState.setValue(ProductionDetectorBlock.POWERED, powered), 3);
            changed = true;
        }

        if (changed) {
            this.level.updateNeighborsAt(this.worldPosition, lowerState.getBlock());
        }
    }

    @Nullable
    public BlockEntity findTargetBlockEntity() {
        if (this.level == null) return null;
        final Direction facing = this.getBlockState().getValue(ProductionDetectorBlock.FACING);
        final BlockEntity adjacent = this.level.getBlockEntity(this.worldPosition.relative(facing));
        if (adjacent instanceof BeltBlockEntity) return adjacent;
        for (int i = 1; i <= this.detectionRange; ++i) {
            final BlockPos checkPos = this.worldPosition.relative(facing, i);
            final BlockState checkState = this.level.getBlockState(checkPos);
            if (checkState.isAir()) continue;
            final BlockEntity be = this.level.getBlockEntity(checkPos);
            if (be != null) return be;
            return null;
        }
        return null;
    }

    public BlockPos getMonitoredTargetPos() {
        if (this.targetPos != null) return this.targetPos;
        final Direction facing = this.getBlockState().getValue(ProductionDetectorBlock.FACING);
        return this.worldPosition.relative(facing);
    }

    public BlockPos getResolvedTargetPos() {
        if (this.sourceMode == SourceMode.BRIDGE && this.bridgeResolvedPos != null) {
            return this.bridgeResolvedPos;
        }
        if (this.targetPos != null) return this.targetPos;
        final BlockEntity targetBE = this.findTargetBlockEntity();
        if (targetBE != null) return this.resolveDisplayTargetBlockEntity(targetBE).getBlockPos();
        final Direction facing = this.getBlockState().getValue(ProductionDetectorBlock.FACING);
        if (this.level != null) {
            for (int i = 1; i <= this.detectionRange; ++i) {
                final BlockPos checkPos = this.worldPosition.relative(facing, i);
                if (!this.level.getBlockState(checkPos).isAir()) return checkPos;
            }
        }
        return this.worldPosition.relative(facing);
    }

    public int getDetectionRange() { return this.detectionRange; }

    public void setDetectionRange(int range) {
        this.detectionRange = Math.max(1, Math.min(8, range));
        this.setChanged();
    }

    public String getMonitoredTargetName() {
        if (this.monitoredTargetName != null && !this.monitoredTargetName.isEmpty()) return this.monitoredTargetName;
        if (this.level == null) return Component.translatable("create_stats.production_detector.unknown_target").getString();
        final BlockState s = this.level.getBlockState(this.getResolvedTargetPos());
        return s.isAir() ? Component.translatable("create_stats.production_detector.no_target").getString() : s.getBlock().getName().getString();
    }

    public TargetState getTargetState() { return this.level == null ? TargetState.NO_TARGET : this.targetState; }

    public String getTargetStatusSummary() {
        final String targetName = this.getMonitoredTargetName();
        return switch (this.getTargetState()) {
            case MONITORING -> Component.translatable("create_stats.production_detector.status.monitoring", targetName).getString();
            case IDLE -> Component.translatable("create_stats.production_detector.status.idle", targetName).getString();
            case BLOCKED -> Component.translatable("create_stats.production_detector.status.blocked", targetName).getString();
            case NO_TARGET -> Component.translatable("create_stats.production_detector.status.no_target").getString();
        };
    }

    public String getSensorIdentityLabel() { return Component.translatable("create_stats.display_source.inventory_display").getString(); }

    public String getTrackingSummaryLabel() {
        final String targetName = this.getMonitoredTargetName();
        return targetName != null && !targetName.isEmpty() ? targetName : Component.translatable("create_stats.production_detector.unknown_target").getString();
    }

    public long getUptimeTicks() {
        return this.level != null && this.startTick >= 0L ? this.level.getGameTime() - this.startTick : 0L;
    }

    public long getTotalItemsSeen() { return this.totalItemsSeen; }
    public int getTrackedItemTypeCount() { return this.itemCounts.size(); }
    public LinkedHashMap<String, Integer> getItemCounts() { return this.itemCounts; }
    public LinkedHashMap<String, String> getItemDisplayNames() { return this.itemDisplayNames; }

    public List<ItemCountEntry> getTopItemEntries(int limit) {
        final ArrayList<ItemCountEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : this.itemCounts.entrySet()) {
            final int count = Math.max(0, entry.getValue());
            if (count <= 0) continue;
            final String itemId = entry.getKey();
            entries.add(new ItemCountEntry(itemId, this.itemDisplayNames.getOrDefault(itemId, itemId), count));
        }
        entries.sort(Comparator
                .comparingInt(ItemCountEntry::count).reversed()
                .thenComparing(ItemCountEntry::displayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(ItemCountEntry::itemId));
        if (entries.size() <= limit) return entries;
        return new ArrayList<>(entries.subList(0, Math.max(0, limit)));
    }

    public int getAnalogOutputSignal() { return this.totalItemsSeen > 0L ? 15 : 0; }
    public boolean isTrackingBeltStacks() { return this.isMonitoringBelt(); }

    public DisplayMode getDisplayMode() { return this.displayMode; }

    private void recomputeDisplayMode() {
        // Bridge mode: ignore any inbound Display-Link feed lines so the resolved
        // remote target's inventory/fluid view always wins over feed text.
        final boolean dlFeedAllowed = this.sourceMode != SourceMode.BRIDGE;
        if (this.dieselSnapshot != null) this.displayMode = DisplayMode.DIESEL;
        else if (this.energySnapshot != null) this.displayMode = DisplayMode.ENERGY;
        else if (!this.itemCounts.isEmpty()) this.displayMode = DisplayMode.INVENTORY;
        else if (!this.fluidEntries.isEmpty() || !this.syntheticEntries.isEmpty() || this.boilerSnapshot != null) this.displayMode = DisplayMode.FLUID;
        else if (dlFeedAllowed && !this.displayLinkLines.isEmpty()) this.displayMode = DisplayMode.DISPLAY_LINK_FEED;
        else this.displayMode = DisplayMode.EMPTY;
    }

    // ====================================================================
    // Snapshots (immutable copies for renderer/screen)
    // ====================================================================

    public List<FluidEntry> getFluidEntriesSnapshot() {
        if (this.fluidEntries.isEmpty()) return Collections.emptyList();
        final List<FluidEntry> list = new ArrayList<>(this.fluidEntries.values());
        list.sort(Comparator.comparingInt(FluidEntry::amount).reversed());
        if (list.size() > 12) return new ArrayList<>(list.subList(0, 12));
        return list;
    }

    public List<SyntheticContentEntry> getSyntheticContentSnapshot() {
        if (this.syntheticEntries.isEmpty()) return Collections.emptyList();
        final List<SyntheticContentEntry> list = new ArrayList<>(this.syntheticEntries.values());
        list.sort(Comparator.comparingInt(SyntheticContentEntry::amount).reversed());
        if (list.size() > 6) return new ArrayList<>(list.subList(0, 6));
        return list;
    }

    @Nullable
    public BoilerSnapshot getBoilerSnapshot() { return this.boilerSnapshot; }

    @Nullable
    public EnergySnapshot getEnergySnapshot() { return this.energySnapshot; }

    @Nullable
    public DieselSnapshot getDieselSnapshot() { return this.dieselSnapshot; }

    public List<DisplayLinkLine> getDisplayLinkLinesSnapshot() {
        if (this.displayLinkLines.isEmpty()) return Collections.emptyList();
        final List<DisplayLinkLine> copy = new ArrayList<>(this.displayLinkLines);
        copy.sort(Comparator.comparingInt(DisplayLinkLine::row));
        return copy;
    }

    public boolean isDisplayLinkActive() { return !this.displayLinkLines.isEmpty(); }

    // ====================================================================
    // Display Link line storage
    // ====================================================================

    public void beginDisplayLinkBatch() {
        this.displayLinkBatchActive = true;
        this.displayLinkBatchChanged = false;
    }

    public void acceptDisplayLinkLine(int row, MutableComponent text) {
        if (this.level == null) return;
        if (row < 0 || row >= MAX_DISPLAY_LINK_LINES) return;
        final String json = Component.Serializer.toJson(text, this.level.registryAccess());
        final long now = this.level.getGameTime();
        for (int i = 0; i < this.displayLinkLines.size(); ++i) {
            final DisplayLinkLine existing = this.displayLinkLines.get(i);
            if (existing.row() == row) {
                if (!existing.text().equals(json)) {
                    this.displayLinkLines.set(i, new DisplayLinkLine(row, json, now));
                    this.displayLinkBatchChanged = true;
                } else {
                    // refresh timestamp without flagging changed
                    this.displayLinkLines.set(i, new DisplayLinkLine(row, json, now));
                }
                return;
            }
        }
        this.displayLinkLines.add(new DisplayLinkLine(row, json, now));
        this.displayLinkBatchChanged = true;
    }

    public void endDisplayLinkBatch() {
        if (this.displayLinkBatchChanged) {
            this.recomputeDisplayMode();
            this.setChanged();
            if (this.level != null) {
                this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
            }
        }
        this.displayLinkBatchActive = false;
        this.displayLinkBatchChanged = false;
    }

    public void clearDisplayLinkLines() {
        if (this.displayLinkLines.isEmpty()) return;
        this.displayLinkLines.clear();
        this.recomputeDisplayMode();
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    private boolean expireDisplayLinkLines() {
        if (this.level == null || this.displayLinkLines.isEmpty()) return false;
        final long now = this.level.getGameTime();
        boolean changed = this.displayLinkLines.removeIf(l -> now - l.lastUpdated() > DISPLAY_LINK_LINE_TIMEOUT_TICKS);
        return changed;
    }

    // ====================================================================
    // Render hash + interactions
    // ====================================================================

    public int getRenderDataHash() {
        int hash = Long.hashCode(this.totalItemsSeen);
        hash = 31 * hash + this.targetState.ordinal();
        hash = 31 * hash + this.detectionRange;
        hash = 31 * hash + (this.monitoredTargetName != null ? this.monitoredTargetName.hashCode() : 0);
        hash = 31 * hash + (this.targetPos != null ? Long.hashCode(this.targetPos.asLong()) : 0);
        hash = 31 * hash + this.itemCounts.hashCode();
        hash = 31 * hash + this.itemDisplayNames.hashCode();
        hash = 31 * hash + this.fluidEntries.hashCode();
        hash = 31 * hash + this.syntheticEntries.hashCode();
        hash = 31 * hash + (this.boilerSnapshot != null ? this.boilerSnapshot.hashCode() : 0);
        hash = 31 * hash + (this.energySnapshot != null ? this.energySnapshot.hashCode() : 0);
        hash = 31 * hash + (this.dieselSnapshot != null ? this.dieselSnapshot.hashCode() : 0);
        hash = 31 * hash + this.displayMode.ordinal();
        hash = 31 * hash + this.sourceMode.ordinal();
        hash = 31 * hash + Boolean.hashCode(this.dieselMultiStructureMode);
        hash = 31 * hash + Boolean.hashCode(this.dieselStructureModeConfigured);
        hash = 31 * hash + this.bridgeStatus.ordinal();
        hash = 31 * hash + (this.bridgeResolvedPos != null ? Long.hashCode(this.bridgeResolvedPos.asLong()) : 0);
        // Display link lines: include row + text only (not timestamps) so animation phase doesn't churn texture
        for (DisplayLinkLine l : this.displayLinkLines) {
            hash = 31 * hash + l.row();
            hash = 31 * hash + l.text().hashCode();
        }
        // Animation tick bucket, refresh every 2 game ticks while in FLUID mode
        // so the dynamic texture re-bakes the wave animation.
        if ((this.displayMode == DisplayMode.FLUID || this.displayMode == DisplayMode.ENERGY || this.displayMode == DisplayMode.DIESEL) && this.level != null) {
            hash = 31 * hash + (int)(this.level.getGameTime() / 2L);
        }
        return hash;
    }

    public void onPlayerInteract(Player player) {
        if (this.level != null && !this.level.isClientSide()) this.scanInventory();
        player.displayClientMessage(Component.literal(String.format("§6[%s]§r %s | %,d items | %d types",
                this.getSensorIdentityLabel(), this.getTargetStatusSummary(),
                this.getTotalItemsSeen(), this.getTrackedItemTypeCount())), true);
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public String getDisplayText(int mode) {
        if (this.dieselSnapshot != null) {
            return switch (mode) {
                case 0 -> Component.translatable("create_stats.production_detector.diesel.fuel", String.format("%.0f", this.dieselSnapshot.fillPercent())).getString();
                case 1 -> MetricType.RPM.formatValue(this.dieselSnapshot.generatedSpeed());
                case 2 -> this.dieselSnapshot.sourceType();
                default -> Component.translatable(this.dieselSnapshot.hasActivity() ? "create_stats.production_detector.diesel.active" : "create_stats.production_detector.diesel.idle").getString();
            };
        }
        if (this.energySnapshot != null) {
            return switch (mode) {
                case 0 -> MetricType.ENERGY_FE.formatValue(this.energySnapshot.stored());
                case 1 -> String.format("%.0f%% charge", this.energySnapshot.fillPercent());
                case 2 -> this.energySnapshot.sourceType();
                default -> MetricType.ENERGY_FE_PER_TICK.formatValue(this.energySnapshot.netRate());
            };
        }
        return switch (mode) {
            case 0 -> String.format("%,d items", this.totalItemsSeen);
            case 1 -> String.format("%d types", this.getTrackedItemTypeCount());
            case 2 -> this.getMonitoredTargetName();
            default -> String.format("%,d items (%d types)", this.totalItemsSeen, this.getTrackedItemTypeCount());
        };
    }

    public void resetCounters() {
        this.itemCounts.clear();
        this.itemDisplayNames.clear();
        this.fluidEntries.clear();
        this.syntheticEntries.clear();
        this.boilerSnapshot = null;
        this.energySnapshot = null;
        this.dieselSnapshot = null;
        this.displayLinkLines.clear();
        this.totalItemsSeen = 0L;
        this.tickCounter = 0;
        this.startTick = -1L;
        this.targetPos = null;
        this.monitoredTargetName = null;
        this.targetState = TargetState.NO_TARGET;
        this.displayMode = DisplayMode.EMPTY;
        this.needsSync = true;
        this.setChanged();
    }

    // ====================================================================
    // NBT
    // ====================================================================

    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        this.feConsumer.save(tag);
        this.writeCommonData(tag, registries);
    }

    private void saveSyncAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        this.writeCommonData(tag, registries);
    }

    private void writeCommonData(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("DetectionRange", this.detectionRange);
        tag.putLong("TotalItems", this.totalItemsSeen);
        tag.putLong("StartTick", this.startTick);
        tag.putString("TargetState", this.targetState.name());
        tag.putString("DisplayMode", this.displayMode.name());
        tag.putString("TargetName", this.monitoredTargetName != null ? this.monitoredTargetName : "");
        if (this.targetPos != null) tag.putLong("TargetPos", this.targetPos.asLong());

        final ListTag countList = new ListTag();
        for (Map.Entry<String, Integer> entry : this.itemCounts.entrySet()) {
            final CompoundTag itemTag = new CompoundTag();
            itemTag.putString("Item", entry.getKey());
            itemTag.putInt("Count", entry.getValue());
            final String displayName = this.itemDisplayNames.get(entry.getKey());
            if (displayName != null && !displayName.isEmpty()) itemTag.putString("Name", displayName);
            countList.add(itemTag);
        }
        tag.put("ItemCounts", countList);

        final ListTag fluidList = new ListTag();
        for (FluidEntry e : this.fluidEntries.values()) {
            final CompoundTag t = new CompoundTag();
            t.putString("Id", e.fluidId());
            t.putString("Name", e.displayName());
            t.putInt("Amount", e.amount());
            t.putInt("Capacity", e.capacity());
            fluidList.add(t);
        }
        tag.put("Fluids", fluidList);

        final ListTag syntheticList = new ListTag();
        for (SyntheticContentEntry e : this.syntheticEntries.values()) {
            final CompoundTag t = new CompoundTag();
            t.putString("Id", e.id());
            t.putString("Label", e.label());
            t.putInt("Amount", e.amount());
            t.putInt("Capacity", e.capacity());
            syntheticList.add(t);
        }
        tag.put("Synthetic", syntheticList);

        if (this.boilerSnapshot != null) {
            final CompoundTag b = new CompoundTag();
            b.putInt("ActiveHeat", this.boilerSnapshot.activeHeat());
            b.putInt("PassiveHeat", this.boilerSnapshot.passiveHeat());
            b.putInt("WaterSupply", this.boilerSnapshot.waterSupply());
            b.putInt("Engines", this.boilerSnapshot.engines());
            b.putInt("Whistles", this.boilerSnapshot.whistles());
            b.putBoolean("Active", this.boilerSnapshot.active());
            tag.put("Boiler", b);
        }

        if (this.energySnapshot != null) {
            final CompoundTag energy = new CompoundTag();
            energy.putString("SourceType", this.energySnapshot.sourceType());
            energy.putFloat("Stored", this.energySnapshot.stored());
            energy.putFloat("Capacity", this.energySnapshot.capacity());
            energy.putFloat("FillPercent", this.energySnapshot.fillPercent());
            energy.putFloat("NetRate", this.energySnapshot.netRate());
            energy.putFloat("InputRate", this.energySnapshot.inputRate());
            energy.putFloat("OutputRate", this.energySnapshot.outputRate());
            energy.putFloat("TotalIn", this.energySnapshot.totalIn());
            energy.putFloat("TotalOut", this.energySnapshot.totalOut());
            energy.putFloat("Connectors", this.energySnapshot.connectors());
            energy.putFloat("Blocks", this.energySnapshot.blocks());
            energy.putBoolean("CanReceive", this.energySnapshot.canReceive());
            energy.putBoolean("CanExtract", this.energySnapshot.canExtract());
            tag.put("Energy", energy);
        }

        if (this.dieselSnapshot != null) {
            final CompoundTag diesel = new CompoundTag();
            diesel.putString("SourceType", this.dieselSnapshot.sourceType());
            diesel.putFloat("FluidAmount", this.dieselSnapshot.fluidAmount());
            diesel.putFloat("FluidCapacity", this.dieselSnapshot.fluidCapacity());
            diesel.putFloat("FillPercent", this.dieselSnapshot.fillPercent());
            diesel.putFloat("ProcessPercent", this.dieselSnapshot.processPercent());
            diesel.putFloat("GeneratedSpeed", this.dieselSnapshot.generatedSpeed());
            diesel.putFloat("GeneratedStress", this.dieselSnapshot.generatedStress());
            diesel.putFloat("NetworkStress", this.dieselSnapshot.networkStress());
            diesel.putFloat("EngineCount", this.dieselSnapshot.engineCount());
            diesel.putFloat("OkEngines", this.dieselSnapshot.okEngines());
            diesel.putFloat("StallingEngines", this.dieselSnapshot.stallingEngines());
            diesel.putFloat("OffEngines", this.dieselSnapshot.offEngines());
            diesel.putFloat("OilRemaining", this.dieselSnapshot.oilRemaining());
            diesel.putFloat("HeatLevel", this.dieselSnapshot.heatLevel());
            diesel.putFloat("PipeLength", this.dieselSnapshot.pipeLength());
            diesel.putFloat("ValvePercent", this.dieselSnapshot.valvePercent());
            diesel.putBoolean("Running", this.dieselSnapshot.running());
            diesel.putBoolean("Processing", this.dieselSnapshot.processing());
            diesel.putBoolean("ValidFuel", this.dieselSnapshot.validFuel());
            diesel.putBoolean("RedstoneDisabled", this.dieselSnapshot.redstoneDisabled());
            tag.put("Diesel", diesel);
        }

        if (!this.displayLinkLines.isEmpty()) {
            final ListTag lines = new ListTag();
            for (DisplayLinkLine l : this.displayLinkLines) {
                final CompoundTag lt = new CompoundTag();
                lt.putInt("Row", l.row());
                lt.putString("Text", l.text());
                lt.putLong("Updated", l.lastUpdated());
                lines.add(lt);
            }
            tag.put("DisplayLinkLines", lines);
        }

        // Round 3: bridge / source-mode
        tag.putString("SourceMode", this.sourceMode.name());
        tag.putBoolean("DieselMultiStructureMode", this.dieselMultiStructureMode);
        tag.putBoolean("DieselStructureModeConfigured", this.dieselStructureModeConfigured);
        tag.putString("BridgeStatus", this.bridgeStatus.name());
        tag.putString("BridgeKind", this.bridgeCandidateKind.name());
        if (this.bridgeCandidatePos != null) tag.putLong("BridgeCandidatePos", this.bridgeCandidatePos.asLong());
        if (this.bridgeResolvedPos != null) tag.putLong("BridgeResolvedPos", this.bridgeResolvedPos.asLong());
        if (this.bridgeResolvedName != null) tag.putString("BridgeResolvedName", this.bridgeResolvedName);
    }

    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.feConsumer.load(tag);
        this.detectionRange = tag.contains("DetectionRange") ? Math.max(1, Math.min(8, tag.getInt("DetectionRange"))) : 1;
        this.totalItemsSeen = this.readCompatLong(tag, "TotalItems", "TotalThroughputUnits");
        this.startTick = tag.contains("StartTick") ? tag.getLong("StartTick") : -1L;
        this.targetState = this.readTargetState(tag);
        this.monitoredTargetName = this.readNullableString(tag, "TargetName");
        this.targetPos = tag.contains("TargetPos") ? BlockPos.of(tag.getLong("TargetPos")) : null;

        this.itemCounts.clear();
        this.itemDisplayNames.clear();
        if (tag.contains("ItemCounts", 9)) {
            final ListTag countList = tag.getList("ItemCounts", 10);
            for (int i = 0; i < countList.size(); ++i) {
                final CompoundTag itemTag = countList.getCompound(i);
                final String item = itemTag.getString("Item");
                final int count = itemTag.getInt("Count");
                this.itemCounts.put(item, count);
                if (itemTag.contains("Name")) this.itemDisplayNames.put(item, itemTag.getString("Name"));
            }
        }
        if (this.totalItemsSeen <= 0L && !this.itemCounts.isEmpty()) {
            long sum = 0L;
            for (Integer count : this.itemCounts.values()) sum += Math.max(0, count);
            this.totalItemsSeen = sum;
        }

        this.fluidEntries.clear();
        if (tag.contains("Fluids", 9)) {
            final ListTag list = tag.getList("Fluids", 10);
            for (int i = 0; i < list.size(); ++i) {
                final CompoundTag t = list.getCompound(i);
                final String id = t.getString("Id");
                this.fluidEntries.put(id, new FluidEntry(id, t.getString("Name"), t.getInt("Amount"), t.getInt("Capacity")));
            }
        }

        this.syntheticEntries.clear();
        if (tag.contains("Synthetic", 9)) {
            final ListTag list = tag.getList("Synthetic", 10);
            for (int i = 0; i < list.size(); ++i) {
                final CompoundTag t = list.getCompound(i);
                final String id = t.getString("Id");
                this.syntheticEntries.put(id, new SyntheticContentEntry(id, t.getString("Label"), t.getInt("Amount"), t.getInt("Capacity")));
            }
        }

        this.boilerSnapshot = null;
        if (tag.contains("Boiler", 10)) {
            final CompoundTag b = tag.getCompound("Boiler");
            this.boilerSnapshot = new BoilerSnapshot(
                    b.getInt("ActiveHeat"), b.getInt("PassiveHeat"), b.getInt("WaterSupply"),
                    b.getInt("Engines"), b.getInt("Whistles"), b.getBoolean("Active"));
        }

            this.energySnapshot = null;
            if (tag.contains("Energy", 10)) {
                final CompoundTag energy = tag.getCompound("Energy");
                this.energySnapshot = new EnergySnapshot(
                    energy.getString("SourceType"),
                    energy.getFloat("Stored"),
                    energy.getFloat("Capacity"),
                    energy.getFloat("FillPercent"),
                    energy.getFloat("NetRate"),
                    energy.contains("InputRate") ? energy.getFloat("InputRate") : Math.max(0.0f, energy.getFloat("NetRate")),
                    energy.contains("OutputRate") ? energy.getFloat("OutputRate") : Math.max(0.0f, -energy.getFloat("NetRate")),
                    energy.getFloat("TotalIn"),
                    energy.getFloat("TotalOut"),
                    energy.getFloat("Connectors"),
                    energy.getFloat("Blocks"),
                    energy.getBoolean("CanReceive"),
                    energy.getBoolean("CanExtract"));
            }

                    this.dieselSnapshot = null;
                    if (tag.contains("Diesel", 10)) {
                    final CompoundTag diesel = tag.getCompound("Diesel");
                    this.dieselSnapshot = new DieselSnapshot(
                        diesel.getString("SourceType"),
                        diesel.getFloat("FluidAmount"),
                        diesel.getFloat("FluidCapacity"),
                        diesel.getFloat("FillPercent"),
                        diesel.getFloat("ProcessPercent"),
                        diesel.getFloat("GeneratedSpeed"),
                        diesel.getFloat("GeneratedStress"),
                        diesel.contains("NetworkStress") ? diesel.getFloat("NetworkStress") : 0.0f,
                        diesel.getFloat("EngineCount"),
                        diesel.contains("OkEngines") ? diesel.getFloat("OkEngines") : 0.0f,
                        diesel.contains("StallingEngines") ? diesel.getFloat("StallingEngines") : 0.0f,
                        diesel.contains("OffEngines") ? diesel.getFloat("OffEngines") : 0.0f,
                        diesel.getFloat("OilRemaining"),
                        diesel.getFloat("HeatLevel"),
                        diesel.getFloat("PipeLength"),
                        diesel.getFloat("ValvePercent"),
                        0.0f,
                        diesel.getBoolean("Running"),
                        diesel.getBoolean("Processing"),
                        diesel.getBoolean("ValidFuel"),
                        diesel.getBoolean("RedstoneDisabled"));
                    }

        this.displayLinkLines.clear();
        if (tag.contains("DisplayLinkLines", 9)) {
            final ListTag lines = tag.getList("DisplayLinkLines", 10);
            for (int i = 0; i < lines.size(); ++i) {
                final CompoundTag lt = lines.getCompound(i);
                this.displayLinkLines.add(new DisplayLinkLine(lt.getInt("Row"), lt.getString("Text"), lt.getLong("Updated")));
            }
        }

        if (tag.contains("DisplayMode")) {
            try {
                this.displayMode = DisplayMode.valueOf(tag.getString("DisplayMode"));
            } catch (IllegalArgumentException e) {
                this.displayMode = DisplayMode.EMPTY;
            }
        } else {
            this.recomputeDisplayMode();
        }

        // Round 3: bridge / source-mode
        if (tag.contains("SourceMode")) {
            try { this.sourceMode = SourceMode.valueOf(tag.getString("SourceMode")); }
            catch (IllegalArgumentException e) { this.sourceMode = SourceMode.DIRECT; }
        } else {
            this.sourceMode = SourceMode.DIRECT;
        }
        this.dieselMultiStructureMode = tag.getBoolean("DieselMultiStructureMode");
        this.dieselStructureModeConfigured = tag.getBoolean("DieselStructureModeConfigured");
        if (tag.contains("BridgeStatus")) {
            try { this.bridgeStatus = BridgeStatus.valueOf(tag.getString("BridgeStatus")); }
            catch (IllegalArgumentException e) { this.bridgeStatus = BridgeStatus.NONE; }
        } else {
            this.bridgeStatus = BridgeStatus.NONE;
        }
        if (tag.contains("BridgeKind")) {
            try { this.bridgeCandidateKind = BridgeKind.valueOf(tag.getString("BridgeKind")); }
            catch (IllegalArgumentException e) { this.bridgeCandidateKind = BridgeKind.NONE; }
        } else {
            this.bridgeCandidateKind = BridgeKind.NONE;
        }
        this.bridgeCandidatePos = tag.contains("BridgeCandidatePos") ? BlockPos.of(tag.getLong("BridgeCandidatePos")) : null;
        this.bridgeResolvedPos = tag.contains("BridgeResolvedPos") ? BlockPos.of(tag.getLong("BridgeResolvedPos")) : null;
        this.bridgeResolvedName = this.readNullableString(tag, "BridgeResolvedName");

        this.needsSync = false;
    }

    private long readCompatLong(CompoundTag tag, String primaryKey, String legacyKey) {
        if (tag.contains(primaryKey, 4)) return tag.getLong(primaryKey);
        if (tag.contains(primaryKey, 3)) return tag.getInt(primaryKey);
        if (tag.contains(legacyKey, 4)) return tag.getLong(legacyKey);
        if (tag.contains(legacyKey, 3)) return tag.getInt(legacyKey);
        return 0L;
    }

    @Nullable
    private String readNullableString(CompoundTag tag, String key) {
        if (!tag.contains(key)) return null;
        final String value = tag.getString(key);
        return value.isEmpty() ? null : value;
    }

    private TargetState readTargetState(CompoundTag tag) {
        if (!tag.contains("TargetState")) return TargetState.NO_TARGET;
        try { return TargetState.valueOf(tag.getString("TargetState")); }
        catch (IllegalArgumentException e) { return TargetState.NO_TARGET; }
    }

    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        this.saveSyncAdditional(tag, registries);
        return tag;
    }

    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        this.loadAdditional(tag, registries);
    }

    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        final CompoundTag tag = pkt.getTag();
        if (tag != null) this.loadAdditional(tag, registries);
    }

    public void setRemoved() {
        if (this.level != null && this.level.isClientSide()) {
            ClientScreenAccess.cleanupProductionDetectorTexture(this.getBlockPos());
        }
        super.setRemoved();
    }

    // ====================================================================
    // Public records
    // ====================================================================

    public record ItemCountEntry(String itemId, String displayName, int count) {
        public String registryName() { return itemId; }
    }

    public record FluidEntry(String fluidId, String displayName, int amount, int capacity) {}

    public record SyntheticContentEntry(String id, String label, int amount, int capacity) {}

    public record BoilerSnapshot(int activeHeat, int passiveHeat, int waterSupply, int engines, int whistles, boolean active) {}

    public record EnergySnapshot(String sourceType, float stored, float capacity, float fillPercent, float netRate, float inputRate, float outputRate, float totalIn, float totalOut, float connectors, float blocks, boolean canReceive, boolean canExtract) {
        public boolean hasEnergyFlow() {
            return Math.abs(this.netRate) > 0.01f || this.inputRate > 0.01f || this.outputRate > 0.01f;
        }
    }

    public record DieselSnapshot(String sourceType, float fluidAmount, float fluidCapacity, float fillPercent, float processPercent,
                                 float generatedSpeed, float generatedStress, float networkStress, float engineCount, float okEngines,
                                 float stallingEngines, float offEngines, float oilRemaining, float heatLevel,
                                 float pipeLength, float valvePercent, float redstoneOutput, boolean running, boolean processing,
                                 boolean validFuel, boolean redstoneDisabled) {
        public boolean hasActivity() {
            return this.running || this.processing || Math.abs(this.generatedSpeed) > 0.01f || this.heatLevel > 0.0f || this.valvePercent > 0.0f;
        }
    }

    public record DisplayLinkLine(int row, String text, long lastUpdated) {
        public String plainText() {
            try {
                net.minecraft.network.chat.Component c = net.minecraft.network.chat.Component.Serializer.fromJson(text, net.minecraft.core.RegistryAccess.EMPTY);
                return c == null ? text : c.getString();
            } catch (Exception ex) {
                return text;
            }
        }
    }

    private record TargetContext(BlockPos pos, BlockState state, @Nullable BlockEntity be, Direction facing) {}

    // ====================================================================
    // Round 3: bridging via Display Link / Smart Observer
    // ====================================================================

    private static final int BRIDGE_SCAN_RADIUS = 8;
    private static final int BRIDGE_MAX_TAXICAB = 64;
    private static final int BRIDGE_MAX_DL_HOPS = 3;

    public SourceMode getSourceMode() { return this.sourceMode; }
    public boolean isDieselMultiStructureMode() {
        if (this.dieselStructureModeConfigured) return this.dieselMultiStructureMode;
        return this.dieselSnapshot != null && (this.dieselSnapshot.sourceType().contains("Group")
                || "Modular Diesel Engine".equals(this.dieselSnapshot.sourceType())
                || "Huge Diesel Engine".equals(this.dieselSnapshot.sourceType()));
    }
    public BridgeStatus getBridgeStatus() { return this.bridgeStatus; }
    public BridgeKind getBridgeCandidateKind() { return this.bridgeCandidateKind; }
    @Nullable public BlockPos getBridgeCandidatePos() { return this.bridgeCandidatePos; }
    @Nullable public BlockPos getBridgeResolvedPos() { return this.bridgeResolvedPos; }
    @Nullable public String getBridgeResolvedName() { return this.bridgeResolvedName; }

    public void setSourceMode(SourceMode mode) {
        if (mode == null || mode == this.sourceMode) return;
        this.sourceMode = mode;
        if (mode == SourceMode.DIRECT) {
            this.bridgeStatus = BridgeStatus.NONE;
            this.bridgeCandidatePos = null;
            this.bridgeCandidateKind = BridgeKind.NONE;
            this.bridgeResolvedPos = null;
            this.bridgeResolvedName = null;
        } else {
            this.bridgeDiscoveryCooldown = 0;
        }
        if (this.level != null && !this.level.isClientSide()) {
            this.scanInventory();
            this.needsSync = true;
            this.setChanged();
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public void setDieselMultiStructureMode(boolean multi) {
        if (this.dieselStructureModeConfigured && this.dieselMultiStructureMode == multi) return;
        this.dieselMultiStructureMode = multi;
        this.dieselStructureModeConfigured = true;
        if (this.level != null && !this.level.isClientSide()) {
            this.scanInventory();
            this.needsSync = true;
            this.setChanged();
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    private void refreshBridgeCandidate() {
        if (this.level == null) return;
        final BlockPos origin = this.worldPosition;
        final int r = BRIDGE_SCAN_RADIUS;

        // Cheap path: 6 immediate neighbours for Smart Observer.
        for (Direction d : Direction.values()) {
            final BlockPos np = origin.relative(d);
            final BlockEntity nbe = this.level.getBlockEntity(np);
            if (nbe instanceof com.simibubi.create.content.redstone.smartObserver.SmartObserverBlockEntity) {
                this.bridgeCandidatePos = np.immutable();
                this.bridgeCandidateKind = BridgeKind.SMART_OBSERVER;
                return;
            }
        }

        // Bounded scan over loaded chunks for a Display Link targeting us.
        final int minCX = (origin.getX() - r) >> 4;
        final int maxCX = (origin.getX() + r) >> 4;
        final int minCZ = (origin.getZ() - r) >> 4;
        final int maxCZ = (origin.getZ() + r) >> 4;
        BlockPos best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int cx = minCX; cx <= maxCX; ++cx) {
            for (int cz = minCZ; cz <= maxCZ; ++cz) {
                final var chunk = this.level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) continue;
                for (BlockPos bep : chunk.getBlockEntitiesPos()) {
                    if (Math.abs(bep.getX() - origin.getX()) > r
                            || Math.abs(bep.getZ() - origin.getZ()) > r
                            || Math.abs(bep.getY() - origin.getY()) > r) continue;
                    final BlockEntity be = this.level.getBlockEntity(bep);
                    if (!(be instanceof com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity dl)) continue;
                    final BlockPos tgt = dl.getTargetPosition();
                    if (tgt == null) continue;
                    if (!tgt.equals(origin)) continue;
                    final int dist = bep.distManhattan(origin);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = bep.immutable();
                    }
                }
            }
        }
        if (best != null) {
            this.bridgeCandidatePos = best;
            this.bridgeCandidateKind = BridgeKind.DISPLAY_LINK;
            return;
        }

        this.bridgeCandidatePos = null;
        this.bridgeCandidateKind = BridgeKind.NONE;
    }

    private boolean isLoadedAt(BlockPos p) {
        if (this.level == null) return false;
        return this.level.getChunkSource().getChunk(p.getX() >> 4, p.getZ() >> 4, false) != null;
    }

    private void resolveBridge() {
        if (this.level == null) return;
        final BlockPos previousResolved = this.bridgeResolvedPos;
        final BridgeStatus previousStatus = this.bridgeStatus;
        final String previousName = this.bridgeResolvedName;

        if (this.bridgeCandidatePos == null || this.bridgeCandidateKind == BridgeKind.NONE) {
            this.bridgeStatus = BridgeStatus.NO_BRIDGE_FOUND;
            this.bridgeResolvedPos = null;
            this.bridgeResolvedName = null;
            this.afterBridgeStatusChange(previousResolved, previousStatus, previousName);
            return;
        }
        if (!this.isLoadedAt(this.bridgeCandidatePos)) {
            this.bridgeStatus = BridgeStatus.UNLOADED;
            this.bridgeResolvedPos = null;
            this.bridgeResolvedName = null;
            this.afterBridgeStatusChange(previousResolved, previousStatus, previousName);
            return;
        }

        BlockPos resolved = null;
        if (this.bridgeCandidateKind == BridgeKind.SMART_OBSERVER) {
            final BlockState soState = this.level.getBlockState(this.bridgeCandidatePos);
            final Direction facing = com.simibubi.create.content.redstone.DirectedDirectionalBlock.getTargetDirection(soState);
            if (facing == null) {
                this.bridgeStatus = BridgeStatus.INVALID_LINK;
                this.bridgeResolvedPos = null;
                this.bridgeResolvedName = null;
                this.afterBridgeStatusChange(previousResolved, previousStatus, previousName);
                return;
            }
            resolved = this.bridgeCandidatePos.relative(facing);
        } else {
            // Display Link chain walk up to BRIDGE_MAX_DL_HOPS.
            BlockPos cur = this.bridgeCandidatePos;
            final java.util.HashSet<BlockPos> seen = new java.util.HashSet<>();
            for (int hop = 0; hop < BRIDGE_MAX_DL_HOPS; ++hop) {
                if (!seen.add(cur)) {
                    this.bridgeStatus = BridgeStatus.CYCLE;
                    this.bridgeResolvedPos = null;
                    this.bridgeResolvedName = null;
                    this.afterBridgeStatusChange(previousResolved, previousStatus, previousName);
                    return;
                }
                if (!this.isLoadedAt(cur)) {
                    this.bridgeStatus = BridgeStatus.UNLOADED;
                    this.bridgeResolvedPos = null;
                    this.bridgeResolvedName = null;
                    this.afterBridgeStatusChange(previousResolved, previousStatus, previousName);
                    return;
                }
                final BlockEntity be = this.level.getBlockEntity(cur);
                if (!(be instanceof com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity dl)) {
                    this.bridgeStatus = BridgeStatus.INVALID_LINK;
                    this.bridgeResolvedPos = null;
                    this.bridgeResolvedName = null;
                    this.afterBridgeStatusChange(previousResolved, previousStatus, previousName);
                    return;
                }
                final BlockPos src = dl.getSourcePosition();
                if (src == null) {
                    this.bridgeStatus = BridgeStatus.INVALID_LINK;
                    this.bridgeResolvedPos = null;
                    this.bridgeResolvedName = null;
                    this.afterBridgeStatusChange(previousResolved, previousStatus, previousName);
                    return;
                }
                if (!this.isLoadedAt(src)) {
                    this.bridgeStatus = BridgeStatus.UNLOADED;
                    this.bridgeResolvedPos = null;
                    this.bridgeResolvedName = null;
                    this.afterBridgeStatusChange(previousResolved, previousStatus, previousName);
                    return;
                }
                final BlockEntity srcBE = this.level.getBlockEntity(src);
                if (srcBE instanceof com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity) {
                    cur = src;
                    continue;
                }
                resolved = src;
                break;
            }
            if (resolved == null) {
                this.bridgeStatus = BridgeStatus.CYCLE;
                this.bridgeResolvedPos = null;
                this.bridgeResolvedName = null;
                this.afterBridgeStatusChange(previousResolved, previousStatus, previousName);
                return;
            }
        }

        // Range guard: taxicab distance from us to resolved.
        if (resolved.distManhattan(this.worldPosition) > BRIDGE_MAX_TAXICAB) {
            this.bridgeStatus = BridgeStatus.OUT_OF_RANGE;
            this.bridgeResolvedPos = null;
            this.bridgeResolvedName = null;
            this.afterBridgeStatusChange(previousResolved, previousStatus, previousName);
            return;
        }
        // Loaded chunk guard.
        if (this.level.getChunkSource().getChunk(resolved.getX() >> 4, resolved.getZ() >> 4, false) == null) {
            this.bridgeStatus = BridgeStatus.UNLOADED;
            this.bridgeResolvedPos = null;
            this.bridgeResolvedName = null;
            this.afterBridgeStatusChange(previousResolved, previousStatus, previousName);
            return;
        }
        // Capability guard, use the same lookup paths as scanInventory() so vault controllers,
        // double chests, and other sided/special handlers aren't falsely rejected.
        final BlockEntity rbe = this.level.getBlockEntity(resolved);
        IItemHandler itemCap = null;
        if (rbe != null) {
            itemCap = this.resolveTargetItemHandler(rbe, Direction.NORTH);
        }
        if (itemCap == null) {
            itemCap = this.level.getCapability(Capabilities.ItemHandler.BLOCK, resolved, null);
        }
        final var fluidCap = this.level.getCapability(Capabilities.FluidHandler.BLOCK, resolved, null);
        boolean isBoiler = false;
        if (rbe instanceof FluidTankBlockEntity tank) {
            FluidTankBlockEntity ctrl = tank.getControllerBE();
            if (ctrl == null) ctrl = tank;
            if (ctrl.boiler != null && ctrl.boiler.isActive()) isBoiler = true;
        }
        // Cauldrons have no BE / no cap but are valid synthetic targets.
        boolean isSyntheticReadable = false;
        final BlockState rstate = this.level.getBlockState(resolved);
        if (rstate.getBlock() == Blocks.CAULDRON
                || rstate.getBlock() == Blocks.WATER_CAULDRON
                || rstate.getBlock() == Blocks.LAVA_CAULDRON
                || rstate.getBlock() == Blocks.POWDER_SNOW_CAULDRON) {
            isSyntheticReadable = true;
        }
        boolean hasEnergyReadout = false;
        if (rbe != null) {
            try {
                hasEnergyReadout = InstrumentSourceHelper.readSource(rbe, null).sourceKind() == InstrumentSourceHelper.SourceKind.ENERGY_STORAGE;
            } catch (Exception ignored) {
                hasEnergyReadout = false;
            }
        }
        if (itemCap == null && fluidCap == null && !isBoiler && !isSyntheticReadable && !hasEnergyReadout) {
            this.bridgeStatus = BridgeStatus.NO_CAPABILITY;
            this.bridgeResolvedPos = null;
            this.bridgeResolvedName = null;
            this.afterBridgeStatusChange(previousResolved, previousStatus, previousName);
            return;
        }

        this.bridgeStatus = BridgeStatus.OK;
        this.bridgeResolvedPos = resolved;
        this.bridgeResolvedName = this.level.getBlockState(resolved).getBlock().getName().getString();
        this.afterBridgeStatusChange(previousResolved, previousStatus, previousName);
    }

    private void afterBridgeStatusChange(@Nullable BlockPos prevPos, BridgeStatus prevStatus, @Nullable String prevName) {
        final boolean changed = prevStatus != this.bridgeStatus
                || !java.util.Objects.equals(prevPos, this.bridgeResolvedPos)
                || !java.util.Objects.equals(prevName, this.bridgeResolvedName);
        if (changed) {
            this.scanInventory();
            this.needsSync = true;
            this.setChanged();
        }
    }
}
