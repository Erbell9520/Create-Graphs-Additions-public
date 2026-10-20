package com.createstats.block;

import com.createstats.blockentity.CapacitorBankBE;
import com.createstats.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CapacitorBankBlock extends Block implements EntityBlock {
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, CapacitorBankBE.LED_SEGMENTS);

    private static final int PLACEMENT_HELPER_ID =
            net.createmod.catnip.placement.PlacementHelpers.register(new CapacitorBankPlacementHelper());

    public enum Segment implements net.minecraft.util.StringRepresentable {
        SINGLE("single"),
        BOTTOM("bottom"),
        MIDDLE("middle"),
        TOP("top");

        private final String name;

        Segment(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final net.minecraft.world.level.block.state.properties.EnumProperty<Segment> SEGMENT =
            net.minecraft.world.level.block.state.properties.EnumProperty.create("segment", Segment.class);

    public CapacitorBankBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LEVEL, 0).setValue(SEGMENT, Segment.SINGLE));
    }

    public static int getLightEmission(BlockState state) {
        final int level = state.hasProperty(LEVEL) ? state.getValue(LEVEL) : 0;
        return level <= 0 ? 0 : 2 + level;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL, SEGMENT);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.CAPACITOR_BANK.create(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.CAPACITOR_BANK.get()) {
            return null;
        }
        return (lvl, pos, blockState, be) -> ((CapacitorBankBE) be).tick();
    }

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(
            ItemStack stack, BlockState state, Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
            net.minecraft.world.phys.BlockHitResult hit) {
        if (!com.simibubi.create.AllItems.WRENCH.isIn(stack)) {
            return placeNext(stack, state, level, pos, player, hand, hit);
        }
        if (!(level.getBlockEntity(pos) instanceof CapacitorBankBE bank)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (level.isClientSide()) {
            return net.minecraft.world.ItemInteractionResult.SUCCESS;
        }

        final int rank = player.isShiftKeyDown() ? bank.cycleReserveRankBack() : bank.cycleReserveRank();
        player.displayClientMessage(
                Component.literal(com.createstats.power.FeReserve.describe(rank)), true);
        return net.minecraft.world.ItemInteractionResult.CONSUME;
    }

    private net.minecraft.world.ItemInteractionResult placeNext(
            ItemStack stack, BlockState state, Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
            net.minecraft.world.phys.BlockHitResult hit) {
        final net.createmod.catnip.placement.IPlacementHelper helper =
                net.createmod.catnip.placement.PlacementHelpers.get(PLACEMENT_HELPER_ID);
        if (!helper.matchesItem(stack)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        final net.createmod.catnip.placement.PlacementOffset offset =
                helper.getOffset(player, level, state, pos, hit);
        final net.minecraft.world.ItemInteractionResult result =
                offset.placeInWorld(level, (net.minecraft.world.item.BlockItem) stack.getItem(), player, hand, hit);
        if (result.consumesAction()
                && stack.getItem() instanceof com.createstats.item.CapacitorBankItem bank) {
            bank.completeLayer(level, player, hand, offset.getBlockPos(), hit);
        }
        return result;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip, "block.create_stats.capacitor_bank",
                List.of(com.createstats.util.CreateStatsTooltipHelper.spec("tooltip.create_stats.bank_rate",
                        com.createstats.power.FeRates.amount(
                                com.createstats.blockentity.CapacitorBankBE.blockCapacity()),
                        com.createstats.power.FeRates.perTick(
                                com.createstats.blockentity.CapacitorBankBE.transferRate()))));
    }
}
