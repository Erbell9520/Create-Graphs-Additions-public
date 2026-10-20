package com.createstats.blockentity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywheelVoiceTest {
    @Test
    void anEmptyBankIsStill() {
        assertEquals(0f, FlywheelVoice.rotorRpm(0f), 0f);
        assertEquals(0f, FlywheelVoice.rotorRpm(-0.2f), 0f);
        assertEquals(0f, FlywheelVoice.rotorRpm(Float.NaN), 0f);
    }

    @Test
    void theRotorFollowsTheEnergyItHolds() {
        assertEquals(FlywheelVoice.STORED_TOP_RPM, FlywheelVoice.rotorRpm(1f), 1e-4f);
        assertEquals(FlywheelVoice.STORED_TOP_RPM / 2f, FlywheelVoice.rotorRpm(0.25f), 1e-3f);
        float previous = 0f;
        for (int pct = 1; pct <= 100; pct++) {
            float rpm = FlywheelVoice.rotorRpm(pct / 100f);
            assertTrue(rpm > previous, "the rotor must speed up with every percent of charge");
            previous = rpm;
        }
        assertEquals(FlywheelVoice.STORED_TOP_RPM, FlywheelVoice.rotorRpm(3f), 1e-4f);
    }

    @Test
    void aToppedOffBankSitsSilent() {
        float trickle = FlywheelVoice.activity(true, false, 164f / 18_204f, 0f);
        assertEquals(0f, FlywheelVoice.humVolume(trickle, 1f, 1), 0f);
    }

    @Test
    void aBankHoldingAnEmptyLineSitsSilent() {
        float activity = FlywheelVoice.activity(false, true, 0f, 0f);
        assertEquals(0f, FlywheelVoice.humVolume(activity, 1f, 1), 0f);
    }

    @Test
    void anIdleBankMakesNoSound() {
        assertEquals(0f, FlywheelVoice.activity(false, false, 1f, 1f), 0f);
    }

    @Test
    void theHumGrowsWithTheWorkAndStaysQuiet() {
        float previous = 0f;
        for (int pct = 3; pct <= 100; pct++) {
            float volume = FlywheelVoice.humVolume(pct / 100f, 1f, 1);
            assertTrue(volume >= previous, "louder work must never sound quieter");
            assertTrue(volume <= FlywheelVoice.HUM_FULL + 1e-6f, "one bank at full power stays at "
                + FlywheelVoice.HUM_FULL + ", was " + volume);
            previous = volume;
        }
        assertEquals(FlywheelVoice.HUM_QUIET,
            FlywheelVoice.humVolume(FlywheelVoice.ACTIVITY_FLOOR, 1f, 1), 0.02f);
    }

    @Test
    void aBankOfWheelsIsFullerNotLouderByTheirNumber() {
        float one = FlywheelVoice.humVolume(1f, 1f, 1);
        float four = FlywheelVoice.humVolume(1f, 1f, 4);
        float twenty = FlywheelVoice.humVolume(1f, 1f, 20);
        assertTrue(four > one);
        assertEquals(four, twenty, 1e-6f, "past four wheels a bank sounds no fuller");
        assertTrue(twenty < one * 1.4f, "twenty wheels must not be much louder than one, was "
            + twenty / one + "x");
    }

    @Test
    void thePlayersSettingScalesTheHumAndZeroSilencesIt() {
        assertEquals(FlywheelVoice.humVolume(0.5f, 1f, 1) * 0.5f, FlywheelVoice.humVolume(0.5f, 0.5f, 1), 1e-6f);
        assertEquals(0f, FlywheelVoice.humVolume(1f, 0f, 1), 0f);
    }

    @Test
    void thePitchRisesWithTheRotorWithinAGentleRange() {
        assertEquals(FlywheelVoice.PITCH_LOW, FlywheelVoice.humPitch(0f), 1e-6f);
        assertEquals(FlywheelVoice.PITCH_HIGH, FlywheelVoice.humPitch(1f), 1e-6f);
        assertEquals(FlywheelVoice.PITCH_HIGH, FlywheelVoice.humPitch(4f), 1e-6f);
        assertTrue(FlywheelVoice.humPitch(0.5f) > FlywheelVoice.humPitch(0.4f));

        assertTrue(FlywheelVoice.PITCH_HIGH / FlywheelVoice.PITCH_LOW < 1.7f);
    }

    @Test
    void loadingAChunkOfChargedBanksIsSilent() {
        FlywheelVoice.Cues cues = new FlywheelVoice.Cues();
        assertEquals(FlywheelVoice.Cues.NONE, cues.update(100f, true, false, 0L));
        assertEquals(FlywheelVoice.Cues.NONE, cues.update(100f, true, false, 1L));
    }

    @Test
    void theChimePlaysOnceWhenABankTopsOff() {
        FlywheelVoice.Cues cues = new FlywheelVoice.Cues();
        cues.update(40f, true, false, 0L);
        assertEquals(FlywheelVoice.Cues.NONE, cues.update(99f, true, false, 1L));
        assertEquals(FlywheelVoice.Cues.CHIME, cues.update(99.6f, true, false, 2L));
        for (long t = 3; t < 200; t++) {
            assertEquals(FlywheelVoice.Cues.NONE, cues.update(99.8f, true, false, t));
        }
    }

    @Test
    void aBankDippingUnderFullDoesNotChimeAgain() {
        FlywheelVoice.Cues cues = new FlywheelVoice.Cues();
        cues.update(50f, true, false, 0L);
        assertEquals(FlywheelVoice.Cues.CHIME, cues.update(100f, true, false, 1L));
        assertEquals(FlywheelVoice.Cues.NONE, cues.update(97f, false, false, 2L));
        assertEquals(FlywheelVoice.Cues.NONE, cues.update(100f, true, false, 3L));
        assertEquals(FlywheelVoice.Cues.NONE, cues.update(90f, false, false, 4L));
        assertEquals(FlywheelVoice.Cues.CHIME, cues.update(100f, true, false, 5L));
    }

    @Test
    void aBankThatIsNotChargingDoesNotChime() {
        FlywheelVoice.Cues cues = new FlywheelVoice.Cues();
        cues.update(50f, false, false, 0L);
        assertEquals(FlywheelVoice.Cues.NONE, cues.update(100f, false, false, 1L));
    }

    @Test
    void aTripThunksOnceAndRetriesStayQuiet() {
        FlywheelVoice.Cues cues = new FlywheelVoice.Cues();
        cues.update(60f, false, false, 0L);
        assertEquals(FlywheelVoice.Cues.THUNK, cues.update(60f, false, true, 10L));

        cues.update(60f, false, false, 50L);
        assertEquals(FlywheelVoice.Cues.NONE, cues.update(60f, false, true, 70L));

        cues.update(60f, false, false, 400L);
        assertEquals(FlywheelVoice.Cues.THUNK, cues.update(60f, false, true, 410L));
    }

    @Test
    void aBankLoadedWhileHaltedDoesNotThunk() {
        FlywheelVoice.Cues cues = new FlywheelVoice.Cues();
        assertEquals(FlywheelVoice.Cues.NONE, cues.update(60f, false, true, 0L));
        assertEquals(FlywheelVoice.Cues.NONE, cues.update(60f, false, true, 1L));
    }
}
