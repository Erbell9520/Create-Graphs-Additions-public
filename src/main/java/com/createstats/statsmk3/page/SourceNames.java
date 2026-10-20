package com.createstats.statsmk3.page;

import java.util.Collection;

import com.createstats.blockentity.SourceConfig;

import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class SourceNames {
    private SourceNames() {
    }

    public static String label(SourceConfig config, boolean typeImplied, boolean ambiguous) {
        if (config == null) {
            return "Source";
        }
        final String user = config.getUserLabel();
        if (user != null && !user.isEmpty()) {
            return user;
        }
        final String type = config.getSourceType() == null || config.getSourceType().isEmpty()
                ? "Source" : config.getSourceType();
        if (!ambiguous) {
            return type;
        }
        final String where = where(config);
        if (where.isEmpty()) {
            return type;
        }
        return typeImplied ? where : type + " " + where;
    }

    public static String where(SourceConfig config) {
        final BlockPos pos = config == null ? null : config.getSourcePos();
        return pos == null ? "" : pos.toShortString();
    }

    public static boolean ambiguous(Collection<SourceConfig> all, SourceConfig one) {
        if (all == null || one == null || one.getSourceType() == null) {
            return false;
        }
        int seen = 0;
        for (final SourceConfig other : all) {
            if (other != null && one.getSourceType().equals(other.getSourceType())) {
                seen++;
                if (seen > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean named(SourceConfig config) {
        final String user = config == null ? null : config.getUserLabel();
        return user != null && !user.isEmpty();
    }
}
