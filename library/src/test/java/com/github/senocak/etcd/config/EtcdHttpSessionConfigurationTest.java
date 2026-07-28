package com.github.senocak.etcd.config;

import io.etcd.jetcd.Client;
import org.junit.jupiter.api.Test;
import org.springframework.core.type.AnnotationMetadata;
import static org.assertj.core.api.Assertions.assertThatCode;

class EtcdHttpSessionConfigurationTest {
    @EnableEtcdHttpSession(
            maxInactiveIntervalInSeconds = 45,
            endpoints = "http://localhost:2379,http://localhost:12379",
            namespace = "test/sessions"
    )
    private static class CustomSessionConfiguration {
    }

    @Test
    void readsTheIntegerTimeoutAndCreatesJetcdClientWithoutConnecting() {
        final EtcdHttpSessionConfiguration configuration = new EtcdHttpSessionConfiguration();
        configuration.setImportMetadata(AnnotationMetadata.introspect(CustomSessionConfiguration.class));

        assertThatCode(() -> {
            final Client client = configuration.etcdSessionClient();
            client.close();
        }).doesNotThrowAnyException();
    }
}
