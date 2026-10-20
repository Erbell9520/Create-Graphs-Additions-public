package com.createstats.holomk3.render;

import org.joml.Vector3f;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface HoloSink {
    void tri(float x1, float y1, float z1, int c1,
             float x2, float y2, float z2, int c2,
             float x3, float y3, float z3, int c3);

    void solid(float x1, float y1, float z1, int c1,
               float x2, float y2, float z2, int c2,
               float x3, float y3, float z3, int c3);

    default void quad(float x1, float y1, float z1, int c1,
                      float x2, float y2, float z2, int c2,
                      float x3, float y3, float z3, int c3,
                      float x4, float y4, float z4, int c4) {
        tri(x1, y1, z1, c1, x2, y2, z2, c2, x3, y3, z3, c3);
        tri(x1, y1, z1, c1, x3, y3, z3, c3, x4, y4, z4, c4);
    }

    default void solidQuad(float x1, float y1, float z1, int c1,
                           float x2, float y2, float z2, int c2,
                           float x3, float y3, float z3, int c3,
                           float x4, float y4, float z4, int c4) {
        solid(x1, y1, z1, c1, x2, y2, z2, c2, x3, y3, z3, c3);
        solid(x1, y1, z1, c1, x3, y3, z3, c3, x4, y4, z4, c4);
    }

    void text(String s, float x, float y, float z, int color, float scale);

    float textWidth(String s, float scale);

    default void textCenter(String s, float x, float y, float z, int color, float scale) {
        final float half = textWidth(s, scale) * 0.5f;
        final Vector3f r = right();
        text(s, x - r.x * half, y - r.y * half, z - r.z * half, color, scale);
    }

    default void textRight(String s, float x, float y, float z, int color, float scale) {
        final float wide = textWidth(s, scale);
        final Vector3f r = right();
        text(s, x - r.x * wide, y - r.y * wide, z - r.z * wide, color, scale);
    }

    Vector3f eye();

    Vector3f right();

    Vector3f up();

    float feather();

    float pixel();
}
