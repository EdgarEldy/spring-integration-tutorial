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
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Error handling of the intake flow: retry of a transient persistence error, dead-letter file of a dropped
 * line whose persistence fails for good, lines refused before persistence left to the bulk report, a failure
 * of the dropped file itself caught by the global errorChannel, and the HTTP path left unchanged (an error
 * response, no dead-letter file).
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 */
// The real OrderService is wrapped in a Mockito spy: a test can make it fail on demand (a transient
// database error that no real database would produce reliably) and count how often the flow called it.
// That changes the context, which therefore gets directories of its own: no other cached context's poller
// can take the files dropped here, and the dead letters and reports written here stay apart from the others.
@SpringBootTest(properties = {
        "orders.directories.incoming=target/test-orders/dead-letter/incoming-orders",
        "orders.directories.outgoing=target/test-orders/dead-letter/outgoing-orders",
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

    @Value("${orders.directories.reports}")
    private Path reports;

    @Value("${orders.directories.failed}")
    private Path failed;

    @Value("${orders.persistence.max-attempts}")
    private int maxAttempts;

    private CommerceTestData data;

    @BeforeEach
    void setUp() throws IOException {
        data = new CommerceTestData(jdbc);
        unblockProcessed();
        Files.createDirectories(failed);
        Files.createDirectories(reports);
        // Emptied but kept: the poller fails on a watched directory that does not exist.
        RoutingTestDirectories.empty(incoming, failed, reports);
        Files.createDirectories(processed);
    }

    @AfterEach
    void tearDown() throws IOException {
        unblockProcessed();
    }

    @Test
    void _01_ShouldRetryThenReportTheLineAndWriteADeadLetterWithoutAnyOrder_WhenPersistenceKeepsFailing()
            throws IOException {
        long customerId = data.customer();
        long productId = data.product("15.00");
        doThrow(TRANSIENT_ERROR).when(orderService).receive(any());
        String line = customerId + "," + productId + ",2";

        drop("transient.csv", line + "\n");

        String deadLetter = awaitSingleDeadLetter("transient.csv");
        verify(orderService, times(maxAttempts)).receive(any());
        assertThat(maxAttempts).isGreaterThan(1);
        assertThat(deadLetter)
                .startsWith("source-file: transient.csv\norder-id: none\n")
                .contains("\nreason: TransientDataAccessResourceException: Database temporarily unavailable\n")
                .endsWith("\npayload:\n" + line + "\n");
        assertThat(awaitReport("transient"))
                .contains("line 1 [" + line + "]: Database temporarily unavailable");
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
        awaitReport("recovers");
        assertThat(deadLetters()).isEmpty();
    }

    @Test
    void _03_ShouldOnlyReportTheLineAfterASingleAttempt_WhenTheCustomerIsUnknown() throws IOException {
        long productId = data.product("15.00");

        drop("unknown-customer.csv", "-1," + productId + ",1");

        assertThat(awaitReport("unknown-customer"))
                .contains("line 1 [-1," + productId + ",1]: Customer with id -1 not found");
        verify(orderService, times(1)).receive(any());
        assertThat(deadLetters()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders WHERE product_id = ?", Integer.class, productId))
                .isZero();
    }

    @Test
    void _04_ShouldOnlyReportTheLine_WhenTheLineIsMalformed() throws IOException {
        drop("malformed.csv", "not,an,order");

        assertThat(awaitReport("malformed")).contains("line 1 [not,an,order]: Malformed order line");
        verify(orderService, never()).receive(any());
        assertThat(deadLetters()).isEmpty();
    }

    @Test
    void _05_ShouldWriteADeadLetterThroughTheGlobalErrorChannel_WhenTheDroppedFileCannotBeMoved()
            throws IOException {
        // A regular file where the processed directory should be: the file is read, then its move fails,
        // before any line exists. That failure of the file itself reaches the global errorChannel.
        Files.delete(processed);
        Files.writeString(processed, "not a directory");

        drop("unmovable.csv", "1,1,1");

        String deadLetter = awaitSingleDeadLetter("message");
        assertThat(deadLetter)
                .startsWith("source-file: none\norder-id: none\n")
                .contains("\nreason: ")
                .endsWith("\npayload:\n(unavailable)\n");
        verify(orderService, never()).receive(any());
    }

    @Test
    @WithMockUser
    void _06_ShouldAnswer404WithoutAnyDeadLetter_WhenAnApiOrderReferencesAnUnknownCustomer() {
        long productId = data.product("15.00");

        assertThat(post(-1, productId)).hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.message").isEqualTo("Customer with id -1 not found");

        verify(orderService, times(1)).receive(any());
        assertThat(deadLetters()).isEmpty();
    }

    @Test
    @WithMockUser
    void _07_ShouldRetryThenAnswerAnErrorWithoutAnyDeadLetter_WhenApiPersistenceKeepsFailing() {
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

    private String awaitSingleDeadLetter(String namePrefix) throws IOException {
        await().atMost(TIMEOUT).until(() -> deadLetters().size() == 1);
        Path deadLetter = deadLetters().get(0);
        assertThat(deadLetter.getFileName().toString()).startsWith(namePrefix + ".");
        return Files.readString(deadLetter, StandardCharsets.UTF_8);
    }

    private String awaitReport(String sourceBaseName) throws IOException {
        Path report = reports.resolve(sourceBaseName + "-report.txt");
        await().atMost(TIMEOUT).until(() -> Files.exists(report));
        return Files.readString(report, StandardCharsets.UTF_8);
    }

    private List<Path> deadLetters() {
        try (Stream<Path> files = Files.list(failed)) {
            return files.filter(file -> file.getFileName().toString().endsWith(OrderErrorHandler.DEAD_LETTER_EXTENSION))
                    .toList();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private void unblockProcessed() throws IOException {
        if (Files.isRegularFile(processed)) {
            Files.delete(processed);
        }
    }

    // Written under a temporary name the *.csv filter ignores, then renamed: the poller can never read a
    // half-written file.
    private void drop(String fileName, String content) throws IOException {
        Path temporary = Files.writeString(incoming.resolve(fileName + ".tmp"), content);
        Files.move(temporary, incoming.resolve(fileName), StandardCopyOption.ATOMIC_MOVE);
    }
}
