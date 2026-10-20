package com.createstats.registry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogLayoutTest {
    private static final int[] SHIPPED = {5, 9, 5, 4, 8, 6};

    @Test
    void eachSectionOpensWithItsBannerRow() {
        final CatalogLayout layout = new CatalogLayout(SHIPPED);
        final int[] banners = new int[layout.sectionCount()];
        for (int s = 0; s < banners.length; s++) {
            banners[s] = layout.bannerRow(s);
        }
        assertArrayEquals(new int[] {0, 6, 16, 22, 27, 36}, banners);
        assertEquals(43, layout.totalRows());
        assertEquals(38, layout.rowsBeyondView());
    }

    @Test
    void aSectionWithNothingToShowGetsNoBanner() {
        final CatalogLayout layout = new CatalogLayout(new int[] {2, 0, 3});
        assertEquals(0, layout.bannerRow(0));
        assertEquals(-1, layout.bannerRow(1));
        assertEquals(3, layout.bannerRow(2));
        assertEquals(7, layout.totalRows());
        assertEquals(-1, layout.jumpTarget(1));
        assertEquals(-1, layout.notchY(1));
    }

    @Test
    void everyRowKnowsItsSectionAndOnlyBannersAreBanners() {
        final CatalogLayout layout = new CatalogLayout(SHIPPED);
        assertEquals(0, layout.bannerAt(0));
        assertEquals(-1, layout.bannerAt(1));
        assertEquals(1, layout.bannerAt(6));
        assertEquals(4, layout.bannerAt(27));
        assertEquals(5, layout.bannerAt(36));
        assertEquals(0, layout.sectionAt(5));
        assertEquals(1, layout.sectionAt(6));
        assertEquals(1, layout.sectionAt(15));
        assertEquals(2, layout.sectionAt(16));
        assertEquals(4, layout.sectionAt(35));
        assertEquals(5, layout.sectionAt(36));
        assertEquals(5, layout.sectionAt(42));
    }

    private static int vanillaRow(float offs, int beyond) {
        return Math.max((int) ((double) (offs * (float) beyond) + 0.5), 0);
    }

    private static float vanillaScroll(int row, int beyond) {
        return Math.max(0.0F, Math.min(1.0F, (float) row / (float) beyond));
    }

    @Test
    void scrollArithmeticIsVanillasOwn() {
        final CatalogLayout layout = new CatalogLayout(SHIPPED);
        final int beyond = layout.rowsBeyondView();
        for (int row = 0; row <= beyond; row++) {
            final float offs = layout.scrollFor(row);
            assertEquals(vanillaScroll(row, beyond), offs, 0.0F, "scroll for row " + row);
            assertEquals(row, layout.firstRowFor(offs), "a jump to row " + row + " must land on it");
            assertEquals(vanillaRow(offs, beyond), layout.firstRowFor(offs));
        }

        for (int k = 0; k <= 1000; k++) {
            final float offs = k / 1000.0F;
            assertEquals(vanillaRow(offs, beyond), layout.firstRowFor(offs));
        }
    }

    @Test
    void aJumpPutsTheBannerOnTopUnlessTheListEndsFirst() {
        final CatalogLayout shipped = new CatalogLayout(SHIPPED);
        for (int s = 0; s < shipped.sectionCount(); s++) {
            assertEquals(shipped.bannerRow(s), shipped.jumpTarget(s));
        }

        final CatalogLayout shortEnd = new CatalogLayout(new int[] {5, 8, 5, 4, 2});
        assertEquals(29, shortEnd.totalRows());
        assertEquals(26, shortEnd.bannerRow(4));
        assertEquals(24, shortEnd.jumpTarget(4));
        assertTrue(shortEnd.jumpTarget(4) + CatalogLayout.VISIBLE_ROWS > shortEnd.bannerRow(4),
            "the jump must still show the banner it was asked for");
    }

    @Test
    void notchesSitUnderTheScrollersCentreAndNeverCollide() {
        final CatalogLayout layout = new CatalogLayout(SHIPPED);
        int previous = -1;
        for (int s = 0; s < layout.sectionCount(); s++) {
            final int y = layout.notchY(s);
            final int scrollerTop = (int) (CatalogLayout.SCROLL_TRAVEL * layout.scrollFor(layout.jumpTarget(s)));
            assertEquals(scrollerTop + CatalogLayout.SCROLLER_MIDDLE, y);
            assertTrue(y >= CatalogLayout.SCROLLER_MIDDLE
                && y <= CatalogLayout.SCROLL_TRAVEL + CatalogLayout.SCROLLER_MIDDLE, "notch " + s + " off the track");
            assertTrue(y > previous + 4, "notch " + s + " at " + y + " crowds the one above at " + previous);
            previous = y;
        }
        assertEquals(CatalogLayout.SCROLLER_MIDDLE, layout.notchY(0));
    }

    @Test
    void theRunningHeadNamesAChapterOnlyOnceItsBannerHasGone() {
        final CatalogLayout layout = new CatalogLayout(SHIPPED);
        assertEquals(-1, layout.runningHead(0), "the Stats banner is the top row: it already says so");
        assertEquals(0, layout.runningHead(1));
        assertEquals(0, layout.runningHead(5));
        assertEquals(-1, layout.runningHead(6), "Power's banner is the top row");
        assertEquals(1, layout.runningHead(7));

        assertEquals(1, layout.runningHead(12));
        assertEquals(5, layout.runningHead(layout.lastFirstRow()));
    }

    @Test
    void aListThatFitsDoesNotScroll() {
        final CatalogLayout layout = new CatalogLayout(new int[] {2, 1});
        assertEquals(5, layout.totalRows());
        assertEquals(0.0F, layout.scrollFor(3), 0.0F);
        assertEquals(0, layout.firstRowFor(1.0F));
        assertEquals(0, layout.jumpTarget(1));
    }
}
