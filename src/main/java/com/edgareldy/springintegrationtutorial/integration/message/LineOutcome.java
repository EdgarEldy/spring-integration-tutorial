package com.edgareldy.springintegrationtutorial.integration.message;

/**
 * What happened to one line of a dropped order file: the per-line result the bulk aggregator collects
 * back together before writing the file's completion report.
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 *
 * @param lineNumber the line's position in the source file, counting from 1 (blank lines included)
 * @param line       the line's content, as read from the file
 * @param result     how the line ended
 * @param orderId    the order the line created, {@code null} for a failed line
 * @param reason     why the line failed, {@code null} otherwise
 */
public record LineOutcome(int lineNumber, String line, Result result, Long orderId, String reason) {

    /**
     * The three ways a line can end. A failed line created no order: it is not the {@code FAILED} status
     * of an order row, only the record, in the report, of an input that never became an order.
     */
    public enum Result {
        /** The order was persisted and confirmed automatically. */
        AUTO_CONFIRMED,
        /** The order was persisted and waits for an administrator's decision. */
        PENDING_REVIEW,
        /** The line was rejected (malformed, unknown customer or product): no order exists. */
        FAILED
    }

    /**
     * @param lineNumber the line's position in the file
     * @param line       the line's content
     * @param orderId    the auto-confirmed order
     * @return the outcome of a line whose order was confirmed automatically
     */
    public static LineOutcome autoConfirmed(int lineNumber, String line, Long orderId) {
        return new LineOutcome(lineNumber, line, Result.AUTO_CONFIRMED, orderId, null);
    }

    /**
     * @param lineNumber the line's position in the file
     * @param line       the line's content
     * @param orderId    the order waiting for review
     * @return the outcome of a line whose order waits for a manual review
     */
    public static LineOutcome pendingReview(int lineNumber, String line, Long orderId) {
        return new LineOutcome(lineNumber, line, Result.PENDING_REVIEW, orderId, null);
    }

    /**
     * @param lineNumber the line's position in the file
     * @param line       the line's content
     * @param reason     why the line was rejected
     * @return the outcome of a line that created no order
     */
    public static LineOutcome failed(int lineNumber, String line, String reason) {
        return new LineOutcome(lineNumber, line, Result.FAILED, null, reason);
    }
}
