package com.createstats.support;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.createstats.CreateStats;
import com.createstats.ModConfiguration;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

// every collector fault tolerant, a diagnostic that throws on a broken server is useless
public final class SupportDiagnostics {
    private static final double LOOK_DISTANCE = 24.0;

    private static final int LOG_TAIL_LIMIT = 60;

    private static final long LOG_WINDOW_BYTES = 4L << 20;

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private SupportDiagnostics() {
    }

    private static void header(List<String> out, String title) {
        out.add("");
        out.add("== " + title + " ==");
    }

    public static List<String> environment() {
        List<String> out = new ArrayList<>();
        header(out, "Environment");
        try {
            out.add("Minecraft: " + SharedConstants.getCurrentVersion().getName());
            out.add("create_stats: " + modVersion("create_stats"));
            out.add("create: " + modVersion("create"));
            out.add("neoforge: " + modVersion("neoforge"));
            out.add("Java: " + System.getProperty("java.version") + " (" + System.getProperty("os.name") + ")");

            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                out.add("Server: " + (server.isDedicatedServer() ? "dedicated" : "integrated")
                    + ", online-mode=" + server.usesAuthentication()
                    + ", players=" + server.getPlayerCount() + "/" + server.getMaxPlayers());
                out.add("Mean tick: " + String.format("%.2f ms", server.getCurrentSmoothedTickTime()));
            }
            out.add("Mods loaded: " + ModList.get().getMods().size());
        } catch (RuntimeException e) {
            out.add("! environment collection failed: " + e);
        }
        return out;
    }

    private static String modVersion(String modId) {
        return ModList.get().getModContainerById(modId)
            .map(container -> container.getModInfo().getVersion().toString())
            .orElse("<not installed>");
    }

    public static List<String> createAddons() {
        List<String> out = new ArrayList<>();
        header(out, "Create ecosystem");
        try {
            List<String> addons = ModList.get().getMods().stream()
                .map(info -> info.getModId() + " " + info.getVersion())
                .filter(entry -> entry.contains("create") || entry.contains("Create"))
                .sorted()
                .toList();
            if (addons.isEmpty()) {
                out.add("<none detected>");
            } else {
                addons.forEach(out::add);
            }
        } catch (RuntimeException e) {
            out.add("! add-on scan failed: " + e);
        }
        return out;
    }

    public static List<String> configDump() {
        List<String> out = new ArrayList<>();
        header(out, "Config (common, effective values)");
        try {
            for (Field field : ModConfiguration.Common.class.getFields()) {
                Object holder = field.get(ModConfiguration.COMMON);
                if (holder instanceof net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<?> value) {
                    Object resolved;
                    try {
                        resolved = value.get();
                    } catch (RuntimeException e) {
                        resolved = "<unreadable: " + e.getClass().getSimpleName() + ">";
                    }
                    out.add("  " + field.getName() + " = " + resolved);
                }
            }
            out.sort(Comparator.naturalOrder());
        } catch (ReflectiveOperationException | RuntimeException e) {
            out.add("! config dump failed: " + e);
        }
        return out;
    }

    public static List<String> lookedAtBlockEntity(ServerPlayer player, boolean ownOnly) {
        List<String> out = new ArrayList<>();
        header(out, "Looked-at block");
        try {
            HitResult hit = player.pick(LOOK_DISTANCE, 0.0f, false);
            if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
                out.add("<not looking at a block within " + (int) LOOK_DISTANCE + " blocks>");
                return out;
            }

            ServerLevel level = player.serverLevel();
            BlockPos pos = blockHit.getBlockPos();
            BlockState state = level.getBlockState(pos);

            out.add("Position: " + pos.toShortString() + " in " + level.dimension().location());
            out.add("Block: " + BuiltInRegistries.BLOCK.getKey(state.getBlock()));
            out.add("State: " + state);
            out.add("Face: " + blockHit.getDirection());

            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) {
                out.add("BlockEntity: <none>");
                return out;
            }
            out.add("BlockEntity: " + be.getClass().getName());
            var typeKey = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
            if (ownOnly && (typeKey == null || !CreateStats.MOD_ID.equals(typeKey.getNamespace()))) {
                out.add("<data withheld: not a create_stats block, needs Tier 1>");
                return out;
            }
            CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());

            Stream.of(NbtUtils.structureToSnbt(tag).split("\n")).forEach(line -> out.add("  " + line));
        } catch (RuntimeException e) {
            out.add("! block entity dump failed: " + e);
        }
        return out;
    }

    public static List<String> nearbyBlockEntities(ServerPlayer player, int chunkRadius) {
        List<String> out = new ArrayList<>();
        header(out, "create_stats block entities within " + chunkRadius + " chunks");
        try {
            ServerLevel level = player.serverLevel();
            int centerX = player.chunkPosition().x;
            int centerZ = player.chunkPosition().z;
            Map<String, Integer> counts = new HashMap<>();
            List<String> positions = new ArrayList<>();

            for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(centerX + dx, centerZ + dz);
                    if (chunk == null) {
                        continue;
                    }
                    for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                        BlockEntity be = entry.getValue();
                        var key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
                        if (key == null || !CreateStats.MOD_ID.equals(key.getNamespace())) {
                            continue;
                        }
                        counts.merge(key.getPath(), 1, Integer::sum);
                        if (positions.size() < 40) {
                            positions.add("  " + key.getPath() + " @ " + entry.getKey().toShortString());
                        }
                    }
                }
            }

            if (counts.isEmpty()) {
                out.add("<none loaded>");
                return out;
            }
            counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> out.add("  " + entry.getValue() + "x " + entry.getKey()));
            out.add("-- positions (first " + positions.size() + ") --");
            out.addAll(positions);
        } catch (RuntimeException e) {
            out.add("! block entity scan failed: " + e);
        }
        return out;
    }

    public static List<String> recentLogLines() {
        List<String> out = new ArrayList<>();
        header(out, "Recent create_stats log entries");
        Path log = Paths.get("logs", "latest.log");
        try {
            if (!Files.isReadable(log)) {
                out.add("<logs/latest.log not readable from the server working directory>");
                return out;
            }
            SupportLogTail.Result tail = SupportLogTail.read(log, LOG_WINDOW_BYTES, LOG_TAIL_LIMIT,
                "create_stats", "createstats", CreateStats.MOD_NAME);
            out.addAll(tail.notes());
            if (tail.matches().isEmpty()) {
                out.add("<no warnings or errors found>");
            } else {
                out.addAll(tail.matches());
            }
        } catch (IOException | RuntimeException e) {
            out.add("! log read failed: " + e);
        }
        return out;
    }

    public static List<String> fullReportHead(ServerPlayer player, boolean safe) {
        List<String> out = new ArrayList<>();
        out.add("create_stats support report");
        out.add("Generated " + LocalDateTime.now() + " for " + player.getGameProfile().getName());
        out.addAll(environment());
        out.addAll(createAddons());
        out.addAll(lookedAtBlockEntity(player, safe));
        out.addAll(nearbyBlockEntities(player, 2));
        out.addAll(configDump());
        return out;
    }

    public static Path writeBundle(List<String> report) throws IOException {
        Path dir = Paths.get("create_stats", "support");
        Files.createDirectories(dir);
        Path file = dir.resolve("support_report_" + LocalDateTime.now().format(STAMP) + ".txt");
        Files.write(file, report, StandardCharsets.UTF_8);
        return file;
    }
}
