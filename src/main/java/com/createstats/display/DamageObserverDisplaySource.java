package com.createstats.display;

import com.createstats.battle.world.BattleText;
import com.createstats.blockentity.DamageObserverBE;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

public class DamageObserverDisplaySource extends DisplaySource {
    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        BlockEntity be = context.getSourceBlockEntity();
        if (!(be instanceof DamageObserverBE marker)) return EMPTY;
        int maxRows = Math.max(1, stats.maxRows());
        List<MutableComponent> lines = new ArrayList<>();
        MutableComponent name = marker.name().isEmpty() ? BattleText.tr("sections.unnamed") : Component.literal(marker.name());
        add(lines, maxRows, marker.permille() < 0 ? BattleText.tr("link.section_none", name)
                : BattleText.tr("link.section", name, marker.permille() / 10));
        add(lines, maxRows, marker.attached()
                ? (marker.reportsTo().isEmpty() ? BattleText.tr("marker.reports_ground") : BattleText.tr("marker.reports", marker.reportsTo()))
                : BattleText.tr("marker.alone"));
        return lines;
    }

    private static void add(List<MutableComponent> lines, int maxRows, MutableComponent text) {
        if (lines.size() < maxRows) lines.add(text);
    }

    @Override
    protected String getTranslationKey() {
        return "damage_observer";
    }
}
