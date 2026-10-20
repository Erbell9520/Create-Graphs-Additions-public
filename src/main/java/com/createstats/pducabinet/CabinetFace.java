package com.createstats.pducabinet;

import java.util.Locale;

public final class CabinetFace {
    public static final int ATLAS_WIDTH=512, ATLAS_HEIGHT=256;
    public static final int SCREEN=0xff15271f, CARD=0xff1c3026, RULE=0xff344c39;

    public static final int HEADER=0xff20392c, SCREEN_EDGE=0xff0f1d17;

    public static final int HEART_PX=5, HEART_PY=8, HEART_X=380, HEART_Y=0, HEART_SIZE=3;
    public static final int TEXT=0xffdbeac5, MUTED=0xff9ab28f, DIM=0xff6f896e;
    public static final int CYAN=0xffb1dbbe, OK=0xff98d6a0, CAUTION=0xffefbf73, FAULT=0xffee8b76, QUIET=0xff819181;

    public static final int LAMP_X=128, LAMP_Y=0, LAMP_SIZE=24, LAMP_PITCH=28;

    public static final int LAMP_GREEN=0xff74e08c, LAMP_IDLE=0xff3f8f55, LAMP_AMBER=0xffffbd4a,
            LAMP_RED=0xffff5a46, LAMP_CYAN=0xff7fd8ef;
    private CabinetFace() {}

    public static int lampColour(CabinetReadout.Port port) {
        return switch (port.state()) {
            case "TRIP" -> LAMP_RED;
            case "WIRE", "PARTIAL", "SHED" -> LAMP_AMBER;
            case "OPEN", "NO WIRE" -> 0;
            case "FEED" -> LAMP_CYAN;
            case "IDLE" -> LAMP_IDLE;
            default -> LAMP_GREEN;
        };
    }

    // transparent when dark, a painted dark lens would glow at night in a full bright atlas
    public static CabinetSurface lamp(CabinetReadout.Port port) {
        CabinetSurface s=new CabinetSurface(LAMP_SIZE,LAMP_SIZE,0);
        int c=lampColour(port);
        if(c==0)return s;
        double r0=LAMP_SIZE/2.0;
        for(int y=0;y<LAMP_SIZE;y++)for(int x=0;x<LAMP_SIZE;x++) {
            double dx=x+.5-r0, dy=y+.5-r0, r=Math.hypot(dx,dy)/r0;
            if(r>.98)continue;

            int p=r<.3?mix(c,0xffffffff,.62):r<.6?mix(c,0xffffffff,.26):r<.86?c:scale(c,.66);
            int sector=(int)((Math.atan2(dy,dx)+Math.PI)/(Math.PI/4))%8;
            if(r>=.3 && r<.86 && sector%2==1)p=scale(p,.84);
            s.pixels[y*LAMP_SIZE+x]=p;
        }

        s.pixels[6*LAMP_SIZE+7]=0xffffffff;s.pixels[6*LAMP_SIZE+8]=mix(c,0xffffffff,.8);
        s.pixels[7*LAMP_SIZE+6]=mix(c,0xffffffff,.8);
        return s;
    }

    private static int mix(int a,int b,double t) {
        int r=(int)Math.round(((a>>16)&255)*(1-t)+((b>>16)&255)*t), g=(int)Math.round(((a>>8)&255)*(1-t)+((b>>8)&255)*t);
        int bl=(int)Math.round((a&255)*(1-t)+(b&255)*t);
        return 0xff000000|(r<<16)|(g<<8)|bl;
    }

    private static int scale(int a,double k) {
        return 0xff000000|((int)(((a>>16)&255)*k)<<16)|((int)(((a>>8)&255)*k)<<8)|(int)((a&255)*k);
    }

    public static int colour(CabinetReadout.Port port) {
        return switch (port.state()) {
            case "TRIP" -> FAULT;
            case "WIRE", "PARTIAL", "SHED" -> CAUTION;
            case "OPEN", "NO WIRE" -> QUIET;
            case "FEED" -> CYAN;
            default -> OK;
        };
    }

    public static String value(long n,int width) {
        String s=String.format(Locale.ROOT,"%,d",Math.max(0,n));
        if (CabinetGlyphs.width(s)<=width) return s;
        double d=Math.max(0,n);
        for (String unit : new String[]{"k","M","G","T"}) {
            d/=1000;
            if(d>=1000 && !unit.equals("T"))continue;
            for(int precision=2;precision>=0;precision--) {
                s=String.format(Locale.ROOT,"%."+precision+"f%s",d,unit);
                if(CabinetGlyphs.width(s)<=width)return s;
            }
        }
        return "--";
    }

    public static int modeColour(String mode) {
        return switch (mode) { case "AUTO" -> OK; case "MANUAL" -> CYAN; default -> QUIET; };
    }

