package com.createstats.pducabinet;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CabinetReadoutTest {
    static CabinetReadout.Port port(int delivered,int requested,boolean closed,boolean trip,boolean limited,boolean shed) {
        return new CabinetReadout.Port(delivered,0,16384,requested,true,closed,trip,limited,shed);
    }

    @Test void outputIncludesBusAndNeverSubstitutesRequestedPower() {
        List<CabinetReadout.Port> ports=new ArrayList<>(CabinetReadout.empty().ports());
        ports.set(0,port(25,12000,true,false,false,false));
        ports.set(8,port(75,20000,true,false,false,false));
        CabinetReadout f=new CabinetReadout("AUTO",100,500,65536,ports,List.of(),"",0);
        assertEquals(100,f.total());
        assertEquals(1,f.wired());
        assertEquals(1,f.flowing());
        assertEquals(100.0/131072,f.wireFraction());
        ports.set(0,port(0,0,true,false,false,false));
        assertEquals(100,f.total(),"snapshot must not retain mutable controller arrays");
    }

    @Test void faultAndBreakerStatesTakePrecedenceOverCachedDemand() {
        assertEquals("TRIP",port(0,65536,false,true,true,true).state());
        assertEquals("OPEN",port(0,65536,false,false,true,true).state());
        assertEquals("WIRE",port(0,65536,true,false,true,true).state());
        assertEquals("PARTIAL",port(50,100,true,false,false,true).state());
        assertEquals("SHED",port(0,100,true,false,false,true).state());
        assertEquals("IDLE",port(0,100,true,false,false,false).state());
        assertEquals("FEED",new CabinetReadout.Port(0,512,16384,0,true,true,false,false,false).state());
        assertEquals("NO WIRE",new CabinetReadout.Port(0,0,0,0,false,true,false,false,false).state());
    }

    @Test void configuredLargeRatesDoNotOverflowTheTotalOrDial() {
        var full=port(Integer.MAX_VALUE,Integer.MAX_VALUE,true,false,false,false);
        CabinetReadout f=new CabinetReadout("OFF",0,0,Integer.MAX_VALUE,
                java.util.Collections.nCopies(9,full),List.of(),"",0);
        assertEquals(9L*Integer.MAX_VALUE,f.total());
        assertEquals(1,f.wireFraction());
        assertEquals(1,full.fraction());
    }

    @Test void busTripRaisesTheSharedFlagButNotAnEightStudCounter() {
        var ports=new ArrayList<>(CabinetReadout.empty().ports());
        ports.set(8,port(0,100,true,true,false,false));
        CabinetReadout f=new CabinetReadout("MANUAL",0,0,65536,ports,List.of(),"BUS / TRIP",120);
        assertEquals(1,f.flags());assertTrue(f.trip());assertEquals(0,f.trips());
    }
}
