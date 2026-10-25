package cliwerk.spec;

import org.junit.Test;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class ArgTypesTest {

    @Test
    public void namesReturnsEveryTypeToClassAccepts() {
        Set<String> names = ArgTypes.names();
        Set<Class<?>> resolved = new HashSet<Class<?>>();
        for (String name : names) {
            resolved.add(ArgTypes.toClass(name));
        }
        assertEquals(new HashSet<Class<?>>(Arrays.<Class<?>>asList(
                String.class, boolean.class, int.class, long.class, double.class, File.class,
                Path.class, URI.class, URL.class, BigDecimal.class, BigInteger.class)), resolved);
    }

    @Test
    public void resolvesTypesPicocliHasBuiltInConvertersFor() {
        assertEquals(Path.class, ArgTypes.toClass("Path"));
        assertEquals(URI.class, ArgTypes.toClass("URI"));
        assertEquals(URL.class, ArgTypes.toClass("URL"));
        assertEquals(BigDecimal.class, ArgTypes.toClass("BigDecimal"));
        assertEquals(BigInteger.class, ArgTypes.toClass("BigInteger"));
    }

    @Test
    public void writesEveryNameBackToItself() {
        for (String name : ArgTypes.names()) {
            assertEquals(name, ArgTypes.toName(ArgTypes.toClass(name)));
        }
    }

    @Test
    public void namesIsUnmodifiable() {
        try {
            ArgTypes.names().add("Bogus");
            org.junit.Assert.fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
    }

    @Test
    public void resolvesArrayTypeSuffix() {
        assertEquals(File[].class, ArgTypes.toClass("File[]"));
        assertEquals(String[].class, ArgTypes.toClass("String[]"));
        assertEquals(int[].class, ArgTypes.toClass("int[]"));
    }

    @Test
    public void rejectsArrayOfUnknownBaseType() {
        try {
            ArgTypes.toClass("Frobnicator[]");
            org.junit.Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            org.junit.Assert.assertTrue(expected.getMessage().contains("Frobnicator"));
        }
    }

    @Test
    public void writesArrayTypeBackToItsSuffixedName() {
        assertEquals("File[]", ArgTypes.toName(File[].class));
        assertEquals("String[]", ArgTypes.toName(String[].class));
    }
}
