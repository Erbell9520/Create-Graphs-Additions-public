package com.createstats.pducabinet;

import java.util.function.IntFunction;

public final class CabinetShapeCache<T> {
    public static final int STEP=3, POSES=36;
    private final Object[] values=new Object[8*POSES];

    public synchronized T get(int turn,boolean upper,double angle,IntFunction<T> factory) {
        int pose=(int)Math.round(Math.max(0,Math.min(CabinetGeometry.OPEN_ANGLE,angle))/STEP);
        int key=(Math.floorMod(turn,4)*2+(upper?1:0))*POSES+pose;
        @SuppressWarnings("unchecked") T cached=(T)values[key];
        if(cached==null)values[key]=cached=factory.apply(pose*STEP);
        return cached;
    }
}
