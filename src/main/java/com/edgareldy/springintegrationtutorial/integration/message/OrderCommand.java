package com.edgareldy.springintegrationtutorial.integration.message;

import com.edgareldy.springintegrationtutorial.entity.OrderSource;

/**
 * The normalized order travelling through the flow once the shared transformer has converted the raw
 * input, whatever its source: from here on, only {@link #source()} still tells where it came from.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 *
 * @param customerId the referenced customer
 * @param productId  the referenced product
 * @param quantity   how many units, always positive
 * @param source     the inbound channel the order arrived through
 */
// A message payload should be immutable, like the Message envelope carrying it: the same payload may
// be read by several endpoints, and none of them should be able to change it under the others' feet.
// A record gives that for free.
public record OrderCommand(Long customerId, Long productId, int quantity, OrderSource source) {
}
