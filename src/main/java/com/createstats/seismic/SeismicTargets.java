package com.createstats.seismic;

import com.createstats.tuner.TunerStation;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SeismicTargets {
    public interface Target {
        String id();

        boolean maybeIn(LevelChunkSection section);

        int kindOf(BlockState state);

        @Nullable
        Heightmap.Types cover();

        boolean diagonal();
    }

    static final String VOIDS_ICON = "minecraft:pointed_dripstone";

    static final String LIQUIDS_ICON = "minecraft:lava_bucket";

    private SeismicTargets() {
    }

    @Nullable
    public static Target resolve(Level level, String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        if (SeismicCatalog.VOIDS.equals(id)) {
            return Voids.INSTANCE;
        }
        if (SeismicCatalog.LIQUIDS.equals(id)) {
            return Liquids.INSTANCE;
        }
        final TagKey<Block> tag = oreTag(id);
        if (tag == null) {
            return null;
        }
        final Optional<HolderSet.Named<Block>> set = blocks(level).getTag(tag);
        return set.isPresent() && set.get().size() > 0 ? new Ore(id, tag) : null;
    }

    public static boolean valid(Level level, String id) {
        return id.isEmpty() || resolve(level, id) != null;
    }

    public static List<TunerStation> stations(Level level) {
        final List<String> ids = new ArrayList<>();
        ids.add(SeismicCatalog.VOIDS);
        ids.add(SeismicCatalog.LIQUIDS);
        blocks(level).getTags()
                .filter(p -> isOreTag(p.getFirst()) && p.getSecond().size() > 0)
                .map(p -> p.getFirst().location().toString())
                .forEach(ids::add);
        ids.sort(SeismicCatalog::compare);
        final List<TunerStation> out = new ArrayList<>(ids.size());
        int dial = 0;
        for (final String id : ids) {
            out.add(station(level, id, dial++));
        }
        return out;
    }

    private static TunerStation station(Level level, String id, int dial) {
        final int colour = 0xFF000000 | SeismicCatalog.colour(id, 0);
        if (SeismicCatalog.VOIDS.equals(id)) {
            return new TunerStation(id, "block.create_stats.seismic_scanner.target.voids", SeismicCatalog.subLabel(id),
                    VOIDS_ICON, colour, SeismicCharge.MIN_RADIUS, 0, dial);
        }
        if (SeismicCatalog.LIQUIDS.equals(id)) {
            return new TunerStation(id, "block.create_stats.seismic_scanner.target.liquids", SeismicCatalog.subLabel(id),
                    LIQUIDS_ICON, colour, SeismicCharge.MIN_RADIUS, 0, dial);
        }
        final Block sample = sample(level, id);
        final String label = sample == null ? id : sample.getDescriptionId();
        final String icon = sample == null ? "" : BuiltInRegistries.ITEM.getKey(sample.asItem()).toString();
        return new TunerStation(id, label, SeismicCatalog.subLabel(id), icon, colour, SeismicCharge.MIN_RADIUS, 0, dial);
    }

    @Nullable
    public static Block sample(Level level, String id) {
        final TagKey<Block> tag = oreTag(id);
        if (tag == null) {
            return null;
        }
        final Optional<HolderSet.Named<Block>> set = blocks(level).getTag(tag);
        if (set.isEmpty() || set.get().size() == 0) {
            return null;
        }
        final String plain = SeismicCatalog.orePath(id) + "_ore";
        Block first = null;
        Block shallow = null;
        for (final Holder<Block> h : set.get()) {
            final Block b = h.value();
            final String path = BuiltInRegistries.BLOCK.getKey(b).getPath();
            if (path.equals(plain)) {
                return b;
            }
            if (first == null) {
                first = b;
            }
            if (shallow == null && !path.startsWith("deepslate_")) {
                shallow = b;
            }
        }
        return shallow != null ? shallow : first;
    }

    @Nullable
    static TagKey<Block> oreTag(String id) {
        if (!SeismicCatalog.isOre(id)) {
            return null;
        }
        final ResourceLocation loc = ResourceLocation.tryParse(id);
        return loc == null ? null : TagKey.create(Registries.BLOCK, loc);
    }

    private static boolean isOreTag(TagKey<Block> tag) {
        return SeismicCatalog.isOre(tag.location().toString());
    }

    private static Registry<Block> blocks(Level level) {
        return level.registryAccess().registryOrThrow(Registries.BLOCK);
    }

    private record Ore(String id, TagKey<Block> tag) implements Target {
        @Override
        public boolean maybeIn(LevelChunkSection section) {
            return !section.hasOnlyAir() && section.maybeHas(s -> s.is(tag));
        }

        @Override
        public int kindOf(BlockState state) {
            return state.is(tag) ? 0 : -1;
        }

        @Override
        public Heightmap.Types cover() {
            return null;
        }

        @Override
        public boolean diagonal() {
            return true;
        }
    }

    private static final class Voids implements Target {
        static final Voids INSTANCE = new Voids();

        @Override
        public String id() {
            return SeismicCatalog.VOIDS;
        }

        @Override
        public boolean maybeIn(LevelChunkSection section) {
            return section.hasOnlyAir() || section.maybeHas(BlockState::isAir);
        }

        @Override
        public int kindOf(BlockState state) {
            return state.isAir() ? 0 : -1;
        }

        @Override
        public Heightmap.Types cover() {
            return Heightmap.Types.MOTION_BLOCKING_NO_LEAVES;
        }

        @Override
        public boolean diagonal() {
            return false;
        }
    }

    private static final class Liquids implements Target {
        static final Liquids INSTANCE = new Liquids();

        @Override
        public String id() {
            return SeismicCatalog.LIQUIDS;
        }

        @Override
        public boolean maybeIn(LevelChunkSection section) {
            return !section.hasOnlyAir() && section.maybeHas(s -> !s.getFluidState().isEmpty());
        }

        @Override
        public int kindOf(BlockState state) {
            final FluidState fluid = state.getFluidState();
            if (fluid.isEmpty()) {
                return -1;
            }
            if (fluid.is(FluidTags.LAVA)) {
                return SeismicCatalog.KIND_LAVA;
            }
            return fluid.is(FluidTags.WATER) ? SeismicCatalog.KIND_WATER : -1;
        }

        @Override
        public Heightmap.Types cover() {
            return Heightmap.Types.OCEAN_FLOOR;
        }

        @Override
        public boolean diagonal() {
            return false;
        }
    }
}
