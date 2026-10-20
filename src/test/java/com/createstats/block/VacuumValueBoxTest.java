package com.createstats.block;

import com.createstats.blockentity.VacuumCollectorBE;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VacuumValueBoxTest {
    private static float storedScale(ValueBoxTransform t) throws ReflectiveOperationException {
        final Field f = ValueBoxTransform.class.getDeclaredField("scale");
        f.setAccessible(true);
        return f.getFloat(t);
    }

    private static VacuumValueBox filter() {
        return new VacuumValueBox(VacuumCollectorBE.MOUTH_X, VacuumCollectorBE.MOUTH_Y, VacuumCollectorBE.SCREEN_Z,
                0.4f, VacuumValueBox.Mount.FRONT, VacuumCollectorBE.DUCT_FRONT);
    }

    private static VacuumValueBox reach() {
        return new VacuumValueBox(5.0, 12.15, 3.0, 0.35f, VacuumValueBox.Mount.TOP, VacuumCollectorBE.DUCT_TOP);
    }

    @Test
    void theScaleCreateStoresIsTheScaleAskedFor() throws ReflectiveOperationException {
        for (VacuumValueBox.Mount mount : VacuumValueBox.Mount.values()) {
            final VacuumValueBox box = new VacuumValueBox(5.0, 9.75, 0.6, 0.4f, mount, 0.0);
            assertEquals(0.4f, storedScale(box), 1.0e-6f, "stored scale, " + mount);
            assertEquals(box.getScale(), storedScale(box), 1.0e-6f, "stored and asked agree, " + mount);
        }
    }

    @Test
    void theFilterTakesAClickAnywhereOnTheBell() {
        final VacuumValueBox f = filter();

        assertTrue(f.hits(5.0, 9.75, 0.0));
        assertTrue(f.hits(2.6, 9.75, 0.0), "left rim");
        assertTrue(f.hits(7.4, 9.75, 0.0), "right rim");
        assertTrue(f.hits(5.0, 7.3, 0.0), "bottom rim");
        assertTrue(f.hits(5.0, 12.2, 0.0), "top rim");
        assertFalse(f.hits(5.0, 12.0, 1.5), "the duct top is not the filter's");
    }

    @Test
    void theReachTakesAClickOnItsHandleOnly() {
        final VacuumValueBox r = reach();
        assertTrue(r.hits(5.0, 12.0, 3.0), "the handle's pivot on the duct top");
        assertTrue(r.hits(6.5, 12.0, 3.0), "along the handle");
        assertFalse(r.hits(5.0, 11.8, 0.0), "the bell's face below it is not the reach's");
    }

    @Test
    void noClickOnTheDuctBelongsToBothBoxes() {
        final VacuumValueBox f = filter();
        final VacuumValueBox r = reach();
        int front = 0;
        int top = 0;

        for (double x = 2.0; x <= 8.0; x += 0.125) {
            for (double y = 6.75; y <= 12.0; y += 0.125) {
                assertFalse(f.hits(x, y, 0.0) && r.hits(x, y, 0.0), "front face at " + x + ", " + y);
                front += f.hits(x, y, 0.0) ? 1 : 0;
            }
            for (double z = 0.0; z <= 5.5; z += 0.125) {
                assertFalse(f.hits(x, 12.0, z) && r.hits(x, 12.0, z), "top face at " + x + ", " + z);
                top += r.hits(x, 12.0, z) ? 1 : 0;
            }
        }
        assertTrue(front > 100, "the filter is reachable on the front: " + front);
        assertTrue(top > 100, "the reach is reachable on the top: " + top);
    }
}
