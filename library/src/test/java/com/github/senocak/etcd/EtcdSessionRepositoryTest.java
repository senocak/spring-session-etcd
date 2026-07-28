package com.github.senocak.etcd;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.session.MapSession;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EtcdSessionRepositoryTest {
    private static final Duration DEFAULT_INTERVAL = Duration.ofMinutes(30);

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private InMemoryEtcdStore store;
    private EtcdSessionRepository repository;

    @BeforeEach
    void setUp() {
        store = new InMemoryEtcdStore();
        repository = new EtcdSessionRepository(objectMapper, store, DEFAULT_INTERVAL, "test-sessions");
    }

    @Test
    void savesAndLoadsSessionAttributes() {
        final EtcdSession session = repository.createSession();
        session.setAttribute("user", "anil");
        session.setAttribute("count", 5);

        repository.save(session);

        final EtcdSession loaded = repository.findById(session.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.<String>getAttribute("user")).isEqualTo("anil");
        assertThat(loaded.<Integer>getAttribute("count")).isEqualTo(5);
        assertThat(store.ttlFor(key(session.getId()))).isEqualTo(DEFAULT_INTERVAL);
    }

    @Test
    void deleteRemovesTheNamespacedEtcdKey() {
        final EtcdSession session = repository.createSession();
        repository.save(session);

        repository.deleteById(session.getId());

        assertThat(store.contains(key(session.getId()))).isFalse();
        assertThat(repository.findById(session.getId())).isNull();
    }

    @Test
    void changingSessionIdDeletesThePreviousKey() {
        final EtcdSession session = repository.createSession();
        repository.save(session);
        final String oldId = session.getId();

        final String newId = session.changeSessionId();
        repository.save(session);

        assertThat(store.contains(key(oldId))).isFalse();
        assertThat(store.contains(key(newId))).isTrue();
    }

    @Test
    void expiredSessionIsRemovedWhenRead() {
        final MapSession expired = new MapSession("expired");
        expired.setLastAccessedTime(Instant.now().minus(Duration.ofHours(1)));
        expired.setMaxInactiveInterval(Duration.ofSeconds(60));
        final byte[] payload = objectMapper.writeValueAsBytes(
                JsonSessionRecord.from(expired, new SessionAttributeJsonSerializer(objectMapper))
        );
        store.put(key(expired.getId()), payload, null);

        assertThat(repository.findById(expired.getId())).isNull();
        assertThat(store.contains(key(expired.getId()))).isFalse();
    }

    @Test
    void refusesToSaveAnInvalidatedSession() {
        final EtcdSession session = repository.createSession();
        repository.save(session);
        repository.deleteById(session.getId());
        session.setAttribute("touched", true);

        assertThatThrownBy(() -> repository.save(session))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Session was invalidated");
    }

    @Test
    void negativeInactiveIntervalCreatesAPersistentKey() {
        final EtcdSession session = repository.createSession();
        session.setMaxInactiveInterval(Duration.ofSeconds(-1));

        repository.save(session);

        assertThat(store.ttlFor(key(session.getId()))).isNull();
    }

    private static String key(final String id) {
        return "test-sessions/sessions/" + id;
    }

    private static final class InMemoryEtcdStore implements EtcdSessionStore {
        private final Map<String, Entry> values = new HashMap<>();

        @Override
        public void put(final String key, final byte[] value, final Duration ttl) {
            values.put(key, new Entry(Arrays.copyOf(value, value.length), ttl));
        }

        @Override
        public byte @Nullable [] get(final String key) {
            final Entry value = values.get(key);
            return value == null ? null : Arrays.copyOf(value.value(), value.value().length);
        }

        @Override
        public boolean contains(final String key) {
            return values.containsKey(key);
        }

        @Override
        public void delete(final String key) {
            values.remove(key);
        }

        @Nullable Duration ttlFor(final String key) {
            final Entry entry = values.get(key);
            return entry == null ? null : entry.ttl();
        }

        private record Entry(byte[] value, Duration ttl) {
        }
    }
}
