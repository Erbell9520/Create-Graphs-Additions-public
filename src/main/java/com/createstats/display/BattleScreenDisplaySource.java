package com.createstats.display;

import com.createstats.battle.core.BattleReport;
import com.createstats.battle.core.HeaderStatus;
import com.createstats.battle.world.BattleText;
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

public class BattleScreenDisplaySource extends DisplaySource implements StatsLinkSource {
    public static final String MODE_TAG_KEY = "BattleMode";

    public static int readMode(DisplayLinkContext context) {
        if (context == null) return 0;
        CompoundTag cfg = context.sourceConfig();
        if (cfg == null || !cfg.contains(MODE_TAG_KEY)) return 0;
        return cfg.getInt(MODE_TAG_KEY) <= 0 ? 0 : 1;
    }

    @Override
    public void initConfigurationWidgets(DisplayLinkContext context, ModularGuiLineBuilder builder, boolean isFirstLine) {
        if (!isFirstLine) return;
        builder.addSelectionScrollInput(0, 137, (input, label) -> input.forOptions(List.of(
                        Component.translatable("create_stats.display_source.battle_screen.mode.v1"),
                        Component.translatable("create_stats.display_source.battle_screen.mode.v2")))
                .titled(Component.translatable("create_stats.display_source.battle_screen.mode").copy()), MODE_TAG_KEY);
    }

    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        BlockEntity be = context.getSourceBlockEntity();
        if (!(be instanceof BattleScreenDisplayBE display)) return EMPTY;
        BattleReport r = display.report();
        long clock = be.getLevel() == null ? r.now() : be.getLevel().getGameTime();
        int maxRows = Math.max(1, stats.maxRows());
        List<MutableComponent> lines = new ArrayList<>();
        MutableComponent name = r.shipName().isEmpty() ? BattleText.tr(r.ground() ? "link.fort" : "link.ship") : Component.literal(r.shipName());
        add(lines, maxRows, r.hasReference() ? BattleText.tr("link.integrity", name, r.integrityPermille() / 10) : BattleText.tr("link.no_survey", name));
        add(lines, maxRows, status(r.status(clock)));
        if (r.lostCount() > 0) add(lines, maxRows, BattleText.tr("link.lost", r.lostCount(), r.placeCount()));
        if (!r.hits().isEmpty()) {
            BattleReport.HitLine h = r.hits().get(0);
            add(lines, maxRows, BattleText.tr("link.last_hit", BattleText.place(h.place()), Math.max(0L, (clock - h.at()) / 20)));
        }
        return lines;
    }

    private static MutableComponent status(HeaderStatus.Status s) {
        return switch (s.kind()) {
            case FIRE -> BattleText.tr("status.fire", BattleText.place(s.place()));
            case UNDER_FIRE -> BattleText.tr("status.under_fire", s.number());
            case SURVEYING -> BattleText.tr("status.surveying", s.number());
            default -> BattleText.tr("status." + s.kind().key());
        };
    }

    private static void add(List<MutableComponent> lines, int maxRows, MutableComponent text) {
        if (lines.size() < maxRows) lines.add(text);
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
