package com.createstats.gametest;

import com.createstats.CreateStats;
import com.createstats.block.SecurityRelayBlock;
import com.createstats.blockentity.HomeSecurityPanelBE;
import com.createstats.blockentity.PowerConnectorBE;
import com.createstats.blockentity.PowerDistributionUnitBE;
import com.createstats.blockentity.PoweredDoorBE;
import com.createstats.blockentity.SecurityRelayBE;
import com.createstats.network.SecurityRelayActionPacket;
import com.createstats.network.SecuritySyncPacket;
import com.createstats.power.FeLock;
import com.createstats.power.PduMode;
import com.createstats.power.WireTier;
import com.createstats.registry.ModBlocks;
import com.createstats.security.SecurityHousehold;
import com.createstats.security.SecurityMode;
import com.createstats.security.SecurityRole;
import com.createstats.security.SensorKind;
import com.createstats.security.relay.RelayBehavior;
import com.createstats.security.relay.RelayConditions;
import com.createstats.security.relay.RelayConfig;
import com.createstats.security.relay.RelayContact;
import com.createstats.security.relay.RelayRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.createstats.gametest.SecurityIntegrityGameTest.door;
import static com.createstats.gametest.SecurityIntegrityGameTest.fake;
import static com.createstats.gametest.SecurityIntegrityGameTest.feed;
import static com.createstats.gametest.SecurityIntegrityGameTest.panel;
import static com.createstats.gametest.SecurityIntegrityGameTest.sensor;

