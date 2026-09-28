package com.edgareldy.springintegrationtutorial.dto;

import java.time.Instant;

/**
 * Standard envelope returned by every HTTP endpoint, including the synchronous entry points into
 * the integration flows, for successes and errors alike.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 *
 * @param success   whether the request succeeded
 * @param message   a human readable message
 * @param data      the payload, {@code null} on most errors
 * @param timestamp when the response was built
 * @param <T>       payload type
 */
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        Instant timestamp
) {

    /**
     * Builds a success envelope stamped with the current instant.
     *
     * @param data    the payload
     * @param message a human readable message
     * @param <T>     payload type
     * @return the success envelope
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, message, data, Instant.now());
    }

    /**
     * Builds an error envelope without payload, stamped with the current instant.
     *
     * @param message what went wrong
     * @param <T>     payload type
     * @return the error envelope
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, Instant.now());
    }
}
