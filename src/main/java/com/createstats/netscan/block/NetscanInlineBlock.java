package com.createstats.netscan.block;

import java.util.List;

import com.createstats.netscan.ScannerTier;
import com.createstats.netscan.blockentity.NetscanInlineBE;
import com.createstats.registry.ModBlockEntities;
import com.createstats.util.ClientScreenAccess;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.AbstractEncasedShaftBlock;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class NetscanInlineBlock extends AbstractEncasedShaftBlock implements IBE<NetscanInlineBE> {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private static final VoxelShape SHAPE_X = Block.box(0.0, 2.5, 2.5, 16.0, 13.5, 13.5);
    private static final VoxelShape SHAPE_Y = Block.box(2.5, 0.0, 2.5, 13.5, 16.0, 13.5);
    private static final VoxelShape SHAPE_Z = Block.box(2.5, 2.5, 0.0, 13.5, 13.5, 16.0);

    private final ScannerTier tier;

    public NetscanInlineBlock(Properties properties, ScannerTier tier) {
        super(properties);
        this.tier = tier;
        registerDefaultState(defaultBlockState().setValue(LIT, false));
    }

    public ScannerTier tier() {
        return this.tier;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
        super.createBlockStateDefinition(builder);
    }

    // SKIP_DEFAULT_BLOCK_INTERACTION, PASS falls through to the exact thing we're avoiding
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (AllItems.WRENCH.isIn(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.getItem() instanceof com.createstats.item.CableItem
                || stack.getItem() instanceof com.createstats.item.CopperWireItem) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide() && level.getBlockEntity(pos) instanceof NetscanInlineBE scanner) {
            ClientScreenAccess.openTopologyScanner(scanner);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext ctx) {
        return switch (state.getValue(AXIS)) {
            case X -> SHAPE_X;
            case Y -> SHAPE_Y;
            case Z -> SHAPE_Z;
        };
    }

    @Override
    public Class<NetscanInlineBE> getBlockEntityClass() {
        return NetscanInlineBE.class;
    }

    @Override
    public BlockEntityType<? extends NetscanInlineBE> getBlockEntityType() {
        return ModBlockEntities.TOPOLOGY_SCANNER_INLINE.get();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip,
                "block.create_stats." + tierId());
    }

    private String tierId() {
        return switch (this.tier) {
            case FIELD -> "topology_scanner";
            case ADVANCED -> "advanced_topology_scanner";
            case INDUSTRIAL -> "industrial_topology_scanner";
        };
    }
}
