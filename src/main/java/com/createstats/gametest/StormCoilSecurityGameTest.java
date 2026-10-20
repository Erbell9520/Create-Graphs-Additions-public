package com.createstats.gametest;

import com.createstats.CreateStats;
import com.createstats.blockentity.HomeSecurityPanelBE;
import com.createstats.blockentity.StormCoilBE;
import com.createstats.registry.ModBlocks;
import com.createstats.security.SecurityEndpoint;
import com.createstats.security.SecurityHousehold;
import com.createstats.security.SecurityIdentity;
import com.createstats.security.SecurityMode;
import com.createstats.stormcoil.CoilRules;
import com.createstats.stormcoil.CoilRules.Grid;
import com.createstats.stormcoil.CoilRules.HouseState;
import com.createstats.stormcoil.CoilRules.Rule;
import com.createstats.stormcoil.StormCoilStuds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@GameTestHolder(CreateStats.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StormCoilSecurityGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("create_stats/gametest");

    private static final String STRANGER = "coil_stranger";
    private static final String MEMBER = "coil_member";

    private StormCoilSecurityGameTest() {
    }

    static ServerPlayer survivor(GameTestHelper h, String name, Vec3 at) {
        final ServerLevel level = h.getLevel();
        final FakePlayer p = SecurityIntegrityGameTest.fake(level, name);
        p.setGameMode(GameType.SURVIVAL);
        p.moveTo(at.x, at.y, at.z, 0f, 0f);
        level.addNewPlayer(p);
        return p;
    }

    @GameTest(template = "fe_bench", timeoutTicks = 1100)
    public static void governedCoilFollowsTheHouse(GameTestHelper h) {
        final ServerLevel level = h.getLevel();
        final boolean pvpWas = level.getServer().isPvpAllowed();
        level.getServer().setPvpAllowed(true);
        final FakePlayer owner = SecurityIntegrityGameTest.fake(level, "coil_sec_owner");
        final BlockPos panelRel = new BlockPos(2, 1, 4);
        final HomeSecurityPanelBE panel = SecurityIntegrityGameTest.panel(h, panelRel, owner);
        final BlockPos coilRel = new BlockPos(10, 1, 10);
        h.setBlock(coilRel, ModBlocks.STORM_COIL.get().defaultBlockState()
                .setValue(com.createstats.block.StormCoilBlock.FACING, Direction.NORTH));
        final StormCoilBE coil = h.getBlockEntity(coilRel);
        final BlockPos coilAbs = h.absolutePos(coilRel);
        final SecurityHousehold house = panel.household();
        final SecurityEndpoint at = SecurityIdentity.at(level, coilAbs);

        final ServerPlayer[] who = new ServerPlayer[2];
        final int[] arcsAt = {0, 0, 0};
        final long[] seenZap = {Long.MIN_VALUE};
        h.onEachTick(() -> {
            SecurityIntegrityGameTest.feed(h, panelRel);
            coil.portFor(StormCoilStuds.IN).receiveEnergy(50_000, false);
            for (ServerPlayer p : who) {
                if (p != null) {
                    p.setHealth(p.getMaxHealth());
                    p.clearFire();
                }
            }
            if (coil.lastZap() != seenZap[0]) {
                seenZap[0] = coil.lastZap();
                if (seenZap[0] != Long.MIN_VALUE) {
                    final String hit = coil.coilReading().lastArc();
                    arcsAt[STRANGER.equals(hit) ? 0 : MEMBER.equals(hit) ? 1 : 2]++;
                    LOG.info("[COIL-SEC-GT] t={} arc at {} | stranger {} member {} other {} | {} {}", h.getTick(), hit,
                            arcsAt[0], arcsAt[1], arcsAt[2], coil.houseState(),
                            coil.securityStatus());
                }
            }
        });

        final Rule hostile = new Rule(true, false, false, CoilRules.NO_MEMBERS);
        h.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    who[0] = survivor(h, STRANGER, Vec3.atBottomCenterOf(h.absolutePos(coilRel.north(4))));
                    who[1] = survivor(h, MEMBER, Vec3.atBottomCenterOf(h.absolutePos(coilRel.south(3))));
                    house.roster().add(who[1].getUUID(), MEMBER);
                    house.roster().setLevel(who[1].getUUID(), 2);
                    panel.link(coilAbs, owner);
                    h.assertTrue(panel.linkedFixtures().contains(coilAbs), "setup: linked by hand");
                })
                .thenIdle(180)
                .thenExecute(() -> {
                    h.assertTrue(coil.governed() && house.id().equals(coil.governingHousehold()),
                            "the hand-linked coil never took its panel (SEC-07's offer)");

                    house.setCoilRules(at, Grid.every(hostile).with(HouseState.AWAY, hostile.withStrangers(true))
                            .with(HouseState.AWAY_PLUS, hostile.withStrangers(true)));
                    house.setMode(SecurityMode.AWAY);
                    coil.repoll();
                    arcsAt[0] = arcsAt[1] = arcsAt[2] = 0;
                })
                .thenIdle(180)
                .thenExecute(() -> {
                    LOG.info("[COIL-SEC-GT] away: stranger {} member {}", arcsAt[0], arcsAt[1]);
                    h.assertTrue(arcsAt[0] >= 3, "in Away a coil set to arc at strangers struck the stranger "
                            + arcsAt[0] + " times in 9 s");
                    h.assertTrue(arcsAt[1] == 0, "in Away the coil arced at a member " + arcsAt[1]
                            + " times: no rule of this house names members");
                    house.setCoilOn(at, false);
                    coil.repoll();
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    h.assertTrue(coil.heldFromArcing(), "stood down at the panel, the coil does not say it is held");
                    arcsAt[0] = arcsAt[1] = arcsAt[2] = 0;
                })
                .thenIdle(120)
                .thenExecute(() -> {
                    h.assertTrue(arcsAt[0] + arcsAt[1] + arcsAt[2] == 0, "stood down, the coil still arced "
                            + (arcsAt[0] + arcsAt[1]) + " times");
                    house.setCoilOn(at, true);

                    house.setCoilRules(at, Grid.every(hostile).with(HouseState.LOCKDOWN,
                            new Rule(true, false, true, 3)));
                    coil.repoll();
                })
                .thenIdle(5)
                .thenExecute(() -> arcsAt[0] = arcsAt[1] = arcsAt[2] = 0)
                .thenIdle(120)
                .thenExecute(() -> {
                    h.assertTrue(arcsAt[0] + arcsAt[1] == 0, "in Away with no player rule the coil arced at a player "
                            + (arcsAt[0] + arcsAt[1]) + " times");
                    house.raiseLockdown(level.getGameTime(), "test intruder");
                    coil.repoll();
                })
                .thenIdle(160)
                .thenExecute(() -> {
                    LOG.info("[COIL-SEC-GT] lockdown: stranger {} member {}", arcsAt[0], arcsAt[1]);
                    h.assertTrue(arcsAt[1] >= 1, "in a Lockdown that arcs at members below 3 the member at 2 was never"
                            + " struck (stranger " + arcsAt[0] + ")");
                    h.assertTrue(coil.houseState() == HouseState.LOCKDOWN,
                            "the coil never read the lockdown");
                    house.clearLockdown();
                    for (ServerPlayer p : who) {
                        if (p != null) {
                            level.removePlayerImmediately(p, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                        }
                    }
                    level.getServer().setPvpAllowed(pvpWas);
                })
                .thenSucceed();
    }
}
