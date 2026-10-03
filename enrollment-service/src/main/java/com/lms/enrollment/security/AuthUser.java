package com.lms.enrollment.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Authenticated principal extracted from the JWT. The raw token is kept only to forward it to other services. */
@Getter
@AllArgsConstructor
public class AuthUser {
    private final Long id;
    private final String email;
    private final Role role;
    private final String token;
}
