package com.edgareldy.springintegrationtutorial.exception;

/**
 * Thrown when a request is well formed but violates a business rule (e.g. resolving an order that is
 * not pending review); translated into a 422 by {@link GlobalExceptionHandler}.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
public class BusinessRuleException extends RuntimeException {

    /**
     * @param message the rule that was violated
     */
    public BusinessRuleException(String message) {
        super(message);
    }
}
