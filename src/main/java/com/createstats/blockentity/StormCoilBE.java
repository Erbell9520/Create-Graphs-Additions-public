package com.createstats.blockentity;

import com.createstats.block.StormCoilBlock;
import com.createstats.block.StormCoilPartBlock;
import com.createstats.block.StormCoilValueBox;
import com.createstats.power.FeGoggle;
import com.createstats.power.FeGridMember;
import com.createstats.power.FeGridNode;
import com.createstats.power.FeRates;
import com.createstats.power.FeStudPanel;
import com.createstats.power.FeWireNetwork;
import com.createstats.power.FeWireTerminal;
import com.createstats.power.PduReason;
import com.createstats.power.WireTier;
import com.createstats.registry.ModBlocks;
import com.createstats.registry.ModSounds;
import com.createstats.security.Clearance;
import com.createstats.security.CoilDevice;
import com.createstats.security.CoilGate;
import com.createstats.security.CoilReading;
import com.createstats.security.CoilStatus;
import com.createstats.security.CoilWords;
import com.createstats.security.PanelSweep;
import com.createstats.security.SecurityIdentity;
import com.createstats.security.SecurityLink;
import com.createstats.security.SecurityLog;
import com.createstats.security.SecurityRoster;
import com.createstats.stormcoil.CoilAim;
import com.createstats.stormcoil.CoilFit;
import com.createstats.stormcoil.CoilGaze;
import com.createstats.stormcoil.CoilLaw;
import com.createstats.stormcoil.CoilMotion;
import com.createstats.stormcoil.CoilRules;
import com.createstats.stormcoil.CoilStage;
import com.createstats.stormcoil.CoilStores;
import com.createstats.stormcoil.CoilTargets;
import com.createstats.stormcoil.CoilWatch;
import com.createstats.stormcoil.CoilZapMemory;
import com.createstats.stormcoil.StormCoilIndex;
import com.createstats.stormcoil.StormCoilStuds;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.gui.AllIcons;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// stud panel, never a member. the breaker lesson
public class StormCoilBE extends SmartBlockEntity implements FeStudPanel, IHaveGoggleInformation,
        com.createstats.power.FeSupply, com.createstats.power.FeRatedLoad, com.createstats.power.FeStationLoad,
        com.createstats.power.SecurityDevice, CoilDevice {
    public static final int MAX_LINKS_PER_STUD = 2;

    private static final int FIT_EVERY = 10;
    private static final int SHARED_EVERY = 5;
    private static final int CORONA_EVERY = 20;

    public static final int ARC_TICKS = 6;

    public static final int STRIKE_FLOOD_TICKS = 40;

    private static final BehaviourType<GearScrollOptionBehaviour<TargetOption>> TARGETS_TYPE = new BehaviourType<>();
    private static final BehaviourType<FlywheelRpmScrollBehaviour> HEIGHT_TYPE = new BehaviourType<>();

    public enum TargetOption implements INamedIconOptions {
        HOSTILE(AllIcons.I_TARGET),
        TRANSFORM(AllIcons.I_REFRESH),
        STORM_ONLY(AllIcons.I_PASSIVE);

        private final AllIcons icon;

        TargetOption(AllIcons icon) {
            this.icon = icon;
        }

        @Override
        public AllIcons getIcon() {
            return icon;
        }

        @Override
        public String getTranslationKey() {
            return "create_stats.storm_coil.targets." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public record StudLink(BlockPos to, WireTier tier) {
    }

    private final List<List<StudLink>> studLinks = List.of(new ArrayList<>(), new ArrayList<>());
    private final CoilStores stores = new CoilStores();
    private final CoilZapMemory memory = new CoilZapMemory();
    private final Port inPort = new Port(StormCoilStuds.IN);
    private final Port outPort = new Port(StormCoilStuds.OUT);

    private GearScrollOptionBehaviour<TargetOption> targets;
    private FlywheelRpmScrollBehaviour height;

    private int stage = 1;
    private int blockedAt;
    private String blocker = "";
    private boolean redstone;
    private boolean shared;
    private boolean corona;
    private boolean releasing;
    private boolean fitDirty = true;
    private boolean dutyWasSource;
    private long lastStrike = Long.MIN_VALUE;
    private long lastZap = Long.MIN_VALUE;
    private float zapX;
    private float zapY;
    private float zapZ;
    private int strikes;

    private final CoilAim aim = new CoilAim();

    private int[] watch = new int[0];

    private long syncedAimAt = Long.MIN_VALUE;
    private int[] syncedAimIds = new int[0];

    private long aimDroppedAt = Long.MIN_VALUE;
    private int[] droppedAimIds = new int[0];

    private final float[] volleyAt = new float[3 * 4];
    private int volleySize;

    private long nextCall;

    private boolean primed;

    private int syncedCapacitor;
    private int syncedCapacitorSize = CoilStage.ONE.capacitorSize();
    private int syncedBank;
    private int syncedBankSize = CoilLaw.bankSize(CoilLaw.DEFAULT_BASE);
    private int syncedIn;
    private int syncedOut;
    private int lastSentCapacitor = -1;
    private int lastSentBank = -1;
    private int lastSentIn = -1;
    private int lastSentOut = -1;

    @Nullable
    private com.createstats.power.FeStationFeed boardFeed;

    private int syncedBoardStud = -1;
    private boolean syncedBoardLive;
    private int syncedBoardGive;
    private int syncedBoardCarried;
    private int syncedBoardCost;
    private int syncedBoardReason;
    private int lastSentBoard = Integer.MIN_VALUE;

    private static final int POLICY_TICKS = 40;

    private static final int REFUSAL_LOG_GAP = 20;

    private static final int FIGHT_COUNT_CAP = 256;

    @Nullable
    private BlockPos panelPos;

    @Nullable
    private UUID panelHousehold;

    @Nullable
    private BlockPos offeredPanel;

    @Nullable
    private SecurityRoster roster;

    private int required = Clearance.DEFAULT_REQUIRED;

    private int sincePolicy = POLICY_TICKS;

    private boolean governed;

    private boolean panelOff;

    private boolean locked;

    private boolean deaf;

    private int rules = -1;

    private int houseState;

    private String houseName = "";

    private String lastArc = "";

    private long fightStart = Long.MIN_VALUE;
    private int fightVolleys;
    private final Set<UUID> fightTargets = new HashSet<>();
    private final Set<UUID> fightPlayers = new HashSet<>();

    @Nullable
    private UUID refusedWho;
    private long refusedAt;

    public final CoilMotion motion = new CoilMotion();

    public final CoilGaze gaze = new CoilGaze();

    public StormCoilBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        targets = new TargetsBox(this);
        targets.withCallback(i -> aim.reset());
        behaviours.add(targets);
        height = new HeightBox(this);
        height.between(1, 4);
        height.withFormatter(v -> "Stage " + Math.max(1, v));
        height.withCallback(v -> fitDirty = true);
        behaviours.add(height);
        relabel();
    }

    private static final class TargetsBox extends GearScrollOptionBehaviour<TargetOption> {
        private final StormCoilBE coil;

        TargetsBox(StormCoilBE coil) {
            super(TargetOption.class, Component.translatable("create_stats.storm_coil.targets"), coil,
                    new StormCoilValueBox(StormCoilValueBox.Slot.TARGETS), TARGETS_TYPE, "Targets", 0);
            this.coil = coil;
        }

        @Override
        public void setValueSettings(Player player, ValueSettingsBehaviour.ValueSettings settings, boolean ctrlDown) {
            if (!coil.refuse(player, CoilGate.Box.TARGETS)) {
                super.setValueSettings(player, settings, ctrlDown);
            }
        }
    }

    private static final class HeightBox extends FlywheelRpmScrollBehaviour {
        private final StormCoilBE coil;

        HeightBox(StormCoilBE coil) {
            super(Component.translatable("create_stats.storm_coil.height"), coil,
                    new StormCoilValueBox(StormCoilValueBox.Slot.HEIGHT), HEIGHT_TYPE, "Height", 1);
            this.coil = coil;
        }

        @Override
        public void setValueSettings(Player player, ValueSettingsBehaviour.ValueSettings settings, boolean ctrlDown) {
            if (!coil.refuse(player, CoilGate.Box.HEIGHT)) {
                super.setValueSettings(player, settings, ctrlDown);
            }
        }
    }

    public CoilTargets.Mode mode() {
        return CoilTargets.Mode.of(targets == null ? 0 : targets.get().ordinal());
    }

    public int requestedStage() {
        return height == null ? 1 : Math.max(1, Math.min(4, height.getValue()));
    }

    public int stage() {
        return stage;
    }

    public CoilStage coilStage() {
        return CoilStage.of(stage);
    }

    public boolean isReleasing() {
        return releasing;
    }

    public boolean isShared() {
        return shared;
    }

    public boolean corona() {
        return corona;
    }

    public long lastZap() {
        return lastZap;
    }

    public long lastStrike() {
        return lastStrike;
    }

    public Vec3 zapTarget() {
        return new Vec3(zapX, zapY, zapZ);
    }

    public int volleySize() {
        return Math.max(1, volleySize);
    }

    public Vec3 volleyTarget(int i) {
        if (i <= 0 || i >= volleySize) {
            return zapTarget();
        }
        return new Vec3(volleyAt[3 * i], volleyAt[3 * i + 1], volleyAt[3 * i + 2]);
    }

    public boolean primed() {
        return primed;
    }

    public List<CoilWatch.Entry> watched() {
        return CoilWatch.unpack(watch);
    }

    public long aimAt() {
        return syncedAimAt;
    }

    public int[] aimIds() {
        return syncedAimIds.clone();
    }

    public long aimDroppedAt() {
        return aimDroppedAt;
    }

    public int[] droppedAimIds() {
        return droppedAimIds.clone();
    }

    public boolean windingUp(long now) {
        return syncedAimAt != Long.MIN_VALUE && now >= syncedAimAt && now - syncedAimAt < CoilLaw.WIND_UP + 20;
    }

    public int blockedAt() {
        return blockedAt;
    }

    public double inShare() {
        if (syncedIn <= 0) {
            return 0.0;
        }
        final CoilStage st = coilStage();
        return Math.min(1.0, syncedIn / (double) Math.max(1, CoilLaw.volleyRate(st.zapCost, st.targets())));
    }

    public boolean[] targetLamps() {
        return com.createstats.stormcoil.CoilCabinet.targetLamps(policy());
    }

    @Nullable
    public LivingEntity resolve(int id) {
        if (level == null) {
            return null;
        }
        if (level instanceof net.createmod.catnip.levelWrappers.SchematicLevel scene) {
            for (Entity e : scene.getEntityList()) {
                if (e.getId() == id && e instanceof LivingEntity living) {
                    return living;
                }
            }
            return null;
        }
        return level.getEntity(id) instanceof LivingEntity living ? living : null;
    }

    public int shownCapacitor() {
        return syncedCapacitor;
    }

    public int shownCapacitorSize() {
        return Math.max(1, syncedCapacitorSize);
    }

    public int shownBank() {
        return syncedBank;
    }

    public int shownBankSize() {
        return Math.max(1, syncedBankSize);
    }

    public int shownIn() {
        return syncedIn;
    }

    public int shownOut() {
        return syncedOut;
    }

    public boolean heldFromArcing() {
        return (redstone && !deaf) || panelOff;
    }

    public void setRedstone(boolean powered) {
        if (redstone != powered) {
            redstone = powered;
            sendData();
        }
    }

    private static int base() {
        return PowerConnectorBE.baseTransferRate();
    }

    private CoilStores.Terms terms() {
        return CoilLaw.terms(coilStage(), zapsAtAll(), base());
    }

    private long now() {
        return level == null ? 0L : level.getGameTime();
    }

    public BlockPos crown() {
        return worldPosition.above(stage - 1);
    }

    // ---------------------------- Ticking ----------------------------

    @Override
    public void initialize() {
        super.initialize();
        StormCoilIndex.add(level, worldPosition);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) {
            return;
        }
        if (level.isClientSide()) {
            clientTick();
            return;
        }
        final long now = now();
        if (fitDirty || now % FIT_EVERY == 0) {
            fitDirty = false;
            refit();
        }
        if (now % SHARED_EVERY == 0) {
            final boolean next = detectShared();
            if (next != shared) {
                shared = next;
                sendData();
            }

            final boolean source = shared && dutyIsSource(terms());
            if (source != dutyWasSource) {
                dutyWasSource = source;
                invalidateLinkedTerminals();
            }
        }
        if (now % CORONA_EVERY == 3) {
            final boolean next = canCatchNow();
            if (next != corona) {
                corona = next;
                sendData();
            }
        }
        if (now % 1200 == 7) {
            memory.prune(now);
        }
        if (++sincePolicy >= POLICY_TICKS) {
            sincePolicy = 0;
            findPolicy();
        }
        endFightIfOver(now);
        callLightning(now);
        if (now % CoilWatch.EVERY == 4) {
            look(now);
        }
        switch (aim.step(now)) {
            case LOOK -> aimVolley(now);
            case FIRE -> fireVolley(now);
            case WAIT -> {
            }
        }
        syncReadouts(now);
    }

    private void clientTick() {
        final long now = com.createstats.client.ponder.SceneClock.now(level);
        final boolean alive = syncedCapacitor > 0 || syncedBank > 0;
        motion.aim(stage);

        motion.gap(CoilMotion.gapFor(alive, syncedIn > 0, windingUp(now), now, lastZap));
        motion.blocked(blockedAt > 0);
        motion.headroom(headroom());
        motion.load(inShare());
        motion.charge(syncedCapacitor / (double) shownCapacitorSize(), syncedBank / (double) shownBankSize());
        motion.tick(alive);
        tickGaze(now);

        com.createstats.stormcoil.client.CoilSounds.tick(this);

        if (primed) {
            sparks(3 + 2 * stage, 0.12, 0.3);
        } else if (corona && level.random.nextInt(3) == 0) {
            sparks(1, 0.01, 0.05);
        }
        if (lastStrike != Long.MIN_VALUE && now - lastStrike >= 0 && now - lastStrike < 4) {
            sparks(8 + 2 * stage, 0.05, 0.25);
        }
    }

    private boolean headroom() {
        final BlockPos above = worldPosition.above(stage);
        if (!level.isLoaded(above)) {
            return true;
        }
        final BlockState s = level.getBlockState(above);
        return s.isAir() || s.canBeReplaced();
    }

    private void tickGaze(long now) {
        final com.createstats.stormcoil.CoilForm form = com.createstats.stormcoil.CoilForm.at(motion.height(1f));
        final double ox = worldPosition.getX() + 0.5;
        final double oy = worldPosition.getY() + form.eyeY(motion.duck(1f)) / 16.0;
        final double oz = worldPosition.getZ() + 0.5;
        final List<CoilGaze.Look> looks = new ArrayList<>(CoilWatch.MAX);
        for (CoilWatch.Entry e : CoilWatch.unpack(watch)) {
            final LivingEntity it = resolve(e.id());
            if (it != null) {
                final Vec3 c = it.getBoundingBox().getCenter();
                looks.add(new CoilGaze.Look(e.id(), e.verdict(), c.x - ox, c.y - oy, c.z - oz));
            }
        }
        final boolean up = primed || (corona && !policy().arcsAtAnything());
        gaze.tick(now, looks, windingUp(now) ? syncedAimIds : new int[0], up);
    }

    private void sparks(int n, double lift, double spread) {
        final com.createstats.stormcoil.CoilForm form = com.createstats.stormcoil.CoilForm.at(motion.height(1f));
        for (int i = 0; i < n; i++) {
            final double a = level.random.nextDouble() * Math.PI * 2;
            final double b = level.random.nextDouble() * Math.PI * 2;
            final double ring = (form.major + form.tube * Math.cos(b)) / 16.0;
            level.addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                    worldPosition.getX() + 0.5 + Math.cos(a) * ring,
                    worldPosition.getY() + (form.toroidY + form.tube * Math.sin(b)) / 16.0,
                    worldPosition.getZ() + 0.5 + Math.sin(a) * ring,
                    Math.cos(a) * spread * level.random.nextDouble(), lift + level.random.nextDouble() * lift,
                    Math.sin(a) * spread * level.random.nextDouble());
        }
    }

    private void callLightning(long now) {
        if (!corona || !(level instanceof ServerLevel server)) {
            nextCall = 0;
            setPrimed(false);
            return;
        }
        if (nextCall == 0) {
            nextCall = now + com.createstats.stormcoil.CoilCatch.callDelay(coilStage(), level.random.nextDouble());
            return;
        }
        if (now >= nextCall) {
            setPrimed(false);
            nextCall = now + com.createstats.stormcoil.CoilCatch.callDelay(coilStage(), level.random.nextDouble());
            if (!bystanders()) {
                final LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(server);
                if (bolt != null) {
                    bolt.moveTo(Vec3.atBottomCenterOf(crown().above()));
                    server.addFreshEntity(bolt);
                }
            }
            return;
        }
        final boolean next = com.createstats.stormcoil.CoilCatch.primed(now, nextCall) && !bystanders();
        if (next && !primed) {
            level.playSound(null, crown(), ModSounds.STORM_COIL_CHARGE.get(), SoundSource.BLOCKS, 2.0f, 1.0f);
        }
        setPrimed(next);
    }

    private boolean bystanders() {
        final BlockPos c = crown();
        final AABB reach = new AABB(c.getX() - 2.5, c.getY() - 2.0, c.getZ() - 2.5, c.getX() + 3.5, c.getY() + 10.0,
                c.getZ() + 3.5);
        return !level.getEntitiesOfClass(LivingEntity.class, reach,
                e -> e.isAlive() && !(e instanceof Enemy) && !(e instanceof Player p && p.isSpectator())).isEmpty();
    }

    private void setPrimed(boolean next) {
        if (next != primed) {
            primed = next;
            sendData();
        }
    }

    private void syncReadouts(long now) {
        syncedCapacitor = stores.capacitor();
        syncedBank = stores.bank();
        syncedCapacitorSize = coilStage().capacitorSize();
        syncedBankSize = CoilLaw.bankSize(base());
        syncedIn = stores.inDrawn(now);
        syncedOut = stores.outGiven(now);
        final com.createstats.power.FeStationFeed feed = boardFeed != null && boardFeed.fresh(now) ? boardFeed : null;
        syncedBoardStud = feed == null ? -1 : Math.max(0, feed.legA() + 1);
        syncedBoardLive = feed != null && feed.liveA();
        syncedBoardGive = feed == null ? 0 : Math.max(0, feed.shareA());
        syncedBoardCarried = feed == null ? 0 : Math.max(0, feed.carried());
        syncedBoardCost = feed == null ? 0 : Math.max(0, feed.cost());
        syncedBoardReason = feed == null ? 0 : feed.reason().ordinal();
        if (now % 10 == 0 && (syncedCapacitor != lastSentCapacitor || syncedBank != lastSentBank
                || syncedIn != lastSentIn || syncedOut != lastSentOut || boardHash() != lastSentBoard)) {
            sendData();
        }
    }

    private int boardHash() {
        int h = syncedBoardStud;
        h = h * 31 + (syncedBoardLive ? 1 : 0);
        h = h * 31 + syncedBoardGive;
        h = h * 31 + syncedBoardCarried;
        h = h * 31 + syncedBoardCost;
        return h * 31 + syncedBoardReason;
    }

    @Override
    public void sendData() {
        lastSentCapacitor = syncedCapacitor;
        lastSentBank = syncedBank;
        lastSentIn = syncedIn;
        lastSentOut = syncedOut;
        lastSentBoard = boardHash();
        super.sendData();
    }

    private boolean ownCell(BlockPos pos) {
        final BlockState s = level.getBlockState(pos);
        return s.getBlock() instanceof StormCoilPartBlock && worldPosition.equals(StormCoilPartBlock.master(level, pos, s));
    }

    private boolean free(BlockPos pos) {
        return level.isLoaded(pos) && (ownCell(pos) || level.getBlockState(pos).canBeReplaced());
    }

    private static final int[][] CORNERS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private boolean rodsFree() {
        for (int[] c : CORNERS) {
            if (!free(worldPosition.offset(c[0], 0, c[1]))) {
                return false;
            }
        }
        return true;
    }

    private void refit() {
        final CoilFit.Fit fit = CoilFit.fit(requestedStage(), k -> free(worldPosition.above(k)), rodsFree());
        final int next = fit.stage();
        final String why = fit.blocked() ? blockerName(fit.blockedCell()) : "";
        final boolean moved = next != stage;
        if (moved) {
            placeCells(next);
            stage = next;
            aim.reset();
            setChanged();
        }
        if (moved || fit.blockedAt() != blockedAt || !why.equals(blocker)) {
            blockedAt = fit.blockedAt();
            blocker = why;
            sendData();
        }
    }

    private String blockerName(int cell) {
        if (cell == CoilFit.RODS) {
            for (int[] c : CORNERS) {
                final BlockPos at = worldPosition.offset(c[0], 0, c[1]);
                if (!free(at)) {
                    return level.getBlockState(at).getBlock().getName().getString();
                }
            }
            return "";
        }
        return level.getBlockState(worldPosition.above(cell)).getBlock().getName().getString();
    }

    private void placeCells(int next) {
        releasing = true;
        try {
            for (int k = 1; k <= 3; k++) {
                final BlockPos at = worldPosition.above(k);
                if (k < next) {
                    if (!ownCell(at)) {
                        level.setBlock(at, StormCoilPartBlock.column(), Block.UPDATE_ALL);
                    }
                } else if (ownCell(at)) {
                    level.removeBlock(at, false);
                }
            }
            for (int[] c : CORNERS) {
                final BlockPos at = worldPosition.offset(c[0], 0, c[1]);
                if (next >= 4) {
                    if (!ownCell(at)) {
                        level.setBlock(at, StormCoilPartBlock.rod(c[0], c[1]), Block.UPDATE_ALL);
                    }
                } else if (ownCell(at)) {
                    level.removeBlock(at, false);
                }
            }
        } finally {
            releasing = false;
        }
    }

    public void onBroken() {
        dropAllStudWires();
        if (level != null) {
            placeCells(1);
        }
    }

    public boolean canCatchNow() {
        if (level == null || !level.isThundering()) {
            return false;
        }
        final BlockPos c = crown();
        return isColumnTop(level, c) && level.isRainingAt(c.above());
    }

    public static boolean isColumnTop(Level level, BlockPos crown) {
        return crown.getY() == level.getHeight(Heightmap.Types.WORLD_SURFACE, crown.getX(), crown.getZ()) - 1;
    }

    public static void struck(Level level, BlockPos at) {
        if (level == null || level.isClientSide()) {
            return;
        }
        final BlockPos coil = StormCoilPartBlock.master(level, at, level.getBlockState(at));
        if (coil != null && level.getBlockEntity(coil) instanceof StormCoilBE be && be.crown().equals(at)) {
            be.onStrike();
        }
    }

    private void onStrike() {
        final int banked = stores.strike(terms());
        strikes++;
        lastStrike = now();
        if (banked > 0) {
            final HomeSecurityPanelBE p = panel();
            if (p != null) {
                p.coilBanked(worldPosition, Math.min(CoilLaw.STRIKES_HELD,
                        stores.bank() / Math.max(1, CoilLaw.strikeFe(base()))), CoilLaw.STRIKES_HELD);
            }
        }

        level.playSound(null, crown(), ModSounds.STORM_COIL_BANK.get(), SoundSource.BLOCKS, 2.5f,
                banked > 0 ? 1.0f : 0.85f);
        setChanged();
        sendData();
    }

    private Vec3 arcOrigin() {
        return Vec3.atBottomCenterOf(worldPosition).add(0, stage - 0.15, 0);
    }

    private void look(long now) {
        if (stores.capacitor() <= 0 && stores.bank() <= 0 && stores.inDrawn(now) <= 0) {
            setWatch(new int[0]);
            return;
        }
        final CoilRules.Policy policy = policy();
        final CoilStage st = coilStage();
        final Vec3 from = arcOrigin();
        final double reachSq = (double) st.reach * st.reach;
        final List<CoilWatch.Seen> near = new ArrayList<>();
        final Map<Integer, LivingEntity> byId = new java.util.HashMap<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, from).inflate(st.reach),
                LivingEntity::isAlive)) {
            if (e instanceof ArmorStand || e.isSpectator()) {
                continue;
            }
            final double d = e.getBoundingBox().getCenter().distanceToSqr(from);
            if (d <= reachSq) {
                near.add(new CoilWatch.Seen(e.getId(), d, describe(e, now), personOf(e)));
                byId.put(e.getId(), e);
            }
        }
        setWatch(CoilWatch.pack(CoilWatch.pick(policy, near, s -> canSee(from, byId.get(s.id())))));
    }

    private void setWatch(int[] next) {
        if (!CoilWatch.same(next, watch)) {
            watch = next;
            sendData();
        }
    }

    private void aimVolley(long now) {
        final CoilRules.Policy policy = policy();
        final CoilStage st = coilStage();
        if (heldFromArcing() || !policy.arcsAtAnything() || !stores.canPay(st.zapCost)) {
            aim.nothing(now);
            return;
        }
        final List<LivingEntity> volley = pickVolley(st, policy, now);
        if (volley.isEmpty()) {
            aim.nothing(now);
            return;
        }
        final int[] ids = new int[volley.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = volley.get(i).getId();
        }
        aim.aim(now, ids);
        sendData();
    }

    private void fireVolley(long now) {
        final CoilRules.Policy policy = policy();
        final CoilStage st = coilStage();
        if (heldFromArcing() || !policy.arcsAtAnything() || !stores.canPay(st.zapCost)
                || !(level instanceof ServerLevel server)) {
            aim.fired(now, 0);
            sendData();
            return;
        }
        final Vec3 from = arcOrigin();
        final double reachSq = (double) st.reach * st.reach;
        final int[] kept = CoilAim.kept(aim.ids(), id -> level.getEntity(id) instanceof LivingEntity e && e.isAlive()
                && e.getBoundingBox().getCenter().distanceToSqr(from) <= reachSq
                && policy.allows(describe(e, now), personOf(e)) && canSee(from, e));

        int hit = 0;
        for (int id : kept) {
            if (hit >= Math.min(st.targets(), CoilAim.MAX) || !(level.getEntity(id) instanceof LivingEntity target)) {
                continue;
            }
            if (!stores.payZap(st.zapCost)) {
                break;
            }
            final LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(server);
            if (bolt != null) {
                bolt.moveTo(Vec3.atBottomCenterOf(target.blockPosition()));
                if (!EventHooks.onEntityStruckByLightning(target, bolt)) {
                    target.thunderHit(server, bolt);
                }
            }
            if (converts(policy)) {
                memory.remember(target.getUUID(), now);
            }
            if (hit == 0) {
                lastArc = SecurityLog.clip(target.getName().getString(), 32);
            }
            noteHit(target, now);
            final Vec3 to = target.getBoundingBox().getCenter();
            final float tx = (float) (to.x - worldPosition.getX());
            final float ty = (float) (to.y - worldPosition.getY());
            final float tz = (float) (to.z - worldPosition.getZ());
            if (hit == 0) {
                zapX = tx;
                zapY = ty;
                zapZ = tz;
            }
            volleyAt[3 * hit] = tx;
            volleyAt[3 * hit + 1] = ty;
            volleyAt[3 * hit + 2] = tz;
            hit++;
        }
        aim.fired(now, hit);
        if (hit == 0) {
            sendData();
            return;
        }
        volleySize = hit;
        lastZap = now;
        fightVolleys++;
        level.playSound(null, worldPosition.above(stage - 1), ModSounds.STORM_COIL_ZAP.get(), SoundSource.BLOCKS,
                1.4f + 0.25f * (hit - 1), 0.9f + level.random.nextFloat() * 0.2f);
        setChanged();
        sendData();
    }

    private List<LivingEntity> pickVolley(CoilStage st, CoilRules.Policy policy, long now) {
        final Vec3 from = arcOrigin();
        final double reachSq = (double) st.reach * st.reach;
        final List<LivingEntity> near = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, from).inflate(st.reach),
                LivingEntity::isAlive)) {
            if (e.getBoundingBox().getCenter().distanceToSqr(from) <= reachSq
                    && policy.allows(describe(e, now), personOf(e))) {
                near.add(e);
            }
        }
        near.sort(java.util.Comparator.comparingDouble(e -> e.getBoundingBox().getCenter().distanceToSqr(from)));
        final List<LivingEntity> out = new ArrayList<>(st.targets());
        for (LivingEntity e : near) {
            if (out.size() >= st.targets()) {
                break;
            }
            if (canSee(from, e)) {
                out.add(e);
            }
        }
        return out;
    }

    private CoilTargets.Target describe(LivingEntity e, long now) {
        final CoilTargets.Kind kind;
        if (e instanceof Player) {
            kind = CoilTargets.Kind.PLAYER;
        } else if (e instanceof IronGolem || e instanceof SnowGolem) {
            kind = CoilTargets.Kind.GOLEM;
        } else if (e instanceof Creeper) {
            kind = CoilTargets.Kind.CREEPER;
        } else if (e instanceof Villager) {
            kind = CoilTargets.Kind.VILLAGER;
        } else if (e instanceof MushroomCow) {
            kind = CoilTargets.Kind.MOOSHROOM;
        } else if (e instanceof Pig) {
            kind = CoilTargets.Kind.PIG;
        } else if (e instanceof Enemy) {
            kind = CoilTargets.Kind.HOSTILE;
        } else {
            kind = CoilTargets.Kind.OTHER;
        }
        final boolean owned = (e instanceof OwnableEntity o && o.getOwnerUUID() != null)
                || (e instanceof TamableAnimal t && t.isTame())
                || (e instanceof AbstractHorse h && h.isTamed());
        final boolean leashed = e instanceof net.minecraft.world.entity.Leashable l && l.isLeashed();
        final boolean creative = e instanceof Player p && (p.isCreative() || p.isSpectator());
        final boolean charged = e instanceof Creeper c && c.isPowered();
        return new CoilTargets.Target(kind, e.isAlive() && !e.isInvulnerable(), owned, e.hasCustomName(), leashed,
                creative, charged, memory.has(e.getUUID(), now));
    }

    private boolean canSee(Vec3 from, LivingEntity e) {
        final Vec3 to = e.getBoundingBox().getCenter();
        final Vec3 d = to.subtract(from);
        final double len = d.length();
        if (len < 0.9) {
            return true;
        }
        final Vec3 start = from.add(d.scale(0.8 / len));
        final BlockHitResult hit = level.clip(new ClipContext(start, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS || ownCell(hit.getBlockPos())
                || hit.getBlockPos().equals(worldPosition);
    }

    public IEnergyStorage portFor(int stud) {
        return stud == StormCoilStuds.OUT ? outPort : inPort;
    }

    public FeGridNode rateNode(int stud) {
        return portFor(stud) instanceof Port p ? p.rate : inPort.rate;
    }

    public int studRate(int stud) {
        int total = 0;
        for (StudLink link : studLinks(stud)) {
            total = FeRates.plus(total, FeRates.wire(link.tier()));
        }
        return total;
    }

    private boolean dutyIsSource(CoilStores.Terms t) {
        return stores.duty(t) == CoilStores.Duty.SOURCE;
    }

    private final class Port implements IEnergyStorage {
        private final int stud;
        final FeGridNode rate = new FeGridNode() {
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
                final CoilStores.Terms t = terms();
                final boolean source = shared ? dutyIsSource(t) : stud == StormCoilStuds.OUT;
                return source ? t.outCap() : t.inCap();
            }

            @Nullable
            @Override
            public IEnergyStorage attachedStorage() {
                return Port.this;
            }
        };

        Port(int stud) {
            this.stud = stud;
        }

        private boolean gives(CoilStores.Terms t) {
            return shared ? dutyIsSource(t) : stud == StormCoilStuds.OUT;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final CoilStores.Terms t = terms();
            if (gives(t)) {
                return 0;
            }
            final int n = stores.receiveIn(maxReceive, simulate, now(), t);
            if (n > 0 && !simulate) {
                setChanged();
            }
            return n;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            final CoilStores.Terms t = terms();
            if (!gives(t)) {
                return 0;
            }
            final int n = stores.extractOut(maxExtract, simulate, now(), t);
            if (n > 0 && !simulate) {
                setChanged();
            }
            return n;
        }

        @Override
        public int getEnergyStored() {
            return gives(terms()) ? stores.bank() : stores.capacitor();
        }

        @Override
        public int getMaxEnergyStored() {
            final CoilStores.Terms t = terms();
            return gives(t) ? t.bankSize() : t.capacitorSize();
        }

        @Override
        public boolean canExtract() {
            return gives(terms());
        }

        @Override
        public boolean canReceive() {
            return !gives(terms());
        }
    }

    @Override
    public int ratedDrawFePerTick() {
        return stores.ratedDraw(terms(), CoilLaw.engaged(now(), lastZap));
    }

    @Override
    public int measuredDrawFePerTick() {
        return stores.inDrawn(now());
    }

    @Override
    public void acceptBoardFeed(com.createstats.power.FeStationFeed feed) {
        if (feed != null) {
            boardFeed = feed;
        }
    }

    @Nullable
    @Override
    public BlockPos studFarEnd(int stud) {
        final List<StudLink> links = studLinks(stud == 1 ? StormCoilStuds.OUT : StormCoilStuds.IN);
        return links.isEmpty() ? null : links.get(0).to();
    }

    @Override
    public int potentialFePerTick() {
        final CoilStores.Terms t = terms();
        if (shared && !dutyIsSource(t)) {
            return 0;
        }
        return Math.min(t.outCap(), stores.bank());
    }

    private boolean detectShared() {
        final List<FeWireNetwork> in = networksOn(StormCoilStuds.IN);
        if (in.isEmpty()) {
            return false;
        }
        for (FeWireNetwork far : networksOn(StormCoilStuds.OUT)) {
            for (FeWireNetwork near : in) {
                if (near == far) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<FeWireNetwork> networksOn(int stud) {
        final List<FeWireNetwork> out = new ArrayList<>(MAX_LINKS_PER_STUD);
        for (StudLink link : studLinks(stud)) {
            if (level.isLoaded(link.to()) && level.getBlockEntity(link.to()) instanceof FeGridMember member
                    && member.network() != null) {
                out.add(member.network());
            }
        }
        return out;
    }

    @Override
    public BlockPos panelPos() {
        return worldPosition;
    }

    public List<StudLink> studLinks(int stud) {
        return StormCoilStuds.isStud(stud) ? studLinks.get(stud) : List.of();
    }

    public int wiresOnStuds() {
        return studLinks.get(0).size() + studLinks.get(1).size();
    }

    @Override
    public int studLinkCount(int stud) {
        return studLinks(stud).size();
    }

    @Override
    public int maxLinksPerStud() {
        return MAX_LINKS_PER_STUD;
    }

    @Override
    public boolean isLinkedToConnector(BlockPos connector) {
        return studOf(connector) >= 0;
    }

    public int studOf(BlockPos far) {
        for (int stud = 0; stud < StormCoilStuds.COUNT; stud++) {
            for (StudLink link : studLinks.get(stud)) {
                if (link.to().equals(far)) {
                    return stud;
                }
            }
        }
        return -1;
    }

    @Override
    public void linkStud(int stud, FeWireTerminal terminal, WireTier tier) {
        if (level == null || level.isClientSide() || !StormCoilStuds.isStud(stud)) {
            return;
        }
        final WireTier grade = tier == null ? WireTier.COPPER : tier;
        studLinks.get(stud).add(new StudLink(terminal.terminalPos().immutable(), grade));
        terminal.addLink(worldPosition, grade);
        syncStudLinks();
    }

    @Override
    public int dropStudWires(int stud) {
        if (level == null || level.isClientSide() || !StormCoilStuds.isStud(stud)) {
            return 0;
        }
        final List<StudLink> links = studLinks.get(stud);
        if (links.isEmpty()) {
            return 0;
        }
        final int removed = links.size();
        final Map<WireTier, Integer> refund = new EnumMap<>(WireTier.class);
        for (StudLink link : new ArrayList<>(links)) {
            refund.merge(link.tier(), 1, Integer::sum);
            if (level.getBlockEntity(link.to()) instanceof FeWireTerminal terminal) {
                terminal.removeDeadLink(worldPosition);
            }
        }
        invalidateLinkedTerminals();
        links.clear();
        for (Map.Entry<WireTier, Integer> entry : refund.entrySet()) {
            Block.popResource(level, worldPosition,
                    new ItemStack(com.createstats.registry.ModItems.wireItem(entry.getKey()), entry.getValue()));
        }
        syncStudLinks();
        return removed;
    }

    public void dropAllStudWires() {
        for (int stud = 0; stud < StormCoilStuds.COUNT; stud++) {
            dropStudWires(stud);
        }
    }

    @Override
    public void forgetLinksTo(BlockPos connector) {
        if (level == null || level.isClientSide()) {
            return;
        }
        boolean changed = false;
        for (List<StudLink> links : studLinks) {
            changed |= links.removeIf(link -> link.to().equals(connector));
        }
        if (changed) {
            syncStudLinks();
        }
    }

    private void syncStudLinks() {
        setChanged();
        invalidateLinkedTerminals();
        shared = detectShared();
        sendData();
    }

    private void invalidateLinkedTerminals() {
        if (level == null || level.isClientSide()) {
            return;
        }
        for (List<StudLink> links : studLinks) {
            for (StudLink link : links) {
                if (level.isLoaded(link.to()) && level.getBlockEntity(link.to()) instanceof FeGridMember member) {
                    member.invalidateNetwork();
                }
            }
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        StormCoilIndex.remove(level, worldPosition);
        invalidateLinkedTerminals();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        StormCoilIndex.remove(level, worldPosition);
    }

    @Override
    public DeviceClass deviceClass() {
        return DeviceClass.COIL;
    }

    @Override
    public DeviceState deviceState() {
        return securityStatus().deviceState();
    }

    public CoilStatus securityStatus() {
        return CoilStatus.of(!panelOff, !deaf, coilReading(), policy().arcsAtAnything());
    }

    public CoilStatus.Lamp securityLamp() {
        return CoilStatus.lamp(governed, houseState == CoilRules.HouseState.LOCKDOWN.ordinal(), securityStatus());
    }

    @Override
    public CoilReading coilReading() {
        final CoilStage st = coilStage();
        final int size = Math.max(1, syncedCapacitorSize);
        final int volleys = (int) Math.min(CoilLaw.CAPACITOR_VOLLEYS,
                (long) syncedCapacitor * CoilLaw.CAPACITOR_VOLLEYS / size);
        final int strike = Math.max(1, syncedBankSize / CoilLaw.STRIKES_HELD);
        final int strikesHeld = Math.min(CoilLaw.STRIKES_HELD, syncedBank / strike);

        final boolean flat = zapsAtAll() && syncedCapacitor < st.zapCost && syncedBank < st.zapCost && syncedIn <= 0;
        final boolean fedShort = syncedBoardStud >= 0
                && PduReason.byOrdinal(syncedBoardReason) == PduReason.WIRE_SHORT;
        return new CoilReading(stage, volleys, CoilLaw.CAPACITOR_VOLLEYS, strikesHeld, CoilLaw.STRIKES_HELD, flat,
                blockedAt > 0 ? blocker : "", fedShort, redstone, CoilLaw.engaged(now(), lastZap), lastZap, lastArc,
                mode());
    }

    public CoilRules.Policy policy() {
        if (!governed) {
            return CoilRules.Policy.own(mode());
        }
        return CoilRules.Policy.governed(grid().rule(CoilRules.HouseState.byOrdinal(houseState)),
                level instanceof ServerLevel server && server.getServer().isPvpAllowed());
    }

    private CoilRules.Grid grid() {
        return rules < 0 ? CoilRules.Grid.ofMode(mode()) : new CoilRules.Grid(rules);
    }

    private boolean zapsAtAll() {
        return governed ? grid().arcsAtAnything() : mode().zaps();
    }

    private static boolean converts(CoilRules.Policy policy) {
        return policy.rule() == null ? policy.mode() == CoilTargets.Mode.TRANSFORM : policy.rule().transform();
    }

    private CoilRules.Person personOf(LivingEntity e) {
        return e instanceof Player p ? CoilGate.personOf(roster, p.getUUID()) : CoilRules.Person.NOT_A_PLAYER;
    }

    @Override
    @Nullable
    public BlockPos governingPanel() {
        return panelPos;
    }

    @Override
    @Nullable
    public UUID governingHousehold() {
        return panelHousehold;
    }

    public boolean governed() {
        return governed;
    }

    public CoilRules.HouseState houseState() {
        return CoilRules.HouseState.byOrdinal(houseState);
    }

    public boolean panelOff() {
        return panelOff;
    }

    public boolean locked() {
        return locked;
    }

    @Override
    public void repoll() {
        sincePolicy = POLICY_TICKS;
    }

    @Override
    public void offerPanel(BlockPos panel) {
        if (panelPos == null && !panel.equals(offeredPanel)) {
            offeredPanel = panel.immutable();
            sincePolicy = POLICY_TICKS;
        }
    }

    @Override
    public void releasePanel(BlockPos panel) {
        if (panel == null) {
            return;
        }
        if (panel.equals(offeredPanel)) {
            offeredPanel = null;
        }
        if (panel.equals(panelPos)) {
            panelPos = null;
            sincePolicy = POLICY_TICKS;
        }
    }

    private void findPolicy() {
        if (level == null) {
            return;
        }
        final boolean onCircuit = !networksOn(StormCoilStuds.IN).isEmpty()
                || !networksOn(StormCoilStuds.OUT).isEmpty() || offeredPanel != null;
        final SecurityLink.Contact<HomeSecurityPanelBE> contact = SecurityLink.resolve(panel(), panelPos != null,
                panelPos != null && level.isLoaded(panelPos), onCircuit, this::searchPanels);
        if (contact.state() == SecurityLink.UNKNOWN) {
            return;
        }
        if (contact.state().isLost()) {
            loseLink();
            return;
        }
        final HomeSecurityPanelBE found = contact.panel();
        if (found == null) {
            return;
        }
        final int before = securityHash();
        panelPos = found.getBlockPos();
        panelHousehold = SecurityIdentity.householdOf(found);
        offeredPanel = null;
        governed = true;
        roster = found.rosterSnapshot();
        required = found.requiredLevel(worldPosition);
        panelOff = !found.coilOn(worldPosition);
        locked = found.coilLocked(worldPosition);
        deaf = !found.coilHearsRedstone(worldPosition);
        final CoilRules.Grid g = found.coilRules(worldPosition);
        rules = g == null ? -1 : g.bits();
        houseState = found.houseState().ordinal();
        houseName = SecurityLog.clip(found.houseName().getString(), 40);
        if (securityHash() != before) {
            relabel();
            setChanged();
            sendData();
        }
    }

    private void loseLink() {
        final boolean had = governed || panelPos != null || panelOff || locked || deaf || rules >= 0;
        panelPos = null;
        offeredPanel = null;
        roster = null;
        required = Clearance.DEFAULT_REQUIRED;
        governed = false;
        panelOff = false;
        locked = false;
        deaf = false;
        rules = -1;
        houseState = 0;
        houseName = "";
        if (had) {
            relabel();
            setChanged();
            sendData();
        }
    }

    private int securityHash() {
        int h = governed ? 1 : 0;
        h = h * 31 + (panelOff ? 1 : 0);
        h = h * 31 + (locked ? 1 : 0);
        h = h * 31 + (deaf ? 1 : 0);
        h = h * 31 + rules;
        h = h * 31 + houseState;
        return h * 31 + houseName.hashCode();
    }

    // IN before OUT
    @Nullable
    private HomeSecurityPanelBE searchPanels() {
        for (int stud : new int[] {StormCoilStuds.IN, StormCoilStuds.OUT}) {
            for (FeWireNetwork network : networksOn(stud)) {
                final HomeSecurityPanelBE wired = PanelSweep.nearest(level, network, worldPosition, worldPosition,
                        panelHousehold);
                if (wired != null) {
                    return wired;
                }
            }
        }
        return hintedPanel();
    }

    @Nullable
    private HomeSecurityPanelBE hintedPanel() {
        if (level == null || offeredPanel == null || !level.isLoaded(offeredPanel)) {
            return null;
        }
        return SecurityIdentity.offered(level, offeredPanel, worldPosition, panelHousehold);
    }

    @Nullable
    public HomeSecurityPanelBE panel() {
        if (level == null || panelPos == null || !level.isLoaded(panelPos)) {
            return null;
        }
        return level.getBlockEntity(panelPos) instanceof HomeSecurityPanelBE p
                && SecurityIdentity.accepts(level, panelHousehold, worldPosition, p) ? p : null;
    }

    @Override
    @Nullable
    public HomeSecurityPanelBE securityPanel() {
        return panel();
    }

    @Override
    public boolean clears(Player player) {
        return roster != null && player != null && roster.passes(player.getUUID(), required);
    }

    private void relabel() {
        if (targets != null) {
            targets.setLabel(Component.translatable(!governed ? "create_stats.storm_coil.targets"
                    : locked ? "create_stats.storm_coil.targets.locked" : "create_stats.storm_coil.targets.panel"));
        }
        if (height != null) {
            height.setLabel(Component.translatable(governed && locked ? "create_stats.storm_coil.height.locked"
                    : "create_stats.storm_coil.height"));
        }
    }

    // tell the player every time, log at most once a second
    private boolean refuse(Player player, CoilGate.Box box) {
        if (!(player instanceof ServerPlayer sp) || level == null || level.isClientSide()) {
            return false;
        }
        final CoilGate gate = CoilGate.decide(box, panelPos != null, locked, roster != null, clears(sp));
        if (gate == CoilGate.ALLOW) {
            return false;
        }
        sp.displayClientMessage(switch (gate) {
            case PANEL_SETS -> Component.translatable("create_stats.storm_coil.refused.panel_sets");
            case LOCKED -> Component.translatable("create_stats.storm_coil.refused.locked");
            case NO_CLEARANCE -> Component.translatable("create_stats.storm_coil.refused.no_clearance", required,
                    Clearance.label(required));
            default -> Component.translatable("create_stats.storm_coil.refused.out_of_contact");
        }, true);
        final long now = now();
        if (!sp.getUUID().equals(refusedWho) || now - refusedAt >= REFUSAL_LOG_GAP) {
            refusedWho = sp.getUUID();
            refusedAt = now;
            final HomeSecurityPanelBE p = panel();
            if (p != null) {
                final String what = box == CoilGate.Box.TARGETS ? "tried to change its targets: " : "tried to change its height: ";
                p.recordDevice(sp, worldPosition, what + switch (gate) {
                    case PANEL_SETS -> "the panel sets them";
                    case LOCKED -> "locked";
                    case NO_CLEARANCE -> "needs " + required + " " + Clearance.plain(required);
                    default -> "out of contact";
                }, SecurityLog.Verdict.DENIED);
            }
        }
        sendData();
        return true;
    }

    private void noteHit(LivingEntity target, long now) {
        if (fightStart == Long.MIN_VALUE) {
            fightStart = now;
        }
        if (fightTargets.size() < FIGHT_COUNT_CAP) {
            fightTargets.add(target.getUUID());
        }
        if (target instanceof Player p && fightPlayers.size() < FIGHT_COUNT_CAP && fightPlayers.add(p.getUUID())) {
            final HomeSecurityPanelBE panel = panel();
            if (panel != null) {
                panel.coilArcedAt(worldPosition, p.getGameProfile().getName());
            }
        }
    }

    private void endFightIfOver(long now) {
        if (fightStart == Long.MIN_VALUE || CoilLaw.engaged(now, lastZap)) {
            return;
        }
        final HomeSecurityPanelBE p = panel();
        if (p != null && fightVolleys > 0) {
            p.coilFought(worldPosition, fightTargets.size(), fightVolleys,
                    (int) Math.max(1L, (lastZap - fightStart) / 20L + 1L));
        }
        fightStart = Long.MIN_VALUE;
        fightVolleys = 0;
        fightTargets.clear();
        fightPlayers.clear();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(FeGoggle.title("Storm Coil"));
        final CoilStage st = coilStage();
        tooltip.add(Component.literal("    Stage " + st.number() + " of 4: reach " + st.reach
                + " blocks, catches lightning within " + st.catchRadius).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("    Arcs to " + (st.targets() == 1 ? "one mob" : st.targets() + " mobs at once")
                + ", a volley a second, " + FeRates.perTick(CoilLaw.volleyRate(st.zapCost, st.targets()))
                + " at full fight").withStyle(ChatFormatting.GRAY));
        if (blockedAt > 0) {
            tooltip.add(FeGoggle.warn("Held at stage " + stage + ": " + (blocker.isEmpty() ? "something" : blocker)
                    + " is in the way"));
        }
        final CoilTargets.Mode mode = mode();
        if (governed) {
            addSecurityLines(tooltip);
        } else {
            tooltip.add(Component.literal("    Targets: " + switch (mode) {
                case HOSTILE -> "Hostile";
                case TRANSFORM -> "Transform";
                case STORM_ONLY -> "Storm only";
            }).withStyle(ChatFormatting.GRAY));
        }
        if (redstone && !deaf && zapsAtAll()) {
            tooltip.add(FeGoggle.warn("Zapping off: redstone signal"));
        }
        tooltip.add(FeGoggle.amount("Zap capacitor", syncedCapacitor, syncedCapacitorSize));
        tooltip.add(FeGoggle.amount("Storm bank", syncedBank, syncedBankSize));
        final boolean inWired = !studLinks(StormCoilStuds.IN).isEmpty();
        final boolean outWired = !studLinks(StormCoilStuds.OUT).isEmpty();
        tooltip.add(Component.literal("    IN: " + (!inWired ? "not wired"
                : syncedIn > 0 ? "drawing " + FeRates.perTick(syncedIn)
                : !zapsAtAll() ? "idle, Storm only takes nothing"
                : syncedBank >= st.zapCost ? "idle, the storm bank pays the zaps"
                : syncedCapacitor >= syncedCapacitorSize ? "idle, capacitor full" : "waiting for power"))
                .withStyle(syncedIn > 0 ? ChatFormatting.AQUA : ChatFormatting.GRAY));
        if (inWired) {
            final com.createstats.power.ConnectorTier via = CoilLaw.inConnector(st, base());
            final String need = "Full volleys take " + FeRates.perTick(CoilLaw.volleyRate(st.zapCost, st.targets()))
                    + ": " + CoilLaw.inWire(st, base()).getDisplayName()
                    + (via == com.createstats.power.ConnectorTier.POWER ? "" : ", " + via.getDisplayName() + "s");
            if (studRate(StormCoilStuds.IN) < CoilLaw.inRate(st, base())) {
                tooltip.add(FeGoggle.warn(need + " (this wire carries " + FeRates.perTick(studRate(StormCoilStuds.IN))
                        + ")"));
            } else {
                tooltip.add(FeGoggle.dim(need));
            }
        }
        if (syncedBoardStud >= 0) {
            addBoardLines(tooltip);
        }
        tooltip.add(Component.literal("    OUT: " + (!outWired ? "not wired"
                : syncedOut > 0 ? "exporting " + FeRates.perTick(syncedOut)
                : syncedBank > 0 ? "ready, nothing drawing" : "idle, the storm bank is empty"))
                .withStyle(syncedOut > 0 ? ChatFormatting.AQUA : ChatFormatting.GRAY));
        if (shared) {
            tooltip.add(FeGoggle.warn("IN and OUT are on one circuit"));
            tooltip.add(FeGoggle.dim("It is a source while the bank pays the zaps, a load otherwise."));
        }
        tooltip.add(FeGoggle.dim(lastStrike == Long.MIN_VALUE || level == null ? "No strike caught yet"
                : "Last strike " + ago(level.getGameTime() - lastStrike) + " ago, " + strikes + " caught"));
        if (corona) {
            tooltip.add(Component.literal("    St Elmo's fire: a storm overhead").withStyle(ChatFormatting.AQUA));
        }
        return true;
    }

    private void addBoardLines(List<Component> tooltip) {
        final String stud = syncedBoardStud > 0 ? "PDU stud " + syncedBoardStud : "PDU";
        final PduReason reason = PduReason.byOrdinal(syncedBoardReason);
        if (reason == PduReason.LOCKED_OFF) {
            tooltip.add(FeGoggle.warn(stud + ": switched off at the board"));
        } else if (!syncedBoardLive || reason == PduReason.BREAKER_OPEN) {
            tooltip.add(FeGoggle.warn(stud + ": its breaker is open"));
        } else if (reason.isShed()) {
            tooltip.add(FeGoggle.warn(stud + ": " + reason.description()));
        } else if (syncedBoardCost <= 0) {
            tooltip.add(Component.literal("    " + stud + ": standing by, the coil is ready")
                    .withStyle(ChatFormatting.GRAY));
        } else if (syncedBoardCarried < syncedBoardCost) {
            tooltip.add(FeGoggle.warn(stud + " has " + FeRates.perTick(syncedBoardCarried) + " spare, an arc a second needs "
                    + FeRates.perTick(syncedBoardCost)));
            tooltip.add(FeGoggle.dim("Fewer arcs, never a trip. A stud of its own carries the rest."));
        } else {
            tooltip.add(Component.literal("    " + stud + ": feeds " + FeRates.perTick(syncedBoardGive) + " of "
                    + FeRates.perTick(syncedBoardCost) + " for an arc a second").withStyle(ChatFormatting.AQUA));
        }
    }

    private static String ago(long ticks) {
        final long s = Math.max(0, ticks) / 20;
        return s < 60 ? s + " s" : s < 3600 ? (s / 60) + " min" : (s / 3600) + " h";
    }

    private void addSecurityLines(List<Component> tooltip) {
        tooltip.add(line(Component.translatable("create_stats.storm_coil.goggle.governed",
                houseName.isEmpty() ? Component.translatable("create_stats.storm_coil.goggle.a_house") : houseName),
                ChatFormatting.GRAY));
        final CoilRules.HouseState state = CoilRules.HouseState.byOrdinal(houseState);
        tooltip.add(line(Component.translatable("create_stats.storm_coil.goggle.rule", CoilWords.state(state),
                CoilWords.rule(grid().rule(state))), ChatFormatting.AQUA));
        if (panelOff) {
            tooltip.add(line(Component.translatable("create_stats.storm_coil.goggle.stood_down"), ChatFormatting.GOLD));
        }
        if (locked) {
            tooltip.add(line(Component.translatable("create_stats.storm_coil.goggle.locked"), ChatFormatting.DARK_GRAY));
        }
        if (deaf) {
            tooltip.add(line(Component.translatable("create_stats.storm_coil.goggle.deaf"), ChatFormatting.DARK_GRAY));
        }
    }

    private static Component line(Component text, ChatFormatting style) {
        return Component.literal("    ").append(text).withStyle(style);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Stage", stage);
        tag.putInt("BlockedAt", blockedAt);
        tag.putString("Blocker", blocker);
        tag.putBoolean("Redstone", redstone);
        tag.putBoolean("Shared", shared);
        tag.putBoolean("Corona", corona);
        tag.putLong("LastStrike", lastStrike);
        tag.putLong("LastZap", lastZap);
        tag.putFloat("ZapX", zapX);
        tag.putFloat("ZapY", zapY);
        tag.putFloat("ZapZ", zapZ);
        tag.putInt("Volley", volleySize);
        final int[] volleyBits = new int[3 * Math.max(0, Math.min(4, volleySize))];
        for (int i = 0; i < volleyBits.length; i++) {
            volleyBits[i] = Float.floatToIntBits(volleyAt[i]);
        }
        tag.putIntArray("VolleyAt", volleyBits);
        tag.putBoolean("Primed", primed);
        tag.putInt("Strikes", strikes);
        tag.putInt("Capacitor", stores.capacitor());
        tag.putInt("Bank", stores.bank());
        tag.putInt("CapacitorSize", syncedCapacitorSize);
        tag.putInt("BankSize", syncedBankSize);
        tag.putInt("InDraw", syncedIn);
        tag.putInt("OutExport", syncedOut);

        tag.putBoolean("SecGoverned", governed);
        tag.putBoolean("SecOff", panelOff);
        tag.putBoolean("SecLocked", locked);
        tag.putBoolean("SecDeaf", deaf);
        tag.putInt("SecRules", rules);
        tag.putInt("SecState", houseState);
        tag.putString("SecHouse", houseName);
        tag.putString("LastArc", lastArc);
        if (!clientPacket) {
            if (panelPos != null) {
                tag.putLong("SecPanel", panelPos.asLong());
            }
            if (panelHousehold != null) {
                tag.putUUID("SecHousehold", panelHousehold);
            }
        }
        if (!clientPacket) {
            tag.putLongArray("Zapped", memory.pack());
        } else {
            tag.putInt("BoardStud", syncedBoardStud);
            tag.putBoolean("BoardLive", syncedBoardLive);
            tag.putInt("BoardGive", syncedBoardGive);
            tag.putInt("BoardCarried", syncedBoardCarried);
            tag.putInt("BoardCost", syncedBoardCost);
            tag.putInt("BoardReason", syncedBoardReason);

            tag.putIntArray("Watch", watch);
            tag.putLong("AimAt", aim.aimAt());
            tag.putIntArray("AimIds", aim.ids());
        }
        final ListTag spans = new ListTag();
        for (int stud = 0; stud < StormCoilStuds.COUNT; stud++) {
            for (StudLink link : studLinks.get(stud)) {
                final CompoundTag span = new CompoundTag();
                span.putByte("S", (byte) stud);
                span.putLong("P", link.to().asLong());
                span.putByte("T", (byte) link.tier().ordinal());
                spans.add(span);
            }
        }
        tag.put("StudWires", spans);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        stage = Math.max(1, Math.min(4, tag.getInt("Stage")));
        blockedAt = tag.getInt("BlockedAt");
        blocker = tag.getString("Blocker");
        redstone = tag.getBoolean("Redstone");
        shared = tag.getBoolean("Shared");
        corona = tag.getBoolean("Corona");
        lastStrike = tag.contains("LastStrike") ? tag.getLong("LastStrike") : Long.MIN_VALUE;
        lastZap = tag.contains("LastZap") ? tag.getLong("LastZap") : Long.MIN_VALUE;
        zapX = tag.getFloat("ZapX");
        zapY = tag.getFloat("ZapY");
        zapZ = tag.getFloat("ZapZ");
        volleySize = Math.max(0, Math.min(4, tag.getInt("Volley")));
        final int[] volleyBits = tag.getIntArray("VolleyAt");
        for (int i = 0; i < volleyAt.length; i++) {
            volleyAt[i] = i < volleyBits.length ? Float.intBitsToFloat(volleyBits[i]) : 0f;
        }
        primed = tag.getBoolean("Primed");
        strikes = tag.getInt("Strikes");
        stores.restore(tag.getInt("Capacitor"), tag.getInt("Bank"));
        syncedCapacitor = stores.capacitor();
        syncedBank = stores.bank();
        if (tag.contains("CapacitorSize")) {
            syncedCapacitorSize = tag.getInt("CapacitorSize");
            syncedBankSize = tag.getInt("BankSize");
        }
        syncedIn = tag.getInt("InDraw");
        syncedOut = tag.getInt("OutExport");
        governed = tag.getBoolean("SecGoverned");
        panelOff = tag.getBoolean("SecOff");
        locked = tag.getBoolean("SecLocked");
        deaf = tag.getBoolean("SecDeaf");
        rules = tag.contains("SecRules") ? tag.getInt("SecRules") : -1;
        houseState = tag.getInt("SecState");
        houseName = tag.getString("SecHouse");
        lastArc = tag.getString("LastArc");
        if (!clientPacket) {
            panelPos = tag.contains("SecPanel") ? BlockPos.of(tag.getLong("SecPanel")) : null;
            panelHousehold = tag.hasUUID("SecHousehold") ? tag.getUUID("SecHousehold") : null;
        }
        relabel();
        if (!clientPacket) {
            memory.unpack(tag.getLongArray("Zapped"));
        } else {
            syncedBoardStud = tag.contains("BoardStud") ? tag.getInt("BoardStud") : -1;
            syncedBoardLive = tag.getBoolean("BoardLive");
            syncedBoardGive = tag.getInt("BoardGive");
            syncedBoardCarried = tag.getInt("BoardCarried");
            syncedBoardCost = tag.getInt("BoardCost");
            syncedBoardReason = tag.getInt("BoardReason");
            watch = tag.getIntArray("Watch");
            final long nextAim = tag.contains("AimAt") ? tag.getLong("AimAt") : Long.MIN_VALUE;
            if (syncedAimAt != Long.MIN_VALUE && nextAim == Long.MIN_VALUE && (lastZap == Long.MIN_VALUE
                    || lastZap < syncedAimAt) && level != null) {
                aimDroppedAt = com.createstats.client.ponder.SceneClock.now(level);
                droppedAimIds = syncedAimIds;
            }
            syncedAimAt = nextAim;
            syncedAimIds = tag.getIntArray("AimIds");
        }
        for (List<StudLink> links : studLinks) {
            links.clear();
        }
        final ListTag spans = tag.getList("StudWires", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < spans.size(); i++) {
            final CompoundTag span = spans.getCompound(i);
            final int stud = span.getByte("S");
            if (StormCoilStuds.isStud(stud)) {
                studLinks.get(stud).add(new StudLink(BlockPos.of(span.getLong("P")),
                        WireTier.byOrdinal(span.getByte("T"))));
            }
        }
    }

    public AABB renderBounds() {
        final AABB body = new AABB(worldPosition).inflate(1.5, 0, 1.5).expandTowards(0, 4, 0);
        AABB box = body;
        for (List<StudLink> links : studLinks) {
            for (StudLink link : links) {
                box = box.minmax(new AABB(link.to()));
            }
        }
        if (lastZap != Long.MIN_VALUE) {
            for (int i = 0; i < volleySize(); i++) {
                final Vec3 to = volleyTarget(i);
                box = box.minmax(new AABB(worldPosition).move(to.x, to.y, to.z).inflate(1));
            }
        }

        final boolean drawingBack = aimDroppedAt != Long.MIN_VALUE && level != null
                && com.createstats.client.ponder.SceneClock.now(level) - aimDroppedAt < 10;
        if (syncedAimAt != Long.MIN_VALUE || drawingBack) {
            final double feel = coilStage().reach / 3.0 + 1.0;
            box = box.minmax(new AABB(worldPosition).inflate(feel, 0, feel).expandTowards(0, stage, 0));
        }

        return box.expandTowards(0, 2, 0);
    }

    public static boolean isCoilBlock(BlockState state) {
        return state.is(ModBlocks.STORM_COIL.get()) || state.getBlock() instanceof StormCoilBlock;
    }
}
