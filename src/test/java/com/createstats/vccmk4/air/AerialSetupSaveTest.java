package com.createstats.vccmk4.air;

import com.createstats.vccmk4.air.core.AerialSetup;
import com.createstats.vccmk4.air.core.AirLight;
import com.createstats.vccmk4.air.core.Feel;
import com.createstats.vccmk4.air.core.LeaveMode;
import com.createstats.vccmk4.air.core.PropLearner;
import com.createstats.vccmk4.core.Channel;
import com.createstats.vccmk4.core.Control;
import com.createstats.vccmk4.core.KeyIds;
import com.createstats.vccmk4.core.MachineAxis;
import com.createstats.vccmk4.core.PartInput;
import com.createstats.vcu.core.Axis6;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AerialSetupSaveTest {
    @Test
    void everyChoiceSurvivesASave() {
        AerialSetup s = new AerialSetup();
        s.feel = Feel.SPORTY;
        s.leave = LeaveMode.GO_TO;
        s.noseTurn = 3;
        s.setTrim(-0.35);
        s.altitudeCap = 180.0;
        s.bell = false;
        s.keymap.set(Control.TRIM, 0, KeyIds.key(265));
        s.keymap.setInverted(Control.STEER, true);
        s.engines.put(11L, new AerialSetup.Engine(AerialSetup.EngineJob.LIFT, false, 50));
        s.engines.put(-12L, new AerialSetup.Engine(AerialSetup.EngineJob.OFF, true, 25));
        s.balloonOff.put(21L, true);
        s.surfaces.put(31L, new AerialSetup.Surface(AerialSetup.SurfaceRole.TOOL, 45, true, PartInput.AUX_C, true));
        s.surfaces.put(32L, new AerialSetup.Surface(AerialSetup.SurfaceRole.ELEVATOR, 15, false, PartInput.OFF, false));
        s.tools.put(41L, new AerialSetup.Tool(PartInput.SLIDE, new MachineAxis.Setting(128, true, true)));
        s.lights.put(51L, new AerialSetup.Light(AirLight.STROBE, false));
        s.lights.put(52L, new AerialSetup.Light(AirLight.AUX_D_MINUS, true));

        CompoundTag tag = AerialNbt.setup(s);
        AerialSetup back = new AerialSetup();
        back.engines.put(99L, AerialSetup.Engine.DEFAULT);
        AerialNbt.readSetup(tag, back);

        assertSame(Feel.SPORTY, back.feel);
        assertSame(LeaveMode.GO_TO, back.leave);
        assertEquals(3, back.noseTurn);
        assertEquals(-0.35, back.trim, 1.0e-9);
        assertEquals(180.0, back.altitudeCap, 1.0e-9);
        assertFalse(back.bell);
        assertArrayEquals(s.keymap.toInts(), back.keymap.toInts());
        assertEquals(s.engines, back.engines, "and a stale entry from before the load is gone");
        assertEquals(Map.of(21L, true), back.balloonOff);
        assertEquals(s.surfaces, back.surfaces);
        assertEquals(s.tools, back.tools);
        assertEquals(s.lights, back.lights);
    }

    @Test
    void theNoseChoiceSurvivesASaveAndA1_14_74SaveFollowsTheEngines() {
        AerialSetup s = new AerialSetup();
        s.noseTurn = 2;
        s.noseSet = true;
        AerialSetup back = new AerialSetup();
        AerialNbt.readSetup(AerialNbt.setup(s), back);
        assertTrue(back.noseSet);
        assertEquals(2, back.noseTurn);

        s.noseSet = false;
        AerialNbt.readSetup(AerialNbt.setup(s), back);
        assertFalse(back.noseSet, "Auto stays Auto");

        CompoundTag old = AerialNbt.setup(new AerialSetup());
        old.remove("NoseSet");
        AerialNbt.readSetup(old, back);
        assertFalse(back.noseSet, "the owner's 1.14.74 save: Nose 0, never touched, follows the engines");
        old.putInt("Nose", 1);
        AerialNbt.readSetup(old, back);
        assertTrue(back.noseSet, "a 1.14.74 nose the player turned is kept");
        assertEquals(1, back.noseTurn);
    }

    @Test
    void anOldSavesVerticalLearningIsDropped() {
        CompoundTag pilot = new CompoundTag();
        net.minecraft.nbt.ListTag v = new net.minecraft.nbt.ListTag();
        for (double d : new double[] {-664.0, 10.66, 0.0, 4035.6}) v.add(net.minecraft.nbt.DoubleTag.valueOf(d));
        pilot.put("Vertical", v);
        assertEquals(null, AerialNbt.readPilot(pilot, false).vertical(), "a version 2 save starts the observer again");
        assertArrayEquals(new double[] {-664.0, 10.66, 0.0, 4035.6}, AerialNbt.readPilot(pilot, true).vertical(), 1.0e-12);
        assertEquals(3, AerialBE.NBT_VERSION);
    }

    @Test
    void aSaveWithNoCapAndNoBellRowReadsAsTheDefaults() {
        AerialSetup back = new AerialSetup();
        AerialNbt.readSetup(new CompoundTag(), back);
        assertTrue(Double.isNaN(back.altitudeCap), "no cap is no cap, not y 0");
        assertTrue(back.bell);
        assertSame(Feel.STANDARD, back.feel);
        assertSame(LeaveMode.ANCHOR, back.leave);
        assertArrayEquals(AerialSetup.defaultKeys().toInts(), back.keymap.toInts());
    }

    @Test
    void thePropellersLearningSurvivesASave() {
        PropLearner measured = PropLearner.restored(-1, 2.5, 0.25, 2.0);
        PropLearner signOnly = new PropLearner();
        for (double thrust : new double[] {-0.2, -0.35, -0.45}) signOnly.observe(3, thrust, Double.NaN, 3.0);
        assertTrue(signOnly.signSeen, "spinning up backwards under a steady three RPM");
        Map<Long, PropLearner> back = AerialNbt.readLearned(AerialNbt.learned(Map.of(7L, measured, -8L, signOnly, 9L, new PropLearner())));
        assertEquals(2, back.size(), "a propeller never seen to turn is not written");
        assertEquals(-1, back.get(7L).sign);
        assertEquals(2.5, back.get(7L).gain, 1.0e-12);
        assertEquals(0.25, back.get(7L).flow, 1.0e-12);
        assertEquals(2.0, back.get(7L).ratio, 1.0e-12);
        assertTrue(back.get(-8L).signSeen);
        assertEquals(-1, back.get(-8L).sign);
        assertFalse(back.get(-8L).known(), "the thrust is measured again in flight");

        CompoundTag old = new CompoundTag();
        CompoundTag p = new CompoundTag();
        p.putInt("Sign", 1);
        p.putDouble("Gain", 1.0);
        p.putDouble("Ratio", 1.0);
        old.put("42", p);
        old.put("not a position", new CompoundTag());
        Map<Long, PropLearner> legacy = AerialNbt.readLearned(old);
        assertEquals(1, legacy.size());
        assertTrue(legacy.get(42L).known());
        assertTrue(legacy.get(42L).signSeen);
    }

    @Test
    void theFinsAnglesSurviveASave() {
        Map<Long, Double> angles = Map.of(5L, 37.5, -6L, -12.0);
        assertEquals(angles, AerialNbt.readAngles(AerialNbt.angles(angles)));
    }

    @Test
    void theAirKeysAreTheGroundKeysWithTheAltitudeOnSpaceAndX() {
        var k = AerialSetup.defaultKeys();
        assertEquals(Channel.JUMP.id(), k.plus(Control.LIFT));
        assertEquals(Channel.AUX2.id(), k.minus(Control.LIFT));
        assertEquals(KeyIds.NONE, k.plus(Control.BRAKE), "Space is the altitude's, not a brake");
        assertEquals(KeyIds.NONE, k.minus(Control.AUX_A), "X is the altitude's second key");
        assertEquals(Channel.AUX1.id(), k.plus(Control.AUX_A), "Z still runs the first tool");
        assertEquals(Channel.FORWARD.id(), k.plus(Control.THROTTLE));
        assertEquals(Channel.BACK.id(), k.minus(Control.THROTTLE));
        assertTrue(AerialSetup.CONTROLS.contains(Control.TRIM));
        assertFalse(AerialSetup.CONTROLS.contains(Control.BRAKE), "the Keys page lists no key the Aerial ignores");
    }

    @Test
    void theConsolesStepsStopAtTheEndsAndClamp() {
        assertEquals(50, AerialSetup.stepLimit(25, 1));
        assertEquals(100, AerialSetup.stepLimit(100, 1), "a plus at the top stays at the top");
        assertEquals(25, AerialSetup.stepLimit(25, -1), "a minus at the bottom stays at the bottom");
        assertEquals(75, AerialSetup.stepLimit(100, -1));
        assertEquals(50, AerialSetup.stepLimit(60, -1), "off the steps, the next one down");
        assertEquals(15, AerialSetup.stepThrow(AerialSetup.SurfaceRole.RUDDER, 10, 1));
        assertEquals(45, AerialSetup.stepThrow(AerialSetup.SurfaceRole.ELEVATOR, 45, 1));
        assertEquals(10, AerialSetup.stepThrow(AerialSetup.SurfaceRole.RUDDER, 10, -1));
        assertEquals(50, AerialSetup.stepThrow(AerialSetup.SurfaceRole.TOOL, 45, 1), "a tool swings on past a fin's 45");
        assertEquals(180, AerialSetup.stepThrow(AerialSetup.SurfaceRole.TOOL, 180, 1));
        assertEquals(5, AerialSetup.stepThrow(AerialSetup.SurfaceRole.TOOL, 5, -1));
        assertEquals(25, new AerialSetup.Engine(AerialSetup.EngineJob.AUTO, true, 3).limit(), "never below the first step");
        assertEquals(45, new AerialSetup.Surface(AerialSetup.SurfaceRole.RUDDER, 90, false, null, false).degrees());
        assertEquals(90, new AerialSetup.Surface(AerialSetup.SurfaceRole.TOOL, 90, false, null, false).degrees(),
                "a cargo hatch opens a quarter turn");
        assertEquals(45, new AerialSetup.Surface(AerialSetup.SurfaceRole.TOOL, 90, false, null, false)
                .withRole(AerialSetup.SurfaceRole.RUDDER).degrees(), "made a rudder, it keeps a rudder's throw");
        AerialSetup s = new AerialSetup();
        s.setTrim(0.333);
        assertEquals(0.35, s.trim, 1.0e-9, "twentieths");
        s.setTrim(7.0);
        assertEquals(1.0, s.trim, 1.0e-9);
        s.setCap(500.7, -64, 272);
        assertEquals(272.0, s.altitudeCap, 1.0e-9);
        s.setCap(Double.NaN, -64, 272);
        assertTrue(Double.isNaN(s.altitudeCap));
    }

    @Test
    void anEnginesJobMasksWhatTheSplitMayUseItFor() {
        double[] column = new double[Axis6.COUNT];
        Arrays.fill(column, 1.0);
        assertSame(column, AerialProps.mask(column, AerialSetup.EngineJob.AUTO));
        double[] push = AerialProps.mask(column, AerialSetup.EngineJob.PUSH);
        double[] lift = AerialProps.mask(column, AerialSetup.EngineJob.LIFT);
        double[] off = AerialProps.mask(column, AerialSetup.EngineJob.OFF);
        for (int i = 0; i < Axis6.COUNT; i++) {
            assertEquals(i == Axis6.FORWARD.index() ? 1.0 : 0.0, push[i], "push, axis " + i);
            assertEquals(i == Axis6.UP.index() ? 1.0 : 0.0, lift[i], "lift, axis " + i);
            assertEquals(0.0, off[i], "off, axis " + i);
        }
        assertEquals(1.0, column[Axis6.YAW.index()], 1.0e-12, "the governor's own column is left as it was");
    }
}
