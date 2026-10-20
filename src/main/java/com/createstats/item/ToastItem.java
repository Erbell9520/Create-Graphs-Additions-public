package com.createstats.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import java.util.List;

// one item five shades via CustomModelData
public class ToastItem extends Item {
    public static final int WARM = 1;
    public static final int LIGHT = 2;
    public static final int GOLDEN = 3;
    public static final int DARK = 4;
    public static final int BURNT = 5;

    public static final int DEFAULT_SHADE = GOLDEN;

    public static final int NAUSEA_TICKS = 100;

    private static final String[] SHADES = {"warm", "warm", "light", "golden", "dark", "burnt"};

    private static final FoodProperties[] FOOD = {
            food(1, 0.5f, true),
            food(1, 0.5f, true),
            food(1, 0.8f, true),
            food(2, 0.8f, true),
            food(1, 0.4f, true),
            burntFood(),
    };

    public ToastItem(Properties properties) {
        super(properties);
    }

    private static FoodProperties food(int nutrition, float saturation, boolean fast) {
        final FoodProperties.Builder b = new FoodProperties.Builder()
                .nutrition(nutrition).saturationModifier(saturation);
        return (fast ? b.fast() : b).build();
    }

    private static FoodProperties burntFood() {
        return new FoodProperties.Builder().nutrition(1).saturationModifier(0.2f)
                .effect(() -> new MobEffectInstance(MobEffects.CONFUSION, NAUSEA_TICKS), 1.0f)
                .build();
    }

    public static FoodProperties defaultFood() {
        return FOOD[DEFAULT_SHADE];
    }

    public static int shadeOf(ItemStack stack) {
        final CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return data == null ? DEFAULT_SHADE : Mth.clamp(data.value(), WARM, BURNT);
    }

    @Override
    public FoodProperties getFoodProperties(ItemStack stack, @Nullable LivingEntity entity) {
        return FOOD[shadeOf(stack)];
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        final boolean burnt = shadeOf(stack) == BURNT;
        final ItemStack remainder = super.finishUsingItem(stack, level, entity);
        if (burnt && !level.isClientSide) {
            entity.removeEffect(MobEffects.POISON);
            entity.removeEffect(MobEffects.HUNGER);
        }
        return remainder;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.create_stats.toast." + SHADES[shadeOf(stack)]);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        final int shade = shadeOf(stack);
        tooltip.add(Component.translatable("item.create_stats.toast.tooltip", shade, BURNT)
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.create_stats.toast.note." + SHADES[shade])
                .withStyle(shade == BURNT ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_GRAY));
    }
}
