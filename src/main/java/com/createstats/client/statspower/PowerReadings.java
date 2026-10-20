package com.createstats.client.statspower;

import java.util.ArrayList;
import java.util.List;

import com.createstats.blockentity.PowerDistributionUnitBE;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.data.TimeSeriesStore;
import com.createstats.power.BatteryControllerPlan;
import com.createstats.power.BatteryControllerPorts;
import com.createstats.power.BatteryOutputMode;
import com.createstats.power.FePriority;
import com.createstats.power.FeReserveReport;
import com.createstats.power.PduMode;
import com.createstats.statsmk3.page.SourceProbe;
import com.createstats.util.CreateStatsPowerMetrics;

import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// decode the flat channel map once, string lookups everywhere silently draw zeros
@OnlyIn(Dist.CLIENT)
public final class PowerReadings {
    public static final String FAMILY_PDU = "energy.pdu";
    public static final String FAMILY_BANK = "energy.battery_controller";
    public static final String FAMILY_METER = "fluid.flow_meter";

    private PowerReadings() {
    }

    public record Leg(int index, String name, boolean wired, float demand, float served,
                      float rating, float state, int devices) {
        public boolean tripped() {
            return this.state >= CreateStatsPowerMetrics.LEG_TRIPPED;
        }

        public boolean open() {
            return this.state >= CreateStatsPowerMetrics.LEG_OPEN && !tripped();
        }

        public boolean overCapacity() {
            return this.state >= CreateStatsPowerMetrics.LEG_OVER_CAPACITY && !open() && !tripped();
        }

        public boolean healthy() {
            return this.state < CreateStatsPowerMetrics.LEG_OVER_CAPACITY;
        }

        public String stateLabel() {
            if (tripped()) {
                return "Tripped";
            }
            if (open()) {
                return "Breaker off";
            }
            if (overCapacity()) {
                return "Over capacity";
            }
            if (this.demand <= 0.5f) {
                return "Idle";
            }
            if (this.served + 0.5f < this.demand) {
                return "Partial";
            }
            return "Served";
        }

        public float loadFraction() {
            return this.rating <= 0.0f ? 0.0f
                    : Math.max(0.0f, Math.min(1.4f, this.served / this.rating));
        }

        public float shortfall() {
            return Math.max(0.0f, this.demand - this.served);
        }
    }

    public record Pdu(SourceProbe.Unit unit, String label, BlockPos pos,
                      float stored, float capacity, float fillPct,
                      float input, float delivered, float net, float enduranceTicks,
                      float generation, float demand, float served, float budget, float ceiling,
                      int devices, int servedDevices, int shedDevices,
                      PduMode mode, boolean surplus, boolean throughputLimited,
                      List<Leg> legs, int[] tierTotal, int[] tierServed,
                      boolean reserveLinked, float reservePct, FeReserveReport.Stance stance,
                      float reserveAllowance, int upstreamSources, int reserveTanks,
                      int reserveDepth, boolean coreProtected) {
        public float shortfall() {
            return Math.max(0.0f, this.demand - this.served);
        }

        public float headroom() {
            return this.generation - this.served;
        }

        public boolean shedding() {
            return this.shedDevices > 0 || shortfall() > 1.0f;
        }

        public int wiredLegs() {
            int n = 0;
            for (final Leg leg : this.legs) {
                if (leg.wired()) {
                    n++;
                }
            }
            return n;
        }

        public TimeSeriesStore history(String metric) {
            return this.unit.store(metric);
        }

        public String diagnosis() {
            if (this.throughputLimited) {
                return "Wiring is the limit, not generation — " + PowerFmt.rate(this.ceiling) + " ceiling";
            }
            for (final Leg leg : this.legs) {
                if (leg.tripped()) {
                    return leg.name() + " tripped — sustained overload threw the breaker";
                }
            }
            for (final Leg leg : this.legs) {
                if (leg.overCapacity()) {
                    return leg.name() + " over capacity — " + PowerFmt.rate(leg.served())
                            + " on a " + PowerFmt.rate(leg.rating()) + " conductor";
                }
            }
            if (this.shedDevices > 0) {
                return this.shedDevices + (this.shedDevices == 1 ? " device shed" : " devices shed")
                        + " — short by " + PowerFmt.rate(shortfall());
            }
            if (this.reserveLinked && this.stance != null && this.stance.isRationing()) {
                return "Upstream reserve is " + this.stance.label().toLowerCase(java.util.Locale.ROOT)
                        + " — the controller is holding charge back, not the panel";
            }
            return null;
        }
    }

