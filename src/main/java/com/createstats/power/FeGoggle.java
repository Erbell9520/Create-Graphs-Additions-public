package com.createstats.power;

import com.createstats.blockentity.CapacitorBankBE;
import com.createstats.blockentity.KineticDynamoBE;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public final class FeGoggle {
    private static final String PAD = "    ";

    private FeGoggle() {
    }

    public static Component title(String name) {
        return line(Component.literal(name), ChatFormatting.GRAY);
    }

    public static Component title(Component name) {
        return Component.literal(PAD).append(name.copy().withStyle(ChatFormatting.GRAY));
    }

    public static Component output(int fePerTick) {
        return fePerTick > 0 ? labeled("Output", fePerTick, ChatFormatting.GREEN)
                : plain("Idle", ChatFormatting.DARK_GRAY);
    }

    public static Component draw(int fePerTick) {
        return fePerTick > 0 ? labeled("Draw", fePerTick, ChatFormatting.GREEN)
                : plain("Idle", ChatFormatting.DARK_GRAY);
    }

    public static Component stored(int stored, int capacity) {
        final float pct = capacity > 0 ? stored * 100.0f / capacity : 0.0f;
        final ChatFormatting colour = pct < 20.0f ? ChatFormatting.RED
                : pct < 60.0f ? ChatFormatting.YELLOW : ChatFormatting.GREEN;
        return line(Component.literal(
                String.format(Locale.ROOT, "Stored: %,d / %,d FE (%.0f%%)", stored, capacity, pct)), colour);
    }

    public static Component chargeFlow(int signedRate) {
        if (signedRate > 0) {
            return labeled("Charging", signedRate, ChatFormatting.GREEN);
        }
        if (signedRate < 0) {
            return labeled("Draining", -signedRate, ChatFormatting.GOLD);
        }
        return plain("Holding steady", ChatFormatting.DARK_GRAY);
    }

    public static Component capacity(int fePerTick) {
        return labeled("Capacity", fePerTick, ChatFormatting.AQUA);
    }

    public static Component carrying(int fePerTick, int ratedFePerTick) {
        if (fePerTick <= 0) {
            return plain("Idle", ChatFormatting.DARK_GRAY);
        }
        return labeled("Carrying", fePerTick,
                ratedFePerTick > 0 && fePerTick >= ratedFePerTick ? ChatFormatting.RED : ChatFormatting.GREEN);
    }

    public static Component transfer(int signedFlow) {
        if (signedFlow > 0) {
            return labeled("Feeding block", signedFlow, ChatFormatting.GREEN);
        }
        if (signedFlow < 0) {
            return labeled("Drawing from block", -signedFlow, ChatFormatting.AQUA);
        }
        return plain("Idle", ChatFormatting.DARK_GRAY);
    }

    public static Component loadState(boolean off, boolean powered, boolean starved) {
        if (off) {
            return plain("Off", ChatFormatting.DARK_GRAY);
        }
        if (!powered) {
            return plain("No power", ChatFormatting.RED);
        }
        if (starved) {
            return plain("Starved", ChatFormatting.GOLD);
        }
        return plain("Powered", ChatFormatting.GREEN);
    }

    public static Component supplyDemand(int getting, int needs) {
        final ChatFormatting colour = getting < needs ? ChatFormatting.GOLD : ChatFormatting.AQUA;
        return line(Component.literal(
                String.format(Locale.ROOT, "Needs %,d, getting %,d FE/t", needs, getting)), colour);
    }

    public static Component ok(String text) {
        return plain(text, ChatFormatting.GREEN);
    }

    public static Component warn(String text) {
        return plain(text, ChatFormatting.GOLD);
    }

    public static Component bad(String text) {
        return plain(text, ChatFormatting.RED);
    }

    public static Component bad(Component text) {
        return line(text, ChatFormatting.RED);
    }

    public static Component dim(String text) {
        return plain(text, ChatFormatting.DARK_GRAY);
    }

    public static Component amount(String label, int stored, int capacity) {
        return line(Component.literal(
                String.format(Locale.ROOT, "%s: %,d / %,d FE", label, stored, capacity)), ChatFormatting.AQUA);
    }

    public static int deviceFlowFePerTick(@Nullable BlockEntity device) {
        if (device instanceof KineticDynamoBE dynamo) {
            return -Math.max(0, dynamo.lastOutflowFePerTick());
        }
        if (device instanceof CapacitorBankBE bank) {
            return bank.chargeRatePerTick();
        }
        if (device instanceof FeRatedLoad rated) {
            return Math.max(0, rated.measuredDrawFePerTick());
        }
        if (device instanceof IFeConsumer consumer) {
            return Math.max(0, consumer.feConsumer().measuredDrainFePerTick());
        }
        return 0;
    }

    // only fall back when this can't read the block, falling back on every zero showed idle machines as busy
    public static boolean readsDeviceFlow(@Nullable BlockEntity device) {
        return device instanceof KineticDynamoBE || device instanceof CapacitorBankBE
                || device instanceof FeRatedLoad || device instanceof IFeConsumer;
    }

    public static Component more() {
        return plain("Sneak for more", ChatFormatting.DARK_GRAY);
    }

    public static String time(int ticks) {
        final int secs = Math.max(0, ticks) / 20;
        return String.format(Locale.ROOT, "%d:%02d", secs / 60, secs % 60);
    }

    private static Component labeled(String label, int value, ChatFormatting colour) {
        return line(Component.literal(String.format(Locale.ROOT, "%s: %,d FE/t", label, value)), colour);
    }

    private static Component plain(String text, ChatFormatting colour) {
        return line(Component.literal(text), colour);
    }

    private static Component line(Component text, ChatFormatting colour) {
        return Component.literal(PAD).append(text.copy().withStyle(colour));
    }
}
