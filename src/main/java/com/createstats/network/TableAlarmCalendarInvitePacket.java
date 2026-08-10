package com.createstats.network;

import com.createstats.alarm.TableAlarmCalendarEvent;
import com.createstats.alarm.TableAlarmCalendarSavedData;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class TableAlarmCalendarInvitePacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TableAlarmCalendarInvitePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "table_alarm_calendar_invite"));
    public static final StreamCodec<FriendlyByteBuf, TableAlarmCalendarInvitePacket> STREAM_CODEC =
        StreamCodec.of((buf, pkt) -> pkt.encode(buf), TableAlarmCalendarInvitePacket::new);

    private final TableAlarmCalendarEvent event;
    private final String targetName;

    public TableAlarmCalendarInvitePacket(TableAlarmCalendarEvent event, String targetName) {
        this.event = event;
        this.targetName = sanitizeTargetName(targetName);
    }

    public TableAlarmCalendarInvitePacket(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        TableAlarmCalendarEvent loadedEvent = tag == null ? null : TableAlarmCalendarEvent.load(tag.getCompound("Event"));
        this.event = loadedEvent == null ? new TableAlarmCalendarEvent(UUID.randomUUID(), null, 0, "Event") : loadedEvent;
        this.targetName = tag == null ? "" : sanitizeTargetName(tag.getString("TargetName"));
    }

    public void encode(FriendlyByteBuf buf) {
        CompoundTag tag = new CompoundTag();
        tag.put("Event", this.event.save());
        tag.putString("TargetName", this.targetName);
        buf.writeNbt(tag);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TableAlarmCalendarInvitePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) ctx.player();
            if (sender == null || sender.getServer() == null || packet.targetName.isBlank()) {
                return;
            }
            MinecraftServer server = sender.getServer();
            List<TargetPlayer> targets = resolveTargets(server, packet.targetName, sender);
            if (targets.isEmpty()) {
                sender.displayClientMessage(Component.translatable("message.create_stats.table_alarm.invite_target_not_found", packet.targetName), false);
                return;
            }
            ServerLevel storageLevel = server.overworld();
            TableAlarmCalendarSavedData data = TableAlarmCalendarSavedData.get(storageLevel);
            TableAlarmCalendarEvent inviteEvent = packet.event.asInvitationFrom(sender.getGameProfile().getName());
            for (TargetPlayer target : targets) {
                data.addEvent(target.id(), inviteEvent);
                if (target.onlinePlayer() != null) {
                    PacketDistributor.sendToPlayer(target.onlinePlayer(), new TableAlarmCalendarDataPacket(data.getEvents(target.id())));
                }
            }
            sender.displayClientMessage(Component.translatable("message.create_stats.table_alarm.invite_sent", targetLabel(targets)), false);
        });
    }

    private static List<TargetPlayer> resolveTargets(MinecraftServer server, String targetName, ServerPlayer sender) {
        if ("@a".equalsIgnoreCase(targetName)) {
            List<TargetPlayer> targets = new ArrayList<>();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!player.getUUID().equals(sender.getUUID())) {
                    targets.add(new TargetPlayer(player.getUUID(), player.getGameProfile().getName(), player));
                }
            }
            return targets;
        }
        TargetPlayer target = resolveTarget(server, targetName);
        return target == null ? List.of() : List.of(target);
    }

    private static TargetPlayer resolveTarget(MinecraftServer server, String targetName) {
        String normalizedTarget = targetName.toLowerCase(Locale.ROOT);
        List<ServerPlayer> onlinePlayers = server.getPlayerList().getPlayers();
        for (ServerPlayer player : onlinePlayers) {
            if (player.getGameProfile().getName().toLowerCase(Locale.ROOT).equals(normalizedTarget)) {
                return new TargetPlayer(player.getUUID(), player.getGameProfile().getName(), player);
            }
        }
        if (server.getProfileCache() != null) {
            Optional<GameProfile> cachedProfile = server.getProfileCache().get(targetName);
            if (cachedProfile.isPresent()) {
                GameProfile profile = cachedProfile.get();
                if (profile.getId() == null) {
                    return null;
                }
                return new TargetPlayer(profile.getId(), profile.getName(), null);
            }
        }
        return null;
    }

    private static Component targetLabel(List<TargetPlayer> targets) {
        if (targets.size() == 1) {
            return Component.literal(targets.get(0).name());
        }
        return Component.translatable("message.create_stats.table_alarm.invite_target_count", targets.size());
    }

    private static String sanitizeTargetName(String targetName) {
        String cleaned = targetName == null ? "" : targetName.trim();
        return cleaned.length() > 32 ? cleaned.substring(0, 32) : cleaned;
    }

    private record TargetPlayer(UUID id, String name, ServerPlayer onlinePlayer) {
    }
}