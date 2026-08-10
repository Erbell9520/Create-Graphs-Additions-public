package com.createstats.blockentity;

import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import com.simibubi.create.content.logistics.tunnel.BrassTunnelBlockEntity;
import com.simibubi.create.content.logistics.tunnel.BrassTunnelFilterSlot;
import com.simibubi.create.content.logistics.tunnel.BrassTunnelModeSlot;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.SidedFilteringBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.utility.BlockHelper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.createmod.catnip.lang.Lang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;

import org.jetbrains.annotations.Nullable;

public class OverflowGateBE extends BrassTunnelBlockEntity {

    private static final int OVERFLOW_DELAY_TICKS = 20;

    private ItemStack bufferedStack;
    final OverflowGateItemHandler tunnelHandler;
    private int roundRobinCursor;
    private int blockedOutputTicks;
    private ScrollOptionBehaviour<OverflowMode> overflowMode;
    private SidedFilteringBehaviour overflowFiltering;

    public OverflowGateBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        bufferedStack = ItemStack.EMPTY;
        tunnelHandler = new OverflowGateItemHandler(this);
        roundRobinCursor = 0;
        blockedOutputTicks = 0;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(overflowMode = new ScrollOptionBehaviour<>(OverflowMode.class,
            Component.translatable("create_stats.overflow_gate.mode_label"), this, new BrassTunnelModeSlot()));
        overflowMode.onlyActiveWhen(this::hasDistributionBehaviour);
    }

    @Override
    public void addBehavioursDeferred(List<BlockEntityBehaviour> behaviours) {
        super.addBehavioursDeferred(behaviours);
    }

    @Override
    protected SidedFilteringBehaviour createSidedFilter() {
        overflowFiltering = new SidedFilteringBehaviour(this, new BrassTunnelFilterSlot(), this::makeFilter,
            side -> side.getAxis().isHorizontal());
        return overflowFiltering;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || bufferedStack.isEmpty()) {
            return;
        }

        BeltBlockEntity beltBelow = BeltHelper.getSegmentBE(level, worldPosition.below());
        if (beltBelow == null || beltBelow.getSpeed() == 0) {
            return;
        }

        routePendingStack();
    }

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        compound.put("OverflowGateStack", bufferedStack.saveOptional(registries));
        compound.putInt("OverflowGateCursor", roundRobinCursor);
        compound.putInt("OverflowGateBlockedTicks", blockedOutputTicks);
        super.write(compound, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        bufferedStack = ItemStack.parseOptional(registries, compound.getCompound("OverflowGateStack"));
        // Migration: old builds stored three separate per-tier cursors
        // (OverflowGateNextFiltered/Any/Overflow). Those are intentionally
        // discarded; the new algorithm uses a single unified cursor.
        roundRobinCursor = compound.getInt("OverflowGateCursor");
        blockedOutputTicks = compound.getInt("OverflowGateBlockedTicks");
        super.read(compound, registries, clientPacket);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        invalidateCapabilities();
    }

    @Override
    public void destroy() {
        super.destroy();
        if (!bufferedStack.isEmpty()) {
            Block.popResource(level, worldPosition, bufferedStack);
            bufferedStack = ItemStack.EMPTY;
        }
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            com.createstats.registry.ModBlockEntities.OVERFLOW_GATE.get(),
            (be, side) -> (side == null || side == Direction.UP) ? be.tunnelHandler : null
        );
    }

    @Override
    public boolean hasDistributionBehaviour() {
        return true;
    }

    @Override
    public boolean canTakeItems() {
        return bufferedStack.isEmpty();
    }

    @Override
    public boolean canInsert(Direction side, ItemStack stack) {
        return canTakeItems();
    }

    @Override
    public void setStackToDistribute(ItemStack stack, @Nullable Direction enteredFrom) {
        bufferedStack = stack;
        blockedOutputTicks = 0;
        sendData();
        setChanged();
    }

    public void setStackToDistribute(ItemStack stack) {
        setStackToDistribute(stack, null);
    }

    @Override
    public List<ItemStack> grabAllStacksOfGroup(boolean simulate) {
        if (bufferedStack.isEmpty()) {
            return Collections.emptyList();
        }
        ItemStack copy = bufferedStack.copy();
        if (!simulate) {
            bufferedStack = ItemStack.EMPTY;
            blockedOutputTicks = 0;
            sendData();
            setChanged();
        }
        return Collections.singletonList(copy);
    }

    public List<ItemStack> grabStoredStacks(boolean simulate) {
        return grabAllStacksOfGroup(simulate);
    }

    public ItemStack getStoredStack() {
        return bufferedStack;
    }

    public OverflowMode getOverflowMode() {
        return overflowMode == null ? OverflowMode.NONE : overflowMode.get();
    }

    public Component getRoutingSummary() {
        Direction forward = getMovementFacing();
        Direction left = forward.getCounterClockWise();
        Direction right = forward.getClockWise();
        Component priority = Component.translatable(getOverflowMode() == OverflowMode.NONE
            ? "create_stats.overflow_gate.summary.priority.no_overflow"
            : "create_stats.overflow_gate.summary.priority.overflow");
        Component overflow = switch (getOverflowMode()) {
            case NONE -> Component.translatable("create_stats.overflow_gate.summary.overflow.disabled");
            case LEFT -> Component.translatable("create_stats.overflow_gate.summary.overflow.side",
                Component.translatable("create_stats.overflow_gate.side.left"), formatDirection(left), OVERFLOW_DELAY_TICKS);
            case FORWARD -> Component.translatable("create_stats.overflow_gate.summary.overflow.side",
                Component.translatable("create_stats.overflow_gate.side.forward"), formatDirection(forward), OVERFLOW_DELAY_TICKS);
            case RIGHT -> Component.translatable("create_stats.overflow_gate.summary.overflow.side",
                Component.translatable("create_stats.overflow_gate.side.right"), formatDirection(right), OVERFLOW_DELAY_TICKS);
        };
        return Component.translatable("create_stats.overflow_gate.summary",
            priority, formatDirection(forward), formatDirection(left), formatDirection(forward), formatDirection(right), overflow);
    }

    private void routePendingStack() {
        ItemStack remaining = bufferedStack.copy();
        int initialCount = remaining.getCount();
        int previousBlockedTicks = blockedOutputTicks;

        Direction overflowSide = getOverflowSide();
        List<Direction> outputs = gatherValidOutputs();
        if (outputs.isEmpty()) {
            return;
        }

        Direction overflowTarget = null;
        List<Direction> normalTargets = new ArrayList<>(outputs.size());
        for (Direction side : outputs) {
            if (side == overflowSide) {
                overflowTarget = side;
                continue;
            }
            normalTargets.add(side);
        }

        // Phase A: non-overflow outputs with an explicit matching filter.
        List<Direction> phaseA = new ArrayList<>(normalTargets.size());
        for (Direction side : normalTargets) {
            if (!flapFilterEmpty(side) && testFlapFilter(side, remaining)) {
                phaseA.add(side);
            }
        }
        remaining = distributeRoundRobin(remaining, phaseA);

        // Phase B: non-overflow wildcard outputs.
        if (!remaining.isEmpty()) {
            List<Direction> phaseB = new ArrayList<>(normalTargets.size());
            for (Direction side : normalTargets) {
                if (flapFilterEmpty(side)) {
                    phaseB.add(side);
                }
            }
            remaining = distributeRoundRobin(remaining, phaseB);
        }

        boolean normalMoved = remaining.getCount() < initialCount;
        if (normalMoved || remaining.isEmpty()) {
            blockedOutputTicks = 0;
        } else if (overflowTarget != null) {
            blockedOutputTicks = Math.min(blockedOutputTicks + 1, OVERFLOW_DELAY_TICKS);
            if (blockedOutputTicks >= OVERFLOW_DELAY_TICKS
                && (flapFilterEmpty(overflowTarget) || testFlapFilter(overflowTarget, remaining))) {
                remaining = insertIntoOutput(overflowTarget, remaining, false);
            }
        } else {
            blockedOutputTicks = 0;
        }

        if (remaining.getCount() == initialCount) {
            if (blockedOutputTicks != previousBlockedTicks) {
                setChanged();
            }
            return;
        }

        bufferedStack = remaining;
        if (bufferedStack.isEmpty()) {
            blockedOutputTicks = 0;
        }
        sendData();
        setChanged();
    }

    private ItemStack distributeRoundRobin(ItemStack stack, List<Direction> outputs) {
        if (stack.isEmpty() || outputs.isEmpty()) {
            return stack;
        }

        ItemStack remaining = stack.copy();
        int cursor = Math.floorMod(roundRobinCursor, outputs.size());
        int failedInRow = 0;

        while (!remaining.isEmpty() && failedInRow < outputs.size()) {
            Direction side = outputs.get(cursor);
            ItemStack attempt = getTransferStackForSide(side, remaining, outputs.size());
            ItemStack remainder = insertIntoOutput(side, attempt, false);

            if (remainder.getCount() == attempt.getCount()) {
                failedInRow++;
            } else {
                remaining.shrink(attempt.getCount() - remainder.getCount());
                failedInRow = 0;
                roundRobinCursor = (cursor + 1) & 0x7fffffff;
            }

            cursor = (cursor + 1) % outputs.size();
        }

        return remaining;
    }

    private ItemStack insertIntoOutput(Direction side, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return stack;
        }

        FilteringBehaviour sideFilter = getSideFilter(side);
        if (sideFilter != null && !sideFilter.upTo) {
            if (stack.getCount() < sideFilter.getAmount()) {
                return stack;
            }
            ItemStack simulated = insertIntoTunnel(this, side, stack, true);
            if (simulated == null || !simulated.isEmpty()) {
                return stack;
            }
        }

        ItemStack remainder = insertIntoTunnel(this, side, stack, simulate);
        return remainder == null ? stack : remainder;
    }

    private ItemStack getTransferStackForSide(Direction side, ItemStack remaining, int outputCount) {
        if (remaining.isEmpty()) {
            return ItemStack.EMPTY;
        }

        FilteringBehaviour sideFilter = getSideFilter(side);
        if (sideFilter == null || sideFilter.getFilter().isEmpty() || usesDefaultAmountMode(sideFilter)) {
            int amount = outputCount > 1 ? 1 : remaining.getCount();
            return remaining.copyWithCount(amount);
        }

        int amount = Math.min(remaining.getCount(), Math.max(1, sideFilter.getAmount()));
        if (!sideFilter.upTo && remaining.getCount() < sideFilter.getAmount()) {
            return ItemStack.EMPTY;
        }
        return remaining.copyWithCount(amount);
    }

    private List<Direction> gatherValidOutputs() {
        if (level == null || !level.isLoaded(worldPosition)) {
            return Collections.emptyList();
        }

        BeltBlockEntity beltBelow = BeltHelper.getSegmentBE(level, worldPosition.below());
        if (beltBelow == null) {
            return Collections.emptyList();
        }

        List<Direction> outputs = new ArrayList<>(3);
        for (Direction side : getPotentialOutputs()) {
            if (!sides.contains(side)) {
                continue;
            }
            if (!canOutputTo(side, beltBelow)) {
                continue;
            }
            outputs.add(side);
        }

        return outputs;
    }

    private boolean canOutputTo(Direction side, BeltBlockEntity beltBelow) {
        if (level == null) {
            return false;
        }

        BlockPos outputPos = worldPosition.below().relative(side);
        BlockState funnelState = level.getBlockState(outputPos.above());
        if (funnelState.getBlock() instanceof BeltFunnelBlock
            && funnelState.getValue(BeltFunnelBlock.SHAPE) == BeltFunnelBlock.Shape.PULLING
            && FunnelBlock.getFunnelFacing(funnelState) == side) {
            return false;
        }

        DirectBeltInputBehaviour input = BlockEntityBehaviour.get(level, outputPos, DirectBeltInputBehaviour.TYPE);
        if (input != null) {
            return input.canInsertFromSide(side);
        }

        return side == beltBelow.getMovementFacing()
            && !BlockHelper.hasBlockSolidSide(level.getBlockState(outputPos), level, outputPos, side.getOpposite());
    }

    private List<Direction> getPotentialOutputs() {
        Direction forward = getMovementFacing();
        List<Direction> outputs = new ArrayList<>(3);
        outputs.add(forward.getCounterClockWise());
        outputs.add(forward);
        outputs.add(forward.getClockWise());
        return outputs;
    }

    public Direction getMovementFacing() {
        BeltBlockEntity below = BeltHelper.getSegmentBE(level, worldPosition.below());
        if (below != null) {
            return below.getMovementFacing();
        }
        return Direction.SOUTH;
    }

    @Nullable
    public Direction getOverflowSide() {
        if (overflowMode == null) {
            return null;
        }

        Direction forward = getMovementFacing();
        return switch (getOverflowMode()) {
            case NONE -> null;
            case LEFT -> forward.getCounterClockWise();
            case FORWARD -> forward;
            case RIGHT -> forward.getClockWise();
        };
    }

    private FilteringBehaviour makeFilter(Direction side, FilteringBehaviour filter) {
        return filter.showCount();
    }

    @Nullable
    private FilteringBehaviour getSideFilter(Direction side) {
        return overflowFiltering == null ? null : overflowFiltering.get(side);
    }

    private boolean usesDefaultAmountMode(FilteringBehaviour sideFilter) {
        return sideFilter.upTo && sideFilter.getAmount() >= sideFilter.getFilter().getMaxStackSize();
    }

    private static Component formatDirection(Direction direction) {
        return Component.translatable("create_stats.direction." + direction.getSerializedName());
    }

    public enum OverflowMode implements INamedIconOptions {
        NONE(AllIcons.I_TUNNEL_SPLIT),
        LEFT(AllIcons.I_TUNNEL_FORCED_SPLIT),
        FORWARD(AllIcons.I_TUNNEL_ROUND_ROBIN),
        RIGHT(AllIcons.I_TUNNEL_FORCED_ROUND_ROBIN);

        private final String translationKey;
        private final AllIcons icon;

        OverflowMode(AllIcons icon) {
            this.icon = icon;
            this.translationKey = "create_stats.overflow_gate.mode." + Lang.asId(name());
        }

        @Override
        public AllIcons getIcon() {
            return icon;
        }

        @Override
        public String getTranslationKey() {
            return translationKey;
        }
    }
}
