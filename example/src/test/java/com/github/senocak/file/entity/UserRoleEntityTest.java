package com.github.senocak.file.entity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserRoleEntityTest {

    @Test
    void userDefaultsToEmptyRoleList() {
        assertThat(new User().getRoles()).isEmpty();
    }

    @Test
    void userGettersAndSettersRoundTrip() {
        final User user = new User();
        user.setId(1L);
        user.setName("Anil");
        user.setEmail("anil@example.com");
        user.setPassword("hashed");

        assertThat(user.getId()).isEqualTo(1L);
        assertThat(user.getName()).isEqualTo("Anil");
        assertThat(user.getEmail()).isEqualTo("anil@example.com");
        assertThat(user.getPassword()).isEqualTo("hashed");
    }

    @Test
    void userHoldsAssignedRoles() {
        final Role admin = new Role();
        admin.setName("ROLE_ADMIN");
        final User user = new User();

        user.setRoles(List.of(admin));

        assertThat(user.getRoles()).containsExactly(admin);
    }

    @Test
    void roleDefaultsToEmptyUserList() {
        assertThat(new Role().getUser()).isEmpty();
    }

    @Test
    void roleGettersAndSettersRoundTrip() {
        final Role role = new Role();
        role.setId(3L);
        role.setName("ROLE_ADMIN");

        assertThat(role.getId()).isEqualTo(3L);
        assertThat(role.getName()).isEqualTo("ROLE_ADMIN");
    }

    @Test
    void roleHoldsBackReferenceToUsers() {
        final User user = new User();
        user.setEmail("anil@example.com");
        final Role role = new Role();

        role.setUser(List.of(user));

        assertThat(role.getUser()).containsExactly(user);
    }
}