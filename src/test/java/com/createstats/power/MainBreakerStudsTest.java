package com.createstats.power;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Main Breaker terminal stubs")
class MainBreakerStudsTest {
    private static final AABB HITBOX =
            new AABB(1.9 / 16.0, 0.0, 1.9 / 16.0, 14.1 / 16.0, 8.0 / 16.0, 14.1 / 16.0);

    @Test
    @DisplayName("both studs stay inside the block on every facing")
    void studsAreInsideTheBlock() {
        for (Direction facing : Direction.values()) {
            final AABB envelope = rotate(facing, HITBOX);
            for (MainBreakerStuds.Stud stud : MainBreakerStuds.Stud.values()) {
                final Vec3 anchor = MainBreakerStuds.anchor(facing, stud);
                assertTrue(contains(envelope, anchor, 1.0e-6),
                        stud + " anchor " + anchor + " is outside the breaker on " + facing);

                assertTrue(contains(envelope.inflate(1.0 / 16.0), MainBreakerStuds.box(facing, stud)),
                        stud + " box escapes the breaker on " + facing);
            }
        }
    }

    @Test
    @DisplayName("a stud's anchor is inside that stud's own guidance box")
    void anchorsSitInTheirOwnBoxes() {
        for (Direction facing : Direction.values()) {
            for (MainBreakerStuds.Stud stud : MainBreakerStuds.Stud.values()) {
                assertTrue(contains(MainBreakerStuds.box(facing, stud),
                                MainBreakerStuds.anchor(facing, stud), 1.0e-6),
                        stud + " anchor is not in its own box on " + facing);
            }
        }
    }

    @Test
    @DisplayName("the two studs are never in the same place")
    void theTwoStudsAreApart() {
        for (Direction facing : Direction.values()) {
            final Vec3 in = MainBreakerStuds.anchor(facing, MainBreakerStuds.Stud.INPUT);
            final Vec3 out = MainBreakerStuds.anchor(facing, MainBreakerStuds.Stud.OUTPUT);
            assertNotEquals(in, out, "the two stubs coincide on " + facing);

            assertTrue(in.distanceTo(out) > 0.25,
                    "the two stubs are only " + in.distanceTo(out) + " apart on " + facing);
        }
    }

    @Test
    @DisplayName("a hit on a stud picks that stud, and the block centre picks neither")
    void pickAnswersTheSameQuestionTheOutlineAsks() {
        for (Direction facing : Direction.values()) {
            for (MainBreakerStuds.Stud stud : MainBreakerStuds.Stud.values()) {
                assertEquals(stud, MainBreakerStuds.pick(facing, MainBreakerStuds.anchor(facing, stud)),
                        "aiming at " + stud + " picked something else on " + facing);
            }
            assertNull(MainBreakerStuds.pick(facing, new Vec3(0.5, 0.5, 0.5)),
                    "the middle of the block picked a stud on " + facing);
        }
    }

    @Test
    @DisplayName("the mode paddle takes its own clicks and nothing else's")
    void theSelectorDoesNotStealClicks() {
        for (Direction facing : Direction.values()) {
            assertTrue(MainBreakerStuds.isOnSelector(facing, MainBreakerStuds.selectorAnchor(facing)),
                    "a click on the paddle missed it on " + facing);
            for (MainBreakerStuds.Stud stud : MainBreakerStuds.Stud.values()) {
                assertFalse(MainBreakerStuds.isOnSelector(facing, MainBreakerStuds.anchor(facing, stud)),
                        "a click on the " + stud + " terminal took the mode paddle on " + facing);
                assertNull(MainBreakerStuds.pick(facing, MainBreakerStuds.selectorAnchor(facing)),
                        "a click on the mode paddle picked a wire terminal on " + facing);
            }
            assertFalse(MainBreakerStuds.isOnSelector(facing, new Vec3(0.5, 0.5, 0.5)),
                    "the middle of the block took the mode paddle on " + facing);
        }
    }

    @Test
    @DisplayName("the paddle's outline contains its own anchor, on every face")
    void theSelectorBoxHoldsItsAnchor() {
        for (Direction facing : Direction.values()) {
            assertTrue(contains(MainBreakerStuds.selectorBox(facing),
                            MainBreakerStuds.selectorAnchor(facing), 1.0e-6),
                    "the mode paddle's anchor is outside its own box on " + facing);
            assertTrue(contains(rotate(facing, HITBOX).inflate(1.0 / 16.0),
                            MainBreakerStuds.selectorBox(facing)),
                    "the mode paddle leaves the breaker's own envelope on " + facing);
        }
    }

