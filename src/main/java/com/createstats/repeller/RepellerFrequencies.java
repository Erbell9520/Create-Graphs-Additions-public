package com.createstats.repeller;

import com.createstats.CreateStats;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.DataMapsUpdatedEvent;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// data map, packs can place modded mobs with one file
public final class RepellerFrequencies {
    public record Frequency(double khz) {
        public static final Codec<Frequency> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("khz").forGetter(Frequency::khz)).apply(i, Frequency::new));
    }

    public static final DataMapType<EntityType<?>, Frequency> DATA_MAP = DataMapType.builder(
            ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "repeller_frequencies"),
            Registries.ENTITY_TYPE, Frequency.CODEC).build();

    @Nullable
    private static volatile FrequencyTable table;

    private RepellerFrequencies() {
    }

    public static FrequencyTable table() {
        FrequencyTable t = table;
        if (t == null) {
            t = build();
            table = t;
        }
        return t;
    }

    public static int tenthsOf(EntityType<?> type) {
        return table().tenthsOf(idOf(type));
    }

    public static String idOf(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
    }

    @Nullable
    public static EntityType<?> typeOf(String id) {
        final ResourceLocation rl = id == null || id.isEmpty() ? null : ResourceLocation.tryParse(id);
        if (rl == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) {
            return null;
        }
        final EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(rl);
        return type.getCategory() == MobCategory.MISC ? null : type;
    }

    private static FrequencyTable build() {
        final Map<String, Double> placed = new HashMap<>();
        for (Map.Entry<ResourceKey<EntityType<?>>, Frequency> e
                : BuiltInRegistries.ENTITY_TYPE.getDataMap(DATA_MAP).entrySet()) {
            placed.put(e.getKey().location().toString(), e.getValue().khz());
        }
        final List<String> ids = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (type.getCategory() != MobCategory.MISC) {
                ids.add(idOf(type));
            }
        }
        return FrequencyTable.build(placed, ids);
    }

    @EventBusSubscriber(modid = CreateStats.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void register(RegisterDataMapTypesEvent event) {
            event.register(DATA_MAP);
        }
    }

    @EventBusSubscriber(modid = CreateStats.MOD_ID)
    public static final class Reload {
        private Reload() {
        }

        @SubscribeEvent
        public static void onDataMaps(DataMapsUpdatedEvent event) {
            if (event.getRegistryKey().equals(Registries.ENTITY_TYPE)) {
                table = null;
            }
        }
    }
}
