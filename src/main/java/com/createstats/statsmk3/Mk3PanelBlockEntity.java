package com.createstats.statsmk3;

import com.createstats.blockentity.StatsDisplayPanelBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class Mk3PanelBlockEntity extends StatsDisplayPanelBE implements com.createstats.util.CopyableSettings {
    private static final int LAYOUT_CHECK_TICKS = 20;

    private final Mk3Settings mk3 = new Mk3Settings();
    private int layoutCheck;

    @org.jetbrains.annotations.Nullable
    private CompoundTag printed;

    private static final long CLIENT_EDIT_HOLD_NANOS = 1_000_000_000L;

    private long clientEditHoldUntil;

    public Mk3PanelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public Mk3Settings mk3Settings() {
        return this.mk3;
    }

    @Override
    public String rsFocusedItemId() {
        return this.mk3.rsFocusItem;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        settlePrint();
        if (!isController()) {
            return;
        }
        if (--this.layoutCheck > 0) {
            return;
        }
        this.layoutCheck = LAYOUT_CHECK_TICKS;

        final java.util.List<DataChannel> channels = getVisibleChannels();
        if (this.mk3.ensureLayout(layoutCols(), layoutRows(), channels == null ? 0 : channels.size())) {
            markDirtyAndSync();
        }
    }

    public int layoutCols() {
        return Mk3Settings.colsFor(getMultiblockWidth());
    }

    public int layoutRows() {
        return Mk3Settings.rowsFor(getMultiblockHeight());
    }

    @Override
    public AABB getRenderBoundingBox() {
        if (!isController()) {
            return new AABB(getBlockPos());
        }
        final BlockPos origin = getControllerPos() != null ? getControllerPos() : getBlockPos();
        final BlockState state = getBlockState();
        final Direction facing = state.hasProperty(HorizontalDirectionalBlock.FACING)
                ? state.getValue(HorizontalDirectionalBlock.FACING)
                : Direction.NORTH;
        return Mk3RenderBounds.assembly(origin, getMultiblockWidth(), getMultiblockHeight(), facing);
    }

    public String cycleTimeRange() {
        final int next = (Mk3Settings.timeRangeIndex(this.mk3.timeRangeSeconds) + 1)
                % Mk3Settings.TIME_RANGES.length;
        this.mk3.timeRangeSeconds = Mk3Settings.TIME_RANGES[next];
        this.markDirtyAndSync();
        return Mk3Settings.TIME_RANGE_LABELS[next];
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        this.mk3.save(tag);
        if (this.printed != null) {
            tag.put("Mk3Print", this.printed.copy());
        }
    }

    @Override
    public String settingsKey() {
        return "create_stats:stats_display_mk3";
    }

    @Override
    public com.createstats.util.CopyableSettings settingsHolder() {
        return getControllerBE() instanceof Mk3PanelBlockEntity controller ? controller : this;
    }

    @Override
    public void writeSettings(CompoundTag tag, HolderLookup.Provider registries) {
        this.mk3.save(tag);
    }

    @Override
    public boolean applySettings(CompoundTag tag, HolderLookup.Provider registries,
                                 @org.jetbrains.annotations.Nullable net.minecraft.world.entity.player.Player player,
                                 boolean simulate) {
        if (!tag.contains("Mk3")) {
            return false;
        }
        final Mk3Settings incoming = new Mk3Settings();
        incoming.load(tag);
        final int lost = incoming.layout.tilesLostFitting(layoutCols(), layoutRows());
        if (lost > 0) {
            if (!simulate) {
                com.createstats.util.CopyableSettings.deny(player, net.minecraft.network.chat.Component.translatable(
                        "create_stats.clipboard.layout_lost", lost, incoming.layout.size()));
            }
            return false;
        }
        if (simulate || alreadyHolds(tag, registries)) {
            return true;
        }
        this.mk3.load(tag);
        final java.util.List<DataChannel> channels = getVisibleChannels();
        this.mk3.ensureLayout(layoutCols(), layoutRows(), channels == null ? 0 : channels.size());

        this.printed = null;
        markDirtyAndSync();
        return true;
    }

    @Override
    public void writeFreshDefaults(CompoundTag tag) {
        tag.putBoolean("IsController", true);
    }

    // schematic face waits, the cannon builds walls row by row and the refit drops tiles it can't bring back
    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        writeFreshDefaults(tag);
        if (isMultiblockMergeDisabled()) {
            tag.putBoolean("MergeDisabled", true);
        }
        if (isController()) {
            final CompoundTag print = new CompoundTag();
            this.mk3.save(print);
            print.putInt("W", getMultiblockWidth());
            print.putInt("H", getMultiblockHeight());
            tag.put("Mk3Print", print);
        }
    }

    private void settlePrint() {
        if (this.printed == null) {
            return;
        }
        if (!(getControllerBE() instanceof Mk3PanelBlockEntity controller)
                || controller.getMultiblockWidth() != this.printed.getInt("W")
                || controller.getMultiblockHeight() != this.printed.getInt("H")) {
            return;
        }
        controller.mk3.load(this.printed);
        final java.util.List<DataChannel> channels = controller.getVisibleChannels();
        controller.mk3.ensureLayout(controller.layoutCols(), controller.layoutRows(),
                channels == null ? 0 : channels.size());
        this.printed = null;
        setChanged();
        controller.markDirtyAndSync();
    }

    public void holdClientEdits() {
        if (this.level != null && this.level.isClientSide()) {
            this.clientEditHoldUntil = System.nanoTime() + CLIENT_EDIT_HOLD_NANOS;
        }
    }

    private boolean clientEditsHeld() {
        return this.level != null && this.level.isClientSide()
                && System.nanoTime() - this.clientEditHoldUntil < 0L;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (!clientEditsHeld()) {
            this.mk3.load(tag);
        }

        this.printed = tag.contains("Mk3Print") ? tag.getCompound("Mk3Print").copy() : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        this.mk3.save(tag);
        return tag;
    }
}
