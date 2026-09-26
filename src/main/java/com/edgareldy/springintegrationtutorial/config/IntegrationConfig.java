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
     * @param fixedDelay        milliseconds between the end of one poll and the start of the next
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
