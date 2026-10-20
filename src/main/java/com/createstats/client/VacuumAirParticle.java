package com.createstats.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;

public class VacuumAirParticle extends TextureSheetParticle {
    private final Vec3 target;
    private final double spinSign;
    private final SpriteSet sprites;
    private final float fullAlpha;

    protected VacuumAirParticle(ClientLevel level, double x, double y, double z, double tx, double ty, double tz,
                                SpriteSet sprites) {
        super(level, x, y, z);
        this.target = new Vec3(tx, ty, tz);
        this.sprites = sprites;
        this.spinSign = this.random.nextBoolean() ? 1.0 : -1.0;
        this.lifetime = 18 + this.random.nextInt(10);
        this.quadSize = 0.035f + this.random.nextFloat() * 0.035f;
        this.gravity = 0.0f;
        this.hasPhysics = false;
        this.fullAlpha = 0.35f + this.random.nextFloat() * 0.25f;
        this.alpha = 0.0f;
        final float tint = 0.9f + this.random.nextFloat() * 0.1f;
        this.rCol = tint;
        this.gCol = tint;
        this.bCol = tint;
        this.xd = 0;
        this.yd = 0;
        this.zd = 0;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        final Vec3 at = new Vec3(this.x, this.y, this.z);
        final Vec3 to = this.target.subtract(at);
        final double d = to.length();
        if (d < 0.12) {
            this.remove();
            return;
        }
        final Vec3 dir = to.scale(1.0 / d);

        final double speed = Math.min(0.5, 0.05 + 0.16 / Math.max(0.35, d));
        Vec3 swirl = new Vec3(0, 1, 0).cross(dir);
        if (swirl.lengthSqr() < 1.0e-6) {
            swirl = new Vec3(1, 0, 0).cross(dir);
        }
        swirl = swirl.normalize().scale(this.spinSign * speed * 0.8 * Math.max(0.0, 1.0 - d / 5.0));
        final Vec3 v = dir.scale(speed).add(swirl);
        this.xd = this.xd + (v.x - this.xd) * 0.4;
        this.yd = this.yd + (v.y - this.yd) * 0.4;
        this.zd = this.zd + (v.z - this.zd) * 0.4;
        this.move(this.xd, this.yd, this.zd);

        final float fadeIn = Math.min(1.0f, this.age / 4.0f);
        final float swallow = (float) Math.min(1.0, d / 0.6);
        this.alpha = this.fullAlpha * fadeIn * swallow;
        this.setSpriteFromAge(this.sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double tx, double ty, double tz) {
            return new VacuumAirParticle(level, x, y, z, tx, ty, tz, this.sprites);
        }
    }
}
