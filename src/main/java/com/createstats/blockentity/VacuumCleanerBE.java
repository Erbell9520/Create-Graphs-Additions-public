package com.createstats.blockentity;

import com.createstats.power.FeGoggle;
import com.createstats.power.FeGridMember;
import com.createstats.power.FeLock;
import com.createstats.power.FeManagedLoad;
import com.createstats.power.FeManagedState;
import com.createstats.power.FePriority;
import com.createstats.power.FeWireNetwork;
import com.createstats.registry.ModDataComponents;
import com.createstats.registry.ModItems;
import com.createstats.registry.ModSounds;
import com.createstats.vacuum.HoseTether;
import com.createstats.vacuum.LooseBlocks;
import com.createstats.vacuum.SuctionEngine;
import com.createstats.vacuum.SuctionReach;
import com.createstats.vacuum.VacuumCatch;
import com.createstats.vacuum.VacuumDraw;
import com.createstats.vacuum.VacuumFrame;
import com.createstats.vacuum.VacuumNet;
import com.createstats.vacuum.VacuumSoundHost;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class VacuumCleanerBE extends SmartBlockEntity implements FeGridMember, FeManagedLoad, VacuumSoundHost,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    public enum State {
        OFF, IDLE, SUCK, DUMP, FULL, DISABLED;

        static State of(int i) {
            final State[] all = values();
            return i >= 0 && i < all.length ? all[i] : OFF;
        }
    }

    public static final double PORT_X = 8.0;
    public static final double PORT_Y = 10.0;
    public static final double PORT_Z = 0.25;

    public static final int DUMP_OPEN = 5;
    public static final int DUMP_TOTAL = 18;

    private static final int SETTLE = 10;

    private int energy;
    private int ratedDraw;
    private int measuredDraw;
    private int spentThisTick;
    private final FeManagedState managed = new FeManagedState();
    @Nullable
    private FeWireNetwork network;

    private final VacuumCatch receiver = new VacuumCatch(9, this::onReceiverChanged);

    @Nullable
    private UUID holder;
    private long suckingUntil = -1000;
    private long lastSuck = -1000;
    private long paidTick = -1;
    private int dumpTick;
    private boolean belowFull;
    private State state = State.OFF;
    private boolean syncDue;
    private int syncCooldown;
    private int caughtTotal;

    @Nullable
    private BlockPos liftPos;
    private int liftTicks;
    private int liftNeeded;
    private int liftStage = -1;

    @Nullable
    private BlockPos liftRefused;
    private long fullWarnedAt = -1000;

    public float flapPrev;
    public float flap;
    private boolean soundPlaying;

    private final IEnergyStorage energyView = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final int room = Math.min(maxReceive, VacuumDraw.CLEANER_BUFFER - energy);
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
            return VacuumDraw.CLEANER_BUFFER;
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

    public VacuumCleanerBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                com.createstats.registry.ModBlockEntities.VACUUM_CLEANER.get(),
                (be, side) -> be.receiver.outside());
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

    public State state() {
        return state;
    }

    public VacuumCatch receiver() {
        return receiver;
    }

    @Nullable
    public UUID holder() {
        return holder;
    }

    public boolean belowFull() {
        return belowFull;
    }

    public int dumpTick() {
        return dumpTick;
    }

    public Vec3 port() {
        return VacuumFrame.horizontal(worldPosition, facing(), PORT_X, PORT_Y, PORT_Z);
    }

    public List<ItemStack> dropContents() {
        return receiver.drain();
    }

    private void onReceiverChanged() {
        setChanged();
        syncDue = true;
    }

    @Nullable
    public Component takeHose(Player player) {
        if (holder != null && !holder.equals(player.getUUID())) {
            final Player other = level.getPlayerByUUID(holder);
            return Component.translatable("block.create_stats.vacuum_cleaner.hose_in_use",
                    other == null ? "someone" : other.getName().getString());
        }
        if (holder != null) {
            return null;
        }
        final ItemStack wand = new ItemStack(ModItems.SUCTION_WAND.get());
        wand.set(ModDataComponents.VACUUM_HOSE.get(), GlobalPos.of(level.dimension(), worldPosition));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, wand);
        holder = player.getUUID();
        level.playSound(null, worldPosition, ModSounds.VACUUM_HOSE_TAKE.get(), SoundSource.BLOCKS, 0.7f, 1.0f);
        syncDue = true;
        setChanged();
        return null;
    }

    public void reclaimHose() {
        if (holder == null) {
            return;
        }
        final Player player = level.getPlayerByUUID(holder);
        if (player != null) {
            removeWandsFrom(player);
        }
        holder = null;
        dropLift();
        level.playSound(null, worldPosition, ModSounds.VACUUM_HOSE_RETURN.get(), SoundSource.BLOCKS, 0.7f, 1.0f);
        syncDue = true;
        setChanged();
    }

    private void removeWandsFrom(Player player) {
        final GlobalPos here = GlobalPos.of(level.dimension(), worldPosition);
        final var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            final ItemStack s = inv.getItem(i);
            if (s.is(ModItems.SUCTION_WAND.get()) && here.equals(s.get(ModDataComponents.VACUUM_HOSE.get()))) {
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    private boolean holds(Player player) {
        final GlobalPos here = GlobalPos.of(level.dimension(), worldPosition);
        final var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            final ItemStack s = inv.getItem(i);
            if (s.is(ModItems.SUCTION_WAND.get()) && here.equals(s.get(ModDataComponents.VACUUM_HOSE.get()))) {
                return true;
            }
        }
        return false;
    }

    public static Vec3 wandHead(Player player) {
        return player.getEyePosition().add(player.getLookAngle().scale(1.05)).add(0, -0.28, 0);
    }

    public void suck(Player player) {
        if (level == null || level.isClientSide() || holder == null || !holder.equals(player.getUUID())) {
            return;
        }
        final long now = level.getGameTime();
        if (state == State.DISABLED || dumpTick > 0) {
            return;
        }
        if (!receiver.hasEmptySlot() && !anyRoom()) {
            warnFull(player, now);
            return;
        }

        final int motor = VacuumDraw.CLEANER_SUCTION;
        if (paidTick != now) {
            if (energy < motor) {
                return;
            }
            energy -= motor;
            spentThisTick += motor;
            paidTick = now;
        }
        suckingUntil = now + 1;
        lastSuck = now;
        final Vec3 head = wandHead(player);
        if (!HoseTether.reaches(new double[] {port().x, port().y, port().z}, new double[] {head.x, head.y, head.z})) {
            reclaimHose();
            return;
        }
        final HitResult hit = player.pick(player.blockInteractionRange(), 1.0f, false);
        final Vec3 at = hit.getLocation();
        final SuctionReach.Box b = SuctionReach.cleaner(at.x, at.y, at.z);

        final AABB box = new AABB(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())
                .minmax(new AABB(head, head).inflate(0.8));
        final Vec3 axis = player.getLookAngle().scale(-1.0);
        int caught = 0;
        for (ItemEntity e : SuctionEngine.items(level, head, box, s -> receiver.room(s) > 0, 16)) {
            final double d = SuctionEngine.centre(e).distanceTo(head);
            if (d > SuctionEngine.CATCH_DISTANCE + 0.25) {
                SuctionEngine.pull(e, head, axis, 1.0, e.getId());
                continue;
            }
            if (caught >= VacuumDraw.CLEANER_CAPTURES_PER_TICK
                    || !VacuumDraw.canAfford(energy, 0, VacuumDraw.CLEANER_PER_ITEM)) {
                continue;
            }
            final ItemStack held = e.getItem();
            final ItemStack rest = receiver.accept(held);
            if (rest.getCount() == held.getCount()) {
                continue;
            }
            energy -= VacuumDraw.CLEANER_PER_ITEM;
            spentThisTick += VacuumDraw.CLEANER_PER_ITEM;
            caught++;
            caughtTotal++;
            VacuumNet.gulp(level, SuctionEngine.centre(e), head, held, e.getId(), 0.2f);
            if (rest.isEmpty()) {
                e.discard();
            } else {
                e.setItem(rest);
            }
        }
        if (player instanceof ServerPlayer server) {
            lift(server, hit, head, now);
        }
    }

    // cracks like mining, then it's whatever a hand break drops
    private void lift(ServerPlayer player, HitResult hit, Vec3 head, long now) {
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            dropLift();
            return;
        }
        BlockPos pos = blockHit.getBlockPos();
        BlockState target = level.getBlockState(pos);

        if (target.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && target.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            pos = pos.below();
            target = level.getBlockState(pos);
        }
        if (liftRefused != null && !liftRefused.equals(pos)) {
            liftRefused = null;
        }
        if (liftRefused != null || !SuctionEngine.loose(level, pos, target)) {
            dropLift();
            return;
        }
        if (!pos.equals(liftPos)) {
            dropLift();
            liftPos = pos.immutable();
            liftNeeded = LooseBlocks.ticksToLift(target.getDestroySpeed(level, pos),
                    target.is(BlockTags.MINEABLE_WITH_SHOVEL), target.requiresCorrectToolForDrops());
        }
        final ServerLevel server = (ServerLevel) level;
        liftTicks = Math.min(liftNeeded, liftTicks + 1);
        final int stage = LooseBlocks.crackStage(liftTicks, liftNeeded);
        if (stage != liftStage) {
            liftStage = stage;
            server.destroyBlockProgress(crackId(), pos, stage);
        }

        if (liftTicks == 1 || now % 4 == 0) {
            final SoundType sound = target.getSoundType(level, pos, player);
            level.playSound(null, pos, sound.getHitSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0f) / 8.0f,
                    sound.getPitch() * 0.5f);
        }
        if (liftTicks < liftNeeded || !VacuumDraw.canAfford(energy, 0, VacuumDraw.CLEANER_PER_BLOCK)) {
            return;
        }

        final ItemStack tool = player.isUsingItem() ? player.getUseItem() : ItemStack.EMPTY;
        final List<ItemStack> drops = Block.getDrops(target, server, pos, null, player, tool);
        if (!receiver.fitsAll(drops)) {
            warnFull(player, now);
            return;
        }
        if (!level.mayInteract(player, pos) || CommonHooks.fireBlockBreak(level,
                player.gameMode.getGameModeForPlayer(), player, pos, target).isCanceled()) {
            dropLift();
            liftRefused = pos.immutable();
            return;
        }
        dropLift();
        if (!level.destroyBlock(pos, false, player)) {
            return;
        }
        energy -= VacuumDraw.CLEANER_PER_BLOCK;
        spentThisTick += VacuumDraw.CLEANER_PER_BLOCK;
        caughtTotal++;
        final Vec3 from = Vec3.atCenterOf(pos);
        int flown = 0;
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) {
                continue;
            }
            receiver.accept(drop);
            if (flown < 3) {
                VacuumNet.gulp(level, from, head, drop, (int) (pos.asLong() * 31 + flown), 0.25f);
                flown++;
            }
        }
    }

    private void dropLift() {
        if (liftPos != null && level instanceof ServerLevel server) {
            server.destroyBlockProgress(crackId(), liftPos, -1);
        }
        liftPos = null;
        liftTicks = 0;
        liftStage = -1;
    }

    private int crackId() {
        return Integer.MIN_VALUE | (worldPosition.hashCode() & 0x7FFFFFFF);
    }

    private void warnFull(Player player, long now) {
        if (now - fullWarnedAt >= 40) {
            fullWarnedAt = now;
            player.displayClientMessage(Component.translatable("item.create_stats.suction_wand.full"), true);
        }
    }

    private boolean anyRoom() {
        for (int i = 0; i < receiver.getSlots(); i++) {
            final ItemStack s = receiver.getStackInSlot(i);
            if (s.isEmpty() || s.getCount() < s.getMaxStackSize()) {
                return true;
            }
        }
        return false;
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
        if (network == null || network.isDirty()) {
            FeWireNetwork.buildFrom(this);
        }
        if (network != null) {
            network.solveOnce(level);
        }
        if (network == null || !network.isPduManaged(level.getGameTime())) {
            setManagedShed(false);
        }
        final long now = level.getGameTime();
        final boolean redstone = level.hasNeighborSignal(worldPosition);
        checkHolder(now);

        final boolean sucking = suckingUntil >= now;
        if (!sucking && liftPos != null) {
            dropLift();
        }
        ratedDraw = redstone || managed.isForcedOff() ? 0
                : (holder != null ? VacuumDraw.CLEANER_SUCTION : VacuumDraw.CLEANER_STANDBY);
        if (!sucking && energy >= VacuumDraw.CLEANER_STANDBY) {
            energy -= VacuumDraw.CLEANER_STANDBY;
            spentThisTick += VacuumDraw.CLEANER_STANDBY;
        }
        measuredDraw = spentThisTick;
        spentThisTick = 0;

        final State before = state;
        dump(now, sucking);
        if (redstone || managed.isSuppressed()) {
            state = State.DISABLED;
        } else if (dumpTick > 0) {
            state = State.DUMP;
        } else if (sucking) {
            state = State.SUCK;
        } else if (energy < VacuumDraw.CLEANER_STANDBY) {
            state = State.OFF;
        } else if (!receiver.hasEmptySlot() && !anyRoom()) {
            state = State.FULL;
        } else {
            state = State.IDLE;
        }
        if (state != before) {
            syncDue = true;
        }
        if (syncDue && --syncCooldown <= 0) {
            syncDue = false;
            syncCooldown = 2;
            sendData();
        }
        setChanged();
    }

    private void checkHolder(long now) {
        if (holder == null || now % 10 != 0) {
            return;
        }
        final Player player = level.getPlayerByUUID(holder);
        if (player == null || !player.isAlive() || !holds(player)) {
            reclaimHose();
            return;
        }
        final Vec3 port = port();
        final Vec3 head = wandHead(player);
        if (!HoseTether.reaches(new double[] {port.x, port.y, port.z}, new double[] {head.x, head.y, head.z})) {
            reclaimHose();
        }
    }

    private void dump(long now, boolean sucking) {
        final IItemHandler below = level.getCapability(Capabilities.ItemHandler.BLOCK, worldPosition.below(), Direction.UP);
        final boolean wasFull = belowFull;
        belowFull = below != null && !acceptsAny(below);
        if (belowFull != wasFull) {
            syncDue = true;
        }
        if (dumpTick > 0) {
            dumpTick++;
            if (dumpTick == DUMP_OPEN) {
                for (int i = 0; i < receiver.getSlots(); i++) {
                    final ItemStack out = receiver.extractItem(i, 64, true);
                    if (out.isEmpty() || below == null) {
                        continue;
                    }
                    final ItemStack rest = ItemHandlerHelper.insertItemStacked(below, out, true);
                    final int moved = out.getCount() - rest.getCount();
                    if (moved > 0) {
                        ItemHandlerHelper.insertItemStacked(below, receiver.extractItem(i, moved, false), false);
                    }
                }
                level.playSound(null, worldPosition, ModSounds.VACUUM_FLAP.get(), SoundSource.BLOCKS, 0.6f, 1.0f);
            }
            if (dumpTick >= DUMP_TOTAL) {
                dumpTick = 0;
                level.playSound(null, worldPosition, ModSounds.VACUUM_FLAP.get(), SoundSource.BLOCKS, 0.45f, 0.75f);
                syncDue = true;
            }
            return;
        }
        if (!sucking && now - lastSuck >= SETTLE && below != null && !belowFull && receiver.stacksHeld() > 0) {
            dumpTick = 1;
            syncDue = true;
        }
    }

    private boolean acceptsAny(IItemHandler below) {
        for (int i = 0; i < receiver.getSlots(); i++) {
            final ItemStack s = receiver.getStackInSlot(i);
            if (!s.isEmpty() && ItemHandlerHelper.insertItemStacked(below, s.copyWithCount(1), true).isEmpty()) {
                return true;
            }
        }

        return receiver.stacksHeld() > 0 ? false
                : ItemHandlerHelper.insertItemStacked(below, new ItemStack(net.minecraft.world.item.Items.STICK), true).isEmpty();
    }

    private void clientTick() {
        flapPrev = flap;
        final float target = dumpTick > 0 && dumpTick < DUMP_TOTAL - 6 ? 1.0f : 0.0f;
        flap += (target - flap) * (target > flap ? 0.45f : 0.22f);
        if (dumpTick > 0) {
            dumpTick = Math.min(DUMP_TOTAL, dumpTick + 1);
        }
        if (state == State.SUCK) {
            com.createstats.client.VacuumSoundInstance.startIfNeeded(this, worldPosition, 1.15f);
        }
    }

    @Override
    public boolean fanRunning() {
        return state == State.SUCK;
    }

    @Override
    public boolean isSoundPlaying() {
        return soundPlaying;
    }

    @Override
    public void setSoundPlaying(boolean playing) {
        soundPlaying = playing;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(FeGoggle.title(Component.translatable("block.create_stats.vacuum_cleaner")));
        tooltip.add(switch (state) {
            case SUCK -> FeGoggle.ok("Sucking");
            case DUMP -> FeGoggle.ok("Dropping a batch");
            case IDLE -> FeGoggle.ok(holder == null ? "Hose parked" : "Hose out, trigger released");
            case FULL -> FeGoggle.warn("Receiver full");
            case DISABLED -> FeGoggle.warn(managed.isSuppressed() ? "Off at the panel" : "Stopped by redstone");
            default -> FeGoggle.bad("No power");
        });
        if (belowFull) {
            tooltip.add(FeGoggle.warn("The block below is full"));
        }
        tooltip.add(FeGoggle.amount("Receiver", receiver.stacksHeld(), receiver.getSlots()));
        tooltip.add(FeGoggle.draw(measuredDraw));
        tooltip.add(FeGoggle.stored(energy, VacuumDraw.CLEANER_BUFFER));
        return true;
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
        return 256;
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
        dropLift();
        if (level != null && !level.isClientSide() && holder != null) {
            final Player player = level.getPlayerByUUID(holder);
            if (player != null) {
                removeWandsFrom(player);
            }
            holder = null;
        }
        super.remove();
    }

    // print a fresh tier, absent ordinal = Critical. every printed one came out Critical
    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        tag.putInt("Tier", FePriority.DEFAULT.ordinal());
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Energy", energy);
        tag.putInt("Tier", managed.priorityOrdinal());
        tag.putInt("Lock", managed.lockOrdinal());
        tag.put("Receiver", receiver.serializeNBT(registries));
        if (holder != null) {
            tag.putUUID("Holder", holder);
        }
        tag.putInt("Dump", dumpTick);
        if (clientPacket) {
            tag.putInt("State", state.ordinal());
            tag.putInt("Draw", measuredDraw);
            tag.putBoolean("BelowFull", belowFull);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        energy = Mth.clamp(tag.getInt("Energy"), 0, VacuumDraw.CLEANER_BUFFER);
        managed.load(tag.getInt("Tier"), tag.getInt("Lock"));
        if (tag.contains("Receiver")) {
            receiver.deserializeNBT(registries, tag.getCompound("Receiver"));
        }
        holder = tag.hasUUID("Holder") ? tag.getUUID("Holder") : null;
        dumpTick = tag.getInt("Dump");
        if (clientPacket) {
            state = State.of(tag.getInt("State"));
            measuredDraw = tag.getInt("Draw");
            belowFull = tag.getBoolean("BelowFull");
        }
    }
}
