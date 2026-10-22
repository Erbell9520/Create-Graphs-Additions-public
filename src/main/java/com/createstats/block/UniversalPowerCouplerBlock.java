package com.createstats.block;

import com.createstats.blockentity.UniversalPowerCouplerBE;
import com.createstats.power.CouplerLever;
import com.createstats.power.CouplerTested;
import com.createstats.power.PowerCouplerMode;
import com.createstats.registry.ModBlockEntities;
import com.createstats.registry.ModDataComponents;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class UniversalPowerCouplerBlock extends HorizontalDirectionalBlock
        implements IBE<UniversalPowerCouplerBE>, IWrenchable {
    public static final EnumProperty<PowerCouplerMode> MODE =
            EnumProperty.create("mode", PowerCouplerMode.class);
    public static final MapCodec<UniversalPowerCouplerBlock> CODEC = simpleCodec(UniversalPowerCouplerBlock::new);

    private static final java.util.Map<Direction, VoxelShape> SHAPES = shapes();

    private static java.util.Map<Direction, VoxelShape> shapes() {
        final java.util.Map<Direction, VoxelShape> out = new java.util.EnumMap<>(Direction.class);
        for (Direction facing : new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            VoxelShape shape = Shapes.empty();
            for (net.minecraft.world.phys.AABB box : com.createstats.power.CouplerShape.boxes(facing)) {
                shape = Shapes.or(shape, Shapes.create(box));
            }
            out.put(facing, shape.optimize());
        }
        return out;
    }

    public UniversalPowerCouplerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(MODE, PowerCouplerMode.PULL));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MODE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.getOrDefault(state.getValue(FACING), SHAPES.get(Direction.NORTH));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        final Direction clicked = context.getClickedFace();
        Direction foreign = clicked.getAxis().isHorizontal() ? clicked.getOpposite() : context.getHorizontalDirection();
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
            foreign = foreign.getOpposite();
        }
        return this.defaultBlockState()
                .setValue(FACING, foreign)
                .setValue(MODE, PowerCouplerMode.PULL);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        final Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        final BlockPos pos = context.getClickedPos();
        final UniversalPowerCouplerBE coupler =
                level.getBlockEntity(pos) instanceof UniversalPowerCouplerBE be ? be : null;
        if (coupler != null) {
            coupler.beforeTurn();
        }
        level.setBlock(pos, state.setValue(FACING, state.getValue(FACING).getClockWise()), Block.UPDATE_ALL);

        level.updateNeighborsAt(pos, this);
        level.invalidateCapabilities(pos);
        if (coupler != null) {
            coupler.afterTurn();
        }
        IWrenchable.playRotateSound(level, pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (AllItems.WRENCH.isIn(stack) || com.createstats.compat.ForeignSpools.isWiringItem(stack)
                || stack.getItem() instanceof com.createstats.item.CopperWireItem) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        final Vec3 local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        if (!CouplerLever.hit(state.getValue(FACING), local)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        final PowerCouplerMode remembered = level.getBlockEntity(pos) instanceof UniversalPowerCouplerBE be
                ? be.rememberedDirection() : PowerCouplerMode.PULL;
        final PowerCouplerMode next = state.getValue(MODE).click(remembered, player.isShiftKeyDown());
        level.setBlock(pos, state.setValue(MODE, next), Block.UPDATE_ALL);
        level.updateNeighborsAt(pos, this);
        level.invalidateCapabilities(pos);
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.35f,
                next == PowerCouplerMode.PULL ? 0.6f : next == PowerCouplerMode.PUSH ? 0.5f : 0.55f);
        if (level.getBlockEntity(pos) instanceof UniversalPowerCouplerBE coupler) {
            coupler.leverThrown();
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbourBlock,
                                BlockPos neighbourPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbourBlock, neighbourPos, movedByPiston);
        if (!level.isClientSide()) {
            level.invalidateCapabilities(pos);
            if (level.getBlockEntity(pos) instanceof UniversalPowerCouplerBE coupler) {
                coupler.invalidateForeignSeam();
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof UniversalPowerCouplerBE coupler) {
            coupler.dropWiresAndUnlinkAll();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        final String key = "block.create_stats.universal_power_coupler";
        final List<Component> specs = new ArrayList<>(2);

        final int rating = com.createstats.power.CouplerRating.rating(
                com.createstats.blockentity.PowerConnectorBE.baseTransferRate(),
                com.createstats.compat.NewAgeCompatHelper.bestWireConductivity(), UniversalPowerCouplerBE.BUFFER_FE);
        specs.add(com.createstats.util.CreateStatsTooltipHelper.spec(key + ".tooltip.spec",
                String.format(Locale.ROOT, "%,d", rating),
                String.format(Locale.ROOT, "%,d", UniversalPowerCouplerBE.BUFFER_FE)));
        final int stored = stack.getOrDefault(ModDataComponents.STORED_FE.get(), 0);
        if (stored > 0) {
            specs.add(com.createstats.util.CreateStatsTooltipHelper.spec(key + ".tooltip.stored",
                    String.format(Locale.ROOT, "%,d", stored)));
        }
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip, key, specs, List.of(),
                () -> testedLines(key));
    }

    private static List<Component> testedLines(String key) {
        final List<CouplerTested.Entry> tested = CouplerTested.all();
        final List<Component> lines = new ArrayList<>(tested.size() + 1);
        if (tested.isEmpty()) {
            lines.add(Component.translatable(key + ".tooltip.alt.none").withStyle(ChatFormatting.DARK_GRAY));
            return lines;
        }
        lines.add(Component.translatable(key + ".tooltip.alt.header").withStyle(ChatFormatting.GRAY));
        for (CouplerTested.Entry e : tested) {
            final boolean loaded = isLoaded(e.modId());
            lines.add(Component.translatable(loaded ? key + ".tooltip.alt.entry_loaded" : key + ".tooltip.alt.entry",
                    e.name(), e.version()).withStyle(loaded ? ChatFormatting.AQUA : ChatFormatting.DARK_AQUA));
            lines.add(Component.translatable(key + ".tooltip.alt.how", e.howTested())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    private static boolean isLoaded(String modId) {
        try {
            return !modId.isEmpty() && net.neoforged.fml.ModList.get() != null
                    && net.neoforged.fml.ModList.get().isLoaded(modId);
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public Class<UniversalPowerCouplerBE> getBlockEntityClass() {
        return UniversalPowerCouplerBE.class;
    }

    @Override
    public BlockEntityType<? extends UniversalPowerCouplerBE> getBlockEntityType() {
        return ModBlockEntities.UNIVERSAL_POWER_COUPLER.get();
    }

    @Nullable
    @Override
    public <S extends BlockEntity> BlockEntityTicker<S> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<S> type) {
        return level.isClientSide() ? null
                : (lvl, pos, blockState, be) -> ((UniversalPowerCouplerBE) be).tick();
    }
}
