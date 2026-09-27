package com.edgareldy.springintegrationtutorial.integration.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.edgareldy.springintegrationtutorial.config.IntegrationConfig;
import com.edgareldy.springintegrationtutorial.entity.Order;
import com.edgareldy.springintegrationtutorial.entity.OrderSource;
import com.edgareldy.springintegrationtutorial.entity.OrderStatus;
import com.edgareldy.springintegrationtutorial.exception.ResourceNotFoundException;
import com.edgareldy.springintegrationtutorial.integration.message.OrderCommand;
import com.edgareldy.springintegrationtutorial.integration.splitter.OrderFileSplitter;
import com.edgareldy.springintegrationtutorial.service.OrderService;
import java.io.File;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.file.FileHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.transformer.MessageTransformationException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandlingException;
import org.springframework.messaging.support.ErrorMessage;

/**
 * Unit tests of the failure rule of {@link OrderErrorHandler}, without any Spring context: the dead letter
 * it sends (content and file name) and the {@code FAILED} status it sets when the failed message
 * identifies an order.
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 */
class OrderErrorHandlerTest {

    private final OrderService orderService = mock(OrderService.class);

    // A QueueChannel keeps what is sent to it, so the test can take the dead letter back.
    private final QueueChannel deadLetters = new QueueChannel();

    private final OrderErrorHandler handler = new OrderErrorHandler(orderService, deadLetters);

    @Test
    void _01_ShouldKeepTheRawLineAndTheRootCause_WhenTheTransformerRejectedAFileLine() {
        Message<String> failed = fileMessage("abc\n", "bad.csv");
        handler.handle(new ErrorMessage(new MessageTransformationException(failed, "Failed to transform",
                new IllegalArgumentException("Malformed order line: abc"))));

        Message<?> deadLetter = nextDeadLetter();
        assertThat((String) deadLetter.getPayload())
                .startsWith("source-file: bad.csv\norder-id: none\nfailed-at: ")
                .contains("\nreason: IllegalArgumentException: Malformed order line: abc\n")
                .endsWith("\npayload:\nabc\n");
        assertThat(deadLetter.getHeaders().get(FileHeaders.FILENAME, String.class))
                .startsWith("bad.csv.").endsWith(OrderErrorHandler.DEAD_LETTER_EXTENSION);
        verifyNoInteractions(orderService);
    }

    @Test
    void _02_ShouldWriteTheCommandBackAsACsvLineWithoutAnyStatus_WhenPersistenceFailed() {
        Message<OrderCommand> failed = MessageBuilder.withPayload(new OrderCommand(4L, 5L, 2, OrderSource.FILE))
                .setHeader(FileHeaders.ORIGINAL_FILE, new File("processed/order-4.csv")).build();
        handler.handle(new ErrorMessage(
                new MessageHandlingException(failed, ResourceNotFoundException.of("Customer", 4L))));

        assertThat((String) nextDeadLetter().getPayload())
                .contains("\norder-id: none\n")
                .contains("\nreason: ResourceNotFoundException: Customer with id 4 not found\n")
                .endsWith("\npayload:\n4,5,2\n");
        verifyNoInteractions(orderService);
    }

    @Test
    void _03_ShouldReportTheDatabaseError_WhenTheRetriesOfATransientErrorAreExhausted() {
        Message<OrderCommand> failed = MessageBuilder.withPayload(new OrderCommand(4L, 5L, 2, OrderSource.FILE))
                .setHeader(FileHeaders.ORIGINAL_FILE, new File("processed/order-4.csv")).build();
        handler.handle(new ErrorMessage(new MessageHandlingException(failed,
                new TransientDataAccessResourceException("Database temporarily unavailable"))));

        assertThat((String) nextDeadLetter().getPayload())
                .contains("\nreason: TransientDataAccessResourceException: Database temporarily unavailable\n");
    }

    @Test
    void _04_ShouldUseGenericValues_WhenTheFailureCarriesNoFailedMessage() {
        handler.handle(new ErrorMessage(new IllegalStateException("disk full")));

        Message<?> deadLetter = nextDeadLetter();
        assertThat((String) deadLetter.getPayload())
                .startsWith("source-file: none\norder-id: none\n")
                .contains("\nreason: IllegalStateException: disk full\n")
                .endsWith("\npayload:\n(unavailable)\n");
        assertThat(deadLetter.getHeaders().get(FileHeaders.FILENAME, String.class)).startsWith("message.");
    }

