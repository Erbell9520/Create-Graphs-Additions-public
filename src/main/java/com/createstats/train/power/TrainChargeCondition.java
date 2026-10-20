package com.createstats.train.power;

import com.createstats.CreateStats;
import com.createstats.registry.ModBlocks;
import com.google.common.collect.ImmutableList;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.condition.LazyTickedScheduleCondition;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import net.createmod.catnip.data.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

// percent, FE thresholds break the moment you add a bank
public class TrainChargeCondition extends LazyTickedScheduleCondition {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "train_charge");

    private static final String STATUS = "create_stats_charge_status";

    public TrainChargeCondition() {
        super(20);
        data.putString("Percent", "90");
    }

    public boolean atMost() {
        return intData("Operator") % 2 == 1;
    }

    public int percent() {
        try {
            return Math.max(0, Math.min(100, Integer.parseInt(textData("Percent").trim())));
        } catch (NumberFormatException e) {
            data.putString("Percent", "0");
            return 0;
        }
    }

    public static boolean met(boolean atMost, int percent, TrainChargeReading reading) {
        return atMost ? reading.percent() <= percent : reading.percent() >= percent;
    }

    @Override
    protected boolean lazyTickCompletion(Level level, Train train, CompoundTag context) {
        final GlobalStation station = train.getCurrentStation();
        final String where = station == null ? "" : station.name;
        final TrainChargeReading live = TrainChargeProbe.read(train, level.getGameTime(), where);
        if (live == null) {
            status(context, "Waiting for the train's carriages to load");
            return false;
        }
        final TrainChargeLedger ledger = TrainChargeLedger.get(level.getServer());
        if (ledger != null) {
            ledger.record(train.id, live);
        }
        if (!live.hasStorage()) {
            status(context, "No Capacitor Banks aboard, not waiting");
            return true;
        }
        status(context, "Charge " + live.percent() + "% of " + (atMost() ? "at most " : "at least ")
                + percent() + "%");
        return met(atMost(), percent(), live);
    }

    private void status(CompoundTag context, String message) {
        if (!message.equals(context.getString(STATUS))) {
            context.putString(STATUS, message);
            requestStatusToUpdate(context);
        }
    }

    @Override
    public MutableComponent getWaitingStatus(Level level, Train train, CompoundTag tag) {
        return tag.contains(STATUS) ? Component.literal(tag.getString(STATUS)) : Component.empty();
    }

    @Override
    public Pair<ItemStack, Component> getSummary() {
        return Pair.of(ModBlocks.CAPACITOR_BANK.asStack(),
                Component.literal((atMost() ? "≤ " : "≥ ") + percent() + "%"));
    }

    @Override
    public List<Component> getTitleAs(String type) {
        return ImmutableList.of(
                Component.translatable("create_stats.schedule.condition.train_charge"),
                Component.translatable(atMost()
                                ? "create_stats.schedule.condition.train_charge.at_most"
                                : "create_stats.schedule.condition.train_charge.at_least", percent())
                        .withStyle(ChatFormatting.DARK_AQUA));
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initConfigurationWidgets(ModularGuiLineBuilder builder) {
        builder.addSelectionScrollInput(0, 60, (input, label) -> input
                .forOptions(ImmutableList.of(
                        Component.translatable("create_stats.schedule.condition.train_charge.op_at_least"),
                        Component.translatable("create_stats.schedule.condition.train_charge.op_at_most")))
                .titled(Component.translatable("create_stats.schedule.condition.train_charge.operator")),
                "Operator");
        builder.addIntegerTextInput(65, 30, (box, tooltip) -> {
        }, "Percent");
    }
}
