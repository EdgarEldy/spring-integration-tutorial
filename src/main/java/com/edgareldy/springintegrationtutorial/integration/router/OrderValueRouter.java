package com.edgareldy.springintegrationtutorial.integration.router;

import com.edgareldy.springintegrationtutorial.config.IntegrationConfig;
import com.edgareldy.springintegrationtutorial.entity.Order;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.annotation.Router;
import org.springframework.stereotype.Component;

/**
 * Content-based router of the order flow: sends each persisted order to the auto-confirm path or to the
 * manual-review path, depending on its stored total.
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 */
@Component
public class OrderValueRouter {

    private final BigDecimal reviewThreshold;

    /**
     * @param reviewThreshold total from which an order needs a human review ({@code orders.review-threshold})
     */
    public OrderValueRouter(@Value("${orders.review-threshold}") BigDecimal reviewThreshold) {
        this.reviewThreshold = reviewThreshold;
    }

    /**
     * @param order the persisted order, carrying its total snapshot
     * @return the name of the channel the order continues on
     */
    // A @Router endpoint does not transform or handle the message: it only decides where it goes next.
    // Returning a channel name is enough, Spring Integration resolves it to the channel bean of that name
    // and sends the unchanged message there. This is a content-based router: the decision reads the
    // payload itself (the total stored when the order was persisted, never recomputed from the current
    // product price), so the routing logic is a single comparison and is tested without any channel.
    @Router(inputChannel = IntegrationConfig.PERSISTED_ORDER_CHANNEL)
    public String route(Order order) {
        return order.getTotal().compareTo(reviewThreshold) < 0
                ? IntegrationConfig.AUTO_CONFIRM_CHANNEL
                : IntegrationConfig.MANUAL_REVIEW_CHANNEL;
    }
}
