package com.edgareldy.springintegrationtutorial.integration.splitter;

import com.edgareldy.springintegrationtutorial.config.IntegrationConfig;
import java.util.ArrayList;
import java.util.List;
import org.springframework.integration.annotation.Splitter;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * Breaks the content of a dropped order file into one message per non-blank line
 * ({@code customerId,productId,quantity}, no header), each correlated to its source file.
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 */
@Component
public class OrderFileSplitter {

    /**
     * Header holding the line's position in the source file (from 1, blank lines counted), so a report can
     * point at the exact line even though blank lines produce no message.
     */
    public static final String LINE_NUMBER_HEADER = "order_line_number";

    /**
     * Header holding the line's content: once the transformer has turned the payload into an
     * {@code OrderCommand}, the original text is still known when that line fails or completes.
     */
    public static final String LINE_HEADER = "order_line";

    private static final char BYTE_ORDER_MARK = '﻿';

    /**
     * @param content the whole text of one order file
     * @return one message per non-blank line, in file order; an empty list for a file without any line
     */
    // A @Splitter endpoint calls this method with each incoming message and sends every element of the
    // returned collection as a message of its own. It is the first half of the splitter/aggregator pair:
    // before sending, the endpoint stamps each part with the id of the file message as correlation id, plus
    // a sequence number and the sequence size (the number of parts, i.e. of non-blank lines), and copies the
    // file's headers (file_name, file_originalFile). The aggregator uses those headers to know which file a
    // line belongs to and when every line of it has come back.
    // Returning Messages rather than bare Strings lets each part carry its own extra headers; the endpoint
    // still adds the correlation headers to them.
    @Splitter(inputChannel = IntegrationConfig.ORDER_FILE_CHANNEL,
            outputChannel = IntegrationConfig.ORDER_LINE_CHANNEL)
    public List<Message<String>> split(String content) {
        // A byte order mark belongs to the file, not to its first line.
        String text = !content.isEmpty() && content.charAt(0) == BYTE_ORDER_MARK ? content.substring(1) : content;
        String[] lines = text.split("\\R", -1);
        List<Message<String>> parts = new ArrayList<>();
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            if (line.isBlank()) {
                continue;
            }
            parts.add(MessageBuilder.withPayload(line)
                    .setHeader(LINE_NUMBER_HEADER, index + 1)
                    .setHeader(LINE_HEADER, line.strip())
                    .build());
        }
        return parts;
    }
}
