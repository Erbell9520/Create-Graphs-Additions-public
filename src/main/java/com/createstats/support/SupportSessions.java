package com.createstats.support;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.createstats.CreateStats;
import com.createstats.support.SupportPolicy.Decision;
import com.createstats.support.SupportPolicy.Facts;
import com.createstats.support.SupportPolicy.Level;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;
import org.jetbrains.annotations.Nullable;

public final class SupportSessions {
    public static final PermissionNode<Boolean> NODE_TIER1 = new PermissionNode<>(
        CreateStats.MOD_ID, "support.tier1", PermissionTypes.BOOLEAN, (player, uuid, context) -> false);
    public static final PermissionNode<Boolean> NODE_TIER2 = new PermissionNode<>(
        CreateStats.MOD_ID, "support.tier2", PermissionTypes.BOOLEAN, (player, uuid, context) -> false);

    private static final long CHALLENGE_LIFETIME_MS = 30_000;

    private static final long REQUEST_INTERVAL_MS = 750;
    private static final long EXPORT_INTERVAL_MS = 5_000;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Map<UUID, Grant> GRANTS = new ConcurrentHashMap<>();
    private static final Set<UUID> VERIFIED = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Challenge> CHALLENGES = new ConcurrentHashMap<>();
    private static final Map<UUID, Journal> JOURNALS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_REQUEST = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_EXPORT = new ConcurrentHashMap<>();

    private SupportSessions() {
    }

    public static Decision access(@Nullable ServerPlayer player) {
        if (player == null) {
            return Decision.NONE;
        }
        MinecraftServer server = player.getServer();
        boolean online = server != null && server.usesAuthentication();
        boolean authority = server != null
            && (server.isSingleplayerOwner(player.getGameProfile()) || player.hasPermissions(2));
        return SupportPolicy.decide(new Facts(SupportAccess.isCreator(player), online,
            VERIFIED.contains(player.getUUID()), authority, grantedLevel(player)));
    }

    private static Level grantedLevel(ServerPlayer player) {
        Level level = Level.NONE;
        Grant grant = GRANTS.get(player.getUUID());
        if (grant != null && !grant.expired(System.currentTimeMillis())) {
            level = grant.level();
        }
        if (node(player, NODE_TIER2)) {
            level = Level.max(level, Level.TIER2);
        } else if (node(player, NODE_TIER1)) {
            level = Level.max(level, Level.TIER1);
        }
        return level;
    }

