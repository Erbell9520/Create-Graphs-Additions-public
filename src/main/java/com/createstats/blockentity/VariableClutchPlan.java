package com.createstats.blockentity;

public final class VariableClutchPlan {
    private VariableClutchPlan() {
    }

    public static float rpmCapModifier(float maxRpm, float sourceSpeed) {
        if (!(sourceSpeed > 0.0f) || !Float.isFinite(sourceSpeed)) {
            return 1.0f;
        }
        float cap = (float) Math.floor(Math.max(0.0f, maxRpm));
        float modifier = cap / sourceSpeed;
        while (modifier > 0.0f && modifier * sourceSpeed > cap) {
            modifier = Math.nextDown(modifier);
        }
        return modifier;
    }
}
