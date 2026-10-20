package com.createstats.baking;

import java.util.function.Supplier;

import net.minecraft.core.Holder;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public enum CakeFlavor implements StringRepresentable {
    CARROT("carrot", 0xE2913A, () -> Items.CARROT, MobEffects.NIGHT_VISION),
    CINNAMON("cinnamon", 0xB9743C, () -> com.createstats.registry.ModItems.CINNAMON.get(), MobEffects.MOVEMENT_SPEED),
    BLUEBERRY("blueberry", 0x6E74B4, () -> com.createstats.registry.ModItems.BLUEBERRY.get(), MobEffects.JUMP),

    CACAO("cacao", 0x7A5233, () -> Items.COCOA_BEANS, MobEffects.DIG_SPEED),
    VANILLA("vanilla", 0xE6D6AE, () -> com.createstats.registry.ModItems.VANILLA_BEAN.get(), MobEffects.REGENERATION),
    STRAWBERRY("strawberry", 0xD6544E, () -> com.createstats.registry.ModItems.STRAWBERRY.get(), MobEffects.ABSORPTION);

    private final String key;
    private final int tint;
    private final Supplier<Item> ingredient;
    private final Holder<MobEffect> effect;

    CakeFlavor(String key, int tint, Supplier<Item> ingredient, Holder<MobEffect> effect) {
        this.key = key;
        this.tint = tint;
        this.ingredient = ingredient;
        this.effect = effect;
    }

    @Override
    public String getSerializedName() {
        return key;
    }

    public int tint() {
        return tint;
    }

    public Item ingredient() {
        return ingredient.get();
    }

    public Holder<MobEffect> effect() {
        return effect;
    }

    public static final int SLICE_EFFECT_TICKS = 160;

    public String batterName() {
        return key + "_cake_batter";
    }

    public String filledMoldName() {
        return key + "_cake_mold";
    }

    public String cakeName() {
        return key + "_cake";
    }

    public String displayName() {
        return Character.toUpperCase(key.charAt(0)) + key.substring(1);
    }
}
