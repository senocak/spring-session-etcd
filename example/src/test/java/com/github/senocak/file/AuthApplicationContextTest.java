package com.github.senocak.file;

import com.github.senocak.file.controller.AuthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the full application context under the etcd session profile. The Jetcd client is created
 * during startup but does not connect until a session operation is performed.
 */
@SpringBootTest
@ActiveProfiles("etcd")
class AuthApplicationContextTest {

    @DynamicPropertySource
    static void useInMemoryDatabase(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:demotest;DB_CLOSE_DELAY=-1");
    }

    @Autowired
    private AuthController authController;
    @Autowired
    private SessionRepository<?> sessionRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void contextLoadsWithEtcdBackedSessionRepository() {
        assertThat(authController).isNotNull();
        assertThat(passwordEncoder).isNotNull();
        assertThat(sessionRepository)
                .isInstanceOf(com.github.senocak.etcd.EtcdSessionRepository.class);
    }
}
