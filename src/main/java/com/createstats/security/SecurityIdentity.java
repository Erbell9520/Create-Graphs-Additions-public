package com.createstats.security;

import com.createstats.blockentity.HomeSecurityPanelBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class SecurityIdentity {
    private SecurityIdentity() {
    }

    public static String dimensionOf(Level level) {
        return level.dimension().location().toString();
    }

    public static SecurityEndpoint at(Level level, BlockPos pos) {
        return SecurityEndpoint.of(dimensionOf(level), pos.asLong());
    }

    public static SecurityEndpoint anchorOf(Level level, BlockPos device) {
        return at(level, SecuritySegment.policyAnchor(level, device));
    }

    public static boolean accepts(@Nullable Level level, @Nullable UUID remembered, BlockPos device,
                                  @Nullable HomeSecurityPanelBE panel) {
        if (panel == null || level == null) {
            return false;
        }
        if (remembered == null) {
            return true;
        }

        final SecurityHousehold its = panel.household();
        if (its == null) {
            return false;
        }
        return remembered.equals(its.id()) || free(level, remembered, device);
    }

    @Nullable
    public static UUID householdOf(@Nullable HomeSecurityPanelBE panel) {
        final SecurityHousehold h = panel == null ? null : panel.household();
        return h == null ? null : h.id();
    }

    @Nullable
    public static HomeSecurityPanelBE offered(@Nullable Level level, @Nullable BlockPos offered, BlockPos device,
                                              @Nullable UUID remembered) {
        if (level == null || offered == null || !level.isLoaded(offered)) {
            return null;
        }
        if (!(level.getBlockEntity(offered) instanceof HomeSecurityPanelBE panel)) {
            return null;
        }
        final boolean listed = panel.listsDevice(device)
                || panel.listsDevice(SecuritySegment.policyAnchor(level, device));
        return listed && accepts(level, remembered, device, panel) ? panel : null;
    }

    public static boolean free(Level level, @Nullable UUID remembered, BlockPos device) {
        if (remembered == null) {
            return true;
        }
        final SecurityHouseholds store = SecurityHouseholds.of(level);
        if (store == null) {
            return false;
        }
        final SecurityHousehold old = store.find(remembered);
        return old == null || old.released(anchorOf(level, device));
    }
}
