package com.createstats.client.ponder.seismic;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeismicPonderStageTest {
    private static final Path STAGES = Path.of("src/main/resources/assets/create_stats/ponder/seismic");

    record Block(String state, CompoundTag nbt) {
    }

    static Map<BlockPos, Block> load(String stage) throws IOException {
        final CompoundTag root = NbtIo.readCompressed(STAGES.resolve(stage + ".nbt"), NbtAccounter.unlimitedHeap());
        final ListTag palette = root.getList("palette", Tag.TAG_COMPOUND);
        final List<String> states = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) {
            final CompoundTag p = palette.getCompound(i);
            final StringBuilder s = new StringBuilder(p.getString("Name"));
            if (p.contains("Properties")) {
                final CompoundTag props = p.getCompound("Properties");
                final Map<String, String> sorted = new TreeMap<>();
                for (String k : props.getAllKeys()) {
                    sorted.put(k, props.getString(k));
                }
                s.append(sorted.toString().replace(" ", "").replace('{', '[').replace('}', ']'));
            }
            states.add(s.toString());
        }
        final Map<BlockPos, Block> blocks = new HashMap<>();
        final ListTag list = root.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            final CompoundTag b = list.getCompound(i);
            final ListTag pos = b.getList("pos", Tag.TAG_INT);
            blocks.put(new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)),
                    new Block(states.get(b.getInt("state")), b.contains("nbt") ? b.getCompound("nbt") : new CompoundTag()));
        }
        return blocks;
    }

    private static String at(Map<BlockPos, Block> blocks, int x, int y, int z) {
        final Block b = blocks.get(new BlockPos(x, y, z));
        return b == null ? "minecraft:air" : b.state();
    }

    private static BlockPos pos(int[] p) {
        return new BlockPos(p[0], p[1], p[2]);
    }

    private static String checker(int dx, int dz, int size) {
        if (size <= 8) {
            return (dx + dz) % 2 == 0 ? "minecraft:white_concrete" : "minecraft:snow_block";
        }
        final boolean even = (dx / 3 + dz / 3) % 2 == 0;
        final boolean centre = dx % 3 == 1 && dz % 3 == 1;
        return (even ? centre : !centre) ? "minecraft:white_concrete" : "minecraft:snow_block";
    }

    private static void floor(Map<BlockPos, Block> blocks, int width, int depth, int rule, String stage) {
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                assertEquals(checker(x, z, rule), at(blocks, x, 0, z), stage + " floor at " + x + ", " + z);
            }
        }
    }

    private static void rig(Map<BlockPos, Block> blocks, int[] p, String stage) {
        final String lower = at(blocks, p[0], p[1], p[2]);
        final String upper = at(blocks, p[0], p[1] + 1, p[2]);
        assertTrue(lower.startsWith("create_stats:seismic_scanner[") && lower.contains("facing=north")
                && lower.contains("half=lower"), stage + ": the rig's lower half at " + pos(p) + " is " + lower);
        assertTrue(upper.startsWith("create_stats:seismic_scanner[") && upper.contains("facing=north")
                && upper.contains("half=upper"), stage + ": the rig's upper half is " + upper);
        assertEquals("create_stats:seismic_scanner", blocks.get(pos(p)).nbt().getString("id"),
                stage + ": the block entity rides the lower half");
        assertTrue(blocks.get(pos(p).above()).nbt().isEmpty(), stage + ": the upper half carries no block entity");
    }

    private static void ore(Map<BlockPos, Block> blocks, int[][][] veins, int[][] coal, String stage) {
        final Set<BlockPos> iron = new HashSet<>();
        final Set<BlockPos> coalAt = new HashSet<>();
        for (Map.Entry<BlockPos, Block> e : blocks.entrySet()) {
            if (e.getValue().state().endsWith("iron_ore")) {
                iron.add(e.getKey());
            }
            if (e.getValue().state().endsWith("coal_ore")) {
                coalAt.add(e.getKey());
            }
        }
        final Set<BlockPos> want = new HashSet<>();
        for (int[] c : SeismicStage.ore(veins)) {
            want.add(pos(c));
        }
        assertEquals(want, iron, stage + ": the iron in the schematic is not the iron the scene surveys");
        final Set<BlockPos> wantCoal = new HashSet<>();
        for (int[] c : coal) {
            wantCoal.add(pos(c));
        }
        assertEquals(wantCoal, coalAt, stage + ": the coal seam moved");
    }

    @Test
    void thePadIsTheScenesPad() throws IOException {
        final Map<BlockPos, Block> b = load("pad");
        floor(b, SeismicStage.PAD_PLATE, SeismicStage.PAD_PLATE, SeismicStage.PAD_PLATE, "pad");
        rig(b, SeismicStage.PAD_RIG, "pad");
        rig(b, SeismicStage.PAD_DECOY, "pad");
        final int[][] g = SeismicStage.PAD_GROUND;
        for (int x = g[0][0]; x <= g[1][0]; x++) {
            for (int z = g[0][2]; z <= g[1][2]; z++) {
                assertEquals("minecraft:dirt", at(b, x, g[0][1], z), "pad dirt");
                assertEquals("minecraft:grass_block", at(b, x, g[1][1], z), "pad grass");
            }
        }
        final int[] r = SeismicStage.PAD_RIG;
        assertEquals("minecraft:grass_block", at(b, r[0], r[1] - 1, r[2]), "the hero stands on natural ground");
        final int[] d = SeismicStage.PAD_DECOY;
        assertEquals("minecraft:oak_planks", at(b, d[0], d[1] - 1, d[2]), "the decoy stands on planks");
        assertEquals(pos(SeismicStage.PAD_PLANK), pos(d).below(), "the plank is under the decoy");
        final int[] cable = SeismicStage.PAD_CABLE;
        final int[] bank = SeismicStage.PAD_BANK;
        assertTrue(at(b, cable[0], cable[1], cable[2]).startsWith("create_stats:surface_cable[")
                && at(b, cable[0], cable[1], cable[2]).contains("down=true"), "a surface cable lies on the grass");
        assertEquals("minecraft:grass_block", at(b, cable[0], cable[1] - 1, cable[2]), "the cable lies on a solid face");
        assertTrue(at(b, bank[0], bank[1], bank[2]).startsWith("create_stats:capacitor_bank"), "the bank");

        assertEquals(pos(r).west(), pos(cable), "the cable is against the rig's west flank");
        assertEquals(pos(cable).west(), pos(bank), "the bank is against the cable");
    }

    @Test
    void theSurveyGroundIsTheScenesGround() throws IOException {
        final Map<BlockPos, Block> b = load("rig");
        floor(b, SeismicStage.RIG_PLATE, SeismicStage.RIG_PLATE, SeismicStage.RIG_PLATE, "rig");
        rig(b, SeismicStage.RIG, "rig");
        final int[][] g = SeismicStage.RIG_GROUND;
        for (int x = g[0][0]; x <= g[1][0]; x++) {
            for (int z = g[0][2]; z <= g[1][2]; z++) {
                assertEquals("minecraft:grass_block", at(b, x, g[1][1], z), "grass on top at " + x + ", " + z);
                for (int y = g[0][1]; y <= g[1][1]; y++) {
                    assertTrue(!at(b, x, y, z).equals("minecraft:air"), "the ground is solid at " + x + ", " + y + ", " + z);
                }
            }
        }
        final int[] r = SeismicStage.RIG;
        assertEquals(g[1][1] + 1, r[1], "the rig stands on the ground's top");
        assertTrue(SeismicStage.inside(g, r[0], r[1] - 1, r[2]) && !SeismicStage.inside(SeismicStage.NOTCH, r[0], r[1] - 1, r[2]),
                "the rig's plate stands on ground the cut leaves in place");
        ore(b, SeismicStage.RIG_VEINS, SeismicStage.RIG_COAL, "rig");

        for (int[] c : SeismicStage.ore(SeismicStage.RIG_VEINS)) {
            assertTrue(SeismicStage.inside(g, c[0], c[1], c[2]), "iron outside the ground at " + pos(c));
        }
    }

    @Test
    void theStripIsTheScenesStrip() throws IOException {
        final Map<BlockPos, Block> b = load("strip");
        floor(b, SeismicStage.STRIP_LENGTH, SeismicStage.STRIP_DEPTH, 9, "strip");
        rig(b, SeismicStage.STRIP_RIG, "strip");
        final int[][] g = SeismicStage.STRIP_GROUND;
        assertEquals(g[1][1] + 1, SeismicStage.STRIP_RIG[1], "the rig stands on the strip's top");
        assertEquals("c:ores/iron", b.get(pos(SeismicStage.STRIP_RIG)).nbt().getString("Target"), "tuned to iron");
        ore(b, SeismicStage.STRIP_VEINS, SeismicStage.STRIP_COAL, "strip");
        for (int x = g[0][0]; x <= g[1][0]; x++) {
            assertEquals("minecraft:grass_block", at(b, x, g[1][1], g[0][2]), "grass along the strip");
        }
    }
}
