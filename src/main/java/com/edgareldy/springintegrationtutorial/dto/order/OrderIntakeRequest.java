package com.edgareldy.springintegrationtutorial.dto.order;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * The raw REST payload of {@code POST /api/v1/orders/intake}: the same three fields a dropped CSV file
 * carries, as JSON. It travels unchanged through the gateway; the shared transformer turns it into an
 * {@code OrderCommand}.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 *
 * @param customerId the customer placing the order
 * @param productId  the ordered product
 * @param quantity   how many units, strictly positive
 */
public record OrderIntakeRequest(
        @NotNull(message = "customerId is required") Long customerId,
        @NotNull(message = "productId is required") Long productId,
        @NotNull(message = "quantity is required") @Positive(message = "quantity must be greater than 0") Integer quantity
) {
}
