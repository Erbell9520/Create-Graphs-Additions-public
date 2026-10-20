package com.createstats.client.dynlight;

import com.createstats.CreateStats;
import com.createstats.ModConfiguration;
import com.createstats.dynlight.ClientLightOwners;
import com.createstats.dynlight.DynamicLightField;
import com.createstats.dynlight.EmissionPlan;
import com.createstats.dynlight.Light;
import com.createstats.mixin.LevelLightEngineAccessor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMaps;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongPredicate;

@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT)
public final class ClientDynamicLights {
    private static final Logger LOGGER = LogUtils.getLogger();

    static final int LEASE_TICKS = 3;

    static final int REASSERT_TICKS = 40;

    static final int HOOK_GRACE_TICKS = 200;

    private static final Map<ResourceLocation, LightSource> SOURCES = Collections.synchronizedMap(new LinkedHashMap<>());
    private static final Set<ResourceLocation> FAILED_SOURCES = ConcurrentHashMap.newKeySet();
    private static final Map<Item, ItemLight> ITEM_LIGHTS = new ConcurrentHashMap<>();
    private static final Map<Object, Lease> LEASES = new HashMap<>();
    private static final Set<Item> FAILED_ITEMS = new HashSet<>();

    @Nullable
    private static ClientLevel boundLevel;
    @Nullable
    private static LightEngine<?, ?> blockEngine;
    private static Long2ByteMap applied = Long2ByteMaps.EMPTY_MAP;
    private static Settings settings = Settings.DEFAULTS;
    private static long tick;
    private static long litSince = -1L;
    private static boolean warnedNoHook;
    private static boolean warnedUpdate;

    private static int lastLights;
    private static int lastDeferred;
    private static final int[] RELIGHTS = new int[20];

