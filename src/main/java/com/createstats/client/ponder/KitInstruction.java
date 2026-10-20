package com.createstats.client.ponder;

import com.createstats.CreateStats;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.TickingInstruction;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// never blocking, never throws out of a tick
public abstract class KitInstruction extends TickingInstruction {
    private static final Set<Class<?>> WARNED = ConcurrentHashMap.newKeySet();

    protected final int start;
    private boolean broken;

    protected KitInstruction(int start, int ticks) {
        super(false, Math.max(1, ticks));
        this.start = start;
    }

    @Override
    public void reset(PonderScene scene) {
        super.reset(scene);
        broken = false;
    }

    @Override
    protected final void firstTick(PonderScene scene) {
        super.firstTick(scene);
        try {
            begin(scene);
        } catch (RuntimeException e) {
            fail(e);
        }
    }

    @Override
    public final void tick(PonderScene scene) {
        super.tick(scene);
        if (broken) return;
        try {
            step(scene, start + (totalTicks - remainingTicks) - 1, remainingTicks == 0);
        } catch (RuntimeException e) {
            fail(e);
        }
    }

    protected void begin(PonderScene scene) {
    }

    protected abstract void step(PonderScene scene, int tick, boolean last);

    private void fail(RuntimeException e) {
        broken = true;
        if (WARNED.add(getClass())) {
            CreateStats.LOGGER.warn("[Ponder] {} stopped: {}", getClass().getSimpleName(), e.toString());
        }
    }
}
