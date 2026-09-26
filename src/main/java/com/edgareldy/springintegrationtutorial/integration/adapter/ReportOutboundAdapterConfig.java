package com.edgareldy.springintegrationtutorial.integration.adapter;

import com.edgareldy.springintegrationtutorial.config.IntegrationConfig;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.file.FileHeaders;
import org.springframework.integration.file.outbound.FileWritingMessageHandler;
import org.springframework.integration.file.support.FileExistsMode;
import org.springframework.messaging.Message;

/**
 * The bulk completion report's outbound channel adapter: writes each report released by the aggregator into
 * the reports directory, as {@code <source file name without .csv>-report.txt}.
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 */
@Configuration
public class ReportOutboundAdapterConfig {

    private static final String CSV_EXTENSION = ".csv";
    private static final String REPORT_SUFFIX = "-report.txt";

    /**
     * @param reportsDirectory where reports are written ({@code orders.directories.reports})
     * @return the handler writing one report file per source file
     */
    // FileWritingMessageHandler is Spring Integration's file outbound handler: it writes a message's payload
    // (a String here, encoded as UTF-8) to a file of the configured directory, the file name coming from a
    // FileNameGenerator. Registered with @ServiceActivator on a @Bean method, the handler itself becomes the
    // endpoint subscribed to the report channel: this is the outbound Channel Adapter of the EIP catalog,
    // connecting a channel to something outside the messaging system.
    @Bean
    @ServiceActivator(inputChannel = IntegrationConfig.REPORT_CHANNEL)
    public FileWritingMessageHandler reportWriter(@Value("${orders.directories.reports}") Path reportsDirectory) {
        FileWritingMessageHandler handler = new FileWritingMessageHandler(reportsDirectory.toFile());
        handler.setAutoCreateDirectory(true);
        handler.setCharset("UTF-8");
        handler.setFileNameGenerator(ReportOutboundAdapterConfig::reportName);
        // A file dropped again under the same name gets a fresh report, as its content replaced the earlier
        // copy in processed/.
        handler.setFileExistsMode(FileExistsMode.REPLACE);
        // By default the handler writes under a temporary name (".writing" suffix) and renames the file once
        // complete: whoever watches the reports directory never reads a half-written report.
        // Writing the report is the end of the file path: nothing is sent on.
        handler.setExpectReply(false);
        return handler;
    }

    private static String reportName(Message<?> message) {
        String fileName = message.getHeaders().get(FileHeaders.FILENAME, String.class);
        if (fileName == null || fileName.isBlank()) {
            fileName = "unknown" + CSV_EXTENSION;
        }
        String baseName = fileName.endsWith(CSV_EXTENSION)
                ? fileName.substring(0, fileName.length() - CSV_EXTENSION.length())
                : fileName;
        return baseName + REPORT_SUFFIX;
    }
}
