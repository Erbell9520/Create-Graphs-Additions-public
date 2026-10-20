package com.createstats.client.tuner;

import com.createstats.tuner.TunerHost;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

// right click reaches the server once, so the hold is detected on the client
@EventBusSubscriber(modid = "create_stats", value = Dist.CLIENT)
public final class TunerHold {
    private static final int HOLD_TICKS = 6;

    @Nullable
    private static BlockPos holding;
    private static int held;
    private static boolean armed = true;

    private TunerHold() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) {
            reset();
            return;
        }
        if (!mc.options.keyUse.isDown()) {
            armed = true;
            reset();
            return;
        }
        if (!armed || !mc.player.getMainHandItem().isEmpty() || mc.player.isSpectator()) {
            reset();
            return;
        }
        final HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            reset();
            return;
        }

        final BlockPos pos = TunerHost.hostPos(mc.level, blockHit.getBlockPos());
        if (pos == null || !(mc.level.getBlockEntity(pos) instanceof TunerHost under)
                || !under.tunesAt(blockHit.getBlockPos(), blockHit.getLocation())) {
            reset();
            return;
        }
        if (!pos.equals(holding)) {
            holding = pos.immutable();
            held = 0;
            TunerSurveys.request(holding);
        }
        if (++held != HOLD_TICKS) {
            return;
        }
        armed = false;
        if (mc.level.getBlockEntity(pos) instanceof TunerHost host) {
            final net.minecraft.network.chat.Component lock = host.tunerLock();
            if (lock != null) {
                mc.player.displayClientMessage(lock, true);
                return;
            }
            mc.setScreen(new TunerScreen(pos, host));
        }
    }

    static void ringClosed() {
        armed = false;
        reset();
    }

    private static void reset() {
        holding = null;
        held = 0;
    }
}
