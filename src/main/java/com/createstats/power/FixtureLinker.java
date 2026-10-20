package com.createstats.power;

import net.minecraft.core.BlockPos;

import java.util.List;

public interface FixtureLinker {
    BlockPos linkerPos();

    List<BlockPos> linkedFixtures();

    // defaults to Gateable so the switch didn't change
    default boolean accepts(net.minecraft.world.level.block.entity.BlockEntity fixture) {
        return fixture instanceof Gateable;
    }

    boolean toggleLink(BlockPos fixture);

    enum Outcome {
        LINKED,
        UNLINKED,
        REFUSED
    }

    default boolean mayLink(@org.jetbrains.annotations.Nullable net.minecraft.world.entity.player.Player actor) {
        return true;
    }

    @org.jetbrains.annotations.Nullable
    default java.util.UUID linkIdentity() {
        return null;
    }

    default Outcome link(BlockPos fixture,
                         @org.jetbrains.annotations.Nullable net.minecraft.world.entity.player.Player actor) {
        if (!mayLink(actor)) {
            return Outcome.REFUSED;
        }
        final boolean had = linkedFixtures().contains(fixture);
        if (toggleLink(fixture)) {
            return Outcome.LINKED;
        }
        return had ? Outcome.UNLINKED : Outcome.REFUSED;
    }
}
