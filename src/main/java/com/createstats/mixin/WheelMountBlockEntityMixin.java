package com.createstats.mixin;

import com.createstats.DebugLogging;
import com.createstats.flight.runtime.WheelMountSteeringOverrides;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlockEntity", remap = false)
public abstract class WheelMountBlockEntityMixin {

    @Unique
    private static long createStats$lastOverrideLogTick = Long.MIN_VALUE;
    @Unique
    private static long createStats$lastOverrideMissLogTick = Long.MIN_VALUE;

    @Shadow private int lastServerSteeringSignal;
    @Shadow private int lastServerSteeringSignalLeft;
    @Shadow private int lastServerSteeringSignalRight;
    @Shadow protected int clientSteeringSignalLeft;
    @Shadow protected int clientSteeringSignalRight;
    @Shadow private int clientSteeringSignal;

    @Inject(method = "getSteeringSignal", at = @At("HEAD"), cancellable = true)
    private void createStats$injectFccSteeringSignal(CallbackInfoReturnable<Integer> cir) {
        BlockEntity blockEntity = (BlockEntity) (Object) this;
        Level level = blockEntity.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        BlockPos pos = blockEntity.getBlockPos();
        long now = level.getGameTime();
        WheelMountSteeringOverrides.Entry entry = WheelMountSteeringOverrides.getForTick(pos, now);
        if (entry == null) {
            if (createStats$lastOverrideMissLogTick == Long.MIN_VALUE || now - createStats$lastOverrideMissLogTick >= 20L) {
                createStats$lastOverrideMissLogTick = now;
                WheelMountSteeringOverrides.Entry peek = WheelMountSteeringOverrides.peek(pos);
                if (peek == null) {
                    DebugLogging.fccInfo("[FCC/WHEEL_OVERRIDE_MISS] pos={} tick={} reason=no-entry", pos, now);
                } else {
                    DebugLogging.fccInfo("[FCC/WHEEL_OVERRIDE_MISS] pos={} tick={} reason=tick-mismatch entryTick={} left={} right={}",
                            pos, now, peek.tick(), peek.leftSignal(), peek.rightSignal());
                }
            }
            return;
        }

        int left = entry.leftSignal();
        int right = entry.rightSignal();
        int combined = left - right;

        boolean changed = combined != lastServerSteeringSignal
                || left != lastServerSteeringSignalLeft
                || right != lastServerSteeringSignalRight;

        lastServerSteeringSignal = combined;
        lastServerSteeringSignalLeft = left;
        lastServerSteeringSignalRight = right;

        // Keep client-facing cached values aligned with server override for smooth display.
        clientSteeringSignal = combined;
        clientSteeringSignalLeft = left;
        clientSteeringSignalRight = right;

        if (changed && level != null) {
            // Mark dirty so normal BE sync path can propagate updated steering values.
            blockEntity.setChanged();
        }
        if (now - createStats$lastOverrideLogTick >= 20L) {
            createStats$lastOverrideLogTick = now;
            DebugLogging.fccInfo("[FCC/WHEEL_OVERRIDE] pos={} left={} right={} combined={}", pos, left, right, combined);
        }
        cir.setReturnValue(combined);
    }
}