    public static CabinetSurface screen(CabinetReadout f) {
        CabinetSurface p=new CabinetSurface(128,256,SCREEN);
        int alert=f.trip()?FAULT:f.flags()>0?CAUTION:OK, mode=modeColour(f.mode()), mw=CabinetGlyphs.width(f.mode());

        p.rect(0,0,127,15,HEADER);p.rect(0,15,127,15,SCREEN_EDGE);
        p.rect(HEART_PX,HEART_PY,HEART_PX+2,HEART_PY+2,scale(mode,.42));
        p.text("PDU",11,6,TEXT);p.text("DISPATCH",34,6,MUTED);
        p.rect(121-mw,4,124,14,mode);p.right(f.mode(),123,6,SCREEN);
        p.line(4,17,123,17,RULE);
        p.text("DELIVERED FE/t",5,23,MUTED);
        p.text(value(f.total(),57),5,35,TEXT,2);
        p.text("A "+value(f.inputA(),45),5,55,CYAN);
        p.right("B "+value(f.inputB(),45),123,55,CYAN);
        p.text("INPUT FE/t",5,66,DIM);p.right(f.flags()>0?f.flags()+" FLAGS":"NORMAL",123,66,alert);
        p.line(4,77,123,77,RULE);
        p.text("OUT",5,82,MUTED);p.right("FE/t",68,82,MUTED);p.text("STATE",78,82,MUTED);

        for(int i=0;i<9;i++) {
            CabinetReadout.Port r=f.ports().get(i);int y=94+i*14,col=colour(r);
            if(i==8)p.rect(3,y-2,124,y+11,CARD);
            p.rect(4,y+1,5,y+7,col);
            p.text(i<8?Integer.toString(i+1):"BUS",9,y,r.wired()?TEXT:QUIET);
            p.right(value(r.delivered(),39),68,y,r.delivered()>0?TEXT:QUIET);
            if(r.tripped()) {
                p.rect(76,y-2,79+CabinetGlyphs.width(r.state()),y+8,FAULT);p.text(r.state(),78,y,SCREEN);
            } else p.text(r.state(),78,y,col);
            p.rect(9,y+10,67,y+10,RULE);
            if(r.delivered()>0)p.rect(9,y+10,9+(int)Math.round(58*r.fraction()),y+10,col);
        }
        p.line(4,219,123,219,RULE);
        p.text(f.history().size()<32?"TRACE / WARMING":"TRACE / 8s",5,222,MUTED);
        long lo=f.history().stream().mapToLong(Long::longValue).min().orElse(0);
        long hi=f.history().stream().mapToLong(Long::longValue).max().orElse(0);
        int px=5,py=241;
        for(int i=0;i<f.history().size();i++) {
            int x=5+(int)Math.round(i*118.0/Math.max(1,f.history().size()-1));
            int y=241-(int)Math.round((f.history().get(i)-(double)lo)/Math.max(1L,hi-lo)*11);
            p.line(x,y,x,241,CARD);
            if(i>0)p.line(px,py,x,y,CYAN);
            px=x;py=y;
        }
        p.text(f.event(),5,247,alert);
        return p;
    }

    public static CabinetSurface atlas(CabinetReadout f) {
        CabinetSurface a=new CabinetSurface(ATLAS_WIDTH,ATLAS_HEIGHT,SCREEN);
        a.display(screen(f),0,0,SCREEN);
        for(int i=0;i<CabinetReadout.PORTS;i++)
            a.paste(lamp(f.ports().get(i)),LAMP_X+i*LAMP_PITCH,LAMP_Y,LAMP_SIZE,LAMP_SIZE,false);

        CabinetSurface heart=new CabinetSurface(HEART_SIZE,HEART_SIZE,0);
        if(!"OFF".equals(f.mode()))heart.rect(0,0,HEART_SIZE-1,HEART_SIZE-1,mix(modeColour(f.mode()),0xffffffff,.35));
        a.paste(heart,HEART_X,HEART_Y,HEART_SIZE,HEART_SIZE,false);
        for(int i=0;i<2;i++) {
            int v=i==0?f.inputA():f.inputB(),col=v>0?CYAN:QUIET;
            CabinetSurface input=new CabinetSurface(24,126,0xff101c16);
            input.centred("IN",12,4,MUTED);input.centred(i==0?"A":"B",12,16,col);
            int pct=(int)Math.round(100*Math.min(1,(double)v/f.inputLimit()));
            int count=(int)Math.ceil(12*Math.min(1,(double)v/f.inputLimit()));
            for(int j=0;j<12;j++) {
                input.rect(5,97-j*6,18,100-j*6,0xff283b2d);
                if(j<count)input.rect(5,97-j*6,18,100-j*6,col);
            }
            input.centred(Integer.toString(pct),12,108,col);input.centred("%",12,118,MUTED);
            a.paste(input,128+i*32,32,24,126,false);
        }
        CabinetSurface counter=new CabinetSurface(144,20,0xff101c16);
        counter.text(value(f.total(),50),5,3,TEXT,2);counter.right("FE/t",140,7,MUTED);
        a.paste(counter,128,176,144,20,false);
        CabinetSurface flag=new CabinetSurface(32,18,f.trip()?FAULT:f.flags()>0?CAUTION:OK);
        flag.rect(0,0,31,0,0xff6b7356);flag.rect(0,0,0,17,0xff6b7356);
        flag.rect(0,17,31,17,0xff252c21);flag.rect(31,0,31,17,0xff252c21);
        flag.centred(f.trip()?"TRIP":f.flags()>0?"!":"OK",16,5,0xff21251f);
        a.paste(flag,288,176,32,18,false);
        return a;
    }
}
