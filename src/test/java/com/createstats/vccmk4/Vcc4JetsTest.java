package com.createstats.vccmk4;

import com.createstats.vccmk4.core.JetKind;
import com.createstats.vccmk4.core.JetSupply;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Vcc4JetsTest {
    private static final Path LANG = Path.of("src/main/resources/assets/create_stats/lang/en_us.json");
    private static final Pattern LANG_KEY = Pattern.compile("\"([^\"]+)\"\\s*:");

    @Test
    void everyJetTypeWrittenIsRegisteredOnceWithANameOfItsOwn() {
        Set<Class<?>> registered = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (Vcc4Jets.Type t : Vcc4Jets.ALL) {
            assertTrue(registered.add(t.getClass()), t.getClass().getSimpleName() + " is registered twice");
            assertFalse(t.name().isBlank(), t.getClass().getSimpleName() + " has no name");
            assertTrue(names.add(t.name()), "two jet types are both called " + t.name());
        }
        int written = 0;
        for (Class<?> c : Vcc4Jets.class.getDeclaredClasses()) {
            if (!Vcc4Jets.Type.class.isAssignableFrom(c) || c.isInterface() || Modifier.isAbstract(c.getModifiers())) continue;
            written++;
            assertTrue(registered.contains(c), c.getSimpleName() + " implements Vcc4Jets.Type but is not in Vcc4Jets.ALL, "
                    + "so the scan would never find that jet");
        }
        assertTrue(written >= 1, "found no jet types; the reflection has drifted from how they are declared");
        assertNull(Vcc4Jets.of(null));
    }

    @Test
    void everyKindAndSupplyStateHasItsWords() throws IOException {
        Set<String> keys = new HashSet<>();
        Matcher m = LANG_KEY.matcher(Files.readString(LANG, StandardCharsets.UTF_8));
        while (m.find()) keys.add(m.group(1));
        for (JetKind k : JetKind.values()) {
            for (String suffix : new String[]{"", ".hint"}) {
                String key = "gui.create_stats.vcc_mk4.jetkind." + k.key() + suffix;
                assertTrue(keys.contains(key), key + " is missing: the console names every jet by its kind");
            }
        }
        for (JetSupply s : JetSupply.values()) {
            if (s == JetSupply.NONE) continue;
            String key = "gui.create_stats.vcc_mk4.jet.supply." + s.key();
            assertTrue(keys.contains(key), key + " is missing: the console says whether a thruster has fuel");
        }
    }
}
