package com.edgareldy.springintegrationtutorial.dto.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/orders/{id}/reject}: why the administrator rejects the order.
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 *
 * @param reason the rejection reason, written into the rejection file
 */
public record RejectRequest(
        @NotBlank(message = "reason is required")
        @Size(max = 500, message = "reason must be at most 500 characters") String reason
) {
}
