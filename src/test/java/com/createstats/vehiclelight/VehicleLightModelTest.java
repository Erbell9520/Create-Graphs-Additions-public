package com.createstats.vehiclelight;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleLightModelTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/create_stats");
    private static final Path DATA = Path.of("src/main/resources/data");
    private static final Path JAVA = Path.of("src/main/java/com/createstats");
    private static final float EPS = 1.0e-3f;

    private static JsonObject json(Path p) throws IOException {
        assertTrue(Files.isRegularFile(p), "missing " + p);
        return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
    }

    private static float[] box(JsonObject element) {
        final JsonArray f = element.getAsJsonArray("from");
        final JsonArray t = element.getAsJsonArray("to");
        return new float[] {f.get(0).getAsFloat(), f.get(1).getAsFloat(), f.get(2).getAsFloat(),
                t.get(0).getAsFloat(), t.get(1).getAsFloat(), t.get(2).getAsFloat()};
    }

    private static List<float[]> elements(JsonObject model) {
        final List<float[]> out = new ArrayList<>();
        for (JsonElement e : model.getAsJsonArray("elements")) {
            out.add(box(e.getAsJsonObject()));
        }
        return out;
    }

    @Test
    void theHousingLeavesTheLensItsOpeningAndFramesIt() throws IOException {
        for (VehicleLightKind k : VehicleLightKind.values()) {
            final List<float[]> housing = elements(json(ASSETS.resolve("models/block/vehicle_light_" + k.id() + ".json")));
            float top = 0.0f;
            for (float[] e : housing) {
                top = Math.max(top, e[4]);
            }
            assertEquals(k.bezelTop(), top, EPS, k + ": the model's front is the bezel top the hitbox uses");
            final float[] bounds = {16, 16, 16, 0, 0, 0};
            for (float[] e : housing) {
                for (int i = 0; i < 3; i++) {
                    bounds[i] = Math.min(bounds[i], e[i]);
                    bounds[i + 3] = Math.max(bounds[i + 3], e[i + 3]);
                }
            }
            final net.minecraft.world.phys.shapes.VoxelShape hit = VehicleLightShapes.up(k);
            final double[] shape = {hit.min(net.minecraft.core.Direction.Axis.X) * 16, hit.min(net.minecraft.core.Direction.Axis.Y) * 16,
                    hit.min(net.minecraft.core.Direction.Axis.Z) * 16, hit.max(net.minecraft.core.Direction.Axis.X) * 16,
                    hit.max(net.minecraft.core.Direction.Axis.Y) * 16, hit.max(net.minecraft.core.Direction.Axis.Z) * 16};
            for (int i = 0; i < 6; i++) {
                assertEquals(bounds[i], shape[i], EPS, k + ": the hitbox is the size of the lamp, bound " + i);
            }
            final float[] f = k.flange();
            assertTrue(housing.stream().anyMatch(e -> Math.abs(e[1]) < EPS && Math.abs(e[0] - f[0]) < EPS
                    && Math.abs(e[2] - f[1]) < EPS && Math.abs(e[3] - f[2]) < EPS && Math.abs(e[5] - f[3]) < EPS),
                    k + ": a flange on the mounting face where the hitbox has it");
            for (float[] lens : k.lens()) {
                for (float[] e : housing) {
                    final boolean overlapsXZ = e[0] < lens[3] - EPS && e[3] > lens[0] + EPS
                            && e[2] < lens[5] - EPS && e[5] > lens[2] + EPS;
                    assertTrue(!overlapsXZ || e[4] <= lens[1] + EPS,
                            k + ": nothing in the housing stands in or over the lens");
                }

                final float[] l = lens;
                assertTrue(housing.stream().anyMatch(e -> e[4] > l[1] + EPS && Math.abs(e[3] - l[0]) < EPS), k + ": west edge");
                assertTrue(housing.stream().anyMatch(e -> e[4] > l[1] + EPS && Math.abs(e[0] - l[3]) < EPS), k + ": east edge");
                assertTrue(housing.stream().anyMatch(e -> e[4] > l[1] + EPS && Math.abs(e[5] - l[2]) < EPS), k + ": north edge");
                assertTrue(housing.stream().anyMatch(e -> e[4] > l[1] + EPS && Math.abs(e[2] - l[5]) < EPS), k + ": south edge");
                break;
            }
        }
    }

    @Test
    void everyFileTheBlocksNeedIsThere() throws IOException {
        final String lang = Files.readString(ASSETS.resolve("lang/en_us.json"));
        final String pickaxe = Files.readString(DATA.resolve("minecraft/tags/block/mineable/pickaxe.json"));
        for (VehicleLightKind k : VehicleLightKind.values()) {
            final String id = "vehicle_light_" + k.id();
            final JsonObject variants = json(ASSETS.resolve("blockstates/" + id + ".json")).getAsJsonObject("variants");
            for (String facing : new String[] {"up", "down", "north", "south", "east", "west"}) {
                final JsonObject v = variants.getAsJsonObject("facing=" + facing);
                assertTrue(v != null, id + " has no variant for facing " + facing);
                assertEquals("create_stats:block/" + id, v.get("model").getAsString());
            }
            assertEquals("create_stats:block/" + id + "_item",
                    json(ASSETS.resolve("models/item/" + id + ".json")).get("parent").getAsString());
            final JsonObject item = json(ASSETS.resolve("models/block/" + id + "_item.json"));
            assertEquals("create_stats:block/vehicle_light_lens_" + k.id(),
                    item.getAsJsonObject("textures").get("lens").getAsString(), id + ": the item shows its lens");
            for (String tex : new String[] {"vehicle_light_housing", "vehicle_light_bezel", "vehicle_light_lens_" + k.id()}) {
                assertTrue(Files.isRegularFile(ASSETS.resolve("textures/block/" + tex + ".png")), "missing texture " + tex);
            }
            final String loot = Files.readString(DATA.resolve("create_stats/loot_table/blocks/" + id + ".json"));
            assertTrue(loot.contains("\"create_stats:" + id + "\""), id + " drops itself");
            assertTrue(pickaxe.contains("\"create_stats:" + id + "\""), id + " is mined with a pickaxe");
            for (String key : new String[] {"", ".tooltip", ".tooltip.usage", ".tooltip.placement", ".tooltip.shift"}) {
                assertTrue(lang.contains("\"block.create_stats." + id + key + "\""), "no lang for block.create_stats." + id + key);
            }
        }
    }

    @Test
    void theBlocksAreRegisteredWithTheirBlockEntityAndRendererAndListed() throws IOException {
        final String blocks = Files.readString(JAVA.resolve("registry/ModBlocks.java"));
        final String entities = Files.readString(JAVA.resolve("registry/ModBlockEntities.java"));
        final String tab = Files.readString(JAVA.resolve("registry/ModCreativeTab.java"));
        final int line = entities.indexOf("blockEntity(\"vehicle_light\"");
        assertTrue(line >= 0, "ModBlockEntities registers vehicle_light");
        final String registration = entities.substring(line, entities.indexOf(".register();", line));
        assertTrue(registration.contains("VehicleLightRenderer::new"), "with its renderer, or the lens is never drawn");
        for (String name : new String[] {"WHITE", "RED", "AMBER"}) {
            final int at = blocks.indexOf("VEHICLE_LIGHT_" + name + " = CreateStats.registrate()");
            assertTrue(at >= 0, "ModBlocks registers VEHICLE_LIGHT_" + name);
            final String statement = blocks.substring(at, blocks.indexOf(".register();", at));
            assertTrue(statement.contains("new com.createstats.vehiclelight.VehicleLightBlock(p, com.createstats.vehiclelight.VehicleLightKind."
                    + name + ")"), "VEHICLE_LIGHT_" + name + " is built as its own colour");
            assertTrue(statement.contains("lightLevel(com.createstats.vehiclelight.VehicleLightBlock.emission(com.createstats.vehiclelight.VehicleLightKind."
                    + name + "))"), "and gives its own colour's light, captured at registration rather than read from a field");
            assertTrue(registration.contains("ModBlocks.VEHICLE_LIGHT_" + name + ".get()"),
                    "the block entity is valid on VEHICLE_LIGHT_" + name + ", or placing one throws");
            assertTrue(tab.contains("ModBlocks.VEHICLE_LIGHT_" + name + "::asItem"), "the creative tab lists VEHICLE_LIGHT_" + name);
        }
    }
}
