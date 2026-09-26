package com.edgareldy.springintegrationtutorial.integration.activator;

import com.edgareldy.springintegrationtutorial.config.IntegrationConfig;
import com.edgareldy.springintegrationtutorial.entity.Order;
import com.edgareldy.springintegrationtutorial.integration.message.OrderCommand;
import com.edgareldy.springintegrationtutorial.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.context.IntegrationContextUtils;
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
    // The output is the persisted order, not the command, because the next step (the content-based router
    // of the routing feature) decides on the stored total snapshot. Until that router exists, the order is
    // sent to nullChannel, a channel Spring Integration always registers that silently discards every
    // message: without an output channel, the endpoint would fail looking for a reply channel that neither
    // the gateway (its method returns void) nor the file adapter ever provides.
    @ServiceActivator(inputChannel = IntegrationConfig.ORDER_COMMAND_CHANNEL,
            outputChannel = IntegrationContextUtils.NULL_CHANNEL_BEAN_NAME)
    public Order persist(OrderCommand command) {
        return orderService.receive(command);
    }
}
