package com.createstats.cinecam;

import com.createstats.cinecam.core.CinePower;
import com.createstats.power.FeProfiles;
import com.createstats.registry.ModDataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.energy.IEnergyStorage;

// keeps its charge in the item, cleared at zero so empty ones stack
public class CineCameraItem extends BlockItem {
    public CineCameraItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static int stored(ItemStack stack) {
        return Math.max(0, stack.getOrDefault(ModDataComponents.STORED_FE.get(), 0));
    }

    static void setStored(ItemStack stack, int fe) {
        if (fe > 0) {
            stack.set(ModDataComponents.STORED_FE.get(), fe);
        } else {
            stack.remove(ModDataComponents.STORED_FE.get());
        }
    }

    static int capacity() {
        return Math.max(1, FeProfiles.settings(FeProfiles.KEY_CINE_CAMERA).capacity());
    }

    private static float fill(ItemStack stack) {
        return Math.min(1.0f, (float) stored(stack) / capacity());
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return stored(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0f * fill(stack));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb(fill(stack) / 3.0f, 1.0f, 1.0f);
    }

    public static final class Battery implements IEnergyStorage {
        private final ItemStack stack;

        public Battery(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int held = stored(stack);
            int taken = CinePower.accepts(held, capacity(), maxReceive, stack.getCount());
            if (!simulate && taken > 0) setStored(stack, held + taken);
            return taken;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return Math.min(stored(stack), capacity());
        }

        @Override
        public int getMaxEnergyStored() {
            return capacity();
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return stack.getCount() == 1;
        }
    }
}
