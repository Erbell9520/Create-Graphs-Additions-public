package com.createstats.statsmk3.paint;

import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
final class ScaledSurface implements PanelSurface {
    private final PanelSurface d;
    private final float ox;
    private final float oy;
    private final float k;

    ScaledSurface(PanelSurface delegate, float originX, float originY, float k) {
        this.d = delegate;
        this.ox = originX;
        this.oy = originY;
        this.k = k > 0.0f && Float.isFinite(k) ? k : 1.0f;
    }

    static Rect virtual(Rect r, float k) {
        return new Rect(0.0f, 0.0f, r.w / k, r.h / k);
    }

    private float x(float v) {
        return this.ox + v * this.k;
    }

    private float y(float v) {
        return this.oy + v * this.k;
    }

    private Rect map(Rect r) {
        return new Rect(x(r.x), y(r.y), r.w * this.k, r.h * this.k);
    }

    @Override
    public void tri(float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) {
        this.d.tri(x(x1), y(y1), c1, x(x2), y(y2), c2, x(x3), y(y3), c3);
    }

    @Override
    public void text(String s, float x, float y, int color, float scale) {
        this.d.text(s, x(x), y(y), color, scale * this.k);
    }

    @Override
    public void textCenter(String s, float centerX, float y, int color, float scale) {
        this.d.textCenter(s, x(centerX), y(y), color, scale * this.k);
    }

    @Override
    public void textRight(String s, float rightX, float y, int color, float scale) {
        this.d.textRight(s, x(rightX), y(y), color, scale * this.k);
    }

    @Override
    public float textWidth(String s, float scale) {
        return this.d.textWidth(s, scale * this.k) / this.k;
    }

    @Override
    public void clip(Rect r) {
        this.d.clip(map(r));
    }

    @Override
    public void unclip() {
        this.d.unclip();
    }

    @Override
    public float feather() {
        return this.d.feather() / this.k;
    }

    @Override
    public boolean culled(Rect r) {
        return this.d.culled(map(r));
    }

    @Override
    public void advanceLayer() {
        this.d.advanceLayer();
    }

    @Override
    public void setAlpha(float mul) {
        this.d.setAlpha(mul);
    }

    @Override
    public float alpha() {
        return this.d.alpha();
    }

    @Override
    public void pushPose() {
        this.d.pushPose();
    }

    @Override
    public void popPose() {
        this.d.popPose();
    }

    @Override
    public void translate(float dx, float dy) {
        this.d.translate(dx * this.k, dy * this.k);
    }

    @Override
    public void itemIcon(net.minecraft.world.item.ItemStack stack, float x, float y, float size) {
        this.d.itemIcon(stack, x(x), y(y), size * this.k);
    }

    @Override
    public boolean drawsItems() {
        return this.d.drawsItems();
    }
}
