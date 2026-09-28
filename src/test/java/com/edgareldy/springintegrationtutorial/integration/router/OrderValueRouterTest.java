package com.edgareldy.springintegrationtutorial.integration.router;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgareldy.springintegrationtutorial.config.IntegrationConfig;
import com.edgareldy.springintegrationtutorial.entity.Order;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Unit tests of {@link OrderValueRouter}: the channel chosen on each side of the review threshold,
 * including the threshold itself.
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 */
class OrderValueRouterTest {

    private final OrderValueRouter router = new OrderValueRouter(new BigDecimal("1000.00"));

    @Test
    void _01_ShouldRouteToAutoConfirm_WhenTheTotalIsUnderTheThreshold() {
        assertThat(router.route(orderOf("999.99"))).isEqualTo(IntegrationConfig.AUTO_CONFIRM_CHANNEL);
    }

    @Test
    void _02_ShouldRouteToManualReview_WhenTheTotalEqualsTheThreshold() {
        assertThat(router.route(orderOf("1000.00"))).isEqualTo(IntegrationConfig.MANUAL_REVIEW_CHANNEL);
    }

    @Test
    void _03_ShouldRouteToManualReview_WhenTheTotalIsAboveTheThreshold() {
        assertThat(router.route(orderOf("1299.00"))).isEqualTo(IntegrationConfig.MANUAL_REVIEW_CHANNEL);
    }

    @Test
    void _04_ShouldCompareByValue_WhenTheTotalHasADifferentScaleThanTheThreshold() {
        // 1000 and 1000.00 are different BigDecimals for equals() but the same amount: compareTo decides.
        assertThat(router.route(orderOf("1000"))).isEqualTo(IntegrationConfig.MANUAL_REVIEW_CHANNEL);
    }

    private static Order orderOf(String total) {
        Order order = new Order();
        order.setTotal(new BigDecimal(total));
        return order;
    }
}
