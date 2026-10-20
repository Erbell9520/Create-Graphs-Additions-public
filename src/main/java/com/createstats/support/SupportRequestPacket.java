package com.createstats.support;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.createstats.support.SupportPolicy.Decision;
import com.createstats.support.SupportPolicy.Level;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SupportRequestPacket(String section) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SupportRequestPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("create_stats", "support_request"));

    public static final StreamCodec<FriendlyByteBuf, SupportRequestPacket> STREAM_CODEC =
        StreamCodec.of(SupportRequestPacket::encode, SupportRequestPacket::decode);

    public static final String SECTION_FULL = "full";
    public static final String SECTION_LOOK = "look";
    public static final String SECTION_NEARBY = "nearby";
    public static final String SECTION_CONFIG = "config";
    public static final String SECTION_LOGS = "logs";
    public static final String SECTION_EXPORT = "export";

    private static void encode(FriendlyByteBuf buf, SupportRequestPacket pkt) {
        buf.writeUtf(pkt.section, 32);
    }

    private static SupportRequestPacket decode(FriendlyByteBuf buf) {
        return new SupportRequestPacket(buf.readUtf(32));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SupportRequestPacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            Decision decision = SupportSessions.access(player);
            if (!decision.atLeast(Level.SAFE)
                || !SupportSessions.allowRequest(player, SECTION_EXPORT.equals(pkt.section))) {
                return;
            }

            boolean safe = !decision.atLeast(Level.TIER1);

            switch (pkt.section) {
                case SECTION_LOOK -> send(player, "Looked-at block", SupportDiagnostics.lookedAtBlockEntity(player, safe));
                case SECTION_NEARBY -> send(player, "Nearby block entities", SupportDiagnostics.nearbyBlockEntities(player, 2));
                case SECTION_CONFIG -> send(player, "Config", SupportDiagnostics.configDump());
                case SECTION_LOGS -> {
                    if (safe) {
                        refuse(player, decision);
                        return;
                    }
                    withLogTail(player, new ArrayList<>(), lines -> send(player, "Recent log entries", lines));
                }
                case SECTION_EXPORT -> {
                    if (safe) {
                        refuse(player, decision);
                        return;
                    }
                    export(player, true);
                    return;
                }
                case SECTION_FULL -> {
                    List<String> head = SupportDiagnostics.fullReportHead(player, safe);
                    if (safe) {
                        send(player, "Full report (safe view)", head);
                    } else {
                        withLogTail(player, head, lines -> send(player, "Full report", lines));
                    }
                }
                default -> {
                    return;
                }
            }
            SupportAccess.audit(player, "tier1", pkt.section);
        });
    }

    private static void send(ServerPlayer player, String title, List<String> lines) {
        PacketDistributor.sendToPlayer(player, SupportReportPacket.of(title, lines));
    }

    private static void refuse(ServerPlayer player, Decision decision) {
        send(player, "Not available", List.of("Needs Tier 1. You have " + decision.level() + " ("
            + decision.basis().label() + "): " + decision.tier2Note()));
    }

    // log read off thread, world reads on it
    private static void withLogTail(ServerPlayer player, List<String> head, Consumer<List<String>> finish) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        CompletableFuture.supplyAsync(SupportDiagnostics::recentLogLines, Util.backgroundExecutor())
            .exceptionally(error -> List.of("! log read failed: " + error))
            .thenAccept(tail -> server.execute(() -> {
                if (player.hasDisconnected()) {
                    return;
                }
                List<String> all = new ArrayList<>(head);
                all.addAll(tail);
                finish.accept(all);
            }));
    }

    public static void export(ServerPlayer player, boolean toClient) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        SupportAccess.audit(player, "tier1", "export");
        List<String> head = SupportDiagnostics.fullReportHead(player, false);
        CompletableFuture.supplyAsync(() -> {
            List<String> report = new ArrayList<>(head);
            report.addAll(SupportDiagnostics.recentLogLines());
            Path file;
            try {
                file = SupportDiagnostics.writeBundle(report);
            } catch (IOException e) {
                file = null;
                report.add("! server copy not written: " + e);
            }
            return new Export(report, file);
        }, Util.backgroundExecutor()).whenComplete((result, error) -> server.execute(() -> {
            if (player.hasDisconnected()) {
                return;
            }
            if (error != null) {
                player.sendSystemMessage(Component.literal("Export failed: " + error).withStyle(ChatFormatting.RED));
                return;
            }
            if (result.serverCopy() != null) {
                player.sendSystemMessage(Component.literal("Server copy written to "
                    + result.serverCopy().toAbsolutePath()).withStyle(ChatFormatting.GRAY));
            }
            if (toClient && !SupportExportPacket.send(player, result.report())) {
                player.sendSystemMessage(Component.literal("Report too large to send; use the server copy.")
                    .withStyle(ChatFormatting.RED));
            }
        }));
    }

    private record Export(List<String> report, Path serverCopy) {
    }
}
