package com.github.senocak.file.controller;

import com.github.senocak.file.dto.UserDto;
import com.github.senocak.file.entity.Role;
import com.github.senocak.file.entity.User;
import com.github.senocak.file.repository.RoleRepository;
import com.github.senocak.file.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;

import java.security.Principal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private Model model;
    @Mock
    private BindingResult bindingResult;
    @Mock
    private Principal principal;

    @InjectMocks
    private AuthController controller;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    private static User userWithRoles(final String name, final String email, final String... roleNames) {
        final User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword("encoded");
        user.setRoles(java.util.Arrays.stream(roleNames).map(rn -> {
            final Role role = new Role();
            role.setName(rn);
            return role;
        }).toList());
        return user;
    }

    // ---- home / index ----

    @Test
    void homeWithoutPrincipalReturnsIndexAndAddsNoUserAttributes() {
        final String view = controller.home(model, null);

        assertThat(view).isEqualTo("index");
        verifyNoInteractions(userRepository);
        verify(model, never()).addAttribute(eq("loggedIn"), any());
    }

    @Test
    void homeWithPrincipalAddsLoggedInUserAttributes() {
        when(principal.getName()).thenReturn("anil@example.com");
        when(userRepository.findByEmail("anil@example.com"))
                .thenReturn(userWithRoles("Anil", "anil@example.com", "ROLE_ADMIN", "ROLE_USER"));

        final String view = controller.home(model, principal);

        assertThat(view).isEqualTo("index");
        verify(model).addAttribute("loggedIn", true);
        verify(model).addAttribute("loggedInUserName", "Anil");
        verify(model).addAttribute("loggedInUserEmail", "anil@example.com");
        verify(model).addAttribute("loggedInUserRoles", "ROLE_ADMIN, ROLE_USER");
    }

    @Test
    void homeWithPrincipalButUnknownUserAddsNoAttributes() {
        when(principal.getName()).thenReturn("ghost@example.com");
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(null);

        final String view = controller.home(model, principal);

        assertThat(view).isEqualTo("index");
        verify(model, never()).addAttribute(eq("loggedIn"), any());
    }

    // ---- registration form ----

    @Test
    void showRegistrationFormRedirectsWhenAlreadyAuthenticated() {
        assertThat(controller.showRegistrationForm(model, principal)).isEqualTo("redirect:/users");
        verify(model, never()).addAttribute(eq("user"), any());
    }

    @Test
    void showRegistrationFormReturnsFormWithEmptyDtoWhenAnonymous() {
        final String view = controller.showRegistrationForm(model, null);

        assertThat(view).isEqualTo("register");
        verify(model).addAttribute(eq("user"), any(UserDto.class));
    }

    // ---- registration save ----

    private UserDto newUserDto() {
        final UserDto dto = new UserDto();
        dto.setName("Anil");
        dto.setEmail("anil@example.com");
        dto.setPassword("secret");
        return dto;
    }

    @Test
    void registrationRejectsWhenEmailAlreadyExists() {
        final UserDto dto = newUserDto();
        when(userRepository.findByEmail("anil@example.com"))
                .thenReturn(userWithRoles("Existing", "anil@example.com"));
        when(bindingResult.hasErrors()).thenReturn(true);

        final String view = controller.registration(dto, bindingResult, model);

        assertThat(view).isEqualTo("/register");
        verify(bindingResult).rejectValue(eq("email"), isNull(), anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void registrationReturnsFormWhenValidationErrorsPresent() {
        final UserDto dto = newUserDto();
        when(userRepository.findByEmail("anil@example.com")).thenReturn(null);
        when(bindingResult.hasErrors()).thenReturn(true);

        final String view = controller.registration(dto, bindingResult, model);

        assertThat(view).isEqualTo("/register");
        verify(model).addAttribute("user", dto);
        verify(userRepository, never()).save(any());
    }

    @Test
    void registrationCreatesUserReusingExistingAdminRole() {
        final UserDto dto = newUserDto();
        when(userRepository.findByEmail("anil@example.com")).thenReturn(null);
        when(bindingResult.hasErrors()).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("hashed");
        final Role existingRole = new Role();
        existingRole.setName("ROLE_ADMIN");
        when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(existingRole);

        final String view = controller.registration(dto, bindingResult, model);

        assertThat(view).isEqualTo("redirect:/register?success");
        verify(roleRepository, never()).save(any());
        verify(userRepository).save(userCaptor.capture());
        final User saved = userCaptor.getValue();
        assertThat(saved.getName()).isEqualTo("Anil");
        assertThat(saved.getEmail()).isEqualTo("anil@example.com");
        assertThat(saved.getPassword()).isEqualTo("hashed");
        assertThat(saved.getRoles()).containsExactly(existingRole);
    }

    @Test
    void registrationCreatesAdminRoleWhenMissing() {
        final UserDto dto = newUserDto();
        when(userRepository.findByEmail("anil@example.com")).thenReturn(null);
        when(bindingResult.hasErrors()).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("hashed");
        when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(null);
        final Role createdRole = new Role();
        createdRole.setName("ROLE_ADMIN");
        when(roleRepository.save(any(Role.class))).thenReturn(createdRole);

        final String view = controller.registration(dto, bindingResult, model);

        assertThat(view).isEqualTo("redirect:/register?success");
        verify(roleRepository).save(any(Role.class));
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getRoles()).containsExactly(createdRole);
    }

    // ---- users listing ----

    @Test
    void usersMapsEntitiesToDtosAndReturnsView() {
        final User u1 = userWithRoles("Anil", "anil@example.com");
        u1.setId(1L);
        final User u2 = userWithRoles("Bob", "bob@example.com");
        u2.setId(2L);
        when(userRepository.findAll()).thenReturn(List.of(u1, u2));

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<List<UserDto>> dtoCaptor = ArgumentCaptor.forClass(List.class);

        final String view = controller.users(model);

        assertThat(view).isEqualTo("users");
        verify(model).addAttribute(eq("users"), dtoCaptor.capture());
        final List<UserDto> dtos = dtoCaptor.getValue();
        assertThat(dtos).hasSize(2);
        assertThat(dtos).extracting(UserDto::getEmail)
                .containsExactly("anil@example.com", "bob@example.com");
        assertThat(dtos).extracting(UserDto::getId).containsExactly(1L, 2L);
        // Passwords must not be copied into the outward-facing DTOs.
        assertThat(dtos).allSatisfy(dto -> assertThat(dto.getPassword()).isNull());
    }

    // ---- login ----

    @Test
    void loginRedirectsWhenAlreadyAuthenticated() {
        assertThat(controller.login(principal)).isEqualTo("redirect:/users");
    }

    @Test
    void loginReturnsLoginViewWhenAnonymous() {
        assertThat(controller.login(null)).isEqualTo("login");
    }
}