package com.createstats.power;

import com.createstats.block.CableBlock;
import com.createstats.blockentity.CableBE;
import com.createstats.blockentity.SwitchBE;
import com.createstats.blockentity.PowerConnectorBE;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

public final class FeWireNetwork {
    private final List<FeGridMember> nodes;

    private final List<FeGridMember> served;
    private boolean dirty;
    private long lastSolvedTick = Long.MIN_VALUE;

    private int distributionCursor;
    private FeGridSolver.Result lastResult = FeGridSolver.Result.EMPTY;

    private static final int MANAGED_GRACE_TICKS = 5;
    private long managedUntilTick = Long.MIN_VALUE;

    @Nullable
    private FeManagerView managerView;
    private long managerViewTick = Long.MIN_VALUE;

    private boolean wasPduManaged;

    private final FeTickTotal managedThroughput = new FeTickTotal();

    private final FeTickTotal seamFlow = new FeTickTotal();

    private final Map<Long, Integer> flowByNode = new HashMap<>();

    private FeFlowGraph flow = FeFlowGraph.of(Map.of(), Map.of());

    private final Map<Long, Integer> seamByNode = new HashMap<>();
    private final Map<Long, Integer> seamByNodeLast = new HashMap<>();
    private long seamNodeTick = Long.MIN_VALUE;

    // one throat for the whole circuit when one cable is common to every source and sink
    private final int conduitCeiling;

    private final FeConduitThroat throat;

    private long budgetTick = Long.MIN_VALUE;

    private int budgetLeft;

    private final List<BlockPos> cappingSupplies;

    private final boolean hasUncappedSupply;

    private final FeSupplyBudget supplyBudget = new FeSupplyBudget();

    private FePathBudget pathBudget = FePathBudget.NONE;

    private long[] moveFrom = new long[0];
    private long[] moveTo = new long[0];
    private int[] moveFe = new int[0];

    private boolean pathsBind;

    private java.util.function.LongSupplier clock = () -> 0L;

    private final Set<Long> machinePositions;

    private final Set<Long> nodePositions;

    private final Set<Long> devicePositions;

    private FeWireNetwork(List<FeGridMember> nodes, List<FeGridMember> served,
                          Set<Long> machinePositions, FeConduitThroat throat,
                          List<BlockPos> cappingSupplies, boolean hasUncappedSupply) {
        this.nodes = nodes;
        this.served = served;
        this.machinePositions = machinePositions;
        this.throat = throat;
        this.cappingSupplies = cappingSupplies;
        this.hasUncappedSupply = hasUncappedSupply;
        this.conduitCeiling = throat.ceiling();
        this.nodePositions = new HashSet<>(nodes.size() * 2);
        for (FeGridMember node : nodes) {
            this.nodePositions.add(node.nodeKey());
        }
        this.devicePositions = new HashSet<>(served.size() * 2);
        for (FeGridMember node : served) {
            final BlockPos device = servedDeviceOf(node);
            if (device != null) {
                this.devicePositions.add(device.asLong());
            }
        }
    }

    public boolean knowsPosition(BlockPos pos) {
        final long key = pos.asLong();
        return machinePositions.contains(key) || nodePositions.contains(key);
    }

    public boolean reachesDevice(BlockPos pos) {
        final long key = pos.asLong();
        return devicePositions.contains(key) || machinePositions.contains(key)
                || nodePositions.contains(key);
    }

    public static FeWireNetwork buildFrom(PowerConnectorBE origin) {
        return build(origin.getLevel(), origin);
    }

    public static FeWireNetwork buildFrom(CableBE origin) {
        return build(origin.getLevel(), origin);
    }

    public static FeWireNetwork buildFrom(com.createstats.blockentity.IndoorCableBE origin) {
        return build(origin.getLevel(), origin);
    }

    public static FeWireNetwork buildFrom(SwitchBE origin) {
        return build(origin.getLevel(), origin);
    }

    public static FeWireNetwork buildFrom(com.createstats.blockentity.InductionRangeBE origin) {
        return build(origin.getLevel(), origin);
    }

    public static FeWireNetwork buildFrom(com.createstats.blockentity.LampBE origin) {
        return build(origin.getLevel(), origin);
    }

    public static FeWireNetwork buildFrom(com.createstats.blockentity.FluidPumpBE origin) {
        return build(origin.getLevel(), origin);
    }

    public static FeWireNetwork buildFrom(com.createstats.blockentity.FeMotorBE origin) {
        return build(origin.getLevel(), origin);
    }

    public static FeWireNetwork buildFrom(com.createstats.blockentity.PortablePowerInterfaceBE origin) {
        return build(origin.getLevel(), origin);
    }

    public static FeWireNetwork buildFrom(com.createstats.blockentity.TrackFeederBE origin) {
        return build(origin.getLevel(), origin);
    }

    public static FeWireNetwork buildFrom(com.createstats.blockentity.ArcFurnaceBE origin) {
        return build(origin.getLevel(), origin);
    }

    public static FeWireNetwork buildFrom(com.createstats.blockentity.InductionSmelterBE origin) {
        return build(origin.getLevel(), origin);
    }

    public static FeWireNetwork buildFrom(com.createstats.blockentity.CoffeeMachineBE origin) {
        return build(origin.getLevel(), origin);
    }

    public static FeWireNetwork buildFrom(com.createstats.blockentity.PaSpeakerBE origin) {
        return build(origin.getLevel(), origin);
    }

    public static FeWireNetwork buildFrom(com.createstats.blockentity.AnnouncementComputerBE origin) {
        return build(origin.getLevel(), origin);
    }

    public static FeWireNetwork buildFrom(com.createstats.blockentity.WaterCoolerBE origin) {
        return build(origin.getLevel(), origin);
    }

    public static FeWireNetwork buildFrom(com.createstats.blockentity.AirFryerBE origin) {
        return build(origin.getLevel(), origin);
    }

    public static FeWireNetwork buildFrom(com.createstats.blockentity.BulkWasherBE origin) {
        return build(origin.getLevel(), origin);
    }

    public static FeWireNetwork buildFrom(com.createstats.blockentity.ToasterBE origin) {
        return build(origin.getLevel(), origin);
    }

    public static FeWireNetwork buildFrom(com.createstats.blockentity.VacuumCollectorBE origin) {
        return build(origin.getLevel(), origin);
    }

    public static FeWireNetwork buildFrom(com.createstats.blockentity.VacuumCleanerBE origin) {
        return build(origin.getLevel(), origin);
    }

    public static FeWireNetwork buildFrom(com.createstats.blockentity.VacuumSnifferBE origin) {
        return build(origin.getLevel(), origin);
    }

    public static FeWireNetwork buildFrom(com.createstats.blockentity.MobRepellerBE origin) {
        return build(origin.getLevel(), origin);
    }

    public static FeWireNetwork buildFrom(com.createstats.blockentity.SeismicScannerBE origin) {
        return build(origin.getLevel(), origin);
    }

    public static FeWireNetwork buildFrom(com.createstats.blockentity.SparkCrownBE origin) {
        return build(origin.getLevel(), origin);
    }

    public static FeWireNetwork buildFrom(com.createstats.blockentity.BreadSlicerBE origin) {
        return build(origin.getLevel(), origin);
    }

    public static FeWireNetwork buildFrom(com.createstats.blockentity.OvenBE origin) {
        return build(origin.getLevel(), origin);
    }

    public static FeWireNetwork buildFrom(com.createstats.blockentity.BiofuelBurnerBE origin) {
        return build(origin.getLevel(), origin);
    }

    public static FeWireNetwork buildFrom(com.createstats.blockentity.SolarPanelBE origin) {
        return build(origin.getLevel(), origin);
    }

    public static FeWireNetwork buildFrom(com.createstats.blockentity.SmartHomePanelBE origin) {
        return build(origin.getLevel(), origin);
    }

    public static FeWireNetwork buildFrom(com.createstats.blockentity.WeatherPanelBE origin) {
        return build(origin.getLevel(), origin);
    }

    public static FeWireNetwork buildFrom(com.createstats.blockentity.HomeSecurityPanelBE origin) {
        return build(origin.getLevel(), origin);
    }

    public static FeWireNetwork buildFrom(com.createstats.blockentity.SecuritySensorBE origin) {
        return build(origin.getLevel(), origin);
    }

    public static FeWireNetwork buildFrom(com.createstats.blockentity.SecurityRelayBE origin) {
        return build(origin.getLevel(), origin);
    }

    public static FeWireNetwork buildFrom(com.createstats.blockentity.BlastShutterBE origin) {
        return build(origin.getLevel(), origin);
    }

    public static FeWireNetwork buildFrom(com.createstats.blockentity.SecurityLockerBE origin) {
        return build(origin.getLevel(), origin);
    }

    public static FeWireNetwork buildFrom(com.createstats.blockentity.DiagnosticJunctionBE origin) {
        return build(origin.getLevel(), origin);
    }

    public static FeWireNetwork buildFrom(com.createstats.blockentity.PoweredDoorBE origin) {
        return build(origin.getLevel(), origin);
    }

    public static FeWireNetwork buildFrom(com.createstats.blockentity.GarageDoorBE origin) {
        return build(origin.getLevel(), origin);
    }

    @javax.annotation.Nullable
    public static FeWireNetwork liveNetworkOf(
            @javax.annotation.Nullable net.minecraft.world.level.block.entity.BlockEntity be) {
        if (!(be instanceof FeGridMember member) || be.getLevel() == null) {
            return null;
        }
        final FeWireNetwork known = member.network();
        if (known != null && !known.isDirty()) {
            return known;
        }
        final FeWireNetwork built = build(be.getLevel(), be);
        return built != null && built.size() > 0 ? built : null;
    }

