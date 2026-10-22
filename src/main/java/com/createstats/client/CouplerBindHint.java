package com.createstats.client;

import com.createstats.battle.world.SablePlots;
import com.createstats.blockentity.UniversalPowerCouplerBE;
import com.createstats.compat.ForeignAnchorReader;
import com.createstats.compat.ForeignSpools;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.power.CouplerFault;
import com.createstats.power.CouplerSockets;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
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
public final class CouplerBindHint {
    private CouplerBindHint() {
    }

    public enum Face { FOREIGN, GRID }

    public enum FaceState { PROMPT, SATISFIED, FAULT }

    private static final int AMBER = 0xE0A44C;

    private static final int CYAN = 0x6BE4FF;

    private static final int GREEN = 0x63D68A;

    private static final int RED = 0xE5533D;

    private static final int PALE = 0xD8D2C4;

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
    private static int hudColour = AMBER;

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
        return held != null && held.getItem() instanceof com.createstats.item.CopperWireItem ? Face.FOREIGN : null;
    }

    public static FaceState faceState(UniversalPowerCouplerBE coupler, CouplerSockets.End end) {
        final CouplerFault fault = coupler.fault();
        if (end == CouplerSockets.End.FOREIGN) {
            if (fault.blocksFlow() && fault.isTheirSide() && fault != CouplerFault.NO_FOREIGN) {
                return FaceState.FAULT;
            }
            return coupler.foreignEndpoint().present() ? FaceState.SATISFIED : FaceState.PROMPT;
        }
        return coupler.gridConnected() ? FaceState.SATISFIED : FaceState.PROMPT;
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

        for (UniversalPowerCouplerBE coupler : nearby(level, player, aimed)) {
            final boolean looking = coupler.getBlockPos().equals(aimed);
            socket(level, coupler, CouplerSockets.End.FOREIGN, coupler.foreignFace(),
                    primary == Face.FOREIGN || secondary == Face.FOREIGN, looking, anchor);
            socket(level, coupler, CouplerSockets.End.GRID, coupler.gridFace(), primary == Face.GRID, looking, null);
            if (looking) {
                describe(coupler);
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

    private static void socket(ClientLevel level, UniversalPowerCouplerBE coupler, CouplerSockets.End end,
                               Direction face, boolean armed, boolean looking, @Nullable BlockPos anchor) {
        final BlockPos pos = coupler.getBlockPos();
        final AABB local = CouplerSockets.local(coupler.getBlockState(), end);
        if (local == null) {
            return;
        }
        final boolean foreign = end == CouplerSockets.End.FOREIGN;
        final FaceState look = faceState(coupler, end);
        final float breath = Mth.sin((level.getGameTime() % 200L) * 0.0314f) * 0.5f + 0.5f;

        final float grow;
        final float width;
        final int colour;
        final boolean surface;
        if (look == FaceState.SATISFIED) {
            grow = GROW_SATISFIED;
            width = LINE_IDLE;
            colour = GREEN;
            surface = false;
        } else if (look == FaceState.FAULT) {
            grow = (looking ? GROW_AIMED : 0.0f) + breath * GROW_BREATH;
            width = looking ? LINE_AIMED : LINE_ARMED;
            colour = RED;
            surface = true;
        } else if (!armed) {
            grow = GROW_IDLE;
            width = LINE_IDLE;
            colour = foreign ? AMBER : CYAN;
            surface = false;
        } else {
            grow = (looking ? GROW_AIMED : 0.0f) + breath * GROW_BREATH;
            width = looking ? LINE_AIMED : LINE_ARMED;
            colour = foreign ? AMBER : CYAN;
            surface = true;
        }

        final Object slot = slot(pos, end, "box");
        Outliner.getInstance().chaseAABB(slot, worldBox(level, pos, local).inflate(grow))
                .colored(colour)
                .lineWidth(width)

                .withFaceTexture(surface ? AllSpecialTextures.SELECTION : null)
                .highlightFace(surface ? face : null)
                .disableLineNormals();

        if (anchor == null || !foreign || look == FaceState.SATISFIED) {
            return;
        }

        final Vec3 from = SableBridge.projectOutOfSubLevel(level, Vec3.atCenterOf(anchor));
        final Vec3 to = SableBridge.projectOutOfSubLevel(level,
                CouplerSockets.anchor(coupler.getBlockState(), end).add(pos.getX(), pos.getY(), pos.getZ()));
        final float paid = Mth.clamp((level.getGameTime() % 60L) / 24.0f, 0.0f, 1.0f);
        Outliner.getInstance().endChasingLine(slot(pos, end, "line"), from, to, paid, true)
                .colored(AMBER)
                .lineWidth(1 / 24f)
                .disableLineNormals();
    }

    private static AABB worldBox(Level level, BlockPos pos, AABB local) {
        final AABB plot = local.move(pos.getX(), pos.getY(), pos.getZ());
        double x0 = Double.MAX_VALUE;
        double y0 = Double.MAX_VALUE;
        double z0 = Double.MAX_VALUE;
        double x1 = -Double.MAX_VALUE;
        double y1 = -Double.MAX_VALUE;
        double z1 = -Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            final Vec3 c = SableBridge.projectOutOfSubLevel(level, new Vec3((i & 1) == 0 ? plot.minX : plot.maxX,
                    (i & 2) == 0 ? plot.minY : plot.maxY, (i & 4) == 0 ? plot.minZ : plot.maxZ));
            x0 = Math.min(x0, c.x);
            y0 = Math.min(y0, c.y);
            z0 = Math.min(z0, c.z);
            x1 = Math.max(x1, c.x);
            y1 = Math.max(y1, c.y);
            z1 = Math.max(z1, c.z);
        }
        return new AABB(x0, y0, z0, x1, y1, z1);
    }

    private static void describe(UniversalPowerCouplerBE coupler) {
        final CouplerFault fault = coupler.fault();
        if (!coupler.foreignEndpoint().present() && !coupler.gridConnected()
                && (fault == CouplerFault.NO_FOREIGN || fault == CouplerFault.NO_GRID)) {
            hudLine = Component.translatable("create_stats.universal_power_coupler.hint.needs_both");
            hudColour = AMBER;
            return;
        }
        if (!fault.isFault()) {
            hudLine = Component.translatable("create_stats.universal_power_coupler.hint.both");
            hudColour = GREEN;
            return;
        }
        hudLine = Component.translatable(fault.langKey());
        hudColour = fault.isTheirSide() ? AMBER : fault.isOurSide() ? CYAN
                : fault == CouplerFault.LOOPED ? RED : PALE;
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
        graphics.drawString(mc.font, text, x, y, 0xFF000000 | hudColour, true);
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

    private static List<UniversalPowerCouplerBE> nearby(ClientLevel level, Player player, @Nullable BlockPos aimed) {
        final List<UniversalPowerCouplerBE> found = new ArrayList<>(4);
        final Vec3 at = player.position();
        final double reach = (double) RADIUS * RADIUS;
        final BlockPos centre = player.blockPosition();
        for (int cx = (centre.getX() - RADIUS) >> 4; cx <= (centre.getX() + RADIUS) >> 4; cx++) {
            for (int cz = (centre.getZ() - RADIUS) >> 4; cz <= (centre.getZ() + RADIUS) >> 4; cz++) {
                if (level.getChunkSource().getChunk(cx, cz, false) instanceof LevelChunk chunk) {
                    collect(chunk, found, be -> be.getBlockPos().distToCenterSqr(at) <= reach);
                }
            }
        }
        for (Object craft : SableBridge.listSubLevels(level)) {
            final AABB bounds = SablePlots.worldBounds(craft);
            if (bounds == null || !bounds.inflate(RADIUS).contains(at)) {
                continue;
            }
            for (LevelChunk chunk : SablePlots.plotChunks(craft)) {
                collect(chunk, found, be -> SableBridge.projectOutOfSubLevel(level,
                        Vec3.atCenterOf(be.getBlockPos())).distanceToSqr(at) <= reach);
            }
        }
        if (aimed != null && level.getBlockEntity(aimed) instanceof UniversalPowerCouplerBE coupler
                && !found.contains(coupler)) {
            found.add(coupler);
        }
        return found;
    }

    private static void collect(LevelChunk chunk, List<UniversalPowerCouplerBE> found,
                                java.util.function.Predicate<UniversalPowerCouplerBE> near) {
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof UniversalPowerCouplerBE coupler && !coupler.isRemoved() && near.test(coupler)
                    && !found.contains(coupler)) {
                found.add(coupler);
            }
        }
    }
}
