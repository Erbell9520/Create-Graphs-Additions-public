package com.createstats.surveyor;

import com.createstats.flight.bridge.SableBridge;
import com.createstats.surveyor.core.Comfort;
import com.createstats.surveyor.core.Pathway;
import com.createstats.surveyor.core.RouteRunner;
import com.createstats.surveyor.network.RouteActionPacket;
import com.createstats.surveyor.network.RoutePickedPacket;
import com.createstats.vccmk4.route.RouteHost;
import com.createstats.vccmk4.route.RouteHosts;
import com.createstats.vccmk4.route.RouteReflect;
import com.createstats.vccmk4.route.Vcc4Route;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RouteOrders {
    public static final int PICK_TICKS = 2400;

    private record Picked(UUID console, long tick) {
    }

    private static final Map<UUID, Picked> PICKS = new ConcurrentHashMap<>();

    private RouteOrders() {
    }

    public static void apply(ServerPlayer player, RouteActionPacket p) {
        if (!(player.level() instanceof ServerLevel level) || player.isSpectator()) return;
        switch (p.op()) {
            case RouteActionPacket.PICK -> pick(player, level, p);
            case RouteActionPacket.ARM_RECORD -> armRecord(player, level, BlockPos.of(p.pos()));
            default -> order(player, level, p);
        }
    }

    private static void pick(ServerPlayer player, ServerLevel level, RouteActionPacket p) {
        BlockPos pos = BlockPos.of(p.pos());
        Vcc4Route route = byBlock(level, pos);
        if (route == null) {
            PacketDistributor.sendToPlayer(player, new RoutePickedPacket(new UUID(0, 0), "", 0, 0, 0));
            player.displayClientMessage(Component.translatable("create_stats.surveyor.not_a_vehicle").withStyle(ChatFormatting.GOLD), true);
            return;
        }
        RouteHost host = route.host();
        if (!SableBridge.isPlayerInInteractionRangeFor(host.consoleEntity(), player) && !withinBlocks(player, host, 8.0)) {
            player.displayClientMessage(Component.translatable("create_stats.surveyor.too_far").withStyle(ChatFormatting.GOLD), true);
            return;
        }
        if (p.a() == 1) {
            if (route.assigned()) route.unassign(level, "by " + player.getGameProfile().getName());
            player.displayClientMessage(Component.translatable("create_stats.surveyor.unassigned", host.vehicleName()), true);
            return;
        }

        if (pos.equals(host.consolePos())) {
            armRecord(player, level, pos);
            return;
        }
        PICKS.put(player.getUUID(), new Picked(host.consoleId(), level.getGameTime()));
        Vector3d at = worldOf(host);
        PacketDistributor.sendToPlayer(player, new RoutePickedPacket(host.consoleId(), host.vehicleName(), at.x, at.y, at.z));
        player.displayClientMessage(Component.translatable("create_stats.surveyor.picked", host.vehicleName()).withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    private static void armRecord(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Vcc4Route route = byBlock(level, pos);
        if (route == null) {
            player.displayClientMessage(Component.translatable("create_stats.surveyor.not_a_vehicle").withStyle(ChatFormatting.GOLD), true);
            return;
        }
        if (!SableBridge.isPlayerInInteractionRangeFor(route.host().consoleEntity(), player) && !player.hasPermissions(2)) {
            player.displayClientMessage(Component.translatable("create_stats.surveyor.too_far").withStyle(ChatFormatting.GOLD), true);
            return;
        }
        route.armRecord(level, player);
    }

    private static void order(ServerPlayer player, ServerLevel level, RouteActionPacket p) {
        Vcc4Route route = RouteHosts.get(p.console());

        if (route == null || route.host().routeLevel() != level) {
            player.displayClientMessage(Component.translatable("create_stats.surveyor.vehicle_unloaded").withStyle(ChatFormatting.GOLD), true);
            return;
        }
        if (!mayOrder(player, level, route)) {
            player.displayClientMessage(Component.translatable("create_stats.surveyor.not_yours").withStyle(ChatFormatting.GOLD), true);
            return;
        }
        switch (p.op()) {
            case RouteActionPacket.ASSIGN -> {
                Pathway path = PathwayStore.get(level).get(p.pathway());
                if (path == null) return;
                if (!PathwayOps.mayEdit(player, path)) {
                    player.displayClientMessage(Component.translatable("create_stats.surveyor.not_your_pathway").withStyle(ChatFormatting.GOLD), true);
                    return;
                }
                if (path.nodes().size() < 2) {
                    player.displayClientMessage(Component.translatable("create_stats.surveyor.pathway_too_short").withStyle(ChatFormatting.GOLD), true);
                    return;
                }
                route.assign(level, path, player);
                PICKS.remove(player.getUUID());
                player.displayClientMessage(Component.translatable("create_stats.surveyor.assigned", route.host().vehicleName(), path.name())
                        .withStyle(ChatFormatting.GREEN), true);
            }
            case RouteActionPacket.UNASSIGN -> route.unassign(level, "by " + player.getGameProfile().getName());
            case RouteActionPacket.START -> route.start(level);
            case RouteActionPacket.PAUSE -> route.pause(level, "paused by " + player.getGameProfile().getName());
            case RouteActionPacket.SKIP_STOP -> route.skipStop();
            case RouteActionPacket.SEND_TO -> route.sendTo(p.a());
            case RouteActionPacket.RECALL -> route.recall();
            case RouteActionPacket.STOP_RECORD -> route.stopRecord(level, p.a() == 1);
            case RouteActionPacket.CANCEL_RECORD -> route.cancelRecord(level);
            case RouteActionPacket.OPTION -> option(route, p.a());
            case RouteActionPacket.KEEP_TRACE -> route.keepTrace(level, level.getGameTime(), "kept", player);
            case RouteActionPacket.REMOVE_SCHEDULE -> route.removeSchedule(level);
            default -> {
            }
        }
    }

    public static final int OPT_CRUISE = 1;
    public static final int OPT_COMFORT = 2;
    public static final int OPT_START_ON_ASSIGN = 3;
    public static final int OPT_HORN = 4;

    private static void option(Vcc4Route route, int a) {
        int id = a / 1000;
        int value = a % 1000;
        RouteRunner.Options o = route.options();
        switch (id) {
            case OPT_CRUISE -> route.setOptions(new RouteRunner.Options(Math.max(0, Math.min(400, value)) / 10.0,
                    o.comfortOverride(), o.startOnAssign(), o.hornWhenBlocked()));
            case OPT_COMFORT -> route.setOptions(Vcc4Route.withComfort(o, value < 0 || value >= Comfort.values().length ? null : Comfort.byOrdinal(value)));
            case OPT_START_ON_ASSIGN -> route.setOptions(new RouteRunner.Options(o.cruiseOverride(), o.comfortOverride(), value != 0, o.hornWhenBlocked()));
            case OPT_HORN -> route.setOptions(new RouteRunner.Options(o.cruiseOverride(), o.comfortOverride(), o.startOnAssign(), value != 0));
            default -> {
            }
        }
    }

    public static boolean mayOrder(ServerPlayer player, ServerLevel level, Vcc4Route route) {
        if (player.hasPermissions(2)) return true;
        RouteHost host = route.host();
        if (SableBridge.isPlayerInInteractionRangeFor(host.consoleEntity(), player)) return true;
        if (player.getUUID().equals(route.assignedBy())) return true;
        Pathway on = route.pathwayId() == null ? null : PathwayStore.get(level).get(route.pathwayId());
        if (on != null && on.owner().equals(player.getUUID())) return true;
        Picked pk = PICKS.get(player.getUUID());
        return pk != null && pk.console().equals(host.consoleId()) && level.getGameTime() - pk.tick() <= PICK_TICKS;
    }

    @Nullable
    public static Vcc4Route byBlock(ServerLevel level, BlockPos pos) {
        for (Vcc4Route r : RouteHosts.all()) {
            RouteHost h = r.host();
            if (h.routeLevel() != level) continue;
            if (h.consolePos().equals(pos)) return r;
        }
        for (Vcc4Route r : RouteHosts.all()) {
            RouteHost h = r.host();
            if (h.routeLevel() != level) continue;
            int[] b = RouteReflect.plotBounds(h.craft());
            if (b != null && pos.getX() >= b[0] && pos.getX() <= b[3] && pos.getY() >= b[1] && pos.getY() <= b[4]
                    && pos.getZ() >= b[2] && pos.getZ() <= b[5]) {
                return r;
            }
        }
        return null;
    }

    private static boolean withinBlocks(ServerPlayer player, RouteHost host, double reach) {
        Vector3d at = worldOf(host);
        return player.position().distanceToSqr(at.x, at.y, at.z) <= reach * reach;
    }

    private static Vector3d worldOf(RouteHost host) {
        Object craft = host.craft();
        Vector3d local = host.referenceLocal();
        Vector3d w = craft == null ? null : SableBridge.localToWorld(craft, local);
        return w == null ? new Vector3d(local) : w;
    }

    public static void forget(UUID player) {
        PICKS.remove(player);
    }
}
