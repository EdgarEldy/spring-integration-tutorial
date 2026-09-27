package com.edgareldy.springintegrationtutorial.integration.activator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.edgareldy.springintegrationtutorial.config.IntegrationConfig;
import com.edgareldy.springintegrationtutorial.entity.Order;
import com.edgareldy.springintegrationtutorial.entity.OrderStatus;
import com.edgareldy.springintegrationtutorial.exception.BusinessRuleException;
import com.edgareldy.springintegrationtutorial.exception.ResourceNotFoundException;
import com.edgareldy.springintegrationtutorial.integration.message.ReviewDecision;
import com.edgareldy.springintegrationtutorial.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

/**
 * Unit tests of {@link ReviewDecisionActivator} with a mocked service and mocked channels: which adapter
 * each decision is forwarded to, and nothing forwarded when the decision is refused.
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 */
@ExtendWith(MockitoExtension.class)
class ReviewDecisionActivatorTest {

    @Mock
    private OrderService orderService;

    @Mock
    private MessageChannel confirmationChannel;

    @Mock
    private MessageChannel rejectionChannel;

    private ReviewDecisionActivator activator;

    @BeforeEach
    void setUp() {
        activator = new ReviewDecisionActivator(orderService, confirmationChannel, rejectionChannel);
    }

    @Test
    void _01_ShouldForwardTheApprovedOrderToTheConfirmationAdapter_WhenTheDecisionIsAnApproval() {
        Order approved = order(OrderStatus.APPROVED);
        when(orderService.resolveReview(7L, OrderStatus.APPROVED)).thenReturn(approved);

        activator.resolve(ReviewDecision.approve(7L));

        assertThat(sentTo(confirmationChannel).getPayload()).isSameAs(approved);
        verifyNoInteractions(rejectionChannel);
    }

    @Test
    void _02_ShouldForwardTheRejectedOrderWithItsReasonToTheRejectionAdapter_WhenTheDecisionIsARejection() {
        Order rejected = order(OrderStatus.REJECTED);
        when(orderService.resolveReview(7L, OrderStatus.REJECTED)).thenReturn(rejected);

        activator.resolve(ReviewDecision.reject(7L, "Out of stock"));

        Message<?> message = sentTo(rejectionChannel);
        assertThat(message.getPayload()).isSameAs(rejected);
        assertThat(message.getHeaders()).containsEntry(IntegrationConfig.REJECTION_REASON_HEADER, "Out of stock");
        verifyNoInteractions(confirmationChannel);
    }

    @Test
    void _03_ShouldForwardNothing_WhenTheOrderIsNotPendingReview() {
        when(orderService.resolveReview(7L, OrderStatus.APPROVED))
                .thenThrow(new BusinessRuleException("Order with id 7 is not pending review (status APPROVED)"));

        assertThatThrownBy(() -> activator.resolve(ReviewDecision.approve(7L)))
                .isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(confirmationChannel, rejectionChannel);
    }

    @Test
    void _04_ShouldForwardNothing_WhenTheOrderDoesNotExist() {
        when(orderService.resolveReview(7L, OrderStatus.REJECTED)).thenThrow(ResourceNotFoundException.of("Order", 7L));

        assertThatThrownBy(() -> activator.resolve(ReviewDecision.reject(7L, "Unknown")))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(confirmationChannel, rejectionChannel);
    }

    private static Message<?> sentTo(MessageChannel channel) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Message<?>> captor = ArgumentCaptor.forClass((Class<Message<?>>) (Class<?>) Message.class);
        verify(channel).send(captor.capture());
        return captor.getValue();
    }

    private static Order order(OrderStatus status) {
        Order order = new Order();
        order.setId(7L);
        order.setStatus(status);
        return order;
    }
}
