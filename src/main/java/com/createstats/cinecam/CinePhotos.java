package com.createstats.cinecam;

import com.createstats.cinecam.core.CineMode;
import com.createstats.cinecam.core.PhotoPrint;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CinePhotos {
    private static final long COOLDOWN = 20;

    private static final long ARRIVAL_TICKS = 100;
    private static final Map<UUID, Long> LAST = new HashMap<>();
    private static final Map<UUID, Job> JOBS = new HashMap<>();

    private CinePhotos() {
    }

    private static final class Job {
        final int id;
        final BlockPos camera;
        final int side;
        final byte[][] maps;
        final long startedAt;
        int received;

        Job(int id, BlockPos camera, int side, long startedAt) {
            this.id = id;
            this.camera = camera;
            this.side = side;
            this.maps = new byte[PhotoPrint.mapsFor(side)][];
            this.startedAt = startedAt;
        }
    }

    public static void receive(ServerLevel level, ServerPlayer player, CineCameraBE cam, int job, int index, int side,
                               byte[] colours) {
        long now = level.getGameTime();
        UUID who = player.getUUID();
        Job j = JOBS.get(who);
        if (j == null || j.id != job || !j.camera.equals(cam.getBlockPos()) || now - j.startedAt > ARRIVAL_TICKS) {
            JOBS.remove(who);
            if (index != 0 || !accept(level, player, cam, side, now)) return;
            j = new Job(job, cam.getBlockPos().immutable(), side, now);
            JOBS.put(who, j);
        }
        if (j.side != side || index >= j.maps.length || j.maps[index] != null) return;
        j.maps[index] = colours;
        if (++j.received < j.maps.length) return;
        JOBS.remove(who);
        print(level, player, cam, j);
    }

    private static boolean accept(ServerLevel level, ServerPlayer player, CineCameraBE cam, int side, long now) {
        if (cam.cineMode() != CineMode.PHOTO) return false;
        if (!cam.runs()) {
            player.displayClientMessage(Component.translatable("create_stats.cine_camera.no_power.short")
                    .withStyle(ChatFormatting.GOLD), true);
            return false;
        }
        Long last = LAST.get(player.getUUID());
        if (last != null && now - last < COOLDOWN) return false;
        int need = PhotoPrint.mapsFor(side);
        if (!player.getAbilities().instabuild && emptyMaps(player.getInventory()) < need) {
            player.displayClientMessage(Component.translatable("create_stats.cine_camera.photo.no_map", need)
                    .withStyle(ChatFormatting.GOLD), true);
            return false;
        }
        LAST.put(player.getUUID(), now);
        return true;
    }

    private static int emptyMaps(Inventory inv) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Items.MAP)) n += s.getCount();
        }
        return n;
    }

    private static void print(ServerLevel level, ServerPlayer player, CineCameraBE cam, Job j) {
        int need = j.maps.length;
        if (!player.getAbilities().instabuild) {
            Inventory inv = player.getInventory();
            if (emptyMaps(inv) < need) {
                player.displayClientMessage(Component.translatable("create_stats.cine_camera.photo.no_map", need)
                        .withStyle(ChatFormatting.GOLD), true);
                return;
            }
            int left = need;
            for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
                ItemStack s = inv.getItem(i);
                if (!s.is(Items.MAP)) continue;
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
        }
        long day = level.getDayTime() / 24000L + 1;
        String subject = cam.targetName().isEmpty() ? "" : " - " + cam.targetName();
        List<ItemStack> prints = new ArrayList<>(need);
        for (int row = 0; row < j.side; row++) {
            for (int col = 0; col < j.side; col++) {
                MapItemSavedData data = MapItemSavedData.createFresh(cam.getBlockPos().getX(), cam.getBlockPos().getZ(),
                        (byte) 0, false, false, level.dimension()).locked();
                System.arraycopy(j.maps[row * j.side + col], 0, data.colors, 0, data.colors.length);
                data.setDirty();
                MapId id = level.getFreeMapId();
                level.setMapData(id, data);
                ItemStack photo = new ItemStack(Items.FILLED_MAP);
                photo.set(DataComponents.MAP_ID, id);
                MutableComponent name = j.side == 1
                        ? Component.translatable("create_stats.cine_camera.photo.name")
                        : Component.translatable("create_stats.cine_camera.photo.name_part", row + 1, col + 1);
                photo.set(DataComponents.CUSTOM_NAME, name.withStyle(s -> s.withItalic(false)));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.translatable("create_stats.cine_camera.photo.lore", day, cam.frames())
                        .append(Component.literal(subject)).withStyle(ChatFormatting.GRAY));
                if (j.side > 1) {
                    lore.add(Component.translatable("create_stats.cine_camera.photo.part", j.side, j.side)
                            .withStyle(ChatFormatting.GRAY));
                }
                photo.set(DataComponents.LORE, new ItemLore(lore));
                prints.add(photo);
            }
        }
        for (ItemStack photo : prints) {
            if (!player.getInventory().add(photo)) player.drop(photo, false);
        }
        cam.photographed();
        level.playSound(null, cam.getBlockPos().above(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.BLOCKS,
                0.8f, 1.5f);
    }
}
