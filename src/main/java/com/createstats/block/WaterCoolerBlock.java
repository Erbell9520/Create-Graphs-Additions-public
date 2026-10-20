package com.createstats.block;

import com.createstats.blockentity.WaterCoolerBE;
import com.createstats.item.CoffeeMugItem;
import com.createstats.registry.ModBlockEntities;
import com.createstats.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// two blocks tall, 16px of water cooler is neither a bottle nor a machine
public class WaterCoolerBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    public enum Tap {
        SPRING, CHILLED, SPARKLING;

        public MugContent content() {
            return switch (this) {
                case SPRING -> MugContent.SPRING_WATER;
                case CHILLED -> MugContent.CHILLED_WATER;
                case SPARKLING -> MugContent.SPARKLING_WATER;
            };
        }

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final double[] TAP_X = {5.8, 8.0, 10.2};

    private enum Zone {
        PADDLE_SPRING(4.70, 10.6, 1.6, 6.90, 13.4, 3.2, Tap.SPRING),
        PADDLE_CHILLED(6.90, 10.6, 1.6, 9.10, 13.4, 3.2, Tap.CHILLED),
        PADDLE_SPARKLING(9.10, 10.6, 1.6, 11.30, 13.4, 3.2, Tap.SPARKLING),

        SPOUT_SPRING(4.70, 9.2, 4.2, 6.90, 11.3, 6.4, Tap.SPRING),
        SPOUT_CHILLED(6.90, 9.2, 4.2, 9.10, 11.3, 6.4, Tap.CHILLED),
        SPOUT_SPARKLING(9.10, 9.2, 4.2, 11.30, 11.3, 6.4, Tap.SPARKLING),

        SWITCH(1.4, 5.8, 2.5, 3.6, 8.2, 3.4, null),

        LOUVER(12.2, 4.8, 2.5, 14.8, 12.8, 3.4, null),

        TRAY(4.0, 2.6, 2.9, 12.0, 9.2, 8.2, null);

        final double x0, y0, z0, x1, y1, z1;
        @Nullable
        final Tap tap;

        Zone(double x0, double y0, double z0, double x1, double y1, double z1, @Nullable Tap tap) {
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
            this.tap = tap;
        }

        boolean contains(double x, double y, double z) {
            return x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1;
        }
    }

    private static final double[][] LOWER_BOXES = {
            {2.2, 0.0, 3.4, 13.8, 1.0, 12.6},
            {1.6, 1.0, 3.8, 14.4, 2.6, 12.2},
            {1.0, 2.6, 2.88, 4.0, 15.0, 13.0},
            {12.0, 2.6, 2.88, 15.0, 15.0, 13.0},
            {4.0, 2.6, 8.0, 12.0, 15.0, 13.0},
            {4.0, 11.2, 3.0, 12.0, 15.0, 8.0},
            {4.0, 2.6, 3.0, 12.0, 4.5, 8.0},
            {4.3, 9.2, 4.4, 11.7, 11.2, 6.2},
            {4.3, 12.6, 1.95, 11.7, 13.3, 3.1},
            {4.7, 10.9, 1.95, 11.3, 12.7, 2.5},
            {0.6, 15.0, 2.6, 15.4, 16.0, 13.4},
            {6.6, 9.6, 12.9, 9.4, 12.4, 14.0},
    };

    private static final double[][] UPPER_BOXES = {
            {4.4, 0.0, 4.4, 11.6, 1.6, 11.6},
            {6.3, 0.5, 6.3, 9.7, 3.0, 9.7},
            {5.5, 3.0, 5.5, 10.5, 4.3, 10.5},
            {4.5, 4.3, 4.5, 11.5, 5.5, 11.5},
            {3.65, 5.5, 3.65, 12.35, 15.6, 12.35},
    };

    private static final Map<Direction, VoxelShape> LOWER_SHAPES = buildShapes(LOWER_BOXES);
    private static final Map<Direction, VoxelShape> UPPER_SHAPES = buildShapes(UPPER_BOXES);