    private static FeWireNetwork build(Level level, Object origin) {
        final FeWireNetwork previous = origin instanceof FeGridMember member ? member.network() : null;
        final List<FeGridMember> found = new ArrayList<>();
        final Walk walk = new Walk();

        final Map<BlockPos, FeAttachment> attachments = new HashMap<>();

        final Map<BlockPos, BlockPos> flowOwners = new HashMap<>();

        final Map<BlockPos, CableBE> surfaceOwners = new HashMap<>();

        final Map<BlockPos, com.createstats.blockentity.IndoorCableBE> conduitOwners = new HashMap<>();
        walk.start(posOf(origin), origin);

        while (!walk.isEmpty()) {
            final Object node = walk.poll();
            if (node instanceof PowerConnectorBE connector) {
                found.add(connector);

                walkTerminalLinks(connector, level, walk);
                enqueueTouchingCable(level, connector.getBlockPos(), walk);

                connector.measureCableFeed();
            } else if (node instanceof SwitchBE sw) {
                if (!sw.isClosed()) {
                    continue;
                }
                found.add(sw);

                spreadFromBlock(level, sw.getBlockPos(), walk, true);
            } else if (node instanceof CableBE cable) {
                found.add(cable);
                spreadAlongRun(level, cable.getBlockPos(), walk);

                spreadFromBlock(level, cable.getBlockPos(), walk, false);
                spreadAcrossFamilies(level, cable.getBlockPos(), walk, true);
                spreadAlongBores(level, cable, walk);
                collectBoreAttachments(level, cable, cable.transferRate(), attachments, flowOwners,
                        surfaceOwners, walk);
                collectAttachments(level, cable, attachments, flowOwners, surfaceOwners, walk);
            } else if (node instanceof com.createstats.blockentity.IndoorCableBE conduit) {
                found.add(conduit);
                spreadAlongConduit(level, conduit.getBlockPos(), walk);

                spreadFromBlock(level, conduit.getBlockPos(), walk, false, true);
                spreadAcrossFamilies(level, conduit.getBlockPos(), walk, false);
                spreadAlongBores(level, conduit, walk);
                collectConduitAttachments(level, conduit, attachments, conduitOwners, flowOwners,
                        walk);
            } else if (node instanceof com.createstats.blockentity.InductionRangeBE range) {
                found.add(range);
                spreadFromBlock(level, range.getBlockPos(), walk, true);
                spreadToBank(level, range.getBlockPos(), walk);
            } else if (node instanceof com.createstats.blockentity.LampBE lamp) {
                found.add(lamp);
                spreadFromBlock(level, lamp.getBlockPos(), walk, true);
            } else if (node instanceof com.createstats.blockentity.FluidPumpBE pump) {
                found.add(pump);
                spreadFromBlock(level, pump.getBlockPos(), walk, true);
                walkTerminalLinks(pump, level, walk);
            } else if (node instanceof com.createstats.blockentity.FeMotorBE motor) {
                found.add(motor);
                spreadFromBlock(level, motor.getBlockPos(), walk, true);
                walkTerminalLinks(motor, level, walk);
            } else if (node instanceof com.createstats.blockentity.PortablePowerInterfaceBE portable) {
                found.add(portable);
                spreadFromBlock(level, portable.getBlockPos(), walk, true);
            } else if (node instanceof com.createstats.blockentity.TrackFeederBE feeder) {
                found.add(feeder);
                spreadFromFace(level, feeder.getBlockPos(), feeder.backFace(), walk);
            } else if (node instanceof com.createstats.blockentity.ArcFurnaceBE furnace) {
                found.add(furnace);
                spreadFromFace(level, furnace.getBlockPos(), furnace.backFace(), walk);
            } else if (node instanceof com.createstats.blockentity.InductionSmelterBE smelter) {
                found.add(smelter);
                spreadFromFace(level, smelter.getBlockPos(), smelter.backFace(), walk);
                spreadToSmelterBank(level, smelter.getBlockPos(), walk);
            } else if (node instanceof com.createstats.blockentity.CoffeeMachineBE coffee) {
                found.add(coffee);
                spreadFromFace(level, coffee.getBlockPos(), coffee.backFace(), walk);
                walkTerminalLinks(coffee, level, walk);
            } else if (node instanceof com.createstats.blockentity.WaterCoolerBE cooler) {
                found.add(cooler);
                spreadFromFace(level, cooler.getBlockPos(), cooler.backFace(), walk);
                walkTerminalLinks(cooler, level, walk);
            } else if (node instanceof com.createstats.blockentity.AirFryerBE fryer) {
                found.add(fryer);
                spreadFromFace(level, fryer.getBlockPos(), fryer.backFace(), walk);
                walkTerminalLinks(fryer, level, walk);
            } else if (node instanceof com.createstats.blockentity.BulkWasherBE washer) {
                found.add(washer);
                spreadFromFace(level, washer.getBlockPos(), washer.backFace(), walk);
                walkTerminalLinks(washer, level, walk);
            } else if (node instanceof com.createstats.blockentity.ToasterBE toaster) {
                found.add(toaster);
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    spreadFromFace(level, toaster.getBlockPos(), side, walk);
                }
                walkTerminalLinks(toaster, level, walk);
            } else if (node instanceof com.createstats.blockentity.VacuumCollectorBE collector) {
                found.add(collector);
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    if (side != collector.facing()) {
                        spreadFromFace(level, collector.getBlockPos(), side, walk);
                    }
                }
            } else if (node instanceof com.createstats.blockentity.VacuumCleanerBE cleaner) {
                found.add(cleaner);
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    if (side != cleaner.facing()) {
                        spreadFromFace(level, cleaner.getBlockPos(), side, walk);
                    }
                }
            } else if (node instanceof com.createstats.blockentity.VacuumSnifferBE sniffer) {
                found.add(sniffer);
                for (Direction side : Direction.values()) {
                    if (side != sniffer.facing()) {
                        spreadFromFace(level, sniffer.getBlockPos(), side, walk);
                    }
                }
            } else if (node instanceof com.createstats.blockentity.MobRepellerBE repeller) {
                found.add(repeller);
                for (Direction side : Direction.values()) {
                    if (repeller.takesPowerOn(side)) {
                        spreadFromFace(level, repeller.getBlockPos(), side, walk);
                    }
                }
            } else if (node instanceof com.createstats.blockentity.SeismicScannerBE scanner) {
                found.add(scanner);
                for (Direction side : Direction.values()) {
                    if (scanner.takesPowerOn(side)) {
                        spreadFromFace(level, scanner.getBlockPos(), side, walk);
                    }
                }
            } else if (node instanceof com.createstats.blockentity.SparkCrownBE crown) {
                found.add(crown);
                if (walk.isOrigin(crown.getBlockPos())) {
                    final BlockPos home = crown.homeLink();
                    if (home == null) {
                        spreadFromFace(level, crown.getBlockPos(), crown.backFace(), walk);
                    } else {
                        walkTerminalLinks(crown, level, walk, home);
                    }
                }
            } else if (node instanceof com.createstats.blockentity.BreadSlicerBE slicer) {
                found.add(slicer);
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    spreadFromFace(level, slicer.getBlockPos(), side, walk);
                }
                walkTerminalLinks(slicer, level, walk);
            } else if (node instanceof com.createstats.blockentity.OvenBE oven) {
                found.add(oven);
                final Direction ovenDoor = oven.frontFace();
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    if (side != ovenDoor) {
                        spreadFromFace(level, oven.getBlockPos(), side, walk);
                    }
                }
                walkTerminalLinks(oven, level, walk);
            } else if (node instanceof com.createstats.blockentity.BiofuelBurnerBE burner) {
                // same three faces as the oven, reversed

                found.add(burner);
                final Direction burnerDoor = burner.frontFace();
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    if (side != burnerDoor) {
                        spreadFromFace(level, burner.getBlockPos(), side, walk);
                    }
                }
                walkTerminalLinks(burner, level, walk);
            } else if (node instanceof com.createstats.blockentity.SolarPanelBE solar) {
                found.add(solar);
                for (Direction side : Direction.values()) {
                    if (side != Direction.UP) {
                        spreadFromFace(level, solar.getBlockPos(), side, walk);
                    }
                }

                spreadAcrossPanels(level, solar.getBlockPos(), walk);
            } else if (node instanceof com.createstats.blockentity.SmartHomePanelBE panel) {
                found.add(panel);
                spreadFromBlock(level, panel.getBlockPos(), walk, true);
                spreadAlongBores(level, panel, walk);
            } else if (node instanceof com.createstats.blockentity.WeatherPanelBE weather) {
                found.add(weather);
                spreadFromBlock(level, weather.getBlockPos(), walk, true);
                spreadAlongBores(level, weather, walk);
            } else if (node instanceof com.createstats.blockentity.HomeSecurityPanelBE keypad) {
                found.add(keypad);
                spreadFromBlock(level, keypad.getBlockPos(), walk, true);
                spreadAlongBores(level, keypad, walk);
            } else if (node instanceof com.createstats.blockentity.SecuritySensorBE sensor) {
                found.add(sensor);
                spreadFromBlock(level, sensor.getBlockPos(), walk, true);
                spreadAlongBores(level, sensor, walk);
            } else if (node instanceof com.createstats.blockentity.SecurityRelayBE relay) {
                found.add(relay);
                spreadFromBlock(level, relay.getBlockPos(), walk, true);
                spreadAlongBores(level, relay, walk);
            } else if (node instanceof com.createstats.blockentity.SecurityLockerBE locker) {
                found.add(locker);
                spreadFromBlock(level, locker.getBlockPos(), walk, true);
                spreadAlongBores(level, locker, walk);
            } else if (node instanceof com.createstats.blockentity.PaSpeakerBE speaker) {
                found.add(speaker);
                spreadFromBlock(level, speaker.getBlockPos(), walk, true);
                walkTerminalLinks(speaker, level, walk);
            } else if (node instanceof com.createstats.blockentity.AnnouncementComputerBE brain) {
                found.add(brain);
                spreadFromFace(level, brain.getBlockPos(), brain.backFace(), walk);
                walkTerminalLinks(brain, level, walk);
            } else if (node instanceof com.createstats.blockentity.PoweredDoorBE door) {
                found.add(door);
                spreadFromBlock(level, door.getBlockPos(), walk, true);
                spreadFromBlock(level, door.getBlockPos().above(), walk, true);

                spreadFromWrappingRun(level, door.getBlockPos(), walk);
                spreadFromWrappingRun(level, door.getBlockPos().above(), walk);

                spreadAlongBores(level, door, walk);
            } else if (node instanceof com.createstats.blockentity.BlastShutterBE shutter) {
                found.add(shutter);
                for (BlockPos block : shutter.columnPositions()) {
                    spreadFromBlock(level, block, walk, true);

                    spreadFromWrappingRun(level, block, walk);
                }
                spreadAlongBores(level, shutter, walk);
            } else if (node instanceof com.createstats.blockentity.GarageDoorBE garage) {
                found.add(garage);
                for (BlockPos frame : garage.frame()) {
                    spreadFromBlock(level, frame, walk, true);

                    spreadFromWrappingRun(level, frame, walk);
                }
                spreadAlongBores(level, garage, walk);
            } else if (node instanceof ForeignTerminalNode bridge) {
                found.add(bridge);
            } else if (node instanceof com.createstats.blockentity.MainBreakerBE breaker) {
                // attachment not member, a member would be walked through and fuse the two circuits. the breaker lesson

                final BlockPos breakerAt = breaker.getBlockPos();
                final Long via = walk.parentOf.get(breakerAt.asLong());
                final int stud = via == null ? -1 : breaker.studOf(BlockPos.of(via));
                if (stud >= 0 && breaker.isPassthrough() && walk.crossPassthrough()) {
                    final int far = MainBreakerStuds.otherStud(stud);
                    for (com.createstats.blockentity.MainBreakerBE.StudLink link
                            : breaker.studLinks(far)) {
                        if (!level.isLoaded(link.to())) {
                            continue;
                        }
                        final net.minecraft.world.level.block.entity.BlockEntity onward =
                                level.getBlockEntity(link.to());
                        if (onward instanceof FeWireTerminal parallel && parallel.feedsInParallel()) {
                            reachParallel(parallel, onward, link.to(), breakerAt, breakerAt, true,
                                    FeGridSolver.NO_CEILING, walk);
                        } else if (onward instanceof FeWireTerminal) {
                            walk.enqueue(breakerAt, link.to(), onward);
                        }
                    }
                } else if (stud >= 0 && !attachments.containsKey(breakerAt)) {
                    attachments.put(breakerAt, new FeAttachment(breakerAt, breaker.portFor(stud),
                            Math.max(1, breaker.studRate(stud))));
                    flowOwners.put(breakerAt, BlockPos.of(via));
                }
            } else if (node instanceof com.createstats.blockentity.StormCoilBE coil) {
                // main breaker's shape, leaf never junction

                final BlockPos coilAt = coil.getBlockPos();
                final Long via = walk.parentOf.get(coilAt.asLong());
                final int stud = via == null ? -1 : coil.studOf(BlockPos.of(via));
                if (stud >= 0 && !attachments.containsKey(coilAt)) {
                    attachments.put(coilAt, new FeAttachment(coilAt, coil.portFor(stud),
                            Math.max(1, coil.studRate(stud)), coil.rateNode(stud)));
                    flowOwners.put(coilAt, BlockPos.of(via));
                }
            } else if (node instanceof com.createstats.blockentity.UniversalPowerCouplerBE coupler) {
                final BlockPos at = coupler.getBlockPos();
                if (!attachments.containsKey(at)) {
                    attachments.put(at, new FeAttachment(at, coupler.gridHandle(),
                            wireRateInto(level, walk, at)));
                    final Long parent = walk.parentOf.get(at.asLong());
                    if (parent != null) {
                        flowOwners.put(at, BlockPos.of(parent));
                    }
                }
            } else if (node instanceof com.createstats.blockentity.DiagnosticJunctionBE junction
                    && junction.isCableSplice() && !junction.isCutOff()) {
                found.add(junction);
                spreadFromBlock(level, junction.getBlockPos(), walk, true);
            }
        }

        final List<ParallelSink> parallelSinks = resolveParallel(walk, attachments, flowOwners);

        // A machine already served by a connector is dropped here rather than left to be served

        for (FeGridMember node : found) {
            if (node instanceof PowerConnectorBE connector) {
                final BlockPos host = connector.getBlockPos().relative(connector.mountedOn());

                if (!(level.getBlockEntity(host) instanceof com.createstats.blockentity.MainBreakerBE)
                        && !(level.getBlockEntity(host) instanceof com.createstats.blockentity.StormCoilBE)) {
                    attachments.remove(host);
                }
            } else if (node instanceof ForeignTerminalNode bridge) {
                attachments.remove(bridge.pos());
            }
        }

        flowOwners.keySet().retainAll(attachments.keySet());
        surfaceOwners.keySet().retainAll(attachments.keySet());

        applySurfaceRunBudgets(level, found, attachments, surfaceOwners);
        applyConduitRunBudgets(level, found, attachments, conduitOwners);

        final TreeArrays tree = treeArrays(found, attachments, walk.parentOf, walk.passRate, parallelSinks);
        final FePathBudget paths = pathBudgetOf(found, attachments, flowOwners, tree);
        applyPathBudget(level, paths, attachments, flowOwners, tree.leaves());
        found.addAll(attachments.values());

        final Set<Long> machinePositions = new HashSet<>(attachments.size() * 2);
        for (BlockPos machine : attachments.keySet()) {
            machinePositions.add(machine.asLong());
        }

        final List<BlockPos> cappingSupplies = cappingSuppliesOf(level, attachments);
        final FeWireNetwork network = new FeWireNetwork(found,
                FeGridDedupe.collapse(found, FeWireNetwork::servedDeviceOf, FeWireNetwork::claimRank),
            machinePositions, conduitThroatOf(found, attachments, flowOwners, tree),
            cappingSupplies, hasSupplyOtherThan(found, cappingSupplies));
        network.pathBudget = paths;

        network.moveFrom = new long[paths.feedSpanCount()];
        network.moveTo = new long[paths.feedSpanCount()];
        network.moveFe = new int[paths.feedSpanCount()];

        network.pathsBind = !paths.isEmpty();
        if (level != null) {
            network.clock = level::getGameTime;
        }
        network.carryTickState(previous);
        network.flow = FeFlowGraph.of(walk.parentOf, keyed(flowOwners));

        for (FeGridMember node : found) {
            node.adoptNetwork(network);
        }
        return network;
    }

    private static void applySurfaceRunBudgets(
            Level level, List<FeGridMember> found, Map<BlockPos, FeAttachment> attachments,
            Map<BlockPos, CableBE> owners) {
        if (level == null || found.stream().noneMatch(CableBE.class::isInstance)) {
            return;
        }
        final Map<BlockPos, CableBE> byPos = new HashMap<>();
        for (FeGridMember node : found) {
            if (node instanceof CableBE cable) {
                byPos.put(cable.getBlockPos(), cable);
            }
        }
        final CableLinks.View view = CableBlock.linkViewOf(level);
        final Map<BlockPos, IndoorCableRunBudget> budgetByPos = new HashMap<>(byPos.size() * 2);
        final Set<BlockPos> seen = new HashSet<>();
        for (BlockPos start : byPos.keySet()) {
            if (!seen.add(start)) {
                continue;
            }
            final List<CableBE> run = new ArrayList<>();
            final Deque<BlockPos> walk = new ArrayDeque<>();
            walk.add(start);
            int rate = Integer.MAX_VALUE;
            while (!walk.isEmpty()) {
                final BlockPos at = walk.poll();
                final CableBE cable = byPos.get(at);
                if (cable == null) {
                    continue;
                }
                run.add(cable);
                rate = Math.min(rate, Math.max(0, cable.transferRate()));
                for (BlockPos next : CableLinks.continuations(view, at)) {
                    final CableBE other = byPos.get(next);
                    if (other != null && other.tier() == cable.tier() && seen.add(next)) {
                        walk.add(next);
                    }
                }
            }
            final IndoorCableRunBudget budget = new IndoorCableRunBudget(
                    rate == Integer.MAX_VALUE ? 0 : rate);
            for (CableBE cable : run) {
                cable.adoptRunBudget(budget, run.size());
                budgetByPos.put(cable.getBlockPos(), budget);
            }
        }
        for (Map.Entry<BlockPos, CableBE> entry : owners.entrySet()) {
            final FeAttachment attachment = attachments.get(entry.getKey());
            final IndoorCableRunBudget budget = budgetByPos.get(entry.getValue().getBlockPos());
            if (attachment == null || budget == null || attachment.storage() == null) {
                continue;
            }
            attachments.put(entry.getKey(), attachment.over(
                    IndoorCableRunBudget.Throttled.of(attachment.storage(), budget, level),
                    Math.min(attachment.rate(), budget.ratePerTick())));
        }
    }

    private static void applyConduitRunBudgets(
            Level level, List<FeGridMember> found, Map<BlockPos, FeAttachment> attachments,
            Map<BlockPos, com.createstats.blockentity.IndoorCableBE> owners) {
        if (level == null || owners.isEmpty() && found.stream()
                .noneMatch(com.createstats.blockentity.IndoorCableBE.class::isInstance)) {
            return;
        }
        final Map<BlockPos, com.createstats.blockentity.IndoorCableBE> byPos = new HashMap<>();
        for (FeGridMember node : found) {
            if (node instanceof com.createstats.blockentity.IndoorCableBE conduit) {
                byPos.put(conduit.getBlockPos(), conduit);
            }
        }
        final int rate = IndoorCable.rate(PowerConnectorBE.baseTransferRate());
        final IndoorCableLinks.View view = com.createstats.block.IndoorCableBlock.linkViewOf(level);
        final Map<BlockPos, IndoorCableRunBudget> budgetByPos = new HashMap<>(byPos.size() * 2);
        for (BlockPos start : byPos.keySet()) {
            if (budgetByPos.containsKey(start)) {
                continue;
            }
            final IndoorCableRunBudget budget = new IndoorCableRunBudget(rate);
            final Deque<BlockPos> walk = new ArrayDeque<>();
            walk.add(start);
            budgetByPos.put(start, budget);
            while (!walk.isEmpty()) {
                final BlockPos at = walk.poll();
                byPos.get(at).adoptRunBudget(budget);
                for (BlockPos next : IndoorCableLinks.continuations(view, at)) {
                    if (byPos.containsKey(next) && budgetByPos.putIfAbsent(next, budget) == null) {
                        walk.add(next);
                    }
                }
            }
        }
        for (Map.Entry<BlockPos, com.createstats.blockentity.IndoorCableBE> entry : owners.entrySet()) {
            final FeAttachment attachment = attachments.get(entry.getKey());
            final IndoorCableRunBudget budget = budgetByPos.get(entry.getValue().getBlockPos());
            if (attachment == null || budget == null || attachment.storage() == null) {
                continue;
            }
            attachments.put(entry.getKey(), attachment.over(
                    IndoorCableRunBudget.Throttled.of(attachment.storage(), budget, level),
                    attachment.rate()));
        }
    }

    @org.jetbrains.annotations.Nullable
    private static BlockPos servedDeviceOf(FeGridMember node) {
        if (node.attachedStorage() == null) {
            return null;
        }
        if (node instanceof FeAttachment attachment) {
            return attachment.machine();
        }
        if (node instanceof PowerConnectorBE connector) {
            return connector.getBlockPos().relative(connector.mountedOn());
        }
        if (node instanceof net.minecraft.world.level.block.entity.BlockEntity be) {
            return be.getBlockPos();
        }
        return null;
    }

    private static int claimRank(FeGridMember node) {
        if (node instanceof PowerConnectorBE) {
            return FeGridDedupe.RANK_TERMINAL;
        }
        if (node instanceof FeAttachment) {
            return FeGridDedupe.RANK_ATTACHMENT;
        }
        return FeGridDedupe.RANK_MACHINE;
    }

    private static void collectAttachments(Level level, CableBE cable,
                                           Map<BlockPos, FeAttachment> out,
                                           Map<BlockPos, BlockPos> flowOwners,
                                           Map<BlockPos, CableBE> surfaceOwners, Walk walk) {
        if (level == null) {
            return;
        }
        for (Direction dir : Direction.values()) {
            final BlockPos at = cable.getBlockPos().relative(dir);
            if (attach(level, cable.getBlockPos(), at, dir.getOpposite(),
                    cable.transferRate(), out, flowOwners, walk)) {
                surfaceOwners.put(at.immutable(), cable);
            }
        }
        for (CableBE.WrappedDevice wrapped : cable.wrappedDevices()) {
            if (attach(level, cable.getBlockPos(), wrapped.pos(), wrapped.fromSide(),
                    cable.transferRate(), out, flowOwners, walk)) {
                surfaceOwners.put(wrapped.pos().immutable(), cable);
            }
        }
    }

    private static boolean attach(Level level, BlockPos owner, BlockPos at, Direction fromSide,
                                  int rate, Map<BlockPos, FeAttachment> out,
                                  Map<BlockPos, BlockPos> flowOwners, Walk walk) {
        return attach(level, owner, at, fromSide, rate, out, flowOwners, walk, false);
    }

    private static boolean attach(Level level, BlockPos owner, BlockPos at, Direction fromSide,
                                  int rate, Map<BlockPos, FeAttachment> out,
                                  Map<BlockPos, BlockPos> flowOwners, Walk walk,
                                  boolean anyFace) {
        if (!level.isLoaded(at) || out.containsKey(at)) {
            return false;
        }
        final net.minecraft.world.level.block.entity.BlockEntity there = level.getBlockEntity(at);
        if (there instanceof FeGridMember) {
            if (walk != null && (there instanceof com.createstats.blockentity.PoweredDoorBE
                    || there instanceof com.createstats.blockentity.BlastShutterBE
                    || there instanceof com.createstats.blockentity.GarageDoorBE)) {
                walk.enqueue(owner, there.getBlockPos(), there);
            }
            return false;
        }

        if (there == null && walkHostedNode(level, owner, at, walk)) {
            return false;
        }
        final net.neoforged.neoforge.energy.IEnergyStorage storage = anyFace
                ? IndoorCable.energyAt(level, at, fromSide)
                : level.getCapability(
                        net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, at, fromSide);
        if (storage != null) {
            if (NewAgeFeed.isSendOnlyBridge(level, at)) {
                return false;
            }

            out.put(at.immutable(), new FeAttachment(at.immutable(),
                    ForeignTerminalNode.asMouth(level, at, storage), rate));

            flowOwners.put(at.immutable(), owner.immutable());
            return true;
        }
        return false;
    }

    private static int wireRateInto(Level level, Walk walk, BlockPos at) {
        final int base = PowerConnectorBE.baseTransferRate();
        final Long parent = walk.parentOf.get(at.asLong());
        if (parent == null || level == null) {
            return base;
        }
        final BlockPos from = BlockPos.of(parent);
        return level.isLoaded(from)
                && level.getBlockEntity(from) instanceof FeWireTerminal terminal
                ? base * terminal.tierOf(at).multiplier() : base;
    }

    private static void enqueueTouchingCable(Level level, BlockPos at, Walk walk) {
        spreadFromBlock(level, at, walk, true, false, false);
    }

    private static void walkTerminalLinks(FeWireTerminal terminal, Level level, Walk walk, @Nullable BlockPos only) {
        final BlockPos from = terminal.terminalPos();
        for (BlockPos link : new ArrayList<>(terminal.links())) {
            if (only != null && !only.equals(link)) {
                continue;
            }
            if (level == null || !level.isLoaded(link)) {
                continue;
            }
            final net.minecraft.world.level.block.entity.BlockEntity linkBE = level.getBlockEntity(link);
            if (linkBE instanceof com.createstats.blockentity.DiagnosticJunctionBE junction) {
                if (!junction.isLinkedToConnector(terminal.terminalPos())) {
                    terminal.removeDeadLink(link);
                    continue;
                }
                for (BlockPos far : junction.reachableTerminalsFrom(terminal.terminalPos())) {
                    if (!level.isLoaded(far)) {
                        continue;
                    }
                    final net.minecraft.world.level.block.entity.BlockEntity farBE = level.getBlockEntity(far);

                    if (farBE instanceof FeWireTerminal parallel && parallel.feedsInParallel()) {
                        final int rate = passThroughRate(terminal.tierOf(link), junction.tierOf(far));
                        if (reachParallel(parallel, farBE, far, from, link, true, rate, walk)) {
                            walk.ratePassThrough(far, rate);
                            if (only != null) {
                                return;
                            }
                        }
                    } else if (farBE instanceof FeWireTerminal && walk.enqueue(from, far, farBE)) {
                        walk.ratePassThrough(far, passThroughRate(terminal.tierOf(link), junction.tierOf(far)));
                        if (only != null) {
                            return;
                        }
                    }
                }
                continue;
            }
            if (linkBE instanceof com.createstats.blockentity.ContactorBE contactor) {
                final int stud = contactor.studOf(from);
                if (stud < 0) {
                    terminal.removeDeadLink(link);
                    continue;
                }
                for (com.createstats.blockentity.ContactorBE.StudLink far : contactor.throughFrom(stud)) {
                    if (!level.isLoaded(far.to())) {
                        continue;
                    }
                    final net.minecraft.world.level.block.entity.BlockEntity farBE = level.getBlockEntity(far.to());
                    if (farBE instanceof FeWireTerminal parallel && parallel.feedsInParallel()) {
                        final int rate = passThroughRate(terminal.tierOf(link), far.tier());
                        if (reachParallel(parallel, farBE, far.to(), from, link, true, rate, walk)) {
                            walk.ratePassThrough(far.to(), rate);
                            if (only != null) {
                                return;
                            }
                        }
                    } else if (farBE instanceof FeWireTerminal && walk.enqueue(from, far.to(), farBE)) {
                        walk.ratePassThrough(far.to(), passThroughRate(terminal.tierOf(link), far.tier()));
                        if (only != null) {
                            return;
                        }
                    }
                }
                continue;
            }
            if (linkBE instanceof com.createstats.blockentity.MainBreakerBE breaker) {
                if (breaker.isClosed()) {
                    walk.enqueue(from, link, linkBE);
                }
                continue;
            }
            if (linkBE instanceof com.createstats.blockentity.StormCoilBE) {
                walk.enqueue(from, link, linkBE);
                continue;
            }
            if (linkBE instanceof FeStudPanel) {
                continue;
            }
            if (linkBE instanceof FeWireTerminal parallel && parallel.feedsInParallel()) {
                reachParallel(parallel, linkBE, link, from, from, true, FeGridSolver.NO_CEILING, walk);
                continue;
            }
            if (linkBE instanceof FeWireTerminal) {
                walk.enqueue(from, link, linkBE);
                continue;
            }

            final ForeignTerminalNode bridge = ForeignTerminalNode.at(level, link, terminal.tierOf(link));
            if (bridge != null) {
                walk.enqueue(from, link, bridge);
                continue;
            }
            terminal.removeDeadLink(link);
        }
    }

    private static void walkTerminalLinks(FeWireTerminal terminal, Level level, Walk walk) {
        walkTerminalLinks(terminal, level, walk, null);
    }

    private static void spreadFromBlock(Level level, BlockPos at, Walk walk, boolean includeCable) {
        spreadFromBlock(level, at, walk, includeCable, false, true);
    }

    private static void spreadFromBlock(Level level, BlockPos at, Walk walk, boolean includeCable,
                                        boolean anyFace) {
        spreadFromBlock(level, at, walk, includeCable, anyFace, true);
    }

    private static void spreadFromBlock(Level level, BlockPos at, Walk walk, boolean includeCable,
                                        boolean anyFace, boolean includeConnectors) {
        if (level == null) {
            return;
        }
        for (Direction dir : Direction.values()) {
            final BlockPos neighbour = at.relative(dir);

            if (!level.isLoaded(neighbour) || (walk.known(neighbour) && !walk.isParallel(neighbour))) {
                continue;
            }
            final net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(neighbour);
            if (be instanceof com.createstats.blockentity.ArcFurnaceBE furnace) {
                if (anyFace || dir.getOpposite() == furnace.backFace()) {
                    walk.enqueue(at, neighbour, be);
                }
                continue;
            }
            if (be instanceof com.createstats.blockentity.InductionSmelterBE smelter) {
                if (anyFace || dir.getOpposite() == smelter.backFace()) {
                    walk.enqueue(at, neighbour, be);
                }
                continue;
            }
            if (be instanceof com.createstats.blockentity.CoffeeMachineBE coffee) {
                if (anyFace || dir.getOpposite() == coffee.backFace()) {
                    walk.enqueue(at, neighbour, be);
                }
                continue;
            }
            if (be instanceof com.createstats.blockentity.AirFryerBE fryer) {
                if (anyFace || dir.getOpposite() == fryer.backFace()) {
                    walk.enqueue(at, neighbour, be);
                }
                continue;
            }
            if (be instanceof com.createstats.blockentity.BulkWasherBE washer) {
                if (anyFace || dir.getOpposite() == washer.backFace()) {
                    walk.enqueue(at, neighbour, be);
                }
                continue;
            }
            if (be instanceof com.createstats.blockentity.OvenBE oven) {
                if (anyFace || (dir.getAxis().isHorizontal()
                        && dir.getOpposite() != oven.frontFace())) {
                    walk.enqueue(at, neighbour, be);
                }
                continue;
            }
            if (be instanceof com.createstats.blockentity.BiofuelBurnerBE burner) {
                if (anyFace || (dir.getAxis().isHorizontal()
                        && dir.getOpposite() != burner.frontFace())) {
                    walk.enqueue(at, neighbour, be);
                }
                continue;
            }
            if (be instanceof com.createstats.blockentity.SolarPanelBE) {
                if (anyFace || dir != Direction.DOWN) {
                    walk.enqueue(at, neighbour, be);
                }
                continue;
            }
            if (be instanceof com.createstats.blockentity.VacuumCollectorBE collector) {
                if (anyFace || (dir.getAxis().isHorizontal() && dir.getOpposite() != collector.facing())) {
                    walk.enqueue(at, neighbour, be);
                }
                continue;
            }
            if (be instanceof com.createstats.blockentity.VacuumCleanerBE cleaner) {
                if (anyFace || (dir.getAxis().isHorizontal() && dir.getOpposite() != cleaner.facing())) {
                    walk.enqueue(at, neighbour, be);
                }
                continue;
            }
            if (be instanceof com.createstats.blockentity.VacuumSnifferBE sniffer) {
                if (anyFace || dir.getOpposite() != sniffer.facing()) {
                    walk.enqueue(at, neighbour, be);
                }
                continue;
            }
            if (be instanceof com.createstats.blockentity.MobRepellerBE repeller) {
                if (anyFace || repeller.takesPowerOn(dir.getOpposite())) {
                    walk.enqueue(at, neighbour, be);
                }
                continue;
            }
            if (be instanceof com.createstats.blockentity.SeismicScannerBE scanner) {
                if (anyFace || scanner.takesPowerOn(dir.getOpposite())) {
                    walk.enqueue(at, neighbour, be);
                }
                continue;
            }
            if (be instanceof com.createstats.blockentity.SparkCrownBE crown) {
                final boolean back = crown.takesPowerOn(dir.getOpposite());
                if (anyFace || back) {
                    reachParallel(crown, be, neighbour, at, null, back, FeGridSolver.NO_CEILING, walk);
                }
                continue;
            }
            if (be instanceof com.createstats.blockentity.ToasterBE
                    || be instanceof com.createstats.blockentity.BreadSlicerBE) {
                if (anyFace || dir.getAxis().isHorizontal()) {
                    walk.enqueue(at, neighbour, be);
                }
                continue;
            }
            if (be instanceof com.createstats.blockentity.PaSpeakerBE
                    || be instanceof com.createstats.blockentity.SmartHomePanelBE
                    || be instanceof com.createstats.blockentity.WeatherPanelBE
                    || be instanceof com.createstats.blockentity.HomeSecurityPanelBE
                    || be instanceof com.createstats.blockentity.SecurityLockerBE
                    || be instanceof com.createstats.blockentity.SecuritySensorBE
                    || be instanceof com.createstats.blockentity.SecurityRelayBE) {
                walk.enqueue(at, neighbour, be);
                continue;
            }
            if (be instanceof com.createstats.blockentity.AnnouncementComputerBE brain) {
                if (anyFace || dir.getOpposite() == brain.backFace()) {
                    walk.enqueue(at, neighbour, be);
                }
                continue;
            }
            if (be instanceof com.createstats.blockentity.WaterCoolerBE cooler) {
                if (anyFace || dir.getOpposite() == cooler.backFace()) {
                    walk.enqueue(at, neighbour, be);
                }
                continue;
            }
            if (be instanceof com.createstats.blockentity.DiagnosticJunctionBE junction) {
                if (junction.isCableSplice() && !junction.isCutOff()) {
                    walk.enqueue(at, neighbour, be);
                }
                continue;
            }
            if (be instanceof com.createstats.blockentity.PoweredDoorBE
                    || be instanceof com.createstats.blockentity.BlastShutterBE
                    || be instanceof com.createstats.blockentity.GarageDoorBE) {
                walk.enqueue(at, neighbour, be);
                continue;
            }
            if (be == null) {
                final net.minecraft.world.level.block.entity.BlockEntity hosted =
                        hostedNodeAt(level, neighbour);
                if (hosted != null) {
                    walk.enqueue(at, hosted.getBlockPos(), hosted);
                }
                continue;
            }
            if ((includeConnectors && be instanceof PowerConnectorBE) || be instanceof SwitchBE
                    || be instanceof com.createstats.blockentity.LampBE
                    || be instanceof com.createstats.blockentity.InductionRangeBE
                    || be instanceof com.createstats.blockentity.FluidPumpBE
                    || be instanceof com.createstats.blockentity.FeMotorBE
                    || (includeCable && be instanceof CableBE)) {
                walk.enqueue(at, neighbour, be);
                continue;
            }

            if (includeCable && be instanceof com.createstats.blockentity.IndoorCableBE) {
                walk.enqueue(at, neighbour, be);
            }
        }
    }

    private static void spreadFromWrappingRun(Level level, BlockPos at, Walk walk) {
        if (level == null) {
            return;
        }
        final Set<BlockPos> edges = new HashSet<>(12);
        for (Direction along : Direction.values()) {
            for (Direction across : Direction.values()) {
                if (across.getAxis() != along.getAxis()) {
                    edges.add(at.relative(along).relative(across));
                }
            }
        }
        for (BlockPos edge : edges) {
            if (!level.isLoaded(edge) || walk.known(edge)) {
                continue;
            }
            if (!(level.getBlockEntity(edge) instanceof CableBE cable)) {
                continue;
            }
            for (CableBE.WrappedDevice wrapped : cable.wrappedDevices()) {
                if (wrapped.pos().equals(at)) {
                    walk.enqueue(at, edge, cable);
                    break;
                }
            }
        }
    }

    private static boolean walkHostedNode(Level level, BlockPos from, BlockPos at,
                                          @javax.annotation.Nullable Walk walk) {
        final net.minecraft.world.level.block.entity.BlockEntity leaf = hostedNodeAt(level, at);
        if (leaf == null) {
            return false;
        }
        if (walk != null) {
            walk.enqueue(from, leaf.getBlockPos(), leaf);
        }
        return true;
    }

    @javax.annotation.Nullable
    private static net.minecraft.world.level.block.entity.BlockEntity hostedNodeAt(
            Level level, BlockPos at) {
        final net.minecraft.world.level.block.state.BlockState state = level.getBlockState(at);

        final com.createstats.blockentity.GarageDoorBE garage =
                com.createstats.block.GarageDoorHeaderBlock.frameHostAt(level, at, state);
        if (garage != null) {
            return garage;
        }
        final boolean door = state.getBlock() instanceof com.createstats.block.PoweredDoorBlock
                && state.getValue(com.createstats.block.PoweredDoorBlock.HALF)
                        == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER;
        if (door) {
            final BlockPos lower = at.below();
            if (!level.isLoaded(lower)) {
                return null;
            }
            final net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(lower);
            return be instanceof com.createstats.blockentity.PoweredDoorBE ? be : null;
        }

        if (state.getBlock() instanceof com.createstats.block.BlastShutterBlock) {
            return com.createstats.block.BlastShutterBlock.unitAt(level, at, state);
        }
        return null;
    }

    private static void spreadAcrossFamilies(Level level, BlockPos at, Walk walk,
                                             boolean fromSurface) {
        if (level == null) {
            return;
        }
        for (Direction dir : Direction.values()) {
            final BlockPos neighbour = at.relative(dir);
            if (!level.isLoaded(neighbour) || walk.known(neighbour)) {
                continue;
            }
            final net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(neighbour);
            final boolean wanted = fromSurface
                    ? be instanceof com.createstats.blockentity.IndoorCableBE
                    : be instanceof CableBE;
            if (wanted) {
                walk.enqueue(at, neighbour, be);
            }
        }
    }

    private static void spreadFromFace(Level level, BlockPos at, Direction face, Walk walk) {
        if (level == null) {
            return;
        }
        final BlockPos neighbour = at.relative(face);
        if (!level.isLoaded(neighbour) || walk.known(neighbour)) {
            return;
        }
        final net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(neighbour);
        if (be instanceof com.createstats.blockentity.DiagnosticJunctionBE junction) {
            if (junction.isCableSplice() && !junction.isCutOff()) {
                walk.enqueue(at, neighbour, be);
            }
            return;
        }
        if (be instanceof PowerConnectorBE || be instanceof SwitchBE || be instanceof CableBE
                || be instanceof com.createstats.blockentity.IndoorCableBE
                || be instanceof com.createstats.blockentity.LampBE
                || be instanceof com.createstats.blockentity.InductionRangeBE
                || be instanceof com.createstats.blockentity.FluidPumpBE
                || be instanceof com.createstats.blockentity.FeMotorBE) {
            walk.enqueue(at, neighbour, be);
        }
    }

    private static void spreadAcrossPanels(Level level, BlockPos at, Walk walk) {
        if (level == null) {
            return;
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            final BlockPos neighbour = at.relative(side);
            if (!level.isLoaded(neighbour) || walk.known(neighbour)) {
                continue;
            }
            if (level.getBlockEntity(neighbour) instanceof com.createstats.blockentity.SolarPanelBE panel) {
                walk.enqueue(at, neighbour, panel);
            }
        }
    }

    private static void spreadAlongRun(Level level, BlockPos at, Walk walk) {
        if (level == null) {
            return;
        }
        for (BlockPos next : CableLinks.continuations(CableBlock.linkViewOf(level), at)) {
            if (!level.isLoaded(next) || walk.known(next)) {
                continue;
            }
            if (level.getBlockEntity(next) instanceof CableBE cable) {
                walk.enqueue(at, next, cable);
            }
        }
    }

    private static void spreadAlongConduit(Level level, BlockPos at, Walk walk) {
        if (level == null) {
            return;
        }
        for (BlockPos next : IndoorCableLinks.continuations(
                com.createstats.block.IndoorCableBlock.linkViewOf(level), at)) {
            if (!level.isLoaded(next) || walk.known(next)) {
                continue;
            }
            if (level.getBlockEntity(next) instanceof com.createstats.blockentity.IndoorCableBE conduit) {
                walk.enqueue(at, next, conduit);
            }
        }
    }

    private static void spreadAlongBores(Level level, BoreLinks.Host host, Walk walk) {
        if (level == null) {
            return;
        }
        for (BlockPos far : new ArrayList<>(host.bores())) {
            if (!level.isLoaded(far) || walk.known(far)) {
                continue;
            }
            final net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(far);
            if (be instanceof CableBE || be instanceof com.createstats.blockentity.IndoorCableBE
                    || be instanceof com.createstats.blockentity.SmartHomePanelBE
                    || be instanceof com.createstats.blockentity.WeatherPanelBE
                    || be instanceof com.createstats.blockentity.HomeSecurityPanelBE
                    || be instanceof com.createstats.blockentity.SecurityLockerBE
                    || be instanceof com.createstats.blockentity.SecuritySensorBE
                    || be instanceof com.createstats.blockentity.SecurityRelayBE
                    || be instanceof com.createstats.blockentity.BlastShutterBE
                    || be instanceof com.createstats.blockentity.GarageDoorBE
                    || be instanceof com.createstats.blockentity.PoweredDoorBE) {
                walk.enqueue(host.borePosition(), far, be);
                continue;
            }

            if (be == null) {
                final net.minecraft.world.level.block.entity.BlockEntity leaf =
                        hostedNodeAt(level, far);
                if (leaf != null) {
                    walk.enqueue(host.borePosition(), leaf.getBlockPos(), leaf);
                }
            }
        }
    }

    private static void collectConduitAttachments(
            Level level, com.createstats.blockentity.IndoorCableBE conduit,
            Map<BlockPos, FeAttachment> out,
            Map<BlockPos, com.createstats.blockentity.IndoorCableBE> owners,
            Map<BlockPos, BlockPos> flowOwners, Walk walk) {
        if (level == null) {
            return;
        }
        for (Direction dir : Direction.values()) {
            final BlockPos at = conduit.getBlockPos().relative(dir);
            if (attach(level, conduit.getBlockPos(), at, dir.getOpposite(), conduit.transferRate(),
                    out, flowOwners, walk, true)) {
                owners.put(at.immutable(), conduit);
            }
        }
        for (BlockPos far : new ArrayList<>(conduit.bores())) {
            final Direction along = BoreLinks.axisBetween(conduit.getBlockPos(), far);
            if (along == null) {
                continue;
            }
            for (BlockPos crossed : BoreLinks.crossings(conduit.getBlockPos(), far)) {
                if (attach(level, conduit.getBlockPos(), crossed, along.getOpposite(),
                        conduit.transferRate(), out, flowOwners, walk, true)) {
                    owners.put(crossed.immutable(), conduit);
                }
            }
            if (attachBoredTerminal(level, conduit, far, along, conduit.transferRate(),
                    out, flowOwners, walk)) {
                owners.put(far.immutable(), conduit);
            }
        }
    }

    private static void collectBoreAttachments(Level level, BoreLinks.Host host, int rate,
                                               Map<BlockPos, FeAttachment> out,
                                               Map<BlockPos, BlockPos> flowOwners,
                                               Map<BlockPos, CableBE> surfaceOwners, Walk walk) {
        if (level == null) {
            return;
        }
        for (BlockPos far : new ArrayList<>(host.bores())) {
            final Direction along = BoreLinks.axisBetween(host.borePosition(), far);
            if (along == null) {
                continue;
            }
            for (BlockPos crossed : BoreLinks.crossings(host.borePosition(), far)) {
                if (attach(level, host.borePosition(), crossed, along.getOpposite(), rate, out,
                        flowOwners, walk) && host instanceof CableBE cable) {
                    surfaceOwners.put(crossed.immutable(), cable);
                }
            }
            if (attachBoredTerminal(level, host, far, along, rate, out, flowOwners, walk)
                    && host instanceof CableBE cable) {
                surfaceOwners.put(far.immutable(), cable);
            }
        }
    }

    private static boolean attachBoredTerminal(Level level, BoreLinks.Host host, BlockPos far,
                                               Direction along, int rate,
                                               Map<BlockPos, FeAttachment> out,
                                               Map<BlockPos, BlockPos> flowOwners, Walk walk) {
        if (!(BoreLinks.hostAt(level, far) instanceof BoreLinks.Socket)) {
            return false;
        }
        return attach(level, host.borePosition(), far, along.getOpposite(), rate, out, flowOwners,
                walk, true);
    }

    private static void spreadToBank(Level level, BlockPos at, Walk walk) {
        if (level == null) {
            return;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    final BlockPos near = at.offset(dx, dy, dz);
                    if (!level.isLoaded(near) || walk.known(near)) {
                        continue;
                    }
                    if (level.getBlockEntity(near)
                            instanceof com.createstats.blockentity.InductionRangeBE other) {
                        walk.enqueue(at, near, other);
                    }
                }
            }
        }
    }

    private static void spreadToSmelterBank(Level level, BlockPos at, Walk walk) {
        if (level == null) {
            return;
        }
        for (Direction dir : Direction.values()) {
            final BlockPos near = at.relative(dir);
            if (!level.isLoaded(near) || walk.known(near)) {
                continue;
            }
            if (level.getBlockEntity(near)
                    instanceof com.createstats.blockentity.InductionSmelterBE other) {
                walk.enqueue(at, near, other);
            }
        }
    }

    private static BlockPos posOf(Object node) {
        return ((net.minecraft.world.level.block.entity.BlockEntity) node).getBlockPos();
    }

    private static Map<Long, Long> keyed(Map<BlockPos, BlockPos> byPos) {
        final Map<Long, Long> out = new HashMap<>(byPos.size() * 2);
        for (Map.Entry<BlockPos, BlockPos> entry : byPos.entrySet()) {
            out.put(entry.getKey().asLong(), entry.getValue().asLong());
        }
        return out;
    }

    private static final class Walk {
        private final Set<BlockPos> seen = new HashSet<>();
        private final Deque<Object> queue = new ArrayDeque<>();

        private final LinkedHashMap<Long, Long> parentOf = new LinkedHashMap<>();

        private boolean passedThrough;

        private final Map<Long, Integer> passRate = new HashMap<>();

        void ratePassThrough(BlockPos child, int rate) {
            passRate.put(child.asLong(), rate);
        }

        private final LinkedHashMap<Long, ParallelArrivals> parallel = new LinkedHashMap<>();

        private final Set<Long> parallelMet = new HashSet<>();

        boolean isOrigin(BlockPos at) {
            final Long from = parentOf.get(at.asLong());
            return from != null && from == at.asLong();
        }

        boolean isParallel(BlockPos at) {
            return parallelMet.contains(at.asLong());
        }

        void meetParallel(BlockPos at) {
            parallelMet.add(at.asLong());
        }

        void feed(FeWireTerminal sink, BlockPos at, BlockPos from, @Nullable BlockPos via, int pass) {
            parallel.computeIfAbsent(at.asLong(), k -> new ParallelArrivals(sink))
                    .add(via == null ? from.asLong() : via.asLong(), from.asLong(), pass);
        }

        boolean crossPassthrough() {
            if (passedThrough) {
                return false;
            }
            passedThrough = true;
            return true;
        }

        void start(BlockPos at, Object node) {
            final BlockPos pos = at.immutable();
            seen.add(pos);
            parentOf.put(pos.asLong(), pos.asLong());
            queue.add(node);
            if (node instanceof FeWireTerminal parallel && parallel.feedsInParallel()) {
                meetParallel(pos);
            }
        }

        boolean known(BlockPos at) {
            return seen.contains(at);
        }

        boolean enqueue(BlockPos from, BlockPos at, Object node) {
            final BlockPos pos = at.immutable();
            if (!seen.add(pos)) {
                return false;
            }
            parentOf.put(pos.asLong(), from.asLong());
            queue.add(node);
            return true;
        }

        boolean isEmpty() {
            return queue.isEmpty();
        }

        Object poll() {
            return queue.poll();
        }
    }

    private static final class ParallelArrivals {
        final FeWireTerminal sink;

        final Set<Long> inputs = new HashSet<>(4);
        final List<Long> from = new ArrayList<>(2);
        final List<Integer> pass = new ArrayList<>(2);

        ParallelArrivals(FeWireTerminal sink) {
            this.sink = sink;
        }

        void add(long input, long fromKey, int passRate) {
            if (inputs.add(input)) {
                from.add(fromKey);
                pass.add(passRate);
            }
        }
    }

    private static boolean reachParallel(FeWireTerminal sink, Object node, BlockPos at, BlockPos from,
                                         @Nullable BlockPos via, boolean mayBeHome, int pass, Walk walk) {
        walk.meetParallel(at);
        if (mayBeHome && java.util.Objects.equals(via, sink.homeLink())) {
            return walk.enqueue(from, at, node);
        }
        walk.feed(sink, at, from, via, pass);
        return false;
    }

    public void markDirty() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public int size() {
        return nodes.size();
    }

    public FeGridSolver.Result lastResult() {
        return lastResult;
    }

    public void markPduManaged(long gameTime) {
        this.managedUntilTick = gameTime + MANAGED_GRACE_TICKS;
    }

    public boolean isPduManaged(long gameTime) {
        return gameTime <= managedUntilTick;
    }

    public void noteManagerView(@Nullable FeManagerView view, long gameTime) {
        if (view == null) {
            return;
        }
        if (gameTime != managerViewTick) {
            managerViewTick = gameTime;
            managerView = view;
        } else {
            managerView = managerView.merge(view);
        }
    }

    @Nullable
    public FeManagerView managerView(long gameTime) {
        if (managerView == null || !isPduManaged(gameTime)
                || gameTime - managerViewTick > MANAGED_GRACE_TICKS) {
            return null;
        }
        return managerView;
    }

    private static FeConduitThroat conduitThroatOf(List<FeGridMember> found,
                                                   Map<BlockPos, FeAttachment> attachments,
                                                   Map<BlockPos, BlockPos> flowOwners,
                                                   TreeArrays tree) {
        if (tree.keys().length == 0) {
            return FeConduitThroat.NONE;
        }
        final long[] keys = tree.keys();
        final Map<Long, FeGridMember> memberByKey = tree.memberByKey();
        final int n = keys.length;
        final int[] suppliers = new int[n];
        final int[] receivers = new int[n];
        for (FeGridMember node : found) {
            seedRole(tree.indexOf(), suppliers, receivers, node.nodeKey(), node.attachedStorage(), node.transferRate());
        }
        for (FeAttachment attachment : attachments.values()) {
            if (tree.leaves().contains(attachment.nodeKey())) {
                continue;
            }
            final BlockPos owner = flowOwners.get(attachment.machine());
            if (owner != null) {
                seedRole(tree.indexOf(), suppliers, receivers, owner.asLong(),
                        attachment.storage(), attachment.transferRate());
            }
        }
        return new FeConduitThroat(keys, tree.parent(), tree.edgeRate(), bodyRates(memberByKey, keys),
                suppliers, receivers, tree.feeds());
    }

    private record ParallelSink(FeGridMember sink, long key, boolean member, long[] from, int[] pass) {
    }

    private static List<ParallelSink> resolveParallel(Walk walk, Map<BlockPos, FeAttachment> attachments,
                                                      Map<BlockPos, BlockPos> flowOwners) {
        if (walk.parallel.isEmpty()) {
            return List.of();
        }
        final List<ParallelSink> out = new ArrayList<>(walk.parallel.size());
        for (Map.Entry<Long, ParallelArrivals> entry : walk.parallel.entrySet()) {
            final ParallelArrivals arrivals = entry.getValue();
            if (arrivals.from.isEmpty() || !(arrivals.sink instanceof FeGridMember sink)
                    || sink.attachedStorage() == null) {
                continue;
            }
            final long key = entry.getKey();
            final long[] from = new long[arrivals.from.size()];
            final int[] pass = new int[from.length];
            for (int i = 0; i < from.length; i++) {
                from[i] = arrivals.from.get(i);
                pass[i] = arrivals.pass.get(i);
            }
            final boolean member = walk.parentOf.containsKey(key);
            if (!member) {
                final BlockPos at = BlockPos.of(key);
                if (attachments.containsKey(at)) {
                    continue;
                }

                attachments.put(at, FeAttachment.station(at, sink.attachedStorage(), sink));
                flowOwners.put(at, BlockPos.of(from[0]));
            }
            out.add(new ParallelSink(sink, key, member, from, pass));
        }
        return out;
    }

    private record TreeArrays(long[] keys, int[] parent, int[] edgeRate, Map<Long, Integer> indexOf,
                              Map<Long, FeGridMember> memberByKey, Set<Long> leaves, List<FeFeeds> feeds) {
    }

    private static TreeArrays treeArrays(List<FeGridMember> members, Map<BlockPos, FeAttachment> attachments,
                                         Map<Long, Long> parentOf, Map<Long, Integer> passRate,
                                         List<ParallelSink> parallel) {
        final List<ParallelSink> hung = new ArrayList<>(parallel.size());
        for (ParallelSink sink : parallel) {
            if (!sink.member() && attachments.containsKey(BlockPos.of(sink.key()))) {
                hung.add(sink);
            }
        }
        final int base = parentOf.size();
        final int n = base + hung.size();
        final Map<Long, Integer> indexOf = new HashMap<>(Math.max(4, n * 2));
        final long[] keys = new long[n];
        int i = 0;
        for (Long key : parentOf.keySet()) {
            indexOf.put(key, i);
            keys[i] = key;
            i++;
        }
        final int[] parent = new int[n];
        i = 0;
        for (Map.Entry<Long, Long> entry : parentOf.entrySet()) {
            final Integer at = indexOf.get(entry.getValue());
            parent[i] = at == null || at >= i ? -1 : at;
            i++;
        }
        final Map<Long, FeGridMember> memberByKey = new HashMap<>(Math.max(4, members.size() * 2));
        for (FeGridMember node : members) {
            memberByKey.put(node.nodeKey(), node);
        }

        Map<Long, FeGridMember> rated = memberByKey;
        if (!parallel.isEmpty()) {
            rated = new HashMap<>(memberByKey);
            for (ParallelSink sink : parallel) {
                rated.putIfAbsent(sink.key(), sink.sink());
            }
        }
        final int[] edgeRate = new int[n];
        for (i = 0; i < base; i++) {
            edgeRate[i] = parent[i] < 0 ? FeGridSolver.NO_CEILING
                    : Math.min(cableEdgeRate(rated, keys[i], keys[parent[i]]),
                    passRate.getOrDefault(keys[i], FeGridSolver.NO_CEILING));
        }
        final Set<Long> leaves = new HashSet<>(Math.max(4, hung.size() * 2));
        for (int j = 0; j < hung.size(); j++) {
            final ParallelSink sink = hung.get(j);
            final int at = base + j;
            final Integer up = indexOf.get(sink.from()[0]);
            keys[at] = sink.key();
            indexOf.put(sink.key(), at);
            parent[at] = up == null ? -1 : up;
            edgeRate[at] = Math.min(cableEdgeRate(rated, sink.from()[0], sink.key()), sink.pass()[0]);
            leaves.add(sink.key());
        }
        final List<FeFeeds> feeds = new ArrayList<>(parallel.size());
        for (ParallelSink sink : parallel) {
            if (!sink.member() && !leaves.contains(sink.key())) {
                continue;
            }

            final int first = sink.member() ? 0 : 1;
            final int count = sink.from().length - first;
            if (count <= 0) {
                continue;
            }
            final long[] from = new long[count];
            final int[] rate = new int[count];
            for (int k = 0; k < count; k++) {
                from[k] = sink.from()[first + k];
                rate[k] = Math.min(cableEdgeRate(rated, from[k], sink.key()), sink.pass()[first + k]);
            }
            feeds.add(new FeFeeds(sink.key(), from, rate));
        }
        return new TreeArrays(keys, parent, edgeRate, indexOf, memberByKey, leaves, feeds);
    }

    private static FePathBudget pathBudgetOf(List<FeGridMember> found,
                                             Map<BlockPos, FeAttachment> attachments,
                                             Map<BlockPos, BlockPos> flowOwners,
                                             TreeArrays tree) {
        if (tree.keys().length == 0) {
            return FePathBudget.NONE;
        }
        final long[] keys = tree.keys();
        final Map<Long, FeGridMember> memberByKey = tree.memberByKey();
        final boolean[] supplier = new boolean[keys.length];
        for (FeGridMember node : found) {
            seedSupply(tree.indexOf(), supplier, node.nodeKey(), node.attachedStorage(), node.transferRate());
        }
        for (FeAttachment attachment : attachments.values()) {
            final BlockPos owner = flowOwners.get(attachment.machine());
            seedSupply(tree.indexOf(), supplier, owner == null || tree.leaves().contains(attachment.nodeKey())
                    ? attachment.machine().asLong() : owner.asLong(), attachment.storage(), attachment.transferRate());
        }
        return new FePathBudget(keys, tree.parent(), tree.edgeRate(), supplier, bodyRates(memberByKey, keys),
                tree.feeds());
    }

    private static int[] bodyRates(Map<Long, FeGridMember> memberByKey, long[] keys) {
        final int[] out = new int[keys.length];
        for (int i = 0; i < keys.length; i++) {
            out[i] = bodyRate(memberByKey.get(keys[i]));
        }
        return out;
    }

    static int bodyRate(@Nullable FeGridMember node) {
        if (node instanceof PowerConnectorBE connector) {
            return Math.max(0, FeRates.connector(connector.connectorTier()));
        }
        return FeGridSolver.NO_CEILING;
    }

    private static void seedSupply(Map<Long, Integer> indexOf, boolean[] supplier, long key,
                                   @Nullable net.neoforged.neoforge.energy.IEnergyStorage storage,
                                   int rate) {
        final Integer at = indexOf.get(key);
        if (at == null || storage == null) {
            return;
        }
        final FeNodeRole role = FeNodeRole.of(storage, rate);
        if (role.kind() == FeNodeRole.Kind.GENERATOR || role.givesNow()) {
            supplier[at] = true;
        }
    }

    private static void applyPathBudget(Level level, FePathBudget budget,
                                        Map<BlockPos, FeAttachment> attachments,
                                        Map<BlockPos, BlockPos> flowOwners, Set<Long> leaves) {
        if (level == null || attachments.isEmpty() || !budget.hasGradedEdges()
                || !cableLimitEnabled()) {
            return;
        }
        for (Map.Entry<BlockPos, FeAttachment> entry : new ArrayList<>(attachments.entrySet())) {
            final FeAttachment attachment = entry.getValue();
            if (attachment.storage() == null) {
                continue;
            }
            final BlockPos owner = flowOwners.get(attachment.machine());
            final long at = owner == null || leaves.contains(attachment.nodeKey())
                    ? attachment.machine().asLong() : owner.asLong();
            attachments.put(entry.getKey(), attachment.over(
                    FePathBudget.Throttled.of(attachment.storage(), budget, at, level),
                    attachment.rate()));
        }
    }

    private static List<BlockPos> cappingSuppliesOf(Level level,
                                                    Map<BlockPos, FeAttachment> attachments) {
        List<BlockPos> out = null;
        for (FeAttachment attachment : attachments.values()) {
            if (!attachment.storage().canExtract()) {
                continue;
            }
            if (!(level.getBlockEntity(attachment.machine()) instanceof FeInlineInstrument box)
                    || !box.capsThroughput()) {
                continue;
            }
            if (out == null) {
                out = new java.util.ArrayList<>(1);
            }
            out.add(attachment.machine().immutable());
        }
        return out == null ? List.of() : out;
    }

    private static boolean hasSupplyOtherThan(List<FeGridMember> found, List<BlockPos> capping) {
        for (FeGridMember node : found) {
            final net.neoforged.neoforge.energy.IEnergyStorage storage = node.attachedStorage();
            if (storage == null || !storage.canExtract()) {
                continue;
            }
            if (node instanceof FeAttachment attachment
                    && capping.contains(attachment.machine())) {
                continue;
            }
            return true;
        }
        return false;
    }

    public int supplyCeiling(Level level) {
        if (cappingSupplies.isEmpty() || hasUncappedSupply || level == null) {
            return FeGridSolver.NO_CEILING;
        }
        final int[] caps = new int[cappingSupplies.size()];
        for (int i = 0; i < caps.length; i++) {
            if (!(level.getBlockEntity(cappingSupplies.get(i)) instanceof FeInlineInstrument box)) {
                return FeGridSolver.NO_CEILING;
            }
            caps[i] = box.throughputCeiling();
        }
        return FeSupplyBudget.ceilingOf(false, caps);
    }

    private int supplyBudgetRemaining(Level level, long now) {
        return supplyBudget.remaining(supplyCeiling(level), now);
    }

    private void spendSupplyBudget(Level level, long now, int fe) {
        supplyBudget.spend(supplyCeiling(level), now, fe);
    }

    private void creditSupplyBudget(Level level, long now, int fe) {
        supplyBudget.credit(supplyCeiling(level), now, fe);
    }

    private static void seedRole(Map<Long, Integer> indexOf, int[] suppliers, int[] receivers,
                                 long key, @Nullable net.neoforged.neoforge.energy.IEnergyStorage storage,
                                 int rate) {
        final Integer at = indexOf.get(key);
        if (at == null || storage == null) {
            return;
        }
        final FeNodeRole role = FeNodeRole.of(storage, rate);
        if (role.kind() == FeNodeRole.Kind.GENERATOR || role.givesNow()) {
            suppliers[at]++;
        }
        if (role.kind() == FeNodeRole.Kind.LOAD || role.takesNow()) {
            receivers[at]++;
        }
    }

    static int cableEdgeRate(Map<Long, FeGridMember> memberByKey, long a, long b) {
        int rate = Math.min(conductorRate(memberByKey.get(a)), conductorRate(memberByKey.get(b)));
        rate = Math.min(rate, spanRate(memberByKey.get(a), b));
        return Math.min(rate, spanRate(memberByKey.get(b), a));
    }

    public static int passThroughRate(@Nullable WireTier in, @Nullable WireTier out) {
        final int a = (in == null ? WireTier.COPPER : in).multiplier();
        final int b = (out == null ? WireTier.COPPER : out).multiplier();
        return Math.max(0, PowerConnectorBE.baseTransferRate() * Math.min(a, b));
    }

    private static int conductorRate(@Nullable FeGridMember node) {
        if (node instanceof CableBE cable) {
            return Math.max(0, cable.transferRate());
        }
        if (node instanceof com.createstats.blockentity.IndoorCableBE conduit) {
            return Math.max(0, conduit.transferRate());
        }
        return FeGridSolver.NO_CEILING;
    }

    private static int spanRate(@Nullable FeGridMember node, long otherKey) {
        if (!(node instanceof FeWireTerminal terminal)) {
            return FeGridSolver.NO_CEILING;
        }
        final BlockPos other = BlockPos.of(otherKey);
        if (!terminal.links().contains(other)) {
            return FeGridSolver.NO_CEILING;
        }
        return Math.max(0, PowerConnectorBE.baseTransferRate() * terminal.tierOf(other).multiplier());
    }

    public int conduitCeiling() {
        return conduitCeiling;
    }

    public int conduitCeilingFrom(long anchorKey) {
        return throat.ceilingFrom(anchorKey);
    }

    int conductorAllowance(long nodeKey) {
        if (!pathsBind || !cableLimitEnabled()) {
            return FeGridSolver.NO_CEILING;
        }
        return pathBudget.remaining(nodeKey, clock.getAsLong());
    }

    void bookConductorDelivery(long nodeKey, int fe) {
        if (fe <= 0 || !pathsBind || !cableLimitEnabled()) {
            return;
        }
        pathBudget.spend(nodeKey, fe, clock.getAsLong());
    }

    private int budgetRemaining(long now) {
        if (conduitCeiling >= FeGridSolver.NO_CEILING) {
            return FeGridSolver.NO_CEILING;
        }
        if (now != budgetTick) {
            budgetTick = now;
            budgetLeft = conduitCeiling;
        }
        return budgetLeft;
    }

    private void carryTickState(@Nullable FeWireNetwork previous) {
        if (previous == null) {
            return;
        }
        lastSolvedTick = previous.lastSolvedTick;
        budgetTick = previous.budgetTick;
        budgetLeft = Math.min(previous.budgetLeft, conduitCeiling);
    }

    private void spendBudget(long now, int fe) {
        if (fe <= 0 || conduitCeiling >= FeGridSolver.NO_CEILING) {
            return;
        }
        budgetLeft = Math.max(0, budgetRemaining(now) - fe);
    }

    public List<FeGridMember> members() {
        return nodes;
    }

    public List<FeGridMember> servedMembers() {
        return served;
    }

    public Set<Long> reachedPositions() {
        final Set<Long> out = new HashSet<>(machinePositions);
        out.addAll(nodePositions);
        return out;
    }

    public void solveOnce(Level level) {
        if (dirty || level == null || level.isClientSide()) {
            return;
        }
        final long now = level.getGameTime();
        if (isPduManaged(now)) {
            for (FeGridMember node : nodes) {
                node.setLastFlow(0);
            }

            if (now != lastSolvedTick) {
                lastSolvedTick = now;
                lastResult = FeGridSolver.observe(served);
            }

            wasPduManaged = true;
            return;
        }
        if (wasPduManaged) {
            wasPduManaged = false;
            for (FeGridMember node : served) {
                if (node.attachedStorage() instanceof FeConsumer fe) {
                    fe.setLoadShed(false);
                }
            }
        }
        if (now == lastSolvedTick) {
            return;
        }
        lastSolvedTick = now;

        nodes.removeIf(node -> node.isGone(level));
        served.removeIf(node -> node.isGone(level));
        if (nodes.isEmpty()) {
            dirty = true;
            lastResult = FeGridSolver.Result.EMPTY;
            return;
        }
        flowByNode.clear();

        final boolean limited = cableLimitEnabled();

        final int supplyLeft = supplyBudgetRemaining(level, now);
        final int ceiling = Math.min(limited ? budgetRemaining(now) : FeGridSolver.NO_CEILING,
                supplyLeft);
        servingExternal = true;
        try {
            lastResult = FeGridSolver.solve(served, distributionCursor++, flowByNode,
                    reserveCascadeEnabled(), ceiling);
        } finally {
            servingExternal = false;
        }

        spendSupplyBudget(level, now, Math.max(lastResult.collected(), lastResult.delivered())
                + lastResult.charged());

        if (limited) {
            spendBudget(now, Math.max(lastResult.collected(), lastResult.delivered())
                    + lastResult.charged());
        }

        for (FeGridMember node : nodes) {
            node.setLastFlow(flowByNode.getOrDefault(node.nodeKey(), 0));
        }

        if (moveFe.length == 0) {
            flow.recompute(flowByNode, seamSeeds(now), now);
        } else {
            flow.recompute(flowByNode, seamSeeds(now), FeFlowGraph.NO_BOUNDARY, 0, moveFrom, moveTo, moveFe,
                    pathBudget.movesInto(now, moveFrom, moveTo, moveFe), now);
        }
    }

    public int flowThrough(long nodeKey, long now) {
        return flow.flowThrough(nodeKey, now);
    }

    public int flowBetween(long a, long b, long now) {
        return flow.edgeFlow(a, b, now);
    }

    private Map<Long, Integer> seamSeeds(long now) {
        if (seamNodeTick == now) {
            return seamByNodeLast;
        }
        if (seamNodeTick == now - 1) {
            return seamByNode;
        }
        return Map.of();
    }

    public void noteSeamFlowAt(long nodeKey, int signedFe, long gameTime) {
        if (signedFe == 0) {
            return;
        }
        if (gameTime != seamNodeTick) {
            seamByNodeLast.clear();

            if (gameTime == seamNodeTick + 1) {
                seamByNodeLast.putAll(seamByNode);
            }
            seamByNode.clear();
            seamNodeTick = gameTime;
        }
        seamByNode.merge(nodeKey, signedFe, Integer::sum);
    }

    public int throughputFePerTick(long now) {
        final int seam = seamFlow.at(now);
        if (isPduManaged(now)) {
            return managedThroughput.at(now) + seam;
        }
        return Math.max(0, lastResult.consumed()) + seam;
    }

    public void reportManagedThroughput(int fePerTick, long gameTime) {
        managedThroughput.add(fePerTick, gameTime);
    }

    public void reportManagedFlows(Map<Long, Integer> signedFlow, long anchorKey, long gameTime) {
        reportManagedFlows(signedFlow, anchorKey, 0, gameTime);
    }

    public void reportManagedFlows(Map<Long, Integer> signedFlow, long anchorKey, int anchorSupply,
                                   long gameTime) {
        managedSeeds.add(signedFlow, anchorKey, anchorSupply, gameTime);

        if (anchorKey != FeFlowGraph.NO_BOUNDARY) {
            pathBudget.rootOn(anchorKey);
        }

        final int moves = moveFe.length == 0 ? 0 : pathBudget.movesInto(gameTime, moveFrom, moveTo, moveFe);
        flow.recompute(managedSeeds.seeds(), seamSeeds(gameTime), managedSeeds.boundaryKey(),
                managedSeeds.boundaryFlow(), moveFrom, moveTo, moveFe, moves, gameTime);
    }

    private final FeManagedSeeds managedSeeds = new FeManagedSeeds();

    public void recordSeamFlow(int fe, long gameTime) {
        seamFlow.add(fe, gameTime);
    }

    public static int noteSeamTransfer(@Nullable FeWireNetwork network, @Nullable Level level,
                                       boolean simulate, int moved, long nodeKey) {
        if (!simulate && moved > 0 && network != null && level != null && !level.isClientSide()) {
            network.recordSeamFlow(moved, level.getGameTime());

            network.noteSeamFlowAt(nodeKey, moved, level.getGameTime());
        }
        return moved;
    }

    private boolean servingExternal;

    private final List<FeGridMember> seamSources = new ArrayList<>();
    private final List<FeGridMember> seamTanks = new ArrayList<>();
    private long seamScanTick = Long.MIN_VALUE;

    private void scanSeam(long now) {
        if (now == seamScanTick) {
            return;
        }
        seamScanTick = now;
        seamSources.clear();
        seamTanks.clear();
        for (FeGridMember node : served) {
            final net.neoforged.neoforge.energy.IEnergyStorage handler = node.attachedStorage();
            if (handler == null) {
                continue;
            }
            switch (FeNodeRole.of(handler, node.transferRate()).kind()) {
                case GENERATOR -> seamSources.add(node);
                case STORAGE -> seamTanks.add(node);
                default -> {
                }
            }
        }

        seamTanks.sort((a, b) -> {
            final int rankA = FeGridSolver.reserveRankOf(a);
            final int rankB = FeGridSolver.reserveRankOf(b);
            return rankA != rankB ? Integer.compare(rankA, rankB)
                    : Long.compare(a.nodeKey(), b.nodeKey());
        });
    }

    public int giveToExternal(long requesterKey, int max, boolean simulate, Level level) {
        if (max <= 0 || servingExternal || level == null || level.isClientSide() || dirty) {
            return 0;
        }
        if (isPduManaged(level.getGameTime())) {
            return 0;
        }
        servingExternal = true;
        try {
            final long now = level.getGameTime();
            scanSeam(now);

            final int allowed = Math.min(Math.min(cableLimitEnabled()
                            ? Math.min(max, budgetRemaining(now)) : max,
                    supplyBudgetRemaining(level, now)), conductorAllowance(requesterKey));
            if (allowed <= 0) {
                return 0;
            }
            int given = 0;
            for (FeGridMember node : nodes) {
                if (given >= allowed) {
                    break;
                }
                if (node.nodeKey() == requesterKey || node.bufferCapacity() <= 0) {
                    continue;
                }
                final int take = Math.min(allowed - given, node.storedEnergy());
                if (take <= 0) {
                    continue;
                }
                if (!simulate) {
                    node.setStoredEnergy(node.storedEnergy() - take);

                    noteSeamFlowAt(node.nodeKey(), -take, now);
                }
                given += take;
            }
            for (FeGridMember node : seamSources) {
                if (given >= allowed) {
                    break;
                }
                if (node.nodeKey() == requesterKey) {
                    continue;
                }
                final net.neoforged.neoforge.energy.IEnergyStorage handler = node.attachedStorage();
                if (handler == null) {
                    continue;
                }
                final int drawn = handler.extractEnergy(
                        Math.min(allowed - given, node.transferRate()), simulate);
                given += drawn;
                if (!simulate) {
                    noteSeamFlowAt(node.nodeKey(), -drawn, now);
                }
            }
            if (!simulate) {
                spendBudget(now, given);
                spendSupplyBudget(level, now, given);
                bookConductorDelivery(requesterKey, given);
            }
            return given;
        } finally {
            servingExternal = false;
        }
    }

    /** the other way in. not symmetrical on purpose, incoming FE can't dodge a priority rule */
    public int takeFromExternal(long requesterKey, int max, boolean simulate, Level level) {
        if (max <= 0 || servingExternal || dirty || level == null || level.isClientSide()) {
            return 0;
        }
        servingExternal = true;
        try {
            final long now = level.getGameTime();
            scanSeam(now);

            final int allowed = cableLimitEnabled()
                    ? Math.min(max, budgetRemaining(now)) : max;
            if (allowed <= 0) {
                return 0;
            }
            int taken = 0;

            int intoTanks = 0;
            for (FeGridMember node : seamTanks) {
                if (taken >= allowed) {
                    break;
                }
                if (node.nodeKey() == requesterKey) {
                    continue;
                }
                final net.neoforged.neoforge.energy.IEnergyStorage tank = node.attachedStorage();
                if (tank == null) {
                    continue;
                }
                final int stored = tank.receiveEnergy(
                        Math.min(allowed - taken, node.transferRate()), simulate);
                taken += stored;
                intoTanks += stored;
                if (!simulate) {
                    noteSeamFlowAt(node.nodeKey(), stored, now);
                }
            }

            recordSeamFlow(simulate ? 0 : intoTanks, now);
            for (FeGridMember node : nodes) {
                if (taken >= allowed) {
                    break;
                }
                if (node.nodeKey() == requesterKey) {
                    continue;
                }
                final int room = Math.min(allowed - taken,
                        node.bufferCapacity() - node.storedEnergy());
                if (room <= 0) {
                    continue;
                }
                if (!simulate) {
                    node.setStoredEnergy(node.storedEnergy() + room);
                    noteSeamFlowAt(node.nodeKey(), room, now);
                }
                taken += room;
            }
            if (!simulate) {
                spendBudget(now, taken);

                creditSupplyBudget(level, now, taken);

                noteSeamFlowAt(requesterKey, -taken, now);
            }
            return taken;
        } finally {
            servingExternal = false;
        }
    }

    public static boolean cableLimitEnabled() {
        try {
            return com.createstats.ModConfiguration.COMMON.cableThroughputLimit.get();
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    private static boolean reserveCascadeEnabled() {
        try {
            return com.createstats.ModConfiguration.COMMON.reserveCascadeEnabled.get();
        } catch (RuntimeException ignored) {
            return true;
        }
    }
}
