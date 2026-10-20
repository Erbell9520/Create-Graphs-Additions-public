package com.createstats.vccmk4;

import com.createstats.CreateStats;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

@EventBusSubscriber(modid = CreateStats.MOD_ID)
public final class Mk4Commands {
    private Mk4Commands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("vcc4")
                .executes(Mk4Commands::status)
                .then(Commands.literal("status").executes(Mk4Commands::status))
                .then(Commands.literal("destroy")
                    .executes(context -> destroy(context, false))
                    .then(Commands.literal("all")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> destroy(context, true))))
                .then(Commands.literal("disarm")
                    .requires(source -> source.hasPermission(2))
                    .executes(context -> setDisarmed(context, true)))
                .then(Commands.literal("arm")
                    .requires(source -> source.hasPermission(2))
                    .executes(context -> setDisarmed(context, false))));
        CreateStats.LOGGER.info("[VCC Mk IV] the console fault guard and /vcc4 are active");
    }

    private static int destroy(CommandContext<CommandSourceStack> context, boolean all) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (all || player == null) {
            int n = ConsoleFault.destroyAll(player == null ? "the server console" : player.getGameProfile().getName());
            source.sendSuccess(() -> Component.literal(n == 0
                    ? "No VCC Mk IV console is loaded."
                    : n + " VCC Mk IV console" + (n == 1 ? "" : "s") + " sent away."), true);
            return n;
        }
        int[] r = ConsoleFault.destroyNear(player);
        if (r[0] == 0 && r[1] == 0) {
            source.sendFailure(Component.literal("No VCC Mk IV console within " + (int) ConsoleFault.DESTROY_REACH
                    + " blocks of you."));
            return 0;
        }
        String msg = r[0] + " VCC Mk IV console" + (r[0] == 1 ? "" : "s") + " back in your inventory.";
        if (r[1] > 0) msg += " " + r[1] + " you may not break here stayed.";
        String line = msg;
        source.sendSuccess(() -> Component.literal(line), false);
        return r[0];
    }

    private static int setDisarmed(CommandContext<CommandSourceStack> context, boolean on) {
        CommandSourceStack source = context.getSource();
        Mk4DisarmData.get(source.getServer()).setDisarmed(on);
        source.sendSuccess(() -> Component.literal(on
                ? "VCC Mk IV consoles disarmed: every one that loads takes itself off its ship and drops as an item. /vcc4 arm to allow them again."
                : "VCC Mk IV consoles allowed again."), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        boolean disarmed = Mk4DisarmData.get(source.getServer()).isDisarmed();
        int loaded = ConsoleFault.loaded().size();
        List<String> recent = ConsoleFault.recent();
        source.sendSuccess(() -> Component.literal("VCC Mk IV: " + (disarmed ? "DISARMED" : "armed") + ", "
                + loaded + " console" + (loaded == 1 ? "" : "s") + " loaded.")
                .withStyle(disarmed ? ChatFormatting.RED : ChatFormatting.GREEN), false);
        for (String r : recent) source.sendSuccess(() -> Component.literal("  " + r).withStyle(ChatFormatting.GRAY), false);
        return loaded;
    }
}
