package com.createstats.blockentity.inventory;

import com.createstats.inventory.core.BinCardLedger;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

// screen only, once a second, only to whoever has it open
public final class InventoryBooks {
    public record Row(String key, ItemStack icon, long here, float inPerMinute, float outPerMinute,
                      BinCardLedger.Row card) {
        public float netPerMinute() {
            return inPerMinute - outPerMinute;
        }
    }

    public final List<Row> rows = new ArrayList<>();

    public BinCardLedger.Row total = new BinCardLedger.Row("", 0, 0, 0, 0, 0, 0, 0, 0, false);
    public int today;
    public int openedDay;

    public boolean chartPresent;
    public long chartLast = Long.MIN_VALUE;
    public int[] chartValues = new int[0];
    public int[] chartStamps = new int[0];
    public byte[] chartFlags = new byte[0];

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(rows.size());
        for (Row r : rows) {
            buf.writeUtf(r.key(), 256);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, r.icon());
            buf.writeVarLong(r.here());
            buf.writeFloat(r.inPerMinute());
            buf.writeFloat(r.outPerMinute());
            writeCard(buf, r.card());
        }
        writeCard(buf, total);
        buf.writeInt(today);
        buf.writeInt(openedDay);
        buf.writeBoolean(chartPresent);
        if (chartPresent) {
            buf.writeLong(chartLast);
            buf.writeVarIntArray(chartValues);
            buf.writeVarIntArray(chartStamps);
            buf.writeByteArray(chartFlags);
        }
    }

    private static void writeCard(RegistryFriendlyByteBuf buf, BinCardLedger.Row c) {
        buf.writeVarLong(c.opening());
        buf.writeVarLong(c.received());
        buf.writeVarLong(c.issued());
        buf.writeVarLong(c.todayIn());
        buf.writeVarLong(c.todayOut());
        buf.writeVarLong(c.yesterdayIn());
        buf.writeVarLong(c.yesterdayOut());
        buf.writeLong(c.yesterdayClose());
        buf.writeBoolean(c.hasYesterday());
    }

    private static BinCardLedger.Row readCard(RegistryFriendlyByteBuf buf, String key) {
        return new BinCardLedger.Row(key, buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong(),
                buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readLong(), buf.readBoolean());
    }

    public static InventoryBooks read(RegistryFriendlyByteBuf buf) {
        final InventoryBooks b = new InventoryBooks();
        final int n = buf.readVarInt();
        if (n < 0 || n > 1024) {
            throw new io.netty.handler.codec.DecoderException("Inventory Display books row count out of range: " + n);
        }
        for (int i = 0; i < n; i++) {
            final String key = buf.readUtf(256);
            final ItemStack icon = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            final long here = buf.readVarLong();
            final float in = buf.readFloat();
            final float out = buf.readFloat();
            b.rows.add(new Row(key, icon, here, in, out, readCard(buf, key)));
        }
        b.total = readCard(buf, "");
        b.today = buf.readInt();
        b.openedDay = buf.readInt();
        b.chartPresent = buf.readBoolean();
        if (b.chartPresent) {
            b.chartLast = buf.readLong();
            b.chartValues = buf.readVarIntArray(1024);
            b.chartStamps = buf.readVarIntArray(1024);
            b.chartFlags = buf.readByteArray(1024);
        }
        return b;
    }

    public Row row(String key) {
        for (Row r : rows) {
            if (r.key().equals(key)) {
                return r;
            }
        }
        return null;
    }
}
