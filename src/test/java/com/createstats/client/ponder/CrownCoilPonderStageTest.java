package com.createstats.client.ponder;

import com.createstats.chunkloader.ponder.CrownPonderScript;
import com.createstats.stormcoil.ponder.CoilPonderScript;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrownCoilPonderStageTest {
    private static final Path PONDER = Path.of("src/main/resources/assets/create_stats/ponder");

    record Block(String state, CompoundTag nbt) {
    }

    static Map<BlockPos, Block> load(String stage) throws IOException {
        final CompoundTag root = NbtIo.readCompressed(PONDER.resolve(stage + ".nbt"), NbtAccounter.unlimitedHeap());
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

    private static BlockPos pos(int[] p) {
        return new BlockPos(p[0], p[1], p[2]);
    }

    private static String state(Map<BlockPos, Block> blocks, BlockPos p) {
        final Block b = blocks.get(p);
        return b == null ? "minecraft:air" : b.state();
    }

    private static void checker(Map<BlockPos, Block> blocks, int plate) {
        for (int x = 0; x < plate; x++) {
            for (int z = 0; z < plate; z++) {
                final String s = state(blocks, new BlockPos(x, 0, z));
                assertTrue(s.equals("minecraft:white_concrete") || s.equals("minecraft:snow_block"),
                        "not Create's checker at " + x + ", " + z + ": " + s);
            }
        }
    }

    private static void spanBothEnds(Map<BlockPos, Block> blocks, BlockPos breaker, int stud, BlockPos far, int tier) {
        final ListTag wires = blocks.get(breaker).nbt().getList("StudWires", Tag.TAG_COMPOUND);
        boolean found = false;
        for (int i = 0; i < wires.size(); i++) {
            final CompoundTag w = wires.getCompound(i);
            found |= w.getByte("S") == stud && w.getLong("P") == far.asLong() && w.getByte("T") == tier;
        }
        assertTrue(found, "the breaker's stud " + stud + " is not strung to " + far);
        final long[] links = blocks.get(far).nbt().getLongArray("Links");
        final byte[] tiers = blocks.get(far).nbt().getByteArray("LinkTiers");
        boolean back = false;
        for (int i = 0; i < links.length; i++) {
            back |= links[i] == breaker.asLong() && tiers[i] == tier;
        }
        assertTrue(back, far + " does not name the breaker back");
    }

    @Test
    @DisplayName("the crown's stage: its station, connectors, breaker and floor map where the script has them")
    void station() throws IOException {
        final Map<BlockPos, Block> b = load("spark_crown/station");
        checker(b, CrownPonderScript.PLATE);
        assertEquals("create_stats:spark_crown[facing=south,light=0]", state(b, pos(CrownPonderScript.CROWN)));
        for (int[] c : new int[][] {CrownPonderScript.CONNECTOR_A, CrownPonderScript.CONNECTOR_B}) {
            assertEquals("create_stats:industrial_power_connector[anchor=center,facing=up]", state(b, pos(c)));
            assertEquals("create:andesite_casing", state(b, pos(c).below()), "a connector with no post");
        }
        assertTrue(state(b, pos(CrownPonderScript.BREAKER)).startsWith("create_stats:creative_main_breaker[")
                && state(b, pos(CrownPonderScript.BREAKER)).contains("facing=up")
                && state(b, pos(CrownPonderScript.BREAKER)).contains("powered=true"), "the breaker, closed, on the floor");
        spanBothEnds(b, pos(CrownPonderScript.BREAKER), 0, pos(CrownPonderScript.CONNECTOR_A), 3);
        spanBothEnds(b, pos(CrownPonderScript.BREAKER), 1, pos(CrownPonderScript.CONNECTOR_B), 3);
        final int[] m = CrownPonderScript.MAP_CENTRE;
        for (int i = 0; i < CrownPonderScript.PICKS; i++) {
            final int[] t = CrownPonderScript.tile(i);
            assertEquals(i == 0 ? "minecraft:magenta_carpet" : "minecraft:pink_carpet",
                    state(b, new BlockPos(m[0] + t[0], m[1], m[2] + t[1])), "map tile " + i);
        }
    }

    @Test
    @DisplayName("the coil's stage: the coil, IN's connector and breaker, the range on OUT, where the script has them")
    void yard() throws IOException {
        final Map<BlockPos, Block> b = load("storm_coil/yard");
        checker(b, CoilPonderScript.PLATE);
        assertEquals("create_stats:storm_coil[facing=east]", state(b, pos(CoilPonderScript.COIL)));
        assertEquals("create_stats:advanced_power_connector[anchor=center,facing=up]",
                state(b, pos(CoilPonderScript.IN_CONNECTOR)));
        assertEquals("create:andesite_casing", state(b, pos(CoilPonderScript.IN_CONNECTOR).below()));
        spanBothEnds(b, pos(CoilPonderScript.BREAKER), 0, pos(CoilPonderScript.IN_CONNECTOR),
                CoilPonderScript.IN_WIRE_TIER.ordinal());
        assertTrue(state(b, pos(CoilPonderScript.RANGE)).startsWith("create_stats:induction_range["), "no range");
        assertEquals(3, b.get(pos(CoilPonderScript.RANGE)).nbt().getInt("Setting"), "the range is not set to high");
        assertEquals("create_stats:power_connector[anchor=center,facing=up]", state(b, pos(CoilPonderScript.OUT_CONNECTOR)));
        assertEquals(pos(CoilPonderScript.RANGE), pos(CoilPonderScript.OUT_CONNECTOR).below(),
                "OUT's connector is not bolted to the range");
        for (double[] at : CoilPonderScript.HUSK_AT) {
            assertEquals("minecraft:air", state(b, BlockPos.containing(at[0], at[1], at[2])), "a husk inside a block");
        }
    }
}
