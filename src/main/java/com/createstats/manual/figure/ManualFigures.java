package com.createstats.manual.figure;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** [1] */
public final class ManualFigures {

    private ManualFigures() {
    }

    private static final Map<String, ManualFigure> FIGURES = new HashMap<>();

    static {
        register(miniDisplay());
        register(flowMeter());
        register(holographicProjector());
        register(statsRecorder());
        register(memoryCog());
        register(statsHandheld());
        register(calculator());
        register(factoryLogicController());
        register(machineSelectionCard());
        register(clutchLinkCard());
        register(controlClutch());
        register(safetyClutch());
        register(autoVariableClutch());
        register(loadBalancer());
        register(smartGearshift());
        register(overflowGate());
        register(kineticFlywheel());
        register(aeroStatsLink());
        register(aeroSourceMatrix());
        register(contraptionInfo());
        register(aeroFlightDeck());
        register(aerolineDirector());
        register(hotAirBurnerTelemetry());
        register(rpmGovernor());
        register(pathScanner());
        register(pathfinderComputer());
        register(damageObserver());
        register(battleBridge());
        register(statsConfigurator());
        register(signalAntennas());
        register(statsAlarm());
        register(tableAlarmClock());
        register(statusTowerLight());
        register(freightManifestClipboard());
        register(dispatchBoard());
    }

    private static void register(ManualFigure figure) {
        FIGURES.put(figure.pageId(), figure);
    }

    public static boolean has(String pageId) {
        return FIGURES.containsKey(pageId);
    }

    public static int sheetCount(String pageId) {
        ManualFigure figure = FIGURES.get(pageId);
        return figure == null ? 0 : figure.sheets().size();
    }

    public static FigureSheet sheet(String pageId, int index) {
        ManualFigure figure = FIGURES.get(pageId);
        if (figure == null || index < 0 || index >= figure.sheets().size()) {
            return null;
        }
        return figure.sheets().get(index);
    }

    // lessons

    private static ManualFigure miniDisplay() {
        FigureSheet flow = FigureSheet.builder("1. A compact wall readout")
                .block("machine", 0.04f, 0.30f, 0.28f, 0.42f, "Machine", "beneath / linked", FigurePalette.BRASS)
                .panel("mini", 0.46f, 0.24f, 0.30f, 0.52f, "Mini Display", "items, fluid, kinetic", FigurePalette.TEAL)
                .arrow("machine", "mini", FigurePalette.BRASS)
                .noteCenter(0.5f, 0.90f, "Shows storage, fluids, boiler, and kinetic data at a glance.",
                        FigurePalette.INK)
                .build();

        FigureSheet modes = FigureSheet.builder("2. Local or linked")
                .block("under", 0.02f, 0.06f, 0.26f, 0.30f, "Local", "machine beneath", FigurePalette.GREEN)
                .block("link", 0.02f, 0.62f, 0.26f, 0.30f, "Linked", "Display Link", FigurePalette.PLUM)
                .panel("mini", 0.44f, 0.30f, 0.30f, 0.40f, "Mini Display", "relays source", FigurePalette.TEAL)
                .arrow("under", "mini", FigurePalette.GREEN)
                .arrow("link", "mini", FigurePalette.PLUM)
                .note(0.02f, 0.94f, "Right-click to choose what it tracks.", FigurePalette.INK)
                .build();

        FigureSheet merge = FigureSheet.builder("3. Merge & filter")
                .panel("left", 0.10f, 0.24f, 0.28f, 0.40f, "Mini", "left half", FigurePalette.TEAL)
                .panel("right", 0.40f, 0.24f, 0.28f, 0.40f, "Mini", "right half", FigurePalette.TEAL)
                .chip(0.72f, 0.34f, "= 2x1 wide", FigurePalette.AMBER)
                .noteCenter(0.5f, 0.74f, "Two side by side merge into one wider display.", FigurePalette.INK)
                .note(0.02f, 0.92f, "Hold a filter item while configuring to pin one item.", FigurePalette.INK)
                .build();

        return new ManualFigure("mini_display", List.of(flow, modes, merge));
    }

    private static ManualFigure flowMeter() {
        FigureSheet basics = FigureSheet.builder("1. A speed sign for pipes")
                .block("pump", 0.02f, 0.32f, 0.22f, 0.36f, "Pump", "pushes fluid", FigurePalette.BRASS)
                .panel("meter", 0.38f, 0.24f, 0.24f, 0.50f, "Flow Meter", "counts the flow", FigurePalette.TEAL)
                .block("tank", 0.76f, 0.32f, 0.22f, 0.36f, "Tank", "fluid lands here", FigurePalette.SKY)
                .arrow("pump", "meter", FigurePalette.BRASS)
                .arrow("meter", "tank", FigurePalette.TEAL)
                .noteCenter(0.5f, 0.84f, "Put it in a pipe. Fluid flows through like normal, and it counts the speed.",
                        FigurePalette.INK)
                .note(0.02f, 0.95f, "Tank ahead full? Fluid stops, so it reads Idle, not Flowing.", FigurePalette.INK)
                .build();

        FigureSheet read = FigureSheet.builder("2. See the numbers")
                .panel("meter", 0.02f, 0.34f, 0.22f, 0.34f, "Flow Meter", "on the pipe", FigurePalette.TEAL)
                .block("link", 0.30f, 0.36f, 0.20f, 0.30f, "Display Link", "aim at screen", FigurePalette.GREEN)
                .panel("screen", 0.58f, 0.12f, 0.40f, 0.60f, "Stats Screen", "Specialized mode", FigurePalette.PLUM)
                .arrow("meter", "link", FigurePalette.TEAL)
                .arrow("link", "screen", FigurePalette.GREEN)
                .chip(0.62f, 0.24f, "Speed mB/t", FigurePalette.AMBER)
                .chip(0.62f, 0.40f, "Fluid & way", FigurePalette.SKY)
                .chip(0.62f, 0.56f, "Total moved", FigurePalette.OLIVE)
                .noteCenter(0.5f, 0.90f, "The screen shows the speed, the fluid, the way it goes, and the total.",
                        FigurePalette.INK)
                .build();

        FigureSheet trigger = FigureSheet.builder("3. Alarm & redstone")
                .panel("meter", 0.02f, 0.34f, 0.22f, 0.34f, "Flow Meter", "watches flow", FigurePalette.TEAL)
                .block("rule", 0.30f, 0.34f, 0.22f, 0.34f, "Your rule", "Flowing / Amount", FigurePalette.AMBER)
                .block("alarm", 0.60f, 0.06f, 0.36f, 0.30f, "Alarm", "warns you", FigurePalette.DANGER)
                .block("redstone", 0.60f, 0.52f, 0.36f, 0.32f, "Redstone", "screen AND pipe", FigurePalette.RUST)
                .arrow("meter", "rule", FigurePalette.TEAL)
                .arrow("rule", "alarm", FigurePalette.DANGER)
                .arrow("rule", "redstone", FigurePalette.RUST)
                .noteCenter(0.5f, 0.92f, "Turn it on in the screen. It can beep and send redstone when your rule is met.",
                        FigurePalette.INK)
                .build();

        return new ManualFigure("flow_meter", List.of(basics, read, trigger));
    }

