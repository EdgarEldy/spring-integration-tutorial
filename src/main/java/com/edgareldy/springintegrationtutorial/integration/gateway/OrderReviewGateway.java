package com.edgareldy.springintegrationtutorial.integration.gateway;

import com.edgareldy.springintegrationtutorial.config.IntegrationConfig;
import org.springframework.integration.annotation.Gateway;
import org.springframework.integration.annotation.MessagingGateway;

/**
 * Entry point from the review controller into the flow: turns an administrator's approval or rejection
 * into a {@code ReviewDecision} message, which closes out the manual-review path.
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 */
// Unlike the intake gateway, whose single argument becomes the payload as is, these methods take plain
// arguments that must be assembled into one payload. @Gateway(payloadExpression) does that with a SpEL
// expression evaluated on every call, whose root object exposes the method arguments as "args": the
// controller keeps calling a plain Java method, and the ReviewDecision type stays a detail of the flow.
// review-decision-channel is a DirectChannel, so the decision is resolved inside the call, and a 404 or a
// 422 thrown by the decision step reaches the controller as the original exception.
@MessagingGateway(defaultRequestChannel = IntegrationConfig.REVIEW_DECISION_CHANNEL)
public interface OrderReviewGateway {

    /**
     * Approves an order pending review; returns once the confirmation file is written.
     *
     * @param orderId the order
     */
    @Gateway(payloadExpression =
            "T(com.edgareldy.springintegrationtutorial.integration.message.ReviewDecision).approve(args[0])")
    void approve(Long orderId);

    /**
     * Rejects an order pending review; returns once the rejection file is written.
     *
     * @param orderId the order
     * @param reason  why it is rejected
     */
    @Gateway(payloadExpression =
            "T(com.edgareldy.springintegrationtutorial.integration.message.ReviewDecision).reject(args[0], args[1])")
    void reject(Long orderId, String reason);
}
