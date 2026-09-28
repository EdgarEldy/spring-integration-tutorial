package com.edgareldy.springintegrationtutorial.integration.activator;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgareldy.springintegrationtutorial.config.IntegrationConfig;
import com.edgareldy.springintegrationtutorial.integration.message.LineOutcome;
import com.edgareldy.springintegrationtutorial.integration.splitter.OrderFileSplitter;
import java.io.File;
import org.junit.jupiter.api.Test;
import org.springframework.integration.IntegrationMessageHeaderAccessor;
import org.springframework.integration.file.FileHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;

/**
 * Unit tests of {@link LineOutcomeActivator}: the written confirmation or review file of a bulk line becomes
 * its outcome, any other order ends without one.
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 */
class LineOutcomeActivatorTest {

    private final LineOutcomeActivator activator = new LineOutcomeActivator();

    @Test
    void _01_ShouldEmitAnAutoConfirmedOutcomeWithTheLineHeaders_WhenTheConfirmationFileOfALineIsWritten() {
        Message<LineOutcome> outcome = activator.confirmed(writtenFileOfLine());

        assertThat(outcome.getPayload()).isEqualTo(LineOutcome.autoConfirmed(4, "1,2,2", 42L));
        assertThat(outcome.getHeaders())
                .containsEntry(IntegrationMessageHeaderAccessor.CORRELATION_ID, "file-1")
                .containsEntry(IntegrationMessageHeaderAccessor.SEQUENCE_SIZE, 3)
                .containsEntry(FileHeaders.FILENAME, "order-42.txt");
    }

    @Test
    void _02_ShouldEmitAPendingReviewOutcome_WhenTheReviewEntryOfALineIsWritten() {
        Message<LineOutcome> outcome = activator.queuedForReview(writtenFileOfLine());

        assertThat(outcome.getPayload()).isEqualTo(LineOutcome.pendingReview(4, "1,2,2", 42L));
    }

    @Test
    void _03_ShouldEmitNothing_WhenTheWrittenFileBelongsToAnHttpOrderOrAnApproval() {
        Message<File> written = MessageBuilder.withPayload(new File("order-7.txt"))
                .setHeader(IntegrationConfig.ORDER_ID_HEADER, 7L).build();

        assertThat(activator.confirmed(written)).isNull();
        assertThat(activator.queuedForReview(written)).isNull();
    }

    private static Message<File> writtenFileOfLine() {
        return MessageBuilder.withPayload(new File("order-42.txt"))
                .setHeader(OrderFileSplitter.LINE_NUMBER_HEADER, 4)
                .setHeader(OrderFileSplitter.LINE_HEADER, "1,2,2")
                .setHeader(IntegrationConfig.ORDER_ID_HEADER, 42L)
                .setHeader(FileHeaders.FILENAME, "order-42.txt")
                .setCorrelationId("file-1")
                .setSequenceNumber(2)
                .setSequenceSize(3)
                .build();
    }
}
