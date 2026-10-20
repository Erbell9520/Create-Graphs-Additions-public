package com.createstats.vccmk3.network;

import com.createstats.CreateStats;
import com.createstats.vccmk3.avionics.AvionicsChannel;
import com.createstats.vccmk3.avionics.AvionicsState;
import com.createstats.vccmk3.avionics.SensorSource;
import com.createstats.vccmk3.client.VccMk3HudState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// small and frequent, separate from the big snapshot
public record VccMk3HudPacket(
        BlockPos controllerPos,
        String craftName,
        boolean armed,
        boolean autoEngaged,
        byte alertLevel,
        String alertCode,
        String alertMessage,
        float altitude, byte altitudeSource,
        float airspeed, byte airspeedSource,
        float groundSpeed, byte groundSpeedSource,
        float climbRate, byte climbSource,
        float headingDeg, byte headingSource,
        float pitchDeg, float rollDeg, byte attitudeSource,
        float terrainClearance, byte terrainSource
) implements CustomPacketPayload {
    public static final Type<VccMk3HudPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "vcc_mk3_hud"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VccMk3HudPacket> STREAM_CODEC =
            StreamCodec.of((buf, p) -> {
                buf.writeBlockPos(p.controllerPos);
                buf.writeUtf(p.craftName, 64);
                buf.writeBoolean(p.armed);
                buf.writeBoolean(p.autoEngaged);
                buf.writeByte(p.alertLevel);
                buf.writeUtf(p.alertCode, 8);
                buf.writeUtf(p.alertMessage, 128);
                buf.writeFloat(p.altitude);
                buf.writeByte(p.altitudeSource);
                buf.writeFloat(p.airspeed);
                buf.writeByte(p.airspeedSource);
                buf.writeFloat(p.groundSpeed);
                buf.writeByte(p.groundSpeedSource);
                buf.writeFloat(p.climbRate);
                buf.writeByte(p.climbSource);
                buf.writeFloat(p.headingDeg);
                buf.writeByte(p.headingSource);
                buf.writeFloat(p.pitchDeg);
                buf.writeFloat(p.rollDeg);
                buf.writeByte(p.attitudeSource);
                buf.writeFloat(p.terrainClearance);
                buf.writeByte(p.terrainSource);
            }, buf -> new VccMk3HudPacket(
                    buf.readBlockPos(), buf.readUtf(64), buf.readBoolean(), buf.readBoolean(),
                    buf.readByte(), buf.readUtf(8), buf.readUtf(128),
                    buf.readFloat(), buf.readByte(),
                    buf.readFloat(), buf.readByte(),
                    buf.readFloat(), buf.readByte(),
                    buf.readFloat(), buf.readByte(),
                    buf.readFloat(), buf.readByte(),
                    buf.readFloat(), buf.readFloat(), buf.readByte(),
                    buf.readFloat(), buf.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static VccMk3HudPacket of(BlockPos pos, String craftName, boolean armed, boolean autoEngaged,
                                     int alertLevel, String alertCode, String alertMessage,
                                     AvionicsState state) {
        return new VccMk3HudPacket(pos, craftName, armed, autoEngaged,
                (byte) alertLevel, alertCode, alertMessage,
                value(state.altitude()), source(state.altitude()),
                value(state.airspeed()), source(state.airspeed()),
                value(state.groundSpeed()), source(state.groundSpeed()),
                value(state.climbRate()), source(state.climbRate()),
                value(state.headingDeg()), source(state.headingDeg()),
                value(state.pitchDeg()), value(state.rollDeg()), source(state.pitchDeg()),
                value(state.terrainClearance()), source(state.terrainClearance()));
    }

    private static float value(AvionicsChannel channel) {
        return channel.present() ? (float) channel.value() : Float.NaN;
    }

    private static byte source(AvionicsChannel channel) {
        return (byte) channel.source().ordinal();
    }

    public static AvionicsChannel channel(float value, byte sourceOrdinal) {
        SensorSource[] all = SensorSource.values();
        SensorSource source = (sourceOrdinal >= 0 && sourceOrdinal < all.length)
                ? all[sourceOrdinal] : SensorSource.NONE;
        if (source == SensorSource.NONE || Float.isNaN(value)) return AvionicsChannel.NONE;
        return new AvionicsChannel(value, source);
    }

    public static void handle(VccMk3HudPacket packet, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        context.enqueueWork(() -> VccMk3HudState.accept(packet));
    }
}
