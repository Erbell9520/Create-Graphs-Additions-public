package com.createstats.blockentity.inventory;

import com.createstats.blockentity.BatteryControllerBE;
import com.createstats.blockentity.CapacitorBankBE;
import com.createstats.blockentity.PowerConnectorBE;
import com.createstats.blockentity.PowerDistributionUnitBE;
import com.createstats.blockentity.PowerMeterBE;
import com.createstats.compat.DieselCompat;
import com.createstats.compat.DieselCompatHelper;
import com.createstats.inventory.core.EnergyReadout;
import com.createstats.power.FeGridSolver;
import com.createstats.power.FeWireNetwork;
import com.createstats.power.IFeConsumer;
import com.createstats.util.InstrumentSourceHelper;
import com.createstats.util.InventoryLookupHelper;
import com.simibubi.create.content.fluids.tank.BoilerData;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.BeltInventory;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Nameable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InventoryScanner {
    private InventoryScanner() {
    }

    public enum Kind {
        NOTHING,
        CANT_READ,
        ITEMS,
        BELT,
        FLUID,
        CAULDRON,
        ENERGY,
        DIESEL
    }

    public record BeltEntry(int identity, String key, ItemStack sample, float position, int count) {
    }

    public static final class Reading {
        public Kind kind = Kind.NOTHING;
        public BlockPos pos = BlockPos.ZERO;
        public Component name = Component.empty();
        public int sizeA;
        public int sizeB;
        public int sizeC;

        public final List<ItemStack> slots = new ArrayList<>();
        public int slotCount;
        public final Map<String, Long> counts = new LinkedHashMap<>();
        public final Map<String, ItemStack> samples = new HashMap<>();

        public final Map<String, Long> partialRoom = new HashMap<>();
        public int emptySlots;
        public int emptySlotLimit = 64;
        public double fill;
        public long totalItems;
        public long freeItems;

        public final List<BeltEntry> belt = new ArrayList<>();
        public float beltGate;
        public int beltIndex;
        public int beltLength;
        public boolean beltPositive;
        public float beltSpeed;

        public int beltGlassSign = 1;

        public FluidStack fluid = FluidStack.EMPTY;
        public long fluidAmount;
        public long fluidCapacity;
        public int boilerLevel = -1;
        public int engines;
        public int whistles;
        public int cauldronLevels;

        public EnergyReadout.Figures energy;
        public boolean energyGrid;

        public long fuelAmount;
        public long fuelCapacity;
        public float rpm;
        public int dieselState;
        public boolean dieselMulti;

        public boolean readable() {
            return kind != Kind.NOTHING && kind != Kind.CANT_READ;
        }

        public long itemCapacity(String key) {
            final ItemStack sample = samples.get(key);
            final int max = sample == null ? 64 : Math.max(1, Math.min(emptySlotLimit, sample.getMaxStackSize()));
            return counts.getOrDefault(key, 0L) + partialRoom.getOrDefault(key, 0L) + (long) emptySlots * max;
        }
    }

    public record Context(ItemKeys keys, @Nullable Direction accessFace, @Nullable Boolean dieselMulti,
                          Direction displayFacing) {
    }

    public static Reading scan(Level level, BlockPos pos, Context ctx) {
        final Reading r = new Reading();
        r.pos = pos.immutable();
        final BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            r.kind = Kind.NOTHING;
            return r;
        }
        final BlockEntity be = level.getBlockEntity(pos);
        r.name = nameOf(state, be);

        if (be instanceof BeltBlockEntity belt) {
            readBelt(level, belt, ctx, r);
            return r;
        }
        if (be != null && DieselCompat.isAvailable() && DieselCompatHelper.isDieselSourceBlockEntity(be)
                && readDiesel(be, ctx, r)) {
            return r;
        }
        if (be != null && readEnergy(be, ctx, r)) {
            return r;
        }
        if (be != null && readItems(level, pos, be, ctx, r)) {
            return r;
        }
        if (readFluid(level, pos, be, r)) {
            return r;
        }
        if (readCauldron(state, r)) {
            return r;
        }
        r.kind = Kind.CANT_READ;
        return r;
    }

    private static Component nameOf(BlockState state, @Nullable BlockEntity be) {
        if (be instanceof Nameable n && n.hasCustomName() && n.getCustomName() != null) {
            return n.getCustomName();
        }
        return state.getBlock().getName();
    }

    private static boolean readItems(Level level, BlockPos pos, BlockEntity be, Context ctx, Reading r) {
        final IItemHandler handler;
        if (be instanceof ItemVaultBlockEntity vault) {
            final ItemVaultBlockEntity controller = vault.getControllerBE() != null ? vault.getControllerBE() : vault;
            handler = level.getCapability(Capabilities.ItemHandler.BLOCK, controller.getBlockPos(), null);
            r.sizeA = controller.getWidth();
            r.sizeB = controller.getWidth();
            r.sizeC = controller.getHeight();
        } else {
            handler = InventoryLookupHelper.resolveItemHandler(level, pos, be, ctx.accessFace());
        }
        if (handler == null) {
            return false;
        }
        final int slots = handler.getSlots();
        r.kind = Kind.ITEMS;
        r.slotCount = slots;
        double fill = 0.0;
        long total = 0L;
        long free = 0L;
        int minLimit = Integer.MAX_VALUE;
        for (int i = 0; i < slots; i++) {
            final ItemStack stack = handler.getStackInSlot(i);
            final int limit = Math.max(1, handler.getSlotLimit(i));
            if (slots <= 54) {
                r.slots.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
            }
            if (stack.isEmpty()) {
                r.emptySlots++;
                minLimit = Math.min(minLimit, limit);
                free += Math.min(limit, 64);
                continue;
            }
            final String key = ctx.keys().key(stack, level.registryAccess());
            final int max = Math.max(1, Math.min(limit, stack.getMaxStackSize()));
            r.counts.merge(key, (long) stack.getCount(), Long::sum);
            r.samples.putIfAbsent(key, stack.copyWithCount(1));
            final long room = Math.max(0, max - stack.getCount());
            r.partialRoom.merge(key, room, Long::sum);
            free += room;
            total += stack.getCount();
            fill += stack.getCount() / (double) max;
        }
        r.emptySlotLimit = minLimit == Integer.MAX_VALUE ? 64 : Math.min(64, minLimit);
        r.fill = slots > 0 ? Math.min(1.0, fill / slots) : 0.0;
        r.totalItems = total;
        r.freeItems = free;
        return true;
    }

    private static void readBelt(Level level, BeltBlockEntity segment, Context ctx, Reading r) {
        r.kind = Kind.BELT;
        final BeltBlockEntity controller = segment.getControllerBE() != null ? segment.getControllerBE() : segment;
        r.beltIndex = segment.index;
        r.beltLength = controller.beltLength;
        r.beltGate = segment.index + 0.5f;
        r.beltSpeed = controller.getDirectionAwareBeltMovementSpeed();
        r.beltPositive = r.beltSpeed > 0.0f;
        final Vec3i chain = segment.getBeltChainDirection();
        final Direction right = ctx.displayFacing().getClockWise();
        final int dot = chain.getX() * right.getStepX() + chain.getZ() * right.getStepZ();
        r.beltGlassSign = dot < 0 ? -1 : 1;
        final BeltInventory inventory = controller.getInventory();
        if (inventory == null) {
            return;
        }
        long total = 0L;
        for (TransportedItemStack t : inventory.getTransportedItems()) {
            if (t.stack.isEmpty()) {
                continue;
            }
            final String key = ctx.keys().key(t.stack, level.registryAccess());
            r.belt.add(new BeltEntry(System.identityHashCode(t), key, t.stack.copyWithCount(1), t.beltPosition,
                    t.stack.getCount()));
            r.counts.merge(key, (long) t.stack.getCount(), Long::sum);
            r.samples.putIfAbsent(key, t.stack.copyWithCount(1));
            total += t.stack.getCount();
        }
        r.totalItems = total;
    }

    private static boolean readFluid(Level level, BlockPos pos, @Nullable BlockEntity be, Reading r) {
        if (be instanceof FluidTankBlockEntity tank) {
            final FluidTankBlockEntity controller = tank.getControllerBE() != null ? tank.getControllerBE() : tank;
            r.sizeA = controller.getWidth();
            r.sizeB = controller.getWidth();
            r.sizeC = controller.getHeight();
            final BoilerData boiler = controller.boiler;
            if (boiler != null && boiler.isActive()) {
                final int size = controller.getTotalTankSize();
                r.boilerLevel = Math.max(0, Math.min(18, Math.min(boiler.activeHeat,
                        Math.min(boiler.getMaxHeatLevelForWaterSupply(), boiler.getMaxHeatLevelForBoilerSize(size)))));
                r.engines = boiler.attachedEngines;
                r.whistles = boiler.attachedWhistles;

                final FluidStack water = controller.getTankInventory().getFluid();
                r.kind = Kind.FLUID;
                r.fluid = water.copy();
                r.fluidAmount = water.getAmount();
                r.fluidCapacity = controller.getTankInventory().getCapacity();
                return true;
            }
        }
        final IFluidHandler fluids = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
        if (fluids == null || fluids.getTanks() <= 0) {
            return false;
        }
        FluidStack main = FluidStack.EMPTY;
        long amount = 0L;
        long capacity = 0L;
        for (int t = 0; t < fluids.getTanks(); t++) {
            final FluidStack stack = fluids.getFluidInTank(t);
            capacity += Math.max(0, fluids.getTankCapacity(t));
            if (!stack.isEmpty()) {
                if (main.isEmpty()) {
                    main = stack.copy();
                }
                if (FluidStack.isSameFluidSameComponents(main, stack)) {
                    amount += stack.getAmount();
                }
            }
        }
        r.kind = Kind.FLUID;
        r.fluid = main;
        r.fluidAmount = amount;
        r.fluidCapacity = capacity;
        return true;
    }

    private static boolean readCauldron(BlockState state, Reading r) {
        if (state.is(Blocks.LAVA_CAULDRON)) {
            r.kind = Kind.CAULDRON;
            r.fluid = new FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 1000);
            r.fluidAmount = 1000;
            r.fluidCapacity = 1000;
            r.cauldronLevels = 3;
            return true;
        }
        if (state.is(Blocks.WATER_CAULDRON)) {
            final int lvl = state.getValue(LayeredCauldronBlock.LEVEL);
            r.kind = Kind.CAULDRON;
            r.fluid = new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000);
            r.fluidAmount = lvl * 1000L / 3L;
            r.fluidCapacity = 1000;
            r.cauldronLevels = 3;
            return true;
        }
        if (state.is(Blocks.CAULDRON) || state.is(Blocks.POWDER_SNOW_CAULDRON)) {
            r.kind = Kind.CAULDRON;
            r.fluid = FluidStack.EMPTY;
            r.fluidAmount = state.is(Blocks.POWDER_SNOW_CAULDRON)
                    ? state.getValue(LayeredCauldronBlock.LEVEL) * 1000L / 3L : 0L;
            r.fluidCapacity = 1000;
            r.cauldronLevels = 3;
            return true;
        }
        return false;
    }

    private static boolean readEnergy(BlockEntity be, Context ctx, Reading r) {
        final InstrumentSourceHelper.SourceReadout readout;
        try {
            readout = InstrumentSourceHelper.readSource(be, ctx.accessFace());
        } catch (RuntimeException e) {
            return false;
        }
        final InstrumentSourceHelper.SourceKind kind = readout.sourceKind();
        if (kind != InstrumentSourceHelper.SourceKind.ENERGY_STORAGE
                && kind != InstrumentSourceHelper.SourceKind.POWER_GRID) {
            return false;
        }
        if (readout.metrics().isEmpty()) {
            return false;
        }
        final Map<String, Float> metrics = new HashMap<>();
        for (InstrumentSourceHelper.MetricSample s : readout.metrics().values()) {
            metrics.put(s.metricId(), s.value());
        }
        final EnergyReadout.Shape shape;
        EnergyReadout.Reserve reserve = EnergyReadout.Reserve.NONE;

        EnergyReadout.Outlook outlook = EnergyReadout.Outlook.UNMANAGED;
        if (be instanceof PowerConnectorBE connector) {
            shape = EnergyReadout.Shape.CONNECTOR;
            final FeWireNetwork network = connector.network();
            if (network != null) {
                final FeGridSolver.Result result = network.lastResult();
                reserve = new EnergyReadout.Reserve(result.reserveCapacity() > 0, result.reserve(),
                        result.reserveCapacity(), result.charged(), result.discharged());

                if (be.getLevel() != null && network.isPduManaged(be.getLevel().getGameTime())) {
                    outlook = EnergyReadout.Outlook.managed(-1, -1);
                }
            }
        } else if (be instanceof PowerDistributionUnitBE board) {
            shape = EnergyReadout.Shape.BOARD;
            if (board.reserveTankCount() > 0) {
                reserve = new EnergyReadout.Reserve(true, board.reserveStored(), board.reserveCapacity(), 0L, 0L);
            }

            outlook = EnergyReadout.Outlook.managed(board.linkedShortInTicks(), -1);
        } else if (be instanceof BatteryControllerBE controller) {
            shape = EnergyReadout.Shape.CONTROLLER;
            outlook = EnergyReadout.Outlook.ofForecast(controller.outlook());
        } else if (be instanceof PowerMeterBE) {
            shape = EnergyReadout.Shape.METER;
        } else if (be instanceof CapacitorBankBE bank) {
            shape = EnergyReadout.Shape.BANK;

            if (bank.isManaged()) {
                outlook = EnergyReadout.Outlook.managed(-1, bank.managedFullInTicks());
            }
        } else if (be instanceof IFeConsumer) {
            shape = EnergyReadout.Shape.DEVICE;
        } else {
            shape = EnergyReadout.Shape.BANK;
        }
        final EnergyReadout.Figures figures = EnergyReadout.read(shape, metrics, reserve, outlook);
        if (!figures.hasStored() && !figures.hasFlows()) {
            return false;
        }
        r.kind = Kind.ENERGY;
        r.energy = figures;
        r.energyGrid = kind == InstrumentSourceHelper.SourceKind.POWER_GRID;
        return true;
    }

    private static boolean readDiesel(BlockEntity be, Context ctx, Reading r) {
        final boolean multi = ctx.dieselMulti() != null ? ctx.dieselMulti()
                : DieselCompatHelper.isMultiStructureMode(null, be);
        final InstrumentSourceHelper.SourceReadout readout;
        try {
            readout = DieselCompatHelper.readSource(be, ctx.accessFace(), multi);
        } catch (RuntimeException e) {
            return false;
        }
        if (readout == null || readout.metrics().isEmpty()) {
            return false;
        }
        final Map<String, InstrumentSourceHelper.MetricSample> m = readout.metrics();
        final float amount = value(m, "fuel_amount", "fluid_amount", "oil_remaining_mb");
        final float capacity = Math.max(amount, value(m, "fuel_capacity", "fluid_capacity", "tank_capacity"));
        final float rpm = Math.abs(value(m, "rpm", "generated_speed", "generated_rpm"));
        final boolean running = value(m, "running", "burner_lit") > 0.5f || rpm > 0.01f;
        final boolean fuel = m.containsKey("valid_fuel") || m.containsKey("pumpjack_valid")
                ? value(m, "valid_fuel", "pumpjack_valid") > 0.5f : amount > 0.0f;
        r.kind = Kind.DIESEL;
        r.fuelAmount = Math.round(amount);
        r.fuelCapacity = Math.round(capacity);
        r.rpm = rpm;
        r.dieselState = !fuel ? 2 : running ? 1 : 0;
        r.dieselMulti = multi;
        r.fluid = FluidStack.EMPTY;
        r.fluidAmount = r.fuelAmount;
        r.fluidCapacity = r.fuelCapacity;
        return true;
    }

    private static float value(Map<String, InstrumentSourceHelper.MetricSample> m, String... ids) {
        for (String id : ids) {
            final InstrumentSourceHelper.MetricSample s = m.get(id);
            if (s != null) {
                return s.value();
            }
        }
        return 0.0f;
    }
}
