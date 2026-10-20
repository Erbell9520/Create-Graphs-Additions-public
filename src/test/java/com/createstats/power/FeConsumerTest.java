package com.createstats.power;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeConsumerTest {
    private static final class TestSettings implements FeConsumer.Settings {
        boolean enabled = true;
        int capacity = 1000;
        int idle = 5;
        int active = 20;
        int burst = 100;

        int rebootPct = 0;

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public int rebootThresholdPct() {
            return rebootPct;
        }

        @Override
        public int capacity() {
            return capacity;
        }

        @Override
        public int idleDrain() {
            return idle;
        }

        @Override
        public int activeDrain() {
            return active;
        }

        @Override
        public int burstCost() {
            return burst;
        }
    }

    @Test
    void disabledModeDoesNotDrainButStillAcceptsFe() {
        TestSettings settings = new TestSettings();
        settings.enabled = false;
        FeConsumer fe = new FeConsumer(settings);
        assertTrue(fe.serviceTick(true));
        assertTrue(fe.serviceTick(false));
        assertTrue(fe.spendBurst());
        assertTrue(fe.isPowered());
        assertEquals(100.0f, fe.hudBufferPct(), 0.001f);
        assertEquals(0, fe.getEnergyStored());

        assertTrue(fe.canReceive());
        assertEquals(500, fe.receiveEnergy(500, false));
        assertEquals(500, fe.getEnergyStored());
        assertTrue(fe.shouldDisplay());
        assertEquals(50.0f, fe.fillPct(), 0.001f);
    }

    @Test
    void receiveFillsToCapacityAndNoFurther() {
        FeConsumer fe = new FeConsumer(new TestSettings());
        assertEquals(600, fe.receiveEnergy(600, false));
        assertEquals(400, fe.receiveEnergy(600, false));
        assertEquals(0, fe.receiveEnergy(600, false));
        assertEquals(1000, fe.getEnergyStored());
        assertEquals(100.0f, fe.fillPct(), 0.001f);
    }

    @Test
    void receiveSimulationDoesNotStore() {
        FeConsumer fe = new FeConsumer(new TestSettings());
        assertEquals(500, fe.receiveEnergy(500, true));
        assertEquals(0, fe.getEnergyStored());
    }

    @Test
    void extractIsAlwaysRefused() {
        FeConsumer fe = new FeConsumer(new TestSettings());
        fe.receiveEnergy(1000, false);
        assertEquals(0, fe.extractEnergy(500, false));
        assertEquals(0, fe.extractEnergy(500, true));
        assertFalse(fe.canExtract());
        assertEquals(1000, fe.getEnergyStored());
    }

    @Test
    void idleVersusActiveDrain() {
        FeConsumer fe = new FeConsumer(new TestSettings());
        fe.receiveEnergy(1000, false);
        assertTrue(fe.serviceTick(false));
        assertEquals(995, fe.getEnergyStored());
        assertTrue(fe.serviceTick(true));
        assertEquals(975, fe.getEnergyStored());
    }

    @Test
    void hardCutoffAtEmptyAndRecovery() {
        TestSettings settings = new TestSettings();
        settings.capacity = 50;
        settings.active = 20;
        FeConsumer fe = new FeConsumer(settings);
        fe.receiveEnergy(50, false);
        assertTrue(fe.serviceTick(true));
        assertTrue(fe.serviceTick(true));
        assertFalse(fe.serviceTick(true));
        assertEquals(0, fe.getEnergyStored());
        assertFalse(fe.isPowered());
        assertEquals(-1, fe.pollTransition());
        assertEquals(0, fe.pollTransition());
        fe.receiveEnergy(50, false);
        assertTrue(fe.serviceTick(true));
        assertTrue(fe.isPowered());
        assertEquals(1, fe.pollTransition());
    }

    @Test
    void burstRefusalDoesNotCutPower() {
        TestSettings settings = new TestSettings();
        settings.burst = 500;
        FeConsumer fe = new FeConsumer(settings);
        fe.receiveEnergy(400, false);
        assertTrue(fe.serviceTick(false));
        assertFalse(fe.spendBurst());
        assertTrue(fe.isPowered());
        fe.receiveEnergy(600, false);
        assertTrue(fe.spendBurst());
        assertTrue(fe.getEnergyStored() < 500);
    }

    @Test
    void persistenceRoundTripKeepsEnergyAndPoweredFlag() {
        TestSettings settings = new TestSettings();
        FeConsumer fe = new FeConsumer(settings);
        fe.receiveEnergy(321, false);
        fe.serviceTick(true);
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        fe.save(tag);
        FeConsumer restored = new FeConsumer(settings);
        restored.load(tag);
        assertEquals(fe.getEnergyStored(), restored.getEnergyStored());
        assertEquals(fe.isPowered(), restored.isPowered());
    }

    @Test
    void onChangedFiresOnPowerFlipAndFillChange() {
        TestSettings settings = new TestSettings();
        settings.capacity = 100;
        settings.idle = 0;
        settings.active = 60;
        AtomicInteger fired = new AtomicInteger();
        FeConsumer fe = new FeConsumer(settings, fired::incrementAndGet);
        fe.receiveEnergy(100, false);
        assertTrue(fired.get() > 0);
        int after = fired.get();
        assertTrue(fe.serviceTick(true));
        assertTrue(fired.get() > after);
        after = fired.get();
        assertFalse(fe.serviceTick(true));
        assertTrue(fired.get() > after);
    }

    @Test
    void loadMultiplierScalesActiveDrainOnly() {
        TestSettings settings = new TestSettings();
        settings.idle = 5;
        settings.active = 20;
        FeConsumer fe = new FeConsumer(settings);
        fe.receiveEnergy(1000, false);

        assertTrue(fe.serviceTick(true, 2.0f));
        assertEquals(40, fe.measuredDrainFePerTick());
        assertEquals(2.0f, fe.appliedLoadMultiplier(), 0.001f);

        assertTrue(fe.serviceTick(false, 8.0f));
        assertEquals(5, fe.measuredDrainFePerTick());
        assertEquals(1.0f, fe.appliedLoadMultiplier(), 0.001f);

        assertTrue(fe.serviceTick(true, 0.25f));
        assertEquals(20, fe.measuredDrainFePerTick());
    }

    @Test
    void measuredMetersTrackDrainAndInput() {
        TestSettings settings = new TestSettings();
        settings.active = 20;
        FeConsumer fe = new FeConsumer(settings);
        fe.receiveEnergy(300, false);

        assertTrue(fe.serviceTick(true));
        assertEquals(300, fe.measuredInputFePerTick());
        assertEquals(20, fe.measuredDrainFePerTick());

        assertTrue(fe.serviceTick(true));
        assertEquals(0, fe.measuredInputFePerTick());
    }

    @Test
    void loadCurveIsSubLinearAndFlattens() {
        assertEquals(1.0f, FeLoadCurve.multiplier(0, 0.85), 0.001f);
        assertEquals(1.0f, FeLoadCurve.multiplier(1, 0.85), 0.001f);
        assertEquals(1.80f, FeLoadCurve.multiplier(2, 0.85), 0.01f);
        assertEquals(7.08f, FeLoadCurve.multiplier(10, 0.85), 0.02f);
        assertEquals(12.76f, FeLoadCurve.multiplier(20, 0.85), 0.05f);

        float previousStep = Float.MAX_VALUE;
        for (int n = 2; n <= 20; n++) {
            float step = FeLoadCurve.multiplier(n, 0.85) - FeLoadCurve.multiplier(n - 1, 0.85);
            assertTrue(step < previousStep, "marginal cost should shrink at n=" + n);
            previousStep = step;
        }

        assertEquals(10.0f, FeLoadCurve.multiplier(10, 1.0), 0.001f);
    }

    @Test
    void rebootThresholdHoldsBlockDownWhileItCharges() {
        TestSettings settings = new TestSettings();
        settings.capacity = 1000;
        settings.active = 100;
        settings.rebootPct = 25;
        FeConsumer fe = new FeConsumer(settings);
        fe.receiveEnergy(1000, false);

        for (int i = 0; i < 10; i++) {
            assertTrue(fe.serviceTick(true), "tick " + i + " is still funded");
        }
        assertEquals(0, fe.getEnergyStored());
        assertTrue(fe.isPowered());
        assertFalse(fe.serviceTick(true));
        assertFalse(fe.isPowered());

        fe.receiveEnergy(150, false);
        assertFalse(fe.serviceTick(true), "must stay down below the reboot threshold");
        assertTrue(fe.isRecharging());

        assertEquals(150, fe.getEnergyStored(), "charging reserve must not be drained");
        assertEquals(0, fe.measuredDrainFePerTick());

        fe.receiveEnergy(100, false);
        assertTrue(fe.serviceTick(true));
        assertTrue(fe.isPowered());
        assertFalse(fe.isRecharging());
        assertEquals(150, fe.getEnergyStored());
    }

    @Test
    void rebootThresholdOfZeroKeepsEdgeTriggeredBehaviour() {
        TestSettings settings = new TestSettings();
        settings.capacity = 100;
        settings.active = 60;
        settings.rebootPct = 0;
        FeConsumer fe = new FeConsumer(settings);
        fe.receiveEnergy(60, false);
        assertTrue(fe.serviceTick(true));
        assertFalse(fe.serviceTick(true));

        fe.receiveEnergy(60, false);
        assertTrue(fe.serviceTick(true));
        assertFalse(fe.isRecharging());
    }

    @Test
    void transitionBeatsSurviveOnePairThenGoQuietWhileFlapping() {
        TestSettings settings = new TestSettings();
        settings.capacity = 100;
        settings.active = 60;
        settings.rebootPct = 0;
        FeConsumer fe = new FeConsumer(settings);
        fe.receiveEnergy(100, false);
        assertTrue(fe.serviceTick(true));
        fe.pollTransition();

        assertFalse(fe.serviceTick(true));
        assertEquals(-1, fe.pollTransition(), "the cutoff beat plays");
        fe.receiveEnergy(60, false);
        assertTrue(fe.serviceTick(true));
        assertEquals(1, fe.pollTransition(), "the immediate recovery beat plays too");

        assertFalse(fe.serviceTick(true));
        assertFalse(fe.isPowered(), "state must still change, only the effect is suppressed");
        assertEquals(0, fe.pollTransition());
        fe.receiveEnergy(60, false);
        assertTrue(fe.serviceTick(true));
        assertEquals(0, fe.pollTransition());
    }

    @Test
    void burstLargerThanBufferIsFlaggedRatherThanFailingSilently() {
        TestSettings settings = new TestSettings();
        settings.capacity = 1000;
        settings.burst = 5000;
        FeConsumer fe = new FeConsumer(settings);
        fe.receiveEnergy(1000, false);
        assertFalse(fe.spendBurst());
        assertTrue(fe.isBurstStarved(), "a fully charged block that can never afford its burst must say so");

        settings.burst = 100;
        assertTrue(fe.spendBurst());
        assertFalse(fe.isBurstStarved());
    }

    @Test
    void clientMirrorReportsServerTuningNotLocalConfig() {
        TestSettings localSettings = new TestSettings();
        localSettings.enabled = false;
        localSettings.capacity = 10;
        localSettings.active = 1;
        FeConsumer mirror = new FeConsumer(localSettings);
        assertFalse(mirror.requiresPower());

        mirror.applyClientSnapshot(new FePowerSnapshot(true, false, true,
                24_000, 96_000, 150, 600, 8_000, 25, 600, 4_096, 1.8f, FePriority.HIGH, true, FeLock.NONE));

        assertTrue(mirror.requiresPower(), "server says this block pays for its power");
        assertEquals(FePriority.HIGH, mirror.priority(), "priority mirrors from the server");
        assertTrue(mirror.isLoadShed(), "shed flag mirrors from the server");
        assertEquals(96_000, mirror.getMaxEnergyStored());
        assertEquals(24_000, mirror.getEnergyStored());
        assertEquals(25.0f, mirror.fillPct(), 0.001f);
        assertFalse(mirror.isPowered());
        assertTrue(mirror.isBurstStarved());
        assertEquals(600, mirror.settings().activeDrain());
        assertEquals(25, mirror.rebootThresholdPct());
        assertEquals(4_096, mirror.measuredInputFePerTick());
        assertEquals(1.8f, mirror.appliedLoadMultiplier(), 0.001f);
    }

    @Test
    void snapshotRoundTripsThroughItsOwnClamping() {
        TestSettings settings = new TestSettings();
        settings.capacity = 500;
        FeConsumer fe = new FeConsumer(settings);
        fe.receiveEnergy(400, false);
        fe.serviceTick(true);

        FePowerSnapshot snapshot = fe.snapshot();
        FeConsumer mirror = new FeConsumer(new TestSettings());
        mirror.applyClientSnapshot(snapshot);
        assertEquals(fe.getEnergyStored(), mirror.getEnergyStored());
        assertEquals(fe.isPowered(), mirror.isPowered());
        assertEquals(fe.fillPct(), mirror.fillPct(), 0.001f);
        assertEquals(snapshot.syncSignature(), mirror.snapshot().syncSignature());

        FePowerSnapshot hostile = new FePowerSnapshot(true, true, false,
                Integer.MAX_VALUE, 1_000, -5, -5, -5, 999, -1, -1, Float.NaN, FePriority.NORMAL, false, FeLock.NONE);
        assertEquals(1_000, hostile.energy());
        assertEquals(0, hostile.idleDrain());
        assertEquals(90, hostile.rebootThresholdPct());
        assertEquals(0, hostile.measuredInput());
        assertEquals(1.0f, hostile.loadMultiplier(), 0.001f);
        assertEquals(100.0f, hostile.fillPct(), 0.001f);
    }

    @Test
    void syncSignatureIgnoresNoiseButCatchesRealChange() {
        FePowerSnapshot base = new FePowerSnapshot(true, true, false,
                50_000, 100_000, 100, 400, 0, 25, 400, 0, 1.0f, FePriority.NORMAL, false, FeLock.NONE);
        FePowerSnapshot oneFeLess = new FePowerSnapshot(true, true, false,
                49_999, 100_000, 100, 400, 0, 25, 400, 0, 1.0f, FePriority.NORMAL, false, FeLock.NONE);
        assertEquals(base.syncSignature(), oneFeLess.syncSignature(), "a single FE is not news");

        FePowerSnapshot unpowered = new FePowerSnapshot(true, false, false,
                50_000, 100_000, 100, 400, 0, 25, 400, 0, 1.0f, FePriority.NORMAL, false, FeLock.NONE);
        assertNotEquals(base.syncSignature(), unpowered.syncSignature(), "a power flip is always news");

        FePowerSnapshot drained = new FePowerSnapshot(true, true, false,
                40_000, 100_000, 100, 400, 0, 25, 400, 0, 1.0f, FePriority.NORMAL, false, FeLock.NONE);
        assertNotEquals(base.syncSignature(), drained.syncSignature(), "10 percent of buffer is news");
    }

    @Test
    void mergingPoolsChargeIntoTheSharedBank() {
        TestSettings settings = new TestSettings();
        settings.capacity = 1000;
        FeConsumer member = new FeConsumer(settings);
        FeConsumer controller = new FeConsumer(settings);
        member.receiveEnergy(400, false);
        controller.receiveEnergy(100, false);

        member.transferChargeTo(controller);
        assertEquals(0, member.getEnergyStored());
        assertEquals(500, controller.getEnergyStored(), "charge is conserved across the merge");

        FeConsumer full = new FeConsumer(settings);
        full.receiveEnergy(1000, false);
        FeConsumer donor = new FeConsumer(settings);
        donor.receiveEnergy(600, false);
        donor.transferChargeTo(full);
        assertEquals(600, donor.getEnergyStored(), "nothing is lost when the bank cannot take it");
        assertEquals(1000, full.getEnergyStored());
    }

    @Test
    void pooledCapacityScalesWithAssemblySizeButDrainDoesNot() {
        final int[] panels = {1};
        TestSettings base = new TestSettings();
        base.capacity = 64_000;
        base.idle = 100;
        base.active = 400;
        FeConsumer.Settings pooled = pooledFor(base, () -> panels[0]);

        assertEquals(64_000, pooled.capacity());
        assertEquals(400, pooled.activeDrain());

        panels[0] = 9;
        assertEquals(576_000, pooled.capacity(), "nine panels pool nine buffers");
        assertEquals(400, pooled.activeDrain(), "but cost the same to run");
        assertEquals(100, pooled.idleDrain());

        assertTrue(576_000 / 400.0f > 64_000 / 400.0f);
    }

    private static FeConsumer.Settings pooledFor(FeConsumer.Settings base, java.util.function.IntSupplier units) {
        return new FeConsumer.Settings() {
            @Override
            public boolean enabled() {
                return base.enabled();
            }

            @Override
            public int capacity() {
                return base.capacity() * Math.max(1, units.getAsInt());
            }

            @Override
            public int idleDrain() {
                return base.idleDrain();
            }

            @Override
            public int activeDrain() {
                return base.activeDrain();
            }

            @Override
            public int burstCost() {
                return base.burstCost();
            }
        };
    }

    @Test
    void lowPowerBrownoutThreshold() {
        TestSettings settings = new TestSettings();
        settings.capacity = 100;
        FeConsumer fe = new FeConsumer(settings);
        fe.receiveEnergy(19, false);
        assertTrue(fe.isBrownout());
        fe.receiveEnergy(81, false);
        assertFalse(fe.isBrownout());
        settings.enabled = false;
        assertFalse(fe.isBrownout());
    }

    @Test
    void suppressionFollowsTheShedFlagAndTheLock() {
        FeConsumer fe = new FeConsumer(new TestSettings());
        assertFalse(fe.isSuppressed());

        fe.setLoadShed(true);
        assertTrue(fe.isSuppressed());

        fe.setLock(FeLock.FORCE_ON);
        assertFalse(fe.isSuppressed(), "force on pins a device the controller wanted to shed");

        fe.setLock(FeLock.FORCE_OFF);
        assertTrue(fe.isSuppressed());

        fe.setLock(FeLock.NONE);
        fe.setLoadShed(false);
        assertFalse(fe.isSuppressed());
    }

    @Test
    void shedDoesNotStopAnOrdinaryConsumerDead() {
        TestSettings settings = new TestSettings();
        FeConsumer fe = new FeConsumer(settings);
        fe.receiveEnergy(settings.capacity, false);
        fe.setLoadShed(true);
        assertTrue(fe.serviceTick(true));
        assertEquals(settings.active, fe.measuredDrainFePerTick());
    }
}
