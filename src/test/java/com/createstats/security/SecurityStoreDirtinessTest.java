package com.createstats.security;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityStoreDirtinessTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private static SecurityHouseholds settled() {
        final SecurityHouseholds store = new SecurityHouseholds();
        final SecurityHousehold h = store.create();
        h.roster().setOwner(OWNER, "Owner");
        h.roster().add(MEMBER, "Member");
        store.setDirty(false);
        return store;
    }

    private static SecurityHousehold only(SecurityHouseholds store) {
        return store.ownedBy(OWNER).get(0);
    }

    @Test
    @DisplayName("A member's clearance change dirties the store")
    void clearance() {
        final SecurityHouseholds store = settled();
        assertTrue(only(store).roster().setLevel(MEMBER, 4));
        assertTrue(store.isDirty(), "a clearance edit the next save would drop");
    }

    @Test
    @DisplayName("A member's role change dirties the store")
    void role() {
        final SecurityHouseholds store = settled();
        assertTrue(only(store).roster().setRole(MEMBER, SecurityRole.ADMIN));
        assertTrue(store.isDirty(), "an admin promotion the next save would drop");
    }

    @Test
    @DisplayName("Adding and removing people dirties the store")
    void membership() {
        SecurityHouseholds store = settled();
        assertTrue(only(store).roster().add(UUID.randomUUID(), "Guest"));
        assertTrue(store.isDirty(), "an added person the next save would drop");

        store = settled();
        assertTrue(only(store).roster().remove(MEMBER));
        assertTrue(store.isDirty(), "a removed person who would be back after a reload");
    }

    @Test
    @DisplayName("An ownership transfer dirties the store")
    void ownership() {
        final SecurityHouseholds store = settled();
        only(store).roster().setOwner(MEMBER, "Member");
        assertTrue(store.isDirty(), "a transfer the next save would hand back");
    }

    @Test
    @DisplayName("A log line alone dirties the store")
    void logOnly() {
        final SecurityHouseholds store = settled();
        only(store).log().add(10L, "Member", "Front Door", "2 < 4", SecurityLog.Verdict.DENIED);
        assertTrue(store.isDirty(), "an access line the next save would drop");
    }

    @Test
    @DisplayName("A last-seen stamp dirties the store")
    void lastSeen() {
        final SecurityHouseholds store = settled();
        only(store).roster().touch(MEMBER, 1234L);
        assertTrue(store.isDirty());
    }

    @Test
    @DisplayName("Reads leave a clean store clean")
    void readsAreClean() {
        final SecurityHouseholds store = settled();
        final SecurityHousehold h = only(store);
        h.roster().find(MEMBER);
        h.roster().entries();
        h.roster().sorted();
        h.roster().passes(MEMBER, 3);
        h.log().recent(10);
        h.requiredLevel(SecurityEndpoint.of("minecraft:overworld", 42L));
        h.presence(SecurityEndpoint.of("minecraft:overworld", 42L), 100L);
        h.facts(100L);
        assertFalse(store.isDirty(), "a read must not cost a save");
    }

    @Test
    @DisplayName("A detached copy stays detached: editing it neither dirties nor changes the household")
    void detachedCopy() {
        final SecurityHouseholds store = settled();
        final SecurityHousehold h = only(store);
        final SecurityRoster copy = h.rosterCopy();
        copy.setLevel(MEMBER, 5);
        copy.add(UUID.randomUUID(), "Stranger");
        assertFalse(store.isDirty(), "a door's snapshot is not the household");
        assertEquals(h.roster().find(MEMBER).level(), Clearance.DEFAULT_GRANTED);
        assertEquals(2, h.roster().size());
    }

    @Test
    @DisplayName("A clean-store change survives the save and reload the dirty flag allows")
    void survivesReload() {
        final SecurityHouseholds store = settled();
        final SecurityHousehold h = only(store);
        h.roster().setLevel(MEMBER, 4);
        h.roster().setRole(MEMBER, SecurityRole.ADMIN);
        h.log().add(10L, "Member", "Front Door", "4 >= 3", SecurityLog.Verdict.PASSED);
        assertTrue(store.isDirty());

        final CompoundTag saved = h.save();
        final SecurityHousehold back = new SecurityHousehold(h.id());
        back.load(saved);
        assertEquals(4, back.roster().find(MEMBER).level());
        assertEquals(SecurityRole.ADMIN, back.roster().roleOf(MEMBER));
        assertEquals(1, back.log().size());
    }
}
