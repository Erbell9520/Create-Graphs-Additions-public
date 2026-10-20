package com.createstats.blockentity.inventory;

import com.createstats.antenna.AntennaTier;
import com.createstats.inventory.core.BinCardLedger;
import com.createstats.inventory.core.ChartHistory;
import com.createstats.inventory.core.FeederBook;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

// contents aren't saved, they rescan. a minute of GATHERING after restart is honest
public final class InventorySave {
    private InventorySave() {
    }

    public static final class Loaded {
        public final InventorySettings settings = new InventorySettings();
        public final FeederBook feeders = new FeederBook();
        public final BinCardLedger ledger = new BinCardLedger();
        public final ChartHistory chart = new ChartHistory();
        public AntennaTier antenna = AntennaTier.NONE;
        public String penKey = "";
        public long stallLastMovement = Long.MIN_VALUE;

        public Long legacyLinkPos;

        public boolean legacy;
    }

    public static void write(CompoundTag tag, InventorySettings settings, FeederBook feeders, BinCardLedger ledger,
                             ChartHistory chart, AntennaTier antenna, String penKey, long stallLastMovement) {
        tag.putInt("InventoryDisplay", 2);
        settings.save(tag);
        final ListTag links = new ListTag();
        for (FeederBook.Entry e : feeders.entries()) {
            final CompoundTag l = new CompoundTag();
            l.putString("Dim", e.dimension());
            l.putLong("Pos", e.pos());
            l.putLong("At", e.announced());
            links.add(l);
        }
        tag.put("Feeders", links);
        tag.putString("Antenna", (antenna == null ? AntennaTier.NONE : antenna).getSerializedName());
        final CompoundTag books = new CompoundTag();
        books.putInt("Today", ledger.today());
        books.putInt("Opened", ledger.openedDay());
        final ListTag cards = new ListTag();
        for (BinCardLedger.Row r : ledger.export()) {
            final CompoundTag c = new CompoundTag();
            c.putString("K", r.key());
            c.putLong("O", r.opening());
            c.putLong("R", r.received());
            c.putLong("I", r.issued());
            c.putLong("TI", r.todayIn());
            c.putLong("TO", r.todayOut());
            c.putLong("YI", r.yesterdayIn());
            c.putLong("YO", r.yesterdayOut());
            c.putLong("YC", r.yesterdayClose());
            c.putBoolean("Y", r.hasYesterday());
            cards.add(c);
        }
        books.put("Cards", cards);
        tag.put("Books", books);
        final CompoundTag c = new CompoundTag();
        c.putLong("Last", chart.lastPeriod());
        c.putIntArray("V", chart.exportValues());
        c.putIntArray("S", chart.exportStamps());
        c.putByteArray("F", chart.exportFlags());
        c.putString("Pen", penKey == null ? "" : penKey);
        tag.put("Chart", c);
        if (stallLastMovement != Long.MIN_VALUE) {
            tag.putLong("StallMoved", stallLastMovement);
        }
    }

    public static Loaded read(CompoundTag tag) {
        final Loaded out = new Loaded();
        out.legacy = !tag.contains("InventoryDisplay");
        out.settings.load(tag);
        if (tag.contains("Feeders", Tag.TAG_LIST)) {
            final ListTag links = tag.getList("Feeders", Tag.TAG_COMPOUND);
            final List<FeederBook.Entry> entries = new ArrayList<>();
            for (int i = 0; i < links.size(); i++) {
                final CompoundTag l = links.getCompound(i);
                entries.add(new FeederBook.Entry(l.getString("Dim"), l.getLong("Pos"), l.getLong("At")));
            }
            out.feeders.restore(entries);
        }
        out.antenna = AntennaTier.byName(tag.getString("Antenna"));
        if (tag.contains("Books", Tag.TAG_COMPOUND)) {
            final CompoundTag books = tag.getCompound("Books");
            final ListTag cards = books.getList("Cards", Tag.TAG_COMPOUND);
            final List<BinCardLedger.Row> rows = new ArrayList<>();
            for (int i = 0; i < cards.size(); i++) {
                final CompoundTag c = cards.getCompound(i);
                rows.add(new BinCardLedger.Row(c.getString("K"), c.getLong("O"), c.getLong("R"), c.getLong("I"),
                        c.getLong("TI"), c.getLong("TO"), c.getLong("YI"), c.getLong("YO"), c.getLong("YC"),
                        c.getBoolean("Y")));
            }
            out.ledger.restore(books.getInt("Today"), books.getInt("Opened"), rows);
        }
        if (tag.contains("Chart", Tag.TAG_COMPOUND)) {
            final CompoundTag c = tag.getCompound("Chart");
            out.chart.restore(c.getLong("Last"), c.getIntArray("V"), c.getIntArray("S"), c.getByteArray("F"));
            out.penKey = c.getString("Pen");
        }
        if (tag.contains("StallMoved")) {
            out.stallLastMovement = tag.getLong("StallMoved");
        }
        if (out.legacy) {
            readLegacy(tag, out);
        }
        return out;
    }

    private static void readLegacy(CompoundTag tag, Loaded out) {
        if ("BRIDGE".equals(tag.getString("SourceMode")) && tag.contains("BridgeCandidatePos")
                && !"SMART_OBSERVER".equals(tag.getString("BridgeKind"))) {
            out.legacyLinkPos = tag.getLong("BridgeCandidatePos");
        }
        if (tag.getBoolean("DieselStructureModeConfigured")) {
            out.settings.dieselMulti = tag.getBoolean("DieselMultiStructureMode");
        }
    }
}
