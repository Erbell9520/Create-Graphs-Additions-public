package com.createstats.vccmk4;

import com.createstats.vccmk4.core.LinkKind;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrailerLinksTest {
    private static final Path LANG = Path.of("src/main/resources/assets/create_stats/lang/en_us.json");
    private static final Pattern LANG_KEY = Pattern.compile("\"([^\"]+)\"\\s*:");

    @Test
    void everyLinkTypeWrittenIsRegisteredOnceWithANameOfItsOwn() {
        Set<Class<?>> registered = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (TrailerLinks.Type t : TrailerLinks.ALL) {
            assertTrue(registered.add(t.getClass()), t.getClass().getSimpleName() + " is registered twice");
            assertFalse(t.name().isBlank(), t.getClass().getSimpleName() + " has no name");
            assertTrue(names.add(t.name()), "two link types are both called " + t.name());
        }
        int written = 0;
        for (Class<?> c : TrailerLinks.class.getDeclaredClasses()) {
            if (!TrailerLinks.Type.class.isAssignableFrom(c) || c.isInterface() || Modifier.isAbstract(c.getModifiers())) continue;
            written++;
            assertTrue(registered.contains(c), c.getSimpleName() + " implements TrailerLinks.Type but is not in TrailerLinks.ALL, "
                    + "so the scan would never find that link");
        }
        assertTrue(written >= 4, "found only " + written + " link types; the reflection has drifted from how they are declared");
        assertNull(TrailerLinks.of(null));
    }

    @Test
    void everyLinkKindHasAName() throws IOException {
        Set<String> keys = new HashSet<>();
        Matcher m = LANG_KEY.matcher(Files.readString(LANG, StandardCharsets.UTF_8));
        while (m.find()) keys.add(m.group(1));
        for (LinkKind k : LinkKind.values()) {
            String key = "gui.create_stats.vcc_mk4.link." + k.key();
            assertTrue(keys.contains(key), key + " is missing: the console words every link by its name");
        }
    }

    @Test
    void severalTiesReadAsTheOneLinkTheyMake() {
        TrailerLinks.Reading slack = new TrailerLinks.Reading(-0.6, Double.NaN, false, false, null);
        TrailerLinks.Reading taut = new TrailerLinks.Reading(0.1, Double.NaN, false, false, null);
        assertEquals(0.1, TrailerLinks.combine(LinkKind.Reading.GAP, List.of(slack, taut)).value(), 1e-9,
                "a gap is the tautest rope's: that is the one pulling");
        assertEquals(0.1, TrailerLinks.combine(LinkKind.Reading.GAP, List.of(taut, slack)).value(), 1e-9, "whichever is met first");
        assertNotNull(TrailerLinks.combine(LinkKind.Reading.GAP, Arrays.asList(null, taut)), "one rope of two gone is not the link gone");
        assertNull(TrailerLinks.combine(LinkKind.Reading.GAP, Arrays.asList(null, null)), "both gone is");
        TrailerLinks.Reading one = new TrailerLinks.Reading(0.4, 30.0, false, false, null);
        TrailerLinks.Reading two = new TrailerLinks.Reading(0.2, 10.0, true, false, null);
        TrailerLinks.Reading springs = TrailerLinks.combine(LinkKind.Reading.STRETCH, List.of(one, two));
        assertEquals(0.3, springs.value(), 1e-9, "stretches average");
        assertEquals(40.0, springs.force(), 1e-9, "and their pulls add");
        assertTrue(springs.warning(), "one near breaking warns for the pair");
        assertTrue(Double.isNaN(TrailerLinks.combine(LinkKind.Reading.GAP, List.of(TrailerLinks.Reading.present(null))).value()),
                "nothing read, nothing made up");
    }

    @Test
    void aLinkMetFromEitherEndHasOneKey() {
        net.minecraft.core.BlockPos a = new net.minecraft.core.BlockPos(1, 2, 3);
        net.minecraft.core.BlockPos b = new net.minecraft.core.BlockPos(4, 5, 6);
        assertTrue(TrailerLinks.pairKey("spring", a, b).equals(TrailerLinks.pairKey("spring", b, a)));
        assertFalse(TrailerLinks.pairKey("spring", a, b).equals(TrailerLinks.pairKey("dock", a, b)));
        assertTrue(List.copyOf(TrailerLinks.ALL).size() == TrailerLinks.ALL.size());
    }
}