@GameTestHolder(CreateStats.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SecurityRelayGameTest {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("create_stats/gametest");

    private SecurityRelayGameTest() {
    }

    static void trace(String tag, GameTestHelper h, SecurityRelayBE r, @org.jetbrains.annotations.Nullable SecurityHousehold house) {
        final StringBuilder s = new StringBuilder("[RELAY-GT] ").append(tag).append(" t=").append(h.getTick())
                .append(" contact=").append(r.contact()).append(" powered=").append(r.powered())
                .append(" out=").append(r.output()).append(" cond=").append(r.config().conditionId())
                .append(" beh=").append(r.config().behavior()).append(" pend=").append(r.pending())
                .append(" read=").append(r.readingDetail()).append(" rev=").append(r.revision())
                .append(" gov=").append(r.governingPanel() == null ? "none" : h.relativePos(r.governingPanel()) + "="
                        + h.getLevel().getBlockEntity(r.governingPanel()))
                .append(" removed=").append(r.isRemoved());
        if (house != null) {
            s.append(" | enrolled=").append(house.enrollments()).append(" relays=").append(house.relays().size());
            if (r.relayId() != null && house.relay(r.relayId()) != null) {
                s.append(" sub.read=").append(house.relay(r.relayId()).reading());
            }
        }
        LOG.info(s.toString());
    }

    static SecurityRelayBE relay(GameTestHelper h, BlockPos rel, Direction facing, FakePlayer placer) {
        h.setBlock(rel.below(), Blocks.STONE);
        h.setBlock(rel, ModBlocks.SECURITY_RELAY.get().defaultBlockState().setValue(SecurityRelayBlock.FACING, facing));
        final SecurityRelayBE r = h.getBlockEntity(rel);
        r.setPlacer(placer);
        return r;
    }

    static HomeSecurityPanelBE panelBeside(GameTestHelper h, BlockPos beside, Direction side, FakePlayer owner) {
        final BlockPos at = beside.relative(side);
        h.setBlock(at.relative(side), Blocks.STONE);
        h.setBlock(at, ModBlocks.HOME_SECURITY_PANEL.get().defaultBlockState()
                .setValue(com.createstats.block.HomeSecurityPanelBlock.FACING, side));
        final HomeSecurityPanelBE p = h.getBlockEntity(at);
        p.claim(owner);
        p.household().setConfigured(true);
        return p;
    }

    static RelayConfig follow(com.createstats.security.relay.RelayCondition c) {
        return new RelayConfig(c.id(), RelayBehavior.FOLLOW, RelayConfig.DEFAULT_PULSE, "home", null);
    }

    static int dustPower(GameTestHelper h, BlockPos rel) {
        final BlockState s = h.getLevel().getBlockState(h.absolutePos(rel));
        return s.is(Blocks.REDSTONE_WIRE) ? s.getValue(RedStoneWireBlock.POWER) : -1;
    }

    @GameTest(template = "fe_bench", timeoutTicks = 400)
    public static void relayDrivesItsBackFaceOnly(GameTestHelper h) {
        final ServerLevel level = h.getLevel();
        final FakePlayer owner = fake(level, "relay_face_owner");
        final Direction[] facings = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        final BlockPos[] centres = {new BlockPos(4, 1, 4), new BlockPos(12, 1, 4), new BlockPos(4, 1, 12),
                new BlockPos(12, 1, 12)};
        final SecurityRelayBE[] relays = new SecurityRelayBE[4];
        final HomeSecurityPanelBE[] panels = new HomeSecurityPanelBE[4];
        for (int i = 0; i < 4; i++) {
            final Direction f = facings[i];
            final BlockPos r = centres[i];

            h.setBlock(r.relative(f.getOpposite()).below(), Blocks.STONE);
            h.setBlock(r.relative(f.getOpposite()), Blocks.REDSTONE_WIRE);
            h.setBlock(r.relative(f).below(), Blocks.STONE);
            h.setBlock(r.relative(f), Blocks.REDSTONE_WIRE);
            relays[i] = relay(h, r, f, owner);
            panels[i] = panelBeside(h, r, f.getClockWise(), owner);
        }

        final BlockPos lampRelay = new BlockPos(20, 1, 4);
        final BlockPos lampDirect = lampRelay.south();
        h.setBlock(lampDirect, Blocks.REDSTONE_LAMP);
        final SecurityRelayBE lamp = relay(h, lampRelay, Direction.NORTH, owner);
        final HomeSecurityPanelBE lampPanel = panelBeside(h, lampRelay, Direction.EAST, owner);
        final BlockPos wallRelay = new BlockPos(20, 1, 12);
        h.setBlock(wallRelay.south(), Blocks.STONE);
        h.setBlock(wallRelay.south(2), Blocks.REDSTONE_LAMP);
        final SecurityRelayBE wall = relay(h, wallRelay, Direction.NORTH, owner);
        final HomeSecurityPanelBE wallPanel = panelBeside(h, wallRelay, Direction.EAST, owner);

        h.onEachTick(() -> {
            for (int i = 0; i < 4; i++) {
                feed(h, centres[i]);
                feed(h, centres[i].relative(facings[i].getClockWise()));
            }
            feed(h, lampRelay);
            feed(h, lampRelay.relative(Direction.EAST));
            feed(h, wallRelay);
            feed(h, wallRelay.relative(Direction.EAST));
        });
        h.startSequence()
                .thenIdle(90)
                .thenExecute(() -> {
                    for (SecurityRelayBE r : new SecurityRelayBE[]{relays[0], relays[1], relays[2], relays[3], lamp, wall}) {
                        h.assertTrue(r.contact() == RelayContact.LINKED,
                                "setup: a relay placed by the owner beside the owner's panel joins at once, was "
                                        + r.contact());
                        h.assertTrue(r.configure(owner, follow(RelayConditions.MODE), r.revision()).applied(),
                                "setup: the owner sets it to follow Household mode = Home");
                    }
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    for (int i = 0; i < 4; i++) {
                        final Direction f = facings[i];
                        h.assertTrue(relays[i].output() == 15, f + ": the relay should be driving 15");
                        h.assertTrue(dustPower(h, centres[i].relative(f.getOpposite())) == 15,
                                f + ": the dust behind the post should read 15, read "
                                        + dustPower(h, centres[i].relative(f.getOpposite())));
                        h.assertTrue(dustPower(h, centres[i].relative(f)) == 0,
                                f + ": the dust in front of the faceplate must read 0");
                        final BlockState behind = level.getBlockState(h.absolutePos(centres[i].relative(f.getOpposite())));
                        h.assertTrue(behind.getValue(RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(f)) != RedstoneSide.NONE,
                                f + ": the dust behind must bend toward the output post");
                    }
                    h.assertTrue(level.getBlockState(h.absolutePos(lampDirect)).getValue(RedstoneLampBlock.LIT),
                            "a lamp directly behind the post lights");
                    h.assertFalse(level.getBlockState(h.absolutePos(wallRelay.south(2))).getValue(RedstoneLampBlock.LIT),
                            "a weak source must not power through the wall behind it");
                })
                .thenSucceed();
    }

    @GameTest(template = "fe_bench", timeoutTicks = 900)
    public static void relayHoldsThroughOutageAndReload(GameTestHelper h) {
        final ServerLevel level = h.getLevel();
        final FakePlayer owner = fake(level, "relay_hold_owner");
        final BlockPos relayRel = new BlockPos(6, 1, 6);
        final SecurityRelayBE[] relay = {relay(h, relayRel, Direction.NORTH, owner)};
        final HomeSecurityPanelBE panel = panelBeside(h, relayRel, Direction.EAST, owner);

        final BlockPos panelRel = relayRel.relative(Direction.EAST);
        final boolean[] powerOn = {true, true};
        h.onEachTick(() -> {
            if (powerOn[0]) {
                feed(h, relayRel);
            }
            if (powerOn[1]) {
                feed(h, panelRel);
            }
            if (h.getTick() % 10 == 0) {
                trace("hold", h, relay[0], null);
            }
        });
        final SecurityHousehold house = panel.household();
        h.startSequence()
                .thenIdle(90)
                .thenExecute(() -> {
                    h.assertTrue(relay[0].contact() == RelayContact.LINKED, "setup: linked");
                    h.assertTrue(relay[0].configure(owner, new RelayConfig(RelayConditions.TAMPER.id(),
                            RelayBehavior.HOLD, 10, "home", null), relay[0].revision()).applied(), "setup: Hold tamper");
                    house.setMode(SecurityMode.AWAY);
                    powerOn[0] = false;
                })

                .thenIdle(260)
                .thenExecute(() -> {
                    h.assertFalse(relay[0].powered(), "setup: the relay has run out of power");
                    h.assertTrue(relay[0].output() == 0, "no power, no output");
                    panel.tripped(panel.getBlockPos(), SensorKind.TAMPER, "test tamper");
                    h.assertTrue(house.relay(relay[0].relayId()).state().pending(),
                            "the household held the incident while the relay was dark");
                    h.assertTrue(relay[0].output() == 0, "a dark relay still drives nothing");
                    powerOn[0] = true;
                })
                .thenIdle(10)
                .thenExecute(() -> h.assertTrue(relay[0].output() == 15,
                        "power back: the held incident is on the line"))
                .thenExecute(() -> {
                    final CompoundTag saved = relay[0].saveWithFullMetadata(level.registryAccess());
                    h.setBlock(relayRel, Blocks.AIR);
                    h.setBlock(relayRel, ModBlocks.SECURITY_RELAY.get().defaultBlockState()
                            .setValue(SecurityRelayBlock.FACING, Direction.NORTH));
                    relay[0] = h.getBlockEntity(relayRel);
                    relay[0].loadWithComponents(saved, level.registryAccess());
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    h.assertTrue(relay[0].output() == 15, "after a reload the relay is holding again");

                    h.setBlock(panelRel, Blocks.AIR);
                    powerOn[1] = false;
                    relay[0].repoll();
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    h.assertTrue(relay[0].contact() == RelayContact.LOST, "no panel left: lost, was " + relay[0].contact());
                    h.assertTrue(relay[0].output() == 15, "a lost relay keeps the hold it had received");
                    h.assertTrue(relay[0].pressReset(owner) == RelayRules.ResetVerdict.NO_CONTACT,
                            "a reset needs the house to be reachable");
                    h.assertTrue(relay[0].output() == 15, "and a refused reset changes nothing");
                })
                .thenSucceed();
    }

    @GameTest(template = "fe_bench", timeoutTicks = 600)
    public static void relayPulsesExactlyAndNeverReplays(GameTestHelper h) {
        final ServerLevel level = h.getLevel();
        final FakePlayer owner = fake(level, "relay_pulse_owner");
        final FakePlayer stranger = fake(level, "relay_pulse_stranger");
        final BlockPos relayRel = new BlockPos(6, 1, 6);

        final BlockPos doorRel = new BlockPos(7, 1, 7);
        final SecurityRelayBE[] relay = {relay(h, relayRel, Direction.NORTH, owner)};
        final HomeSecurityPanelBE panel = panelBeside(h, relayRel, Direction.EAST, owner);
        final PoweredDoorBE door = door(h, doorRel);
        final BlockPos panelRel = relayRel.relative(Direction.EAST);
        final int[] onTicks = {0};
        h.onEachTick(() -> {
            feed(h, relayRel);
            feed(h, panelRel);
            feed(h, doorRel);
            if (relay[0].output() > 0) {
                onTicks[0]++;
            }
        });
        h.startSequence()
                .thenIdle(90)
                .thenExecute(() -> {
                    h.assertTrue(door.governed(), "setup: the door answers to the panel");

                    door.noteAccess(stranger, false);
                    h.assertTrue(relay[0].configure(owner, new RelayConfig(RelayConditions.DENIED.id(),
                                    RelayBehavior.PULSE, 7, "home", null), relay[0].revision()).applied(),
                            "setup: Pulse 7 on access denied");
                    onTicks[0] = 0;
                })
                .thenIdle(30)
                .thenExecute(() -> {
                    h.assertTrue(onTicks[0] == 0, "a refusal from before the relay was set must not replay, saw "
                            + onTicks[0] + " ticks on");
                    door.noteAccess(stranger, false);
                })
                .thenIdle(30)
                .thenExecute(() -> {
                    // 7 ticks, or 6 if the refusal lands after our tick. never a second pulse

                    h.assertTrue(onTicks[0] == 7 || onTicks[0] == 6,
                            "one refusal, one pulse of its 7 ticks from acceptance, saw " + onTicks[0]);
                    final CompoundTag saved = relay[0].saveWithFullMetadata(level.registryAccess());
                    h.setBlock(relayRel, Blocks.AIR);
                    h.setBlock(relayRel, ModBlocks.SECURITY_RELAY.get().defaultBlockState()
                            .setValue(SecurityRelayBlock.FACING, Direction.NORTH));
                    relay[0] = h.getBlockEntity(relayRel);
                    relay[0].loadWithComponents(saved, level.registryAccess());
                    onTicks[0] = 0;
                })
                .thenIdle(80)
                .thenExecute(() -> h.assertTrue(onTicks[0] == 0,
                        "a reload must not replay a pulse that already ran, saw " + onTicks[0]))
                .thenSucceed();
    }

    // door broken out: latch within one sweep, not after the freshness window and not never
    @GameTest(template = "fe_bench", timeoutTicks = 700)
    public static void relayHoldsABrokenDoorAndKeepsItsPlace(GameTestHelper h) {
        final ServerLevel level = h.getLevel();
        final FakePlayer owner = fake(level, "relay_breach_owner");
        final BlockPos relayRel = new BlockPos(6, 1, 6);
        final BlockPos doorRel = new BlockPos(7, 1, 7);
        final SecurityRelayBE[] relay = {relay(h, relayRel, Direction.NORTH, owner)};
        final HomeSecurityPanelBE panel = panelBeside(h, relayRel, Direction.EAST, owner);
        final PoweredDoorBE[] door = {door(h, doorRel)};
        final BlockPos panelRel = relayRel.relative(Direction.EAST);
        final boolean[] doorStands = {true};
        final com.createstats.security.SecurityEndpoint doorAt =
                com.createstats.security.SecurityIdentity.anchorOf(level, h.absolutePos(doorRel));
        final RelayConfig onTheDoor = new RelayConfig(RelayConditions.BREACHED.id(), RelayBehavior.HOLD,
                RelayConfig.DEFAULT_PULSE, "home", doorAt);
        final SecurityHousehold house = panel.household();
        h.onEachTick(() -> {
            feed(h, relayRel);
            feed(h, panelRel);
            if (doorStands[0]) {
                feed(h, doorRel);
            }
            if (h.getTick() % 10 == 0) {
                trace("breach", h, relay[0], house);
            }
        });
        h.startSequence()
                .thenIdle(90)
                .thenExecute(() -> {
                    h.assertTrue(relay[0].contact() == RelayContact.LINKED, "setup: linked");
                    h.assertTrue(door[0].governed(), "setup: the door answers to the panel");
                    h.assertTrue(relay[0].configure(owner, onTheDoor, relay[0].revision()).applied(),
                            "setup: Hold on a breach of that one door");
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    h.assertTrue(relay[0].reading() == com.createstats.security.Truth.FALSE,
                            "a standing door is known not breached, read " + relay[0].reading());
                    h.assertTrue(relay[0].output() == 0, "nothing held yet");
                    doorStands[0] = false;
                    h.setBlock(doorRel.above(), Blocks.AIR);
                    h.setBlock(doorRel, Blocks.AIR);
                })

                .thenIdle(45)
                .thenExecute(() -> {
                    h.assertTrue(relay[0].pending(), "the door broken out of the wall is held as a breach");
                    h.assertTrue(relay[0].output() == 15, "and driven, output " + relay[0].output());
                    h.assertTrue(relay[0].pressReset(owner) == RelayRules.ResetVerdict.STILL_ACTIVE,
                            "the door is still gone, so the incident is not over");

                    h.setBlock(relayRel, Blocks.AIR);
                    relay[0] = relay(h, relayRel, Direction.NORTH, owner);
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    h.assertTrue(relay[0].contact() == RelayContact.LINKED, "put back: linked, was " + relay[0].contact());
                    h.assertTrue(onTheDoor.equals(relay[0].config()),
                            "put back: it took over its record, door and all, not defaults: " + relay[0].config());
                    h.assertTrue(relay[0].pending() && relay[0].output() == 15,
                            "breaking the relay is not a way to drop the incident it held");
                    h.assertTrue(house.relays().size() == 1, "one place, one record, saw " + house.relays().size());
                    h.assertTrue(panel.relayAt(h.absolutePos(relayRel)).relayId().equals(relay[0].relayId()),
                            "the panel's Relays tab reads the record the block runs");
                    door[0] = door(h, doorRel);
                    doorStands[0] = true;
                })
                .thenIdle(90)
                .thenExecute(() -> {
                    h.assertTrue(relay[0].reading() == com.createstats.security.Truth.FALSE,
                            "the door is back, so the breach is over, read " + relay[0].reading());
                    h.assertTrue(relay[0].pressReset(owner) == RelayRules.ResetVerdict.RESET, "reset now allowed");
                })
                .thenIdle(3)
                .thenExecute(() -> h.assertTrue(relay[0].output() == 0, "reset: the line drops"))
                .thenSucceed();
    }

    @GameTest(template = "fe_bench", timeoutTicks = 700)
    public static void relayHoldsADoubleDoorOpenedByItsSecondLeaf(GameTestHelper h) {
        final ServerLevel level = h.getLevel();
        final FakePlayer owner = fake(level, "relay_double_owner");
        final BlockPos relayRel = new BlockPos(6, 1, 6);
        final BlockPos doorRel = new BlockPos(7, 1, 7);
        final SecurityRelayBE relay = relay(h, relayRel, Direction.NORTH, owner);
        final HomeSecurityPanelBE panel = panelBeside(h, relayRel, Direction.EAST, owner);
        door(h, doorRel);
        final BlockState first = level.getBlockState(h.absolutePos(doorRel));
        final BlockPos secondAbs = h.absolutePos(doorRel).relative(
                com.createstats.block.PoweredDoorBlock.widthAxis(first.getValue(com.createstats.block.PoweredDoorBlock.FACING)));
        final BlockState secondLower = first.setValue(com.createstats.block.PoweredDoorBlock.HINGE,
                net.minecraft.world.level.block.state.properties.DoorHingeSide.RIGHT);
        final Runnable placeSecond = () -> {
            level.setBlockAndUpdate(secondAbs.below(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(secondAbs, secondLower);
            level.setBlockAndUpdate(secondAbs.above(), secondLower.setValue(com.createstats.block.PoweredDoorBlock.HALF,
                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        };
        placeSecond.run();
        final BlockPos panelRel = relayRel.relative(Direction.EAST);
        final boolean[] secondStands = {true};
        final com.createstats.security.SecurityEndpoint doorway =
                com.createstats.security.SecurityIdentity.anchorOf(level, h.absolutePos(doorRel));
        final com.createstats.security.SecurityEndpoint second =
                com.createstats.security.SecurityIdentity.at(level, secondAbs);
        final RelayConfig onTheDoorway = new RelayConfig(RelayConditions.BREACHED.id(), RelayBehavior.HOLD,
                RelayConfig.DEFAULT_PULSE, "home", doorway);
        final SecurityHousehold house = panel.household();
        h.onEachTick(() -> {
            feed(h, relayRel);
            feed(h, panelRel);
            feed(h, doorRel);
            if (secondStands[0]) {
                final IEnergyStorage e = level.getCapability(
                        net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, secondAbs, null);
                if (e != null) {
                    e.receiveEnergy(1_000, false);
                }
            }
            if (h.getTick() % 10 == 0) {
                trace("double", h, relay, house);
            }
        });
        h.startSequence()
                .thenIdle(90)
                .thenExecute(() -> {
                    h.assertTrue(com.createstats.block.PoweredDoorBlock.partnerOf(level, h.absolutePos(doorRel), first)
                            != null, "setup: the two leaves pair as one double door");
                    h.assertTrue(doorway.pos() != second.pos(),
                            "setup: the leaf mined below is not the row's anchor (a wrong pairing would make it so)");
                    h.assertTrue(relay.contact() == RelayContact.LINKED, "setup: linked");
                    h.assertTrue(house.isEnrolled(doorway), "setup: the doorway is enrolled");
                    h.assertTrue(house.isEnrolled(second) && house.doorwayOf(second).equals(doorway),
                            "the second leaf is enrolled and tied to its doorway");
                    final long doorRows = panel.devices().stream()
                            .filter(d -> d.deviceClass() == com.createstats.power.SecurityDevice.DeviceClass.DOOR)
                            .count();
                    h.assertTrue(doorRows == 1, "still one row for the pair, no ghost row for its leaf: " + doorRows);
                    h.assertTrue(relay.configure(owner, onTheDoorway, relay.revision()).applied(),
                            "setup: Hold on a breach of the doorway");
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    h.assertTrue(relay.reading() == com.createstats.security.Truth.FALSE,
                            "both leaves standing: not breached, read " + relay.reading());
                    secondStands[0] = false;
                    level.setBlockAndUpdate(secondAbs.above(), Blocks.AIR.defaultBlockState());
                    level.setBlockAndUpdate(secondAbs, Blocks.AIR.defaultBlockState());
                })

                .thenIdle(45)
                .thenExecute(() -> {
                    h.assertTrue(relay.pending(), "the second leaf broken out is held as a breach of the doorway");
                    h.assertTrue(relay.output() == 15, "and driven, output " + relay.output());
                    placeSecond.run();
                    secondStands[0] = true;
                })
                .thenIdle(90)
                .thenExecute(() -> {
                    h.assertTrue(relay.reading() == com.createstats.security.Truth.FALSE,
                            "the leaf is back, so the breach is over, read " + relay.reading());
                    h.assertTrue(relay.pressReset(owner) == RelayRules.ResetVerdict.RESET, "reset now allowed");
                })
                .thenIdle(3)
                .thenExecute(() -> h.assertTrue(relay.output() == 0, "reset: the line drops"))
                .thenSucceed();
    }

    @GameTest(template = "fe_bench", timeoutTicks = 300)
    public static void relayRefusesWithoutRights(GameTestHelper h) {
        final ServerLevel level = h.getLevel();
        final FakePlayer owner = fake(level, "relay_auth_owner");
        final FakePlayer member = fake(level, "relay_auth_member");
        final FakePlayer stranger = fake(level, "relay_auth_stranger");
        final BlockPos relayRel = new BlockPos(6, 1, 6);
        final SecurityRelayBE relay = relay(h, relayRel, Direction.NORTH, owner);
        final HomeSecurityPanelBE panel = panelBeside(h, relayRel, Direction.EAST, owner);
        panel.household().roster().add(member.getUUID(), "relay_auth_member");
        final BlockPos panelRel = relayRel.relative(Direction.EAST);
        h.onEachTick(() -> {
            feed(h, relayRel);
            feed(h, panelRel);
        });
        h.startSequence()
                .thenIdle(90)
                .thenExecute(() -> {
                    final RelayConfig before = relay.config();
                    h.assertTrue(relay.configure(member, follow(RelayConditions.FIRE), relay.revision())
                                    == RelayRules.ConfigVerdict.FORBIDDEN, "a member may not retune a relay");
                    h.assertTrue(relay.configure(stranger, follow(RelayConditions.FIRE), relay.revision())
                                    == RelayRules.ConfigVerdict.FORBIDDEN, "a stranger may not retune a relay");
                    h.assertTrue(relay.config().equals(before), "a refused edit changes nothing");
                    h.assertTrue(relay.pressReset(stranger) == RelayRules.ResetVerdict.FORBIDDEN,
                            "a stranger may not reset");
                    panel.household().roster().setRole(member.getUUID(), SecurityRole.ADMIN);
                    h.assertTrue(relay.configure(member, follow(RelayConditions.FIRE), relay.revision()).applied(),
                            "promoted to admin, the same edit is accepted");
                    h.assertTrue(relay.configure(owner, follow(RelayConditions.WATER), relay.revision() - 1)
                                    == RelayRules.ConfigVerdict.STALE, "an edit drawn before that one is stale");
                })
                .thenSucceed();
    }

    @GameTest(template = "fe_bench", timeoutTicks = 400)
    public static void candidateDrivesNothingUntilAccepted(GameTestHelper h) {
        final ServerLevel level = h.getLevel();
        final FakePlayer owner = fake(level, "relay_cand_owner");
        final FakePlayer stranger = fake(level, "relay_cand_stranger");
        final BlockPos relayRel = new BlockPos(6, 1, 6);
        final SecurityRelayBE relay = relay(h, relayRel, Direction.NORTH, stranger);
        final HomeSecurityPanelBE panel = panelBeside(h, relayRel, Direction.EAST, owner);

        final BlockPos panelRel = relayRel.relative(Direction.EAST);
        h.onEachTick(() -> {
            feed(h, relayRel);
            feed(h, panelRel);
        });
        h.startSequence()
                .thenIdle(90)
                .thenExecute(() -> {
                    h.assertTrue(relay.contact() == RelayContact.CANDIDATE,
                            "placed by a stranger: a candidate, was " + relay.contact());
                    panel.household().raiseLockdown(level.getGameTime(), "test");
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    h.assertTrue(relay.output() == 0, "a candidate drives nothing, lockdown or not");
                    panel.rebuild();
                    panel.relayAction(owner, h.absolutePos(relayRel), SecurityRelayActionPacket.ACCEPT,
                            RelayConfig.DEFAULT, 0L);
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    h.assertTrue(relay.contact() == RelayContact.LINKED, "accepted by the owner: linked");
                    h.assertTrue(relay.output() == 15, "and following the standing lockdown");
                })
                .thenSucceed();
    }

    @GameTest(template = "fe_bench", timeoutTicks = 900)
    public static void relayFollowsFireAndIgnoresWater(GameTestHelper h) {
        final ServerLevel level = h.getLevel();
        final FakePlayer owner = fake(level, "relay_fire_owner");
        final BlockPos fireRelayRel = new BlockPos(6, 1, 6);
        final BlockPos waterRelayRel = new BlockPos(6, 1, 8);
        final SecurityRelayBE fireRelay = relay(h, fireRelayRel, Direction.NORTH, owner);
        final HomeSecurityPanelBE panel = panelBeside(h, fireRelayRel, Direction.EAST, owner);

        final BlockPos panelRel = fireRelayRel.relative(Direction.EAST);
        final BlockPos sensorRel = new BlockPos(6, 1, 7);
        sensor(h, sensorRel, SensorKind.FIRE);
        final SecurityRelayBE waterRelay = relay(h, waterRelayRel, Direction.NORTH, owner);
        final BlockPos waterSensorRel = new BlockPos(5, 1, 8);
        sensor(h, waterSensorRel, SensorKind.WATER);
        final BlockPos campfire = new BlockPos(9, 1, 9);
        h.setBlock(campfire.below(), Blocks.STONE);
        h.onEachTick(() -> {
            feed(h, fireRelayRel);
            feed(h, waterRelayRel);
            feed(h, panelRel);
            feed(h, sensorRel);
            feed(h, waterSensorRel);
            if (h.getTick() % 20 == 0) {
                trace("fire", h, fireRelay, panel.household());
                final com.createstats.security.SecurityEndpoint at = com.createstats.security.SecurityIdentity.at(level,
                        h.absolutePos(sensorRel));
                final com.createstats.blockentity.SecuritySensorBE sb = h.getBlockEntity(sensorRel);
                final StringBuilder rows = new StringBuilder();
                for (com.createstats.security.SecuritySegment.Device d : panel.devices()) {
                    rows.append(h.relativePos(d.pos())).append(':').append(d.deviceClass()).append(' ');
                }
                LOG.info("[RELAY-GT] fire panel house=" + panel.householdId() + " awake=" + panel.isAwake()
                        + " rows=" + rows + " | sensor governed=" + sb.governed() + " gov=" + sb.governingHousehold()
                        + " panel=" + (sb.governingPanel() == null ? null : h.relativePos(sb.governingPanel())));
                LOG.info("[RELAY-GT] fire sensor facet " + panel.household().hazards().truth(at,
                        com.createstats.security.HazardWatch.Hazard.FIRE, level.getGameTime()) + " heard="
                        + panel.household().hazards().heardFrom(at, com.createstats.security.HazardWatch.Hazard.FIRE)
                        + " presence=" + panel.household().presence(at, level.getGameTime()));
            }
        });
        h.startSequence()
                .thenIdle(90)
                .thenExecute(() -> {
                    h.assertTrue(fireRelay.configure(owner, follow(RelayConditions.FIRE), fireRelay.revision()).applied(),
                            "setup: follow fire");
                    h.assertTrue(waterRelay.configure(owner, follow(RelayConditions.WATER), waterRelay.revision())
                            .applied(), "setup: follow water");
                })
                .thenIdle(160)
                .thenExecute(() -> {
                    h.assertTrue(fireRelay.output() == 0, "no fire yet: 0");
                    h.setBlock(campfire, Blocks.CAMPFIRE);
                })
                .thenIdle(120)
                .thenExecute(() -> {
                    h.assertTrue(fireRelay.output() == 15, "a lit campfire in reach: the fire line is high");
                    h.assertTrue(waterRelay.output() == 0, "and the water line knows nothing about fire");

                    final SecuritySyncPacket.RelayRow row = SecuritySyncPacket.of(panel, owner).relays().stream()
                            .filter(r -> r.pos().equals(fireRelay.getBlockPos())).findFirst().orElse(null);
                    h.assertTrue(row != null && row.subscribed() && row.output() == 15
                                    && row.contact() == RelayContact.LINKED.ordinal()
                                    && row.condition().equals(RelayConditions.FIRE.id())
                                    && row.truth() == com.createstats.security.Truth.TRUE.ordinal(),
                            "the panel's row must say what the block does: " + row);
                    final CompoundTag tag = fireRelay.getUpdateTag(level.registryAccess());
                    h.assertTrue(tag.getInt("Out") == 15 && tag.getBoolean("Powered")
                                    && tag.getInt("Contact") == RelayContact.LINKED.ordinal(),
                            "the client copy must say what the block does: " + tag);
                    h.assertTrue(level.getSignal(fireRelay.getBlockPos(), Direction.NORTH) == 15,
                            "and the neighbour behind it must be getting it");
                    h.setBlock(campfire, Blocks.STONE);
                })
                .thenIdle(140)
                .thenExecute(() -> {
                    h.assertTrue(fireRelay.output() == 0, "the fire is out and a clean sweep has passed: 0");
                    h.assertTrue(waterRelay.output() == 0, "the water line never moved");
                })
                .thenSucceed();
    }

    @GameTest(template = "fe_bench", timeoutTicks = 1500)
    public static void relayUnderAnActiveAutoBoard(GameTestHelper h) {
        final ServerLevel level = h.getLevel();
        final FakePlayer owner = fake(level, "relay_pdu_owner");
        final BlockPos pduRel = new BlockPos(3, 1, 3);
        h.setBlock(pduRel, ModBlocks.POWER_DISTRIBUTION_UNIT.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        final PowerDistributionUnitBE pdu = h.getBlockEntity(pduRel);
        final BlockPos relayRel = new BlockPos(10, 1, 10);
        final SecurityRelayBE relay = relay(h, relayRel, Direction.NORTH, owner);

        final BlockPos connRel = relayRel.above();
        h.setBlock(connRel, ModBlocks.POWER_CONNECTOR.get().defaultBlockState()
                .setValue(com.createstats.block.PowerConnectorBlock.FACING, Direction.UP));
        final PowerConnectorBE conn = h.getBlockEntity(connRel);
        final BlockPos spareRel = new BlockPos(6, 2, 1);
        h.setBlock(spareRel.below(), Blocks.STONE);
        h.setBlock(spareRel, ModBlocks.POWER_CONNECTOR.get().defaultBlockState()
                .setValue(com.createstats.block.PowerConnectorBlock.FACING, Direction.UP));
        final int leg = 2;
        pdu.linkStud(0, h.<PowerConnectorBE>getBlockEntity(spareRel), WireTier.COPPER);
        pdu.linkStud(leg, conn, WireTier.COPPER);
        pdu.setMode(PduMode.AUTO);
        final HomeSecurityPanelBE panel = panelBeside(h, relayRel, Direction.EAST, owner);

        final BlockPos panelRel = relayRel.relative(Direction.EAST);
        final Direction facing = pdu.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        final IEnergyStorage inCw = pdu.inputHandle(facing.getClockWise());
        final IEnergyStorage inCcw = pdu.inputHandle(facing.getCounterClockWise());
        final boolean[] supply = {true};
        final int[] trips = {0};
        h.onEachTick(() -> {
            if (supply[0]) {
                inCw.receiveEnergy(20_000, false);
                inCcw.receiveEnergy(20_000, false);
            }
            feed(h, panelRel);
            if (pdu.isLegTripped(leg)) {
                trips[0]++;
            }
        });
        h.startSequence()
                .thenIdle(160)
                .thenExecute(() -> {
                    h.assertTrue(relay.contact() == RelayContact.LINKED, "setup: linked, was " + relay.contact());
                    h.assertTrue(relay.configure(owner, follow(RelayConditions.MODE), relay.revision()).applied(),
                            "setup: follow Household mode = Home");
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    h.assertTrue(relay.powered(), "fed through the board, the relay runs");
                    h.assertTrue(relay.output() == 15, "and drives its line with the coil in");
                    h.assertTrue(relay.measuredDrawFePerTick() == 3, "standby plus coil, 3 FE/t, measured "
                            + relay.measuredDrawFePerTick());
                    relay.setManagedLock(FeLock.FORCE_OFF);
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    h.assertTrue(relay.output() == 0, "locked off at the board: nothing on the line");
                    relay.setManagedLock(FeLock.NONE);
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    h.assertTrue(relay.output() == 15, "unlocked: back on the line");

                    pdu.dropStudWires(leg);
                })
                .thenIdle(200)
                .thenExecute(() -> {
                    h.assertTrue(relay.output() == 0, "cut off from the board, its buffer runs out: no output");
                    h.assertFalse(relay.powered(), "and it says it has no power");
                    pdu.linkStud(leg, conn, WireTier.COPPER);
                })
                .thenIdle(200)
                .thenExecute(() -> {
                    h.assertTrue(relay.output() == 15, "wire restored: the relay comes back by itself");
                    h.assertTrue(trips[0] == 0, "the relay's leg tripped for " + trips[0] + " ticks");
                })
                .thenSucceed();
    }
}
