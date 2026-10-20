package com.createstats.power;

import com.createstats.CreateStats;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ComposterBlock;

// gate and value are separate. burn time alone = coal generator with a leaf texture
public final class BiofuelFuel {
    public static final TagKey<Item> BIOFUEL = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "biofuel"));

    public static final TagKey<Item> RAW_BIOFUEL = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "raw_biofuel"));

    public static final TagKey<Item> NOT_BIOFUEL = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "not_biofuel"));

    private static final BiofuelGrade[] BANDS_HIGHEST_FIRST = {
            BiofuelGrade.BALE, BiofuelGrade.LOG, BiofuelGrade.DENSE, BiofuelGrade.PRESSED,
            BiofuelGrade.PACKED, BiofuelGrade.PLANK, BiofuelGrade.CROP, BiofuelGrade.FIBRE,
            BiofuelGrade.GREEN,
    };

    private BiofuelFuel() {
    }

    public static TagKey<Item> bandTag(BiofuelGrade grade) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(
                CreateStats.MOD_ID, "biofuel/" + grade.name().toLowerCase(java.util.Locale.ROOT)));
    }

    public static BiofuelGrade gradeOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        for (BiofuelGrade grade : BANDS_HIGHEST_FIRST) {
            if (stack.is(bandTag(grade))) {
                return grade;
            }
        }
        return null;
    }

    public static boolean accepts(ItemStack stack) {
        if (stack.isEmpty() || stack.is(NOT_BIOFUEL)) {
            return false;
        }
        return stack.is(BIOFUEL) || ComposterBlock.getValue(stack) > 0.0f;
    }

    public static int burnTicksOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        return BiofuelValue.burnTicks(gradeOf(stack), ComposterBlock.getValue(stack),
                stack.getBurnTime(null));
    }
}
