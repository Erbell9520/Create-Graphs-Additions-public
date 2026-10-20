package com.createstats.security;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// one store per server via overworld(), a nether wing is still the same house
public final class SecurityHouseholds extends SavedData {
    private static final String DATA_NAME = "create_stats_security_households";

    private final Map<UUID, SecurityHousehold> households = new HashMap<>();

    public static SecurityHouseholds get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SecurityHouseholds::new, SecurityHouseholds::load), DATA_NAME);
    }

    @Nullable
    public static SecurityHouseholds of(@Nullable Level level) {
        if (level == null || level.isClientSide() || level.getServer() == null) {
            return null;
        }
        final ServerLevel overworld = level.getServer().overworld();
        return overworld == null ? null : get(overworld);
    }

    private static SecurityHouseholds load(CompoundTag tag, HolderLookup.Provider registries) {
        final SecurityHouseholds data = new SecurityHouseholds();
        final ListTag list = tag.getList("Households", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            final CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("Id")) {
                continue;
            }
            final SecurityHousehold household = new SecurityHousehold(entry.getUUID("Id"));
            household.load(entry);
            data.wire(household);
            data.households.put(household.id(), household);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        final ListTag list = new ListTag();
        for (SecurityHousehold household : this.households.values()) {
            list.add(household.save());
        }
        tag.put("Households", list);
        return tag;
    }

    private void wire(SecurityHousehold household) {
        household.onChange(this::setDirty);
    }

    @Nullable
    public SecurityHousehold find(UUID id) {
        return id == null ? null : this.households.get(id);
    }

    public SecurityHousehold create() {
        UUID id = UUID.randomUUID();
        while (this.households.containsKey(id)) {
            id = UUID.randomUUID();
        }
        return adopt(id);
    }

    public SecurityHousehold adopt(UUID id) {
        SecurityHousehold household = this.households.get(id);
        if (household == null) {
            household = new SecurityHousehold(id);
            this.households.put(id, household);
            setDirty();
        }
        wire(household);
        return household;
    }

    public boolean drop(UUID id) {
        final SecurityHousehold gone = this.households.remove(id);
        if (gone != null) {
            gone.markDropped();
            setDirty();
            return true;
        }
        return false;
    }

    static SecurityHouseholds read(CompoundTag tag) {
        return load(tag, null);
    }

    public boolean removeOrphan(UUID id, UUID actor, boolean serverOperator) {
        final SecurityHousehold household = find(id);
        if (household == null || !household.panels().isEmpty()) {
            return false;
        }
        if (!serverOperator && !household.roster().isOwner(actor)) {
            return false;
        }
        return drop(id);
    }

    public List<SecurityHousehold> removableBy(UUID player, boolean serverOperator) {
        final List<SecurityHousehold> out = new ArrayList<>();
        if (player == null) {
            return out;
        }
        for (SecurityHousehold household : this.households.values()) {
            if (household.panels().isEmpty()
                    && (serverOperator || household.roster().isOwner(player))) {
                out.add(household);
            }
        }
        out.sort(java.util.Comparator.comparing(SecurityHousehold::house,
                String.CASE_INSENSITIVE_ORDER).thenComparing(h -> h.id().toString()));
        return out;
    }

    public List<SecurityHousehold> ownedBy(UUID player) {
        final List<SecurityHousehold> out = new ArrayList<>();
        if (player == null) {
            return out;
        }
        for (SecurityHousehold household : this.households.values()) {
            if (household.roster().isOwner(player)) {
                out.add(household);
            }
        }
        out.sort(java.util.Comparator.comparing(SecurityHousehold::house,
                String.CASE_INSENSITIVE_ORDER).thenComparing(h -> h.id().toString()));
        return out;
    }

    public int size() {
        return this.households.size();
    }
}
