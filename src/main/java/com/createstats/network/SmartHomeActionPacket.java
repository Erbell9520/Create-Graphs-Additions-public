package com.createstats.network;

import com.createstats.blockentity.SmartHomePanelBE;
import com.createstats.home.HouseModel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// one packet, a discriminator, one reach check
public record SmartHomeActionPacket(BlockPos panelPos, int action, BlockPos target, int value)
        implements CustomPacketPayload {
    public static final int ACTION_REFRESH = 0;

    public static final int ACTION_GATE = 1;

    public static final int ACTION_MODE = 2;

    public static final int ACTION_ALL = 3;

    public static final int ACTION_UNLINK = 4;

    public static final int ACTION_FACE = 5;

    public static final int ACTION_COLOUR = 6;

    public static final int ACTION_ROOM_ALL = 7;

    public static final int ACTION_STANDBY_OFF = 8;

    public static final int ACTION_CLAIM = 9;

    public static final int ACTION_ROOM_DELETE = 10;

    public static final int ACTION_ROOM_FOLLOW = 11;

    public static final int ACTION_ROOM_ICON = 12;

    public static final int ACTION_SCENE_RECALL = 13;

    public static final int ACTION_SCENE_SAVE = 14;

    public static final int ACTION_SCENE_DELETE = 15;

    public static final int ACTION_SCENE_ICON = 16;

    public static final int ACTION_RULE = 17;

    public static final int ACTION_TOASTS = 18;

    public static final int ACTION_KIND_ALL = 19;

    public static final int ACTION_ROOM_LIGHTS = 20;

    public static final CustomPacketPayload.Type<SmartHomeActionPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("create_stats", "smart_home_action"));

    public static final StreamCodec<FriendlyByteBuf, SmartHomeActionPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {
                buf.writeBlockPos(pkt.panelPos());
                buf.writeVarInt(pkt.action());
                buf.writeBlockPos(pkt.target());
                buf.writeVarInt(pkt.value());
            }, buf -> new SmartHomeActionPacket(buf.readBlockPos(), buf.readVarInt(),
                    buf.readBlockPos(), buf.readVarInt()));

    public static SmartHomeActionPacket refresh(BlockPos panel, int knownRevision, boolean wiring) {
        return new SmartHomeActionPacket(panel, ACTION_REFRESH, panel,
                (Math.max(-1, knownRevision) << 1) | (wiring ? 1 : 0));
    }

    public static SmartHomeActionPacket gate(BlockPos panel, BlockPos row, boolean off) {
        return new SmartHomeActionPacket(panel, ACTION_GATE, row, off ? 1 : 0);
    }

    public static SmartHomeActionPacket mode(BlockPos panel, BlockPos row, int mode) {
        return new SmartHomeActionPacket(panel, ACTION_MODE, row, mode);
    }

    public static SmartHomeActionPacket unlink(BlockPos panel, BlockPos row) {
        return new SmartHomeActionPacket(panel, ACTION_UNLINK, row, 0);
    }

    public static SmartHomeActionPacket face(BlockPos panel, boolean hiFi) {
        return new SmartHomeActionPacket(panel, ACTION_FACE, panel, hiFi ? 1 : 0);
    }

    public static SmartHomeActionPacket colour(BlockPos panel, BlockPos row, int dyeId) {
        return new SmartHomeActionPacket(panel, ACTION_COLOUR, row, dyeId);
    }

    public static SmartHomeActionPacket roomAll(BlockPos panel, int roomId, boolean off) {
        return new SmartHomeActionPacket(panel, ACTION_ROOM_ALL, panel, ((roomId + 2) << 1) | (off ? 1 : 0));
    }

    public static SmartHomeActionPacket standbyOff(BlockPos panel) {
        return new SmartHomeActionPacket(panel, ACTION_STANDBY_OFF, panel, 0);
    }

    public static SmartHomeActionPacket claim(BlockPos panel, BlockPos floor) {
        return new SmartHomeActionPacket(panel, ACTION_CLAIM, floor, 0);
    }

    public static SmartHomeActionPacket roomDelete(BlockPos panel, int roomId) {
        return new SmartHomeActionPacket(panel, ACTION_ROOM_DELETE, panel, roomId);
    }

    public static SmartHomeActionPacket roomFollow(BlockPos panel, int roomId, boolean follow) {
        return new SmartHomeActionPacket(panel, ACTION_ROOM_FOLLOW, panel, (roomId << 1) | (follow ? 1 : 0));
    }

    public static SmartHomeActionPacket roomIcon(BlockPos panel, int roomId, boolean back) {
        return new SmartHomeActionPacket(panel, ACTION_ROOM_ICON, panel, (roomId << 1) | (back ? 1 : 0));
    }

    public static SmartHomeActionPacket sceneRecall(BlockPos panel, int slot) {
        return new SmartHomeActionPacket(panel, ACTION_SCENE_RECALL, panel, slot);
    }

    public static SmartHomeActionPacket sceneSave(BlockPos panel, int slot, int roomId, boolean lightsOnly) {
        return new SmartHomeActionPacket(panel, ACTION_SCENE_SAVE, panel,
                (slot & 15) | (lightsOnly ? 16 : 0) | ((roomId + 1) << 5));
    }

    public static SmartHomeActionPacket sceneDelete(BlockPos panel, int slot) {
        return new SmartHomeActionPacket(panel, ACTION_SCENE_DELETE, panel, slot);
    }

    public static SmartHomeActionPacket sceneIcon(BlockPos panel, int slot, boolean back) {
        return new SmartHomeActionPacket(panel, ACTION_SCENE_ICON, panel, (slot << 1) | (back ? 1 : 0));
    }

    public static SmartHomeActionPacket rule(BlockPos panel, HouseModel.Rule rule, int slot) {
        return new SmartHomeActionPacket(panel, ACTION_RULE, panel, (rule.ordinal() << 4) | ((slot + 1) & 15));
    }

    public static SmartHomeActionPacket toasts(BlockPos panel, boolean on) {
        return new SmartHomeActionPacket(panel, ACTION_TOASTS, panel, on ? 1 : 0);
    }

    public static SmartHomeActionPacket kindAll(BlockPos panel, int category, boolean off) {
        return new SmartHomeActionPacket(panel, ACTION_KIND_ALL, panel, (category << 1) | (off ? 1 : 0));
    }

    public static SmartHomeActionPacket roomLights(BlockPos panel, int roomId, boolean off) {
        return new SmartHomeActionPacket(panel, ACTION_ROOM_LIGHTS, panel, ((roomId + 2) << 1) | (off ? 1 : 0));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SmartHomeActionPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            final BlockPos at = packet.panelPos();

            if (!player.level().isLoaded(at)
                    || player.distanceToSqr(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5)
                            > 64.0) {
                return;
            }
            if (!(player.level().getBlockEntity(at) instanceof SmartHomePanelBE panel)) {
                return;
            }
            final int v = packet.value();
            int known = -1;
            boolean wiring = false;
            switch (packet.action()) {
                case ACTION_GATE -> panel.setRowGated(packet.target(), v != 0);
                case ACTION_MODE -> panel.setRowMode(packet.target(), v);

                case ACTION_ALL -> panel.setRoomGated(-1, (v & 1) != 0);
                case ACTION_UNLINK -> panel.toggleLinkIfLinked(packet.target());
                case ACTION_FACE -> panel.setHiFiFace(v != 0);
                case ACTION_COLOUR -> panel.setRowColour(packet.target(), v);
                case ACTION_ROOM_ALL -> panel.setRoomGated((v >> 1) - 2, (v & 1) != 0);
                case ACTION_STANDBY_OFF -> panel.switchOffStandby();
                case ACTION_CLAIM -> {
                    final int[] foot = panel.footprint();
                    final BlockPos f = packet.target();
                    if (f.getX() < foot[0] - 32 || f.getX() > foot[2] + 32
                            || f.getZ() < foot[1] - 32 || f.getZ() > foot[3] + 32
                            || !player.level().isLoaded(f)) {
                        break;
                    }
                    final String refusal = panel.claimRoom(f);
                    if (refusal != null) {
                        player.displayClientMessage(Component.translatable(refusal), true);
                    }
                }
                case ACTION_ROOM_DELETE -> panel.deleteRoom(v);
                case ACTION_ROOM_FOLLOW -> {
                    panel.house().setFollowPeople(v >> 1, (v & 1) != 0);
                    panel.setChanged();
                }
                case ACTION_ROOM_ICON -> {
                    panel.house().cycleRoomIcon(v >> 1, (v & 1) != 0 ? -1 : 1);
                    panel.setChanged();
                }
                case ACTION_SCENE_RECALL -> panel.recallScene(v);
                case ACTION_SCENE_SAVE -> panel.saveScene(v & 15, (v >> 5) - 1, (v & 16) != 0);
                case ACTION_SCENE_DELETE -> panel.deleteScene(v);
                case ACTION_SCENE_ICON -> {
                    panel.house().cycleSceneIcon(v >> 1, (v & 1) != 0 ? -1 : 1);
                    panel.setChanged();
                }
                case ACTION_RULE -> {
                    final int ordinal = v >> 4;
                    if (ordinal >= 0 && ordinal < HouseModel.Rule.values().length) {
                        panel.house().setRule(HouseModel.Rule.values()[ordinal], (v & 15) - 1);
                        panel.setChanged();
                    }
                }
                case ACTION_TOASTS -> {
                    panel.house().setToasts(v != 0);
                    panel.setChanged();
                }
                case ACTION_KIND_ALL -> panel.setKindGated(v >> 1, (v & 1) != 0);
                case ACTION_ROOM_LIGHTS -> panel.setLights((v >> 1) - 2, (v & 1) == 0);
                default -> {
                    known = v >> 1;
                    wiring = (v & 1) != 0;
                    panel.rebuildIfStale();
                }
            }

            PacketDistributor.sendToPlayer(player, SmartHomeSyncPacket.of(panel, known, wiring));
        });
    }
}
