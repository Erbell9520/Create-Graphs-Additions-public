package com.createstats.client;

import com.createstats.block.MainBreakerBlock;
import com.createstats.power.MainBreakerStuds;
import com.simibubi.create.AllSpecialTextures;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public final class MainBreakerBindHint {
    private MainBreakerBindHint() {
    }

    private static final int AMBER = 0xE0A44C;

    private static final int CYAN = 0x6BE4FF;

    private static final int GREEN = 0x7BE38C;

    private static final int RADIUS = 5;

    private static final float LINE_IDLE = 1 / 42f;
    private static final float LINE_ARMED = 1 / 32f;
    private static final float LINE_AIMED = 1 / 22f;
    private static final float GROW_IDLE = -0.010f;
    private static final float GROW_AIMED = 0.022f;
    private static final float GROW_BREATH = 0.010f;

    private static final Set<Object> LIVE_SLOTS = new HashSet<>(8);
    private static final Set<Object> SHOWN_SLOTS = new HashSet<>(8);

    @Nullable
    private static Component hudLine;

    public static void tick() {
        hudLine = null;
        LIVE_SLOTS.clear();
        final Minecraft mc = Minecraft.getInstance();
        final LocalPlayer player = mc.player;
        final ClientLevel level = mc.level;
        if (player == null || level == null || !holdsWiring(player)) {
            clear();
            return;
        }

        final BlockHitResult hit = mc.hitResult instanceof BlockHitResult b ? b : null;
        for (BlockPos pos : nearby(level, player.blockPosition())) {
            final Direction facing = level.getBlockState(pos).getValue(MainBreakerBlock.FACING);
            final MainBreakerStuds.Stud aimed = aimedStud(hit, pos, facing);
            final com.createstats.blockentity.MainBreakerBE breaker =
                    level.getBlockEntity(pos) instanceof com.createstats.blockentity.MainBreakerBE be
                            ? be : null;
            for (MainBreakerStuds.Stud stud : MainBreakerStuds.Stud.values()) {
                final boolean wired = breaker != null && breaker.studLinkCount(stud.index()) > 0;
                outline(level, pos, facing, stud, stud == aimed, wired);
            }
            if (aimed != null) {
                hudLine = Component.translatable("message.create_stats.main_breaker.aim_stud",
                        Component.translatable("message.create_stats.main_breaker.stud." + aimed.key()));
            }
        }
        retire();
    }

    @Nullable
    private static MainBreakerStuds.Stud aimedStud(@Nullable BlockHitResult hit, BlockPos pos,
                                                   Direction facing) {
        if (hit == null || !hit.getBlockPos().equals(pos)) {
            return null;
        }
        final Vec3 at = hit.getLocation();
        return MainBreakerStuds.pick(facing,
                new Vec3(at.x - pos.getX(), at.y - pos.getY(), at.z - pos.getZ()));
    }

    private static void outline(ClientLevel level, BlockPos pos, Direction facing,
                                MainBreakerStuds.Stud stud, boolean aimed, boolean wired) {
        final AABB box = MainBreakerStuds.box(facing, stud).move(pos.getX(), pos.getY(), pos.getZ());
        final float breath = Mth.sin((level.getGameTime() % 200L) * 0.0314f) * 0.5f + 0.5f;
        final float grow = aimed ? GROW_AIMED + breath * GROW_BREATH : GROW_IDLE;
        final float width = aimed ? LINE_AIMED : LINE_ARMED;

        Outliner.getInstance().chaseAABB(slot(pos, stud), box.inflate(grow))
                .colored(wired ? GREEN : stud == MainBreakerStuds.Stud.INPUT ? AMBER : CYAN)
                .lineWidth(width)

                .withFaceTexture(aimed ? AllSpecialTextures.SELECTION : null)
                .highlightFace(aimed ? facing : null)
                .disableLineNormals();
    }

    public static void renderHud(GuiGraphics graphics) {
        final Component line = hudLine;
        final Minecraft mc = Minecraft.getInstance();
        if (line == null || mc.player == null || mc.options.hideGui) {
            return;
        }
        final String text = line.getString();
        final int x = graphics.guiWidth() / 2 - mc.font.width(text) / 2;
        final int y = graphics.guiHeight() / 2 + 12;
        graphics.drawString(mc.font, text, x, y, 0xFFE0A44C, true);
    }

    public static void clear() {
        if (SHOWN_SLOTS.isEmpty()) {
            return;
        }
        for (Object slot : SHOWN_SLOTS) {
            Outliner.getInstance().remove(slot);
        }
        SHOWN_SLOTS.clear();
    }

    private static void retire() {
        SHOWN_SLOTS.removeIf(slot -> {
            if (LIVE_SLOTS.contains(slot)) {
                return false;
            }
            Outliner.getInstance().remove(slot);
            return true;
        });
        SHOWN_SLOTS.addAll(LIVE_SLOTS);
    }

    private static Object slot(BlockPos pos, MainBreakerStuds.Stud stud) {
        final Object key = "main_breaker_hint:" + pos.asLong() + ':' + stud;
        LIVE_SLOTS.add(key);
        return key;
    }

    private static boolean holdsWiring(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            final ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof com.createstats.item.CopperWireItem
                    || stack.getItem() instanceof com.createstats.item.CableItem) {
                return true;
            }
            if (stack.getItem() instanceof BlockItem blockItem
                    && blockItem.getBlock() instanceof com.createstats.block.PowerConnectorBlock) {
                return true;
            }
        }
        return false;
    }

    private static List<BlockPos> nearby(ClientLevel level, BlockPos centre) {
        final List<BlockPos> found = new ArrayList<>(2);
        for (BlockPos p : BlockPos.betweenClosed(centre.offset(-RADIUS, -RADIUS, -RADIUS),
                centre.offset(RADIUS, RADIUS, RADIUS))) {
            if (level.getBlockState(p).getBlock() instanceof MainBreakerBlock) {
                found.add(p.immutable());
            }
        }
        return found;
    }
}
