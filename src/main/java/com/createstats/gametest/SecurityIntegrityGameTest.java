package com.createstats.gametest;

import com.createstats.CreateStats;
import com.createstats.block.DoorMaterial;
import com.createstats.block.GarageDoorHeaderBlock;
import com.createstats.block.GarageDoorSite;
import com.createstats.block.HomeSecurityPanelBlock;
import com.createstats.block.PoweredDoorBlock;
import com.createstats.block.SecuritySensorBlock;
import com.createstats.blockentity.HomeSecurityPanelBE;
import com.createstats.blockentity.PoweredDoorBE;
import com.createstats.blockentity.SecuritySensorBE;
import com.createstats.power.SecurityDevice;
import com.createstats.registry.ModBlocks;
import com.createstats.registry.ModItems;
import com.createstats.security.SecurityEndpoint;
import com.createstats.security.SecurityHousehold;
import com.createstats.security.SecurityHouseholds;
import com.createstats.security.SecurityIdentity;
import com.createstats.security.SecurityRole;
import com.createstats.security.SensorKind;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@GameTestHolder(CreateStats.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SecurityIntegrityGameTest {
    private SecurityIntegrityGameTest() {
    }

    static FakePlayer fake(ServerLevel level, String name) {
        return FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), name));
    }

    static HomeSecurityPanelBE panel(GameTestHelper h, BlockPos rel, FakePlayer owner) {
        h.setBlock(rel.north(), Blocks.STONE);
        h.setBlock(rel, ModBlocks.HOME_SECURITY_PANEL.get().defaultBlockState()
                .setValue(HomeSecurityPanelBlock.FACING, Direction.NORTH));
        final HomeSecurityPanelBE p = h.getBlockEntity(rel);
        if (owner != null) {
            p.claim(owner);
            p.household().setConfigured(true);
        }
        return p;
    }

    static SecuritySensorBE sensor(GameTestHelper h, BlockPos rel, SensorKind kind) {
        h.setBlock(rel.below(), Blocks.STONE);
        h.setBlock(rel, ModBlocks.securitySensor(kind).get().defaultBlockState()
                .setValue(SecuritySensorBlock.FACING, Direction.DOWN));
        return h.getBlockEntity(rel);
    }

    static PoweredDoorBE door(GameTestHelper h, BlockPos rel) {
        h.setBlock(rel.below(), Blocks.STONE);
        final BlockState lower = ModBlocks.poweredDoor(DoorMaterial.IRON).get().defaultBlockState()
                .setValue(PoweredDoorBlock.FACING, Direction.NORTH)
                .setValue(PoweredDoorBlock.HINGE, DoorHingeSide.LEFT)
                .setValue(PoweredDoorBlock.OPEN, false)
                .setValue(PoweredDoorBlock.HALF, DoubleBlockHalf.LOWER);
        h.setBlock(rel, lower);
        h.setBlock(rel.above(), lower.setValue(PoweredDoorBlock.HALF, DoubleBlockHalf.UPPER));
        return h.getBlockEntity(rel);
    }

    static void feed(GameTestHelper h, BlockPos rel) {
        final IEnergyStorage e = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,
                h.absolutePos(rel), null);
        if (e != null) {
            e.receiveEnergy(1_000, false);
        }
    }

    static void use(GameTestHelper h, FakePlayer who, ItemStack stack, BlockPos rel) {
        who.setItemInHand(InteractionHand.MAIN_HAND, stack);
        final BlockPos abs = h.absolutePos(rel);
        stack.getItem().onItemUseFirst(stack, new UseOnContext(who, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false)));
    }

    static ItemStack card() {
        return new ItemStack(ModItems.SWITCH_LINK_CARD.get(), 16);
    }

    @GameTest(template = "fe_bench", timeoutTicks = 100)
    public static void sec01StrangerCardIsRefused(GameTestHelper h) {
        final ServerLevel level = h.getLevel();
        final FakePlayer owner = fake(level, "sec01_owner");
        final FakePlayer stranger = fake(level, "sec01_stranger");
        final BlockPos panelRel = new BlockPos(2, 1, 4);
        final HomeSecurityPanelBE p = panel(h, panelRel, owner);
        final BlockPos linkedRel = new BlockPos(10, 1, 10);
        final BlockPos otherRel = new BlockPos(14, 1, 10);
        sensor(h, linkedRel, SensorKind.MOTION);
        sensor(h, otherRel, SensorKind.MOTION);
        p.link(h.absolutePos(linkedRel), owner);
        h.assertTrue(p.linkedFixtures().contains(h.absolutePos(linkedRel)), "setup: owner links a sensor");

        final ItemStack card = card();
        use(h, stranger, card, panelRel);
        use(h, stranger, card, linkedRel);
        h.assertTrue(p.linkedFixtures().contains(h.absolutePos(linkedRel)),
                "SEC-01: a stranger's card unlinked a device from an owned panel");

        use(h, stranger, card, panelRel);
        use(h, stranger, card, otherRel);
        h.assertFalse(p.linkedFixtures().contains(h.absolutePos(otherRel)),
                "SEC-01: a stranger's card linked a device to an owned panel");
        h.succeed();
    }

    @GameTest(template = "fe_bench", timeoutTicks = 100)
    public static void sec01RoleRevokedBetweenClicks(GameTestHelper h) {
        final ServerLevel level = h.getLevel();
        final FakePlayer owner = fake(level, "sec01b_owner");
        final FakePlayer admin = fake(level, "sec01b_admin");
        final BlockPos panelRel = new BlockPos(2, 1, 4);
        final HomeSecurityPanelBE p = panel(h, panelRel, owner);
        final SecurityHousehold house = p.household();
        house.roster().add(admin.getUUID(), "sec01b_admin");
        house.roster().setRole(admin.getUUID(), SecurityRole.ADMIN);
        final BlockPos targetRel = new BlockPos(10, 1, 10);
        sensor(h, targetRel, SensorKind.MOTION);

        final ItemStack card = card();
        use(h, admin, card, panelRel);
        house.roster().setRole(admin.getUUID(), SecurityRole.MEMBER);
        use(h, admin, card, targetRel);
        h.assertFalse(p.linkedFixtures().contains(h.absolutePos(targetRel)),
                "SEC-01: a card held while admin still linked after the holder was demoted");
        h.succeed();
    }

    @GameTest(template = "fe_bench", timeoutTicks = 100)
    public static void sec01ReplacedHostIsRefused(GameTestHelper h) {
        final ServerLevel level = h.getLevel();
        final FakePlayer owner = fake(level, "sec01c_owner");
        final FakePlayer other = fake(level, "sec01c_other");
        final BlockPos panelRel = new BlockPos(2, 1, 4);
        panel(h, panelRel, owner);
        final BlockPos targetRel = new BlockPos(10, 1, 10);
        sensor(h, targetRel, SensorKind.MOTION);

        final ItemStack card = card();
        use(h, owner, card, panelRel);

        h.setBlock(panelRel, Blocks.AIR);
        final HomeSecurityPanelBE replacement = panel(h, panelRel, other);
        replacement.household().roster().add(owner.getUUID(), "sec01c_owner");
        replacement.household().roster().setRole(owner.getUUID(), SecurityRole.ADMIN);
        use(h, owner, card, targetRel);
        h.assertFalse(replacement.linkedFixtures().contains(h.absolutePos(targetRel)),
                "SEC-01: a card held on one household's panel linked into its replacement's household");
        h.succeed();
    }

    @GameTest(template = "fe_bench", timeoutTicks = 100)
    public static void sec03UnloadKeepsRegistration(GameTestHelper h) {
        final ServerLevel level = h.getLevel();
        final FakePlayer owner = fake(level, "sec03_owner");
        final SecurityHouseholds store = SecurityHouseholds.of(level);
        final HomeSecurityPanelBE asleep = panel(h, new BlockPos(2, 1, 4), owner);
        final SecurityHousehold sleeping = asleep.household();

        asleep.onChunkUnloaded();
        asleep.setRemoved();
        h.assertFalse(sleeping.panels().isEmpty(),
                "SEC-03: unloading a panel's chunk deregistered it from its household");
        h.assertFalse(store.removableBy(owner.getUUID(), false).contains(sleeping),
                "SEC-03: a household whose panel is only asleep was offered for deletion");
        h.assertTrue(store.find(sleeping.id()) != null, "SEC-03: the household record went away");

        final FakePlayer owner2 = fake(level, "sec03_owner2");
        final BlockPos goneRel = new BlockPos(8, 1, 4);
        final HomeSecurityPanelBE gone = panel(h, goneRel, owner2);
        final SecurityHousehold orphan = gone.household();
        h.setBlock(goneRel, Blocks.AIR);
        h.assertTrue(orphan.panels().isEmpty(), "a panel really removed still deregisters");
        h.assertTrue(store.removableBy(owner2.getUUID(), false).contains(orphan),
                "a household whose last panel was really removed is still recoverable by its owner");
        h.succeed();
    }

    @GameTest(template = "fe_bench", timeoutTicks = 400)
    public static void sec04RemovedSensorStaysAFault(GameTestHelper h) {
        final ServerLevel level = h.getLevel();
        final FakePlayer owner = fake(level, "sec04_owner");
        final BlockPos panelRel = new BlockPos(2, 1, 4);
        final BlockPos sensorRel = new BlockPos(3, 1, 4);
        final HomeSecurityPanelBE p = panel(h, panelRel, owner);
        sensor(h, sensorRel, SensorKind.MOTION);
        final boolean[] sensorStands = {true};
        h.onEachTick(() -> {
            feed(h, panelRel);
            if (sensorStands[0]) {
                feed(h, sensorRel);
            }
        });
        h.startSequence()
                .thenIdle(120)
                .thenExecute(() -> {
                    h.assertTrue(p.unionDevices().stream().anyMatch(d -> d.pos().equals(h.absolutePos(sensorRel))),
                            "setup: the panel lists its sensor");
                    h.assertFalse(p.hasFault(), "setup: a powered sensor is no fault");
                    sensorStands[0] = false;
                    h.setBlock(sensorRel, Blocks.AIR);
                })
                .thenIdle(200)
                .thenExecute(() -> h.assertTrue(p.hasFault(),
                        "SEC-04: a removed sensor vanished from the fault count instead of staying missing"))
                .thenSucceed();
    }

    @GameTest(template = "fe_bench", timeoutTicks = 400)
    public static void sec04OverflowKeepsCoverage(GameTestHelper h) {
        final ServerLevel level = h.getLevel();
        final FakePlayer owner = fake(level, "sec04b_owner");
        final BlockPos panelRel = new BlockPos(1, 1, 1);
        final HomeSecurityPanelBE p = panel(h, panelRel, owner);
        final List<BlockPos> sensors = new ArrayList<>();
        for (int x = 2; x <= 14; x++) {
            for (int z = 1; z <= 10; z++) {
                final BlockPos rel = new BlockPos(x, 1, z);
                sensor(h, rel, SensorKind.MOTION);
                sensors.add(rel);
            }
        }

        final BlockPos blind = sensors.stream()
                .max(Comparator.comparingLong(rel -> h.absolutePos(rel).asLong())).orElseThrow();
        h.onEachTick(() -> {
            feed(h, panelRel);
            for (BlockPos rel : sensors) {
                if (!rel.equals(blind)) {
                    feed(h, rel);
                }
            }
        });
        h.startSequence()
                .thenIdle(160)
                .thenExecute(() -> h.assertTrue(p.hasFault(),
                        "SEC-04: a blind sensor past the 128 row cap was missing from the fault count"))
                .thenSucceed();
    }

    @GameTest(template = "fe_bench", timeoutTicks = 500)
    public static void sec05ReplacementPanelGetsNoRoster(GameTestHelper h) {
        final ServerLevel level = h.getLevel();
        final FakePlayer owner = fake(level, "sec05_owner");
        final FakePlayer stranger = fake(level, "sec05_stranger");
        final BlockPos panelRel = new BlockPos(2, 1, 4);
        final BlockPos doorRel = new BlockPos(3, 1, 4);
        panel(h, panelRel, owner);
        final PoweredDoorBE d = door(h, doorRel);
        h.onEachTick(() -> {
            feed(h, panelRel);
            feed(h, doorRel);
        });
        h.startSequence()
                .thenIdle(120)
                .thenExecute(() -> {
                    h.assertTrue(d.governed(), "setup: the door answers to the owner's panel");
                    h.assertFalse(d.mayOperate(stranger), "setup: the stranger may not open it");
                    h.setBlock(panelRel, Blocks.AIR);
                    panel(h, panelRel, stranger);
                })
                .thenIdle(160)
                .thenExecute(() -> h.assertFalse(d.mayOperate(stranger),
                        "SEC-05: a replacement panel of another household handed the door its roster"))
                .thenSucceed();
    }

    @GameTest(template = "fe_bench", timeoutTicks = 400)
    public static void sec06SleepingGovernorHidesDevice(GameTestHelper h) {
        final ServerLevel level = h.getLevel();
        final FakePlayer ownerA = fake(level, "sec06_a");
        final FakePlayer ownerB = fake(level, "sec06_b");
        final BlockPos aRel = new BlockPos(2, 1, 4);
        final BlockPos sensorRel = new BlockPos(3, 1, 4);
        final BlockPos bRel = new BlockPos(5, 1, 4);
        final BlockPos bridgeRel = new BlockPos(4, 1, 4);
        final HomeSecurityPanelBE a = panel(h, aRel, ownerA);
        final SecuritySensorBE s = sensor(h, sensorRel, SensorKind.MOTION);
        sensor(h, bridgeRel, SensorKind.MOTION);
        final HomeSecurityPanelBE b = panel(h, bRel, ownerB);
        h.onEachTick(() -> {
            feed(h, aRel);
            feed(h, bRel);
            feed(h, sensorRel);
            feed(h, bridgeRel);
        });
        h.startSequence()
                .thenIdle(120)
                .thenExecute(() -> {
                    h.assertTrue(h.absolutePos(aRel).equals(s.governingPanel()),
                            "setup: the sensor answers to the nearer panel, household A");
                    b.rebuild();
                    h.assertFalse(b.devices().stream().anyMatch(d -> d.pos().equals(h.absolutePos(sensorRel))),
                            "setup: household B does not list A's sensor while A's panel is awake");

                    setPanelPos(s, new BlockPos(29_000_000, 64, 29_000_000));
                    b.rebuild();
                    h.assertFalse(b.devices().stream().anyMatch(d -> d.pos().equals(h.absolutePos(sensorRel))),
                            "SEC-06: household B listed A's sensor while A's panel was asleep");
                })
                .thenSucceed();
    }

    private static void setPanelPos(SecuritySensorBE sensor, BlockPos far) {
        try {
            final java.lang.reflect.Field f = SecuritySensorBE.class.getDeclaredField("panelPos");
            f.setAccessible(true);
            f.set(sensor, far);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @GameTest(template = "fe_bench", timeoutTicks = 500)
    public static void sec07ManualLinkGoverns(GameTestHelper h) {
        final ServerLevel level = h.getLevel();
        final FakePlayer owner = fake(level, "sec07_owner");
        final BlockPos panelRel = new BlockPos(2, 1, 4);
        final BlockPos sensorRel = new BlockPos(16, 1, 16);
        final HomeSecurityPanelBE p = panel(h, panelRel, owner);
        final SecuritySensorBE s = sensor(h, sensorRel, SensorKind.MOTION);
        h.onEachTick(() -> {
            feed(h, panelRel);
            feed(h, sensorRel);
        });
        h.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    h.assertFalse(s.governed(), "setup: a sensor on its own circuit answers to nobody");
                    p.link(h.absolutePos(sensorRel), owner);
                    h.assertTrue(p.linkedFixtures().contains(h.absolutePos(sensorRel)), "setup: linked by hand");
                })
                .thenIdle(160)
                .thenExecute(() -> {
                    h.assertTrue(s.governed(), "SEC-07: a hand-linked sensor never took its panel's policy");
                    p.setSensorRadius(h.absolutePos(sensorRel), 9, owner);
                })
                .thenIdle(100)
                .thenExecute(() -> h.assertTrue(s.radius() == 9,
                        "SEC-07: a range set on the panel never reached the hand-linked sensor"))
                .thenSucceed();
    }

    @GameTest(template = "fe_bench", timeoutTicks = 400)
    public static void sec09GarageNorth(GameTestHelper h) {
        sec09CutWireKeepsGarageSettings(h, Direction.NORTH);
    }

    @GameTest(template = "fe_bench", timeoutTicks = 400)
    public static void sec09GarageSouth(GameTestHelper h) {
        sec09CutWireKeepsGarageSettings(h, Direction.SOUTH);
    }

    @GameTest(template = "fe_bench", timeoutTicks = 400)
    public static void sec09GarageEast(GameTestHelper h) {
        sec09CutWireKeepsGarageSettings(h, Direction.EAST);
    }

    @GameTest(template = "fe_bench", timeoutTicks = 400)
    public static void sec09GarageWest(GameTestHelper h) {
        sec09CutWireKeepsGarageSettings(h, Direction.WEST);
    }

    static void sec09CutWireKeepsGarageSettings(GameTestHelper h, Direction facing) {
        final ServerLevel level = h.getLevel();
        final FakePlayer owner = fake(level, "sec09_owner_" + facing.getName());
        final BlockPos panelAt = h.absolutePos(new BlockPos(12, 3, 12));
        level.setBlock(panelAt.north(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(panelAt, ModBlocks.HOME_SECURITY_PANEL.get().defaultBlockState()
                .setValue(HomeSecurityPanelBlock.FACING, Direction.NORTH), 3);
        final HomeSecurityPanelBE p = (HomeSecurityPanelBE) level.getBlockEntity(panelAt);
        p.claim(owner);
        p.household().setConfigured(true);
        final SecurityHousehold house = p.household();

        final BlockPos wire = panelAt.east();
        level.setBlock(wire.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(wire, ModBlocks.securitySensor(SensorKind.MOTION).get().defaultBlockState()
                .setValue(SecuritySensorBlock.FACING, Direction.DOWN), 3);

        final Direction along = GarageDoorSite.widthAxis(facing).getAxis() == Direction.Axis.X
                ? Direction.EAST : Direction.SOUTH;
        final List<BlockPos> hoods = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            final BlockPos hood = wire.east().relative(along, i);
            level.setBlock(hood.below(2), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(hood, ModBlocks.GARAGE_DOOR_HEADER.get().defaultBlockState()
                    .setValue(GarageDoorHeaderBlock.FACING, facing)
                    .setValue(GarageDoorHeaderBlock.CONTROLLER, false), 3);
            hoods.add(hood);
        }
        GarageDoorSite.form(level, hoods.get(0));
        final BlockPos controller = hoods.stream()
                .filter(pos -> level.getBlockState(pos).getValue(GarageDoorHeaderBlock.CONTROLLER))
                .findFirst().orElse(null);
        h.assertTrue(controller != null, "setup: the bay formed and elected a controller");
        final SecurityEndpoint anchor = SecurityIdentity.anchorOf(level, controller);
        final String where = facing.getName() + ", controller " + controller.toShortString() + ", anchor "
                + BlockPos.of(anchor.pos()).toShortString();

        final boolean[] wireStands = {true};
        h.onEachTick(() -> {
            final IEnergyStorage e = level.getCapability(Capabilities.EnergyStorage.BLOCK, panelAt, null);
            if (e != null) {
                e.receiveEnergy(1_000, false);
            }
            if (wireStands[0]) {
                final IEnergyStorage s = level.getCapability(Capabilities.EnergyStorage.BLOCK, wire, null);
                if (s != null) {
                    s.receiveEnergy(1_000, false);
                }
            }
        });
        h.startSequence()
                .thenIdle(80)
                .thenExecute(() -> {
                    h.assertTrue(p.unionDevices().stream().anyMatch(d -> d.pos().equals(controller)),
                            "setup: the panel lists the bay (" + where + ")");
                    house.setRequiredLevel(anchor, 3);
                    house.setName(anchor, "Bay " + facing.getName());
                    h.assertTrue(house.requiredLevel(anchor) == 3, "setup: the bay's clearance is set");
                    wireStands[0] = false;
                    level.setBlock(wire, Blocks.AIR.defaultBlockState(), 3);
                })
                .thenIdle(160)
                .thenExecute(() -> {
                    final BlockPos anchorAt = BlockPos.of(anchor.pos());
                    h.assertTrue(p.unionDevices().stream().anyMatch(d -> d.pos().equals(anchorAt)
                                    && d.state() == SecurityDevice.DeviceState.OFFLINE),
                            "setup: with its wire mined the bay is out of reach and listed missing (" + where + ")");
                    h.assertTrue(level.getBlockEntity(controller) != null, "setup: the bay is still standing");
                    h.assertTrue(house.requiredLevel(anchor) == 3 && house.hasName(anchor),
                            "SEC-09: a garage bay's settings were pruned when its wire was cut (" + where + ")");
                })
                .thenSucceed();
    }
}
