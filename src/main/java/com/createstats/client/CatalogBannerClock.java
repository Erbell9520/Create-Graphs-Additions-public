package com.createstats.client;

public final class CatalogBannerClock {
    static final float RATE_UP = 14.0F;
    static final float RATE_DOWN = 7.0F;

    static final float MAX_STEP_SECONDS = 0.1F;

    private final CatalogBannerSpec spec;
    private long startMillis = -1L;
    private long lastMillis = -1L;
    private float power;

    public CatalogBannerClock(CatalogBannerSpec spec) {
        this.spec = spec;
    }

    public void update(boolean hovered, long nowMillis) {
        final float dt = this.lastMillis < 0L ? 0.0F
            : Math.max(0.0F, Math.min(MAX_STEP_SECONDS, (nowMillis - this.lastMillis) / 1000.0F));
        this.lastMillis = nowMillis;
        if (hovered && this.startMillis < 0L) {
            this.startMillis = nowMillis;
        }
        final float target = hovered ? 1.0F : 0.0F;
        if (dt > 0.0F) {
            final float rate = hovered ? RATE_UP : RATE_DOWN;
            this.power += (target - this.power) * (1.0F - (float) Math.exp(-rate * dt));
        }
        if (Math.abs(target - this.power) < 0.003F) {
            this.power = target;
        }
        if (!hovered && this.power == 0.0F) {
            this.startMillis = -1L;
        }
    }

    public float power() {
        return this.power;
    }

    public int liveFrame(long nowMillis) {
        if (this.startMillis < 0L) {
            return 0;
        }
        final long frameMillis = (long) this.spec.frameTicks * CatalogBannerSpec.TICK_MILLIS;
        final long n = Math.max(0L, nowMillis - this.startMillis) / frameMillis;
        if (n < this.spec.intro) {
            return 1 + (int) n;
        }
        if (this.spec.loop <= 0) {
            return this.spec.intro;
        }
        return 1 + this.spec.intro + (int) ((n - this.spec.intro) % this.spec.loop);
    }
}
