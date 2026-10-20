package com.createstats.pducabinet;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import com.createstats.util.PreviewImages;
import static org.junit.jupiter.api.Assertions.*;

class CabinetFaceTest {
    @Test void allPortStatesStayDistinctOnScreenAndPilotLamps() throws Exception {
        List<CabinetReadout.Port> ports=new ArrayList<>();
        ports.add(CabinetReadoutTest.port(1200,1200,true,false,false,false));
        ports.add(CabinetReadoutTest.port(600,1400,true,false,false,true));
        ports.add(CabinetReadoutTest.port(0,900,true,false,false,true));
        ports.add(CabinetReadoutTest.port(0,1000,false,false,false,false));
        ports.add(CabinetReadoutTest.port(0,22000,true,true,true,false));
        ports.add(CabinetReadoutTest.port(1000,22000,true,false,true,false));
        ports.add(CabinetReadout.empty().ports().get(0));
        ports.add(new CabinetReadout.Port(0,512,16384,0,true,true,false,false,false));
        ports.add(CabinetReadoutTest.port(450,450,true,false,false,false));
        List<Long> history=java.util.stream.IntStream.range(0,32).mapToLong(i->2200+(i%9)*130).boxed().toList();
        CabinetReadout f=new CabinetReadout("AUTO",2048,8192,65536,ports,history,"05 / TRIP",1280);
        CabinetSurface screen=CabinetFace.screen(f), atlas=CabinetFace.atlas(f);
        for(int i=0;i<8;i++) {
            int col=CabinetFace.colour(ports.get(i));
            assertEquals(col,screen.pixels[(96+i*14)*128+4],"status rail "+i);
        }

        int mid=CabinetFace.LAMP_SIZE/2;
        for(int i=0;i<9;i++) {
            int lamp=CabinetFace.lampColour(ports.get(i));
            int texel=atlas.pixels[(CabinetFace.LAMP_Y+mid-1)*CabinetFace.ATLAS_WIDTH+CabinetFace.LAMP_X
                    +i*CabinetFace.LAMP_PITCH+mid-9];
            if(lamp==0)assertEquals(0,texel>>>24,"a dark lamp must leave the world-lit lens visible, leg "+i);
            else assertEquals(lamp,texel,"pilot lamp "+i);
        }

        assertEquals(5,java.util.Set.of(0,1,3,4,7).stream().map(i->CabinetFace.lampColour(ports.get(i))).distinct().count());
        assertEquals(CabinetFace.LAMP_RED,CabinetFace.lampColour(ports.get(4)));
        for(int c:CabinetFace.lamp(ports.get(6)).pixels)assertEquals(0,c>>>24,"NO WIRE lamp must be dark");
        assertEquals(0,CabinetFace.lamp(ports.get(0)).pixels[0]>>>24,"a lit lamp is round, its corners cut out");
        save("states_screen_v05.png",screen);save("states_atlas_v05.png",atlas);
        save("idle_screen_v05.png",CabinetFace.screen(CabinetReadout.empty()));
        var live=java.util.stream.IntStream.range(0,9)
                .mapToObj(i->CabinetReadoutTest.port(1400+i*320,1400+i*320,true,false,false,false)).toList();
        var healthy=new CabinetReadout("AUTO",12288,12288,65536,live,history,"01 / LIVE",1420);
        save("live_screen_v05.png",CabinetFace.screen(healthy));save("live_atlas_v05.png",CabinetFace.atlas(healthy));
    }

    @Test void largeReadingsFitTheirScreenAndCounterColumns() {
        for(long n:new long[]{0,1,4096,65536,131072,214748,1048576,Integer.MAX_VALUE,9L*Integer.MAX_VALUE}) {
            for(int width:new int[]{22,38,42,47,55,68})
                assertTrue(CabinetGlyphs.width(CabinetFace.value(n,width))<=width,"value "+n+" in "+width);
            assertTrue(CabinetGlyphs.width(CabinetFace.value(n,68-CabinetGlyphs.width(" FE/t"))+" FE/t")<=68);
        }
    }

