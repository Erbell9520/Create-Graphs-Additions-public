package com.createstats.train.power;

import com.createstats.block.CapacitorBankBlock;
import com.createstats.blockentity.CapacitorBankBE;
import com.createstats.mixin.ContraptionUpdateTagsAccessor;
import com.createstats.network.DeviceSyncPacket;
import com.createstats.network.TrainChargeSyncPacket;
import com.createstats.power.FeRates;
import com.simibubi.create.Create;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class CarriedDeviceSync {
    private static final class Awake {
        final CarriedBankTelemetry telemetry = new CarriedBankTelemetry();
        final DeviceSyncThrottle<BlockPos> charge = new DeviceSyncThrottle<>();
        final DeviceSyncThrottle<BlockPos> inRate = new DeviceSyncThrottle<>();
        final DeviceSyncThrottle<BlockPos> outRate = new DeviceSyncThrottle<>();
        boolean pending;
    }

    private static final Map<AbstractContraptionEntity, Awake> AWAKE = new WeakHashMap<>();

    private static final long SUMMARY_TICKS = 20L;

    private static final long SUMMARY_KEEPALIVE_TICKS = 100L;

    private static final Map<UUID, Integer> LAST_TRAIN_STORED = new HashMap<>();
    private static final DeviceSyncThrottle<UUID> TRAIN_THROTTLE = new DeviceSyncThrottle<>();

    private CarriedDeviceSync() {
    }

    public static void touched(AbstractContraptionEntity entity) {
        if (entity == null || entity.level().isClientSide()) {
            return;
        }
        AWAKE.computeIfAbsent(entity, e -> new Awake()).pending = true;
    }

    public static void tick(Level level) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        final long now = server.getGameTime();
        final Iterator<Map.Entry<AbstractContraptionEntity, Awake>> it = AWAKE.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<AbstractContraptionEntity, Awake> entry = it.next();
            final AbstractContraptionEntity entity = entry.getKey();
            if (entity == null || entity.isRemoved() || entity.getContraption() == null) {
                it.remove();
                continue;
            }
            if (entity.level() != server) {
                continue;
            }
            if (step(entity, entry.getValue(), now)) {
                it.remove();
            }
        }
        if (Level.OVERWORLD.equals(server.dimension()) && now % SUMMARY_TICKS == 0L) {
            sendTrainSummaries(now);
        }
    }

    private static boolean step(AbstractContraptionEntity entity, Awake awake, long now) {
        final Contraption contraption = entity.getContraption();
        final ContraptionFeStores stores = TrainChargeProbe.storesOf(contraption);
        final List<CarriedBankTelemetry.Cell> cells = stores.bankCells();
        final Map<BlockPos, CarriedBankTelemetry.Cell> cellAt = new HashMap<>(cells.size() * 2);
        for (CarriedBankTelemetry.Cell cell : cells) {
            cellAt.put(cell.pos(), cell);
        }
        final List<CarriedBankTelemetry.Bank> banks = awake.telemetry.step(cells,
                CapacitorBankBE.maxFootprint(), CapacitorBankBE.maxHeight());
        final Map<BlockPos, StructureBlockInfo> blocks = contraption.getBlocks();
        final List<DeviceSyncPacket.Entry> entries = new ArrayList<>();
        boolean waiting = false;
        for (CarriedBankTelemetry.Bank bank : banks) {
            final BlockPos controller = bank.controller();
            final CompoundTag controllerTag = stores.tagAt(controller);
            boolean rateNews = false;
            if (controllerTag != null) {
                controllerTag.putInt("InRate", bank.inRate());
                controllerTag.putInt("OutRate", bank.outRate());
                controllerTag.putInt("Rate", bank.inRate() - bank.outRate());
                rateNews = awake.inRate.rateWorthSending(controller, bank.inRate(), now)
                        || awake.outRate.rateWorthSending(controller, bank.outRate(), now);
                waiting |= !rateNews && (awake.inRate.differs(controller, bank.inRate())
                        || awake.outRate.differs(controller, bank.outRate()));
            }
            for (BlockPos pos : bank.members()) {
                final CompoundTag tag = stores.tagAt(pos);
                final StructureBlockInfo info = blocks.get(pos);
                final CarriedBankTelemetry.Cell cell = cellAt.get(pos);
                if (tag == null || info == null || cell == null) {
                    continue;
                }
                BlockState state = null;
                if (info.state().hasProperty(CapacitorBankBlock.LEVEL)
                        && info.state().getValue(CapacitorBankBlock.LEVEL) != bank.ledLevel()) {
                    state = info.state().setValue(CapacitorBankBlock.LEVEL, bank.ledLevel());
                    blocks.put(pos, new StructureBlockInfo(info.pos(), state, info.nbt()));
                }
                refreshUpdateTag(contraption, pos, tag);
                final boolean isController = pos.equals(controller);
                final boolean chargeNews = awake.charge.chargeWorthSending(pos, cell.energy(),
                        cell.capacity(), now);
                if (state != null || chargeNews || (isController && rateNews)) {
                    entries.add(new DeviceSyncPacket.Entry(pos, state, tag.copy()));
                    awake.charge.sent(pos, cell.energy(), now);
                    if (isController) {
                        awake.inRate.sent(pos, bank.inRate(), now);
                        awake.outRate.sent(pos, bank.outRate(), now);
                    }
                } else if (awake.charge.differs(pos, cell.energy())) {
                    waiting = true;
                }
            }
        }
        if (!entries.isEmpty()) {
            PacketDistributor.sendToPlayersTrackingEntity(entity,
                    new DeviceSyncPacket(entity.getId(), entries));
        }
        final boolean wasPending = awake.pending;
        awake.pending = false;
        return !wasPending && !waiting && awake.telemetry.isSettled();
    }

    private static void refreshUpdateTag(Contraption contraption, BlockPos pos, CompoundTag saved) {
        final CompoundTag update = ((ContraptionUpdateTagsAccessor) contraption).createStats$updateTags().get(pos);
        if (update != null) {
            update.merge(saved);
        }
    }

    private static void sendTrainSummaries(long now) {
        final Set<UUID> live = new HashSet<>();
        for (Train train : Create.RAILWAYS.trains.values()) {
            final List<TrainChargeProbe.CarReading> cars = TrainChargeProbe.readCars(train);
            if (cars.isEmpty()) {
                continue;
            }
            live.add(train.id);
            int stored = 0;
            int capacity = 0;
            int lowestCar = -1;
            int lowestPct = Integer.MAX_VALUE;
            for (TrainChargeProbe.CarReading car : cars) {
                stored = FeRates.plus(stored, car.stored());
                capacity = FeRates.plus(capacity, car.capacity());
                final int pct = (int) Math.floor(100.0 * car.stored() / Math.max(1, car.capacity()));
                if (pct < lowestPct) {
                    lowestPct = pct;
                    lowestCar = car.index();
                }
            }
            final Integer last = LAST_TRAIN_STORED.put(train.id, stored);
            final int net = last == null ? 0 : (int) ((stored - (long) last) / SUMMARY_TICKS);
            final boolean news = TRAIN_THROTTLE.chargeWorthSending(train.id, stored, capacity, now)
                    || net != 0 || now % SUMMARY_KEEPALIVE_TICKS == 0L;
            if (!news) {
                continue;
            }
            TRAIN_THROTTLE.sent(train.id, stored, now);
            final TrainChargeSyncPacket packet = new TrainChargeSyncPacket(new TrainChargeSyncPacket.Summary(
                    train.id, stored, capacity, net, lowestCar, Math.max(0, Math.min(100, lowestPct))));
            for (Carriage carriage : train.carriages) {
                final CarriageContraptionEntity entity = carriage.anyAvailableEntity();
                if (entity != null && !entity.isRemoved()) {
                    PacketDistributor.sendToPlayersTrackingEntity(entity, packet);
                }
            }
        }
        LAST_TRAIN_STORED.keySet().retainAll(live);
        TRAIN_THROTTLE.retain(live::contains);
    }
}