    public record Circuit(int index, String name, float flow, float demand, float ceiling,
                          float floorPct, float critical, int devices, int panels,
                          BatteryControllerPlan.Reason reason, BatteryOutputMode mode,
                          FePriority tier) {
        public boolean wired() {
            return this.reason != BatteryControllerPlan.Reason.UNWIRED;
        }

        public boolean withheld() {
            return this.reason != null && this.reason.isWithheld();
        }

        public boolean faulted() {
            return this.reason != null
                    && this.reason != BatteryControllerPlan.Reason.SERVED
                    && this.reason != BatteryControllerPlan.Reason.IDLE
                    && this.reason != BatteryControllerPlan.Reason.UNWIRED;
        }

        public String reasonLabel() {
            return this.reason == null ? "—" : this.reason.label();
        }

        public float loadFraction() {
            return this.ceiling <= 0.0f ? 0.0f
                    : Math.max(0.0f, Math.min(1.4f, this.flow / this.ceiling));
        }

        public float shortfall() {
            return Math.max(0.0f, this.demand - this.flow);
        }
    }

    public record Feed(int index, String name, float flow, int tanks, int sources) {
        public boolean wired() {
            return this.tanks > 0 || this.sources > 0 || this.flow > 0.5f;
        }
    }

    public record Bank(SourceProbe.Unit unit, String label, BlockPos pos,
                       float reserve, float capacity, float fillPct,
                       float generation, float delivered, float net, float enduranceTicks,
                       float bus, FeReserveReport.Stance stance, boolean managedDownstream,
                       List<Feed> feeds, List<Circuit> circuits) {
        public boolean charging() {
            return this.net > 0.5f;
        }

        public boolean discharging() {
            return this.net < -0.5f;
        }

        public float totalDemand() {
            float total = 0.0f;
            for (final Circuit c : this.circuits) {
                total += c.demand();
            }
            return total;
        }

        public float totalShortfall() {
            float total = 0.0f;
            for (final Circuit c : this.circuits) {
                total += c.shortfall();
            }
            return total;
        }

        public TimeSeriesStore history(String metric) {
            return this.unit.store(metric);
        }

        public float ticksToFull() {
            if (this.net <= 0.5f || this.capacity <= 0.0f || this.reserve >= this.capacity) {
                return -1.0f;
            }
            return (this.capacity - this.reserve) / this.net;
        }

        public String diagnosis() {
            for (final Circuit c : this.circuits) {
                if (c.reason() == BatteryControllerPlan.Reason.FLOOR) {
                    return c.name() + " is under its " + PowerFmt.pct0(c.floorPct())
                            + " floor — policy, not a shortage";
                }
            }
            for (final Circuit c : this.circuits) {
                if (c.reason() == BatteryControllerPlan.Reason.SHED) {
                    return c.name() + " shed — higher tiers took the whole reserve";
                }
            }
            if (this.stance == FeReserveReport.Stance.CRITICAL) {
                return "Below the reserve band — critical load only until the banks recover";
            }
            if (discharging() && this.fillPct < 15.0f) {
                return "Reserve nearly empty and still draining";
            }
            if (this.stance == FeReserveReport.Stance.CONSERVE) {
                return "Holding the band — living on generation, nothing new restored";
            }
            return null;
        }
    }

