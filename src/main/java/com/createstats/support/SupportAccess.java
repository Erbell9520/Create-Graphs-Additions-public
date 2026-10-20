package com.createstats.support;

import java.util.UUID;

import com.createstats.CreateStats;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public final class SupportAccess {
    public static final UUID CREATOR_UUID = UUID.fromString("c8881ffb-c50b-4c86-a87e-aeddb3ac65b3");

    public static final String CREATOR_NAME = "AEA19";

    private static final boolean DEFAULT_AUDIT_ENABLED = false;

    private static final boolean DEFAULT_TIER2_AUDIT_ENABLED = true;

    private static volatile boolean auditEnabled = DEFAULT_AUDIT_ENABLED;

    private static volatile boolean tier2AuditEnabled = DEFAULT_TIER2_AUDIT_ENABLED;

    private SupportAccess() {
    }

    public static boolean isCreator(@Nullable Player player) {
        return player != null && CREATOR_UUID.equals(player.getUUID());
    }

    public static boolean canToggleAudit(@Nullable ServerPlayer player) {
        return isCreator(player) || (player != null && player.hasPermissions(2));
    }

    public static boolean canToggleAudit(CommandSourceStack source) {
        return isCreator(source.getPlayer()) || source.hasPermission(2);
    }

    public static boolean auditEnabled() {
        return auditEnabled;
    }

    public static boolean tier2AuditEnabled() {
        return tier2AuditEnabled;
    }

    public static void resetAuditEnabled() {
        if (DEFAULT_AUDIT_ENABLED && !auditEnabled) {
            CreateStats.LOGGER.info("[SUPPORT] <server> :: audit-log ENABLED (reset on server stop)");
        }
        if (DEFAULT_TIER2_AUDIT_ENABLED && !tier2AuditEnabled) {
            CreateStats.LOGGER.info("[SUPPORT] <server> :: tier2-log ENABLED (reset on server stop)");
        }
        auditEnabled = DEFAULT_AUDIT_ENABLED;
        tier2AuditEnabled = DEFAULT_TIER2_AUDIT_ENABLED;
    }

    public static void setAuditEnabled(@Nullable ServerPlayer player, boolean enabled) {
        auditEnabled = enabled;
        if (enabled) {
            auditAlways(player, "audit-log", "ENABLED");
        }
    }

    public static void setTier2AuditEnabled(@Nullable ServerPlayer player, boolean enabled) {
        tier2AuditEnabled = enabled;
        if (enabled) {
            auditAlways(player, "tier2-log", "ENABLED");
        }
    }

    public static void audit(@Nullable ServerPlayer player, String action, Object... details) {
        if (!auditEnabled) {
            return;
        }
        auditAlways(player, action, details);
    }

    public static void auditTier2(@Nullable ServerPlayer player, String action, Object... details) {
        if (!tier2AuditEnabled) {
            return;
        }
        auditAlways(player, action, details);
    }

    public static void auditAlways(@Nullable ServerPlayer player, String action, Object... details) {
        String who = player == null ? "<unknown>" : player.getGameProfile().getName() + "/" + player.getUUID();
        StringBuilder line = new StringBuilder("[SUPPORT] ").append(who).append(" :: ").append(action);
        for (Object detail : details) {
            line.append(' ').append(detail);
        }
        CreateStats.LOGGER.info(line.toString());
    }
}