    private static ManualFigure holographicProjector() {
        FigureSheet flow = FigureSheet.builder("1. Your factory dashboard")
                .block("sources", 0.02f, 0.28f, 0.26f, 0.44f, "Sources", "machines", FigurePalette.BRASS)
                .block("proj", 0.38f, 0.24f, 0.26f, 0.52f, "Projector", "central display", FigurePalette.PLUM)
                .panel("holo", 0.74f, 0.24f, 0.24f, 0.52f, "Holograms", "floating readouts", FigurePalette.SKY)
                .arrow("sources", "proj", FigurePalette.BRASS)
                .arrow("proj", "holo", FigurePalette.PLUM)
                .noteCenter(0.5f, 0.90f, "Renders the channels you pick as floating holograms.",
                        FigurePalette.INK)
                .build();

        FigureSheet feed = FigureSheet.builder("2. Two ways to feed it")
                .block("link", 0.02f, 0.06f, 0.30f, 0.30f, "Display Link", "send a channel", FigurePalette.GREEN)
                .block("scan", 0.02f, 0.60f, 0.30f, 0.32f, "Scanner", "gauges, tanks, stations", FigurePalette.AMBER)
                .block("proj", 0.48f, 0.30f, 0.28f, 0.40f, "Projector", "collects both", FigurePalette.PLUM)
                .arrow("link", "proj", FigurePalette.GREEN)
                .arrow("scan", "proj", FigurePalette.AMBER)
                .note(0.02f, 0.94f, "Enable the built-in scanner to auto-pick nearby supported blocks.",
                        FigurePalette.INK)
                .build();

        FigureSheet outputs = FigureSheet.builder("3. Tune & output")
                .block("proj", 0.04f, 0.30f, 0.26f, 0.40f, "Projector", "control room", FigurePalette.PLUM)
                .panel("holo", 0.40f, 0.08f, 0.30f, 0.34f, "Holograms", "glance readouts", FigurePalette.SKY)
                .block("redstone", 0.40f, 0.56f, 0.30f, 0.32f, "Redstone", "from a channel", FigurePalette.RUST)
                .arrow("proj", "holo", FigurePalette.SKY)
                .arrow("proj", "redstone", FigurePalette.RUST)
                .chip(0.74f, 0.34f, "Stats Configurator", FigurePalette.TEAL)
                .noteCenter(0.5f, 0.94f, "Place it on a solid block where you want a live dashboard.",
                        FigurePalette.INK)
                .build();

        return new ManualFigure("holographic_projector", List.of(flow, feed, outputs));
    }

    private static ManualFigure statsRecorder() {
        FigureSheet flow = FigureSheet.builder("1. The capture box")
                .block("source", 0.02f, 0.30f, 0.26f, 0.40f, "Source", "a stat to log", FigurePalette.BRASS)
                .panel("rec", 0.40f, 0.22f, 0.28f, 0.56f, "Recorder", "stores samples", FigurePalette.OLIVE)
                .arrow("source", "rec", FigurePalette.BRASS)
                .chip(0.74f, 0.34f, "armed / recording", FigurePalette.GREEN)
                .noteCenter(0.5f, 0.90f, "Right-click empty-handed to see armed, recording, paused, or waiting.",
                        FigurePalette.INK)
                .build();

        FigureSheet sources = FigureSheet.builder("2. Two ways to capture")
                .block("link", 0.02f, 0.06f, 0.30f, 0.30f, "Display Link", "sends data in", FigurePalette.GREEN)
                .block("panel", 0.02f, 0.60f, 0.34f, 0.32f, "Near a Stats Display", "auto-mirrors it", FigurePalette.TEAL)
                .panel("rec", 0.50f, 0.30f, 0.28f, 0.40f, "Recorder", "arms itself", FigurePalette.OLIVE)
                .arrow("link", "rec", FigurePalette.GREEN)
                .arrow("panel", "rec", FigurePalette.TEAL)
                .note(0.02f, 0.94f, "Placed beside a panel, it mirrors that panel's main channel.",
                        FigurePalette.INK)
                .build();

        FigureSheet save = FigureSheet.builder("3. Save a snapshot")
                .panel("rec", 0.04f, 0.30f, 0.28f, 0.40f, "Recorder", "has samples", FigurePalette.OLIVE)
                .block("cog", 0.42f, 0.08f, 0.24f, 0.30f, "Empty Cog", "in hand", FigurePalette.AMBER)
                .block("filled", 0.70f, 0.50f, 0.26f, 0.34f, "Filled Cog", "portable snapshot", FigurePalette.BRASS)
                .arrow("rec", "filled", FigurePalette.OLIVE)
                .arrow("cog", "filled", FigurePalette.AMBER)
                .noteCenter(0.5f, 0.94f, "Interact with an empty Memory Cog to copy the stored samples.",
                        FigurePalette.INK)
                .build();

        return new ManualFigure("stats_recorder", List.of(flow, sources, save));
    }

    private static ManualFigure memoryCog() {
        FigureSheet flow = FigureSheet.builder("1. Portable stat memory")
                .panel("rec", 0.02f, 0.30f, 0.26f, 0.40f, "Recorder", "with samples", FigurePalette.OLIVE)
                .block("cog", 0.40f, 0.28f, 0.22f, 0.44f, "Memory Cog", "carries data", FigurePalette.AMBER)
                .panel("hand", 0.72f, 0.26f, 0.26f, 0.48f, "Handheld", "replays graph", FigurePalette.TEAL)
                .arrow("rec", "cog", FigurePalette.OLIVE)
                .arrow("cog", "hand", FigurePalette.AMBER)
                .noteCenter(0.5f, 0.90f, "Record once, then carry the graph anywhere in a cog.", FigurePalette.INK)
                .build();

        FigureSheet use = FigureSheet.builder("2. Fill & replay")
                .chip(0.02f, 0.08f, "Empty cog on Recorder = write", FigurePalette.AMBER)
                .chip(0.02f, 0.32f, "Filled cog glints + tooltip", FigurePalette.BRASS)
                .chip(0.02f, 0.56f, "Off-hand + open Handheld = replay", FigurePalette.TEAL)
                .note(0.02f, 0.84f, "Tooltip shows metric, samples, source, position, dimension.",
                        FigurePalette.INK)
                .build();

        return new ManualFigure("memory_cog", List.of(flow, use));
    }

    private static ManualFigure statsHandheld() {
        FigureSheet flow = FigureSheet.builder("1. A pocket terminal")
                .block("target", 0.02f, 0.30f, 0.30f, 0.40f, "Linked target", "panel / recorder / board", FigurePalette.BRASS)
                .panel("hand", 0.46f, 0.22f, 0.30f, 0.56f, "Handheld", "live graphs", FigurePalette.TEAL)
                .arrow("target", "hand", FigurePalette.BRASS)
                .noteCenter(0.5f, 0.90f, "Carry a remote view of a machine in your hand.", FigurePalette.INK)
                .build();

        FigureSheet link = FigureSheet.builder("2. Link, then open")
                .chip(0.02f, 0.10f, "1. Use it on a target = link", FigurePalette.GREEN)
                .chip(0.02f, 0.34f, "2. Right-click air = open", FigurePalette.TEAL)
                .block("target", 0.04f, 0.56f, 0.28f, 0.34f, "Target", "panel / recorder", FigurePalette.BRASS)
                .panel("hand", 0.44f, 0.54f, 0.30f, 0.38f, "Handheld", "shows it", FigurePalette.TEAL)
                .arrow("target", "hand", FigurePalette.BRASS)
                .note(0.02f, 0.96f, "Reports if the link is missing, out of range, or in another dimension.",
                        FigurePalette.INK)
                .build();

        FigureSheet reach = FigureSheet.builder("3. Extend the reach")
                .block("antenna", 0.02f, 0.08f, 0.30f, 0.30f, "Antennas", "extend range", FigurePalette.AMBER)
                .block("trt", 0.02f, 0.60f, 0.30f, 0.32f, "TRT target", "bridge dimensions", FigurePalette.PLUM)
                .panel("hand", 0.50f, 0.30f, 0.28f, 0.40f, "Handheld", "farther reach", FigurePalette.TEAL)
                .arrow("antenna", "hand", FigurePalette.AMBER)
                .arrow("trt", "hand", FigurePalette.PLUM)
                .note(0.02f, 0.94f, "Hold a filled Memory Cog in the off hand to replay a stored graph.",
                        FigurePalette.INK)
                .build();

        return new ManualFigure("stats_handheld", List.of(flow, link, reach));
    }

