package com.createstats.network;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.level.ServerPlayer;
import com.createstats.blockentity.StatsDisplayPanelBE;
import net.minecraft.network.FriendlyByteBuf;
import java.util.UUID;
import com.createstats.data.Annotation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class AnnotationActionPacket implements CustomPacketPayload
{
    private final BlockPos pos;
    private final Action action;
    private long tickStamp;
    private long endTickStamp;
    private String label;
    private int color;
    private Annotation.AnnotationType annotationType;
    private UUID annotationId;
    private String newLabel;
    private int newColor;
    
    public static final CustomPacketPayload.Type<AnnotationActionPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "annotation_action"));
    public static final StreamCodec<FriendlyByteBuf, AnnotationActionPacket> STREAM_CODEC = StreamCodec.of((buf, pkt) -> pkt.encode(buf), AnnotationActionPacket::new);
    
    private AnnotationActionPacket(BlockPos pos, Action action) {
        this.pos = pos;
        this.action = action;
    }
    
    public static AnnotationActionPacket add(BlockPos pos, long tick, String label, int color) {
        final AnnotationActionPacket pkt = new AnnotationActionPacket(pos, Action.ADD);
        pkt.tickStamp = tick;
        pkt.endTickStamp = tick;
        pkt.label = label;
        pkt.color = color;
        pkt.annotationType = Annotation.AnnotationType.MARKER;
        return pkt;
    }
    
    public static AnnotationActionPacket addRegion(BlockPos pos, long startTick, long endTick, String label, int color) {
        final AnnotationActionPacket pkt = new AnnotationActionPacket(pos, Action.ADD);
        pkt.tickStamp = startTick;
        pkt.endTickStamp = endTick;
        pkt.label = label;
        pkt.color = color;
        pkt.annotationType = Annotation.AnnotationType.REGION;
        return pkt;
    }
    
    public static AnnotationActionPacket remove(BlockPos pos, UUID annotationId) {
        final AnnotationActionPacket pkt = new AnnotationActionPacket(pos, Action.REMOVE);
        pkt.annotationId = annotationId;
        return pkt;
    }
    
    public static AnnotationActionPacket edit(BlockPos pos, UUID annotationId, String newLabel, int newColor) {
        final AnnotationActionPacket pkt = new AnnotationActionPacket(pos, Action.EDIT);
        pkt.annotationId = annotationId;
        pkt.newLabel = newLabel;
        pkt.newColor = newColor;
        return pkt;
    }
    
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeEnum((Enum)this.action);
        switch (this.action) {
            case ADD: {
                buf.writeLong(this.tickStamp);
                buf.writeLong(this.endTickStamp);
                buf.writeUtf(this.label, 48);
                buf.writeInt(this.color);
                buf.writeEnum((Enum)this.annotationType);
                break;
            }
            case REMOVE: {
                buf.writeUUID(this.annotationId);
                break;
            }
            case EDIT: {
                buf.writeUUID(this.annotationId);
                buf.writeUtf(this.newLabel, 48);
                buf.writeInt(this.newColor);
                break;
            }
        }
    }
    
    public AnnotationActionPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.action = (Action)buf.readEnum((Class)Action.class);
        switch (this.action) {
            case ADD: {
                this.tickStamp = buf.readLong();
                this.endTickStamp = buf.readLong();
                this.label = buf.readUtf(48);
                this.color = buf.readInt();
                this.annotationType = (Annotation.AnnotationType)buf.readEnum((Class)Annotation.AnnotationType.class);
                break;
            }
            case REMOVE: {
                this.annotationId = buf.readUUID();
                break;
            }
            case EDIT: {
                this.annotationId = buf.readUUID();
                this.newLabel = buf.readUtf(48);
                this.newColor = buf.readInt();
                break;
            }
        }
    }
    
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    public static void handle(AnnotationActionPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            final ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) {
                return;
            }
            else if (player.distanceToSqr(packet.pos.getX() + 0.5, packet.pos.getY() + 0.5, packet.pos.getZ() + 0.5) > 64.0) {
                return;
            }
            else {
                final BlockEntity be = player.level().getBlockEntity(packet.pos);
                if (be instanceof StatsDisplayPanelBE panel) {
                    switch (packet.action) {
                        case ADD: {
                            final String trimmed = packet.label.trim();
                            if (trimmed.isEmpty() || trimmed.length() > 48) {
                                return;
                            }
                            else if (packet.tickStamp < 0L) {
                                return;
                            }
                            else {
                                final long currentTick = player.level().getGameTime();
                                if (packet.tickStamp > currentTick + 20L) {
                                    return;
                                }
                                else if (packet.endTickStamp < packet.tickStamp) {
                                    return;
                                }
                                else {
                                    final Annotation annotation = new Annotation(UUID.randomUUID(), packet.tickStamp, packet.endTickStamp, trimmed, player.getUUID(), packet.color | 0xFF000000, packet.annotationType);
                                    final boolean added = panel.getAnnotationManager().add(annotation);
                                    if (added) {
                                        panel.setChanged();
                                        player.level().sendBlockUpdated(packet.pos, panel.getBlockState(), panel.getBlockState(), 3);
                                    }
                                    break;
                                }
                            }
                        }
                        case REMOVE: {
                            final Annotation existing = panel.getAnnotationManager().getById(packet.annotationId);
                            if (existing == null) {
                                return;
                            }
                            else if (!existing.getPlayerUUID().equals(player.getUUID()) && !player.hasPermissions(2)) {
                                return;
                            }
                            else {
                                final boolean removed = panel.getAnnotationManager().remove(packet.annotationId);
                                if (removed) {
                                    panel.setChanged();
                                    player.level().sendBlockUpdated(packet.pos, panel.getBlockState(), panel.getBlockState(), 3);
                                }
                                break;
                            }
                        }
                        case EDIT: {
                            final Annotation existing2 = panel.getAnnotationManager().getById(packet.annotationId);
                            if (existing2 == null) {
                                return;
                            }
                            else if (!existing2.getPlayerUUID().equals(player.getUUID()) && !player.hasPermissions(2)) {
                                return;
                            }
                            else {
                                final String trimmed2 = packet.newLabel.trim();
                                if (trimmed2.isEmpty() || trimmed2.length() > 48) {
                                    return;
                                }
                                else {
                                    final boolean edited = panel.getAnnotationManager().edit(packet.annotationId, trimmed2, packet.newColor | 0xFF000000);
                                    if (edited) {
                                        panel.setChanged();
                                        player.level().sendBlockUpdated(packet.pos, panel.getBlockState(), panel.getBlockState(), 3);
                                        break;
                                    }
                                    else {
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    return;
                }
                else {
                    return;
                }
            }
        });
    }
    
    public enum Action
    {
        ADD, 
        REMOVE, 
        EDIT;
    }
}
