package com.eventbook.backend.common.deserializer;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Usado via {@code @JsonDeserialize(using = ...)} em campos específicos.
 * Não deve ser registrado como componente Jackson global: isso o aplicaria a todo String.
 */
public class UpperCaseTrimDeserializer extends ValueDeserializer<String> {

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        String value = parser.getValueAsString();
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim().toUpperCase();
    }
}
