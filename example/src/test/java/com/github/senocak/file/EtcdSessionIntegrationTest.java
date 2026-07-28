package com.github.senocak.file;

import com.github.senocak.etcd.EtcdSession;
import com.github.senocak.etcd.EtcdSessionRepository;
import com.github.senocak.file.entity.Role;
import com.github.senocak.file.entity.User;
import com.github.senocak.file.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Verifies HTTP login, session reuse, and logout against a real etcd v3 server.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("etcd")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL) // Enables constructor injection or spring.test.constructor.autowire.mode = all
class EtcdSessionIntegrationTest {
    private static final int ETCD_CLIENT_PORT = 2379;
    private static final String EMAIL = "etcd-session@example.com";
    private static final String PASSWORD = "secret";

    @Container
    static final GenericContainer<?> etcd = new GenericContainer<>(DockerImageName.parse("quay.io/coreos/etcd:v3.6.7"))
            .withExposedPorts(ETCD_CLIENT_PORT)
            .withCommand(
                    "/usr/local/bin/etcd",
                    "--listen-client-urls=http://0.0.0.0:2379",
                    "--advertise-client-urls=http://0.0.0.0:2379"
            )
            .waitingFor(Wait.forLogMessage(".*ready to serve client requests.*\\n", 1));

    private final EtcdSessionRepository sessionRepository;
    private final MockMvc mockMvc;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    EtcdSessionIntegrationTest(final EtcdSessionRepository sessionRepository,
                               final MockMvc mockMvc,
                               final UserRepository userRepository,
                               final PasswordEncoder passwordEncoder) {
        this.sessionRepository = sessionRepository;
        this.mockMvc = mockMvc;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @DynamicPropertySource
    static void configureProperties(final @NonNull DynamicPropertyRegistry registry) {
        registry.add("app.session.etcd.endpoints", () -> "http://" + etcd.getHost() + ":" + etcd.getMappedPort(ETCD_CLIENT_PORT));
        registry.add("app.session.etcd.namespace", () -> "spring-session/integration-test");
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:etcdintegration;DB_CLOSE_DELAY=-1");
    }

    @BeforeEach
    void createAuthenticatedUser() {
        if (userRepository.findByEmail(EMAIL) != null) {
            return;
        }
        final Role adminRole = new Role();
        adminRole.setName("ROLE_ADMIN");
        final User user = new User();
        user.setName("Etcd Session User");
        user.setEmail(EMAIL);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setRoles(List.of(adminRole));
        userRepository.save(user);
    }

    @Test
    void loginStoresSecurityContextInEtcdAndRestoresItForTheNextRequest() throws Exception {
        final LoggedInSession loggedInSession = login();

        final EtcdSession storedSession = sessionRepository.findById(loggedInSession.id());
        assertThat(storedSession).isNotNull();
        final Object securityContext = storedSession.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(securityContext).isNotNull();

        mockMvc.perform(get("/users").cookie(loggedInSession.cookie()))
                .andExpect(status().isOk())
                .andExpect(view().name("users"));
    }

    @Test
    void logoutDeletesTheEtcdBackedHttpSession() throws Exception {
        final LoggedInSession loggedInSession = login();
        assertThat(sessionRepository.findById(loggedInSession.id())).isNotNull();

        mockMvc.perform(post("/logout").cookie(loggedInSession.cookie()))
                .andExpect(status().is3xxRedirection());

        assertThat(sessionRepository.findById(loggedInSession.id())).isNull();
    }

    private @NonNull LoggedInSession login() throws Exception {
        final MvcResult login = mockMvc.perform(post("/login")
                        .param("username", EMAIL)
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        final Cookie sessionCookie = login.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();
        final String id = new String(Base64.getUrlDecoder().decode(sessionCookie.getValue()), StandardCharsets.UTF_8);
        return new LoggedInSession(id, sessionCookie);
    }

    private record LoggedInSession(String id, Cookie cookie) {
    }
}
