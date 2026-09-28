package com.edgareldy.springintegrationtutorial.integration.adapter;

import com.edgareldy.springintegrationtutorial.config.IntegrationConfig;
import com.edgareldy.springintegrationtutorial.entity.Order;
import com.edgareldy.springintegrationtutorial.entity.OrderStatus;
import com.edgareldy.springintegrationtutorial.service.OrderService;
import com.edgareldy.springintegrationtutorial.integration.error.OrderFailureRecordingAdvice;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.annotation.Transformer;
import org.springframework.integration.file.outbound.FileWritingMessageHandler;
import org.springframework.messaging.Message;

/**
 * The review-queue outbound channel: records that an order is waiting for review, then writes a review-queue
 * entry into the reviews directory (a file standing in for an email or a ticket), telling an administrator
 * how to resolve it.
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 */
// Same shape as ConfirmationOutboundAdapterConfig: status step, rendering, file writing, each behind its
// own channel (manual-review-channel, review-queue-channel, review-file-channel).
@Configuration
public class ReviewOutboundAdapterConfig {

    private final OrderService orderService;
    private final Path reviewsDirectory;

    /**
     * @param orderService     records the status
     * @param reviewsDirectory where review-queue entries go ({@code orders.directories.reviews})
     */
    public ReviewOutboundAdapterConfig(
            OrderService orderService,
            @Value("${orders.directories.reviews}") Path reviewsDirectory) {
        this.orderService = orderService;
        this.reviewsDirectory = reviewsDirectory;
    }

    /**
     * @param order an order the router found at or above the review threshold
     * @return the order with its status {@code PENDING_REVIEW}
     */
    // Steps after persistence: a failure is recorded (FAILED, dead letter) by orderFailureRecordingAdvice.
    @ServiceActivator(inputChannel = IntegrationConfig.MANUAL_REVIEW_CHANNEL,
            outputChannel = IntegrationConfig.REVIEW_QUEUE_CHANNEL,
            adviceChain = "orderFailureRecordingAdvice")
    public Order queueForReview(Order order) {
        return orderService.updateStatus(order.getId(), OrderStatus.PENDING_REVIEW);
    }

    /**
     * @param message an order pending review
     * @return the review-queue entry, with the two requests that resolve the order
     */
    @Transformer(inputChannel = IntegrationConfig.REVIEW_QUEUE_CHANNEL,
            outputChannel = IntegrationConfig.REVIEW_FILE_CHANNEL,
            adviceChain = "orderFailureRecordingAdvice")
    public Message<String> toReviewEntry(Message<Order> message) {
        Long orderId = message.getPayload().getId();
        return OrderFiles.render(message,
                "approve=POST /api/v1/orders/" + orderId + "/approve",
                "reject=POST /api/v1/orders/" + orderId + "/reject");
    }

    /**
     * @param orderFailureRecordingAdvice records a failure of the writer
     * @return the writer of the review-queue entries
     */
    // Replies with the written entry on review-written-channel, like the confirmation writer: for a line of a
    // bulk file, that reply becomes the line's pending review outcome.
    @Bean
    @ServiceActivator(inputChannel = IntegrationConfig.REVIEW_FILE_CHANNEL)
    public FileWritingMessageHandler reviewFileWriter(OrderFailureRecordingAdvice orderFailureRecordingAdvice) {
        FileWritingMessageHandler writer = OrderFiles.writer(reviewsDirectory, orderFailureRecordingAdvice);
        writer.setExpectReply(true);
        writer.setOutputChannelName(IntegrationConfig.REVIEW_WRITTEN_CHANNEL);
        return writer;
    }
}
