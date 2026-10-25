package cliwerk.spec;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Maps between the short type names used in a JSON spec or the DSL (e.g. {@code "boolean"},
 * {@code "File"}) and the actual {@link Class} picocli should use for an option or positional
 * parameter. Shared by {@link CommandSpecJson} and the DSL parser so both accept and produce
 * the same vocabulary.
 * <p>Any scalar type name may be suffixed with {@code "[]"} (e.g. {@code "File[]"}) for a
 * multi-value option/positional backed by a real array type. This is not just cosmetic: picocli
 * requires an array or {@code Collection} type to bind more than one value to an option or
 * positional parameter -- an {@code arity="0..*"} argument declared with a plain scalar type
 * (e.g. {@code "File"}) throws {@code UnmatchedArgumentException} on the second value, since a
 * scalar field can only ever hold one match. {@code Collection}/{@code Map} types (which need an
 * explicit auxiliary type due to generics erasure) are not yet supported; arrays cover the same
 * need without that extra complexity.</p>
 * <p>The vocabulary is deliberately limited to types picocli converts out of the box, so a spec
 * never needs a custom {@code ITypeConverter} to be registered on the host command line.</p>
 */
final class ArgTypes {

    private ArgTypes() {}

    private static final Map<String, Class<?>> TYPES_BY_NAME = new LinkedHashMap<String, Class<?>>();
    static {
        TYPES_BY_NAME.put("String", String.class);
        TYPES_BY_NAME.put("boolean", boolean.class);
        TYPES_BY_NAME.put("int", int.class);
        TYPES_BY_NAME.put("long", long.class);
        TYPES_BY_NAME.put("double", double.class);
        TYPES_BY_NAME.put("File", File.class);
        TYPES_BY_NAME.put("Path", Path.class);
        TYPES_BY_NAME.put("URI", URI.class);
        TYPES_BY_NAME.put("URL", URL.class);
        TYPES_BY_NAME.put("BigDecimal", BigDecimal.class);
        TYPES_BY_NAME.put("BigInteger", BigInteger.class);
    }
    private static final Map<Class<?>, String> NAMES_BY_TYPE = new LinkedHashMap<Class<?>, String>();
    static {
        for (Map.Entry<String, Class<?>> entry : TYPES_BY_NAME.entrySet()) {
            NAMES_BY_TYPE.put(entry.getValue(), entry.getKey());
        }
    }

    /** Returns the supported type names (the vocabulary accepted by {@link #toClass(String)}), e.g. for validation or error messages. */
    static Set<String> names() {
        return Collections.unmodifiableSet(TYPES_BY_NAME.keySet());
    }

    private static final String ARRAY_SUFFIX = "[]";

    static Class<?> toClass(String typeName) {
        if (typeName.endsWith(ARRAY_SUFFIX)) {
            String baseName = typeName.substring(0, typeName.length() - ARRAY_SUFFIX.length());
            return java.lang.reflect.Array.newInstance(toClass(baseName), 0).getClass();
        }
        Class<?> type = TYPES_BY_NAME.get(typeName);
        if (type == null) {
            throw new IllegalArgumentException("Unknown type \"" + typeName + "\": supported types are " + names()
                    + " (optionally suffixed with \"[]\" for a multi-value array type)");
        }
        return type;
    }

    static String toName(Class<?> type) {
        if (type.isArray()) {
            return toName(type.getComponentType()) + ARRAY_SUFFIX;
        }
        String name = NAMES_BY_TYPE.get(type);
        return name != null ? name : type.getSimpleName();
    }
}
