package com.createstats.client.timeswitch;

import com.createstats.block.TimeSwitchBlock;
import com.createstats.blockentity.SwitchBE;
import com.createstats.network.TimeSwitchEditPacket;
import com.createstats.timeswitch.TimeSwitchDial;
import com.createstats.timeswitch.TimeSwitchProgram;
import com.createstats.timeswitch.TimeSwitchProgram.Mode;
import com.createstats.timeswitch.TimeSwitchProgram.Preset;
import com.createstats.timeswitch.TimeSwitchText;
import com.simibubi.create.AllItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

// first slider decides the stroke, so dragging paints
@EventBusSubscriber(modid = "create_stats", value = Dist.CLIENT)
public final class TimeSwitchInput {
    private static final int HOLD_TICKS = 6;

    private static final int HOVER_REFRESH = 40;

    private static final int STATUS_TICKS = 50;

    @Nullable
    private static BlockPos strokePos;

    private static boolean paint;

    private static int lastPin = -1;

    @Nullable
    private static BlockPos holdPos;
    private static int held;

    private static boolean armed = true;

    private static String lastHover = "";
    private static long hoverAt;
    private static long quietUntil;
    private static long ticks;

    private TimeSwitchInput() {
    }

    @SubscribeEvent
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        final SwitchBE be = target(mc);
        if (be == null || !takes(mc)) {
            return;
        }
        event.setCanceled(true);
        if (strokePos != null || holdPos != null || !armed) {
            event.setSwingHand(false);
            return;
        }
        event.setSwingHand(true);
        final BlockPos pos = be.getBlockPos().immutable();
        final Preset picked = TimeSwitchWrenchPanel.picked(pos);
        if (picked != null && AllItems.WRENCH.isIn(mc.player.getMainHandItem())) {
            PacketDistributor.sendToServer(TimeSwitchEditPacket.preset(pos, picked));
            TimeSwitchWrenchPanel.applied();
            return;
        }
        final TimeSwitchDial.Aim aim = TimeSwitchView.aimNow(be);
        switch (aim.kind()) {
            case PIN -> {
                paint = !TimeSwitchProgram.isIn(TimeSwitchView.shownPins(be), aim.index());
                strokePos = pos;
                lastPin = aim.index();
                send(be, TimeSwitchProgram.bit(aim.index()), paint);
            }
            case MODE -> {
                final Mode mode = Mode.byId(aim.index());
                if (mode != TimeSwitchView.shownMode(be)) {
                    TimeSwitchView.sentMode(be, mode);
                    PacketDistributor.sendToServer(TimeSwitchEditPacket.mode(pos, mode));
                }
            }
            case HUB -> {
                holdPos = pos;
                held = 0;
            }
            default -> status(mc, be);
        }
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        ticks++;
        final Minecraft mc = Minecraft.getInstance();
        TimeSwitchView.tick(mc.level);
        if (mc.player == null || mc.level == null) {
            strokePos = null;
            holdPos = null;
            return;
        }
        final boolean down = mc.options.keyUse.isDown();
        if (!down) {
            armed = true;
        }
        if (mc.screen != null) {
            strokePos = null;
            holdPos = null;
            return;
        }
        final SwitchBE be = target(mc);
        stroke(be, down);
        hold(mc, be, down);
        hover(mc, be);
    }

    private static void stroke(@Nullable SwitchBE be, boolean down) {
        if (strokePos == null) {
            return;
        }
        if (!down || be == null || !be.getBlockPos().equals(strokePos)) {
            strokePos = null;
            lastPin = -1;
            return;
        }
        final TimeSwitchDial.Aim aim = TimeSwitchView.aimNow(be);
        if (!aim.isPin()) {
            lastPin = -1;
            return;
        }
        if (aim.index() == lastPin) {
            return;
        }
        final int crossed = lastPin < 0 ? TimeSwitchProgram.bit(aim.index()) : TimeSwitchDial.sweep(lastPin, aim.index());
        final int shown = TimeSwitchView.shownPins(be);
        final int todo = paint ? crossed & ~shown : crossed & shown;
        if (todo != 0) {
            send(be, todo, paint);
        }
        lastPin = aim.index();
    }

    private static void hold(Minecraft mc, @Nullable SwitchBE be, boolean down) {
        if (holdPos == null) {
            return;
        }
        if (be == null || !be.getBlockPos().equals(holdPos)) {
            holdPos = null;
            return;
        }
        if (!down) {
            holdPos = null;
            status(mc, be);
            return;
        }
        if (TimeSwitchView.aimNow(be).kind() != TimeSwitchDial.Kind.HUB) {
            holdPos = null;
            return;
        }
        if (++held >= HOLD_TICKS) {
            holdPos = null;
            armed = false;
            mc.setScreen(new TimeSwitchScreen(be.getBlockPos()));
        }
    }

    private static void hover(Minecraft mc, @Nullable SwitchBE be) {
        if (be == null || !mc.player.getMainHandItem().isEmpty() || ticks < quietUntil) {
            if (be == null) {
                lastHover = "";
            }
            return;
        }
        final TimeSwitchDial.Aim aim = TimeSwitchView.aimNow(be);
        final Component line = switch (aim.kind()) {
            case PIN -> TimeSwitchText.pin(aim.index(),
                    TimeSwitchProgram.isIn(TimeSwitchView.shownPins(be), aim.index()));
            case MODE -> TimeSwitchText.detent(Mode.byId(aim.index()),
                    aim.index() == TimeSwitchView.shownMode(be).id());
            case HUB -> Component.translatable("create_stats.time_switch.hover.hub");
            default -> null;
        };
        if (line == null) {
            lastHover = "";
            return;
        }
        final String text = line.getString();
        if (!text.equals(lastHover) || ticks - hoverAt >= HOVER_REFRESH) {
            mc.player.displayClientMessage(line.copy().withStyle(ChatFormatting.GRAY), true);
            lastHover = text;
            hoverAt = ticks;
        }
    }

    private static void status(Minecraft mc, SwitchBE be) {
        if (mc.player == null || mc.level == null) {
            return;
        }
        mc.player.displayClientMessage(TimeSwitchText.status(be.isClosed(), TimeSwitchView.shownPins(be),
                TimeSwitchView.shownMode(be), mc.level.getDayTime()).copy().withStyle(ChatFormatting.GRAY), true);
        quietUntil = ticks + STATUS_TICKS;
        lastHover = "";
    }

    private static void send(SwitchBE be, int mask, boolean in) {
        TimeSwitchView.sentPins(be, mask, in);
        PacketDistributor.sendToServer(TimeSwitchEditPacket.pins(be.getBlockPos(), mask, in));
    }

    static void ringClosed() {
        armed = false;
        holdPos = null;
    }

    @Nullable
    static SwitchBE target(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.player.isSpectator()
                || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        final BlockPos pos = hit.getBlockPos();
        if (!(mc.level.getBlockState(pos).getBlock() instanceof TimeSwitchBlock)) {
            return null;
        }
        return mc.level.getBlockEntity(pos) instanceof SwitchBE be ? be : null;
    }

    private static boolean takes(Minecraft mc) {
        final ItemStack held = mc.player.getMainHandItem();
        return held.isEmpty() || (AllItems.WRENCH.isIn(held) && !mc.player.isShiftKeyDown());
    }
}
