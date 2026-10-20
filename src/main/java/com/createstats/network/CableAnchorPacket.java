package com.createstats.network;

import com.createstats.power.CablePlacement;
import com.createstats.power.CableRoute;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

// the anchor map is server side only. singleplayer shares the jvm so nobody noticed until multiplayer
public class CableAnchorPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CableAnchorPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "cable_anchor"));
    public static final StreamCodec<FriendlyByteBuf, CableAnchorPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> pkt.encode(buf), CableAnchorPacket::new);

    @Nullable
    private final CableRoute.Step anchor;

    public CableAnchorPacket(@Nullable CableRoute.Step anchor) {
        this.anchor = anchor;
    }

    public CableAnchorPacket(FriendlyByteBuf buf) {
        this.anchor = buf.readBoolean()
                ? new CableRoute.Step(buf.readBlockPos(), buf.readEnum(Direction.class))
                : null;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.anchor != null);
        if (this.anchor != null) {
            buf.writeBlockPos(this.anchor.pos());
            buf.writeEnum(this.anchor.face());
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CableAnchorPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() != null) {
                CablePlacement.acceptFromServer(ctx.player().getUUID(), packet.anchor);
            }
        });
    }
}
