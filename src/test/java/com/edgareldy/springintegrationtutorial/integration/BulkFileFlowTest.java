package com.edgareldy.springintegrationtutorial.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgareldy.springintegrationtutorial.TestcontainersConfiguration;
import com.edgareldy.springintegrationtutorial.config.IntegrationConfig;
import com.edgareldy.springintegrationtutorial.integration.message.LineOutcome;
import com.edgareldy.springintegrationtutorial.integration.splitter.OrderFileSplitter;
import com.edgareldy.springintegrationtutorial.support.CommerceTestData;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.integration.IntegrationMessageHeaderAccessor;
import org.springframework.integration.file.FileHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.test.context.MockIntegrationContext;
import org.springframework.integration.test.context.SpringIntegrationTest;
import org.springframework.integration.test.mock.MockIntegration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.test.context.ActiveProfiles;

/**
 * Tests the bulk file path inside the real application context: the splitter's correlation headers, the
 * aggregator's release, the report writer, and local per-line failures ending up in the report instead
 * of the global error channel. Messages are sent straight to the flow's channels, so no file poller is
 * involved and every assertion runs right after the synchronous send.
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringIntegrationTest
class BulkFileFlowTest {

    private static final String LINE_DISPATCH_ENDPOINT = "orderLineActivator.dispatch.serviceActivator";
    private static final String REPORT_WRITER_ENDPOINT = "reportWriter.serviceActivator";

    @Autowired
    private MockIntegrationContext mockIntegrationContext;

    @Autowired
    @Qualifier(IntegrationConfig.ORDER_FILE_CHANNEL)
    private MessageChannel orderFileChannel;

    @Autowired
    @Qualifier(IntegrationConfig.LINE_OUTCOME_CHANNEL)
    private MessageChannel lineOutcomeChannel;

    @Autowired
    @Qualifier(IntegrationConfig.REPORT_CHANNEL)
    private MessageChannel reportChannel;

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${orders.directories.reports}")
    private Path reports;

    private CommerceTestData data;

    @BeforeEach
    void setUp() {
        data = new CommerceTestData(jdbc);
    }

    @AfterEach
    void restoreRealEndpoints() {
        mockIntegrationContext.resetBeans();
    }

    @Test
    void _01_ShouldCorrelateEveryNonBlankLineToItsFile_WhenAFileContentIsSplit() {
        ArgumentCaptor<Message<?>> captor = replaceWithACaptor(LINE_DISPATCH_ENDPOINT);

        Message<String> file = MessageBuilder.withPayload("1,2,3\n\n4,5,6\r\n7,8,9\n")
                .setHeader(FileHeaders.FILENAME, "split.csv").build();
        orderFileChannel.send(file);

        List<Message<?>> lines = captor.getAllValues();
        assertThat(lines).extracting(line -> (Object) line.getPayload()).containsExactly("1,2,3", "4,5,6", "7,8,9");
        assertThat(lines).allSatisfy(line -> assertThat(line.getHeaders())
                .containsEntry(IntegrationMessageHeaderAccessor.CORRELATION_ID, file.getHeaders().getId())
                .containsEntry(IntegrationMessageHeaderAccessor.SEQUENCE_SIZE, 3)
                .containsEntry(FileHeaders.FILENAME, "split.csv"));
        assertThat(lines).extracting(line -> line.getHeaders().get(IntegrationMessageHeaderAccessor.SEQUENCE_NUMBER))
                .containsExactly(1, 2, 3);
        assertThat(lines).extracting(line -> line.getHeaders().get(OrderFileSplitter.LINE_NUMBER_HEADER))
                .containsExactly(1, 3, 4);
    }

    @Test
    void _02_ShouldSendNothingOn_WhenTheFileIsBlank() {
        ArgumentCaptor<Message<?>> captor = replaceWithACaptor(LINE_DISPATCH_ENDPOINT);

        orderFileChannel.send(MessageBuilder.withPayload("\n \n").setHeader(FileHeaders.FILENAME, "blank.csv").build());

        assertThat(captor.getAllValues()).isEmpty();
    }

    @Test
    void _03_ShouldReleaseOneReportOnlyOnceEveryLineIsAccountedFor_WhenOutcomesArriveForAFile() {
        ArgumentCaptor<Message<?>> captor = replaceWithACaptor(REPORT_WRITER_ENDPOINT);
        String correlationId = UUID.randomUUID().toString();

        lineOutcomeChannel.send(outcome(LineOutcome.autoConfirmed(1, "1,2,2", 10L), correlationId, 1, 3));
        lineOutcomeChannel.send(outcome(LineOutcome.failed(2, "x", "Malformed order line 'x'"), correlationId, 2, 3));
        assertThat(captor.getAllValues()).isEmpty();

        lineOutcomeChannel.send(outcome(LineOutcome.pendingReview(4, "1,3,1", 11L), correlationId, 3, 3));

        assertThat(captor.getAllValues()).singleElement().satisfies(report -> {
            assertThat(report.getHeaders()).containsEntry(FileHeaders.FILENAME, "mixed.csv");
            assertThat((String) report.getPayload()).contains("Lines: 3", "Auto-confirmed: 1", "Pending review: 1",
                    "Failed: 1", "line 2 [x]: Malformed order line 'x'");
        });
    }

    @Test
    void _04_ShouldKeepTheGroupsOfTwoFilesApart_WhenTheirOutcomesInterleave() {
        ArgumentCaptor<Message<?>> captor = replaceWithACaptor(REPORT_WRITER_ENDPOINT);
        String first = UUID.randomUUID().toString();
        String second = UUID.randomUUID().toString();

        lineOutcomeChannel.send(outcome(LineOutcome.autoConfirmed(1, "1,2,2", 10L), first, 1, 2));
        lineOutcomeChannel.send(outcome(LineOutcome.failed(1, "y", "bad"), second, 1, 2));
        lineOutcomeChannel.send(outcome(LineOutcome.autoConfirmed(2, "1,2,2", 12L), second, 2, 2));

        assertThat(captor.getAllValues()).singleElement()
                .satisfies(report -> assertThat((String) report.getPayload()).contains("Lines: 2", "Failed: 1"));
    }

    @Test
    void _05_ShouldWriteTheReportFileNamedAfterTheSourceFile_WhenAReportIsReleased() throws IOException {
        String baseName = "report-" + UUID.randomUUID();

        reportChannel.send(MessageBuilder.withPayload("Order file report\nLines: 1\n")
                .setHeader(FileHeaders.FILENAME, baseName + ".csv").build());

        Path report = reports.resolve(baseName + "-report.txt");
        assertThat(report).exists();
        assertThat(Files.readString(report, StandardCharsets.UTF_8)).isEqualTo("Order file report\nLines: 1\n");
    }

    @Test
    void _06_ShouldReportEveryFailedLineAndCreateNoOrder_WhenEveryLineOfAFileIsInvalid() throws IOException {
        long customerId = data.customer();
        long productId = data.product("15.00");
        String baseName = "invalid-" + UUID.randomUUID();

        // Nothing is mocked: each line runs through the real transformer and persistence step, fails there,
        // and the per-line error flow turns the failure into a failed outcome. The send returns normally,
        // which shows no failure escaped to the caller (the poller, in production).
        orderFileChannel.send(MessageBuilder.withPayload("""
                        abc,%d,1

                        -1,%d,2
                        %d,-1,3
                        %d,%d,0
                        """.formatted(productId, productId, customerId, customerId, productId))
                .setHeader(FileHeaders.FILENAME, baseName + ".csv").build());

        Path report = reports.resolve(baseName + "-report.txt");
        assertThat(report).exists();
        assertThat(Files.readString(report, StandardCharsets.UTF_8)).isEqualTo("""
                Order file report
                Source file: %s.csv
                Lines: 4
                Auto-confirmed: 0
                Pending review: 0
                Failed: 4

                Failed lines:
                line 1 [abc,%d,1]: Malformed order line 'abc,%d,1': customerId, productId and quantity must be integers
                line 3 [-1,%d,2]: Customer with id -1 not found
                line 4 [%d,-1,3]: Product with id -1 not found
                line 5 [%d,%d,0]: Malformed order line '%d,%d,0': quantity must be greater than 0
                """.formatted(baseName, productId, productId, productId, customerId, customerId, productId,
                customerId, productId));
        assertThat(data.orderCount(customerId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders WHERE product_id = ?", Integer.class, productId))
                .isZero();
    }

    private ArgumentCaptor<Message<?>> replaceWithACaptor(String endpoint) {
        ArgumentCaptor<Message<?>> captor = MockIntegration.messageArgumentCaptor();
        // The mock captures every message it receives and sends nothing on: the last handleNext function is
        // reused once the registered ones are used up, so a single one serves any number of messages.
        mockIntegrationContext.substituteMessageHandlerFor(endpoint,
                MockIntegration.mockMessageHandler(captor).handleNext(message -> { }));
        return captor;
    }

    private static Message<LineOutcome> outcome(LineOutcome outcome, String correlationId, int sequenceNumber,
            int sequenceSize) {
        return MessageBuilder.withPayload(outcome)
                .setHeader(FileHeaders.FILENAME, "mixed.csv")
                .setCorrelationId(correlationId)
                .setSequenceNumber(sequenceNumber)
                .setSequenceSize(sequenceSize)
                .build();
    }
}
