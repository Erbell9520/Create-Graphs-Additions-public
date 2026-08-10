package com.createstats.blockentity;

import java.util.List;
import java.util.Locale;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.decoration.bracket.BracketedBlockEntityBehaviour;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.PipeConnection;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.createmod.catnip.data.Couple;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** [1] */
public class FlowMeterBE extends SmartBlockEntity implements IHaveGoggleInformation {

    private FluidStack currentFluid = FluidStack.EMPTY;
    private int flowRateMbPerTick;
    private Direction flowDirection;
    private long totalMovedMb;

    // Redstone trigger, configured from the Stats Display panel GUI. When enabled the pipe itself
    // emits a weak redstone signal (the linked display panel emits its own signal in parallel).
    private boolean triggerEnabled;
    private int triggerMode; // 0 = flowing (binary presence), 1 = amount (mB/t threshold)
    private int triggerThresholdMb;
    private boolean triggerAbove = true;
    private int emittedRedstone;

    // Last reading pushed to the client (throttled) so the animated renderer + GUI see live data.
    private int syncedRate = -1;
    private FluidStack syncedFluid = FluidStack.EMPTY;
    private Direction syncedDir;
    private long lastReadingSyncTick;

    public FlowMeterBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new MeterPipeBehaviour(this));
        behaviours.add(new BracketedBlockEntityBehaviour(this));
    }

    @Override
    public void tick() {
        super.tick();
        Level lvl = this.level;
        if (lvl == null || lvl.isClientSide) {
            return;
        }
        this.sampleFlow();
        this.updateEmittedRedstone(false);
        this.maybeSyncReading();
    }

    private void sampleFlow() {
        FluidTransportBehaviour pipe = this.getBehaviour(FluidTransportBehaviour.TYPE);
        if (pipe == null) {
            this.clearSample();
            return;
        }
        BlockState state = this.getBlockState();
        Direction.Axis axis = state.hasProperty(BlockStateProperties.AXIS)
                ? state.getValue(BlockStateProperties.AXIS)
                : Direction.Axis.Y;
        Direction positive = Direction.get(Direction.AxisDirection.POSITIVE, axis);

        FluidStack bestFluid = FluidStack.EMPTY;
        int bestRate = 0;
        Direction bestMovement = null;
        for (Direction side : new Direction[] { positive, positive.getOpposite() }) {
            PipeConnection.Flow flow = pipe.getFlow(side);
            if (flow == null || flow.fluid.isEmpty() || !flow.complete) {
                continue;
            }
            int rate = 1;
            PipeConnection connection = pipe.getConnection(side);
            if (connection != null) {
                Couple<Float> pressure = connection.getPressure();
                float magnitude = Math.max(Math.abs(pressure.getFirst()), Math.abs(pressure.getSecond()));
                rate = Math.max(1, (int) (magnitude / 2.0f));
            }
            // A flow flagged inbound enters from this side, so the fluid travels toward the opposite face.
            Direction movement = flow.inbound ? side.getOpposite() : side;
            if (bestFluid.isEmpty() || rate > bestRate) {
                bestFluid = flow.fluid;
                bestRate = rate;
                bestMovement = movement;
            }
        }

        // [2]
        if (bestRate > 0 && bestMovement != null && !bestFluid.isEmpty() && this.level != null) {
            final BlockPos downstream = this.getBlockPos().relative(bestMovement);
            final IFluidHandler handler =
                    this.level.getCapability(Capabilities.FluidHandler.BLOCK, downstream, bestMovement.getOpposite());
            if (handler != null
                    && handler.fill(bestFluid.copyWithAmount(Math.max(1, bestRate)), IFluidHandler.FluidAction.SIMULATE) <= 0) {
                bestRate = 0;
                bestMovement = null;
            }
        }

        this.currentFluid = bestFluid;
        this.flowRateMbPerTick = bestRate;
        this.flowDirection = bestRate > 0 ? bestMovement : null;
        if (bestRate > 0) {
            this.totalMovedMb += bestRate;
        }
    }

    private void clearSample() {
        this.currentFluid = FluidStack.EMPTY;
        this.flowRateMbPerTick = 0;
        this.flowDirection = null;
    }

    public int getFlowRateMbPerTick() {
        return this.flowRateMbPerTick;
    }

    public long getTotalMovedMb() {
        return this.totalMovedMb;
    }

    public FluidStack getCurrentFluid() {
        return this.currentFluid;
    }

    public boolean hasFlow() {
        return this.flowRateMbPerTick > 0 && !this.currentFluid.isEmpty();
    }

    public int getFlowDirectionSign() {
        if (this.flowDirection == null) {
            return 0;
        }
        return this.flowDirection.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : -1;
    }

    public String getFluidId() {
        if (this.currentFluid.isEmpty()) {
            return "";
        }
        net.minecraft.resources.ResourceLocation id =
                net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(this.currentFluid.getFluid());
        return id != null ? id.toString() : "";
    }

    public Component getFluidLabel() {
        return this.currentFluid.isEmpty()
                ? Component.translatable("create_stats.flow_meter.fluid.none")
                : this.currentFluid.getHoverName();
    }

    public Component getDirectionLabel() {
        if (this.flowDirection == null || this.flowRateMbPerTick <= 0) {
            return Component.translatable("create_stats.flow_meter.direction.idle");
        }
        return Component.translatable("create_stats.direction." + this.flowDirection.getName());
    }

    public boolean isTriggerEnabled() {
        return this.triggerEnabled;
    }

    public int getTriggerMode() {
        return this.triggerMode;
    }

    public int getTriggerThresholdMb() {
        return this.triggerThresholdMb;
    }

    public boolean isTriggerAbove() {
        return this.triggerAbove;
    }

    public int getEmittedRedstone() {
        return this.emittedRedstone;
    }

    /** Applies a trigger configuration pushed from the display panel GUI (server side). */
    public void applyTrigger(boolean enabled, int mode, int threshold, boolean above) {
        this.triggerEnabled = enabled;
        this.triggerMode = (mode == 1) ? 1 : 0;
        this.triggerThresholdMb = Math.max(0, threshold);
        this.triggerAbove = above;
        this.updateEmittedRedstone(false);
        this.notifyUpdate();
    }

    private int evaluateTrigger() {
        if (!this.triggerEnabled) {
            return 0;
        }
        final boolean condition;
        if (this.triggerMode == 1) {
            condition = this.triggerAbove
                    ? this.flowRateMbPerTick >= this.triggerThresholdMb
                    : this.flowRateMbPerTick <= this.triggerThresholdMb;
        } else {
            condition = this.hasFlow();
        }
        return condition ? 15 : 0;
    }

    private void updateEmittedRedstone(boolean forceNeighborUpdate) {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        final int next = this.evaluateTrigger();
        if (next != this.emittedRedstone) {
            this.emittedRedstone = next;
            this.notifyUpdate();
            this.level.updateNeighborsAt(this.getBlockPos(), this.getBlockState().getBlock());
        } else if (forceNeighborUpdate) {
            this.level.updateNeighborsAt(this.getBlockPos(), this.getBlockState().getBlock());
        }
    }

    /** [3] */
    private void maybeSyncReading() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        final boolean fluidChanged = !FluidStack.isSameFluidSameComponents(this.currentFluid, this.syncedFluid);
        final boolean dirChanged = this.flowDirection != this.syncedDir;
        final boolean flowingChanged = (this.flowRateMbPerTick > 0) != (this.syncedRate > 0);
        final boolean rateChanged = Math.abs(this.flowRateMbPerTick - this.syncedRate) > Math.max(1, this.syncedRate / 5);
        if (!(fluidChanged || dirChanged || flowingChanged || rateChanged)) {
            return;
        }
        if (this.level.getGameTime() - this.lastReadingSyncTick < 8L) {
            return;
        }
        this.syncedRate = this.flowRateMbPerTick;
        this.syncedFluid = this.currentFluid.copy();
        this.syncedDir = this.flowDirection;
        this.lastReadingSyncTick = this.level.getGameTime();
        this.notifyUpdate();
    }

    // goggle overlay
    // Shown whenever the player wears Create's goggles or this mod's Pilot Helmet (registered as a
    // goggles predicate in CreateStats.commonSetup), so the same live reading appears through both.

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("  ").append(
                Component.translatable("block.create_stats.flow_meter").withStyle(ChatFormatting.GRAY)));

        final int rate = this.flowRateMbPerTick;
        if (this.hasFlow()) {
            tooltip.add(Component.literal("    ").append(this.currentFluid.getHoverName()));
            tooltip.add(Component.literal("    ").append(
                    Component.literal(String.format(Locale.ROOT, "%,d mB/t \u00b7 %,d mB/s", rate, rate * 20))
                            .withStyle(ChatFormatting.AQUA)));
            tooltip.add(Component.literal("    ").append(
                    Component.translatable("block.create_stats.flow_meter.goggle.flowing", this.getDirectionLabel())
                            .withStyle(ChatFormatting.GREEN)));
        } else {
            tooltip.add(Component.literal("    ").append(
                    Component.translatable("block.create_stats.flow_meter.goggle.no_flow").withStyle(ChatFormatting.DARK_GRAY)));
        }

        tooltip.add(Component.literal("    ").append(
                Component.translatable("block.create_stats.flow_meter.goggle.total",
                        String.format(Locale.ROOT, "%,d", this.totalMovedMb))
                        .withStyle(ChatFormatting.DARK_AQUA)));

        if (this.triggerEnabled) {
            final boolean active = this.emittedRedstone > 0;
            final Component desc = this.triggerMode == 1
                    ? Component.translatable(this.triggerAbove
                                    ? "block.create_stats.flow_meter.goggle.alarm_above"
                                    : "block.create_stats.flow_meter.goggle.alarm_below",
                            String.format(Locale.ROOT, "%,d", this.triggerThresholdMb))
                    : Component.translatable("block.create_stats.flow_meter.goggle.alarm_flowing");
            tooltip.add(Component.literal("    ").append(
                    (active ? Component.translatable("block.create_stats.flow_meter.goggle.alarm_active", desc) : desc)
                            .copy().withStyle(active ? ChatFormatting.RED : ChatFormatting.YELLOW)));
        }

        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putLong("TotalMovedMb", this.totalMovedMb);
        tag.putBoolean("TriggerEnabled", this.triggerEnabled);
        tag.putInt("TriggerMode", this.triggerMode);
        tag.putInt("TriggerThreshold", this.triggerThresholdMb);
        tag.putBoolean("TriggerAbove", this.triggerAbove);
        tag.putInt("EmittedRedstone", this.emittedRedstone);
        tag.put("Fluid", this.currentFluid.saveOptional(registries));
        tag.putInt("Rate", this.flowRateMbPerTick);
        tag.putInt("FlowDir", this.flowDirection == null ? -1 : this.flowDirection.get3DDataValue());
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.totalMovedMb = tag.getLong("TotalMovedMb");
        this.triggerEnabled = tag.getBoolean("TriggerEnabled");
        this.triggerMode = tag.getInt("TriggerMode");
        this.triggerThresholdMb = tag.getInt("TriggerThreshold");
        this.triggerAbove = tag.contains("TriggerAbove") ? tag.getBoolean("TriggerAbove") : true;
        this.emittedRedstone = tag.getInt("EmittedRedstone");
        this.currentFluid = FluidStack.parseOptional(registries, tag.getCompound("Fluid"));
        this.flowRateMbPerTick = tag.getInt("Rate");
        final int fd = tag.contains("FlowDir") ? tag.getInt("FlowDir") : -1;
        this.flowDirection = fd < 0 ? null : Direction.from3DDataValue(fd);
    }

    private class MeterPipeBehaviour extends FluidTransportBehaviour {

        MeterPipeBehaviour(SmartBlockEntity be) {
            super(be);
        }

        @Override
        public boolean canHaveFlowToward(BlockState state, Direction direction) {
            return state.hasProperty(BlockStateProperties.AXIS)
                    && state.getValue(BlockStateProperties.AXIS) == direction.getAxis();
        }
    }
}
