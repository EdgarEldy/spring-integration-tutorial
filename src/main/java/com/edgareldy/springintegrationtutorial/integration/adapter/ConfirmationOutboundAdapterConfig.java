package com.edgareldy.springintegrationtutorial.integration.adapter;

import com.edgareldy.springintegrationtutorial.config.IntegrationConfig;
import com.edgareldy.springintegrationtutorial.entity.Order;
import com.edgareldy.springintegrationtutorial.entity.OrderStatus;
import com.edgareldy.springintegrationtutorial.service.OrderService;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.annotation.Transformer;
import org.springframework.integration.file.outbound.FileWritingMessageHandler;
import org.springframework.messaging.Message;

/**
 * The confirmation outbound channel: records an auto-confirmed order's status, then writes a confirmation
 * file into the confirmations directory for every confirmed order, auto-confirmed or approved.
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 */
// Three endpoints, each behind its own channel:
//   auto-confirm-channel      -> autoConfirm()            status AUTO_CONFIRMED
//   confirmation-channel      -> toConfirmationFile()     the order rendered as a file message
//   confirmation-file-channel -> confirmationFileWriter   the file written to disk
// The status step comes first and stays apart from the file part: an approved order enters at
// confirmation-channel with its APPROVED status already recorded and reuses the file part unchanged, and a
// failure while writing the file happens after the status update, as a separate step that an error policy
// can target on its own.
@Configuration
public class ConfirmationOutboundAdapterConfig {

    private final OrderService orderService;
    private final Path confirmationsDirectory;

    /**
     * @param orderService           records the status
     * @param confirmationsDirectory where confirmation files go ({@code orders.directories.confirmations})
     */
    public ConfirmationOutboundAdapterConfig(
            OrderService orderService,
            @Value("${orders.directories.confirmations}") Path confirmationsDirectory) {
        this.orderService = orderService;
        this.confirmationsDirectory = confirmationsDirectory;
    }

    /**
     * @param order an order the router found under the review threshold
     * @return the order with its status {@code AUTO_CONFIRMED}
     */
    @ServiceActivator(inputChannel = IntegrationConfig.AUTO_CONFIRM_CHANNEL,
            outputChannel = IntegrationConfig.CONFIRMATION_CHANNEL)
    public Order autoConfirm(Order order) {
        return orderService.updateStatus(order.getId(), OrderStatus.AUTO_CONFIRMED);
    }

    /**
     * @param message a confirmed order, its status already recorded
     * @return the confirmation file message
     */
    // A @Transformer changes the payload's representation without changing its meaning: the order becomes
    // the text of its file. Keeping it apart from the file writer leaves the writer a plain, generic
    // adapter that only knows how to write a String into a directory.
    @Transformer(inputChannel = IntegrationConfig.CONFIRMATION_CHANNEL,
            outputChannel = IntegrationConfig.CONFIRMATION_FILE_CHANNEL)
    public Message<String> toConfirmationFile(Message<Order> message) {
        return OrderFiles.render(message);
    }

    /**
     * @return the writer of the confirmation files
     */
    // @ServiceActivator on a @Bean returning a MessageHandler subscribes that ready-made handler to the input
    // channel: the endpoint is named after the bean, confirmationFileWriter.serviceActivator.
    // Unlike the other writers, it replies with the written file (headers kept) on confirmation-written-channel:
    // for a line of a bulk file, that reply becomes the line's auto-confirmed outcome, only once the file exists.
    @Bean
    @ServiceActivator(inputChannel = IntegrationConfig.CONFIRMATION_FILE_CHANNEL)
    public FileWritingMessageHandler confirmationFileWriter() {
        FileWritingMessageHandler writer = OrderFiles.writer(confirmationsDirectory);
        writer.setExpectReply(true);
        // A ready-made handler declared as a @Bean takes its output channel itself, not from the annotation.
        writer.setOutputChannelName(IntegrationConfig.CONFIRMATION_WRITTEN_CHANNEL);
        return writer;
    }
}
