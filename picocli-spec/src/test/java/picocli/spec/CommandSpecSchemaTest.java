package picocli.spec;

import org.junit.Test;
import picocli.spec.json.Json;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Guards against drift between the published JSON Schema
 * ({@code /picocli/spec/command-spec.schema.json} on the classpath) and what
 * {@link CommandSpecJson}/{@link ArgTypes} actually accept, by checking the schema's declared
 * {@code type} enum and {@code arity} pattern against the real vocabulary and against the
 * checked-in {@code flix.json} fixture. This is a structural smoke test, not a full JSON
 * Schema validator (the module has no JSON Schema library dependency, by design).
 */
public class CommandSpecSchemaTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readClasspathJson(String resourcePath) {
        InputStream in = CommandSpecSchemaTest.class.getResourceAsStream(resourcePath);
        if (in == null) {
            throw new IllegalStateException("Resource not found on classpath: " + resourcePath);
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) { out.write(buffer, 0, read); }
            return (Map<String, Object>) Json.parse(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read resource: " + resourcePath, e);
        } finally {
            try { in.close(); } catch (IOException ignored) { /* best effort */ }
        }
    }

    private static Map<String, Object> schema() {
        return readClasspathJson("/picocli/spec/command-spec.schema.json");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void optionSchemaDeclaresUsageHelpAndVersionHelp() {
        Map<String, Object> schema = schema();
        Map<String, Object> defs = (Map<String, Object>) schema.get("$defs");
        Map<String, Object> optionProperties = (Map<String, Object>) ((Map<String, Object>) defs.get("option")).get("properties");

        assertTrue(optionProperties.containsKey("usageHelp"));
        assertTrue(optionProperties.containsKey("versionHelp"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void commandSchemaDeclaresMixinStandardHelpOptions() {
        Map<String, Object> schema = schema();
        Map<String, Object> defs = (Map<String, Object>) schema.get("$defs");
        Map<String, Object> commandProperties = (Map<String, Object>) ((Map<String, Object>) defs.get("command")).get("properties");

        assertTrue(commandProperties.containsKey("mixinStandardHelpOptions"));
        Map<String, Object> prop = (Map<String, Object>) commandProperties.get("mixinStandardHelpOptions");
        assertEquals("boolean", prop.get("type"));
        assertEquals(Boolean.FALSE, prop.get("default"));

        assertTrue(commandProperties.containsKey("helpSection"));
        Map<String, Object> helpSecProp = (Map<String, Object>) commandProperties.get("helpSection");
        assertEquals("string", helpSecProp.get("type"));

        assertTrue(commandProperties.containsKey("helpSections"));
        Map<String, Object> helpSecsProp = (Map<String, Object>) commandProperties.get("helpSections");
        assertEquals("array", helpSecsProp.get("type"));
        assertTrue(defs.containsKey("helpSectionDef"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void positionalParamSchemaDeclaresHelpSection() {
        Map<String, Object> schema = schema();
        Map<String, Object> defs = (Map<String, Object>) schema.get("$defs");
        Map<String, Object> paramProperties = (Map<String, Object>) ((Map<String, Object>) defs.get("positionalParam")).get("properties");

        assertTrue(paramProperties.containsKey("helpSection"));
        Map<String, Object> helpSecProp = (Map<String, Object>) paramProperties.get("helpSection");
        assertEquals("string", helpSecProp.get("type"));
    }

    @Test
    public void schemaIsWellFormedJson() {
        Map<String, Object> schema = schema();
        assertEquals("https://json-schema.org/draft/2020-12/schema", schema.get("$schema"));
        assertTrue(schema.containsKey("$defs"));
    }

    /**
     * The checked-in schema's {@code $id} is the *unversioned* "latest" URL. The release
     * workflow (.github/workflows/release.yml) publishes the checked-in schema unchanged at
     * this URL (only the latest schema is published, no versioned copies) -- the URL must
     * match the GitHub Pages location the workflow copies the file to.
     */
    @Test
    public void schemaIdIsTheUnversionedLatestUrl() {
        assertEquals("https://wstein.github.io/cliwerk/spec/schema/command-spec.schema.json", schema().get("$id"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void schemaTypePatternMatchesEveryArgTypesNameAndItsArraySuffix() {
        Map<String, Object> schema = schema();
        Map<String, Object> defs = (Map<String, Object>) schema.get("$defs");
        Pattern typePattern = Pattern.compile((String) ((Map<String, Object>) defs.get("type")).get("pattern"));

        for (String name : ArgTypes.names()) {
            assertTrue("schema type pattern should accept \"" + name + "\"", typePattern.matcher(name).matches());
            assertTrue("schema type pattern should accept \"" + name + "[]\"", typePattern.matcher(name + "[]").matches());
        }
        assertFalse("schema type pattern should reject an unknown type", typePattern.matcher("Frobnicator").matches());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void everyFixtureTypeValueMatchesTheSchemaTypePattern() {
        Map<String, Object> schema = schema();
        Map<String, Object> defs = (Map<String, Object>) schema.get("$defs");
        Pattern typePattern = Pattern.compile((String) ((Map<String, Object>) defs.get("type")).get("pattern"));

        Map<String, Object> fixture = readClasspathJson("/picocli/spec/fixtures/flix.json");
        Set<Object> typesInFixture = new HashSet<Object>();
        collectValuesOfKey(fixture, "type", typesInFixture);

        assertTrue("expected at least one \"type\" value in the fixture", !typesInFixture.isEmpty());
        for (Object type : typesInFixture) {
            assertTrue("fixture uses a type not matching the schema pattern: " + type, typePattern.matcher((String) type).matches());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void everyFixtureArityValueMatchesTheSchemaPattern() {
        Map<String, Object> schema = schema();
        Map<String, Object> defs = (Map<String, Object>) schema.get("$defs");
        Pattern arityPattern = Pattern.compile((String) ((Map<String, Object>) defs.get("arity")).get("pattern"));

        Map<String, Object> fixture = readClasspathJson("/picocli/spec/fixtures/flix.json");
        Set<Object> aritiesInFixture = new HashSet<Object>();
        collectValuesOfKey(fixture, "arity", aritiesInFixture);

        assertTrue("expected at least one \"arity\" value in the fixture", !aritiesInFixture.isEmpty());
        for (Object arity : aritiesInFixture) {
            assertTrue("arity \"" + arity + "\" does not match schema pattern", arityPattern.matcher((String) arity).matches());
        }
    }

    @SuppressWarnings("unchecked")
    private static void collectValuesOfKey(Object node, String key, Set<Object> result) {
        if (node instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) node;
            if (map.containsKey(key)) { result.add(map.get(key)); }
            for (Object value : map.values()) { collectValuesOfKey(value, key, result); }
        } else if (node instanceof List) {
            for (Object item : (List<Object>) node) { collectValuesOfKey(item, key, result); }
        }
    }
}
