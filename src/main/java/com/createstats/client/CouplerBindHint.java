package com.createstats.client;

import com.createstats.block.UniversalPowerCouplerBlock;
import com.createstats.blockentity.UniversalPowerCouplerBE;
import com.createstats.compat.ForeignAnchorReader;
import com.createstats.compat.ForeignSpools;
import com.createstats.power.CouplerSockets;
import com.createstats.power.FeGridMember;
import com.createstats.power.NewAgeFeed;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public final class CouplerBindHint {
    private CouplerBindHint() {
    }

    public enum Face { FOREIGN, GRID }

    public enum FaceState { PROMPT, SATISFIED }

    private static final int AMBER = 0xE0A44C;

    private static final int CYAN = 0x6BE4FF;

    private static final int GREEN = 0x63D68A;

    private static final int RADIUS = 6;

    private static final float LINE_AIMED = 1 / 22f;
    private static final float LINE_ARMED = 1 / 32f;
    private static final float LINE_IDLE = 1 / 52f;

    private static final float GROW_AIMED = 0.028f;
    private static final float GROW_BREATH = 0.012f;
    private static final float GROW_SATISFIED = -0.028f;
    private static final float GROW_IDLE = -0.02f;

    private static final Set<Object> LIVE_SLOTS = new HashSet<>(8);
    private static final Set<Object> SHOWN_SLOTS = new HashSet<>(8);

    @Nullable
    private static Component hudLine;

    @Nullable
    public static Face emphasis(@Nullable ItemStack held) {
        if (held == null || held.isEmpty()) {
            return null;
        }
        if (held.getItem() instanceof com.createstats.item.CopperWireItem
                || held.getItem() instanceof com.createstats.item.CableItem) {
            return Face.GRID;
        }
        if (held.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof com.createstats.block.PowerConnectorBlock) {
            return Face.GRID;
        }
        return ForeignSpools.isWiringItem(held) ? Face.FOREIGN : null;
    }

    @Nullable
    private static Face emphasisAlso(@Nullable ItemStack held) {
        return held != null && held.getItem() instanceof com.createstats.item.CopperWireItem
                ? Face.FOREIGN : null;
    }

    public static FaceState faceState(Level level, BlockPos couplerPos, Direction face,
                                      boolean isForeignFace) {
        final UniversalPowerCouplerBE coupler =
                level.getBlockEntity(couplerPos) instanceof UniversalPowerCouplerBE be ? be : null;
        if (isForeignFace) {
            if (coupler != null && coupler.wiredSeamPos() != null) {
                return FaceState.SATISFIED;
            }
            return NewAgeFeed.isForeignSeam(level, couplerPos, face)
                    ? FaceState.SATISFIED : FaceState.PROMPT;
        }
        if (coupler != null && coupler.gridLinkCount() > 0) {
            return FaceState.SATISFIED;
        }
        final BlockPos at = couplerPos.relative(face);
        if (level.getBlockEntity(at) instanceof FeGridMember) {
            return FaceState.SATISFIED;
        }
        return level.getCapability(Capabilities.EnergyStorage.BLOCK, at, face.getOpposite()) != null
                ? FaceState.SATISFIED : FaceState.PROMPT;
    }

    public static void tick() {
        hudLine = null;
        LIVE_SLOTS.clear();
        final Minecraft mc = Minecraft.getInstance();
        final LocalPlayer player = mc.player;
        final ClientLevel level = mc.level;
        if (player == null || level == null) {
            clear();
            return;
        }
        final ItemStack held = wiringInHand(player);
        if (held == null) {
            clear();
            return;
        }
        final Face primary = emphasis(held);
        final Face secondary = emphasisAlso(held);
        final BlockPos aimed = mc.hitResult instanceof BlockHitResult hit ? hit.getBlockPos() : null;
        final BlockPos anchor = ForeignAnchorReader.peek(held);

        for (UniversalPowerCouplerBE coupler : nearby(level, player.blockPosition())) {
            final BlockPos pos = coupler.getBlockPos();
            final boolean looking = pos.equals(aimed);
            socket(level, coupler, CouplerSockets.End.FOREIGN, coupler.foreignFace(),
                    primary == Face.FOREIGN || secondary == Face.FOREIGN, looking, anchor);
            socket(level, coupler, CouplerSockets.End.GRID, coupler.gridFace(),
                    primary == Face.GRID, looking, null);
            if (looking) {
                hudLine = describe(level, coupler);
            }
        }
        retire();
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

    private static void socket(ClientLevel level, UniversalPowerCouplerBE coupler,
                               CouplerSockets.End end, Direction face, boolean armed,
                               boolean looking, @Nullable BlockPos anchor) {
        final BlockPos pos = coupler.getBlockPos();
        final AABB box = CouplerSockets.world(coupler.getBlockState(), pos, end);
        if (box == null) {
            return;
        }
        final boolean foreign = end == CouplerSockets.End.FOREIGN;
        final boolean satisfied =
                faceState(level, pos, face, foreign) == FaceState.SATISFIED;

        final float grow;
        final float width;
        final int colour;
        if (satisfied) {
            grow = GROW_SATISFIED;
            width = LINE_IDLE;
            colour = GREEN;
        } else if (!armed) {
            grow = GROW_IDLE;
            width = LINE_IDLE;
            colour = foreign ? AMBER : CYAN;
        } else {
            final float breath = Mth.sin(
                    (level.getGameTime() % 200L) * 0.0314f) * 0.5f + 0.5f;
            grow = (looking ? GROW_AIMED : 0.0f) + breath * GROW_BREATH;
            width = looking ? LINE_AIMED : LINE_ARMED;
            colour = foreign ? AMBER : CYAN;
        }

        final Object slot = slot(pos, end, "box");
        Outliner.getInstance().chaseAABB(slot, box.inflate(grow))
                .colored(colour)
                .lineWidth(width)

                .withFaceTexture(armed && !satisfied ? AllSpecialTextures.SELECTION : null)
                .highlightFace(armed && !satisfied ? face : null)
                .disableLineNormals();

        if (anchor == null || !foreign || satisfied) {
            return;
        }

        final Vec3 from = Vec3.atCenterOf(anchor);
        final Vec3 to = CouplerSockets.anchor(coupler.getBlockState(), end)
                .add(pos.getX(), pos.getY(), pos.getZ());
        final float paid = Mth.clamp((level.getGameTime() % 60L) / 24.0f, 0.0f, 1.0f);
        Outliner.getInstance().endChasingLine(slot(pos, end, "line"), from, to, paid, true)
                .colored(AMBER)
                .lineWidth(1 / 24f)
                .disableLineNormals();
    }

    private static Component describe(ClientLevel level, UniversalPowerCouplerBE coupler) {
        final BlockPos pos = coupler.getBlockPos();
        final boolean foreignDone =
                faceState(level, pos, coupler.foreignFace(), true) == FaceState.SATISFIED;
        final boolean gridDone =
                faceState(level, pos, coupler.gridFace(), false) == FaceState.SATISFIED;
        if (foreignDone && gridDone) {
            return Component.translatable("create_stats.universal_power_coupler.hint.both");
        }
        if (foreignDone) {
            return Component.translatable("create_stats.universal_power_coupler.hint.needs_grid");
        }
        if (gridDone) {
            return Component.translatable("create_stats.universal_power_coupler.hint.needs_foreign");
        }
        return Component.translatable("create_stats.universal_power_coupler.hint.needs_both");
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

    private static Object slot(BlockPos pos, CouplerSockets.End end, String kind) {
        final Object key = "coupler_hint:" + pos.asLong() + ':' + end + ':' + kind;
        LIVE_SLOTS.add(key);
        return key;
    }

    @Nullable
    private static ItemStack wiringInHand(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            final ItemStack stack = player.getItemInHand(hand);
            if (emphasis(stack) != null) {
                return stack;
            }
        }
        return null;
    }

    private static List<UniversalPowerCouplerBE> nearby(ClientLevel level, BlockPos centre) {
        final List<UniversalPowerCouplerBE> found = new ArrayList<>(2);
        for (BlockPos p : BlockPos.betweenClosed(centre.offset(-RADIUS, -RADIUS, -RADIUS),
                centre.offset(RADIUS, RADIUS, RADIUS))) {
            if (level.getBlockState(p).getBlock() instanceof UniversalPowerCouplerBlock
                    && level.getBlockEntity(p) instanceof UniversalPowerCouplerBE coupler) {
                found.add(coupler);
            }
        }
        return found;
    }
}
