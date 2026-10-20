package com.createstats.repeller;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RepellerWiringTest {
    private static final Path HOOKS = Path.of("src/main/java/com/createstats/repeller/RepellerSpawnHooks.java");
    private static final Path BE = Path.of("src/main/java/com/createstats/blockentity/MobRepellerBE.java");
    private static final Path MOB_HOOKS = Path.of("src/main/java/com/createstats/repeller/RepellerMobHooks.java");
    private static final Path GOAL = Path.of("src/main/java/com/createstats/repeller/LeaveVolumeGoal.java");
    private static final Path MIXINS = Path.of("src/main/resources/create_stats.mixins.json");

    private static String code(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8)
                .replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ");
    }

    private static String body(String source, String signature) {
        final int at = source.indexOf(signature);
        assertTrue(at >= 0, "method not found, so this test now guards nothing: " + signature);
        final int open = source.indexOf('{', at);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            final char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return source.substring(open + 1, i);
            }
        }
        throw new IllegalStateException("unbalanced braces after " + signature);
    }

    private static void fastPathFirst(String body, String hook) {
        final String first = body.trim();
        assertTrue(first.startsWith("if (!RepellerIndex.anyActive()"),
                hook + " must ask RepellerIndex.anyActive() before anything else:\n" + first);
    }

    @Test
    void theNaturalHookRefusesNaturalAndChunkGenerationOnly() throws IOException {
        final String b = body(code(HOOKS), "void onPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event)");
        fastPathFirst(b, "onPlacementCheck");
        assertTrue(b.contains("MobSpawnType.NATURAL") && b.contains("MobSpawnType.CHUNK_GENERATION"), b);
        assertTrue(!b.contains("MobSpawnType.SPAWNER") && !b.contains("MobSpawnType.TRIAL_SPAWNER"),
                "spawners must stay untouched: " + b);
        assertTrue(b.contains("SpawnPlacementCheck.Result.FAIL"), b);
    }

    @Test
    void theFinalizeHookCancelsTheSpawnAndTheEvent() throws IOException {
        final String b = body(code(HOOKS), "void onFinalize(FinalizeSpawnEvent event)");
        fastPathFirst(b, "onFinalize");
        assertTrue(b.contains("MobSpawnType.PATROL") && b.contains("MobSpawnType.NATURAL"), b);
        assertTrue(!b.contains("MobSpawnType.SPAWNER") && !b.contains("MobSpawnType.EVENT"), b);
        assertTrue(b.contains("setSpawnCancelled(true)"), "without it the mob still joins the level: " + b);
        assertTrue(b.contains("setCanceled(true)"), "without it finalizeSpawn still runs its side effects: " + b);
    }

    @Test
    void thePhantomHookDeniesAPlayerStandingInAColumn() throws IOException {
        final String b = body(code(HOOKS), "void onPhantoms(PlayerSpawnPhantomsEvent event)");
        assertTrue(b.trim().startsWith("if (!RepellerIndex.anyActive()"), b);
        assertTrue(b.contains("EntityType.PHANTOM") && b.contains("PlayerSpawnPhantomsEvent.Result.DENY"), b);
    }

    @Test
    void everyServerMobIsGivenTheLeaveGoalAsItJoins() throws IOException {
        final String b = body(code(MOB_HOOKS), "void onJoin(EntityJoinLevelEvent event)");
        assertTrue(b.contains("isClientSide()"), "goals run on the server; a client copy needs none: " + b);
        assertTrue(b.contains("goalSelector.addGoal(LeaveVolumeGoal.PRIORITY, new LeaveVolumeGoal("),
                "without it no mob already in the world ever leaves: " + b);
    }

    @Test
    void aTargetInsideIsRefusedByANullTargetNeverByCancelling() throws IOException {
        final String b = body(code(MOB_HOOKS), "void onTarget(LivingChangeTargetEvent event)");
        fastPathFirst(b, "onTarget");
        assertTrue(b.contains("setNewAboutToBeSetTarget(null)"), b);
        assertTrue(!b.contains("setCanceled("),
                "Mob.setTarget keeps the old target when the event is cancelled, so a cancel drops nothing: " + b);
        assertTrue(b.contains("LeaveVolumeGoal.exempt(mob)"), "a farm's spawner mobs must still fight: " + b);
        assertTrue(b.contains("mob.getX()") && b.contains("target.getX()"),
                "refused when the mob stands inside and when its target does: " + b);
    }

    @Test
    void theLeaveGoalOutranksEveryGoalButLetsAMobFloat() throws IOException {
        final String goal = code(GOAL);
        final Matcher m = Pattern.compile("int PRIORITY = (-?\\d+);").matcher(goal);
        assertTrue(m.find(), "PRIORITY not found");
        assertTrue(Integer.parseInt(m.group(1)) < 0, "vanilla's FloatGoal sits at 0; leaving must outrank it");
        final String ctor = body(goal, "public LeaveVolumeGoal(Mob mob)");
        assertTrue(ctor.contains("Goal.Flag.MOVE") && ctor.contains("Goal.Flag.LOOK"), ctor);
        assertTrue(!ctor.contains("Goal.Flag.JUMP"), "taking the jump flag stops FloatGoal and drowns mobs: " + ctor);
        fastPathFirst(body(goal, "public boolean canUse()"), "LeaveVolumeGoal.canUse");
        assertTrue(body(goal, "private void dropTarget()").contains("setSwellDir(-1)"),
                "a creeper whose swell goal was pre-empted burns on to the explosion");
    }

    @Test
    void spawnerMobsAndRaidsAreLeftAloneAsTheTooltipPromises() throws IOException {
        final String b = body(code(GOAL), "public static boolean exempt(Mob mob)");
        assertTrue(b.contains("MobSpawnType.SPAWNER") && b.contains("MobSpawnType.TRIAL_SPAWNER"), b);
        assertTrue(b.contains("hasActiveRaid()"), b);
    }

    @Test
    void thePhantomAccessorIsAppliedOnTheServer() throws IOException {
        final String json = Files.readString(MIXINS, StandardCharsets.UTF_8);
        final Matcher common = Pattern.compile("\"mixins\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL).matcher(json);
        assertTrue(common.find(), "no common mixins list");
        assertTrue(common.group(1).contains("\"PhantomAccessor\""),
                "unapplied, the goal's cast to PhantomAccessor throws on the first phantom that leaves");
    }

    @Test
    void theBlockEntityJoinsItsGridAndLeavesTheIndexWhenItGoes() throws IOException {
        final String be = code(BE);
        assertTrue(body(be, "public void tick()").contains("FeWireNetwork.buildFrom(this)"),
                "PR-02: the tick must build its own grid, or it is solved only when a neighbour ticks (KI-46)");
        assertTrue(body(be, "public void invalidate()").contains("RepellerIndex.remove("),
                "invalidate runs on unload as well as break; the index must forget the repeller there");
        final String index = body(be, "private void updateIndex(");
        assertTrue(index.contains("EntityType.PHANTOM") && index.contains("RepellerShape.COLUMN"),
                "phantoms are refused in a column, never a sphere: " + index);
    }
}
