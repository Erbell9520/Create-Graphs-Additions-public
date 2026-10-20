package com.createstats.vccmk4;

import com.createstats.vccmk4.core.HeldKeys;
import net.minecraft.server.level.ServerPlayer;

public interface HelmConsole {
    void acceptInput(ServerPlayer player, HeldKeys keys);
}
