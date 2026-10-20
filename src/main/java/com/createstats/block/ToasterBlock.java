package com.createstats.block;

import com.createstats.blockentity.ToasterBE;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
import java.util.Map;

public class ToasterBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    // LIT costs no blockstate json, the variants only list facing
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public static final int LIT_LEVEL = 5;

    private static final double[][] SHAPE_BOXES = {
            {1.85, 0.0, 2.75, 14.15, 1.45, 12.85},
            {1.30, 1.45, 2.20, 14.70, 10.60, 13.40},
            {1.60, 10.60, 2.50, 14.40, 11.58, 13.10},
            {2.55, 4.30, 1.20, 4.65, 10.20, 2.20},
            {9.75, 6.35, 1.35, 12.85, 9.45, 2.20},
            {5.05, 3.50, 1.78, 9.25, 5.20, 2.20},
            {3.30, 1.45, 1.90, 12.70, 3.15, 2.20},
            {4.40, 4.70, 13.40, 6.80, 5.50, 13.70},
    };

    private static final Map<Direction, VoxelShape> SHAPES = buildShapes();

    public ToasterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LIT, false));
    }

    private static Map<Direction, VoxelShape> buildShapes() {
        final Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        for (final Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (final double[] b : SHAPE_BOXES) {
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
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(ToasterBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        final InteractionResult result = operate(state, level, pos, player, stack, hit);
        if (result == InteractionResult.PASS) {
            return stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                    : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
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
        final ToasterControls.Zone zone =
                ToasterControls.zoneAt(state.getValue(FACING), pos, hit.getLocation());
        if (zone == null) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof ToasterBE toaster)) {
            return InteractionResult.PASS;
        }

        if (zone.slot() >= 0 && declinesSlot(toaster, stack, zone.slot())) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        return switch (zone) {
            case LEVER -> pushLever(toaster, level, pos, player);
            case DIAL -> turnDial(toaster, level, pos, player);
            case BAGEL -> pressBagel(toaster, level, pos, player);
            case CANCEL -> pressCancel(toaster, level, pos, player);
            case TRAY -> pullTray(toaster, level, pos);
            case SLOT_A, SLOT_B -> useSlot(toaster, level, pos, player, stack, zone.slot());
        };
    }

    private InteractionResult pushLever(ToasterBE toaster, Level level, BlockPos pos, Player player) {
        final ToasterBE.Refusal why = toaster.pushDown();
        if (why == ToasterBE.Refusal.NONE) {
            play(level, pos, SoundEvents.LEVER_CLICK, 0.6f, 0.55f);
            play(level, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, 0.35f, 1.8f);

            final ToasterBE.Refusal blocked = toaster.checkRun();
            if (blocked != ToasterBE.Refusal.NONE) {
                say(player, switch (blocked) {
                    case NO_POWER -> toaster.isWired() ? "charging" : "not_wired";
                    case SHED -> "shed";
                    case GATED -> "gated";
                    default -> "no_power";
                });
            }
            return InteractionResult.CONSUME;
        }
        play(level, pos, SoundEvents.LEVER_CLICK, 0.3f, 0.5f);
        say(player, switch (why) {
            case EMPTY -> "empty";
            case RUNNING -> "latched";
            case TRAY_OUT -> "tray_out";
            default -> "empty";
        });
        return InteractionResult.CONSUME;
    }

    private InteractionResult turnDial(ToasterBE toaster, Level level, BlockPos pos, Player player) {
        if (toaster.turnDial(!player.isShiftKeyDown())) {
            playPop(level, pos);
        }
        play(level, pos, SoundEvents.STONE_BUTTON_CLICK_ON, 0.35f, 1.7f);
        player.displayClientMessage(Component.translatable(
                "message.create_stats.toaster.browning", toaster.detent() + 1,
                Component.translatable("mode.create_stats.toaster.stage_"
                        + toaster.targetStage())), true);
        return InteractionResult.CONSUME;
    }

    private InteractionResult pressBagel(ToasterBE toaster, Level level, BlockPos pos, Player player) {
        toaster.markPressed(0);
        toaster.toggleBagel();
        play(level, pos, SoundEvents.STONE_BUTTON_CLICK_ON, 0.4f, toaster.isBagel() ? 1.5f : 1.1f);
        say(player, toaster.isBagel() ? "bagel_on" : "bagel_off");
        return InteractionResult.CONSUME;
    }

    private InteractionResult pressCancel(ToasterBE toaster, Level level, BlockPos pos,
                                          Player player) {
        toaster.markPressed(1);
        if (!toaster.isDown()) {
            play(level, pos, SoundEvents.LEVER_CLICK, 0.3f, 0.5f);
            say(player, "not_running");
            return InteractionResult.CONSUME;
        }
        playPop(level, pos);
        toaster.pop();
        say(player, "cancelled");
        return InteractionResult.CONSUME;
    }

    private InteractionResult pullTray(ToasterBE toaster, Level level, BlockPos pos) {
        final boolean open = !toaster.isTrayOpen();
        toaster.setTrayOpen(open);
        play(level, pos, open ? SoundEvents.BARREL_OPEN : SoundEvents.BARREL_CLOSE, 0.4f, 1.6f);
        return InteractionResult.CONSUME;
    }

    private InteractionResult useSlot(ToasterBE toaster, Level level, BlockPos pos, Player player,
                                      ItemStack stack, int slot) {
        if (!stack.isEmpty() && ToasterBE.toastable(stack)) {
            final ToasterBE.Refusal why = toaster.checkLoad(slot, stack);
            if (why == ToasterBE.Refusal.NONE) {
                final int taken = toaster.load(slot, stack);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(taken);
                }
                play(level, pos, SoundEvents.COMPOSTER_FILL, 0.5f, taken > 1 ? 1.3f : 1.4f);
                return InteractionResult.CONSUME;
            }
            play(level, pos, SoundEvents.LEVER_CLICK, 0.3f, 0.5f);
            say(player, why == ToasterBE.Refusal.RUNNING ? "latched" : "slot_full");
            return InteractionResult.CONSUME;
        }
        final ItemStack out = toaster.unload(slot);
        if (!out.isEmpty()) {
            player.getInventory().placeItemBackInInventory(out);
            play(level, pos, SoundEvents.ITEM_PICKUP, 0.5f, 1.3f);
            return InteractionResult.CONSUME;
        }
        play(level, pos, SoundEvents.LEVER_CLICK, 0.3f, 0.5f);
        say(player, toaster.isDown() ? "latched" : "slot_empty");
        return InteractionResult.CONSUME;
    }

    private static boolean declinesSlot(ToasterBE toaster, ItemStack stack, int slot) {
        return !stack.isEmpty() && !ToasterBE.toastable(stack)
                && (toaster.isDown() || toaster.slot(slot).isEmpty());
    }

    private static void say(Player player, String key) {
        player.displayClientMessage(
                Component.translatable("message.create_stats.toaster." + key), true);
    }

    public static void playPop(Level level, BlockPos pos) {
        play(level, pos, SoundEvents.IRON_TRAPDOOR_OPEN, 0.5f, 1.9f);
        play(level, pos, SoundEvents.AMETHYST_BLOCK_CHIME, 0.45f, 1.25f);
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        final BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ToasterBlock)) {
            return;
        }
        for (final double x : new double[]{10.20, 5.80}) {
            final Vec3 mouth = fromAuthoredFrame(state, pos, x, 12.0, 7.80);
            server.sendParticles(ParticleTypes.CLOUD, mouth.x, mouth.y, mouth.z,
                    4, 0.04, 0.02, 0.10, 0.01);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos,
                            net.minecraft.util.RandomSource random) {
        if (!state.getValue(LIT) || random.nextInt(6) != 0) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof ToasterBE toaster)
                || !toaster.isHeating() || toaster.targetStage() < 4) {
            return;
        }
        final double x = random.nextBoolean() ? 10.20 : 5.80;
        final Vec3 mouth = fromAuthoredFrame(state, pos, x, 11.6, 7.80 + random.nextDouble() * 2 - 1);
        level.addParticle(ParticleTypes.SMOKE, mouth.x, mouth.y, mouth.z,
                0.0, 0.012, 0.0);
    }

    private static void play(Level level, BlockPos pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, volume, pitch);
    }

    public static Vec3 fromAuthoredFrame(BlockState state, BlockPos pos, double px, double py,
                                         double pz) {
        final Vec3 local = fromAuthoredFrameLocal(state, px, py, pz);
        return new Vec3(pos.getX() + local.x, pos.getY() + local.y, pos.getZ() + local.z);
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

    public static Direction wireFace(BlockState state) {
        return state.getValue(FACING).getOpposite();
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   BlockPos fromPos, boolean moving) {
        super.neighborChanged(state, level, pos, block, fromPos, moving);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ToasterBE toaster) {
            toaster.onRedstone(level.hasNeighborSignal(pos));
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState,
                           boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ToasterBE toaster) {
            toaster.adoptRedstone(level.hasNeighborSignal(pos));
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ToasterBE toaster ? toaster.comparatorSignal() : 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip,
                "block.create_stats.toaster",
                List.of(com.createstats.util.CreateStatsTooltipHelper.spec(
                                "tooltip.create_stats.toaster_draw",
                                ToasterBE.DRAW_TOASTING, ToasterBE.DRAW_BAGEL, ToasterBE.DRAW_IDLE),
                        com.createstats.util.CreateStatsTooltipHelper.spec(
                                "tooltip.create_stats.toaster_slots", ToasterBE.SLOTS),
                        com.createstats.util.CreateStatsTooltipHelper.spec(
                                "tooltip.create_stats.toaster_dial", ToasterBE.DETENTS)));
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()
                && level.getBlockEntity(pos) instanceof ToasterBE toaster) {
            toaster.dropWiresAndUnlinkAll();
            for (final ItemStack dropped : toaster.dropContents()) {
                Block.popResource(level, pos, dropped);
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.TOASTER.create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (type != ModBlockEntities.TOASTER.get()) {
            return null;
        }
        if (level.isClientSide()) {
            return (lvl, pos, st, be) -> ((ToasterBE) be).clientTick();
        }
        return (lvl, pos, st, be) -> ((ToasterBE) be).serverTick();
    }
}
