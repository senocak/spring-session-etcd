package com.github.senocak.file.controller;

import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import com.github.senocak.file.dto.UserDto;
import com.github.senocak.file.entity.Role;
import com.github.senocak.file.entity.User;
import com.github.senocak.file.repository.RoleRepository;
import com.github.senocak.file.repository.UserRepository;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class AuthController {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(final UserRepository userRepository,
                          final RoleRepository roleRepository,
                          final PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/index")
    public String home(final Model model, final Principal principal) {
        if (principal != null) {
            final User loggedInUser = userRepository.findByEmail(principal.getName());
            if (loggedInUser != null) {
                model.addAttribute("loggedIn", true);
                model.addAttribute("loggedInUserName", loggedInUser.getName());
                model.addAttribute("loggedInUserEmail", loggedInUser.getEmail());
                model.addAttribute(
                        "loggedInUserRoles",
                        loggedInUser.getRoles().stream().map(Role::getName).collect(Collectors.joining(", "))
                );
            }
        }
        return "index";
    }

    @GetMapping("/register")
    public String showRegistrationForm(final Model model, final Principal principal) {
        if (principal != null) {
            return "redirect:/users";
        }
        final UserDto userDto = new UserDto();
        model.addAttribute("user", userDto); //model object is used to store data that is entered from form.
        return "register";
    }

    @PostMapping("/register/save")
    public String registration(final @Valid @ModelAttribute("user") UserDto userDto,
                               final BindingResult result,
                               final Model model) {
        final User existingUser = userRepository.findByEmail(userDto.getEmail());
        if (existingUser != null && existingUser.getEmail() != null && !existingUser.getEmail().isEmpty()) {
            result.rejectValue("email", null, "there is already an account existed with this email");
        }
        if (result.hasErrors()) {
            model.addAttribute("user", userDto);
            return "/register";
        }
        final User user = new User();
        user.setName(userDto.getName());
        user.setEmail(userDto.getEmail());
        user.setPassword(passwordEncoder.encode(userDto.getPassword()));
        Role role = roleRepository.findByName("ROLE_ADMIN");
        if (role == null) {
            role = new Role();
            role.setName("ROLE_ADMIN");
            role = roleRepository.save(role);
        }
        user.setRoles(List.of(role));
        userRepository.save(user);
        return "redirect:/register?success";
    }

    @GetMapping("/users")
    public String users(final Model model) {
        List<User> users = userRepository.findAll();
        final List<UserDto> userDtos = new ArrayList<>();
        for (final User user : users) {
            UserDto userDto = new UserDto();
            userDto.setId(user.getId());
            userDto.setName(user.getName());
            userDto.setEmail(user.getEmail());
            userDtos.add(userDto);
        }
        model.addAttribute("users", userDtos);
        return "users";
    }

    @GetMapping("/login")
    public String login(final Principal principal) {
        if (principal != null) {
            return "redirect:/users";
        }
        return "login";
    }
}
