package com.edgareldy.springintegrationtutorial.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.edgareldy.springintegrationtutorial.TestcontainersConfiguration;
import com.edgareldy.springintegrationtutorial.support.CommerceTestData;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;
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
import org.springframework.util.FileSystemUtils;

/**
 * End-to-end intake through each source, with nothing mocked: an HTTP call and a CSV file dropped into
 * the watched directory both end up as an order row in PostgreSQL.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
// Every cached Spring context keeps its file poller running until the JVM exits. This class therefore
// watches an incoming directory of its own (a property, so its own context too): no other context can
// grab the files dropped here and persist them in another database.
@SpringBootTest(properties = "orders.directories.incoming=target/test-orders/end-to-end/incoming-orders")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class OrderIntakeEndToEndTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${orders.directories.incoming}")
    private Path incoming;

    @Value("${orders.directories.processed}")
    private Path processed;

    private CommerceTestData data;

    @BeforeEach
    void setUp() throws IOException {
        data = new CommerceTestData(jdbc);
        // Empties the watched directory (processed/ included) but keeps it: the poller fails on a
        // directory that does not exist.
        try (Stream<Path> children = Files.list(incoming)) {
            for (Path child : children.toList()) {
                FileSystemUtils.deleteRecursively(child);
            }
        }
        Files.createDirectories(processed);
    }

    @Test
    @WithMockUser
    void _01_ShouldPersistAnApiOrder_WhenItIsPostedToTheIntakeEndpoint() {
        long customerId = data.customer();
        long productId = data.product("15.00");

        assertThat(mvc.post().uri("/api/v1/orders/intake")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerId": %d, "productId": %d, "quantity": 3}
                        """.formatted(customerId, productId)))
                .hasStatus(HttpStatus.ACCEPTED);

        assertAutoConfirmedOrder(customerId, productId, 3, "45.00", "API");
    }

    @Test
    void _02_ShouldPersistAFileOrderAndMoveTheFileToProcessed_WhenACsvFileIsDropped() throws IOException {
        long customerId = data.customer();
        long productId = data.product("15.00");

        drop("order-1.csv", customerId + "," + productId + ",2\n");

        await().atMost(TIMEOUT).until(() -> data.orderCount(customerId) == 1);
        assertAutoConfirmedOrder(customerId, productId, 2, "30.00", "FILE");
        await().atMost(TIMEOUT).until(() -> Files.exists(processed.resolve("order-1.csv")));
        assertThat(incoming.resolve("order-1.csv")).doesNotExist();
    }

    @Test
    void _03_ShouldPersistASecondOrder_WhenAFileWithAnAlreadyProcessedNameIsDroppedAgain() throws IOException {
        long customerId = data.customer();
        long productId = data.product("15.00");

        drop("repeat.csv", customerId + "," + productId + ",1");
        await().atMost(TIMEOUT).until(() -> data.orderCount(customerId) == 1);
        drop("repeat.csv", customerId + "," + productId + ",1");

        await().atMost(TIMEOUT).until(() -> data.orderCount(customerId) == 2);
    }

    @Test
    void _04_ShouldLeaveTheFileUntouched_WhenItIsNotACsvFile() throws IOException {
        long customerId = data.customer();
        long productId = data.product("15.00");

        drop("order.txt", customerId + "," + productId + ",1");

        // Several polls happen during this window (100 ms apart in the test profile): the file must still
        // be there at the end of it and no order may have been created.
        await().during(Duration.ofMillis(800)).atMost(TIMEOUT)
                .until(() -> Files.exists(incoming.resolve("order.txt")) && data.orderCount(customerId) == 0);
    }

    @Test
    void _05_ShouldMoveTheFileWithoutPersisting_WhenTheCustomerIsUnknown() throws IOException {
        long productId = data.product("15.00");

        drop("unknown-customer.csv", "-1," + productId + ",1");

        // No error handling exists yet in the file path: the failure goes to the default errorChannel,
        // which logs it, and the file is not picked up again since it has already left the directory.
        await().atMost(TIMEOUT).until(() -> Files.exists(processed.resolve("unknown-customer.csv")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders WHERE product_id = ?", Integer.class, productId))
                .isZero();
    }

    // The totals used here are far under the review threshold: once persisted, the order continues through
    // the router to the auto-confirm path, whichever source it came from. For a file, that happens in the
    // poller thread, a moment after the row appears: the assertion is retried until it holds.
    private void assertAutoConfirmedOrder(long customerId, long productId, int quantity, String total, String source) {
        await().atMost(TIMEOUT).untilAsserted(() -> {
            Map<String, Object> order = data.singleOrderOf(customerId);
            assertThat(order).containsEntry("product_id", productId).containsEntry("quantity", quantity)
                    .containsEntry("source", source).containsEntry("status", "AUTO_CONFIRMED");
            assertThat((BigDecimal) order.get("total")).isEqualByComparingTo(total);
        });
    }

    // Written under a temporary name the *.csv filter ignores, then renamed: the poller can never read a
    // half-written file.
    private void drop(String fileName, String content) throws IOException {
        Path temporary = Files.writeString(incoming.resolve(fileName + ".tmp"), content);
        Files.move(temporary, incoming.resolve(fileName), StandardCopyOption.ATOMIC_MOVE);
    }
}
