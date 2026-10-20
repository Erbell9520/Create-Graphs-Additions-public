package com.createstats.book.specimen;

import com.createstats.blockentity.BatteryControllerBE;
import com.createstats.blockentity.CableBE;
import com.createstats.blockentity.CapacitorBankBE;
import com.createstats.blockentity.DiagnosticJunctionBE;
import com.createstats.blockentity.IndoorCableBE;
import com.createstats.blockentity.WallOutletBE;
import com.createstats.book.scene.BlockFigure;
import com.createstats.book.scene.BlockFigures;
import com.createstats.book.scene.FigureWorld;
import com.createstats.power.BatteryControllerPlan;
import com.createstats.power.CableLinks;
import com.createstats.power.IndoorCableAnchor;
import com.createstats.power.IndoorCableLinks;
import com.createstats.power.ReserveProjection;
import com.createstats.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

// the block's own BE class answering example getters. never ticked, never saved
@OnlyIn(Dist.CLIENT)
public final class BlockFixtures {
    private BlockFixtures() {
    }

    static final BlockPos AT = CarryFixtures.AT;

    @Nullable
    public static BlockEntity create(BlockFigure figure, int index, BlockState state,
                                     Function<BlockPos, BlockState> stateAt) {
        final String fixture = figure.placements().get(index).fixture();
        return switch (fixture) {
            case StoreFixtures.BANK_FACE -> new BankFace(state);
            case StoreFixtures.CONTROLLER_FACE -> new ControllerFace(state);
            case PduPlates.FACE -> pduFace(state);
            case CarryFixtures.CABLE -> new RunCable(state, figure, index, stateAt);
            case CarryFixtures.INDOOR -> new RunIndoorCable(state, figure, index, stateAt);
            case CarryFixtures.JUNCTION_CABLE, CarryFixtures.JUNCTION_WIRE ->
                    new JunctionFace(state, figure, index, CarryFixtures.junction(fixture));
            case CarryFixtures.OUTLET -> new OutletCord(state, figure, index);
            default -> null;
        };
    }

    static float reachInto(@Nullable BlockState ahead, Direction towards) {
        final float minimum = 1.0f / 16.0f;
        if (ahead == null) {
            return minimum;
        }
        try {
            final VoxelShape shape = ahead.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            if (shape.isEmpty()) {
                return minimum;
            }
            final Direction.Axis axis = towards.getAxis();
            final double depth = towards.getAxisDirection() == Direction.AxisDirection.POSITIVE
                    ? shape.min(axis) : 1.0 - shape.max(axis);
            return (float) Math.max(minimum, Math.min(0.4, depth));
        } catch (RuntimeException needsAWorld) {
            return minimum;
        }
    }

    private static final class RunCable extends CableBE {
        private final FigureWorld world;
        private final BlockPos cell;
        private final Function<BlockPos, BlockState> stateAt;

        RunCable(BlockState state, BlockFigure figure, int index, Function<BlockPos, BlockState> stateAt) {
            super(ModBlockEntities.CABLE.get(), CarryFixtures.positionOf(figure, index), state);
            this.world = FigureWorld.of(figure);
            this.cell = world.cellOf(index);
            this.stateAt = stateAt;
            for (final BlockPos far : world.boresOf(cell)) {
                addBore(AT.offset(far));
            }
        }

        @Override
        public CableLinks.Link link(Direction face, Direction towards) {
            return world.link(cell, face, towards);
        }

        @Override
        public boolean neighborOtherGrade(Direction face, Direction towards, CableLinks.Link kind) {
            return world.otherGrade(cell, face, towards, kind);
        }

        @Override
        public float reachInto(Direction towards) {
            return BlockFixtures.reachInto(stateAt.apply(cell.relative(towards)), towards);
        }
    }

    private static final class RunIndoorCable extends IndoorCableBE {
        private final FigureWorld world;
        private final BlockPos cell;
        private final Function<BlockPos, BlockState> stateAt;

        RunIndoorCable(BlockState state, BlockFigure figure, int index, Function<BlockPos, BlockState> stateAt) {
            super(ModBlockEntities.INDOOR_CABLE.get(), CarryFixtures.positionOf(figure, index), state);
            this.world = FigureWorld.of(figure);
            this.cell = world.cellOf(index);
            this.stateAt = stateAt;
            for (final BlockPos far : world.boresOf(cell)) {
                addBore(AT.offset(far));
            }
        }

        @Override
        public IndoorCableLinks.Link link(Direction towards) {
            return world.indoorLink(cell, towards);
        }

        @Override
        public IndoorCableAnchor anchorTowards(Direction towards) {
            return world.anchorTowards(cell, towards);
        }

        @Override
        public float reachInto(Direction towards) {
            return BlockFixtures.reachInto(stateAt.apply(cell.relative(towards)), towards);
        }
    }

    private static final class JunctionFace extends DiagnosticJunctionBE {
        private final CarryFixtures.JunctionReading r;

        JunctionFace(BlockState state, BlockFigure figure, int index, CarryFixtures.JunctionReading reading) {
            super(ModBlockEntities.DIAGNOSTIC_JUNCTION.get(), CarryFixtures.positionOf(figure, index), state);
            this.r = reading;
        }

        @Override
        public int flow() {
            return r.flow();
        }

