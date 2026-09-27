package com.edgareldy.springintegrationtutorial.integration.gateway;

import com.edgareldy.springintegrationtutorial.config.IntegrationConfig;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.messaging.Message;

/**
 * Mid-flow entry point sending one split line of an order file into the shared intake flow, with its own
 * error channel so a failing line is reported instead of stopping the rest of its file.
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 */
// A line enters the same intake channel as an HTTP order or a single-order file: the transformer,
// persistence and routing steps exist once and never learn they are processing a bulk file.
// What differs is the errorChannel attribute. The flow is synchronous, so a failing step throws back into
// this gateway; with an error channel set, the gateway catches that exception and sends an ErrorMessage
// (the exception as payload, the line message as original message) to the error channel instead of
// rethrowing it. The failure thus stays local to its line: the loop over the other lines of the file goes
// on, and the error flow turns the failure into a failed outcome for the aggregator. The HTTP gateway has no
// error channel, which is why an HTTP client keeps getting its 404 back as before.
@MessagingGateway(defaultRequestChannel = IntegrationConfig.INTAKE_CHANNEL,
        errorChannel = IntegrationConfig.ORDER_LINE_ERROR_CHANNEL)
public interface OrderLineGateway {

    /**
     * Runs one line through the shared flow. Returns normally whether the line succeeded or failed: a
     * failure has been handed to the error channel by then.
     *
     * @param line the split line, with its correlation and line headers, which the flow keeps
     */
    void submit(Message<String> line);
}