    private static ManualFigure calculator() {
        FigureSheet flow = FigureSheet.builder("1. Pocket math block")
                .block("buttons", 0.02f, 0.28f, 0.28f, 0.44f, "Buttons", "build expression", FigurePalette.BRASS)
                .panel("display", 0.42f, 0.22f, 0.32f, 0.56f, "Display", "result or error", FigurePalette.GREEN)
                .arrow("buttons", "display", FigurePalette.BRASS)
                .chip(0.80f, 0.34f, "click = full screen", FigurePalette.TEAL)
                .noteCenter(0.5f, 0.90f, "C and backspace edit; recent calculations are stored.",
                        FigurePalette.INK)
                .build();

        FigureSheet panel = FigureSheet.builder("2. Build a big panel")
                .block("a", 0.06f, 0.14f, 0.24f, 0.22f, "Calc", "", FigurePalette.OLIVE)
                .block("b", 0.34f, 0.14f, 0.24f, 0.22f, "Calc", "", FigurePalette.OLIVE)
                .block("c", 0.62f, 0.14f, 0.24f, 0.22f, "Calc", "", FigurePalette.OLIVE)
                .block("d", 0.06f, 0.42f, 0.24f, 0.22f, "Calc", "", FigurePalette.OLIVE)
                .block("e", 0.34f, 0.42f, 0.24f, 0.22f, "Calc", "", FigurePalette.OLIVE)
                .block("f", 0.62f, 0.42f, 0.24f, 0.22f, "Calc", "", FigurePalette.OLIVE)
                .noteCenter(0.5f, 0.74f, "Sneak-place a matching Calculator to auto-fill a 2x3 panel.",
                        FigurePalette.INK)
                .build();

        FigureSheet sci = FigureSheet.builder("3. Scientific mode")
                .chip(0.02f, 0.12f, "x, powers", FigurePalette.SKY)
                .chip(0.40f, 0.12f, "pi, e", FigurePalette.PLUM)
                .chip(0.70f, 0.12f, "functions", FigurePalette.AMBER)
                .panel("graph", 0.10f, 0.36f, 0.80f, 0.48f, "Graph", "plot formulas", FigurePalette.GREEN)
                .noteCenter(0.5f, 0.92f, "Unlocks graphing and advanced formulas.", FigurePalette.INK)
                .build();

        return new ManualFigure("calculator", List.of(flow, panel, sci));
    }

    private static ManualFigure factoryLogicController() {
        FigureSheet flow = FigureSheet.builder("1. Stop idle branches")
                .block("machines", 0.02f, 0.28f, 0.28f, 0.44f, "Watched", "presses, mixers", FigurePalette.BRASS)
                .panel("flc", 0.38f, 0.22f, 0.26f, 0.56f, "Controller", "idle timer", FigurePalette.RUST)
                .block("clutch", 0.74f, 0.28f, 0.24f, 0.44f, "Control Clutch", "RUN / STOP", FigurePalette.TEAL)
                .arrow("machines", "flc", FigurePalette.BRASS)
                .arrow("flc", "clutch", FigurePalette.RUST)
                .noteCenter(0.5f, 0.90f, "Idle too long? It STOPs the branch; work resumes -> RUN.",
                        FigurePalette.INK)
                .build();

        FigureSheet row = FigureSheet.builder("2. One row = two cards")
                .block("sel", 0.04f, 0.12f, 0.40f, 0.30f, "Machine Selection", "what to watch", FigurePalette.AMBER)
                .block("link", 0.04f, 0.50f, 0.40f, 0.30f, "Clutch Link", "which clutch", FigurePalette.GREEN)
                .panel("flc", 0.58f, 0.26f, 0.30f, 0.44f, "Controller row", "pairs them", FigurePalette.RUST)
                .arrow("sel", "flc", FigurePalette.AMBER)
                .arrow("link", "flc", FigurePalette.GREEN)
                .noteCenter(0.5f, 0.92f, "Each row needs both cards together.", FigurePalette.INK)
                .build();

        FigureSheet timing = FigureSheet.builder("3. Timing & states")
                .chip(0.02f, 0.10f, "+/- sets idle time", FigurePalette.RUST)
                .chip(0.02f, 0.34f, "Pause: off until Reset", FigurePalette.AMBER)
                .chip(0.02f, 0.58f, "Work resumes = RUN", FigurePalette.GREEN)
                .note(0.02f, 0.86f, "Mount a TRT Signal Antenna for Overworld<->Nether clutch links.",
                        FigurePalette.INK)
                .build();

        return new ManualFigure("factory_logic_controller", List.of(flow, row, timing));
    }

    private static ManualFigure machineSelectionCard() {
        FigureSheet flow = FigureSheet.builder("1. Pick what to watch")
                .block("machine", 0.04f, 0.30f, 0.28f, 0.40f, "Machine", "sneak-click to store type", FigurePalette.BRASS)
                .block("card", 0.48f, 0.26f, 0.30f, 0.48f, "Selection Card", "remembers the type", FigurePalette.AMBER)
                .arrow("machine", "card", FigurePalette.BRASS)
                .noteCenter(0.5f, 0.90f, "Sneak + right-click a machine to store its type.", FigurePalette.INK)
                .build();

        FigureSheet area = FigureSheet.builder("2. Mark the area")
                .block("a", 0.06f, 0.16f, 0.22f, 0.26f, "Corner A", "click 1", FigurePalette.GREEN)
                .block("b", 0.70f, 0.54f, 0.22f, 0.26f, "Corner B", "click 2", FigurePalette.GREEN)
                .block("box", 0.34f, 0.34f, 0.30f, 0.30f, "Watched box", "the region", FigurePalette.AMBER)
                .arrow("a", "box", FigurePalette.GREEN)
                .arrow("b", "box", FigurePalette.GREEN)
                .note(0.02f, 0.92f, "Hold the card to preview; pair it with the Clutch Link Card in one row.",
                        FigurePalette.INK)
                .build();

        return new ManualFigure("machine_selection_card", List.of(flow, area));
    }

    private static ManualFigure clutchLinkCard() {
        FigureSheet flow = FigureSheet.builder("1. Name the clutch")
                .block("clutch", 0.04f, 0.30f, 0.28f, 0.40f, "Control Clutch", "right-click to store", FigurePalette.TEAL)
                .block("card", 0.42f, 0.28f, 0.26f, 0.44f, "Link Card", "holds its position", FigurePalette.GREEN)
                .panel("flc", 0.74f, 0.28f, 0.24f, 0.44f, "Controller", "commands it", FigurePalette.RUST)
                .arrow("clutch", "card", FigurePalette.TEAL)
                .arrow("card", "flc", FigurePalette.GREEN)
                .noteCenter(0.5f, 0.90f, "Tells one row which clutch it may command.", FigurePalette.INK)
                .build();

        FigureSheet controls = FigureSheet.builder("2. Use it")
                .chip(0.02f, 0.12f, "Right-click clutch = store", FigurePalette.TEAL)
                .chip(0.02f, 0.36f, "Pair with Selection Card", FigurePalette.AMBER)
                .chip(0.02f, 0.60f, "Sneak-click air = clear", FigurePalette.RUST)
                .note(0.02f, 0.86f, "Normal setup: Controller + Selection Card + Control Clutch.",
                        FigurePalette.INK)
                .build();

        return new ManualFigure("safety_clutch_link_card", List.of(flow, controls));
    }

