package com.github.senocak.etcd;

import org.jspecify.annotations.NonNull;
import org.springframework.session.MapSession;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;

record JsonSessionRecord(String id,
                         Instant creationTime,
                         Instant lastAccessedTime,
                         long maxInactiveIntervalSeconds,
                         Map<String, JsonNode> attributes) {
    static JsonSessionRecord from(final @NonNull MapSession session, final SessionAttributeJsonSerializer serializer) {
        final Map<String, JsonNode> attributes = new HashMap<>();
        for (String name : session.getAttributeNames()) {
            attributes.put(name, serializer.serialize(session.getAttribute(name)));
        }
        return new JsonSessionRecord(
                session.getId(),
                session.getCreationTime(),
                session.getLastAccessedTime(),
                session.getMaxInactiveInterval().getSeconds(),
                attributes
        );
    }

    @NonNull MapSession toMapSession(final SessionAttributeJsonSerializer serializer) {
        final MapSession session = new MapSession(id);
        session.setCreationTime(creationTime);
        session.setLastAccessedTime(lastAccessedTime);
        session.setMaxInactiveInterval(Duration.ofSeconds(maxInactiveIntervalSeconds));
        if (attributes != null) {
            attributes.forEach((name, value) -> session.setAttribute(name, serializer.deserialize(value)));
        }
        return session;
    }
}