    @Test
    void _05_ShouldGiveEachDeadLetterItsOwnFileName_WhenTheSameFileFailsTwice() {
        ErrorMessage error = new ErrorMessage(new MessageHandlingException(fileMessage("1,2,3", "same.csv"),
                new IllegalStateException("boom")));

        handler.handle(error);
        handler.handle(error);

        assertThat(nextDeadLetter().getHeaders().get(FileHeaders.FILENAME))
                .isNotEqualTo(nextDeadLetter().getHeaders().get(FileHeaders.FILENAME));
    }

    @Test
    void _06_ShouldSetTheOrderToFailedAndNameTheDeadLetterAfterIt_WhenTheFailedFileMessageCarriesAnOrderId() {
        Message<String> failed = MessageBuilder.withPayload("orderId=7\nstatus=AUTO_CONFIRMED\n")
                .setHeader(FileHeaders.FILENAME, "order-7.txt")
                .setHeader(IntegrationConfig.ORDER_ID_HEADER, 7L).build();

        handler.recordFailure(failed, new IllegalArgumentException("Destination directory is not a directory"));

        verify(orderService).updateStatus(7L, OrderStatus.FAILED);
        Message<?> deadLetter = nextDeadLetter();
        assertThat((String) deadLetter.getPayload())
                .startsWith("source-file: none\norder-id: 7\n")
                .endsWith("\npayload:\norderId=7\nstatus=AUTO_CONFIRMED\n");
        assertThat(deadLetter.getHeaders().get(FileHeaders.FILENAME, String.class)).startsWith("order-7.");
    }

    @Test
    void _07_ShouldSetTheOrderToFailedAndWriteItsColumns_WhenTheFailedPayloadIsAnOrder() {
        Order order = new Order();
        order.setId(9L);
        order.setSource(OrderSource.FILE);
        order.setQuantity(3);
        order.setTotal(new BigDecimal("45.00"));
        Message<Order> failed = MessageBuilder.withPayload(order)
                .setHeader(FileHeaders.ORIGINAL_FILE, new File("processed/order-9.csv")).build();

        handler.recordFailure(failed, new IllegalStateException("boom"));

        verify(orderService).updateStatus(9L, OrderStatus.FAILED);
        assertThat((String) nextDeadLetter().getPayload())
                .startsWith("source-file: order-9.csv\norder-id: 9\n")
                .endsWith("\npayload:\norderId=9\nsource=FILE\nquantity=3\ntotal=45.00\n");
    }

    @Test
    void _08_ShouldStillWriteTheDeadLetter_WhenTheOrderCannotBeSetToFailed() {
        when(orderService.updateStatus(any(), any())).thenThrow(ResourceNotFoundException.of("Order", 7L));
        Message<String> failed = MessageBuilder.withPayload("content")
                .setHeader(IntegrationConfig.ORDER_ID_HEADER, 7L).build();

        handler.recordFailure(failed, new IllegalStateException("boom"));

        assertThat((String) nextDeadLetter().getPayload()).contains("\norder-id: 7\n");
    }

    @Test
    void _09_ShouldRecordNothing_WhenTheFailureWasAlreadyRecordedByTheStepThatRaisedIt() {
        Message<String> failed = MessageBuilder.withPayload("content")
                .setHeader(IntegrationConfig.ORDER_ID_HEADER, 7L).build();

        handler.handle(new ErrorMessage(new MessageHandlingException(failed,
                new RecordedOrderFailureException(new IllegalStateException("boom")))));

        assertThat(deadLetters.receive(0)).isNull();
        verifyNoInteractions(orderService);
    }

    @Test
    void _10_ShouldWriteTheRawLineOfTheDroppedFile_WhenTheFailedMessageCarriesIt() {
        Message<String> failed = MessageBuilder.withPayload("orderId=7\nstatus=AUTO_CONFIRMED\n")
                .setHeader(OrderFileSplitter.LINE_HEADER, "3,4,5")
                .setHeader(FileHeaders.ORIGINAL_FILE, new File("processed/orders.csv"))
                .setHeader(IntegrationConfig.ORDER_ID_HEADER, 7L).build();

        handler.recordFailure(failed, new IllegalStateException("boom"));

        assertThat((String) nextDeadLetter().getPayload())
                .startsWith("source-file: orders.csv\norder-id: 7\n")
                .endsWith("\npayload:\n3,4,5\n");
    }

    private Message<?> nextDeadLetter() {
        Message<?> deadLetter = deadLetters.receive(0);
        assertThat(deadLetter).isNotNull();
        return deadLetter;
    }

    private static Message<String> fileMessage(String content, String fileName) {
        return MessageBuilder.withPayload(content)
                .setHeader(FileHeaders.FILENAME, fileName)
                .setHeader(FileHeaders.ORIGINAL_FILE, new File("processed", fileName))
                .build();
    }
}
