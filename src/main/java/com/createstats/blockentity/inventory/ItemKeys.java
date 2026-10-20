package com.createstats.blockentity.inventory;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ItemKeys {
    private static final int CACHE = 256;

    private final Map<DataComponentPatch, String> cache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<DataComponentPatch, String> eldest) {
            return size() > CACHE;
        }
    };

    public String key(ItemStack stack, HolderLookup.Provider registries) {
        if (stack.isEmpty()) {
            return "";
        }
        final ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        final String base = id.toString();
        if (stack.isComponentsPatchEmpty()) {
            return base;
        }
        final DataComponentPatch patch = stack.getComponentsPatch();
        String suffix = this.cache.get(patch);
        if (suffix == null) {
            suffix = stableHash(patch, registries);
            this.cache.put(patch, suffix);
        }
        return base + "#" + suffix;
    }

    private static String stableHash(DataComponentPatch patch, HolderLookup.Provider registries) {
        try {
            final Tag tag = DataComponentPatch.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE),
                    patch).result().orElse(null);
            if (tag != null) {
                return Integer.toHexString(tag.toString().hashCode());
            }
        } catch (RuntimeException ignored) {
        }
        return Integer.toHexString(patch.hashCode());
    }

    public static String itemId(String key) {
        final int hash = key.indexOf('#');
        return hash < 0 ? key : key.substring(0, hash);
    }
}