    private static ManualFigure controlClutch() {
        FigureSheet flow = FigureSheet.builder("1. The branch switch")
                .block("power", 0.02f, 0.30f, 0.22f, 0.40f, "Power", "shaft line", FigurePalette.BRASS)
                .block("clutch", 0.34f, 0.24f, 0.28f, 0.52f, "Control Clutch", "RUN / STOP gate", FigurePalette.TEAL)
                .block("machines", 0.72f, 0.30f, 0.26f, 0.40f, "Machines", "downstream", FigurePalette.GREEN)
                .arrow("power", "clutch", FigurePalette.BRASS)
                .arrow("clutch", "machines", FigurePalette.TEAL)
                .noteCenter(0.5f, 0.90f, "It does not tune speed - it only follows RUN/STOP.", FigurePalette.INK)
                .build();

        FigureSheet wire = FigureSheet.builder("2. Wire it up")
                .block("card", 0.04f, 0.12f, 0.34f, 0.30f, "Clutch Link Card", "right-click to link", FigurePalette.GREEN)
                .panel("flc", 0.04f, 0.54f, 0.34f, 0.30f, "Controller", "inserts the card", FigurePalette.RUST)
                .block("clutch", 0.54f, 0.30f, 0.30f, 0.40f, "Control Clutch", "obeys the row", FigurePalette.TEAL)
                .arrow("card", "clutch", FigurePalette.GREEN)
                .arrow("flc", "clutch", FigurePalette.RUST)
                .note(0.02f, 0.92f, "Comparator / redstone output reports status outside the menu.",
                        FigurePalette.INK)
                .build();

        return new ManualFigure("control_clutch", List.of(flow, wire));
    }

    private static ManualFigure safetyClutch() {
        FigureSheet flow = FigureSheet.builder("1. A kinetic safety valve")
                .block("power", 0.02f, 0.30f, 0.22f, 0.40f, "Network", "stress flows", FigurePalette.BRASS)
                .block("clutch", 0.34f, 0.22f, 0.28f, 0.56f, "Safety Clutch", "monitors stress", FigurePalette.DANGER)
                .block("machines", 0.72f, 0.30f, 0.26f, 0.40f, "Machines", "protected", FigurePalette.GREEN)
                .arrow("power", "clutch", FigurePalette.BRASS)
                .arrow("clutch", "machines", FigurePalette.DANGER)
                .noteCenter(0.5f, 0.90f, "On overload it trips and cuts power downstream.", FigurePalette.INK)
                .build();

        FigureSheet settings = FigureSheet.builder("2. Trip settings")
                .chip(0.02f, 0.12f, "Threshold: trip point", FigurePalette.DANGER)
                .chip(0.02f, 0.36f, "Hysteresis: re-arm gap", FigurePalette.AMBER)
                .chip(0.02f, 0.60f, "Latched, or auto-reset", FigurePalette.GREEN)
                .note(0.02f, 0.86f, "Place inline anywhere a machine line should fail safe.",
                        FigurePalette.INK)
                .build();

        return new ManualFigure("safety_clutch", List.of(flow, settings));
    }

    private static ManualFigure autoVariableClutch() {
        FigureSheet flow = FigureSheet.builder("1. Self-tuning gear")
                .block("source", 0.02f, 0.30f, 0.22f, 0.40f, "Source", "raw power", FigurePalette.BRASS)
                .block("clutch", 0.34f, 0.22f, 0.28f, 0.56f, "Auto Clutch", "stress governor", FigurePalette.PLUM)
                .block("machines", 0.72f, 0.30f, 0.26f, 0.40f, "Machines", "steady load", FigurePalette.GREEN)
                .arrow("source", "clutch", FigurePalette.BRASS)
                .arrow("clutch", "machines", FigurePalette.PLUM)
                .noteCenter(0.5f, 0.90f, "It changes ratio on its own to hold a target stress.",
                        FigurePalette.INK)
                .build();

        FigureSheet react = FigureSheet.builder("2. How it reacts")
                .block("low", 0.04f, 0.12f, 0.34f, 0.28f, "Stress low", "speeds up", FigurePalette.GREEN)
                .block("high", 0.04f, 0.52f, 0.34f, 0.28f, "Stress high", "slows down", FigurePalette.DANGER)
                .panel("band", 0.54f, 0.28f, 0.34f, 0.44f, "Target band", "min/max RPM", FigurePalette.PLUM)
                .arrow("low", "band", FigurePalette.GREEN)
                .arrow("high", "band", FigurePalette.DANGER)
                .note(0.02f, 0.92f, "If power drops out it holds neutral until it returns.",
                        FigurePalette.INK)
                .build();

        return new ManualFigure("auto_transmission_clutch", List.of(flow, react));
    }

    private static ManualFigure loadBalancer() {
        FigureSheet flow = FigureSheet.builder("1. Split one shaft in two")
                .block("power", 0.38f, 0.04f, 0.24f, 0.24f, "Power", "rear face", FigurePalette.BRASS)
                .panel("bal", 0.36f, 0.36f, 0.28f, 0.30f, "Balancer", "divides load", FigurePalette.OLIVE)
                .block("left", 0.04f, 0.70f, 0.30f, 0.24f, "Left branch", "a share", FigurePalette.GREEN)
                .block("right", 0.66f, 0.70f, 0.30f, 0.24f, "Right branch", "a share", FigurePalette.SKY)
                .arrow("power", "bal", FigurePalette.BRASS)
                .arrow("bal", "left", FigurePalette.GREEN)
                .arrow("bal", "right", FigurePalette.SKY)
                .noteCenter(0.5f, 0.74f, "Rear takes power; left & right feed the two branches.",
                        FigurePalette.INK)
                .build();

        FigureSheet modes = FigureSheet.builder("2. Strict or Elastic")
                .block("strict", 0.04f, 0.16f, 0.42f, 0.32f, "Strict", "each keeps its share", FigurePalette.SKY)
                .block("elastic", 0.04f, 0.56f, 0.42f, 0.32f, "Elastic", "borrows headroom", FigurePalette.GREEN)
                .note(0.04f, 0.94f, "Set a stress band and the split; it reports missing input or bad topology.",
                        FigurePalette.INK)
                .build();

        return new ManualFigure("load_balancer", List.of(flow, modes));
    }

    private static ManualFigure smartGearshift() {
        FigureSheet flow = FigureSheet.builder("1. Redstone speed presets")
                .block("in", 0.02f, 0.30f, 0.22f, 0.40f, "Input", "any RPM", FigurePalette.BRASS)
                .panel("gear", 0.34f, 0.22f, 0.28f, 0.56f, "Smart Gearshift", "redstone picks stage", FigurePalette.SKY)
                .block("out", 0.72f, 0.30f, 0.26f, 0.40f, "Output", "target RPM", FigurePalette.GREEN)
                .arrow("in", "gear", FigurePalette.BRASS)
                .arrow("gear", "out", FigurePalette.SKY)
                .noteCenter(0.5f, 0.90f, "Power level 0-15 selects the speed stage.", FigurePalette.INK)
                .build();

        FigureSheet stages = FigureSheet.builder("2. 16 stages")
                .chip(0.02f, 0.12f, "Level 0 = pass through", FigurePalette.GREEN)
                .chip(0.02f, 0.36f, "Levels 1-15 = 8..256 RPM", FigurePalette.SKY)
                .chip(0.02f, 0.60f, "Never exceeds 256 RPM", FigurePalette.AMBER)
                .note(0.02f, 0.86f, "A Display Link can show stage, target, source RPM, and direction.",
                        FigurePalette.INK)
                .build();

        return new ManualFigure("smart_gearshift", List.of(flow, stages));
    }