    @Test void glassEffectIsDeterministicAndRetainsBrightGlyphs() {
        var face=CabinetReadout.empty();
        CabinetSurface a=CabinetFace.atlas(face),b=CabinetFace.atlas(face);
        assertArrayEquals(a.pixels,b.pixels);
        CabinetSurface sample=new CabinetSurface(128,256,CabinetFace.TEXT);
        CabinetSurface glass=new CabinetSurface(128,256,0);
        glass.paste(sample,0,0,128,256,true);
        int bright=glass.pixels[40*128+40],dark=glass.pixels[41*128+40];
        assertTrue((bright&255)>180,"LCD effect must not bury text");
        assertTrue((dark&255)<(bright&255));
        assertEquals(512*256,a.pixels.length,"instrument uploads must stay eight times smaller than the old atlas");
    }

    @Test void screenGlowsLikeALitPanelWithoutLosingItsGlyphs() {
        CabinetSurface layout=new CabinetSurface(128,256,CabinetFace.SCREEN);
        layout.rect(40,40,44,60,CabinetFace.TEXT);
        CabinetSurface a=new CabinetSurface(128,256,0),b=new CabinetSurface(128,256,0);
        a.display(layout,0,0,CabinetFace.SCREEN);b.display(layout,0,0,CabinetFace.SCREEN);
        assertArrayEquals(a.pixels,b.pixels,"the display pass must be deterministic");
        int bright=a.pixels[50*128+42],near=a.pixels[50*128+45],far=a.pixels[50*128+60],edge=a.pixels[50*128+1];
        assertTrue(((bright>>8)&255)>200,"a lit glyph must stay bright");
        assertTrue(((near>>8)&255)>((far>>8)&255)+12,"a lit pixel must glow onto the dark beside it");
        assertTrue(((near>>8)&255)<((bright>>8)&255)-60,"the glow must stay well under the glyph itself");
        assertTrue(((edge>>8)&255)<((far>>8)&255),"the panel's unlit border must be darker than its background");
        assertEquals(0xff,a.pixels[0]>>>24,"the display is opaque, unlike a lamp");
    }

    @Test void heartbeatBeatsOnlyWhileTheControllerRuns() {
        var ports=CabinetReadout.empty().ports();
        int lit=CabinetFace.atlas(new CabinetReadout("AUTO",0,0,65536,ports,List.of(),"",0))
                .pixels[CabinetFace.HEART_Y*CabinetFace.ATLAS_WIDTH+CabinetFace.HEART_X];
        int off=CabinetFace.atlas(CabinetReadout.empty()).pixels[CabinetFace.HEART_Y*CabinetFace.ATLAS_WIDTH+CabinetFace.HEART_X];
        assertEquals(0xff,lit>>>24,"AUTO must give the heartbeat a lit dot to flash");
        assertEquals(0,off>>>24,"OFF must leave nothing to flash over the dark dot");
        CabinetSurface screen=CabinetFace.screen(CabinetReadout.empty());
        assertNotEquals(CabinetFace.HEADER,screen.pixels[(CabinetFace.HEART_PY+1)*128+CabinetFace.HEART_PX+1],
                "the dark dot must be drawn where the lit one lands");
    }

    @Test void manualModeDoesNotPresentCachedAutoRequestsAsCurrentMeasurements() {
        var ports=new ArrayList<>(CabinetReadout.empty().ports());
        ports.set(0,CabinetReadoutTest.port(500,12000,true,false,false,false));
        var first=new CabinetReadout("MANUAL",100,200,65536,ports,List.of(),"",0);
        ports.set(0,CabinetReadoutTest.port(500,999999,true,false,false,false));
        var other=new CabinetReadout("MANUAL",100,200,65536,ports,List.of(),"",0);
        assertArrayEquals(CabinetFace.screen(first).pixels,CabinetFace.screen(other).pixels);
    }

    @Test void historyKeepsConfiguredRatesAboveTheIntegerRange() {
        var history=List.of(3_000_000_000L,4_000_000_000L,3_500_000_000L);
        var f=new CabinetReadout("AUTO",0,0,65536,CabinetReadout.empty().ports(),history,"",0);
        assertEquals(4_000_000_000L,f.history().get(1));
        CabinetSurface p=CabinetFace.screen(f);
        assertEquals(CabinetFace.CYAN,p.pixels[230*128+64],"4G peak must not wrap or saturate to 2G");
    }

    private static void save(String name,CabinetSurface p) throws Exception {
        Path dir=Path.of("pdu_preview/tall_cabinet/game_v01");Files.createDirectories(dir);
        BufferedImage image=new BufferedImage(p.width,p.height,BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0,0,p.width,p.height,p.pixels,0,p.width);
        PreviewImages.write(image,"png",dir.resolve(name).toFile());
    }
}
