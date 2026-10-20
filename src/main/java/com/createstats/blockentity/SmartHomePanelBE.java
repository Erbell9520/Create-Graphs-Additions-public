package com.createstats.blockentity;

import com.createstats.block.SmartHomePanelBlock;
import com.createstats.home.ColumnRule;
import com.createstats.home.FloorBands;
import com.createstats.home.FollowPeople;
import com.createstats.home.HouseModel;
import com.createstats.home.Presence;
import com.createstats.home.RoomFill;
import com.createstats.home.SceneDiff;
import com.createstats.power.Adjustable;
import com.createstats.power.ApplianceStatus;
import com.createstats.power.BoreLinks;
import com.createstats.power.FeGridMember;
import com.createstats.power.FeLock;
import com.createstats.power.FeManagedLoad;
import com.createstats.power.FeManagedState;
import com.createstats.power.FePriority;
import com.createstats.power.FeWireNetwork;
import com.createstats.power.FixtureLinker;
import com.createstats.power.Gateable;
import com.createstats.power.HomeAppliance;
import com.createstats.power.HomeSegment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// discovery costs power, control doesn't. dark panel still works
public class SmartHomePanelBE extends BlockEntity implements FeGridMember, FeManagedLoad,
        FixtureLinker, BoreLinks.Socket,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    public static final int DRAIN = 1;

    private static final int BUFFER = 200;

    private static final int RESCAN_TICKS = 40;

    private static final int RULE_TICKS = 20;

    private static final int ROOM_TICKS = 100;
    private static final int ROOM_TRIES = 4;

    private static final int ROOM_RETRY_TICKS = 1200;

    public static final int MAX_WIRES = 1024;

    private int energy;
    @Nullable
    private FeWireNetwork network;
    private final FeManagedState managed = new FeManagedState();
    private int ratedDraw;
    private int measuredDraw;

    private final List<BlockPos> linked = new ArrayList<>();

    private final List<BlockPos> bores = new ArrayList<>(1);

    private final Set<Long> gatedByMe = new HashSet<>();

    private final Map<Long, String> customNames = new HashMap<>();

    private final HouseModel house = new HouseModel();

    private List<HomeSegment.Device> roster = List.of();

    private int rosterDropped;

    private List<HomeSegment.Hint> unlisted = List.of();

    private List<Integer> storeys = List.of();
    private int panelStorey;

    private int minX;
    private int minZ;
    private int maxX;
    private int maxZ;

    private List<PlayerMark> players = List.of();

    private final Map<Long, Boolean> wasRunning = new HashMap<>();

    private final Map<Long, Component> doneTexts = new HashMap<>();

    private final Map<Long, Float> lastProgress = new HashMap<>();

    private final com.createstats.home.ToastGuard toastGuard = new com.createstats.home.ToastGuard();

    private static final float DONE_AT = 0.75f;

    private long meterTick = -1;

    private boolean houseAsleep;
    private int ruleClock;

    private int roomClock = ROOM_TICKS - 20;

    private int roomCursor;

    private final Map<Long, Long> roomMisses = new HashMap<>();

    private int sinceScan = RESCAN_TICKS;

    private int shownDevices;
    private int shownOn;

    private int shownDraw;

    private int shownStarved;
    private int shownRunning;

    private String owner = "";

    private String title = "";

    private boolean hiFiFace;

    public record PlayerMark(UUID id, String name, int x, int z, float yaw, int floorY) {
    }

    public record WireNode(BlockPos pos, int flow) {
    }

    private final IEnergyStorage energyView = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final int room = Math.min(maxReceive, BUFFER - energy);
            if (room <= 0) {
                return 0;
            }
            if (!simulate) {
                energy += room;
                setChanged();
            }
            return room;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return BUFFER;
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

    public SmartHomePanelBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public IEnergyStorage energyHandle() {
        return energyView;
    }

    public static int drawPerTick() {
        return DRAIN;
    }

    public void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (network == null || network.isDirty()) {
            if (BoreLinks.prune(level, this)) {
                return;
            }
            FeWireNetwork.buildFrom(this);
        }
        if (network != null) {
            network.solveOnce(level);
        }

        if (network == null || !network.isPduManaged(level.getGameTime())) {
            setManagedShed(false);
        }

        final boolean wantsToRun = !managed.isSuppressed();
        ratedDraw = wantsToRun ? DRAIN : 0;
        final boolean awake = wantsToRun && energy >= DRAIN;
        if (awake) {
            energy -= DRAIN;
            measuredDraw = DRAIN;
            setChanged();
        } else {
            measuredDraw = 0;
        }

        if (awake && ++sinceScan >= RESCAN_TICKS) {
            sinceScan = 0;
            rebuild();
        }

        if (awake && ++ruleClock >= RULE_TICKS) {
            ruleClock = 0;
            runRules();
        }
        if (awake && ++roomClock >= ROOM_TICKS) {
            roomClock = 0;
            findRooms();
        }
        refresh(awake);
    }

    private void findRooms() {
        final long now = level.getGameTime();
        final List<HouseModel.Room> known = house.rooms();
        if (!known.isEmpty()) {
            final HouseModel.Room room = known.get(Math.floorMod(roomCursor++, known.size()));
            if (recheck(room)) {
                setChanged();
            }
        }
        final Set<Long> present = new HashSet<>();
        int tries = 0;
        for (HomeSegment.Device d : flatRoster()) {
            final BlockPos a = d.anchor();
            present.add(a.asLong());
            if (tries >= ROOM_TRIES || house.rooms().size() >= HouseModel.MAX_ROOMS
                    || house.roomOf(a.getX(), a.getZ(), d.floorY()) != null) {
                continue;
            }
            final Long retry = roomMisses.get(a.asLong());
            if (retry != null && now < retry) {
                continue;
            }
            tries++;
            if (roomAround(a, d.floorY())) {
                roomMisses.remove(a.asLong());
                setChanged();
            } else {
                roomMisses.put(a.asLong(), now + ROOM_RETRY_TICKS);
            }
        }
        roomMisses.keySet().retainAll(present);
    }

    private boolean recheck(HouseModel.Room room) {
        final int f = room.floorY();
        final List<Long> seeds = new ArrayList<>(4);
        seeds.add(HouseModel.centreCell(room.cells()));
        int i = 0;
        final int step = Math.max(1, room.cells().size() / 3);
        for (long c : room.cells()) {
            if (i++ % step == 0 && seeds.size() < 4 && !seeds.contains(c)) {
                seeds.add(c);
            }
        }
        for (long c : seeds) {
            final RoomFill.Result fill = RoomFill.fill((x, z) -> HomeSegment.classifyColumn(level, x, f, z),
                    RoomFill.unpackX(c), RoomFill.unpackZ(c));
            if (fill.isRoom()) {
                return house.confirmRoom(room.id(), fill.cells());
            }
        }
        return house.missRoom(room.id());
    }

    private static final int MIN_ROOM_CELLS = 4;

    private boolean roomAround(BlockPos at, int f) {
        final int y = roomFloor(at, f);
        final int[][] seeds = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] s : seeds) {
            final RoomFill.Result fill = RoomFill.fill((x, z) -> HomeSegment.classifyColumn(level, x, y, z),
                    at.getX() + s[0], at.getZ() + s[1]);
            if (!fill.isRoom() || fill.cells().size() < MIN_ROOM_CELLS) {
                continue;
            }

            final HouseModel.Room known = house.overlapping(y, fill.cells());
            if (known != null) {
                house.confirmRoom(known.id(), fill.cells());
                return true;
            }
            final String[] named = nameFromContents(fill.cells(), y);
            if (house.addRoom(named[0], named[1], y, fill.cells()) != null) {
                return true;
            }
        }
        return false;
    }

    private int roomFloor(BlockPos at, int f) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (standsDry(at.getX() + d.getStepX(), f - 1, at.getZ() + d.getStepZ())) {
                return f - 1;
            }
        }
        return f;
    }

    // never loads a chunk
    private boolean standsDry(int x, int f, int z) {
        final BlockPos floor = new BlockPos(x, f, z);
        if (!level.isLoaded(floor) || !level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
            return false;
        }
        final BlockPos feet = floor.above();
        final BlockState at = level.getBlockState(feet);
        if (!at.getFluidState().isEmpty()) {
            return false;
        }
        final net.minecraft.world.phys.shapes.VoxelShape shape = at.getCollisionShape(level, feet);
        return shape.isEmpty() || shape.max(Direction.Axis.Y) <= ColumnRule.BARRIER_ABOVE;
    }

    public void rebuild() {
        if (level == null || level.isClientSide()) {
            return;
        }
        lastRebuild = level.getGameTime();
        pruneLinks();
        final HomeSegment.Roster scanned = HomeSegment.scanRoster(level, worldPosition, network, linked, customNames);
        roster = scanned.rows();
        rosterDropped = scanned.dropped();
        unlisted = HomeSegment.unlisted(level, worldPosition, network, linked);
        int on = 0;
        int draw = 0;
        int starved = 0;
        int running = 0;
        for (HomeSegment.Device device : roster) {
            if (device.state() == HomeSegment.State.ON) {
                on++;
            }
            draw = saturatingAdd(draw, device.drawFe());
            for (HomeSegment.Device child : device.plugged()) {
                draw = saturatingAdd(draw, child.drawFe());
            }
        }
        for (HomeSegment.Device device : flatRoster()) {
            if (device.state() == HomeSegment.State.NO_POWER) {
                starved++;
            }
            if (device.status() != null && device.status().running()) {
                running++;
            }
        }
        shownDevices = roster.size();
        shownOn = on;
        shownDraw = draw;
        shownStarved = starved;
        shownRunning = running;
        measureHouse();
        bookMeter(draw);
        noticeFinished();
        players = findPlayers();
    }

    private long lastRebuild = -1;

    public void rebuildIfStale() {
        if (level == null) {
            return;
        }
        final long now = level.getGameTime();
        if (lastRebuild < 0 || now - lastRebuild >= 10 || now < lastRebuild) {
            rebuild();
        }
    }

    private static int saturatingAdd(int a, int b) {
        final long sum = (long) a + Math.max(0, b);
        return (int) Math.min(Integer.MAX_VALUE, sum);
    }

    private void measureHouse() {
        final List<Integer> floors = new ArrayList<>();
        final int panelFloor = HomeSegment.floorUnder(level, worldPosition);
        floors.add(panelFloor);
        int x0 = worldPosition.getX();
        int x1 = x0;
        int z0 = worldPosition.getZ();
        int z1 = z0;
        for (HomeSegment.Device d : flatRoster()) {
            if (d.manual() && d.anchor().distManhattan(worldPosition) > 64) {
                continue;
            }
            floors.add(d.floorY());
            for (BlockPos p : d.members()) {
                x0 = Math.min(x0, p.getX());
                x1 = Math.max(x1, p.getX());
                z0 = Math.min(z0, p.getZ());
                z1 = Math.max(z1, p.getZ());
            }
        }
        for (HouseModel.Room room : house.rooms()) {
            floors.add(room.floorY());
            for (long c : room.cells()) {
                x0 = Math.min(x0, RoomFill.unpackX(c));
                x1 = Math.max(x1, RoomFill.unpackX(c));
                z0 = Math.min(z0, RoomFill.unpackZ(c));
                z1 = Math.max(z1, RoomFill.unpackZ(c));
            }
        }
        storeys = FloorBands.storeys(floors);
        panelStorey = Math.max(0, FloorBands.storeyOf(storeys, panelFloor));
        minX = x0;
        maxX = x1;
        minZ = z0;
        maxZ = z1;
    }

    private void bookMeter(int drawPerTick) {
        final long now = level.getGameTime();
        final long dayTime = level.getDayTime();
        if (meterTick >= 0 && now > meterTick) {
            final long ticks = Math.min(now - meterTick, 2L * RESCAN_TICKS);
            house.meter().add(dayTime, (long) drawPerTick * ticks);
        } else {
            house.meter().advanceTo(dayTime);
        }
        meterTick = now;
    }

    private void noticeFinished() {
        final Set<Long> seen = new HashSet<>();
        final boolean speak = house.toasts() && isAwake();
        final long now = level.getGameTime();
        for (HomeSegment.Device d : flatRoster()) {
            final long key = d.anchor().asLong();
            seen.add(key);
            final ApplianceStatus status = d.status();
            final boolean running = status != null && status.running();
            final Boolean before = wasRunning.put(key, running);
            if (Boolean.TRUE.equals(before) && !running && d.state() == HomeSegment.State.ON && speak
                    && reachedTheEnd(key)) {
                final Component done = doneTexts.get(key);
                if (done != null) {
                    switch (toastGuard.ask(key, now)) {
                        case TOAST -> toast(d, d.name(), done);
                        case LAST -> toast(d, Component.translatable("gui.create_stats.smart_home.toast.muted",
                                d.name()), done);
                        default -> {
                        }
                    }
                }
            }
            if (running) {
                if (status.doneText() != null) {
                    doneTexts.put(key, status.doneText());
                } else {
                    doneTexts.remove(key);
                }
                if (status.progress() != ApplianceStatus.NO_PROGRESS) {
                    lastProgress.put(key, status.progress());
                } else {
                    lastProgress.remove(key);
                }
            } else {
                lastProgress.remove(key);
            }
        }
        wasRunning.keySet().retainAll(seen);
        doneTexts.keySet().retainAll(seen);
        lastProgress.keySet().retainAll(seen);
        toastGuard.retain(seen);
    }

    private boolean reachedTheEnd(long key) {
        final Float progress = lastProgress.get(key);
        return progress == null || progress >= DONE_AT;
    }

    private void toast(HomeSegment.Device d, Component title, Component done) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        final ItemStack icon = new ItemStack(level.getBlockState(d.anchor()).getBlock().asItem());
        final com.createstats.network.SmartHomeToastPacket packet =
                new com.createstats.network.SmartHomeToastPacket(icon, title, done);
        for (PlayerMark mark : players) {
            if (server.getPlayerByUUID(mark.id()) instanceof ServerPlayer p) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, packet);
            }
        }
    }

    private static final int HOME_RADIUS = 16;

    private List<PlayerMark> findPlayers() {
        final List<PlayerMark> out = new ArrayList<>();
        if (storeys.isEmpty()) {
            return out;
        }
        final int low = storeys.get(0) - 2;
        final int high = storeys.get(storeys.size() - 1) + 8;
        for (net.minecraft.world.entity.player.Player p : level.players()) {
            if (p.isSpectator()) {
                continue;
            }
            final BlockPos feet = p.blockPosition();
            if (feet.getY() < low || feet.getY() > high) {
                continue;
            }
            final boolean inFootprint = feet.getX() >= minX - 2 && feet.getX() <= maxX + 2
                    && feet.getZ() >= minZ - 2 && feet.getZ() <= maxZ + 2;
            final long dx = feet.getX() - worldPosition.getX();
            final long dz = feet.getZ() - worldPosition.getZ();
            if (!inFootprint && dx * dx + dz * dz > (long) HOME_RADIUS * HOME_RADIUS) {
                continue;
            }
            out.add(new PlayerMark(p.getUUID(), p.getGameProfile().getName(), feet.getX(), feet.getZ(),
                    p.getYRot(), HomeSegment.floorUnder(level, feet)));
            if (out.size() >= 16) {
                break;
            }
        }
        return out;
    }

    private void runRules() {
        final long now = level.getGameTime();
        players = findPlayers();
        final Presence.Edge edge = house.presence().update(players.size(), now);
        if (edge == Presence.Edge.LAST_OUT) {
            runRule(house.awayScene(), false);
        } else if (edge == Presence.Edge.FIRST_HOME) {
            if (house.homeScene() != HouseModel.LIGHTS || level.isNight()) {
                runRule(house.homeScene(), true);
            }
        }

        boolean allAsleep = !players.isEmpty();
        if (level instanceof ServerLevel server) {
            for (PlayerMark mark : players) {
                if (!(server.getPlayerByUUID(mark.id()) instanceof ServerPlayer p) || !p.isSleeping()) {
                    allAsleep = false;
                }
            }
        }
        if (allAsleep && !houseAsleep) {
            runRule(house.bedtimeScene(), false);
        }
        houseAsleep = allAsleep;

        for (HouseModel.Room room : house.rooms()) {
            if (!room.followPeople()) {
                continue;
            }
            boolean occupied = false;
            for (PlayerMark mark : players) {
                occupied |= room.contains(mark.x(), mark.z(), mark.floorY());
            }
            final FollowPeople.Action action = room.follow().update(occupied, now, darkIn(room));
            if (action != FollowPeople.Action.NONE) {
                setRoomLights(room, action == FollowPeople.Action.ON);
            }
        }
    }

    private static final int DIM_SKY = 8;

    private boolean darkIn(HouseModel.Room room) {
        if (level.isNight()) {
            return true;
        }
        final long centre = HouseModel.centreCell(room.cells());
        final BlockPos feet = new BlockPos(RoomFill.unpackX(centre), room.floorY() + 1, RoomFill.unpackZ(centre));
        return level.isLoaded(feet) && level.getBrightness(net.minecraft.world.level.LightLayer.SKY, feet) < DIM_SKY;
    }

    private void runRule(int what, boolean lightsOn) {
        if (what == HouseModel.LIGHTS) {
            setLights(-1, lightsOn);
        } else if (what >= 0) {
            recallScene(what);
        }
    }

    public void setLights(int roomId, boolean on) {
        for (HomeSegment.Device d : flatRoster()) {
            if (d.category() == HomeAppliance.Category.LIGHTING && (roomId == -1 || matchesRoom(d, roomId))) {
                for (BlockPos pos : d.members()) {
                    applyGate(pos, !on);
                }
            }
        }
        rebuild();
    }

    private void setRoomLights(HouseModel.Room room, boolean on) {
        for (HomeSegment.Device d : flatRoster()) {
            if (d.category() == HomeAppliance.Category.LIGHTING && inRoom(d, room)) {
                for (BlockPos pos : d.members()) {
                    applyGate(pos, !on);
                }
            }
        }
        rebuild();
    }

    private boolean inRoom(HomeSegment.Device d, HouseModel.Room room) {
        final HouseModel.Room of = house.roomOf(d.anchor().getX(), d.anchor().getZ(), d.floorY());
        return of != null && of.id() == room.id();
    }

    private void pruneLinks() {
        if (level == null) {
            return;
        }
        boolean changed = false;
        for (int i = linked.size() - 1; i >= 0; i--) {
            final BlockPos pos = linked.get(i);
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (!(level.getBlockEntity(pos) instanceof Gateable)) {
                linked.remove(i);
                changed = true;
            }
        }
        if (changed) {
            setChanged();
        }
    }

    private void refresh(boolean awake) {
        if (level == null || !(getBlockState().getBlock() instanceof SmartHomePanelBlock)) {
            return;
        }
        SmartHomePanelBlock.Screen band = SmartHomePanelBlock.Screen.DARK;
        if (awake) {
            band = SmartHomePanelBlock.Screen.LIVE;
            if (roster.isEmpty()) {
                band = SmartHomePanelBlock.Screen.ALERT;
            } else {
                for (HomeSegment.Device device : roster) {
                    if (device.state() == HomeSegment.State.NO_POWER) {
                        band = SmartHomePanelBlock.Screen.ALERT;
                        break;
                    }
                }
            }
        }
        final BlockState state = getBlockState();
        if (state.getValue(SmartHomePanelBlock.SCREEN) != band) {
            level.setBlock(worldPosition, state.setValue(SmartHomePanelBlock.SCREEN, band),
                    Block.UPDATE_ALL);
        } else if (shownDevices != savedDevices || shownOn != savedOn || shownDraw != savedDraw
                || shownStarved != savedStarved || shownRunning != savedRunning) {
            savedDevices = shownDevices;
            savedOn = shownOn;
            savedDraw = shownDraw;
            savedStarved = shownStarved;
            savedRunning = shownRunning;
            setChanged();
            level.sendBlockUpdated(worldPosition, state, state, 2);
        }
    }

    private int savedDevices = -1;
    private int savedOn = -1;
    private int savedDraw = -1;
    private int savedStarved = -1;
    private int savedRunning = -1;

    public List<HomeSegment.Device> roster() {
        return roster;
    }

    private List<HomeSegment.Device> flatRoster() {
        final List<HomeSegment.Device> out = new ArrayList<>(roster.size());
        for (HomeSegment.Device d : roster) {
            out.add(d);
            out.addAll(d.plugged());
        }
        return out;
    }

    public List<HomeSegment.Hint> unlisted() {
        return unlisted;
    }

    public int rosterDropped() {
        return rosterDropped;
    }

    public HouseModel house() {
        return house;
    }

    public List<Integer> storeys() {
        return storeys;
    }

    public int panelStorey() {
        return panelStorey;
    }

    public int[] footprint() {
        return new int[] {minX, minZ, maxX, maxZ};
    }

    public List<PlayerMark> players() {
        return players;
    }

    public int[] dayChart() {
        return level == null ? new int[com.createstats.home.DayMeter.HOURS] : house.meter().chart(level.getDayTime());
    }

    public int hourOfDay() {
        return level == null ? 0 : com.createstats.home.DayMeter.hourOfDay(level.getDayTime());
    }

    public List<WireNode> wiring() {
        if (network == null || level == null) {
            return List.of();
        }
        final long now = level.getGameTime();
        final List<WireNode> out = new ArrayList<>();
        for (FeGridMember member : network.members()) {
            if (member.attachedStorage() != null || member == this) {
                continue;
            }
            final long key = member.nodeKey();
            final int flow = network.flowThrough(key, now);
            out.add(new WireNode(BlockPos.of(key),
                    flow == com.createstats.power.FeFlowGraph.UNKNOWN ? -1 : Math.abs(flow)));
            if (out.size() >= MAX_WIRES) {
                break;
            }
        }
        return out;
    }

    public int shownDevices() {
        return shownDevices;
    }

    public int shownOn() {
        return shownOn;
    }

    public int shownDrawFe() {
        return shownDraw;
    }

    public int shownStarved() {
        return shownStarved;
    }

    public int shownRunning() {
        return shownRunning;
    }

    public Component houseName() {
        if (!title.isEmpty()) {
            return Component.literal(title);
        }
        return owner.isEmpty()
                ? Component.translatable("gui.create_stats.smart_home.house_anon")
                : Component.translatable("gui.create_stats.smart_home.house", owner);
    }

    public boolean hasTitle() {
        return !title.isEmpty();
    }

    public void setTitle(String name) {
        final String trimmed = name == null ? "" : name.strip();
        final String next = trimmed.substring(0, Math.min(trimmed.length(), HouseModel.MAX_NAME));
        if (next.equals(title) || level == null) {
            return;
        }
        title = next;
        setChanged();
        if (!level.isClientSide()) {
            final BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 2);
        }
    }

    public boolean hasOwner() {
        return !owner.isEmpty();
    }

    public void setOwner(String name) {
        this.owner = name == null ? "" : name.substring(0, Math.min(name.length(), 32));
        setChanged();
        if (level != null && !level.isClientSide()) {
            final BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 2);
        }
    }

    public boolean isHiFiFace() {
        return hiFiFace;
    }

    public void setHiFiFace(boolean hiFi) {
        if (hiFiFace == hiFi || level == null) {
            return;
        }
        hiFiFace = hiFi;
        setChanged();
        if (!level.isClientSide()) {
            final BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 2);
        }
    }

    public boolean isAwake() {
        return getBlockState().hasProperty(SmartHomePanelBlock.SCREEN)
                && getBlockState().getValue(SmartHomePanelBlock.SCREEN)
                        != SmartHomePanelBlock.Screen.DARK;
    }

    // only rows on our own roster, otherwise the packet is "switch off any block anywhere"
    public void setRowGated(BlockPos anchor, boolean gated) {
        final HomeSegment.Device row = findDeep(anchor);
        if (row == null) {
            return;
        }
        for (BlockPos pos : row.members()) {
            applyGate(pos, gated);
        }
        rebuild();
    }

    public void setCustomName(BlockPos anchor, String name) {
        final String trimmed = name == null ? "" : name.strip();
        if (trimmed.isEmpty()) {
            if (customNames.remove(anchor.asLong()) == null) {
                return;
            }
        } else {
            if (findDeep(anchor) == null) {
                return;
            }
            customNames.put(anchor.asLong(), trimmed.substring(0, Math.min(trimmed.length(), 32)));
        }
        setChanged();
        rebuild();
    }

    public void setRowMode(BlockPos anchor, int mode) {
        final HomeSegment.Device row = findDeep(anchor);
        if (row == null || level == null) {
            return;
        }
        for (BlockPos pos : row.members()) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof Adjustable adjustable) {
                adjustable.setMode(mode);
            }
        }
        rebuild();
    }

    public void setRowColour(BlockPos anchor, int colourId) {
        final HomeSegment.Device row = findDeep(anchor);
        if (row == null || level == null || colourId < 0 || colourId > 15) {
            return;
        }
        for (BlockPos pos : row.members()) {
            applyColour(pos, colourId);
        }
        rebuild();
    }

    private void applyColour(BlockPos pos, int colourId) {
        if (!level.isLoaded(pos)) {
            return;
        }
        final BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(com.createstats.block.LampBlock.COLOR)) {
            return;
        }
        final DyeColor dye = DyeColor.byId(colourId);
        if (state.getValue(com.createstats.block.LampBlock.COLOR) != dye) {
            level.setBlock(pos, state.setValue(com.createstats.block.LampBlock.COLOR, dye), Block.UPDATE_ALL);
        }
    }

    public void setRoomGated(int roomId, boolean gated) {
        for (HomeSegment.Device d : flatRoster()) {
            if (roomId == -1 || matchesRoom(d, roomId)) {
                for (BlockPos pos : d.members()) {
                    applyGate(pos, gated);
                }
            }
        }
        rebuild();
    }

    public void setKindGated(int category, boolean gated) {
        final HomeAppliance.Category kind = HomeAppliance.Category.byOrdinal(category);
        for (HomeSegment.Device d : flatRoster()) {
            if (d.category() == kind) {
                for (BlockPos pos : d.members()) {
                    applyGate(pos, gated);
                }
            }
        }
        rebuild();
    }

    private boolean matchesRoom(HomeSegment.Device d, int roomId) {
        final HouseModel.Room of = house.roomOf(d.anchor().getX(), d.anchor().getZ(), d.floorY());
        return roomId == -2 ? of == null : of != null && of.id() == roomId;
    }

    public void switchOffStandby() {
        for (HomeSegment.Device d : flatRoster()) {
            if (isStandby(d)) {
                for (BlockPos pos : d.members()) {
                    applyGate(pos, true);
                }
            }
        }
        rebuild();
    }

    public static boolean isStandby(HomeSegment.Device d) {
        return d.state() == HomeSegment.State.ON && d.drawFe() > 0
                && d.category() != HomeAppliance.Category.LIGHTING
                && d.category() != HomeAppliance.Category.SOCKETS
                && d.status() != null && !d.status().running();
    }

    private void applyGate(BlockPos pos, boolean gated) {
        if (level == null || !level.isLoaded(pos)) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof Gateable fixture)) {
            return;
        }
        if (fixture.isGated() != gated) {
            fixture.setGated(gated);
        }
        if (gated) {
            gatedByMe.add(pos.asLong());
        } else {
            gatedByMe.remove(pos.asLong());
        }
        setChanged();
    }

    @Nullable
    private HomeSegment.Device findDeep(BlockPos anchor) {
        for (HomeSegment.Device row : roster) {
            if (row.anchor().equals(anchor)) {
                return row;
            }
            for (HomeSegment.Device child : row.plugged()) {
                if (child.anchor().equals(anchor)) {
                    return child;
                }
            }
        }
        return null;
    }

    public Map<Long, SceneDiff.State> currentStates() {
        final Map<Long, SceneDiff.State> out = new LinkedHashMap<>();
        for (HomeSegment.Device d : flatRoster()) {
            out.put(d.anchor().asLong(), stateOf(d));
        }
        return out;
    }

    public static SceneDiff.State stateOf(HomeSegment.Device d) {
        final boolean on = d.state() != HomeSegment.State.OFF;
        return new SceneDiff.State(on, d.modes().isEmpty() ? -1 : d.mode(), d.colour());
    }

    public void saveScene(int slot, int roomId, boolean lightsOnly) {
        rebuild();
        final Map<Long, SceneDiff.State> states = new LinkedHashMap<>();
        for (HomeSegment.Device d : flatRoster()) {
            if (d.state() == HomeSegment.State.MISSING) {
                continue;
            }
            if (roomId >= 0 && !matchesRoom(d, roomId)) {
                continue;
            }
            if (lightsOnly && d.category() != HomeAppliance.Category.LIGHTING) {
                continue;
            }
            states.put(d.anchor().asLong(), stateOf(d));
        }
        final HouseModel.Scene old = house.scene(slot);
        final String name = old != null ? old.name() : "Scene " + (slot + 1);
        final String icon = old != null ? old.icon()
                : HouseModel.SCENE_ICONS.get(slot % HouseModel.SCENE_ICONS.size());
        house.saveScene(slot, name, icon, states);
        setChanged();
    }

    public void recallScene(int slot) {
        final HouseModel.Scene scene = house.scene(slot);
        if (scene == null || level == null) {
            return;
        }
        for (HomeSegment.Device d : flatRoster()) {
            final SceneDiff.State target = scene.states().get(d.anchor().asLong());
            if (target == null || !SceneDiff.differs(stateOf(d), target)) {
                continue;
            }

            if (!target.on() && d.status() != null && d.status().running()) {
                continue;
            }
            for (BlockPos pos : d.members()) {
                applyGate(pos, !target.on());
                if (target.on()) {
                    if (target.mode() >= 0 && level.getBlockEntity(pos) instanceof Adjustable adjustable
                            && adjustable.getMode() != target.mode()) {
                        adjustable.setMode(target.mode());
                    }
                    if (target.colour() >= 0) {
                        applyColour(pos, target.colour());
                    }
                }
            }
        }
        house.markRecalled(slot);
        setChanged();
        rebuild();
    }

    public void deleteScene(int slot) {
        house.deleteScene(slot);
        setChanged();
    }

    @Nullable
    public String claimRoom(BlockPos floor) {
        if (level == null) {
            return "gui.create_stats.smart_home.claim.not_room";
        }
        final int f = floor.getY();
        final RoomFill.Result fill = RoomFill.fill((x, z) -> HomeSegment.classifyColumn(level, x, f, z),
                floor.getX(), floor.getZ());
        switch (fill.refusal()) {
            case NOT_FLOOR -> {
                return "gui.create_stats.smart_home.claim.not_floor";
            }
            case ESCAPED -> {
                return "gui.create_stats.smart_home.claim.open";
            }
            case TOO_BIG -> {
                return "gui.create_stats.smart_home.claim.too_big";
            }
            default -> {
            }
        }
        final String[] named = nameFromContents(fill.cells(), f);
        final HouseModel.Room room = house.addRoom(named[0], named[1], f, fill.cells());
        if (room == null) {
            return house.rooms().size() >= HouseModel.MAX_ROOMS
                    ? "gui.create_stats.smart_home.claim.full" : "gui.create_stats.smart_home.claim.overlap";
        }
        setChanged();
        rebuild();
        return null;
    }

    private String[] nameFromContents(Set<Long> cells, int f) {
        boolean kitchen = false;
        boolean laundry = false;
        for (HomeSegment.Device d : flatRoster()) {
            final int x = d.anchor().getX();
            final int z = d.anchor().getZ();
            final boolean inside = cells.contains(RoomFill.pack(x, z)) || cells.contains(RoomFill.pack(x + 1, z))
                    || cells.contains(RoomFill.pack(x - 1, z)) || cells.contains(RoomFill.pack(x, z + 1))
                    || cells.contains(RoomFill.pack(x, z - 1));
            if (!inside || Math.abs(d.floorY() - f) > FloorBands.SAME_STOREY) {
                continue;
            }
            kitchen |= d.category() == HomeAppliance.Category.KITCHEN;
            laundry |= d.category() == HomeAppliance.Category.LAUNDRY;
        }

        int beds = 0;
        int books = 0;
        int workshop = 0;
        int baths = 0;
        int storage = 0;
        int seats = 0;
        int rugs = 0;
        int exits = 0;
        boolean brewing = false;
        int x0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int z0 = Integer.MAX_VALUE;
        int z1 = Integer.MIN_VALUE;
        for (long c : cells) {
            x0 = Math.min(x0, RoomFill.unpackX(c));
            x1 = Math.max(x1, RoomFill.unpackX(c));
            z0 = Math.min(z0, RoomFill.unpackZ(c));
            z1 = Math.max(z1, RoomFill.unpackZ(c));
            final BlockPos feet = new BlockPos(RoomFill.unpackX(c), f + 1, RoomFill.unpackZ(c));
            for (Direction dir : new Direction[] {null, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                final BlockPos p = dir == null ? feet : feet.relative(dir);

                if (!level.isLoaded(p)) {
                    continue;
                }
                final BlockState s = level.getBlockState(p);

                final boolean own = dir == null;
                if (s.is(BlockTags.BEDS)) {
                    beds++;
                }
                if (s.is(Blocks.BOOKSHELF) || s.is(Blocks.CHISELED_BOOKSHELF) || s.is(Blocks.LECTERN)
                        || s.is(Blocks.ENCHANTING_TABLE)) {
                    books++;
                }
                if (s.is(Blocks.CRAFTING_TABLE) || s.is(BlockTags.ANVIL) || s.is(Blocks.SMITHING_TABLE)
                        || s.is(Blocks.FURNACE) || s.is(Blocks.BLAST_FURNACE) || s.is(Blocks.STONECUTTER)
                        || s.is(Blocks.GRINDSTONE) || s.is(Blocks.LOOM) || s.is(Blocks.CARTOGRAPHY_TABLE)) {
                    workshop++;
                }
                kitchen |= s.is(Blocks.SMOKER);
                brewing |= s.is(Blocks.BREWING_STAND);
                if (s.is(Blocks.CAULDRON) || s.is(Blocks.WATER_CAULDRON)) {
                    baths++;
                }
                if (own && (s.is(Blocks.CHEST) || s.is(Blocks.BARREL) || s.is(Blocks.TRAPPED_CHEST))) {
                    storage++;
                }
                if (own && s.getBlock() instanceof net.minecraft.world.level.block.StairBlock) {
                    seats++;
                }
                if (own && s.is(BlockTags.WOOL_CARPETS)) {
                    rugs++;
                }

                if (!own && s.is(BlockTags.DOORS)) {
                    final BlockPos beyond = p.relative(dir);
                    if (level.isLoaded(beyond) && !HomeSegment.roofed(level, beyond.getX(), f, beyond.getZ())) {
                        exits++;
                    }
                }
            }
        }
        final com.createstats.home.RoomNaming.Kind named = com.createstats.home.RoomNaming.kind(kitchen, laundry,
                beds, books, workshop, baths, storage, seats, rugs, brewing, exits, x1 - x0 + 1, z1 - z0 + 1);
        if (named != null) {
            final Set<String> taken = new HashSet<>();
            for (HouseModel.Room r : house.rooms()) {
                taken.add(r.name());
            }
            return new String[] {com.createstats.home.RoomNaming.unique(named.name(), taken), named.icon()};
        }
        return new String[] {"Room " + nextRoomNumber(), "minecraft:lantern"};
    }

    private int nextRoomNumber() {
        final Set<String> taken = new HashSet<>();
        for (HouseModel.Room r : house.rooms()) {
            taken.add(r.name());
        }
        int n = 1;
        while (taken.contains("Room " + n)) {
            n++;
        }
        return n;
    }

    public void deleteRoom(int id) {
        house.removeRoom(id);
        setChanged();
    }

    @Override
    public BlockPos linkerPos() {
        return worldPosition;
    }

    @Override
    public List<BlockPos> linkedFixtures() {
        return linked;
    }

    @Override
    public boolean toggleLink(BlockPos fixture) {
        if (linked.remove(fixture)) {
            applyGate(fixture, false);
            setChanged();
            rebuild();
            return false;
        }
        linked.add(fixture.immutable());
        setChanged();
        rebuild();
        return true;
    }

    public void toggleLinkIfLinked(BlockPos fixture) {
        if (linked.contains(fixture)) {
            toggleLink(fixture);
        }
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
        return 64;
    }

    @Override
    public IEnergyStorage attachedStorage() {
        return energyView;
    }

    @Override
    public boolean isGone(Level owner) {
        return isRemoved() || getLevel() != owner;
    }

    @Override
    public void setLastFlow(int flow) {
    }

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
        // scan next tick, not here. a flood per block change while someone builds a wall, no thanks

        sinceScan = RESCAN_TICKS;
    }

    @Override
    public BlockPos borePosition() {
        return worldPosition;
    }

    @Override
    public List<BlockPos> bores() {
        return bores;
    }

    @Override
    public boolean addBore(BlockPos far) {
        if (bores.contains(far)) {
            return false;
        }
        bores.add(far.immutable());
        return true;
    }

    @Override
    public boolean removeBore(BlockPos far) {
        return bores.remove(far);
    }

    @Override
    public void invalidate(Level owner) {
        setChanged();
        invalidateNetwork();
        if (owner != null && !owner.isClientSide()) {
            owner.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
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
        if (managed.isShed() == shed) {
            return;
        }
        managed.setShed(shed);
    }

    @Override
    public boolean isManagedShed() {
        return managed.isShed();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(com.createstats.power.FeGoggle.title("Smart Home Panel"));
        if (!isAwake()) {
            tooltip.add(com.createstats.power.FeGoggle.bad("No power, discovery paused"));
        } else if (shownDevices == 0) {
            tooltip.add(com.createstats.power.FeGoggle.warn("Nothing found on this circuit"));
        } else {
            tooltip.add(com.createstats.power.FeGoggle.ok(shownOn + " of " + shownDevices + " on"));
        }
        if (!linked.isEmpty()) {
            tooltip.add(com.createstats.power.FeGoggle.dim(linked.size() + " linked by hand"));
        }
        tooltip.add(com.createstats.power.FeGoggle.draw(measuredDraw));
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy);
        tag.putInt("Tier", managed.priorityOrdinal());
        tag.putInt("Lock", managed.lockOrdinal());
        tag.putInt("Devices", shownDevices);
        tag.putInt("On", shownOn);
        tag.putInt("Draw", shownDraw);
        tag.putInt("Starved", shownStarved);
        tag.putInt("Running", shownRunning);
        tag.putString("Owner", owner);
        tag.putString("Title", title);
        tag.putBoolean("HiFi", hiFiFace);
        final long[] links = new long[linked.size()];
        for (int i = 0; i < linked.size(); i++) {
            links[i] = linked.get(i).asLong();
        }
        tag.putLongArray("Linked", links);
        BoreLinks.save(tag, bores);
        final long[] held = new long[gatedByMe.size()];
        int at = 0;
        for (long key : gatedByMe) {
            held[at++] = key;
        }
        tag.putLongArray("Held", held);
        final net.minecraft.nbt.ListTag names = new net.minecraft.nbt.ListTag();
        for (Map.Entry<Long, String> entry : customNames.entrySet()) {
            final CompoundTag one = new CompoundTag();
            one.putLong("Pos", entry.getKey());
            one.putString("Name", entry.getValue());
            names.add(one);
        }
        tag.put("Names", names);
        final CompoundTag houseTag = new CompoundTag();
        house.save(houseTag);
        tag.put("House", houseTag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy = Math.max(0, Math.min(BUFFER, tag.getInt("Energy")));
        managed.load(tag.getInt("Tier"), tag.getInt("Lock"));
        shownDevices = Math.max(0, tag.getInt("Devices"));
        shownOn = Math.max(0, tag.getInt("On"));
        shownDraw = Math.max(0, tag.getInt("Draw"));
        shownStarved = Math.max(0, tag.getInt("Starved"));
        shownRunning = Math.max(0, tag.getInt("Running"));
        owner = tag.getString("Owner");
        title = tag.getString("Title");
        hiFiFace = tag.getBoolean("HiFi");
        savedDevices = shownDevices;
        savedOn = shownOn;
        savedDraw = shownDraw;
        savedStarved = shownStarved;
        savedRunning = shownRunning;
        linked.clear();
        for (long packed : tag.getLongArray("Linked")) {
            linked.add(BlockPos.of(packed));
        }
        bores.clear();
        bores.addAll(BoreLinks.load(tag));
        gatedByMe.clear();
        for (long packed : tag.getLongArray("Held")) {
            gatedByMe.add(packed);
        }
        customNames.clear();
        final net.minecraft.nbt.ListTag names = tag.getList("Names", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < names.size(); i++) {
            final CompoundTag one = names.getCompound(i);
            final String value = one.getString("Name");
            if (!value.isEmpty()) {
                customNames.put(one.getLong("Pos"), value);
            }
        }
        if (tag.contains("House")) {
            house.load(tag.getCompound("House"));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        tag.remove("House");
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<
            net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadAdditional(tag, registries);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        invalidateNetwork();
        if (level != null && level.isClientSide()) {
            com.createstats.renderer.SmartHomeFaceTextureManager.disposeFor(worldPosition);
        }

        if (level != null && !level.isClientSide()) {
            for (long key : new ArrayList<>(gatedByMe)) {
                applyGate(BlockPos.of(key), false);
            }
        }
    }
}