    public WaterCoolerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(WaterCoolerBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    private static Map<Direction, VoxelShape> buildShapes(double[][] boxes) {
        final Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        for (final Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (final double[] b : boxes) {
                shape = Shapes.or(shape, rotateBox(b, facing));
            }
            map.put(facing, shape);
        }
        return map;
    }

    private static VoxelShape rotateBox(double[] b, Direction facing) {
        return switch (facing) {
            case EAST -> Block.box(16 - b[5], b[1], b[0], 16 - b[2], b[4], b[3]);
            case SOUTH -> Block.box(16 - b[3], b[1], 16 - b[5], 16 - b[0], b[4], 16 - b[2]);
            case WEST -> Block.box(b[2], b[1], 16 - b[3], b[5], b[4], 16 - b[0]);
            default -> Block.box(b[0], b[1], b[2], b[3], b[4], b[5]);
        };
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        final Map<Direction, VoxelShape> shapes =
                state.getValue(HALF) == DoubleBlockHalf.UPPER ? UPPER_SHAPES : LOWER_SHAPES;
        return shapes.get(state.getValue(FACING));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        final BlockPos pos = context.getClickedPos();
        if (pos.getY() >= context.getLevel().getMaxBuildHeight() - 1
                || !context.getLevel().getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            final BlockState below = level.getBlockState(pos.below());
            return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
        }
        final BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        final DoubleBlockHalf half = state.getValue(HALF);
        final boolean towardPartner = direction.getAxis() == Direction.Axis.Y
                && (half == DoubleBlockHalf.LOWER) == (direction == Direction.UP);
        if (towardPartner) {
            return neighborState.is(this) && neighborState.getValue(HALF) != half
                    ? state : Blocks.AIR.defaultBlockState();
        }
        return half == DoubleBlockHalf.LOWER && direction == Direction.DOWN
                && !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative()
                && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            final BlockPos below = pos.below();
            final BlockState lower = level.getBlockState(below);
            if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_SUPPRESS_DROPS
                        | Block.UPDATE_ALL);
                level.levelEvent(player, 2001, below, Block.getId(lower));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()
                && state.getValue(HALF) == DoubleBlockHalf.LOWER
                && level.getBlockEntity(pos) instanceof WaterCoolerBE cooler) {
            cooler.dropWiresAndUnlinkAll();
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    private static Vec3 toAuthoredFrame(BlockState state, BlockPos pos, Vec3 hit) {
        final double x = hit.x - pos.getX();
        final double y = hit.y - pos.getY();
        final double z = hit.z - pos.getZ();
        final Vec3 local = switch (state.getValue(FACING)) {
            case EAST -> new Vec3(z, y, 1.0 - x);
            case SOUTH -> new Vec3(1.0 - x, y, 1.0 - z);
            case WEST -> new Vec3(1.0 - z, y, x);
            default -> new Vec3(x, y, z);
        };
        return local.scale(16.0);
    }

    public static Vec3 fromAuthoredFrameLocal(BlockState state, double px, double py, double pz) {
        final double x = px / 16.0;
        final double y = py / 16.0;
        final double z = pz / 16.0;
        return switch (state.getValue(FACING)) {
            case EAST -> new Vec3(1.0 - z, y, x);
            case SOUTH -> new Vec3(1.0 - x, y, 1.0 - z);
            case WEST -> new Vec3(z, y, 1.0 - x);
            default -> new Vec3(x, y, z);
        };
    }

    public static Vec3 fromAuthoredFrame(BlockState state, BlockPos pos, double px, double py, double pz) {
        final Vec3 local = fromAuthoredFrameLocal(state, px, py, pz);
        return new Vec3(pos.getX() + local.x, pos.getY() + local.y, pos.getZ() + local.z);
    }

    public static Direction wireFace(BlockState state) {
        return state.getValue(FACING).getOpposite();
    }

    @Nullable
    private static Zone zoneOf(BlockState state, BlockPos pos, BlockHitResult hit) {
        final Vec3 p = toAuthoredFrame(state, pos, hit.getLocation());
        for (final Zone zone : Zone.values()) {
            if (zone.contains(p.x, p.y, p.z)) {
                return zone;
            }
        }
        return null;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        final InteractionResult result = operate(state, level, pos, player, stack, hit);
        return result == InteractionResult.PASS
                ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                : ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        return operate(state, level, pos, player, ItemStack.EMPTY, hit);
    }

    private InteractionResult operate(BlockState state, Level level, BlockPos pos, Player player,
                                      ItemStack stack, BlockHitResult hit) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            return operateBottle(level, pos.below(), player, stack);
        }
        if (!(level.getBlockEntity(pos) instanceof WaterCoolerBE cooler)) {
            return InteractionResult.PASS;
        }
        final Zone zone = zoneOf(state, pos, hit);
        if (zone == null) {
            return InteractionResult.PASS;
        }

