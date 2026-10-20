package com.createstats.client.dynlight;

import com.createstats.dynlight.Light;
import com.createstats.dynlight.PointLight;
import it.unimi.dsi.fastutil.objects.Reference2ByteOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

final class HeldItemLights {
    private static final Reference2ByteOpenHashMap<Item> BLOCK_LEVELS = new Reference2ByteOpenHashMap<>();

    static {
        BLOCK_LEVELS.defaultReturnValue((byte) -1);
    }

    private HeldItemLights() {
    }

    static void collect(ClientLevel level, LightSink sink) {
        final ClientDynamicLights.Settings settings = ClientDynamicLights.settings();
        if (!settings.heldItems() && !settings.droppedItems() && !ClientDynamicLights.hasItemLights()) {
            return;
        }
        final Vec3 camera = sink.camera();
        final double maxSq = sink.maxDistance() * sink.maxDistance();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity.isSpectator() || entity.distanceToSqr(camera) > maxSq) {
                continue;
            }
            if (entity instanceof LivingEntity living) {
                offer(sink, living, living.getMainHandItem(), settings.heldItems());
                offer(sink, living, living.getOffhandItem(), settings.heldItems());
            } else if (entity instanceof ItemEntity item) {
                offer(sink, item, item.getItem(), settings.droppedItems());
            }
        }
    }

    private static void offer(LightSink sink, Entity carrier, ItemStack stack, boolean blockLights) {
        if (stack.isEmpty()) {
            return;
        }
        final Item item = stack.getItem();
        final ItemLight custom = ClientDynamicLights.itemLight(item);
        if (custom != null) {
            final Light light;
            try {
                light = custom.lightFor(carrier, stack);
            } catch (RuntimeException e) {
                ClientDynamicLights.itemLightFailed(item, e);
                return;
            }
            if (light != null) {
                sink.add(light);
            }
            return;
        }
        if (!blockLights) {
            return;
        }
        final int level = blockLevel(item);
        if (level > 0) {
            final double y = carrier instanceof ItemEntity ? carrier.getY() + 0.25 : carrier.getEyeY();
            sink.add(new PointLight(carrier.getX(), y, carrier.getZ(), level));
        }
    }

    static int blockLevel(Item item) {
        final byte known = BLOCK_LEVELS.getByte(item);
        if (known >= 0) {
            return known;
        }
        final int level = item instanceof BlockItem block
                ? Math.max(0, Math.min(15, block.getBlock().defaultBlockState().getLightEmission()))
                : 0;
        BLOCK_LEVELS.put(item, (byte) level);
        return level;
    }
}
