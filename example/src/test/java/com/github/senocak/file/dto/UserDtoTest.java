package com.github.senocak.file.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UserDtoTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static UserDto validDto() {
        final UserDto dto = new UserDto();
        dto.setName("Anil");
        dto.setEmail("anil@example.com");
        dto.setPassword("secret");
        return dto;
    }

    private Set<String> violatedProperties(final UserDto dto) {
        return validator.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void validDtoHasNoViolations() {
        assertThat(validator.validate(validDto())).isEmpty();
    }

    @Test
    void emptyNameIsRejected() {
        final UserDto dto = validDto();
        dto.setName("");
        assertThat(violatedProperties(dto)).contains("name");
    }

    @Test
    void emptyEmailIsRejected() {
        final UserDto dto = validDto();
        dto.setEmail("");
        assertThat(violatedProperties(dto)).contains("email");
    }

    @Test
    void malformedEmailIsRejected() {
        final UserDto dto = validDto();
        dto.setEmail("not-an-email");
        assertThat(violatedProperties(dto)).contains("email");
    }

    @Test
    void emptyPasswordIsRejected() {
        final UserDto dto = validDto();
        dto.setPassword("");
        assertThat(violatedProperties(dto)).contains("password");
    }

    @Test
    void gettersAndSettersRoundTrip() {
        final UserDto dto = new UserDto();
        dto.setId(7L);
        dto.setName("Anil");
        dto.setEmail("anil@example.com");
        dto.setPassword("secret");

        assertThat(dto.getId()).isEqualTo(7L);
        assertThat(dto.getName()).isEqualTo("Anil");
        assertThat(dto.getEmail()).isEqualTo("anil@example.com");
        assertThat(dto.getPassword()).isEqualTo("secret");
    }
}