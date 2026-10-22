package com.createstats.blockentity;

import com.createstats.pducabinet.CabinetGeometry;
import com.createstats.pducabinet.CabinetReadout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class PduCabinetBE extends PowerDistributionUnitBE {
    private CabinetReadout readout = CabinetReadout.empty();
    private final List<Long> history = new ArrayList<>();
    private boolean doorOpen;
    private double doorFrom;
    private long doorStart;
    private double doorDuration = 24;
    private String lastEvent = "WAITING FOR OUTPUT";
    private long eventTick;
    private List<String> previousStates = List.of();
    private double flagFrom, flagTarget;
    private long instrumentStart;

    public PduCabinetBE(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }

    @Override
    public void onLoad() {
        super.onLoad();
        com.createstats.pducabinet.CabinetTelemetry.watch(this);
    }

    public void captureReadout() {
        if (level == null || level.isClientSide()) return;

        int[][] traffic = completedPortTraffic();
        List<CabinetReadout.Port> ports = new ArrayList<>();
        // request/admitted only refreshes in Active Auto, don't call an old request live

        boolean demandKnown = mode() == com.createstats.power.PduMode.AUTO;
        for (int i = 0; i < LEGS; i++) {
            final int leg = i;
            boolean shed = roster().stream().anyMatch(d -> d.leg() == leg && d.shed());
            ports.add(new CabinetReadout.Port(traffic[0][i], traffic[1][i], legRating(i), demandKnown ? legShownDemand(i) : 0,
                    isLegWired(i), isLegClosed(i), isLegTripped(i), demandKnown && isLegAtCapacity(i), shed));
        }
        List<String> states = ports.stream().map(CabinetReadout.Port::state).toList();
        if (!states.equals(previousStates)) {
            int changed = 0;
            while (changed < LEGS && !previousStates.isEmpty() && states.get(changed).equals(previousStates.get(changed))) changed++;
            lastEvent = previousStates.isEmpty() ? "OUTPUT MONITOR READY"
                    : (changed == BACK_LEG ? "BUS" : String.format(java.util.Locale.ROOT, "%02d", changed + 1))
                    + " / " + states.get(Math.min(changed, BACK_LEG));
            eventTick = level.getGameTime();
            previousStates = states;
        }
        int[] inputs = completedBossIntakes();
        CabinetReadout next = new CabinetReadout(mode().name(), inputs[0], inputs[1], inputRatePerBoss(),
                ports, history, lastEvent, eventTick);
        if (level.getGameTime() % 5 == 0) {
            history.add(next.total());
            if (history.size() > 32) history.remove(0);
            next = new CabinetReadout(next.mode(), inputs[0], inputs[1], next.inputLimit(), ports,
                    history, lastEvent, eventTick);
            readout = next;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        } else readout = next;
    }

    public CabinetReadout readout() { return readout; }
    public boolean doorOpen() { return doorOpen; }

    private double instrument(double from, double target, float partial) {
        double t = Math.max(0, Math.min(1, (level == null ? 0 : level.getGameTime() - instrumentStart + partial) / 6.0));
        return from + (target - from) * t * t * (3 - 2 * t);
    }

    public double flagHeight(float partial) { return instrument(flagFrom, flagTarget, partial); }

    public double doorAngle(float partial) {
        return CabinetGeometry.doorAngle(doorFrom, doorOpen,
                level == null ? 0 : level.getGameTime() - doorStart + partial, doorDuration);
    }

    public void toggleDoor() {
        if (level == null || level.isClientSide()) return;
        doorFrom = doorAngle(0);
        doorOpen = !doorOpen;
        doorStart = level.getGameTime();
        doorDuration = Math.max(1, 24 * Math.abs((doorOpen ? CabinetGeometry.OPEN_ANGLE : 0) - doorFrom)
                / CabinetGeometry.OPEN_ANGLE);
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("CabDoor", doorOpen);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        doorOpen = tag.getBoolean("CabDoor");
        if (tag.contains("CabPorts")) {
            doorFrom = tag.getDouble("CabDoorFrom");
            doorStart = tag.getLong("CabDoorStart");
            doorDuration = tag.getDouble("CabDoorDuration");
            int[] data = tag.getIntArray("CabPorts");
            List<CabinetReadout.Port> ports = new ArrayList<>();
            for (int i = 0; i < LEGS; i++) {
                int b = i * 9;
                if (data.length != LEGS * 9) break;
                ports.add(new CabinetReadout.Port(data[b], data[b+1], data[b+2], data[b+3],
                        data[b+4] != 0, data[b+5] != 0, data[b+6] != 0, data[b+7] != 0, data[b+8] != 0));
            }
            if (ports.size() == LEGS) {
                List<Long> trace = java.util.Arrays.stream(tag.getLongArray("CabHistory")).limit(32).boxed().toList();
                readout = new CabinetReadout(mode().name(), tag.getInt("CabInA"), tag.getInt("CabInB"),
                        tag.getInt("CabInLimit"), ports, trace, tag.getString("CabEvent"), tag.getLong("CabEventTick"));

                double flag = readout.flags() > 0 ? 1 : 0;
                if (flag != flagTarget) {
                    flagFrom = flagHeight(0);
                    flagTarget = flag;
                    instrumentStart = level == null ? 0 : level.getGameTime();
                }
            }
        } else {
            doorFrom = doorOpen ? CabinetGeometry.OPEN_ANGLE : 0;
            doorStart = 0;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putDouble("CabDoorFrom", doorFrom);
        tag.putLong("CabDoorStart", doorStart);
        tag.putDouble("CabDoorDuration", doorDuration);
        int[] data = new int[LEGS * 9];
        for (int i = 0; i < LEGS; i++) {
            CabinetReadout.Port p = readout.ports().get(i);
            int b = i * 9;
            int[] values = {p.delivered(), p.intake(), p.rating(), p.requested(), p.wired() ? 1 : 0,
                    p.closed() ? 1 : 0, p.tripped() ? 1 : 0, p.limited() ? 1 : 0, p.shed() ? 1 : 0};
            System.arraycopy(values, 0, data, b, 9);
        }
        tag.putIntArray("CabPorts", data);
        tag.putInt("CabInA", readout.inputA());
        tag.putInt("CabInB", readout.inputB());
        tag.putInt("CabInLimit", readout.inputLimit());
        tag.putLongArray("CabHistory", readout.history().stream().mapToLong(Long::longValue).toArray());
        tag.putString("CabEvent", readout.event());
        tag.putLong("CabEventTick", readout.eventTick());
        return tag;
    }
}
