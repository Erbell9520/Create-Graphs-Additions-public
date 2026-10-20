package com.createstats.battle.net;

import com.createstats.CreateStats;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record BattleWatchPacket(String key, List<ContactLine> contacts, List<GunDetail> guns) implements CustomPacketPayload {
    public static final int MAX_CONTACTS = 24;
    public static final int MAX_GUNS = 32;

    public record ContactLine(int kind, int entityId, String shipId, String name, float range, float bearing,
                              float elevation, float closing, boolean hostile, boolean targetsCrew, float radius, int band) {
    }

    public record GunDetail(int id, int mount, String yawWhere, String pitchWhere, int yawShare, int pitchShare, int aim,
                            float flight, float lead, float error, float miss, float speed, int yawRpm, int pitchRpm,
                            float yawGain, float pitchGain, float reach) {
    }

    public static final Type<BattleWatchPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "battle_watch"));

    public static final StreamCodec<FriendlyByteBuf, BattleWatchPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUtf(p.key(), 128);
                int nc = Math.min(MAX_CONTACTS, p.contacts().size());
                buf.writeVarInt(nc);
                for (int i = 0; i < nc; i++) {
                    ContactLine c = p.contacts().get(i);
                    buf.writeByte(c.kind());
                    buf.writeVarInt(c.entityId());
                    buf.writeUtf(c.shipId(), 40);
                    buf.writeUtf(c.name(), 64);
                    buf.writeFloat(c.range());
                    buf.writeFloat(c.bearing());
                    buf.writeFloat(c.elevation());
                    buf.writeFloat(c.closing());
                    buf.writeBoolean(c.hostile());
                    buf.writeBoolean(c.targetsCrew());
                    buf.writeFloat(c.radius());
                    buf.writeByte(c.band());
                }
                int ng = Math.min(MAX_GUNS, p.guns().size());
                buf.writeVarInt(ng);
                for (int i = 0; i < ng; i++) {
                    GunDetail g = p.guns().get(i);
                    buf.writeVarInt(g.id());
                    buf.writeByte(g.mount());
                    buf.writeUtf(g.yawWhere(), 64);
                    buf.writeUtf(g.pitchWhere(), 64);
                    buf.writeVarInt(g.yawShare());
                    buf.writeVarInt(g.pitchShare());
                    buf.writeByte(g.aim());
                    buf.writeFloat(g.flight());
                    buf.writeFloat(g.lead());
                    buf.writeFloat(g.error());
                    buf.writeFloat(g.miss());
                    buf.writeFloat(g.speed());
                    buf.writeVarInt(g.yawRpm());
                    buf.writeVarInt(g.pitchRpm());
                    buf.writeFloat(g.yawGain());
                    buf.writeFloat(g.pitchGain());
                    buf.writeFloat(g.reach());
                }
            },
            buf -> {
                String key = buf.readUtf(128);
                int nc = Math.min(MAX_CONTACTS, buf.readVarInt());
                List<ContactLine> contacts = new ArrayList<>(nc);
                for (int i = 0; i < nc; i++) {
                    contacts.add(new ContactLine(buf.readByte(), buf.readVarInt(), buf.readUtf(40), buf.readUtf(64),
                            buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean(),
                            buf.readBoolean(), buf.readFloat(), buf.readByte()));
                }
                int ng = Math.min(MAX_GUNS, buf.readVarInt());
                List<GunDetail> guns = new ArrayList<>(ng);
                for (int i = 0; i < ng; i++) {
                    guns.add(new GunDetail(buf.readVarInt(), buf.readByte(), buf.readUtf(64), buf.readUtf(64),
                            buf.readVarInt(), buf.readVarInt(), buf.readByte(), buf.readFloat(), buf.readFloat(),
                            buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(),
                            buf.readFloat(), buf.readFloat(), buf.readFloat()));
                }
                return new BattleWatchPacket(key, contacts, guns);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BattleWatchPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> BattleClientBridge.watch(packet));
    }
}
