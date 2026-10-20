package com.createstats.statsmk3.ui;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface PanelSurface {
    void tri(float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3);

    void text(String s, float x, float y, int color, float scale);

    void textCenter(String s, float centerX, float y, int color, float scale);

    void textRight(String s, float rightX, float y, int color, float scale);

    float textWidth(String s, float scale);

    default void textV(String s, float x, float centerY, int color, float scale) {
        text(s, x, centerY - Text.lineHeight(scale) * 0.5f, color, scale);
    }

    default void textRightV(String s, float rightX, float centerY, int color, float scale) {
        textRight(s, rightX, centerY - Text.lineHeight(scale) * 0.5f, color, scale);
    }

    default void textCenterV(String s, float centerX, float centerY, int color, float scale) {
        textCenter(s, centerX, centerY - Text.lineHeight(scale) * 0.5f, color, scale);
    }

    void clip(Rect r);

    void unclip();

    default float feather() {
        return 0.75f;
    }

    default boolean culled(Rect r) {
        return false;
    }

    default void advanceLayer() {
    }

    default void setAlpha(float mul) {
    }

    default float alpha() {
        return 1.0f;
    }

    default void pushPose() {
    }

    default void popPose() {
    }

    default void translate(float dx, float dy) {
    }

    default void rect(Rect r, int color) {
        Shapes.rect(this, r, color);
    }

    default void rect(float x, float y, float w, float h, int color) {
        Shapes.rect(this, x, y, w, h, color);
    }

    default void gradientV(Rect r, int top, int bottom) {
        Shapes.gradientV(this, r, top, bottom);
    }

    default void gradientH(Rect r, int left, int right) {
        Shapes.gradientH(this, r, left, right);
    }

    default void hairlineH(float x, float y, float w, int color) {
        Shapes.rect(this, x, Math.round(y), w, 1.0f, color);
    }

    default void hairlineV(float x, float y, float h, int color) {
        Shapes.rect(this, Math.round(x), y, 1.0f, h, color);
    }

    default void roundedRect(Rect r, float radius, int color) {
        Shapes.roundedRect(this, r, radius, color);
    }

    default void roundedRectOutline(Rect r, float radius, float thickness, int color) {
        Shapes.roundedRectOutline(this, r, radius, thickness, color);
    }

    default void card(Rect r, float radius, int fill, int border) {
        Shapes.card(this, r, radius, fill, border);
    }

    default void shadow(Rect r, float radius, float spread, int color) {
        Shapes.shadow(this, r, radius, spread, color);
    }

    default void circle(float cx, float cy, float radius, int color) {
        Shapes.circle(this, cx, cy, radius, color);
    }

    default void arc(float cx, float cy, float radius, float thickness, float from, float sweep,
                     int color) {
        Shapes.arc(this, cx, cy, radius, thickness, from, sweep, color);
    }

    default void line(float x1, float y1, float x2, float y2, float thickness, int color) {
        Shapes.line(this, x1, y1, x2, y2, thickness, color);
    }

    default void polyline(float[] pts, int pointCount, float thickness, int color) {
        Shapes.polyline(this, pts, pointCount, thickness, color);
    }

    default void areaFill(float[] pts, int pointCount, float baseline, int topColor,
                          int bottomColor) {
        Shapes.areaFill(this, pts, pointCount, baseline, topColor, bottomColor);
    }

    default void text(String s, float x, float y, int color) {
        text(s, x, y, color, Text.BODY);
    }

    default void itemIcon(net.minecraft.world.item.ItemStack stack, float x, float y, float size) {
        final Rect box = new Rect(x, y, size, size);
        Shapes.roundedRect(this, box.inset(size * 0.08f), size * 0.16f, Tokens.LINE_HI);
        Shapes.line(this, x + size * 0.28f, y + size * 0.68f, x + size * 0.72f, y + size * 0.32f,
                Math.max(1.0f, size * 0.08f), Tokens.INK_3);
    }

    default boolean drawsItems() {
        return false;
    }
}
