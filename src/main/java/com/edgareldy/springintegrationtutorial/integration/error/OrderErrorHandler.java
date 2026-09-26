package com.edgareldy.springintegrationtutorial.integration.error;

import com.edgareldy.springintegrationtutorial.integration.adapter.DeadLetterOutboundAdapterConfig;
import com.edgareldy.springintegrationtutorial.integration.message.OrderCommand;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.context.IntegrationContextUtils;
import org.springframework.integration.file.FileHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.stereotype.Component;

/**
 * Global error handler of the asynchronous paths: turns every failure published on {@code errorChannel}
 * into a dead letter (the failed payload plus the reason) sent to the dead-letter channel, so a failed
 * order is recorded instead of silently dropped.
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
     * Source name used when the failed message did not come from a file.
     */
    static final String UNKNOWN_SOURCE = "message";

    /**
     * Builds the dead letter of one failure. Its content is plain text:
     * <pre>
     * source-file: order-1.csv
     * failed-at: 2026-09-27T10:15:30.123Z
     * reason: ResourceNotFoundException: Customer not found with id 42
     * payload:
     * 42,5,1
     * </pre>
     * The payload is the failed message's payload: the raw line when the failure happened before the
     * transformer produced an {@link OrderCommand}, the command written back as the same
     * {@code customerId,productId,quantity} line otherwise, so the payload section can be dropped into the
     * incoming directory again once the cause is fixed. The file is named
     * {@code <source-file>.<unique id>.failed}.
     *
     * @param errorMessage the failure, as published on {@code errorChannel}
     * @return the dead letter, its file name in the {@link FileHeaders#FILENAME} header
     */
    // errorChannel is the channel Spring Integration publishes to when a flow started by a poller (here the
    // file inbound adapter) fails: nobody waits for an answer on that thread, so the exception, wrapped
    // in an ErrorMessage whose payload keeps the failed message, is sent there instead of being thrown.
    // Spring Integration registers it by default, as a publish-subscribe channel whose only subscriber
    // logs the error: this activator becomes a second subscriber, and the log stays.
    // A synchronous call never reaches it: the HTTP gateway runs the flow in the request thread and gets
    // the exception back (a 404 ApiResponse for an unknown customer), and a bulk line failure is handled by
    // the aggregator path before it could escape the flow.
    // Whatever step failed (reading the file, the transformer, the persistence once its retries are
    // exhausted), no order row exists at this point, so the dead-letter file is the only record left.
    @ServiceActivator(inputChannel = IntegrationContextUtils.ERROR_CHANNEL_BEAN_NAME,
            outputChannel = DeadLetterOutboundAdapterConfig.DEAD_LETTER_CHANNEL)
    public Message<String> handle(ErrorMessage errorMessage) {
        Throwable failure = errorMessage.getPayload();
        Message<?> failedMessage = failure instanceof MessagingException messagingException
                ? messagingException.getFailedMessage()
                : null;
        String source = sourceOf(failedMessage);
        String reason = reasonOf(failure);
        log.warn("Order from {} failed, written to the dead-letter directory: {}", source, reason);

        String content = "source-file: " + source + "\n"
                + "failed-at: " + Instant.now() + "\n"
                + "reason: " + reason + "\n"
                + "payload:\n"
                + payloadOf(failedMessage) + "\n";
        return MessageBuilder.withPayload(content)
                .setHeader(FileHeaders.FILENAME, source + "." + UUID.randomUUID() + DEAD_LETTER_EXTENSION)
                .build();
    }

    private static String sourceOf(Message<?> failedMessage) {
        Object fileName = failedMessage == null ? null : failedMessage.getHeaders().get(FileHeaders.FILENAME);
        return fileName == null ? UNKNOWN_SOURCE : fileName.toString();
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
        Object payload = failedMessage.getPayload();
        if (payload instanceof OrderCommand command) {
            return command.customerId() + "," + command.productId() + "," + command.quantity();
        }
        return payload.toString().strip();
    }
}
