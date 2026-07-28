package com.github.senocak.etcd;

import io.etcd.jetcd.Client;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.session.MapSession;
import org.springframework.session.SessionRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import tools.jackson.databind.ObjectMapper;

/**
 * Stores one JSON session document per etcd key. A lease is attached on every save
 * so etcd removes inactive sessions even when no application node reads them again.
 */
public final class EtcdSessionRepository implements SessionRepository<EtcdSession> {
    public static final String DEFAULT_NAMESPACE = "spring-session/etcd";

    private static final Logger log = LoggerFactory.getLogger(EtcdSessionRepository.class);

    private final ObjectMapper objectMapper;
    private final SessionAttributeJsonSerializer attributeSerializer;
    private final EtcdSessionStore store;
    private final Duration maxInactiveInterval;
    private final String keyPrefix;

    public EtcdSessionRepository(@NonNull final ObjectMapper objectMapper,
                                 @NonNull final Client client,
                                 @NonNull final Duration maxInactiveInterval,
                                 @NonNull final String namespace) {
        this(objectMapper, new JetcdEtcdSessionStore(client), maxInactiveInterval, namespace);
    }

    EtcdSessionRepository(@NonNull final ObjectMapper objectMapper,
                          @NonNull final EtcdSessionStore store,
                          @NonNull final Duration maxInactiveInterval,
                          @NonNull final String namespace) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.maxInactiveInterval = Objects.requireNonNull(maxInactiveInterval, "maxInactiveInterval must not be null");
        this.attributeSerializer = new SessionAttributeJsonSerializer(objectMapper);
        this.keyPrefix = normalizedNamespace(namespace) + "/sessions/";
    }

    @NonNull
    @Override
    public EtcdSession createSession() {
        final MapSession session = new MapSession();
        session.setMaxInactiveInterval(maxInactiveInterval);
        return new EtcdSession(session, true, this::writeSession, this::deleteById);
    }

    @Override
    public void save(@NonNull final EtcdSession session) {
        Objects.requireNonNull(session, "session must not be null");
        if (!session.isNewSession() && !store.contains(key(session.idToCheckForSave()))) {
            throw new IllegalStateException("Session was invalidated");
        }
        session.persist();
    }

    @Nullable
    @Override
    public EtcdSession findById(final @NonNull String id) {
        Objects.requireNonNull(id, "id must not be null");
        final byte[] value = store.get(key(id));
        if (value == null) {
            return null;
        }
        final MapSession session;
        try {
            session = objectMapper.readValue(value, JsonSessionRecord.class).toMapSession(attributeSerializer);
        } catch (Exception ex) {
            log.warn("Unable to deserialize etcd session id={}; treating it as missing", id, ex);
            return null;
        }
        if (session.isExpired()) {
            deleteById(id);
            return null;
        }
        return new EtcdSession(session, false, this::writeSession, this::deleteById);
    }

    @Override
    public void deleteById(final @NonNull String id) {
        Objects.requireNonNull(id, "id must not be null");
        store.delete(key(id));
    }

    private void writeSession(final @NonNull MapSession session) {
        if (session.isExpired()) {
            deleteById(session.getId());
            return;
        }
        try {
            final byte[] payload = objectMapper.writeValueAsBytes(JsonSessionRecord.from(session, attributeSerializer));
            store.put(key(session.getId()), payload, remainingTtl(session));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to persist session id=" + session.getId() + " in etcd", ex);
        }
    }

    @Nullable
    private static Duration remainingTtl(final @NonNull MapSession session) {
        final Duration interval = session.getMaxInactiveInterval();
        if (interval.isNegative()) {
            return null;
        }
        final Duration remaining = Duration.between(Instant.now(), session.getLastAccessedTime().plus(interval));
        final long seconds = Math.max(1, remaining.toSeconds() + (remaining.getNano() == 0 ? 0 : 1));
        return Duration.ofSeconds(seconds);
    }

    private String key(final String id) {
        return keyPrefix + id;
    }

    private static @NonNull String normalizedNamespace(final String namespace) {
        final String value = Objects.requireNonNull(namespace, "namespace must not be null").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("namespace must not be blank");
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
