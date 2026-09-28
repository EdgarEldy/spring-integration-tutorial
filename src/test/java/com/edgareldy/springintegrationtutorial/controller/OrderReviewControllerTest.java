package com.edgareldy.springintegrationtutorial.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgareldy.springintegrationtutorial.TestcontainersConfiguration;
import com.edgareldy.springintegrationtutorial.dto.order.OrderIntakeRequest;
import com.edgareldy.springintegrationtutorial.integration.gateway.OrderIntakeGateway;
import com.edgareldy.springintegrationtutorial.support.CommerceTestData;
import com.edgareldy.springintegrationtutorial.support.RoutingTestDirectories;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
 * HTTP contract of {@code POST /api/v1/orders/{id}/approve} and {@code /reject}, through the real review
 * gateway and flow: 200 with an {@code ApiResponse} and the matching outbound file, 422 for an order that is
 * not (or no longer) pending review, 404 for an unknown order, 400 without a reason, 401 and 403.
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 */
// Same property overrides as OrderRoutingEndToEndTest, hence the same cached context and directories.
@SpringBootTest(properties = {RoutingTestDirectories.INCOMING, RoutingTestDirectories.OUTGOING})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class OrderReviewControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private OrderIntakeGateway orderIntakeGateway;

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${orders.directories.confirmations}")
    private Path confirmations;

    @Value("${orders.directories.reviews}")
    private Path reviews;

    @Value("${orders.directories.rejections}")
    private Path rejections;

    private CommerceTestData data;

    @BeforeEach
    void setUp() throws IOException {
        data = new CommerceTestData(jdbc);
        RoutingTestDirectories.empty(confirmations, reviews, rejections);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void _01_ShouldApproveAndWriteAConfirmationFile_WhenTheOrderIsPendingReview() throws IOException {
        long orderId = pendingOrder();

        assertThat(approve(orderId))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.success", value -> value.assertThat().isEqualTo(true))
                .hasPathSatisfying("$.message", value -> value.assertThat().isEqualTo("Order approved"))
                .hasPathSatisfying("$.data", value -> value.assertThat().isNull());
        assertThat(data.statusOf(orderId)).isEqualTo("APPROVED");
        assertThat(Files.readString(confirmations.resolve(fileOf(orderId))))
                .contains("orderId=" + orderId, "status=APPROVED");
        assertThat(rejections.resolve(fileOf(orderId))).doesNotExist();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void _02_ShouldRejectAndWriteARejectionFileWithTheReason_WhenTheOrderIsPendingReview() throws IOException {
        long orderId = pendingOrder();

        assertThat(reject(orderId, "{\"reason\": \"Customer credit check failed\"}"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.success", value -> value.assertThat().isEqualTo(true))
                .hasPathSatisfying("$.message", value -> value.assertThat().isEqualTo("Order rejected"));
        assertThat(data.statusOf(orderId)).isEqualTo("REJECTED");
        assertThat(Files.readString(rejections.resolve(fileOf(orderId))))
                .contains("orderId=" + orderId, "status=REJECTED", "reason=Customer credit check failed");
        assertThat(confirmations.resolve(fileOf(orderId))).doesNotExist();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void _03_ShouldAnswerUnprocessable_WhenTheOrderIsApprovedTwice() {
        long orderId = pendingOrder();
        assertThat(approve(orderId)).hasStatusOk();

        assertThat(approve(orderId))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson()
                .hasPathSatisfying("$.success", value -> value.assertThat().isEqualTo(false))
                .hasPathSatisfying("$.message", value -> value.assertThat()
                        .isEqualTo("Order with id " + orderId + " is not pending review (status APPROVED)"));
        assertThat(data.statusOf(orderId)).isEqualTo("APPROVED");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void _04_ShouldAnswerUnprocessableAndWriteNoRejection_WhenAnApprovedOrderIsRejected() {
        long orderId = pendingOrder();
        assertThat(approve(orderId)).hasStatusOk();

        assertThat(reject(orderId, "{\"reason\": \"Too late\"}")).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(data.statusOf(orderId)).isEqualTo("APPROVED");
        assertThat(rejections.resolve(fileOf(orderId))).doesNotExist();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void _05_ShouldAnswerUnprocessable_WhenTheOrderWasAutoConfirmed() {
        long customerId = data.customer();
        long productId = data.product("10.00");
        orderIntakeGateway.submit(new OrderIntakeRequest(customerId, productId, 1));
        long orderId = orderIdOf(customerId);

        assertThat(approve(orderId)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(data.statusOf(orderId)).isEqualTo("AUTO_CONFIRMED");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void _06_ShouldAnswerNotFound_WhenTheOrderToApproveDoesNotExist() {
        assertThat(approve(-1L))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .hasPathSatisfying("$.message", value -> value.assertThat().isEqualTo("Order with id -1 not found"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void _07_ShouldAnswerNotFound_WhenTheOrderToRejectDoesNotExist() {
        assertThat(reject(-1L, "{\"reason\": \"Unknown\"}")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void _08_ShouldAnswerBadRequestAndKeepTheOrderPending_WhenTheReasonIsMissing() {
        long orderId = pendingOrder();

        assertThat(reject(orderId, "{}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .hasPathSatisfying("$.data.reason", value -> value.assertThat().isEqualTo("reason is required"));
        assertThat(data.statusOf(orderId)).isEqualTo("PENDING_REVIEW");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void _09_ShouldAnswerBadRequest_WhenTheReasonIsBlank() {
        long orderId = pendingOrder();

        assertThat(reject(orderId, "{\"reason\": \"   \"}")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(data.statusOf(orderId)).isEqualTo("PENDING_REVIEW");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void _10_ShouldWriteTheReasonOnOneLine_WhenItContainsLineBreaks() throws IOException {
        long orderId = pendingOrder();

        assertThat(reject(orderId, "{\"reason\": \"Out of stock\\nforever\"}")).hasStatusOk();
        assertThat(Files.readAllLines(rejections.resolve(fileOf(orderId)))).contains("reason=Out of stock forever");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void _11_ShouldAnswerBadRequest_WhenTheOrderIdIsNotANumber() {
        assertThat(mvc.post().uri("/api/v1/orders/abc/approve")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void _12_ShouldAnswerUnauthorizedAndKeepTheOrderPending_WhenTheCallerIsAnonymous() {
        long orderId = pendingOrder();

        assertThat(approve(orderId)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(data.statusOf(orderId)).isEqualTo("PENDING_REVIEW");
    }

    @Test
    @WithMockUser(roles = "USER")
    void _13_ShouldAnswerForbiddenAndKeepTheOrderPending_WhenTheCallerIsAUser() {
        long orderId = pendingOrder();

        assertThat(approve(orderId)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(reject(orderId, "{\"reason\": \"Not mine to decide\"}")).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(data.statusOf(orderId)).isEqualTo("PENDING_REVIEW");
    }

    // Goes through the real intake flow: a total above the review threshold leaves the order PENDING_REVIEW.
    private long pendingOrder() {
        long customerId = data.customer();
        long productId = data.product("1299.00");
        orderIntakeGateway.submit(new OrderIntakeRequest(customerId, productId, 1));
        long orderId = orderIdOf(customerId);
        assertThat(data.statusOf(orderId)).isEqualTo("PENDING_REVIEW");
        return orderId;
    }

    private long orderIdOf(long customerId) {
        return ((Number) data.singleOrderOf(customerId).get("id")).longValue();
    }

    private MvcTestResult approve(long orderId) {
        return mvc.post().uri("/api/v1/orders/{id}/approve", orderId).exchange();
    }

    private MvcTestResult reject(long orderId, String body) {
        return mvc.post().uri("/api/v1/orders/{id}/reject", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private static String fileOf(long orderId) {
        return "order-" + orderId + ".txt";
    }
}
