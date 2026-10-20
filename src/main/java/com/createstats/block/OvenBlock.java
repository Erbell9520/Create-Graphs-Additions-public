package com.createstats.block;

import com.createstats.baking.OvenThermal;
import com.createstats.blockentity.OvenBE;
import com.createstats.registry.ModBlockEntities;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OvenBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    public static final BooleanProperty OPEN = BooleanProperty.create("open");

    public static final int LIT_LEVEL = 8;

    public static final int BUTTON_START = 0;
    public static final int BUTTON_LAMP = 1;

    private static final double[][] SHAPE_BOXES = {
            {1.8, 0.0, 2.0, 14.2, 1.2, 14.0},
            {0.4, 1.2, 1.0, 15.6, 15.1, 15.6},
            {0.25, 15.1, 0.85, 15.75, 16.0, 15.75},
            {4.0, 2.6, 15.55, 8.0, 5.0, 16.0},
    };

    private static final double[] DOOR_BOX = {1.0, 1.7, 0.0, 15.0, 11.8, 1.05};

    private static final Map<Direction, VoxelShape> SHUT = buildShapes(true);
    private static final Map<Direction, VoxelShape> AJAR = buildShapes(false);

    public OvenBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LIT, false)
                .setValue(OPEN, false));
    }

    private static Map<Direction, VoxelShape> buildShapes(boolean withDoor) {
        final Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        for (final Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (final double[] b : SHAPE_BOXES) {
                shape = Shapes.or(shape, rotateBox(b, facing));
            }
            if (withDoor) {
                shape = Shapes.or(shape, rotateBox(DOOR_BOX, facing));
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
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(OvenBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT, OPEN);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        final Map<Direction, VoxelShape> shapes = state.getValue(OPEN) ? AJAR : SHUT;
        return shapes.get(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return SHUT.get(state.getValue(FACING));
    }

    @Nullable
    private static OvenControls.Zone zoneOf(BlockState state, BlockPos pos, BlockHitResult hit,
                                            boolean doorOpen) {
        return OvenControls.zoneAt(state.getValue(FACING), pos, hit.getLocation(), doorOpen);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        final InteractionResult result = operate(state, level, pos, player, stack, hit);
        if (result == InteractionResult.PASS) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        return operate(state, level, pos, player, ItemStack.EMPTY, hit);
    }

    private InteractionResult operate(BlockState state, Level level, BlockPos pos, Player player,
                                      ItemStack stack, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof OvenBE oven)) {
            return InteractionResult.PASS;
        }
        final OvenControls.Zone zone = zoneOf(state, pos, hit, oven.isDoorOpen());
        if (zone == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        return switch (zone) {
            case DIAL -> turnDial(oven, level, pos, player);
            case START -> pressStart(oven, level, pos, player);
            case LAMP -> pressLamp(oven, level, pos);
            case SHELF -> useShelf(oven, level, pos, player, stack);
            case DOOR -> useDoor(oven, level, pos);
        };
    }

    private InteractionResult turnDial(OvenBE oven, Level level, BlockPos pos, Player player) {
        final int setting = oven.cycleSetting();
        play(level, pos, SoundEvents.STONE_BUTTON_CLICK_ON, 0.45f, 1.0f + 0.12f * setting);
        player.displayClientMessage(Component.translatable(
                "message.create_stats.oven.setting",
                OvenThermal.setpointCelsius(setting),
                String.format(Locale.ROOT, "%d", OvenThermal.TIME_PERCENT[setting])), true);
        return InteractionResult.CONSUME;
    }

    private InteractionResult pressStart(OvenBE oven, Level level, BlockPos pos, Player player) {
        oven.markPressed(BUTTON_START);
        final boolean started = oven.toggleStart();
        play(level, pos, started ? SoundEvents.STONE_BUTTON_CLICK_ON
                : SoundEvents.STONE_BUTTON_CLICK_OFF, 0.5f, started ? 1.4f : 0.9f);
        announceState(oven, player, started);
        return InteractionResult.CONSUME;
    }

    private InteractionResult pressLamp(OvenBE oven, Level level, BlockPos pos) {
        oven.markPressed(BUTTON_LAMP);
        final boolean on = oven.toggleLamp();
        play(level, pos, SoundEvents.LEVER_CLICK, 0.35f, on ? 1.3f : 0.9f);
        return InteractionResult.CONSUME;
    }

    private InteractionResult useShelf(OvenBE oven, Level level, BlockPos pos, Player player,
                                       ItemStack stack) {
        final List<ItemStack> finished = oven.unloadFinished();
        if (!finished.isEmpty()) {
            for (final ItemStack out : finished) {
                player.getInventory().placeItemBackInInventory(out);
            }
            play(level, pos, SoundEvents.ITEM_PICKUP, 0.5f, 1.15f);
            return InteractionResult.CONSUME;
        }
        if (player.isShiftKeyDown()) {
            final ItemStack raw = oven.unloadRaw();
            if (!raw.isEmpty()) {
                player.getInventory().placeItemBackInInventory(raw);
                play(level, pos, SoundEvents.ITEM_PICKUP, 0.5f, 1.05f);
                return InteractionResult.CONSUME;
            }
        }
        if (!stack.isEmpty()) {
            final OvenBE.Refusal why = oven.checkLoad(stack);
            if (why == OvenBE.Refusal.NONE) {
                final int taken = oven.load(stack);
                if (taken > 0 && !player.getAbilities().instabuild) {
                    stack.shrink(taken);
                }
                play(level, pos, SoundEvents.COMPOSTER_FILL, 0.55f, 1.1f);
                return InteractionResult.CONSUME;
            }

            if (why != OvenBE.Refusal.WRONG_ITEM || oven.loaded().isEmpty()) {
                play(level, pos, SoundEvents.LEVER_CLICK, 0.3f, 0.5f);
                player.displayClientMessage(Component.translatable(
                        "message.create_stats.oven." + why.key()), true);
                return InteractionResult.CONSUME;
            }
        }
        oven.setDoorOpen(false);
        play(level, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, 0.5f, 1.1f);
        return InteractionResult.CONSUME;
    }

    private InteractionResult useDoor(OvenBE oven, Level level, BlockPos pos) {
        final boolean opening = !oven.isDoorOpen();
        oven.setDoorOpen(opening);
        play(level, pos, opening ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE,
                0.5f, opening ? 0.9f : 1.1f);
        return InteractionResult.CONSUME;
    }

    private void announceState(OvenBE oven, Player player, boolean started) {
        if (!started) {
            player.displayClientMessage(
                    Component.translatable("message.create_stats.oven.off"), true);
            return;
        }
        final OvenBE.Refusal why = oven.checkRun();
        if (why == OvenBE.Refusal.NONE) {
            player.displayClientMessage(
                    Component.translatable("message.create_stats.oven.baking"), true);
        } else if (why == OvenBE.Refusal.COLD) {
            player.displayClientMessage(Component.translatable(
                    "message.create_stats.oven.preheating", OvenBE.clock(oven.preheatTicks())), true);
        } else {
            player.displayClientMessage(Component.translatable(
                    "message.create_stats.oven." + why.key()), true);
        }
    }

    public static void playDing(Level level, BlockPos pos) {
        play(level, pos, SoundEvents.NOTE_BLOCK_BELL.value(), 0.5f, 1.4f);
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        final BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof OvenBlock)) {
            return;
        }
        for (final double x : new double[]{5.0, 11.0}) {
            final Vec3 mouth = fromAuthoredFrame(state, pos, x, 11.9, 0.9);
            server.sendParticles(ParticleTypes.CLOUD, mouth.x, mouth.y, mouth.z,
                    2, 0.05, 0.02, 0.05, 0.005);
        }
    }

    private static void play(Level level, BlockPos pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, volume, pitch);
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

    public static Vec3 fromAuthoredFrame(BlockState state, BlockPos pos, double px, double py,
                                         double pz) {
        final Vec3 local = fromAuthoredFrameLocal(state, px, py, pz);
        return new Vec3(pos.getX() + local.x, pos.getY() + local.y, pos.getZ() + local.z);
    }

    public static Direction wireFace(BlockState state) {
        return state.getValue(FACING).getOpposite();
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   BlockPos fromPos, boolean moving) {
        super.neighborChanged(state, level, pos, block, fromPos, moving);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof OvenBE oven) {
            oven.onRedstone(level.hasNeighborSignal(pos));
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof OvenBE oven ? oven.comparatorSignal() : 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip,
                "block.create_stats.oven",
                List.of(com.createstats.util.CreateStatsTooltipHelper.spec(
                                "tooltip.create_stats.oven_draw",
                                OvenThermal.DRAW_ELEMENT, OvenBE.DRAW_IDLE),
                        com.createstats.util.CreateStatsTooltipHelper.spec(
                                "tooltip.create_stats.oven_temps",
                                OvenThermal.setpointCelsius(0),
                                OvenThermal.setpointCelsius(OvenThermal.settings() - 1))));
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()
                && level.getBlockEntity(pos) instanceof OvenBE oven) {
            oven.dropWiresAndUnlinkAll();
            oven.dropContents();
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.OVEN.create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (type != ModBlockEntities.OVEN.get() || level.isClientSide()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((OvenBE) be).serverTick();
    }
}
