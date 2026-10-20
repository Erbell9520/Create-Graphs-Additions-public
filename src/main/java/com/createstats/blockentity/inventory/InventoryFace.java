package com.createstats.blockentity.inventory;

import com.createstats.inventory.core.FlowClock;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

// small snapshot. the old block resent its whole tag every second, ~2KB/s per detector per player
public final class InventoryFace {
    public enum Page {
        BLANK,
        NOTHING,
        CANT_READ,
        CONTAINER,
        VAULT,
        BELT,
        TANK,
        ENERGY,
        DIESEL,
        LINK_TEXT,
        WATCH_ONE,
        WATCH_MANY,
        OUT_OF_REACH,
        NOT_LOADED;

        static Page of(int i) {
            final Page[] all = values();
            return i >= 0 && i < all.length ? all[i] : BLANK;
        }
    }

    public static final byte SOURCE_BEHIND = 0;
    public static final byte SOURCE_LINK = 1;

    public static final byte LENS_DARK = 0;
    public static final byte LENS_READING = 1;
    public static final byte LENS_CANT_READ = 2;

    public static final int LAMP_LOW = 1;
    public static final int LAMP_FULL = 2;
    public static final int LAMP_STALL = 4;
    public static final int LAMP_POWER = 8;

    public static final byte FLOW_NONE = 0;
    public static final byte FLOW_UP = 1;
    public static final byte FLOW_DOWN = 2;

    public static final byte STATE_OK = 0;
    public static final byte STATE_LOW = 1;
    public static final byte STATE_HIGH = 2;

    public record Mover(ItemStack icon, float perMinute) {
    }

    public record Tile(ItemStack icon, long count) {
    }

    public record BeltItem(ItemStack icon, float position) {
    }

    public record WatchRow(String markKey, ItemStack icon, Component name, long count, long low, long high,
                           float inPerMinute, float outPerMinute, FlowClock.Kind clock, float clockSeconds, byte state,
                           int markUnit) {
        public float netPerMinute() {
            return inPerMinute - outPerMinute;
        }
    }

    public int seq;

    public int contentSeq;
    public Page page = Page.BLANK;
    public byte source = SOURCE_BEHIND;
    public Component title = Component.empty();

    public int sizeA;
    public int sizeB;
    public int sizeC;

    public int linkDistance = -1;

    public byte antennaTier;
    public int linkReach;

    public long sourcePos;

    public ItemStack sourceIcon = ItemStack.EMPTY;

    public final List<ItemStack> slots = new ArrayList<>();
    public byte[] slotFlow = new byte[0];
    public int totalSlots;
    public int usedSlots;
    public final List<Tile> tiles = new ArrayList<>();
    public int totalTypes;
    public float fill;

    public long totalItems;
    public float inPerMinute;
    public float outPerMinute;
    public final List<Mover> risers = new ArrayList<>();
    public final List<Mover> fallers = new ArrayList<>();

    public int gatherSeconds = -1;
    public FlowClock.Kind clock = FlowClock.Kind.NONE;
    public float clockSeconds;

    public final List<BeltItem> beltItems = new ArrayList<>();
    public final List<Mover> passing = new ArrayList<>();
    public float passPerMinute;
    public int itemsOnBelt;

    public int stalledSeconds = -1;

    public byte beltDirection;
    public float beltSpeed;

    public int beltGate;
    public int beltLength;

    public FluidStack fluid = FluidStack.EMPTY;
    public long fluidAmount;
    public long fluidCapacity;
    public boolean cauldron;

    public byte boilerLevel = -1;
    public byte engines;
    public byte whistles;

    public boolean energyStored;
    public boolean energyFlows;
    public long energy;
    public long energyCapacity;
    public long energyIn;
    public long energyOut;

    public byte energyCountKind;
    public long energyCount;

    public boolean energyGrid;

    public byte energyClock;

    public float dieselRpm;

    public byte dieselState;
    public boolean dieselMulti;

    public final List<String> lines = new ArrayList<>();

    public final List<WatchRow> rows = new ArrayList<>();

    public int baseReach;

