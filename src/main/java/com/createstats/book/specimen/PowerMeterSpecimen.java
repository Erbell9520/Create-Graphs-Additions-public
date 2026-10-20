package com.createstats.book.specimen;

import com.createstats.blockentity.PowerMeterBE;
import com.createstats.block.PowerMeterBlock;
import com.createstats.gui.PowerMeterScreen;
import com.createstats.gui.meter.MeterHistory;
import com.createstats.registry.ModBlockEntities;
import com.createstats.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class PowerMeterSpecimen {
    static final int RATE = 295;
    static final int PEAK = 640;
    static final long TOTAL = 1_320_000L;

    static final int LOAD_STORED = 3_200;
    static final int LOAD_CAPACITY = 8_000;

    private PowerMeterSpecimen() {
    }

    public static ManualSpecimen create() {
        final BlockState state = ModBlocks.POWER_METER.get().defaultBlockState()
                .setValue(PowerMeterBlock.FACING, Direction.NORTH);
        final Fixture meter = new Fixture(new BlockPos(128, 64, -310), state);
        final CompoundTag tag = new CompoundTag();
        tag.putInt("Rate", RATE);
        tag.putInt("PeakFe", PEAK);
        tag.putLong("TotalFe", TOTAL);
        tag.putInt("Energy", 0);
        tag.putBoolean("Looped", false);
        meter.handleUpdateTag(tag, registries());
        return PowerMeterScreen.specimen(meter, stack("create_stats:surface_cable"),
                stack("create_stats:induction_smelter"), trace());
    }

    private static HolderLookup.Provider registries() {
        final Minecraft mc = Minecraft.getInstance();
        return mc.level != null ? mc.level.registryAccess() : RegistryAccess.EMPTY;
    }

    private static ItemStack stack(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
    }

    private static Component name(String id) {
        final Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
        return block.getName();
    }

    // deterministic so the figure is the same every open
    static int[] trace() {
        final int[] out = new int[MeterHistory.SIZE];
        for (int i = 0; i < out.length; i++) {
            final double phase = i * (Math.PI * 2.0) / 57.0;
            final double ripple = Math.sin(i * 1.7) * 6.0;
            out[i] = (int) Math.round(RATE + 150.0 * Math.sin(phase) + ripple);
        }
        return out;
    }

    private static final class Fixture extends PowerMeterBE {
        Fixture(BlockPos pos, BlockState state) {
            super(ModBlockEntities.POWER_METER.get(), pos, state);
        }

        @Override
        public Status status() {
            return Status.FLOWING;
        }

        @Override
        public boolean hasSupply() {
            return true;
        }

        @Override
        public boolean hasLoad() {
            return true;
        }

        @Override
        public Component getSupplyName() {
            return name("create_stats:surface_cable");
        }

        @Override
        public Component getLoadName() {
            return name("create_stats:induction_smelter");
        }

        @Override
        public int[] getLoadBuffer() {
            return new int[]{LOAD_STORED, LOAD_CAPACITY};
        }
    }
}
