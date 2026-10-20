package com.createstats.cinecam.client;

import com.createstats.CreateStats;
import com.createstats.cinecam.core.FilmFormat;
import com.createstats.cinecam.core.MapPalette;
import com.createstats.cinecam.core.PhotoPrint;
import com.createstats.cinecam.net.CinePhotoPacket;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

// read the frame once on the render thread, everything else on a worker
final class CinePhotoTaker {
    static final String FOLDER = "cine_photos";
    private static final AtomicInteger JOB = new AtomicInteger();

    private CinePhotoTaker() {
    }

    static Path folder(Minecraft mc) {
        return mc.gameDirectory.toPath().resolve(FOLDER);
    }

    static void take(Minecraft mc, BlockPos camera, PhotoPrint.Target target, int side, String subject,
                     int[] paletteRgb, byte[] paletteIds) {
        NativeImage frame = Screenshot.takeScreenshot(mc.getMainRenderTarget());
        int job = JOB.incrementAndGet();
        Util.backgroundExecutor().execute(() -> {
            try (frame) {
                int w = frame.getWidth();
                int h = frame.getHeight();
                int sq = Math.min(w, h);
                int x0 = (w - sq) / 2;
                int y0 = (h - sq) / 2;
                if (target.file()) saveFile(mc, frame, x0, y0, sq, subject);
                if (target.maps()) sendMaps(mc, frame, x0, y0, sq, camera, job, side, paletteRgb, paletteIds);
            } catch (IOException | RuntimeException e) {
                CreateStats.LOGGER.warn("Cine Camera: the photograph could not be made", e);
                mc.execute(() -> say(mc, Component.translatable("create_stats.cine_camera.photo.failed",
                        String.valueOf(e.getMessage())).withStyle(ChatFormatting.RED)));
            }
        });
    }

    private static void saveFile(Minecraft mc, NativeImage frame, int x0, int y0, int sq, String subject) throws IOException {
        Path dir = folder(mc);
        Files.createDirectories(dir);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss"));
        String safe = FilmFormat.fileSafe(subject);
        String base = safe.isEmpty() ? stamp : stamp + "_" + safe;
        Path file = dir.resolve(base + ".png");
        for (int n = 2; Files.exists(file); n++) file = dir.resolve(base + "_" + n + ".png");
        try (NativeImage out = new NativeImage(sq, sq, false)) {
            frame.copyRect(out, x0, y0, 0, 0, sq, sq, false, false);
            out.writeToFile(file);
        }
        Path saved = file;
        mc.execute(() -> {
            MutableComponent link = Component.literal(saved.getFileName().toString()).withStyle(ChatFormatting.UNDERLINE)
                    .withStyle(st -> st.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, saved.toAbsolutePath().toString())));
            MutableComponent folder = Component.translatable("create_stats.cine_camera.film.folder")
                    .withStyle(ChatFormatting.UNDERLINE)
                    .withStyle(st -> st.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, saved.getParent().toAbsolutePath().toString())));
            say(mc, Component.translatable("create_stats.cine_camera.photo.saved", link, sq + " x " + sq, folder)
                    .withStyle(ChatFormatting.GREEN));
        });
    }

    private static void sendMaps(Minecraft mc, NativeImage frame, int x0, int y0, int sq, BlockPos camera, int job,
                                 int side, int[] paletteRgb, byte[] paletteIds) {
        int[] argb = new int[sq * sq];
        for (int y = 0; y < sq; y++) {
            for (int x = 0; x < sq; x++) {
                int c = frame.getPixelRGBA(x0 + x, y0 + y);
                argb[y * sq + x] = 0xFF000000 | (c & 0xFF) << 16 | (c >> 8 & 0xFF) << 8 | (c >> 16 & 0xFF);
            }
        }
        int size = PhotoPrint.pixelsFor(side);
        byte[] ids = MapPalette.quantize(MapPalette.squareDown(argb, sq, sq, size), size, paletteRgb, paletteIds, true);
        List<CinePhotoPacket> packets = new ArrayList<>();
        for (int row = 0; row < side; row++) {
            for (int col = 0; col < side; col++) {
                packets.add(new CinePhotoPacket(camera, job, row * side + col, side, PhotoPrint.tile(ids, side, row, col)));
            }
        }
        mc.execute(() -> {
            if (mc.getConnection() == null) return;
            for (CinePhotoPacket p : packets) PacketDistributor.sendToServer(p);
        });
    }

    private static void say(Minecraft mc, Component c) {
        if (mc.player != null) mc.player.displayClientMessage(c, false);
    }
}
