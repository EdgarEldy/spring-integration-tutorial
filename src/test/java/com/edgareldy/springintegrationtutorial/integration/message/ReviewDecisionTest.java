package com.edgareldy.springintegrationtutorial.integration.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edgareldy.springintegrationtutorial.entity.OrderStatus;
import org.junit.jupiter.api.Test;

/**
 * Unit tests of {@link ReviewDecision}: its two factories and the refusal of any other outcome.
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 */
class ReviewDecisionTest {

    @Test
    void _01_ShouldCarryAnApprovalWithoutReason_WhenCreatedByApprove() {
        assertThat(ReviewDecision.approve(3L)).isEqualTo(new ReviewDecision(3L, OrderStatus.APPROVED, null));
    }

    @Test
    void _02_ShouldCarryTheReason_WhenCreatedByReject() {
        assertThat(ReviewDecision.reject(3L, "Duplicate")).isEqualTo(new ReviewDecision(3L, OrderStatus.REJECTED, "Duplicate"));
    }

    @Test
    void _03_ShouldRefuseTheDecision_WhenTheOutcomeDoesNotEndAReview() {
        assertThatThrownBy(() -> new ReviewDecision(3L, OrderStatus.AUTO_CONFIRMED, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
