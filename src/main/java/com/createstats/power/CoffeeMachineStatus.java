package com.createstats.power;

import com.createstats.blockentity.CoffeeMachineBE;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class CoffeeMachineStatus {
    private static final float BRIMMED = 0.999f;

    private CoffeeMachineStatus() {
    }

    @Nullable
    public static ApplianceStatus of(CoffeeMachineBE be) {
        if (!be.isOn()) {
            return null;
        }
        if (be.isBrewing()) {
            final int elapsed = (int) Math.min(CoffeeMachineBE.BREW_TICKS, Math.max(0L, be.brewElapsed()));
            return ApplianceStatus.running(be.isPouring()
                            ? Component.translatable("status.create_stats.home.pouring")
                            : Component.translatable("status.create_stats.home.brewing"),
                    CoffeeMachineBE.BREW_TICKS, CoffeeMachineBE.BREW_TICKS - elapsed, doneText(be));
        }
        if (be.isFrothing()) {
            final int left = Math.round((1.0f - be.frothProgress(0.0f)) * CoffeeMachineBE.FROTH_TICKS);
            return ApplianceStatus.running(Component.translatable("status.create_stats.home.frothing"),
                    CoffeeMachineBE.FROTH_TICKS, left, doneText(be));
        }
        if (be.isManagedShed() || (be.ratedDrawFePerTick() > 0 && be.measuredDrawFePerTick() <= 0)) {
            return null;
        }
        if (be.hasMug() && be.isFull()) {
            return ApplianceStatus.idle(be.hasMilk()
                    ? Component.translatable("status.create_stats.home.latte_ready")
                    : Component.translatable("status.create_stats.home.coffee_ready"));
        }

        final CoffeeMachineBE.Refusal blocking = be.checkReady();
        if (blocking == CoffeeMachineBE.Refusal.UNLOCKED) {
            return ApplianceStatus.idle(Component.translatable("status.create_stats.home.portafilter"));
        }
        if (blocking == CoffeeMachineBE.Refusal.NO_BEANS) {
            return ApplianceStatus.idle(Component.translatable("status.create_stats.home.no_beans"));
        }
        if (blocking == CoffeeMachineBE.Refusal.NO_POWER) {
            return ApplianceStatus.idle(Component.translatable("status.create_stats.home.charging"));
        }
        if (!be.hasMug()) {
            return ApplianceStatus.idle(Component.translatable("status.create_stats.home.no_cup"));
        }
        final float fill = be.displayFill(0.0f);
        if (fill > 0.0f) {
            return ApplianceStatus.idle(Component.translatable("status.create_stats.home.cup_part",
                    Math.round(fill * 100.0f)));
        }
        if (be.hasMilk()) {
            return ApplianceStatus.idle(Component.translatable("status.create_stats.home.steamed_milk"));
        }
        return ApplianceStatus.idle(Component.translatable("status.create_stats.home.ready"));
    }

    @Nullable
    private static Component doneText(CoffeeMachineBE be) {
        if (!be.hasMug()) {
            return null;
        }
        final boolean milk = be.hasMilk() || be.isFrothing();
        if (be.isBrewing() && !be.isFull() && be.displayFill(CoffeeMachineBE.BREW_TICKS) >= BRIMMED) {
            return milk
                    ? Component.translatable("status.create_stats.home.latte_ready")
                    : Component.translatable("status.create_stats.home.coffee_ready");
        }
        if (be.isFrothing() && !be.hasMilk() && be.isFull()) {
            return Component.translatable("status.create_stats.home.latte_ready");
        }
        return null;
    }
}
