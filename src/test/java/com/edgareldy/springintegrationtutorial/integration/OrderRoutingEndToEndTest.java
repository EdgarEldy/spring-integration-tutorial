package com.edgareldy.springintegrationtutorial.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.edgareldy.springintegrationtutorial.TestcontainersConfiguration;
import com.edgareldy.springintegrationtutorial.dto.order.OrderIntakeRequest;
import com.edgareldy.springintegrationtutorial.integration.gateway.OrderIntakeGateway;
import com.edgareldy.springintegrationtutorial.support.CommerceTestData;
import com.edgareldy.springintegrationtutorial.support.RoutingTestDirectories;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Content-based routing end to end, with nothing mocked: an order under the review threshold ends up
 * auto-confirmed with a confirmation file, an order at or above it pending review with a review-queue
 * entry, whichever source it came from. Only the resulting files and statuses are checked, never the
 * router itself.
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 */
@SpringBootTest(properties = {RoutingTestDirectories.INCOMING, RoutingTestDirectories.OUTGOING})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class OrderRoutingEndToEndTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private OrderIntakeGateway orderIntakeGateway;

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${orders.directories.incoming}")
    private Path incoming;

    @Value("${orders.directories.processed}")
    private Path processed;

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
        RoutingTestDirectories.empty(incoming, confirmations, reviews, rejections);
        // The file adapter moves each read file there and only creates it at startup.
        Files.createDirectories(processed);
    }

    @Test
    void _01_ShouldAutoConfirmAndWriteAConfirmationFile_WhenTheTotalIsUnderTheThreshold() throws IOException {
        long customerId = data.customer();
        long productId = data.product("229.00");

        orderIntakeGateway.submit(new OrderIntakeRequest(customerId, productId, 2));

        long orderId = orderIdOf(customerId);
        assertThat(data.statusOf(orderId)).isEqualTo("AUTO_CONFIRMED");
        assertThat(Files.readString(confirmations.resolve(fileOf(orderId))))
                .contains("orderId=" + orderId, "status=AUTO_CONFIRMED", "source=API", "quantity=2", "total=458.00");
        assertThat(reviews.resolve(fileOf(orderId))).doesNotExist();
    }

    @Test
    void _02_ShouldQueueForReviewAndWriteAReviewEntry_WhenTheTotalIsAboveTheThreshold() throws IOException {
        long customerId = data.customer();
        long productId = data.product("1299.00");

        orderIntakeGateway.submit(new OrderIntakeRequest(customerId, productId, 1));

        long orderId = orderIdOf(customerId);
        assertThat(data.statusOf(orderId)).isEqualTo("PENDING_REVIEW");
        assertThat(Files.readString(reviews.resolve(fileOf(orderId))))
                .contains("orderId=" + orderId, "status=PENDING_REVIEW", "total=1299.00",
                        "approve=POST /api/v1/orders/" + orderId + "/approve",
                        "reject=POST /api/v1/orders/" + orderId + "/reject");
        assertThat(confirmations.resolve(fileOf(orderId))).doesNotExist();
    }

    @Test
    void _03_ShouldQueueForReview_WhenTheTotalIsExactlyTheThreshold() {
        long customerId = data.customer();
        long productId = data.product("500.00");

        orderIntakeGateway.submit(new OrderIntakeRequest(customerId, productId, 2));

        long orderId = orderIdOf(customerId);
        assertThat(data.statusOf(orderId)).isEqualTo("PENDING_REVIEW");
        assertThat(reviews.resolve(fileOf(orderId))).exists();
    }

    @Test
    void _04_ShouldAutoConfirm_WhenTheTotalIsJustUnderTheThreshold() {
        long customerId = data.customer();
        long productId = data.product("999.99");

        orderIntakeGateway.submit(new OrderIntakeRequest(customerId, productId, 1));

        long orderId = orderIdOf(customerId);
        assertThat(data.statusOf(orderId)).isEqualTo("AUTO_CONFIRMED");
        assertThat(confirmations.resolve(fileOf(orderId))).exists();
    }

    @Test
    void _05_ShouldQueueForReview_WhenAHighValueOrderArrivesAsAFile() throws IOException {
        long customerId = data.customer();
        long productId = data.product("1299.00");

        drop("high-value.csv", customerId + "," + productId + ",1");

        // The file source runs the same flow in the poller thread: wait for its outcome.
        await().atMost(TIMEOUT).until(() -> data.orderCount(customerId) == 1
                && "PENDING_REVIEW".equals(data.statusOf(orderIdOf(customerId))));
        long orderId = orderIdOf(customerId);
        await().atMost(TIMEOUT).until(() -> Files.exists(reviews.resolve(fileOf(orderId))));
        assertThat(Files.readString(reviews.resolve(fileOf(orderId)))).contains("source=FILE");
    }

    @Test
    void _06_ShouldAutoConfirm_WhenALowValueOrderArrivesAsAFile() throws IOException {
        long customerId = data.customer();
        long productId = data.product("229.00");

        drop("low-value.csv", customerId + "," + productId + ",2");

        await().atMost(TIMEOUT).until(() -> data.orderCount(customerId) == 1
                && "AUTO_CONFIRMED".equals(data.statusOf(orderIdOf(customerId))));
        long orderId = orderIdOf(customerId);
        await().atMost(TIMEOUT).until(() -> Files.exists(confirmations.resolve(fileOf(orderId))));
        assertThat(Files.readString(confirmations.resolve(fileOf(orderId)))).contains("source=FILE");
    }

    private long orderIdOf(long customerId) {
        return ((Number) data.singleOrderOf(customerId).get("id")).longValue();
    }

    private static String fileOf(long orderId) {
        return "order-" + orderId + ".txt";
    }

    // Written under a temporary name the *.csv filter ignores, then renamed: the poller never reads a
    // half-written file.
    private void drop(String fileName, String content) throws IOException {
        Path temporary = Files.writeString(incoming.resolve(fileName + ".tmp"), content);
        Files.move(temporary, incoming.resolve(fileName), StandardCopyOption.ATOMIC_MOVE);
    }
}