    private static ManualFigure overflowGate() {
        FigureSheet flow = FigureSheet.builder("1. A smart belt splitter")
                .block("belt", 0.02f, 0.34f, 0.24f, 0.32f, "Belt in", "buffered stack", FigurePalette.BRASS)
                .panel("gate", 0.34f, 0.30f, 0.26f, 0.40f, "Overflow Gate", "sorts to 3 sides", FigurePalette.OLIVE)
                .block("s1", 0.70f, 0.06f, 0.26f, 0.22f, "Side", "filtered", FigurePalette.GREEN)
                .block("s2", 0.70f, 0.39f, 0.26f, 0.22f, "Side", "open", FigurePalette.SKY)
                .block("s3", 0.70f, 0.72f, 0.26f, 0.22f, "Side", "overflow", FigurePalette.AMBER)
                .arrow("belt", "gate", FigurePalette.BRASS)
                .arrow("gate", "s1", FigurePalette.GREEN)
                .arrow("gate", "s2", FigurePalette.SKY)
                .arrow("gate", "s3", FigurePalette.AMBER)
                .build();

        FigureSheet order = FigureSheet.builder("2. Routing priority")
                .chip(0.02f, 0.12f, "1. Matching filtered side", FigurePalette.GREEN)
                .chip(0.02f, 0.36f, "2. Any open side", FigurePalette.SKY)
                .chip(0.02f, 0.60f, "3. Chosen overflow side", FigurePalette.AMBER)
                .note(0.02f, 0.86f, "Right-click to see routing; sneak-click to take back the buffer.",
                        FigurePalette.INK)
                .build();

        return new ManualFigure("overflow_gate", List.of(flow, order));
    }

    private static ManualFigure kineticFlywheel() {
        FigureSheet flow = FigureSheet.builder("1. An SU battery for your shafts")
                .block("surplus", 0.04f, 0.10f, 0.26f, 0.30f, "Surplus", "engines spare SU", FigurePalette.BRASS)
                .panel("wheel", 0.37f, 0.30f, 0.26f, 0.40f, "Flywheel", "SU reserve", FigurePalette.TEAL)
                .block("net", 0.70f, 0.30f, 0.26f, 0.40f, "Network", "your machines", FigurePalette.SKY)
                .arrow("surplus", "wheel", FigurePalette.GREEN)
                .arrow("wheel", "net", FigurePalette.SKY)
                .noteCenter(0.5f, 0.88f, "Banks spare SU as a consumer, then powers the line when engines stop.",
                        FigurePalette.INK)
                .build();

        FigureSheet cycle = FigureSheet.builder("2. Charge, hold, discharge")
                .chip(0.02f, 0.12f, "Surplus power -> CHARGE", FigurePalette.GREEN)
                .chip(0.02f, 0.36f, "Reserve full -> HOLD", FigurePalette.AMBER)
                .chip(0.02f, 0.60f, "Network idle -> DISCHARGE", FigurePalette.SKY)
                .note(0.02f, 0.86f, "Flips on its own: ~180s to fill, ~90s of full-load backup (15 min idle).",
                        FigurePalette.INK)
                .build();

        FigureSheet tune = FigureSheet.builder("3. Tune the wheel")
                .block("charge", 0.04f, 0.12f, 0.42f, 0.30f, "Charge gear", "fill speed 1-3", FigurePalette.GREEN)
                .block("discharge", 0.04f, 0.50f, 0.42f, 0.30f, "Discharge gear", "peak output 1-3", FigurePalette.SKY)
                .chip(0.52f, 0.16f, "Add Flywheels = Mass x1..x4", FigurePalette.PLUM)
                .chip(0.52f, 0.42f, "More mass = more power & reserve", FigurePalette.AMBER)
                .note(0.02f, 0.88f, "RPM sags from 256 as the reserve drains, but torque stays rated.",
                        FigurePalette.INK)
                .build();

        return new ManualFigure("kinetic_flywheel", List.of(flow, cycle, tune));
    }

    private static ManualFigure aeroStatsLink() {
        FigureSheet flow = FigureSheet.builder("1. A bridge to Create Stats")
                .block("aero", 0.02f, 0.28f, 0.30f, 0.44f, "Aeronautics", "flight block", FigurePalette.SKY)
                .block("link", 0.40f, 0.24f, 0.24f, 0.52f, "Display Link", "Stats Link mode", FigurePalette.GREEN)
                .panel("stats", 0.74f, 0.22f, 0.24f, 0.56f, "Create Stats", "panels, dials, lights", FigurePalette.TEAL)
                .arrow("aero", "link", FigurePalette.SKY)
                .arrow("link", "stats", FigurePalette.GREEN)
                .noteCenter(0.5f, 0.90f, "Compatible flight blocks expose telemetry into the Stats network.",
                        FigurePalette.INK)
                .build();

        FigureSheet trust = FigureSheet.builder("2. Trust the in-game list")
                .chip(0.02f, 0.14f, "Targets: panel, recorder, dial", FigurePalette.TEAL)
                .chip(0.02f, 0.38f, "...light, projector", FigurePalette.TEAL)
                .note(0.02f, 0.66f, "Source names are still being locked in.", FigurePalette.INK)
                .note(0.02f, 0.82f, "Trust the Display Link source list in-game over older wording.",
                        FigurePalette.INK)
                .build();

        return new ManualFigure("aero_stats_link", List.of(flow, trust));
    }

    private static ManualFigure aeroSourceMatrix() {
        FigureSheet flow = FigureSheet.builder("1. What an airship can share")
                .block("motion", 0.04f, 0.08f, 0.26f, 0.22f, "Motion", "speed", FigurePalette.SKY)
                .block("attitude", 0.37f, 0.08f, 0.26f, 0.22f, "Attitude", "pitch / roll", FigurePalette.TEAL)
                .block("lift", 0.70f, 0.08f, 0.26f, 0.22f, "Lift", "altitude", FigurePalette.GREEN)
                .block("prop", 0.04f, 0.40f, 0.26f, 0.22f, "Propulsion", "thrust", FigurePalette.AMBER)
                .block("dock", 0.37f, 0.40f, 0.26f, 0.22f, "Docking", "state", FigurePalette.RUST)
                .block("nav", 0.70f, 0.40f, 0.26f, 0.22f, "Navigation", "heading", FigurePalette.PLUM)
                .noteCenter(0.5f, 0.74f, "Any of these become readable when the matching block exposes them.",
                        FigurePalette.INK)
                .build();

        FigureSheet pick = FigureSheet.builder("2. Pick \"Stats Link\"")
                .block("link", 0.06f, 0.28f, 0.34f, 0.40f, "Display Link", "open the UI", FigurePalette.GREEN)
                .panel("stats", 0.54f, 0.28f, 0.34f, 0.40f, "Stats Link", "the source to choose", FigurePalette.TEAL)
                .arrow("link", "stats", FigurePalette.GREEN)
                .noteCenter(0.5f, 0.84f, "You MUST choose \"Stats Link\" in the Display Link to extract the data.",
                        FigurePalette.DANGER)
                .build();

        return new ManualFigure("aero_source_matrix", List.of(flow, pick));
    }

