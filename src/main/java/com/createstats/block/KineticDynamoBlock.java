package com.createstats.block;

import com.createstats.blockentity.KineticDynamoBE;
import com.createstats.registry.ModBlockEntities;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/** [1] */
public class KineticDynamoBlock extends KineticBlock implements IBE<KineticDynamoBE> {

    /** [2] */
    private static final VoxelShape OPEN_FRAME_SHAPE = Shapes.or(
            Block.box(1, 0, 1, 15, 2, 15),
            Block.box(2, 2, 5, 14, 12, 11),
            Block.box(5, 2, 5, 11, 15, 11));
    private static final VoxelShape STANDARD_SHAPE = Shapes.or(
            Block.box(2, 0, 2, 14, 2, 14),
            Block.box(2, 2, 2, 14, 12, 14),
            Block.box(4, 12, 4, 12, 15, 12),
            Block.box(13, 4, 6, 16, 9, 10));
    private static final VoxelShape INDUSTRIAL_SHAPE = Shapes.or(
            Block.box(3, 0, 3, 13, 2, 13),
            Block.box(3.4, 2, 3.4, 12.6, 13.5, 12.6),
            Block.box(5, 13.5, 5, 11, 16, 11),
            Block.box(12, 5, 6, 16, 10, 10));

    private final com.createstats.power.DynamoTier tier;

    public KineticDynamoBlock(Properties properties, com.createstats.power.DynamoTier tier) {
        super(properties);
        this.tier = tier;
    }

    /** Which grade this is. Read by the block entity for output and stress. */
    public com.createstats.power.DynamoTier tier() {
        return tier;
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return Direction.Axis.Y;
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == Direction.DOWN;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (tier) {
            case OPEN_FRAME -> OPEN_FRAME_SHAPE;
            case STANDARD -> STANDARD_SHAPE;
            case INDUSTRIAL -> INDUSTRIAL_SHAPE;
        };
    }

    /** [3] */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof KineticDynamoBE dynamo)) {
            return;
        }
        final float speed = Math.abs(dynamo.getSpeed());
        if (speed <= 1.0f) {
            return;
        }
        final float load = Math.min(1.0f, speed / 256.0f);
        // Two sparks a tick flat out, roughly one every eight ticks at a crawl.
        final int sparks = random.nextFloat() < load * 0.9f ? (load > 0.6f ? 2 : 1) : 0;
        final double top = terminalHeight();
        for (int i = 0; i < sparks; i++) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    pos.getX() + 0.32 + random.nextDouble() * 0.36,
                    pos.getY() + top,
                    pos.getZ() + 0.32 + random.nextDouble() * 0.36,
                    0.0, 0.01 + 0.02 * load, 0.0);
        }
        if (load > 0.75f && random.nextFloat() < (load - 0.75f) * 2.0f) {
            level.addParticle(ParticleTypes.SMOKE,
                    pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.3,
                    pos.getY() + top + 0.08,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.3,
                    0.0, 0.015, 0.0);
        }
    }

    /** Where the sparks belong on this grade: at the top of its rotor, not at a shared guess. */
    private double terminalHeight() {
        return switch (tier) {
            case OPEN_FRAME -> 0.80;
            case STANDARD -> 0.94;
            case INDUSTRIAL -> 1.00;
        };
    }

    @Override
    public Class<KineticDynamoBE> getBlockEntityClass() {
        return KineticDynamoBE.class;
    }

    @Override
    public BlockEntityType<? extends KineticDynamoBE> getBlockEntityType() {
        return ModBlockEntities.KINETIC_DYNAMO.get();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip, "block.create_stats.kinetic_dynamo");
    }
}
