package com.lms.user.security;

import com.lms.user.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Authenticated principal extracted from the JWT. */
@Getter
@AllArgsConstructor
public class AuthUser {
    private final Long id;
    private final String email;
    private final Role role;
}
