package com.createstats.book;

import com.createstats.book.content.Book;
import com.createstats.book.content.Configs;
import com.createstats.book.content.Part;
import com.createstats.book.content.Sheet;
import com.createstats.book.layout.BookGeometry;
import com.createstats.book.layout.BookPages;
import com.createstats.book.layout.BookPainter;
import com.createstats.book.layout.BookText;
import com.createstats.book.layout.Ctx;
import com.createstats.book.layout.Page;
import com.createstats.book.scene.BlockFigure;
import com.createstats.book.scene.BlockFigures;
import com.createstats.book.specimen.BatteryPlates;
import com.createstats.book.specimen.StoreFixtures;
import com.createstats.gui.battery.BatteryView;
import com.createstats.power.DynamoTier;
import com.createstats.power.FeBankMerge;
import com.createstats.power.FeGoggle;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoreFiguresTest {
    @Test
    void everyFixtureIsKnownAndDrawnByItsRenderer() {
        final Set<String> known = new HashSet<>(Set.of(StoreFixtures.BANK_FACE, StoreFixtures.CONTROLLER_FACE));
        known.addAll(com.createstats.book.specimen.CarryFixtures.IDS);

        known.add(com.createstats.book.specimen.PduPlates.FACE);
        final Set<String> used = new HashSet<>();
        for (final BlockFigure f : BlockFigures.all()) {
            for (final BlockFigure.Placement p : f.placements()) {
                if (p.fixture() == null) {
                    continue;
                }
                assertTrue(known.contains(p.fixture()), f.id() + " names an unknown fixture " + p.fixture());
                assertTrue(p.renderer(), f.id() + ": a fixture is only seen through its renderer");
                used.add(p.fixture());
            }
        }
        assertEquals(known, used, "a fixture no figure draws");

        final List<BlockFigure.Placement> bank = BlockFigures.CAPACITOR_BANK.placements().stream()
                .filter(BlockFigure.Placement::renderer).toList();
        assertEquals(1, bank.size(), "one block draws the bank's face");
        assertEquals(0.0, bank.get(0).x() + bank.get(0).y() + bank.get(0).z(), 0.0, "the lowest corner");
    }

    @Test
    void theFacesAreTheFiguresOwnExamples() {
        final StoreFixtures.BankReading b = StoreFixtures.bank();
        final int side = BlockFigures.BANK_SIDE;
        assertEquals(side * side * side, b.blocks(), "the figure's own bank");
        assertEquals(FeBankMerge.capacity(b.blocks(), Configs.bankBlockCapacity(), Configs.bankMergeExponent()),
                b.capacity());
        assertEquals(b.capacity() / 2, b.stored(), "half full");
        assertEquals((int) Math.round(DynamoTier.STANDARD.fePerRpm() * com.createstats.block.KineticDynamoBlock.TOP_RPM),
                b.rate(), "charging on one Kinetic Dynamo at top speed");
        assertEquals("44:29", FeGoggle.time(b.enduranceTicks()), "the face's FULL and the goggle's Full in agree");

        final StoreFixtures.ControllerReading c = StoreFixtures.controller();
        final BatteryView v = BatteryPlates.view();
        assertEquals(v.capacity(), c.capacity());
        assertEquals(v.socPct(), c.pct());
        assertEquals(v.generation(), c.generation());
        assertEquals(v.delivered(), c.delivered());
        assertEquals(v.outputs().stream().map(BatteryView.Output::reason).toList(), c.lamps(),
                "the face's lamps are the plates' outputs");
    }
}
