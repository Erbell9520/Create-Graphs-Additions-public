package com.createstats.pducabinet;

public final class CabinetSurface {
    public final int width, height;
    public final int[] pixels;

    public static final int INACTIVE=0xff080f0c;

    public CabinetSurface(int width, int height, int colour) {
        this.width=width; this.height=height; pixels=new int[width*height];
        java.util.Arrays.fill(pixels,colour);
    }

    public void rect(int x0,int y0,int x1,int y1,int colour) {
        x0=Math.max(0,x0);x1=Math.min(width-1,x1);
        if(x0>x1)return;
        for (int y=Math.max(0,y0);y<=Math.min(height-1,y1);y++)
            java.util.Arrays.fill(pixels,y*width+Math.max(0,x0),y*width+Math.min(width,x1+1),colour);
    }

    public void text(String s,int x,int y,int colour,int scale) {
        for (char c : s.toCharArray()) {
            int idx=c>=32 && c<127 ? c-32 : '?'-32;
            for (int row=0;row<8;row++) for (int col=0;col<8;col++)
                if ((CabinetGlyphs.ROWS[idx*8+row] & (1<<col))!=0)
                    rect(x+col*scale,y+row*scale,x+(col+1)*scale-1,y+(row+1)*scale-1,colour);
            x+=CabinetGlyphs.ADVANCE[idx]*scale;
        }
    }

    public void text(String s,int x,int y,int colour) { text(s,x,y,colour,1); }
    public void right(String s,int x,int y,int colour) { text(s,x-CabinetGlyphs.width(s),y,colour); }
    public void centred(String s,int x,int y,int colour) { text(s,x-CabinetGlyphs.width(s)/2,y,colour); }

    public void line(int x0,int y0,int x1,int y1,int colour) {
        int n=Math.max(Math.abs(x1-x0),Math.abs(y1-y0));
        for (int i=0;i<=n;i++) {
            double f=n==0?0:(double)i/n;
            rect((int)Math.round(x0+(x1-x0)*f),(int)Math.round(y0+(y1-y0)*f),
                    (int)Math.round(x0+(x1-x0)*f),(int)Math.round(y0+(y1-y0)*f),colour);
        }
    }

    public void display(CabinetSurface source,int x,int y,int background) {
        int w=source.width,h=source.height,n=w*h;
        int br=(background>>16)&255,bg=(background>>8)&255,bb=background&255;
        float[][] light=new float[3][n],across=new float[3][n],halo=new float[3][n];
        for(int i=0;i<n;i++) {
            int c=source.pixels[i];
            light[0][i]=Math.max(0,((c>>16)&255)-br);light[1][i]=Math.max(0,((c>>8)&255)-bg);light[2][i]=Math.max(0,(c&255)-bb);
        }
        float[] k={.06f,.24f,.40f,.24f,.06f};
        for(int ch=0;ch<3;ch++) {
            for(int j=0;j<h;j++)for(int i=0;i<w;i++) {
                float s=0;
                for(int t=-2;t<=2;t++)if(i+t>=0 && i+t<w)s+=k[t+2]*light[ch][j*w+i+t];
                across[ch][j*w+i]=s;
            }
            for(int j=0;j<h;j++)for(int i=0;i<w;i++) {
                float s=0;
                for(int t=-2;t<=2;t++)if(j+t>=0 && j+t<h)s+=k[t+2]*across[ch][(j+t)*w+i];
                halo[ch][j*w+i]=Math.max(0,s-.16f*light[ch][j*w+i]);
            }
        }
        CabinetSurface lit=new CabinetSurface(w,h,0);
        for(int j=0;j<h;j++)for(int i=0;i<w;i++) {
            int edge=Math.min(Math.min(i,w-1-i),Math.min(j,h-1-j)), corner=Math.min(i,w-1-i)+Math.min(j,h-1-j);
            if(edge<2 || corner<4) {
                lit.pixels[j*w+i]=INACTIVE;continue;
            }
            int c=source.pixels[j*w+i];
            double dx=(i+.5)/w-.5, dy=(j+.5)/h-.5, back=7*Math.max(0,1-4*(dx*dx+dy*dy));
            int r=(int)Math.min(255,((c>>16)&255)+.55*halo[0][j*w+i]+back*.7);
            int g=(int)Math.min(255,((c>>8)&255)+.55*halo[1][j*w+i]+back);
            int b=(int)Math.min(255,(c&255)+.55*halo[2][j*w+i]+back*.85);
            lit.pixels[j*w+i]=0xff000000|(r<<16)|(g<<8)|b;
        }
        paste(lit,x,y,w,h,true);
    }

    public void paste(CabinetSurface source,int x,int y,int w,int h,boolean lcd) {
        double[] shineX=new double[w],shineY=new double[h];
        if(lcd) {
            for(int i=0;i<w;i++)shineX[i]=2.2*Math.exp(-i/25.0);
            for(int j=0;j<h;j++)shineY[j]=1.3*Math.exp(-j/32.0);
        }
        for (int j=0;j<h;j++) for (int i=0;i<w;i++) {
            int c=source.pixels[(j*source.height/h)*source.width+i*source.width/w];
            if (lcd) {
                double edge=Math.min(Math.min(i,w-1-i),Math.min(j,h-1-j));
                double k=(.94+.06*Math.min(edge/24,1))*(j%2==1?.96:1);
                double shine=shineX[i]+shineY[j];
                int r=Math.min(255,(int)(((c>>16)&255)*k+shine*.68));
                int g=Math.min(255,(int)(((c>>8)&255)*k+shine*.86));
                int b=Math.min(255,(int)((c&255)*k+shine));
                c=0xff000000|(r<<16)|(g<<8)|b;
            }
            pixels[(y+j)*width+x+i]=c;
        }
    }
}
