package com.createstats.power;

import com.createstats.CreateStats;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FeForeignHandlers {
    public static final TagKey<Block> FOREIGN_NETWORK_MOUTHS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "foreign_network_mouths"));

    public static final TagKey<Block> FOREIGN_ENERGY_STORAGE = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "foreign_energy_storage"));

    private static final String[] NETWORK_MOUTHS = {
            "com.mrh0.createaddition.blocks.connector.",

            "org.antarcticgardens.cna.content.electricity.connector.",

            "mekanism.common.tile.transmitter.",
            "mekanism.common.content.network.",

            "cofh.thermal.core.tileentity.device.",
            "cofh.lib.energy.EnergyStorageCoFH$",
            "cofh.thermal.dynamics.",

            "sonar.fluxnetworks.common.connection.",
            "sonar.fluxnetworks.common.blockentity.",

            "owmii.powah.block.cable.",
            "owmii.lib.block.AbstractEnergyBlock$",

            "com.enderio.conduits.common.",

            "de.maxhenkel.pipez.blocks.tileentity.",

            "appeng.blockentity.networking.EnergyAcceptorBlockEntity",
            "appeng.me.energy.",

            "blusunrize.immersiveengineering.common.blocks.metal.ConnectorBlockEntity",
            "blusunrize.immersiveengineering.api.energy.",
    };

    private static final String[] KNOWN_STORAGE = {
            "com.mrh0.createaddition.blocks.modular_accumulator.",

            "mekanism.common.tile.multiblock.TileEntityInductionCasing",
            "mekanism.common.content.matrix.",

            "owmii.powah.block.energycell.",
            "owmii.powah.block.reactor.",

            "cofh.thermal.core.tileentity.storage.EnergyCellTile",

            "blusunrize.immersiveengineering.common.blocks.metal.CapacitorBlockEntity",

            "appeng.blockentity.networking.EnergyCellBlockEntity",
    };

    private static final Map<String, Boolean> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> STORAGE_CACHE = new ConcurrentHashMap<>();

    private enum Declared {
        MOUTH,

        STORAGE,

        NEITHER
    }

    private static final Map<String, Declared> LEARNED = new ConcurrentHashMap<>();

    private FeForeignHandlers() {
    }

    public static void learn(@Nullable BlockGetter level, @Nullable BlockPos pos,
                             @Nullable IEnergyStorage handler) {
        if (level == null || pos == null || handler == null
                || handler instanceof FeReserve || handler instanceof FeConsumer) {
            return;
        }
        final String className = handler.getClass().getName();
        if (LEARNED.containsKey(className)) {
            return;
        }
        final net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        LEARNED.put(className, state.is(FOREIGN_ENERGY_STORAGE) ? Declared.STORAGE
                : state.is(FOREIGN_NETWORK_MOUTHS) ? Declared.MOUTH
                : Declared.NEITHER);
    }

    public static boolean isNetworkMouth(IEnergyStorage handler) {
        if (handler == null) {
            return false;
        }

        if (handler instanceof FeReserve || handler instanceof FeConsumer) {
            return false;
        }

        if (isKnownStorage(handler)) {
            return false;
        }

        final Declared declared = LEARNED.get(handler.getClass().getName());
        if (declared == Declared.MOUTH) {
            return true;
        }
        if (declared == Declared.STORAGE) {
            return false;
        }
        if (handler.getMaxEnergyStored() <= 0) {
            return true;
        }
        return CACHE.computeIfAbsent(handler.getClass().getName(), FeForeignHandlers::matchesMouth);
    }

    public static boolean isKnownStorage(IEnergyStorage handler) {
        if (handler == null) {
            return false;
        }
        if (handler instanceof FeReserve) {
            return true;
        }
        if (handler instanceof FeConsumer) {
            return false;
        }

        final Declared declared = LEARNED.get(handler.getClass().getName());
        if (declared == Declared.STORAGE) {
            return true;
        }
        if (declared == Declared.MOUTH) {
            return false;
        }
        return STORAGE_CACHE.computeIfAbsent(handler.getClass().getName(),
                FeForeignHandlers::matchesStorage);
    }

    public static void forgetLearned() {
        LEARNED.clear();
        CACHE.clear();
        STORAGE_CACHE.clear();
    }

    private static boolean matchesMouth(String className) {
        return matchesAny(className, NETWORK_MOUTHS);
    }

    private static boolean matchesStorage(String className) {
        return matchesAny(className, KNOWN_STORAGE);
    }

    private static boolean matchesAny(String className, String[] prefixes) {
        for (String prefix : prefixes) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
