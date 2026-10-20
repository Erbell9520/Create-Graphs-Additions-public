package com.createstats.repeller;

import com.createstats.tuner.TunerStation;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.event.EventHooks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RepellerStations {
    public static final int SURVEY_RADIUS = RepellerDraw.MAX_RADIUS;

    static final int MAX_PIECES = 160;

    private RepellerStations() {
    }

    public static List<TunerStation> survey(ServerLevel level, BlockPos repeller, String tuned) {
        final List<StationSurvey.Sighting> seen = new ArrayList<>();
        final StructureManager structures = level.structureManager();
        final ChunkGenerator generator = level.getChunkSource().getGenerator();
        final double cx = repeller.getX() + 0.5;
        final double cy = repeller.getY() + 0.5;
        final double cz = repeller.getZ() + 0.5;
        final String cat = RepellerFrequencies.idOf(EntityType.CAT);

        for (int[] o : StationSurvey.samples(SURVEY_RADIUS)) {
            final BlockPos at = repeller.offset(o[0], o[1], o[2]);
            if (!sampleable(level, at)) {
                continue;
            }
            final double d = Math.sqrt(o[0] * (double) o[0] + o[1] * (double) o[1] + o[2] * (double) o[2]);
            sightAt(level, structures, generator, at, d, seen);
            if (level.dimension() == Level.OVERWORLD && level.isVillage(at)) {
                seen.add(new StationSurvey.Sighting(cat, d));
            }
        }
        for (BlockPos at : structurePoints(level, structures, repeller, cx, cy, cz)) {
            if (sampleable(level, at)) {
                sightAt(level, structures, generator, at,
                        Math.sqrt(at.distToCenterSqr(cx, cy, cz)), seen);
            }
        }
        if (level.dimension() == Level.OVERWORLD && level.dimensionType().hasSkyLight()
                && level.getGameRules().getBoolean(GameRules.RULE_DOINSOMNIA)) {
            seen.add(new StationSurvey.Sighting(RepellerFrequencies.idOf(EntityType.PHANTOM), 0.0));
        }
        if (level.dimension() == Level.OVERWORLD && level.getGameRules().getBoolean(GameRules.RULE_DO_PATROL_SPAWNING)
                && !level.getBiome(repeller).is(BiomeTags.WITHOUT_PATROL_SPAWNS)) {
            seen.add(new StationSurvey.Sighting(RepellerFrequencies.idOf(EntityType.PILLAGER), 0.0));
        }

        final List<StationSurvey.Station> merged = StationSurvey.merge(seen, RepellerFrequencies.table(), tuned,
                Set.of(RepellerFrequencies.idOf(EntityType.PHANTOM)));
        final List<TunerStation> out = new ArrayList<>(merged.size());
        for (StationSurvey.Station s : merged) {
            final EntityType<?> type = RepellerFrequencies.typeOf(s.id());
            if (type == null) {
                continue;
            }
            out.add(new TunerStation(s.id(), type.getDescriptionId(), FrequencyTable.format(s.tenths()) + " kHz",
                    "entity:" + s.id(), colourOf(type.getCategory()), s.minRadius(),
                    s.column() ? TunerStation.STYLE_COLUMN : 0, s.tenths()));
        }
        return out;
    }

    public static int colourOf(MobCategory category) {
        return switch (category) {
            case MONSTER -> 0xFFC8553D;
            case CREATURE -> 0xFF6FAF4E;
            case AMBIENT -> 0xFF9A8FB0;
            case AXOLOTLS -> 0xFFE08FB0;
            case UNDERGROUND_WATER_CREATURE -> 0xFF3FB6A8;
            case WATER_CREATURE -> 0xFF3D8BC8;
            case WATER_AMBIENT -> 0xFF62B0E0;
            default -> 0xFFB0B0B0;
        };
    }

    private static boolean sampleable(ServerLevel level, BlockPos pos) {
        return pos.getY() >= level.getMinBuildHeight() && pos.getY() < level.getMaxBuildHeight()
                && level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }

    private static void sightAt(ServerLevel level, StructureManager structures, ChunkGenerator generator, BlockPos pos,
                                double d, List<StationSurvey.Sighting> out) {
        final Holder<Biome> biome = level.getBiome(pos);
        for (MobCategory category : MobCategory.values()) {
            if (category == MobCategory.MISC) {
                continue;
            }
            WeightedRandomList<MobSpawnSettings.SpawnerData> list = null;

            if (NaturalSpawner.isInNetherFortressBounds(pos, level, category, structures)) {
                final StructureSpawnOverride fortress = structures.registryAccess().registryOrThrow(Registries.STRUCTURE)
                        .getOrThrow(BuiltinStructures.FORTRESS).spawnOverrides().get(MobCategory.MONSTER);
                if (fortress != null) {
                    list = fortress.spawns();
                }
            }
            if (list == null) {
                list = generator.getMobsAt(biome, structures, category, pos);
            }
            list = EventHooks.getPotentialSpawns(level, category, pos, list);
            for (MobSpawnSettings.SpawnerData data : list.unwrap()) {
                out.add(new StationSurvey.Sighting(RepellerFrequencies.idOf(data.type), d));
            }
        }
    }

    private static List<BlockPos> structurePoints(ServerLevel level, StructureManager structures, BlockPos repeller,
                                                  double cx, double cy, double cz) {
        final List<double[]> found = new ArrayList<>();
        final Set<Long> startsSeen = new HashSet<>();
        final int r = SURVEY_RADIUS;
        for (long key : RepellerShape.chunksTouched(cx, cz, r)) {
            final LevelChunk chunk = level.getChunkSource().getChunkNow((int) key, (int) (key >> 32));
            if (chunk == null) {
                continue;
            }
            for (Map.Entry<Structure, LongSet> ref : chunk.getAllReferences().entrySet()) {
                final Structure structure = ref.getKey();
                if (structure.spawnOverrides().isEmpty()) {
                    continue;
                }
                boolean pieces = false;
                for (StructureSpawnOverride o : structure.spawnOverrides().values()) {
                    pieces |= o.boundingBox() == StructureSpawnOverride.BoundingBoxType.PIECE;
                }
                final boolean byPiece = pieces;
                structures.fillStartsForStructure(structure, ref.getValue(), start -> {
                    final long id = start.getChunkPos().toLong() * 31 + System.identityHashCode(structure);
                    if (!startsSeen.add(id)) {
                        return;
                    }
                    if (byPiece) {
                        for (StructurePiece piece : start.getPieces()) {
                            nearest(piece.getBoundingBox(), cx, cy, cz, r, found);
                        }
                    }
                    nearest(start.getBoundingBox(), cx, cy, cz, r, found);
                });
            }
        }
        found.sort((a, b) -> Double.compare(a[3], b[3]));
        final List<BlockPos> out = new ArrayList<>();
        for (int i = 0; i < found.size() && out.size() < MAX_PIECES; i++) {
            out.add(BlockPos.containing(found.get(i)[0], found.get(i)[1], found.get(i)[2]));
        }
        return out;
    }

    private static void nearest(BoundingBox box, double cx, double cy, double cz, int r, List<double[]> out) {
        final double x = Math.max(box.minX() + 0.5, Math.min(box.maxX() + 0.5, cx));
        final double y = Math.max(box.minY() + 0.5, Math.min(box.maxY() + 0.5, cy));
        final double z = Math.max(box.minZ() + 0.5, Math.min(box.maxZ() + 0.5, cz));
        final double d = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy) + (z - cz) * (z - cz));
        if (d <= r) {
            out.add(new double[] {x, y, z, d});
        }
    }
}
