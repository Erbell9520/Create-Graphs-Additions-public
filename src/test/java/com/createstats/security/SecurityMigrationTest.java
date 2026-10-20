package com.createstats.security;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityMigrationTest {
    private static SecurityEndpoint at(long pos) {
        return SecurityEndpoint.of("minecraft:overworld", pos);
    }

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000000f");

    private static CompoundTag legacyPanel() {
        final CompoundTag tag = new CompoundTag();
        tag.putBoolean("FailSecure", false);
        tag.putInt("Proximity", 7);
        tag.putInt("Mode", SecurityMode.AWAY.ordinal());
        tag.putBoolean("Lockdown", true);
        tag.putLong("LockdownAt", 500L);
        tag.putString("LockdownCause", "Zombie");
        tag.putLong("Entrance", 10L);
        tag.putString("House", "Bolat");
        tag.putLongArray("SealedBanks", new long[]{30L});
        tag.putLongArray("DuskBanks", new long[]{30L});

        final SecurityRoster roster = new SecurityRoster();
        roster.setOwner(OWNER, "Bolat");
        tag.put("Roster", roster.save());

        tag.put("Levels", intRow(10L, "L", 4));
        tag.put("Reach", intRow(10L, "R", 6));
        tag.put("Radii", intRow(20L, "R", 9));

        final ListTag names = new ListTag();
        final CompoundTag name = new CompoundTag();
        name.putLong("P", 10L);
        name.putString("N", "Front Door");
        names.add(name);
        tag.put("Names", names);

        final ListTag disarmed = new ListTag();
        final CompoundTag off = new CompoundTag();
        off.putLong("P", 20L);
        disarmed.add(off);
        tag.put("Disarmed", disarmed);

        final SecurityLog log = new SecurityLog();
        log.add(1L, "Bolat", "Front Door", "set to 4 TRUSTED", SecurityLog.Verdict.CHANGED);
        tag.put("Log", log.save());
        return tag;
    }

    private static ListTag intRow(long pos, String valueKey, int value) {
        final ListTag list = new ListTag();
        final CompoundTag row = new CompoundTag();
        row.putLong("P", pos);
        row.putInt(valueKey, value);
        list.add(row);
        return list;
    }

    @Test
    @DisplayName("Every legacy key lands in the household, none of them silently dropped")
    void everyKeySurvives() {
        final SecurityHousehold h = new SecurityHousehold(UUID.randomUUID());
        h.loadLegacyPanel(legacyPanel());

        assertFalse(h.failSecure());
        assertEquals(7, h.proximity());
        assertEquals(SecurityMode.AWAY, h.mode());
        assertTrue(h.lockdown());
        assertEquals(500L, h.lockdownAt());
        assertEquals("Zombie", h.lockdownCause());
        assertTrue(h.hasEntrance());
        assertEquals(10L, h.entrance().pos());
        assertTrue(h.isEntrance(at(10L)), "a legacy entrance answers for its position until it is settled");
        assertEquals("Bolat", h.house());
        assertTrue(h.shutterSealed(at(30L)), "a sealed bank must not be lost by the migration");
        assertTrue(h.shutterDusk(at(30L)));
        assertEquals(4, h.requiredLevel(at(10L)));
        assertEquals("Front Door", h.nameOf(at(10L), "Iron Door"));
        assertFalse(h.sensorFlag(at(20L)), "a disarmed sensor must not come back armed");
        assertEquals(6, h.proximityOverride(at(10L)));
        assertEquals(9, h.sensorRadius(at(20L), 6));
        assertEquals(1, h.log().size());
        assertNotNull(h.roster().find(OWNER));
        assertEquals("Bolat", h.roster().find(OWNER).name());
        assertTrue(h.roster().isOwner(OWNER), "the owner must survive, or nobody owns the house");
    }

    @Test
    @DisplayName("A migrated household is already configured, so an old base gets no wizard")
    void migratedIsConfigured() {
        final SecurityHousehold h = new SecurityHousehold(UUID.randomUUID());
        h.loadLegacyPanel(legacyPanel());
        assertTrue(h.configured(),
                "an established house must not be greeted by a setup page over its own roster");

        final SecurityHousehold blank = new SecurityHousehold(UUID.randomUUID());
        blank.loadLegacyPanel(new CompoundTag());
        assertTrue(blank.configured());
    }

    @Test
    @DisplayName("A migrated household keeps its own id, not one off the panel tag")
    void idIsNotOverwritten() {
        final UUID mine = UUID.fromString("00000000-0000-0000-0000-0000000000cc");
        final SecurityHousehold h = new SecurityHousehold(mine);
        final CompoundTag tag = legacyPanel();
        tag.putUUID("Id", UUID.randomUUID());
        h.loadLegacyPanel(tag);
        assertEquals(mine, h.id());
    }

    @Test
    @DisplayName("A panel tag carries no panel list, so a migration cannot invent siblings")
    void noPanelsAreAdopted() {
        final SecurityHousehold h = new SecurityHousehold(UUID.randomUUID());
        final CompoundTag tag = legacyPanel();
        tag.putLongArray("Panels", new long[]{1L, 2L, 3L});
        h.loadLegacyPanel(tag);
        assertTrue(h.panels().isEmpty(),
                "the panel registers itself after migrating; the legacy tag has no say");
    }

    @Test
    @DisplayName("A panel that never had policy migrates to a household at the defaults")
    void emptyPanel() {
        final SecurityHousehold h = new SecurityHousehold(UUID.randomUUID());
        h.loadLegacyPanel(new CompoundTag());
        assertTrue(h.failSecure());
        assertEquals(3, h.proximity());
        assertEquals(SecurityMode.HOME, h.mode());
        assertFalse(h.hasEntrance());
        assertFalse(h.lockdown());
        assertFalse(h.hasPassword(), "a migrated house has no password until somebody sets one");
    }

    @Test
    @DisplayName("A migrated household round trips again through its own save")
    void migrateThenSave() {
        final SecurityHousehold h = new SecurityHousehold(UUID.randomUUID());
        h.loadLegacyPanel(legacyPanel());
        h.addPanel(at(77L));

        final SecurityHousehold back = new SecurityHousehold(h.id());
        back.load(h.save());

        assertEquals(4, back.requiredLevel(at(10L)));
        assertTrue(back.shutterSealed(at(30L)));
        assertEquals("Bolat", back.house());
        assertTrue(back.panels().contains(at(77L)));
    }
}
