package com.edgareldy.springintegrationtutorial.integration.error;

/**
 * Thrown by {@link OrderFailureRecordingAdvice} once a failure after persistence has been recorded (order
 * set to {@code FAILED}, dead-letter file written): whoever catches it further up knows there is nothing
 * left to record.
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 */
// A plain RuntimeException on purpose, not a MessagingException: the generated gateway rethrows the first
// non-messaging runtime exception of the chain, so the HTTP caller receives this one (a generic 500 through
// GlobalExceptionHandler) whatever the underlying cause, instead of a status derived from a technical cause.
public class RecordedOrderFailureException extends RuntimeException {

    /**
     * @param cause the failure that was recorded
     */
    public RecordedOrderFailureException(Throwable cause) {
        super("Order failure recorded (status FAILED when the order exists, dead-letter file written)", cause);
    }
}
