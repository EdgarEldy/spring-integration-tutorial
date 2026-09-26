package com.edgareldy.springintegrationtutorial.integration.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgareldy.springintegrationtutorial.entity.OrderSource;
import com.edgareldy.springintegrationtutorial.exception.ResourceNotFoundException;
import com.edgareldy.springintegrationtutorial.integration.message.OrderCommand;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.integration.file.FileHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.transformer.MessageTransformationException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandlingException;
import org.springframework.messaging.support.ErrorMessage;

/**
 * Unit tests of the dead letter {@link OrderErrorHandler} builds from a failure, without any Spring
 * context: content (source, reason, payload) and file name.
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 */
class OrderErrorHandlerTest {

    private final OrderErrorHandler handler = new OrderErrorHandler();

    @Test
    void _01_ShouldKeepTheRawLineAndTheRootCause_WhenTheTransformerRejectedAFileLine() {
        Message<String> failed = fileMessage("abc\n", "bad.csv");
        ErrorMessage error = new ErrorMessage(new MessageTransformationException(failed, "Failed to transform",
                new IllegalArgumentException("Malformed order line: abc")));

        Message<String> deadLetter = handler.handle(error);

        assertThat(deadLetter.getPayload())
                .startsWith("source-file: bad.csv\nfailed-at: ")
                .contains("\nreason: IllegalArgumentException: Malformed order line: abc\n")
                .endsWith("\npayload:\nabc\n");
        assertThat(deadLetter.getHeaders().get(FileHeaders.FILENAME, String.class))
                .startsWith("bad.csv.").endsWith(OrderErrorHandler.DEAD_LETTER_EXTENSION);
    }

    @Test
    void _02_ShouldWriteTheCommandBackAsACsvLine_WhenPersistenceFailed() {
        Message<OrderCommand> failed = MessageBuilder.withPayload(new OrderCommand(4L, 5L, 2, OrderSource.FILE))
                .setHeader(FileHeaders.FILENAME, "order-4.csv").build();
        ErrorMessage error = new ErrorMessage(
                new MessageHandlingException(failed, ResourceNotFoundException.of("Customer", 4L)));

        String content = handler.handle(error).getPayload();

        assertThat(content).contains("\nreason: ResourceNotFoundException: Customer with id 4 not found\n")
                .endsWith("\npayload:\n4,5,2\n");
    }

    @Test
    void _03_ShouldReportTheDatabaseError_WhenTheRetriesOfATransientErrorAreExhausted() {
        Message<OrderCommand> failed = MessageBuilder.withPayload(new OrderCommand(4L, 5L, 2, OrderSource.FILE))
                .setHeader(FileHeaders.FILENAME, "order-4.csv").build();
        ErrorMessage error = new ErrorMessage(new MessageHandlingException(failed,
                new TransientDataAccessResourceException("Database temporarily unavailable")));

        assertThat(handler.handle(error).getPayload())
                .contains("\nreason: TransientDataAccessResourceException: Database temporarily unavailable\n");
    }

    @Test
    void _04_ShouldUseGenericValues_WhenTheFailureCarriesNoFailedMessage() {
        Message<String> deadLetter = handler.handle(new ErrorMessage(new IllegalStateException("disk full")));

        assertThat(deadLetter.getPayload())
                .startsWith("source-file: " + OrderErrorHandler.UNKNOWN_SOURCE + "\n")
                .contains("\nreason: IllegalStateException: disk full\n")
                .endsWith("\npayload:\n(unavailable)\n");
        assertThat(deadLetter.getHeaders().get(FileHeaders.FILENAME, String.class))
                .startsWith(OrderErrorHandler.UNKNOWN_SOURCE + ".");
    }

    @Test
    void _05_ShouldGiveEachDeadLetterItsOwnFileName_WhenTheSameFileFailsTwice() {
        ErrorMessage error = new ErrorMessage(new MessageHandlingException(fileMessage("1,2,3", "same.csv"),
                new IllegalStateException("boom")));

        assertThat(handler.handle(error).getHeaders().get(FileHeaders.FILENAME))
                .isNotEqualTo(handler.handle(error).getHeaders().get(FileHeaders.FILENAME));
    }

    private static Message<String> fileMessage(String content, String fileName) {
        return MessageBuilder.withPayload(content).setHeader(FileHeaders.FILENAME, fileName).build();
    }
}
