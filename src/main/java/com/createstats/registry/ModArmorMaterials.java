package com.createstats.registry;

import com.createstats.CreateStats;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModArmorMaterials {
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
        DeferredRegister.create(Registries.ARMOR_MATERIAL, CreateStats.MOD_ID);

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> PILOT =
        ARMOR_MATERIALS.register("pilot", () -> new ArmorMaterial(
            buildDefenseMap(),
            15,
            SoundEvents.ARMOR_EQUIP_NETHERITE,
            () -> Ingredient.of(Items.NETHERITE_INGOT),
            List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "pilot"))),
            2.5F,
            0.0F
        ));

    private ModArmorMaterials() {
    }

    public static Holder<ArmorMaterial> pilot() {
        return PILOT;
    }

    public static void register(IEventBus modEventBus) {
        ARMOR_MATERIALS.register(modEventBus);
    }

    private static EnumMap<ArmorItem.Type, Integer> buildDefenseMap() {
        EnumMap<ArmorItem.Type, Integer> map = new EnumMap<>(ArmorItem.Type.class);

        map.put(ArmorItem.Type.HELMET, 3);
        map.put(ArmorItem.Type.CHESTPLATE, 8);
        map.put(ArmorItem.Type.LEGGINGS, 6);
        map.put(ArmorItem.Type.BOOTS, 3);
        map.put(ArmorItem.Type.BODY, 11);
        return map;
    }
}
