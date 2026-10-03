package com.lms.course.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Authenticated principal extracted from the JWT issued by user-service. */
@Getter
@AllArgsConstructor
public class AuthUser {
    private final Long id;
    private final String email;
    private final Role role;
}
