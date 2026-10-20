package com.createstats.client.wire;

import com.createstats.block.PowerConnectorBlock;
import com.createstats.blockentity.PowerConnectorBE;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

@EventBusSubscriber(modid = "create_stats", value = Dist.CLIENT)
public final class WireTensionHold {
    public static final int HOLD_TICKS = 6;

    @Nullable
    private static BlockPos holding;
    private static int held;

    private static boolean armed = true;

    private WireTensionHold() {
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
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK
                || !(mc.level.getBlockState(blockHit.getBlockPos()).getBlock() instanceof PowerConnectorBlock)) {
            reset();
            return;
        }
        final BlockPos pos = blockHit.getBlockPos();
        if (!pos.equals(holding)) {
            holding = pos.immutable();
            held = 0;
        }
        if (++held != HOLD_TICKS) {
            return;
        }
        armed = false;
        if (!(mc.level.getBlockEntity(pos) instanceof PowerConnectorBE be)) {
            return;
        }
        if (be.links().isEmpty()) {
            mc.player.displayClientMessage(Component.translatable("create_stats.wire_tension.no_wires"), true);
            return;
        }
        mc.setScreen(new WireTensionScreen(be));
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
