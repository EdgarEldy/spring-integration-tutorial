package com.edgareldy.springintegrationtutorial.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * Issues the JSON Web Tokens returned on login and reads them back on every authenticated request.
 * A token is signed with HMAC-SHA using the {@code app.jwt.secret} key and carries the email as its
 * subject, the roles (informative only) and its issue and expiry instants.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
@Component
public class JwtService {

    private final SecretKey key;
    private final Duration expiration;

    /**
     * @param secret     the signing secret, at least 32 bytes (256 bits) long
     * @param expiration how long an issued token stays valid
     */
    // Keys.hmacShaKeyFor refuses a secret shorter than 256 bits (WeakKeyException): a misconfigured
    // secret stops the application at startup instead of producing easily forged tokens.
    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration}") Duration expiration) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    /**
     * @param user the authenticated account
     * @return a signed, compact token whose subject is the account's email
     */
    public String generateToken(UserDetails user) {
        Instant now = Instant.now();
        // The roles are only a hint for the client: every request reloads the account, so a role
        // removed after the token was issued is not granted by an old token.
        List<String> roles = user.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key)
                .compact();
    }

    /**
     * Verifies the signature and the expiry of a token and returns its subject.
     *
     * @param token the compact token, without the {@code Bearer } prefix
     * @return the email the token was issued to
     * @throws JwtException if the token is malformed, tampered with or expired
     */
    public String extractEmail(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();
    }

    /**
     * @return how long an issued token stays valid
     */
    public Duration getExpiration() {
        return expiration;
    }
}
