package com.createstats.pa;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeakerCoverageTest {
    private static final BlockPos SOURCE = new BlockPos(0, 64, 0);

    @Test
    void coversTheFullRangeHorizontally() {
        for (SpeakerKind kind : SpeakerKind.values()) {
            final BlockPos edge = new BlockPos(kind.range(), 64, 0);
            assertTrue(PaAudioZone.covers(SOURCE, edge, kind),
                    kind + " should reach exactly its own range");
            final BlockPos beyond = new BlockPos(kind.range() + 1, 64, 0);
            assertFalse(PaAudioZone.covers(SOURCE, beyond, kind),
                    kind + " should stop one block past its range");
        }
    }

    @Test
    void doesNotCarryUpThroughTheCeiling() {
        for (SpeakerKind kind : SpeakerKind.values()) {
            final BlockPos twoStoreysUp = SOURCE.above(kind.halfHeight());
            assertTrue(PaAudioZone.covers(SOURCE, twoStoreysUp, kind),
                    kind + " should still be heard two storeys up");
            for (int dy : new int[] {kind.halfHeight() + 1, kind.range() / 2, kind.range()}) {
                assertFalse(PaAudioZone.covers(SOURCE, SOURCE.above(dy), kind),
                        kind + " must not be heard " + dy + " blocks straight up");
                assertFalse(PaAudioZone.covers(SOURCE, SOURCE.below(dy), kind),
                        kind + " must not be heard " + dy + " blocks straight down");
            }
        }
    }

    @Test
    void bothDevicesShareTheSameVerticalReach() {
        assertEquals(SpeakerKind.SPEAKER.halfHeight(), SpeakerKind.MEGAPHONE.halfHeight());
        for (SpeakerKind kind : SpeakerKind.values()) {
            assertTrue(kind.halfHeight() < kind.range(),
                    kind + " vertical reach must stay well under its horizontal one, or the volume "
                            + "is a sphere with extra steps");
        }
    }

    @Test
    void theCornerOfTheBoundingBoxIsOutsideTheCylinder() {
        final int r = SpeakerKind.MEGAPHONE.range();
        assertFalse(PaAudioZone.covers(SOURCE, new BlockPos(r, 64, r), SpeakerKind.MEGAPHONE),
                "coverage must be round in plan, not square");
        assertTrue(PaAudioZone.covers(SOURCE, new BlockPos(r * 7 / 10, 64, r * 7 / 10),
                        SpeakerKind.MEGAPHONE),
                "and a point inside the disc on the diagonal must still be covered");
    }

    @Test
    void aVeryLargeRangeDoesNotWrapToCoveringNothing() {
        final BlockPos atTheBorder = new BlockPos(30_000_000, 64, 0);
        assertFalse(PaAudioZone.covers(SOURCE, atTheBorder, SpeakerKind.SPEAKER),
                "nowhere near a real speaker's range");
        assertTrue(PaAudioZone.covers(SOURCE, atTheBorder, 40_000_000, 8),
                "but covered when the range really is that big, which int arithmetic would deny");
    }

    @Test
    void theHornTradesReachForDraw() {
        assertTrue(SpeakerKind.MEGAPHONE.range() < SpeakerKind.SPEAKER.range(),
                "the horn is the short ranged one");
        assertTrue(SpeakerKind.MEGAPHONE.idleDrawFe() > SpeakerKind.SPEAKER.idleDrawFe(),
                "and it draws more for it, or there is no reason to pick the cabinet");
    }

    @Test
    void everyKindHoldsSeveralSecondsOfBuffer() {
        for (SpeakerKind kind : SpeakerKind.values()) {
            assertTrue(kind.bufferFe() >= kind.idleDrawFe() * 100,
                    kind + " should hold at least five seconds, so a grid dip does not cut a word");
        }
    }

    @Test
    void namesRoundTripAndAnUnknownOneFallsBack() {
        for (SpeakerKind kind : SpeakerKind.values()) {
            assertEquals(kind, SpeakerKind.byName(kind.getSerializedName()));
        }
        assertEquals(SpeakerKind.SPEAKER, SpeakerKind.byName("nonsense"));
        assertEquals(SpeakerKind.SPEAKER, SpeakerKind.byName(""));
    }

    @Test
    void theTwoKindsAreActuallyDistinct() {
        assertNotEquals(SpeakerKind.SPEAKER.getSerializedName(),
                SpeakerKind.MEGAPHONE.getSerializedName());
        assertNotEquals(SpeakerKind.SPEAKER.range(), SpeakerKind.MEGAPHONE.range());
    }
}
