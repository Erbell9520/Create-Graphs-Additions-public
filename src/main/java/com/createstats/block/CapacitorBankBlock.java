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

/** [1] */
public class CapacitorBankBlock extends Block implements EntityBlock {

    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, CapacitorBankBE.LED_SEGMENTS);

    /** [2] */
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

    /** Light emission ramp, used at registration. Dark when flat, a soft glow when charged. */
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
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip, "block.create_stats.capacitor_bank");
    }
}
