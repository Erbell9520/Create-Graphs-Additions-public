package com.createstats.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.createstats.support.SupportPolicy.Basis;
import com.createstats.support.SupportPolicy.Decision;
import com.createstats.support.SupportPolicy.Facts;
import com.createstats.support.SupportPolicy.Level;
import org.junit.jupiter.api.Test;

class SupportPolicyTest {
    private static Decision creator(boolean online, boolean key, boolean authority, Level granted) {
        return SupportPolicy.decide(new Facts(true, online, key, authority, granted));
    }

    private static Decision other(boolean online, boolean authority, Level granted) {
        return SupportPolicy.decide(new Facts(false, online, false, authority, granted));
    }

    @Test
    void creatorNeverLockedOutOfOwnWorldOrServer() {
        for (boolean online : new boolean[]{true, false}) {
            Decision d = creator(online, false, true, Level.NONE);
            assertEquals(Level.TIER2, d.level());
            assertEquals(Basis.AUTHORITY, d.basis());
            assertFalse(d.basis().borrowed(), "own authority is not undone at logout");
        }
    }

    @Test
    void creatorOnForeignOnlineServerNeedsKeyForTier2() {
        Decision noKey = creator(true, false, false, Level.NONE);
        assertEquals(Level.TIER1, noKey.level());
        assertEquals(Basis.CREATOR_ONLINE, noKey.basis());
        assertTrue(noKey.tier2Note().contains(SupportKeyProof.KEY_FILE_NAME));

        Decision withKey = creator(true, true, false, Level.NONE);
        assertEquals(Level.TIER2, withKey.level());
        assertEquals(Basis.CREATOR_KEY, withKey.basis());
        assertTrue(withKey.basis().borrowed());
    }

    @Test
    void keyIsIgnoredOnOfflineServers() {
        Decision d = creator(false, true, false, Level.NONE);
        assertEquals(Level.SAFE, d.level());
        assertEquals(Basis.CREATOR_NAME_ONLY, d.basis());
    }

    @Test
    void creatorNameOnOfflineServerGetsSafeViewOnly() {
        Decision d = creator(false, false, false, Level.NONE);
        assertEquals(Level.SAFE, d.level());
        assertFalse(d.atLeast(Level.TIER1));
    }

    @Test
    void ownerGrantLiftsCreatorOnOfflineServer() {
        assertEquals(Level.TIER2, creator(false, false, false, Level.TIER2).level());
        assertEquals(Basis.GRANT, creator(false, false, false, Level.TIER2).basis());
        assertEquals(Level.TIER1, creator(false, false, false, Level.TIER1).level());
    }

    @Test
    void grantLiftsCreatorWithoutKeyOnOnlineServer() {
        Decision d = creator(true, false, false, Level.TIER2);
        assertEquals(Level.TIER2, d.level());
        assertEquals(Basis.GRANT, d.basis());
    }

    @Test
    void otherPlayersGetNothingWithoutAGrantEvenAsOperators() {
        assertEquals(Level.NONE, other(true, false, Level.NONE).level());
        assertEquals(Level.NONE, other(true, true, Level.NONE).level(),
            "being an operator is not consent to the support tooling");
        assertEquals(Level.NONE, other(false, true, Level.NONE).level());
    }

    @Test
    void otherPlayersGetExactlyWhatWasGranted() {
        Decision t1 = other(true, false, Level.TIER1);
        assertEquals(Level.TIER1, t1.level());
        assertEquals(Basis.GRANT, t1.basis());
        assertFalse(t1.tier2Note().isEmpty());

        Decision t2 = other(false, false, Level.TIER2);
        assertEquals(Level.TIER2, t2.level());
        assertTrue(t2.basis().borrowed());
    }

    @Test
    void levelsAreOrdered() {
        assertTrue(Level.TIER2.atLeast(Level.TIER1));
        assertTrue(Level.TIER1.atLeast(Level.SAFE));
        assertTrue(Level.SAFE.atLeast(Level.NONE));
        assertFalse(Level.SAFE.atLeast(Level.TIER1));
        assertEquals(Level.TIER2, Level.max(Level.TIER1, Level.TIER2));
        assertEquals(Level.NONE, SupportPolicy.decide(new Facts(false, true, true, true, null)).level());
    }
}
