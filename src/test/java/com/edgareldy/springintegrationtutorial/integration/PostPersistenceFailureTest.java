package com.edgareldy.springintegrationtutorial.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.edgareldy.springintegrationtutorial.TestcontainersConfiguration;
import com.edgareldy.springintegrationtutorial.integration.error.OrderErrorHandler;
import com.edgareldy.springintegrationtutorial.support.CommerceTestData;
import com.edgareldy.springintegrationtutorial.support.RoutingTestDirectories;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
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
 * Failures after persistence, with nothing mocked: the confirmation file cannot be written (a regular file
 * stands where its directory should be), on the file path and on the HTTP paths. The order exists, so it
 * ends up {@code FAILED}, with exactly one dead-letter file; an HTTP caller still gets a 500.
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 */
// Directories of its own (hence its own context): blocking the confirmations directory here never disturbs
// the other file-based tests, and no other context's poller takes the files dropped here.
@SpringBootTest(properties = {
        "orders.directories.incoming=target/test-orders/post-persistence/incoming-orders",
        "orders.directories.outgoing=target/test-orders/post-persistence/outgoing-orders",
        "orders.directories.failed=target/test-orders/post-persistence/failed-orders"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PostPersistenceFailureTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${orders.directories.incoming}")
    private Path incoming;

    @Value("${orders.directories.processed}")
    private Path processed;

    @Value("${orders.directories.confirmations}")
    private Path confirmations;

    @Value("${orders.directories.failed}")
    private Path failed;

    private CommerceTestData data;

    @BeforeEach
    void setUp() throws IOException {
        data = new CommerceTestData(jdbc);
        unblockConfirmations();
        Files.createDirectories(failed);
        RoutingTestDirectories.empty(incoming, failed);
        Files.createDirectories(processed);
    }

    @AfterEach
    void tearDown() throws IOException {
        unblockConfirmations();
    }

    @Test
    void _01_ShouldSetFailedAndWriteASingleDeadLetter_WhenTheConfirmationOfADroppedFileCannotBeWritten()
            throws IOException {
        long customerId = data.customer();
        long productId = data.product("15.00");
        blockConfirmations();

        drop("blocked.csv", customerId + "," + productId + ",2");

        await().atMost(TIMEOUT).until(() -> data.orderCount(customerId) == 1
                && "FAILED".equals(data.singleOrderOf(customerId).get("status")));
        long orderId = orderIdOf(customerId);
        // The advice of the file writer records the failure, then the exception reaches errorChannel through
        // the poller: during several polls, there must still be one dead letter only.
        await().during(Duration.ofMillis(500)).atMost(TIMEOUT).until(() -> deadLetters().size() == 1);
        assertThat(read(deadLetters().get(0)))
                .startsWith("source-file: blocked.csv\norder-id: " + orderId + "\n")
                .contains("\nreason: IllegalArgumentException: Destination path [")
                .contains("does not point to a directory.\n")
                .contains("\npayload:\norderId=" + orderId + "\nstatus=AUTO_CONFIRMED\n");
    }

    @Test
    @WithMockUser
    void _02_ShouldSetFailedWriteADeadLetterAndAnswer500_WhenTheConfirmationOfAnApiOrderCannotBeWritten() {
        long customerId = data.customer();
        long productId = data.product("15.00");
        blockConfirmations();

        assertThat(mvc.post().uri("/api/v1/orders/intake")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerId": %d, "productId": %d, "quantity": 1}
                        """.formatted(customerId, productId)))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyJson().extractingPath("$.success").isEqualTo(false);

        long orderId = orderIdOf(customerId);
        assertThat(data.statusOf(orderId)).isEqualTo("FAILED");
        assertThat(deadLetters()).singleElement()
                .satisfies(file -> assertThat(file.getFileName().toString()).startsWith("order-" + orderId + "."))
                .satisfies(file -> assertThat(read(file)).startsWith("source-file: none\norder-id: " + orderId + "\n"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void _03_ShouldSetFailedWriteADeadLetterAndAnswer500_WhenTheConfirmationOfAnApprovedOrderCannotBeWritten() {
        long customerId = data.customer();
        long productId = data.product("1200.00");
        assertThat(mvc.post().uri("/api/v1/orders/intake")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerId": %d, "productId": %d, "quantity": 1}
                        """.formatted(customerId, productId)))
                .hasStatus(HttpStatus.ACCEPTED);
        long orderId = orderIdOf(customerId);
        assertThat(data.statusOf(orderId)).isEqualTo("PENDING_REVIEW");
        blockConfirmations();

        assertThat(mvc.post().uri("/api/v1/orders/{id}/approve", orderId))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyJson().extractingPath("$.success").isEqualTo(false);

        assertThat(data.statusOf(orderId)).isEqualTo("FAILED");
        assertThat(deadLetters()).singleElement()
                .satisfies(file -> assertThat(read(file)).contains("\npayload:\norderId=" + orderId + "\nstatus=APPROVED\n"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void _04_ShouldAnswer404WithoutAnyDeadLetter_WhenTheApprovedOrderDoesNotExist() {
        assertThat(mvc.post().uri("/api/v1/orders/{id}/approve", Long.MAX_VALUE))
                .hasStatus(HttpStatus.NOT_FOUND);

        assertThat(deadLetters()).isEmpty();
    }

    // A regular file where the confirmations directory should be: the file writer checks its directory on
    // every message and fails, after the order's status was recorded.
    private void blockConfirmations() {
        try {
            FileSystemUtils.deleteRecursively(confirmations);
            Files.createDirectories(confirmations.getParent());
            Files.writeString(confirmations, "not a directory");
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private void unblockConfirmations() throws IOException {
        if (Files.isRegularFile(confirmations)) {
            Files.delete(confirmations);
        }
        Files.createDirectories(confirmations);
    }

    private long orderIdOf(long customerId) {
        return ((Number) data.singleOrderOf(customerId).get("id")).longValue();
    }

    private List<Path> deadLetters() {
        try (Stream<Path> files = Files.list(failed)) {
            return files.filter(file -> file.getFileName().toString().endsWith(OrderErrorHandler.DEAD_LETTER_EXTENSION))
                    .toList();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    // Written under a temporary name the *.csv filter ignores, then renamed: the poller can never read a
    // half-written file.
    private void drop(String fileName, String content) throws IOException {
        Path temporary = Files.writeString(incoming.resolve(fileName + ".tmp"), content);
        Files.move(temporary, incoming.resolve(fileName), StandardCopyOption.ATOMIC_MOVE);
    }
}
