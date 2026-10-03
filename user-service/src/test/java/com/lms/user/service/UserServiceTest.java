package com.lms.user.service;

import com.lms.user.dto.*;
import com.lms.user.entity.Role;
import com.lms.user.entity.User;
import com.lms.user.exception.BadRequestException;
import com.lms.user.exception.DuplicateResourceException;
import com.lms.user.exception.UnauthorizedException;
import com.lms.user.repository.UserRepository;
import com.lms.user.security.AuthUser;
import com.lms.user.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @InjectMocks private UserService userService;

    @Test
    void register_savesHashedPasswordAndDefaultsToStudent() {
        when(userRepository.existsByEmail("anita@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret@123")).thenReturn("HASHED");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(7L);
            return u;
        });

        UserResponse response = userService.register(
                new RegisterRequest("Anita", "Anita@Example.com", "Secret@123", null));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("HASHED");
        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.email()).isEqualTo("anita@example.com");
        assertThat(response.role()).isEqualTo(Role.STUDENT);
    }

    @Test
    void register_duplicateEmail_throwsConflict() {
        when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);
        assertThrows(DuplicateResourceException.class, () ->
                userService.register(new RegisterRequest("Dup", "dup@example.com", "Secret@123", Role.STUDENT)));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_asAdmin_isRejected() {
        assertThrows(BadRequestException.class, () ->
                userService.register(new RegisterRequest("Evil", "evil@example.com", "Secret@123", Role.ADMIN)));
        verifyNoInteractions(userRepository);
    }

    @Test
    void login_success_returnsToken() {
        User user = User.builder().id(4L).name("Sam").email("student1@example.com")
                .password("HASHED").role(Role.STUDENT).build();
        when(userRepository.findByEmail("student1@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Student@123", "HASHED")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("jwt-token");
        when(jwtService.getExpirationMs()).thenReturn(3600000L);

        LoginResponse response = userService.login(new LoginRequest("student1@example.com", "Student@123"));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().email()).isEqualTo("student1@example.com");
    }

    @Test
    void login_wrongPassword_throwsUnauthorized() {
        User user = User.builder().id(4L).email("student1@example.com").password("HASHED").role(Role.STUDENT).build();
        when(userRepository.findByEmail("student1@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad", "HASHED")).thenReturn(false);

        assertThrows(UnauthorizedException.class,
                () -> userService.login(new LoginRequest("student1@example.com", "bad")));
        verifyNoInteractions(jwtService);
    }

    @Test
    void login_unknownEmail_throwsUnauthorized() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
        assertThrows(UnauthorizedException.class,
                () -> userService.login(new LoginRequest("ghost@example.com", "x")));
    }

    @Test
    void update_otherUsersProfile_asStudent_isForbidden() {
        AuthUser student = new AuthUser(4L, "student1@example.com", Role.STUDENT);
        assertThrows(AccessDeniedException.class,
                () -> userService.update(5L, new UpdateUserRequest("New", null, null, null), student));
    }

    @Test
    void update_roleChange_asStudent_isForbidden() {
        AuthUser student = new AuthUser(4L, "student1@example.com", Role.STUDENT);
        User existing = User.builder().id(4L).name("Sam").email("student1@example.com")
                .password("HASHED").role(Role.STUDENT).build();
        when(userRepository.findById(4L)).thenReturn(Optional.of(existing));

        assertThrows(AccessDeniedException.class,
                () -> userService.update(4L, new UpdateUserRequest(null, null, null, Role.INSTRUCTOR), student));
        verify(userRepository, never()).save(any());
    }
}
