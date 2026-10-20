package com.createstats.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class VacuumGritParticle extends TerrainParticle {
    private final Vec3 target;
    private final double spinSign;

    public VacuumGritParticle(ClientLevel level, double x, double y, double z, BlockState state, BlockPos pos,
                              Vec3 target) {
        super(level, x, y, z, 0.0, 0.0, 0.0, state, pos);
        this.target = target;
        this.spinSign = this.random.nextBoolean() ? 1.0 : -1.0;
        this.xd = 0.0;
        this.yd = 0.0;
        this.zd = 0.0;
        this.gravity = 0.0f;
        this.hasPhysics = false;
        this.lifetime = 16 + this.random.nextInt(8);
        this.quadSize *= 0.75f + this.random.nextFloat() * 0.4f;
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
        final Vec3 to = this.target.subtract(this.x, this.y, this.z);
        final double d = to.length();
        if (d < 0.15) {
            this.remove();
            return;
        }
        final Vec3 dir = to.scale(1.0 / d);

        final double speed = Math.min(0.55, 0.06 + 0.2 / Math.max(0.35, d));
        Vec3 swirl = new Vec3(0, 1, 0).cross(dir);
        if (swirl.lengthSqr() < 1.0e-6) {
            swirl = new Vec3(1, 0, 0).cross(dir);
        }
        swirl = swirl.normalize().scale(this.spinSign * speed * 0.6 * Math.max(0.0, 1.0 - d / 4.0));
        final Vec3 v = dir.scale(speed).add(swirl);
        this.xd += (v.x - this.xd) * 0.45;
        this.yd += (v.y - this.yd) * 0.45;
        this.zd += (v.z - this.zd) * 0.45;
        this.move(this.xd, this.yd, this.zd);
    }
}
