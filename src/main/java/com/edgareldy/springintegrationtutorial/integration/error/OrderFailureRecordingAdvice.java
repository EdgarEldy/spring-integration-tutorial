package com.edgareldy.springintegrationtutorial.integration.error;

import org.springframework.integration.handler.advice.AbstractRequestHandlerAdvice;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * Advice of the steps that run after persistence (status updates, file rendering, file writing): when one
 * of them fails, the failure is recorded on the spot through {@link OrderErrorHandler#recordFailure}
 * (order set to {@code FAILED}, dead-letter file), then rethrown as a {@link RecordedOrderFailureException}.
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 */
// A custom advice, next to the retry advice of the persistence step: it wraps the handler of each endpoint
// naming it in its adviceChain, like an around interceptor, and sees the message and the exception of that
// step only (for a reply-producing endpoint, sending the result on is outside the advice).
// It exists for the synchronous paths: an HTTP intake or a review decision runs the whole flow in the
// request thread, so errorChannel never sees its failures. Recording here, where the failed message still
// carries its order, and then rethrowing, gives the HTTP paths the same rule as the file path while the
// caller still gets its error (a generic 500).
// On the file path the rethrown exception travels up to the poller and on to errorChannel: OrderErrorHandler
// finds the RecordedOrderFailureException in the cause chain and does not record the failure a second time.
@Component
public class OrderFailureRecordingAdvice extends AbstractRequestHandlerAdvice {

    private final OrderErrorHandler orderErrorHandler;

    /**
     * @param orderErrorHandler applies the failure rule
     */
    public OrderFailureRecordingAdvice(OrderErrorHandler orderErrorHandler) {
        this.orderErrorHandler = orderErrorHandler;
    }

    @Override
    protected Object doInvoke(ExecutionCallback callback, Object target, Message<?> message) {
        try {
            return callback.execute();
        } catch (RuntimeException ex) {
            // The callback wraps checked exceptions in a holder exception: the recorded reason is the real one.
            Throwable failure = unwrapThrowableIfNecessary(ex);
            if (OrderErrorHandler.alreadyRecorded(failure)) {
                throw ex;
            }
            orderErrorHandler.recordFailure(message, failure);
            throw new RecordedOrderFailureException(failure);
        }
    }
}
