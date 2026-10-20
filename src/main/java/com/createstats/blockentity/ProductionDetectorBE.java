package com.createstats.blockentity;

import com.createstats.antenna.AntennaTier;
import com.createstats.antenna.RangeAttachment;
import com.createstats.block.ProductionDetectorBlock;
import com.createstats.blockentity.inventory.InventoryBooks;
import com.createstats.blockentity.inventory.InventoryDialBox;
import com.createstats.blockentity.inventory.InventoryFace;
import com.createstats.blockentity.inventory.InventorySave;
import com.createstats.blockentity.inventory.InventoryScanner;
import com.createstats.blockentity.inventory.InventorySettings;
import com.createstats.blockentity.inventory.ItemKeys;
import com.createstats.blockentity.inventory.SampleCupBox;
import com.createstats.display.DisplayLinkDimensionExtension;
import com.createstats.inventory.core.BeltPassCounter;
import com.createstats.inventory.core.BinCardLedger;
import com.createstats.inventory.core.BridgeReach;
import com.createstats.inventory.core.ChartHistory;
import com.createstats.inventory.core.Eased;
import com.createstats.inventory.core.EnergyReadout;
import com.createstats.inventory.core.FeederBook;
import com.createstats.inventory.core.FlowClock;
import com.createstats.inventory.core.MarkLadder;
import com.createstats.inventory.core.SetPointRelay;
import com.createstats.inventory.core.StallDetector;
import com.createstats.inventory.core.StockFlow;
import com.createstats.network.InventoryBooksPacket;
import com.createstats.network.InventoryFacePacket;
import com.createstats.power.FeConsumer;
import com.createstats.power.FePowerFx;
import com.createstats.power.FeProfiles;
import com.createstats.power.IFeConsumer;
import com.createstats.util.ClientScreenAccess;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ProductionDetectorBE extends SmartBlockEntity implements IFeConsumer, RangeAttachment {
    private final FeConsumer feConsumer = FeConsumer.forBlockEntity(this,
            FeProfiles.settings(FeProfiles.KEY_PRODUCTION_DETECTOR));

    @Override
    public FeConsumer feConsumer() {
        return feConsumer;
    }

    public static final int MAX_DISPLAY_LINK_LINES = 8;
    public static final int LINK_LINE_WIDTH = 36;
    private static final int LINK_LINE_TIMEOUT_TICKS = 200;

    private static final int SCAN_INTERVAL = 5;

    public static final int EVENT_TEST = 1;
    private static final int LAMP_TEST_TICKS = 20;
    private static final int JEWEL_HOLD_TICKS = 10;
    private static final int VIEWER_TIMEOUT_TICKS = 200;

    private static final BehaviourType<SettingsDial> LOW_DIAL = new BehaviourType<>("inventory_low_dial");
    private static final BehaviourType<SettingsDial> HIGH_DIAL = new BehaviourType<>("inventory_high_dial");

    private FilteringBehaviour cup;
    private SettingsDial lowDial;
    private SettingsDial highDial;

    private InventorySettings settings;
    private FeederBook feeders;
    private BinCardLedger ledger;
    private ChartHistory chart;
    private StockFlow flow;
    private StallDetector stall;
    private BeltPassCounter passes;
    private FlowClock pageClock;
    private ItemKeys keys;
    private AntennaTier antennaTier;
    private String penKey;
    private Long legacyLinkPos;
    private final List<SetPointRelay> relays = new ArrayList<>();
    private final List<FlowClock> rowClocks = new ArrayList<>();
    private final List<String> relayKeys = new ArrayList<>();
    private final SetPointRelay.AlarmLamp stallLamp = new SetPointRelay.AlarmLamp();
    private final Map<String, ItemStack> knownSamples = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ItemStack> eldest) {
            return size() > 256;
        }
    };
    private final Map<UUID, Long> viewers = new HashMap<>();
    private final LinkedHashMap<Integer, String> linkLines = new LinkedHashMap<>();
    private final Map<Integer, Long> linkLineTimes = new HashMap<>();

    private boolean lastPowered = true;
    private long lastScanTick = Long.MIN_VALUE;
    private long lastReadTick = Long.MIN_VALUE;
    private String targetIdentity = "";

    private String storeIdentity = "";
    private boolean freshTarget;
    private boolean readingBelt;
    private int seq;
    private int contentSeq;
    private long lampTestUntil = Long.MIN_VALUE;
    private boolean lowOut;
    private boolean highOut;
    // hold the jewel 10 ticks, a 4 tick pulse can fall between snapshots
    private long lowJewelUntil = Long.MIN_VALUE;
    private long highJewelUntil = Long.MIN_VALUE;
    private int comparator;
    private int lastContentHash;
    private int lastRateHash;
    private long lastContentSend = Long.MIN_VALUE;
    private long lastRateSend = Long.MIN_VALUE;
    private long lastBooksSend = Long.MIN_VALUE;
    private boolean chartAppended;
    private InventoryFace serverFace;
    private InventoryScanner.Reading lastReading;
    private List<Watch> watch = new ArrayList<>();
    private Quantity quantity;

    private InventoryFace clientFace;
    private ChartHistory clientChart;
    private InventoryBooks clientBooks;
    private byte[] pendingFaceBytes;

    public long testPressedAt = Long.MIN_VALUE;

    public final Anim anim = new Anim();

    public static final class Anim {
        public final Eased pen = new Eased();

        public final Eased low = new Eased();
        public final Eased high = new Eased();

        public int lastContentSeq = Integer.MIN_VALUE;
        public double blinkUntil = Double.NEGATIVE_INFINITY;
    }

    public ProductionDetectorBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.settings = new InventorySettings();
        this.feeders = new FeederBook();
        this.ledger = new BinCardLedger();
        this.chart = new ChartHistory();
        this.flow = new StockFlow();
        this.stall = new StallDetector();
        this.passes = new BeltPassCounter();
        this.pageClock = new FlowClock();
        this.keys = new ItemKeys();
        this.antennaTier = AntennaTier.NONE;
        this.penKey = "";
        this.clientChart = new ChartHistory();
        setLazyTickRate(20);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        this.cup = new FilteringBehaviour(this, new SampleCupBox()).withCallback(stack -> this.onCupChanged());
        this.cup.setLabel(Component.translatable("create_stats.inventory_display.value.sample_cup"));
        behaviours.add(this.cup);
        this.lowDial = new SettingsDial(false);
        this.highDial = new SettingsDial(true);
        behaviours.add(this.lowDial);
        behaviours.add(this.highDial);
    }

    private final class SettingsDial extends SettingsValueBox {
        private final boolean high;

        SettingsDial(boolean high) {
            super(Component.translatable(high ? "create_stats.inventory_display.value.high"
                            : "create_stats.inventory_display.value.low"),
                    ProductionDetectorBE.this, new InventoryDialBox(high), high ? HIGH_DIAL : LOW_DIAL, high ? 3 : 2,
                    high ? "InventoryHighMark" : "InventoryLowMark", 20, 4,
                    List.of(Component.translatable("create_stats.inventory_display.value.items")),
                    () -> new ValueSettings(0, ProductionDetectorBE.this.dialNotch(high)),
                    (player, v) -> ProductionDetectorBE.this.setMarkFromDial(high, v.value()),
                    v -> Component.literal(ProductionDetectorBE.this.dialLadder().label(v.value())),
                    () -> ProductionDetectorBE.this.dialLabel(high));
            this.high = high;
        }

        @Override
        public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
            final MarkLadder ladder = ProductionDetectorBE.this.dialLadder();
            return new ValueSettingsBoard(this.label, ladder.maxNotch(), 4, List.of(unitName(ladder)),
                    new ValueSettingsFormatter(v -> Component.literal(ladder.label(v.value()))));
        }
    }

    private static Component unitName(MarkLadder ladder) {
        return switch (ladder) {
            case ITEMS -> Component.translatable("create_stats.inventory_display.value.items");
            case PER_MINUTE -> Component.translatable("create_stats.inventory_display.value.per_min");
            default -> Component.translatable("create_stats.inventory_display.value.percent");
        };
    }

    private record Watch(String markKey, MarkLadder unit, ItemStack icon, Component name, long count,
                         long capacity, double inPerMinute, double outPerMinute) {
    }

    private record Quantity(String markKey, MarkLadder unit, double value, boolean valid) {
    }

    private MarkLadder dialLadder() {
        if (!this.watch.isEmpty()) {
            return this.watch.get(0).unit();
        }
        return this.quantity != null ? this.quantity.unit() : MarkLadder.PERCENT;
    }

    private String dialKey() {
        if (!this.watch.isEmpty()) {
            return this.watch.get(0).markKey();
        }
        return this.quantity != null ? this.quantity.markKey() : InventorySettings.CONTAINER;
    }

    private int dialNotch(boolean high) {
        if (this.settings == null) {
            return 0;
        }
        final InventorySettings.Marks m = this.settings.marks(dialKey(), dialLadder());
        return dialLadder().notchOf(high ? m.high() : m.low());
    }

    private String dialLabel(boolean high) {
        if (this.settings == null) {
            return "";
        }
        final MarkLadder ladder = dialLadder();
        final InventorySettings.Marks m = this.settings.marks(dialKey(), ladder);
        return ladder.label(ladder.notchOf(high ? m.high() : m.low()));
    }

    private void setMarkFromDial(boolean high, int notch) {
        if (this.settings == null || this.level == null || this.level.isClientSide()) {
            return;
        }
        final MarkLadder ladder = dialLadder();
        final InventorySettings.Marks m = this.settings.marks(dialKey(), ladder);
        final long v = ladder.valueAt(notch);
        if (high) {
            this.settings.setMarks(dialKey(), ladder, Math.min(m.low(), Math.max(0L, v - 1L)), v);
        } else {
            this.settings.setMarks(dialKey(), ladder, v, Math.max(m.high(), v + 1L));
        }
        settingsChanged();
    }

    private void onCupChanged() {
        if (this.level != null && !this.level.isClientSide()) {
            this.lastScanTick = Long.MIN_VALUE;
            this.relays.clear();
            this.relayKeys.clear();
            this.rowClocks.clear();
            setChanged();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level == null) {
            return;
        }
        if (this.level.isClientSide()) {
            clientTick();
            return;
        }
        final boolean powered = this.feConsumer.serviceTick(this.readingBelt);
        FePowerFx.handleTransition(this.level, this.worldPosition, this.feConsumer);
        if (!powered) {
            if (this.lastPowered) {
                releaseOutputs();
            }
            this.lastPowered = false;
            return;
        }
        this.lastPowered = true;
        serverTick(this.level.getGameTime());
    }

    @Override
    public void onFePowerLost() {
        releaseOutputs();
    }

    private void releaseOutputs() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        for (SetPointRelay r : this.relays) {
            r.reset();
        }
        this.stallLamp.reset();
        this.lampTestUntil = Long.MIN_VALUE;
        final boolean had = this.lowOut || this.highOut || this.comparator != 0;
        this.lowOut = false;
        this.highOut = false;
        this.lowJewelUntil = Long.MIN_VALUE;
        this.highJewelUntil = Long.MIN_VALUE;
        this.comparator = 0;
        setPoweredState(false);
        if (had) {
            notifyOutputs();
        }

        if (this.serverFace != null) {
            this.serverFace.lampsLit = 0;
            this.serverFace.lampsFlashing = 0;
            this.serverFace.lowRelay = false;
            this.serverFace.highRelay = false;
            this.serverFace.comparator = 0;
            pushFace(this.serverFace, true);
        }
    }

    private void serverTick(long now) {
        if (this.legacyLinkPos != null) {
            this.feeders.announce(this.level.dimension().location().toString(), this.legacyLinkPos, now);
            this.legacyLinkPos = null;
        }
        expireLinkLines(now);
        final boolean due = this.readingBelt || this.lastScanTick == Long.MIN_VALUE
                || now - this.lastScanTick >= SCAN_INTERVAL;
        if (due) {
            scan(now);
        }
        boolean outputsChanged = false;
        for (SetPointRelay r : this.relays) {
            outputsChanged |= r.tick();
        }
        if (outputsChanged) {
            applyOutputs();
        }
        if (ChartHistory.isSampleTick(now)) {
            recordChart(now);
        }
        if (this.serverFace != null) {
            maybeSendFace(now);
        }
        if (now - this.lastBooksSend >= 20 && !this.viewers.isEmpty()) {
            sendBooksToViewers(now, false);
        }
    }

    private record Source(Kind kind, @Nullable Level level, BlockPos pos, @Nullable Direction accessFace,
                          boolean viaLink, @Nullable BlockPos linkPos, int linkDistance,
                          @Nullable BridgeReach.Verdict verdict, boolean behindPresent) {
        enum Kind { READ, NONE, OUT_OF_REACH, NOT_LOADED }
    }

    private Source resolveSource(long now) {
        final Direction facing = getBlockState().getValue(ProductionDetectorBlock.FACING);
        final BlockPos behind = this.worldPosition.relative(facing);
        final boolean behindPresent = this.level.isLoaded(behind) && !this.level.getBlockState(behind).isAir();
        final Optional<FeederBook.Choice> choice = this.feeders.isEmpty() ? Optional.empty()
                : this.feeders.choose(this::linkState);
        final Source fromBehind = new Source(Source.Kind.READ, this.level, behind, facing.getOpposite(), false,
                null, -1, null, behindPresent);

        if (choice.isEmpty() || (this.settings.preferBehind && behindPresent)) {
            return fromBehind;
        }
        return linkSource(choice.get(), behindPresent);
    }

    private Source linkSource(FeederBook.Choice choice, boolean behindPresent) {
        final FeederBook.Entry e = choice.entry();
        final BlockPos linkPos = BlockPos.of(e.pos());
        final ServerLevel linkLevel = levelOf(e.dimension());
        final boolean sameDim = linkLevel == this.level;
        if (!choice.loaded() || linkLevel == null) {
            return new Source(Source.Kind.NOT_LOADED, linkLevel, linkPos, null, true, linkPos,
                    sameDim ? (int) Math.round(Math.sqrt(linkPos.distSqr(this.worldPosition))) : -1, null, behindPresent);
        }
        final double base = linkRange();
        final BridgeReach.Verdict verdict = BridgeReach.check(sameDim,
                com.createstats.antenna.AntennaRange.isOverworldNetherPair(linkLevel.dimension(), this.level.dimension()),
                sameDim && Level.NETHER.equals(this.level.dimension()),
                sameDim ? linkPos.distSqr(this.worldPosition) : 0.0, base, this.antennaTier);
        final int distance = sameDim ? (int) Math.round(verdict.distance()) : -1;
        if (!verdict.reaches()) {
            return new Source(Source.Kind.OUT_OF_REACH, linkLevel, linkPos, null, true, linkPos, distance, verdict,
                    behindPresent);
        }
        final BlockEntity be = linkLevel.getBlockEntity(linkPos);
        if (!(be instanceof DisplayLinkBlockEntity dl)) {
            return new Source(Source.Kind.NONE, linkLevel, linkPos, null, true, linkPos, distance, verdict, behindPresent);
        }
        final BlockPos src = dl.getSourcePosition();
        if (!linkLevel.isLoaded(src)) {
            return new Source(Source.Kind.NOT_LOADED, linkLevel, src, null, true, linkPos, distance, verdict,
                    behindPresent);
        }
        return new Source(Source.Kind.READ, linkLevel, src, null, true, linkPos, distance, verdict, behindPresent);
    }

    private static double linkRange() {
        try {
            return AllConfigs.server().logistics.displayLinkRange.get();
        } catch (RuntimeException e) {
            return 64.0;
        }
    }

    @Nullable
    private ServerLevel levelOf(String dimension) {
        if (!(this.level instanceof ServerLevel server) || server.getServer() == null) {
            return null;
        }
        final ResourceLocation id = ResourceLocation.tryParse(dimension);
        return id == null ? null : server.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    private FeederBook.State linkState(FeederBook.Entry e) {
        final ServerLevel linkLevel = levelOf(e.dimension());
        if (linkLevel == null) {
            return FeederBook.State.GONE;
        }
        final BlockPos pos = BlockPos.of(e.pos());
        if (!linkLevel.isLoaded(pos)) {
            return FeederBook.State.UNLOADED;
        }
        if (!(linkLevel.getBlockEntity(pos) instanceof DisplayLinkBlockEntity dl)) {
            return FeederBook.State.GONE;
        }
        ResourceLocation targetDim = null;
        if ((Object) dl instanceof DisplayLinkDimensionExtension ext) {
            targetDim = ext.createStats$getTargetDimension();
        }
        final ResourceLocation here = this.level.dimension().location();
        final ResourceLocation aimed = targetDim != null ? targetDim : linkLevel.dimension().location();
        final BlockPos target = dl.getTargetPosition();
        final boolean aimsHere = aimed.equals(here)
                && (target.equals(this.worldPosition) || target.equals(this.worldPosition.above()));
        return aimsHere ? FeederBook.State.LOADED : FeederBook.State.GONE;
    }

    public void acceptFeeder(ResourceKey<Level> dimension, BlockPos linkPos) {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        if (this.feeders.announce(dimension.location().toString(), linkPos.asLong(), this.level.getGameTime())) {
            this.lastScanTick = Long.MIN_VALUE;
            setChanged();
        }
    }

    public void acceptLinkText(int line, List<MutableComponent> text) {
        if (this.level == null || this.level.isClientSide() || text == null) {
            return;
        }
        final long now = this.level.getGameTime();
        for (int i = 0; i < text.size(); i++) {
            final int row = line + i;
            if (row < 0 || row >= MAX_DISPLAY_LINK_LINES) {
                break;
            }
            String s = text.get(i).getString();
            final StringBuilder b = new StringBuilder();
            for (int c = 0; c < s.length() && b.length() < LINK_LINE_WIDTH; c++) {
                final char ch = s.charAt(c);
                b.append(ch >= 32 && ch <= 126 ? ch : '?');
            }
            this.linkLines.put(row, b.toString());
            this.linkLineTimes.put(row, now);
        }
    }

    public void clearLinkText() {
        this.linkLines.clear();
        this.linkLineTimes.clear();
    }

    private void expireLinkLines(long now) {
        final Iterator<Map.Entry<Integer, Long>> it = this.linkLineTimes.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<Integer, Long> e = it.next();
            if (now - e.getValue() > LINK_LINE_TIMEOUT_TICKS) {
                this.linkLines.remove(e.getKey());
                it.remove();
            }
        }
    }

    private void scan(long now) {
        this.lastScanTick = now;
        this.stall.setStallIndex(this.settings.stallIndex);
        final Source src = resolveSource(now);
        String identity = src.kind() == Source.Kind.READ && src.level() != null
                ? src.level().dimension().location() + "@" + src.pos().asLong() : "";
        if (!identity.equals(this.targetIdentity)) {
            onTargetChanged(identity);
        }
        InventoryScanner.Reading r = null;
        if (src.kind() == Source.Kind.READ && src.level() != null) {
            r = InventoryScanner.scan(src.level(), src.pos(), new InventoryScanner.Context(this.keys,
                    src.accessFace(), this.settings.dieselMulti, getBlockState().getValue(ProductionDetectorBlock.FACING)));
            if (r.readable()) {
                this.lastReadTick = now;
            }
        }
        this.readingBelt = r != null && r.kind == InventoryScanner.Kind.BELT;
        this.lastReading = r;
        if (r != null) {
            for (Map.Entry<String, ItemStack> e : r.samples.entrySet()) {
                this.knownSamples.put(e.getKey(), e.getValue());
            }
        }
        final Map<String, Long> counts = countsOf(r);
        if (r != null && r.readable()) {
            final boolean baseline = this.flow.awaitingBaseline();
            final Map<String, Long> deltas = this.flow.observe(now, counts);
            final int day = BinCardLedger.dayOf(this.level.getDayTime());
            if (baseline) {
                if (this.freshTarget || !this.ledger.isOpen()) {
                    this.ledger.open(day, counts);
                } else {
                    this.ledger.reconcile(day, counts);
                }
                this.freshTarget = false;
            } else {
                this.ledger.apply(day, deltas);
            }
            if (!deltas.isEmpty()) {
                this.contentSeq++;
            }
            if (this.readingBelt) {
                final List<BeltPassCounter.Item> items = new ArrayList<>(r.belt.size());
                for (InventoryScanner.BeltEntry b : r.belt) {
                    items.add(new BeltPassCounter.Item(b.identity(), b.key(), b.position(), b.count()));
                }
                final Map<String, Integer> passed = this.passes.observe(now, items, r.beltGate, r.beltPositive);
                this.stall.observe(now, !passed.isEmpty());
            } else {
                this.stall.observe(now, !deltas.isEmpty());
            }
            if (!deltas.isEmpty()) {
                setChanged();
            }
        }
        this.watch = watchRows(r, now);
        this.quantity = quantityOf(r, now);
        updateRelays(now);
        final InventoryFace f = buildFace(now, src, r);
        this.serverFace = f;
    }

    private void onTargetChanged(String identity) {
        this.targetIdentity = identity;
        this.flow.rebaseline();
        this.stall.reset();
        this.passes.reset();
        this.pageClock.reset();
        this.rowClocks.clear();
        this.relays.clear();
        this.relayKeys.clear();

        if (!identity.isEmpty()) {
            this.freshTarget = !this.storeIdentity.isEmpty() && !this.storeIdentity.equals(identity);
            this.storeIdentity = identity;
        }
    }

    private static Map<String, Long> countsOf(@Nullable InventoryScanner.Reading r) {
        final Map<String, Long> out = new LinkedHashMap<>();
        if (r == null) {
            return out;
        }
        switch (r.kind) {
            case ITEMS, BELT -> out.putAll(r.counts);
            case FLUID, CAULDRON -> out.put("fluid", r.fluidAmount);
            case ENERGY -> {
                if (r.energy != null && r.energy.hasStored()) {
                    out.put("fe", r.energy.stored());
                }
            }
            case DIESEL -> out.put("fuel", r.fuelAmount);
            default -> {
            }
        }
        return out;
    }

    private List<Watch> watchRows(@Nullable InventoryScanner.Reading r, long now) {
        final List<Watch> out = new ArrayList<>();
        if (r == null || r.kind != InventoryScanner.Kind.ITEMS || this.cup == null || this.cup.getFilter().isEmpty()) {
            return out;
        }
        final ItemStack filterStack = this.cup.getFilter().copy();
        final FilterItemStack filter = FilterItemStack.of(filterStack.copy());
        if (filter instanceof FilterItemStack.ListFilterItemStack list && !list.isBlacklist) {
            for (FilterItemStack entry : list.containedItems) {
                if (out.size() >= 3) {
                    break;
                }
                final ItemStack icon = entry.item().copyWithCount(1);
                out.add(watchOf(r, now, this.keys.key(icon, this.level.registryAccess()), icon, icon.getHoverName(),
                        s -> entry.test(this.level, s, list.shouldRespectNBT)));
            }
            return out;
        }
        if (filter.isFilterItem()) {
            out.add(watchOf(r, now, "filter:" + this.keys.key(filterStack, this.level.registryAccess()),
                    filterStack.copyWithCount(1), filterStack.getHoverName(), s -> filter.test(this.level, s)));
            return out;
        }
        final ItemStack icon = filterStack.copyWithCount(1);
        out.add(watchOf(r, now, this.keys.key(icon, this.level.registryAccess()), icon, icon.getHoverName(),
                s -> filter.test(this.level, s)));
        return out;
    }

    private Watch watchOf(InventoryScanner.Reading r, long now, String markKey, ItemStack icon, Component name,
                          java.util.function.Predicate<ItemStack> matches) {
        long count = 0L;
        long capacity = 0L;
        double in = 0.0;
        double out = 0.0;
        for (Map.Entry<String, Long> e : r.counts.entrySet()) {
            final ItemStack sample = r.samples.get(e.getKey());
            if (sample == null || !matches.test(sample)) {
                continue;
            }
            count += e.getValue();
            final StockFlow.Rate rate = this.flow.rate(e.getKey(), now);
            in += rate.inPerMinute();
            out += rate.outPerMinute();
        }

        final int max = Math.max(1, Math.min(r.emptySlotLimit, icon.getMaxStackSize()));
        for (Map.Entry<String, Long> e : r.partialRoom.entrySet()) {
            final ItemStack sample = r.samples.get(e.getKey());
            if (sample != null && matches.test(sample)) {
                capacity += e.getValue();
            }
        }
        capacity += count + (long) r.emptySlots * max;
        return new Watch(markKey, MarkLadder.ITEMS, icon, name, count, capacity, in, out);
    }

    @Nullable
    private Quantity quantityOf(@Nullable InventoryScanner.Reading r, long now) {
        if (r == null || !r.readable()) {
            return null;
        }
        return switch (r.kind) {
            case ITEMS -> new Quantity(InventorySettings.CONTAINER, MarkLadder.PERCENT, r.fill * 100.0, true);
            case FLUID, CAULDRON -> new Quantity(InventorySettings.CONTAINER, MarkLadder.PERCENT,
                    r.fluidCapacity > 0 ? r.fluidAmount * 100.0 / r.fluidCapacity : 0.0, r.fluidCapacity > 0);
            case DIESEL -> new Quantity(InventorySettings.CONTAINER, MarkLadder.PERCENT,
                    r.fuelCapacity > 0 ? r.fuelAmount * 100.0 / r.fuelCapacity : 0.0, r.fuelCapacity > 0);
            case ENERGY -> new Quantity(InventorySettings.ENERGY, MarkLadder.ENERGY_PERCENT,
                    r.energy != null ? r.energy.fill() * 100.0 : 0.0, r.energy != null && r.energy.hasStored());
            case BELT -> new Quantity(InventorySettings.BELT, MarkLadder.PER_MINUTE, this.passes.totalPerMinute(now),
                    !this.passes.gathering(now));
            default -> null;
        };
    }

    private List<double[]> relayInputs() {
        final List<double[]> out = new ArrayList<>();
        if (!this.watch.isEmpty()) {
            for (Watch w : this.watch) {
                final InventorySettings.Marks m = this.settings.marks(w.markKey(), w.unit());
                out.add(new double[]{w.count(), m.low(), m.high()});
            }
            return out;
        }
        if (this.quantity != null && this.quantity.valid()) {
            final InventorySettings.Marks m = this.settings.marks(this.quantity.markKey(), this.quantity.unit());
            out.add(new double[]{this.quantity.value(), m.low(), m.high()});
        }
        return out;
    }

    private List<String> relayKeysNow() {
        final List<String> out = new ArrayList<>();
        if (!this.watch.isEmpty()) {
            for (Watch w : this.watch) {
                out.add(w.markKey());
            }
        } else if (this.quantity != null && this.quantity.valid()) {
            out.add(this.quantity.markKey());
        }
        return out;
    }

    private void updateRelays(long now) {
        final List<String> keysNow = relayKeysNow();
        if (!keysNow.equals(this.relayKeys)) {
            this.relays.clear();
            this.relayKeys.clear();
            this.relayKeys.addAll(keysNow);
            for (int i = 0; i < keysNow.size(); i++) {
                this.relays.add(new SetPointRelay());
            }
        }
        final List<double[]> inputs = relayInputs();
        for (int i = 0; i < this.relays.size() && i < inputs.size(); i++) {
            final double[] in = inputs.get(i);
            this.relays.get(i).update(in[0], in[1], in[2], this.settings.lowMode, this.settings.highMode);
        }
        this.stallLamp.update(this.stall.stalled(now));
        applyOutputs();
    }

    private void applyOutputs() {
        boolean low = false;
        boolean high = false;
        for (SetPointRelay r : this.relays) {
            low |= r.lowOutput();
            high |= r.highOutput();
        }
        final List<double[]> inputs = relayInputs();
        final int comp = inputs.isEmpty() ? 0 : SetPointRelay.comparator(inputs.get(0)[0], inputs.get(0)[1],
                inputs.get(0)[2]);
        final boolean relayChanged = low != this.lowOut || high != this.highOut;
        final boolean compChanged = comp != this.comparator;
        this.lowOut = low;
        this.highOut = high;
        this.comparator = comp;
        if (this.level != null && this.settings != null) {
            final long now = this.level.getGameTime();
            if (low && this.settings.lowMode == SetPointRelay.Mode.PULSE) {
                this.lowJewelUntil = now + JEWEL_HOLD_TICKS;
            }
            if (high && this.settings.highMode == SetPointRelay.Mode.PULSE) {
                this.highJewelUntil = now + JEWEL_HOLD_TICKS;
            }
        }
        if (relayChanged) {
            setPoweredState(low || high);
        }
        if (relayChanged || compChanged) {
            notifyOutputs();
        }
    }

    private void notifyOutputs() {
        if (this.level == null) {
            return;
        }
        final BlockState state = getBlockState();
        this.level.updateNeighborsAt(this.worldPosition, state.getBlock());
        this.level.updateNeighbourForOutputSignal(this.worldPosition, state.getBlock());
        final BlockPos up = this.worldPosition.above();
        this.level.updateNeighborsAt(up, state.getBlock());
        this.level.updateNeighbourForOutputSignal(up, state.getBlock());
    }

    private void setPoweredState(boolean powered) {
        if (this.level == null) {
            return;
        }
        final BlockState lower = this.level.getBlockState(this.worldPosition);
        if (lower.hasProperty(ProductionDetectorBlock.POWERED) && lower.getValue(ProductionDetectorBlock.POWERED) != powered) {
            this.level.setBlock(this.worldPosition, lower.setValue(ProductionDetectorBlock.POWERED, powered), 2);
        }
        final BlockPos upPos = this.worldPosition.above();
        final BlockState upper = this.level.getBlockState(upPos);
        if (upper.is(lower.getBlock()) && upper.hasProperty(ProductionDetectorBlock.HALF)
                && upper.getValue(ProductionDetectorBlock.HALF) == DoubleBlockHalf.UPPER
                && upper.getValue(ProductionDetectorBlock.POWERED) != powered) {
            this.level.setBlock(upPos, upper.setValue(ProductionDetectorBlock.POWERED, powered), 2);
        }
    }

    public int relaySignal(Direction towardReceiver) {
        if (!this.lastPowered) {
            return 0;
        }
        final Direction facing = getBlockState().getValue(ProductionDetectorBlock.FACING);
        if (towardReceiver == facing.getCounterClockWise()) {
            return this.lowOut ? 15 : 0;
        }
        if (towardReceiver == facing.getClockWise()) {
            return this.highOut ? 15 : 0;
        }
        return 0;
    }

    public int comparatorLevel() {
        return this.lastPowered ? this.comparator : 0;
    }

    public boolean lowRelayOn() {
        return this.lowOut;
    }

    public boolean highRelayOn() {
        return this.highOut;
    }

    public void pressTest(@Nullable Player player) {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        this.level.blockEvent(this.worldPosition, getBlockState().getBlock(), EVENT_TEST, 0);
        if (!this.feConsumer.isPowered()) {
            return;
        }
        boolean acknowledged = false;
        for (SetPointRelay r : this.relays) {
            acknowledged |= r.acknowledge();
        }
        acknowledged |= this.stallLamp.acknowledge();
        if (!acknowledged) {
            this.lampTestUntil = this.level.getGameTime() + LAMP_TEST_TICKS;
        }
        if (player != null) {
            player.displayClientMessage(Component.translatable(acknowledged
                    ? "create_stats.inventory_display.test.acknowledged"
                    : "create_stats.inventory_display.test.lamps"), true);
        }
        this.lastScanTick = Long.MIN_VALUE;
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id == EVENT_TEST) {
            if (this.level != null && this.level.isClientSide()) {
                this.testPressedAt = this.level.getGameTime();
            }
            return true;
        }
        return super.triggerEvent(id, param);
    }

    private void recordChart(long now) {
        final String pen = penKeyNow();
        if (!pen.equals(this.penKey)) {
            this.penKey = pen;
            this.chart.clear();
        }
        if (pen.isEmpty()) {
            return;
        }
        final long value = penValue(now);
        final long period = ChartHistory.periodOf(now);
        final int stamp = ChartHistory.stampOf(this.level.getDayTime());
        this.chart.record(period, stamp, (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value)));
        this.chartAppended = true;
        setChanged();
        if (this.serverFace != null) {
            fillChart(this.serverFace);
        }
    }

    private String penKeyNow() {
        final InventoryScanner.Reading r = this.lastReading;
        if (!this.watch.isEmpty()) {
            return "watch:" + this.watch.get(0).markKey();
        }
        if (r == null || !r.readable()) {
            return this.penKey;
        }
        return switch (r.kind) {
            case ITEMS -> "items";
            case FLUID, CAULDRON -> "fluid";
            case DIESEL -> "fuel";
            case ENERGY -> r.energy != null && r.energy.hasStored() ? "fe" : "";
            case BELT -> "belt";
            default -> "";
        };
    }

    private long penValue(long now) {
        final InventoryScanner.Reading r = this.lastReading;
        if (!this.watch.isEmpty()) {
            return this.watch.get(0).count();
        }
        if (r == null) {
            return 0L;
        }
        return switch (r.kind) {
            case ITEMS -> r.totalItems;
            case FLUID, CAULDRON -> r.fluidAmount;
            case DIESEL -> r.fuelAmount;
            case ENERGY -> r.energy != null ? r.energy.stored() : 0L;
            case BELT -> Math.round(this.passes.totalPerMinute(now));
            default -> 0L;
        };
    }

    private void fillChart(InventoryFace f) {
        final InventoryScanner.Reading r = this.lastReading;
        long scale = 1L;
        double lo = Double.NaN;
        double hi = Double.NaN;
        long maxSample = 1L;
        for (int k = 0; k < this.chart.size(); k++) {
            if (!this.chart.gap(k)) {
                maxSample = Math.max(maxSample, this.chart.value(k));
            }
        }
        if (!this.watch.isEmpty()) {
            final InventorySettings.Marks m = this.settings.marks(this.watch.get(0).markKey(), MarkLadder.ITEMS);
            scale = Math.max(Math.max(Math.round(m.high() * 1.25), Math.round(maxSample * 1.05)), 1L);
            lo = m.low() / (double) scale;
            hi = m.high() / (double) scale;
            f.penName = this.watch.get(0).name();
        } else if (r != null && r.readable()) {
            switch (r.kind) {
                case ITEMS -> {
                    scale = Math.max(1L, r.totalItems + r.freeItems);
                    final InventorySettings.Marks m = this.settings.marks(InventorySettings.CONTAINER, MarkLadder.PERCENT);
                    lo = m.low() / 100.0;
                    hi = m.high() / 100.0;
                }
                case FLUID, CAULDRON, DIESEL -> {
                    scale = Math.max(1L, r.kind == InventoryScanner.Kind.DIESEL ? r.fuelCapacity : r.fluidCapacity);
                    final InventorySettings.Marks m = this.settings.marks(InventorySettings.CONTAINER, MarkLadder.PERCENT);
                    lo = m.low() / 100.0;
                    hi = m.high() / 100.0;
                }
                case ENERGY -> {
                    scale = Math.max(1L, r.energy != null ? r.energy.capacity() : 1L);
                    final InventorySettings.Marks m = this.settings.marks(InventorySettings.ENERGY, MarkLadder.ENERGY_PERCENT);
                    lo = m.low() / 100.0;
                    hi = m.high() / 100.0;
                }
                case BELT -> {
                    final InventorySettings.Marks m = this.settings.marks(InventorySettings.BELT, MarkLadder.PER_MINUTE);
                    scale = Math.max(Math.max(Math.round(m.high() * 1.25), Math.round(maxSample * 1.05)), 1L);
                    lo = m.low() / (double) scale;
                    hi = m.high() / (double) scale;
                }
                default -> {
                }
            }
            f.penName = r.name;
        }
        f.chartScale = (int) Math.min(Integer.MAX_VALUE, scale);
        f.chartLow = (float) lo;
        f.chartHigh = (float) hi;
        if (this.chart.size() > 0) {
            final int k = this.chart.size() - 1;
            f.chartPeriod = this.chart.lastPeriod();
            f.chartStamp = this.chart.stamp(k);
            f.chartValue = this.chart.value(k);
            f.chartGap = this.chart.gap(k);
        }
    }

    private InventoryFace buildFace(long now, Source src, @Nullable InventoryScanner.Reading r) {
        final InventoryFace f = new InventoryFace();
        f.contentSeq = this.contentSeq;
        f.source = src.viaLink() ? InventoryFace.SOURCE_LINK : InventoryFace.SOURCE_BEHIND;
        f.antennaTier = (byte) this.antennaTier.ordinal();
        f.linkReach = (int) Math.round(BridgeReach.reachOf(linkRange(), this.antennaTier,
                Level.NETHER.equals(this.level.dimension())));
        f.linkDistance = src.linkDistance();
        f.baseReach = (int) Math.round(linkRange());
        f.bothSources = src.viaLink() ? src.behindPresent() : !this.feeders.isEmpty() && src.behindPresent();
        f.preferBehind = this.settings.preferBehind;
        f.lowMode = (byte) this.settings.lowMode.ordinal();
        f.highMode = (byte) this.settings.highMode.ordinal();
        f.stallIndex = (byte) this.settings.stallIndex;
        f.chartDays = (byte) this.settings.chartDays;
        f.markUnit = (byte) dialLadder().ordinal();
        f.lampTestUntil = this.lampTestUntil;
        f.sourcePos = r != null ? r.pos.asLong() : 0L;
        if (r != null) {
            f.title = r.name;
            f.sizeA = r.sizeA;
            f.sizeB = r.sizeB;
            f.sizeC = r.sizeC;
            if (src.level() != null && src.level().isLoaded(r.pos)) {
                f.sourceIcon = new ItemStack(src.level().getBlockState(r.pos).getBlock().asItem());
            }
        }
        f.lens = src.viaLink() ? InventoryFace.LENS_DARK
                : r == null || r.kind == InventoryScanner.Kind.NOTHING ? InventoryFace.LENS_DARK
                : r.readable() ? InventoryFace.LENS_READING : InventoryFace.LENS_CANT_READ;

        switch (src.kind()) {
            case OUT_OF_REACH -> {
                f.page = InventoryFace.Page.OUT_OF_REACH;
                f.title = linkTargetName(src);
                final BridgeReach.Verdict v = src.verdict();
                if (v != null) {
                    f.otherDimension = v.crossDimension();
                    f.neededTier = v.needed() == null ? -1 : (byte) v.needed().ordinal();
                    f.neededReach = v.needed() == null ? 0 : (int) Math.round(BridgeReach.reachOf(linkRange(), v.needed(),
                            Level.NETHER.equals(this.level.dimension())));
                }
            }
            case NOT_LOADED -> {
                f.page = InventoryFace.Page.NOT_LOADED;
                f.title = linkTargetName(src);
                f.lastReadSeconds = this.lastReadTick == Long.MIN_VALUE ? -1
                        : (int) Math.min(Integer.MAX_VALUE, (now - this.lastReadTick) / 20L);
            }
            case NONE -> f.page = InventoryFace.Page.NOTHING;
            case READ -> fillPage(f, now, src, r);
        }
        if (this.quantity != null && this.quantity.valid()) {
            final InventorySettings.Marks m = this.settings.marks(this.quantity.markKey(), this.quantity.unit());
            f.quantityKey = this.quantity.markKey();
            f.quantityValue = (float) this.quantity.value();
            f.quantityLow = m.low();
            f.quantityHigh = m.high();
        }
        fillOutputs(f, now);
        fillChart(f);
        return f;
    }

    private Component linkTargetName(Source src) {
        if (src.level() != null && src.linkPos() != null && src.level().isLoaded(src.linkPos())
                && src.level().getBlockEntity(src.linkPos()) instanceof DisplayLinkBlockEntity dl
                && src.level().isLoaded(dl.getSourcePosition())) {
            return src.level().getBlockState(dl.getSourcePosition()).getBlock().getName();
        }
        return Component.translatable("create_stats.inventory_display.face.display_link");
    }

    private void fillPage(InventoryFace f, long now, Source src, @Nullable InventoryScanner.Reading r) {
        if (r == null || r.kind == InventoryScanner.Kind.NOTHING || r.kind == InventoryScanner.Kind.CANT_READ) {
            if (src.viaLink() && !this.linkLines.isEmpty()) {
                f.page = InventoryFace.Page.LINK_TEXT;
                for (int row = 0; row < MAX_DISPLAY_LINK_LINES; row++) {
                    f.lines.add(this.linkLines.getOrDefault(row, ""));
                }
                while (!f.lines.isEmpty() && f.lines.get(f.lines.size() - 1).isEmpty()) {
                    f.lines.remove(f.lines.size() - 1);
                }
                return;
            }
            f.page = r != null && r.kind == InventoryScanner.Kind.CANT_READ ? InventoryFace.Page.CANT_READ
                    : InventoryFace.Page.NOTHING;
            return;
        }
        final boolean gathering = this.flow.gathering(now);
        f.gatherSeconds = gathering ? this.flow.secondsUntilRates(now) : -1;
        switch (r.kind) {
            case ITEMS -> fillItems(f, now, r, gathering);
            case BELT -> fillBelt(f, now, r);
            case FLUID, CAULDRON -> fillTank(f, now, r, gathering);
            case ENERGY -> fillEnergy(f, now, r, gathering);
            case DIESEL -> fillDiesel(f, now, r, gathering);
            default -> f.page = InventoryFace.Page.NOTHING;
        }
    }

    private ItemStack sample(String key) {
        final ItemStack s = this.knownSamples.get(key);
        return s != null ? s : ItemStack.EMPTY;
    }

    private void fillItems(InventoryFace f, long now, InventoryScanner.Reading r, boolean gathering) {
        f.totalSlots = r.slotCount;
        f.fill = (float) r.fill;
        f.totalItems = r.totalItems;
        final StockFlow.Rate total = this.flow.total(now);
        f.inPerMinute = (float) total.inPerMinute();
        f.outPerMinute = (float) total.outPerMinute();

        if (!this.watch.isEmpty()) {
            while (this.rowClocks.size() < this.watch.size()) {
                this.rowClocks.add(new FlowClock());
            }
            for (int i = 0; i < this.watch.size(); i++) {
                final Watch w = this.watch.get(i);
                final InventorySettings.Marks m = this.settings.marks(w.markKey(), w.unit());
                FlowClock.Projection p = FlowClock.Projection.NONE;
                if (!gathering) {
                    p = this.rowClocks.get(i).update(w.count(), w.capacity(), w.inPerMinute() - w.outPerMinute(),
                            (double) m.low(), (double) m.high(), false, Math.max(m.high(), w.count()));
                }
                final byte state = w.count() <= m.low() ? InventoryFace.STATE_LOW
                        : w.count() >= m.high() ? InventoryFace.STATE_HIGH : InventoryFace.STATE_OK;
                f.rows.add(new InventoryFace.WatchRow(w.markKey(), w.icon(), w.name(), w.count(), m.low(), m.high(),
                        (float) w.inPerMinute(), (float) w.outPerMinute(), p.kind(), (float) p.seconds(), state,
                        w.unit().ordinal()));
            }
        }
        if (r.slotCount > 54) {
            f.page = InventoryFace.Page.VAULT;
            final List<Map.Entry<String, Long>> sorted = new ArrayList<>(r.counts.entrySet());
            sorted.sort((a, b) -> {
                final int c = Long.compare(b.getValue(), a.getValue());
                return c != 0 ? c : a.getKey().compareTo(b.getKey());
            });
            f.totalTypes = sorted.size();
            for (int i = 0; i < sorted.size() && i < 16; i++) {
                final Map.Entry<String, Long> e = sorted.get(i);
                f.tiles.add(new InventoryFace.Tile(r.samples.getOrDefault(e.getKey(), ItemStack.EMPTY), e.getValue()));
            }
        } else {
            f.page = InventoryFace.Page.CONTAINER;
            final byte[] flows = new byte[r.slots.size()];
            int used = 0;
            for (int i = 0; i < r.slots.size(); i++) {
                final ItemStack s = r.slots.get(i);
                f.slots.add(s);
                if (s.isEmpty()) {
                    continue;
                }
                used++;
                if (!gathering) {
                    final double net = this.flow.net(this.keys.key(s, this.level.registryAccess()), now);
                    flows[i] = net > 0.0 ? InventoryFace.FLOW_UP : net < 0.0 ? InventoryFace.FLOW_DOWN : InventoryFace.FLOW_NONE;
                }
            }
            f.slotFlow = flows;
            f.usedSlots = used;
        }
        if (!gathering) {
            for (StockFlow.Mover m : this.flow.top(2, true, now)) {
                f.risers.add(new InventoryFace.Mover(sample(m.key()), (float) m.perMinute()));
            }
            for (StockFlow.Mover m : this.flow.top(2, false, now)) {
                f.fallers.add(new InventoryFace.Mover(sample(m.key()), (float) m.perMinute()));
            }
            final FlowClock.Projection p = this.pageClock.update(r.totalItems, r.totalItems + r.freeItems,
                    total.net(), null, null, false);
            f.clock = p.kind();
            f.clockSeconds = (float) p.seconds();
        }
        if (!f.rows.isEmpty()) {
            f.page = f.rows.size() == 1 ? InventoryFace.Page.WATCH_ONE : InventoryFace.Page.WATCH_MANY;
        }
    }

    private void fillBelt(InventoryFace f, long now, InventoryScanner.Reading r) {
        f.page = InventoryFace.Page.BELT;
        f.itemsOnBelt = r.belt.size();
        f.beltGate = r.beltIndex;
        f.beltLength = r.beltLength;
        f.beltSpeed = r.beltSpeed;
        f.beltDirection = (byte) (r.beltSpeed == 0.0f ? 0 : (r.beltPositive ? r.beltGlassSign : -r.beltGlassSign));

        for (InventoryScanner.BeltEntry b : r.belt) {
            final float rel = (b.position() - r.beltGate) * r.beltGlassSign;
            if (Math.abs(rel) <= 3.0f) {
                f.beltItems.add(new InventoryFace.BeltItem(b.sample(), (rel + 3.0f) / 6.0f));
            }
        }
        final boolean gathering = this.passes.gathering(now);
        f.gatherSeconds = gathering ? Math.max(1, StockFlow.WINDOW_SECONDS - this.passes.coveredSeconds(now)) : -1;
        for (StockFlow.Mover m : this.passes.top(3, now)) {
            f.passing.add(new InventoryFace.Mover(sample(m.key()), (float) m.perMinute()));
        }
        f.passPerMinute = (float) this.passes.totalPerMinute(now);
        f.stalledSeconds = this.stall.stalled(now) ? (int) (this.stall.quietTicks(now) / 20L) : -1;
    }

    private void fillTank(InventoryFace f, long now, InventoryScanner.Reading r, boolean gathering) {
        f.page = InventoryFace.Page.TANK;
        f.fluid = r.fluid.copy();
        f.fluidAmount = r.fluidAmount;
        f.fluidCapacity = r.fluidCapacity;
        f.cauldron = r.kind == InventoryScanner.Kind.CAULDRON;
        f.boilerLevel = (byte) r.boilerLevel;
        f.engines = (byte) Math.min(127, r.engines);
        f.whistles = (byte) Math.min(127, r.whistles);
        f.fill = r.fluidCapacity > 0 ? (float) (r.fluidAmount / (double) r.fluidCapacity) : 0.0f;
        final StockFlow.Rate rate = this.flow.rate("fluid", now);
        f.inPerMinute = (float) rate.inPerMinute();
        f.outPerMinute = (float) rate.outPerMinute();
        if (!gathering) {
            final FlowClock.Projection p = this.pageClock.update(r.fluidAmount, r.fluidCapacity, rate.net(), null, null,
                    false);
            f.clock = p.kind();
            f.clockSeconds = (float) p.seconds();
        }
    }

    private void fillEnergy(InventoryFace f, long now, InventoryScanner.Reading r, boolean gathering) {
        f.page = InventoryFace.Page.ENERGY;
        final EnergyReadout.Figures e = r.energy;
        f.energyGrid = r.energyGrid;
        if (e == null) {
            return;
        }
        f.energyStored = e.hasStored();
        f.energyFlows = e.hasFlows();
        f.energy = e.stored();
        f.energyCapacity = e.capacity();
        f.energyIn = e.inPerTick();
        f.energyOut = e.outPerTick();
        f.energyCountKind = (byte) e.countKind().ordinal();
        f.energyCount = e.count();
        f.fill = (float) e.fill();
        final EnergyReadout.Quote quote = e.quote();
        f.energyClock = (byte) quote.clock().ordinal();
        final boolean own = quote.clock() == EnergyReadout.Clock.OWN;
        if (!own) {
            f.gatherSeconds = -1;
        }
        if (e.hasStored() && !(own && gathering)) {
            // reserve runtime = reserve over its own drain, never a published rate

            final FlowClock.Projection p = EnergyReadout.clock(quote, () -> this.pageClock.update(e.stored(),
                    e.capacity(), this.flow.rate("fe", now).net(), null, null, true));
            f.clock = p.kind();
            f.clockSeconds = (float) p.seconds();
        }
    }

    private void fillDiesel(InventoryFace f, long now, InventoryScanner.Reading r, boolean gathering) {
        f.page = InventoryFace.Page.DIESEL;
        f.fluidAmount = r.fuelAmount;
        f.fluidCapacity = r.fuelCapacity;
        f.fill = r.fuelCapacity > 0 ? (float) (r.fuelAmount / (double) r.fuelCapacity) : 0.0f;
        f.dieselRpm = r.rpm;
        f.dieselState = (byte) r.dieselState;
        f.dieselMulti = r.dieselMulti;
        final StockFlow.Rate rate = this.flow.rate("fuel", now);
        f.inPerMinute = (float) rate.inPerMinute();
        f.outPerMinute = (float) rate.outPerMinute();
        if (!gathering) {
            final FlowClock.Projection p = this.pageClock.update(r.fuelAmount, r.fuelCapacity, rate.net(), null, null,
                    false);
            f.clock = p.kind();
            f.clockSeconds = (float) p.seconds();
        }
    }

    private void fillOutputs(InventoryFace f, long now) {
        int lit = 0;
        int flashing = 0;
        for (SetPointRelay r : this.relays) {
            if (r.lowLamp().lit()) {
                lit |= InventoryFace.LAMP_LOW;
            }
            if (r.lowLamp() == SetPointRelay.Lamp.FLASHING) {
                flashing |= InventoryFace.LAMP_LOW;
            }
            if (r.fullLamp().lit()) {
                lit |= InventoryFace.LAMP_FULL;
            }
            if (r.fullLamp() == SetPointRelay.Lamp.FLASHING) {
                flashing |= InventoryFace.LAMP_FULL;
            }
        }
        if (this.stallLamp.state().lit()) {
            lit |= InventoryFace.LAMP_STALL;
        }
        if (this.stallLamp.state() == SetPointRelay.Lamp.FLASHING) {
            flashing |= InventoryFace.LAMP_STALL;
        }
        f.lampsLit = (byte) lit;
        f.lampsFlashing = (byte) flashing;

        f.lowRelay = this.lowOut || now < this.lowJewelUntil;
        f.highRelay = this.highOut || now < this.highJewelUntil;
        f.comparator = (byte) this.comparator;
        final MarkLadder ladder = dialLadder();
        final InventorySettings.Marks m = this.settings.marks(dialKey(), ladder);
        f.lowNeedle = (float) ladder.fraction(m.low());
        f.highNeedle = (float) ladder.fraction(m.high());
    }

    private void maybeSendFace(long now) {
        final InventoryFace f = this.serverFace;
        final int content = contentHash(f);
        final int rates = rateHash(f);
        final boolean contentChanged = content != this.lastContentHash;
        final boolean ratesChanged = rates != this.lastRateHash;
        final boolean contentDue = contentChanged && (this.lastContentSend == Long.MIN_VALUE || now - this.lastContentSend >= 5);
        final boolean ratesDue = ratesChanged && (this.lastRateSend == Long.MIN_VALUE || now - this.lastRateSend >= 20);
        if (!contentDue && !ratesDue && !this.chartAppended) {
            return;
        }
        this.lastContentHash = content;
        this.lastRateHash = rates;
        if (contentDue) {
            this.lastContentSend = now;
        }
        if (ratesDue || contentDue) {
            this.lastRateSend = now;
        }
        this.chartAppended = false;
        pushFace(f, false);
    }

    private void pushFace(InventoryFace f, boolean force) {
        if (!(this.level instanceof ServerLevel server)) {
            return;
        }
        f.seq = ++this.seq;
        boolean fallback = false;
        for (ServerPlayer p : server.getChunkSource().chunkMap.getPlayers(new ChunkPos(this.worldPosition), false)) {
            if (p.connection != null && p.connection.hasChannel(InventoryFacePacket.TYPE)) {
                p.connection.send(new InventoryFacePacket(this.worldPosition, f));
            } else {
                fallback = true;
            }
        }
        if (fallback) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    private static int contentHash(InventoryFace f) {
        int h = f.page.ordinal();
        h = 31 * h + f.title.getString().hashCode();
        h = 31 * h + f.contentSeq;
        h = 31 * h + f.lampsLit * 7 + f.lampsFlashing;
        h = 31 * h + (f.lowRelay ? 1 : 0) + (f.highRelay ? 2 : 0) + f.comparator * 4;
        h = 31 * h + Float.hashCode(f.lowNeedle) + 3 * Float.hashCode(f.highNeedle);
        h = 31 * h + f.lens + 5 * f.source + 11 * f.antennaTier;
        h = 31 * h + Long.hashCode(f.lampTestUntil);
        h = 31 * h + f.lowMode + 3 * f.highMode + 9 * f.stallIndex + 27 * f.chartDays + (f.preferBehind ? 81 : 0)
                + (f.bothSources ? 162 : 0) + 324 * f.markUnit;
        h = 31 * h + f.linkDistance + 7 * f.neededTier + (f.otherDimension ? 1 : 0) + f.lastReadSeconds / 60;
        h = 31 * h + Math.round(f.fill * 1000.0f);
        h = 31 * h + f.lines.hashCode();
        h = 31 * h + Long.hashCode(f.fluidAmount) + Long.hashCode(f.energy / 100L) + 13 * f.energyClock;
        h = 31 * h + f.itemsOnBelt + 3 * f.beltItems.size();
        for (InventoryFace.WatchRow r : f.rows) {
            h = 31 * h + Long.hashCode(r.count()) + Long.hashCode(r.low()) * 3 + Long.hashCode(r.high()) * 7 + r.state();
        }
        h = 31 * h + Float.hashCode(f.chartLow) + Float.hashCode(f.chartHigh) + f.chartScale;
        h = 31 * h + Long.hashCode(f.quantityLow) + 7 * Long.hashCode(f.quantityHigh) + f.quantityKey.hashCode();
        return h;
    }

    private static int rateHash(InventoryFace f) {
        int h = Math.round(f.inPerMinute * 10.0f);
        h = 31 * h + Math.round(f.outPerMinute * 10.0f);
        h = 31 * h + f.clock.ordinal();
        h = 31 * h + Math.round(f.clockSeconds);
        h = 31 * h + f.gatherSeconds;
        h = 31 * h + Math.round(f.passPerMinute * 10.0f) + f.stalledSeconds;
        for (InventoryFace.Mover m : f.risers) {
            h = 31 * h + Math.round(m.perMinute() * 10.0f) + m.icon().getItem().hashCode();
        }
        for (InventoryFace.Mover m : f.fallers) {
            h = 31 * h + Math.round(m.perMinute() * 10.0f) + m.icon().getItem().hashCode();
        }
        for (InventoryFace.Mover m : f.passing) {
            h = 31 * h + Math.round(m.perMinute() * 10.0f);
        }
        h = 31 * h + (int) (f.energyIn + 7 * f.energyOut);
        h = 31 * h + new String(f.slotFlow, java.nio.charset.StandardCharsets.ISO_8859_1).hashCode();
        for (InventoryFace.WatchRow r : f.rows) {
            h = 31 * h + Math.round(r.netPerMinute() * 10.0f) + r.clock().ordinal() + Math.round(r.clockSeconds());
        }
        return h;
    }

    public void addViewer(ServerPlayer player) {
        if (this.level == null) {
            return;
        }
        final boolean fresh = !this.viewers.containsKey(player.getUUID());
        this.viewers.put(player.getUUID(), this.level.getGameTime());
        if (fresh) {
            sendBooks(player, true);
            if (this.serverFace != null) {
                player.connection.send(new InventoryFacePacket(this.worldPosition, this.serverFace));
            }
        }
    }

    public void removeViewer(ServerPlayer player) {
        this.viewers.remove(player.getUUID());
    }

    private void sendBooksToViewers(long now, boolean full) {
        this.lastBooksSend = now;
        if (!(this.level instanceof ServerLevel server)) {
            return;
        }
        final Iterator<Map.Entry<UUID, Long>> it = this.viewers.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<UUID, Long> e = it.next();
            final ServerPlayer p = server.getServer().getPlayerList().getPlayer(e.getKey());
            if (p == null || now - e.getValue() > VIEWER_TIMEOUT_TICKS || p.level() != this.level) {
                it.remove();
                continue;
            }
            sendBooks(p, full);
        }
    }

    private void sendBooks(ServerPlayer player, boolean full) {
        if (player.connection == null || !player.connection.hasChannel(InventoryBooksPacket.TYPE)) {
            return;
        }
        player.connection.send(new InventoryBooksPacket(this.worldPosition, buildBooks(full)));
    }

    private InventoryBooks buildBooks(boolean full) {
        final InventoryBooks b = new InventoryBooks();
        final long now = this.level.getGameTime();
        final InventoryScanner.Reading r = this.lastReading;
        final Map<String, Long> here = r != null && (r.kind == InventoryScanner.Kind.ITEMS
                || r.kind == InventoryScanner.Kind.BELT) ? r.counts : Map.of();
        final List<String> order = new ArrayList<>(here.keySet());
        for (String key : this.ledger.keys()) {
            if (!order.contains(key) && !BinCardLedger.OTHER.equals(key)) {
                order.add(key);
            }
        }
        order.sort((a, c) -> {
            final int x = Long.compare(here.getOrDefault(c, 0L), here.getOrDefault(a, 0L));
            return x != 0 ? x : a.compareTo(c);
        });
        final Map<String, BinCardLedger.Row> cards = new HashMap<>();
        for (BinCardLedger.Row row : this.ledger.export()) {
            cards.put(row.key(), row);
        }
        for (String key : order) {
            if (b.rows.size() >= 64) {
                break;
            }
            final StockFlow.Rate rate = this.flow.rate(key, now);
            final BinCardLedger.Row card = cards.getOrDefault(key,
                    new BinCardLedger.Row(key, 0, 0, 0, 0, 0, 0, 0, 0, false));
            b.rows.add(new InventoryBooks.Row(key, sample(key), here.getOrDefault(key, 0L),
                    (float) rate.inPerMinute(), (float) rate.outPerMinute(), card));
        }
        final BinCardLedger.Card t = this.ledger.total();
        b.total = new BinCardLedger.Row("", t.opening(), t.received(), t.issued(), t.todayIn(), t.todayOut(),
                t.yesterdayIn(), t.yesterdayOut(), t.yesterdayClose(), t.hasYesterday());
        b.today = BinCardLedger.dayOf(this.level.getDayTime());
        b.openedDay = this.ledger.isOpen() ? this.ledger.openedDay() : b.today;
        if (full) {
            b.chartPresent = true;
            b.chartLast = this.chart.lastPeriod();
            b.chartValues = this.chart.exportValues();
            b.chartStamps = this.chart.exportStamps();
            b.chartFlags = this.chart.exportFlags();
        }
        return b;
    }

    public void closeBooks() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        this.ledger.close(BinCardLedger.dayOf(this.level.getDayTime()));
        setChanged();
        sendBooksToViewers(this.level.getGameTime(), false);
    }

    public void settingsChanged() {
        this.lastScanTick = Long.MIN_VALUE;
        setChanged();
    }

    public InventorySettings settings() {
        return this.settings;
    }

    public void setMarks(String markKey, long low, long high) {
        final MarkLadder unit = unitOfKey(markKey);
        this.settings.setMarks(markKey, unit, low, high);
        settingsChanged();
    }

    private MarkLadder unitOfKey(String markKey) {
        if (InventorySettings.CONTAINER.equals(markKey)) {
            return MarkLadder.PERCENT;
        }
        if (InventorySettings.BELT.equals(markKey)) {
            return MarkLadder.PER_MINUTE;
        }
        if (InventorySettings.ENERGY.equals(markKey)) {
            return MarkLadder.ENERGY_PERCENT;
        }
        return MarkLadder.ITEMS;
    }

    @Override
    public AntennaTier getAntennaTier() {
        return this.antennaTier;
    }

    @Override
    public void setAntennaTier(AntennaTier tier) {
        final AntennaTier t = tier == null ? AntennaTier.NONE : tier;
        if (t == this.antennaTier) {
            return;
        }
        this.antennaTier = t;
        this.lastScanTick = Long.MIN_VALUE;
        setChanged();
        sendData();
    }

    public LinkedHashMap<String, Integer> getItemCounts() {
        final LinkedHashMap<String, Integer> out = new LinkedHashMap<>();
        final InventoryScanner.Reading r = this.lastReading;
        if (r == null) {
            return out;
        }
        for (Map.Entry<String, Long> e : r.counts.entrySet()) {
            out.merge(ItemKeys.itemId(e.getKey()), (int) Math.min(Integer.MAX_VALUE, e.getValue()),
                    (a, b) -> (int) Math.min(Integer.MAX_VALUE, (long) a + b));
        }
        return out;
    }

    public LinkedHashMap<String, String> getItemDisplayNames() {
        final LinkedHashMap<String, String> out = new LinkedHashMap<>();
        final InventoryScanner.Reading r = this.lastReading;
        if (r == null) {
            return out;
        }
        for (Map.Entry<String, ItemStack> e : r.samples.entrySet()) {
            final String id = ItemKeys.itemId(e.getKey());
            if (!out.containsKey(id)) {
                final ItemStack plain = new ItemStack(e.getValue().getItem());
                out.put(id, plain.getHoverName().getString());
            }
        }
        return out;
    }

    public long getTotalItemsSeen() {
        final InventoryScanner.Reading r = this.lastReading;
        return r == null ? 0L : r.totalItems;
    }

    public int getTrackedItemTypeCount() {
        return getItemCounts().size();
    }

    public String getTrackingSummaryLabel() {
        final InventoryScanner.Reading r = this.lastReading;
        if (r == null || !r.readable()) {
            return Component.translatable("create_stats.inventory_display.link.nothing").getString();
        }
        return r.name.getString();
    }

    public String getTargetStatusSummary() {
        if (!this.feConsumer.isPowered()) {
            return Component.translatable("create_stats.inventory_display.link.no_power").getString();
        }
        final InventoryScanner.Reading r = this.lastReading;
        if (r == null || !r.readable()) {
            return Component.translatable("create_stats.inventory_display.link.nothing").getString();
        }
        if (r.kind == InventoryScanner.Kind.ITEMS) {
            int used = 0;
            for (ItemStack s : r.slots) {
                if (!s.isEmpty()) {
                    used++;
                }
            }
            return Component.translatable("create_stats.inventory_display.link.items", r.name,
                    com.createstats.inventory.core.FaceFormat.grouped(r.totalItems),
                    r.slotCount > 54 ? Integer.toString(r.counts.size()) : Integer.toString(used),
                    Integer.toString(r.slotCount)).getString();
        }
        return r.name.getString();
    }

    @Nullable
    public InventoryFace serverFace() {
        return this.serverFace;
    }

    private void clientTick() {
        if (this.pendingFaceBytes != null && this.level != null) {
            final byte[] bytes = this.pendingFaceBytes;
            this.pendingFaceBytes = null;
            applyFace(InventoryFace.fromBytes(bytes, this.level.registryAccess()));
        }
    }

    public void applyFace(InventoryFace f) {
        final InventoryFace old = this.clientFace;
        if (old != null && f.seq != 0 && old.seq > f.seq && f.seq > 0 && old.seq - f.seq < 1000) {
            return;
        }
        this.clientFace = f;
        if (f.chartPeriod != Long.MIN_VALUE) {
            if (f.chartGap) {
                this.clientChart.recordGap(f.chartPeriod, f.chartStamp);
            } else {
                this.clientChart.record(f.chartPeriod, f.chartStamp, f.chartValue);
            }
        }
    }

    public void applyBooks(InventoryBooks b) {
        this.clientBooks = b;
        if (b.chartPresent) {
            this.clientChart.restore(b.chartLast, b.chartValues, b.chartStamps, b.chartFlags);
        }
    }

    @Nullable
    public InventoryFace clientFace() {
        return this.clientFace;
    }

    public ChartHistory clientChart() {
        return this.clientChart;
    }

    @Nullable
    public InventoryBooks clientBooks() {
        return this.clientBooks;
    }

    public FilteringBehaviour cup() {
        return this.cup;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) {
            tag.putString("Antenna", this.antennaTier.getSerializedName());
            if (this.serverFace != null && registries instanceof RegistryAccess access) {
                tag.putByteArray("Face", this.serverFace.toBytes(access));
            } else if (this.serverFace != null && this.level != null) {
                tag.putByteArray("Face", this.serverFace.toBytes(this.level.registryAccess()));
            }
            final CompoundTag c = new CompoundTag();
            c.putLong("Last", this.chart.lastPeriod());
            c.putIntArray("V", this.chart.exportValues());
            c.putIntArray("S", this.chart.exportStamps());
            c.putByteArray("F", this.chart.exportFlags());
            tag.put("Chart", c);
            return;
        }
        this.feConsumer.save(tag);
        InventorySave.write(tag, this.settings, this.feeders, this.ledger, this.chart, this.antennaTier, this.penKey,
                this.stall.lastMovement());
        tag.putString("Store", this.storeIdentity);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) {
            this.antennaTier = AntennaTier.byName(tag.getString("Antenna"));
            if (tag.contains("Chart")) {
                final CompoundTag c = tag.getCompound("Chart");
                this.clientChart.restore(c.getLong("Last"), c.getIntArray("V"), c.getIntArray("S"),
                        c.getByteArray("F"));
            }
            if (tag.contains("Face")) {
                final byte[] bytes = tag.getByteArray("Face");
                if (registries instanceof RegistryAccess access) {
                    applyFace(InventoryFace.fromBytes(bytes, access));
                } else if (this.level != null) {
                    applyFace(InventoryFace.fromBytes(bytes, this.level.registryAccess()));
                } else {
                    this.pendingFaceBytes = bytes;
                }
            }
            return;
        }
        this.feConsumer.load(tag);
        final InventorySave.Loaded loaded = InventorySave.read(tag);
        this.settings = loaded.settings;
        this.feeders = loaded.feeders;
        this.ledger = loaded.ledger;
        this.chart = loaded.chart;
        this.antennaTier = loaded.antenna;
        this.penKey = loaded.penKey;
        this.legacyLinkPos = loaded.legacyLinkPos;
        this.storeIdentity = tag.getString("Store");
        if (loaded.stallLastMovement != Long.MIN_VALUE) {
            this.stall.restore(loaded.stallLastMovement);
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (this.level != null && this.level.isClientSide()) {
            ClientScreenAccess.cleanupProductionDetectorTexture(this.worldPosition);
        }
    }

    @Override
    protected AABB createRenderBoundingBox() {
        return new AABB(this.worldPosition).expandTowards(0.0, 2.0, 0.0).inflate(0.25);
    }
}
