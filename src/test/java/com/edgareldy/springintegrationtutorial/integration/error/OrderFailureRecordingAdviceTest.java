package com.edgareldy.springintegrationtutorial.integration.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.handler.AbstractReplyProducingMessageHandler;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;

/**
 * Unit tests of {@link OrderFailureRecordingAdvice} applied to a small reply-producing handler, without any
 * Spring context: a failing step is recorded once and rethrown as a recorded failure, a successful one is
 * left alone.
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 */
class OrderFailureRecordingAdviceTest {

    private final OrderErrorHandler orderErrorHandler = mock(OrderErrorHandler.class);

    private final QueueChannel output = new QueueChannel();

    private final Message<String> message = MessageBuilder.withPayload("order").build();

    @Test
    void _01_ShouldRecordTheFailureThenRethrowItAsRecorded_WhenTheAdvisedStepFails() {
        IllegalStateException failure = new IllegalStateException("boom");

        Throwable thrown = catchThrowable(() -> advised(payload -> {
            throw failure;
        }).handleMessage(message));

        verify(orderErrorHandler).recordFailure(same(message), same(failure));
        assertThat(OrderErrorHandler.alreadyRecorded(thrown)).isTrue();
        assertThat(output.receive(0)).isNull();
    }

    @Test
    void _02_ShouldPassTheResultOnWithoutRecordingAnything_WhenTheAdvisedStepSucceeds() {
        advised(payload -> payload + " handled").handleMessage(message);

        assertThat(output.receive(0).getPayload()).isEqualTo("order handled");
        verify(orderErrorHandler, never()).recordFailure(any(), any());
    }

    @Test
    void _03_ShouldNotRecordTheFailureAgain_WhenItWasAlreadyRecordedFurtherDown() {
        Throwable thrown = catchThrowable(() -> advised(payload -> {
            throw new RecordedOrderFailureException(new IllegalStateException("boom"));
        }).handleMessage(message));

        verify(orderErrorHandler, never()).recordFailure(any(), any());
        assertThat(OrderErrorHandler.alreadyRecorded(thrown)).isTrue();
    }

    private AbstractReplyProducingMessageHandler advised(Function<Object, Object> step) {
        AbstractReplyProducingMessageHandler handler = new AbstractReplyProducingMessageHandler() {

            @Override
            protected Object handleRequestMessage(Message<?> requestMessage) {
                return step.apply(requestMessage.getPayload());
            }
        };
        handler.setOutputChannel(output);
        handler.setAdviceChain(List.of(new OrderFailureRecordingAdvice(orderErrorHandler)));
        handler.setBeanFactory(new DefaultListableBeanFactory());
        handler.afterPropertiesSet();
        return handler;
    }
}
