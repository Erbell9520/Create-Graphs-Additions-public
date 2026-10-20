package com.createstats.client.tuner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// the mob itself, 16 spawn eggs round a dial are 16 speckled ovals
public final class TunerIcons {
    private static final Map<EntityType<?>, LivingEntity> MODELS = new HashMap<>();
    private static final Set<EntityType<?>> FAILED = new HashSet<>();
    @Nullable
    private static Level madeIn;

    private TunerIcons() {
    }

    public static void draw(GuiGraphics g, String icon, int x, int y, int size) {
        if (icon.startsWith("entity:")) {
            final EntityType<?> type = entityType(icon.substring("entity:".length()));
            if (type != null && portrait(g, type, x, y, size)) {
                return;
            }
            if (type != null) {
                final SpawnEggItem egg = SpawnEggItem.byId(type);
                if (egg != null) {
                    item(g, new ItemStack(egg), x, y, size);
                    return;
                }
            }
            unknown(g, x, y, size);
            return;
        }
        final ResourceLocation id = ResourceLocation.tryParse(icon);
        final Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null) {
            unknown(g, x, y, size);
            return;
        }
        item(g, new ItemStack(item), x, y, size);
    }

    @Nullable
    private static EntityType<?> entityType(String id) {
        final ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(rl).orElse(null);
    }

    private static void item(GuiGraphics g, ItemStack stack, int x, int y, int size) {
        final int off = (size - 16) / 2;
        g.renderItem(stack, x + off, y + off);
    }

    private static void unknown(GuiGraphics g, int x, int y, int size) {
        final Minecraft mc = Minecraft.getInstance();
        g.drawString(mc.font, "?", x + (size - mc.font.width("?")) / 2, y + (size - 8) / 2, 0xFF404040, false);
    }

    private static boolean portrait(GuiGraphics g, EntityType<?> type, int x, int y, int size) {
        final LivingEntity model = model(type);
        if (model == null) {
            return false;
        }
        final float height = model.getBbHeight();
        final float width = model.getBbWidth();
        final float shown = Math.max(0.3f, Math.min(height, Math.max(width, 0.9f) * 1.2f));
        final float scale = (size - 2) / shown;
        final float feet = y + 1 + height * scale;
        final float yaw = 25.0f;
        final float bodyRot = model.yBodyRot;
        final float yRot = model.getYRot();
        final float xRot = model.getXRot();
        final float headRotO = model.yHeadRotO;
        final float headRot = model.yHeadRot;
        model.yBodyRot = 180.0f + yaw;
        model.setYRot(180.0f + yaw);
        model.setXRot(0.0f);
        model.yHeadRot = model.getYRot();
        model.yHeadRotO = model.getYRot();
        g.enableScissor(x, y, x + size, y + size);
        try {
            final Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
            final Quaternionf tilt = new Quaternionf().rotateX((float) Math.toRadians(-8.0));
            pose.mul(tilt);
            InventoryScreen.renderEntityInInventory(g, x + size / 2.0f, feet, scale, new Vector3f(), pose, tilt, model);
        } catch (RuntimeException | LinkageError e) {
            FAILED.add(type);
            MODELS.remove(type);
            return false;
        } finally {
            g.disableScissor();
            model.yBodyRot = bodyRot;
            model.setYRot(yRot);
            model.setXRot(xRot);
            model.yHeadRotO = headRotO;
            model.yHeadRot = headRot;
        }
        return true;
    }

    @Nullable
    private static LivingEntity model(EntityType<?> type) {
        final Level level = Minecraft.getInstance().level;
        if (level == null || FAILED.contains(type)) {
            return null;
        }
        if (level != madeIn) {
            MODELS.clear();
            FAILED.clear();
            madeIn = level;
        }
        final LivingEntity cached = MODELS.get(type);
        if (cached != null) {
            return cached;
        }
        try {
            final Entity made = type.create(level);
            if (made instanceof LivingEntity living) {
                MODELS.put(type, living);
                return living;
            }
        } catch (RuntimeException e) {
        }
        FAILED.add(type);
        return null;
    }
}