    public record Meter(SourceProbe.Unit unit, String label, BlockPos pos,
                        float rate, float total, float direction, String fluidId,
                        String fluidName) {
        public boolean flowing() {
            return this.rate > 0.0f;
        }

        public boolean known() {
            return this.fluidId != null && !this.fluidId.isEmpty();
        }

        public int sign() {
            return this.direction > 0.1f ? 1 : (this.direction < -0.1f ? -1 : 0);
        }

        public TimeSeriesStore history() {
            return this.unit.store("flow_rate_mbt");
        }

        public TimeSeriesStore totalHistory() {
            return this.unit.store("flow_total_mb");
        }

        public float bucketsPerMinute() {
            return this.rate * 20.0f * 60.0f / 1000.0f;
        }
    }

    public static List<SourceProbe.Unit> pduUnits(StatsDisplayPanelBE panel) {
        return SourceProbe.byFamily(panel, FAMILY_PDU);
    }

    public static List<SourceProbe.Unit> bankUnits(StatsDisplayPanelBE panel) {
        return SourceProbe.byFamily(panel, FAMILY_BANK);
    }

    public static List<SourceProbe.Unit> meterUnits(StatsDisplayPanelBE panel) {
        return SourceProbe.byFamily(panel, FAMILY_METER);
    }

    public static boolean hasAny(StatsDisplayPanelBE panel) {
        return !pduUnits(panel).isEmpty() || !bankUnits(panel).isEmpty()
                || !meterUnits(panel).isEmpty();
    }

    public static Pdu readPdu(SourceProbe.Unit unit) {
        if (unit == null) {
            return null;
        }
        final List<Leg> legs = new ArrayList<>(PowerDistributionUnitBE.LEGS);
        final int wiredMask = Math.round(unit.value("pdu_legs_wired", 0.0f));
        for (int i = 0; i < PowerDistributionUnitBE.LEGS; i++) {
            final String prefix = "pdu_leg" + i + "_";

            final boolean wired = ((wiredMask >> i) & 1) != 0 || unit.has(prefix + "served");
            final String name = i == PowerDistributionUnitBE.BACK_LEG ? "Back" : ("Leg " + (i + 1));
            legs.add(new Leg(i, name, wired,
                    unit.value(prefix + "demand", 0.0f),
                    unit.value(prefix + "served", 0.0f),
                    unit.value(prefix + "ceiling", 0.0f),
                    unit.value(prefix + "state", CreateStatsPowerMetrics.LEG_OK),
                    Math.round(unit.value(prefix + "devices", 0.0f))));
        }

        final FePriority[] tiers = FePriority.values();
        final int[] tierTotal = new int[tiers.length];
        final int[] tierServed = new int[tiers.length];
        for (int i = 0; i < tiers.length; i++) {
            tierTotal[i] = Math.round(unit.value("pdu_tier" + i + "_total", 0.0f));
            tierServed[i] = Math.round(unit.value("pdu_tier" + i + "_served", 0.0f));
        }

        final boolean reserveLinked = unit.flag("pdu_reserve_linked");
        return new Pdu(unit, unit.label(), posOf(unit),
                unit.value("energy_stored", 0.0f),
                unit.value("energy_capacity", 0.0f),
                unit.value("energy_fill_pct", 0.0f),
                unit.value("energy_input_rate", 0.0f),
                unit.value("energy_output_rate", 0.0f),
                unit.value("energy_net_rate", 0.0f),
                unit.value("energy_endurance", 0.0f),
                unit.value("pdu_generation", 0.0f),
                unit.value("pdu_demand", 0.0f),
                unit.value("pdu_served_demand", 0.0f),
                unit.value("pdu_budget", 0.0f),
                unit.value("pdu_ceiling", 0.0f),
                Math.round(unit.value("pdu_devices", 0.0f)),
                Math.round(unit.value("pdu_served_devices", 0.0f)),
                Math.round(unit.value("pdu_shed_devices", 0.0f)),
                PduMode.byOrdinal(Math.round(unit.value("pdu_mode", 1.0f))),
                unit.flag("pdu_surplus"),
                unit.flag("pdu_throughput_limited"),
                legs, tierTotal, tierServed,
                reserveLinked,
                unit.value("pdu_reserve_pct", 0.0f),
                reserveLinked ? stanceOf(unit.value("pdu_reserve_stance", -1.0f)) : null,
                unit.value("pdu_reserve_allowance", 0.0f),
                Math.round(unit.value("pdu_upstream_sources", 0.0f)),
                Math.round(unit.value("pdu_reserve_tanks", 0.0f)),
                Math.round(unit.value("pdu_reserve_depth", 0.0f)),
                unit.flag("pdu_core_protected"));
    }

