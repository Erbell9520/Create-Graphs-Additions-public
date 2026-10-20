package com.createstats.item;

import java.util.List;

import com.createstats.data.ConductorsLensData;
import com.createstats.data.ConductorsLensData.Reading;
import com.createstats.train.smart.TrainIntel;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class ConductorsLensItem extends Item {
    public ConductorsLensItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        if (!ConductorsLensData.has(stack)) {
            tooltip.add(Component.translatable("item.create_stats.conductors_lens.tooltip.empty")
                .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.create_stats.conductors_lens.tooltip.usage")
                .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        Reading r = ConductorsLensData.read(stack);
        if (!r.hasTrain()) {
            tooltip.add(Component.translatable("item.create_stats.conductors_lens.tooltip.no_train")
                .withStyle(ChatFormatting.RED));
            return;
        }

        tooltip.add(Component.translatable("item.create_stats.conductors_lens.tooltip.target", r.trainName())
            .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.create_stats.conductors_lens.tooltip.speed",
            String.format("%.1f", r.speedMps()), String.format("%.1f", r.maxSpeedMps()))
            .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.create_stats.conductors_lens.tooltip.carriages",
            String.valueOf(r.carriages())).withStyle(ChatFormatting.GRAY));
        if (!r.owner().isEmpty()) {
            tooltip.add(Component.translatable("item.create_stats.conductors_lens.tooltip.owner", r.owner())
                .withStyle(ChatFormatting.GRAY));
        }
        if (r.hasDestination()) {
            tooltip.add(Component.translatable("item.create_stats.conductors_lens.tooltip.next_stop",
                r.destinationName()).withStyle(ChatFormatting.YELLOW));
            tooltip.add(Component.translatable("item.create_stats.conductors_lens.tooltip.track_remaining",
                TrainIntel.distance(r.distanceToDestination())).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (r.waitingForSignal()) {
            tooltip.add(Component.translatable("item.create_stats.conductors_lens.tooltip.signal_ahead",
                TrainIntel.distance(r.distanceToSignal())).withStyle(ChatFormatting.RED));
        }
        if (r.fuelTicks() > 0) {
            tooltip.add(Component.translatable("item.create_stats.conductors_lens.tooltip.fuel",
                TrainIntel.duration(r.fuelTicks() / TrainIntel.TICKS_PER_SECOND))
                .withStyle(ChatFormatting.GOLD));
        }
        if (r.itemCount() > 0) {
            tooltip.add(Component.translatable("item.create_stats.conductors_lens.tooltip.cargo",
                String.valueOf(r.itemCount()), r.topItem()).withStyle(ChatFormatting.GREEN));
        }
        if (r.fluidAmount() > 0) {
            tooltip.add(Component.translatable("item.create_stats.conductors_lens.tooltip.fluid",
                String.valueOf(r.fluidAmount()), r.topFluid()).withStyle(ChatFormatting.BLUE));
        }
    }
}
