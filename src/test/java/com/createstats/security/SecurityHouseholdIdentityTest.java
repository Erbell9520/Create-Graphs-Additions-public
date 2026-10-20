package com.createstats.security;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityHouseholdIdentityTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-0000000000a3");

    private static SecurityEndpoint ow(long pos) {
        return SecurityEndpoint.of(OVERWORLD, pos);
    }

    private static SecurityEndpoint nether(long pos) {
        return SecurityEndpoint.of(NETHER, pos);
    }

    private static SecurityHousehold house() {
        final SecurityHousehold h = new SecurityHousehold(UUID.randomUUID());
        h.roster().setOwner(OWNER, "Owner");
        h.roster().add(ADMIN, "Admin");
        h.roster().setRole(ADMIN, SecurityRole.ADMIN);
        h.roster().add(MEMBER, "Member");
        return h;
    }

    private static CompoundTag schemaOne(long[] panels) {
        final CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", UUID.randomUUID());
        tag.putLongArray("Panels", panels);
        final ListTag levels = new ListTag();
        final CompoundTag row = new CompoundTag();
        row.putLong("P", 10L);
        row.putInt("L", 5);
        levels.add(row);
        tag.put("Levels", levels);
        tag.putLongArray("SealedBanks", new long[]{30L});
        tag.putLong("Entrance", 10L);
        return tag;
    }

    @Test
    @DisplayName("The same coordinates in two dimensions are two places")
    void dimensionsDoNotAlias() {
        final SecurityHousehold h = house();
        h.setRequiredLevel(ow(100L), 5);
        h.setName(ow(100L), "Hall door");
        h.setSensorFlag(nether(100L), false);
        assertEquals(5, h.requiredLevel(ow(100L)));
        assertEquals(Clearance.DEFAULT_REQUIRED, h.requiredLevel(nether(100L)),
                "a Nether door at the same coordinates must not inherit the hall's clearance");
        assertEquals("Wing door", h.nameOf(nether(100L), "Wing door"));
        assertTrue(h.sensorFlag(ow(100L)), "disarming the Nether sensor must not disarm the hall's");
        assertFalse(h.sensorFlag(nether(100L)));
    }

    @Test
    @DisplayName("Pruning one dimension never touches another's settings")
    void pruneJudgesItsOwnWorldOnly() {
        final SecurityHousehold h = house();
        h.setRequiredLevel(ow(100L), 5);
        h.setRequiredLevel(nether(100L), 4);
        h.setEntrance(nether(100L));

        h.prune(OVERWORLD, pos -> pos == 100L);
        assertEquals(Clearance.DEFAULT_REQUIRED, h.requiredLevel(ow(100L)));
        assertEquals(4, h.requiredLevel(nether(100L)), "the Nether wing's policy was judged in the wrong world");
        assertTrue(h.isEntrance(nether(100L)));
    }

    @Test
    @DisplayName("A schema 1 house reads exactly as before until its panels prove its dimension")
    void legacyKeysAreAFallback() {
        final SecurityHousehold h = new SecurityHousehold(UUID.randomUUID());
        h.load(schemaOne(new long[]{1L}));
        assertEquals(5, h.requiredLevel(ow(10L)), "a legacy setting must still apply");
        assertEquals(5, h.requiredLevel(nether(10L)), "and apply wherever it applied before, unproven");
        assertTrue(h.shutterSealed(ow(30L)));
        assertTrue(h.isEntrance(ow(10L)));
        assertNull(h.provenDimension(), "a legacy panel entry means the dimension is not proven");
        assertFalse(h.settleLegacy(), "nothing settles on a guess");
        assertEquals(3, h.legacyUnresolved(), "the level, the seal and the entrance, and nothing else");
    }

    @Test
    @DisplayName("Once every panel has registered in one dimension, legacy keys move there and only there")
    void legacySettlesWhenProven() {
        final SecurityHousehold h = new SecurityHousehold(UUID.randomUUID());
        h.load(schemaOne(new long[]{1L, 2L}));
        h.addPanel(ow(1L));
        assertNull(h.provenDimension(), "one legacy panel is still unaccounted for");
        h.addPanel(ow(2L));
        assertEquals(OVERWORLD, h.provenDimension());
        assertTrue(h.settleLegacy());
        assertEquals(5, h.requiredLevel(ow(10L)));
        assertEquals(Clearance.DEFAULT_REQUIRED, h.requiredLevel(nether(10L)),
                "settled keys belong to the proven dimension alone");
        assertEquals(ow(10L), h.entrance());
        assertEquals(0, h.legacyUnresolved());
        assertFalse(h.settleLegacy(), "idempotent");
    }

    @Test
    @DisplayName("A house proven to span dimensions keeps legacy keys, and writes shadow them per dimension")
    void ambiguousLegacyIsKeptAndShadowed() {
        final SecurityHousehold h = new SecurityHousehold(UUID.randomUUID());
        h.load(schemaOne(new long[]{1L}));
        h.addPanel(ow(1L));
        h.addPanel(nether(7L));
        assertNull(h.provenDimension(), "two dimensions: not proven, nothing guessed");
        assertFalse(h.settleLegacy());
        h.clearRequiredLevel(ow(10L));
        assertEquals(Clearance.DEFAULT_REQUIRED, h.requiredLevel(ow(10L)), "the clear holds in the overworld");
        assertEquals(5, h.requiredLevel(nether(10L)), "the legacy setting still serves the other wing");
    }

    @Test
    @DisplayName("A pre-household panel migrates straight into its own dimension")
    void preHouseholdPanelSettlesAtOnce() {
        final CompoundTag legacyPanel = schemaOne(new long[0]);
        final SecurityHousehold h = new SecurityHousehold(UUID.randomUUID());
        h.loadLegacyPanel(legacyPanel, NETHER);
        assertEquals(5, h.requiredLevel(nether(10L)));
        assertEquals(Clearance.DEFAULT_REQUIRED, h.requiredLevel(ow(10L)));
        assertEquals(0, h.legacyUnresolved());
    }

    @Test
    @DisplayName("A panel's legacy registration is replaced, not doubled, when it registers with a dimension")
    void panelRegistrationIsReplaced() {
        final SecurityHousehold h = new SecurityHousehold(UUID.randomUUID());
        h.load(schemaOne(new long[]{1L}));
        h.addPanel(ow(1L));
        assertEquals(1, h.panels().size());
        assertTrue(h.panels().contains(ow(1L)));
    }

    @Test
    @DisplayName("A device at its defaults is remembered as a member")
    void defaultsAreEnrolled() {
        final SecurityHousehold h = house();
        assertTrue(h.enroll(ow(5L), SecurityKinds.SENSOR_MOTION, 10L));
        assertFalse(h.knows(ow(5L)), "no setting was spent on it");
        assertTrue(h.isEnrolled(ow(5L)), "and it is still the house's");
        assertFalse(h.isEmpty(), "a household that governs a device is never junk");
    }

    @Test
    @DisplayName("Presence: sighted, stale, missing, and back")
    void presenceLifecycle() {
        final SecurityHousehold h = house();
        h.enroll(ow(5L), SecurityKinds.SENSOR_FIRE, 0L);
        assertEquals(Presence.STALE, h.presence(ow(5L), 0L), "enrolled and never seen is not present");
        h.sight(ow(5L), SecurityKinds.SENSOR_FIRE, Presence.PRESENT, false, false, 100L);
        assertEquals(Presence.PRESENT, h.presence(ow(5L), 150L));
        assertEquals(Presence.STALE, h.presence(ow(5L), 100L + SecurityHousehold.SIGHTING_FRESH_TICKS + 1),
                "a sighting nobody refreshed goes stale, never present");
        h.markMissing(ow(5L), 300L);
        assertEquals(Presence.MISSING, h.presence(ow(5L), 300L));
        h.enroll(ow(5L), SecurityKinds.SENSOR_FIRE, 400L);
        assertFalse(h.enrollment(ow(5L)).missing(), "a device that comes back is present again");
    }

    @Test
    @DisplayName("Retiring needs device-edit rights, and releases the device")
    void retireIsAuthorised() {
        final SecurityHousehold h = house();
        h.enroll(ow(5L), SecurityKinds.DOOR, 0L);
        assertFalse(h.retire(ow(5L), MEMBER, "Member", 1L), "a member cannot retire a device");
        assertTrue(h.isEnrolled(ow(5L)));
        assertTrue(h.retire(ow(5L), ADMIN, "Admin", 2L));
        assertFalse(h.isEnrolled(ow(5L)));
        assertTrue(h.released(ow(5L)), "retired is released: another house may take it");
        h.enroll(ow(5L), SecurityKinds.DOOR, 3L);
        assertFalse(h.released(ow(5L)), "enrolling again clears the release");
    }

    @Test
    @DisplayName("A repeller switched off is not a monitoring hole; a relay never is")
    void requiredMonitoring() {
        final SecurityHousehold h = house();
        assertTrue(h.requiredForMonitoring(ow(1L), SecurityKinds.REPELLER));
        h.setRepellerOn(ow(1L), false);
        assertFalse(h.requiredForMonitoring(ow(1L), SecurityKinds.REPELLER));
        assertFalse(h.requiredForMonitoring(ow(2L), SecurityKinds.RELAY));
        assertTrue(h.requiredForMonitoring(ow(3L), SecurityKinds.SENSOR_WATER));
    }

    @Test
    @DisplayName("Schema 2 round trip: places, enrollments, releases and the event counter")
    void schemaTwoRoundTrip() {
        final SecurityHousehold h = house();
        h.addPanel(ow(1L));
        h.addPanel(nether(1L));
        h.setRequiredLevel(nether(9L), 3);
        h.setEntrance(ow(9L));
        h.enroll(ow(9L), SecurityKinds.DOOR, 5L);
        h.enroll(nether(9L), SecurityKinds.SENSOR_WATER, 6L);
        h.markMissing(nether(9L), 7L);
        h.enroll(ow(11L), SecurityKinds.LOCKER, 8L);
        h.retire(ow(11L), OWNER, "Owner", 9L);
        h.acceptEvent(SecurityEvent.ACCESS_DENIED, ow(9L), 10L);

        final SecurityHousehold back = new SecurityHousehold(h.id());
        back.load(h.save());
        assertEquals(h.panels(), back.panels());
        assertEquals(3, back.requiredLevel(nether(9L)));
        assertEquals(Clearance.DEFAULT_REQUIRED, back.requiredLevel(ow(9L)));
        assertEquals(ow(9L), back.entrance());
        assertTrue(back.isEnrolled(ow(9L)));
        assertTrue(back.enrollment(nether(9L)).missing(), "a verified removal survives a reload");
        assertTrue(back.released(ow(11L)));
        assertEquals(1L, back.eventSequence(), "the event counter is never reused after a reload");
        assertEquals(Presence.STALE, back.presence(ow(9L), 20L),
                "runtime sightings are not saved: everything is unseen after a reload");
    }
}
