package com.github.senocak.etcd.config;

import com.github.senocak.etcd.EtcdSessionRepository;
import io.etcd.jetcd.Client;
import org.jspecify.annotations.NonNull;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.session.MapSession;
import org.springframework.session.config.annotation.web.http.SpringHttpSessionConfiguration;
import org.springframework.util.StringValueResolver;

import java.time.Duration;
import java.util.Arrays;
import tools.jackson.databind.json.JsonMapper;

/** Creates the Jetcd client and Spring Session repository selected by {@link EnableEtcdHttpSession}. */
@Configuration(proxyBeanMethods = false)
@Import(SpringHttpSessionConfiguration.class)
public class EtcdHttpSessionConfiguration implements EmbeddedValueResolverAware, ImportAware {
    private StringValueResolver embeddedValueResolver;
    private Duration maxInactiveInterval = MapSession.DEFAULT_MAX_INACTIVE_INTERVAL;
    private String[] endpoints = {"http://localhost:2379"};
    private String namespace = EtcdSessionRepository.DEFAULT_NAMESPACE;

    @Bean(destroyMethod = "close")
    public Client etcdSessionClient() {
        return Client.builder().endpoints(resolvedEndpoints()).build();
    }

    @Bean
    public EtcdSessionRepository sessionRepository(final Client etcdSessionClient) {
        return new EtcdSessionRepository(
                JsonMapper.builder().build(),
                etcdSessionClient,
                maxInactiveInterval,
                resolve(namespace)
        );
    }

    @Override
    public void setEmbeddedValueResolver(@NonNull final StringValueResolver resolver) {
        this.embeddedValueResolver = resolver;
    }

    @Override
    public void setImportMetadata(@NonNull final AnnotationMetadata metadata) {
        final AnnotationAttributes attributes = AnnotationAttributes.fromMap(
            metadata.getAnnotationAttributes(EnableEtcdHttpSession.class.getName())
        );
        if (attributes == null) {
            return;
        }
        this.maxInactiveInterval = Duration.ofSeconds(
                attributes.getNumber("maxInactiveIntervalInSeconds").longValue()
        );
        this.endpoints = attributes.getStringArray("endpoints");
        this.namespace = attributes.getString("namespace");
    }

    private String @NonNull [] resolvedEndpoints() {
        return Arrays.stream(endpoints)
                .map(this::resolve)
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toArray(String[]::new);
    }

    private String resolve(final String value) {
        if (embeddedValueResolver == null) {
            return value;
        }
        final String resolved = embeddedValueResolver.resolveStringValue(value);
        return resolved != null ? resolved : value;
    }
}
