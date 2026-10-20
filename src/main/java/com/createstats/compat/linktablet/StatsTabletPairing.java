package com.createstats.compat.linktablet;

import com.createstats.blockentity.SourceConfig;
import com.createstats.blockentity.StatsDisplayPanelBE;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class StatsTabletPairing {
    public record Target(ResourceLocation dimension, BlockPos pos) {
    }

    public record Candidate(BlockPos pos, ResourceLocation dimension, String label, double distanceSq) {
    }

    private static final int SCAN_CHUNK_RADIUS = 6;
    private static final Path FILE =
            FMLPaths.CONFIGDIR.get().resolve("create_stats").resolve("linktablet_pairing.txt");

    private static Target paired;
    private static boolean loaded;

    private StatsTabletPairing() {
    }

    public static Target getPaired() {
        if (!loaded) {
            load();
        }
        return paired;
    }

    public static boolean isPaired(BlockPos pos, ResourceLocation dimension) {
        final Target t = getPaired();
        return t != null && t.pos().equals(pos) && t.dimension().equals(dimension);
    }

    public static void setPaired(Candidate candidate) {
        paired = new Target(candidate.dimension(), candidate.pos().immutable());
        loaded = true;
        save();
    }

    public static void clear() {
        paired = null;
        loaded = true;
        save();
    }

    public static StatsDisplayPanelBE resolvePairedPanel() {
        final Target t = getPaired();
        final ClientLevel level = Minecraft.getInstance().level;
        if (t == null || level == null) {
            return null;
        }
        if (!level.dimension().location().equals(t.dimension()) || !level.isLoaded(t.pos())) {
            return null;
        }
        return level.getBlockEntity(t.pos()) instanceof StatsDisplayPanelBE panel ? panel : null;
    }

    public static String pairedLabel() {
        if (getPaired() == null) {
            return null;
        }
        final StatsDisplayPanelBE panel = resolvePairedPanel();
        return panel != null ? panelLabel(panel) : null;
    }

    public static List<Candidate> scanNearby(int max) {
        final List<Candidate> out = new ArrayList<>();
        final Minecraft mc = Minecraft.getInstance();
        final ClientLevel level = mc.level;
        final LocalPlayer player = mc.player;
        if (level == null || player == null) {
            return out;
        }
        final ResourceLocation dimension = level.dimension().location();
        final Vec3 eye = player.getEyePosition();
        final int pcx = player.chunkPosition().x;
        final int pcz = player.chunkPosition().z;
        for (int cx = pcx - SCAN_CHUNK_RADIUS; cx <= pcx + SCAN_CHUNK_RADIUS; cx++) {
            for (int cz = pcz - SCAN_CHUNK_RADIUS; cz <= pcz + SCAN_CHUNK_RADIUS; cz++) {
                final LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) {
                    continue;
                }
                for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                    if (entry.getValue() instanceof StatsDisplayPanelBE panel && panel.isController()) {
                        final BlockPos pos = entry.getKey().immutable();
                        out.add(new Candidate(pos, dimension, panelLabel(panel), eye.distanceToSqr(Vec3.atCenterOf(pos))));
                    }
                }
            }
        }
        out.sort(Comparator.comparingDouble(Candidate::distanceSq));
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    private static String panelLabel(StatsDisplayPanelBE panel) {
        SourceConfig best = null;
        for (SourceConfig source : panel.getSourceRegistry().values()) {
            if (source == null) {
                continue;
            }
            if (source.isActive()) {
                best = source;
                if ("Stressometer".equals(source.getSourceType())) {
                    break;
                }
            } else if (best == null) {
                best = source;
            }
        }
        if (best != null) {
            final String label = best.getDisplayLabel();
            if (label != null && !label.isBlank()) {
                return label;
            }
            final String type = best.getSourceType();
            if (type != null && !type.isBlank()) {
                return type;
            }
        }
        return "Stats Display";
    }

    private static void load() {
        loaded = true;
        paired = null;
        try {
            if (!Files.isRegularFile(FILE)) {
                return;
            }
            final String line = Files.readString(FILE).trim();
            if (line.isEmpty()) {
                return;
            }
            final String[] parts = line.split("\\s+");
            if (parts.length != 4) {
                return;
            }
            final ResourceLocation dimension = ResourceLocation.tryParse(parts[0]);
            if (dimension == null) {
                return;
            }
            paired = new Target(dimension,
                    new BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])));
        } catch (IOException | RuntimeException ignored) {
            paired = null;
        }
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            if (paired == null) {
                Files.writeString(FILE, "");
                return;
            }
            final BlockPos p = paired.pos();
            Files.writeString(FILE, paired.dimension() + " " + p.getX() + " " + p.getY() + " " + p.getZ());
        } catch (IOException | RuntimeException ignored) {
        }
    }
}
