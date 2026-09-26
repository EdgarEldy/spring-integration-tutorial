package com.edgareldy.springintegrationtutorial.entity;

/**
 * Where an order stands in the flow. Every terminal outcome of a persisted order is recorded as exactly
 * one status update.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
public enum OrderStatus {

    /** Persisted by the intake flow, not routed yet. */
    RECEIVED,

    /** Under the review threshold, confirmed without human intervention. */
    AUTO_CONFIRMED,

    /** At or above the review threshold, waiting for an administrator's decision. */
    PENDING_REVIEW,

    /** Approved by an administrator after review. */
    APPROVED,

    /** Rejected by an administrator after review. */
    REJECTED,

    /** Failed after it was persisted (for example while writing an outbound file). */
    FAILED
}
