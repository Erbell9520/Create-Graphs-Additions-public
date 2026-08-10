package com.createstats.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.createstats.blockentity.FreightManifestClipboardBE;
import com.createstats.train.manifest.FreightManifest;
import com.createstats.train.manifest.FreightManifestLine;
import com.createstats.train.manifest.FreightManifestSavedData;
import com.createstats.train.manifest.FreightManifestValidationResult;
import com.createstats.train.manifest.FreightManifestLogic;
import com.createstats.train.manifest.FreightStationLink;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class SaveFreightManifestPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SaveFreightManifestPacket> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "save_freight_manifest"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, SaveFreightManifestPacket> STREAM_CODEC = 
        StreamCodec.of((buf, pkt) -> pkt.encode(buf), SaveFreightManifestPacket::new);

    private final BlockPos pos;
    private final int expectedVersion;
    private final List<FreightManifestLine> lines;

    public SaveFreightManifestPacket(BlockPos pos, int expectedVersion, List<FreightManifestLine> lines) {
        this.pos = pos;
        this.expectedVersion = expectedVersion;
        this.lines = new ArrayList<>();
        for (FreightManifestLine line : lines) {
            this.lines.add(line.copy());
        }
    }

    public SaveFreightManifestPacket(RegistryFriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.expectedVersion = buf.readVarInt();
        int size = buf.readVarInt();
        if (size > FreightManifestLogic.MAX_LINES) {
            throw new IllegalArgumentException(
                "Freight manifest line count " + size + " exceeds maximum of " + FreightManifestLogic.MAX_LINES);
        }
        this.lines = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            this.lines.add(FreightManifestLine.decode(buf));
        }
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeVarInt(this.expectedVersion);
        buf.writeVarInt(this.lines.size());
        for (FreightManifestLine line : this.lines) {
            line.encode(buf);
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SaveFreightManifestPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) {
                return;
            }
            if (player.distanceToSqr(packet.pos.getX() + 0.5, packet.pos.getY() + 0.5, packet.pos.getZ() + 0.5) > 64.0) {
                return;
            }
            BlockEntity be = player.level().getBlockEntity(packet.pos);
            if (!(be instanceof FreightManifestClipboardBE clipboard)) {
                return;
            }

            FreightStationLink link = clipboard.getStationLink();
            if (!link.isLinked() || link.getStationDimension() == null || link.getStationId() == null) {
                player.displayClientMessage(Component.translatable("message.create_stats.freight_manifest.not_linked").withStyle(ChatFormatting.RED), false);
                return;
            }

            ServerLevel stationLevel = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, link.getStationDimension()));
            if (stationLevel == null) {
                player.displayClientMessage(Component.translatable("message.create_stats.freight_manifest.dimension_unloaded").withStyle(ChatFormatting.RED), false);
                return;
            }

            FreightManifestValidationResult validation = FreightManifestLogic.validate(packet.lines);
            if (!validation.isValid()) {
                player.displayClientMessage(Component.literal(validation.getMessage()).withStyle(ChatFormatting.RED), false);
                return;
            }

            UUID stationId = link.getStationId();
            FreightManifestSavedData savedData = FreightManifestSavedData.get(stationLevel);
            FreightManifest current = savedData.getManifest(stationId);
            if (current == null) {
                current = new FreightManifest();
            }

            if (current.getOwnerId() != null && !current.getOwnerId().equals(player.getUUID()) && !player.hasPermissions(2)) {
                player.displayClientMessage(Component.translatable("message.create_stats.freight_manifest.no_permission").withStyle(ChatFormatting.RED), false);
                return;
            }

            if (current.getVersion() != packet.expectedVersion) {
                player.displayClientMessage(Component.translatable("message.create_stats.freight_manifest.version_conflict")
                    .withStyle(ChatFormatting.RED), false);
                return;
            }

            if (current.getOwnerId() == null) {
                current.setOwnerId(player.getUUID());
            }
            current.setLines(packet.lines);
            current.bumpVersion();
            savedData.putManifest(stationId, current, stationLevel.getGameTime());
            player.displayClientMessage(Component.translatable("message.create_stats.freight_manifest.saved").withStyle(ChatFormatting.GOLD), false);
        });
    }
}
