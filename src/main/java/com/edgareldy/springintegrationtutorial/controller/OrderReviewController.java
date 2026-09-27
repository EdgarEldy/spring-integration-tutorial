package com.edgareldy.springintegrationtutorial.controller;

import com.edgareldy.springintegrationtutorial.dto.ApiResponse;
import com.edgareldy.springintegrationtutorial.dto.order.RejectRequest;
import com.edgareldy.springintegrationtutorial.integration.gateway.OrderReviewGateway;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP entry point of the manual-review decision: an administrator approves or rejects an order pending
 * review through {@link OrderReviewGateway}. Access is restricted to {@code ADMIN} by {@code SecurityConfig}.
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderReviewController {

    private final OrderReviewGateway orderReviewGateway;

    /**
     * @param id the order pending review
     * @return 200 with an empty success envelope once the order is approved and its confirmation written
     */
    @Operation(summary = "Approve an order pending review")
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<Void>> approve(@PathVariable Long id) {
        orderReviewGateway.approve(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Order approved"));
    }

    /**
     * @param id      the order pending review
     * @param request the rejection reason
     * @return 200 with an empty success envelope once the order is rejected and its rejection file written
     */
    @Operation(summary = "Reject an order pending review")
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<Void>> reject(@PathVariable Long id, @Valid @RequestBody RejectRequest request) {
        orderReviewGateway.reject(id, request.reason());
        return ResponseEntity.ok(ApiResponse.success(null, "Order rejected"));
    }
}
