package com.github.senocak.file.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class SpringSecurityTest {

    @Test
    void passwordEncoderIsBCryptAndVerifiesEncodedPasswords() {
        final PasswordEncoder encoder = SpringSecurity.passwordEncoder();

        assertThat(encoder).isInstanceOf(BCryptPasswordEncoder.class);

        final String encoded = encoder.encode("secret");
        assertThat(encoded).isNotEqualTo("secret");
        assertThat(encoder.matches("secret", encoded)).isTrue();
        assertThat(encoder.matches("wrong", encoded)).isFalse();
    }
}