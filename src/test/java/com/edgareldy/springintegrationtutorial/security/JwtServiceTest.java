package com.edgareldy.springintegrationtutorial.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.WeakKeyException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Unit tests of {@link JwtService}: token round trip, rejection of tampered and expired tokens, and
 * refusal of a weak secret.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-of-at-least-32-bytes-long";
    private static final String OTHER_SECRET = "another-secret-of-at-least-32-bytes-long!!";

    private final UserDetails jane = User.withUsername("jane@example.com").password("x").roles("USER").build();

    @Test
    void _01_ShouldReturnTheEmail_WhenTheTokenWasIssuedByTheSameService() {
        JwtService service = new JwtService(SECRET, Duration.ofHours(1));

        String token = service.generateToken(jane);

        assertThat(service.extractEmail(token)).isEqualTo("jane@example.com");
    }

    @Test
    void _02_ShouldRejectTheToken_WhenItWasSignedWithAnotherSecret() {
        String forged = new JwtService(OTHER_SECRET, Duration.ofHours(1)).generateToken(jane);
        JwtService service = new JwtService(SECRET, Duration.ofHours(1));

        assertThatThrownBy(() -> service.extractEmail(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    void _03_ShouldRejectTheToken_WhenItHasExpired() {
        JwtService service = new JwtService(SECRET, Duration.ofMinutes(-1));

        String token = service.generateToken(jane);

        assertThatThrownBy(() -> service.extractEmail(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void _04_ShouldRejectTheToken_WhenItIsMalformed() {
        JwtService service = new JwtService(SECRET, Duration.ofHours(1));

        assertThatThrownBy(() -> service.extractEmail("not-a-jwt")).isInstanceOf(JwtException.class);
    }

    @Test
    void _05_ShouldRefuseToStart_WhenTheSecretIsShorterThan256Bits() {
        assertThatThrownBy(() -> new JwtService("too-short", Duration.ofHours(1)))
                .isInstanceOf(WeakKeyException.class);
    }

    @Test
    void _06_ShouldExposeTheConfiguredExpiration_WhenAsked() {
        assertThat(new JwtService(SECRET, Duration.ofMinutes(30)).getExpiration()).isEqualTo(Duration.ofMinutes(30));
    }
}
