package com.createstats.display;

import com.createstats.cinecam.CineCameraBE;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CineCameraDisplaySource extends DisplaySource {
    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        BlockEntity be = context.getSourceBlockEntity();

        if (!(be instanceof CineCameraBE) && context.level() != null) {
            be = context.level().getBlockEntity(context.getSourcePos().below());
        }
        if (!(be instanceof CineCameraBE cam)) return EMPTY;
        List<MutableComponent> lines = new ArrayList<>();
        if (cam.recording()) {
            long s = (long) (cam.recordingTicks() / 20.0 / cam.filmSpeed().factor());
            String speed = cam.filmSpeed() == com.createstats.cinecam.core.FilmFormat.Speed.NORMAL ? ""
                    : "  " + cam.filmSpeed().label();
            lines.add(Component.literal(String.format(Locale.ROOT, "REC %02d:%02d  %s %d FPS%s", s / 60, s % 60,
                    cam.filmResolution().label(), cam.filmRate(), speed)));
        }
        String name = cam.targetName().isEmpty() ? "?" : cam.targetName();
        MutableComponent status = switch (cam.status()) {
            case CineCameraBE.TRACKING -> Component.translatable("create_stats.cine_camera.status.locked");
            case CineCameraBE.LOST -> Component.translatable("create_stats.cine_camera.status.seeking");
            default -> Component.translatable("create_stats.cine_camera.status.none");
        };
        lines.add(cam.targetKind() == CineCameraBE.KIND_NONE ? status : Component.literal(name + "  ").append(status));
        if (cam.status() == CineCameraBE.TRACKING) {
            lines.add(Component.literal(String.format(Locale.ROOT, "AZ %05.1f  EL %+05.1f", cam.bearing(), cam.elevation())));
            lines.add(Component.literal(String.format(Locale.ROOT, "RNG %.0f +-%.0f", cam.range(), Math.max(1f, cam.rangeSigma()))));
            lines.add(Component.literal(String.format(Locale.ROOT, "HGT %.1f  SPD ~%.1f", cam.height(), cam.speed())));
            lines.add(Component.literal(String.format(Locale.ROOT, "CLB %+.1f  HDG %03.0f", cam.climb(), cam.heading())));
        }
        if (cam.status() != CineCameraBE.TRACKING && cam.runSeconds() > 0f) {
            lines.add(Component.translatable("create_stats.cine_camera.last_run",
                    cam.runName().isEmpty() ? "?" : cam.runName(),
                    String.format(Locale.ROOT, "%.0f", cam.runSeconds()),
                    String.format(Locale.ROOT, "%.0f", cam.runDistance()),
                    String.format(Locale.ROOT, "%.1f", cam.runTopSpeed()),
                    String.format(Locale.ROOT, "%.0f", cam.runTopHeight())));
        }
        return lines.size() > stats.maxRows() ? lines.subList(0, Math.max(1, stats.maxRows())) : lines;
    }
}