    public static Bank readBank(SourceProbe.Unit unit) {
        if (unit == null) {
            return null;
        }
        final List<Feed> feeds = new ArrayList<>(BatteryControllerPorts.INPUTS);
        for (int i = 0; i < BatteryControllerPorts.INPUTS; i++) {
            feeds.add(new Feed(i, "Input " + (char) ('A' + i),
                    unit.value("bank_in" + i + "_flow", 0.0f),
                    Math.round(unit.value("bank_in" + i + "_tanks", 0.0f)),
                    Math.round(unit.value("bank_in" + i + "_sources", 0.0f))));
        }

        final BatteryControllerPlan.Reason[] reasons = BatteryControllerPlan.Reason.values();
        final List<Circuit> circuits = new ArrayList<>(BatteryControllerPorts.OUTPUTS);
        for (int i = 0; i < BatteryControllerPorts.OUTPUTS; i++) {
            final String prefix = "bank_out" + i + "_";
            final int reasonOrdinal = Math.round(unit.value(prefix + "reason", -1.0f));
            circuits.add(new Circuit(i, "Output " + (i + 1),
                    unit.value(prefix + "flow", 0.0f),
                    unit.value(prefix + "demand", 0.0f),
                    unit.value(prefix + "ceiling", 0.0f),
                    unit.value(prefix + "floor", 0.0f),
                    unit.value(prefix + "critical", 0.0f),
                    Math.round(unit.value(prefix + "devices", 0.0f)),
                    Math.round(unit.value(prefix + "panels", 0.0f)),
                    reasonOrdinal >= 0 && reasonOrdinal < reasons.length
                            ? reasons[reasonOrdinal] : null,
                    BatteryOutputMode.byOrdinal(Math.round(unit.value(prefix + "mode", 0.0f))),
                    FePriority.byOrdinal(Math.round(unit.value(prefix + "tier", 2.0f)))));
        }

        return new Bank(unit, unit.label(), posOf(unit),
                unit.value("energy_stored", 0.0f),
                unit.value("energy_capacity", 0.0f),
                unit.value("energy_fill_pct", 0.0f),
                unit.value("energy_input_rate", 0.0f),
                unit.value("energy_output_rate", 0.0f),
                unit.value("energy_net_rate", 0.0f),
                unit.value("energy_endurance", 0.0f),
                unit.value("bank_bus", 0.0f),
                stanceOf(unit.value("bank_stance", -1.0f)),
                unit.flag("bank_managed"),
                feeds, circuits);
    }

    public static Meter readMeter(SourceProbe.Unit unit) {
        if (unit == null) {
            return null;
        }

        final String fluidId = unit.labelOf("fluid_id", "");
        return new Meter(unit, unit.label(), posOf(unit),
                unit.value("flow_rate_mbt", 0.0f),
                unit.value("flow_total_mb", 0.0f),
                unit.value("flow_direction", 0.0f),
                fluidId,
                PowerFmt.prettyFluidId(fluidId));
    }

    private static BlockPos posOf(SourceProbe.Unit unit) {
        return unit.config() == null ? null : unit.config().getSourcePos();
    }

    private static FeReserveReport.Stance stanceOf(float raw) {
        final int ordinal = Math.round(raw);
        final FeReserveReport.Stance[] all = FeReserveReport.Stance.values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : null;
    }
}
