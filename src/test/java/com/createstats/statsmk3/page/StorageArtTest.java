package com.createstats.statsmk3.page;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.createstats.statsmk3.RasterSurface;
import com.createstats.statsmk3.page.StorageReadings.Kind;
import com.createstats.statsmk3.page.StorageReadings.Vessel;
import com.createstats.statsmk3.paint.Lod;
import com.createstats.statsmk3.paint.PanelModel;
import com.createstats.statsmk3.paint.StorageFacePainter;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Tokens;
import com.createstats.util.PreviewImages;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageArtTest {
    private static final File OUT = new File("fixture_preview");
    private static final int W = 620;
    private static final int H = 400;

    @Test
    void rendersEveryKindOfStore() throws IOException {
        MachinePageArtTest.ensureOut();
        final BufferedImage sheet = MachinePageArtTest.sheet(3 * (W + 24) + 24, 2 * (H + 44) + 24);
        final Graphics2D g = sheet.createGraphics();

        place(g, hero(vault(), 0.0f), 0, 0, "Vault behind an observer / contents breakdown");
        place(g, hero(stockpileSwitch(), 0.0f), 1, 0, "Stockpile Switch / thresholds and signal");
        place(g, hero(tank(), 0.35f), 2, 0, "Fluid tank / draining, agitated surface");

        place(g, hero(bank(), 0.7f), 0, 1, "Capacitor bank / charging cells");
        place(g, hero(observerNoCapacity(), 0.2f), 1, 1, "Amount only / no capacity to be a % of");
        place(g, hero(fullVault(), 0.5f), 2, 1, "Vault at 100% / backed up");

        g.dispose();
        PreviewImages.write(sheet, "png", new File(OUT, "mk3_page_storage.png"));
    }

    @Test
    void rendersRosterFocusAndCompare() throws IOException {
        MachinePageArtTest.ensureOut();
        final int w = 1000;
        final int h = 620;
        final BufferedImage sheet = MachinePageArtTest.sheet(2 * (w + 24) + 24, 2 * (h + 44) + 24);
        final Graphics2D g = sheet.createGraphics();

        placeAt(g, roster(6, PeerRoster.Mode.ROSTER, null, w, h), 0, 0, w, h,
                "Roster / six stores of three kinds, worst first");
        placeAt(g, roster(6, PeerRoster.Mode.FOCUS, null, w, h), 1, 0, w, h,
                "Focus / hero plus the chip rail");
        placeAt(g, roster(6, PeerRoster.Mode.COMPARE, null, w, h), 0, 1,
                w, h, "Compare / three on one percentage axis");
        placeAt(g, roster(6, PeerRoster.Mode.ROSTER, Kind.FLUID, w, h), 1, 1,
                w, h, "Filtered to fluid by the category chip");

        g.dispose();
        PreviewImages.write(sheet, "png", new File(OUT, "mk3_page_storage_roster.png"));
    }

    @Test
    void rendersTheBoard() throws IOException {
        MachinePageArtTest.ensureOut();
        final int[][] sizes = {{1, 1}, {2, 2}, {4, 3}};
        final Lod[] levels = {Lod.FULL, Lod.REDUCED, Lod.GLYPH};
        final int cell = 240;

        final BufferedImage sheet = MachinePageArtTest.sheet(
                48 + levels.length * (cell + 200), 60 + sizes.length * (cell + 130));
        final Graphics2D g = sheet.createGraphics();
        MachinePageArtTest.caption(g,
                "Storage block face. Rows are assembly size; columns are detail level.", 24, 22);

        for (int row = 0; row < sizes.length; row++) {
            final int y = 48 + row * (cell + 130);
            MachinePageArtTest.caption(g, sizes[row][0] + " x " + sizes[row][1], 24, y - 8);
            for (int i = 0; i < levels.length; i++) {
                final int fw = 64 * sizes[row][0] - 16;
                final int fh = 64 * sizes[row][1] - 20;
                final RasterSurface s = new RasterSurface(fw, fh, 0x05070A);
                StorageFacePainter.paint(s, new Rect(0.0f, 0.0f, fw, fh), boardModel(""), levels[i]);
                g.drawImage(s.finish(), 24 + i * (cell + 200), y, null);
            }
        }
        g.dispose();
        PreviewImages.write(sheet, "png", new File(OUT, "mk3_face_storage.png"));
    }

    @Test
    void aStockpileSwitchTakesTheKindOfWhatItWatches() {
        assertEquals(List.of("items"), StorageReadings.variantsOf(stockpileSwitch()));
        assertEquals(List.of("fluid"), StorageReadings.variantsOf(fluidStockpileSwitch()));

        final Vessel fluid = StorageReadings.read(fluidStockpileSwitch(), "fluid");
        assertEquals(Kind.FLUID, fluid.kind());
        assertTrue(fluid.amountText().endsWith(" mB"), "a fluid switch must print millibuckets");
    }

    @Test
    void oneMachineCanHoldTwoKinds() {
        final SourceProbe.Unit both = MachinePageArtTest.unit("Content Observer", "Depot",
                MachinePageArtTest.metric("total_items", 1_800.0f),
                MachinePageArtTest.metric("slot_capacity", 3_456.0f),
                MachinePageArtTest.metric("unique_types", 4.0f),
                MachinePageArtTest.metric("fluid_amount", 6_000.0f),
                MachinePageArtTest.metric("tank_capacity", 8_000.0f));
        assertEquals(List.of("items", "fluid"), StorageReadings.variantsOf(both));

        final Vessel items = StorageReadings.read(both, "items");
        final Vessel fluid = StorageReadings.read(both, "fluid");
        assertEquals(1_800.0f, items.level(), 0.5f);
        assertEquals(6_000.0f, fluid.level(), 0.5f);
        assertEquals(75.0f, fluid.pct(), 0.5f, "fluid percent must not be the item percent");

        final Peers.Peer a = new Peers.Peer(both, 0, "", "items");
        final Peers.Peer b = new Peers.Peer(both, 1, "", "fluid");
        assertEquals(a.sourceId, b.sourceId);
        assertFalse(a.key.equals(b.key), "two stores in one machine must not share a roster key");
    }

    @Test
    void thePageIsRegistered() {
        boolean found = false;
        for (final Mk3Page page : Mk3PageRegistry.all()) {
            found |= "storage".equals(page.id());
        }
        assertTrue(found, "no panel can ever show a page the registry does not know about");
    }

    @Test
    void machinesThatHoldNothingAreNotStores() {
        assertTrue(StorageReadings.variantsOf(MachinePageArtTest.unit("Stressometer", "Gauge",
                MachinePageArtTest.metric("stress_su", 1_400.0f),
                MachinePageArtTest.metric("capacity_su", 2_048.0f))).isEmpty());

        assertTrue(StorageReadings.variantsOf(MachinePageArtTest.unit("Content Observer", "Belt",
                MachinePageArtTest.metric("observer_active", 1.0f))).isEmpty());
    }

    @Test
    void aGridAggregateIsNotAStore() {
        assertTrue(StorageReadings.variantsOf(MachinePageArtTest.unit("Power Connector", "Main Bus",
                MachinePageArtTest.metric("energy_stored", 1_480_000.0f),
                MachinePageArtTest.metric("energy_capacity", 2_400_000.0f),
                MachinePageArtTest.metric("network_pushed", 760.0f))).isEmpty());

        assertEquals(List.of("energy"), StorageReadings.variantsOf(bank()));
    }

    @Test
    void aStatedPercentageImpliesTheCapacity() {
        final Vessel v = StorageReadings.read(MachinePageArtTest.unit("Fluid Tank", "Cistern",
                MachinePageArtTest.metric("fluid_amount", 5_600.0f),
                MachinePageArtTest.metric("tank_fill", 70.0f)), "fluid");
        assertTrue(v.hasCapacity(), "a level and its share are a capacity");
        assertEquals(8_000.0f, v.capacity(), 1.0f);
        assertEquals(70.0f, v.pct(), 0.01f);

        final Vessel noise = StorageReadings.read(MachinePageArtTest.unit("Fluid Tank", "Drip",
                MachinePageArtTest.metric("fluid_amount", 4.0f),
                MachinePageArtTest.metric("tank_fill", 0.02f)), "fluid");
        assertFalse(noise.hasCapacity());
    }

    @Test
    void anAmountOnlyStoreHasNoPercentage() {
        final Vessel v = StorageReadings.read(observerNoCapacity(), "items");
        assertFalse(v.hasCapacity());
        assertEquals(0, v.percentSeries().length, "no capacity means no percentage series");
        assertNull(v.etaSeconds(), "there is no end to forecast towards");

        assertEquals(u(observerNoCapacity(), "total_items"), v.level(), 0.01f);
    }

    @Test
    void thresholdsUseTheSwitchesOwnRange() {
        final Vessel v = StorageReadings.read(stockpileSwitch(), "items");
        assertTrue(v.hasThresholds());

        assertEquals((512.0f - 128.0f) / (2_048.0f - 128.0f) * 100.0f, v.lowerPct(), 0.05f);
        assertEquals((1_792.0f - 128.0f) / (2_048.0f - 128.0f) * 100.0f, v.upperPct(), 0.05f);
        assertTrue(v.hasSignal());
    }

    @Test
    void energyIsTheOneKindWhereFullIsGood() {
        assertEquals(Tokens.GOOD, StorageReadings.read(bank(), "energy").statusColor());
        assertEquals(Tokens.CRITICAL, StorageReadings.read(fullVault(), "items").statusColor());
        assertEquals(Tokens.CRITICAL,
                StorageReadings.read(flatBank(), "energy").statusColor());
        assertFalse(StorageReadings.read(bank(), "energy").alarming());
        assertTrue(StorageReadings.read(fullVault(), "items").alarming());
    }

    @Test
    void aStoreAboutToRunOutOutranksOneMerelySittingHigh() {
        final Vessel emptying = StorageReadings.read(tank(), "fluid");
        final Vessel parked = StorageReadings.read(parkedVault(), "items");
        assertNotNull(emptying.etaSeconds(), "the draining tank must have a forecast");
        assertTrue(emptying.urgency() > parked.urgency(),
                "a tank emptying in a minute must outrank a vault parked at 82%");
    }

    @Test
    void eitherSpellingOfAnItemCapacityCounts() {
        for (final String metric : new String[]{"slot_capacity", "item_capacity"}) {
            final Vessel v = StorageReadings.read(MachinePageArtTest.unit("Container", metric,
                    MachinePageArtTest.metric("total_items", 6_912.0f),
                    MachinePageArtTest.metric(metric, 27_648.0f)), "items");
            assertTrue(v.hasCapacity(), metric + " was not read as a capacity");
            assertEquals(25.0f, v.pct(), 0.01f);
        }
    }

    @Test
    void aContainerLinkedDirectlyIsANamedStore() {
        final SourceProbe.Unit u = MachinePageArtTest.unit(
                com.createstats.display.StatsDisplayTarget.CONTAINER_SOURCE_TYPE, "direct-barrel",
                MachinePageArtTest.metric("total_items", 640.0f),
                MachinePageArtTest.metric("slot_capacity", 1_728.0f),
                MachinePageArtTest.metric("unique_types", 2.0f),
                MachinePageArtTest.metric("item_minecraft_oak_log", 512.0f),
                MachinePageArtTest.metric("item_minecraft_stick", 128.0f));
        u.config().setUserLabel("");
        u.config().setObservedTarget(new BlockPos(3, 64, 8), "Barrel");

        assertEquals(List.of("items"), StorageReadings.variantsOf(u));
        assertEquals("Barrel", StorageReadings.nameOf(u.config(), false),
                "a card names the container, not the category it was linked under");
        assertEquals("factory.container", com.createstats.display
                .CustomScreenSourceFamilyCatalog.familyForSource(u.config()).getFamilyId(),
                "an unregistered id would fall to generic.unknown and lose its compact policy");

        final Vessel v = StorageReadings.read(u, "items");
        assertTrue(v.hasCapacity());
        assertEquals(2, v.items().size());
    }

    @Test
    void itemCountsAreWholeThings() {
        final Vessel items = StorageReadings.read(vault(), "items");
        assertEquals("1", items.compact(1.0f));
        assertEquals("65", items.compact(65.0f));
        assertEquals("16 258", items.compact(16_258.0f));

        assertEquals("65.4", StorageReadings.read(tank(), "fluid").compact(65.4f));
    }

    @Test
    void categoriesTotalQuantitiesAndNotPercentages() {
        final List<Vessel> vessels = List.of(
                StorageReadings.read(vault(), "items"),
                StorageReadings.read(fullVault(), "items"),
                StorageReadings.read(tank(), "fluid"));
        final List<StorageArt.Category> categories = StorageArt.categorise(vessels);
        assertEquals(2, categories.size());
        assertEquals(Kind.ITEMS, categories.get(0).kind());
        assertEquals(2, categories.get(0).count());
        assertEquals(Tokens.CRITICAL, categories.get(0).worstColor(),
                "a category reports its worst member, not its average");

        final Vessel a = StorageReadings.read(vault(), "items");
        final Vessel b = StorageReadings.read(fullVault(), "items");
        assertEquals((a.pct() + b.pct()) * 0.5f, categories.get(0).pct(), 0.01f);
        assertEquals(Kind.FLUID, categories.get(1).kind());
    }

    @Test
    void anUnmeasuredStoreDoesNotInflateItsKind() {
        final List<Vessel> vessels = List.of(
                StorageReadings.read(stockpileSwitch(), "items"),
                StorageReadings.read(observerNoCapacity(), "items"));
        final StorageArt.Category items = StorageArt.categorise(vessels).get(0);
        assertEquals(2, items.count(), "both stores are still linked and still counted");
        assertTrue(items.pct() <= 100.0f);
        assertEquals(StorageReadings.read(stockpileSwitch(), "items").pct(), items.pct(), 0.01f,
                "with one measurable store the kind's share is exactly that store's own reading");
    }

    @Test
    void everyCardStaysOnThePage() {
        for (final PeerRoster.Mode mode : PeerRoster.Mode.values()) {
            final PeerRoster roster = seeded(StorageReadings.ROSTER_CAP, mode, null);
            final Rect body = roster.heroRect();
            for (final Peers.Peer p : roster.peers()) {
                final Rect r = roster.rectOf(p);
                assertTrue(r.bottom() <= body.bottom() + 0.6f,
                        p.numeral + " runs past the page in " + mode + ": " + r);
                assertTrue(r.h >= 30.0f, p.numeral + " collapsed to " + r.h + " in " + mode);
            }
        }
    }

    @Test
    void nineUpStaysWithinTheTriangleBudget() {
        final com.createstats.statsmk3.CountingSurface counter =
                new com.createstats.statsmk3.CountingSurface();
        final PeerRoster roster = seeded(StorageReadings.ROSTER_CAP, PeerRoster.Mode.ROSTER, null);
        paint(counter, roster, new Rect(24.0f, 24.0f, 980.0f, 600.0f), null);
        assertTrue(counter.triangles < 9446, "nine up storage roster drew " + counter.triangles
                + " triangles, over the Instruments page budget");
    }

    @Test
    void degradesRatherThanOverlapping() throws IOException {
        MachinePageArtTest.ensureOut();
        final BufferedImage sheet = MachinePageArtTest.sheet(3 * (W + 24) + 24, 220 + 44);
        final Graphics2D g = sheet.createGraphics();

        final RasterSurface cramped = new RasterSurface(W, 200, 0x111318);
        paint(cramped, seeded(4, PeerRoster.Mode.ROSTER, null, W, 200),
                new Rect(12.0f, 12.0f, W - 24.0f, 200.0f - 24.0f), null);
        g.drawImage(cramped.finish(), 24, 44, null);
        MachinePageArtTest.caption(g, "Roster cramped to 200px", 24, 36);

        final RasterSurface none = new RasterSurface(W, 200, 0x111318);
        StorageArt.empty(none, new Rect(12.0f, 12.0f, W - 24.0f, 200.0f - 24.0f));
        g.drawImage(none.finish(), 24 + W + 24, 44, null);
        MachinePageArtTest.caption(g, "Nothing linked", 24 + W + 24, 36);

        final RasterSurface filtered = new RasterSurface(W, 200, 0x111318);
        StorageArt.emptyFilter(filtered, new Rect(12.0f, 12.0f, W - 24.0f, 200.0f - 24.0f),
                Kind.ENERGY);
        g.drawImage(filtered.finish(), 24 + 2 * (W + 24), 44, null);
        MachinePageArtTest.caption(g, "Filtered to a kind nothing holds", 24 + 2 * (W + 24), 36);

        g.dispose();
        PreviewImages.write(sheet, "png", new File(OUT, "mk3_page_storage_degraded.png"));
    }

    private static BufferedImage hero(SourceProbe.Unit unit, float time) {
        final RasterSurface s = new RasterSurface(W, H, 0x111318);
        final String variant = StorageReadings.variantsOf(unit).get(0);
        final Peers.Peer peer = new Peers.Peer(unit, 0, "24m NE, y+3", variant);
        final Vessel v = StorageReadings.read(unit, variant);
        final StorageArt.PeerArt art = new StorageArt.PeerArt(peer.numeral,
                StorageReadings.nameOf(unit.config(), false), peer.bearing, peer.series, false,
                v.pct(), v.pct(), v.level(), v);

        StorageArt.header(s, new Rect(16.0f, 12.0f, W - 32.0f, StorageArt.HEADER_H),
                StorageArt.categorise(List.of(v)), List.of(art), null, 0.0f, 0);
        StorageArt.paintPeer(s, art,
                new Rect(16.0f, 12.0f + StorageArt.HEADER_H, W - 32.0f,
                        H - 24.0f - StorageArt.HEADER_H),
                1.0f, 1.0f, false, time);
        return s.finish();
    }

    private static BufferedImage roster(int n, PeerRoster.Mode mode, Kind filter, int w, int h) {
        final RasterSurface s = new RasterSurface(w, h, 0x111318);
        paint(s, seeded(n, mode, filter, w, h), new Rect(24.0f, 24.0f, w - 48.0f, h - 48.0f),
                filter);
        return s.finish();
    }

    private static void paint(PanelSurface s, PeerRoster roster, Rect area, Kind filter) {
        final List<StorageArt.PeerArt> arts = new ArrayList<>();
        final List<Vessel> vessels = new ArrayList<>();
        for (final Peers.Peer p : roster.peers()) {
            arts.add(art(p, roster));
            vessels.add(vesselOf(p));
        }
        StorageArt.header(s, area, StorageArt.categorise(vessels), arts, filter, 224.0f, 0);
        if (roster.mode() == PeerRoster.Mode.COMPARE) {
            final List<StorageArt.PeerArt> compared = new ArrayList<>();
            for (final Peers.Peer p : roster.compared()) {
                compared.add(art(p, roster));
            }
            StorageArt.compareChart(s, roster.compareChartRect(), compared);
        }
        roster.draw(s, new Probe());
    }

    private static StorageArt.PeerArt art(Peers.Peer p, PeerRoster roster) {
        final Vessel v = vesselOf(p);
        return new StorageArt.PeerArt(p.numeral,
                StorageReadings.nameOf(p.unit.config(), false), p.bearing, p.series,
                roster.isCompared(p), v.pct(), v.pct(), v.level(), v);
    }

    private static Vessel vesselOf(Peers.Peer p) {
        return StorageReadings.read(p.unit, p.variant);
    }

    private static final class Probe implements PeerRoster.Delegate {
        @Override
        public void paintPeer(PanelSurface c, Peers.Peer peer, Rect r, float big, float mid,
                              boolean hovered, float alpha) {
            final Vessel v = vesselOf(peer);
            StorageArt.paintPeer(c, new StorageArt.PeerArt(peer.numeral,
                    StorageReadings.nameOf(peer.unit.config(), false), peer.bearing, peer.series,
                    false, v.pct(), v.pct(), v.level(), v), r, big, mid, hovered, 0.0f);
        }

        @Override
        public float urgency(Peers.Peer peer) {
            return vesselOf(peer).urgency();
        }

        @Override
        public int statusColor(Peers.Peer peer) {
            return vesselOf(peer).statusColor();
        }

        @Override
        public float[] compareSeries(Peers.Peer peer) {
            return vesselOf(peer).percentSeries();
        }

        @Override
        public boolean alarming(Peers.Peer peer) {
            return vesselOf(peer).alarming();
        }
    }

    private static PeerRoster seeded(int n, PeerRoster.Mode mode, Kind filter) {
        return seeded(n, mode, filter, 1000, 620);
    }

    private static PeerRoster seeded(int n, PeerRoster.Mode mode, Kind filter, int w, int h) {
        PeerFocusMemory.clear();
        final Probe probe = new Probe();
        final PeerRoster roster = new PeerRoster("storage", new BlockPos(0, 64, 0));
        roster.setArea(new Rect(24.0f, 24.0f + StorageArt.HEADER_H,
                w - 48.0f, h - 48.0f - StorageArt.HEADER_H));
        roster.setPeers(peers(n, filter), probe);
        for (int i = 0; i < 90; i++) {
            roster.update(1.0f / 60.0f);
        }
        if (mode == PeerRoster.Mode.FOCUS && roster.size() > 1) {
            roster.focusOn(roster.peers().get(1));
        } else if (mode == PeerRoster.Mode.COMPARE && roster.size() > 1) {
            roster.toCompare();
        }
        for (int i = 0; i < 120; i++) {
            roster.setPeers(roster.peers(), probe);
            roster.update(1.0f / 60.0f);
        }
        return roster;
    }

    private static List<Peers.Peer> peers(int n, Kind filter) {
        final SourceProbe.Unit[] units = {
                vault(), tank(), bank(), stockpileSwitch(), fullVault(), fluidStockpileSwitch(),
                observerNoCapacity(), parkedVault(), flatBank()};
        final BlockPos here = new BlockPos(0, 64, 0);
        final int[][] at = {{18, 2, -12}, {-7, 0, 1}, {4, -4, 31}, {12, 2, -9}, {-20, 1, 14},
                {2, -2, -9}, {44, 6, 3}, {11, 0, 11}, {0, 3, 5}};

        final List<Peers.Peer> out = new ArrayList<>();
        int index = 0;
        for (int i = 0; i < n && i < units.length; i++) {
            final SourceProbe.Unit u = units[i];
            u.config().setSourcePos(new BlockPos(at[i][0], 64 + at[i][1], at[i][2]));
            for (final String variant : StorageReadings.variantsOf(u)) {
                final Peers.Peer p = new Peers.Peer(u, index++, Peers.bearing(here,
                        u.config().getSourcePos()), variant);
                if (filter == null || Kind.of(variant) == filter) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    private static PanelModel boardModel(String kind) {
        final PanelModel model = new PanelModel();
        model.storageKind = kind;
        for (final Peers.Peer p : peers(9, null)) {
            model.storage.add(new StorageReadings.Store(
                    StorageReadings.nameOf(p.unit.config(), false), vesselOf(p)));
        }
        return model;
    }

    private static SourceProbe.Unit vault() {
        final SourceProbe.Unit u = MachinePageArtTest.unit("Content Observer", "bulk-vault",
                MachinePageArtTest.series("total_items",
                        MachinePageArtTest.ramp(120, 9_200.0f, 12_400.0f)),
                MachinePageArtTest.metric("slot_capacity", 27_648.0f),
                MachinePageArtTest.metric("unique_types", 6.0f),
                MachinePageArtTest.metric("item_minecraft_cobblestone", 7_420.0f),
                MachinePageArtTest.metric("item_create_andesite_alloy", 2_180.0f),
                MachinePageArtTest.metric("item_minecraft_iron_ingot", 1_460.0f),
                MachinePageArtTest.metric("item_minecraft_gravel", 880.0f),
                MachinePageArtTest.metric("item_create_zinc_ingot", 340.0f),
                MachinePageArtTest.metric("item_minecraft_flint", 120.0f));
        u.config().setUserLabel("");
        u.config().setObservedTarget(new BlockPos(18, 66, -12), "Vault");
        label(u, "item_minecraft_cobblestone", "Cobblestone");
        label(u, "item_create_andesite_alloy", "Andesite Alloy");
        label(u, "item_minecraft_iron_ingot", "Iron Ingot");
        label(u, "item_minecraft_gravel", "Gravel");
        label(u, "item_create_zinc_ingot", "Zinc Ingot");
        label(u, "item_minecraft_flint", "Flint");
        return u;
    }

    private static SourceProbe.Unit fullVault() {
        final SourceProbe.Unit u = MachinePageArtTest.unit("Content Observer", "overflow-vault",
                MachinePageArtTest.series("total_items",
                        MachinePageArtTest.ramp(120, 24_000.0f, 27_600.0f)),
                MachinePageArtTest.metric("slot_capacity", 27_648.0f),
                MachinePageArtTest.metric("unique_types", 2.0f),
                MachinePageArtTest.metric("item_minecraft_cobblestone", 26_900.0f),
                MachinePageArtTest.metric("item_minecraft_gravel", 700.0f));
        u.config().setUserLabel("");
        u.config().setObservedTarget(new BlockPos(-20, 65, 14), "Overflow Vault");
        label(u, "item_minecraft_cobblestone", "Cobblestone");
        label(u, "item_minecraft_gravel", "Gravel");
        return u;
    }

    private static SourceProbe.Unit parkedVault() {
        final SourceProbe.Unit u = MachinePageArtTest.unit("Content Observer", "Ingot Store",
                MachinePageArtTest.series("total_items",
                        MachinePageArtTest.ramp(120, 22_670.0f, 22_672.0f)),
                MachinePageArtTest.metric("slot_capacity", 27_648.0f),
                MachinePageArtTest.metric("unique_types", 1.0f),
                MachinePageArtTest.metric("item_minecraft_iron_ingot", 22_672.0f));
        label(u, "item_minecraft_iron_ingot", "Iron Ingot");
        return u;
    }

    private static SourceProbe.Unit stockpileSwitch() {
        final SourceProbe.Unit u = MachinePageArtTest.unit("Stockpile Switch", "Kiln Feed",
                MachinePageArtTest.series("stock_level",
                        MachinePageArtTest.ramp(120, 1_500.0f, 620.0f)),
                MachinePageArtTest.metric("stock_capacity", 2_048.0f),
                MachinePageArtTest.metric("min_level", 128.0f),
                MachinePageArtTest.metric("lower_thresh", 512.0f),
                MachinePageArtTest.metric("upper_thresh", 1_792.0f),
                MachinePageArtTest.metric("fill_pct", (620.0f - 128.0f) / (2_048.0f - 128.0f)
                        * 100.0f),
                MachinePageArtTest.metric("powered", 1.0f));
        type(u, "stock_level", com.createstats.data.MetricType.ITEM_COUNT);
        return u;
    }

    private static SourceProbe.Unit fluidStockpileSwitch() {
        final SourceProbe.Unit u = MachinePageArtTest.unit("Stockpile Switch", "Lava Reserve",
                MachinePageArtTest.series("stock_level",
                        MachinePageArtTest.ramp(120, 3_000.0f, 5_600.0f)),
                MachinePageArtTest.metric("stock_capacity", 8_000.0f),
                MachinePageArtTest.metric("min_level", 0.0f),
                MachinePageArtTest.metric("lower_thresh", 1_600.0f),
                MachinePageArtTest.metric("upper_thresh", 6_400.0f),
                MachinePageArtTest.metric("fill_pct", 70.0f),
                MachinePageArtTest.metric("powered", 0.0f));
        type(u, "stock_level", com.createstats.data.MetricType.FLUID_MB);
        return u;
    }

    private static SourceProbe.Unit tank() {
        final SourceProbe.Unit u = MachinePageArtTest.unit("Fluid Tank", "Lava Buffer",
                MachinePageArtTest.series("fluid_amount",
                        MachinePageArtTest.ramp(120, 27_000.0f, 5_400.0f)),
                MachinePageArtTest.metric("tank_capacity", 32_000.0f),
                MachinePageArtTest.metric("tank_fill", 16.9f),
                MachinePageArtTest.metric("fluid_lava", 5_400.0f));
        label(u, "fluid_lava", "Lava (mB)");
        return u;
    }

    private static SourceProbe.Unit bank() {
        return MachinePageArtTest.unit("Capacitor Bank", "Cellar Bank",
                MachinePageArtTest.series("energy_stored",
                        MachinePageArtTest.ramp(120, 1_050_000.0f, 2_180_000.0f)),
                MachinePageArtTest.metric("energy_capacity", 2_400_000.0f),
                MachinePageArtTest.metric("energy_net_rate", 620.0f));
    }

    private static SourceProbe.Unit flatBank() {
        return MachinePageArtTest.unit("Modular Accumulator", "Night Reserve",
                MachinePageArtTest.series("energy_stored",
                        MachinePageArtTest.ramp(120, 400_000.0f, 61_000.0f)),
                MachinePageArtTest.metric("energy_capacity", 4_000_000.0f),
                MachinePageArtTest.metric("energy_net_rate", -180.0f));
    }

    private static SourceProbe.Unit observerNoCapacity() {
        final SourceProbe.Unit u = MachinePageArtTest.unit("Content Observer", "crop-depot",
                MachinePageArtTest.series("total_items",
                        MachinePageArtTest.ramp(120, 2_900.0f, 3_480.0f)),
                MachinePageArtTest.metric("unique_types", 5.0f),
                MachinePageArtTest.metric("item_minecraft_wheat", 2_100.0f),
                MachinePageArtTest.metric("item_minecraft_carrot", 980.0f),
                MachinePageArtTest.metric("item_minecraft_potato", 400.0f),

                MachinePageArtTest.metric("item_minecraft_beetroot", 65.0f),
                MachinePageArtTest.metric("item_minecraft_bamboo", 1.0f));
        u.config().setUserLabel("");
        u.config().setObservedTarget(new BlockPos(44, 70, 3), "Crop Depot");
        label(u, "item_minecraft_wheat", "Wheat");
        label(u, "item_minecraft_carrot", "Carrot");
        label(u, "item_minecraft_potato", "Potato");
        label(u, "item_minecraft_beetroot", "Beetroot");
        label(u, "item_minecraft_bamboo", "Bamboo");
        return u;
    }

    private static float u(SourceProbe.Unit unit, String metricId) {
        return unit.store(metricId).getLatestValue();
    }

    private static void label(SourceProbe.Unit u, String metricId, String label) {
        u.channel(metricId).setLabel(label);
    }

    private static void type(SourceProbe.Unit u, String metricId,
                             com.createstats.data.MetricType type) {
        u.store(metricId).setMetricType(type);
    }

    private static void place(Graphics2D g, BufferedImage img, int col, int row, String caption) {
        placeAt(g, img, col, row, W, H, caption);
    }

    private static void placeAt(Graphics2D g, BufferedImage img, int col, int row, int w, int h,
                                String caption) {
        final int x = 24 + col * (w + 24);
        final int y = 44 + row * (h + 44);
        g.drawImage(img, x, y, null);
        MachinePageArtTest.caption(g, caption, x, y - 8);
    }
}
