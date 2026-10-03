package com.lms.user.dto;

import com.lms.user.entity.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** All fields are optional; null means "leave unchanged". */
public record UpdateUserRequest(
        @Schema(example = "Anita K.")
        @Size(min = 1, max = 100, message = "Name must be between 1 and 100 characters")
        String name,

        @Schema(example = "anita.new@example.com")
        @Email(message = "Email must be valid")
        @Size(max = 150, message = "Email must be at most 150 characters")
        String email,

        @Schema(example = "NewSecret@123")
        @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
        String password,

        @Schema(example = "INSTRUCTOR", description = "Only ADMIN may change roles")
        Role role
) { }
