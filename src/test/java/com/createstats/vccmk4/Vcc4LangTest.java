package com.createstats.vccmk4;

import com.createstats.vccmk4.console.Vcc4Console;
import com.createstats.vccmk4.core.AxleRole;
import com.createstats.vccmk4.core.BodyRole;
import com.createstats.vccmk4.core.Control;
import com.createstats.vccmk4.core.JetKind;
import com.createstats.vccmk4.core.JetMixer;
import com.createstats.vccmk4.core.JetRole;
import com.createstats.vccmk4.core.JetSupply;
import com.createstats.vccmk4.core.LinkKind;
import com.createstats.vccmk4.core.LinkSource;
import com.createstats.vccmk4.core.SteerPreset;
import com.createstats.vccmk4.core.PartInput;
import com.createstats.vccmk4.core.TrailerMode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Vcc4LangTest {
    private static final Path LANG = Path.of("src/main/resources/assets/create_stats/lang/en_us.json");
    private static final Pattern LANG_KEY = Pattern.compile("\"([^\"]+)\"\\s*:");
    private static final String L = "gui.create_stats.vcc_mk4.";

    @Test
    void everyEnumConstantHasItsRow() throws IOException {
        Set<String> keys = new TreeSet<>();
        Matcher m = LANG_KEY.matcher(Files.readString(LANG, StandardCharsets.UTF_8));
        while (m.find()) keys.add(m.group(1));

        List<String> wanted = new ArrayList<>();
        for (SteerPreset p : SteerPreset.values()) {
            wanted.add(L + "preset." + p.key());
            wanted.add(L + "profile." + p.key());
            if (p != SteerPreset.CUSTOM) wanted.add(L + "preset." + p.key() + ".hint");
        }
        for (LinkSource src : LinkSource.values()) {
            wanted.add(L + "source." + src.key());
            wanted.add(L + "source." + src.key() + ".hint");
        }
        for (String gear : new String[]{"drive", "reverse", "brake", "hold", "park", "neutral"}) wanted.add(L + "gear." + gear);
        for (AxleRole r : AxleRole.values()) {
            wanted.add(L + "role." + r.key());
            wanted.add(L + "role." + r.key() + ".hint");
        }
        for (Control c : Control.values()) {
            wanted.add(L + "control." + c.key());
            wanted.add(L + "control." + c.key() + ".hint");
        }
        for (JetRole r : JetRole.values()) {
            wanted.add(L + "jetrole." + r.key());
            wanted.add(L + "jetrole." + r.key() + ".hint");
        }
        for (JetMixer.Job j : JetMixer.Job.values()) {
            wanted.add(L + "job." + j.key());
            wanted.add(L + "job." + j.key() + ".why");
        }
        for (JetKind k : JetKind.values()) {
            wanted.add(L + "jetkind." + k.key());
            wanted.add(L + "jetkind." + k.key() + ".hint");
        }
        for (JetSupply supply : JetSupply.values()) {
            if (supply != JetSupply.NONE) wanted.add(L + "jet.supply." + supply.key());
        }
        for (PartInput in : PartInput.values()) wanted.add(L + "input." + in.key());
        for (TrailerMode mode : TrailerMode.values()) {
            wanted.add(L + "trailermode." + mode.key());
            wanted.add(L + "trailermode." + mode.key() + ".hint");
        }
        for (LinkKind k : LinkKind.values()) wanted.add(L + "link." + k.key());
        for (BodyRole role : BodyRole.values()) wanted.add(L + "trailers.role." + role.key() + ".hint");
        for (Vcc4Console.Page p : Vcc4Console.Page.values()) {
            String tab = p.name().toLowerCase(java.util.Locale.ROOT);
            wanted.add(L + "tab." + tab);
            wanted.add(L + "tab." + tab + ".hint");
        }
        for (String action : new String[]{"rescan", "relearn", "turn_nose", "flip_nose", "reset_auto"}) {
            wanted.add(L + action);
            wanted.add(L + action + ".hint");
        }
        wanted.add("key.create_stats.vcc4_aux1");
        wanted.add("key.create_stats.vcc4_aux2");
        wanted.add("key.create_stats.vcc4_aux3");
        wanted.add("block.create_stats.vcc_mk4");
        wanted.add("block.create_stats.vcc_mk4.tooltip");

        List<String> missing = wanted.stream().filter(k -> !keys.contains(k)).toList();
        assertTrue(missing.isEmpty(), "Console keys with no en_us.json row (they would print raw):\n  "
                + String.join("\n  ", missing));
    }
}
