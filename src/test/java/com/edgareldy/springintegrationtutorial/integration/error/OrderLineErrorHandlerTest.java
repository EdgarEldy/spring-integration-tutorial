package com.edgareldy.springintegrationtutorial.integration.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edgareldy.springintegrationtutorial.config.IntegrationConfig;
import com.edgareldy.springintegrationtutorial.entity.Order;
import com.edgareldy.springintegrationtutorial.exception.ResourceNotFoundException;
import com.edgareldy.springintegrationtutorial.integration.message.LineOutcome;
import com.edgareldy.springintegrationtutorial.integration.splitter.OrderFileSplitter;
import org.junit.jupiter.api.Test;
import org.springframework.integration.IntegrationMessageHeaderAccessor;
import org.springframework.integration.file.FileHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandlingException;
import org.springframework.messaging.support.ErrorMessage;

/**
 * Unit tests of {@link OrderLineErrorHandler}: how the failure of one file line becomes a failed
 * {@link LineOutcome} the aggregator can correlate.
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 */
class OrderLineErrorHandlerTest {

    private final OrderLineErrorHandler handler = new OrderLineErrorHandler();

    @Test
    void _01_ShouldBuildAFailedOutcomeWithTheRootCauseAndTheLineHeaders_WhenTheOriginalLineIsKnown() {
        Message<String> line = line();
        MessageHandlingException failure = new MessageHandlingException(line, "handler failed",
                ResourceNotFoundException.of("Customer", -1L));

        Message<LineOutcome> outcome = handler.toFailedOutcome(new ErrorMessage(failure, line));

        assertThat(outcome.getPayload()).isEqualTo(
                LineOutcome.failed(3, "-1,2,1", "Customer with id -1 not found"));
        assertThat(outcome.getHeaders())
                .containsEntry(IntegrationMessageHeaderAccessor.CORRELATION_ID, "file-1")
                .containsEntry(IntegrationMessageHeaderAccessor.SEQUENCE_SIZE, 2)
                .containsEntry(FileHeaders.FILENAME, "orders.csv");
    }

    @Test
    void _02_ShouldUseTheFailedMessageOfTheException_WhenTheErrorMessageHasNoOriginalMessage() {
        MessageHandlingException failure = new MessageHandlingException(line(), "handler failed",
                new IllegalArgumentException("Malformed order line"));

        Message<LineOutcome> outcome = handler.toFailedOutcome(new ErrorMessage(failure));

        assertThat(outcome.getPayload()).isEqualTo(LineOutcome.failed(3, "-1,2,1", "Malformed order line"));
        assertThat(outcome.getHeaders()).containsEntry(IntegrationMessageHeaderAccessor.CORRELATION_ID, "file-1");
    }

    @Test
    void _03_ShouldRefuseTheError_WhenTheFailedLineCannotBeIdentified() {
        ErrorMessage errorMessage = new ErrorMessage(new IllegalStateException("no message at all"));

        assertThatThrownBy(() -> handler.toFailedOutcome(errorMessage))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot identify the failed order line");
    }

    @Test
    void _04_ShouldKeepTheOrderId_WhenTheLineFailedWhileItsOutboundFileWasWritten() {
        Message<String> fileMessage = MessageBuilder.withPayload("orderId=42")
                .setHeader(IntegrationConfig.ORDER_ID_HEADER, 42L).build();
        MessageHandlingException failure = new MessageHandlingException(fileMessage, "writer failed",
                new IllegalStateException("Disk full"));

        Message<LineOutcome> outcome = handler.toFailedOutcome(new ErrorMessage(failure, line()));

        assertThat(outcome.getPayload()).isEqualTo(LineOutcome.failed(3, "-1,2,1", 42L, "Disk full"));
    }

    @Test
    void _05_ShouldKeepTheOrderId_WhenTheLineFailedOnAStepHandlingThePersistedOrder() {
        Order order = new Order();
        order.setId(43L);
        MessageHandlingException failure = new MessageHandlingException(MessageBuilder.withPayload(order).build(),
                "status step failed", new IllegalStateException("Connection lost"));

        Message<LineOutcome> outcome = handler.toFailedOutcome(new ErrorMessage(failure, line()));

        assertThat(outcome.getPayload().orderId()).isEqualTo(43L);
    }

    private static Message<String> line() {
        return MessageBuilder.withPayload("-1,2,1")
                .setHeader(OrderFileSplitter.LINE_NUMBER_HEADER, 3)
                .setHeader(OrderFileSplitter.LINE_HEADER, "-1,2,1")
                .setHeader(FileHeaders.FILENAME, "orders.csv")
                .setCorrelationId("file-1")
                .setSequenceNumber(2)
                .setSequenceSize(2)
                .build();
    }
}
