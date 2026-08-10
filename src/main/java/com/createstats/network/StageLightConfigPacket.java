package com.createstats.network;

import com.createstats.blockentity.StageLightBE;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.createstats.block.StageLightBlock;

/** [1] */
public class StageLightConfigPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StageLightConfigPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "stage_light_config"));

    public static final StreamCodec<FriendlyByteBuf, StageLightConfigPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> pkt.encode(buf), StageLightConfigPacket::new);

    private final BlockPos pos;
    private final int pan;
    private final int tilt;
    private final int range;
    private final int brightness;
    private final int focus;
    private final int tint;
    private final boolean beamVisible;
    private final boolean volumetric;
    private final boolean lit;
    private final int beamWidth;
    private final int lightSpread;
    private final int effect;
    private final int effectSpeed;

    public StageLightConfigPacket(BlockPos pos, int pan, int tilt, int range, int brightness, int focus, int tint,
                                  boolean beamVisible, boolean volumetric, boolean lit, int beamWidth, int lightSpread,
                                  int effect, int effectSpeed) {
        this.pos = pos;
        this.pan = pan;
        this.tilt = tilt;
        this.range = range;
        this.brightness = brightness;
        this.focus = focus;
        this.tint = tint;
        this.beamVisible = beamVisible;
        this.volumetric = volumetric;
        this.lit = lit;
        this.beamWidth = beamWidth;
        this.lightSpread = lightSpread;
        this.effect = effect;
        this.effectSpeed = effectSpeed;
    }

    public StageLightConfigPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.pan = buf.readInt();
        this.tilt = buf.readInt();
        this.range = buf.readInt();
        this.brightness = buf.readInt();
        this.focus = buf.readInt();
        this.tint = buf.readInt();
        this.beamVisible = buf.readBoolean();
        this.volumetric = buf.readBoolean();
        this.lit = buf.readBoolean();
        this.beamWidth = buf.readInt();
        this.lightSpread = buf.readInt();
        this.effect = buf.readInt();
        this.effectSpeed = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeInt(this.pan);
        buf.writeInt(this.tilt);
        buf.writeInt(this.range);
        buf.writeInt(this.brightness);
        buf.writeInt(this.focus);
        buf.writeInt(this.tint);
        buf.writeBoolean(this.beamVisible);
        buf.writeBoolean(this.volumetric);
        buf.writeBoolean(this.lit);
        buf.writeInt(this.beamWidth);
        buf.writeInt(this.lightSpread);
        buf.writeInt(this.effect);
        buf.writeInt(this.effectSpeed);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StageLightConfigPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            final Level level = player.level();
            if (!level.isLoaded(packet.pos)) {
                return;
            }
            if (player.distanceToSqr(packet.pos.getX() + 0.5, packet.pos.getY() + 0.5, packet.pos.getZ() + 0.5) > 4096.0) {
                return;
            }
            final BlockEntity be = level.getBlockEntity(packet.pos);
            if (!(be instanceof StageLightBE stageLight)) {
                return;
            }
            stageLight.applyConfig(packet.pan, packet.tilt, packet.range, packet.brightness, packet.focus, packet.tint,
                    packet.beamVisible, packet.volumetric, packet.beamWidth, packet.lightSpread,
                    packet.effect, packet.effectSpeed);
            final BlockState state = level.getBlockState(packet.pos);
            if (state.hasProperty(StageLightBlock.LIT) && state.getValue(StageLightBlock.LIT) != packet.lit) {
                level.setBlock(packet.pos, state.setValue(StageLightBlock.LIT, packet.lit), Block.UPDATE_ALL);
            }
        });
    }
}
