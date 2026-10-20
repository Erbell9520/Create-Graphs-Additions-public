package com.createstats.support;

import java.util.List;

import com.createstats.CreateStats;
import com.createstats.registry.ModItems;
import com.createstats.support.SupportPolicy.Decision;
import com.createstats.support.SupportPolicy.Level;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = CreateStats.MOD_ID)
public final class SupportCommands {
    private static final int DEFAULT_GRANT_MINUTES = 30;
    private static final int MAX_GRANT_MINUTES = 24 * 60;

    private SupportCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("createstats")
                .requires(source -> hasAccess(source, Level.SAFE) || SupportAccess.canToggleAudit(source))
                .then(Commands.literal("support")
                    .executes(SupportCommands::status)
                    .then(Commands.literal("status")
                        .requires(source -> hasAccess(source, Level.SAFE))
                        .executes(SupportCommands::status))
                    .then(Commands.literal("give")
                        .requires(source -> hasAccess(source, Level.SAFE))
                        .executes(SupportCommands::give))
                    .then(Commands.literal("diag")
                        .requires(source -> hasAccess(source, Level.SAFE))
                        .executes(SupportCommands::diag))
                    .then(Commands.literal("export")
                        .requires(source -> hasAccess(source, Level.TIER1))
                        .executes(SupportCommands::export))
                    .then(Commands.literal("end")
                        .requires(source -> hasAccess(source, Level.SAFE))
                        .executes(SupportCommands::end))
                    .then(Commands.literal("audit")
                        .requires(SupportAccess::canToggleAudit)
                        .executes(SupportCommands::auditStatus)
                        .then(Commands.literal("status").executes(SupportCommands::auditStatus))
                        .then(Commands.literal("on").executes(context -> setAudit(context, true)))
                        .then(Commands.literal("off").executes(context -> setAudit(context, false)))
                        .then(Commands.literal("tier1")
                            .then(Commands.literal("on").executes(context -> setAudit(context, true)))
                            .then(Commands.literal("off").executes(context -> setAudit(context, false))))
                        .then(Commands.literal("tier2")
                            .then(Commands.literal("on").executes(context -> setTier2Audit(context, true)))
                            .then(Commands.literal("off").executes(context -> setTier2Audit(context, false)))))
                    .then(Commands.literal("sessions")
                        .requires(SupportAccess::canToggleAudit)
                        .executes(SupportCommands::sessions))
                    .then(Commands.literal("grant")
                        .requires(source -> source.hasPermission(4))
                        .then(Commands.argument("player", EntityArgument.player())
                            .then(grantLevel("tier1", Level.TIER1))
                            .then(grantLevel("tier2", Level.TIER2))))
                    .then(Commands.literal("revoke")
                        .requires(source -> source.hasPermission(4))
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(SupportCommands::revoke)))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> grantLevel(
            String name, Level level) {
        return Commands.literal(name)
            .executes(context -> grant(context, level, DEFAULT_GRANT_MINUTES))
            .then(Commands.argument("minutes", IntegerArgumentType.integer(1, MAX_GRANT_MINUTES))
                .executes(context -> grant(context, level, IntegerArgumentType.getInteger(context, "minutes"))));
    }

    private static boolean hasAccess(CommandSourceStack source, Level level) {
        return SupportSessions.access(source.getPlayer()).atLeast(level);
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Decision decision = SupportSessions.access(source.getPlayer());
        if (!decision.atLeast(Level.SAFE)) {
            return auditStatus(context);
        }
        source.sendSuccess(() -> Component.literal("create_stats support").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("  Level: " + decision.level() + " (" + decision.basis().label() + ")")
            .withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> decision.atLeast(Level.TIER2)
            ? Component.literal("  Tier 2 (world actions): available").withStyle(ChatFormatting.GREEN)
            : Component.literal("  Tier 2 (world actions): locked, " + decision.tier2Note()).withStyle(ChatFormatting.RED),
            false);
        return 1;
    }

    private static int give(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }

        SupportAccess.audit(player, "tier1", "give-terminal");
        ItemStack stack = new ItemStack(ModItems.SUPPORT_TERMINAL.get());
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        source.sendSuccess(() -> Component.literal("Support terminal added to your inventory.")
            .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int diag(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }

        SupportAccess.audit(player, "tier1", "diag");
        List<String> lines = SupportDiagnostics.environment();
        lines.addAll(SupportDiagnostics.createAddons());
        for (String line : lines) {
            source.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
        }
        source.sendSuccess(() -> Component.literal(
            "The terminal's Export saves the full report on your own machine.").withStyle(ChatFormatting.DARK_GRAY), false);
        return 1;
    }

    private static int export(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null || !SupportSessions.allowRequest(player, true)) {
            return 0;
        }
        SupportRequestPacket.export(player, false);
        return 1;
    }

    private static int end(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        if (!SupportSessions.endSession(player)) {
            context.getSource().sendFailure(Component.literal("No support session is open."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Support session ended.")
            .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int sessions(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<String> lines = SupportSessions.describe(source.getServer());
        if (lines.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No one online has support access.")
                .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        for (String line : lines) {
            source.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
        }
        return lines.size();
    }

    private static int grant(CommandContext<CommandSourceStack> context, Level level, int minutes)
        throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        String by = context.getSource().getTextName();
        SupportSessions.grant(target, level, minutes, by);
        context.getSource().sendSuccess(() -> Component.literal("Granted " + target.getGameProfile().getName()
            + " support " + level + " for " + minutes + " minutes.").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int revoke(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        if (!SupportSessions.revoke(target, context.getSource().getTextName())) {
            context.getSource().sendFailure(Component.literal(target.getGameProfile().getName()
                + " holds no support grant. Permission-node access is managed by your permissions mod."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Revoked " + target.getGameProfile().getName()
            + "'s support grant.").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int auditStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        boolean tier1 = SupportAccess.auditEnabled();
        boolean tier2 = SupportAccess.tier2AuditEnabled();
        source.sendSuccess(() -> Component.literal("Support log, Tier 1 reads: " + (tier1 ? "ON" : "OFF"))
            .withStyle(tier1 ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("Support log, Tier 2 actions: " + (tier2 ? "ON" : "OFF"))
            .withStyle(tier2 ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        return 1;
    }

    private static int setAudit(CommandContext<CommandSourceStack> context, boolean enabled) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        SupportAccess.setAuditEnabled(player, enabled);
        source.sendSuccess(() -> Component.literal("Support log for Tier 1 reads " + (enabled ? "enabled." : "disabled."))
            .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        return 1;
    }

    private static int setTier2Audit(CommandContext<CommandSourceStack> context, boolean enabled) {
        CommandSourceStack source = context.getSource();
        SupportAccess.setTier2AuditEnabled(source.getPlayer(), enabled);
        source.sendSuccess(() -> Component.literal("Support log for Tier 2 actions " + (enabled ? "enabled." : "disabled."))
            .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        return 1;
    }
}
