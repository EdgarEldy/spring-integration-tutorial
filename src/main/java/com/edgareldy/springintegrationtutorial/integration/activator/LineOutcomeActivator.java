package com.edgareldy.springintegrationtutorial.integration.activator;

import com.edgareldy.springintegrationtutorial.config.IntegrationConfig;
import com.edgareldy.springintegrationtutorial.integration.message.LineOutcome;
import com.edgareldy.springintegrationtutorial.integration.splitter.OrderFileSplitter;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * End of the routed flow for a line of a bulk file: once the confirmation file or the review-queue entry of
 * its order is written, turns the line into an auto-confirmed or pending review {@link LineOutcome} for the
 * aggregator. Orders that did not come from a file line (HTTP intake, approval) end here without outcome.
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 */
// The file writers reply with the written file, and the reply keeps the headers of the message they wrote,
// which were copied all along the flow: the split line's correlation, sequence and line headers, and the
// orderId header. Waiting for that reply is what makes the outcome final: a line is counted as confirmed
// or pending review only once its status is recorded AND its file exists, and a failure while writing the
// file goes to the line error flow instead, so a line never produces two outcomes.
@Component
public class LineOutcomeActivator {

    /**
     * @param written the reply of the confirmation file writer
     * @return the auto-confirmed outcome of a bulk line, or {@code null} for any other confirmed order
     */
    // Returning null from a service activator is how an endpoint ends a message's journey on purpose: nothing
    // is sent on. An HTTP order or an approval carries no line header and stops here.
    @ServiceActivator(inputChannel = IntegrationConfig.CONFIRMATION_WRITTEN_CHANNEL,
            outputChannel = IntegrationConfig.LINE_OUTCOME_CHANNEL)
    public Message<LineOutcome> confirmed(Message<?> written) {
        return outcome(written, LineOutcome.Result.AUTO_CONFIRMED);
    }

    /**
     * @param written the reply of the review-queue file writer
     * @return the pending review outcome of a bulk line, or {@code null} for an HTTP order
     */
    @ServiceActivator(inputChannel = IntegrationConfig.REVIEW_WRITTEN_CHANNEL,
            outputChannel = IntegrationConfig.LINE_OUTCOME_CHANNEL)
    public Message<LineOutcome> queuedForReview(Message<?> written) {
        return outcome(written, LineOutcome.Result.PENDING_REVIEW);
    }

    private static Message<LineOutcome> outcome(Message<?> written, LineOutcome.Result result) {
        Integer lineNumber = written.getHeaders().get(OrderFileSplitter.LINE_NUMBER_HEADER, Integer.class);
        if (lineNumber == null) {
            return null;
        }
        String line = written.getHeaders().get(OrderFileSplitter.LINE_HEADER, String.class);
        Long orderId = written.getHeaders().get(IntegrationConfig.ORDER_ID_HEADER, Long.class);
        LineOutcome outcome = result == LineOutcome.Result.AUTO_CONFIRMED
                ? LineOutcome.autoConfirmed(lineNumber, line, orderId)
                : LineOutcome.pendingReview(lineNumber, line, orderId);
        return MessageBuilder.withPayload(outcome).copyHeaders(written.getHeaders()).build();
    }
}
