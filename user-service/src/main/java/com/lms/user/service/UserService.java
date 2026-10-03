package com.lms.user.service;

import com.lms.user.dto.*;
import com.lms.user.entity.Role;
import com.lms.user.entity.User;
import com.lms.user.exception.BadRequestException;
import com.lms.user.exception.DuplicateResourceException;
import com.lms.user.exception.ResourceNotFoundException;
import com.lms.user.exception.UnauthorizedException;
import com.lms.user.repository.UserRepository;
import com.lms.user.security.AuthUser;
import com.lms.user.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        Role role = request.role() == null ? Role.STUDENT : request.role();
        if (role == Role.ADMIN) {
            throw new BadRequestException("Self-registration as ADMIN is not allowed");
        }
        String email = normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email already registered: " + email);
        }
        User user = User.builder()
                .name(request.name().trim())
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .role(role)
                .build();
        User saved = userRepository.save(user);
        log.info("User registered: id={}, email={}, role={}", saved.getId(), saved.getEmail(), saved.getRole());
        return UserResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String email = normalize(request.email());
        User user = userRepository.findByEmail(email).orElseThrow(() -> {
            log.warn("Login failed: unknown email {}", email);
            return new UnauthorizedException("Invalid email or password");
        });
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            log.warn("Login failed: wrong password for {}", email);
            throw new UnauthorizedException("Invalid email or password");
        }
        String token = jwtService.generateToken(user);
        log.info("Login successful: id={}, email={}", user.getId(), user.getEmail());
        return new LoginResponse(token, "Bearer", jwtService.getExpirationMs(), UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return UserResponse.from(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAll() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request, AuthUser current) {
        boolean admin = current.getRole() == Role.ADMIN;
        if (!admin && !current.getId().equals(id)) {
            throw new AccessDeniedException("You can only update your own profile");
        }
        User user = findOrThrow(id);

        if (request.name() != null && !request.name().isBlank()) {
            user.setName(request.name().trim());
        }
        if (request.email() != null && !request.email().isBlank()) {
            String email = normalize(request.email());
            if (!email.equals(user.getEmail())) {
                if (userRepository.existsByEmail(email)) {
                    throw new DuplicateResourceException("Email already registered: " + email);
                }
                user.setEmail(email);
            }
        }
        if (request.password() != null && !request.password().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.password()));
        }
        if (request.role() != null && request.role() != user.getRole()) {
            if (!admin) {
                throw new AccessDeniedException("Only admins can change roles");
            }
            user.setRole(request.role());
        }
        User saved = userRepository.save(user);
        log.info("User updated: id={} by userId={}", saved.getId(), current.getId());
        return UserResponse.from(saved);
    }

    @Transactional
    public void delete(Long id, AuthUser current) {
        User user = findOrThrow(id);
        userRepository.delete(user);
        log.info("User deleted: id={} by userId={}", id, current.getId());
    }

    private User findOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + id));
    }

    private String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
