package com.learnjava.todo.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

// JwtServiceImpl contains all the JWT logic.
// Keeping it separate from the interface means:
//   - Tests mock the interface, never needing to touch this class
//   - The implementation can be swapped (e.g., switch to asymmetric RSA keys later)
//   - No ByteBuddy issues on Java 25 — Mockito mocks the JwtService interface via JDK proxy
@Service
public class JwtServiceImpl implements JwtService {

    // @Value reads from application.properties — keeps secrets out of code.
    // In production, this value comes from an environment variable or vault.
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expirationMs;

    @Override
    public String generateToken(UserDetails userDetails) {
        return Jwts.builder()
                .subject(userDetails.getUsername())   // "sub" claim — identifies the user
                .issuedAt(new Date())                  // "iat" claim — when the token was issued
                .expiration(new Date(System.currentTimeMillis() + expirationMs)) // "exp" claim
                .signWith(getSigningKey())             // signs header+payload with our secret
                .compact();                            // serializes to the xxx.yyy.zzz string
    }

    @Override
    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    @Override
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    // -----------------------------------------------------------------------
    // PRIVATE HELPERS
    // -----------------------------------------------------------------------

    private boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }

    // Parses the token and returns all claims.
    // JJWT throws an exception if the signature doesn't match, the token is expired,
    // or the token is malformed — these propagate up to JwtAuthenticationFilter.
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // Converts our plain-text secret into a cryptographic SecretKey.
    // Keys.hmacShaKeyFor() validates the key length (must be >= 256 bits for HS256).
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
