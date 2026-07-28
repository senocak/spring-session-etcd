package com.github.senocak.etcd;

import org.junit.jupiter.api.Test;
import org.springframework.session.MapSession;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EtcdSessionTest {
    @Test
    void cleanExistingSessionIsNotWrittenAgain() {
        final List<String> written = new ArrayList<>();
        final EtcdSession session = new EtcdSession(new MapSession("existing"), false,
                value -> written.add(value.getId()), ignored -> { });

        session.persist();

        assertThat(written).isEmpty();
    }

    @Test
    void changingSessionIdDeletesOldKeyBeforeWritingNewOne() {
        final List<String> written = new ArrayList<>();
        final List<String> deleted = new ArrayList<>();
        final EtcdSession session = new EtcdSession(new MapSession("old-id"), false,
                value -> written.add(value.getId()), deleted::add);

        final String newId = session.changeSessionId();
        session.persist();

        assertThat(deleted).containsExactly("old-id");
        assertThat(written).containsExactly(newId);
        assertThat(session.isNewSession()).isFalse();
    }
}
