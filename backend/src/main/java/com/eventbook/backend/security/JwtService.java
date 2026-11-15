package com.eventbook.backend.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(@Value("${security.jwt.secret}") String secret,
                      @Value("${security.jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.expirationMs = expirationMs;
    }

    /** Gera token a partir do AuthenticatedUser, incluindo a versão atual do token. */
    public String generateToken(AuthenticatedUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.id().toString())            // UUID como sub
                .claim("email", user.email())
                .claim("role", user.authorities().stream()
                        .findFirst().orElseThrow().getAuthority())
                .claim("name", user.name())
                .claim("tokenVersion", user.getTokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(key)
                .compact();
    }

    /** Valida assinatura/expiração e devolve as claims (lança exceção se inválido). */
    public Jws<Claims> validateToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
    }

    /** Extrai o UUID do subject */
    public UUID getUserIdFromToken(String token) {
        return UUID.fromString(validateToken(token).getPayload().getSubject());
    }
}
