package com.createstats.network;

import com.createstats.alarm.annunciator.AnnunciatorSettings;
import com.createstats.alarm.annunciator.WatchRule;
import com.createstats.blockentity.AnnunciatorBE;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public class AnnunciatorConfigPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AnnunciatorConfigPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("create_stats", "annunciator_config"));

    public static final StreamCodec<FriendlyByteBuf, AnnunciatorConfigPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> packet.encode(buf), AnnunciatorConfigPacket::new);

    private final BlockPos pos;
    private final AnnunciatorSettings settings;
    private final List<WatchRule> rules;

    public AnnunciatorConfigPacket(BlockPos pos, AnnunciatorSettings settings, List<WatchRule> rules) {
        this.pos = pos;
        this.settings = settings;
        this.rules = rules;
    }

    public AnnunciatorConfigPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.settings = AnnunciatorSettings.decode(buf);
        final CompoundTag tag = buf.readNbt();
        this.rules = new ArrayList<>();
        if (tag != null) {
            final ListTag list = tag.getList("Rules", Tag.TAG_COMPOUND);
            // cap on the way in too, clients aren't trusted

            for (int i = 0; i < list.size() && i < WatchRule.MAX_RULES; i++) {
                this.rules.add(WatchRule.load(list.getCompound(i)));
            }
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        this.settings.encode(buf);
        final CompoundTag tag = new CompoundTag();
        final ListTag list = new ListTag();
        for (int i = 0; i < this.rules.size() && i < WatchRule.MAX_RULES; i++) {
            list.add(this.rules.get(i).save());
        }
        tag.put("Rules", list);
        buf.writeNbt(tag);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AnnunciatorConfigPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            final ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) {
                return;
            }
            if (player.distanceToSqr(packet.pos.getX() + 0.5, packet.pos.getY() + 0.5,
                    packet.pos.getZ() + 0.5) > 64.0) {
                return;
            }
            final BlockEntity be = player.level().getBlockEntity(packet.pos);
            if (be instanceof AnnunciatorBE annunciator) {
                annunciator.setSettings(packet.settings);
                annunciator.setRules(packet.rules);
            }
        });
    }
}
