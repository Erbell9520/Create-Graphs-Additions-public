package com.createstats.vccmk4.route;

import com.createstats.surveyor.core.PathSpline;
import com.createstats.surveyor.core.Pathway;
import com.createstats.surveyor.core.StopCondition;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockApproachTest {
    private static Pathway east() {
        return Pathway.create(new UUID(1, 1), "dock", 0, new UUID(2, 2))
                .appendAll(List.of(new double[]{0, 64, 0}, new double[]{20, 64, 0}, new double[]{40, 64, 0}));
    }

    @Test
    void theVehicleRestsWhereItsTipMeetsTheBays() {
        PathSpline sp = PathSpline.of(east());
        double nodeS = sp.nodeS(1);
        double[] fit = DockApproach.restingPoint(sp, nodeS, 1.5, 2.0, new Vec3(21.5, 64.5, 2.0));
        assertEquals(nodeS, fit[0], 0.06, "at the node");
        assertTrue(fit[1] < 0.06, "the tips meet: " + fit[1]);

        double[] on = DockApproach.restingPoint(sp, nodeS, 1.5, 2.0, new Vec3(23.0, 64.5, 2.0));
        assertEquals(nodeS + 1.5, on[0], 0.06);
    }

    @Test
    void aBayOffThePathIsMeasuredNotReached() {
        PathSpline sp = PathSpline.of(east());
        double[] fit = DockApproach.restingPoint(sp, sp.nodeS(1), 1.5, 2.0, new Vec3(21.5, 64.5, 2.8));
        assertEquals(0.8, fit[1], 0.02, "how far it stands off the path");
        assertTrue(fit[1] > DockApproach.CAPTURE, "more than capture allows: said before it arrives");
    }

    @Test
    void aDockingStopIsOneWhoseRulesWaitForDocked() {
        assertTrue(DockApproach.waitsForDock(List.of(List.of(new StopCondition.Docked(), new StopCondition.Idle(3)))));
        assertTrue(DockApproach.waitsForDock(List.of(List.of(new StopCondition.Wait(30)), List.of(new StopCondition.Docked()))));
        assertFalse(DockApproach.waitsForDock(List.of(List.of(new StopCondition.Wait(5)))));
        assertFalse(DockApproach.waitsForDock(null));
    }
}
