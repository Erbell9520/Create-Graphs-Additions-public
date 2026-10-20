package com.createstats.statsmk3.ui;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class RectSpring {
    private final Spring x = Spring.ui(0.0f);
    private final Spring y = Spring.ui(0.0f);
    private final Spring w = Spring.ui(0.0f);
    private final Spring h = Spring.ui(0.0f);

    // retarget without losing momentum
    public void to(Rect r) {
        this.x.set(r.x);
        this.y.set(r.y);
        this.w.set(r.w);
        this.h.set(r.h);
    }

    public void jump(Rect r) {
        this.x.jump(r.x);
        this.y.jump(r.y);
        this.w.jump(r.w);
        this.h.jump(r.h);
    }

    public void step(float dt) {
        this.x.step(dt);
        this.y.step(dt);
        this.w.step(dt);
        this.h.step(dt);
    }

    public Rect get() {
        return new Rect(this.x.value(), this.y.value(), this.w.value(), this.h.value());
    }

    public boolean settled() {
        return this.x.settled(0.5f) && this.y.settled(0.5f)
                && this.w.settled(0.5f) && this.h.settled(0.5f);
    }
}
