package com.createstats.blockentity;

import com.createstats.flight.bridge.SableBridge;
import com.createstats.network.SeismicRereadPacket;
import com.createstats.network.SeismicVeinsPacket;
import com.createstats.power.FeGoggle;
import com.createstats.power.FeGridMember;
import com.createstats.power.FeLock;
import com.createstats.power.FeManagedLoad;
import com.createstats.power.FeManagedState;
import com.createstats.power.FePriority;
import com.createstats.power.FeWireNetwork;
import com.createstats.registry.ModSounds;
import com.createstats.repeller.IntakeLedger;
import com.createstats.seismic.SeismicCatalog;
import com.createstats.seismic.SeismicCharge;
import com.createstats.seismic.SeismicGeometry;
import com.createstats.seismic.SeismicRig;
import com.createstats.seismic.SeismicSurvey;
import com.createstats.seismic.SeismicTargets;
import com.createstats.seismic.SeismicTrace;
import com.createstats.seismic.SeismicWave;
import com.createstats.seismic.VeinBook;
import com.createstats.seismic.VeinOutline;
import com.createstats.seismic.archive.SeismicArchiveHook;
import com.createstats.seismic.archive.SurveyRecall;
import com.createstats.seismic.archive.SurveyReread;
import com.createstats.tuner.TunerHost;
import com.createstats.tuner.TunerRange;
import com.createstats.tuner.TunerStation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

// weight height = stored charge
public class SeismicScannerBE extends SmartBlockEntity implements FeGridMember, FeManagedLoad, TunerHost,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation, com.createstats.util.CopyableSettings {
    public static final TunerRange RANGE = new TunerRange(SeismicCharge.MIN_RADIUS, SeismicCharge.MAX_RADIUS,
            SeismicCharge.RADIUS_STEP, r -> SeismicCharge.WINCH_FE_PER_TICK);

    public static final double WITNESS_RADIUS = 8.0;

    public static final double FELT_RADIUS = 14.0;

    public static final String ITEM_TUNING = "SeismicTuning";

    private static final SurveyReread.Throttle REREADS = new SurveyReread.Throttle();

    private record Sheet(String station, long at, int veins, List<SeismicVeinsPacket> packets) {
    }

