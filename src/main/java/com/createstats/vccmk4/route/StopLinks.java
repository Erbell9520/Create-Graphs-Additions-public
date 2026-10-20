package com.createstats.vccmk4.route;

import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;

import javax.annotation.Nullable;

final class StopLinks {
    @Nullable private Transmitter live;

    void set(LevelAccessor level, @Nullable BlockPos at, String first, String second) {
        if (at == null || (first.isEmpty() && second.isEmpty())) {
            clear(level);
            return;
        }
        Couple<RedstoneLinkNetworkHandler.Frequency> key = Couple.create(RouteSensors.frequency(first), RouteSensors.frequency(second));
        if (live != null && live.at.equals(at) && live.key.equals(key)) return;
        clear(level);
        Transmitter t = new Transmitter(at, key);
        try {
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, t);
            live = t;
        } catch (RuntimeException | LinkageError e) {
            RouteReflect.warnOnce("dockedLink", e.toString());
        }
    }

    void set(LevelAccessor level, BlockPos at, Couple<RedstoneLinkNetworkHandler.Frequency> key) {
        if (live != null && live.at.equals(at) && live.key.equals(key)) return;
        clear(level);
        Transmitter t = new Transmitter(at, key);
        try {
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, t);
            live = t;
        } catch (RuntimeException | LinkageError e) {
            RouteReflect.warnOnce("dockLink", e.toString());
        }
    }

    void clear(LevelAccessor level) {
        Transmitter t = live;
        live = null;
        if (t == null) return;
        t.alive = false;
        try {
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, t);
        } catch (RuntimeException | LinkageError e) {
            RouteReflect.warnOnce("dockedLink", e.toString());
        }
    }

    boolean transmitting() {
        return live != null;
    }

    private static final class Transmitter implements IRedstoneLinkable {
        private final BlockPos at;
        private final Couple<RedstoneLinkNetworkHandler.Frequency> key;
        private boolean alive = true;

        Transmitter(BlockPos at, Couple<RedstoneLinkNetworkHandler.Frequency> key) {
            this.at = at.immutable();
            this.key = key;
        }

        @Override
        public int getTransmittedStrength() {
            return alive ? 15 : 0;
        }

        @Override
        public void setReceivedStrength(int power) {
        }

        @Override
        public boolean isListening() {
            return false;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }

        @Override
        public Couple<RedstoneLinkNetworkHandler.Frequency> getNetworkKey() {
            return key;
        }

        @Override
        public BlockPos getLocation() {
            return at;
        }
    }
}
