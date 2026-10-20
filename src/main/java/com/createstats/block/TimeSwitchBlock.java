package com.createstats.block;

import com.createstats.blockentity.BlastShutterBE;
import com.createstats.blockentity.SwitchBE;
import com.createstats.power.FeGoggle;
import com.createstats.registry.ModSounds;
import com.createstats.timeswitch.TimeSwitchDial;
import com.createstats.timeswitch.TimeSwitchProgram;
import com.createstats.timeswitch.TimeSwitchProgram.Mode;
import com.createstats.timeswitch.TimeSwitchProgram.Preset;
import com.createstats.timeswitch.TimeSwitchText;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class TimeSwitchBlock extends SwitchBlock {
    private static final float PIN_VOLUME = 0.42f;
    private static final float DETENT_VOLUME = 0.46f;
    private static final float CONTACT_VOLUME = 0.40f;
    private static final float SWEEP_VOLUME = 0.40f;

    public TimeSwitchBlock(Properties properties) {
        super(properties, LampShapes.TIME_SWITCH, false, Click.ROCKER);
    }

    @Override
    public void tickSwitch(Level level, BlockPos pos, BlockState state, SwitchBE be) {
        keepTime(level, pos, state, be);
    }

    private void keepTime(Level level, BlockPos pos, BlockState state, SwitchBE be) {
        final boolean want = TimeSwitchProgram.closed(level.getDayTime(), be.timePins(), be.timeMode());
        if (want != isOn(state)) {
            setContacts(level, pos, state, POWERED, want);
        }
    }

    @Override
    protected void playContacts(Level level, BlockPos pos, boolean closed) {
        level.playSound(null, pos, ModSounds.TIME_SWITCH_CONTACT.get(), SoundSource.BLOCKS, CONTACT_VOLUME,
                closed ? 1.0f : 0.84f);
    }

    public void setPins(Level level, BlockPos pos, int mask, boolean in) {
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof SwitchBE be)) {
            return;
        }
        final int before = be.timePins();
        if (!be.setTimePins(TimeSwitchProgram.set(before, mask, in))) {
            return;
        }
        final int moved = TimeSwitchProgram.count(before ^ be.timePins());
        final float pitch = (in ? 1.04f : 0.92f) + (level.random.nextFloat() - 0.5f) * 0.08f;
        level.playSound(null, pos, in ? ModSounds.TIME_SWITCH_PIN_IN.get() : ModSounds.TIME_SWITCH_PIN_OUT.get(),
                SoundSource.BLOCKS, Math.min(0.7f, PIN_VOLUME + 0.05f * (moved - 1)), pitch);
        keepTime(level, pos, level.getBlockState(pos), be);
    }

    public void setMode(Level level, BlockPos pos, Mode mode) {
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof SwitchBE be)) {
            return;
        }
        final Mode before = be.timeMode();
        if (!be.setTimeMode(mode)) {
            return;
        }
        detent(level, pos, mode);
        if ((before == Mode.ON && mode == Mode.OFF) || (before == Mode.OFF && mode == Mode.ON)) {
            level.scheduleTick(pos, this, 2);
        }
        keepTime(level, pos, level.getBlockState(pos), be);
    }

    private static void detent(Level level, BlockPos pos, Mode mode) {
        level.playSound(null, pos, ModSounds.TIME_SWITCH_DETENT.get(), SoundSource.BLOCKS, DETENT_VOLUME,
                mode == Mode.AUTO ? 1.0f : mode == Mode.ON ? 1.08f : 0.9f);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        detent(level, pos, Mode.AUTO);
    }

    public void applyPreset(Level level, BlockPos pos, Preset preset) {
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof SwitchBE be)) {
            return;
        }
        final int next = TimeSwitchProgram.preset(preset, be.timePins(), BlastShutterBE.DUSK, BlastShutterBE.DAWN);
        if (!be.setTimePins(next)) {
            return;
        }
        level.playSound(null, pos, ModSounds.TIME_SWITCH_SWEEP.get(), SoundSource.BLOCKS, SWEEP_VOLUME, 1.0f);
        keepTime(level, pos, level.getBlockState(pos), be);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof SwitchBE be)) {
            return InteractionResult.PASS;
        }
        final TimeSwitchDial.Aim aim = aim(state, pos, player.getEyePosition(), player.getViewVector(1.0f),
                be.timeMode(), TimeSwitchProgram.hourOf((double) level.getDayTime()));
        switch (aim.kind()) {
            case PIN -> setPins(level, pos, TimeSwitchProgram.bit(aim.index()),
                    !TimeSwitchProgram.isIn(be.timePins(), aim.index()));
            case MODE -> setMode(level, pos, Mode.byId(aim.index()));
            default -> player.displayClientMessage(TimeSwitchText.status(isOn(state), be.timePins(),
                    be.timeMode(), level.getDayTime()).copy().withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResult.CONSUME;
    }

    public static TimeSwitchDial.Aim aim(BlockState state, BlockPos pos, Vec3 eye, Vec3 look, Mode mode,
                                         double hourF) {
        final Direction facing = state.getValue(FACING);
        final double[] o = TimeSwitchMount.toAuthored(facing, eye.x - pos.getX(), eye.y - pos.getY(),
                eye.z - pos.getZ(), true);
        final double[] d = TimeSwitchMount.toAuthored(facing, look.x, look.y, look.z, false);
        return TimeSwitchDial.aim(o[0] * 16.0, o[1] * 16.0, o[2] * 16.0, d[0], d[1], d[2],
                TimeSwitchDial.dialDegrees(hourF), mode);
    }

    @Override
    public boolean addGoggleLines(SwitchBE be, List<Component> tooltip, boolean isPlayerSneaking) {
        final Level level = be.getLevel();
        if (level == null) {
            return false;
        }
        final long day = level.getDayTime();
        final boolean closed = be.isClosed();
        final int pins = be.timePins();
        final Mode mode = be.timeMode();
        tooltip.add(FeGoggle.title(Component.translatable("block.create_stats.time_switch")));
        tooltip.add(closed ? FeGoggle.ok(Component.translatable("create_stats.time_switch.closed").getString())
                : FeGoggle.warn(Component.translatable("create_stats.time_switch.open").getString()));
        tooltip.add(FeGoggle.dim(TimeSwitchText.why(closed, pins, mode, day).getString()));
        tooltip.add(FeGoggle.dim(TimeSwitchText.program(pins).getString()));
        final Component next = TimeSwitchText.next(closed, pins, mode, day);
        if (next != null) {
            tooltip.add(FeGoggle.dim(next.getString()));
        }
        tooltip.add(FeGoggle.dim(Component.translatable("create_stats.time_switch.now",
                TimeSwitchText.now(day), TimeSwitchText.mode(mode)).getString()));
        if (!level.dimensionType().natural()) {
            tooltip.add(FeGoggle.dim(Component.translatable("create_stats.time_switch.overworld_time").getString()));
        }
        if (!level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
            tooltip.add(FeGoggle.warn(Component.translatable("create_stats.time_switch.frozen").getString()));
        }
        final int links = be.linkedFixtures().size();
        tooltip.add(FeGoggle.dim(links == 0
                ? Component.translatable("create_stats.time_switch.linked.none").getString()
                : Component.translatable("create_stats.time_switch.linked", links).getString()));
        if (closed) {
            tooltip.add(FeGoggle.capacity(be.transferRate()));
        }
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        final List<Component> specs = new java.util.ArrayList<>();
        final net.minecraft.world.item.component.CustomData data =
                stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
        if (data != null) {
            final net.minecraft.nbt.CompoundTag tag = data.copyTag();
            if (tag.contains("TimePins") || tag.contains("TimeMode")) {
                final int pins = tag.getInt("TimePins") & TimeSwitchProgram.ALL;
                final Mode mode = Mode.byId(tag.contains("TimeMode") ? tag.getByte("TimeMode") : 0);
                specs.add(com.createstats.util.CreateStatsTooltipHelper.spec(
                        "block.create_stats.time_switch.tooltip.program", TimeSwitchText.program(pins),
                        TimeSwitchText.mode(mode)));
            }
        }
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip, stack.getItem().getDescriptionId(), specs);
    }
}
