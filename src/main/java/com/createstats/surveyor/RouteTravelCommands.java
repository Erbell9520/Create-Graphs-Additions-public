package com.createstats.surveyor;

import com.createstats.CreateStats;
import com.createstats.vccmk4.route.RouteHosts;
import com.createstats.vccmk4.route.Vcc4Route;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = CreateStats.MOD_ID)
public final class RouteTravelCommands {
    private static final Map<UUID, UUID> WATCHES = new HashMap<>();
    private RouteTravelCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("pathways")
                .then(Commands.literal("travel")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            int count = 0;
                            for (Vcc4Route route : RouteHosts.all()) {
                                if (!route.assigned() || route.host().routeLevel() != player.serverLevel() || !RouteOrders.mayOrder(player, player.serverLevel(), route)) continue;
                                String line = diagnostic(route);
                                ctx.getSource().sendSuccess(() -> Component.literal(line), false);
                                String command = "/pathways travel watch " + route.host().consoleId();
                                ctx.getSource().sendSuccess(() -> Component.literal("Watch " + route.host().vehicleName())
                                        .withStyle(style -> style.withUnderlined(true)
                                                .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, command))), false);
                                count++;
                            }
                            if (count == 0) ctx.getSource().sendSuccess(() -> Component.literal("No active route vehicles you can inspect in this dimension."), false);
                            return count;
                        })
                        .then(Commands.literal("watch").then(Commands.argument("console", UuidArgument.uuid()).executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            UUID id = UuidArgument.getUuid(ctx, "console"); Vcc4Route route = RouteHosts.get(id);
                            if (route == null || route.host().routeLevel() != player.serverLevel() || !RouteOrders.mayOrder(player, player.serverLevel(), route)) {
                                ctx.getSource().sendFailure(Component.literal("That vehicle is not available to you here.")); return 0;
                            }
                            WATCHES.put(player.getUUID(), id);
                            ctx.getSource().sendSuccess(() -> Component.literal("Watching " + route.host().vehicleName() + ". Stop with /pathways travel off."), false);
                            return 1;
                        })))
                        .then(Commands.literal("off").executes(ctx -> {
                            WATCHES.remove(ctx.getSource().getPlayerOrException().getUUID());
                            ctx.getSource().sendSuccess(() -> Component.literal("Travel watch stopped."), false); return 1;
                        }))));
    }

    public static String diagnostic(Vcc4Route route) {
        var host = route.host(); var command = route.command(); var runner = route.runner();
        return String.format(Locale.ROOT, "%s | %s%s | %s | %.2f b/s | s %.1f | throttle %.2f brake %.2f%s",
                host.vehicleName(), host.present() ? "Real" : "Unseen", route.awaitingVehicle() ? " (preparing)" : "",
                route.state().key, host.measuredSpeed(), runner == null ? 0 : runner.progress(), command.throttle(),
                command.brakeLevel(), runner == null || runner.reason().isEmpty() ? "" : " | " + runner.reason());
    }

    @SubscribeEvent
    public static void watch(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.tickRateManager().runsNormally() || level.getGameTime() % 20 != 0) return;
        for (ServerPlayer player : level.players()) {
            UUID id = WATCHES.get(player.getUUID()); if (id == null) continue;
            Vcc4Route route = RouteHosts.get(id);
            if (route == null || route.host().routeLevel() != level) {
                player.displayClientMessage(Component.literal("Travel watch: vehicle is preparing or unavailable"), true); continue;
            }
            if (!RouteOrders.mayOrder(player, level, route)) { WATCHES.remove(player.getUUID()); continue; }
            player.displayClientMessage(Component.literal(diagnostic(route)), true);
        }
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { WATCHES.clear(); }
}
