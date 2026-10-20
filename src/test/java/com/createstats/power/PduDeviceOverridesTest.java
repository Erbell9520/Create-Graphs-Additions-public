package com.createstats.power;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PduDeviceOverridesTest {
    private static long at(int x, int y, int z) {
        return new BlockPos(x, y, z).asLong();
    }

    @Test
    void unsetPositionReportsTheDefaults() {
        final PduDeviceOverrides overrides = new PduDeviceOverrides();
        assertEquals(FePriority.DEFAULT, overrides.priority(at(1, 2, 3)));
        assertEquals(FeLock.NONE, overrides.lock(at(1, 2, 3)));
        assertFalse(overrides.has(at(1, 2, 3)));
        assertEquals(0, overrides.size());
    }

    @Test
    void tierAndLockAreKeptIndependently() {
        final PduDeviceOverrides overrides = new PduDeviceOverrides();
        final long pos = at(10, 64, -30);
        overrides.setPriority(pos, FePriority.CRITICAL);
        assertEquals(FePriority.CRITICAL, overrides.priority(pos));
        assertEquals(FeLock.NONE, overrides.lock(pos));

        overrides.setLock(pos, FeLock.FORCE_OFF);
        assertEquals(FePriority.CRITICAL, overrides.priority(pos),
                "setting a lock must not disturb the tier stored beside it");
        assertEquals(FeLock.FORCE_OFF, overrides.lock(pos));
    }

    @Test
    void everyTierAndLockPairSurvivesThePacking() {
        final PduDeviceOverrides overrides = new PduDeviceOverrides();
        long pos = 0;
        for (FePriority tier : FePriority.values()) {
            for (FeLock lock : FeLock.values()) {
                final long key = pos++;
                overrides.setPriority(key, tier);
                overrides.setLock(key, lock);
                assertEquals(tier, overrides.priority(key), tier + " with " + lock);
                assertEquals(lock, overrides.lock(key), tier + " with " + lock);
            }
        }
    }

    @Test
    void writingTheDefaultPairErasesTheEntry() {
        final PduDeviceOverrides overrides = new PduDeviceOverrides();
        final long pos = at(4, 5, 6);
        overrides.setPriority(pos, FePriority.LOW);
        assertTrue(overrides.has(pos));
        overrides.setPriority(pos, FePriority.DEFAULT);
        assertFalse(overrides.has(pos), "a device back at the defaults is a device with no setting");
        assertEquals(0, overrides.size());
    }

    @Test
    void aLockAloneKeepsTheEntryAlive() {
        final PduDeviceOverrides overrides = new PduDeviceOverrides();
        final long pos = at(7, 8, 9);
        overrides.setLock(pos, FeLock.FORCE_ON);
        assertTrue(overrides.has(pos));
        assertEquals(1, overrides.size());
    }

    @Test
    void forgetDropsOnePositionAndLeavesTheRest() {
        final PduDeviceOverrides overrides = new PduDeviceOverrides();
        overrides.setPriority(at(1, 0, 0), FePriority.HIGH);
        overrides.setPriority(at(2, 0, 0), FePriority.LOW);
        overrides.forget(at(1, 0, 0));
        assertEquals(FePriority.DEFAULT, overrides.priority(at(1, 0, 0)));
        assertEquals(FePriority.LOW, overrides.priority(at(2, 0, 0)));
    }

    @Test
    void savesNothingWhenThereIsNothingToSave() {
        final CompoundTag tag = new CompoundTag();
        new PduDeviceOverrides().save(tag, "Ovr");
        assertTrue(tag.isEmpty(),
                "an ordinary branch has no third party machines and must cost the tag no bytes");
    }

    @Test
    void roundTripsThroughNbt() {
        final PduDeviceOverrides written = new PduDeviceOverrides();
        written.setPriority(at(1, 2, 3), FePriority.CRITICAL);
        written.setLock(at(1, 2, 3), FeLock.FORCE_ON);
        written.setPriority(at(-40, 70, 900), FePriority.OPTIONAL);

        final CompoundTag tag = new CompoundTag();
        written.save(tag, "Ovr");

        final PduDeviceOverrides read = new PduDeviceOverrides();
        read.load(tag, "Ovr");
        assertEquals(2, read.size());
        assertEquals(FePriority.CRITICAL, read.priority(at(1, 2, 3)));
        assertEquals(FeLock.FORCE_ON, read.lock(at(1, 2, 3)));
        assertEquals(FePriority.OPTIONAL, read.priority(at(-40, 70, 900)));
        assertEquals(FeLock.NONE, read.lock(at(-40, 70, 900)));
    }

    @Test
    void loadingAnAbsentKeyClearsRatherThanThrows() {
        final PduDeviceOverrides overrides = new PduDeviceOverrides();
        overrides.setPriority(at(1, 1, 1), FePriority.HIGH);
        overrides.load(new CompoundTag(), "Ovr");
        assertEquals(0, overrides.size(),
                "a panel loaded from a tag with no overrides must not keep the ones it had");
    }

    @Test
    void aTruncatedTagIsReadAsFarAsItGoes() {
        final CompoundTag inner = new CompoundTag();
        inner.putLongArray("P", new long[]{at(1, 0, 0), at(2, 0, 0), at(3, 0, 0)});
        inner.putByteArray("V", new byte[]{(byte) FePriority.HIGH.ordinal()});
        final CompoundTag tag = new CompoundTag();
        tag.put("Ovr", inner);

        final PduDeviceOverrides overrides = new PduDeviceOverrides();
        overrides.load(tag, "Ovr");
        assertEquals(1, overrides.size());
        assertEquals(FePriority.HIGH, overrides.priority(at(1, 0, 0)));
    }
}
