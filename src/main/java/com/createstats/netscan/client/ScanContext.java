package com.createstats.netscan.client;

import java.util.List;

import com.createstats.netscan.ScannerForm;
import com.createstats.netscan.ScannerTier;
import com.createstats.netscan.scan.ScanGraph;
import com.createstats.netscan.scan.ScanLayout;
import com.createstats.netscan.scan.ScanLayoutMode;

import net.minecraft.client.gui.Font;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ScanContext {
    public final Font font;
    public final BlockPos pos;

    public ScannerTier tier = ScannerTier.FIELD;
    public ScannerForm form = ScannerForm.INLINE;
    public ScanGraph graph = ScanGraph.EMPTY;

    public boolean powered;

    public boolean seedMissing;
    public boolean sweeping;
    public int sweepCount;
    public List<float[]> history = List.of();

    public BlockPos selected;

    public ScanLayoutMode mode = ScanLayoutMode.DRIVE;

    private ScanLayout.Result layout;
    private int layoutHash = Integer.MIN_VALUE;
    private ScanLayoutMode layoutMode;

    public ScanContext(Font font, BlockPos pos) {
        this.font = font;
        this.pos = pos;
    }

    public ScanLayout.Result layout() {
        if (this.layout == null || this.layoutHash != this.graph.contentHash()
                || this.layoutMode != this.mode) {
            this.layout = ScanLayout.layout(this.graph, this.mode);
            this.layoutHash = this.graph.contentHash();
            this.layoutMode = this.mode;
        }
        return this.layout;
    }

    public boolean allows(int detailLevel) {
        return this.tier.detailLevel() >= detailLevel;
    }

    public static String unlockedBy(int detailLevel) {
        return detailLevel >= 3 ? "Industrial" : detailLevel == 2 ? "Advanced" : "Field";
    }
}
