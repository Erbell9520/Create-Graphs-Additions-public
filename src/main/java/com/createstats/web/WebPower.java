package com.createstats.web;

import java.util.List;

import com.createstats.client.statspower.PowerReadings;
import com.createstats.data.TimeSeriesStore;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.power.FePriority;
import com.createstats.power.FeReserveReport;
import com.createstats.renderer.FluidPalette;
import com.createstats.statsmk3.page.SourceProbe;

import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class WebPower {
    private static final int HISTOGRAM_BINS = 14;

    private static final float TPS = 20.0f;

    private WebPower() {
    }

    public static void write(WebJson j, StatsDisplayPanelBE be) {
        j.name("power").beginObject();
        try {
            writePdus(j, be == null ? List.of() : PowerReadings.pduUnits(be));
            writeBanks(j, be == null ? List.of() : PowerReadings.bankUnits(be));
            writeMeters(j, be == null ? List.of() : PowerReadings.meterUnits(be));
        } catch (final RuntimeException failed) {
            j.name("error").value(true);
        }
        j.endObject();
    }

    public static void writeEmpty(WebJson j) {
        j.name("power").beginObject()
                .name("pdu").beginArray().endArray()
                .name("bank").beginArray().endArray()
                .name("flow").beginArray().endArray()
                .endObject();
    }

    private static void writePdus(WebJson j, List<SourceProbe.Unit> units) {
        j.name("pdu").beginArray();
        for (final SourceProbe.Unit unit : units) {
            final PowerReadings.Pdu pdu = PowerReadings.readPdu(unit);
            if (pdu == null) {
                continue;
            }
            j.beginObject();
            j.put("id", idOf(unit));
            j.put("label", pdu.label());
            j.put("pos", posOf(pdu.pos()));

            j.put("gen", pdu.generation());
            j.put("demand", pdu.demand());
            j.put("served", pdu.served());
            j.put("budget", pdu.budget());
            j.put("ceiling", pdu.ceiling());
            j.put("shortfall", pdu.shortfall());
            j.put("headroom", pdu.headroom());

            j.put("stored", pdu.stored());
            j.put("capacity", pdu.capacity());
            j.put("fill", pdu.fillPct());
            j.put("input", pdu.input());
            j.put("delivered", pdu.delivered());
            j.put("net", pdu.net());
            j.put("endurance", pdu.enduranceTicks() / TPS);
            j.put("draining", pdu.net() < -0.5f);

            j.put("devices", pdu.devices());
            j.put("servedDevices", pdu.servedDevices());
            j.put("shedDevices", pdu.shedDevices());
            j.put("mode", pdu.mode().getDisplayName());
            j.put("surplus", pdu.surplus());
            j.put("wiringLimited", pdu.throughputLimited());
            j.put("diagnosis", pdu.diagnosis() == null ? "" : pdu.diagnosis());

            j.name("legs").beginArray();
            for (final PowerReadings.Leg leg : pdu.legs()) {
                j.beginObject()
                        .put("i", leg.index())
                        .put("name", leg.name())
                        .put("wired", leg.wired())
                        .put("demand", leg.demand())
                        .put("served", leg.served())
                        .put("rating", leg.rating())
                        .put("devices", leg.devices())
                        .put("state", leg.stateLabel())
                        .put("open", leg.open())
                        .put("tripped", leg.tripped())
                        .put("over", leg.overCapacity())
                        .endObject();
            }
            j.endArray();

            j.name("tiers").beginArray();
            final FePriority[] tiers = FePriority.values();
            for (int i = 0; i < tiers.length; i++) {
                j.beginObject()
                        .put("name", tiers[i].getDisplayName())
                        .put("total", i < pdu.tierTotal().length ? pdu.tierTotal()[i] : 0)
                        .put("served", i < pdu.tierServed().length ? pdu.tierServed()[i] : 0)
                        .endObject();
            }
            j.endArray();

            final FeReserveReport.Stance stance = pdu.stance();
            j.name("reserve").beginObject()
                    .put("linked", pdu.reserveLinked())
                    .put("pct", pdu.reservePct())
                    .put("stance", stance == null ? "" : stance.label())
                    .put("blurb", stance == null ? "" : stance.blurb())
                    .put("rationing", stance != null && stance.isRationing())
                    .put("allowance", pdu.reserveAllowance())
                    .put("sources", pdu.upstreamSources())
                    .put("tanks", pdu.reserveTanks())
                    .put("depth", pdu.reserveDepth())
                    .put("core", pdu.coreProtected())
                    .endObject();

            j.put("dt", cadence(pdu.history("pdu_served_demand")));
            j.name("h").beginObject();
            series(j, "gen", pdu.history("pdu_generation"));
            series(j, "demand", pdu.history("pdu_demand"));
            series(j, "served", pdu.history("pdu_served_demand"));
            series(j, "fill", pdu.history("energy_fill_pct"));
            j.endObject();

            j.endObject();
        }
        j.endArray();
    }

    private static void writeBanks(WebJson j, List<SourceProbe.Unit> units) {
        j.name("bank").beginArray();
        for (final SourceProbe.Unit unit : units) {
            final PowerReadings.Bank bank = PowerReadings.readBank(unit);
            if (bank == null) {
                continue;
            }
            j.beginObject();
            j.put("id", idOf(unit));
            j.put("label", bank.label());
            j.put("pos", posOf(bank.pos()));

            j.put("reserve", bank.reserve());
            j.put("capacity", bank.capacity());
            j.put("fill", bank.fillPct());
            j.put("gen", bank.generation());
            j.put("delivered", bank.delivered());
            j.put("net", bank.net());
            j.put("bus", bank.bus());
            j.put("charging", bank.charging());
            j.put("discharging", bank.discharging());
            j.put("endurance", bank.enduranceTicks() / TPS);
            j.put("toFull", bank.ticksToFull() < 0.0f ? -1.0 : bank.ticksToFull() / TPS);
            j.put("demandTotal", bank.totalDemand());
            j.put("shortfall", bank.totalShortfall());
            j.put("managed", bank.managedDownstream());
            j.put("diagnosis", bank.diagnosis() == null ? "" : bank.diagnosis());

            final FeReserveReport.Stance stance = bank.stance();
            j.put("stance", stance == null ? "" : stance.label());
            j.put("stanceBlurb", stance == null ? "" : stance.blurb());
            j.put("rationing", stance != null && stance.isRationing());

            j.name("feeds").beginArray();
            for (final PowerReadings.Feed feed : bank.feeds()) {
                j.beginObject()
                        .put("name", feed.name())
                        .put("flow", feed.flow())
                        .put("tanks", feed.tanks())
                        .put("sources", feed.sources())
                        .put("wired", feed.wired())
                        .endObject();
            }
            j.endArray();

            j.name("circuits").beginArray();
            for (final PowerReadings.Circuit c : bank.circuits()) {
                j.beginObject()
                        .put("name", c.name())
                        .put("flow", c.flow())
                        .put("demand", c.demand())
                        .put("ceiling", c.ceiling())
                        .put("floor", c.floorPct())
                        .put("critical", c.critical())
                        .put("devices", c.devices())
                        .put("panels", c.panels())
                        .put("wired", c.wired())
                        .put("withheld", c.withheld())
                        .put("faulted", c.faulted())
                        .put("reason", c.reasonLabel())
                        .put("mode", c.mode().label())
                        .put("modeBlurb", c.mode().blurb())
                        .put("tier", c.tier().getDisplayName())
                        .endObject();
            }
            j.endArray();

            j.put("dt", cadence(bank.history("energy_net_rate")));
            j.name("h").beginObject();
            series(j, "net", bank.history("energy_net_rate"));
            series(j, "soc", bank.history("energy_fill_pct"));
            series(j, "in", bank.history("energy_input_rate"));
            series(j, "out", bank.history("energy_output_rate"));
            j.endObject();

            j.endObject();
        }
        j.endArray();
    }

    private static void writeMeters(WebJson j, List<SourceProbe.Unit> units) {
        j.name("flow").beginArray();
        for (final SourceProbe.Unit unit : units) {
            final PowerReadings.Meter meter = PowerReadings.readMeter(unit);
            if (meter == null) {
                continue;
            }
            final TimeSeriesStore store = meter.history();
            j.beginObject();
            j.put("id", idOf(unit));
            j.put("label", meter.label());
            j.put("pos", posOf(meter.pos()));

            j.put("rate", meter.rate());
            j.put("total", meter.total());
            j.put("dir", meter.sign());
            j.put("flowing", meter.flowing());
            j.put("bpm", meter.bucketsPerMinute());
            j.put("fluidId", meter.fluidId());
            j.put("fluid", meter.known() ? meter.fluidName() : "");
            j.putColor("color", meter.known()
                    ? FluidPalette.colorForFluidId(meter.fluidId()) : 0xFF5F6773);

            writeMeterStatistics(j, store);

            j.put("dt", cadence(store));
            j.name("h").beginObject();
            series(j, "rate", store);
            series(j, "total", meter.totalHistory());
            j.endObject();

            j.endObject();
        }
        j.endArray();
    }

    private static void writeMeterStatistics(WebJson j, TimeSeriesStore store) {
        if (store == null || store.getSampleCount() < 1) {
            j.put("min", 0.0).put("avg", 0.0).put("max", 0.0).put("sd", 0.0)
                    .put("duty", 0.0).put("steady", 0.0).put("samples", 0)
                    .name("bins").beginArray().endArray();
            return;
        }
        final float[] data = store.getData();
        final float max = store.getMax();
        final float avg = store.getAverage();
        final float sd = store.getStdDev();

        int flowing = 0;
        final int[] bins = new int[HISTOGRAM_BINS];
        final float span = Math.max(1.0e-4f, max);
        for (final float v : data) {
            if (v > 0.0f) {
                flowing++;
            }
            final int b = Math.max(0, Math.min(HISTOGRAM_BINS - 1,
                    (int) (v / span * (HISTOGRAM_BINS - 1))));
            bins[b]++;
        }

        j.put("min", store.getMin());
        j.put("avg", avg);
        j.put("max", max);
        j.put("sd", sd);
        j.put("samples", store.getSampleCount());
        j.put("duty", data.length == 0 ? 0.0f : flowing * 100.0f / data.length);

        j.put("steady", avg <= 0.0f ? 0.0f
                : Math.max(0.0f, Math.min(100.0f, 100.0f * (1.0f - Math.min(1.0f, sd / avg)))));
        j.name("bins").beginArray();
        for (final int count : bins) {
            j.value(count);
        }
        j.endArray();
    }

    private static void series(WebJson j, String key, TimeSeriesStore store) {
        j.name(key).beginArray();
        if (store != null && store.getSampleCount() > 0) {
            final float[] window = store.getDataWindow(WebSnapshot.HISTORY_CAP);
            if (window != null) {
                for (final float sample : window) {
                    j.value(sample);
                }
            }
        }
        j.endArray();
    }

    private static double cadence(TimeSeriesStore store) {
        return store == null ? 1.0 : Math.max(1, store.getSampleIntervalTicks()) / (double) TPS;
    }

    private static String idOf(SourceProbe.Unit unit) {
        return unit.config() == null || unit.config().getSourceId() == null
                ? "" : unit.config().getSourceId();
    }

    private static String posOf(BlockPos pos) {
        return pos == null ? "" : pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
