package com.createstats.vccmk4.air;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AerialDeckWiringTest {
    private static final Path JAVA = Path.of("src/main/java/com/createstats");
    private static final Path BLOCKS = JAVA.resolve("registry/ModBlocks.java");
    private static final Path BLOCK_ENTITIES = JAVA.resolve("registry/ModBlockEntities.java");
    private static final Path TAB = JAVA.resolve("registry/ModCreativeTab.java");
    private static final Path PARTIALS = JAVA.resolve("client/ModPartialModels.java");
    private static final Path DECK = JAVA.resolve("vccmk4/air/AerialDeckBlock.java");
    private static final Path PART = JAVA.resolve("vccmk4/air/AerialDeckPartBE.java");
    private static final Path RENDERER = JAVA.resolve("renderer/AerialCockpitRenderer.java");
    private static final Path RES = Path.of("src/main/resources");
    private static final Path ASSETS = RES.resolve("assets/create_stats");
    private static final String ID = "vcc_mk4_aerial_flight_deck";

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String code(Path p) throws IOException {
        return read(p).replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//[^\\n]*", "");
    }

    private static String statement(String source, String field) {
        int start = source.indexOf(field + " = CreateStats.registrate()");
        assertTrue(start >= 0, field + " is not registered");
        int end = source.indexOf(".register();", start);
        assertTrue(end > start, field + "'s registration has no .register()");
        return source.substring(start, end + ".register();".length());
    }

    private static String body(String source, String signature) {
        int at = source.indexOf(signature);
        assertTrue(at >= 0, "no method " + signature);
        int open = source.indexOf('{', at);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}' && --depth == 0) return source.substring(open, i + 1);
        }
        throw new AssertionError("method " + signature + " never closes");
    }

    private static JsonObject json(Path p) throws IOException {
        return JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static int count(String source, String needle) {
        int n = 0;
        for (int i = source.indexOf(needle); i >= 0; i = source.indexOf(needle, i + needle.length())) n++;
        return n;
    }

    @Test
    void theDeckIsItsOwnBlockAndItsLeftHalfIsAnAerial() throws IOException {
        String block = statement(code(BLOCKS), "VCC_MK4_AERIAL_DECK");
        assertTrue(block.contains(".block(\"" + ID + "\", com.createstats.vccmk4.air.AerialDeckBlock::new)"), block);
        assertTrue(block.contains(".addLayer(() -> RenderType::cutoutMipped)"), "the cockpit's own layer");
        assertTrue(block.contains(".simpleItem()"), "an item to place it with");
        String entities = code(BLOCK_ENTITIES);
        String aerial = statement(entities, "VCC_MK4_AERIAL");
        assertTrue(aerial.contains(".validBlock(() -> ModBlocks.VCC_MK4_AERIAL.get())")
                        && aerial.contains(".validBlock(() -> ModBlocks.VCC_MK4_AERIAL_DECK.get())"),
                "the deck's left half is an Aerial of the Aerial's own type, drawn by the cockpit's renderer: " + aerial);
        String part = statement(entities, "VCC_MK4_AERIAL_DECK_PART");
        assertTrue(part.contains(".blockEntity(\"" + ID + "_part\", com.createstats.vccmk4.air.AerialDeckPartBE::new)"), part);
        assertTrue(part.contains(".validBlock(() -> ModBlocks.VCC_MK4_AERIAL_DECK.get())"), part);
        assertFalse(part.contains(".renderer("), "the left half's renderer draws the whole deck");
    }

    @Test
    void theTabListsTheDeckOnceBesideTheAerial() throws IOException {
        String tab = code(TAB);
        assertEquals(1, count(tab, "ModBlocks.VCC_MK4_AERIAL_DECK::asItem"), "listed once");
        assertTrue(tab.contains("ModBlocks.VCC_MK4_AERIAL::asItem, ModBlocks.VCC_MK4_AERIAL_DECK::asItem"),
                "right after the Aerial, in its row");
    }

    @Test
    void theRightHalfIsPlacedWithTheLeftAndTheDeckIsRefusedWithoutRoomForIt() throws IOException {
        String src = code(DECK);
        assertTrue(src.contains("public class AerialDeckBlock extends AerialBlock"), "everything an Aerial does");
        assertTrue(src.contains("builder.add(FACING, HALF);"), "the facing and the half");
        String place = body(src, "public BlockState getStateForPlacement(");
        assertTrue(place.contains("context.getClickedPos().relative(facing.getClockWise())"), "the right half to the placer's right");
        assertTrue(place.contains("canBeReplaced(context)") && place.contains("return null;"),
                "refused when that block is taken: half a deck is no deck");
        assertTrue(place.contains("setValue(HALF, DeckHalf.LEFT)"), "what is placed is the left half");
        String placed = body(src, "public void setPlacedBy(");
        assertTrue(placed.contains("level.setBlock(pos.relative(towardPartner(state)), state.setValue(HALF, DeckHalf.RIGHT)"),
                "and the right half goes in with it");
        String toward = body(src, "public static Direction towardPartner(");
        assertTrue(toward.contains("state.getValue(HALF) == DeckHalf.LEFT ? facing.getClockWise() : facing.getCounterClockWise()"),
                "the halves find each other either way");
    }

    @Test
    void breakingEitherHalfBreaksTheDeckAndOnlyTheLeftDrops() throws IOException {
        String src = code(DECK);
        String shape = body(src, "protected BlockState updateShape(");
        assertTrue(shape.contains("direction == towardPartner(state)"));
        assertTrue(shape.contains("Blocks.AIR.defaultBlockState()"), "a half whose partner is gone goes too");
        String creative = body(src, "public BlockState playerWillDestroy(");
        assertTrue(creative.contains("player.isCreative()") && creative.contains("DeckHalf.RIGHT")
                        && creative.contains("Block.UPDATE_SUPPRESS_DROPS"),
                "in creative, breaking the right half takes the left without a drop");
        JsonObject loot = json(RES.resolve("data/create_stats/loot_table/blocks/" + ID + ".json"));
        JsonObject pool = loot.getAsJsonArray("pools").get(0).getAsJsonObject();
        assertEquals("create_stats:" + ID, pool.getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString());
        boolean leftOnly = false;
        for (JsonElement c : pool.getAsJsonArray("conditions")) {
            JsonObject o = c.getAsJsonObject();
            if (o.get("condition").getAsString().equals("minecraft:block_state_property")) {
                leftOnly = o.getAsJsonObject("properties").get("half").getAsString().equals("left");
            }
        }
        assertTrue(leftOnly, "only the left half drops the deck: one item, whichever half was broken");
        String sneak = body(src, "public InteractionResult onSneakWrenched(");
        assertTrue(sneak.contains("master(state, context.getClickedPos())"), "a wrench takes the deck through its left half");
        String wrench = body(src, "public InteractionResult onWrenched(");
        assertFalse(wrench.contains("getRotatedBlockState") || wrench.contains("setBlock") || wrench.contains("super.onWrenched"),
                "a wrench never turns one half on its own");
    }

    @Test
    void eitherHalfOpensTheConsoleShowsTheGogglesAndFeedsADisplayLink() throws IOException {
        String src = code(DECK);
        assertTrue(body(src, "protected InteractionResult useWithoutItem(").contains("super.useWithoutItem(state, level, master(state, pos), player, hit)"),
                "the console and the leave mode from either half");
        assertTrue(src.contains("implements IProxyHoveringInformation"), "the goggles read the Aerial from either half");
        assertTrue(body(src, "public BlockPos getInformationSource(").contains("return master(state, pos);"));
        String be = body(src, "public BlockEntity newBlockEntity(");
        assertTrue(be.contains("if (state.getValue(HALF) == DeckHalf.LEFT) return super.newBlockEntity(pos, state);"),
                "one Aerial, in the left half: a second would stand down the first (yields)");
        assertTrue(be.contains("new AerialDeckPartBE(ModBlockEntities.VCC_MK4_AERIAL_DECK_PART.get(), pos, state)"));
        String part = code(PART);
        assertTrue(part.contains("implements AerialTelemetrySource"), "a Display Link pointed at the right half reads the Aerial");
        assertTrue(body(part, "public AerialSnapshot aerialSnapshot()").contains("aerial.aerialSnapshot()"));
        assertTrue(body(part, "public boolean onAerialCommand(").contains("aerial.onAerialCommand(command)"));
        assertFalse(part.contains("saveAdditional"), "it owns nothing to save");
        assertTrue(body(src, "protected VoxelShape getShape(").contains("LEFT_SHAPES : RIGHT_SHAPES"));
        assertTrue(src.contains("shapes(CockpitShape.LEFT)") && src.contains("shapes(CockpitShape.RIGHT)"),
                "each half's own boxes, which CockpitShapeTest holds to its half of the model");
    }

    @Test
    void theAerialsRendererDrawsTheWholeDeckFromItsLeftHalf() throws IOException {
        String src = code(RENDERER);
        assertTrue(src.contains("boolean deck = state.getBlock() instanceof AerialDeckBlock;"));
        assertTrue(src.contains("ms.translate(DECK_FIRST_OFFICER, 0.0f, 0.0f);"), "the first officer's set, a block right");
        assertTrue(src.contains("CachedBuffers.partial(ModPartialModels.AERIAL_DECK_TRIM_WHEEL, state)")
                && src.contains(".rotateDegrees((float) f.trim, Direction.EAST)"), "the trim wheel turned by the pitch trim");
        String box = body(src, "public AABB getRenderBoundingBox(");
        assertTrue(box.contains("getClockWise()"), "the render box covers the right half, or its side vanishes with the left");
        assertTrue(code(PARTIALS).contains("AERIAL_DECK_TRIM_WHEEL = block(\"" + ID + "/trim_wheel\")"));
        assertTrue(Files.exists(ASSETS.resolve("models/block/" + ID + "/trim_wheel.json")));
    }

    @Test
    void theBlockstateHasBothHalvesAtEveryFacingAndTheItemShowsTheDeck() throws IOException {
        JsonObject variants = json(ASSETS.resolve("blockstates/" + ID + ".json")).getAsJsonObject("variants");
        assertEquals(8, variants.size(), "facing by half");
        String[][] facings = {{"north", "0"}, {"east", "90"}, {"south", "180"}, {"west", "270"}};
        for (String[] f : facings) {
            for (String half : new String[]{"left", "right"}) {
                JsonObject v = variants.getAsJsonObject("facing=" + f[0] + ",half=" + half);
                assertEquals("create_stats:block/" + ID + "/body_" + half, v.get("model").getAsString());
                assertEquals(Integer.parseInt(f[1]), v.has("y") ? v.get("y").getAsInt() : 0, f[0] + " " + half);
            }
        }
        assertEquals("create_stats:block/" + ID + "/item", json(ASSETS.resolve("models/item/" + ID + ".json")).get("parent").getAsString());
        JsonArray rows = json(RES.resolve("data/minecraft/tags/block/mineable/pickaxe.json")).getAsJsonArray("values");
        boolean pick = false;
        for (JsonElement e : rows) pick |= e.getAsString().equals("create_stats:" + ID);
        assertTrue(pick, "mined with a pickaxe, as the Aerial is");
        JsonObject recipe = json(RES.resolve("data/create_stats/recipe/" + ID + ".json"));
        assertEquals("create_stats:" + ID, recipe.getAsJsonObject("result").get("id").getAsString());
    }

    @Test
    void theDeckHasItsNameAndItsThreeTooltipLines() throws IOException {
        String lang = read(ASSETS.resolve("lang/en_us.json"));
        for (String suffix : new String[]{"", ".tooltip", ".tooltip.placement", ".tooltip.usage"}) {
            assertTrue(lang.contains("\"block.create_stats." + ID + suffix + "\":"), "lang " + suffix);
        }
        assertTrue(lang.contains("\"block.create_stats." + ID + "\": \"VCC Mk IV Aerial Flight Deck\""), "the family's name");
        assertTrue(code(DECK).contains("\"block.create_stats." + ID + "\""), "the deck's own tooltip");
    }
}
