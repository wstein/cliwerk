package cliwerk.spec.json;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class JsonTest {

    @Test
    public void parsesEmptyObject() {
        assertEquals(new LinkedHashMap<String, Object>(), Json.parse("{}"));
    }

    @Test
    public void parsesEmptyArray() {
        assertEquals(Arrays.asList(), Json.parse("[]"));
    }

    @Test
    public void parsesStringValue() {
        Map<?, ?> map = (Map<?, ?>) Json.parse("{\"name\": \"flix\"}");
        assertEquals("flix", map.get("name"));
    }

    @Test
    public void parsesIntegralNumberAsLong() {
        Map<?, ?> map = (Map<?, ?>) Json.parse("{\"count\": 42}");
        assertEquals(42L, map.get("count"));
    }

    @Test
    public void parsesFractionalNumberAsDouble() {
        Map<?, ?> map = (Map<?, ?>) Json.parse("{\"ratio\": 3.5}");
        assertEquals(3.5, map.get("ratio"));
    }

    @Test
    public void parsesBooleansAndNull() {
        Map<?, ?> map = (Map<?, ?>) Json.parse("{\"a\": true, \"b\": false, \"c\": null}");
        assertEquals(Boolean.TRUE, map.get("a"));
        assertEquals(Boolean.FALSE, map.get("b"));
        assertNull(map.get("c"));
        assertTrue(map.containsKey("c"));
    }

    @Test
    public void parsesNestedArraysAndObjects() {
        Map<?, ?> map = (Map<?, ?>) Json.parse("{\"names\": [\"-v\", \"--verbose\"], \"nested\": {\"x\": 1}}");
        assertEquals(Arrays.asList("-v", "--verbose"), map.get("names"));
        assertEquals(42L, ((Map<?, ?>) Json.parse("{\"x\": 42}")).get("x"));
        assertEquals(1L, ((Map<?, ?>) map.get("nested")).get("x"));
    }

    @Test
    public void parsesEscapedStrings() {
        Map<?, ?> map = (Map<?, ?>) Json.parse("{\"s\": \"a\\\"b\\\\c\\nd\\te\"}");
        assertEquals("a\"b\\c\nd\te", map.get("s"));
    }

    @Test
    public void parsesUnicodeEscapes() {
        Map<?, ?> map = (Map<?, ?>) Json.parse("{\"s\": \"\\u0041\\u00e9\"}");
        assertEquals("Aé", map.get("s"));
    }

    @Test
    public void skipsInsignificantWhitespace() {
        Map<?, ?> map = (Map<?, ?>) Json.parse("  {\n \"a\" :  1 ,\n\"b\": [ 1 , 2 ]\n}  ");
        assertEquals(1L, map.get("a"));
        assertEquals(Arrays.asList(1L, 2L), map.get("b"));
    }

    @Test
    public void rejectsTrailingGarbage() {
        try {
            Json.parse("{}garbage");
            fail("expected JsonParseException");
        } catch (JsonParseException expected) {
            // ok
        }
    }

    @Test
    public void rejectsMalformedInput() {
        try {
            Json.parse("{\"a\": }");
            fail("expected JsonParseException");
        } catch (JsonParseException expected) {
            // ok
        }
    }

    @Test
    public void writesObjectWithPrettyPrinting() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("name", "flix");
        map.put("count", 42L);
        String json = Json.write(map);
        assertEquals("{\n  \"name\": \"flix\",\n  \"count\": 42\n}", json);
    }

    @Test
    public void writesNestedArraysAndObjects() {
        Map<String, Object> inner = new LinkedHashMap<String, Object>();
        inner.put("x", true);
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("names", Arrays.asList("-v", "--verbose"));
        map.put("nested", inner);
        String json = Json.write(map);
        assertEquals("{\n  \"names\": [\n    \"-v\",\n    \"--verbose\"\n  ],\n  \"nested\": {\n    \"x\": true\n  }\n}", json);
    }

    @Test
    public void escapesSpecialCharactersOnWrite() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("s", "a\"b\\c\nd");
        assertEquals("{\n  \"s\": \"a\\\"b\\\\c\\nd\"\n}", Json.write(map));
    }

    @Test
    public void writesNullAndEmptyContainers() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("a", null);
        map.put("b", Arrays.asList());
        map.put("c", new LinkedHashMap<String, Object>());
        assertEquals("{\n  \"a\": null,\n  \"b\": [],\n  \"c\": {}\n}", Json.write(map));
    }

    @Test
    public void roundTripsParseAndWrite() {
        String original = "{\n  \"name\": \"flix\",\n  \"options\": [\n    {\n      \"names\": [\n        \"-v\",\n        \"--verbose\"\n      ],\n      \"type\": \"boolean\"\n    }\n  ]\n}";
        Object parsed = Json.parse(original);
        String written = Json.write(parsed);
        assertEquals(original, written);
        assertEquals(parsed, Json.parse(written));
    }
}
