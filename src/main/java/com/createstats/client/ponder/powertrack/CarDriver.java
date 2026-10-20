package com.createstats.client.ponder.powertrack;

import com.createstats.client.ponder.KitInstruction;
import com.createstats.powertrack.client.ClientRail;
import com.createstats.powertrack.core.RailPower;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Random;

final class CarDriver extends KitInstruction {
    private static final double PADDLE_HALF_LENGTH = 4.5 / 16.0;

    private final ElementLink<WorldSectionElement> link;
    private final List<RailStory.Frame> frames;
    private final SceneRailHook hook;
    private final RailPicture[] picture;
    private final BlockPos[] bogies;
    private final int rating;
    private boolean placed;

    CarDriver(ElementLink<WorldSectionElement> link, List<RailStory.Frame> frames, SceneRailHook hook,
              RailPicture[] picture, BlockPos[] bogies, int start, int ticks) {
        super(start, ticks);
        this.link = link;
        this.frames = frames;
        this.hook = hook;
        this.picture = picture;
        this.bogies = bogies;
        this.rating = RailPower.carRate(PowerTrackPonderBeats.base(), RailPower.MAX_COLLECTORS_PER_CAR);
    }

    @Override
    protected void begin(PonderScene scene) {
        placed = false;
        ClientRail.scene(scene.getWorld(), hook);
    }

    @Override
    protected void step(PonderScene scene, int tick, boolean last) {
        if (tick < 0 || tick >= frames.size()) {
            return;
        }
        final RailStory.Frame f = frames.get(tick);
        hook.update(f);
        final WorldSectionElement e = scene.resolve(link);
        if (e != null) {
            e.setAnimatedOffset(new Vec3(f.carX(), 0, 0), !placed);
            placed = true;
        }
        final double dx = f.carX() - f.carXOld();
        for (BlockPos b : bogies) {
            if (scene.getWorld().getBlockEntity(b) instanceof AbstractBogeyBlockEntity bogey) {
                bogey.animate((float) -dx);
            }
        }
        final Random random = new Random(tick * 7919L + 17);
        final double load = Math.min(1.0, Math.abs(f.carFlow()) / (double) Math.max(1, rating));
        for (int s = 0; s < f.shoes().length; s++) {
            final RailStory.Shoe shoe = f.shoes()[s];
            if (shoe.arc() && !Double.isNaN(shoe.railTop())) {
                strike(scene, shoe, random, tick);
            }
            if (!shoe.touching() || f.carFlow() == 0 || Double.isNaN(shoe.railTop())) {
                continue;
            }
            final double chance = 0.12 + 0.6 * load;
            final int count = random.nextDouble() < chance ? 1 + (int) (load * 2.5) : 0;

            final int trail = PowerTrackStage.trailSign(PowerTrackStage.CAR_COLLECTORS[s]);
            final double ex = Math.abs(dx) > 1.0e-3 ? -Math.signum(dx) : trail;
            for (int i = 0; i < count; i++) {
                final double side = (random.nextDouble() - 0.5) * 0.2;
                scene.getWorld().addParticle(ParticleTypes.ELECTRIC_SPARK, shoe.x() + ex * PADDLE_HALF_LENGTH,
                        shoe.railTop() + 0.03, shoe.z() + side,
                        -dx * 0.4 + ex * 0.04 + (random.nextDouble() - 0.5) * 0.08, 0.05 + random.nextDouble() * 0.07,
                        (random.nextDouble() - 0.5) * 0.08);
            }
        }
    }

    private void strike(PonderScene scene, RailStory.Shoe shoe, Random random, int tick) {
        final double soleY = shoe.railTop() + 0.02;
        if (picture[0] != null) {
            picture[0].strike(new RailPicture.Arc(new Vec3(shoe.x(), soleY + 0.28, shoe.z()),
                    new Vec3(shoe.x(), shoe.railTop(), shoe.z()), random.nextInt(), tick));
        }
        for (int i = 0; i < 8; i++) {
            scene.getWorld().addParticle(ParticleTypes.ELECTRIC_SPARK, shoe.x(), shoe.railTop() + 0.05, shoe.z(),
                    (random.nextDouble() - 0.5) * 0.25, 0.05 + random.nextDouble() * 0.15, (random.nextDouble() - 0.5) * 0.25);
        }
    }
}
