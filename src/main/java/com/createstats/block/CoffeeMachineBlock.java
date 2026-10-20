package com.createstats.block;

import com.createstats.blockentity.CoffeeMachineBE;
import com.createstats.item.CoffeeMugItem;
import com.createstats.registry.ModBlockEntities;
import com.createstats.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

public class CoffeeMachineBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    private enum Zone {
        PORTAFILTER(9.2, 7.0, 0.0, 12.0, 9.4, 4.2),

        WAND(12.9, 5.6, 4.2, 15.8, 12.6, 9.6),

        SWITCH(2.4, 4.1, 1.6, 6.6, 6.8, 2.8),

        LEVER(8.4, 12.6, 3.2, 12.8, 16.0, 11.0),

        HOPPER(3.0, 8.6, 14.0, 13.0, 13.6, 16.0),

        TRAY(7.4, 2.4, 2.4, 13.9, 7.9, 8.6);

        final double x0, y0, z0, x1, y1, z1;

        Zone(double x0, double y0, double z0, double x1, double y1, double z1) {
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
        }

        boolean contains(double x, double y, double z) {
            return x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1;
        }
    }

    private static final double[][] SHAPE_BOXES = {
            {1.4, 0.0, 2.4, 14.6, 2.5, 14.6},
            {1.6, 2.5, 1.8, 7.4, 13.2, 14.4},
            {7.4, 11.6, 2.2, 14.4, 13.2, 14.4},
            {7.4, 2.5, 8.4, 14.4, 11.6, 14.4},
            {13.8, 2.5, 2.6, 15.5, 12.1, 8.4},
            {7.4, 2.5, 2.8, 13.8, 3.4, 8.4},
            {8.6, 7.2, 0.0, 12.6, 11.6, 8.4},
            {2.4, 13.2, 3.4, 9.2, 15.5, 13.6},
            {8.4, 13.2, 3.6, 12.8, 16.0, 10.8},
            {9.2, 13.2, 10.6, 13.6, 13.8, 13.6},
            {3.4, 9.0, 14.4, 12.6, 12.8, 15.6},
            {13.0, 5.8, 4.6, 15.2, 10.4, 9.2},
    };

    private static final Map<Direction, VoxelShape> SHAPES = buildShapes();

    public CoffeeMachineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
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
        return simpleCodec(CoffeeMachineBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
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
        if (result == InteractionResult.PASS) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        return operate(state, level, pos, player, ItemStack.EMPTY, hit);
    }

    private InteractionResult operate(BlockState state, Level level, BlockPos pos, Player player,
                                      ItemStack stack, BlockHitResult hit) {
        final Zone zone = zoneOf(state, pos, hit);
        if (zone == null) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof CoffeeMachineBE machine)) {
            return InteractionResult.PASS;
        }

        if (zone == Zone.TRAY && !machine.hasMug() && ModBlocks.mugIndex(stack.getItem()) < 0) {
            return InteractionResult.PASS;
        }
        if (zone == Zone.HOPPER && !stack.is(Items.COCOA_BEANS)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        return switch (zone) {
            case SWITCH -> {
                machine.setOn(!machine.isOn());
                play(level, pos, SoundEvents.LEVER_CLICK, 0.4f, machine.isOn() ? 0.9f : 0.7f);
                yield InteractionResult.CONSUME;
            }
            case LEVER -> {
                pullLever(machine, level, pos, player);
                yield InteractionResult.CONSUME;
            }
            case WAND -> {
                yield useWand(machine, level, pos, state, player, stack);
            }
            case PORTAFILTER -> {
                machine.setLocked(!machine.isLocked());

                play(level, pos, machine.isLocked()
                                ? SoundEvents.IRON_TRAPDOOR_CLOSE : SoundEvents.IRON_TRAPDOOR_OPEN,
                        0.45f, machine.isLocked() ? 1.4f : 1.1f);
                yield InteractionResult.CONSUME;
            }
            case HOPPER -> {
                yield handleBeans(machine, level, pos, player, stack);
            }
            case TRAY -> {
                yield handleMug(machine, level, pos, player, stack);
            }
        };
    }

    private void pullLever(CoffeeMachineBE machine, Level level, BlockPos pos, Player player) {
        final CoffeeMachineBE.Refusal refusal = machine.startBrew();
        if (refusal == CoffeeMachineBE.Refusal.NONE) {
            play(level, pos, SoundEvents.PISTON_CONTRACT, 0.4f, 1.5f);
            play(level, pos, SoundEvents.BREWING_STAND_BREW, 0.5f, 1.2f);
            return;
        }
        if (refusal == CoffeeMachineBE.Refusal.BUSY) {
            return;
        }

        play(level, pos, SoundEvents.LEVER_CLICK, 0.3f, 0.5f);
        final String key = switch (refusal) {
            case OFF -> "off";
            case UNLOCKED -> "unlocked";
            case NO_BEANS -> "no_beans";
            case NO_POWER -> "no_power";
            default -> null;
        };
        if (key != null) {
            player.displayClientMessage(
                    Component.translatable("message.create_stats.coffee_machine." + key), true);
        }
    }

    private InteractionResult useWand(CoffeeMachineBE machine, Level level, BlockPos pos, BlockState state,
                                      Player player, ItemStack stack) {
        if (!stack.is(Items.MILK_BUCKET)) {
            if (machine.isOn()) {
                machine.setWandOut(!machine.isWandOut());
                play(level, pos, SoundEvents.FIRE_EXTINGUISH, 0.35f, 1.8f);
                steam(level, pos, state);
            }
            return InteractionResult.CONSUME;
        }

        final CoffeeMachineBE.Refusal refusal = machine.startFroth();
        if (refusal != CoffeeMachineBE.Refusal.NONE) {
            play(level, pos, SoundEvents.LEVER_CLICK, 0.3f, 0.5f);
            final String key = switch (refusal) {
                case OFF -> "off";
                case NO_MUG -> "no_mug";
                case ALREADY_MILKY -> "already_milky";
                case NO_POWER -> "no_power";
                default -> null;
            };
            if (key != null) {
                player.displayClientMessage(
                        Component.translatable("message.create_stats.coffee_machine." + key), true);
            }
            return InteractionResult.CONSUME;
        }

        if (!machine.isWandOut()) {
            machine.setWandOut(true);
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
            player.getInventory().placeItemBackInInventory(new ItemStack(Items.BUCKET));
        }
        play(level, pos, SoundEvents.FIRE_EXTINGUISH, 0.5f, 1.9f);
        steam(level, pos, state);
        return InteractionResult.CONSUME;
    }

    private InteractionResult handleBeans(CoffeeMachineBE machine, Level level, BlockPos pos,
                                          Player player, ItemStack stack) {
        final int taken = machine.addBeans(stack.getCount());
        if (taken <= 0) {
            player.displayClientMessage(
                    Component.translatable("message.create_stats.coffee_machine.hopper_full"), true);
            return InteractionResult.CONSUME;
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(taken);
        }
        play(level, pos, SoundEvents.SAND_PLACE, 0.5f, 1.1f);
        player.displayClientMessage(Component.translatable(
                "message.create_stats.coffee_machine.beans", machine.beans(),
                CoffeeMachineBE.BEAN_CAPACITY), true);
        return InteractionResult.CONSUME;
    }

    private InteractionResult handleMug(CoffeeMachineBE machine, Level level, BlockPos pos, Player player,
                                        ItemStack stack) {
        if (machine.hasMug()) {
            final ItemStack out = new ItemStack(ModBlocks.mugVariant(machine.mug()));
            final MugContent content = machine.trayContent();
            machine.clearMug();
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
                    Component.translatable("message.create_stats.coffee_machine.mug_full"), true);
            return InteractionResult.CONSUME;
        }
        machine.setMug(variant);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        play(level, pos, SoundEvents.GLASS_PLACE, 0.5f, 1.3f);
        return InteractionResult.CONSUME;
    }

    private static void play(Level level, BlockPos pos, net.minecraft.sounds.SoundEvent sound,
                             float volume, float pitch) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, volume, pitch);
    }

    private static void steam(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        final Vec3 tip = fromAuthoredFrame(state, pos, 14.5, 6.4, 6.6);
        server.sendParticles(ParticleTypes.CLOUD, tip.x, tip.y, tip.z, 6, 0.08, 0.08, 0.08, 0.01);
    }

    public static Vec3 fromAuthoredFrame(BlockState state, BlockPos pos, double px, double py, double pz) {
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
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                java.util.List<Component> tooltip,
                                net.minecraft.world.item.TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip,
                "block.create_stats.coffee_machine");
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()
                && level.getBlockEntity(pos) instanceof CoffeeMachineBE machine) {
            machine.dropWiresAndUnlinkAll();
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.COFFEE_MACHINE.create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.COFFEE_MACHINE.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((CoffeeMachineBE) be).serverTick();
    }
}
