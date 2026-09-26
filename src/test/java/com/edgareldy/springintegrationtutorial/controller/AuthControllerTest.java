package com.edgareldy.springintegrationtutorial.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgareldy.springintegrationtutorial.TestcontainersConfiguration;
import com.edgareldy.springintegrationtutorial.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * MockMvc tests of {@link AuthController} through the real security filter chain and database:
 * registration, login, current profile and their error responses.
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
// MockMvc runs the request in the test thread, so the whole request joins the test transaction and
// the accounts it creates are rolled back: the database shared by the other context tests stays empty.
@Transactional
class AuthControllerTest {

    private static final String REGISTER = "/api/v1/auth/register";
    private static final String LOGIN = "/api/v1/auth/login";
    private static final String ME = "/api/v1/auth/me";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void _01_ShouldRegisterAnEnabledUserAccount_WhenTheRequestIsValid() {
        assertThat(register("Jane.Doe@Example.com", "password123"))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$.success").isEqualTo(true);
                    json.assertThat().extractingPath("$.data.id").isNotNull();
                    json.assertThat().extractingPath("$.data.email").isEqualTo("jane.doe@example.com");
                    json.assertThat().extractingPath("$.data.roles").asArray().containsExactly("USER");
                    json.assertThat().extractingPath("$.data.firstName").isEqualTo("Jane");
                    json.assertThat().doesNotHavePath("$.data.password");
                });
        Boolean enabled = jdbc.queryForObject(
                "SELECT enabled FROM users WHERE email = 'jane.doe@example.com'", Boolean.class);
        String hash = jdbc.queryForObject(
                "SELECT password FROM users WHERE email = 'jane.doe@example.com'", String.class);
        assertThat(enabled).isTrue();
        assertThat(hash).startsWith("{bcrypt}").doesNotContain("password123");
    }

    @Test
    void _02_ShouldAnswer422_WhenTheEmailIsAlreadyRegistered() {
        register("jane@example.com", "password123");

        assertThat(register("JANE@example.com", "another-password"))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$.success").isEqualTo(false);
                    json.assertThat().extractingPath("$.message").isEqualTo("Email is already registered");
                });
    }

    @Test
    void _03_ShouldAnswer400WithTheInvalidFields_WhenTheRegistrationIsInvalid() {
        assertThat(mvc.post().uri(REGISTER).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"firstName": "", "lastName": "Doe", "email": "not-an-email", "password": "short"}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$.data.firstName").isNotNull();
                    json.assertThat().extractingPath("$.data.email").isNotNull();
                    json.assertThat().extractingPath("$.data.password").isNotNull();
                    json.assertThat().doesNotHavePath("$.data.lastName");
                });
    }

    @Test
    void _04_ShouldReturnABearerToken_WhenTheCredentialsAreValid() {
        register("jane@example.com", "password123");

        assertThat(login("Jane@Example.com", "password123"))
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$.success").isEqualTo(true);
                    json.assertThat().extractingPath("$.data.accessToken").asString().isNotBlank();
                    json.assertThat().extractingPath("$.data.tokenType").isEqualTo("Bearer");
                    json.assertThat().extractingPath("$.data.expiresIn").isEqualTo(3600);
                });
    }

    @Test
    void _05_ShouldAnswer401_WhenThePasswordIsWrong() {
        register("jane@example.com", "password123");

        assertThat(login("jane@example.com", "wrong-password"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$.success").isEqualTo(false);
                    json.assertThat().extractingPath("$.message").isEqualTo("Invalid email or password");
                });
    }

    @Test
    void _06_ShouldAnswerTheSame401_WhenTheEmailIsUnknown() {
        assertThat(login("nobody@example.com", "password123"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson().extractingPath("$.message").isEqualTo("Invalid email or password");
    }

    @Test
    void _07_ShouldAnswer401_WhenTheAccountIsLocked() {
        register("jane@example.com", "password123");
        lock("jane@example.com");

        assertThat(login("jane@example.com", "password123"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson().extractingPath("$.message").isEqualTo("Account is disabled or locked");
    }

    @Test
    void _08_ShouldAnswer400_WhenTheLoginFieldsAreBlank() {
        assertThat(login("", ""))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.message").isEqualTo("Validation failed");
    }

    @Test
    void _09_ShouldReturnTheCurrentProfile_WhenCalledWithAValidToken() {
        register("jane@example.com", "password123");
        String token = tokenOf("jane@example.com", "password123");

        assertThat(mvc.get().uri(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$.data.email").isEqualTo("jane@example.com");
                    json.assertThat().extractingPath("$.data.lastName").isEqualTo("Doe");
                    json.assertThat().extractingPath("$.data.roles").asArray().containsExactly("USER");
                });
    }

    @Test
    void _10_ShouldAnswer401WithAnErrorEnvelope_WhenTheProfileIsRequestedWithoutToken() {
        assertThat(mvc.get().uri(ME))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$.success").isEqualTo(false);
                    json.assertThat().extractingPath("$.message").isEqualTo("Authentication required");
                });
    }

    @Test
    void _11_ShouldAnswer401_WhenTheTokenIsNotValid() {
        assertThat(mvc.get().uri(ME).header(HttpHeaders.AUTHORIZATION, "Bearer not.a.valid-token"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void _12_ShouldAnswer401_WhenTheAccountWasLockedAfterTheTokenWasIssued() {
        register("jane@example.com", "password123");
        String token = tokenOf("jane@example.com", "password123");
        lock("jane@example.com");

        assertThat(mvc.get().uri(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private void lock(String email) {
        // Through the managed entity rather than SQL: the test transaction's persistence context
        // already holds the account and would otherwise keep serving its stale, unlocked state.
        userRepository.findByEmail(email).orElseThrow().setAccountLocked(true);
    }

    private MvcTestResult register(String email, String password) {
        return mvc.post().uri(REGISTER).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"firstName": "Jane", "lastName": "Doe", "email": "%s", "password": "%s"}
                        """.formatted(email, password))
                .exchange();
    }

    private MvcTestResult login(String email, String password) {
        return mvc.post().uri(LOGIN).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password))
                .exchange();
    }

    private String tokenOf(String email, String password) {
        MvcTestResult result = login(email, password);
        assertThat(result).hasStatusOk();
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
        } catch (java.io.UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
