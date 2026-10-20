package com.createstats.vccmk4.client;

import com.createstats.CreateStats;
import com.createstats.vccmk4.core.JoinEscapeFile;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

// join escape. a huge ship closed the server seconds after sitting down, the save put you back in the seat, repeat forever
@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT)
public final class JoinEscape {
    static final int RELEASE_AT = 10;

    static final int RESEND_AT = 30;

    private static int sinceLogin = -1;
    private static List<String> commands = List.of();

    private JoinEscape() {
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        sinceLogin = -1;
        if (Minecraft.getInstance().isLocalServer()) return;
        List<String> read = read();
        if (read == null) return;
        LocalPlayer player = event.getPlayer();
        if (player == null || player.connection == null) return;
        commands = read;
        player.connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));
        for (String c : commands) player.connection.sendCommand(c);
        sinceLogin = 0;
        CreateStats.LOGGER.info("[Join escape] sneak pressed and {} sent on login", commands);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (sinceLogin < 0) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.connection == null) {
            sinceLogin = -1;
            return;
        }
        sinceLogin++;
        if (sinceLogin == RELEASE_AT) {
            player.connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
        }
        if (sinceLogin == RESEND_AT) {
            for (String c : commands) player.connection.sendCommand(c);
            sinceLogin = -1;
            player.displayClientMessage(Component.literal("[Create: S&P] Join escape: got off the seat and sent /"
                    + String.join(", /", commands) + ". Delete config/" + JoinEscapeFile.NAME + " to turn it off.")
                    .withStyle(ChatFormatting.YELLOW), false);
            CreateStats.LOGGER.info("[Join escape] {} sent again", commands);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        sinceLogin = -1;
    }

    private static List<String> read() {
        Path file = FMLPaths.CONFIGDIR.get().resolve(JoinEscapeFile.NAME);
        if (!Files.isRegularFile(file)) return null;
        try {
            return JoinEscapeFile.commands(Files.readAllLines(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            CreateStats.LOGGER.warn("[Join escape] could not read {}: {}", file, e.toString());
            return List.of();
        }
    }
}
