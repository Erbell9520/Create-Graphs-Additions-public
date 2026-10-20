package com.createstats.powertrack.client;

import com.createstats.power.FeGoggle;
import com.createstats.power.FeRates;
import com.createstats.powertrack.CollectorGeometry;
import com.createstats.powertrack.SectionView;
import com.createstats.powertrack.core.CollectorPose;
import com.createstats.powertrack.core.RailLayout;
import com.createstats.powertrack.core.ShoeSlide;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

// the first goggles were fixed advice, a dead train looked the same as a live one
public final class CollectorReadout {
    private CollectorReadout() {
    }

    public static void placed(List<Component> tooltip, Level level, BlockPos pos, BlockState state) {
        final Vec3 centre = Vec3.atCenterOf(pos);
        final Direction.Axis axis = CurrentCollectorRenderer.placedAxis(level, centre, state);
        describe(tooltip, level, centre, CollectorGeometry.trailLocal(axis, 1),
                CollectorGeometry.trailLocal(CollectorGeometry.across(axis), 1), 0.0, false, -1);
    }

    public static void carried(List<Component> tooltip, AbstractContraptionEntity entity, BlockPos localPos) {
        if (!(entity instanceof CarriageContraptionEntity)) {
            tooltip.add(FeGoggle.warn("    Not on a train: only a train's car rides the rail"));
            return;
        }
        final Level level = entity.level();
        final ClientRail.Carried c = ClientRail.carried(entity.getId(), localPos, level.getGameTime());
        if (c == null) {
            tooltip.add(FeGoggle.dim("    Shoe: not heard from yet"));
            return;
        }
        describe(tooltip, level, c.centre(), c.trail(), c.side(), c.centreOffset(), true, entity.getId());
    }

    private static void describe(List<Component> tooltip, Level level, Vec3 centre, Vec3 trail, Vec3 side,
                                 double centreOffset, boolean onCar, int entityId) {
        final CollectorGeometry.Placement placed = ClientRail.place(level, centre, trail, side, centreOffset, onCar);
        final double contact = CollectorPose.CONTACT_HALF_WIDTH_PX / 16.0;
        final double run = Math.abs(placed.slide());
        if (placed.rest().touching()) {
            final Vec3 shoe = CollectorGeometry.shoeAt(placed.slid(), trail, placed.rest().angleDeg());
            final ClientRail.Under under = ClientRail.railUnder(level, shoe.x, shoe.y, shoe.z, contact,
                    ShoeSlide.MIN_ABOVE, ShoeSlide.MAX_ABOVE);
            tooltip.add(FeGoggle.ok("    Shoe on the conductor rail"));
            if (run >= 0.05) {
                tooltip.add(FeGoggle.dim("    Sled run out " + blocks(run) + " to reach it"));
            }
            final SectionView view = under == null ? null : under.view();
            if (view != null) {
                tooltip.add(view.stateLine("    Section "));
            }
            if (onCar) {
                final int current = ClientRail.currentOf(entityId, level.getGameTime());
                if (current > 0) {
                    tooltip.add(FeGoggle.ok("    Charging this car " + FeRates.perTick(current)));
                } else if (current < 0) {
                    tooltip.add(FeGoggle.ok("    Giving to the rail " + FeRates.perTick(-current)));
                } else if (view != null && view.state() == SectionView.LIVE) {
                    tooltip.add(FeGoggle.warn("    Live, but this car takes nothing:"));
                    tooltip.add(FeGoggle.warn("    no Capacitor Bank on this car, or it is full"));
                }
            }
            return;
        }
        if (placed.target() != null) {
            if (run >= ShoeSlide.MAX - 1e-6) {
                tooltip.add(FeGoggle.warn("    Out of reach: the rail is more than " + blocks(ShoeSlide.MAX) + " across"));
                tooltip.add(FeGoggle.dim("    Hang it on the car's centre line, or a block to either side"));
                return;
            }
            final Vec3 at = placed.slid();
            final ClientRail.Under under = ClientRail.railUnder(level, at.x, at.y, at.z, contact,
                    ShoeSlide.MIN_ABOVE, ShoeSlide.MAX_ABOVE);
            tooltip.add(FeGoggle.warn("    Over a gap: " + (under == null ? "no rail here"
                    : PowerTrackGoggles.gapLine(under.gap()))));
            tooltip.add(FeGoggle.dim("    The shoe hangs on its stop until rail comes back"));
            return;
        }

        final ClientRail.Under wide = ClientRail.railUnder(level, centre.x, centre.y, centre.z, RailLayout.INDEX_PAD,
                0.25, 8.0);
        if (wide == null) {
            tooltip.add(FeGoggle.warn("    No Power Track under this block"));
            tooltip.add(FeGoggle.dim("    Ordinary track has no conductor rail"));
            return;
        }
        final long above = Math.round(centre.y - 0.5 - wide.hit().floorY());
        if (above != 1) {
            tooltip.add(FeGoggle.warn("    " + above + " blocks above the track: it must be exactly 1"));
            tooltip.add(FeGoggle.dim("    In the bogies' layer, hung under the car floor"));
        } else {
            tooltip.add(FeGoggle.warn("    Out of reach: the rail is " + blocks(wide.hit().lateral()) + " to the side"));
            tooltip.add(FeGoggle.dim("    Hang it on the car's centre line, or a block to either side"));
        }
    }

    private static String blocks(double v) {
        return String.format(Locale.ROOT, "%.1f %s", v, Math.abs(v - 1.0) < 0.05 ? "block" : "blocks");
    }
}
