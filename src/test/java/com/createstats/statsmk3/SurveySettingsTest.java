package com.createstats.statsmk3;

import net.minecraft.nbt.CompoundTag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurveySettingsTest {
    private static Mk3Settings roundTrip(Mk3Settings in) {
        final CompoundTag tag = new CompoundTag();
        in.save(tag);
        final Mk3Settings out = new Mk3Settings();
        out.load(tag);
        return out;
    }

    @Test
    void whatTheStudioSetsComesBackWhole() {
        final Mk3Settings s = new Mk3Settings();
        s.faceMode = Mk3Settings.FaceMode.SURVEY_DEPTH;
        s.survey.yaw = 215.5f;
        s.survey.pitch = 12.0f;
        s.survey.zoom = 3.25f;
        s.survey.cx = 160.5f;
        s.survey.cy = -40.0f;
        s.survey.cz = -358.5f;
        s.survey.sliceLo = -64.0f;
        s.survey.sliceHi = -40.0f;
        s.survey.focus = "c:ores/diamond";
        s.survey.hidden = "c:ores/coal,create_stats:voids";
        s.survey.pin = 0x1234_5678_9ABCL;
        final Mk3Settings r = roundTrip(s);
        assertEquals(Mk3Settings.FaceMode.SURVEY_DEPTH, r.faceMode);
        assertTrue(r.faceMode.isSurvey());
        assertEquals(215.5f, r.survey.yaw);
        assertEquals(12.0f, r.survey.pitch);
        assertEquals(3.25f, r.survey.zoom);
        assertEquals(160.5f, r.survey.cx);
        assertEquals(-40.0f, r.survey.cy);
        assertEquals(-358.5f, r.survey.cz);
        assertEquals(-64.0f, r.survey.sliceLo);
        assertEquals(-40.0f, r.survey.sliceHi);
        assertEquals("c:ores/diamond", r.survey.focus);
        assertEquals("c:ores/coal,create_stats:voids", r.survey.hidden);
        assertEquals(0x1234_5678_9ABCL, r.survey.pin);
    }

    @Test
    void anUnsetViewStaysUnset() {
        final Mk3Settings r = roundTrip(new Mk3Settings());
        assertFalse(r.survey.hasCentre());
        assertFalse(r.survey.hasSlice());
        assertEquals(Long.MIN_VALUE, r.survey.pin);
        assertEquals(SurveySettings.DEFAULT_YAW, r.survey.yaw);
        assertEquals(SurveySettings.DEFAULT_PITCH, r.survey.pitch);
        assertEquals(1.0f, r.survey.zoom);
    }

    @Test
    void aBrokenPacketIsClampedNotTrusted() {
        final CompoundTag survey = new CompoundTag();
        survey.putFloat("Yaw", -30.0f);
        survey.putFloat("Pitch", 400.0f);
        survey.putFloat("Zoom", Float.POSITIVE_INFINITY);
        survey.putFloat("Cx", Float.NaN);
        survey.putFloat("Cy", 3.0f);
        survey.putFloat("Cz", 4.0f);
        survey.putFloat("SliceLo", 10.0f);
        survey.putFloat("SliceHi", -10.0f);
        survey.putString("Focus", "x".repeat(500));
        survey.putString("Hidden", "y".repeat(5_000));
        final CompoundTag parent = new CompoundTag();
        final Mk3Settings fresh = new Mk3Settings();
        fresh.save(parent);
        final CompoundTag inner = parent.getCompound(firstKey(parent));
        inner.put("Survey", survey);
        final Mk3Settings r = new Mk3Settings();
        r.load(parent);
        assertEquals(330.0f, r.survey.yaw, 1.0e-4f, "a bearing wraps");
        assertEquals(90.0f, r.survey.pitch, "a plan at most");
        assertEquals(1.0f, r.survey.zoom, "an infinite zoom is no zoom");
        assertFalse(r.survey.hasCentre(), "a centre with a NaN in it is no centre");
        assertFalse(r.survey.hasSlice(), "a band upside down is no band");
        assertEquals("", r.survey.focus);
        assertEquals("", r.survey.hidden);
        final CompoundTag far = new CompoundTag();
        far.putFloat("Zoom", 1.0e9f);
        far.putFloat("Cx", 1.0e12f);
        far.putFloat("Cy", 0.0f);
        far.putFloat("Cz", 0.0f);
        inner.put("Survey", far);
        r.load(parent);
        assertEquals(SurveySettings.MAX_ZOOM, r.survey.zoom);
        assertFalse(r.survey.hasCentre(), "past the world border is a corrupt packet, not a place");
    }

    private static String firstKey(CompoundTag t) {
        return t.getAllKeys().iterator().next();
    }

    @Test
    void resetViewForgetsTheCameraNotTheFilters() {
        final SurveySettings s = new SurveySettings();
        s.yaw = 10.0f;
        s.pitch = 80.0f;
        s.zoom = 4.0f;
        s.cx = 1.0f;
        s.cy = 2.0f;
        s.cz = 3.0f;
        s.focus = "c:ores/gold";
        s.hidden = "create_stats:voids";
        s.sliceLo = -10.0f;
        s.sliceHi = 0.0f;
        s.resetView();
        assertEquals(SurveySettings.DEFAULT_YAW, s.yaw);
        assertEquals(SurveySettings.DEFAULT_PITCH, s.pitch);
        assertEquals(1.0f, s.zoom);
        assertFalse(s.hasCentre());
        assertEquals("c:ores/gold", s.focus);
        assertEquals("create_stats:voids", s.hidden);
        assertTrue(s.hasSlice());
    }

    @Test
    void aStudioViewIsItsOwnUntilItIsPutOnTheWall() {
        final SurveySettings wall = new SurveySettings();
        final SurveySettings studio = new SurveySettings();
        studio.copyFrom(wall);
        assertTrue(studio.frames(wall));
        studio.yaw = 140.0f;
        studio.focus = "c:ores/gold";
        assertFalse(studio.frames(wall), "exploring moved the studio's view, not the wall's");
        assertEquals(SurveySettings.DEFAULT_YAW, wall.yaw);
        wall.copyFrom(studio);
        assertTrue(studio.frames(wall), "framed: the wall shows the studio's view");
    }

    @Test
    void everySurveyFaceIsNamedAndNoOtherIs() {
        for (final Mk3Settings.FaceMode m : Mk3Settings.FaceMode.values()) {
            assertEquals(m.name().startsWith("SURVEY_"), m.isSurvey(), m.name());
            assertFalse(m.isSurvey() && (m.isAero() || m.isDrive() || m.isTrain()), m.name());
            assertEquals(m, Mk3Settings.FaceMode.parse(m.name()), "a saved face mode parses back");
        }
    }
}
