package com.edgareldy.springintegrationtutorial.integration.gateway;

import com.edgareldy.springintegrationtutorial.config.IntegrationConfig;
import com.edgareldy.springintegrationtutorial.dto.order.OrderIntakeRequest;
import org.springframework.integration.annotation.MessagingGateway;

/**
 * Entry point from regular application code (the intake controller) into the order flow.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
// @MessagingGateway turns a plain interface into a messaging entry point: Spring Integration generates the
// implementation, which wraps the argument into a Message and sends it to the default request channel. The
// caller depends on a Java method, not on the messaging API, and never learns which channels or endpoints
// sit behind it.
// The gateway carries the raw REST payload on purpose: converting it into an OrderCommand is the shared
// transformer's job, for this source and the file source alike.
// Because intake-channel is a DirectChannel, the whole flow runs inside submit(): when a step throws, the
// generated implementation walks the cause chain of the MessagingException wrapping it and rethrows the
// first application exception it finds (a ResourceNotFoundException, for instance), so the controller sees
// the original exception, not a messaging one.
@MessagingGateway(defaultRequestChannel = IntegrationConfig.INTAKE_CHANNEL)
public interface OrderIntakeGateway {

    /**
     * Sends one order into the flow and returns once it has been persisted. There is no reply: the method
     * returns void, so the gateway only sends.
     *
     * @param request the raw REST payload
     */
    void submit(OrderIntakeRequest request);
}
