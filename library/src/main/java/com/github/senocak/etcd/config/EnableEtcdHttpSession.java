package com.github.senocak.etcd.config;

import com.github.senocak.etcd.EtcdSessionRepository;
import org.springframework.context.annotation.Import;
import org.springframework.session.MapSession;
import org.springframework.session.web.http.SessionRepositoryFilter;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Enables etcd-backed {@link SessionRepositoryFilter HTTP sessions}. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import(EtcdHttpSessionConfiguration.class)
public @interface EnableEtcdHttpSession {
    /** @return inactivity timeout in seconds */
    int maxInactiveIntervalInSeconds() default MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS;

    /** @return one or more etcd v3 client endpoints */
    String[] endpoints() default "http://localhost:2379";

    /** @return etcd key prefix used by this application's sessions */
    String namespace() default EtcdSessionRepository.DEFAULT_NAMESPACE;
}
