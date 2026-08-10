package com.createstats.flight.network;

import com.createstats.CreateStats;
import com.createstats.flight.blockentity.FlightControlComputerBE;
import com.createstats.flight.menu.FlightControlComputerMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import javax.annotation.Nullable;
import java.util.UUID;

public record FccWorkbenchActionPacket(
        int containerId,
        Action action,
        @Nullable UUID primaryId,
        @Nullable UUID secondaryId,
        String text
) implements CustomPacketPayload {

    public enum Action {
        RENAME_DEVICE,
        CREATE_GROUP,
        ASSIGN_DEVICE_TO_GROUP,
        RENAME_GROUP,
        DELETE_GROUP,
        UNASSIGN_DEVICE_FROM_GROUP,
        CREATE_CONTROL,
        RENAME_CONTROL,
        CYCLE_CONTROL_WIDGET,
        DELETE_CONTROL,
        ADD_ROUTE_TO_CONTROL,
        DELETE_ROUTE,
        CYCLE_ROUTE_OUTPUT,
        TOGGLE_ROUTE_INVERT,
        ADJUST_ROUTE_SCALE,
        CYCLE_DEVICE_CATEGORY,
        APPLY_ZEPPELIN_TEMPLATE,
        TOGGLE_RACK_ROW_MODE,
        ASSIGN_PAYLOAD_LINK,
        ASSIGN_DEVICE_TO_DIAGRAM_ZONE,
        SYNC_DIAGRAM_COCKPIT,
        RESET_DIAGRAM,
        ADJUST_CONTROL_VALUE,
        CENTER_CONTROL_VALUE,
        SET_CONTROL_VALUE,
        SET_LINKED_CONTROLLER_INTERVAL,
        RECALIBRATE_BATTLE_BASELINE,
        SET_NAV_TABLE_SPEED,
        SET_PATHFINDER_LOOKAHEAD_SCALE
    }

    public static final CustomPacketPayload.Type<FccWorkbenchActionPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "fcc_workbench_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FccWorkbenchActionPacket> STREAM_CODEC =
            StreamCodec.of((buf, p) -> {
                buf.writeVarInt(p.containerId);
                buf.writeEnum(p.action);
                buf.writeBoolean(p.primaryId != null);
                if (p.primaryId != null) buf.writeUUID(p.primaryId);
                buf.writeBoolean(p.secondaryId != null);
                if (p.secondaryId != null) buf.writeUUID(p.secondaryId);
                buf.writeUtf(p.text == null ? "" : p.text, 128);
            }, buf -> new FccWorkbenchActionPacket(
                    buf.readVarInt(),
                    buf.readEnum(Action.class),
                    buf.readBoolean() ? buf.readUUID() : null,
                    buf.readBoolean() ? buf.readUUID() : null,
                    buf.readUtf(128)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // [1]
    private static final java.util.Map<java.util.UUID, long[]> ACTION_RATE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int ACTION_BURST = 120;
    private static final long ACTION_WINDOW_MS = 1000L;

    private static boolean rateLimited(ServerPlayer player) {
        long now = System.currentTimeMillis();
        // state[0] = window start ms, state[1] = tokens remaining. Runs on the server thread only.
        long[] state = ACTION_RATE.computeIfAbsent(player.getUUID(), k -> new long[]{now, ACTION_BURST});
        if (now - state[0] >= ACTION_WINDOW_MS) {
            state[0] = now;
            state[1] = ACTION_BURST;
        }
        if (state[1] <= 0) return true;
        state[1]--;
        return false;
    }

    public static void handle(FccWorkbenchActionPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (!(sp.containerMenu instanceof FlightControlComputerMenu menu)) return;
            if (menu.containerId != pkt.containerId) return;
            if (!menu.stillValid(sp)) return;
            FlightControlComputerBE fcc = menu.getController();
            if (fcc.isRemoved()) return;
            if (rateLimited(sp)) return;

            switch (pkt.action) {
                case RENAME_DEVICE -> {
                    if (pkt.primaryId == null) return;
                    fcc.renameWorkbenchDevice(pkt.primaryId, pkt.text);
                }
                case CREATE_GROUP -> fcc.createWorkbenchGroup(pkt.text);
                case RENAME_GROUP -> {
                    if (pkt.primaryId == null) return;
                    fcc.renameWorkbenchGroup(pkt.primaryId, pkt.text);
                }
                case DELETE_GROUP -> {
                    if (pkt.primaryId == null) return;
                    fcc.deleteWorkbenchGroup(pkt.primaryId);
                }
                case ASSIGN_DEVICE_TO_GROUP -> {
                    if (pkt.primaryId == null || pkt.secondaryId == null) return;
                    fcc.assignWorkbenchDeviceToGroup(pkt.primaryId, pkt.secondaryId);
                }
                case UNASSIGN_DEVICE_FROM_GROUP -> {
                    if (pkt.primaryId == null || pkt.secondaryId == null) return;
                    fcc.unassignWorkbenchDeviceFromGroup(pkt.primaryId, pkt.secondaryId);
                }
                case CREATE_CONTROL -> fcc.createWorkbenchControl(pkt.text);
                case RENAME_CONTROL -> {
                    if (pkt.primaryId == null) return;
                    fcc.renameWorkbenchControl(pkt.primaryId, pkt.text);
                }
                case CYCLE_CONTROL_WIDGET -> {
                    if (pkt.primaryId == null) return;
                    fcc.cycleWorkbenchControlWidget(pkt.primaryId);
                }
                case DELETE_CONTROL -> {
                    if (pkt.primaryId == null) return;
                    fcc.deleteWorkbenchControl(pkt.primaryId);
                }
                case ADD_ROUTE_TO_CONTROL -> {
                    if (pkt.primaryId == null || pkt.secondaryId == null) return;
                    fcc.addWorkbenchRoute(pkt.primaryId, pkt.secondaryId, pkt.text);
                }
                case DELETE_ROUTE -> {
                    if (pkt.primaryId == null) return;
                    fcc.deleteWorkbenchRoute(pkt.primaryId);
                }
                case CYCLE_ROUTE_OUTPUT -> {
                    if (pkt.primaryId == null) return;
                    fcc.cycleWorkbenchRouteOutput(pkt.primaryId);
                }
                case TOGGLE_ROUTE_INVERT -> {
                    if (pkt.primaryId == null) return;
                    fcc.toggleWorkbenchRouteInvert(pkt.primaryId);
                }
                case ADJUST_ROUTE_SCALE -> {
                    if (pkt.primaryId == null) return;
                    fcc.adjustWorkbenchRouteScale(pkt.primaryId, pkt.text);
                }
                case CYCLE_DEVICE_CATEGORY -> {
                    if (pkt.primaryId == null) return;
                    fcc.cycleWorkbenchDeviceCategory(pkt.primaryId);
                }
                case APPLY_ZEPPELIN_TEMPLATE -> fcc.applyZeppelinTemplate();
                case TOGGLE_RACK_ROW_MODE -> {
                    try {
                        fcc.toggleRackRowMode(Integer.parseInt(pkt.text.trim()));
                    } catch (NumberFormatException ignored) {
                    }
                }
                case ASSIGN_PAYLOAD_LINK -> {
                    if (pkt.primaryId == null) return;
                    fcc.assignWorkbenchPayloadLink(pkt.primaryId, pkt.text);
                }
                case ASSIGN_DEVICE_TO_DIAGRAM_ZONE -> {
                    if (pkt.primaryId == null) return;
                    fcc.assignWorkbenchDeviceToDiagramZone(pkt.primaryId, pkt.text);
                }
                case SYNC_DIAGRAM_COCKPIT -> fcc.syncWorkbenchDiagramCockpit();
                case RESET_DIAGRAM -> fcc.resetWorkbenchDiagram();
                case ADJUST_CONTROL_VALUE -> {
                    if (pkt.primaryId == null) return;
                    fcc.adjustWorkbenchControlValue(pkt.primaryId, pkt.text);
                }
                case CENTER_CONTROL_VALUE -> {
                    if (pkt.primaryId == null) return;
                    fcc.centerWorkbenchControlValue(pkt.primaryId);
                }
                case SET_CONTROL_VALUE -> {
                    if (pkt.primaryId == null) return;
                    fcc.setWorkbenchControlValue(pkt.primaryId, pkt.text);
                }
                case SET_LINKED_CONTROLLER_INTERVAL -> fcc.setLinkedControllerRampInterval(pkt.text);
                case RECALIBRATE_BATTLE_BASELINE -> fcc.recalibrateBattleBaseline();
                case SET_NAV_TABLE_SPEED -> fcc.setNavTableMaxSpeed(pkt.text);
                case SET_PATHFINDER_LOOKAHEAD_SCALE -> fcc.setPathfinderLookaheadScale(pkt.text);
            }
        });
    }
}
