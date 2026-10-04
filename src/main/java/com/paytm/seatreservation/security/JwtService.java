package com.paytm.seatreservation.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;

import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties jwtProperties;

    private SecretKey getSigningKey() {

        byte[] keyBytes =
                jwtProperties.getSecret()
                        .getBytes(StandardCharsets.UTF_8);

        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(
            String username,
            String role
    ) {

        Date now = new Date();

        Date expiration =
                new Date(
                        now.getTime()
                                + jwtProperties.getExpirationMs()
                );

        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getSigningKey())
                .compact();
    }

    public Claims extractClaims(String token) {

        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token) {

        return extractClaims(token)
                .getSubject();
    }

    public String extractRole(String token) {

        return extractClaims(token)
                .get("role", String.class);
    }

    public boolean isTokenValid(String token) {

        try {

            Claims claims =
                    extractClaims(token);

            return claims.getExpiration()
                    .after(new Date());

        } catch (Exception e) {

            return false;
        }
    }
}