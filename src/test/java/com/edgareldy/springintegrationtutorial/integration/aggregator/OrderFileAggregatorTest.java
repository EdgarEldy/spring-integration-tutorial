package com.edgareldy.springintegrationtutorial.integration.aggregator;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgareldy.springintegrationtutorial.integration.message.LineOutcome;
import com.edgareldy.springintegrationtutorial.integration.splitter.OrderFileSplitter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.integration.IntegrationMessageHeaderAccessor;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;

/**
 * Unit tests of {@link OrderFileAggregator}: its release strategy and the report it builds from a
 * complete group of line outcomes.
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 */
class OrderFileAggregatorTest {

    private final OrderFileAggregator aggregator = new OrderFileAggregator();

    @Test
    void _01_ShouldNotRelease_WhenSomeLinesOfTheFileHaveNoOutcomeYet() {
        List<Message<?>> group = new ArrayList<>(List.of(
                outcome(LineOutcome.autoConfirmed(1, "1,2,3", 10L), 3),
                outcome(LineOutcome.failed(2, "x", "bad"), 3)));

        assertThat(aggregator.allLinesAccountedFor(group)).isFalse();
    }

    @Test
    void _02_ShouldRelease_WhenEveryLineOfTheFileHasAnOutcome() {
        List<Message<?>> group = List.of(
                outcome(LineOutcome.autoConfirmed(1, "1,2,3", 10L), 3),
                outcome(LineOutcome.failed(2, "x", "bad"), 3),
                outcome(LineOutcome.pendingReview(3, "1,3,1", 11L), 3));

        assertThat(aggregator.allLinesAccountedFor(group)).isTrue();
    }

    @Test
    void _03_ShouldReleaseOnTheFirstOutcome_WhenTheFileHoldsASingleLine() {
        assertThat(aggregator.allLinesAccountedFor(List.of(outcome(LineOutcome.autoConfirmed(1, "1,2,3", 10L), 1))))
                .isTrue();
    }

    @Test
    void _04_ShouldCountEachResultAndListTheFailedLinesInFileOrder_WhenTheGroupIsAggregated() {
        String report = aggregator.aggregate(List.of(
                outcome(LineOutcome.failed(5, "9,9", "Malformed order line '9,9': expected customerId,productId,quantity"), 5),
                outcome(LineOutcome.autoConfirmed(1, "1,2,2", 10L), 5),
                outcome(LineOutcome.pendingReview(2, "1,3,1", 11L), 5),
                outcome(LineOutcome.autoConfirmed(4, "2,1,1", 12L), 5),
                outcome(LineOutcome.failed(3, "-1,2,1", "Customer with id -1 not found"), 5)));

        assertThat(report).isEqualTo("""
                Order file report
                Source file: orders.csv
                Lines: 5
                Auto-confirmed: 2
                Pending review: 1
                Failed: 2

                Failed lines:
                line 3 [-1,2,1]: Customer with id -1 not found
                line 5 [9,9]: Malformed order line '9,9': expected customerId,productId,quantity
                """);
    }

    @Test
    void _05_ShouldOmitTheFailedLinesSection_WhenNoLineFailed() {
        String report = aggregator.aggregate(List.of(outcome(LineOutcome.pendingReview(1, "1,3,1", 11L), 1)));

        assertThat(report).contains("Pending review: 1", "Failed: 0").doesNotContain("Failed lines:");
    }

    @Test
    void _06_ShouldNameAnUnknownSourceFile_WhenTheOutcomesCarryNoFileName() {
        Message<LineOutcome> anonymous = MessageBuilder.withPayload(LineOutcome.failed(1, "x", "bad"))
                .setHeader(IntegrationMessageHeaderAccessor.SEQUENCE_SIZE, 1).build();

        assertThat(aggregator.aggregate(List.of(anonymous))).contains("Source file: unknown");
    }

    @Test
    void _07_ShouldNameTheOrder_WhenALineFailedAfterItsOrderWasPersisted() {
        String report = aggregator.aggregate(List.of(outcome(LineOutcome.failed(1, "1,2,2", 42L, "Disk full"), 1)));

        assertThat(report).contains("line 1 [1,2,2]: (order 42) Disk full");
    }

    private static Message<LineOutcome> outcome(LineOutcome outcome, int sequenceSize) {
        return MessageBuilder.withPayload(outcome)
                .setHeader(OrderFileSplitter.SOURCE_FILE_HEADER, "orders.csv")
                .setHeader(IntegrationMessageHeaderAccessor.SEQUENCE_SIZE, sequenceSize)
                .build();
    }
}
