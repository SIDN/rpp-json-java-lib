package nl.sidn.rpp.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Adds {@code "unevaluatedProperties": false} to every object-schema node of a schema,
 * following the algorithm of the RPP JSON specification. The input is never modified.
 */
final class UnevaluatedPropertiesInjector {

    private static final Set<String> BRANCH_ARRAYS = Set.of("allOf", "anyOf", "oneOf", "prefixItems");
    private static final Set<String> BRANCH_SCHEMAS = Set.of("not", "if", "then", "else", "contains");
    private static final Set<String> SCHEMA_MAPS = Set.of("properties", "patternProperties", "dependentSchemas");
    private static final Set<String> USE_SITE_SCHEMAS = Set.of("items", "additionalProperties", "unevaluatedItems");

    private UnevaluatedPropertiesInjector() {
    }

    static JsonNode inject(JsonNode schema) {
        return inject(schema, false);
    }

    // inDefs is true for $defs entries and combinator branches: the keyword belongs on the referencing node.
    private static JsonNode inject(JsonNode schema, boolean inDefs) {
        if (!schema.isObject()) {
            return schema;
        }

        ObjectNode result = JsonNodeFactory.instance.objectNode();
        Iterator<Map.Entry<String, JsonNode>> fields = schema.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = field.getKey();
            JsonNode value = field.getValue();

            if (BRANCH_ARRAYS.contains(key) && value.isArray()) {
                var branches = result.putArray(key);
                value.forEach(branch -> branches.add(inject(branch, true)));
            } else if (BRANCH_SCHEMAS.contains(key) && value.isObject()) {
                result.set(key, inject(value, true));
            } else if (SCHEMA_MAPS.contains(key) && value.isObject()) {
                result.set(key, injectMembers(value, false));
            } else if (key.equals("$defs") && value.isObject()) {
                result.set(key, injectMembers(value, true));
            } else if (USE_SITE_SCHEMAS.contains(key) && value.isObject()) {
                result.set(key, inject(value, false));
            } else {
                result.set(key, value.deepCopy());
            }
        }

        if (!inDefs && isObjectSchema(result) && !result.has("additionalProperties")
                && !result.has("unevaluatedProperties")) {
            result.put("unevaluatedProperties", false);
        }
        return result;
    }

    private static ObjectNode injectMembers(JsonNode members, boolean inDefs) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        members.fields().forEachRemaining(e -> result.set(e.getKey(), inject(e.getValue(), inDefs)));
        return result;
    }

    private static boolean isObjectSchema(ObjectNode node) {
        return "object".equals(node.path("type").asText())
                || node.has("properties")
                || node.has("patternProperties")
                || node.has("$ref")
                || node.has("allOf")
                || node.has("anyOf")
                || node.has("oneOf");
    }
}
