package com.createstats.book.content;

import java.util.ArrayList;
import java.util.List;

public final class Scenes {
    public record Row(Section section, String itemId, String labelKey, List<String> alsoOn) {
        public Row {
            alsoOn = List.copyOf(alsoOn);
        }

        public boolean covers(String id) {
            return itemId.equals(id) || alsoOn.contains(id);
        }
    }

    private static final String P = "create_stats:";

    private static final List<Row> ROWS = List.of(

            new Row(Section.POWER, P + "surface_cable", "create_stats.ponder.fe_cables.header",
                    List.of(P + "cable_tray", P + "bus_duct", P + "busbar_trunk")),

            new Row(Section.POWER, P + "capacitor_bank", "create_stats.ponder.fe_capacitor_bank.header", List.of()),

            new Row(Section.POWER, P + "fe_battery_controller", "create_stats.book.ponder.battery_controller",
                    List.of()),

            new Row(Section.POWER, P + "power_connector", "create_stats.ponder.fe_wires.header",
                    List.of(P + "advanced_power_connector", P + "industrial_power_connector", P + "copper_wire",
                            P + "insulated_wire", P + "shielded_wire", P + "hv_line")),

            new Row(Section.POWER, P + "power_distribution_unit", "create_stats.book.ponder.pdu", List.of()),

            new Row(Section.POWER, P + "power_track", "create_stats.book.ponder.power_track",
                    List.of(P + "section_insulator")),

            new Row(Section.POWER, P + "track_feeder", "create_stats.book.ponder.track_feeder", List.of()),

            new Row(Section.POWER, P + "current_collector", "create_stats.ponder.power_track_collector.header",
                    List.of()),

            new Row(Section.POWER, P + "storm_coil", "create_stats.ponder.storm_coil.header", List.of()),

            new Row(Section.POWER, P + "spark_crown", "create_stats.ponder.spark_crown.header", List.of()),

            new Row(Section.POWER, P + "universal_power_coupler", "create_stats.ponder.universal_power_coupler.header",
                    List.of()),

            new Row(Section.MACHINES, P + "seismic_scanner", "create_stats.book.ponder.seismic_scanner", List.of()),

            new Row(Section.TRANSPORT, P + "vcc_mk4_aerial", "create_stats.book.ponder.vcc_mk4_aerial", List.of()));

    private Scenes() {
    }

    public static List<Row> all() {
        return ROWS;
    }

    public static List<Row> rows(Section section, List<String> withScenes) {
        final List<Row> out = new ArrayList<>();
        for (final String id : withScenes) {
            Row hit = null;
            for (final Row r : ROWS) {
                if (r.covers(id) && withScenes.contains(r.itemId())) {
                    hit = r;
                    break;
                }
            }
            if (hit == null) {
                hit = new Row(section, id, null, List.of());
            }
            if (!out.contains(hit)) {
                out.add(hit);
            }
        }
        return out;
    }

    public static boolean covers(String itemId) {
        for (final Row r : ROWS) {
            if (r.covers(itemId)) {
                return true;
            }
        }
        return false;
    }
}
