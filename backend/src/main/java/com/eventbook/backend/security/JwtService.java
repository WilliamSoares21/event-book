package com.eventbook.backend.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {

    private final Key key;
    private final long expirationMs;

    public JwtService(@Value("${security.jwt.secret}") String secret,
                      @Value("${security.jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes()); // HMAC‑SHA256
        this.expirationMs = expirationMs;
    }

    /** Gera token a partir do AuthenticatedUser */
    public String generateToken(AuthenticatedUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(user.id().toString())            // UUID como sub
                .claim("email", user.email())
                .claim("role", user.authorities().stream()
                        .findFirst().orElseThrow().getAuthority())
                .claim("name", user.name())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /** Valida token e devolve as claims (lança exceção se inválido) */
    public Jws<Claims> validateToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token);
    }

    /** Extrai o UUID do subject */
    public UUID getUserIdFromToken(String token) {
        Claims claims = validateToken(token).getBody();
        return UUID.fromString(claims.getSubject());
    }
}
