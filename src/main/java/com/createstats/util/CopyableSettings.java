package com.createstats.util;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.api.schematic.nbt.PartialSafeNBT;
import com.simibubi.create.content.equipment.clipboard.ClipboardCloneable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

// one writer for clipboard and schematics so they can't drift
public interface CopyableSettings extends ClipboardCloneable, PartialSafeNBT {
    String settingsKey();

    default boolean hasCopyableSettings() {
        return true;
    }

    default CopyableSettings settingsHolder() {
        return this;
    }

    void writeSettings(CompoundTag tag, HolderLookup.Provider registries);

    boolean applySettings(CompoundTag tag, HolderLookup.Provider registries, @Nullable Player player,
                          boolean simulate);

    default void writeFreshDefaults(CompoundTag tag) {
    }

    default boolean alreadyHolds(CompoundTag tag, HolderLookup.Provider registries) {
        final CompoundTag now = new CompoundTag();
        writeSettings(now, registries);
        return now.equals(tag);
    }

    @Override
    default String getClipboardKey() {
        return settingsKey();
    }

    @Override
    default boolean writeToClipboard(HolderLookup.Provider registries, CompoundTag tag, Direction side) {
        final CopyableSettings holder = settingsHolder();
        if (!holder.hasCopyableSettings()) {
            return false;
        }
        holder.writeSettings(tag, registries);
        return !tag.isEmpty();
    }

    @Override
    default boolean readFromClipboard(HolderLookup.Provider registries, CompoundTag tag, Player player,
                                      Direction side, boolean simulate) {
        final CopyableSettings holder = settingsHolder();
        if (tag.isEmpty() || !holder.hasCopyableSettings()) {
            return false;
        }
        return holder.applySettings(tag, registries, player, simulate);
    }

    @Override
    default void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        writeFreshDefaults(tag);
        writeSettings(tag, registries);
    }

    static void deny(@Nullable Player player, Component reason) {
        if (!(player instanceof ServerPlayer server)) {
            return;
        }
        server.displayClientMessage(reason.copy().withStyle(ChatFormatting.RED), true);
        AllSoundEvents.DENY.playOnServer(server.level(), server.blockPosition(), 1.0f, 1.0f);
    }
}
