package com.createstats.item;

import com.createstats.upgrade.UpgradeDegree;
import com.createstats.upgrade.UpgradeKind;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class StorageUpgradeItem extends Item {
    private final UpgradeKind kind;
    private final UpgradeDegree degree;

    public StorageUpgradeItem(UpgradeKind kind, UpgradeDegree degree, Item.Properties properties) {
        super(properties);
        this.kind = kind;
        this.degree = degree;
    }

    public UpgradeKind kind() {
        return this.kind;
    }

    public UpgradeDegree degree() {
        return this.degree;
    }

    public static StorageUpgradeItem of(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof StorageUpgradeItem up
                ? up : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.create_stats.storage_upgrade.tooltip."
                        + this.kind.getSerializedName(), format(this.degree.multiplier()))
                .withStyle(ChatFormatting.GRAY));

        tooltip.add(Component.translatable("item.create_stats.storage_upgrade.tooltip.rack")
                .withStyle(ChatFormatting.DARK_GRAY));

        tooltip.add(Component.translatable("item.create_stats.storage_upgrade.tooltip.cumulative",
                        format(com.createstats.upgrade.UpgradeScale.FULL_SET))
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static String format(double multiplier) {
        return multiplier == Math.rint(multiplier)
                ? String.valueOf((long) multiplier)
                : String.valueOf(multiplier);
    }
}
