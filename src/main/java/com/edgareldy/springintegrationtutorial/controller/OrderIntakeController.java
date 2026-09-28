package com.edgareldy.springintegrationtutorial.controller;

import com.edgareldy.springintegrationtutorial.dto.ApiResponse;
import com.edgareldy.springintegrationtutorial.dto.order.OrderIntakeRequest;
import com.edgareldy.springintegrationtutorial.integration.gateway.OrderIntakeGateway;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP entry point of the order flow: validates the request and hands it to {@link OrderIntakeGateway}.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderIntakeController {

    private final OrderIntakeGateway orderIntakeGateway;

    /**
     * @param request the order to submit
     * @return 202 with an empty success envelope once the flow has accepted the order
     */
    @Operation(summary = "Submit an order into the intake flow")
    @PostMapping("/intake")
    public ResponseEntity<ApiResponse<Void>> intake(@Valid @RequestBody OrderIntakeRequest request) {
        // No business logic here: the gateway is the only dependency, the flow does the rest. 202 Accepted
        // says the order entered the flow; what happens next (routing, review) is decided further down.
        orderIntakeGateway.submit(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(null, "Order accepted"));
    }
}
