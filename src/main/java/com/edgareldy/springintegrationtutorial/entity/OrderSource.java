package com.edgareldy.springintegrationtutorial.entity;

/**
 * The inbound channel an order arrived through, recorded on the order by the shared transformer.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
public enum OrderSource {

    /** Submitted through the HTTP gateway ({@code POST /api/v1/orders/intake}). */
    API,

    /** Dropped as a CSV file into the watched incoming directory. */
    FILE
}
