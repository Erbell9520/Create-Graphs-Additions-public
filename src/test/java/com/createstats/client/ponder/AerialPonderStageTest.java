package com.createstats.client.ponder;

import com.createstats.vccmk4.air.core.AerialParts;
import com.createstats.vccmk4.air.core.AerialSetup;
import com.createstats.vccmk4.air.core.AirLight;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AerialPonderStageTest {
    private static final Path STAGES = Path.of("src/main/resources/assets/create_stats/ponder/vcc_mk4_aerial");

    private static Map<List<Integer>, String> load(String stage) throws IOException {
        CompoundTag root = NbtIo.readCompressed(STAGES.resolve(stage + ".nbt"), NbtAccounter.unlimitedHeap());
        ListTag palette = root.getList("palette", Tag.TAG_COMPOUND);
        List<String> states = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) {
            CompoundTag p = palette.getCompound(i);
            StringBuilder s = new StringBuilder(p.getString("Name"));
            if (p.contains("Properties")) {
                CompoundTag props = p.getCompound("Properties");
                Map<String, String> sorted = new TreeMap<>();
                for (String k : props.getAllKeys()) sorted.put(k, props.getString(k));
                s.append(sorted.toString().replace(" ", "").replace('{', '[').replace('}', ']'));
            }
            states.add(s.toString());
        }
        Map<List<Integer>, String> blocks = new HashMap<>();
        ListTag list = root.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag b = list.getCompound(i);
            ListTag pos = b.getList("pos", Tag.TAG_INT);
            blocks.put(List.of(pos.getInt(0), pos.getInt(1), pos.getInt(2)), states.get(b.getInt("state")));
        }
        return blocks;
    }

    private static String at(Map<List<Integer>, String> blocks, int[] p) {
        return blocks.getOrDefault(List.of(p[0], p[1], p[2]), "minecraft:air");
    }

    private static List<String> stages() throws IOException {
        List<String> out = new ArrayList<>();
        try (var files = Files.list(STAGES)) {
            for (Path f : files.toList()) {
                String n = f.getFileName().toString();
                if (n.endsWith(".nbt")) out.add(n.substring(0, n.length() - 4));
            }
        }
        return out;
    }

    @Test
    void everyStageStandsOnCreatesChecker() throws IOException {
        List<String> stages = stages();
        assertFalse(stages.isEmpty(), "no stages found: has the folder moved? This test is then checking nothing.");
        for (String stage : stages) {
            Map<List<Integer>, String> blocks = load(stage);
            for (int x = 0; x < AerialStage.PLATE; x++) {
                for (int z = 0; z < AerialStage.PLATE; z++) {
                    boolean even = (x / 3 + z / 3) % 2 == 0;
                    boolean centre = x % 3 == 1 && z % 3 == 1;
                    String want = (even != centre) ? "minecraft:snow_block" : "minecraft:white_concrete";
                    assertEquals(want, blocks.get(List.of(x, 0, z)), stage + " floor at " + x + ", " + z);
                }
            }
        }
    }

    @Test
    void theStageMatchesItsConstants() throws IOException {
        for (String stage : List.of("hangar", "voyage")) {
            Map<List<Integer>, String> b = load(stage);
            assertEquals("create_stats:vcc_mk4_aerial[facing=east]", at(b, AerialStage.AERIAL), stage);
            assertEquals("create:red_seat", at(b, AerialStage.SEAT), stage);
            for (int[] burner : new int[][] {AerialStage.BURNER_AFT, AerialStage.BURNER_FORE}) {
                assertTrue(at(b, burner).startsWith("aeronautics:adjustable_burner["), stage + " burner");
            }
            for (int[] prop : new int[][] {AerialStage.PROP_PORT, AerialStage.PROP_STARBOARD}) {
                assertEquals("aeronautics:andesite_propeller[facing=west,reversed=false]", at(b, prop), stage);
            }
            for (int[] gov : new int[][] {AerialStage.GOVERNOR_PORT, AerialStage.GOVERNOR_STARBOARD}) {
                assertTrue(at(b, gov).startsWith("create_stats:rpm_governor["), stage + " governor");
            }
            assertTrue(at(b, AerialStage.ENGINE).startsWith("simulated:red_portable_engine["), stage);
            assertTrue(at(b, AerialStage.ASSEMBLER).startsWith("simulated:physics_assembler["), stage);
            assertTrue(at(b, AerialStage.FIN_BEARING).startsWith("simulated:swivel_bearing[") && at(b, AerialStage.FIN_BEARING)
                    .contains("facing=up"), stage + " fin bearing, hinged upright");
            assertEquals("simulated:gray_symmetric_sail[axis=z]", at(b, AerialStage.FIN_TIP), stage);
            assertEquals("create_stats:vehicle_light_red[facing=north,glow=off]", at(b, AerialStage.LAMP_RED), stage);
            assertEquals("create_stats:vehicle_light_white[facing=down,glow=off]", at(b, AerialStage.LAMP_WHITE), stage);
            assertEquals("create_stats:vehicle_light_amber[facing=up,glow=off]", at(b, AerialStage.LAMP_AMBER), stage);
            int[] lo = AerialStage.PORT_WALL_MIN;
            int[] hi = AerialStage.PORT_WALL_MAX;
            for (int x = lo[0]; x <= hi[0]; x++) {
                for (int y = lo[1]; y <= hi[1]; y++) {
                    assertTrue(at(b, new int[] {x, y, lo[2]}).endsWith("_envelope"), stage + " port wall at " + x + ", " + y);
                }
            }

            for (int x = (int) Math.floor(AerialStage.INSIDE_MIN[0]); x < AerialStage.INSIDE_MAX[0]; x++) {
                for (int y = (int) Math.floor(AerialStage.INSIDE_MIN[1]); y < AerialStage.INSIDE_MAX[1]; y++) {
                    for (int z = (int) Math.floor(AerialStage.INSIDE_MIN[2]); z < AerialStage.INSIDE_MAX[2]; z++) {
                        assertEquals("minecraft:air", at(b, new int[] {x, y, z}), stage + " inside at " + x + ", " + y + ", " + z);
                    }
                }
            }
        }
        Map<List<Integer>, String> hangar = load("hangar");
        for (int[] crate : AerialStage.CARGO) assertTrue(at(hangar, crate).startsWith("minecraft:barrel"), "cargo");
        assertTrue(at(hangar, AerialStage.LEVER).startsWith("create:analog_lever["), "the twin's lever");
        assertTrue(at(hangar, AerialStage.LEVER_BURNER).startsWith("aeronautics:adjustable_burner["), "the twin's burner");
    }

    private static Map<List<Integer>, String> ship(Map<List<Integer>, String> blocks) {
        Map<List<Integer>, String> out = new HashMap<>();
        for (var e : blocks.entrySet()) {
            List<Integer> p = e.getKey();
            boolean inside = p.get(0) >= AerialStage.SHIP_MIN[0] && p.get(0) <= AerialStage.SHIP_MAX[0]
                    && p.get(1) >= AerialStage.SHIP_MIN[1] && p.get(1) <= AerialStage.SHIP_MAX[1]
                    && p.get(2) >= AerialStage.SHIP_MIN[2] && p.get(2) <= AerialStage.SHIP_MAX[2];
            if (inside && !e.getValue().startsWith("minecraft:barrel")) out.put(p, e.getValue());
        }
        return out;
    }

    @Test
    void theShipIsOneShipAndItsCentreOfMassIsWhereTheScenesDrawIt() throws IOException {
        Map<List<Integer>, String> hangar = ship(load("hangar"));
        Map<List<Integer>, String> voyage = ship(load("voyage"));
        assertEquals(hangar, voyage, "the same ship in every stage (S9)");
        assertEquals(AerialStage.SHIP_BLOCKS, hangar.size());
        double[] sum = new double[3];
        for (List<Integer> p : hangar.keySet()) for (int i = 0; i < 3; i++) sum[i] += p.get(i) + 0.5;
        for (int i = 0; i < 3; i++) assertEquals(AerialStage.COM[i], sum[i] / hangar.size(), 0.002, "centre of mass axis " + i);
    }

    @Test
    void theChipsAreTheAerialsOwnProposals() {
        assertTrue(AerialStage.finSpec().lon() < -AerialParts.TAIL, "the fin is well aft: " + AerialStage.finSpec().lon());
        assertEquals(AerialSetup.SurfaceRole.RUDDER, AerialStage.finJob());
        assertEquals(AirLight.POSITION, AerialStage.redJob());
        assertEquals(AirLight.BEACON, AerialStage.amberJob());
        assertEquals(AirLight.LANDING, AerialStage.whiteJob());
        assertEquals(AerialSetup.EngineJob.AUTO, AerialStage.engineJob());
        assertEquals(0, AerialStage.noseTurns(), "facing the bow along the engines, the nose stands");
    }
}
