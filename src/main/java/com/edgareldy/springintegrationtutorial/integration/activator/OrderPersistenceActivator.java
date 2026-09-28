package com.edgareldy.springintegrationtutorial.integration.activator;

import com.edgareldy.springintegrationtutorial.config.IntegrationConfig;
import com.edgareldy.springintegrationtutorial.entity.Order;
import com.edgareldy.springintegrationtutorial.integration.message.OrderCommand;
import com.edgareldy.springintegrationtutorial.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.stereotype.Component;

/**
 * Persistence step of the intake flow: hands each {@link OrderCommand} to {@link OrderService}, whatever
 * source it came from, and emits the persisted order.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
@Component
@RequiredArgsConstructor
public class OrderPersistenceActivator {

    private final OrderService orderService;

    /**
     * @param command the normalized order
     * @return the persisted order, with its id, total snapshot and status {@code RECEIVED}
     */
    // A @ServiceActivator connects a channel to a plain bean method: the business logic (OrderService) knows
    // nothing about messages, and this thin adapter is the only thing the flow sees. A non-null return value
    // becomes the payload of the next message, sent to the output channel.
    // The output is the persisted order, not the command, because the next step, the content-based router,
    // decides on the stored total snapshot. Persistence therefore happens exactly once, here, whichever
    // branch the router then picks: every branch only updates the status of an order that already exists.
    // adviceChain names the advice beans wrapping this endpoint's handler: a transient database error is
    // retried by orderPersistenceRetryAdvice (IntegrationConfig) before it counts as a failure.
    @ServiceActivator(inputChannel = IntegrationConfig.ORDER_COMMAND_CHANNEL,
            outputChannel = IntegrationConfig.PERSISTED_ORDER_CHANNEL,
            adviceChain = "orderPersistenceRetryAdvice")
    public Order persist(OrderCommand command) {
        return orderService.receive(command);
    }
}