    public static final TagKey<Block> COUPLING = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("create_stats", "seismic_coupling"));

    private static final int COUPLING_TICKS = 20;

    private static final int STATIONS_TTL = 600;

    static final int MAX_ECHOES = 48;

    private int stored;
    private String target = "";
    private int radius = SeismicCharge.DEFAULT_RADIUS;
    private final FeManagedState managed = new FeManagedState();

    private String lastTarget = "";
    private int lastReach;
    private int lastVeins = -1;
    private int lastBlocks;
    private int lastNearest = -1;
    private long lastAt = Long.MIN_VALUE;

    private int lastHow;
    private boolean lastFull;

    private int lastFullAt;
    private long lastChecked;
    private long lastPeakNanos;

    @Nullable
    private FeWireNetwork network;
    private final IntakeLedger intake = new IntakeLedger();

    private long takeTick = Long.MIN_VALUE;
    private int takeSum;
    private int ratedDraw;
    private int measuredDraw;
    private double intakeSmoothed;
    @Nullable
    private SeismicSurvey survey;
    private List<UUID> witnesses = List.of();

    private boolean wasPowered;
    private boolean signalSettled;

    private int signalShown = -1;

    private long reportAt = Long.MIN_VALUE;
    private List<UUID> readers = List.of();
    private List<UUID> bystanders = List.of();
    private final Map<UUID, Long> leverClicks = new HashMap<>();

    @Nullable
    private SeismicArchiveHook.Stamp readBackAt;
    private List<Sheet> readBack = List.of();

    @Nullable
    private SeismicArchiveHook.Stamp readingAt;
    private final Map<UUID, Map<String, Long>> waiting = new HashMap<>();
    private int sinceCoupling = COUPLING_TICKS;
    @Nullable
    private List<TunerStation> stations;
    private long stationsAt = Long.MIN_VALUE;
    private int dropCharge;
    private boolean syncDue;
    private int syncCooldown;
    private int chargeSyncCooldown;
    private int lastRatchetTooth = -1;

    private SeismicRig.State state = SeismicRig.State.NO_TARGET;
    private boolean coupled = true;

    private String ground = "";
    private boolean onCraft;
    private long dropAt = Long.MIN_VALUE;
    private float dropHeight;
    private int fallTicks;

    private long impactAt = Long.MIN_VALUE;
    private int surveyReach;
    private float blowStrength;

    private int[] echoes = new int[0];
    private int intakeShown;
    private int surveyFront;
    private int surveyVeins;

    public float weightShown;
    public float weightShownOld;

    public float tickShown = (float) SeismicCharge.heightFor(SeismicCharge.DEFAULT_RADIUS);
    public float tickShownOld = tickShown;

    public float paperDeg;
    public float paperDegOld;

    public long blowAt = Long.MIN_VALUE;
    public double blowShown;
    public int[] blowEchoes = new int[0];

    public long prevBlowAt = Long.MIN_VALUE;
    public double prevBlowShown;
    public int[] prevBlowEchoes = new int[0];

    public final java.util.BitSet echoesHeard = new java.util.BitSet();

    public int toothHeard;

    public float lampGlow;
    public float lampGlowOld;
    public final float[] lampRgb = {1.0f, 0.64f, 0.14f};
    public final float[] lampRgbOld = {1.0f, 0.64f, 0.14f};

    @Nullable
    public String specimenShown;
    public float specimenOut;
    public float specimenOutOld;

    public long syncedAt = Long.MIN_VALUE;

    private final IEnergyStorage energyView = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (level == null || !winchTakes()) {
                return 0;
            }
            final long now = level.getGameTime();
            final int already = takeTick == now ? takeSum : 0;
            final int take = SeismicCharge.winchTake(radius, stored, maxReceive, already);
            if (take <= 0) {
                return 0;
            }
            if (!simulate) {
                if (takeTick != now) {
                    takeTick = now;
                    takeSum = 0;
                }
                takeSum += take;
                stored += take;
                intake.record(now, take);
                setChanged();
            }
            return take;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return stored;
        }

        @Override
        public int getMaxEnergyStored() {
            return SeismicCharge.FULL_BURST;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    };

    public SeismicScannerBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public IEnergyStorage energyHandle() {
        return energyView;
    }

    public Direction facing() {
        final BlockState s = getBlockState();
        return s.hasProperty(HorizontalDirectionalBlock.FACING) ? s.getValue(HorizontalDirectionalBlock.FACING)
                : Direction.NORTH;
    }

    public boolean takesPowerOn(@Nullable Direction side) {
        return side == null || (side.getAxis().isHorizontal() && side != facing());
    }

    public SeismicRig.State state() {
        return state;
    }

    public int stored() {
        return stored;
    }

    public String target() {
        return target;
    }

    public int radius() {
        return radius;
    }

    public boolean coupled() {
        return coupled;
    }

    public double height() {
        return SeismicCharge.heightOf(stored);
    }

    public long dropAt() {
        return dropAt;
    }

    public float dropHeight() {
        return dropHeight;
    }

    public int fallTicks() {
        return fallTicks;
    }

    public long impactAt() {
        return impactAt;
    }

    public int surveyReach() {
        return surveyReach;
    }

    public float blowStrength() {
        return blowStrength;
    }

    public int[] echoes() {
        return echoes;
    }

    public long lastAt() {
        return lastAt;
    }

    public int lastHow() {
        return lastHow;
    }

    public int lastReach() {
        return lastReach;
    }

    public int intakeShown() {
        return intakeShown;
    }

    public boolean falling(long now) {
        return dropAt != Long.MIN_VALUE && now >= dropAt && now < dropAt + fallTicks;
    }

    public long landingAt() {
        return dropAt == Long.MIN_VALUE || fallTicks <= 0 ? Long.MIN_VALUE : dropAt + fallTicks;
    }

    private boolean tuned() {
        return !target.isEmpty();
    }

    private boolean dropping() {
        return state == SeismicRig.State.DROPPING;
    }

    private boolean winchTakes() {
        return SeismicRig.winchTakes(tuned(), coupled, dropping(), managed.isSuppressed(),
                SeismicCharge.ready(radius, stored));
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) {
            return;
        }
        if (level.isClientSide()) {
            com.createstats.seismic.client.SeismicClient.tick(this);
            return;
        }
        if (network == null || network.isDirty()) {
            FeWireNetwork.buildFrom(this);
        }
        if (network != null) {
            network.solveOnce(level);
        }
        final long now = level.getGameTime();

        if (network == null || !network.isPduManaged(now)) {
            setManagedShed(false);
        }
        if (++sinceCoupling >= COUPLING_TICKS) {
            sinceCoupling = 0;
            readCoupling();
        }
        final boolean powered = level.hasNeighborSignal(worldPosition) || level.hasNeighborSignal(worldPosition.above());
        if (SeismicRig.signalFires(powered, wasPowered, signalSettled)) {
            release(null);
        }
        wasPowered = powered;
        signalSettled = true;
        if (state == SeismicRig.State.DROPPING && now >= dropAt + fallTicks) {
            impact(now);
        }
        if (survey != null) {
            stepSurvey(now);
        }
        if (survey == null && reportAt != Long.MIN_VALUE && now >= reportAt) {
            report();
        }

        final int signal = SeismicRig.comparator(stored, SeismicCharge.burstFor(radius));
        if (signal != signalShown) {
            signalShown = signal;
            level.updateNeighbourForOutputSignal(worldPosition.above(), getBlockState().getBlock());
        }

        final SeismicRig.State before = state;
        final int arriving = intake.completedBefore(now);
        state = state == SeismicRig.State.DROPPING ? state
                : SeismicRig.state(tuned(), coupled, false, managed.isSuppressed(), SeismicCharge.ready(radius, stored),
                arriving);
        if (state == SeismicRig.State.READY && before == SeismicRig.State.CHARGING) {
            level.playSound(null, worldPosition, ModSounds.SEISMIC_PAWL.get(), SoundSource.BLOCKS, 0.9f, 1.0f);
        }
        ratedDraw = SeismicRig.rated(tuned(), coupled, dropping(), managed.isForcedOff(), stored, radius);
        measuredDraw = arriving;
        intakeSmoothed += (arriving - intakeSmoothed) * 0.25;
        intakeShown = (int) Math.round(intakeSmoothed);

        if (state != before) {
            syncDue = true;
            syncCooldown = 0;
        }

        if (state == SeismicRig.State.CHARGING && --chargeSyncCooldown <= 0) {
            chargeSyncCooldown = 2;
            syncDue = true;
        }
        final int tooth = SeismicGeometry.tooth(height());
        if (tooth != lastRatchetTooth) {
            lastRatchetTooth = tooth;
            syncDue = true;
        }
        if (syncDue && --syncCooldown <= 0) {
            syncDue = false;
            syncCooldown = 2;
            sendData();
        }
        setChanged();
    }

    private void readCoupling() {
        if (level == null) {
            return;
        }
        final boolean craft = SableBridge.subLevelAccessOf(this) != null;
        final BlockState below = level.getBlockState(worldPosition.below());
        final boolean now = !craft && below.is(COUPLING);
        final String name = BuiltInRegistries.BLOCK.getKey(below.getBlock()).toString();
        if (now != coupled || craft != onCraft || !name.equals(ground)) {
            coupled = now;
            onCraft = craft;
            ground = name;
            syncDue = true;
            syncCooldown = 0;
        }
    }

    public void neighbourChanged() {
        sinceCoupling = COUPLING_TICKS;
    }

    public void pullLever(Player player) {
        if (level == null || level.isClientSide()) {
            return;
        }
        final long now = level.getGameTime();
        final Long last = leverClicks.put(player.getUUID(), now);
        if (!SeismicRig.newPull(last == null ? Long.MIN_VALUE : last, now)) {
            return;
        }
        release(player);
    }

    private void release(@Nullable Player who) {
        if (level == null || state == SeismicRig.State.DROPPING) {
            return;
        }
        final long now = level.getGameTime();
        level.playSound(null, worldPosition, ModSounds.SEISMIC_LEVER.get(), SoundSource.BLOCKS, 0.8f, 1.0f);
        if (!tuned()) {
            return;
        }
        dropAt = now;
        if (stored <= 0) {
            fallTicks = 0;
            dropHeight = 0.0f;
            syncDue = true;
            syncCooldown = 0;
            return;
        }
        dropCharge = stored;
        dropHeight = (float) SeismicCharge.heightOf(stored);
        fallTicks = SeismicCharge.fallTicks(dropHeight);

        blowStrength = (float) SeismicTrace.blowStrength(SeismicCharge.reachOf(stored));
        stored = 0;
        state = SeismicRig.State.DROPPING;

        final int clip = Mth.clamp(fallTicks, 1, SeismicCharge.FULL_FALL_TICKS) - 1;
        level.playSound(null, worldPosition.above(), ModSounds.SEISMIC_FALL.get(clip).get(), SoundSource.BLOCKS, 1.0f, 1.0f);
        syncDue = true;
        syncCooldown = 0;
        setChanged();
    }

    private void impact(long now) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        state = SeismicRig.State.WAITING;
        impactAt = now;
        final int reach = SeismicCharge.surveyRadius(radius, dropCharge);
        final float loud = 0.5f + 0.7f * dropHeight;
        server.playSound(null, worldPosition, ModSounds.SEISMIC_THUD.get(), SoundSource.BLOCKS, loud, 1.0f);
        final BlockState below = server.getBlockState(worldPosition.below());
        final double px = worldPosition.getX() + SeismicGeometry.PLATE_X / 16.0;
        final double pz = worldPosition.getZ() + SeismicGeometry.PLATE_Z / 16.0;
        if (!below.isAir()) {
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), px, worldPosition.getY() + 0.1, pz,
                    8 + Math.round(24 * dropHeight), 0.3, 0.05, 0.3, 0.12);
        }
        server.sendParticles(ParticleTypes.POOF, px, worldPosition.getY() + 0.15, pz, 2 + Math.round(6 * dropHeight),
                0.25, 0.02, 0.25, 0.02);
        echoes = new int[0];
        surveyFront = 0;
        surveyVeins = 0;
        lastTarget = target;
        lastAt = now;
        lastFull = false;
        lastFullAt = 0;
        lastChecked = 0L;
        lastPeakNanos = 0L;
        final SeismicTargets.Target t = SeismicTargets.resolve(server, target);
        if (!coupled) {
            survey = null;
            surveyReach = 0;
            record(2);
        } else if (t == null) {
            survey = null;
            surveyReach = 0;
            record(3);
        } else if (reach <= 0) {
            survey = null;
            surveyReach = 0;
            record(1);
        } else {
            server.playSound(null, worldPosition, ModSounds.SEISMIC_RUMBLE.get(), SoundSource.BLOCKS, loud * 0.8f, 1.0f);
            survey = new SeismicSurvey(server, worldPosition, t, reach);

            com.createstats.seismic.archive.SeismicArchiveHook.begin(server, worldPosition, t.id(), reach, now);
            surveyReach = reach;
            witnesses = witnessesOf(server);

            sendVeins(server, List.of(), SeismicVeinsPacket.OPENS);
            lastReach = reach;
            lastVeins = 0;
            lastBlocks = 0;
            lastNearest = -1;
            lastHow = 0;
        }

        readers = witnessesOf(server);
        bystanders = feltBy(server);
        reportAt = lastHow == 0 ? now + SeismicWave.listenTicks(reach) : now;
        dropCharge = 0;
        syncDue = true;
        syncCooldown = 0;
        setChanged();
    }

    private void record(int how) {
        lastHow = how;
        lastReach = 0;
        lastVeins = -1;
        lastBlocks = 0;
        lastNearest = -1;
    }

    // sable plot is ~20.48M out, raw distance puts every player out of reach. sable tax #2
    @Nullable
    private Vec3 worldMiddle() {
        return SableBridge.worldPointOf(this, new Vec3(worldPosition.getX() + 0.5, worldPosition.getY() + 1.0,
                worldPosition.getZ() + 0.5));
    }

    private static boolean at(ServerPlayer p, Vec3 mid, double radius) {
        return SurveyReread.inReach(p.getX() - mid.x, p.getY() - mid.y, p.getZ() - mid.z, radius);
    }

    private List<UUID> witnessesOf(ServerLevel server) {
        final Vec3 mid = worldMiddle();
        if (mid == null) {
            return List.of();
        }
        final List<UUID> out = new ArrayList<>();
        for (final ServerPlayer p : server.players()) {
            if (!p.isSpectator() && at(p, mid, WITNESS_RADIUS)) {
                out.add(p.getUUID());
            }
        }
        return List.copyOf(out);
    }

    private List<UUID> feltBy(ServerLevel server) {
        final Vec3 mid = worldMiddle();
        if (mid == null) {
            return List.of();
        }
        final List<UUID> out = new ArrayList<>();
        for (final ServerPlayer p : server.players()) {
            if (!p.isSpectator() && !at(p, mid, WITNESS_RADIUS) && at(p, mid, FELT_RADIUS)) {
                out.add(p.getUUID());
            }
        }
        return List.copyOf(out);
    }

    // a null result looked like a failure without this
    private void report() {
        reportAt = Long.MIN_VALUE;
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        final Component line = switch (lastHow) {
            case 1 -> Component.translatable("block.create_stats.seismic_scanner.report.tap");
            case 2 -> Component.translatable("block.create_stats.seismic_scanner.report.loose");
            case 3 -> Component.translatable("block.create_stats.seismic_scanner.report.untuned");
            default -> {
                final Component what = targetComponent(lastTarget);
                final int bottom = SeismicCatalog.bowlBottom(worldPosition.getY(), lastReach);
                if (lastVeins <= 0) {
                    yield Component.translatable("block.create_stats.seismic_scanner.report.none", what, bottom);
                }
                yield lastFullAt > 0
                        ? Component.translatable("block.create_stats.seismic_scanner.report.full", what, lastVeins,
                        lastFullAt, lastReach, lastNearest)
                        : Component.translatable("block.create_stats.seismic_scanner.report.found", what, lastVeins,
                        lastNearest, bottom);
            }
        };

        final Component far = lastHow == 0
                ? Component.translatable("block.create_stats.seismic_scanner.report.too_far", (int) WITNESS_RADIUS)
                : line;
        for (final UUID who : readers) {
            final ServerPlayer p = server.getServer().getPlayerList().getPlayer(who);
            if (p != null && p.serverLevel() == server) {
                p.displayClientMessage(line, true);
            }
        }
        for (final UUID who : bystanders) {
            final ServerPlayer p = server.getServer().getPlayerList().getPlayer(who);
            if (p != null && p.serverLevel() == server) {
                p.displayClientMessage(far, true);
            }
        }
        readers = List.of();
        bystanders = List.of();
    }

    private void stepSurvey(long now) {
        if (!(level instanceof ServerLevel server) || survey == null) {
            return;
        }
        final List<VeinBook.Vein> closed = survey.step();
        com.createstats.seismic.archive.SeismicArchiveHook.step(server, worldPosition, survey, closed);
        surveyFront = (int) Math.min(survey.radius(), SeismicWave.frontAt(survey.tick()));
        if (!closed.isEmpty()) {
            addEchoes(closed);
            sendVeins(server, closed, 0);
            surveyVeins = survey.veins();
            syncDue = true;
        }
        lastVeins = survey.veins();
        lastBlocks = survey.blocks();
        lastNearest = survey.nearest();
        if (survey.full() && !lastFull) {
            lastFullAt = Math.max(1, surveyFront);
        }
        lastFull = survey.full();
        lastChecked = survey.checked();
        lastPeakNanos = survey.peakNanos();
        if (survey.done()) {
            survey = null;
            witnesses = List.of();
            syncDue = true;
            syncCooldown = 0;
        }
        setChanged();
    }

    private void addEchoes(List<VeinBook.Vein> closed) {
        final int have = echoes.length / 2;
        final int room = Math.max(0, MAX_ECHOES - have);
        final int add = Math.min(room, closed.size());
        if (add <= 0) {
            return;
        }
        final int[] grown = Arrays.copyOf(echoes, (have + add) * 2);
        for (int i = 0; i < add; i++) {
            final VeinBook.Vein v = closed.get(i);
            grown[(have + i) * 2] = v.echoTick();
            grown[(have + i) * 2 + 1] = SeismicTrace.echoAmplitude(v.count(), v.nearest());
        }
        echoes = grown;
    }

    private void sendVeins(ServerLevel server, List<VeinBook.Vein> closed, int flags) {
        if (witnesses.isEmpty() || survey == null) {
            return;
        }
        final List<SeismicVeinsPacket> packets = packets(worldPosition, impactAt, survey.target().id(), closed, flags);
        for (final UUID who : witnesses) {
            final ServerPlayer p = server.getServer().getPlayerList().getPlayer(who);
            if (p != null && p.serverLevel() == server) {
                for (final SeismicVeinsPacket pkt : packets) {
                    PacketDistributor.sendToPlayer(p, pkt);
                }
            }
        }
    }

    static List<SeismicVeinsPacket> packets(BlockPos rig, long at, String station, List<VeinBook.Vein> veins, int flags) {
        final int n = veins.size();
        final SeismicVeinsPacket.Vein[] out = new SeismicVeinsPacket.Vein[n];
        final int[] counts = new int[n];
        for (int i = 0; i < n; i++) {
            final VeinBook.Vein v = veins.get(i);
            final int cell = VeinOutline.cellFor(v.count());
            final int[] folded = VeinOutline.cells(v.blocks(), cell);
            out[i] = new SeismicVeinsPacket.Vein(SeismicCatalog.veinLabel(station, v.kind()),
                    SeismicCatalog.colour(station, v.kind()), v.count(), v.echoTick(), (float) v.cx(), (float) v.cy(),
                    (float) v.cz(), cell,
                    folded.length > SeismicVeinsPacket.MAX_CELLS ? Arrays.copyOf(folded, SeismicVeinsPacket.MAX_CELLS) : folded);
            counts[i] = out[i].cells().length;
        }
        final int replay = flags & SeismicVeinsPacket.REPLAY;
        final List<SeismicVeinsPacket> packets = new ArrayList<>();
        for (final int[] b : SurveyReread.batches(counts, SeismicVeinsPacket.MAX_VEINS, SeismicVeinsPacket.MAX_CELLS)) {
            packets.add(new SeismicVeinsPacket(rig, at, station, packets.isEmpty() ? flags : replay,
                    List.copyOf(Arrays.asList(Arrays.copyOfRange(out, b[0], b[1])))));
        }
        if (packets.isEmpty() && (flags & SeismicVeinsPacket.OPENS) != 0) {
            packets.add(new SeismicVeinsPacket(rig, at, station, flags, List.of()));
        }
        return packets;
    }

    public void reread(ServerPlayer player, List<SeismicRereadPacket.Held> held) {
        if (!(level instanceof ServerLevel server) || player.isSpectator() || player.serverLevel() != server) {
            return;
        }
        if (survey != null) {
            return;
        }
        final Vec3 mid = worldMiddle();
        if (mid == null || !at(player, mid, WITNESS_RADIUS)) {
            return;
        }
        if (!REREADS.allow(player.getUUID(), server.getServer().getTickCount())) {
            return;
        }
        final Map<String, Long> holds = new HashMap<>();
        for (final SeismicRereadPacket.Held h : held) {
            holds.merge(h.station(), h.at(), Math::max);
        }
        final SeismicArchiveHook.Stamp now = SeismicArchiveHook.stamp(server, worldPosition);
        if (now.same(readBackAt)) {
            answer(server, player, holds);
            return;
        }
        waiting.put(player.getUUID(), holds);
        if (now.same(readingAt)) {
            return;
        }
        readingAt = now;
        final BlockPos rig = worldPosition;
        SeismicArchiveHook.readBack(server, rig, recalls -> {
            final List<Sheet> sheets = new ArrayList<>(recalls.size());
            for (final SurveyRecall r : recalls) {
                sheets.add(new Sheet(r.target(), r.at(), r.veins().size(), packets(rig, r.at(), r.target(), r.veins(),
                        SeismicVeinsPacket.OPENS | SeismicVeinsPacket.REPLAY)));
            }
            return List.copyOf(sheets);
        }, sheets -> readBackDone(now, sheets));
    }

    private void readBackDone(SeismicArchiveHook.Stamp stamp, @Nullable List<Sheet> sheets) {
        if (readingAt != null && !stamp.same(readingAt)) {
            return;
        }
        readingAt = null;
        if (sheets == null || isRemoved() || !(level instanceof ServerLevel server)) {
            waiting.clear();
            return;
        }
        readBackAt = stamp;
        readBack = sheets;
        final Vec3 mid = worldMiddle();
        for (final Map.Entry<UUID, Map<String, Long>> e : waiting.entrySet()) {
            final ServerPlayer p = server.getServer().getPlayerList().getPlayer(e.getKey());

            if (p != null && p.serverLevel() == server && mid != null && at(p, mid, WITNESS_RADIUS)) {
                answer(server, p, e.getValue());
            }
        }
        waiting.clear();
    }

    private void answer(ServerLevel server, ServerPlayer player, Map<String, Long> holds) {
        final List<Sheet> send = SurveyReread.toSend(readBack, Sheet::station, Sheet::at, holds);
        for (final Sheet s : send) {
            for (final SeismicVeinsPacket pkt : s.packets()) {
                PacketDistributor.sendToPlayer(player, pkt);
            }
        }
        final long now = server.getGameTime();
        final Component line;
        if (send.isEmpty()) {
            line = holds.isEmpty() ? Component.translatable("block.create_stats.seismic_scanner.report.reread_none") : null;
        } else if (send.size() == 1) {
            final Sheet s = send.get(0);
            line = s.veins() > 0
                    ? Component.translatable("block.create_stats.seismic_scanner.report.reread_one",
                    targetComponent(s.station()), s.veins(), agoComponent(now - s.at()))
                    : Component.translatable("block.create_stats.seismic_scanner.report.reread_one_none",
                    targetComponent(s.station()), agoComponent(now - s.at()));
        } else {
            line = Component.translatable("block.create_stats.seismic_scanner.report.reread_many", send.size(),
                    targetComponent(send.get(0).station()));
        }
        if (line != null) {
            player.displayClientMessage(line, true);
        }
    }

    @Override
    public TunerRange tunerRange() {
        return RANGE;
    }

    @Override
    public Component rangeLabel(@Nullable TunerStation station, int range) {
        return Component.translatable("block.create_stats.seismic_scanner.tuner.range", range);
    }

    @Override
    public Component costLabel(int range, boolean untuned) {
        return Component.translatable("block.create_stats.seismic_scanner.tuner.cost",
                String.format(Locale.ROOT, "%,d", untuned ? 0 : SeismicCharge.burstFor(range)));
    }

    @Override
    @Nullable
    public Component tunerNote(@Nullable TunerStation station, int range) {
        if (!coupled) {
            return Component.translatable("block.create_stats.seismic_scanner.tuner.no_ground");
        }
        final String id = station == null ? "" : station.id();
        return SeismicCatalog.bandMissed(id, worldPosition.getY(), range)
                ? Component.translatable("block.create_stats.seismic_scanner.tuner.band", SeismicCatalog.densest(id))
                : null;
    }

    @Override
    public Component stationLine(TunerStation station, int range, boolean compact) {
        final int bottom = SeismicCatalog.bowlBottom(worldPosition.getY(), SeismicCharge.clampRadius(range));
        return compact ? Component.translatable("block.create_stats.seismic_scanner.tuner.depth", bottom)
                : Component.translatable("block.create_stats.seismic_scanner.tuner.depth_wide", station.subLabel(), bottom);
    }

    @Override
    public boolean tunesAt(BlockPos clicked, net.minecraft.world.phys.Vec3 hit) {
        final double x = (hit.x - worldPosition.getX()) * 16.0;
        final double y = (hit.y - worldPosition.getY()) * 16.0;
        final double z = (hit.z - worldPosition.getZ()) * 16.0;
        return !SeismicGeometry.onLever(quarter(facing()), x, y, z);
    }

    @Override
    public String tunedStation() {
        return target;
    }

    @Override
    public int tunedRange() {
        return radius;
    }

    @Override
    public List<TunerStation> surveyStations(ServerPlayer player) {
        if (level == null) {
            return List.of();
        }
        final long now = level.getGameTime();
        if (stations == null || now - stationsAt > STATIONS_TTL || now < stationsAt) {
            stations = SeismicTargets.stations(level);
            stationsAt = now;
        }
        if (target.isEmpty() || stations.stream().anyMatch(s -> s.id().equals(target))) {
            return stations;
        }

        final List<TunerStation> out = new ArrayList<>(stations);
        out.add(new TunerStation(target, target, target, "", 0xFF808080, 0, 0, out.size()));
        return out;
    }

    @Override
    public void applyTuning(ServerPlayer player, String wanted, int range) {
        if (level == null || level.isClientSide()) {
            return;
        }
        retune(wanted, range);
    }

    private boolean retune(@Nullable String wanted, int range) {
        if (level == null) {
            return false;
        }
        final String id = wanted == null ? "" : wanted;
        if (!SeismicTargets.valid(level, id)) {
            return false;
        }
        final int r = SeismicCharge.clampRadius(range);
        if (id.equals(target) && r == radius) {
            return false;
        }
        final boolean retuned = !id.equals(target);
        target = id;
        radius = r;
        level.playSound(null, worldPosition, ModSounds.SEISMIC_RATCHET.get(), SoundSource.BLOCKS,
                retuned ? 0.7f : 0.45f, retuned ? 0.8f : 1.15f);
        syncDue = true;
        syncCooldown = 0;
        setChanged();
        return true;
    }

    public static int quarter(Direction facing) {
        return switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }

    @Override
    public String settingsKey() {
        return "create_stats:seismic_scanner";
    }

    @Override
    public void writeSettings(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("Target", target);
        tag.putInt("Radius", radius);
    }

    @Override
    public boolean applySettings(CompoundTag tag, HolderLookup.Provider registries, @Nullable Player player,
                                 boolean simulate) {
        if (!tag.contains("Target") || !tag.contains("Radius") || level == null) {
            return false;
        }
        final String wanted = tag.getString("Target");
        if (!SeismicTargets.valid(level, wanted)) {
            return false;
        }
        if (simulate) {
            return true;
        }
        if (alreadyHolds(tag, registries)) {
            return true;
        }
        return retune(wanted, tag.getInt("Radius"));
    }

    @Override
    public void writeFreshDefaults(CompoundTag tag) {
        tag.putInt("Tier", FePriority.DEFAULT.ordinal());
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        writeFreshDefaults(tag);
        writeSettings(tag, registries);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (!target.isEmpty()) {
            final CompoundTag tuning = new CompoundTag();
            writeSettings(tuning, null);
            final CompoundTag data = new CompoundTag();
            data.put(ITEM_TUNING, tuning);
            builder.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        final CustomData data = input.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(ITEM_TUNING)) {
            return;
        }
        final CompoundTag tuning = data.copyTag().getCompound(ITEM_TUNING);
        final String wanted = tuning.getString("Target");
        if (level == null || SeismicTargets.valid(level, wanted)) {
            target = wanted;
            radius = SeismicCharge.clampRadius(tuning.getInt("Radius"));
            syncDue = true;
            syncCooldown = 0;
        }
    }

    @Nullable
    public static Component tuningLine(ItemStack stack, @Nullable Level level) {
        final CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(ITEM_TUNING)) {
            return null;
        }
        final CompoundTag tuning = data.copyTag().getCompound(ITEM_TUNING);
        final String id = tuning.getString("Target");
        if (id.isEmpty()) {
            return null;
        }
        return Component.translatable("block.create_stats.seismic_scanner.tooltip.tuned", targetComponent(level, id),
                SeismicCharge.clampRadius(tuning.getInt("Radius")));
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(FeGoggle.title(Component.translatable("block.create_stats.seismic_scanner")));
        final long now = level == null ? 0L : level.getGameTime();
        final int burst = SeismicCharge.burstFor(radius);
        final int pct = burst <= 0 ? 0 : (int) Math.min(100, Math.round(100.0 * stored / burst));

        final int bottom = SeismicCatalog.bowlBottom(worldPosition.getY(), radius);
        tooltip.add(switch (state) {
            case READY -> FeGoggle.ok(tr("goggle.ready", radius));
            case CHARGING -> FeGoggle.ok(intakeShown > 0
                    ? tr("goggle.charging_eta", pct, FeGoggle.time(SeismicCharge.ticksToCharge(stored, radius, intakeShown)))
                    : tr("goggle.charging", pct));
            case WAITING -> FeGoggle.warn(tr("goggle.waiting"));
            case SHED -> FeGoggle.warn(tr("goggle.shed", pct));
            case DROPPING -> FeGoggle.warn(tr("goggle.dropping"));
            case NO_GROUND -> FeGoggle.bad(onCraft ? tr("goggle.on_craft") : tr("goggle.no_ground", groundName()));
            default -> FeGoggle.warn(tr("goggle.no_target"));
        });
        tooltip.add(FeGoggle.dim(target.isEmpty() ? tr("goggle.target_none") : tr("goggle.target", targetName())));
        tooltip.add(FeGoggle.dim(tr("goggle.radius", radius, bottom)));
        if (SeismicCatalog.bandMissed(target, worldPosition.getY(), radius)) {
            tooltip.add(FeGoggle.warn(tr("goggle.band", SeismicCatalog.densest(target))));
        }
        tooltip.add(FeGoggle.amount(tr("goggle.charge"), stored, burst));
        tooltip.add(FeGoggle.draw(measuredDraw));
        if (surveyReach > 0 && impactAt != Long.MIN_VALUE && now - impactAt <= SeismicWave.listenTicks(surveyReach)) {
            tooltip.add(FeGoggle.ok(tr("goggle.surveying", surveyFront, surveyReach, surveyVeins)));
        }
        if (lastAt != Long.MIN_VALUE) {
            tooltip.add(FeGoggle.dim(lastLine(now)));
        }

        if (level != null && level.isClientSide() && com.createstats.seismic.client.SeismicClient.mayReadBack(this)) {
            tooltip.add(FeGoggle.ok(tr("goggle.reread")));
        }
        if (isPlayerSneaking) {
            if (coupled) {
                tooltip.add(FeGoggle.dim(tr("goggle.coupled", groundName())));
            }
            if (stored > 0) {
                tooltip.add(FeGoggle.dim(tr("goggle.take_up")));
            }

            tooltip.add(FeGoggle.dim(tr("goggle.display_link",
                    Component.translatable("create_stats.display_source.seismic_scanner"))));

            if (lastChecked > 0 && level != null && level.isClientSide()
                    && com.createstats.seismic.client.SeismicClient.advanced()) {
                tooltip.add(FeGoggle.dim(tr("goggle.cost", String.format(Locale.ROOT, "%,d", lastChecked),
                        String.format(Locale.ROOT, "%.2f", lastPeakNanos / 1.0e6))));
            }
        } else {
            tooltip.add(FeGoggle.more());
        }
        return true;
    }

    private String lastLine(long now) {
        final String ago = ago(now - lastAt);
        return switch (lastHow) {
            case 1 -> tr("goggle.last_tap", ago);
            case 2 -> tr("goggle.last_loose", ago);
            case 3 -> tr("goggle.last_untuned", ago);
            default -> lastVeins <= 0
                    ? tr("goggle.last_none", SeismicCatalog.bowlBottom(worldPosition.getY(), lastReach), ago)
                    : lastFullAt > 0 ? tr("goggle.last_full", lastVeins, lastFullAt, lastReach, ago)
                    : tr("goggle.last", lastVeins, String.format(Locale.ROOT, "%,d", lastBlocks), lastNearest, ago);
        };
    }

    private static String ago(long ticks) {
        final long s = Math.max(0L, ticks) / 20L;
        if (s < 60L) {
            return tr("time.seconds", s);
        }
        return s < 3600L ? tr("time.minutes", s / 60L) : tr("time.hours", s / 3600L);
    }

    private static Component agoComponent(long ticks) {
        final long s = Math.max(0L, ticks) / 20L;
        final String p = "block.create_stats.seismic_scanner.time.";
        if (s < 60L) {
            return Component.translatable(p + "seconds", s);
        }
        return s < 3600L ? Component.translatable(p + "minutes", s / 60L) : Component.translatable(p + "hours", s / 3600L);
    }

    private String targetName() {
        return targetComponent(target).getString();
    }

    private Component targetComponent(String id) {
        return targetComponent(level, id);
    }

    private static Component targetComponent(@Nullable Level level, String id) {
        if (SeismicCatalog.VOIDS.equals(id)) {
            return Component.translatable("block.create_stats.seismic_scanner.target.voids");
        }
        if (SeismicCatalog.LIQUIDS.equals(id)) {
            return Component.translatable("block.create_stats.seismic_scanner.target.liquids");
        }
        final Block sample = level == null ? null : SeismicTargets.sample(level, id);
        return sample == null ? Component.literal(id) : sample.getName();
    }

    private String groundName() {
        final ResourceLocation id = ResourceLocation.tryParse(ground);
        final Block b = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        return b == null ? ground : b.getName().getString();
    }

    private static String tr(String key, Object... args) {
        return Component.translatable("block.create_stats.seismic_scanner." + key, args).getString();
    }

    @Override
    public int ratedDrawFePerTick() {
        return ratedDraw;
    }

    @Override
    public int measuredDrawFePerTick() {
        return measuredDraw;
    }

    @Override
    public FePriority managedPriority() {
        return managed.priority();
    }

    @Override
    public void setManagedPriority(FePriority tier) {
        managed.setPriority(tier);
        setChanged();
    }

    @Override
    public FeLock managedLock() {
        return managed.lock();
    }

    @Override
    public void setManagedLock(FeLock lock) {
        managed.setLock(lock);
        setChanged();
    }

    @Override
    public void setManagedShed(boolean shed) {
        managed.setShed(shed);
    }

    @Override
    public boolean isManagedShed() {
        return managed.isShed();
    }

    @Override
    public long nodeKey() {
        return worldPosition.asLong();
    }

    @Override
    public int storedEnergy() {
        return 0;
    }

    @Override
    public void setStoredEnergy(int amount) {
    }

    @Override
    public int bufferCapacity() {
        return 0;
    }

    @Override
    public int transferRate() {
        return SeismicCharge.WINCH_FE_PER_TICK;
    }

    @Override
    public IEnergyStorage attachedStorage() {
        return energyView;
    }

    @Override
    public boolean isGone(net.minecraft.world.level.Level owner) {
        return isRemoved() || getLevel() != owner;
    }

    @Override
    public void setLastFlow(int flow) {
    }

    @Override
    public void adoptNetwork(FeWireNetwork network) {
        this.network = network;
    }

    @Override
    @Nullable
    public FeWireNetwork network() {
        return network;
    }

    @Override
    public void invalidateNetwork() {
        if (network != null) {
            network.markDirty();
            network = null;
        }
    }

    @Override
    public void remove() {
        invalidateNetwork();
        super.remove();
    }

    public AABB renderBounds() {
        return new AABB(worldPosition).expandTowards(0, 1, 0).inflate(0.25);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Energy", stored);
        tag.putString("Target", target);
        tag.putInt("Radius", radius);
        tag.putInt("Tier", managed.priorityOrdinal());
        tag.putInt("Lock", managed.lockOrdinal());
        tag.putString("LastTarget", lastTarget);
        tag.putInt("LastReach", lastReach);
        tag.putInt("LastVeins", lastVeins);
        tag.putInt("LastBlocks", lastBlocks);
        tag.putInt("LastNearest", lastNearest);
        tag.putLong("LastAt", lastAt);
        tag.putInt("LastHow", lastHow);
        tag.putBoolean("LastFull", lastFull);
        tag.putInt("LastFullAt", lastFullAt);
        tag.putLong("LastChecked", lastChecked);
        tag.putLong("LastPeak", lastPeakNanos);
        if (!clientPacket) {
            tag.putBoolean("Powered", wasPowered);

            if (state == SeismicRig.State.DROPPING) {
                tag.putLong("FallFrom", dropAt);
                tag.putInt("FallTicks", fallTicks);
                tag.putInt("FallCharge", dropCharge);
                tag.putFloat("FallHeight", dropHeight);
                tag.putFloat("FallBlow", blowStrength);
            }
        }
        if (clientPacket) {
            tag.putInt("State", state.ordinal());
            tag.putBoolean("Coupled", coupled);
            tag.putString("Ground", ground);
            tag.putBoolean("OnCraft", onCraft);
            tag.putLong("DropAt", dropAt);
            tag.putFloat("DropHeight", dropHeight);
            tag.putInt("Fall", fallTicks);
            tag.putLong("ImpactAt", impactAt);
            tag.putInt("Reach", surveyReach);
            tag.putFloat("Blow", blowStrength);
            tag.put("Echoes", new IntArrayTag(echoes));
            tag.putInt("Getting", intakeShown);
            tag.putInt("Draw", measuredDraw);
            tag.putInt("Front", surveyFront);
            tag.putInt("FoundVeins", surveyVeins);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        stored = Mth.clamp(tag.getInt("Energy"), 0, SeismicCharge.FULL_BURST);
        target = tag.getString("Target");
        radius = tag.contains("Radius") ? SeismicCharge.clampRadius(tag.getInt("Radius")) : SeismicCharge.DEFAULT_RADIUS;
        managed.load(tag.getInt("Tier"), tag.getInt("Lock"));
        lastTarget = tag.getString("LastTarget");
        lastReach = tag.getInt("LastReach");
        lastVeins = tag.contains("LastVeins") ? tag.getInt("LastVeins") : -1;
        lastBlocks = tag.getInt("LastBlocks");
        lastNearest = tag.contains("LastNearest") ? tag.getInt("LastNearest") : -1;
        lastAt = tag.contains("LastAt") ? tag.getLong("LastAt") : Long.MIN_VALUE;
        lastHow = tag.getInt("LastHow");
        lastFull = tag.getBoolean("LastFull");
        lastFullAt = tag.getInt("LastFullAt");
        lastChecked = tag.getLong("LastChecked");
        lastPeakNanos = tag.getLong("LastPeak");
        if (!clientPacket) {
            wasPowered = tag.getBoolean("Powered");
            if (tag.contains("FallFrom")) {
                state = SeismicRig.State.DROPPING;
                dropAt = tag.getLong("FallFrom");
                fallTicks = tag.getInt("FallTicks");
                dropCharge = tag.getInt("FallCharge");
                dropHeight = tag.getFloat("FallHeight");
                blowStrength = tag.getFloat("FallBlow");
            }
        }

        if (clientPacket || tag.contains("State")) {
            state = SeismicRig.State.of(tag.getInt("State"));
            coupled = !tag.contains("Coupled") || tag.getBoolean("Coupled");
            ground = tag.getString("Ground");
            onCraft = tag.getBoolean("OnCraft");
            dropAt = tag.contains("DropAt") ? tag.getLong("DropAt") : Long.MIN_VALUE;
            dropHeight = tag.getFloat("DropHeight");
            fallTicks = tag.getInt("Fall");
            impactAt = tag.contains("ImpactAt") ? tag.getLong("ImpactAt") : Long.MIN_VALUE;
            surveyReach = tag.getInt("Reach");
            blowStrength = tag.getFloat("Blow");
            echoes = tag.getIntArray("Echoes");
            intakeShown = tag.getInt("Getting");
            measuredDraw = tag.getInt("Draw");
            surveyFront = tag.getInt("Front");
            surveyVeins = tag.getInt("FoundVeins");
        }
        if (clientPacket && level != null && level.isClientSide()) {
            syncedAt = level.getGameTime();
        }
    }
}
