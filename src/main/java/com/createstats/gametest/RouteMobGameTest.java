package com.createstats.gametest;

import com.createstats.CreateStats;
import com.createstats.surveyor.UnseenRoutes;
import com.createstats.surveyor.core.PathSpline;
import com.createstats.surveyor.core.Pathway;
import com.createstats.surveyor.core.RigidMove;
import com.createstats.vccmk4.route.RouteSensors;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@GameTestHolder(CreateStats.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RouteMobGameTest {
    private RouteMobGameTest() {}

    @GameTest(template = "fe_bench", timeoutTicks = 20)
    public static void hostileSpeciesDoNotBlockTheDriver(GameTestHelper h) {
        PathSpline line = road(h);
        for (EntityType<? extends LivingEntity> type : List.of(EntityType.ZOMBIE, EntityType.CREEPER,
                EntityType.SKELETON, EntityType.SPIDER, EntityType.SLIME, EntityType.ENDERMAN)) {
            LivingEntity mob = spawn(h, type, 10);
            try {
                h.assertTrue(!Double.isFinite(sweep(h, line).distance()),
                        "hostile still makes the route brake: " + mob.getName().getString());
            } finally { mob.discard(); }
        }
        h.succeed();
    }

    @GameTest(template = "fe_bench", timeoutTicks = 20)
    public static void petsAnimalsVillagersAndPlayersStillBlockTheDriver(GameTestHelper h) {
        PathSpline line = road(h);
        for (EntityType<? extends LivingEntity> type : List.of(EntityType.CAT, EntityType.WOLF,
                EntityType.FOX, EntityType.COW, EntityType.VILLAGER, EntityType.IRON_GOLEM)) {
            LivingEntity mob = spawn(h, type, 10);
            try {
                h.assertTrue(Double.isFinite(sweep(h, line).distance()),
                        "protected creature disappeared from the obstacle sweep: " + mob.getName().getString());
                if (mob instanceof Wolf wolf) {
                    wolf.setRemainingPersistentAngerTime(200);
                    h.assertTrue(Double.isFinite(sweep(h, line).distance()), "an angry wolf must still be protected");
                }
            } finally { mob.discard(); }
        }
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "Road pedestrian"));
        BlockPos at = h.absolutePos(new BlockPos(10, 1, 12));
        player.setPos(at.getX() + .5, at.getY(), at.getZ() + .5);
        h.getLevel().addFreshEntity(player);
        try {
            RouteSensors.Obstacle obstacle = sweep(h, line);
            h.assertTrue(Double.isFinite(obstacle.distance()) && obstacle.player(), "player lost emergency obstacle protection");
        } finally { player.discard(); }
        h.succeed();
    }

    @GameTest(template = "fe_bench", timeoutTicks = 20)
    public static void aCloserHostileCannotHideACatOrABlock(GameTestHelper h) {
        PathSpline line = road(h);
        LivingEntity hostile = spawn(h, EntityType.ZOMBIE, 8);
        LivingEntity cat = spawn(h, EntityType.CAT, 14);
        try {
            RouteSensors.Obstacle obstacle = sweep(h, line);
            h.assertTrue(obstacle.what().equals(cat.getName().getString()), "closer hostile hid the cat ahead: " + obstacle.what());
            cat.discard();

            h.setBlock(new BlockPos(14, 2, 11), Blocks.STONE);
            h.assertTrue(sweep(h, line).block(), "ignoring a hostile must never hide a solid block behind it");
        } finally { cat.discard(); hostile.discard(); }
        h.succeed();
    }

    @GameTest(template = "fe_bench", timeoutTicks = 20)
    public static void aHostileInTheReturnSpotDoesNotHoldUnseenTravel(GameTestHelper h) throws Exception {
        road(h);
        LivingEntity hostile = spawn(h, EntityType.ZOMBIE, 10);
        try {
            Clearance clearance = returnClearance(h);
            h.assertTrue(!clearance.reason().isEmpty(), "return must still reject an occupied assembly footprint");
            h.assertTrue(!clearance.road(), "a hostile must not hold the unseen driver waiting for it to leave");
        } finally { hostile.discard(); }
        h.succeed();
    }

    @GameTest(template = "fe_bench", timeoutTicks = 20)
    public static void returnClearanceKeepsPetsProtectedInMixedGroups(GameTestHelper h) throws Exception {
        road(h);
        LivingEntity hostile = spawn(h, EntityType.ZOMBIE, 10);
        for (EntityType<? extends LivingEntity> type : List.of(EntityType.CAT, EntityType.WOLF, EntityType.FOX)) {
            LivingEntity pet = spawn(h, type, 10);
            try {
                Clearance clearance = returnClearance(h);
                h.assertTrue(clearance.road() && clearance.reason().startsWith(pet.getName().getString()),
                        "return clearance hid a pet behind a hostile: " + clearance);
            } finally { pet.discard(); }
        }
        hostile.discard();
        h.succeed();
    }

    @GameTest(template = "fe_bench", timeoutTicks = 20)
    public static void aHostileAtTheTractorCannotHideAPetAtTheTrailer(GameTestHelper h) throws Exception {
        road(h);
        LivingEntity hostile = spawn(h, EntityType.ZOMBIE, 10);
        LivingEntity cat = spawn(h, EntityType.CAT, 6);
        try {
            Clearance clearance = returnClearance(h, true);
            h.assertTrue(clearance.road() && clearance.reason().startsWith(cat.getName().getString()),
                    "an earlier hostile body obstruction hid the trailer's pet: " + clearance);
        } finally { cat.discard(); hostile.discard(); }
        h.succeed();
    }

    private static PathSpline road(GameTestHelper h) {
        for (int x = 1; x <= 22; x++) for (int z = 11; z <= 13; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        BlockPos a = h.absolutePos(new BlockPos(2, 1, 12));
        BlockPos b = h.absolutePos(new BlockPos(22, 1, 12));
        return PathSpline.of(Pathway.create(UUID.randomUUID(), "Mob road", 3, UUID.randomUUID()).appendAll(List.of(
                new double[]{a.getX() + .5, a.getY(), a.getZ() + .5},
                new double[]{b.getX() + .5, b.getY(), b.getZ() + .5})));
    }

    private static LivingEntity spawn(GameTestHelper h, EntityType<? extends LivingEntity> type, int x) {
        LivingEntity mob = type.create(h.getLevel());
        if (mob == null) throw new IllegalStateException("could not create " + type);
        if (mob instanceof Mob m) m.setNoAi(true);
        BlockPos at = h.absolutePos(new BlockPos(x, 1, 12));
        mob.setPos(at.getX() + .5, at.getY(), at.getZ() + .5);
        h.getLevel().addFreshEntity(mob);
        return mob;
    }

    private static RouteSensors.Obstacle sweep(GameTestHelper h, PathSpline line) {
        return RouteSensors.sweep(h.getLevel(), line, 0, 2, 1.5, 20, List.of(), List.of());
    }

    private record Clearance(String reason, boolean road) {}

    private static Clearance returnClearance(GameTestHelper h) throws Exception {
        return returnClearance(h, false);
    }

    private static Clearance returnClearance(GameTestHelper h, boolean trailer) throws Exception {
        Class<?> type = Class.forName("com.createstats.surveyor.UnseenRoutes$Vehicle");
        var constructor = type.getDeclaredConstructor(UUID.class); constructor.setAccessible(true);
        Object vehicle = constructor.newInstance(UUID.randomUUID());
        BlockPos at = h.absolutePos(new BlockPos(10, 1, 12));
        double x = at.getX() + .5, y = at.getY(), z = at.getZ() + .5;
        UUID body = UUID.randomUUID(), other = UUID.randomUUID();
        field(type, vehicle, "chain", trailer ? List.of(body, other) : List.of(body));
        field(type, vehicle, "moving", trailer ? Set.of(body, other) : Set.of(body));
        double[] tractor = {x - 2, y, z - 1.5, x + 2, y + 3, z + 1.5};
        double[] towed = {x - 6, y, z - 1.5, x - 2, y + 3, z + 1.5};
        field(type, vehicle, "chainBounds", trailer ? List.of(tractor, towed) : List.of(tractor));
        if (trailer) field(type, vehicle, "rear", 6.0);
        field(type, vehicle, "parkX", x); field(type, vehicle, "parkY", y); field(type, vehicle, "parkZ", z);
        field(type, vehicle, "parkHeading", Math.PI / 2);
        var method = UnseenRoutes.class.getDeclaredMethod("blockedAt", net.minecraft.server.level.ServerLevel.class, type, RigidMove.class);
        method.setAccessible(true);
        Object result = method.invoke(null, h.getLevel(), vehicle, RigidMove.NONE);
        var reason = result.getClass().getDeclaredMethod("reason"); reason.setAccessible(true);
        var road = result.getClass().getDeclaredMethod("road"); road.setAccessible(true);
        return new Clearance((String) reason.invoke(result), (boolean) road.invoke(result));
    }

    private static void field(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
}
