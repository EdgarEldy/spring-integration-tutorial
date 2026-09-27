package com.edgareldy.springintegrationtutorial.integration.error;

import com.edgareldy.springintegrationtutorial.config.IntegrationConfig;
import com.edgareldy.springintegrationtutorial.entity.Order;
import com.edgareldy.springintegrationtutorial.integration.message.LineOutcome;
import com.edgareldy.springintegrationtutorial.integration.splitter.OrderFileSplitter;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.stereotype.Component;

/**
 * Local error handling of the bulk file path: turns the failure of one line (malformed, unknown customer or
 * product) into a failed {@link LineOutcome} for the aggregator, so it never reaches the global error
 * channel and never keeps its file's report from being released. A line failing after persistence or
 * after exhausted retries also gets the failure rule of {@link OrderErrorHandler}.
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 */
@Component
public class OrderLineErrorHandler {

    private final OrderErrorHandler orderErrorHandler;

    /**
     * @param orderErrorHandler applies the failure rule (dead letter, {@code FAILED}) to the lines it concerns
     */
    public OrderLineErrorHandler(OrderErrorHandler orderErrorHandler) {
        this.orderErrorHandler = orderErrorHandler;
    }

    /**
     * @param errorMessage the failure caught by the per-line gateway
     * @return the failed outcome, carrying the line's correlation headers
     * @throws IllegalStateException if the failed line cannot be identified
     */
    // An ErrorMessage is the message Spring Integration builds for a failure: its payload is the exception,
    // and its original message is the message that was being sent when it happened, here the split line as
    // the gateway sent it, with the correlation id, sequence and line headers the aggregator needs. The
    // outcome copies those headers, so the aggregator files it under the right file, exactly as it would a
    // successful line.
    // This is also the seam of a line failing after persistence: the exception's failed message then
    // identifies the order, whose id the failed outcome keeps.
    @ServiceActivator(inputChannel = IntegrationConfig.ORDER_LINE_ERROR_CHANNEL,
            outputChannel = IntegrationConfig.LINE_OUTCOME_CHANNEL)
    public Message<LineOutcome> toFailedOutcome(ErrorMessage errorMessage) {
        Throwable failure = errorMessage.getPayload();
        Message<?> line = errorMessage.getOriginalMessage();
        if (line == null && failure instanceof MessagingException messagingException) {
            line = messagingException.getFailedMessage();
        }
        if (line == null) {
            throw new IllegalStateException("Cannot identify the failed order line", failure);
        }
        Integer lineNumber = line.getHeaders().get(OrderFileSplitter.LINE_NUMBER_HEADER, Integer.class);
        String content = line.getHeaders().get(OrderFileSplitter.LINE_HEADER, String.class);
        // The exception reaching the gateway wraps the real one (a MessageHandlingException naming the failing
        // endpoint); the report keeps the root cause, the part a person dropping the file can act on.
        String reason = NestedExceptionUtils.getMostSpecificCause(failure).getMessage();
        Long orderId = orderIdOf(failure);
        recordIfNeeded(failure, orderId, line);
        LineOutcome outcome = LineOutcome.failed(lineNumber == null ? 0 : lineNumber, content, orderId, reason);
        return MessageBuilder.withPayload(outcome)
                .copyHeaders(line.getHeaders())
                .build();
    }

    // On top of its failed entry in the report, a line gets the failure rule of the flow (dead letter, FAILED
    // when its order exists) when it failed after persistence (an order id is known) or because persistence
    // kept failing after its retries. A line refused before persistence (malformed, unknown customer or
    // product) is only a failed entry of the report: nothing was created, and the report says why.
    // A failure of a step after persistence was already recorded by OrderFailureRecordingAdvice before it
    // reached this handler: it is not recorded twice.
    private void recordIfNeeded(Throwable failure, Long orderId, Message<?> line) {
        if (OrderErrorHandler.alreadyRecorded(failure)) {
            return;
        }
        if (orderId != null || IntegrationConfig.isRetriedPersistenceFailure(failure)) {
            Message<?> failedMessage = failure instanceof MessagingException messagingException
                    && messagingException.getFailedMessage() != null
                    ? messagingException.getFailedMessage()
                    : line;
            orderErrorHandler.recordFailure(failedMessage, failure);
        }
    }

    // The failed message of the exception is the one the failing endpoint was handling. Once the order is
    // persisted, every later message of the flow carries the orderId header (the rendered outbound file) or
    // the order itself (the status step), so a failure after persistence still names the order it concerns.
    private static Long orderIdOf(Throwable failure) {
        if (!(failure instanceof MessagingException messagingException)
                || messagingException.getFailedMessage() == null) {
            return null;
        }
        Message<?> failedMessage = messagingException.getFailedMessage();
        if (failedMessage.getPayload() instanceof Order order) {
            return order.getId();
        }
        return failedMessage.getHeaders().get(IntegrationConfig.ORDER_ID_HEADER, Long.class);
    }
}
