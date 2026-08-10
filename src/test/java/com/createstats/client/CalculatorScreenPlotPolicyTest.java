package com.createstats.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculatorScreenPlotPolicyTest {
    @Test
    void plotPolicyAutoEnablesScientificExpressions() {
        assertTrue(CalculatorScreen.requiresScientificPlotMode("sin(x)"));
        assertTrue(CalculatorScreen.requiresScientificPlotMode("x^2"));
        assertTrue(CalculatorScreen.requiresScientificPlotMode("sqrt(abs(x))"));
        assertTrue(CalculatorScreen.requiresScientificPlotMode("pi*x"));
        assertFalse(CalculatorScreen.requiresScientificPlotMode("1+2*3"));
    }
}