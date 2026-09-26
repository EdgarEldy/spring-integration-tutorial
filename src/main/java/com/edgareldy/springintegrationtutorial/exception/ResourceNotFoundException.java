package com.edgareldy.springintegrationtutorial.exception;

/**
 * Thrown when a referenced resource (customer, product, order...) does not exist; translated into a
 * 404 by {@link GlobalExceptionHandler} when it reaches the HTTP layer.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * @param message what could not be found
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }

    /**
     * Builds the standard "Resource with id X not found" message.
     *
     * @param resource the resource name, e.g. "Product"
     * @param id       the identifier that was looked up
     * @return the exception
     */
    public static ResourceNotFoundException of(String resource, Object id) {
        return new ResourceNotFoundException(resource + " with id " + id + " not found");
    }
}
