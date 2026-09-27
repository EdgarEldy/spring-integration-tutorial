package com.edgareldy.springintegrationtutorial.integration.adapter;

import com.edgareldy.springintegrationtutorial.config.IntegrationConfig;
import com.edgareldy.springintegrationtutorial.entity.Order;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.annotation.Transformer;
import org.springframework.integration.file.outbound.FileWritingMessageHandler;
import org.springframework.messaging.Message;

/**
 * The rejection outbound channel: writes a rejection file, with the administrator's reason, into the
 * rejections directory for every rejected order.
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 */
// Same shape as the review-queue writer, minus the status step: the decision activator has already
// recorded REJECTED when the order arrives on rejection-channel, with the reason in a header.
@Configuration
public class RejectionOutboundAdapterConfig {

    private final Path rejectionsDirectory;

    /**
     * @param rejectionsDirectory where rejection files go ({@code orders.directories.rejections})
     */
    public RejectionOutboundAdapterConfig(@Value("${orders.directories.rejections}") Path rejectionsDirectory) {
        this.rejectionsDirectory = rejectionsDirectory;
    }

    /**
     * @param message a rejected order, carrying the reason in the {@code rejectionReason} header
     * @return the rejection file message
     */
    @Transformer(inputChannel = IntegrationConfig.REJECTION_CHANNEL,
            outputChannel = IntegrationConfig.REJECTION_FILE_CHANNEL)
    public Message<String> toRejectionFile(Message<Order> message) {
        String reason = message.getHeaders().get(IntegrationConfig.REJECTION_REASON_HEADER, String.class);
        // The reason is free text typed by an administrator: kept on one line, it cannot add lines of its own.
        return OrderFiles.render(message, "reason=" + OrderFiles.singleLine(reason == null ? "" : reason));
    }

    /**
     * @return the writer of the rejection files
     */
    @Bean
    @ServiceActivator(inputChannel = IntegrationConfig.REJECTION_FILE_CHANNEL)
    public FileWritingMessageHandler rejectionFileWriter() {
        return OrderFiles.writer(rejectionsDirectory);
    }
}
