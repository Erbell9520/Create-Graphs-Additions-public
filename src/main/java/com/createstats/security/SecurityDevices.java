package com.createstats.security;

import com.createstats.power.SecurityDevice;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

// instanceof first, then adapters. the doors were being written in parallel
public final class SecurityDevices {
    private SecurityDevices() {
    }

    public interface Adapter extends Function<BlockEntity, SecurityDevice> {
    }

    private static final List<Adapter> ADAPTERS = new CopyOnWriteArrayList<>();

    static {
        ADAPTERS.add(SecurityDevices::adaptGenericDoor);
    }

    public static void register(Adapter adapter) {
        if (adapter != null) {
            ADAPTERS.add(0, adapter);
        }
    }

    public static SecurityDevice of(BlockEntity be) {
        if (be == null) {
            return null;
        }
        if (be instanceof SecurityDevice native_) {
            return native_;
        }
        for (Adapter adapter : ADAPTERS) {
            final SecurityDevice adapted = adapter.apply(be);
            if (adapted != null) {
                return adapted;
            }
        }
        return null;
    }

    public static boolean isDevice(BlockEntity be) {
        return of(be) != null;
    }

    private static SecurityDevice adaptGenericDoor(BlockEntity be) {
        final BlockState state = be.getBlockState();
        final ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!"create_stats".equals(id.getNamespace()) || !id.getPath().endsWith("door")) {
            return null;
        }
        final boolean open = state.hasProperty(BlockStateProperties.OPEN)
                && state.getValue(BlockStateProperties.OPEN);
        final Component name = state.getBlock().getName();
        return new SecurityDevice() {
            @Override
            public DeviceClass deviceClass() {
                return DeviceClass.DOOR;
            }

            @Override
            public Component deviceName() {
                return name;
            }

            @Override
            public DeviceState deviceState() {
                return open ? DeviceState.ACTIVE : DeviceState.IDLE;
            }
        };
    }
}