    @Test
    @DisplayName("the authored up axis lands on the face the breaker is mounted to")
    void theAuthoredUpAxisLandsOnTheMountingFace() {
        for (Direction facing : Direction.values()) {
            final double[] out = com.createstats.block.MountedPart.toBlockSpace(facing, 0.5, 1.0, 0.5);
            assertEquals(0.5 + facing.getStepX() * 0.5, out[0], 1.0e-9, "+y x on " + facing);
            assertEquals(0.5 + facing.getStepY() * 0.5, out[1], 1.0e-9, "+y y on " + facing);
            assertEquals(0.5 + facing.getStepZ() * 0.5, out[2], 1.0e-9, "+y z on " + facing);

            final Direction back = facing.getOpposite();
            final double[] wall = com.createstats.block.MountedPart.toBlockSpace(facing, 0.5, 0.0, 0.5);
            assertEquals(0.5 + back.getStepX() * 0.5, wall[0], 1.0e-9, "-y x on " + facing);
            assertEquals(0.5 + back.getStepY() * 0.5, wall[1], 1.0e-9, "-y y on " + facing);
            assertEquals(0.5 + back.getStepZ() * 0.5, wall[2], 1.0e-9, "-y z on " + facing);
            assertEquals(back, com.createstats.block.MountedPart.baseFace(facing),
                    "toBlockSpace and baseFace disagree about the wall on " + facing);
        }
    }

    @Test
    @DisplayName("both stubs sit on the outward half of the block, never behind it")
    void stubsAreOnTheOutwardSide() {
        for (Direction facing : Direction.values()) {
            for (MainBreakerStuds.Stud stud : MainBreakerStuds.Stud.values()) {
                final Vec3 at = MainBreakerStuds.anchor(facing, stud);
                final double outward = (at.x - 0.5) * facing.getStepX()
                        + (at.y - 0.5) * facing.getStepY()
                        + (at.z - 0.5) * facing.getStepZ();
                assertTrue(outward > -0.06,
                        stud + " sits " + (-outward) + " behind the mounting plane on " + facing);
            }
        }
    }

    @Test
    @DisplayName("the mounting rotation moves the studs: no two facings agree")
    void everyFacingIsDistinct() {
        for (Direction a : Direction.values()) {
            for (Direction b : Direction.values()) {
                if (a == b) {
                    continue;
                }
                assertNotEquals(MainBreakerStuds.anchor(a, MainBreakerStuds.Stud.INPUT),
                        MainBreakerStuds.anchor(b, MainBreakerStuds.Stud.INPUT),
                        "the INPUT stub is in the same place on " + a + " and " + b);
            }
        }
    }

    private static AABB rotate(Direction facing, AABB box) {
        final double[] a = com.createstats.block.MountedPart.toBlockSpace(
                facing, box.minX, box.minY, box.minZ);
        final double[] b = com.createstats.block.MountedPart.toBlockSpace(
                facing, box.maxX, box.maxY, box.maxZ);
        return new AABB(Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]),
                Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2]));
    }

    private static boolean contains(AABB outer, Vec3 point, double slack) {
        return point.x >= outer.minX - slack && point.x <= outer.maxX + slack
                && point.y >= outer.minY - slack && point.y <= outer.maxY + slack
                && point.z >= outer.minZ - slack && point.z <= outer.maxZ + slack;
    }

    private static boolean contains(AABB outer, AABB inner) {
        return contains(outer, new Vec3(inner.minX, inner.minY, inner.minZ), 1.0e-6)
                && contains(outer, new Vec3(inner.maxX, inner.maxY, inner.maxZ), 1.0e-6);
    }

    @Test
    @DisplayName("a stud's index round trips, and only two indices are studs")
    void indicesNameTheSameStuds() {
        for (MainBreakerStuds.Stud stud : MainBreakerStuds.Stud.values()) {
            assertTrue(MainBreakerStuds.isStudIndex(stud.index()),
                    stud + " has an index the wiring would refuse");
            assertSame(stud, MainBreakerStuds.byIndex(stud.index()),
                    "index " + stud.index() + " does not come back as " + stud);
        }
        assertEquals(MainBreakerStuds.COUNT, MainBreakerStuds.Stud.values().length);
        assertFalse(MainBreakerStuds.isStudIndex(-1));
        assertFalse(MainBreakerStuds.isStudIndex(MainBreakerStuds.COUNT));
        assertNull(MainBreakerStuds.byIndex(MainBreakerStuds.COUNT));
    }

    @Test
    @DisplayName("the index pick and the enum pick answer the same hit the same way, on every facing")
    void theTwoPickFormsAgree() {
        for (Direction facing : Direction.values()) {
            for (MainBreakerStuds.Stud stud : MainBreakerStuds.Stud.values()) {
                final Vec3 at = MainBreakerStuds.anchor(facing, stud);
                assertEquals(stud.index(), MainBreakerStuds.pickIndex(facing, at.x, at.y, at.z),
                        "the index pick missed " + stud + " on " + facing);
            }
            assertEquals(-1, MainBreakerStuds.pickIndex(facing, 0.5, 0.5, 0.5),
                    "the block centre resolved to a stud on " + facing);
        }
    }
}