    private static boolean node(ServerPlayer player, PermissionNode<Boolean> node) {
        try {
            return Boolean.TRUE.equals(PermissionAPI.getPermission(player, node));
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static void sendStatus(ServerPlayer player) {
        Decision decision = access(player);
        Grant grant = GRANTS.get(player.getUUID());
        int secondsLeft = grant == null ? -1
            : (int) Math.max(0, (grant.expiresAt() - System.currentTimeMillis()) / 1000);
        PacketDistributor.sendToPlayer(player, new SupportStatusPacket(
            decision.level().ordinal(), decision.basis().name(), decision.tier2Note(), secondsLeft));
    }

    private static void refresh(ServerPlayer player) {
        sendStatus(player);
        MinecraftServer server = player.getServer();
        if (server != null) {
            server.getCommands().sendCommands(player);
        }
    }

    public static boolean allowRequest(ServerPlayer player, boolean export) {
        Map<UUID, Long> clock = export ? LAST_EXPORT : LAST_REQUEST;
        long interval = export ? EXPORT_INTERVAL_MS : REQUEST_INTERVAL_MS;
        long now = System.currentTimeMillis();
        Long last = clock.get(player.getUUID());
        if (last != null && now - last < interval) {
            return false;
        }
        clock.put(player.getUUID(), now);
        return true;
    }

    public static void issueChallenge(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (!SupportAccess.isCreator(player) || server == null || !server.usesAuthentication()
            || server.getKeyPair() == null || access(player).atLeast(Level.TIER2)) {
            return;
        }
        byte[] nonce = new byte[SupportKeyProof.NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        CHALLENGES.put(player.getUUID(), new Challenge(nonce, System.currentTimeMillis()));
        PacketDistributor.sendToPlayer(player, new SupportChallengePacket(nonce));
    }

    public static void acceptProof(ServerPlayer player, byte[] signature) {
        Challenge challenge = CHALLENGES.remove(player.getUUID());
        MinecraftServer server = player.getServer();
        if (challenge == null || System.currentTimeMillis() - challenge.issuedAt() > CHALLENGE_LIFETIME_MS
            || server == null || server.getKeyPair() == null) {
            SupportAccess.auditAlways(player, "key-proof", "REFUSED", "no live challenge");
            return;
        }
        boolean valid = SupportKeyProof.verify(SupportKeyProof.creatorPublicKey(), player.getUUID(),
            challenge.nonce(), server.getKeyPair().getPublic().getEncoded(), signature);
        if (valid) {
            VERIFIED.add(player.getUUID());
            SupportAccess.auditAlways(player, "key-proof", "VERIFIED");
        } else {
            SupportAccess.auditAlways(player, "key-proof", "REFUSED", "signature does not verify");
            player.sendSystemMessage(Component.literal("Support key proof refused by the server.")
                .withStyle(ChatFormatting.RED));
        }
        refresh(player);
    }

    public static void grant(ServerPlayer target, Level level, int minutes, String grantedBy) {
        long expiresAt = System.currentTimeMillis() + minutes * 60_000L;
        GRANTS.put(target.getUUID(), new Grant(level, expiresAt, grantedBy));
        SupportAccess.auditAlways(target, "grant", level, minutes + "min", "by " + grantedBy);
        target.sendSystemMessage(Component.literal("You have been granted support " + level + " for "
            + minutes + " minutes by " + grantedBy + ".").withStyle(ChatFormatting.GOLD));
        refresh(target);
    }

    public static boolean revoke(ServerPlayer target, String revokedBy) {
        Grant removed = GRANTS.remove(target.getUUID());
        if (removed == null) {
            return false;
        }
        SupportAccess.auditAlways(target, "revoke", "by " + revokedBy);
        endBorrowedSession(target, "grant revoked by " + revokedBy);
        target.sendSystemMessage(Component.literal("Your support grant was revoked by " + revokedBy + ".")
            .withStyle(ChatFormatting.GOLD));
        refresh(target);
        return true;
    }

    public static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Grant> entry : List.copyOf(GRANTS.entrySet())) {
            if (!entry.getValue().expired(now)) {
                continue;
            }
            GRANTS.remove(entry.getKey());
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                SupportAccess.auditAlways(player, "grant", "EXPIRED");
                endBorrowedSession(player, "grant expired");
                player.sendSystemMessage(Component.literal("Your support grant has expired.")
                    .withStyle(ChatFormatting.GOLD));
                refresh(player);
            }
        }
    }

