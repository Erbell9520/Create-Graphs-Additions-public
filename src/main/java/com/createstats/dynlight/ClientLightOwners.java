package com.createstats.dynlight;

import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.Consumer;

public final class ClientLightOwners {
    private static volatile Consumer<BlockEntity> onLoad = be -> {
    };
    private static volatile Consumer<BlockEntity> onUnload = be -> {
    };

    private ClientLightOwners() {
    }

    public static void loaded(BlockEntity be) {
        onLoad.accept(be);
    }

    public static void unloaded(BlockEntity be) {
        onUnload.accept(be);
    }

    public static void install(Consumer<BlockEntity> load, Consumer<BlockEntity> unload) {
        onLoad = load;
        onUnload = unload;
    }
}
