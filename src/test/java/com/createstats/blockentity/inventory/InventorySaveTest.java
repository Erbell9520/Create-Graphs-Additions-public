package com.createstats.blockentity.inventory;

import com.createstats.antenna.AntennaTier;
import com.createstats.inventory.core.BinCardLedger;
import com.createstats.inventory.core.ChartHistory;
import com.createstats.inventory.core.FeederBook;
import com.createstats.inventory.core.MarkLadder;
import com.createstats.inventory.core.SetPointRelay;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventorySaveTest {
    @Test
    void everythingSavedComesBack() {
        final InventorySettings settings = new InventorySettings();
        settings.setMarks("minecraft:iron_ingot#0", MarkLadder.ITEMS, 128, 4096);
        settings.setMarks(InventorySettings.CONTAINER, MarkLadder.PERCENT, 15, 85);
        settings.lowMode = SetPointRelay.Mode.PULSE;
        settings.highMode = SetPointRelay.Mode.OFF;
        settings.stallIndex = 3;
        settings.chartDays = 1;
        settings.preferBehind = true;
        settings.dieselMulti = Boolean.TRUE;
        final FeederBook feeders = new FeederBook();
        feeders.announce("minecraft:overworld", new BlockPos(10, 64, -5).asLong(), 100L);
        feeders.announce("minecraft:the_nether", new BlockPos(-3, 70, 8).asLong(), 250L);
        final BinCardLedger ledger = new BinCardLedger();
        final Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("minecraft:iron_ingot#0", 64L);
        counts.put("minecraft:coal#0", 12L);
        ledger.open(40, counts);
        ledger.apply(41, Map.of("minecraft:iron_ingot#0", 30L, "minecraft:coal#0", -4L));
        final ChartHistory chart = new ChartHistory();
        chart.record(5000L, 4920, 64);
        chart.record(5001L, 4921, 70);
        chart.recordGap(5003L, 4923);
        chart.record(5004L, 4924, 94);

        final CompoundTag tag = new CompoundTag();
        InventorySave.write(tag, settings, feeders, ledger, chart, AntennaTier.ADVANCED, "minecraft:iron_ingot#0", 12_345L);
        final InventorySave.Loaded back = InventorySave.read(tag);

        assertFalse(back.legacy);
        assertNull(back.legacyLinkPos);
        assertEquals(new InventorySettings.Marks(128, 4096), back.settings.marks("minecraft:iron_ingot#0", MarkLadder.ITEMS));
        assertEquals(new InventorySettings.Marks(15, 85), back.settings.marks(InventorySettings.CONTAINER, MarkLadder.PERCENT));
        assertEquals(SetPointRelay.Mode.PULSE, back.settings.lowMode);
        assertEquals(SetPointRelay.Mode.OFF, back.settings.highMode);
        assertEquals(3, back.settings.stallIndex);
        assertEquals(1, back.settings.chartDays);
        assertTrue(back.settings.preferBehind);
        assertEquals(Boolean.TRUE, back.settings.dieselMulti);
        assertEquals(feeders.entries(), back.feeders.entries());
        assertEquals(AntennaTier.ADVANCED, back.antenna);
        assertEquals("minecraft:iron_ingot#0", back.penKey);
        assertEquals(12_345L, back.stallLastMovement);
        assertEquals(ledger.today(), back.ledger.today());
        assertEquals(ledger.openedDay(), back.ledger.openedDay());
        assertEquals(ledger.export(), back.ledger.export());
        assertEquals(chart.lastPeriod(), back.chart.lastPeriod());
        assertArrayEquals(chart.exportValues(), back.chart.exportValues());
        assertArrayEquals(chart.exportStamps(), back.chart.exportStamps());
        assertArrayEquals(chart.exportFlags(), back.chart.exportFlags());
    }

    private static CompoundTag shipped(String sourceMode, String bridgeKind, boolean dieselConfigured, boolean dieselMulti) {
        final CompoundTag tag = new CompoundTag();
        tag.putString("SourceMode", sourceMode);
        tag.putBoolean("DieselMultiStructureMode", dieselMulti);
        tag.putBoolean("DieselStructureModeConfigured", dieselConfigured);
        tag.putString("BridgeStatus", "CONNECTED");
        tag.putString("BridgeKind", bridgeKind);
        tag.putLong("BridgeCandidatePos", new BlockPos(10, 64, -5).asLong());
        tag.putLong("BridgeResolvedPos", new BlockPos(11, 64, -5).asLong());
        tag.putString("BridgeResolvedName", "Item Vault");
        tag.putInt("DetectionRange", 8);
        return tag;
    }

    @Test
    void aShippedDisplayLinkBridgeSeedsTheFeederBook() {
        final InventorySave.Loaded back = InventorySave.read(shipped("BRIDGE", "DISPLAY_LINK", false, false));
        assertTrue(back.legacy);
        assertEquals(new BlockPos(10, 64, -5).asLong(), back.legacyLinkPos);

        assertFalse(back.ledger.isOpen());
        assertEquals(0, back.chart.size());
        assertEquals(new InventorySettings.Marks(10, 90), back.settings.marks(InventorySettings.CONTAINER, MarkLadder.PERCENT));
        assertEquals(AntennaTier.NONE, back.antenna);
        assertNull(back.settings.dieselMulti);
        assertTrue(back.feeders.isEmpty(), "the block entity gives the candidate its dimension on its first tick");
    }

    @Test
    void aShippedSmartObserverBridgeIsDropped() {
        final InventorySave.Loaded back = InventorySave.read(shipped("BRIDGE", "SMART_OBSERVER", false, false));
        assertTrue(back.legacy);
        assertNull(back.legacyLinkPos, "D21: the Smart Observer bridge is gone; the block behind is read instead");
    }

    @Test
    void aShippedDirectDisplayHasNoLink() {
        assertNull(InventorySave.read(shipped("DIRECT", "NONE", false, false)).legacyLinkPos);
    }

    @Test
    void theShippedDieselChoiceCarriesOver() {
        assertEquals(Boolean.TRUE, InventorySave.read(shipped("DIRECT", "NONE", true, true)).settings.dieselMulti);
        assertEquals(Boolean.FALSE, InventorySave.read(shipped("DIRECT", "NONE", true, false)).settings.dieselMulti);
        assertNull(InventorySave.read(shipped("DIRECT", "NONE", false, true)).settings.dieselMulti,
                "a choice never made stays the source's to decide");
    }

    @Test
    void aNewSaveIsNeverReadAsTheShippedOne() {
        final CompoundTag tag = new CompoundTag();
        InventorySave.write(tag, new InventorySettings(), new FeederBook(), new BinCardLedger(), new ChartHistory(),
                AntennaTier.NONE, "", Long.MIN_VALUE);

        tag.putString("SourceMode", "BRIDGE");
        tag.putLong("BridgeCandidatePos", 42L);
        final InventorySave.Loaded back = InventorySave.read(tag);
        assertFalse(back.legacy);
        assertNull(back.legacyLinkPos);
        assertEquals(Long.MIN_VALUE, back.stallLastMovement);
        assertEquals(List.of(), back.feeders.entries());
    }
}
