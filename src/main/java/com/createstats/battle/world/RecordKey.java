package com.createstats.battle.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// keyed by ship id so a replacement bridge keeps the damage it knew
public record RecordKey(boolean ground, @Nullable UUID ship, String dim, long pos) {
    public static RecordKey ship(UUID id, ResourceKey<Level> dim) {
        return new RecordKey(false, id, dim.location().toString(), 0L);
    }

    public static RecordKey ground(ResourceKey<Level> dim, BlockPos pos) {
        return new RecordKey(true, null, dim.location().toString(), pos.asLong());
    }

    public String fileName() {
        if (!ground && ship != null) return "create_stats_battle_" + ship;
        BlockPos p = BlockPos.of(pos);
        return "create_stats_battle_g_" + dim.replace(':', '_').replace('/', '_') + "_" + p.getX() + "_" + p.getY() + "_" + p.getZ();
    }

    public String text() {
        if (!ground && ship != null) return "s:" + ship;
        return "g:" + dim + ":" + pos;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Kind", ground ? "ground" : "ship");
        if (ship != null) tag.putUUID("Ship", ship);
        tag.putString("Dim", dim);
        tag.putLong("Pos", pos);
        return tag;
    }

    @Nullable
    public static RecordKey load(@Nullable CompoundTag tag) {
        if (tag == null || !tag.contains("Kind")) return null;
        boolean ground = "ground".equals(tag.getString("Kind"));
        UUID ship = tag.hasUUID("Ship") ? tag.getUUID("Ship") : null;
        if (!ground && ship == null) return null;
        return new RecordKey(ground, ship, tag.getString("Dim"), tag.getLong("Pos"));
    }
}
