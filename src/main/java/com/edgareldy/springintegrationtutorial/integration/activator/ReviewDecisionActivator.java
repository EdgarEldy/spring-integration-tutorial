package com.edgareldy.springintegrationtutorial.integration.activator;

import com.edgareldy.springintegrationtutorial.config.IntegrationConfig;
import com.edgareldy.springintegrationtutorial.entity.Order;
import com.edgareldy.springintegrationtutorial.entity.OrderStatus;
import com.edgareldy.springintegrationtutorial.integration.message.ReviewDecision;
import com.edgareldy.springintegrationtutorial.service.OrderService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Component;

/**
 * Closes out the manual-review path: resolves an order pending review with the administrator's decision,
 * then forwards it to the confirmation adapter (approved) or the rejection adapter (rejected).
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 */
@Component
public class ReviewDecisionActivator {

    private final OrderService orderService;
    private final MessageChannel confirmationChannel;
    private final MessageChannel rejectionChannel;

    /**
     * @param orderService        resolves the order
     * @param confirmationChannel input of the confirmation file adapter
     * @param rejectionChannel    input of the rejection file adapter
     */
    public ReviewDecisionActivator(
            OrderService orderService,
            @Qualifier(IntegrationConfig.CONFIRMATION_CHANNEL) MessageChannel confirmationChannel,
            @Qualifier(IntegrationConfig.REJECTION_CHANNEL) MessageChannel rejectionChannel) {
        this.orderService = orderService;
        this.confirmationChannel = confirmationChannel;
        this.rejectionChannel = rejectionChannel;
    }

    /**
     * @param decision the administrator's decision
     * @throws com.edgareldy.springintegrationtutorial.exception.ResourceNotFoundException if the order
     *                                                                                      does not exist
     * @throws com.edgareldy.springintegrationtutorial.exception.BusinessRuleException     if the order is
     *                                                                                      not pending review
     */
    // OrderService checks and records the decision in one step (404 for an unknown order, 422 for one that is
    // not PENDING_REVIEW, so an order is resolved only once). The method returns nothing, so the endpoint
    // produces no reply; instead it sends the resolved order itself to the adapter matching the decision.
    // The target depends on the decision, not on a fixed output channel, and the two adapters are reached
    // through their input channels, exactly like the router reaches them: the confirmation adapter is reused
    // as is, without knowing whether the order was approved or auto-confirmed.
    @ServiceActivator(inputChannel = IntegrationConfig.REVIEW_DECISION_CHANNEL)
    public void resolve(ReviewDecision decision) {
        Order order = orderService.resolveReview(decision.orderId(), decision.outcome());
        if (decision.outcome() == OrderStatus.APPROVED) {
            confirmationChannel.send(MessageBuilder.withPayload(order).build());
        } else {
            rejectionChannel.send(MessageBuilder.withPayload(order)
                    .setHeader(IntegrationConfig.REJECTION_REASON_HEADER, decision.reason())
                    .build());
        }
    }
}
