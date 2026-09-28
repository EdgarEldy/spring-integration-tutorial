package com.edgareldy.springintegrationtutorial.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgareldy.springintegrationtutorial.TestcontainersConfiguration;
import com.edgareldy.springintegrationtutorial.support.CommerceTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * HTTP contract of {@code POST /api/v1/orders/intake}, through the real gateway and flow: 202 with an
 * {@code ApiResponse}, 400 on an invalid body, 404 on an unknown customer or product, 401 for an anonymous
 * caller.
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
class OrderIntakeControllerTest {

    private static final String INTAKE_URL = "/api/v1/orders/intake";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private CommerceTestData data;

    @BeforeEach
    void setUp() {
        data = new CommerceTestData(jdbc);
    }

    // @WithMockUser runs the request as an authenticated user without any real token, so this feature is
    // tested independently of how authentication is implemented.
    @Test
    @WithMockUser
    void _01_ShouldAnswerAcceptedWithASuccessEnvelopeAndPersistTheOrder_WhenTheRequestIsValid() {
        long customerId = data.customer();
        long productId = data.product("10.00");

        assertThat(post(customerId, productId, 2))
                .hasStatus(HttpStatus.ACCEPTED)
                .bodyJson()
                .hasPathSatisfying("$.success", value -> value.assertThat().isEqualTo(true))
                .hasPathSatisfying("$.message", value -> value.assertThat().isEqualTo("Order accepted"))
                .hasPathSatisfying("$.data", value -> value.assertThat().isNull());
        assertThat(data.orderCount(customerId)).isOne();
    }

    @Test
    @WithMockUser
    void _02_ShouldAnswerBadRequestListingEveryMissingField_WhenTheBodyIsEmpty() {
        assertThat(mvc.post().uri(INTAKE_URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .hasPathSatisfying("$.success", value -> value.assertThat().isEqualTo(false))
                .hasPathSatisfying("$.data.customerId", value -> value.assertThat().isEqualTo("customerId is required"))
                .hasPathSatisfying("$.data.productId", value -> value.assertThat().isEqualTo("productId is required"))
                .hasPathSatisfying("$.data.quantity", value -> value.assertThat().isEqualTo("quantity is required"));
    }

    @Test
    @WithMockUser
    void _03_ShouldAnswerBadRequestWithoutPersisting_WhenTheQuantityIsZero() {
        long customerId = data.customer();
        long productId = data.product("10.00");

        assertThat(post(customerId, productId, 0))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .hasPathSatisfying("$.data.quantity",
                        value -> value.assertThat().isEqualTo("quantity must be greater than 0"));
        assertThat(data.orderCount(customerId)).isZero();
    }

    @Test
    @WithMockUser
    void _04_ShouldAnswerNotFoundWithAnErrorEnvelope_WhenTheCustomerDoesNotExist() {
        long productId = data.product("10.00");

        assertThat(post(-1L, productId, 1))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .hasPathSatisfying("$.success", value -> value.assertThat().isEqualTo(false))
                .hasPathSatisfying("$.message", value -> value.assertThat().isEqualTo("Customer with id -1 not found"));
    }

    @Test
    @WithMockUser
    void _05_ShouldAnswerNotFoundWithoutPersisting_WhenTheProductDoesNotExist() {
        long customerId = data.customer();

        assertThat(post(customerId, -1L, 1))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .hasPathSatisfying("$.message", value -> value.assertThat().isEqualTo("Product with id -1 not found"));
        assertThat(data.orderCount(customerId)).isZero();
    }

    @Test
    @WithMockUser
    void _06_ShouldAnswerBadRequest_WhenTheBodyIsNotValidJson() {
        assertThat(mvc.post().uri(INTAKE_URL).contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .hasPathSatisfying("$.message", value -> value.assertThat().isEqualTo("Malformed request body"));
    }

    @Test
    void _07_ShouldAnswerUnauthorizedWithoutPersisting_WhenTheCallerIsAnonymous() {
        long customerId = data.customer();
        long productId = data.product("10.00");

        assertThat(post(customerId, productId, 1)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(data.orderCount(customerId)).isZero();
    }

    private MvcTestResult post(long customerId, long productId, int quantity) {
        return mvc.post().uri(INTAKE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerId": %d, "productId": %d, "quantity": %d}
                        """.formatted(customerId, productId, quantity))
                .exchange();
    }
}
