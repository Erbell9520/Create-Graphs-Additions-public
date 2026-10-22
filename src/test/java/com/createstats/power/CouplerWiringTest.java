package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CouplerWiringTest {
    private static final Path BE = Path.of("src/main/java/com/createstats/blockentity/UniversalPowerCouplerBE.java");
    private static final Path BLOCK = Path.of("src/main/java/com/createstats/block/UniversalPowerCouplerBlock.java");
    private static final Path BUFFER = Path.of("src/main/java/com/createstats/power/CouplerBuffer.java");
    private static final Path LOOT =
            Path.of("src/main/resources/data/create_stats/loot_table/blocks/universal_power_coupler.json");

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    static String body(String src, String signature) {
        final int at = src.indexOf(signature);
        if (at < 0) {
            return null;
        }
        final int open = src.indexOf('{', at);
        int depth = 0;
        for (int i = open; i < src.length(); i++) {
            final char c = src.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return src.substring(open, i + 1);
            }
        }
        return null;
    }

    private static String need(String src, String signature) {
        final String b = body(src, signature);
        assertTrue(b != null, "cannot find " + signature);
        return b;
    }

    @Test
    @DisplayName("the goggles read synced facts only, never a server-side helper (KI-203, INV-15)")
    void gogglesReadNoServerHelper() throws IOException {
        final String goggles = need(read(BE), "public boolean addToGoggleTooltip(");
        for (String helper : new String[] {"surveyNetwork", "pullSourceCount", "acceptanceFePerTick",
                "drainNetwork", "isForeignSeam", "isSeamTarget", "foreignVerdict", "farVerdict", "getCapability",
                "NewAgeCompatHelper.", "NewAgeFeed.", "theirSurvey"}) {
            assertFalse(goggles.contains(helper), "addToGoggleTooltip calls " + helper
                    + ", which answers nothing on a client: the goggles would read a confident zero");
        }
    }

    @Test
    @DisplayName("every field the goggles read is written unconditionally and read back (INV-15)")
    void everyGoggleFieldRidesTheWire() throws IOException {
        final String src = read(BE);
        final String goggles = need(src, "public boolean addToGoggleTooltip(");
        final String write = need(src, "protected void write(CompoundTag tag");
        final String read = need(src, "protected void read(CompoundTag tag");
        assertFalse(write.contains("if (!clientPacket") || write.contains("if (clientPacket"),
                "write() holds a field back from the client packet: the goggles and the renderer read every one");
        final Set<String> fields = new LinkedHashSet<>();
        final Matcher m = Pattern.compile("this\\.([A-Za-z_][A-Za-z0-9_]*)\\b(?!\\s*\\()").matcher(goggles);
        while (m.find()) {
            fields.add(m.group(1));
        }
        fields.remove("level");
        fields.remove("worldPosition");
        assertTrue(fields.size() >= 8, "the scan found too few goggle fields to be reading the method: " + fields);
        for (String f : fields) {
            assertTrue(write.contains("this." + f), f + " is on the goggles and never written to the update tag");
            assertTrue(read.contains("this." + f), f + " is written to the update tag and never read back");
        }
    }

    @Test
    @DisplayName("a mode, loop or lock flip re-floods every attached circuit (KI-209)")
    void roleFlipsReflood() throws IOException {
        final String src = read(BE);
        assertTrue(need(src, "private void reflood(").contains(".markDirty()"), "reflood marks nothing dirty");
        assertTrue(need(src, "public void leverThrown(").contains("this.reflood()"), "the lever's flip re-floods nothing");
        assertTrue(need(src, "public void beforeTurn(").contains("this.reflood()"), "a wrench turn re-floods nothing");
        final String tick = need(src, "public void tick(");
        final int flag = tick.indexOf("if (roleChanged)");
        assertTrue(flag > 0 && tick.indexOf("this.reflood();", flag) > flag,
                "a role flip noticed in the tick re-floods nothing");
        assertTrue(tick.indexOf("roleChanged = true", tick.indexOf("this.seenMode != mode")) > 0,
                "a mode set elsewhere is not a role change");
        assertTrue(tick.indexOf("roleChanged = true", tick.indexOf("forcedOff != this.seenForcedOff")) > 0,
                "a lock flip is not a role change");
        assertTrue(tick.contains("roleChanged |= this.survey(now)"), "a loop flip is not a role change");
    }

    @Test
    @DisplayName("a seam span appearing or going invalidates the capability and tells the neighbours (KI-219)")
    void seamChangeTellsNeighbours() throws IOException {
        final String src = read(BE);
        final String changed = need(src, "private void linksChanged(");
        final int ifSeam = changed.indexOf("if (seamChanged)");
        assertTrue(ifSeam > 0 && changed.indexOf("this.tellNeighbours()", ifSeam) > ifSeam,
                "a seam link change tells nobody");
        final String tell = need(src, "private void tellNeighbours(");
        assertTrue(tell.contains("invalidateCapabilities") && tell.contains("updateNeighborsAt"),
                "tellNeighbours must invalidate the capability and notify every neighbour");
    }

    @Test
    @DisplayName("nothing subtracts from a field that starts as a never sentinel (KI-202)")
    void noSentinelSubtraction() throws IOException {
        for (Path p : new Path[] {BE, BUFFER}) {
            final String src = read(p);
            final Matcher decl = Pattern.compile(
                    "(?:private|protected|public)\\s+long\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(?:CouplerSyncGate\\.NEVER|Long\\.MIN_VALUE)")
                    .matcher(src);
            int found = 0;
            while (decl.find()) {
                found++;
                final String field = decl.group(1);
                final Matcher minus = Pattern.compile("-\\s*(?:this\\.)?" + field + "\\b").matcher(src);
                assertFalse(minus.find(), p.getFileName() + " subtracts " + field
                        + ", which starts at Long.MIN_VALUE: the difference wraps and a gate never opens");
            }
            assertTrue(found > 0, p.getFileName() + ": no sentinel fields found, the scan is reading nothing");
        }
    }

    @Test
    @DisplayName("the store goes with the item, and the loot table carries it (KI-217)")
    void theStoreGoesWithTheItem() throws IOException {
        final String src = read(BE);
        final String collect = need(src, "protected void collectImplicitComponents(");
        assertTrue(collect.contains("builder.set(ModDataComponents.STORED_FE.get(), fe)")
                && collect.contains("this.buffer.energy()"), "the store is not put on the item");
        final String apply = need(src, "protected void applyImplicitComponents(");
        final int got = apply.indexOf("input.get(ModDataComponents.STORED_FE.get())");
        assertTrue(got > 0 && apply.indexOf("this.buffer.setEnergy(", got) > got, "the item's store is not placed");
        final String loot = read(LOOT);
        assertTrue(loot.contains("\"minecraft:copy_components\"") && loot.contains("\"block_entity\"")
                && loot.contains("\"create_stats:stored_fe\""), "the loot table drops the store");
    }

    @Test
    @DisplayName("the wrench turns the block and never the mode; the lever flips the mode only on the lever (KI-218)")
    void wrenchTurnsLeverFlips() throws IOException {
        final String src = read(BLOCK);
        final String wrench = need(src, "public InteractionResult onWrenched(");
        assertTrue(wrench.contains("setValue(FACING"), "the wrench no longer turns the Coupler");
        assertFalse(wrench.contains("setValue(MODE"), "the wrench changes the mode again");
        final String lever = need(src, "protected InteractionResult useWithoutItem(");
        final int hit = lever.indexOf("CouplerLever.hit(");
        assertTrue(hit > 0 && lever.indexOf("setValue(MODE", hit) > hit,
                "the mode flips without the click being on the lever");
        assertTrue(lever.contains("leverThrown()"), "the throw is not dated, so it neither animates nor syncs at once");
        final String place = need(src, "public BlockState getStateForPlacement(");
        assertTrue(place.contains("clicked.getOpposite()"),
                "a side click no longer puts the foreign end against the clicked block");
    }

    @Test
    @DisplayName("the mode-blind store is served to a bolted New Age connector only; everyone else gets the strict port (KI-208)")
    void portsByEndpoint() throws IOException {
        final String handle = need(read(BE), "public IEnergyStorage seamHandle(");
        assertTrue(handle.contains("case NEW_AGE_CONNECTOR -> this.newAgePort"), "New Age lost its reversible port");
        assertTrue(handle.indexOf("this.newAgePort") == handle.lastIndexOf("this.newAgePort"),
                "the mode-blind port is served to more than one kind of endpoint");
        assertTrue(handle.contains("this.strictPort"), "no strict port for everyone else");

        final int valid = handle.indexOf("this.faceVerdictValid = true");
        assertTrue(valid > 0 && valid < handle.indexOf("NewAgeFeed.foreignVerdict("),
                "the face verdict is computed before it is marked valid: a re-entrant query recurses");
    }

    @Test
    @DisplayName("the bolted pump never drives New Age; the grid pump skips our members; force off stops both ways")
    void pumpsAndLock() throws IOException {
        final String src = read(BE);
        final String bolted = need(src, "private void pumpBolted(");
        assertTrue(bolted.contains("CouplerEndpoint.BOLTED_GENERIC"), "the bolted pump lost its generic case (KI-207)");
        assertFalse(bolted.contains("BOLTED_NEW_AGE"),
                "the bolted pump names New Age: their network must never be driven from our tick (contract 7)");
        final String through = need(src, "private void pumpThrough(");
        final int member = through.indexOf("instanceof FeGridMember");
        assertTrue(member > 0 && member < through.indexOf("getCapability"),
                "the grid face pump serves our own members a second time (KI-225)");
        assertTrue(need(src, "private boolean canGridGive(").contains("isForcedOff()"),
                "force off does not stop an importing Coupler (KI-220)");
        assertTrue(need(src, "public int ratedDrawFePerTick(").contains("CouplerPrice.pushPrice("),
                "the export is priced at the rating again, whatever their side takes (KI-211)");
    }

    @Test
    @DisplayName("OFF, the lever's middle detent: no pump moves, no loop, and the direction comes back")
    void offShutsAndRemembers() throws IOException {
        final String src = read(BE);
        for (String pump : new String[] {"private void pumpSeamWire(", "private void pumpBolted("}) {
            final String b = need(src, pump);
            final int gate = b.indexOf("!this.mode().isDirection()");
            assertTrue(gate > 0 && gate < b.indexOf("return;"),
                    pump + " does not stop for OFF: its PULL-else-PUSH branch would export while switched off");
        }
        final String survey = need(src, "private boolean survey(");
        final int dir = survey.indexOf("if (this.mode().isDirection())");
        assertTrue(dir > 0 && dir < survey.indexOf("publishAndAsk(") && survey.indexOf(".forget(", dir) > dir,
                "an OFF Coupler still takes part in the loop guard");
        final String tick = need(src, "public void tick(");
        assertTrue(tick.indexOf("this.remember(mode)", tick.indexOf("this.seenMode != mode")) > 0,
                "a direction set elsewhere is not remembered");
        assertTrue(need(src, "public void leverThrown(").contains("this.remember(mode)"),
                "the lever's direction is not remembered");
        assertTrue(need(src, "public void writeSafe(").contains("tag.putString(\"Direction\""),
                "a printed Coupler loses the direction it was built for");
        assertTrue(need(src, "protected void write(CompoundTag tag").contains("tag.putString(\"Direction\"")
                && need(src, "protected void read(CompoundTag tag").contains("tag.getString(\"Direction\")"),
                "the remembered direction is not saved and loaded");
        final String lever = need(read(BLOCK), "protected InteractionResult useWithoutItem(");
        assertTrue(lever.contains(".click(") && lever.contains("rememberedDirection()")
                        && lever.contains("isShiftKeyDown()"),
                "the lever does not use the three-detent rule with the remembered direction and sneak");
    }
}
