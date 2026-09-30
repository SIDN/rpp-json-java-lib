package nl.sidn.rpp.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SpecVersion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Loads the RPP base schema and extension schemas, composes them into an effective schema
 * for a base object, and validates JSON instances against it.
 *
 * <p>Extensions declare the base objects they extend using the {@code rpp:extends} property.
 * Only the extensions that have been added to a library instance take part in composition, so
 * one library instance corresponds to one deployment profile.
 */
public final class RppSchemaLibrary {

    public static final String EXTENDS = "rpp:extends";
    private static final String DRAFT_2020_12 = "https://json-schema.org/draft/2020-12/schema";

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, JsonNode> schemas = new LinkedHashMap<>();

    /** Registers a schema document under its top-level {@code $id}. */
    public RppSchemaLibrary addSchema(JsonNode schema) {
        String id = schema.path("$id").asText(null);
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Schema has no top-level $id");
        }
        schemas.put(id, schema);
        return this;
    }

    public RppSchemaLibrary addSchema(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return addSchema(mapper.readTree(in));
        }
    }

    /** Registers every {@code *.json} file in the directory. */
    public RppSchemaLibrary addSchemasFrom(Path directory) throws IOException {
        List<Path> files;
        try (Stream<Path> list = Files.list(directory)) {
            files = list.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        for (Path file : files) {
            addSchema(file);
        }
        return this;
    }

    /**
     * Builds the effective schema for a base object such as
     * {@code https://rpp.example/rpp/schema.json#/$defs/domainObject.create}.
     *
     * @param baseRef     absolute reference to the base object definition
     * @param effectiveId the {@code $id} to assign to the effective schema, distinct from all other schemas
     */
    public EffectiveSchema effectiveSchema(String baseRef, String effectiveId) {
        String baseId = stripFragment(baseRef);
        if (!schemas.containsKey(baseId)) {
            throw new IllegalArgumentException("Base schema not loaded: " + baseId);
        }
        if (schemas.containsKey(effectiveId)) {
            throw new IllegalArgumentException("Effective $id must differ from any loaded schema: " + effectiveId);
        }

        ObjectNode document = buildEffectiveDocument(baseRef, effectiveId);

        Map<String, String> registry = new HashMap<>();
        schemas.forEach((id, schema) -> registry.put(id, UnevaluatedPropertiesInjector.inject(schema).toString()));
        registry.put(effectiveId, UnevaluatedPropertiesInjector.inject(document).toString());

        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012,
                builder -> builder.schemaLoaders(loaders -> loaders.schemas(registry)));
        JsonSchema validator = factory.getSchema(SchemaLocation.of(effectiveId));
        return new EffectiveSchema(effectiveId, document, validator);
    }

    private ObjectNode buildEffectiveDocument(String baseRef, String effectiveId) {
        String defName = "effective." + baseRef.substring(baseRef.lastIndexOf('/') + 1);

        ObjectNode document = mapper.createObjectNode();
        document.put("$schema", DRAFT_2020_12);
        document.put("$id", effectiveId);
        document.put("$ref", "#/$defs/" + defName);

        ArrayNode allOf = document.putObject("$defs").putObject(defName).putArray("allOf");
        allOf.addObject().put("$ref", baseRef);
        for (String extensionRef : extensionsOf(baseRef)) {
            allOf.addObject().put("$ref", extensionRef);
        }
        return document;
    }

    // Resolves each rpp:extends mapping that targets baseRef to an absolute extension reference.
    private List<String> extensionsOf(String baseRef) {
        List<String> refs = new ArrayList<>();
        schemas.forEach((id, schema) -> {
            JsonNode target = schema.path(EXTENDS).get(baseRef);
            if (target != null) {
                String ref = target.asText();
                refs.add(ref.startsWith("#") ? id + ref : ref);
            }
        });
        return refs;
    }

    private static String stripFragment(String ref) {
        int hash = ref.indexOf('#');
        return hash < 0 ? ref : ref.substring(0, hash);
    }
}
