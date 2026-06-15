package io.datacatalog.auth;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
