package com.createstats.train.power;

import com.createstats.power.TrainBankLedger;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import com.createstats.power.FeRates;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class TrainChargeProbe {
    private static final Map<Contraption, ContraptionFeStores> STORES = new WeakHashMap<>();

    private TrainChargeProbe() {
    }

    public static ContraptionFeStores storesOf(Contraption contraption) {
        synchronized (STORES) {
            return STORES.computeIfAbsent(contraption, ContraptionFeStores::scan);
        }
    }

    @Nullable
    public static TrainChargeReading read(Train train, long now, String where) {
        int stored = 0;
        int capacity = 0;
        int loaded = 0;
        for (Carriage carriage : train.carriages) {
            final CarriageContraptionEntity entity = carriage.anyAvailableEntity();
            if (entity == null || entity.getContraption() == null) {
                continue;
            }
            loaded++;
            final TrainBankLedger ledger = storesOf(entity.getContraption()).ledger();
            stored = FeRates.plus(stored, ledger.stored());
            capacity = FeRates.plus(capacity, ledger.capacity());
        }
        if (loaded == 0) {
            return null;
        }
        final String label = loaded < train.carriages.size()
                ? (where.isEmpty() ? "" : where + ", ") + loaded + "/" + train.carriages.size() + " cars"
                : where;
        return new TrainChargeReading(stored, capacity, now, label);
    }

    public record CarReading(int index, int stored, int capacity) {
    }

    // unloaded cars are left out, not read as empty
    public static List<CarReading> readCars(Train train) {
        final List<CarReading> out = new ArrayList<>();
        for (int i = 0; i < train.carriages.size(); i++) {
            final CarriageContraptionEntity entity = train.carriages.get(i).anyAvailableEntity();
            if (entity == null || entity.getContraption() == null) {
                continue;
            }
            final TrainBankLedger ledger = storesOf(entity.getContraption()).ledger();
            if (ledger.capacity() > 0) {
                out.add(new CarReading(i, ledger.stored(), ledger.capacity()));
            }
        }
        return out;
    }

    @Nullable
    public static TrainChargeReading readAndRecord(Level level, Train train, String where) {
        final long now = level.getGameTime();
        final TrainChargeReading live = read(train, now, where);
        final TrainChargeLedger ledger = TrainChargeLedger.get(level.getServer());
        if (ledger == null) {
            return live;
        }
        if (live != null) {
            ledger.record(train.id, live);
            return live;
        }
        return ledger.get(train.id);
    }

    @Nullable
    public static UUID trainOf(@Nullable AbstractContraptionEntity entity) {
        return entity instanceof CarriageContraptionEntity carriage ? carriage.trainId : null;
    }
}
