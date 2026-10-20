package com.createstats.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.createstats.CreateStats;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLPaths;

@OnlyIn(Dist.CLIENT)
public final class SupportClientHooks {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private SupportClientHooks() {
    }

    public static void deliverReport(String title, List<String> lines) {
        SupportTerminalScreen.acceptReport(title, lines);
    }

    public static void refreshTerminal() {
        if (Minecraft.getInstance().screen instanceof SupportTerminalScreen terminal) {
            terminal.statusChanged();
        }
    }

    public static void saveExport(List<String> lines) {
        Minecraft minecraft = Minecraft.getInstance();
        ServerData server = minecraft.getCurrentServer();
        String where = server == null ? "singleplayer" : server.ip.replaceAll("[^A-Za-z0-9._-]", "_");
        if (where.length() > 40) {
            where = where.substring(0, 40);
        }
        Path dir = FMLPaths.GAMEDIR.get().resolve("create_stats").resolve("support_reports");
        Path file = dir.resolve("support_report_" + where + "_" + LocalDateTime.now().format(STAMP) + ".txt");
        Component message;
        try {
            Files.createDirectories(dir);
            Files.write(file, lines, StandardCharsets.UTF_8);
            String path = file.toAbsolutePath().toString();
            message = Component.literal("Support report saved: ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(file.getFileName().toString()).withStyle(style -> style
                    .withColor(ChatFormatting.WHITE).withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, path))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(path)))));
        } catch (IOException | RuntimeException e) {
            CreateStats.LOGGER.warn("[SUPPORT] could not save export to {}", file, e);
            message = Component.literal("Could not save the support report: " + e).withStyle(ChatFormatting.RED);
        }
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(message, false);
        }
        SupportTerminalScreen.acceptExportSaved(lines.size(), file.getFileName().toString());
    }
}
