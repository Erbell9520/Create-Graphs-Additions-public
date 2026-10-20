package com.createstats.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpressionEvaluatorTest {
    @Test
    void graphPresetExpressionsEvaluateForX() throws ExpressionEvaluator.EvaluationException {
        assertEquals(0.0D, ExpressionEvaluator.evaluateForX("sin(x)", true, 0.0D), 1.0E-9D);
        assertEquals(9.0D, ExpressionEvaluator.evaluateForX("x^2", true, -3.0D), 1.0E-9D);
        assertEquals(4.0D, ExpressionEvaluator.evaluateForX("abs(x)", true, -4.0D), 1.0E-9D);
        assertEquals(2.0D, ExpressionEvaluator.evaluateForX("sqrt(abs(x))", true, -4.0D), 1.0E-9D);
    }

    @Test
    void graphPresetExpressionsNeedScientificMode() {
        assertThrows(ExpressionEvaluator.EvaluationException.class, () -> ExpressionEvaluator.evaluateForX("x^2", false, 2.0D));
        assertThrows(ExpressionEvaluator.EvaluationException.class, () -> ExpressionEvaluator.evaluate("sin(0)", false));
    }

    @Test
    void samplePlotUsesRequestedResolution() throws ExpressionEvaluator.EvaluationException {
        final float[] samples = ExpressionEvaluator.samplePlot("x^2", true, -2.0D, 2.0D, 160);
        assertEquals(160, samples.length);
        assertTrue(samples[0] > samples[samples.length / 2]);
    }
}
