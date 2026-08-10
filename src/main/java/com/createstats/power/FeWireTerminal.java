package com.createstats.power;

import net.minecraft.core.BlockPos;

import java.util.List;

/** [1] */
public interface FeWireTerminal {

    /** Where this terminal is in the world. */
    BlockPos terminalPos();

    /** Every far end this terminal has a wire to. Walked by the renderer and the flood fill. */
    List<BlockPos> links();

    /** The grade of the wire to {@code link}, or {@link WireTier#COPPER} if untiered. */
    WireTier tierOf(BlockPos link);

    /** Records one wire from here to {@code far}. The caller links the far end too. */
    void addLink(BlockPos far, WireTier tier);

    /** Drops one link proven to point at something that is no longer a terminal. */
    void removeDeadLink(BlockPos far);

    /** Strips every wire off this terminal, returning the wires, and unlinks the far ends. */
    void dropWiresAndUnlinkAll();
}
