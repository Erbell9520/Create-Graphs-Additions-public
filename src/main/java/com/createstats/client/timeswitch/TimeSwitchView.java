package com.createstats.client.timeswitch;

import com.createstats.block.TimeSwitchBlock;
import com.createstats.blockentity.SwitchBE;
import com.createstats.timeswitch.TimeSwitchDial;
import com.createstats.timeswitch.TimeSwitchProgram;
import com.createstats.timeswitch.TimeSwitchProgram.Mode;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

@OnlyIn(Dist.CLIENT)
public final class TimeSwitchView {
    private static final int PENDING_TICKS = 20;

    private record Pending(int pins, Mode mode, long until) {
    }

    private static final Map<BlockPos, Pending> PENDING = new HashMap<>();
    private static long ticks;

    @Nullable
    private static BlockPos previewPos;
    private static int previewPins;

    @Nullable
    private static BlockPos aimPos;
    private static TimeSwitchDial.Aim aim = TimeSwitchDial.Aim.NONE;

    private TimeSwitchView() {
    }

    private static final Map<Level, Double> STAGED = new WeakHashMap<>();

    public static void stageClock(Level level, double hour) {
        STAGED.put(level, hour);
    }

    public static double hourF(Level level, float partialTick) {
        final Double staged = STAGED.get(level);
        if (staged != null) {
            return staged;
        }
        double day = level.getDayTime();
        if (level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
            final float perTick = level.getDayTimePerTick();
            day += partialTick * (perTick < 0.0f ? 1.0f : perTick);
        }
        return TimeSwitchProgram.hourOf(day);
    }

    public static int shownPins(SwitchBE be) {
        final Pending p = live(be) ? PENDING.get(be.getBlockPos()) : null;
        return p != null ? p.pins() : be.timePins();
    }

    public static Mode shownMode(SwitchBE be) {
        final Pending p = live(be) ? PENDING.get(be.getBlockPos()) : null;
        return p != null ? p.mode() : be.timeMode();
    }

    public static boolean live(SwitchBE be) {
        return be.getLevel() != null && be.getLevel() == Minecraft.getInstance().level;
    }

    static void sentPins(SwitchBE be, int mask, boolean in) {
        PENDING.put(be.getBlockPos().immutable(), new Pending(TimeSwitchProgram.set(shownPins(be), mask, in),
                shownMode(be), ticks + PENDING_TICKS));
    }

    static void sentMode(SwitchBE be, Mode mode) {
        PENDING.put(be.getBlockPos().immutable(), new Pending(shownPins(be), mode, ticks + PENDING_TICKS));
    }

    static void tick(@Nullable Level level) {
        ticks++;
        if (level == null) {
            PENDING.clear();
            return;
        }
        PENDING.entrySet().removeIf(e -> {
            if (ticks > e.getValue().until() || !level.isLoaded(e.getKey())
                    || !(level.getBlockEntity(e.getKey()) instanceof SwitchBE be)) {
                return true;
            }
            return be.timePins() == e.getValue().pins() && be.timeMode() == e.getValue().mode();
        });
    }

    static void preview(BlockPos pos, int pins) {
        previewPos = pos.immutable();
        previewPins = pins & TimeSwitchProgram.ALL;
    }

    static void clearPreview(BlockPos pos) {
        if (pos.equals(previewPos)) {
            previewPos = null;
        }
    }

    public static int previewFor(SwitchBE be) {
        return live(be) && be.getBlockPos().equals(previewPos) ? previewPins : -1;
    }

    public static TimeSwitchDial.Aim updateAim(SwitchBE be, float partialTick) {
        final Minecraft mc = Minecraft.getInstance();
        final BlockPos pos = be.getBlockPos();
        if (!live(be)) {
            return TimeSwitchDial.Aim.NONE;
        }
        if (mc.player == null || mc.level == null || !(mc.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(pos)) {
            if (pos.equals(aimPos)) {
                aimPos = null;
                aim = TimeSwitchDial.Aim.NONE;
            }
            return TimeSwitchDial.Aim.NONE;
        }
        final BlockState state = be.getBlockState();
        aim = TimeSwitchBlock.aim(state, pos, mc.player.getEyePosition(partialTick),
                mc.player.getViewVector(partialTick), shownMode(be), hourF(mc.level, partialTick));
        aimPos = pos.immutable();
        return aim;
    }

    public static TimeSwitchDial.Aim aimed(BlockPos pos) {
        return pos.equals(aimPos) ? aim : TimeSwitchDial.Aim.NONE;
    }

    static TimeSwitchDial.Aim aimNow(SwitchBE be) {
        final BlockPos pos = be.getBlockPos();
        if (pos.equals(aimPos)) {
            return aim;
        }
        return updateAim(be, 1.0f);
    }
}
