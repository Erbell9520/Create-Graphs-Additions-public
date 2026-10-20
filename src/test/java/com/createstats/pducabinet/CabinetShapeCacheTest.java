package com.createstats.pducabinet;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CabinetShapeCacheTest {
    @Test void thousandsOfQueriesCannotGrowPastThePhysicalPoses() {
        var cache=new CabinetShapeCache<Object>();var builds=new AtomicInteger();
        for(int pass=0;pass<3;pass++)for(int facing=0;facing<4;facing++)for(boolean upper:new boolean[]{false,true})
            for(double angle=0;angle<=105;angle+=.25)
                cache.get(facing,upper,angle,p->{builds.incrementAndGet();return new Object();});
        assertEquals(8*36,builds.get());
        Object open=cache.get(0,false,105,p->fail("rebuilding open pose"));
        assertSame(open,cache.get(4,false,1000,p->fail("unbounded angle")));
        assertNotSame(open,cache.get(0,true,105,p->fail("upper pose missing")));
    }

    @Test void selectionQuantizationStaysWithinLessThanAThirdOfAModelPixel() {
        var cache=new CabinetShapeCache<Integer>();
        for(double angle=0;angle<=105;angle+=.1) {
            int actual=cache.get(0,false,angle,p->p);
            double[] exact=CabinetGeometry.doorPoint(new double[]{13.15/16,0,3.5/16},angle);
            double[] stepped=CabinetGeometry.doorPoint(new double[]{13.15/16,0,3.5/16},actual);
            assertTrue(16*Math.hypot(exact[0]-stepped[0],exact[2]-stepped[2])<.28);
        }
    }
}
