package picocli;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Guards the exceptions of kind {@code annotation-element} in {@code gradle/api-compat-exceptions.txt}:
 * japicmp (task apiCompat) cannot see default values, so the exception is only valid while the annotation
 * element still exists and still has a default value. If a default is removed, this test fails.
 */
public class ApiCompatExceptionsTest {
    private static final Pattern LINE = Pattern.compile("^annotation-element\\s+(\\S+)#(\\w+)\\(\\)\\s*\\|\\s*(\\S.*)$");

    private static File exceptionsFile() {
        File dir = new File(System.getProperty("user.dir")).getAbsoluteFile();
        while (dir != null) {
            File f = new File(dir, "gradle/api-compat-exceptions.txt");
            if (f.isFile()) { return f; }
            dir = dir.getParentFile();
        }
        return null;
    }

    private static List<String[]> annotationElementExceptions() throws IOException {
        File file = exceptionsFile();
        assertNotNull("gradle/api-compat-exceptions.txt not found from " + System.getProperty("user.dir"), file);
        List<String[]> result = new ArrayList<String[]>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.startsWith("annotation-element")) { continue; }
                Matcher m = LINE.matcher(line);
                assertTrue("malformed annotation-element line: " + line, m.matches());
                result.add(new String[] {m.group(1), m.group(2)});
            }
        } finally {
            reader.close();
        }
        return result;
    }

    @Test
    public void testAnnotationElementExceptionsHaveDefaultValue() throws Exception {
        List<String[]> exceptions = annotationElementExceptions();
        assertEquals("expected one entry per known annotation element", 6, exceptions.size());
        for (String[] e : exceptions) {
            Class<?> type = Class.forName(e[0]);
            assertTrue(e[0] + " must be an annotation type", type.isAnnotation());
            Method method = type.getDeclaredMethod(e[1]);
            assertNotNull(e[0] + "#" + e[1] + "() must have a default value (otherwise it is a source-incompatible API change"
                    + " and its api-compat exception must be removed)", method.getDefaultValue());
        }
    }
}
