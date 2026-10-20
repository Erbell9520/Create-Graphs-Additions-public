package com.createstats.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkViewWiringTest {
    private static final Path MAIN = Path.of("src/main/java");
    private static final Path CLIENT = MAIN.resolve("com/createstats/client");
    private static final Path VIEW = CLIENT.resolve("LinkView.java");
    private static final Path WEB = CLIENT.resolve("LinkWeb.java");
    private static final Path SWITCH = MAIN.resolve("com/createstats/blockentity/SwitchBE.java");

    @Test
    void theViewIsAClientOnlySubscriberNothingOutsideTheClientNames() {
        final String view = read(VIEW);
        assertTrue(view.contains("@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT)"),
                "LinkView must subscribe on the client only, or a dedicated server loads it and its render types");
        final List<String> outside = new ArrayList<>();
        for (final Path p : javaFiles()) {
            if (!p.normalize().startsWith(CLIENT.normalize()) && code(read(p)).contains("LinkView")) {
                outside.add(MAIN.relativize(p).toString());
            }
        }
        assertTrue(outside.isEmpty(), "outside the client package, these name LinkView, which a server must never"
                + " load: " + outside);
    }

    @Test
    void theGeometryIsFreeOfMinecraftTypes() {
        final Matcher imports = Pattern.compile("(?m)^import\\s+(?:static\\s+)?([\\w.]+)").matcher(read(WEB));
        final List<String> foreign = new ArrayList<>();
        int count = 0;
        while (imports.find()) {
            count++;
            if (!imports.group(1).startsWith("java.")) {
                foreign.add(imports.group(1));
            }
        }
        assertTrue(count > 0, "no imports read: the scan is not reading LinkWeb");
        assertTrue(foreign.isEmpty(), "LinkWeb must stay pure so LinkWebTest can pin it; it imports " + foreign);
    }

    @Test
    void everyHostIsDecidedAndNoSecurityDeviceIsEverDrawn() {
        final String view = read(VIEW);
        final String drawn = body(view, "boolean drawnSource(");
        final String scan = body(view, "void scan(");
        assertNotNull(drawn, "LinkView.drawnSource is gone: nothing says which hosts are drawn");
        assertNotNull(scan, "LinkView.scan is gone");
        final List<String> wrong = new ArrayList<>();
        int hosts = 0;
        for (final Path p : javaFiles()) {
            final String name = p.getFileName().toString().replace(".java", "");
            final Matcher decl = Pattern.compile("\\bclass\\s+" + Pattern.quote(name) + "\\b[^{]*").matcher(read(p));
            if (!decl.find() || !decl.group().contains("FixtureLinker")) {
                continue;
            }
            hosts++;
            if (!view.contains(name)) {
                wrong.add(name + " is a FixtureLinker that LinkView never names: decide whether its bindings may be"
                        + " shown, and name it either way");
            }
        }
        if (drawn.contains("HomeSecurityPanel")) {
            wrong.add("drawnSource draws the Home Security Panel: the view must not map which keypad opens which door");
        }
        if (!drawn.contains("SecurityDevices.isDevice(")) {
            wrong.add("drawnSource no longer refuses a host that is a security device");
        }
        if (!scan.contains("SecurityDevices.isDevice(")) {
            wrong.add("scan no longer refuses a fixture that is a security device");
        }
        assertTrue(hosts >= 3, "found only " + hosts + " FixtureLinker hosts: the scan is not reading the tree");
        assertTrue(wrong.isEmpty(), String.join("\n", wrong));
    }

    @Test
    void nothingIsScannedWhileNoToolIsHeld() {
        final String tick = body(read(VIEW), "void onClientTick(");
        assertNotNull(tick, "LinkView.onClientTick is gone");
        final int guard = tick.indexOf("if (held == Mode.OFF && web == null)");
        final int scan = tick.indexOf("scan(");
        assertTrue(guard >= 0 && scan > guard && tick.indexOf("return;", guard) < scan,
                "the tick must return before any scan when no tool is held and nothing is fading: the view costs"
                        + " nothing then");
    }

    @Test
    void aLinkChangeReachesTheClientAtOnce() {
        final String sw = read(SWITCH);
        final String toggle = body(sw, "boolean toggleLink(");
        final String sync = body(sw, "void syncLinks(");
        final String prune = body(sw, "void pushGateToLinked(");
        assertNotNull(toggle, "SwitchBE.toggleLink is gone");
        assertNotNull(sync, "SwitchBE.syncLinks is gone: a binding reaches the client only with the pilot heartbeat");
        assertNotNull(prune, "SwitchBE.pushGateToLinked is gone");
        final List<String> wrong = new ArrayList<>();
        if (!sync.contains("sendBlockUpdated(")) {
            wrong.add("syncLinks sends nothing");
        }
        if (count(toggle, "syncLinks();") < 2) {
            wrong.add("toggleLink syncs only one of its two outcomes: a link or an unlink waits up to half a minute"
                    + " to reach the client (KI-134)");
        }
        if (!prune.contains("syncLinks()")) {
            wrong.add("pushGateToLinked prunes a dead link without telling the client (KI-134)");
        }
        assertTrue(wrong.isEmpty(), String.join("\n", wrong));
    }

    private static int count(String text, String needle) {
        int n = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) {
            n++;
        }
        return n;
    }

    private static List<Path> javaFiles() {
        try (Stream<Path> files = Files.walk(MAIN)) {
            return files.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String body(String text, String signature) {
        final String code = code(text);
        int from = 0;
        while (true) {
            final int at = code.indexOf(signature, from);
            if (at < 0) {
                return null;
            }
            from = at + signature.length();
            final int open = code.indexOf('{', at);
            final int semi = code.indexOf(';', at);
            if (open < 0) {
                return null;
            }
            if (semi >= 0 && semi < open) {
                continue;
            }
            int depth = 0;
            for (int i = open; i < code.length(); i++) {
                final char c = code.charAt(i);
                if (c == '"' || c == '\'') {
                    i = skipLiteral(code, i, c);
                } else if (c == '{') {
                    depth++;
                } else if (c == '}' && --depth == 0) {
                    return code.substring(open + 1, i);
                }
            }
            return null;
        }
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
