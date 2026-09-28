package com.edgareldy.springintegrationtutorial.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgareldy.springintegrationtutorial.TestcontainersConfiguration;
import com.edgareldy.springintegrationtutorial.dto.auth.RegisterRequest;
import com.edgareldy.springintegrationtutorial.security.JwtService;
import com.edgareldy.springintegrationtutorial.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checks the security filter chain: public health, documentation, integration graph and auth
 * endpoints, 401 with an error envelope for anonymous callers everywhere else, 403 for a {@code USER}
 * on the admin-only order review routes.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
// The accounts created to obtain real tokens are rolled back after each test.
@Transactional
class SecurityConfigTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private JwtService jwtService;

    @Test
    void _01_ShouldAnswer401_WhenAnAnonymousCallerRequestsAnOrderRoute() {
        assertThat(mvc.post().uri("/api/v1/orders/intake")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void _02_ShouldServeTheOpenApiDocumentWithTheBearerScheme_WhenCalledWithoutCredentials() {
        assertThat(mvc.get().uri("/v3/api-docs"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.components.securitySchemes.bearerAuth.scheme").isEqualTo("bearer");
    }

    @Test
    void _03_ShouldAnswer404Not401_WhenTheIntegrationGraphIsRequestedOutsideTheDevProfile() {
        // The route is public, but the endpoint is not exposed outside dev: the caller learns it does
        // not exist, not that credentials are missing.
        assertThat(mvc.get().uri("/actuator/integrationgraph")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void _04_ShouldAnswer401WithAnErrorEnvelope_WhenAnAnonymousCallerRequestsAnOrderRoute() {
        assertThat(mvc.get().uri("/api/v1/orders/1"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$.success").isEqualTo(false);
                    json.assertThat().extractingPath("$.message").isEqualTo("Authentication required");
                    json.assertThat().extractingPath("$.timestamp").isNotNull();
                });
    }

    @Test
    void _05_ShouldAnswer403WithAnErrorEnvelope_WhenAUserApprovesAnOrder() {
        String token = userToken();

        assertThat(mvc.post().uri("/api/v1/orders/1/approve").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$.success").isEqualTo(false);
                    json.assertThat().extractingPath("$.message").isEqualTo("Access denied");
                });
    }

    @Test
    void _06_ShouldAnswer403_WhenAUserRejectsAnOrder() {
        String token = userToken();

        assertThat(mvc.post().uri("/api/v1/orders/1/reject").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void _07_ShouldLetTheRequestThrough_WhenAnAdminCallsAReviewRoute() {
        userService.createAdminIfAbsent("admin@example.com", "admin-password");
        String token = tokenOf("admin@example.com");

        // Order 1 may or may not exist, or be pending review, in the shared database: whatever the review
        // endpoint answers (200, 404, 422), the security layer must neither ask for credentials nor refuse
        // the ADMIN role.
        MvcTestResult result = mvc.post().uri("/api/v1/orders/1/approve")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();

        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }

    @Test
    void _08_ShouldLetTheRequestThrough_WhenAUserCallsTheIntakeRoute() {
        String token = userToken();

        MvcTestResult result = mvc.post().uri("/api/v1/orders/intake")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();

        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }

    @Test
    void _09_ShouldAnswer401_WhenTheBearerTokenIsInvalid() {
        assertThat(mvc.post().uri("/api/v1/orders/intake").header(HttpHeaders.AUTHORIZATION, "Bearer forged"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void _10_ShouldReachTheLoginEndpoint_WhenCalledWithoutCredentials() {
        // A validation error, not a 401: the login route itself is public.
        assertThat(mvc.post().uri("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void _11_ShouldServeAPublicRoute_WhenTheBearerTokenIsInvalid() {
        assertThat(mvc.get().uri("/actuator/health").header(HttpHeaders.AUTHORIZATION, "Bearer forged"))
                .hasStatusOk();
    }

    private String userToken() {
        userService.register(new RegisterRequest("Jane", "Doe", "jane@example.com", "password123"));
        return tokenOf("jane@example.com");
    }

    private String tokenOf(String email) {
        return jwtService.generateToken(userDetailsService.loadUserByUsername(email));
    }
}