    static {
        registerSource(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "held_items"), HeldItemLights::collect);
        registerSource(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "stage_lights"), StageLightLights::collect);
        registerSource(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "vehicle_lights"), VehicleLightLights::collect);

        ClientLightOwners.install(be -> {
            StageLightLights.loaded(be);
            VehicleLightLights.loaded(be);
        }, be -> {
            StageLightLights.unloaded(be);
            VehicleLightLights.unloaded(be);
        });
    }

    private ClientDynamicLights() {
    }

    public static void init() {
    }

    static void registerItemLight(Item item, ItemLight light) {
        ITEM_LIGHTS.put(item, light);
        onClientThread(() -> FAILED_ITEMS.remove(item));
    }

    static void registerSource(ResourceLocation id, LightSource source) {
        SOURCES.put(id, source);
        FAILED_SOURCES.remove(id);
    }

    static void submit(Object key, Light light) {
        if (key == null || light == null) {
            return;
        }
        if (!RenderSystem.isOnRenderThread()) {
            Minecraft.getInstance().execute(() -> submit(key, light));
            return;
        }
        LEASES.put(key, new Lease(light, tick + LEASE_TICKS));
    }

    static void remove(Object key) {
        if (key == null) {
            return;
        }
        if (!RenderSystem.isOnRenderThread()) {
            Minecraft.getInstance().execute(() -> remove(key));
            return;
        }
        LEASES.remove(key);
    }

    static boolean isActive() {
        return settings.enabled() && blockEngine != null;
    }

    static Settings settings() {
        return settings;
    }

    @Nullable
    static ItemLight itemLight(Item item) {
        final ItemLight light = ITEM_LIGHTS.get(item);
        return light == null || FAILED_ITEMS.contains(item) ? null : light;
    }

    static boolean hasItemLights() {
        return !ITEM_LIGHTS.isEmpty();
    }

    static void itemLightFailed(Item item, RuntimeException e) {
        if (FAILED_ITEMS.add(item)) {
            LOGGER.error("[Create Stats] The dynamic light of {} threw and is switched off until restart", item, e);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        try {
            update(Minecraft.getInstance());
        } catch (RuntimeException e) {
            if (!warnedUpdate) {
                warnedUpdate = true;
                LOGGER.error("[Create Stats] A dynamic light update failed; the next tick tries again", e);
            }
        }
    }

    private static void update(Minecraft mc) {
        tick++;
        final ClientLevel level = mc.level;
        if (level != boundLevel) {
            rebind(level);
        }
        final LightEngine<?, ?> engine = blockEngine;
        if (level == null || engine == null) {
            return;
        }
        settings = Settings.read();
        final Vec3 camera = cameraOf(mc);

        final Long2ByteOpenHashMap wanted = new Long2ByteOpenHashMap();
        final Sink sink = new Sink(level, camera, settings.maxDistance(), wanted);
        collectLeases(sink, settings.enabled());
        if (settings.enabled()) {
            collectSources(level, sink);
        }
        lastLights = sink.count;

        final EmissionPlan.Step step = EmissionPlan.plan(applied, wanted, camera.x, camera.y, camera.z,
                settings.maxCells(), settings.maxChanges());
        lastDeferred = step.deferred();
        RELIGHTS[(int) (tick % RELIGHTS.length)] = step.changed().length;
        if (!step.isIdle()) {
            applied = step.next();
            DynamicLightField.publish(applied);
            final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (long pos : step.changed()) {
                engine.checkBlock(cursor.set(pos));
            }
        }
        if (tick % REASSERT_TICKS == 0) {
            reassert(pos -> true);
        }
        watchHook();
    }

    private static void rebind(@Nullable ClientLevel level) {
        applied = Long2ByteMaps.EMPTY_MAP;
        LEASES.clear();
        boundLevel = level;
        blockEngine = level == null ? null
                : ((LevelLightEngineAccessor) level.getChunkSource().getLightEngine()).createstats$blockEngine();
        DynamicLightField.bind(blockEngine);
        litSince = -1L;
        warnedNoHook = false;
    }

    private static void collectLeases(Sink sink, boolean enabled) {
        final Iterator<Lease> it = LEASES.values().iterator();
        while (it.hasNext()) {
            final Lease lease = it.next();
            if (lease.expires() < tick) {
                it.remove();
            } else if (enabled) {
                sink.add(lease.light());
            }
        }
    }

    private static void collectSources(ClientLevel level, Sink sink) {
        final List<Map.Entry<ResourceLocation, LightSource>> sources;
        synchronized (SOURCES) {
            sources = new ArrayList<>(SOURCES.entrySet());
        }
        for (Map.Entry<ResourceLocation, LightSource> source : sources) {
            if (FAILED_SOURCES.contains(source.getKey())) {
                continue;
            }
            try {
                source.getValue().collect(level, sink);
            } catch (RuntimeException | LinkageError e) {
                FAILED_SOURCES.add(source.getKey());
                LOGGER.error("[Create Stats] Dynamic light source {} threw and is switched off until restart",
                        source.getKey(), e);
            }
        }
    }

    public static void onServerLightData(int chunkX, int chunkZ) {
        if (blockEngine == null || applied.isEmpty() || Minecraft.getInstance().level != boundLevel) {
            return;
        }
        reassert(pos -> Math.abs(SectionPos.blockToSectionCoord(BlockPos.getX(pos)) - chunkX) <= 1
                && Math.abs(SectionPos.blockToSectionCoord(BlockPos.getZ(pos)) - chunkZ) <= 1);
    }

    private static void reassert(LongPredicate which) {
        final LightEngine<?, ?> engine = blockEngine;
        if (engine == null) {
            return;
        }
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        final LongIterator it = applied.keySet().iterator();
        while (it.hasNext()) {
            final long pos = it.nextLong();
            if (which.test(pos)) {
                engine.checkBlock(cursor.set(pos));
            }
        }
    }

    private static void watchHook() {
        if (applied.isEmpty()) {
            return;
        }
        if (litSince < 0L) {
            litSince = tick;
        }
        if (!warnedNoHook && !DynamicLightField.asked() && tick - litSince > HOOK_GRACE_TICKS) {
            warnedNoHook = true;
            LOGGER.warn("[Create Stats] Dynamic lights: {} cells have been lit for {} ticks and the client light "
                    + "engine never asked for them. Another mod has probably replaced the light engine, so dynamic "
                    + "lights (held lights, Stage Light pools) will not show.", applied.size(), HOOK_GRACE_TICKS);
        }
    }

    private static Vec3 cameraOf(Minecraft mc) {
        final Camera camera = mc.gameRenderer.getMainCamera();
        if (camera.isInitialized()) {
            return camera.getPosition();
        }
        return mc.player != null ? mc.player.getEyePosition() : Vec3.ZERO;
    }

    private static void onClientThread(Runnable task) {
        if (RenderSystem.isOnRenderThread()) {
            task.run();
        } else {
            Minecraft.getInstance().execute(task);
        }
    }

    @SubscribeEvent
    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        final Minecraft mc = Minecraft.getInstance();
        if (boundLevel == null || !mc.getDebugOverlay().showDebugScreen()) {
            return;
        }
        int relights = 0;
        for (int n : RELIGHTS) {
            relights += n;
        }
        event.getRight().add("");
        event.getRight().add(String.format(Locale.ROOT, "Create Stats dynamic lights: %d lights, %d cells",
                lastLights, applied.size()));
        event.getRight().add(String.format(Locale.ROOT, "%s, %d relights/s%s", status(), relights,
                lastDeferred > 0 ? ", " + lastDeferred + " queued" : ""));
        event.getRight().add(DrawnBeams.status());
    }

    private static String status() {
        if (!settings.enabled()) {
            return "Off in config";
        }
        if (DynamicLightField.asked()) {
            return "Live";
        }
        return warnedNoHook ? "NOT SHOWING: light engine replaced?" : "Waiting";
    }

    private record Lease(Light light, long expires) {
    }

    private static final class Sink implements LightSink {
        private final ClientLevel level;
        private final Vec3 camera;
        private final double maxDistance;
        private final double maxDistanceSq;
        private final Long2ByteOpenHashMap cells;
        private int count;

        Sink(ClientLevel level, Vec3 camera, double maxDistance, Long2ByteOpenHashMap cells) {
            this.level = level;
            this.camera = camera;
            this.maxDistance = maxDistance;
            this.maxDistanceSq = maxDistance * maxDistance;
            this.cells = cells;
        }

        @Override
        public void add(Light light) {
            if (light == null || !light.isValid()) {
                return;
            }
            final double dx = light.x() - this.camera.x;
            final double dy = light.y() - this.camera.y;
            final double dz = light.z() - this.camera.z;
            if (dx * dx + dy * dy + dz * dz > this.maxDistanceSq) {
                return;
            }
            LightTracer.resolve(this.level, light, this.cells);
            this.count++;
        }

        @Override
        public Vec3 camera() {
            return this.camera;
        }

        @Override
        public double maxDistance() {
            return this.maxDistance;
        }
    }

    record Settings(boolean enabled, boolean heldItems, boolean droppedItems, int maxDistance, int maxCells,
                    int maxChanges, boolean drawnBeams) {
        static final Settings DEFAULTS = new Settings(true, true, true, 64, 256, 64, true);

        static Settings read() {
            try {
                final ModConfiguration.Client c = ModConfiguration.CLIENT;
                return new Settings(c.dynamicLights.get(), c.dynamicLightsHeldItems.get(),
                        c.dynamicLightsDroppedItems.get(), c.dynamicLightsMaxDistance.get(),
                        c.dynamicLightsMaxCells.get(), c.dynamicLightsMaxChangesPerTick.get(),
                        c.dynamicLightsDrawnBeams.get());
            } catch (RuntimeException notLoadedYet) {
                return DEFAULTS;
            }
        }
    }
}
