package com.createstats.compat;

import com.createstats.power.WireTier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

// by item id, survives their refactors and degrades to "not recognised" instead of a crash
public final class ForeignSpools {
    private static final ResourceLocation CA_EMPTY_SPOOL =
            ResourceLocation.fromNamespaceAndPath("createaddition", "spool");

    public enum Family {
        CREATE_ADDITION,

        NEW_AGE
    }

    public record Spool(Family family, WireTier tier) {
    }

    private static final Map<ResourceLocation, Spool> TABLE = Map.of(

            ResourceLocation.fromNamespaceAndPath("createaddition", "copper_spool"),
            new Spool(Family.CREATE_ADDITION, WireTier.COPPER),
            ResourceLocation.fromNamespaceAndPath("createaddition", "gold_spool"),
            new Spool(Family.CREATE_ADDITION, WireTier.INSULATED),
            ResourceLocation.fromNamespaceAndPath("createaddition", "electrum_spool"),
            new Spool(Family.CREATE_ADDITION, WireTier.SHIELDED),
            ResourceLocation.fromNamespaceAndPath("createaddition", "festive_spool"),
            new Spool(Family.CREATE_ADDITION, WireTier.COPPER),

            ResourceLocation.fromNamespaceAndPath("create_new_age", "copper_wire"),
            new Spool(Family.NEW_AGE, WireTier.COPPER),
            ResourceLocation.fromNamespaceAndPath("create_new_age", "overcharged_iron_wire"),
            new Spool(Family.NEW_AGE, WireTier.INSULATED),
            ResourceLocation.fromNamespaceAndPath("create_new_age", "overcharged_golden_wire"),
            new Spool(Family.NEW_AGE, WireTier.SHIELDED),
            ResourceLocation.fromNamespaceAndPath("create_new_age", "overcharged_diamond_wire"),
            new Spool(Family.NEW_AGE, WireTier.HV));

    private static final Set<ResourceLocation> WIRING_ITEMS = buildWiringItems();

    private static Set<ResourceLocation> buildWiringItems() {
        final Set<ResourceLocation> ids = new HashSet<>(TABLE.keySet());
        ids.add(CA_EMPTY_SPOOL);
        for (String path : new String[] {"copper_wire", "iron_wire", "gold_wire", "electrum_wire",
                "connector", "large_connector", "small_light_connector"}) {
            ids.add(ResourceLocation.fromNamespaceAndPath("createaddition", path));
        }
        for (String path : new String[] {"electrical_connector", "street_light"}) {
            ids.add(ResourceLocation.fromNamespaceAndPath("create_new_age", path));
        }
        return ids;
    }

    static boolean isWiringItemId(@Nullable ResourceLocation id) {
        return id != null && WIRING_ITEMS.contains(id);
    }

    public static boolean isWiringItem(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && isWiringItemId(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    private ForeignSpools() {
    }

    public static boolean anyForeignWireLoaded() {
        return net.neoforged.fml.ModList.get().isLoaded("createaddition")
                || net.neoforged.fml.ModList.get().isLoaded("create_new_age");
    }

    @Nullable
    public static Spool of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return TABLE.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    @Nullable
    public static WireTier tierOf(ItemStack stack) {
        final Spool spool = of(stack);
        return spool == null ? null : spool.tier();
    }

    public static boolean isRemover(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && CA_EMPTY_SPOOL.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public static boolean isKnown(ItemStack stack) {
        return of(stack) != null || isRemover(stack);
    }

    public static BiConsumer<Player, ItemStack> stockFor(ItemStack held) {
        final Spool spool = of(held);
        if (spool == null || spool.family() != Family.CREATE_ADDITION) {
            return ForeignSpools::shrinkOne;
        }
        return (player, stack) -> {
            if (player.getAbilities().instabuild) {
                return;
            }
            stack.shrink(1);
            giveBack(player, CA_EMPTY_SPOOL);
        };
    }

    private static void shrinkOne(Player player, ItemStack stack) {
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }

    private static void giveBack(Player player, ResourceLocation id) {
        final Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null) {
            return;
        }
        final ItemStack back = new ItemStack(item);
        if (!player.getInventory().add(back)) {
            player.drop(back, false);
        }
    }
}
