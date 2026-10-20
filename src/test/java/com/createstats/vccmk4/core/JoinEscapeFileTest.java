package com.createstats.vccmk4.core;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinEscapeFileTest {
    @Test
    void theDefaultFileSendsSpawn() {
        assertEquals(List.of("spawn"), JoinEscapeFile.commands(Arrays.asList(JoinEscapeFile.DEFAULT.split("\n"))));
    }

    @Test
    void slashesCommentsBlanksAndAByteOrderMarkAreHandled() {
        List<String> lines = List.of("﻿# note", "", "  /home base  ", "//spawn", "   ", "# off");
        assertEquals(List.of("home base", "spawn"), JoinEscapeFile.commands(lines));
    }

    @Test
    void aFileFullOfTextCannotFloodTheServer() {
        List<String> lines = java.util.Collections.nCopies(100, "spawn");
        assertEquals(JoinEscapeFile.MAX, JoinEscapeFile.commands(lines).size());
        assertTrue(JoinEscapeFile.commands(List.of("x".repeat(1000))).isEmpty());
    }

    @Test
    void noLinesNoCommands() {
        assertTrue(JoinEscapeFile.commands(null).isEmpty());
        assertTrue(JoinEscapeFile.commands(List.of()).isEmpty());
    }
}
