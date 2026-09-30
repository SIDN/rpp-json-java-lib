package nl.sidn.rpp.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class RppSchemaLibraryTest {

    private static final String READ = "https://rpp.example/rpp/schema.json#/$defs/domainObject.read";

    private final ObjectMapper mapper = new ObjectMapper();

    private RppSchemaLibrary baseOnly() throws IOException {
        return new RppSchemaLibrary().addSchemasFrom(Path.of("schemas/base"));
    }

    private RppSchemaLibrary withExtension() throws IOException {
        return baseOnly().addSchemasFrom(Path.of("schemas/extensions"));
    }

    private JsonNode example(String name) throws IOException {
        return mapper.readTree(Path.of("examples", name).toFile());
    }

    private JsonNode json(String text) throws IOException {
        return mapper.readTree(text);
    }

    @Test
    void effectiveSchemaComposesBaseAndExtensionWithAllOf() throws IOException {
        EffectiveSchema effective = withExtension().effectiveSchema(Main.DOMAIN_CREATE, Main.EFFECTIVE_ID);

        JsonNode allOf = effective.toJson().at("/$defs/effective.domainObject.create/allOf");
        assertEquals(2, allOf.size());
        assertEquals(Main.DOMAIN_CREATE, allOf.get(0).get("$ref").asText());
        assertEquals("https://rpp.example/schemas/ext-domain-extproperty1.json#/$defs/ext.domain.create",
                allOf.get(1).get("$ref").asText());
    }

    @Test
    void publishedSchemasDoNotContainInjectedKeyword() throws IOException {
        EffectiveSchema effective = withExtension().effectiveSchema(Main.DOMAIN_CREATE, Main.EFFECTIVE_ID);

        assertFalse(effective.toJson().toString().contains("unevaluatedProperties"));
    }

    @Test
    void instanceUsingTheExtensionIsValid() throws IOException {
        EffectiveSchema effective = withExtension().effectiveSchema(Main.DOMAIN_CREATE, Main.EFFECTIVE_ID);

        assertEquals(java.util.List.of(), effective.validate(example("domain-create-valid.json")));
    }

    @Test
    void requiredExtensionPropertyIsEnforced() throws IOException {
        EffectiveSchema effective = withExtension().effectiveSchema(Main.DOMAIN_CREATE, Main.EFFECTIVE_ID);

        assertFalse(effective.isValid(example("domain-create-invalid-missing-required.json")));
    }

    @Test
    void extensionPropertyConstraintsAreEnforced() throws IOException {
        EffectiveSchema effective = withExtension().effectiveSchema(Main.DOMAIN_CREATE, Main.EFFECTIVE_ID);

        assertFalse(effective.isValid(example("domain-create-invalid-type.json")));
    }

    @Test
    void undeclaredPropertyIsRejected() throws IOException {
        EffectiveSchema effective = withExtension().effectiveSchema(Main.DOMAIN_CREATE, Main.EFFECTIVE_ID);

        assertFalse(effective.isValid(example("domain-create-invalid-undeclared.json")));
    }

    @Test
    void undeclaredPropertyInNestedBaseObjectIsRejected() throws IOException {
        EffectiveSchema effective = withExtension().effectiveSchema(Main.DOMAIN_CREATE, Main.EFFECTIVE_ID);

        JsonNode instance = json("""
                {"@type":"domainName","name":"example.example","extProperty1":"x",
                 "registrant":{"@type":"contact","id":"c-1","bogus":true}}""");

        assertFalse(effective.isValid(instance));
    }

    @Test
    void extensionPropertyIsRejectedWhenExtensionIsNotSupported() throws IOException {
        EffectiveSchema effective = baseOnly().effectiveSchema(Main.DOMAIN_CREATE, Main.EFFECTIVE_ID);

        assertTrue(effective.isValid(example("domain-create-invalid-missing-required.json")));
        assertFalse(effective.isValid(example("domain-create-valid.json")));
    }

    @Test
    void extensionMappedToOtherOperationIsComposedForThatOperation() throws IOException {
        EffectiveSchema effective = withExtension().effectiveSchema(READ, "https://rpp.example/rpp/effective/domain.read.json");

        JsonNode instance = json("""
                {"@type":"domainName","name":"example.example","extProperty1":"x"}""");

        assertTrue(effective.isValid(instance));
    }

    @Test
    void effectiveIdMustDifferFromLoadedSchemas() throws IOException {
        RppSchemaLibrary library = withExtension();

        assertThrows(IllegalArgumentException.class,
                () -> library.effectiveSchema(Main.DOMAIN_CREATE, "https://rpp.example/rpp/schema.json"));
    }
}
