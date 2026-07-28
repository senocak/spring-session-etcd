package com.github.senocak.file.config;

import com.github.senocak.etcd.config.EnableEtcdHttpSession;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Activates the library's etcd-backed Spring Session repository for this demo. */
@Profile("etcd")
@Configuration(proxyBeanMethods = false)
@EnableEtcdHttpSession(
        maxInactiveIntervalInSeconds = 30,
        endpoints = "${app.session.etcd.endpoints:http://localhost:2379}",
        namespace = "${app.session.etcd.namespace:spring-session/demo}"
)
public class EtcdSessionConfig {
}
