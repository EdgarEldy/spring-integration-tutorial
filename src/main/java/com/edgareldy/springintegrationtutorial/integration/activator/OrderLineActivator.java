package com.edgareldy.springintegrationtutorial.integration.activator;

import com.edgareldy.springintegrationtutorial.config.IntegrationConfig;
import com.edgareldy.springintegrationtutorial.integration.gateway.OrderLineGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * Hands each split line of an order file to {@link OrderLineGateway}, which runs it through the shared
 * intake flow and keeps a failure local to that line.
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 */
@Component
@RequiredArgsConstructor
public class OrderLineActivator {

    private final OrderLineGateway orderLineGateway;

    /**
     * @param line one line of a file, as emitted by the splitter
     */
    // The splitter sends its parts to a channel, and a gateway is called from code: this activator is the
    // bridge between the two. It returns nothing, so the endpoint sends nothing on; each line's outcome
    // reaches the aggregator from the end of the flow it went through (or from the line error flow).
    @ServiceActivator(inputChannel = IntegrationConfig.ORDER_LINE_CHANNEL)
    public void dispatch(Message<String> line) {
        orderLineGateway.submit(line);
    }
}
