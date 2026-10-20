package com.createstats.pa;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.createstats.blockentity.AnnouncementComputerBE;
import com.createstats.blockentity.PaSpeakerBE;
import com.createstats.power.FeWireNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class PaSpeakerLink {
    private PaSpeakerLink() {
    }

    public static List<PaSpeakerBE> speakersFor(ServerLevel level, AnnouncementComputerBE brain) {
        return collect(level, PaReach.circuitPositions(brain));
    }

    public static List<PaSpeakerBE> collect(ServerLevel level, Set<Long> circuit) {
        final List<PaSpeakerBE> out = new ArrayList<>();
        for (Long packed : circuit) {
            final BlockPos pos = BlockPos.of(packed);
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (level.getBlockEntity(pos) instanceof PaSpeakerBE speaker && speaker.isPowered()) {
                out.add(speaker);
            }
        }
        return out;
    }

    public static List<AnnouncementComputerBE> brainsFor(ServerLevel level, PaSpeakerBE speaker) {
        final List<AnnouncementComputerBE> out = new ArrayList<>();
        final FeWireNetwork network = speaker.feNetwork();
        if (network == null) {
            return out;
        }
        for (Long packed : network.reachedPositions()) {
            final BlockPos pos = BlockPos.of(packed);
            if (!level.isLoaded(pos)) {
                continue;
            }
            final BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof AnnouncementComputerBE brain) {
                out.add(brain);
            }
        }
        return out;
    }
}