        @Override
        public int capacity() {
            return r.capacity();
        }

        @Override
        public int buffer() {
            return r.buffer();
        }

        @Override
        public int maxBuffer() {
            return r.maxBuffer();
        }

        @Override
        public Fault fault() {
            return r.fault();
        }

        @Override
        public Fault channel(int index) {
            return index >= 0 && index < r.channels().size() ? r.channels().get(index) : Fault.NO_BUS;
        }

        @Override
        public int channelFlow(int channel) {
            return channel >= 0 && channel < r.channelFlow().length ? r.channelFlow()[channel] : 0;
        }

        @Override
        public int channelCapacity(int channel) {
            return channel >= 0 && channel < r.channelCapacity().length ? r.channelCapacity()[channel] : 0;
        }

        @Override
        public int channelBuffer(int channel) {
            return channel >= 0 && channel < r.channelBuffer().length ? r.channelBuffer()[channel] : 0;
        }

        @Override
        public float loadFraction() {
            return r.loadFraction();
        }

        @Override
        public boolean isMerged() {
            return r.merged();
        }

        @Override
        public boolean isCutOff() {
            return r.cutOff();
        }
    }

    private static final class OutletCord extends WallOutletBE {
        @Nullable
        private final CarryFixtures.Cord cord;

        OutletCord(BlockState state, BlockFigure figure, int index) {
            super(ModBlockEntities.WALL_OUTLET.get(), CarryFixtures.positionOf(figure, index), state);
            this.cord = CarryFixtures.cord(figure, index);
        }

        @Override
        public boolean anyCorded() {
            return cord != null;
        }

        @Override
        @Nullable
        public BlockPos cordTarget(int gang) {
            return gang == 0 && cord != null ? AT.offset(cord.machine()) : null;
        }

        @Override
        @Nullable
        public Direction cordFace(int gang) {
            return gang == 0 && cord != null ? cord.face() : null;
        }

        @Override
        public List<Vec3> cordRoute(int gang) {
            if (gang != 0 || cord == null) {
                return List.of();
            }
            final List<Vec3> out = new ArrayList<>(cord.route().size());
            for (final Vec3 v : cord.route()) {
                out.add(v.add(AT.getX(), AT.getY(), AT.getZ()));
            }
            return out;
        }

        @Override
        public boolean isLinked() {
            return false;
        }
    }

    private static final class BankFace extends CapacitorBankBE {
        private final StoreFixtures.BankReading r = StoreFixtures.bank();

        BankFace(BlockState state) {
            super(ModBlockEntities.CAPACITOR_BANK.get(), AT, state);
        }

        @Override
        public boolean isRenderController() {
            return true;
        }

        @Override
        public boolean isSolidBox() {
            return true;
        }

        @Override
        public BlockPos structureMin() {
            return getBlockPos();
        }

        @Override
        public BlockPos structureMax() {
            final int s = BlockFigures.BANK_SIDE - 1;
            return getBlockPos().offset(s, s, s);
        }

        @Override
        public int groupSize() {
            return r.blocks();
        }

        @Override
        public float groupFillFraction() {
            return r.fill();
        }

        @Override
        public int groupEnergy() {
            return r.stored();
        }

        @Override
        public int groupCapacity() {
            return r.capacity();
        }

        @Override
        public int chargeFePerTick() {
            return r.in();
        }

        @Override
        public int dischargeFePerTick() {
            return r.out();
        }

        @Override
        public int chargeRatePerTick() {
            return r.rate();
        }

        @Override
        public int enduranceTicks() {
            return r.enduranceTicks();
        }

        @Override
        public boolean isManaged() {
            return false;
        }

        @Override
        public int managedSettlePct() {
            return -1;
        }

        @Override
        public int managedFullInTicks() {
            return -1;
        }
    }

    private static BlockEntity pduFace(BlockState state) {
        final com.createstats.blockentity.PowerDistributionUnitBE be =
                new com.createstats.blockentity.PowerDistributionUnitBE(ModBlockEntities.POWER_DISTRIBUTION_UNIT.get(),
                        AT, state);
        final net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        be.handleUpdateTag(PduPlates.fixtureTag(null, false), mc.level != null ? mc.level.registryAccess()
                : net.minecraft.core.RegistryAccess.EMPTY);
        return be;
    }

    private static final class ControllerFace extends BatteryControllerBE {
        private final StoreFixtures.ControllerReading r = StoreFixtures.controller();

        ControllerFace(BlockState state) {
            super(ModBlockEntities.BATTERY_CONTROLLER.get(), AT, state);
        }

        @Override
        public int reserveCapacity() {
            return r.capacity();
        }

        @Override
        public int reservePct() {
            return r.pct();
        }

        @Override
        public int reserveRateFePerTick() {
            return r.rate();
        }

        @Override
        public int generationFePerTick() {
            return r.generation();
        }

        @Override
        public int deliveredFePerTick() {
            return r.delivered();
        }

        @Override
        public BatteryControllerPlan.Reason outputReason(int index) {
            return index >= 0 && index < r.lamps().size() ? r.lamps().get(index)
                    : BatteryControllerPlan.Reason.UNWIRED;
        }

        @Override
        public ReserveProjection.Result outlook() {
            return r.outlook();
        }
    }
}
