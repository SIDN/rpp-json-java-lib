package nl.sidn.rpp.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

// Validates every example in examples/ against the Domain Name create schema extended by extProperty1.
public final class Main {

    static final String DOMAIN_CREATE = "https://rpp.example/rpp/schema.json#/$defs/domainObject.create";
    static final String EFFECTIVE_ID = "https://rpp.example/rpp/effective/domain.create.json";

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        RppSchemaLibrary library = new RppSchemaLibrary()
                .addSchemasFrom(Path.of("schemas/base"))
                .addSchemasFrom(Path.of("schemas/extensions"));

        EffectiveSchema effective = library.effectiveSchema(DOMAIN_CREATE, EFFECTIVE_ID);
        System.out.println("Effective schema:");
        System.out.println(effective.toJson().toPrettyString());

        List<Path> examples;
        try (Stream<Path> list = Files.list(Path.of("examples"))) {
            examples = list.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        for (Path example : examples) {
            JsonNode instance = mapper.readTree(example.toFile());
            List<String> errors = effective.validate(instance);
            System.out.println();
            System.out.println(example.getFileName() + ": " + (errors.isEmpty() ? "valid" : "invalid"));
            errors.forEach(e -> System.out.println("  " + e));
        }
    }
}
