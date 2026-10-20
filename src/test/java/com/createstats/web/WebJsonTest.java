package com.createstats.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

class WebJsonTest {
    private static JsonObject parse(String json) {
        final JsonElement parsed = JsonParser.parseString(json);
        assertTrue(parsed.isJsonObject(), "not an object: " + json);
        return parsed.getAsJsonObject();
    }

    @Test
    void writesNestedStructureWithCorrectSeparators() {
        final WebJson j = new WebJson();
        j.beginObject()
                .put("a", 1)
                .put("b", "two")
                .name("c").beginObject().put("d", true).endObject()
                .name("e").beginArray().value(1).value(2).value(3).endArray()
                .name("empty").beginObject().endObject()
                .name("none").beginArray().endArray()
                .endObject();

        final JsonObject o = parse(j.toString());
        assertEquals(1, o.get("a").getAsInt());
        assertEquals("two", o.get("b").getAsString());
        assertTrue(o.getAsJsonObject("c").get("d").getAsBoolean());
        assertEquals(3, o.getAsJsonArray("e").size());
        assertEquals(0, o.getAsJsonObject("empty").size());
        assertEquals(0, o.getAsJsonArray("none").size());
    }

    @Test
    void survivesEveryCharacterAPlayerCanTypeIntoALabel() {
        final String[] hostile = {
            "quote \" inside",
            "backslash \\ inside",
            "both \\\" together",
            "</script><img src=x>",
            "newline\nand tab\t",
            "carriage\rreturn",
            "accents éàü",
            "japanese 蒸気機関",
            "",
            "   ",
        };
        for (final String label : hostile) {
            final WebJson j = new WebJson();
            j.beginObject().put("label", label).endObject();
            final JsonObject o = parse(j.toString());
            assertEquals(label, o.get("label").getAsString(),
                    "did not round trip: " + label.replace("\n", "[nl]"));
        }
    }

    @Test
    void neverEmitsAngleBrackets() {
        final WebJson j = new WebJson();
        j.beginObject().put("x", "a <b> c </script> d").endObject();
        final String out = j.toString();
        assertFalse(out.contains("<"), out);
        assertFalse(out.contains(">"), out);
        assertEquals("a <b> c </script> d", parse(out).get("x").getAsString());
    }

    @Test
    void escapesTheUnicodeLineSeparators() {
        final String text = "a" + ((char) 0x2028) + "b" + ((char) 0x2029) + "c";
        final WebJson j = new WebJson();
        j.beginObject().put("x", text).endObject();
        final String out = j.toString();
        assertTrue(out.contains("\\u2028"), out);
        assertTrue(out.contains("\\u2029"), out);
        assertEquals(text, parse(out).get("x").getAsString());
    }

    @Test
    void nonFiniteNumbersBecomeZeroRatherThanBreakingTheDocument() {
        final WebJson j = new WebJson();
        j.beginObject()
                .put("nan", Double.NaN)
                .put("inf", Double.POSITIVE_INFINITY)
                .put("ninf", Double.NEGATIVE_INFINITY)
                .endObject();
        final JsonObject o = parse(j.toString());
        assertEquals(0.0, o.get("nan").getAsDouble());
        assertEquals(0.0, o.get("inf").getAsDouble());
        assertEquals(0.0, o.get("ninf").getAsDouble());
    }

    @Test
    void roundsToThreeDecimalsAndDropsPointlessDecimalPoints() {
        assertEquals("1", WebJson.compact(1.0));
        assertEquals("-4", WebJson.compact(-4.0));
        assertEquals("0", WebJson.compact(0.0));
        assertEquals("0.125", WebJson.compact(0.125));
        assertEquals("0.333", WebJson.compact(1.0 / 3.0));
        assertEquals("12345", WebJson.compact(12345.0));
        assertEquals("0", WebJson.compact(0.0001));
    }

    @Test
    void writesColoursAsSixDigitHexWithTheAlphaDropped() {
        assertEquals("#ff5c90", WebJson.hex(0xFFFF5C90));
        assertEquals("#000000", WebJson.hex(0xFF000000));
        assertEquals("#ffffff", WebJson.hex(0x00FFFFFF));
        assertEquals("#34d399", WebJson.hex(0xFF34D399));
    }
}
