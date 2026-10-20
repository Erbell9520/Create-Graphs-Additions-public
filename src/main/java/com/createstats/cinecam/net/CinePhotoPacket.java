package com.createstats.cinecam.net;

import com.createstats.CreateStats;
import com.createstats.cinecam.CineCameraBE;
import com.createstats.cinecam.CinePhotos;
import com.createstats.cinecam.core.MapPalette;
import com.createstats.cinecam.core.PhotoPrint;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CinePhotoPacket(BlockPos pos, int job, int index, int side, byte[] colours) implements CustomPacketPayload {
    public static final Type<CinePhotoPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "cine_photo"));

    public static final StreamCodec<FriendlyByteBuf, CinePhotoPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.pos());
                buf.writeVarInt(p.job());
                buf.writeVarInt(p.index());
                buf.writeVarInt(p.side());
                buf.writeByteArray(p.colours());
            },
            buf -> new CinePhotoPacket(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readByteArray(MapPalette.SIZE * MapPalette.SIZE)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CinePhotoPacket p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (p.colours().length != MapPalette.SIZE * MapPalette.SIZE) return;
            if (p.side() < PhotoPrint.MIN_SIDE || p.side() > PhotoPrint.MAX_SIDE) return;
            if (p.index() < 0 || p.index() >= PhotoPrint.mapsFor(p.side())) return;
            ServerLevel level = player.serverLevel();
            if (!level.isLoaded(p.pos()) || !(level.getBlockEntity(p.pos()) instanceof CineCameraBE cam)) return;
            if (!CineControlPacket.near(player, cam)) return;
            CinePhotos.receive(level, player, cam, p.job(), p.index(), p.side(), p.colours());
        });
    }
}
