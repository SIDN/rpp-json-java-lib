package nl.sidn.rpp.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.ValidationMessage;

import java.util.List;

/** A base object schema composed with its extensions, ready to validate JSON instances. */
public final class EffectiveSchema {

    private final String id;
    private final JsonNode document;
    private final JsonSchema validator;

    EffectiveSchema(String id, JsonNode document, JsonSchema validator) {
        this.id = id;
        this.document = document;
        this.validator = validator;
    }

    public String id() {
        return id;
    }

    /** The published effective schema document, without injected keywords. */
    public JsonNode toJson() {
        return document;
    }

    /** Returns the validation errors for the instance; an empty list means the instance is valid. */
    public List<String> validate(JsonNode instance) {
        return validator.validate(instance).stream()
                .map(ValidationMessage::getMessage)
                .sorted()
                .toList();
    }

    public boolean isValid(JsonNode instance) {
        return validate(instance).isEmpty();
    }
}
