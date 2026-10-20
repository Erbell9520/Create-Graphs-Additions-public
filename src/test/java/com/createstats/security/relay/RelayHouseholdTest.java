package com.createstats.security.relay;

import com.createstats.security.Presence;
import com.createstats.security.SecurityEndpoint;
import com.createstats.security.SecurityEvent;
import com.createstats.security.SecurityHousehold;
import com.createstats.security.SecurityKinds;
import com.createstats.security.SecurityLog;
import com.createstats.security.SecurityMode;
import com.createstats.security.SecurityRole;
import com.createstats.security.Truth;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelayHouseholdTest {
    private static final String DIM = "minecraft:overworld";
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-0000000000b3");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000000b4");
    private static final UUID RELAY = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    private static SecurityEndpoint at(long p) {
        return SecurityEndpoint.of(DIM, p);
    }

    private static SecurityHousehold house() {
        final SecurityHousehold h = new SecurityHousehold(UUID.randomUUID());
        h.roster().setOwner(OWNER, "Owner");
        h.roster().add(ADMIN, "Admin");
        h.roster().setRole(ADMIN, SecurityRole.ADMIN);
        h.roster().add(MEMBER, "Member");
        return h;
    }

    private static RelaySubscription claimed(SecurityHousehold h) {
        return h.claimRelay(RELAY, at(500), OWNER, "Owner", 10L);
    }

    @Test
    @DisplayName("Only owner or admin may enrol a relay; a member or a stranger is refused")
    void claimingIsAuthorised() {
        final SecurityHousehold h = house();
        assertNull(h.claimRelay(RELAY, at(500), MEMBER, "Member", 1L));
        assertNull(h.claimRelay(RELAY, at(500), STRANGER, "Stranger", 1L));
        final RelaySubscription sub = h.claimRelay(RELAY, at(500), ADMIN, "Admin", 2L);
        assertNotNull(sub);
        assertEquals(RelayConfig.DEFAULT, sub.config());
        assertTrue(h.isEnrolled(at(500)), "a relay is a member of the house like any device");
        assertNull(h.claimRelay(RELAY, at(600), OWNER, "Owner", 3L),
                "the same id somewhere else is a copied identity, never a move");
    }

    @Test
    @DisplayName("Two panels editing one relay: the stale edit is refused and the first is kept")
    void revisionConflict() {
        final SecurityHousehold h = house();
        final RelaySubscription sub = claimed(h);
        final long seen = sub.revision();
        final RelayConfig fireHold = new RelayConfig(RelayConditions.FIRE.id(), RelayBehavior.HOLD, 10, "away", null);
        final RelayConfig floodFollow = new RelayConfig(RelayConditions.WATER.id(), RelayBehavior.FOLLOW, 10, "away",
                null);
        assertEquals(RelayRules.ConfigVerdict.APPLIED, h.configureRelay(RELAY, fireHold, seen, OWNER, "Owner", 20L));
        assertEquals(RelayRules.ConfigVerdict.STALE, h.configureRelay(RELAY, floodFollow, seen, ADMIN, "Admin", 21L),
                "the back panel drew revision " + seen + " and must not overwrite the front panel's edit");
        assertEquals(fireHold, h.relay(RELAY).config());
        assertEquals(RelayRules.ConfigVerdict.FORBIDDEN,
                h.configureRelay(RELAY, floodFollow, h.relay(RELAY).revision(), MEMBER, "Member", 22L));
    }

    @Test
    @DisplayName("An event accepted while the relay is asleep is waiting for it; nothing older replays")
    void heldWhileAsleep() {
        final SecurityHousehold h = house();
        h.acceptEvent(SecurityEvent.TAMPER_ACCEPTED, at(9), 5L);
        final RelaySubscription sub = claimed(h);
        final RelayConfig hold = new RelayConfig(RelayConditions.TAMPER.id(), RelayBehavior.HOLD, 10, "away", null);
        h.configureRelay(RELAY, hold, sub.revision(), OWNER, "Owner", 20L);
        assertFalse(h.relay(RELAY).state().pending(), "the tamper before binding is history");

        h.acceptEvent(SecurityEvent.TAMPER_ACCEPTED, at(9), 30L);
        assertTrue(h.relay(RELAY).state().pending(), "accepted while the relay slept, held for it");
    }

    @Test
    @DisplayName("Narrowed relays hear only their device's events")
    void scopedEvents() {
        final SecurityHousehold h = house();
        h.enroll(at(9), SecurityKinds.DOOR, 1L);
        h.enroll(at(8), SecurityKinds.DOOR, 1L);
        final RelaySubscription sub = claimed(h);
        final RelayConfig frontDoor = new RelayConfig(RelayConditions.DENIED.id(), RelayBehavior.HOLD, 10, "away",
                at(9));
        assertEquals(RelayRules.ConfigVerdict.APPLIED,
                h.configureRelay(RELAY, frontDoor, sub.revision(), OWNER, "Owner", 2L));
        h.acceptEvent(SecurityEvent.ACCESS_DENIED, at(8), 3L);
        assertFalse(h.relay(RELAY).state().pending(), "the back door's refusal is not the front door's");
        h.acceptEvent(SecurityEvent.ACCESS_DENIED, at(9), 4L);
        assertTrue(h.relay(RELAY).state().pending());
    }

    @Test
    @DisplayName("Hold on a sustained fact latches through the household clock with no relay ticking")
    void clockLatches() {
        final SecurityHousehold h = house();
        final RelaySubscription sub = claimed(h);
        final RelayConfig lockdownHold = new RelayConfig(RelayConditions.LOCKDOWN.id(), RelayBehavior.HOLD, 10,
                "away", null);
        h.configureRelay(RELAY, lockdownHold, sub.revision(), OWNER, "Owner", 1L);
        h.advance(2L);
        assertFalse(h.relay(RELAY).state().pending());
        h.raiseLockdown(3L, "zombie");
        h.advance(3L);
        assertTrue(h.relay(RELAY).state().pending());
        h.clearLockdown();
        h.advance(4L);
        assertTrue(h.relay(RELAY).state().pending(), "clearing the lockdown does not reset the relay");
        assertEquals(Truth.FALSE, h.relay(RELAY).reading().truth());
    }

    @Test
    @DisplayName("Reset clears this relay only: not the lockdown, not the mode, not the log's history")
    void resetIsLocal() {
        final SecurityHousehold h = house();
        final RelaySubscription sub = claimed(h);
        h.configureRelay(RELAY, new RelayConfig(RelayConditions.LOCKDOWN.id(), RelayBehavior.HOLD, 10, "away", null),
                sub.revision(), OWNER, "Owner", 1L);
        h.setMode(SecurityMode.AWAY);
        h.raiseLockdown(2L, "zombie");
        h.advance(2L);
        assertEquals(RelayRules.ResetVerdict.STILL_ACTIVE, h.resetRelay(RELAY, OWNER, "Owner", true, 3L),
                "a standing lockdown cannot be reset at a relay");
        assertTrue(h.lockdown());
        h.clearLockdown();
        assertEquals(RelayRules.ResetVerdict.FORBIDDEN, h.resetRelay(RELAY, MEMBER, "Member", true, 4L));
        assertEquals(RelayRules.ResetVerdict.NO_CONTACT, h.resetRelay(RELAY, OWNER, "Owner", false, 5L));
        final int linesBefore = h.log().size();
        assertEquals(RelayRules.ResetVerdict.RESET, h.resetRelay(RELAY, ADMIN, "Admin", true, 6L));
        assertFalse(h.relay(RELAY).state().pending());
        assertEquals(SecurityMode.AWAY, h.mode(), "the mode is the owner's, not the relay's");
        assertEquals(linesBefore + 1, h.log().size(), "one audit line, nothing removed");
    }

    @Test
    @DisplayName("A reset leaves a standing lockdown, an open fire release and the roster exactly as they were")
    void resetTouchesNothingElse() {
        final SecurityHousehold h = house();
        final RelaySubscription sub = claimed(h);

        h.configureRelay(RELAY, new RelayConfig(RelayConditions.TAMPER.id(), RelayBehavior.HOLD, 10, "away", null),
                sub.revision(), OWNER, "Owner", 1L);
        h.setMode(SecurityMode.AWAY);
        h.raiseLockdown(2L, "tamper");
        h.acceptEvent(SecurityEvent.TAMPER_ACCEPTED, at(9), 2L);
        h.reportFire(3L);
        h.advance(3L);
        assertTrue(h.relay(RELAY).state().pending(), "setup: the tamper is held");
        final net.minecraft.nbt.CompoundTag rosterBefore = h.roster().save();
        assertEquals(RelayRules.ResetVerdict.RESET, h.resetRelay(RELAY, OWNER, "Owner", true, 4L));
        assertFalse(h.relay(RELAY).state().pending());
        assertTrue(h.lockdown(), "a reset at a relay is not the owner clearing the lockdown");
        assertTrue(h.released(4L), "and it does not shut a fire release, which the doors are obeying");
        assertEquals(rosterBefore, h.roster().save(), "and it does not touch who may do what");
        assertEquals(SecurityMode.AWAY, h.mode());
    }

    @Test
    @DisplayName("Retuning with an unresolved incident is refused; decommissioning it is owner only and logged")
    void retireRules() {
        final SecurityHousehold h = house();
        final RelaySubscription sub = claimed(h);
        h.configureRelay(RELAY, new RelayConfig(RelayConditions.TAMPER.id(), RelayBehavior.HOLD, 10, "away", null),
                sub.revision(), OWNER, "Owner", 1L);
        h.acceptEvent(SecurityEvent.TAMPER_ACCEPTED, at(9), 2L);
        assertEquals(RelayRules.ConfigVerdict.PENDING_INCIDENT, h.configureRelay(RELAY, RelayConfig.DEFAULT,
                h.relay(RELAY).revision(), OWNER, "Owner", 3L));
        assertEquals(RelayRules.RetireVerdict.OWNER_ONLY_WHILE_HELD, h.retireRelay(RELAY, ADMIN, "Admin", 4L));
        assertNotNull(h.relay(RELAY));
        assertEquals(RelayRules.RetireVerdict.RETIRED, h.retireRelay(RELAY, OWNER, "Owner", 5L));
        assertNull(h.relay(RELAY));
        assertEquals(SecurityLog.Verdict.FAULT, h.log().recent(1).get(0).verdict(),
                "an incident ended unresolved is on the record as one");
        assertTrue(h.released(at(500)), "the relay is free to be claimed again");
    }

    @Test
    @DisplayName("A held incident survives save and reload; the baseline does not, so nothing fires on load")
    void persistence() {
        final SecurityHousehold h = house();
        final RelaySubscription sub = claimed(h);
        h.configureRelay(RELAY, new RelayConfig(RelayConditions.FIRE.id(), RelayBehavior.PULSE, 25, "away", null),
                sub.revision(), OWNER, "Owner", 1L);
        h.relay(RELAY).setState(new RelayState(7L, 5_000L, false, Truth.FALSE, 4_975L));
        final SecurityHousehold back = new SecurityHousehold(h.id());
        back.load(h.save());
        final RelaySubscription loaded = back.relay(RELAY);
        assertNotNull(loaded);
        assertEquals(h.relay(RELAY).config(), loaded.config());
        assertEquals(h.relay(RELAY).revision(), loaded.revision());
        assertEquals(7L, loaded.state().lastEventSequence());
        assertEquals(5_000L, loaded.state().pulseUntil());
        assertEquals(Truth.UNKNOWN, loaded.state().baseline(), "a reload knows nothing about the world yet");
        assertEquals(Truth.UNKNOWN, loaded.reading().truth());

        final SecurityHousehold held = house();
        final RelaySubscription hs = claimed(held);
        held.configureRelay(RELAY, new RelayConfig(RelayConditions.DENIED.id(), RelayBehavior.HOLD, 10, "away",
                null), hs.revision(), OWNER, "Owner", 1L);
        held.acceptEvent(SecurityEvent.ACCESS_DENIED, at(3), 2L);
        final SecurityHousehold heldBack = new SecurityHousehold(held.id());
        heldBack.load(held.save());
        assertTrue(heldBack.relay(RELAY).state().pending(), "a held incident survives a normal save");
        assertEquals(1L, heldBack.eventSequence());
        heldBack.acceptEvent(SecurityEvent.ACCESS_DENIED, at(3), 3L);
        assertEquals(2L, heldBack.eventSequence(), "the counter carries on, it never restarts");
    }

    @Test
    @DisplayName("A relay from a newer build is kept verbatim and drives nothing")
    void unknownIsPreserved() {
        final CompoundTag tag = RelayCodec.save(new RelaySubscription(RELAY, at(500), RelayConfig.DEFAULT, 4L,
                RelayState.seed(0), 1L, null));
        tag.putString("Cond", "create_stats:future_condition");
        tag.putString("Future", "kept");
        final RelaySubscription loaded = RelayCodec.load(tag);
        assertNotNull(loaded);
        assertFalse(loaded.supported());
        assertEquals("kept", RelayCodec.save(loaded).getString("Future"), "written back unchanged");
        assertEquals(0, RelayRules.signal(loaded.config(), loaded.state(), Truth.TRUE, true, RelayContact.LINKED,
                1, RelayState.NEVER).level());
    }

    @Test
    @DisplayName("Malformed rows grant nothing: no id or no place is not a relay, and numbers are clamped")
    void malformedRows() {
        final CompoundTag noId = new CompoundTag();
        noId.putLong("P", 1L);
        assertNull(RelayCodec.load(noId));
        final CompoundTag noPlace = new CompoundTag();
        noPlace.putUUID("Relay", RELAY);
        assertNull(RelayCodec.load(noPlace));
        final CompoundTag huge = RelayCodec.save(new RelaySubscription(RELAY, at(500), RelayConfig.DEFAULT, 0L,
                RelayState.seed(0), 1L, null));
        huge.putInt("Pulse", 1_000_000);
        huge.putLong("Rev", -5L);
        final RelaySubscription loaded = RelayCodec.load(huge);
        assertEquals(RelayConfig.MAX_PULSE, loaded.config().pulseTicks());
        assertEquals(0L, loaded.revision());
    }

    private static final UUID PUT_BACK = UUID.fromString("00000000-0000-0000-0000-0000000000c2");

    private static RelayConfig breachHoldOnDoor() {
        return new RelayConfig(RelayConditions.BREACHED.id(), RelayBehavior.HOLD, 10, "away", at(1));
    }

    @Test
    @DisplayName("SEC-11: a relay put back on its spot takes over the record there, settings and all")
    void putBackTakesOverItsPlace() {
        final SecurityHousehold h = house();
        h.enroll(at(1), SecurityKinds.DOOR, 0L);
        final RelaySubscription first = claimed(h);
        assertEquals(RelayRules.ConfigVerdict.APPLIED, h.configureRelay(RELAY, breachHoldOnDoor(),
                first.revision(), OWNER, "Owner", 5L));
        final long revision = h.relay(RELAY).revision();

        final RelaySubscription back = h.claimRelay(PUT_BACK, at(500), OWNER, "Owner", 9L);
        assertNotNull(back);
        assertEquals(PUT_BACK, back.relayId(), "the record answers to the block now standing there");
        assertEquals(breachHoldOnDoor(), back.config(), "the door and the behaviour came with it");
        assertEquals(revision, back.revision(), "a screen's revision stays valid across the swap");
        assertNull(h.relay(RELAY), "the old id is gone, not left as a ghost");
        assertEquals(1, h.relays().size(), "one place, one record");
        assertTrue(h.log().recent(50).stream().anyMatch(l -> l.detail().startsWith("put back a Security Relay")));
    }

    @Test
    @DisplayName("SEC-11: taking over is an enrolment, so a member's relay on that spot takes nothing")
    void putBackNeedsTheRight() {
        final SecurityHousehold h = house();
        claimed(h);
        assertNull(h.claimRelay(PUT_BACK, at(500), MEMBER, "Member", 9L));
        assertNull(h.claimRelay(PUT_BACK, at(500), STRANGER, "Stranger", 9L));
        assertNotNull(h.relay(RELAY), "the record stays where it was, for somebody who may accept it");
    }

    @Test
    @DisplayName("SEC-11: a held incident is still held by the relay that takes the place over")
    void putBackKeepsAHeldIncident() {
        final SecurityHousehold h = house();
        final RelaySubscription sub = claimed(h);
        h.configureRelay(RELAY, new RelayConfig(RelayConditions.DENIED.id(), RelayBehavior.HOLD, 10, "away", null),
                sub.revision(), OWNER, "Owner", 1L);
        h.acceptEvent(SecurityEvent.ACCESS_DENIED, at(3), 2L);
        assertTrue(h.relay(RELAY).state().pending());
        final RelaySubscription back = h.claimRelay(PUT_BACK, at(500), ADMIN, "Admin", 9L);
        assertTrue(back.state().pending(), "breaking the block is not a way to reset an incident");
    }

    @Test
    @DisplayName("SEC-11: a 1.15.24 save holding two records at one place settles to the one the panel showed")
    void legacyGhostSettles() {
        final SecurityHousehold h = house();
        h.enroll(at(1), SecurityKinds.DOOR, 0L);
        final RelaySubscription ghost = claimed(h);
        h.configureRelay(RELAY, breachHoldOnDoor(), ghost.revision(), OWNER, "Owner", 5L);
        h.claimRelay(PUT_BACK, at(501), OWNER, "Owner", 6L);

        final CompoundTag saved = h.save();
        final net.minecraft.nbt.ListTag rows = saved.getList("Relays", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < rows.size(); i++) {
            if (rows.getCompound(i).getUUID("Relay").equals(PUT_BACK)) {
                rows.getCompound(i).putLong("P", 500L);
            }
        }
        final SecurityHousehold back = new SecurityHousehold(h.id());
        back.load(saved);
        assertEquals(2, back.relays().size(), "the save really holds two records at one place");

        final RelaySubscription standing = back.relayStandingAt(PUT_BACK, at(500), 20L);
        assertNotNull(standing);
        assertEquals(PUT_BACK, standing.relayId(), "kept under the id of the block that stands there");
        assertEquals(breachHoldOnDoor(), standing.config(),
                "the oldest record wins: it is the one the Relays tab showed and the owner edited");
        assertEquals(1, back.relays().size());
        assertNull(back.relay(RELAY));
        assertTrue(back.log().recent(50).stream().anyMatch(l -> l.detail().startsWith("dropped a duplicate")));
        assertEquals(standing, back.relayStandingAt(PUT_BACK, at(500), 21L), "settled once, then left alone");
    }

    @Test
    @DisplayName("SEC-12: a door broken out of the wall trips Breached through the clock, and a pulse fires")
    void brokenDoorIsABreach() {
        final SecurityHousehold h = house();
        h.enroll(at(1), SecurityKinds.DOOR, 0L);
        final RelaySubscription sub = claimed(h);
        h.configureRelay(RELAY, new RelayConfig(RelayConditions.BREACHED.id(), RelayBehavior.PULSE, 10, "away",
                at(1)), sub.revision(), OWNER, "Owner", 1L);
        h.sight(at(1), SecurityKinds.DOOR, Presence.PRESENT, false, false, 10L);
        h.advance(11L);
        assertEquals(Truth.FALSE, h.relay(RELAY).reading().truth(), "a standing door is not breached");
        assertTrue(h.markMissing(at(1), 12L), "the panel's sweep finds the door gone");
        assertFalse(h.markMissing(at(1), 13L), "once, so the log gets one line");
        h.advance(12L);
        assertEquals(Truth.TRUE, h.relay(RELAY).reading().truth(), "a door gone from its place is breached");
        assertEquals(12L + 10L, h.relay(RELAY).state().pulseUntil(),
                "straight from FALSE to TRUE, so the pulse edge is seen");
        h.enroll(at(1), SecurityKinds.DOOR, 30L);
        h.sight(at(1), SecurityKinds.DOOR, Presence.PRESENT, false, false, 30L);
        h.advance(31L);
        assertEquals(Truth.FALSE, h.relay(RELAY).reading().truth(), "a door put back ends the breach");
    }

    @Test
    @DisplayName("A double door: the leaf that is not the row's anchor broken out latches a relay held on the doorway")
    void doubleDoorOtherLeaf() {
        final SecurityHousehold h = house();
        h.enroll(at(1), SecurityKinds.DOOR, 0L);
        h.enrollLeaf(at(2), at(1), SecurityKinds.DOOR, 0L);
        assertEquals(at(1), h.doorwayOf(at(2)));
        assertEquals(at(1), h.doorwayOf(at(1)), "the anchor is its own doorway");
        final RelaySubscription sub = claimed(h);
        h.configureRelay(RELAY, new RelayConfig(RelayConditions.BREACHED.id(), RelayBehavior.HOLD, 10, "away",
                at(1)), sub.revision(), OWNER, "Owner", 1L);
        h.sight(at(1), SecurityKinds.DOOR, Presence.PRESENT, false, false, 10L);
        h.sight(at(2), SecurityKinds.DOOR, Presence.PRESENT, false, false, 10L);
        h.advance(11L);
        assertEquals(Truth.FALSE, h.relay(RELAY).reading().truth(), "both leaves standing");
        assertTrue(h.markMissing(at(2), 12L), "the sweep finds the second leaf gone; the anchor still stands");
        h.sight(at(1), SecurityKinds.DOOR, Presence.PRESENT, false, false, 12L);
        h.advance(12L);
        assertEquals(Truth.TRUE, h.relay(RELAY).reading().truth(), "the doorway is open");
        assertTrue(h.relay(RELAY).state().pending(), "and the hold latched");

        final SecurityHousehold back = new SecurityHousehold(h.id());
        back.load(h.save());
        assertEquals(at(1), back.doorwayOf(at(2)), "the leaf's doorway survives a save");
        assertTrue(back.enrollment(at(2)).missing());

        assertTrue(h.retire(at(1), OWNER, "Owner", 20L));
        assertEquals(at(2), h.doorwayOf(at(2)), "a retired doorway frees its leaf");
        h.enrollLeaf(at(3), at(3), SecurityKinds.DOOR, 21L);
        assertEquals(at(3), h.doorwayOf(at(3)), "a leaf is never its own doorway's leaf");
    }

    @Test
    @DisplayName("Device trouble through the clock: a sensor that goes unseen is trouble within the freshness window")
    void troubleThroughTheClock() {
        final SecurityHousehold h = house();
        h.enroll(at(1), SecurityKinds.SENSOR_MOTION, 0L);
        final RelaySubscription sub = claimed(h);
        h.configureRelay(RELAY, new RelayConfig(RelayConditions.TROUBLE.id(), RelayBehavior.FOLLOW, 10, "away", null),
                sub.revision(), OWNER, "Owner", 1L);
        h.sight(at(1), SecurityKinds.SENSOR_MOTION, Presence.PRESENT, false, false, 10L);
        h.advance(20L);
        assertEquals(Truth.FALSE, h.relay(RELAY).reading().truth());
        h.advance(10L + SecurityHousehold.SIGHTING_FRESH_TICKS + 1);
        assertEquals(Truth.TRUE, h.relay(RELAY).reading().truth(), "nobody has seen the sensor lately");
    }
}