        if (zone == Zone.TRAY && !cooler.hasMug() && ModBlocks.mugIndex(stack.getItem()) < 0) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        return switch (zone) {
            case SWITCH -> {
                cooler.setOn(!cooler.isOn());
                play(level, pos, SoundEvents.LEVER_CLICK, 0.4f, cooler.isOn() ? 0.9f : 0.7f);
                yield InteractionResult.CONSUME;
            }
            case LOUVER -> {
                player.displayClientMessage(cooler.statusLine(), true);
                yield InteractionResult.CONSUME;
            }
            case TRAY -> handleMug(cooler, level, pos, player, stack);
            default -> pull(cooler, level, pos, state, player, stack, zone.tap);
        };
    }

    private InteractionResult pull(WaterCoolerBE cooler, Level level, BlockPos pos, BlockState state,
                                   Player player, ItemStack stack, @Nullable Tap tap) {
        if (tap == null) {
            return InteractionResult.PASS;
        }

        if (tap == Tap.SPRING && (stack.is(Items.BUCKET) || stack.is(Items.GLASS_BOTTLE))) {
            return fillVessel(cooler, level, pos, player, stack);
        }

        if (!cooler.hasMug()) {
            final int variant = ModBlocks.mugIndex(stack.getItem());
            if (variant >= 0 && !CoffeeMugItem.isFilled(stack)) {
                cooler.setMug(variant);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                play(level, pos, SoundEvents.GLASS_PLACE, 0.5f, 1.3f);
            }
        }

        final WaterCoolerBE.Refusal refusal = cooler.startPour(tap);
        if (refusal == WaterCoolerBE.Refusal.NONE) {
            play(level, pos, SoundEvents.WOODEN_BUTTON_CLICK_ON, 0.4f, 1.6f);
            if (tap == Tap.SPARKLING) {
                play(level, pos, SoundEvents.FIRE_EXTINGUISH, 0.35f, 2.0f);
                fizz(level, pos, state, tap);
            }
            play(level, pos, SoundEvents.BUCKET_FILL, 0.35f, 1.5f);
            return InteractionResult.CONSUME;
        }
        if (refusal == WaterCoolerBE.Refusal.BUSY) {
            return InteractionResult.CONSUME;
        }

        play(level, pos, SoundEvents.LEVER_CLICK, 0.3f, 0.5f);
        player.displayClientMessage(Component.translatable(
                "message.create_stats.water_cooler." + refusal.key()), true);
        return InteractionResult.CONSUME;
    }

    private InteractionResult fillVessel(WaterCoolerBE cooler, Level level, BlockPos pos, Player player,
                                         ItemStack stack) {
        final boolean bucket = stack.is(Items.BUCKET);
        final int cost = bucket ? WaterCoolerBE.BUCKET_MB : WaterCoolerBE.BOTTLE_MB;
        final WaterCoolerBE.Refusal refusal = cooler.drawOff(cost);
        if (refusal != WaterCoolerBE.Refusal.NONE) {
            play(level, pos, SoundEvents.LEVER_CLICK, 0.3f, 0.5f);
            player.displayClientMessage(Component.translatable(
                    "message.create_stats.water_cooler." + refusal.key()), true);
            return InteractionResult.CONSUME;
        }
        final ItemStack filled = bucket
                ? new ItemStack(Items.WATER_BUCKET)
                : net.minecraft.world.item.alchemy.PotionContents.createItemStack(
                        Items.POTION, net.minecraft.world.item.alchemy.Potions.WATER);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
            player.getInventory().placeItemBackInInventory(filled);
        }
        play(level, pos, bucket ? SoundEvents.BUCKET_FILL : SoundEvents.BOTTLE_FILL, 0.6f, 1.0f);
        return InteractionResult.CONSUME;
    }

    private InteractionResult handleMug(WaterCoolerBE cooler, Level level, BlockPos pos, Player player,
                                        ItemStack stack) {
        if (cooler.hasMug()) {
            final ItemStack out = new ItemStack(ModBlocks.mugVariant(cooler.mug()));
            final MugContent content = cooler.trayContent();
            cooler.clearMug();
            if (!player.getAbilities().instabuild) {
                player.getInventory().placeItemBackInInventory(
                        CoffeeMugItem.withContent(out, content));
            }
            play(level, pos, SoundEvents.ITEM_PICKUP, 0.4f, content.isDrinkable() ? 1.2f : 1.4f);
            return InteractionResult.CONSUME;
        }
        final int variant = ModBlocks.mugIndex(stack.getItem());
        if (variant < 0) {
            return InteractionResult.PASS;
        }
        if (CoffeeMugItem.isFilled(stack)) {
            player.displayClientMessage(
                    Component.translatable("message.create_stats.water_cooler.mug_full"), true);
            return InteractionResult.CONSUME;
        }
        cooler.setMug(variant);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        play(level, pos, SoundEvents.GLASS_PLACE, 0.5f, 1.3f);
        return InteractionResult.CONSUME;
    }

    private InteractionResult operateBottle(Level level, BlockPos lowerPos, Player player, ItemStack stack) {
        if (!(level.getBlockEntity(lowerPos) instanceof WaterCoolerBE cooler)) {
            return InteractionResult.PASS;
        }
        if (!stack.is(Items.WATER_BUCKET)) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            player.displayClientMessage(cooler.statusLine(), true);
            return InteractionResult.CONSUME;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (cooler.addWater(WaterCoolerBE.BUCKET_MB, false) < WaterCoolerBE.BUCKET_MB) {
            player.displayClientMessage(
                    Component.translatable("message.create_stats.water_cooler.tank_full"), true);
            return InteractionResult.CONSUME;
        }
        cooler.addWater(WaterCoolerBE.BUCKET_MB, true);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
            player.getInventory().placeItemBackInInventory(new ItemStack(Items.BUCKET));
        }
        play(level, lowerPos, SoundEvents.BUCKET_EMPTY, 0.7f, 1.0f);
        return InteractionResult.CONSUME;
    }

    private static void play(Level level, BlockPos pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, volume, pitch);
    }

    private static void fizz(Level level, BlockPos pos, BlockState state, Tap tap) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        final Vec3 tip = fromAuthoredFrame(state, pos, TAP_X[tap.ordinal()], 9.2, 5.3);
        server.sendParticles(ParticleTypes.BUBBLE_POP, tip.x, tip.y, tip.z, 8, 0.10, 0.06, 0.10, 0.01);
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip,
                "block.create_stats.water_cooler");
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER
                ? ModBlockEntities.WATER_COOLER.create(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.WATER_COOLER.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((WaterCoolerBE) be).serverTick();
    }
}
