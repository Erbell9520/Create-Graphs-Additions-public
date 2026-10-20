package com.createstats.security;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityHouseholdTest {
    private static final String DIM = "minecraft:overworld";

    private static SecurityEndpoint at(long pos) {
        return SecurityEndpoint.of(DIM, pos);
    }

    private static SecurityHousehold fresh() {
        return new SecurityHousehold(UUID.fromString("00000000-0000-0000-0000-0000000000ab"));
    }

    @Test
    @DisplayName("A new household starts at the documented defaults")
    void defaults() {
        final SecurityHousehold h = fresh();
        assertTrue(h.failSecure());
        assertEquals(3, h.proximity());
        assertEquals(SecurityMode.HOME, h.mode());
        assertFalse(h.lockdown());
        assertFalse(h.hasEntrance());
        assertEquals(Clearance.DEFAULT_REQUIRED, h.requiredLevel(at(42L)));
        assertTrue(h.sensorFlag(at(42L)), "a sensor nobody has touched is armed");
        assertEquals(-1, h.proximityOverride(at(42L)), "no override means follow the house");
    }

    @Test
    @DisplayName("Every field survives a save and load, the shutter sets included")
    void roundTrip() {
        final SecurityHousehold h = fresh();
        h.setRequiredLevel(at(10L), 4);
        h.setName(at(10L), "Front Door");
        h.setSensorFlag(at(20L), false);
        h.setSensorRadius(at(20L), 9);
        h.setProximityOverride(at(10L), 6);
        h.setShutterSealed(at(30L), true);
        h.setShutterDusk(at(30L), true);
        h.setEntrance(at(10L));
        h.setFailSecure(false);
        h.setProximity(7);
        h.setMode(SecurityMode.AWAY);
        h.raiseLockdown(500L, "Zombie");
        h.setHouse("Bolat");
        h.addPanel(at(99L));
        h.log().add(1L, "who", "what", "detail", SecurityLog.Verdict.CHANGED);

        final SecurityHousehold back = fresh();
        back.load(h.save());

        assertEquals(4, back.requiredLevel(at(10L)));
        assertEquals("Front Door", back.nameOf(at(10L), "Iron Door"));
        assertFalse(back.sensorFlag(at(20L)));
        assertEquals(9, back.sensorRadius(at(20L), 6));
        assertEquals(6, back.proximityOverride(at(10L)));
        assertTrue(back.shutterSealed(at(30L)), "a sealed bank must survive a reload");
        assertTrue(back.shutterDusk(at(30L)), "a dusk flag must survive a reload");
        assertTrue(back.hasEntrance());
        assertEquals(at(10L), back.entrance());
        assertFalse(back.failSecure());
        assertEquals(7, back.proximity());
        assertEquals(SecurityMode.AWAY, back.mode());
        assertTrue(back.lockdown());
        assertEquals(500L, back.lockdownAt());
        assertEquals("Zombie", back.lockdownCause());
        assertEquals("Bolat", back.house());
        assertTrue(back.panels().contains(at(99L)));
        assertEquals(1, back.log().size());
    }

    @Test
    @DisplayName("A fire release lapses on its own, and a hazard banner with it")
    void alarmsLapse() {
        final SecurityHousehold h = fresh();
        h.reportFire(1000L);
        assertTrue(h.released(1000L));
        assertTrue(h.released(1199L));
        assertFalse(h.released(1200L), "the release must expire without anybody clearing it");

        h.reportHazard(1000L, "water");
        assertTrue(h.hazard(1000L));
        assertEquals("water", h.hazardCause(1000L));
        assertFalse(h.hazard(1200L));
        assertEquals("", h.hazardCause(1200L), "a lapsed hazard names nothing");
    }

    @Test
    @DisplayName("A cause longer than the wire can carry is clipped where it enters")
    void causesAreBounded() {
        final SecurityHousehold h = fresh();
        final String huge = "x".repeat(200);
        h.raiseLockdown(1L, huge);
        h.reportHazard(1L, huge);
        assertEquals(SecurityLog.MAX_DETAIL, h.lockdownCause().length());
        assertEquals(SecurityLog.MAX_DETAIL, h.hazardCause(1L).length());
    }

    @Test
    @DisplayName("Pruning drops dead policy and leaves live policy alone")
    void pruning() {
        final SecurityHousehold h = fresh();
        h.setRequiredLevel(at(10L), 5);
        h.setRequiredLevel(at(11L), 5);
        h.setName(at(10L), "Front Door");
        h.setSensorFlag(at(10L), false);
        h.setSensorRadius(at(10L), 9);
        h.setProximityOverride(at(10L), 6);
        h.setShutterSealed(at(10L), true);
        h.setShutterDusk(at(10L), true);
        h.setEntrance(at(10L));

        h.prune(DIM, key -> key == 10L);

        assertEquals(Clearance.DEFAULT_REQUIRED, h.requiredLevel(at(10L)));
        assertEquals(5, h.requiredLevel(at(11L)), "live policy is untouched");
        assertEquals("Iron Door", h.nameOf(at(10L), "Iron Door"));
        assertTrue(h.sensorFlag(at(10L)), "a pruned sensor comes back armed, not silently switched off");
        assertEquals(6, h.sensorRadius(at(10L), 6));
        assertEquals(-1, h.proximityOverride(at(10L)));
        assertFalse(h.shutterSealed(at(10L)));
        assertFalse(h.shutterDusk(at(10L)));
        assertFalse(h.hasEntrance(), "the entrance is pruned like every other per device policy");
    }

    @Test
    @DisplayName("A household holding nothing but its owner may be dropped")
    void emptiness() {
        final SecurityHousehold h = fresh();
        h.roster().setOwner(UUID.randomUUID(), "Bolat");
        assertTrue(h.isEmpty());
        h.setRequiredLevel(at(10L), 3);
        assertFalse(h.isEmpty(), "a household that governs something is not empty");
    }

    @Test
    @DisplayName("A panel registers once, however many times it says so")
    void panelRegistry() {
        final SecurityHousehold h = fresh();
        h.addPanel(at(99L));
        h.addPanel(at(99L));
        assertEquals(1, h.panels().size());
        assertTrue(h.removePanel(at(99L)));
        assertFalse(h.removePanel(at(99L)));
    }

    @Test
    @DisplayName("Only the owner or a server operator may remove a panel-less household")
    void orphanRemovalAuthority() {
        final SecurityHouseholds store = new SecurityHouseholds();
        final SecurityHousehold h = store.create();
        final UUID owner = UUID.randomUUID();
        final UUID householdAdmin = UUID.randomUUID();
        h.roster().setOwner(owner, "Owner");
        h.roster().add(householdAdmin, "Household admin");
        h.roster().setRole(householdAdmin, SecurityRole.ADMIN);

        assertEquals(java.util.List.of(h), store.removableBy(owner, false));
        assertTrue(store.removableBy(householdAdmin, false).isEmpty());
        assertEquals(java.util.List.of(h), store.removableBy(householdAdmin, true));
        assertFalse(store.removeOrphan(h.id(), householdAdmin, false),
                "a household admin is not the household's owner");
        assertTrue(store.removeOrphan(h.id(), owner, false));

        final SecurityHousehold second = store.create();
        second.roster().setOwner(owner, "Owner");
        assertTrue(store.removeOrphan(second.id(), householdAdmin, true),
                "permission-level-2 operators may clear an abandoned record");
    }

    @Test
    @DisplayName("Even an owner or operator cannot remove a household with a registered panel")
    void liveHouseholdCannotBeRemoved() {
        final SecurityHouseholds store = new SecurityHouseholds();
        final SecurityHousehold h = store.create();
        final UUID owner = UUID.randomUUID();
        h.roster().setOwner(owner, "Owner");
        h.addPanel(at(99L));

        assertTrue(store.removableBy(owner, false).isEmpty());
        assertTrue(store.removableBy(UUID.randomUUID(), true).isEmpty());
        assertFalse(store.removeOrphan(h.id(), owner, false));
        assertFalse(store.removeOrphan(h.id(), UUID.randomUUID(), true));
        assertEquals(h, store.find(h.id()), "the live household and all of its policy remain");
    }

    @Test
    @DisplayName("Every mutator marks the store dirty, or a world silently loses its edits")
    void dirtiness() {
        final SecurityHousehold h = fresh();
        final int[] hits = {0};
        h.onChange(() -> hits[0]++);
        h.setProximity(5);
        h.setMode(SecurityMode.AWAY);
        h.setRequiredLevel(at(10L), 2);
        assertEquals(3, hits[0]);
    }

    @Test
    @DisplayName("A fresh household is unconfigured, and a name does not make it configured")
    void configuredIsItsOwnFlag() {
        final SecurityHousehold h = fresh();
        assertFalse(h.configured());
        // claim() names the house at placement, so "configured" can't come from the name

        h.setHouse("Bolat");
        assertFalse(h.configured(), "a name is not a setup");
        h.setConfigured(true);
        assertTrue(h.configured());

        final SecurityHousehold back = fresh();
        back.load(h.save());
        assertTrue(back.configured(), "or every reload reopens the wizard");
    }

    @Test
    @DisplayName("A configured household is never empty, so splitting one off cannot drop it")
    void configuredIsNotEmpty() {
        final SecurityHousehold h = fresh();
        h.setConfigured(true);
        assertFalse(h.isEmpty());
    }

    @Test
    @DisplayName("A password is write only, and three wrong tries shut the pad")
    void password() {
        final SecurityHousehold h = fresh();
        assertFalse(h.hasPassword());
        assertFalse(h.setPassword("12"), "not four digits is not a password");
        assertTrue(h.setPassword("1234"));
        assertTrue(h.hasPassword());
        assertEquals(LockerCode.Result.OPENED, h.offerPassword("1234", 0L));
        assertEquals(LockerCode.Result.WRONG, h.offerPassword("0000", 0L));
        assertEquals(LockerCode.Result.WRONG, h.offerPassword("0000", 0L));
        assertEquals(LockerCode.Result.TAMPER, h.offerPassword("0000", 0L));
        assertEquals(LockerCode.Result.LOCKED_OUT, h.offerPassword("1234", 1L),
                "a correct code during a lockout is exactly the try the lockout is for");
    }

    @Test
    @DisplayName("Admission lapses once the screen stops asking, and not before")
    void admission() {
        final SecurityHousehold h = fresh();
        final int window = SecurityHousehold.ADMISSION_TICKS;
        final UUID who = UUID.randomUUID();
        assertFalse(h.admitted(who, 0L));
        h.admit(who, 100L);
        assertTrue(h.admitted(who, 100L));
        assertTrue(h.admitted(who, 100L + window - 1));
        assertFalse(h.admitted(who, 100L + window),
                "a screen that stopped polling is a screen that closed");
        h.admit(who, 200L);
        h.refreshAdmission(who, 205L);
        assertTrue(h.admitted(who, 205L + window - 1), "a poll extends a standing admission");
    }

    @Test
    @DisplayName("The admission window is longer than the screen's poll, or nobody stays admitted")
    void admissionOutlivesOnePoll() {
        final int pollTicks = 10;
        assertTrue(SecurityHousehold.ADMISSION_TICKS > pollTicks * 2,
                "the window must survive a missed poll, not merely match one");

        final SecurityHousehold h = fresh();
        final UUID who = UUID.randomUUID();
        h.admit(who, 0L);

        for (int tick = pollTicks; tick <= pollTicks * 5; tick += pollTicks) {
            assertTrue(h.admitted(who, tick), "admission must survive poll at tick " + tick);
            h.refreshAdmission(who, tick);
        }
    }

    @Test
    @DisplayName("The password survives a save, lockout included")
    void passwordRoundTrip() {
        final SecurityHousehold h = fresh();
        h.setPassword("1234");
        h.setAlwaysAsk(true);
        h.offerPassword("0000", 0L);
        h.offerPassword("0000", 0L);
        h.offerPassword("0000", 0L);

        final SecurityHousehold back = fresh();
        back.load(h.save());
        assertTrue(back.hasPassword());
        assertTrue(back.alwaysAsk());
        assertEquals(LockerCode.Result.LOCKED_OUT, back.offerPassword("1234", 1L),
                "unloading the chunk must not be a reset button on the lockout");
    }

    @Test
    @DisplayName("Admission is never saved, because a session that ended types again")
    void admissionIsNotSaved() {
        final SecurityHousehold h = fresh();
        final UUID who = UUID.fromString("00000000-0000-0000-0000-0000000000cd");
        h.setPassword("1234");
        h.admit(who, 100L);

        final SecurityHousehold back = fresh();
        back.load(h.save());
        assertFalse(back.admitted(who, 100L));
    }

    @Test
    @DisplayName("A household knows nothing about a device nobody has configured")
    void knowsNothingByDefault() {
        assertFalse(fresh().knows(at(42L)), "defaults are not a claim on anything");
    }

    @Test
    @DisplayName("Every setting a player can spend a click on counts as knowing the device")
    void knowsEverySetting() {
        final SecurityEndpoint anchor = at(42L);

        SecurityHousehold h = fresh();
        h.setRequiredLevel(anchor, 4);
        assertTrue(h.knows(anchor), "a clearance");

        h = fresh();
        h.setName(anchor, "Front door");
        assertTrue(h.knows(anchor), "a name");

        h = fresh();
        h.setSensorFlag(anchor, false);
        assertTrue(h.knows(anchor), "a disarmed sensor");

        h = fresh();
        h.setSensorRadius(anchor, 6);
        assertTrue(h.knows(anchor), "a detection range");

        h = fresh();
        h.setProximityOverride(anchor, 2);
        assertTrue(h.knows(anchor), "an opening radius");

        h = fresh();
        h.setShutterSealed(anchor, true);
        assertTrue(h.knows(anchor), "a sealed bank");

        h = fresh();
        h.setShutterDusk(anchor, true);
        assertTrue(h.knows(anchor), "a dusk flag");

        h = fresh();
        h.setEntrance(anchor);
        assertTrue(h.knows(anchor), "the entrance");
    }

    @Test
    @DisplayName("Knowing one device says nothing about the one beside it")
    void knowsIsPerDevice() {
        final SecurityHousehold h = fresh();
        h.setRequiredLevel(at(42L), 4);
        assertTrue(h.knows(at(42L)));
        assertFalse(h.knows(at(43L)));
    }

    @Test
    @DisplayName("A setting taken back is a device the household stops knowing")
    void knowsFollowsTheRecord() {
        final SecurityHousehold h = fresh();
        h.setRequiredLevel(at(42L), 4);
        h.clearRequiredLevel(at(42L));
        assertFalse(h.knows(at(42L)), "cleared is not remembered");

        h.setEntrance(at(42L));
        h.clearEntrance();
        assertFalse(h.knows(at(42L)), "and neither is a cleared entrance");
    }

    @Test
    @DisplayName("Knowing survives the round trip, because the sweep runs after a reload")
    void knowsSurvivesSave() {
        final SecurityHousehold h = fresh();
        h.setName(at(99L), "Vault");
        final SecurityHousehold back = fresh();
        back.load(h.save());
        assertTrue(back.knows(at(99L)));
    }

    @Test
    @DisplayName("A sealed house comes back sealed, on the key it already had")
    void awayPlusSurvivesSave() {
        final SecurityHousehold h = fresh();
        h.setMode(SecurityMode.AWAY_PLUS);
        final SecurityHousehold back = fresh();
        back.load(h.save());
        assertEquals(SecurityMode.AWAY_PLUS, back.mode());
        assertEquals(3, h.save().getInt("Mode"),
                "the mode is persisted as its ordinal, so appending was the whole migration");
    }

    @Test
    @DisplayName("Sealing the house adds no key to the save at all")
    void awayPlusAddsNoPersistentState() {
        final SecurityHousehold away = fresh();
        away.setMode(SecurityMode.AWAY);
        away.setShutterSealed(at(30L), true);

        final SecurityHousehold sealed = fresh();
        sealed.setMode(SecurityMode.AWAY_PLUS);
        sealed.setShutterSealed(at(30L), true);

        assertEquals(away.save().getAllKeys(), sealed.save().getAllKeys(),
                "AWAY+ must persist nothing that AWAY does not");
    }
}