    private static ManualFigure contraptionInfo() {
        FigureSheet modes = FigureSheet.builder("1. Three special modes")
                .block("info", 0.02f, 0.30f, 0.30f, 0.40f, "Contraption Info", "raw detail", FigurePalette.SKY)
                .block("deck", 0.36f, 0.30f, 0.28f, 0.40f, "Aerodeck", "operator view", FigurePalette.TEAL)
                .block("line", 0.68f, 0.30f, 0.30f, 0.40f, "Aeroline", "GPS guidance", FigurePalette.PLUM)
                .noteCenter(0.5f, 0.88f, "Readiness depends on the contraption type.", FigurePalette.INK)
                .build();

        FigureSheet sensors = FigureSheet.builder("2. Sensor requirements")
                .block("gimbal", 0.02f, 0.10f, 0.30f, 0.30f, "Gimbal sensor", "required", FigurePalette.AMBER)
                .block("ci", 0.52f, 0.04f, 0.30f, 0.24f, "Contraption Info", "needs gimbal", FigurePalette.SKY)
                .block("ad", 0.52f, 0.34f, 0.30f, 0.24f, "Aerodeck", "needs gimbal", FigurePalette.TEAL)
                .block("nav", 0.02f, 0.60f, 0.30f, 0.30f, "Navigation Table", "for Aeroline", FigurePalette.PLUM)
                .block("al", 0.52f, 0.66f, 0.30f, 0.24f, "Aeroline", "needs nav table", FigurePalette.PLUM)
                .arrow("gimbal", "ci", FigurePalette.AMBER)
                .arrow("gimbal", "ad", FigurePalette.AMBER)
                .arrow("nav", "al", FigurePalette.PLUM)
                .note(0.86f, 0.94f, "Others help, not required.", FigurePalette.INK)
                .build();

        return new ManualFigure("contraption_info", List.of(modes, sensors));
    }

    private static ManualFigure aeroFlightDeck() {
        FigureSheet flow = FigureSheet.builder("1. One operator view")
                .block("telemetry", 0.02f, 0.28f, 0.30f, 0.44f, "Telemetry", "all airship data", FigurePalette.SKY)
                .panel("deck", 0.48f, 0.22f, 0.34f, 0.56f, "Aerodeck", "readable overview", FigurePalette.TEAL)
                .arrow("telemetry", "deck", FigurePalette.SKY)
                .noteCenter(0.5f, 0.90f, "Gathers the available telemetry into one glance.", FigurePalette.INK)
                .build();

        FigureSheet versus = FigureSheet.builder("2. How the three differ")
                .block("deck", 0.04f, 0.10f, 0.40f, 0.22f, "Aerodeck", "the overview", FigurePalette.TEAL)
                .block("line", 0.04f, 0.40f, 0.40f, 0.22f, "Aeroline", "target / GPS", FigurePalette.PLUM)
                .block("info", 0.04f, 0.70f, 0.40f, 0.22f, "Contraption Info", "fine detail", FigurePalette.SKY)
                .note(0.50f, 0.50f, "Specialized pages handle the details.", FigurePalette.INK)
                .build();

        return new ManualFigure("aero_flight_deck", List.of(flow, versus));
    }

    private static ManualFigure aerolineDirector() {
        FigureSheet flow = FigureSheet.builder("1. Your airship GPS")
                .block("target", 0.02f, 0.30f, 0.26f, 0.40f, "Target", "where to go", FigurePalette.RUST)
                .panel("dir", 0.38f, 0.22f, 0.28f, 0.56f, "Aeroline", "heading & range", FigurePalette.PLUM)
                .block("you", 0.74f, 0.30f, 0.24f, 0.40f, "Helm", "follow it", FigurePalette.GREEN)
                .arrow("target", "dir", FigurePalette.RUST)
                .arrow("dir", "you", FigurePalette.PLUM)
                .noteCenter(0.5f, 0.90f, "Prioritises the target location and the heading to reach it.",
                        FigurePalette.INK)
                .build();

        FigureSheet needs = FigureSheet.builder("2. What it needs")
                .block("nav", 0.04f, 0.28f, 0.34f, 0.40f, "Navigation Table", "from Aeronautics", FigurePalette.AMBER)
                .panel("dir", 0.54f, 0.28f, 0.34f, 0.40f, "Aeroline", "enriched guidance", FigurePalette.PLUM)
                .arrow("nav", "dir", FigurePalette.AMBER)
                .note(0.04f, 0.84f, "The Aerodeck shows the same data, but without the target focus.",
                        FigurePalette.INK)
                .build();

        return new ManualFigure("aeroline_director", List.of(flow, needs));
    }

    private static ManualFigure hotAirBurnerTelemetry() {
        FigureSheet flow = FigureSheet.builder("1. Plain burner numbers")
                .block("burner", 0.04f, 0.30f, 0.30f, 0.40f, "Hot Air Burner", "the source", FigurePalette.RUST)
                .panel("read", 0.48f, 0.24f, 0.34f, 0.52f, "Telemetry", "standard readout", FigurePalette.AMBER)
                .arrow("burner", "read", FigurePalette.RUST)
                .noteCenter(0.5f, 0.90f, "No flourish - just the burner data you need.", FigurePalette.INK)
                .build();

        return new ManualFigure("hot_air_burner_telemetry", List.of(flow));
    }

    private static ManualFigure rpmGovernor() {
        FigureSheet flow = FigureSheet.builder("1. The VCC's hand on a shaft")
                .panel("vcc", 0.02f, 0.28f, 0.28f, 0.44f, "Vehicle Control", "the brain", FigurePalette.TEAL)
                .block("gov", 0.40f, 0.24f, 0.24f, 0.52f, "RPM Governor", "obeys VCC", FigurePalette.SKY)
                .block("shaft", 0.74f, 0.30f, 0.24f, 0.40f, "Shaft", "one per shaft", FigurePalette.BRASS)
                .arrow("vcc", "gov", FigurePalette.TEAL)
                .arrow("gov", "shaft", FigurePalette.SKY)
                .noteCenter(0.5f, 0.90f, "Put one Governor on each shaft the VCC should control.",
                        FigurePalette.INK)
                .build();

        FigureSheet bind = FigureSheet.builder("2. Bind & fail-safe")
                .chip(0.02f, 0.14f, "Open the VCC", FigurePalette.TEAL)
                .chip(0.02f, 0.38f, "Bind Governor -> a device", FigurePalette.SKY)
                .block("safe", 0.02f, 0.60f, 0.42f, 0.30f, "VCC silent?", "Governor lets go", FigurePalette.AMBER)
                .note(0.02f, 0.94f, "Releasing the shaft stops the craft getting stuck.", FigurePalette.INK)
                .build();

        return new ManualFigure("rpm_governor", List.of(flow, bind));
    }

    private static ManualFigure pathScanner() {
        FigureSheet flow = FigureSheet.builder("1. Eyes on the ground ahead")
                .block("scan", 0.02f, 0.26f, 0.28f, 0.48f, "Path Scanner", "faces forward", FigurePalette.OLIVE)
                .block("terrain", 0.40f, 0.30f, 0.24f, 0.40f, "Terrain", "loaded chunks", FigurePalette.BRASS)
                .panel("pf", 0.74f, 0.26f, 0.24f, 0.48f, "Pathfinder", "via Display Link", FigurePalette.SKY)
                .arrow("scan", "terrain", FigurePalette.OLIVE)
                .arrow("terrain", "pf", FigurePalette.BRASS)
                .noteCenter(0.5f, 0.90f, "Face it the way the vehicle should go; let it scan.", FigurePalette.INK)
                .build();

        FigureSheet role = FigureSheet.builder("2. It sees, it does not decide")
                .block("scan", 0.04f, 0.30f, 0.34f, 0.40f, "Scanner", "reads the world", FigurePalette.OLIVE)
                .panel("pf", 0.54f, 0.30f, 0.34f, 0.40f, "Pathfinder", "chooses the path", FigurePalette.SKY)
                .arrow("scan", "pf", FigurePalette.OLIVE)
                .noteCenter(0.5f, 0.84f, "The Scanner only observes - the Pathfinder Computer routes.",
                        FigurePalette.INK)
                .build();

        return new ManualFigure("path_scanner", List.of(flow, role));
    }

