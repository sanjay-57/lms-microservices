package com.lms.user.dto;

public record LoginResponse(String token, String tokenType, long expiresInMs, UserResponse user) { }
