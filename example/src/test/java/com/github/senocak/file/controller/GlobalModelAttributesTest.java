package com.github.senocak.file.controller;

import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalModelAttributesTest {

    private final GlobalModelAttributes advice = new GlobalModelAttributes();

    @Test
    void loggedInIsTrueWhenPrincipalPresent() {
        final Model model = new ConcurrentModel();
        final Principal principal = () -> "anil@example.com";

        advice.addCommonAttributes(model, principal);

        assertThat(model.getAttribute("loggedIn")).isEqualTo(true);
    }

    @Test
    void loggedInIsFalseWhenPrincipalAbsent() {
        final Model model = new ConcurrentModel();

        advice.addCommonAttributes(model, null);

        assertThat(model.getAttribute("loggedIn")).isEqualTo(false);
    }
}