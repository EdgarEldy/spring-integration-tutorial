package com.edgareldy.springintegrationtutorial.service;

import com.edgareldy.springintegrationtutorial.entity.Order;
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
}