    private static ManualFigure pathfinderComputer() {
        FigureSheet flow = FigureSheet.builder("1. From scan to route")
                .block("scan", 0.02f, 0.28f, 0.26f, 0.44f, "Path Scanner", "terrain data", FigurePalette.OLIVE)
                .panel("pf", 0.38f, 0.22f, 0.26f, 0.56f, "Pathfinder", "picks a route", FigurePalette.SKY)
                .panel("vcc", 0.74f, 0.28f, 0.24f, 0.44f, "VCC", "follows heading", FigurePalette.TEAL)
                .arrow("scan", "pf", FigurePalette.OLIVE)
                .arrow("pf", "vcc", FigurePalette.SKY)
                .noteCenter(0.5f, 0.90f, "Fresh scan data in, a chosen direction out to the VCC.",
                        FigurePalette.INK)
                .build();

        FigureSheet links = FigureSheet.builder("2. Two links to make")
                .block("scanlink", 0.04f, 0.12f, 0.40f, 0.30f, "Scanner Display Link", "data in", FigurePalette.OLIVE)
                .block("vcclink", 0.04f, 0.52f, 0.40f, 0.30f, "Guidance -> VCC", "route out", FigurePalette.TEAL)
                .panel("pf", 0.56f, 0.28f, 0.32f, 0.44f, "Pathfinder", "between them", FigurePalette.SKY)
                .arrow("scanlink", "pf", FigurePalette.OLIVE)
                .arrow("pf", "vcclink", FigurePalette.TEAL)
                .note(0.02f, 0.92f, "Link the Scanner first, then link guidance to the VCC.",
                        FigurePalette.INK)
                .build();

        return new ManualFigure("pathfinder_computer", List.of(flow, links));
    }

    private static ManualFigure damageObserver() {
        FigureSheet flow = FigureSheet.builder("1. Watch one named zone")
                .block("part", 0.02f, 0.28f, 0.28f, 0.44f, "Ship part", "on a solid face", FigurePalette.BRASS)
                .block("obs", 0.40f, 0.24f, 0.24f, 0.52f, "Observer", "named zone", FigurePalette.DANGER)
                .panel("bridge", 0.74f, 0.26f, 0.24f, 0.48f, "Battle Bridge", "via Display Link", FigurePalette.RUST)
                .arrow("part", "obs", FigurePalette.BRASS)
                .arrow("obs", "bridge", FigurePalette.DANGER)
                .noteCenter(0.5f, 0.90f, "Each Observer reports one zone to the bridge.", FigurePalette.INK)
                .build();

        FigureSheet setup = FigureSheet.builder("2. Set it up")
                .chip(0.02f, 0.12f, "Name the zone", FigurePalette.DANGER)
                .chip(0.02f, 0.36f, "Pick its role", FigurePalette.AMBER)
                .chip(0.02f, 0.60f, "Pick the watched size", FigurePalette.SKY)
                .note(0.02f, 0.86f, "Then send the zone to a Battle Bridge with a Display Link.",
                        FigurePalette.INK)
                .build();

        return new ManualFigure("damage_observer", List.of(flow, setup));
    }

    private static ManualFigure battleBridge() {
        FigureSheet flow = FigureSheet.builder("1. The battle bridge")
                .block("obs", 0.02f, 0.10f, 0.30f, 0.30f, "Observers", "watched zones", FigurePalette.DANGER)
                .block("links", 0.02f, 0.56f, 0.30f, 0.30f, "Display Links", "feed the bridge", FigurePalette.GREEN)
                .panel("bridge", 0.50f, 0.26f, 0.34f, 0.48f, "Battle Bridge", "zones + weapons", FigurePalette.RUST)
                .arrow("obs", "bridge", FigurePalette.DANGER)
                .arrow("links", "bridge", FigurePalette.GREEN)
                .noteCenter(0.5f, 0.90f, "Shows watched zones and weapon readiness. Right-click to open.",
                        FigurePalette.INK)
                .build();

        FigureSheet trouble = FigureSheet.builder("2. No feed?")
                .block("bridge", 0.04f, 0.28f, 0.30f, 0.40f, "Bridge", "says no feed", FigurePalette.RUST)
                .block("link", 0.52f, 0.28f, 0.34f, 0.40f, "Display Link", "check this first", FigurePalette.GREEN)
                .arrow("bridge", "link", FigurePalette.RUST)
                .note(0.04f, 0.84f, "Mount on a wall; feed it from one or more Battle Observers.",
                        FigurePalette.INK)
                .build();

        return new ManualFigure("battle_screen_display", List.of(flow, trouble));
    }

    private static ManualFigure statsConfigurator() {
        FigureSheet flow = FigureSheet.builder("1. The tuning tool")
                .block("tool", 0.02f, 0.30f, 0.26f, 0.40f, "Configurator", "right-click a display", FigurePalette.BRASS)
                .panel("panel", 0.40f, 0.04f, 0.30f, 0.22f, "Stats Panel", "settings", FigurePalette.TEAL)
                .panel("alarm", 0.40f, 0.30f, 0.30f, 0.22f, "Stats Alarm", "settings", FigurePalette.DANGER)
                .panel("light", 0.40f, 0.56f, 0.30f, 0.22f, "Tower Light", "settings", FigurePalette.GREEN)
                .panel("proj", 0.40f, 0.80f, 0.30f, 0.18f, "Projector", "settings", FigurePalette.PLUM)
                .arrow("tool", "panel", FigurePalette.TEAL)
                .arrow("tool", "alarm", FigurePalette.DANGER)
                .arrow("tool", "light", FigurePalette.GREEN)
                .arrow("tool", "proj", FigurePalette.PLUM)
                .build();

        FigureSheet copy = FigureSheet.builder("2. Copy & paste a layout")
                .block("src", 0.04f, 0.28f, 0.26f, 0.40f, "Source panel", "sneak-use = copy", FigurePalette.TEAL)
                .block("tool", 0.38f, 0.30f, 0.22f, 0.36f, "Configurator", "now charged", FigurePalette.BRASS)
                .block("dst", 0.70f, 0.28f, 0.26f, 0.40f, "Target panel", "use = paste", FigurePalette.GREEN)
                .arrow("src", "tool", FigurePalette.TEAL)
                .arrow("tool", "dst", FigurePalette.GREEN)
                .noteCenter(0.5f, 0.86f, "Same data, different colors, radius, thresholds, or presentation.",
                        FigurePalette.INK)
                .build();

        return new ManualFigure("stats_configurator", List.of(flow, copy));
    }

    private static ManualFigure signalAntennas() {
        FigureSheet flow = FigureSheet.builder("1. Extend the reach")
                .block("block", 0.04f, 0.30f, 0.28f, 0.40f, "Stats block", "mount an antenna", FigurePalette.TEAL)
                .block("reach", 0.46f, 0.24f, 0.34f, 0.52f, "Direct reach", "farther, no chunkloading", FigurePalette.GREEN)
                .arrow("block", "reach", FigurePalette.TEAL)
                .noteCenter(0.5f, 0.90f, "Right-click a compatible block to mount; wrench to remove.",
                        FigurePalette.INK)
                .build();

        FigureSheet tiers = FigureSheet.builder("2. Three tiers")
                .block("small", 0.04f, 0.10f, 0.42f, 0.22f, "Small", "4x same-dimension", FigurePalette.SKY)
                .block("adv", 0.04f, 0.40f, 0.42f, 0.22f, "Advanced", "12x same-dimension", FigurePalette.AMBER)
                .block("trt", 0.04f, 0.70f, 0.42f, 0.24f, "TRT", "Overworld<->Nether links", FigurePalette.PLUM)
                .note(0.52f, 0.78f, "TRT also gives unlimited reach in those two realms.",
                        FigurePalette.INK)
                .build();

        return new ManualFigure("signal_antennas", List.of(flow, tiers));
    }

