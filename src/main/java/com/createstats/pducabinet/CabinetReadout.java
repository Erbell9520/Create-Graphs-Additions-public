package com.createstats.pducabinet;

import java.util.List;

public record CabinetReadout(String mode, int inputA, int inputB, int inputLimit,
                             List<Port> ports, List<Long> history, String event, long eventTick) {
    public static final int PORTS = 9;

    public CabinetReadout {
        ports = List.copyOf(ports);
        history = List.copyOf(history);
        if (ports.size() != PORTS) throw new IllegalArgumentException("Eight outputs plus BUS required");
        inputA = Math.max(0, inputA);
        inputB = Math.max(0, inputB);
        inputLimit = Math.max(1, inputLimit);
        mode = mode == null ? "OFF" : mode;
        event = event == null ? "WAITING FOR OUTPUT" : event;
    }

    public record Port(int delivered, int intake, int rating, int requested, boolean wired,
                       boolean closed, boolean tripped, boolean limited, boolean shed) {
        public Port {
            delivered = Math.max(0, delivered);
            intake = Math.max(0, intake);
            rating = Math.max(0, rating);
            requested = Math.max(0, requested);
        }

        public String state() {
            if (!wired) return "NO WIRE";
            if (tripped) return "TRIP";
            if (!closed) return "OPEN";
            if (limited) return "WIRE";
            if (shed) return delivered > 0 ? "PARTIAL" : "SHED";
            if (delivered > 0) return "LIVE";
            return intake > 0 ? "FEED" : "IDLE";
        }

        public double fraction() {
            return rating > 0 ? Math.min(1, (double) delivered / rating) : 0;
        }

        public boolean warning() { return tripped || limited || shed; }
    }

    public long total() { return ports.stream().mapToLong(Port::delivered).sum(); }
    public int flags() { return (int) ports.stream().filter(Port::warning).count(); }
    public boolean trip() { return ports.stream().anyMatch(Port::tripped); }
    public double wireFraction() { return Math.min(1, (double) total() / (2L * inputLimit)); }
    public int wired() { return (int) ports.subList(0, 8).stream().filter(Port::wired).count(); }
    public int flowing() { return (int) ports.subList(0, 8).stream().filter(p -> p.delivered > 0).count(); }
    public int trips() { return (int) ports.subList(0, 8).stream().filter(Port::tripped).count(); }

    public static CabinetReadout empty() {
        return new CabinetReadout("OFF", 0, 0, 65536,
                java.util.Collections.nCopies(PORTS, new Port(0, 0, 0, 0, false, true, false, false, false)),
                List.of(), "WAITING FOR OUTPUT", 0);
    }
}
