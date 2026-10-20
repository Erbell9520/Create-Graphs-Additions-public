package com.createstats.item;

import com.createstats.antenna.AntennaTier;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class SignalAntennaItem extends Item {
    private final AntennaTier tier;

    public SignalAntennaItem(AntennaTier tier, Item.Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public AntennaTier getTier() {
        return this.tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.create_stats.signal_antenna.tooltip.attach").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.create_stats.signal_antenna.tooltip." + this.tier.getSerializedName()).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.create_stats.signal_antenna.tooltip.remove").withStyle(ChatFormatting.DARK_GRAY));
    }
}
