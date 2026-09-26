package com.edgareldy.springintegrationtutorial.service;

import com.edgareldy.springintegrationtutorial.entity.Order;
import com.edgareldy.springintegrationtutorial.entity.OrderStatus;
import com.edgareldy.springintegrationtutorial.integration.message.OrderCommand;

/**
 * Business operations on orders, called by the integration flow's service activators.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
public interface OrderService {

    /**
     * Persists a new order: checks that the customer and the product exist, takes the total snapshot
     * ({@code quantity * unitPrice}) and saves the order with the status {@code RECEIVED} and the
     * command's source.
     *
     * @param command the normalized order
     * @return the persisted order
     * @throws com.edgareldy.springintegrationtutorial.exception.ResourceNotFoundException if the customer
     *                                                                                      or the product does not exist
     */
    Order receive(OrderCommand command);

    /**
     * Records the outcome the flow gave an existing order (auto-confirmed, pending review).
     *
     * @param orderId the order
     * @param status  its new status
     * @return the updated order
     * @throws com.edgareldy.springintegrationtutorial.exception.ResourceNotFoundException if the order
     *                                                                                      does not exist
     */
    Order updateStatus(Long orderId, OrderStatus status);

    /**
     * Resolves an order waiting for review with an administrator's decision. An order is resolved once:
     * the status only changes if it is still {@code PENDING_REVIEW}.
     *
     * @param orderId the order
     * @param outcome {@code APPROVED} or {@code REJECTED}
     * @return the resolved order
     * @throws com.edgareldy.springintegrationtutorial.exception.ResourceNotFoundException if the order
     *                                                                                      does not exist
     * @throws com.edgareldy.springintegrationtutorial.exception.BusinessRuleException     if the order is
     *                                                                                      not pending review
     * @throws IllegalArgumentException if the outcome is neither {@code APPROVED} nor {@code REJECTED}
     */
    Order resolveReview(Long orderId, OrderStatus outcome);
}
