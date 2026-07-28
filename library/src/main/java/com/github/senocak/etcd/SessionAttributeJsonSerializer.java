package com.github.senocak.etcd;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.security.jackson.SecurityJacksonModules;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

final class SessionAttributeJsonSerializer {
    private final ObjectMapper objectMapper;

    SessionAttributeJsonSerializer(final @NonNull ObjectMapper source) {
        final BasicPolymorphicTypeValidator.Builder validator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("java.")
                .allowIfSubType("kotlin.")
                .allowIfSubType("org.springframework.")
                .allowIfSubType("com.github.senocak.");
        this.objectMapper = source.rebuild()
                .addModules(SecurityJacksonModules.getModules(EtcdSessionRepository.class.getClassLoader(), validator))
                .build();
    }

    JsonNode serialize(@Nullable final Object value) {
        if (value == null) {
            return objectMapper.nullNode();
        }
        try {
            return objectMapper.readTree(objectMapper.writeValueAsBytes(value));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to serialize session attribute", ex);
        }
    }

    @Nullable
    Object deserialize(@Nullable final JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return objectMapper.readValue(objectMapper.writeValueAsBytes(value), Object.class);
        } catch (Exception _) {
            return legacyPrimitive(value);
        }
    }

    @Nullable
    private static Object legacyPrimitive(final JsonNode value) {
        if (value.isString()) {
            return value.asString();
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isIntegralNumber()) {
            return value.asLong();
        }
        if (value.isFloatingPointNumber()) {
            return value.asDouble();
        }
        return null;
    }
}
