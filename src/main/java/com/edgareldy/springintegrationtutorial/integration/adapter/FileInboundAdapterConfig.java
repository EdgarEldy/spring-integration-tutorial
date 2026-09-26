package com.edgareldy.springintegrationtutorial.integration.adapter;

import com.edgareldy.springintegrationtutorial.config.IntegrationConfig;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.InboundChannelAdapter;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.file.FileHeaders;
import org.springframework.integration.file.filters.AcceptOnceFileListFilter;
import org.springframework.integration.file.filters.CompositeFileListFilter;
import org.springframework.integration.file.filters.SimplePatternFileListFilter;
import org.springframework.integration.file.inbound.FileReadingMessageSource;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;

/**
 * The file-drop inbound channel: polls the incoming orders directory and sends the whole content of each
 * {@code *.csv} file (one or more order lines, {@code customerId,productId,quantity}) to the bulk splitter,
 * after moving the file to the processed directory.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
@Configuration
public class FileInboundAdapterConfig {

    // Remembers, in memory, every file already handed out, so a file still sitting in the directory is
    // never read twice. It is emptied by a restart, which is why each file is also moved out of the
    // watched directory once read: the move is what really guarantees a file is processed only once.
    private final AcceptOnceFileListFilter<File> acceptOnceFilter = new AcceptOnceFileListFilter<>();

    private final Path incomingDirectory;
    private final Path processedDirectory;

    /**
     * @param incomingDirectory  the watched directory ({@code orders.directories.incoming})
     * @param processedDirectory where read files are moved ({@code orders.directories.processed})
     */
    public FileInboundAdapterConfig(
            @Value("${orders.directories.incoming}") Path incomingDirectory,
            @Value("${orders.directories.processed}") Path processedDirectory) {
        this.incomingDirectory = incomingDirectory;
        this.processedDirectory = processedDirectory;
    }

    /**
     * @return the source listing the order files waiting in the incoming directory
     */
    // FileReadingMessageSource is Spring Integration's file inbound source: each receive() returns the next
    // matching file of its directory as a Message<File>. It lists the directory itself only, never its
    // sub-directories, so the processed/ folder underneath is not scanned again.
    @Bean
    public FileReadingMessageSource incomingOrderFiles() {
        FileReadingMessageSource source = new FileReadingMessageSource();
        source.setDirectory(incomingDirectory.toFile());
        source.setAutoCreateDirectory(true);
        // Only *.csv files are orders: anything else dropped there (a .gitkeep, a note, a file still being
        // written under a temporary name) is left alone.
        source.setFilter(new CompositeFileListFilter<>(
                List.of(new SimplePatternFileListFilter("*.csv"), acceptOnceFilter)));
        return source;
    }

    /**
     * @param incomingOrderFiles the directory source
     * @return the source of order file contents polled onto the order file channel
     */
    // @InboundChannelAdapter on a MessageSource bean creates a polling endpoint: on every tick of the poller
    // (none is named here, so the default PollerMetadata of IntegrationConfig applies) it calls receive() and
    // sends each non-null message to the channel. It is the inbound Channel Adapter of the EIP catalog: it
    // connects something outside the messaging system, a directory, to a channel.
    // The message it sends carries the file's content, not the File. Every file goes to the splitter, which
    // sends each of its lines into the shared intake flow: the transformer receives a raw CSV line from this
    // source exactly as it receives a raw REST payload from the gateway, and a single-order file is simply a
    // batch of one line.
    @Bean
    @InboundChannelAdapter(channel = IntegrationConfig.ORDER_FILE_CHANNEL)
    public MessageSource<String> incomingOrderLines(FileReadingMessageSource incomingOrderFiles) {
        createDirectory(processedDirectory);
        return () -> {
            Message<File> fileMessage = incomingOrderFiles.receive();
            if (fileMessage == null) {
                return null;
            }
            File file = fileMessage.getPayload();
            String content = read(file);
            // Moved as soon as it is read, before the order goes through the flow: a failure further down
            // (malformed line, unknown customer) never makes the poller pick the same file up again.
            Path processed = moveToProcessed(file);
            // The file is gone from the watched directory, so the accept-once filter can forget it: an
            // order file dropped later under the same name is a new order and must be read.
            acceptOnceFilter.remove(file);
            return MessageBuilder.withPayload(content)
                    .setHeader(FileHeaders.FILENAME, file.getName())
                    .setHeader(FileHeaders.ORIGINAL_FILE, processed.toFile())
                    .build();
        };
    }

    private Path moveToProcessed(File file) {
        try {
            return Files.move(file.toPath(), processedDirectory.resolve(file.getName()),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot move " + file + " to " + processedDirectory, ex);
        }
    }

    private static String read(File file) {
        try {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot read order file " + file, ex);
        }
    }

    private static void createDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot create directory " + directory, ex);
        }
    }
}