    public byte neededTier = -1;
    public int neededReach;
    public boolean otherDimension;

    public int lastReadSeconds = -1;

    public byte lampsLit;
    public byte lampsFlashing;

    public long lampTestUntil;
    public boolean lowRelay;
    public boolean highRelay;
    public byte comparator;
    public float lowNeedle;
    public float highNeedle;
    public byte lens = LENS_DARK;

    public byte lowMode;
    public byte highMode;
    public byte stallIndex;
    public byte chartDays = 1;
    public boolean preferBehind;

    public boolean bothSources;

    public byte markUnit;

    public String quantityKey = "";
    public float quantityValue;
    public long quantityLow;
    public long quantityHigh;

    public int chartScale = 1;
    public float chartLow = Float.NaN;
    public float chartHigh = Float.NaN;
    public Component penName = Component.empty();

    public long chartPeriod = Long.MIN_VALUE;
    public int chartStamp;
    public int chartValue;
    public boolean chartGap;

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(seq);
        buf.writeVarInt(contentSeq);
        buf.writeByte(page.ordinal());
        buf.writeByte(source);
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, title);
        buf.writeVarInt(sizeA);
        buf.writeVarInt(sizeB);
        buf.writeVarInt(sizeC);
        buf.writeVarInt(linkDistance + 1);
        buf.writeByte(antennaTier);
        buf.writeVarInt(linkReach);
        buf.writeLong(sourcePos);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, sourceIcon);

        buf.writeVarInt(slots.size());
        for (ItemStack s : slots) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, s);
        }
        buf.writeByteArray(slotFlow);
        buf.writeVarInt(totalSlots);
        buf.writeVarInt(usedSlots);
        buf.writeVarInt(tiles.size());
        for (Tile t : tiles) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, t.icon());
            buf.writeVarLong(t.count());
        }
        buf.writeVarInt(totalTypes);
        buf.writeFloat(fill);
        buf.writeVarLong(totalItems);
        buf.writeFloat(inPerMinute);
        buf.writeFloat(outPerMinute);
        writeMovers(buf, risers);
        writeMovers(buf, fallers);
        buf.writeVarInt(gatherSeconds + 1);
        buf.writeByte(clock.ordinal());
        buf.writeFloat(clockSeconds);

        buf.writeVarInt(beltItems.size());
        for (BeltItem b : beltItems) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, b.icon());
            buf.writeFloat(b.position());
        }
        writeMovers(buf, passing);
        buf.writeFloat(passPerMinute);
        buf.writeVarInt(itemsOnBelt);
        buf.writeVarInt(stalledSeconds + 1);
        buf.writeByte(beltDirection);
        buf.writeFloat(beltSpeed);
        buf.writeVarInt(beltGate);
        buf.writeVarInt(beltLength);

        FluidStack.OPTIONAL_STREAM_CODEC.encode(buf, fluid);
        buf.writeVarLong(fluidAmount);
        buf.writeVarLong(fluidCapacity);
        buf.writeBoolean(cauldron);
        buf.writeByte(boilerLevel);
        buf.writeByte(engines);
        buf.writeByte(whistles);

        buf.writeBoolean(energyStored);
        buf.writeBoolean(energyFlows);
        buf.writeVarLong(energy);
        buf.writeVarLong(energyCapacity);
        buf.writeLong(energyIn);
        buf.writeLong(energyOut);
        buf.writeByte(energyCountKind);
        buf.writeVarLong(energyCount);
        buf.writeBoolean(energyGrid);
        buf.writeByte(energyClock);

        buf.writeFloat(dieselRpm);
        buf.writeByte(dieselState);
        buf.writeBoolean(dieselMulti);

        buf.writeVarInt(lines.size());
        for (String l : lines) {
            buf.writeUtf(l, 64);
        }

        buf.writeVarInt(rows.size());
        for (WatchRow r : rows) {
            buf.writeUtf(r.markKey(), 256);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, r.icon());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, r.name());
            buf.writeVarLong(r.count());
            buf.writeVarLong(r.low());
            buf.writeVarLong(r.high());
            buf.writeFloat(r.inPerMinute());
            buf.writeFloat(r.outPerMinute());
            buf.writeByte(r.clock().ordinal());
            buf.writeFloat(r.clockSeconds());
            buf.writeByte(r.state());
            buf.writeByte(r.markUnit());
        }

        buf.writeVarInt(baseReach);
        buf.writeByte(neededTier);
        buf.writeVarInt(neededReach);
        buf.writeBoolean(otherDimension);
        buf.writeVarInt(lastReadSeconds + 1);

        buf.writeByte(lampsLit);
        buf.writeByte(lampsFlashing);
        buf.writeLong(lampTestUntil);
        buf.writeBoolean(lowRelay);
        buf.writeBoolean(highRelay);
        buf.writeByte(comparator);
        buf.writeFloat(lowNeedle);
        buf.writeFloat(highNeedle);
        buf.writeByte(lens);

        buf.writeByte(lowMode);
        buf.writeByte(highMode);
        buf.writeByte(stallIndex);
        buf.writeByte(chartDays);
        buf.writeBoolean(preferBehind);
        buf.writeBoolean(bothSources);
        buf.writeByte(markUnit);
        buf.writeUtf(quantityKey, 256);
        buf.writeFloat(quantityValue);
        buf.writeVarLong(quantityLow);
        buf.writeVarLong(quantityHigh);

        buf.writeVarInt(chartScale);
        buf.writeFloat(chartLow);
        buf.writeFloat(chartHigh);
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, penName);
        buf.writeLong(chartPeriod);
        buf.writeVarInt(chartStamp);
        buf.writeVarInt(chartValue);
        buf.writeBoolean(chartGap);
    }

    private static void writeMovers(RegistryFriendlyByteBuf buf, List<Mover> movers) {
        buf.writeVarInt(movers.size());
        for (Mover m : movers) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, m.icon());
            buf.writeFloat(m.perMinute());
        }
    }

    private static final int LIMIT = 4096;

    private static int count(RegistryFriendlyByteBuf buf) {
        final int n = buf.readVarInt();
        if (n < 0 || n > LIMIT) {
            throw new io.netty.handler.codec.DecoderException("Inventory Display snapshot count out of range: " + n);
        }
        return n;
    }

    public static InventoryFace read(RegistryFriendlyByteBuf buf) {
        final InventoryFace f = new InventoryFace();
        f.seq = buf.readVarInt();
        f.contentSeq = buf.readVarInt();
        f.page = Page.of(buf.readByte());
        f.source = buf.readByte();
        f.title = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
        f.sizeA = buf.readVarInt();
        f.sizeB = buf.readVarInt();
        f.sizeC = buf.readVarInt();
        f.linkDistance = buf.readVarInt() - 1;
        f.antennaTier = buf.readByte();
        f.linkReach = buf.readVarInt();
        f.sourcePos = buf.readLong();
        f.sourceIcon = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);

        for (int i = count(buf); i > 0; i--) {
            f.slots.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
        }
        f.slotFlow = buf.readByteArray(LIMIT);
        f.totalSlots = buf.readVarInt();
        f.usedSlots = buf.readVarInt();
        for (int i = count(buf); i > 0; i--) {
            f.tiles.add(new Tile(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarLong()));
        }
        f.totalTypes = buf.readVarInt();
        f.fill = buf.readFloat();
        f.totalItems = buf.readVarLong();
        f.inPerMinute = buf.readFloat();
        f.outPerMinute = buf.readFloat();
        readMovers(buf, f.risers);
        readMovers(buf, f.fallers);
        f.gatherSeconds = buf.readVarInt() - 1;
        f.clock = kind(buf.readByte());
        f.clockSeconds = buf.readFloat();

        for (int i = count(buf); i > 0; i--) {
            f.beltItems.add(new BeltItem(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readFloat()));
        }
        readMovers(buf, f.passing);
        f.passPerMinute = buf.readFloat();
        f.itemsOnBelt = buf.readVarInt();
        f.stalledSeconds = buf.readVarInt() - 1;
        f.beltDirection = buf.readByte();
        f.beltSpeed = buf.readFloat();
        f.beltGate = buf.readVarInt();
        f.beltLength = buf.readVarInt();

        f.fluid = FluidStack.OPTIONAL_STREAM_CODEC.decode(buf);
        f.fluidAmount = buf.readVarLong();
        f.fluidCapacity = buf.readVarLong();
        f.cauldron = buf.readBoolean();
        f.boilerLevel = buf.readByte();
        f.engines = buf.readByte();
        f.whistles = buf.readByte();

        f.energyStored = buf.readBoolean();
        f.energyFlows = buf.readBoolean();
        f.energy = buf.readVarLong();
        f.energyCapacity = buf.readVarLong();
        f.energyIn = buf.readLong();
        f.energyOut = buf.readLong();
        f.energyCountKind = buf.readByte();
        f.energyCount = buf.readVarLong();
        f.energyGrid = buf.readBoolean();
        f.energyClock = buf.readByte();

        f.dieselRpm = buf.readFloat();
        f.dieselState = buf.readByte();
        f.dieselMulti = buf.readBoolean();

        for (int i = count(buf); i > 0; i--) {
            f.lines.add(buf.readUtf(64));
        }

        for (int i = count(buf); i > 0; i--) {
            final String markKey = buf.readUtf(256);
            final ItemStack icon = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            final Component name = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            f.rows.add(new WatchRow(markKey, icon, name, buf.readVarLong(), buf.readVarLong(), buf.readVarLong(),
                    buf.readFloat(), buf.readFloat(), kind(buf.readByte()), buf.readFloat(), buf.readByte(),
                    buf.readByte()));
        }

        f.baseReach = buf.readVarInt();
        f.neededTier = buf.readByte();
        f.neededReach = buf.readVarInt();
        f.otherDimension = buf.readBoolean();
        f.lastReadSeconds = buf.readVarInt() - 1;

        f.lampsLit = buf.readByte();
        f.lampsFlashing = buf.readByte();
        f.lampTestUntil = buf.readLong();
        f.lowRelay = buf.readBoolean();
        f.highRelay = buf.readBoolean();
        f.comparator = buf.readByte();
        f.lowNeedle = buf.readFloat();
        f.highNeedle = buf.readFloat();
        f.lens = buf.readByte();

        f.lowMode = buf.readByte();
        f.highMode = buf.readByte();
        f.stallIndex = buf.readByte();
        f.chartDays = buf.readByte();
        f.preferBehind = buf.readBoolean();
        f.bothSources = buf.readBoolean();
        f.markUnit = buf.readByte();
        f.quantityKey = buf.readUtf(256);
        f.quantityValue = buf.readFloat();
        f.quantityLow = buf.readVarLong();
        f.quantityHigh = buf.readVarLong();

        f.chartScale = buf.readVarInt();
        f.chartLow = buf.readFloat();
        f.chartHigh = buf.readFloat();
        f.penName = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
        f.chartPeriod = buf.readLong();
        f.chartStamp = buf.readVarInt();
        f.chartValue = buf.readVarInt();
        f.chartGap = buf.readBoolean();
        return f;
    }

    private static void readMovers(RegistryFriendlyByteBuf buf, List<Mover> into) {
        for (int i = count(buf); i > 0; i--) {
            into.add(new Mover(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readFloat()));
        }
    }

    private static FlowClock.Kind kind(int i) {
        final FlowClock.Kind[] all = FlowClock.Kind.values();
        return i >= 0 && i < all.length ? all[i] : FlowClock.Kind.NONE;
    }

    public byte[] toBytes(RegistryAccess registries) {
        final RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            write(buf);
            final byte[] out = new byte[buf.readableBytes()];
            buf.readBytes(out);
            return out;
        } finally {
            buf.release();
        }
    }

    public static InventoryFace fromBytes(byte[] bytes, RegistryAccess registries) {
        final RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), registries);
        try {
            return read(buf);
        } finally {
            buf.release();
        }
    }

    public boolean lit(int bit) {
        return (this.lampsLit & bit) != 0;
    }

    public boolean flashing(int bit) {
        return (this.lampsFlashing & bit) != 0;
    }
}
