package com.edgareldy.springintegrationtutorial.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edgareldy.springintegrationtutorial.TestcontainersConfiguration;
import com.edgareldy.springintegrationtutorial.config.IntegrationConfig;
import com.edgareldy.springintegrationtutorial.dto.order.OrderIntakeRequest;
import com.edgareldy.springintegrationtutorial.entity.Order;
import com.edgareldy.springintegrationtutorial.entity.OrderSource;
import com.edgareldy.springintegrationtutorial.exception.ResourceNotFoundException;
import com.edgareldy.springintegrationtutorial.integration.gateway.OrderIntakeGateway;
import com.edgareldy.springintegrationtutorial.integration.message.OrderCommand;
import com.edgareldy.springintegrationtutorial.support.CommerceTestData;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.integration.test.context.MockIntegrationContext;
import org.springframework.integration.test.context.SpringIntegrationTest;
import org.springframework.integration.test.mock.MockIntegration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandlingException;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.context.ActiveProfiles;

/**
 * Tests the transformer and the persistence activator of the intake flow in isolation, inside the real
 * application context, plus the way the gateway hands a flow exception back to its caller.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
// @SpringIntegrationTest registers a MockIntegrationContext: it can swap the handler of any endpoint for a
// mock (and restore the real one afterwards), so one step of a flow is tested while the steps after it are
// cut off, without writing a special test configuration or rewiring any channel.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringIntegrationTest
class OrderIntakeFlowTest {

    // Bean name Spring Integration gives the endpoint of an annotated method: <bean>.<method>.<annotation>.
    private static final String PERSISTENCE_ENDPOINT = "orderPersistenceActivator.persist.serviceActivator";

    private static final String ROUTER_ENDPOINT = "orderValueRouter.route.router";

    @Autowired
    private MockIntegrationContext mockIntegrationContext;

    @Autowired
    @Qualifier(IntegrationConfig.INTAKE_CHANNEL)
    private MessageChannel intakeChannel;

    @Autowired
    @Qualifier(IntegrationConfig.ORDER_COMMAND_CHANNEL)
    private MessageChannel orderCommandChannel;

    @Autowired
    private OrderIntakeGateway orderIntakeGateway;

    @Autowired
    private JdbcTemplate jdbc;

    private CommerceTestData data;

    @BeforeEach
    void setUp() {
        data = new CommerceTestData(jdbc);
    }

    @AfterEach
    void restoreRealEndpoints() {
        mockIntegrationContext.resetBeans();
    }

    @Test
    void _01_ShouldSendAnApiCommandToThePersistenceStep_WhenARestRequestReachesTheIntakeChannel() {
        ArgumentCaptor<Message<?>> captor = replacePersistenceWithACaptor();

        intakeChannel.send(new GenericMessage<>(new OrderIntakeRequest(10L, 20L, 3)));

        assertThat(captor.getValue().getPayload()).isEqualTo(new OrderCommand(10L, 20L, 3, OrderSource.API));
    }

    @Test
    void _02_ShouldSendAFileCommandToThePersistenceStep_WhenACsvLineReachesTheIntakeChannel() {
        ArgumentCaptor<Message<?>> captor = replacePersistenceWithACaptor();

        intakeChannel.send(new GenericMessage<>("11,21,4"));

        assertThat(captor.getValue().getPayload()).isEqualTo(new OrderCommand(11L, 21L, 4, OrderSource.FILE));
    }

    @Test
    void _03_ShouldPersistAReceivedOrderAndPassItToTheRouter_WhenACommandReachesThePersistenceStep() {
        long customerId = data.customer();
        long productId = data.product("12.50");
        // The router is cut off, so the order keeps the status the persistence step gave it.
        ArgumentCaptor<Message<?>> captor = replaceWithACaptor(ROUTER_ENDPOINT);

        orderCommandChannel.send(new GenericMessage<>(new OrderCommand(customerId, productId, 4, OrderSource.FILE)));

        Map<String, Object> order = data.singleOrderOf(customerId);
        assertThat(order).containsEntry("product_id", productId).containsEntry("quantity", 4)
                .containsEntry("source", "FILE").containsEntry("status", "RECEIVED");
        assertThat((BigDecimal) order.get("total")).isEqualByComparingTo("50.00");
        assertThat(captor.getValue().getPayload()).isInstanceOfSatisfying(Order.class,
                persisted -> assertThat(persisted.getId()).isEqualTo(order.get("id")));
    }

    @Test
    void _04_ShouldFailWithTheNotFoundCauseAndPersistNothing_WhenTheCustomerOfTheCommandIsUnknown() {
        long productId = data.product("12.50");

        // A step called through a channel reports its failure wrapped in a MessagingException that tells
        // which message failed; the original exception is its cause.
        assertThatThrownBy(() -> orderCommandChannel.send(
                new GenericMessage<>(new OrderCommand(-1L, productId, 1, OrderSource.API))))
                .isInstanceOf(MessageHandlingException.class)
                .hasCauseInstanceOf(ResourceNotFoundException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders WHERE product_id = ?", Integer.class, productId))
                .isZero();
    }

    @Test
    void _05_ShouldRethrowTheOriginalNotFoundException_WhenTheGatewayCallsTheFlowWithAnUnknownProduct() {
        long customerId = data.customer();

        // Through the gateway, the same failure comes back unwrapped: the controller, and therefore the
        // GlobalExceptionHandler, gets the ResourceNotFoundException itself.
        assertThatThrownBy(() -> orderIntakeGateway.submit(new OrderIntakeRequest(customerId, -1L, 1)))
                .isExactlyInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product with id -1 not found");
        assertThat(data.orderCount(customerId)).isZero();
    }

    private ArgumentCaptor<Message<?>> replacePersistenceWithACaptor() {
        return replaceWithACaptor(PERSISTENCE_ENDPOINT);
    }

    private ArgumentCaptor<Message<?>> replaceWithACaptor(String endpoint) {
        ArgumentCaptor<Message<?>> captor = MockIntegration.messageArgumentCaptor();
        // The mock captures the message and does nothing else: nothing is handled, nothing is sent on.
        mockIntegrationContext.substituteMessageHandlerFor(endpoint,
                MockIntegration.mockMessageHandler(captor).handleNext(message -> { }));
        return captor;
    }
}
