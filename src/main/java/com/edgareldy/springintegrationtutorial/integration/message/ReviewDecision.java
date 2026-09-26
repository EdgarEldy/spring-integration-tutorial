package com.edgareldy.springintegrationtutorial.integration.message;

import com.edgareldy.springintegrationtutorial.entity.OrderStatus;

/**
 * An administrator's decision on an order pending review, as it travels from the review gateway to the
 * decision activator.
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 *
 * @param orderId the order being resolved
 * @param outcome {@code APPROVED} or {@code REJECTED}
 * @param reason  why the order was rejected, {@code null} for an approval
 */
public record ReviewDecision(Long orderId, OrderStatus outcome, String reason) {

    /**
     * Only the two statuses that end a review are valid outcomes.
     */
    public ReviewDecision {
        if (outcome != OrderStatus.APPROVED && outcome != OrderStatus.REJECTED) {
            throw new IllegalArgumentException("A review decision is APPROVED or REJECTED, not " + outcome);
        }
    }

    /**
     * @param orderId the order to approve
     * @return the approval
     */
    public static ReviewDecision approve(Long orderId) {
        return new ReviewDecision(orderId, OrderStatus.APPROVED, null);
    }

    /**
     * @param orderId the order to reject
     * @param reason  why it is rejected
     * @return the rejection
     */
    public static ReviewDecision reject(Long orderId, String reason) {
        return new ReviewDecision(orderId, OrderStatus.REJECTED, reason);
    }
}
