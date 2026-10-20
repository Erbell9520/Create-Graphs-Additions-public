package com.createstats.blockentity;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

// the settings object is the truth, a stock ScrollValue keeps a second copy and they drift
public class SettingsValueBox extends ScrollValueBehaviour {
    private final BehaviourType<?> type;
    private final int netId;
    private final String clipboardKey;
    private final int maxValue;
    private final int milestone;
    private final List<Component> rows;
    private final Supplier<ValueSettings> read;
    private final BiConsumer<Player, ValueSettings> apply;
    private final Function<ValueSettings, MutableComponent> format;
    private final Supplier<String> display;

    public SettingsValueBox(Component label, SmartBlockEntity be, ValueBoxTransform slot,
                            BehaviourType<?> type, int netId, String clipboardKey,
                            int maxValue, int milestone, List<Component> rows,
                            Supplier<ValueSettings> read, BiConsumer<Player, ValueSettings> apply,
                            Function<ValueSettings, MutableComponent> format, Supplier<String> display) {
        super(label, be, slot);
        this.type = type;
        this.netId = netId;
        this.clipboardKey = clipboardKey;
        this.maxValue = maxValue;
        this.milestone = milestone;
        this.rows = rows;
        this.read = read;
        this.apply = apply;
        this.format = format;
        this.display = display;
    }

    @Override
    public BehaviourType<?> getType() {
        return type;
    }

    @Override
    public int netId() {
        return netId;
    }

    @Override
    public String getClipboardKey() {
        return clipboardKey;
    }

    @Override
    public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
        return new ValueSettingsBoard(label, maxValue, milestone, rows, new ValueSettingsFormatter(format));
    }

    @Override
    public ValueSettings getValueSettings() {
        return read.get();
    }

    @Override
    public void setValueSettings(Player player, ValueSettings valueSetting, boolean ctrlDown) {
        if (valueSetting.equals(getValueSettings())) {
            return;
        }
        playFeedbackSound(this);
        apply.accept(player, valueSetting);
    }

    @Override
    public String formatValue() {
        return display.get();
    }

    @Override
    public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
    }

    @Override
    public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
    }
}
