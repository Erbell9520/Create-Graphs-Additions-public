package com.createstats.power;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

public interface HomeAppliance {
    Category applianceCategory();

    default Component applianceName() {
        if (this instanceof BlockEntity be) {
            return be.getBlockState().getBlock().getName();
        }
        return Component.empty();
    }

    @org.jetbrains.annotations.Nullable
    default ApplianceStatus applianceStatus() {
        return null;
    }

    enum Category {
        LIGHTING("lighting"),
        SOCKETS("sockets"),
        KITCHEN("kitchen"),
        LAUNDRY("laundry"),

        OTHER("other");

        private final String key;

        Category(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        public Component label() {
            return Component.translatable("category.create_stats.home." + key);
        }

        public static Category byOrdinal(int ordinal) {
            final Category[] all = values();
            return ordinal >= 0 && ordinal < all.length ? all[ordinal] : OTHER;
        }
    }
}
