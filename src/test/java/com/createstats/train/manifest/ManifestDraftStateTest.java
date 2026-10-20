package com.createstats.train.manifest;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ManifestDraftStateTest {
    @Test
    void cleanEditorFollowsAutomaticRetirement() {
        ManifestDraftState state = new ManifestDraftState(60);
        assertTrue(state.receive(61, false, false));
        assertEquals(61, state.version());
        assertFalse(state.dirty());
    }

    @Test
    void retirementCannotEraseOrRebaseAnUnsavedDraft() {
        ManifestDraftState state = new ManifestDraftState(60);
        state.edit();
        assertFalse(state.receive(61, false, false));
        assertEquals(60, state.version(), "Apply must conflict rather than resurrect retired orders");
        assertTrue(state.dirty());
    }

    @Test
    void pollingIsNotASaveAcknowledgement() {
        ManifestDraftState state = new ManifestDraftState(60);
        state.edit();
        assertTrue(state.beginSave());
        assertFalse(state.beginSave(), "do not send duplicate saves while awaiting a reply");
        assertFalse(state.receive(61, false, false));
        assertTrue(state.pending());
        assertTrue(state.dirty());
        assertTrue(state.receive(61, true, true));
        assertFalse(state.pending());
        assertFalse(state.dirty());
        assertEquals(61, state.version());
    }

    @Test
    void rejectedSaveKeepsDraftAndOriginalVersion() {
        ManifestDraftState state = new ManifestDraftState(60);
        state.edit();
        state.beginSave();
        assertFalse(state.receive(61, true, false));
        assertFalse(state.pending());
        assertTrue(state.dirty());
        assertEquals(60, state.version());
        assertFalse(state.receive(61, false, false));
    }

    @Test
    void typingWhileSaveIsInFlightKeepsTheNewerEdits() {
        ManifestDraftState state = new ManifestDraftState(60);
        state.edit();
        state.beginSave();
        state.edit();
        assertFalse(state.receive(61, true, true));
        assertTrue(state.dirty());
        assertEquals(61, state.version());
        assertTrue(state.beginSave());
        assertTrue(state.receive(62, true, true));
        assertFalse(state.dirty());
    }

    @Test
    void rejectedUnchangedSaveStillWarnsBeforeClosing() {
        ManifestDraftState state = new ManifestDraftState(60);
        state.beginSave();
        assertFalse(state.receive(0, true, false));
        assertTrue(state.dirty());
        assertEquals(60, state.version());
    }
}
