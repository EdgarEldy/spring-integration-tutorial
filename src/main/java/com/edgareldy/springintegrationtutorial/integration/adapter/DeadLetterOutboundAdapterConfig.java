package com.edgareldy.springintegrationtutorial.integration.adapter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.file.FileHeaders;
import org.springframework.integration.file.outbound.FileWritingMessageHandler;
import org.springframework.integration.file.support.FileExistsMode;

/**
 * The end of the Dead Letter Channel: writes every dead letter prepared by {@code OrderErrorHandler}
 * into the failed orders directory, one text file per failed message.
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 */
@Configuration
public class DeadLetterOutboundAdapterConfig {

    /**
     * Name of the channel carrying the dead letters (text content, target file name in the
     * {@link FileHeaders#FILENAME} header) to the file writer.
     */
    public static final String DEAD_LETTER_CHANNEL = "dead-letter-channel";

    /**
     * @return the dead-letter channel
     */
    // A DirectChannel: the dead letter is written in the thread that handled the failure, so the error
    // handler only returns once the file is on disk.
    @Bean(DEAD_LETTER_CHANNEL)
    public DirectChannel deadLetterChannel() {
        return new DirectChannel();
    }

    /**
     * @param failedDirectory the dead-letter directory ({@code orders.directories.failed})
     * @return the handler writing each dead letter as a file
     */
    // FileWritingMessageHandler is Spring Integration's outbound file adapter: it writes each message's
    // payload (a String here, written as UTF-8) into a directory, under the name its FileNameGenerator
    // returns. The default generator reads the file_name header, which the error handler sets to a
    // unique name, so two failures of files with the same name never overwrite each other.
    // Registered with @ServiceActivator on a MessageHandler @Bean, it becomes the consumer of the
    // dead-letter channel, the outbound counterpart of the file inbound adapter.
    @Bean
    @ServiceActivator(inputChannel = DEAD_LETTER_CHANNEL)
    public FileWritingMessageHandler deadLetterFileWriter(
            @Value("${orders.directories.failed}") Path failedDirectory) {
        FileWritingMessageHandler handler = new FileWritingMessageHandler(failedDirectory.toFile());
        handler.setAutoCreateDirectory(true);
        handler.setCharset(StandardCharsets.UTF_8.name());
        // Names are unique, so an existing file can only be a leftover: replacing it is harmless.
        handler.setFileExistsMode(FileExistsMode.REPLACE);
        // The handler could also reply with the written File; nothing downstream needs it, so the dead
        // letter ends here (without this, it would look for a reply channel that does not exist).
        handler.setExpectReply(false);
        return handler;
    }
}
