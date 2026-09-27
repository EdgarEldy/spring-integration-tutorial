package com.edgareldy.springintegrationtutorial.integration.error;

import com.edgareldy.springintegrationtutorial.config.IntegrationConfig;
import com.edgareldy.springintegrationtutorial.entity.Order;
import com.edgareldy.springintegrationtutorial.entity.OrderStatus;
import com.edgareldy.springintegrationtutorial.exception.ResourceNotFoundException;
import com.edgareldy.springintegrationtutorial.integration.adapter.DeadLetterOutboundAdapterConfig;
import com.edgareldy.springintegrationtutorial.integration.message.OrderCommand;
import com.edgareldy.springintegrationtutorial.integration.splitter.OrderFileSplitter;
import com.edgareldy.springintegrationtutorial.service.OrderService;
import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.context.IntegrationContextUtils;
import org.springframework.integration.file.FileHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.stereotype.Component;

/**
 * The failure rule of the order flows, in one place: {@link #recordFailure} sets an existing order to
 * {@code FAILED} and sends a dead letter (failed payload plus reason) to the dead-letter channel. It is
 * applied to every failure published on {@code errorChannel}, to the steps that run after persistence
 * (through {@link OrderFailureRecordingAdvice}) and to the lines of a dropped file that fail after
 * persistence or after exhausted retries (through {@link OrderLineErrorHandler}).
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 */
@Component
public class OrderErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderErrorHandler.class);

    /**
     * Extension of every dead-letter file.
     */
    public static final String DEAD_LETTER_EXTENSION = ".failed";

    /**
     * Value written for a field the failed message does not carry (no source file, no order).
     */
    static final String NONE = "none";

    private final OrderService orderService;
    private final MessageChannel deadLetterChannel;

    /**
     * @param orderService      sets the order to {@code FAILED}
     * @param deadLetterChannel where the dead letters go
     */
    public OrderErrorHandler(
            OrderService orderService,
            @Qualifier(DeadLetterOutboundAdapterConfig.DEAD_LETTER_CHANNEL) MessageChannel deadLetterChannel) {
        this.orderService = orderService;
        this.deadLetterChannel = deadLetterChannel;
    }

    /**
     * @param errorMessage a failure of an asynchronous path, as published on {@code errorChannel}
     */
    // errorChannel is the channel Spring Integration publishes to when a flow started by a poller (here the
    // file inbound adapter) fails: nobody waits for an answer on that thread, so the exception, wrapped
    // in an ErrorMessage whose payload keeps the failed message, is sent there instead of being thrown.
    // Spring Integration registers it by default, as a publish-subscribe channel whose only subscriber
    // logs the error: this activator becomes a second subscriber, and the log stays.
    // A synchronous call never reaches it: the HTTP gateway runs the flow in the request thread and gets
    // the exception back. Neither does a line of a dropped file: the per-line gateway sends its failures to
    // its own error channel (OrderLineErrorHandler), so the line is reported and its file's group released.
    // What is left for errorChannel is a failure of the file itself, outside any line: reading or moving the
    // dropped file, aggregating its outcomes or writing its report.
    @ServiceActivator(inputChannel = IntegrationContextUtils.ERROR_CHANNEL_BEAN_NAME)
    public void handle(ErrorMessage errorMessage) {
        Throwable failure = errorMessage.getPayload();
        // A step after persistence already recorded its own failure (OrderFailureRecordingAdvice) before
        // the exception travelled up to the poller: recording it again would write a second dead letter.
        if (alreadyRecorded(failure)) {
            log.debug("Failure already recorded by the step that raised it: {}", failure.getMessage());
            return;
        }
        Message<?> failedMessage = failure instanceof MessagingException messagingException
                ? messagingException.getFailedMessage()
                : null;
        recordFailure(failedMessage, failure);
    }

    /**
     * Records one failure. When the failed message identifies an order (an {@code orderId} header, or an
     * {@link Order} payload), the failure happened after persistence and the order is set to
     * {@code FAILED}, once, if it still exists. In every case a dead letter is written, the only record
     * left of an input that never became an order. Its content is plain text:
     * <pre>
     * source-file: order-1.csv
     * order-id: 42
     * failed-at: 2026-09-27T10:15:30.123Z
     * reason: IllegalArgumentException: Destination directory [...] is not a directory
     * payload:
     * orderId=42
     * ...
     * </pre>
     * {@code source-file} is the dropped file the order came from, {@code none} for an HTTP order, and
     * {@code order-id} is {@code none} when the failure happened before persistence. The payload is the
     * text of the dropped file's line when the message carries it ({@code order_line} header), otherwise
     * the failed message's payload: the raw input before the transformer, an {@link OrderCommand} written back
     * as its {@code customerId,productId,quantity} line (it can be dropped into the incoming directory
     * again once the cause is fixed), an {@link Order} as {@code key=value} lines, or the outbound file
     * content. The file is named {@code <source-file or order-<id>>.<unique id>.failed}.
     *
     * @param failedMessage the message being processed when the failure happened, {@code null} if unknown
     * @param failure       the failure
     */
    public void recordFailure(Message<?> failedMessage, Throwable failure) {
        String sourceFile = sourceFileOf(failedMessage);
        Long orderId = orderIdOf(failedMessage);
        String reason = reasonOf(failure);
        if (orderId != null) {
            markFailed(orderId);
        }
        log.warn("Order failed (source file {}, order {}), written to the dead-letter directory: {}",
                sourceFile, orderId == null ? NONE : orderId, reason);

        String content = "source-file: " + sourceFile + "\n"
                + "order-id: " + (orderId == null ? NONE : orderId) + "\n"
                + "failed-at: " + Instant.now() + "\n"
                + "reason: " + reason + "\n"
                + "payload:\n"
                + payloadOf(failedMessage) + "\n";
        String baseName = !NONE.equals(sourceFile) ? sourceFile
                : orderId != null ? "order-" + orderId
                : "message";
        deadLetterChannel.send(MessageBuilder.withPayload(content)
                .setHeader(FileHeaders.FILENAME, baseName + "." + UUID.randomUUID() + DEAD_LETTER_EXTENSION)
                .build());
    }

    // FAILED is a status: it only applies to an order that exists. The update is attempted once; if it
    // fails too (the database may be the very cause of the failure), the dead letter is still written.
    private void markFailed(Long orderId) {
        try {
            orderService.updateStatus(orderId, OrderStatus.FAILED);
        } catch (ResourceNotFoundException ex) {
            log.warn("Order {} does not exist, no FAILED status to record", orderId);
        } catch (RuntimeException ex) {
            log.error("Could not set order {} to FAILED", orderId, ex);
        }
    }

    static boolean alreadyRecorded(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof RecordedOrderFailureException) {
                return true;
            }
        }
        return false;
    }

    // The inbound file adapter sets file_originalFile to the dropped file (moved to processed/); file_name
    // is not used, since the outbound adapters reuse it for the name of the file they write.
    private static String sourceFileOf(Message<?> failedMessage) {
        Object originalFile = failedMessage == null ? null : failedMessage.getHeaders().get(FileHeaders.ORIGINAL_FILE);
        if (originalFile instanceof File file) {
            return file.getName();
        }
        if (originalFile != null) {
            return Path.of(originalFile.toString()).getFileName().toString();
        }
        return NONE;
    }

    private static Long orderIdOf(Message<?> failedMessage) {
        if (failedMessage == null) {
            return null;
        }
        if (failedMessage.getPayload() instanceof Order order) {
            return order.getId();
        }
        Object header = failedMessage.getHeaders().get(IntegrationConfig.ORDER_ID_HEADER);
        return header instanceof Number number ? number.longValue() : null;
    }

    // The most specific cause is the real reason: the outer exceptions only say which endpoint failed.
    private static String reasonOf(Throwable failure) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(failure);
        String message = cause.getMessage();
        String type = cause.getClass().getSimpleName();
        return message == null ? type : type + ": " + message.strip().replaceAll("\\R", " ");
    }

    private static String payloadOf(Message<?> failedMessage) {
        if (failedMessage == null) {
            return "(unavailable)";
        }
        // A line of a dropped file carries its own text all the way through the flow: that raw line is the
        // most useful payload, whatever step failed, since it can be dropped again once the cause is fixed.
        String line = failedMessage.getHeaders().get(OrderFileSplitter.LINE_HEADER, String.class);
        if (line != null) {
            return line;
        }
        Object payload = failedMessage.getPayload();
        if (payload instanceof OrderCommand command) {
            return command.customerId() + "," + command.productId() + "," + command.quantity();
        }
        if (payload instanceof Order order) {
            // Only the order's own columns: its lazy associations cannot be loaded outside a transaction.
            return "orderId=" + order.getId() + "\nsource=" + order.getSource()
                    + "\nquantity=" + order.getQuantity() + "\ntotal=" + order.getTotal();
        }
        return payload.toString().strip();
    }
}
