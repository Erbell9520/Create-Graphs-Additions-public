package com.createstats.netscan;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public interface NetscanHost {
    ScannerTier scannerTier();

    ScannerForm scannerForm();

    NetscanEngine scanEngine();

    BlockPos scanSeed();

    boolean scanPowered();

    Level scanLevel();

    BlockPos scanPos();
}
