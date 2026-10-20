package com.createstats.network;

import com.createstats.blockentity.HomeSecurityPanelBE;
import com.createstats.security.SecurityRole;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;
import java.util.UUID;

public record SecurityActionPacket(BlockPos panelPos, int action, BlockPos target,
                                   Optional<UUID> player, int value, String text)
        implements CustomPacketPayload {
    public static final int REFRESH = 0;

    public static final int SET_LEVEL = 1;

    public static final int SET_DEVICE_NAME = 2;

    public static final int UNLINK = 3;

    public static final int ADD_PLAYER = 4;

    public static final int REMOVE_PLAYER = 5;

    public static final int SET_PLAYER_LEVEL = 6;

    public static final int SET_PLAYER_ROLE = 7;

    public static final int TRANSFER = 8;

    public static final int FAIL_SECURE = 9;

    public static final int PROXIMITY = 10;

    public static final int HOUSE_NAME = 11;

    public static final int SET_MODE = 12;

    public static final int CLEAR_LOCKDOWN = 13;

    public static final int SET_ENTRANCE = 14;

    public static final int SET_SENSOR_ARMED = 15;

    public static final int SET_SENSOR_RANGE = 16;

    public static final int SET_LOCKER_CODE = 17;

    public static final int SEAL_SHUTTER = 18;

    public static final int SET_SHUTTER_DUSK = 19;

    public static final int SEAL_ALL_SHUTTERS = 20;

    public static final int SET_DOOR_RANGE = 21;

    public static final int SET_PASSWORD = 22;

    public static final int SET_ALWAYS_ASK = 23;

    public static final int JOIN_HOUSEHOLD = 24;

    public static final int FINISH_SETUP = 26;

    public static final int REMOVE_HOUSEHOLD = 27;

    public static final int SET_REPELLER_ON = 28;

    public static final int SET_REPELLER_LOCK = 29;

    public static final int SET_REPELLER_REDSTONE = 30;

    public static final int TUNE_REPELLER = 31;

    public static final int SWITCH_ALL_REPELLERS = 32;

    public static final int SURVEY_REPELLER = 33;

    public static final int FORGET_DEVICE = 34;

    public static final int SET_COIL_ON = 35;

    public static final int SET_COIL_LOCK = 36;

    public static final int SET_COIL_REDSTONE = 37;

    public static final int SET_COIL_RULE = 38;

    public static final int SWITCH_ALL_COILS = 39;

    private static final double REACH_SQ = 64.0;

    public static final CustomPacketPayload.Type<SecurityActionPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("create_stats", "security_action"));

    public static final StreamCodec<FriendlyByteBuf, SecurityActionPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {
                buf.writeBlockPos(pkt.panelPos());
                buf.writeVarInt(pkt.action());
                buf.writeBlockPos(pkt.target());
                buf.writeBoolean(pkt.player().isPresent());
                pkt.player().ifPresent(buf::writeUUID);
                buf.writeVarInt(pkt.value());
                buf.writeUtf(pkt.text(), 64);
            }, buf -> new SecurityActionPacket(buf.readBlockPos(), buf.readVarInt(),
                    buf.readBlockPos(),
                    buf.readBoolean() ? Optional.of(buf.readUUID()) : Optional.empty(),
                    buf.readVarInt(), buf.readUtf(64)));

    public static SecurityActionPacket refresh(BlockPos panel) {
        return new SecurityActionPacket(panel, REFRESH, panel, Optional.empty(), 0, "");
    }

    public static SecurityActionPacket device(BlockPos panel, int action, BlockPos device, int value) {
        return new SecurityActionPacket(panel, action, device, Optional.empty(), value, "");
    }

    public static SecurityActionPacket rename(BlockPos panel, BlockPos device, String name) {
        return new SecurityActionPacket(panel, SET_DEVICE_NAME, device, Optional.empty(), 0, name);
    }

    public static SecurityActionPacket person(BlockPos panel, int action, UUID id, int value) {
        return new SecurityActionPacket(panel, action, panel, Optional.of(id), value, "");
    }

    public static SecurityActionPacket panel(BlockPos panel, int action, int value, String text) {
        return new SecurityActionPacket(panel, action, panel, Optional.empty(), value, text);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SecurityActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)) {
                return;
            }

            if (sender.distanceToSqr(packet.panelPos().getX() + 0.5,
                    packet.panelPos().getY() + 0.5, packet.panelPos().getZ() + 0.5) > REACH_SQ) {
                return;
            }
            final BlockEntity be = sender.level().getBlockEntity(packet.panelPos());
            if (!(be instanceof HomeSecurityPanelBE panel)) {
                return;
            }

            if (!panel.admits(sender)) {
                if (!panel.powered()) {
                    PacketDistributor.sendToPlayer(sender,
                            OpenSecurityPanelPacket.shut(packet.panelPos()));
                }
                return;
            }

            panel.refreshAdmission(sender);

            switch (packet.action()) {
                case SET_LEVEL -> panel.setRequiredLevel(packet.target(), packet.value(), sender);
                case SET_DEVICE_NAME -> panel.setDeviceName(packet.target(), packet.text(), sender);
                // unbind only. the old toggle bound any device in the dimension onto our roster

                case UNLINK -> {
                    if (panel.roleOf(sender).mayEditDevices()) {
                        panel.toggleLinkIfLinked(packet.target(), sender);
                    }
                }
                case ADD_PLAYER -> addByName(panel, sender, packet.text());
                case REMOVE_PLAYER -> packet.player().ifPresent(id -> panel.removePlayer(id, sender));
                case SET_PLAYER_LEVEL -> packet.player()
                        .ifPresent(id -> panel.setPlayerLevel(id, packet.value(), sender));
                case SET_PLAYER_ROLE -> packet.player().ifPresent(id ->
                        panel.setPlayerRole(id, SecurityRole.byOrdinal(packet.value()), sender));
                case TRANSFER -> packet.player().ifPresent(id -> panel.transferOwnership(id, sender));
                case FAIL_SECURE -> panel.setFailSecure(packet.value() != 0, sender);
                case PROXIMITY -> panel.setProximity(packet.value(), sender);
                case HOUSE_NAME -> panel.setHouseName(packet.text(), sender);

                case SET_MODE -> panel.setMode(
                        com.createstats.security.SecurityMode.byOrdinal(packet.value()), sender);
                case CLEAR_LOCKDOWN -> panel.clearLockdown(sender);
                case SET_ENTRANCE -> panel.setEntrance(packet.target(), sender);
                case SET_SENSOR_ARMED -> panel.setSensorArmed(packet.target(), packet.value() != 0, sender);
                case SET_SENSOR_RANGE -> panel.setSensorRadius(packet.target(), packet.value(), sender);
                case SET_DOOR_RANGE -> panel.setDoorProximity(packet.target(), packet.value(), sender);
                case SET_LOCKER_CODE -> panel.setLockerCode(packet.target(), packet.text(), sender);
                case SEAL_SHUTTER -> panel.setShutterSealed(packet.target(), packet.value() != 0, sender);
                case SET_SHUTTER_DUSK -> panel.setShutterDusk(packet.target(), packet.value() != 0, sender);
                case SEAL_ALL_SHUTTERS -> panel.sealAllShutters(packet.value() != 0, sender);
                case SET_PASSWORD -> panel.setPassword(packet.text(), sender);
                case SET_ALWAYS_ASK -> panel.setAlwaysAsk(packet.value() != 0, sender);
                case JOIN_HOUSEHOLD -> packet.player()
                        .ifPresent(id -> panel.joinHousehold(id, sender));
                case FINISH_SETUP -> panel.finishSetup(sender);
                case REMOVE_HOUSEHOLD -> packet.player()
                        .ifPresent(id -> panel.removeHousehold(id, sender));
                case SET_REPELLER_ON -> panel.setRepellerOn(packet.target(), packet.value() != 0, sender);
                case SET_REPELLER_LOCK -> panel.setRepellerLocked(packet.target(), packet.value() != 0, sender);
                case SET_REPELLER_REDSTONE ->
                        panel.setRepellerRedstone(packet.target(), packet.value() != 0, sender);
                case TUNE_REPELLER ->
                        panel.tuneRepeller(packet.target(), packet.text(), packet.value(), sender);
                case SWITCH_ALL_REPELLERS -> panel.switchAllRepellers(packet.value() != 0, sender);
                case SURVEY_REPELLER -> panel.surveyRepeller(packet.target(), sender);
                case FORGET_DEVICE -> panel.forgetDevice(packet.target(), sender);
                case SET_COIL_ON -> panel.setCoilOn(packet.target(), packet.value() != 0, sender);
                case SET_COIL_LOCK -> panel.setCoilLocked(packet.target(), packet.value() != 0, sender);
                case SET_COIL_REDSTONE -> panel.setCoilRedstone(packet.target(), packet.value() != 0, sender);
                case SET_COIL_RULE -> panel.setCoilRule(packet.target(),
                        com.createstats.stormcoil.CoilRules.editState(packet.value()),
                        com.createstats.stormcoil.CoilRules.editRule(packet.value()), sender);
                case SWITCH_ALL_COILS -> panel.switchAllCoils(packet.value() != 0, sender);

                default -> {
                }
            }
            PacketDistributor.sendToPlayer(sender, SecuritySyncPacket.of(panel, sender));
        });
    }

    private static void addByName(HomeSecurityPanelBE panel, ServerPlayer sender, String name) {
        if (name == null || name.isBlank() || sender.getServer() == null) {
            return;
        }
        final ServerPlayer online = sender.getServer().getPlayerList().getPlayerByName(name);
        if (online != null) {
            panel.addPlayer(online.getUUID(), online.getGameProfile().getName(), sender);
            return;
        }
        final net.minecraft.server.players.GameProfileCache cache =
                sender.getServer().getProfileCache();
        if (cache != null) {
            cache.get(name).ifPresent(profile ->
                    panel.addPlayer(profile.getId(), profile.getName(), sender));
        }
    }
}
