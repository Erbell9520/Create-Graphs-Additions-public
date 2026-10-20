package com.createstats.battle.world;

import com.createstats.battle.core.PlaceName;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class BattleText {
    public static final String L = "battle.create_stats.";

    private BattleText() {
    }

    public static MutableComponent tr(String key, Object... args) {
        return Component.translatable(L + key, args);
    }

    public static MutableComponent name(String name) {
        if (name == null || name.isEmpty()) return tr("target.unnamed");
        return name.charAt(0) == '#' ? Component.translatable(name.substring(1)) : Component.literal(name);
    }

    public static MutableComponent place(String token) {
        if (token == null || token.isEmpty()) return tr("place.unnamed");
        String rest = token.length() > 2 ? token.substring(2) : "";
        String[] p = rest.split(":");
        try {
            switch (token.charAt(0)) {
                case 'M':
                    return rest.isEmpty() ? tr("place.unnamed") : Component.literal(rest);
                case 'G':
                    return tr("place.gun", Integer.parseInt(p[0]));
                case 'P':
                    return tr("place.propeller", Integer.parseInt(p[0]));
                case 'E':
                    return tr("place.envelope." + Integer.parseInt(p[0]));
                case 'B':
                    return tr("place.broke_away");
                case 'S': {
                    MutableComponent out = tr("place.fore." + Integer.parseInt(p[0]));
                    int side = Integer.parseInt(p[1]);
                    if (side != PlaceName.SIDE_CENTRE) out = out.append(", ").append(tr("place.side." + side));
                    int level = Integer.parseInt(p[2]);
                    if (level != PlaceName.LEVEL_MAIN) out = out.append(", ").append(tr("place.level." + level));
                    return out;
                }
                case 'C': {
                    int ns = Integer.parseInt(p[0]);
                    int we = Integer.parseInt(p[1]);
                    MutableComponent out = null;
                    if (ns != PlaceName.SIDE_CENTRE || we == PlaceName.SIDE_CENTRE) out = tr("place.ns." + ns);
                    if (we != PlaceName.SIDE_CENTRE) out = out == null ? tr("place.we." + we) : out.append(", ").append(tr("place.we." + we));
                    int level = Integer.parseInt(p[2]);
                    if (level != PlaceName.LEVEL_MAIN) out = out.append(", ").append(tr("place.level." + level));
                    return out;
                }
                default:
                    return tr("place.unnamed");
            }
        } catch (RuntimeException e) {
            return tr("place.unnamed");
        }
    }
}
