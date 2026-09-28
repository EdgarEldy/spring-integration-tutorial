package com.edgareldy.springintegrationtutorial.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.edgareldy.springintegrationtutorial.TestcontainersConfiguration;
import com.edgareldy.springintegrationtutorial.support.CommerceTestData;
import com.edgareldy.springintegrationtutorial.support.RoutingTestDirectories;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * End-to-end bulk processing with nothing mocked: a multi-line file dropped into the watched directory is
 * split, each line goes through the shared transformer, persistence and routing flow, and one completion
 * report sums up what happened to every line.
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 */
// Shares the routing tests' context (same property overrides): its own incoming and outgoing directories,
// polled and written by no other context.
@SpringBootTest(properties = {RoutingTestDirectories.INCOMING, RoutingTestDirectories.OUTGOING})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class BulkFileEndToEndTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

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

    @Value("${orders.directories.reports}")
    private Path reports;

    private CommerceTestData data;

    @BeforeEach
    void setUp() throws IOException {
        data = new CommerceTestData(jdbc);
        RoutingTestDirectories.empty(incoming, confirmations, reviews, reports);
        Files.createDirectories(processed);
    }

    @Test
    void _01_ShouldReportEachLineAndRouteEveryValidOne_WhenAFileMixesLowValueHighValueAndInvalidRows()
            throws IOException {
        long customerId = data.customer();
        long cheap = data.product("15.00");
        long expensive = data.product("1500.00");
        String baseName = "bulk-" + UUID.randomUUID();

        drop(baseName + ".csv", """
                %d,%d,2

                %d,%d,1
                not-an-order
                -1,%d,1
                %d,%d,1
                """.formatted(customerId, cheap, customerId, expensive, cheap, customerId, cheap));

        Path report = reports.resolve(baseName + "-report.txt");
        await().atMost(TIMEOUT).until(() -> Files.exists(report));
        assertThat(Files.readString(report, StandardCharsets.UTF_8)).isEqualTo("""
                Order file report
                Source file: %s.csv
                Lines: 5
                Auto-confirmed: 2
                Pending review: 1
                Failed: 2

                Failed lines:
                line 4 [not-an-order]: Malformed order line 'not-an-order': expected customerId,productId,quantity
                line 5 [-1,%d,1]: Customer with id -1 not found
                """.formatted(baseName, cheap));

        // The report is written once every line is accounted for, i.e. after the last status update and file.
        List<Map<String, Object>> orders = jdbc.queryForList(
                "SELECT id, product_id, quantity, status, source FROM orders WHERE customer_id = ? ORDER BY id",
                customerId);
        assertThat(orders).extracting(order -> order.get("status"))
                .containsExactly("AUTO_CONFIRMED", "PENDING_REVIEW", "AUTO_CONFIRMED");
        assertThat(orders).extracting(order -> order.get("product_id")).containsExactly(cheap, expensive, cheap);
        assertThat(orders).allSatisfy(order -> assertThat(order).containsEntry("source", "FILE"));
        assertThat(confirmations.resolve("order-" + orders.get(0).get("id") + ".txt")).exists();
        assertThat(reviews.resolve("order-" + orders.get(1).get("id") + ".txt")).exists();
        assertThat(confirmations.resolve("order-" + orders.get(2).get("id") + ".txt")).exists();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders WHERE product_id = ? AND customer_id <> ?",
                Integer.class, cheap, customerId)).isZero();
    }

    @Test
    void _02_ShouldWriteAReportOfOneLine_WhenASingleOrderFileIsDropped() throws IOException {
        long customerId = data.customer();
        long expensive = data.product("1500.00");
        String baseName = "single-" + UUID.randomUUID();

        drop(baseName + ".csv", customerId + "," + expensive + ",1\n");

        Path report = reports.resolve(baseName + "-report.txt");
        await().atMost(TIMEOUT).until(() -> Files.exists(report));
        assertThat(Files.readString(report, StandardCharsets.UTF_8))
                .contains("Lines: 1", "Auto-confirmed: 0", "Pending review: 1", "Failed: 0")
                .doesNotContain("Failed lines:");
        assertThat(data.singleOrderOf(customerId)).containsEntry("status", "PENDING_REVIEW");
    }

    @Test
    void _03_ShouldWriteNoReport_WhenTheDroppedFileIsBlank() throws IOException {
        String baseName = "blank-" + UUID.randomUUID();

        drop(baseName + ".csv", "\n  \n");

        // The file is read and moved like any other, but it has no line to report on.
        await().atMost(TIMEOUT).until(() -> Files.exists(processed.resolve(baseName + ".csv")));
        await().during(Duration.ofMillis(500)).atMost(TIMEOUT)
                .until(() -> !Files.exists(reports.resolve(baseName + "-report.txt")));
    }

    // Written under a temporary name the *.csv filter ignores, then renamed: the poller can never read a
    // half-written file.
    private void drop(String fileName, String content) throws IOException {
        Path temporary = Files.writeString(incoming.resolve(fileName + ".tmp"), content);
        Files.move(temporary, incoming.resolve(fileName), StandardCopyOption.ATOMIC_MOVE);
    }
}
