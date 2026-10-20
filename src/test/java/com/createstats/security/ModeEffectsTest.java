package com.createstats.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModeEffectsTest {
    private static ModeEffects.House house() {
        return new ModeEffects.House(false, "", 5, 2, 3, 4, 4, 0);
    }

    private static String phrase(SecurityMode mode, ModeEffects.Axis axis) {
        return ModeEffects.phrase(mode, axis, house());
    }

    private static boolean changed(SecurityMode mode, ModeEffects.Axis axis) {
        for (ModeEffects.Row row : ModeEffects.rows(mode, house())) {
            if (row.axis() == axis) {
                return row.changed();
            }
        }
        throw new AssertionError("no row for " + axis);
    }

    @Test
    @DisplayName("Every mode answers all seven axes, in one fixed order, with nothing blank")
    void sevenRowsAlways() {
        for (SecurityMode mode : SecurityMode.all()) {
            final List<ModeEffects.Row> rows = ModeEffects.rows(mode, house());
            assertEquals(ModeEffects.Axis.values().length, rows.size(), mode.name());
            for (int i = 0; i < rows.size(); i++) {
                assertEquals(ModeEffects.Axis.values()[i], rows.get(i).axis(), mode.name());
                assertFalse(rows.get(i).phrase().isBlank(),
                        mode.name() + " " + rows.get(i).axis());
            }
            assertFalse(ModeEffects.posture(mode, house()).isBlank(), mode.name());
        }
    }

    @Test
    @DisplayName("HOME is the baseline, so nothing on it is marked changed")
    void homeChangesNothing() {
        for (ModeEffects.Row row : ModeEffects.rows(SecurityMode.HOME, house())) {
            assertFalse(row.changed(), row.axis().name());
        }
    }

    @Test
    @DisplayName("AWAY changes exactly two axes, which is the honest picture of what it does")
    void awayChangesTwo() {
        assertTrue(changed(SecurityMode.AWAY, ModeEffects.Axis.SENSORS));
        assertTrue(changed(SecurityMode.AWAY, ModeEffects.Axis.IF_TRIPPED));
        assertFalse(changed(SecurityMode.AWAY, ModeEffects.Axis.DOORS));
        assertFalse(changed(SecurityMode.AWAY, ModeEffects.Axis.AUTO_OPEN));
        assertFalse(changed(SecurityMode.AWAY, ModeEffects.Axis.SHUTTERS));
        assertFalse(changed(SecurityMode.AWAY, ModeEffects.Axis.LOCKERS));
        assertFalse(changed(SecurityMode.AWAY, ModeEffects.Axis.SWITCHING));
    }

    @Test
    @DisplayName("CUSTOM changes the same two, and differs from AWAY on only one of them")
    void customChangesTwo() {
        assertTrue(changed(SecurityMode.CUSTOM, ModeEffects.Axis.SENSORS));
        assertTrue(changed(SecurityMode.CUSTOM, ModeEffects.Axis.IF_TRIPPED));
        assertFalse(changed(SecurityMode.CUSTOM, ModeEffects.Axis.DOORS));
        assertEquals(phrase(SecurityMode.AWAY, ModeEffects.Axis.IF_TRIPPED),
                phrase(SecurityMode.CUSTOM, ModeEffects.Axis.IF_TRIPPED),
                "a trip does the same thing in both, and the card must not imply otherwise");
        assertFalse(phrase(SecurityMode.AWAY, ModeEffects.Axis.SENSORS)
                        .equals(phrase(SecurityMode.CUSTOM, ModeEffects.Axis.SENSORS)),
                "but which sensors are armed is the whole difference between them");
    }

    @Test
    @DisplayName("AWAY+ changes all seven, which is why it is the mode that needed this card")
    void awayPlusChangesEverything() {
        for (ModeEffects.Row row : ModeEffects.rows(SecurityMode.AWAY_PLUS, house())) {
            assertTrue(row.changed(), row.axis().name());
        }
    }

    @Test
    @DisplayName("A standing lockdown is explained on the If tripped row, by its cause")
    void lockdownRewritesTheTrippedRow() {
        final ModeEffects.House tripped = new ModeEffects.House(true, "a Skeleton", 5, 2, 3, 4, 4, 0);
        assertTrue(ModeEffects.phrase(SecurityMode.AWAY, ModeEffects.Axis.IF_TRIPPED, tripped)
                        .contains("a Skeleton"),
                ModeEffects.phrase(SecurityMode.AWAY, ModeEffects.Axis.IF_TRIPPED, tripped));
        assertTrue(ModeEffects.posture(SecurityMode.AWAY, tripped).toLowerCase().contains("tripped"),
                ModeEffects.posture(SecurityMode.AWAY, tripped));
        assertTrue(ModeEffects.posture(SecurityMode.AWAY_PLUS, tripped).toLowerCase()
                        .contains("tripped"),
                "the alarm outranks the posture in every mode, sealed included");
    }

    @Test
    @DisplayName("A lockdown with no recorded cause still reads as a sentence and not as a hole")
    void lockdownWithoutACause() {
        final ModeEffects.House tripped = new ModeEffects.House(true, "", 5, 2, 3, 4, 4, 0);
        final String line = ModeEffects.phrase(SecurityMode.AWAY, ModeEffects.Axis.IF_TRIPPED,
                tripped);
        assertEquals("locked down", line);
        final ModeEffects.House nullCause = new ModeEffects.House(true, null, 5, 2, 3, 4, 4, 0);
        assertEquals("locked down",
                ModeEffects.phrase(SecurityMode.AWAY, ModeEffects.Axis.IF_TRIPPED, nullCause),
                "a null cause off an unfilled packet must not print the word null at a player");
    }

    @Test
    @DisplayName("The shutter row folds in how many banks come back when the mode stands down")
    void shutterRowCountsTheRestore() {
        assertEquals("all shut · 3 reopen on exit",
                phrase(SecurityMode.AWAY_PLUS, ModeEffects.Axis.SHUTTERS));
    }

    @Test
    @DisplayName("A house that had every bank sealed already is told so rather than promised three")
    void shutterRowWhenNothingReopens() {
        final ModeEffects.House allSealed = new ModeEffects.House(false, "", 5, 5, 3, 4, 4, 0);
        assertEquals("all shut, and all were shut already",
                ModeEffects.phrase(SecurityMode.AWAY_PLUS, ModeEffects.Axis.SHUTTERS, allSealed));
    }

    @Test
    @DisplayName("A house with no shutters or lockers says so rather than counting to zero")
    void emptyHouseSaysSo() {
        final ModeEffects.House bare = new ModeEffects.House(false, "", 0, 0, 0, 0, 0, 0);
        for (ModeEffects.Row row : ModeEffects.rows(SecurityMode.AWAY_PLUS, bare)) {
            if (row.axis() == ModeEffects.Axis.SHUTTERS || row.axis() == ModeEffects.Axis.LOCKERS
                    || row.axis() == ModeEffects.Axis.SENSORS) {
                assertEquals("none in this house", row.phrase(), row.axis().name());
                assertFalse(row.changed(), "a row about nothing cannot be a change");
            }
        }
    }

    @Test
    @DisplayName("A sensor with no supply is named, because all armed over a dead one is a lie")
    void blindSensorsAreNamed() {
        final ModeEffects.House dark = new ModeEffects.House(false, "", 5, 2, 3, 4, 3, 1);
        assertEquals("all armed · 1 blind, no power",
                ModeEffects.phrase(SecurityMode.AWAY, ModeEffects.Axis.SENSORS, dark));
        assertEquals("recording only · 1 blind, no power",
                ModeEffects.phrase(SecurityMode.HOME, ModeEffects.Axis.SENSORS, dark),
                "a blind sensor is not recording either, so HOME says it too");

        for (ModeEffects.Row row : ModeEffects.rows(SecurityMode.AWAY, dark)) {
            if (row.axis() == ModeEffects.Axis.SENSORS) {
                assertTrue(row.changed());
            }
        }
    }
}
