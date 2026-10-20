package com.createstats.client;

import com.createstats.block.ContactorBlock;
import com.createstats.blockentity.ContactorBE;
import com.createstats.contactor.ContactorLogic;
import com.createstats.contactor.ContactorStuds;
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

// same chamfered box language as the breaker guide
@OnlyIn(Dist.CLIENT)
public final class ContactorBindHint {
    private ContactorBindHint() {
    }

    private static final int AMBER = 0xE0A44C;

    private static final int CYAN = 0x6BE4FF;

    private static final int GREEN = 0x7BE38C;

    private static final int RADIUS = 5;

    private static final float LINE_ARMED = 1 / 32f;
    private static final float LINE_AIMED = 1 / 22f;
    private static final float GROW_IDLE = -0.012f;
    private static final float GROW_AIMED = 0.02f;
    private static final float GROW_BREATH = 0.009f;

    private static final Set<Object> LIVE_SLOTS = new HashSet<>(16);
    private static final Set<Object> SHOWN_SLOTS = new HashSet<>(16);

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
            final var state = level.getBlockState(pos);
            final Direction facing = state.getValue(ContactorBlock.FACING);
            final int spin = ContactorBlock.spin(state);
            final int aimed = aimedStud(hit, pos, facing, spin);
            final ContactorBE be = level.getBlockEntity(pos) instanceof ContactorBE c ? c : null;
            for (int stud = 0; stud < ContactorLogic.STUDS; stud++) {
                final boolean wired = be != null && be.studLinkCount(stud) > 0;
                outline(level, pos, facing, spin, stud, stud == aimed, wired);
            }
            if (aimed >= 0) {
                hudLine = Component.translatable("message.create_stats.contactor.aim_stud",
                        ContactorLogic.studName(aimed),
                        Component.translatable(ContactorLogic.isLine(aimed)
                                ? "message.create_stats.contactor.row.line" : "message.create_stats.contactor.row.load"),
                        Component.translatable("message.create_stats.contactor.pole." + ContactorLogic.poleOf(aimed)));
            }
        }
        retire();
    }

    private static int aimedStud(@Nullable BlockHitResult hit, BlockPos pos, Direction facing, int spin) {
        if (hit == null || !hit.getBlockPos().equals(pos)) {
            return -1;
        }
        final Vec3 at = hit.getLocation();
        return ContactorStuds.pick(facing, spin, new Vec3(at.x - pos.getX(), at.y - pos.getY(), at.z - pos.getZ()));
    }

    private static void outline(ClientLevel level, BlockPos pos, Direction facing, int spin, int stud, boolean aimed,
                                boolean wired) {
        final AABB box = ContactorStuds.box(facing, spin, stud).move(pos.getX(), pos.getY(), pos.getZ());
        final float breath = Mth.sin((level.getGameTime() % 200L) * 0.0314f) * 0.5f + 0.5f;
        final float grow = aimed ? GROW_AIMED + breath * GROW_BREATH : GROW_IDLE;
        Outliner.getInstance().chaseAABB(slot(pos, stud), box.inflate(grow))
                .colored(wired ? GREEN : ContactorLogic.isLine(stud) ? AMBER : CYAN)
                .lineWidth(aimed ? LINE_AIMED : LINE_ARMED)
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

    private static Object slot(BlockPos pos, int stud) {
        final Object key = "contactor_hint:" + pos.asLong() + ':' + stud;
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
            if (level.getBlockState(p).getBlock() instanceof ContactorBlock) {
                found.add(p.immutable());
            }
        }
        return found;
    }
}
