package com.createstats.cinecam.net;

import com.createstats.CreateStats;
import com.createstats.cinecam.CineCameraBE;
import com.createstats.flight.bridge.SableBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record CineControlPacket(BlockPos pos, int op, float a, float b, int kind, UUID id) implements CustomPacketPayload {
    public static final int AIM = 0;
    public static final int LOCK = 1;
    public static final int RELEASE = 2;
    public static final int MODE = 3;
    public static final int MARK = 4;
    public static final int IRIS = 5;
    public static final int RECORD = 6;
    public static final int FORMAT = 7;
    public static final int PHOTO = 8;

    public static final int ZOOM = 10;

    public static final int SOUND = 11;

    private static final UUID NONE = new UUID(0L, 0L);

    public static final Type<CineControlPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "cine_control"));

    public static final StreamCodec<FriendlyByteBuf, CineControlPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.pos());
                buf.writeVarInt(p.op());
                buf.writeFloat(p.a());
                buf.writeFloat(p.b());
                buf.writeVarInt(p.kind());
                buf.writeUUID(p.id());
            },
            buf -> new CineControlPacket(buf.readBlockPos(), buf.readVarInt(), buf.readFloat(), buf.readFloat(),
                    buf.readVarInt(), buf.readUUID()));

    public static CineControlPacket of(BlockPos pos, int op, float a, float b) {
        return new CineControlPacket(pos, op, a, b, 0, NONE);
    }

    public static CineControlPacket lock(BlockPos pos, int kind, UUID id) {
        return new CineControlPacket(pos, LOCK, 0f, 0f, kind, id);
    }

    public static CineControlPacket format(BlockPos pos, int packed) {
        return new CineControlPacket(pos, FORMAT, 0f, 0f, packed, NONE);
    }

    public static CineControlPacket photo(BlockPos pos, int packed) {
        return new CineControlPacket(pos, PHOTO, 0f, 0f, packed, NONE);
    }

    public static CineControlPacket setting(BlockPos pos, int op, int value) {
        return new CineControlPacket(pos, op, 0f, 0f, value, NONE);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static boolean near(ServerPlayer player, CineCameraBE cam) {
        Vec3 w = cam.trunnionWorld();
        if (w != null && player.getEyePosition().distanceTo(w) <= CineCameraBE.REACH) return true;
        return SableBridge.isPlayerInInteractionRangeFor(cam, player);
    }

    public static void handle(CineControlPacket p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();
            if (!level.isLoaded(p.pos()) || !(level.getBlockEntity(p.pos()) instanceof CineCameraBE cam)) return;
            if (!near(player, cam)) return;
            switch (p.op()) {
                case AIM -> {
                    if (Float.isFinite(p.a()) && Float.isFinite(p.b())) cam.handAim(p.a(), p.b());
                }
                case LOCK -> lock(level, cam, p.kind(), p.id());
                case RELEASE -> cam.release();
                case MODE -> cam.setMode((int) p.a());
                case MARK -> cam.mark();
                case IRIS -> cam.setTStop(p.a());
                case RECORD -> cam.setRecording(p.a() > 0.5f, player);
                case FORMAT -> cam.setFilmFormat(p.kind());
                case PHOTO -> cam.setPhotoFormat(p.kind());
                case ZOOM -> cam.setAutoZoom(p.kind());
                case SOUND -> cam.setFilmSound(p.kind() != 0);
                default -> {
                }
            }
        });
    }

    private static void lock(ServerLevel level, CineCameraBE cam, int kind, UUID id) {
        Vec3 lens = cam.trunnionWorld();
        if (lens == null) return;
        if (kind == CineCameraBE.KIND_ENTITY) {
            Entity e = level.getEntity(id);
            if (e == null || e.isRemoved() || e.position().distanceTo(lens) > CineCameraBE.MAX_RANGE) return;
            cam.lock(CineCameraBE.KIND_ENTITY, id, e.getName().getString());
        } else if (kind == CineCameraBE.KIND_SHIP) {
            Object sub = SableBridge.findSubLevelById(level, id);
            if (sub == null) return;
            org.joml.Vector3d c = SableBridge.subLevelWorldCenter(sub);
            if (c == null || c.distance(lens.x, lens.y, lens.z) > CineCameraBE.MAX_RANGE) return;
            String name = SableBridge.subLevelName(sub);
            cam.lock(CineCameraBE.KIND_SHIP, id, name == null ? "" : name);
        }
    }
}
