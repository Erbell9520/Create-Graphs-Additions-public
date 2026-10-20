package com.createstats.pducabinet;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CabinetGeometryTest {
    @Test void numberedStudsPickTheSamePortInEveryFacingAndHalf() {
        for(int turn=0;turn<4;turn++)for(int port=0;port<8;port++) {
            double[] world=CabinetGeometry.stud(port,turn);
            assertEquals(port,CabinetGeometry.pick(turn,world[0],world[1],world[2]));
            assertEquals(port<4,world[1]>1,"first four studs must be on the upper block");
            double[] model=CabinetGeometry.toModel(turn,world[0],world[1],world[2]);
            assertArrayEquals(new double[]{5.8/16,(28.25-3.2*port)/16,15.55/16},model,1e-12);
        }
        assertEquals(-1,CabinetGeometry.pick(0,.9,1.75,.97));
        assertEquals(-1,CabinetGeometry.pick(0,5.8/16,0,.97));
        assertEquals(-1,CabinetGeometry.pick(0,5.8/16,(28.25-1.6)/16,.97));
        assertThrows(IllegalArgumentException.class,()->CabinetGeometry.stud(8,0));
    }

    @Test void facingTransformsPreserveHeightAndRoundTripOutsideTheBlock() {
        for(int turn=-8;turn<=8;turn++) {
            double[] w=CabinetGeometry.toWorld(turn,-.2,1.8,1.4);
            assertArrayEquals(new double[]{-.2,1.8,1.4},CabinetGeometry.toModel(turn,w[0],w[1],w[2]),1e-12);
        }

        assertArrayEquals(new double[]{0,1,.5},CabinetGeometry.toWorld(1,.5,1,1),1e-12);
    }

    @Test void openingAndClosingCanReverseAtAnyIntermediatePose() {
        assertEquals(0,CabinetGeometry.doorAngle(0,true,-1,24));
        assertEquals(105,CabinetGeometry.doorAngle(0,true,24,24));
        for(int tick=0;tick<=24;tick++) {
            double current=CabinetGeometry.doorAngle(0,true,tick,24);
            assertEquals(current,CabinetGeometry.doorAngle(current,false,0,24));
            assertEquals(0,CabinetGeometry.doorAngle(current,false,24,24));
            double[] handle={12.75/16,11.5/16,1.8/16};
            assertArrayEquals(handle,CabinetGeometry.doorPoint(CabinetGeometry.doorPoint(handle,current),-current),1e-12);
        }
    }
}