    public static List<String> describe(MinecraftServer server) {
        List<String> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Decision decision = access(player);
            if (decision.level() == Level.NONE) {
                continue;
            }
            StringBuilder line = new StringBuilder(player.getGameProfile().getName())
                .append(": ").append(decision.level()).append(" (").append(decision.basis().label()).append(')');
            Grant grant = GRANTS.get(player.getUUID());
            if (grant != null) {
                line.append(", grant ").append(Math.max(0, (grant.expiresAt() - now) / 60_000))
                    .append(" min left, by ").append(grant.grantedBy());
            }
            Journal journal = JOURNALS.get(player.getUUID());
            if (journal != null) {
                line.append(", session open: ").append(journal.actions).append(" actions, ")
                    .append(journal.rig.size()).append(" rig blocks");
            }
            out.add(line.toString());
        }
        return out;
    }

    public static void tier2(ServerPlayer player, Decision decision, String verb, Object... details) {
        Journal journal = JOURNALS.computeIfAbsent(player.getUUID(), uuid -> {
            SupportAccess.auditTier2(player, "session-start", decision.basis());
            return new Journal();
        });
        journal.borrowed |= decision.basis().borrowed();
        journal.actions++;

        Object[] line = new Object[details.length + 1];
        line[0] = verb;
        System.arraycopy(details, 0, line, 1, details.length);
        SupportAccess.auditTier2(player, "tier2", line);
    }

    public static void noteGamemodeBefore(ServerPlayer player) {
        Journal journal = JOURNALS.get(player.getUUID());
        if (journal != null && journal.originalMode == null) {
            journal.originalMode = player.gameMode.getGameModeForPlayer();
        }
    }

    public static void noteFlightBefore(ServerPlayer player) {
        Journal journal = JOURNALS.get(player.getUUID());
        if (journal != null && journal.originalMayfly == null) {
            journal.originalMayfly = player.getAbilities().mayfly;
        }
    }

    public static void noteRigBlock(ServerPlayer player, ServerLevel level, BlockPos pos, Block block) {
        Journal journal = JOURNALS.get(player.getUUID());
        if (journal != null) {
            journal.rig.add(new RigBlock(level.dimension(), pos.immutable(), block));
        }
    }

    public static int clearRig(ServerPlayer player) {
        Journal journal = JOURNALS.get(player.getUUID());
        MinecraftServer server = player.getServer();
        if (journal == null || server == null) {
            return 0;
        }
        int removed = 0;
        for (RigBlock rig : journal.rig) {
            ServerLevel level = server.getLevel(rig.dimension());
            if (level != null && level.isLoaded(rig.pos()) && level.getBlockState(rig.pos()).is(rig.block())) {
                level.setBlock(rig.pos(), Blocks.AIR.defaultBlockState(), 3);
                removed++;
            }
        }
        journal.rig.clear();
        return removed;
    }

    public static boolean endSession(ServerPlayer player) {
        return finish(player, "ended by player", true);
    }

    private static void endBorrowedSession(ServerPlayer player, String reason) {
        Journal journal = JOURNALS.get(player.getUUID());
        finish(player, reason, journal != null && journal.borrowed);
    }

    private static boolean finish(ServerPlayer player, String reason, boolean undo) {
        Journal journal = JOURNALS.get(player.getUUID());
        if (journal == null) {
            return false;
        }
        List<String> undone = new ArrayList<>();
        if (undo) {
            if (journal.originalMode != null && player.gameMode.getGameModeForPlayer() != journal.originalMode) {
                player.setGameMode(journal.originalMode);
                undone.add("gamemode " + journal.originalMode.getName());
            }

            if (journal.originalMayfly != null && player.getAbilities().mayfly != journal.originalMayfly
                && (player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL
                    || player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE)) {
                player.getAbilities().mayfly = journal.originalMayfly;
                if (!journal.originalMayfly) {
                    player.getAbilities().flying = false;
                }
                player.onUpdateAbilities();
                undone.add("flight " + (journal.originalMayfly ? "on" : "off"));
            }
            int cleared = clearRig(player);
            if (cleared > 0) {
                undone.add(cleared + " rig blocks removed");
            }
        }
        JOURNALS.remove(player.getUUID());
        String summary = undone.isEmpty() ? "nothing to undo" : String.join(", ", undone);
        SupportAccess.auditTier2(player, "session-end", reason, undo ? summary : "kept (own authority)");
        return true;
    }

    public static void onLogout(ServerPlayer player) {
        endBorrowedSession(player, "logged out");
        GRANTS.remove(player.getUUID());
        VERIFIED.remove(player.getUUID());
        CHALLENGES.remove(player.getUUID());
        LAST_REQUEST.remove(player.getUUID());
        LAST_EXPORT.remove(player.getUUID());
    }

    public static void reset() {
        GRANTS.clear();
        VERIFIED.clear();
        CHALLENGES.clear();
        JOURNALS.clear();
        LAST_REQUEST.clear();
        LAST_EXPORT.clear();
    }

    private record Grant(Level level, long expiresAt, String grantedBy) {
        boolean expired(long now) {
            return now >= this.expiresAt;
        }
    }

    private record Challenge(byte[] nonce, long issuedAt) {
    }

    private record RigBlock(ResourceKey<net.minecraft.world.level.Level> dimension, BlockPos pos, Block block) {
    }

    private static final class Journal {
        boolean borrowed;
        int actions;
        @Nullable GameType originalMode;
        @Nullable Boolean originalMayfly;
        final List<RigBlock> rig = new ArrayList<>();
    }
}
