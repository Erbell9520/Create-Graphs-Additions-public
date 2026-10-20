package com.createstats.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public final class RefinedStorageCompatHelper {
    private static final String NODE_CONTAINER_BE =
            "com.refinedmods.refinedstorage.common.api.support.network.AbstractNetworkNodeContainerBlockEntity";
    private static final String NETWORK_NODE =
            "com.refinedmods.refinedstorage.api.network.node.NetworkNode";
    private static final String STORAGE_COMPONENT =
            "com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent";
    private static final String ENERGY_COMPONENT =
            "com.refinedmods.refinedstorage.api.network.energy.EnergyNetworkComponent";
    private static final String AUTOCRAFTING_COMPONENT =
            "com.refinedmods.refinedstorage.api.network.autocrafting.AutocraftingNetworkComponent";
    private static final String ROOT_STORAGE_IMPL =
            "com.refinedmods.refinedstorage.api.storage.root.RootStorageImpl";
    private static final String COMPOSITE_STORAGE =
            "com.refinedmods.refinedstorage.api.storage.composite.CompositeStorage";
    private static final String PROXY_STORAGE =
            "com.refinedmods.refinedstorage.api.storage.AbstractProxyStorage";
    private static final String LIMITED_STORAGE =
            "com.refinedmods.refinedstorage.api.storage.limited.LimitedStorage";
    private static final String PLATFORM_STORAGE =
            "com.refinedmods.refinedstorage.common.storage.PlatformStorage";
    private static final String ITEM_RESOURCE =
            "com.refinedmods.refinedstorage.common.support.resource.ItemResource";
    private static final String FLUID_RESOURCE =
            "com.refinedmods.refinedstorage.common.support.resource.FluidResource";
    private static final String GRID_BE =
            "com.refinedmods.refinedstorage.common.grid.AbstractGridBlockEntity";

    public static final String SOURCE_TYPE = "Refined Storage Network";

    // no bare cable, a link on a random cable is a link nobody finds again
    private static final Set<String> STATS_LINK_BLOCK_PATHS = Set.of(
            "grid", "crafting_grid", "pattern_grid",
            "controller", "creative_controller",
            "disk_drive", "disk_interface", "external_storage",
            "storage_monitor", "autocrafting_monitor", "autocrafter_manager",
            "network_receiver", "network_transmitter", "relay",
            "importer", "exporter", "interface", "detector", "security_manager",
            "wireless_transmitter");

    public static final int MAX_TRACKED_ITEMS = 24;

    private RefinedStorageCompatHelper() {
    }

    public record Entry(String id, String displayName, ItemStack icon, long amount) {
    }

    public record NetworkRead(boolean online,
                              long itemsStored,
                              long itemCapacity,
                              long fluidsStored,
                              int itemTypes,
                              int fluidTypes,
                              long energyStored,
                              long energyCapacity,
                              int patternCount,
                              int storageCount,
                              List<Entry> items,
                              List<Entry> fluids) {
        public static final NetworkRead OFFLINE = new NetworkRead(
                false, 0L, -1L, 0L, 0, 0, 0L, 0L, 0, 0, List.of(), List.of());

        public boolean hasCapacity() {
            return this.itemCapacity > 0L;
        }

        public float fillPercent() {
            return hasCapacity() ? (float) (this.itemsStored * 100.0 / this.itemCapacity) : -1.0f;
        }

        public float energyPercent() {
            return this.energyCapacity > 0L
                    ? (float) (this.energyStored * 100.0 / this.energyCapacity) : -1.0f;
        }
    }

    public static boolean supportsStatsLinkBlock(@Nullable Block block) {
        if (!RefinedStorageCompat.isAvailable() || block == null) {
            return false;
        }
        final ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return id != null && RefinedStorageCompat.MOD_ID.equals(id.getNamespace())
                && STATS_LINK_BLOCK_PATHS.contains(id.getPath());
    }

    public static String getSourceType(@Nullable BlockEntity sourceBE) {
        return isNetworkBlockEntity(sourceBE) ? SOURCE_TYPE : "";
    }

    public static boolean isNetworkBlockEntity(@Nullable BlockEntity sourceBE) {
        if (!RefinedStorageCompat.isAvailable() || sourceBE == null) {
            return false;
        }
        final Class<?> base = cls(NODE_CONTAINER_BE);
        return base != null && base.isInstance(sourceBE);
    }

    public static NetworkRead read(@Nullable BlockEntity sourceBE) {
        if (!isNetworkBlockEntity(sourceBE)) {
            return NetworkRead.OFFLINE;
        }
        try {
            final Object network = networkOf(sourceBE);
            if (network == null) {
                return NetworkRead.OFFLINE;
            }
            final Object storage = component(network, STORAGE_COMPONENT);
            final Object energy = component(network, ENERGY_COMPONENT);
            final Object autocrafting = component(network, AUTOCRAFTING_COMPONENT);

            final List<Entry> items = new ArrayList<>();
            final List<Entry> fluids = new ArrayList<>();
            final long[] totals = collect(storage, items, fluids);

            final long[] capacity = capacityOf(storage);

            return new NetworkRead(
                    gridActive(sourceBE),
                    totals[0],
                    capacity[0],
                    totals[1],
                    items.size(),
                    fluids.size(),
                    longOf(energy, "getStored"),
                    longOf(energy, "getCapacity"),
                    patternCount(autocrafting),
                    (int) capacity[1],
                    trim(items),
                    trim(fluids));
        } catch (RuntimeException | LinkageError failed) {
            return NetworkRead.OFFLINE;
        }
    }

    private static boolean gridActive(BlockEntity sourceBE) {
        final Class<?> grid = cls(GRID_BE);
        if (grid == null || !grid.isInstance(sourceBE)) {
            return true;
        }
        final Object active = invoke(sourceBE, "isGridActive");
        return !(active instanceof Boolean b) || b;
    }

    private static List<Entry> trim(List<Entry> entries) {
        entries.sort((a, b) -> Long.compare(b.amount(), a.amount()));
        return entries.size() <= MAX_TRACKED_ITEMS
                ? List.copyOf(entries)
                : List.copyOf(entries.subList(0, MAX_TRACKED_ITEMS));
    }

    @Nullable
    private static Object networkOf(BlockEntity sourceBE) {
        final Field field = field(sourceBE.getClass(), "mainNetworkNode");
        if (field == null) {
            return null;
        }
        final Object node;
        try {
            node = field.get(sourceBE);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
        final Class<?> nodeType = cls(NETWORK_NODE);
        if (node == null || nodeType == null || !nodeType.isInstance(node)) {
            return null;
        }
        return invoke(node, "getNetwork");
    }

    @Nullable
    private static Object component(@Nullable Object network, String componentClass) {
        final Class<?> type = cls(componentClass);
        if (network == null || type == null) {
            return null;
        }
        final Method getComponent = method(network.getClass(), "getComponent", Class.class);
        if (getComponent == null) {
            return null;
        }
        try {
            return getComponent.invoke(network, type);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static long[] collect(@Nullable Object storage, List<Entry> items, List<Entry> fluids) {
        final long[] totals = new long[2];
        final Object all = invoke(storage, "getAll");
        if (!(all instanceof Collection<?> resources)) {
            return totals;
        }
        final Class<?> itemResource = cls(ITEM_RESOURCE);
        final Class<?> fluidResource = cls(FLUID_RESOURCE);
        final Map<String, long[]> itemAmounts = new LinkedHashMap<>();
        final Map<String, ItemStack> itemIcons = new LinkedHashMap<>();
        final Map<String, long[]> fluidAmounts = new LinkedHashMap<>();
        final Map<String, String> fluidNames = new LinkedHashMap<>();

        for (final Object entry : resources) {
            final Object resource = invoke(entry, "resource");
            final Object amountBox = invoke(entry, "amount");
            if (resource == null || !(amountBox instanceof Long amount) || amount <= 0L) {
                continue;
            }
            if (itemResource != null && itemResource.isInstance(resource)) {
                final ItemStack stack = stackOf(resource);
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                final ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (key == null) {
                    continue;
                }
                final String id = key.toString();
                final long[] slot = itemAmounts.computeIfAbsent(id, k -> new long[]{0L, 0L});
                slot[0] += amount;
                if (amount > slot[1]) {
                    slot[1] = amount;
                    itemIcons.put(id, stack);
                }
                totals[0] += amount;
            } else if (fluidResource != null && fluidResource.isInstance(resource)) {
                final Object fluid = invoke(resource, "fluid");
                final ResourceLocation key = fluid == null
                        ? null : BuiltInRegistries.FLUID.getKey((net.minecraft.world.level.material.Fluid) fluid);
                if (key == null) {
                    continue;
                }
                final String id = key.toString();
                fluidAmounts.computeIfAbsent(id, k -> new long[]{0L})[0] += amount;
                fluidNames.putIfAbsent(id, fluidDisplayName(key));
                totals[1] += amount;
            }
        }

        for (final Map.Entry<String, long[]> e : itemAmounts.entrySet()) {
            final ItemStack icon = itemIcons.getOrDefault(e.getKey(), ItemStack.EMPTY);
            items.add(new Entry(e.getKey(), icon.isEmpty()
                    ? e.getKey() : icon.getHoverName().getString(), icon, e.getValue()[0]));
        }
        for (final Map.Entry<String, long[]> e : fluidAmounts.entrySet()) {
            fluids.add(new Entry(e.getKey(), fluidNames.getOrDefault(e.getKey(), e.getKey()),
                    ItemStack.EMPTY, e.getValue()[0]));
        }
        return totals;
    }

    private static String fluidDisplayName(ResourceLocation key) {
        final String path = key.getPath().replace('_', ' ');
        final StringBuilder sb = new StringBuilder(path.length());
        boolean upper = true;
        for (int i = 0; i < path.length(); i++) {
            final char c = path.charAt(i);
            sb.append(upper ? Character.toUpperCase(c) : c);
            upper = c == ' ';
        }
        return sb.toString();
    }

    @Nullable
    private static ItemStack stackOf(Object itemResource) {
        final Object stack = invoke(itemResource, "toItemStack");
        return stack instanceof ItemStack is ? is : null;
    }

    private static long[] capacityOf(@Nullable Object rootStorage) {
        if (rootStorage == null) {
            return new long[]{-1L, 0L};
        }
        final Field storageField = field(rootStorage.getClass(), "storage");
        Object composite = null;
        if (storageField != null && cls(ROOT_STORAGE_IMPL) != null
                && cls(ROOT_STORAGE_IMPL).isInstance(rootStorage)) {
            try {
                composite = storageField.get(rootStorage);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                composite = null;
            }
        }
        if (composite == null) {
            return new long[]{-1L, 0L};
        }
        final long[] acc = new long[]{0L, 0L};
        final boolean[] exact = {true};
        walkSources(composite, new IdentityHashMap<>(), acc, exact, 0);
        return new long[]{exact[0] ? acc[0] : -1L, acc[1]};
    }

    private static void walkSources(Object storage, IdentityHashMap<Object, Boolean> seen,
                                    long[] acc, boolean[] exact, int depth) {
        if (storage == null || depth > 8 || seen.put(storage, Boolean.TRUE) != null) {
            return;
        }
        final Class<?> composite = cls(COMPOSITE_STORAGE);
        if (composite != null && composite.isInstance(storage)) {
            final Object sources = invoke(storage, "getSources");
            if (sources instanceof Collection<?> list) {
                for (final Object child : list) {
                    walkSources(child, seen, acc, exact, depth + 1);
                }
            }
            return;
        }
        final Class<?> limited = cls(LIMITED_STORAGE);
        if (limited != null && limited.isInstance(storage)) {
            acc[1]++;
            final long capacity = longOf(storage, "getCapacity");
            switch (classify(storage)) {
                case 1 -> acc[0] += capacity;
                case 0 -> {  }
                default -> exact[0] = false;
            }
            return;
        }

        final Class<?> proxy = cls(PROXY_STORAGE);
        if (proxy != null && proxy.isInstance(storage)) {
            final Field delegate = field(storage.getClass(), "delegate");
            if (delegate != null) {
                try {
                    walkSources(delegate.get(storage), seen, acc, exact, depth + 1);
                    return;
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            }
        }
    }

    private static int classify(Object storage) {
        final Class<?> platform = cls(PLATFORM_STORAGE);
        if (platform == null || !platform.isInstance(storage)) {
            return -1;
        }
        final Method getType = method(platform, "getType");
        if (getType == null) {
            return -1;
        }
        final Object type;
        try {
            type = getType.invoke(storage);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return -1;
        }
        final Object probe = itemProbe();
        if (type == null || probe == null) {
            return -1;
        }
        final Method isAllowed = method(type.getClass(), "isAllowed",
                cls("com.refinedmods.refinedstorage.api.resource.ResourceKey"));
        if (isAllowed == null) {
            return -1;
        }
        try {
            return Boolean.TRUE.equals(isAllowed.invoke(type, probe)) ? 1 : 0;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return -1;
        }
    }

    @Nullable
    private static Object itemProbe() {
        if (PROBE.length == 0) {
            return null;
        }
        if (PROBE[0] == null) {
            final Class<?> type = cls(ITEM_RESOURCE);
            if (type == null) {
                return null;
            }
            try {
                PROBE[0] = type.getConstructor(net.minecraft.world.item.Item.class)
                        .newInstance(Items.STONE);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
        return PROBE[0];
    }

    private static final Object[] PROBE = new Object[1];

    private static int patternCount(@Nullable Object autocrafting) {
        final Object patterns = invoke(autocrafting, "getPatterns");
        return patterns instanceof Collection<?> c ? c.size() : 0;
    }

    private static final Method NO_METHOD;
    private static final Field NO_FIELD;

    static {
        Method noMethod = null;
        Field noField = null;
        try {
            noMethod = Object.class.getMethod("hashCode");
            noField = Holder.class.getDeclaredField("sentinel");
        } catch (ReflectiveOperationException impossible) {
        }
        NO_METHOD = noMethod;
        NO_FIELD = noField;
    }

    private static final class Holder {
        @SuppressWarnings("unused")
        private static final int sentinel = 0;
    }

    private static final Map<String, Object> CLASSES = new ConcurrentHashMap<>();
    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();
    private static final Map<String, Field> FIELDS = new ConcurrentHashMap<>();
    private static final Object NO_CLASS = new Object();

    @Nullable
    private static Class<?> cls(String name) {
        final Object cached = CLASSES.computeIfAbsent(name, key -> {
            try {
                return Class.forName(key, false, RefinedStorageCompatHelper.class.getClassLoader());
            } catch (ClassNotFoundException | LinkageError missing) {
                return NO_CLASS;
            }
        });
        return cached == NO_CLASS ? null : (Class<?>) cached;
    }

    @Nullable
    private static Method method(@Nullable Class<?> owner, String name, Class<?>... params) {
        if (owner == null) {
            return null;
        }
        final StringBuilder key = new StringBuilder(owner.getName()).append('#').append(name);
        for (final Class<?> p : params) {
            key.append('/').append(p == null ? "?" : p.getName());
        }
        for (final Class<?> p : params) {
            if (p == null) {
                return null;
            }
        }
        final Method found = METHODS.computeIfAbsent(key.toString(), ignored -> {
            for (Class<?> c = owner; c != null && c != Object.class; c = c.getSuperclass()) {
                try {
                    final Method m = c.getDeclaredMethod(name, params);
                    m.setAccessible(true);
                    return m;
                } catch (ReflectiveOperationException | RuntimeException ignoredToo) {
                }
            }
            try {
                final Method m = owner.getMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (ReflectiveOperationException | RuntimeException ignoredToo) {
                return NO_METHOD;
            }
        });
        return found == NO_METHOD ? null : found;
    }

    @Nullable
    private static Field field(Class<?> owner, String name) {
        final Field found = FIELDS.computeIfAbsent(owner.getName() + "#" + name, ignored -> {
            for (Class<?> c = owner; c != null && c != Object.class; c = c.getSuperclass()) {
                try {
                    final Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    return f;
                } catch (ReflectiveOperationException | RuntimeException ignoredToo) {
                }
            }
            return NO_FIELD;
        });
        return found == NO_FIELD ? null : found;
    }

    @Nullable
    private static Object invoke(@Nullable Object target, String name) {
        if (target == null) {
            return null;
        }
        final Method m = method(target.getClass(), name);
        if (m == null) {
            return null;
        }
        try {
            return m.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static long longOf(@Nullable Object target, String name) {
        final Object value = invoke(target, name);
        return value instanceof Number n ? n.longValue() : 0L;
    }
}
