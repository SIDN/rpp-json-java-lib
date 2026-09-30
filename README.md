# rpp-json-java-lib

A generic Java library for consumers (servers and clients) of the RESTful Provisioning Protocol (RPP) that need to handle RPP JSON extensions. It reads the RPP base schema and the extension schemas, composes them into the effective schema of a base object, and validates JSON instances against it.

The library follows the *Consumer Implementation*, *JSON Schema Composition* and *Validation* sections of the RPP extension guidelines ([draft-wullink-rpp-extension-guidelines](https://github.com/SIDN/ietf-rpp-extension-guidelines)) and the extension framework of [draft-ietf-rpp-json](https://github.com/ietf-wg-rpp/draft-ietf-rpp-json).

## Approach

A consumer does not need generated code for every combination of a base object and the extensions offered by a particular server. Extension schemas are additive, self-contained and independent of one another, so composing a base object with the extensions that apply is a runtime decision:

- The base object and each extension are kept separate. The library contains no code that is specific to an extension; supporting a new extension only requires adding its JSON Schema document.
- An extension declares the base object definitions it extends with the `rpp:extends` property. The library uses these mappings to find the extensions of a base object without knowing them in advance.
- The library only composes and validates the extensions that have been loaded into it. A consumer loads the extensions that the server has advertised in the `extensions` list of its RPP Discovery response, so one library instance corresponds to one deployment profile.
- An extension that is not loaded contributes no properties. Properties belonging to an extension that is not loaded are rejected as undeclared.
- The extension's properties are merged flat with the base object, as the JSON Schema `allOf` composition requires; they are not nested under a separate object.

The library does not retrieve or parse the RPP Discovery response. Determining which extensions a server supports, and loading their schemas, is the responsibility of the calling application.

## How it works

For a base object definition, for example `https://rpp.example/rpp/schema.json#/$defs/domainObject.create`, the library:

1. Finds every loaded extension schema whose `rpp:extends` property maps that definition to a local definition.
2. Builds an effective schema, with its own `$id`, that references the base definition and each extension definition in an `allOf`. The base definition is referenced, never copied.
3. Adds `"unevaluatedProperties": false` to every object-schema node of copies of all schemas, so undeclared properties are rejected while any combination of loaded extensions validates. The schema documents that were loaded are never modified.
4. Validates JSON instances against the result. All references are resolved from the loaded schemas; nothing is fetched from the network.

## Classes

All classes are in the package `nl.sidn.rpp.schema`.

| Class | Description |
| --- | --- |
| `RppSchemaLibrary` | Entry point. Registers schema documents by their top-level `$id`, individually (`addSchema`) or from a directory (`addSchemasFrom`), and builds an effective schema for a base object definition (`effectiveSchema`). |
| `EffectiveSchema` | A base object composed with its extensions. Provides the effective schema document (`toJson`), the list of validation errors for an instance (`validate`), and a boolean check (`isValid`). |
| `UnevaluatedPropertiesInjector` | Adds `"unevaluatedProperties": false` to a copy of a schema, following the algorithm of the RPP JSON specification. |
| `Main` | Command line example that validates the instances in `java/examples`. |

## Usage

```java
RppSchemaLibrary library = new RppSchemaLibrary()
        .addSchemasFrom(Path.of("schemas/base"))
        .addSchemasFrom(Path.of("schemas/extensions"));

EffectiveSchema effective = library.effectiveSchema(
        "https://rpp.example/rpp/schema.json#/$defs/domainObject.create",
        "https://rpp.example/rpp/effective/domain.create.json");

List<String> errors = effective.validate(new ObjectMapper().readTree(instanceJson));
```

An empty list means that the instance conforms to the base object and to every loaded extension. The `$id` of an effective schema must differ from the `$id` of every loaded schema.

## Example extension

`java/schemas/extensions/ext-domain-extproperty1.json` is an example extension that adds the required string property `extProperty1` to the Domain Name create definition, and an optional one to the read definition. `java/schemas/base/rpp-core.json` is a reduced version of the RPP base schema, and `java/examples` contains valid and invalid example instances.

## Build and run

Requires a Java 17 or later JDK and Apache Maven. Run the commands from the `java` directory, because the schema and example directories are resolved relative to the working directory.

```sh
cd java

# Run the unit tests
mvn test

# Run Main: prints the effective schema and validates each instance in examples/
mvn compile exec:java
```

The text of the validation messages depends on the default locale of the JVM. For English messages, run for example `MAVEN_OPTS="-Duser.language=en" mvn compile exec:java`.

## Dependencies

- [Jackson](https://github.com/FasterXML/jackson) for JSON processing
- [networknt json-schema-validator](https://github.com/networknt/json-schema-validator) for JSON Schema 2020-12 validation