    private static ManualFigure statsAlarm() {
        FigureSheet flow = FigureSheet.builder("1. Watch for trouble")
                .block("panels", 0.02f, 0.28f, 0.28f, 0.44f, "Nearby panels", "active channels", FigurePalette.TEAL)
                .panel("alarm", 0.40f, 0.22f, 0.26f, 0.56f, "Stats Alarm", "scans them", FigurePalette.DANGER)
                .block("out", 0.74f, 0.28f, 0.24f, 0.44f, "Trips", "sound, particles", FigurePalette.AMBER)
                .arrow("panels", "alarm", FigurePalette.TEAL)
                .arrow("alarm", "out", FigurePalette.DANGER)
                .noteCenter(0.5f, 0.90f, "When a channel reaches its threshold, the alarm fires.",
                        FigurePalette.INK)
                .build();

        FigureSheet tune = FigureSheet.builder("2. Tune it")
                .chip(0.02f, 0.12f, "Scan radius", FigurePalette.TEAL)
                .chip(0.02f, 0.36f, "Sound & particles", FigurePalette.AMBER)
                .chip(0.02f, 0.60f, "Warning message", FigurePalette.DANGER)
                .chip(0.50f, 0.60f, "Power redstone", FigurePalette.GREEN)
                .note(0.02f, 0.86f, "Adjust everything with the Stats Configurator.", FigurePalette.INK)
                .build();

        return new ManualFigure("stats_alarm", List.of(flow, tune));
    }

    private static ManualFigure tableAlarmClock() {
        FigureSheet flow = FigureSheet.builder("1. A small desk clock")
                .block("clock", 0.04f, 0.30f, 0.28f, 0.40f, "Alarm Clock", "table or desk", FigurePalette.BRASS)
                .panel("screen", 0.46f, 0.24f, 0.34f, 0.52f, "Clock screen", "right-click to open", FigurePalette.TEAL)
                .arrow("clock", "screen", FigurePalette.BRASS)
                .noteCenter(0.5f, 0.90f, "Rings, lights up, and sends redstone until you silence it.",
                        FigurePalette.INK)
                .build();

        FigureSheet modes = FigureSheet.builder("2. Four modes")
                .block("clock", 0.04f, 0.12f, 0.42f, 0.22f, "Clock", "current time", FigurePalette.SKY)
                .block("watch", 0.54f, 0.12f, 0.42f, 0.22f, "Stopwatch", "time how long", FigurePalette.GREEN)
                .block("timer", 0.04f, 0.46f, 0.42f, 0.22f, "Timer", "count down", FigurePalette.AMBER)
                .block("alarm", 0.54f, 0.46f, 0.42f, 0.22f, "Alarm", "rings daily", FigurePalette.DANGER)
                .noteCenter(0.5f, 0.80f, "Pick the mode that fits the job.", FigurePalette.INK)
                .build();

        return new ManualFigure("table_alarm_clock", List.of(flow, modes));
    }

    private static ManualFigure statusTowerLight() {
        FigureSheet flow = FigureSheet.builder("1. Status at a glance")
                .block("src", 0.02f, 0.30f, 0.28f, 0.40f, "Machine", "local or linked", FigurePalette.BRASS)
                .panel("light", 0.46f, 0.20f, 0.26f, 0.60f, "Tower Light", "one clear lamp", FigurePalette.GREEN)
                .arrow("src", "light", FigurePalette.BRASS)
                .noteCenter(0.5f, 0.90f, "Place above a machine, or link a remote source with a Display Link.",
                        FigurePalette.INK)
                .build();

        FigureSheet colors = FigureSheet.builder("2. What the colors mean")
                .block("green", 0.04f, 0.10f, 0.42f, 0.22f, "Green", "normal", FigurePalette.GREEN)
                .block("yellow", 0.04f, 0.40f, 0.42f, 0.22f, "Yellow", "warning / no data", FigurePalette.AMBER)
                .block("red", 0.04f, 0.70f, 0.42f, 0.24f, "Red", "critical (+redstone)", FigurePalette.DANGER)
                .note(0.52f, 0.78f, "Choose source mode, metric, and threshold vs on/off logic.",
                        FigurePalette.INK)
                .build();

        return new ManualFigure("status_tower_light", List.of(flow, colors));
    }

    private static ManualFigure freightManifestClipboard() {
        FigureSheet flow = FigureSheet.builder("1. Bind to a station")
                .block("item", 0.02f, 0.30f, 0.24f, 0.40f, "Clipboard item", "right-click station", FigurePalette.BRASS)
                .block("station", 0.34f, 0.24f, 0.26f, 0.52f, "Create station", "now bound", FigurePalette.PLUM)
                .panel("board", 0.70f, 0.26f, 0.26f, 0.48f, "Placed board", "its manifest", FigurePalette.TEAL)
                .arrow("item", "station", FigurePalette.BRASS)
                .arrow("station", "board", FigurePalette.PLUM)
                .noteCenter(0.5f, 0.90f, "Place it on the ground or a wall after binding.", FigurePalette.INK)
                .build();

        FigureSheet manage = FigureSheet.builder("2. Manage the cargo")
                .chip(0.02f, 0.14f, "Right-click = manifest", FigurePalette.TEAL)
                .chip(0.02f, 0.38f, "Tick the cargo checklist", FigurePalette.GREEN)
                .chip(0.02f, 0.62f, "Relink or copy the link", FigurePalette.AMBER)
                .note(0.02f, 0.88f, "A linked clipboard item can relink the placed block.",
                        FigurePalette.INK)
                .build();

        return new ManualFigure("freight_manifest_clipboard", List.of(flow, manage));
    }

    private static ManualFigure dispatchBoard() {
        FigureSheet flow = FigureSheet.builder("1. A public dispatch screen")
                .block("station", 0.02f, 0.30f, 0.26f, 0.40f, "Station", "bind, then place", FigurePalette.PLUM)
                .panel("board", 0.40f, 0.22f, 0.30f, 0.56f, "Dispatch Board", "live screen", FigurePalette.TEAL)
                .block("ops", 0.78f, 0.30f, 0.20f, 0.40f, "Platform", "near it", FigurePalette.BRASS)
                .arrow("station", "board", FigurePalette.PLUM)
                .arrow("board", "ops", FigurePalette.TEAL)
                .noteCenter(0.5f, 0.90f, "Shows cargo totals, order status, and train presence at a glance.",
                        FigurePalette.INK)
                .build();

        FigureSheet ops = FigureSheet.builder("2. Operator controls")
                .chip(0.02f, 0.14f, "Hide / reveal orders", FigurePalette.AMBER)
                .chip(0.02f, 0.38f, "Reset delivery orders", FigurePalette.DANGER)
                .chip(0.02f, 0.62f, "Needs permission", FigurePalette.GREEN)
                .note(0.02f, 0.88f, "The Freight Clipboard edits cargo; the board publishes status.",
                        FigurePalette.INK)
                .build();

        return new ManualFigure("dispatch_board", List.of(flow, ops));
    }
}
