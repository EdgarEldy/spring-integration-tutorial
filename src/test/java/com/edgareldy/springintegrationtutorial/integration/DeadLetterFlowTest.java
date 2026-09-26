package com.edgareldy.springintegrationtutorial.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.edgareldy.springintegrationtutorial.TestcontainersConfiguration;
import com.edgareldy.springintegrationtutorial.integration.error.OrderErrorHandler;
import com.edgareldy.springintegrationtutorial.service.OrderService;
import com.edgareldy.springintegrationtutorial.support.CommerceTestData;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.util.FileSystemUtils;

/**
 * Error handling of the intake flow: retry of a transient persistence error, dead-letter file of an
 * order dropped as a file that fails for good, and the HTTP path left unchanged (an error response, no
 * dead-letter file).
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 */
// The real OrderService is wrapped in a Mockito spy: a test can make it fail on demand (a transient
// database error that no real database would produce reliably) and count how often the flow called it.
// That changes the context, which therefore gets directories of its own: no other cached context's poller
// can take the files dropped here, and the dead letters written here stay apart from the others.
@SpringBootTest(properties = {
        "orders.directories.incoming=target/test-orders/dead-letter/incoming-orders",
        "orders.directories.failed=target/test-orders/dead-letter/failed-orders"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class DeadLetterFlowTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final TransientDataAccessResourceException TRANSIENT_ERROR =
            new TransientDataAccessResourceException("Database temporarily unavailable");

    @MockitoSpyBean
    private OrderService orderService;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${orders.directories.incoming}")
    private Path incoming;

    @Value("${orders.directories.processed}")
    private Path processed;

    @Value("${orders.directories.failed}")
    private Path failed;

    @Value("${orders.persistence.max-attempts}")
    private int maxAttempts;

    private CommerceTestData data;

    @BeforeEach
    void setUp() throws IOException {
        data = new CommerceTestData(jdbc);
        // Both directories are emptied but kept: the poller fails on a watched directory that does not exist.
        empty(incoming);
        empty(failed);
        Files.createDirectories(processed);
    }

    @Test
    void _01_ShouldRetryThenWriteADeadLetterWithoutAnyOrder_WhenPersistenceKeepsFailingWithATransientError()
            throws IOException {
        long customerId = data.customer();
        long productId = data.product("15.00");
        doThrow(TRANSIENT_ERROR).when(orderService).receive(any());

        drop("transient.csv", customerId + "," + productId + ",2\n");

        String deadLetter = awaitSingleDeadLetter("transient.csv");
        verify(orderService, times(maxAttempts)).receive(any());
        assertThat(maxAttempts).isGreaterThan(1);
        assertThat(deadLetter)
                .startsWith("source-file: transient.csv\n")
                .contains("\nreason: TransientDataAccessResourceException: Database temporarily unavailable\n")
                .endsWith("\npayload:\n" + customerId + "," + productId + ",2\n");
        assertThat(data.orderCount(customerId)).isZero();
    }

    @Test
    void _02_ShouldPersistTheOrderWithoutAnyDeadLetter_WhenTheTransientErrorDisappearsOnARetry()
            throws IOException {
        long customerId = data.customer();
        long productId = data.product("15.00");
        doThrow(TRANSIENT_ERROR).doCallRealMethod().when(orderService).receive(any());

        drop("recovers.csv", customerId + "," + productId + ",1");

        await().atMost(TIMEOUT).until(() -> data.orderCount(customerId) == 1);
        verify(orderService, times(2)).receive(any());
        assertThat(deadLetters()).isEmpty();
    }

    @Test
    void _03_ShouldWriteADeadLetterAfterASingleAttempt_WhenTheCustomerIsUnknown() throws IOException {
        long productId = data.product("15.00");

        drop("unknown-customer.csv", "-1," + productId + ",1");

        String deadLetter = awaitSingleDeadLetter("unknown-customer.csv");
        verify(orderService, times(1)).receive(any());
        assertThat(deadLetter)
                .contains("\nreason: ResourceNotFoundException: Customer with id -1 not found\n")
                .endsWith("\npayload:\n-1," + productId + ",1\n");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders WHERE product_id = ?", Integer.class, productId))
                .isZero();
    }

    @Test
    void _04_ShouldWriteTheRawLineToADeadLetter_WhenTheLineIsMalformed() throws IOException {
        drop("malformed.csv", "not,an,order");

        String deadLetter = awaitSingleDeadLetter("malformed.csv");
        verify(orderService, never()).receive(any());
        assertThat(deadLetter)
                .contains("\nreason: IllegalArgumentException: Malformed order line")
                .endsWith("\npayload:\nnot,an,order\n");
    }

    @Test
    @WithMockUser
    void _05_ShouldAnswer404WithoutAnyDeadLetter_WhenAnApiOrderReferencesAnUnknownCustomer() {
        long productId = data.product("15.00");

        assertThat(post(-1, productId)).hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.message").isEqualTo("Customer with id -1 not found");

        verify(orderService, times(1)).receive(any());
        assertThat(deadLetters()).isEmpty();
    }

    @Test
    @WithMockUser
    void _06_ShouldRetryThenAnswerAnErrorWithoutAnyDeadLetter_WhenApiPersistenceKeepsFailing() {
        long customerId = data.customer();
        long productId = data.product("15.00");
        doThrow(TRANSIENT_ERROR).when(orderService).receive(any());

        assertThat(post(customerId, productId)).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyJson().extractingPath("$.success").isEqualTo(false);

        verify(orderService, times(maxAttempts)).receive(any());
        assertThat(deadLetters()).isEmpty();
        assertThat(data.orderCount(customerId)).isZero();
    }

    private MvcTestResult post(long customerId, long productId) {
        return mvc.post().uri("/api/v1/orders/intake")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerId": %d, "productId": %d, "quantity": 1}
                        """.formatted(customerId, productId))
                .exchange();
    }

    private String awaitSingleDeadLetter(String sourceFile) throws IOException {
        await().atMost(TIMEOUT).until(() -> deadLetters().size() == 1);
        Path deadLetter = deadLetters().get(0);
        assertThat(deadLetter.getFileName().toString()).startsWith(sourceFile + ".");
        return Files.readString(deadLetter, StandardCharsets.UTF_8);
    }

    private List<Path> deadLetters() {
        try (Stream<Path> files = Files.list(failed)) {
            return files.filter(file -> file.getFileName().toString().endsWith(OrderErrorHandler.DEAD_LETTER_EXTENSION))
                    .toList();
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

    private static void empty(Path directory) throws IOException {
        Files.createDirectories(directory);
        try (Stream<Path> children = Files.list(directory)) {
            for (Path child : children.toList()) {
                FileSystemUtils.deleteRecursively(child);
            }
        }
    }
}
