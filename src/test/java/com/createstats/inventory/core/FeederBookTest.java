package com.createstats.inventory.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeederBookTest {
    private static final String OW = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    @Test
    void theNewestAnnouncementComesFirst() {
        final FeederBook b = new FeederBook();
        assertTrue(b.announce(OW, 1L, 10));
        assertTrue(b.announce(OW, 2L, 20));
        assertEquals(2L, b.entries().get(0).pos());
        assertFalse(b.announce(OW, 2L, 30), "the front link announcing again changes no order");
        assertEquals(30L, b.entries().get(0).announced());
        assertTrue(b.announce(OW, 1L, 40), "an older link announcing again moves to the front");
        assertEquals(1L, b.entries().get(0).pos());
        assertEquals(2, b.entries().size());
    }

    @Test
    void theBookHoldsFourAndDropsTheOldest() {
        final FeederBook b = new FeederBook();
        for (long p = 1; p <= 6; p++) {
            b.announce(OW, p, p * 10);
        }
        assertEquals(FeederBook.MAX_LINKS, b.entries().size());
        assertTrue(b.entries().stream().noneMatch(e -> e.pos() == 1L || e.pos() == 2L));
    }

    @Test
    void theSamePositionInAnotherDimensionIsAnotherLink() {
        final FeederBook b = new FeederBook();
        b.announce(OW, 5L, 1);
        b.announce(NETHER, 5L, 2);
        assertEquals(2, b.entries().size());
    }

    @Test
    void goneLinksAreDroppedUnloadedKeptAndTheNewestLoadedFeeds() {
        final FeederBook b = new FeederBook();
        b.announce(OW, 1L, 1);
        b.announce(OW, 2L, 2);
        b.announce(OW, 3L, 3);
        final Optional<FeederBook.Choice> c = b.choose(e -> e.pos() == 1L ? FeederBook.State.LOADED
                : e.pos() == 2L ? FeederBook.State.GONE : FeederBook.State.UNLOADED);
        assertTrue(c.isPresent());
        assertEquals(1L, c.get().entry().pos(), "a loaded link feeds before a newer unloaded one");
        assertTrue(c.get().loaded());
        assertEquals(List.of(3L, 1L), b.entries().stream().map(FeederBook.Entry::pos).toList());
    }

    @Test
    void withNothingLoadedTheNewestKeptLinkIsReportedNotLoaded() {
        final FeederBook b = new FeederBook();
        b.announce(OW, 7L, 1);
        b.announce(OW, 8L, 2);
        final Optional<FeederBook.Choice> c = b.choose(e -> FeederBook.State.UNLOADED);
        assertTrue(c.isPresent());
        assertEquals(8L, c.get().entry().pos());
        assertFalse(c.get().loaded());
        assertEquals(2, b.entries().size(), "kept, not forgotten");
    }

    @Test
    void anEmptyOrAllGoneBookFeedsNothing() {
        final FeederBook b = new FeederBook();
        assertTrue(b.choose(e -> FeederBook.State.LOADED).isEmpty());
        b.announce(OW, 1L, 1);
        assertTrue(b.choose(e -> FeederBook.State.GONE).isEmpty());
        assertTrue(b.isEmpty());
    }

    @Test
    void restoreKeepsTheCapAndDropsDuplicates() {
        final FeederBook b = new FeederBook();
        b.restore(List.of(new FeederBook.Entry(OW, 1L, 5), new FeederBook.Entry(OW, 1L, 4),
                new FeederBook.Entry(OW, 2L, 3), new FeederBook.Entry(OW, 3L, 2), new FeederBook.Entry(OW, 4L, 1),
                new FeederBook.Entry(OW, 5L, 0)));
        assertEquals(4, b.entries().size());
        assertEquals(1L, b.entries().get(0).pos());
    }
}
