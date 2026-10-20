package com.createstats.vccmk3.network;

import com.createstats.CreateStats;
import com.createstats.vccmk3.VccMk3BlockEntity;
import com.createstats.vccmk3.VccMk3Menu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public record VccMk3ActionPacket(int containerId, Action action, @Nullable UUID targetId,
                                 double value, String text) implements CustomPacketPayload {
    public enum Action {
        SET_ARMED,
        SET_CRAFT_NAME,
        TOGGLE_LINK_ROW,

        SET_AXIS,

        CENTRE_AXES,
        RESCAN,

        SET_AIRFRAME,

        ASSIGN_SLOT,

        CLEAR_SLOT,
        SET_MANUAL_OVERRIDE,
        CLEAR_MANUAL_OVERRIDES,
        SET_FLIP_GUARD,
        TOGGLE_RX_ROW,

        BIND_RX_AXIS,
        TOGGLE_DEVICE_ENABLED,
        SET_AUTOPILOT,
        SET_HUD_PIN,
        CYCLE_AUTO_SOURCE
    }

    public static final Type<VccMk3ActionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "vcc_mk3_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VccMk3ActionPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> {
                buf.writeVarInt(packet.containerId);
                buf.writeEnum(packet.action);
                buf.writeBoolean(packet.targetId != null);
                if (packet.targetId != null) buf.writeUUID(packet.targetId);
                buf.writeDouble(packet.value);
                buf.writeUtf(packet.text == null ? "" : packet.text, 64);
            }, buf -> new VccMk3ActionPacket(
                    buf.readVarInt(),
                    buf.readEnum(Action.class),
                    buf.readBoolean() ? buf.readUUID() : null,
                    buf.readDouble(),
                    buf.readUtf(64)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static final Map<UUID, long[]> RATE = new ConcurrentHashMap<>();
    private static final int BURST = 120;
    private static final long WINDOW_MS = 1000L;

    private static boolean allow(ServerPlayer player) {
        long now = System.currentTimeMillis();
        long[] bucket = RATE.computeIfAbsent(player.getUUID(), key -> new long[] { now, 0L });
        synchronized (bucket) {
            if (now - bucket[0] > WINDOW_MS) {
                bucket[0] = now;
                bucket[1] = 0L;
            }
            return ++bucket[1] <= BURST;
        }
    }

    public static void handle(VccMk3ActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!allow(player)) return;
            if (!(player.containerMenu instanceof VccMk3Menu menu)) return;
            if (menu.containerId != packet.containerId) return;

            VccMk3BlockEntity controller = menu.controller();
            if (controller == null || controller.isRemoved()) return;

            switch (packet.action) {
                case SET_ARMED -> controller.setArmed(packet.value > 0.5);
                case SET_CRAFT_NAME -> controller.setCraftName(packet.text);
                case TOGGLE_LINK_ROW -> controller.toggleLinkRow((int) Math.round(packet.value));
                case SET_AXIS -> controller.setAxisDemand(
                        com.createstats.vccmk3.model.ControlAxis.safeValueOf(packet.text, null),
                        packet.value);
                case CENTRE_AXES -> controller.centreAllAxes();
                case SET_AIRFRAME -> {
                    int dropped = controller.setAirframe(
                            com.createstats.vccmk3.model.Airframe.byOrdinal((int) Math.round(packet.value)));
                    if (dropped > 0) {
                        controller.setNotice(dropped + (dropped == 1
                                ? " assignment did not exist on this airframe and was dropped."
                                : " assignments did not exist on this airframe and were dropped."));
                    }
                }
                case ASSIGN_SLOT -> controller.assignToSlot(packet.targetId, packet.text);
                case CLEAR_SLOT -> controller.clearSlot(packet.text);
                case RESCAN -> controller.rescan();
                case SET_MANUAL_OVERRIDE -> {
                    if (packet.targetId != null) {
                        controller.setManualOverride(packet.targetId, (int) Math.round(packet.value));
                    }
                }
                case CLEAR_MANUAL_OVERRIDES -> controller.clearManualOverrides();
                case SET_AUTOPILOT -> controller.setAutoPilotEngaged(packet.value > 0.5);
                case SET_HUD_PIN -> controller.setHudSubscribed(player, packet.value > 0.5);
                case CYCLE_AUTO_SOURCE -> controller.cycleAutoSource();
                case TOGGLE_DEVICE_ENABLED -> {
                    if (packet.targetId != null) controller.toggleDeviceEnabled(packet.targetId);
                }
                case TOGGLE_RX_ROW -> controller.toggleRxRow((int) Math.round(packet.value));
                case BIND_RX_AXIS -> controller.bindRxAxis((int) Math.round(packet.value),
                        com.createstats.vccmk3.model.ControlAxis.safeValueOf(packet.text, null));
                case SET_FLIP_GUARD -> controller.setFlipGuardDegrees(packet.value);
            }
        });
    }

    // strip control chars, a stray section sign recolours the panel
    static String sanitiseLabel(String raw) {
        if (raw == null) return "";
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length() && out.length() < 32; i++) {
            char c = raw.charAt(i);
            if (c >= ' ' && c != '\u00A7' && c != '\u007F') out.append(c);
        }
        return out.toString().trim();
    }
}
