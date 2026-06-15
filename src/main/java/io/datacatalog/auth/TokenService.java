package io.datacatalog.auth;

import java.time.Duration;
import java.time.Instant;

import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import io.datacatalog.user.User;

@Service
public class TokenService {

    private static final String ISSUER = "datacatalog";
    private static final Duration TTL = Duration.ofHours(1);

    private final JwtEncoder jwtEncoder;

    public TokenService(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    public IssuedToken issue(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(now.plus(TTL))
                .subject(user.getUsername())
                .claim("uid", user.getId().toString())
                .build();
        String value = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return new IssuedToken(value, TTL.toSeconds());
    }

    public record IssuedToken(String value, long expiresInSeconds) {
    }
}
