package com.createstats.display;

import com.createstats.battle.BattleObserverSnapshot;
import com.createstats.blockentity.BattleScreenDisplayBE;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BattleScreenDisplaySource extends DisplaySource implements StatsLinkSource {

    /** NBT key under DisplayLinkContext.sourceConfig() for the V1/V2 mode selector. */
    public static final String MODE_TAG_KEY = "BattleMode";

    /** Read the configured mode from the Display Link source config. 0 = V1, 1 = V2. */
    public static int readMode(DisplayLinkContext context) {
        if (context == null) {
            return 0;
        }
        final CompoundTag cfg = context.sourceConfig();
        if (cfg == null || !cfg.contains(MODE_TAG_KEY)) {
            return 0;
        }
        return cfg.getInt(MODE_TAG_KEY) <= 0 ? 0 : 1;
    }

    @Override
    public void initConfigurationWidgets(DisplayLinkContext context, ModularGuiLineBuilder builder, boolean isFirstLine) {
        if (!isFirstLine) {
            return;
        }
        builder.addSelectionScrollInput(0, 137, (input, label) -> {
            input.forOptions(List.of(
                            Component.translatable("create_stats.display_source.battle_screen.mode.v1"),
                            Component.translatable("create_stats.display_source.battle_screen.mode.v2")))
                    .titled(Component.translatable("create_stats.display_source.battle_screen.mode").copy());
        }, MODE_TAG_KEY);
    }


    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        BlockEntity blockEntity = context.getSourceBlockEntity();
        if (!(blockEntity instanceof BattleScreenDisplayBE display)) {
            return EMPTY;
        }

        List<BattleObserverSnapshot> snapshots = display.getBattleFeedSnapshots();
        int maxRows = Math.max(1, stats.maxRows());
        List<MutableComponent> lines = new ArrayList<>();
        this.addLine(lines, maxRows, Component.translatable(snapshots.isEmpty()
                ? "create_stats.battle_screen.display.no_observers"
                : "create_stats.battle_screen.display.live_feed"));
        if (snapshots.isEmpty()) {
            return lines;
        }

        int observerCount = snapshots.size();
        int weaponCount = 0;
        int totalMissing = 0;
        int totalNew = 0;
        double totalHealth = 0.0;
        for (BattleObserverSnapshot snapshot : snapshots) {
            totalHealth += snapshot.healthPercent();
            totalMissing += snapshot.missingBlocks();
            totalNew += snapshot.newBlocks();
            if (snapshot.weapon().detected()) {
                weaponCount++;
            }
        }

        this.addLine(lines, maxRows, Component.translatable("create_stats.battle_screen.display.observers", observerCount, weaponCount));
        this.addLine(lines, maxRows, Component.translatable("create_stats.battle_screen.display.integrity",
                String.format(Locale.ROOT, "%.0f", totalHealth / observerCount), totalMissing));
        this.addLine(lines, maxRows, Component.translatable("create_stats.battle_screen.display.added", totalNew));
        if (maxRows >= 5) {
            BattleObserverSnapshot hottest = null;
            for (BattleObserverSnapshot snapshot : snapshots) {
                if (hottest == null || snapshot.missingBlocks() > hottest.missingBlocks()) {
                    hottest = snapshot;
                }
            }
            if (hottest != null) {
                this.addLine(lines, maxRows, Component.translatable("create_stats.battle_screen.display.priority",
                        hottest.name(), hottest.category().getDisplayName()));
            }
        }
        return lines;
    }

    private void addLine(List<MutableComponent> lines, int maxRows, MutableComponent text) {
        if (lines.size() < maxRows) {
            lines.add(text);
        }
    }

    @Override
    protected String getTranslationKey() {
        return "battle_screen";
    }

    @Override
    public int getPassiveRefreshTicks() {
        return 5;
    }
}