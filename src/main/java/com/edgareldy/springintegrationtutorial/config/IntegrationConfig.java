package com.edgareldy.springintegrationtutorial.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.scheduling.PollerMetadata;
import org.springframework.scheduling.support.PeriodicTrigger;

/**
 * Central place for the message channels shared by several flows and for the default poller of every
 * polling endpoint.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
@Configuration
public class IntegrationConfig {

    /**
     * Name of the channel both inbound sources (HTTP gateway, file adapter) send their raw orders to.
     */
    public static final String INTAKE_CHANNEL = "intake-channel";

    /**
     * Name of the channel carrying the normalized {@code OrderCommand}s from the shared transformer to
     * the persistence activator.
     */
    public static final String ORDER_COMMAND_CHANNEL = "order-command-channel";

    /**
     * Name of the channel carrying each persisted order from the persistence activator to the
     * content-based router.
     */
    public static final String PERSISTED_ORDER_CHANNEL = "persisted-order-channel";

    /**
     * Name of the router's output for orders under the review threshold.
     */
    public static final String AUTO_CONFIRM_CHANNEL = "auto-confirm-channel";

    /**
     * Name of the router's output for orders at or above the review threshold.
     */
    public static final String MANUAL_REVIEW_CHANNEL = "manual-review-channel";

    /**
     * Name of the input of the confirmation file adapter: a confirmed order (auto-confirmed or approved),
     * whose status is already recorded.
     */
    public static final String CONFIRMATION_CHANNEL = "confirmation-channel";

    /**
     * Name of the channel carrying the rendered confirmation file to its file writer.
     */
    public static final String CONFIRMATION_FILE_CHANNEL = "confirmation-file-channel";

    /**
     * Name of the input of the review-queue file adapter: an order whose status is already
     * {@code PENDING_REVIEW}.
     */
    public static final String REVIEW_QUEUE_CHANNEL = "review-queue-channel";

    /**
     * Name of the channel carrying the rendered review-queue entry to its file writer.
     */
    public static final String REVIEW_FILE_CHANNEL = "review-file-channel";

    /**
     * Name of the input of the rejection file adapter: a rejected order, with the reason in the
     * {@link #REJECTION_REASON_HEADER} header.
     */
    public static final String REJECTION_CHANNEL = "rejection-channel";

    /**
     * Name of the channel carrying the rendered rejection file to its file writer.
     */
    public static final String REJECTION_FILE_CHANNEL = "rejection-file-channel";

    /**
     * Name of the channel the review gateway sends each administrator decision to.
     */
    public static final String REVIEW_DECISION_CHANNEL = "review-decision-channel";

    /**
     * Header carrying the id of the order an outbound file is written for, so a step failing on the file
     * message still knows which order it concerns.
     */
    public static final String ORDER_ID_HEADER = "orderId";

    /**
     * Header carrying the administrator's reason on a rejected order.
     */
    public static final String REJECTION_REASON_HEADER = "rejectionReason";

    /**
     * Name of the channel carrying the whole content of each dropped file from the file adapter to the
     * bulk splitter.
     */
    public static final String ORDER_FILE_CHANNEL = "order-file-channel";

    /**
     * Name of the channel carrying each non-blank line of a file, split out and correlated to its file.
     */
    public static final String ORDER_LINE_CHANNEL = "order-line-channel";

    /**
     * Name of the error channel of the per-line gateway: where a line that failed in the shared flow
     * (malformed, unknown customer or product) is turned into a failed outcome.
     */
    public static final String ORDER_LINE_ERROR_CHANNEL = "order-line-error-channel";

    /**
     * Name of the channel carrying every per-line outcome, success or failure, to the bulk aggregator.
     */
    public static final String LINE_OUTCOME_CHANNEL = "line-outcome-channel";

    /**
     * Name of the channel carrying each released file report to the report writer.
     */
    public static final String REPORT_CHANNEL = "report-channel";

    /**
     * @return the shared intake channel
     */
    // A DirectChannel hands each message to its single subscriber in the sender's own thread, like a
    // method call. That is what the intake needs: an HTTP request travels through the flow while the
    // client waits, so a failure (unknown customer, unknown product) comes back as an exception to the
    // controller, and a transaction can span the whole call. A QueueChannel would buffer the message and
    // return immediately, decoupling producer and consumer at the cost of that immediate answer; a
    // PublishSubscribeChannel would broadcast to every subscriber, whereas an order must be processed once.
    // Declaring the channel explicitly (instead of letting Spring Integration create it on first reference)
    // gives it one documented type and makes it visible in the integration graph under a stable name.
    @Bean(INTAKE_CHANNEL)
    public DirectChannel intakeChannel() {
        return new DirectChannel();
    }

    /**
     * @return the channel between the transformer and the persistence activator
     */
    // Also a DirectChannel, for the same reason as the intake channel: the persistence step must still run
    // in the HTTP caller's thread, so that an unknown customer or product reaches the client as a 404.
    @Bean(ORDER_COMMAND_CHANNEL)
    public DirectChannel orderCommandChannel() {
        return new DirectChannel();
    }

    // The routing part of the flow below is made of DirectChannels too. An HTTP intake keeps running in
    // the request thread down to the outbound file, and a review decision down to its confirmation or
    // rejection file, so an unknown order (404) or an order resolved twice (422) reaches the administrator
    // as an HTTP error. Each outcome step (status update, rendering, file writing) sits behind its own
    // channel, so it can be tested, replaced or given an error policy on its own.

    /**
     * @return the channel between the persistence activator and the router
     */
    @Bean(PERSISTED_ORDER_CHANNEL)
    public DirectChannel persistedOrderChannel() {
        return new DirectChannel();
    }

    /**
     * @return the router's auto-confirm output
     */
    @Bean(AUTO_CONFIRM_CHANNEL)
    public DirectChannel autoConfirmChannel() {
        return new DirectChannel();
    }

    /**
     * @return the router's manual-review output
     */
    @Bean(MANUAL_REVIEW_CHANNEL)
    public DirectChannel manualReviewChannel() {
        return new DirectChannel();
    }

    /**
     * @return the input of the confirmation file adapter
     */
    @Bean(CONFIRMATION_CHANNEL)
    public DirectChannel confirmationChannel() {
        return new DirectChannel();
    }

    /**
     * @return the channel feeding the confirmation file writer
     */
    @Bean(CONFIRMATION_FILE_CHANNEL)
    public DirectChannel confirmationFileChannel() {
        return new DirectChannel();
    }

    /**
     * @return the input of the review-queue file adapter
     */
    @Bean(REVIEW_QUEUE_CHANNEL)
    public DirectChannel reviewQueueChannel() {
        return new DirectChannel();
    }

    /**
     * @return the channel feeding the review-queue file writer
     */
    @Bean(REVIEW_FILE_CHANNEL)
    public DirectChannel reviewFileChannel() {
        return new DirectChannel();
    }

    /**
     * @return the input of the rejection file adapter
     */
    @Bean(REJECTION_CHANNEL)
    public DirectChannel rejectionChannel() {
        return new DirectChannel();
    }

    /**
     * @return the channel feeding the rejection file writer
     */
    @Bean(REJECTION_FILE_CHANNEL)
    public DirectChannel rejectionFileChannel() {
        return new DirectChannel();
    }

    /**
     * @return the channel of the administrators' review decisions
     */
    @Bean(REVIEW_DECISION_CHANNEL)
    public DirectChannel reviewDecisionChannel() {
        return new DirectChannel();
    }

    /**
     * @return the channel between the file adapter and the splitter
     */
    // The bulk file path stays synchronous from end to end: the poller thread reads a file, splits it, runs
    // every line through the shared flow and writes the report before the next poll. Each of its channels is
    // therefore a DirectChannel too, and a file's report exists as soon as the poll that read it is over.
    @Bean(ORDER_FILE_CHANNEL)
    public DirectChannel orderFileChannel() {
        return new DirectChannel();
    }

    /**
     * @return the channel between the splitter and the per-line dispatch
     */
    @Bean(ORDER_LINE_CHANNEL)
    public DirectChannel orderLineChannel() {
        return new DirectChannel();
    }

    /**
     * @return the error channel of the per-line gateway
     */
    @Bean(ORDER_LINE_ERROR_CHANNEL)
    public DirectChannel orderLineErrorChannel() {
        return new DirectChannel();
    }

    /**
     * @return the channel feeding the aggregator
     */
    @Bean(LINE_OUTCOME_CHANNEL)
    public DirectChannel lineOutcomeChannel() {
        return new DirectChannel();
    }

    /**
     * @return the channel between the aggregator and the report writer
     */
    @Bean(REPORT_CHANNEL)
    public DirectChannel reportChannel() {
        return new DirectChannel();
    }

    /**
     * @param fixedDelay       milliseconds between the end of one poll and the start of the next
     * @param maxMessagesPerPoll how many messages one poll may take at most
     * @return the poller used by every polling endpoint that does not declare its own
     */
    // Some endpoints are not pushed messages but have to fetch them: a file inbound adapter checks its
    // directory, a consumer of a QueueChannel takes from the queue. PollerMetadata describes how often
    // they poll (the trigger) and how much they take per poll. Registered under the reserved name
    // PollerMetadata.DEFAULT_POLLER, it applies to every polling endpoint without a poller of its own and
    // replaces the one Spring Boot would otherwise derive from spring.integration.poller.* properties, so
    // the rhythm is configured under orders.poller like the rest of the application settings.
    @Bean(PollerMetadata.DEFAULT_POLLER)
    public PollerMetadata defaultPoller(
            @Value("${orders.poller.fixed-delay}") long fixedDelay,
            @Value("${orders.poller.max-messages-per-poll}") int maxMessagesPerPoll) {
        PollerMetadata poller = new PollerMetadata();
        // Fixed delay rather than fixed rate: the next poll starts only once the previous one is done,
        // so a slow batch of files never piles overlapping polls on top of each other.
        poller.setTrigger(new PeriodicTrigger(Duration.ofMillis(fixedDelay)));
        poller.setMaxMessagesPerPoll(maxMessagesPerPoll);
        return poller;
    }
}
