package com.createstats.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopyableSettingsCoverageTest {
    private static final Path MAIN = Path.of("src/main/java");
    private static final Path INTERFACE = MAIN.resolve("com/createstats/util/CopyableSettings.java");

    private static final Set<String> SMART_BASES = Set.of("SmartBlockEntity", "SplitShaftBlockEntity",
            "KineticBlockEntity", "GeneratingKineticBlockEntity", "PortableStorageInterfaceBlockEntity");

    private static final Set<String> FORBIDDEN = Set.of(
            "Energy", "FeEnergy", "FePowered", "Owner", "OwnerUUID", "Code", "Codes", "Clearance",
            "Household", "Roster", "Panel", "Linked", "LinkedSource", "ActiveSourcePos", "Links",
            "LinkTiers", "StudWires", "Channels", "SourceRegistry", "Slots", "Items", "Inventory", "Item",
            "Recorder", "GroundRecorder", "Route", "Pilot", "ControllerId", "ScannerId", "Id", "Priority",
            "Tier", "Lock", "Locked", "Gated", "On", "Running", "Down", "Live", "Ringing", "TimerRunning",
            "StopwatchRunning", "Rate", "Total", "TotalFe", "TotalMovedMb", "PeakFe", "Repelled");

    private static final List<String> VEHICLE_PARTS = List.of("JetRoles", "JetInverted", "SwivelInputs",
            "AuxSettings", "MachineInputs", "MachineSettings", "LinkSources", "LinkToggle", "GovernorFlipped",
            "GovernorExcluded", "TrailerModes", "PropRoles", "AuxRoles");

    private static final List<String> AIRSHIP_PARTS = List.of("Nose", "NoseSet", "Trim", "Engines",
            "BalloonsOff", "Surfaces", "Tools", "Lights");

    private static final Pattern PUT_KEY = Pattern.compile("\\.put[A-Za-z]*\\(\\s*\"([^\"]+)\"");
    private static final Pattern LITERAL = Pattern.compile("\"([^\"]*)\"");
    private static final Pattern EXTENDS = Pattern.compile("\\bextends\\s+([A-Za-z0-9_.]+)");
    private static final Pattern UNGUARDED_TIER_LOAD =
            Pattern.compile("managed\\.load\\(\\s*tag\\.getInt\\(\\s*\"(Tier|Priority)\"\\s*\\)");

    private static final String TIER_GUARD = "if\\s*\\(\\s*tag\\.contains\\(\\s*\"%s\"\\s*[,)]";

    @Test
    void everySettingsKeyIsNamespacedAndBelongsToOneFamily() {
        final Map<String, String> owner = new LinkedHashMap<>();
        final List<String> wrong = new ArrayList<>();
        for (final Source s : sources()) {
            final String body = s.body("String settingsKey(");
            if (body == null) {
                continue;
            }
            final Matcher m = LITERAL.matcher(body);
            boolean any = false;
            while (m.find()) {
                any = true;
                final String key = m.group(1);
                if (!key.startsWith("create_stats:")) {
                    wrong.add(s.name + " uses \"" + key + "\", which is not namespaced create_stats:");
                }
                final String first = owner.putIfAbsent(key, s.name);
                if (first != null && !first.equals(s.name)) {
                    wrong.add(s.name + " and " + first + " both use \"" + key + "\": a paste would cross"
                            + " from one family's fields into the other's");
                }
            }
            if (!any) {
                wrong.add(s.name + ".settingsKey() returns no literal key");
            }
        }
        assertFalse(owner.isEmpty(), "no settingsKey() found at all: the scan is not reading the tree");
        assertTrue(wrong.isEmpty(), String.join("\n", wrong));
    }

    @Test
    void smartBlockEntitiesPutTheirSettingsInSchematicsThemselves() {
        final List<String> wrong = new ArrayList<>();
        int smart = 0;
        for (final Source s : sources()) {
            if (!s.implementsCopyable() || s.extendsName == null || !SMART_BASES.contains(s.extendsName)) {
                continue;
            }
            smart++;
            final String writeSafe = s.body("void writeSafe(");
            if (writeSafe == null) {
                wrong.add(s.name + " is a smart block entity and does not override writeSafe, so"
                        + " SmartBlockEntity's class method wins and its settings never reach a schematic");
                continue;
            }
            if (!writeSafe.contains("super.writeSafe(")) {
                wrong.add(s.name + ".writeSafe does not call super.writeSafe, so Create's own scroll values"
                        + " stop printing");
            }
            if (!writeSafe.contains("writeSettings(")) {
                wrong.add(s.name + ".writeSafe does not call writeSettings, so its settings never print");
            }
        }
        assertTrue(smart > 0, "no smart CopyableSettings block entity found: the scan is not reading the tree");
        assertTrue(wrong.isEmpty(), String.join("\n", wrong));
    }

    @Test
    void noSettingsWriterCarriesWhatMustNeverTravel() {
        final List<String> wrong = new ArrayList<>();
        int writers = 0;
        for (final Source s : sources()) {
            final String body = s.body("void writeSettings(");
            if (body == null) {
                continue;
            }
            writers++;
            final Matcher m = LITERAL.matcher(body);
            while (m.find()) {
                if (FORBIDDEN.contains(m.group(1))) {
                    wrong.add(s.name + ".writeSettings writes \"" + m.group(1) + "\", which must never travel"
                            + " on a clipboard or into a schematic");
                }
            }
        }
        assertTrue(writers > 0, "no writeSettings found: the scan is not reading the tree");
        assertTrue(wrong.isEmpty(), String.join("\n", wrong));
    }

    @Test
    void everyKeyAPrintedBlockIsHandedIsOneItReads() {
        final List<String> wrong = new ArrayList<>();
        for (final Source s : sources()) {
            final List<Source> chain = chain(s);
            final String settings = s.body("void writeSettings(");
            if (settings != null && carriesSettings(chain)) {
                for (final String key : putKeys(settings)) {
                    if (!readsKey(chain, key)) {
                        wrong.add(s.name + ".writeSettings writes \"" + key + "\" into schematics and nothing"
                                + " in its load path reads it, so a printed block drops it");
                    }
                }
            }
            for (final String method : List.of("void writeFreshDefaults(", "void writeSafe(")) {
                final String body = s.body(method);
                if (body == null) {
                    continue;
                }
                for (final String key : putKeys(body)) {
                    if (!readsKey(chain, key)) {
                        wrong.add(s.name + " " + method + ") writes \"" + key + "\" and nothing reads it back");
                    }
                }
            }
        }
        assertTrue(wrong.isEmpty(), String.join("\n", wrong));
    }

    @Test
    void anAbsentPduTierIsNeverLeftToReadAsCritical() {
        final List<String> wrong = new ArrayList<>();
        for (final Source s : sources()) {
            if (!s.implementsCopyable() && s.body("void writeSafe(") == null) {
                continue;
            }
            final List<Source> chain = chain(s);
            final Set<String> written = new LinkedHashSet<>();
            for (final Source c : chain) {
                for (final String method : List.of("void writeFreshDefaults(", "void writeSafe(")) {
                    final String body = c.body(method);
                    if (body != null) {
                        written.addAll(putKeys(body));
                    }
                }
            }
            for (final Source c : chain) {
                for (final String method : List.of("void loadAdditional(", "void read(CompoundTag")) {
                    final String body = c.body(method);
                    if (body == null) {
                        continue;
                    }
                    final Matcher m = UNGUARDED_TIER_LOAD.matcher(body);
                    while (m.find()) {
                        if (!written.contains(m.group(1))) {
                            wrong.add(s.name + " loads \"" + m.group(1) + "\" with no contains guard and its"
                                    + " schematic tag never writes it: FeManagedState reads the absent ordinal"
                                    + " as 0, Critical, so every printed one is Critical where a placed one is"
                                    + " Normal. Write the fresh value in writeFreshDefaults");
                        }
                    }
                }
            }
        }
        assertTrue(wrong.isEmpty(), String.join("\n", wrong));
    }

    @Test
    void aSmartBlockEntityPrintsTheTierItsLoaderWouldMisread() {
        final List<String> wrong = new ArrayList<>();
        final Set<String> smartLoaders = new LinkedHashSet<>();
        for (final Source s : sources()) {
            final List<Source> chain = chain(s);
            if (!isSmart(chain)) {
                continue;
            }
            final Set<String> unguarded = new LinkedHashSet<>();
            boolean loadsTier = false;
            for (final Source c : chain) {
                for (final String method : List.of("void loadAdditional(", "void read(CompoundTag")) {
                    final String body = c.body(method);
                    if (body == null) {
                        continue;
                    }
                    final String code = code(body);
                    final Matcher m = UNGUARDED_TIER_LOAD.matcher(code);
                    while (m.find()) {
                        loadsTier = true;
                        if (!guarded(code, m.start(), m.group(1))) {
                            unguarded.add(m.group(1));
                        }
                    }
                }
            }
            if (loadsTier) {
                smartLoaders.add(s.name);
            }
            if (unguarded.isEmpty()) {
                continue;
            }
            final String writeSafe = nearestBody(chain, "void writeSafe(");
            if (writeSafe == null) {
                wrong.add(s.name + " is a smart block entity that loads " + unguarded + " with no contains guard"
                        + " and never overrides writeSafe: Create prints it from its behaviours alone, so"
                        + " every printed one loads the absent tier as 0, Critical, where a placed one is"
                        + " Normal. Override writeSafe: super.writeSafe, then the fresh tier");
                continue;
            }
            final String code = code(writeSafe);
            if (!code.contains("super.writeSafe(")) {
                wrong.add(s.name + ".writeSafe does not call super.writeSafe, so Create's own dials stop printing");
            }
            final Map<String, String> puts = putStatements(code);
            if (code.contains("writeFreshDefaults(")) {
                final String fresh = nearestBody(chain, "void writeFreshDefaults(");
                if (fresh != null) {
                    putStatements(code(fresh)).forEach(puts::putIfAbsent);
                }
            }
            for (final String key : unguarded) {
                final String statement = puts.get(key);
                if (statement == null) {
                    wrong.add(s.name + ".writeSafe never writes \"" + key + "\", which its loader reads with no"
                            + " guard: every printed one loads it as 0, Critical");
                } else if (!statement.contains("FePriority.DEFAULT")) {
                    wrong.add(s.name + ".writeSafe writes \"" + key + "\" as something other than"
                            + " FePriority.DEFAULT: a printed block must start at a fresh tier, never this one's,"
                            + " because a board's decision does not travel without the board");
                }
            }
        }
        assertTrue(smartLoaders.size() >= 7, "the scan found only " + smartLoaders
                + " as smart block entities loading a PDU tier: it is not reading the tree");
        assertTrue(wrong.isEmpty(), String.join("\n", wrong));
    }

    @Test
    void aVehicleTuningNeverCarriesAPart() {
        final String ground = source("com/createstats/vccmk4/Vcc4Nbt.java").body("static CompoundTag tuning(");
        final String air = source("com/createstats/vccmk4/air/AerialNbt.java").body("static CompoundTag tuning(");
        assertNotNull(ground, "Vcc4Nbt.tuning is gone: the ground console's clipboard has nothing to check");
        assertNotNull(air, "AerialNbt.tuning is gone: the Aerial's clipboard has nothing to check");
        final List<String> wrong = new ArrayList<>();
        for (final String key : VEHICLE_PARTS) {
            if (ground.contains("\"" + key + "\"")) {
                wrong.add("Vcc4Nbt.tuning writes \"" + key + "\", a choice keyed by one vehicle's part");
            }
        }
        for (final String key : AIRSHIP_PARTS) {
            if (air.contains("\"" + key + "\"")) {
                wrong.add("AerialNbt.tuning writes \"" + key + "\", which belongs to one airframe");
            }
        }
        assertTrue(wrong.isEmpty(), String.join("\n", wrong));
    }

    @Test
    void theHandlerLeavesSmartBlockEntitiesToCreate() {
        final String target = source("com/createstats/util/CopyableSettingsHandler.java").body("CopyableSettings target(");
        assertNotNull(target, "CopyableSettingsHandler.target is gone");
        assertTrue(target.contains("instanceof SmartBlockEntity"),
                "the handler must skip smart block entities: Create's own handler already pastes onto them,"
                        + " and handling them here as well pastes twice");
        assertTrue(target.contains("hasCopyableSettings()"),
                "the handler must leave a block with nothing to copy (a plain rocker) its ordinary click");
    }

    private static boolean carriesSettings(List<Source> chain) {
        for (final Source c : chain) {
            final String writeSafe = c.body("void writeSafe(");
            if (writeSafe != null) {
                return writeSafe.contains("writeSettings(");
            }
            if (c.extendsName != null && SMART_BASES.contains(c.extendsName)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSmart(List<Source> chain) {
        for (final Source c : chain) {
            if (c.extendsName != null && SMART_BASES.contains(c.extendsName)) {
                return true;
            }
        }
        return false;
    }

    private static String nearestBody(List<Source> chain, String signature) {
        for (final Source c : chain) {
            final String body = c.body(signature);
            if (body != null) {
                return body;
            }
        }
        return null;
    }

    private static boolean guarded(String code, int at, String key) {
        final int from = Math.max(code.lastIndexOf(';', at), code.lastIndexOf('}', at)) + 1;
        final Pattern guard = Pattern.compile(String.format(TIER_GUARD, Pattern.quote(key)));
        return guard.matcher(code.substring(from, at)).find();
    }

    private static Map<String, String> putStatements(String body) {
        final Map<String, String> out = new LinkedHashMap<>();
        final Matcher m = PUT_KEY.matcher(body);
        while (m.find()) {
            final int end = body.indexOf(';', m.start());
            out.putIfAbsent(m.group(1), body.substring(m.start(), end < 0 ? body.length() : end));
        }
        return out;
    }

    private static String code(String s) {
        final StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c == '"' || c == '\'') {
                final int close = skipLiteral(s, i, c);
                out.append(s, i, Math.min(s.length(), close + 1));
                i = close;
            } else if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') {
                final int nl = s.indexOf('\n', i);
                i = nl < 0 ? s.length() : nl - 1;
            } else if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
                final int end = s.indexOf("*/", i + 2);
                i = end < 0 ? s.length() : end + 1;
                out.append(' ');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static Set<String> putKeys(String body) {
        final Set<String> keys = new LinkedHashSet<>();
        final Matcher m = PUT_KEY.matcher(body);
        while (m.find()) {
            keys.add(m.group(1));
        }
        return keys;
    }

    private static boolean readsKey(List<Source> chain, String key) {
        final Pattern read = Pattern.compile("(?:\\.get[A-Za-z]*|\\.contains|\\.hasUUID)\\(\\s*\""
                + Pattern.quote(key) + "\"");
        for (final Source c : chain) {
            if (read.matcher(c.text).find()) {
                return true;
            }
        }
        return false;
    }

    private static List<Source> chain(Source s) {
        final Map<String, Source> byName = byName();
        final List<Source> out = new ArrayList<>();
        Source c = s;
        while (c != null && !out.contains(c)) {
            out.add(c);
            c = c.extendsName == null ? null : byName.get(c.extendsName);
        }
        return out;
    }

    private static Source source(String relative) {
        return new Source(MAIN.resolve(relative));
    }

    private static List<Source> SOURCES;
    private static Map<String, Source> BY_NAME;

    private static List<Source> sources() {
        if (SOURCES == null) {
            try (Stream<Path> files = Files.walk(MAIN)) {
                SOURCES = files.filter(p -> p.toString().endsWith(".java"))
                        .filter(p -> !p.normalize().equals(INTERFACE.normalize()))
                        .sorted()
                        .map(Source::new)
                        .toList();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return SOURCES;
    }

    private static Map<String, Source> byName() {
        if (BY_NAME == null) {
            final Map<String, Source> map = new HashMap<>();
            for (final Source s : sources()) {
                map.putIfAbsent(s.name, s);
            }
            BY_NAME = map;
        }
        return BY_NAME;
    }

    private static final class Source {
        final String name;
        final String text;
        final String header;
        final String extendsName;

        Source(Path path) {
            this.name = path.getFileName().toString().replace(".java", "");
            try {
                this.text = Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            final Matcher decl = Pattern.compile("\\b(?:class|interface|record|enum)\\s+" + Pattern.quote(name)
                    + "\\b").matcher(text);
            String head = "";
            if (decl.find()) {
                final int open = text.indexOf('{', decl.start());
                head = open < 0 ? "" : text.substring(decl.start(), open);
            }
            this.header = head;
            final Matcher ext = EXTENDS.matcher(head);
            final String parent = ext.find() ? ext.group(1) : null;
            this.extendsName = parent == null ? null : parent.substring(parent.lastIndexOf('.') + 1);
        }

        boolean implementsCopyable() {
            return header.contains("CopyableSettings");
        }

        String body(String signature) {
            int from = 0;
            while (true) {
                final int at = text.indexOf(signature, from);
                if (at < 0) {
                    return null;
                }
                from = at + signature.length();
                final int open = text.indexOf('{', at);
                final int semi = text.indexOf(';', at);
                if (open < 0) {
                    return null;
                }
                if (semi >= 0 && semi < open) {
                    continue;
                }
                final int close = matching(text, open);
                return close < 0 ? null : text.substring(open + 1, close);
            }
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static int matching(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipLiteral(s, i, c);
                continue;
            }
            if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') {
                final int nl = s.indexOf('\n', i);
                i = nl < 0 ? s.length() : nl;
                continue;
            }
            if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
                final int end = s.indexOf("*/", i + 2);
                i = end < 0 ? s.length() : end + 1;
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private static int skipLiteral(String s, int start, char quote) {
        for (int i = start + 1; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == quote) {
                return i;
            }
        }
        return s.length();
    }
}
