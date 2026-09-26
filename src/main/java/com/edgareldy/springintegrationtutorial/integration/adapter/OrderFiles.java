package com.edgareldy.springintegrationtutorial.integration.adapter;

import com.edgareldy.springintegrationtutorial.config.IntegrationConfig;
import com.edgareldy.springintegrationtutorial.entity.Order;
import java.nio.file.Path;
import java.util.StringJoiner;
import org.springframework.integration.file.FileHeaders;
import org.springframework.integration.file.outbound.FileWritingMessageHandler;
import org.springframework.integration.file.support.FileExistsMode;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;

/**
 * What the outbound file adapters have in common: the {@code order-<id>.txt} text file they write for an
 * order, as {@code key=value} lines, and the way their file writers are configured.
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 */
final class OrderFiles {

    private OrderFiles() {
    }

    /**
     * Renders the file message of an order: its content is the order's own columns (status included,
     * already updated when this runs) followed by the extra lines of the adapter.
     *
     * @param message    the message carrying the order
     * @param extraLines additional {@code key=value} lines, specific to one adapter
     * @return a message whose payload is the file content, named by the {@code file_name} header
     */
    static Message<String> render(Message<Order> message, String... extraLines) {
        Order order = message.getPayload();
        // Only the order's own columns are read: its customer and product are lazy associations of an
        // entity whose transaction is over, and touching them here would fail.
        StringJoiner content = new StringJoiner("\n", "", "\n")
                .add("orderId=" + order.getId())
                .add("status=" + order.getStatus())
                .add("source=" + order.getSource())
                .add("quantity=" + order.getQuantity())
                .add("total=" + order.getTotal().toPlainString());
        for (String line : extraLines) {
            content.add(line);
        }
        // The incoming headers are copied, so nothing the flow attached upstream is lost; the order id
        // travels as a header too, so a step failing on this message still knows which order it concerns.
        return MessageBuilder.withPayload(content.toString())
                .copyHeaders(message.getHeaders())
                .setHeader(FileHeaders.FILENAME, "order-" + order.getId() + ".txt")
                .setHeader(IntegrationConfig.ORDER_ID_HEADER, order.getId())
                .build();
    }

    /**
     * @param directory where the files are written
     * @return a file writer naming each file after its {@code file_name} header
     */
    // FileWritingMessageHandler is Spring Integration's outbound file adapter: it writes the payload of
    // each message (a String here) into a file of its directory. Its default file name generator reads the
    // file_name header, set by render(). It first writes under a temporary ".writing" name and renames the
    // file once complete, so whoever watches the directory never reads a half-written file.
    static FileWritingMessageHandler writer(Path directory) {
        FileWritingMessageHandler writer = new FileWritingMessageHandler(directory.toFile());
        writer.setAutoCreateDirectory(true);
        writer.setCharset("UTF-8");
        // One file per order: writing it again (the same order id after the database was recreated, for
        // instance) replaces the old file instead of failing or appending to it.
        writer.setFileExistsMode(FileExistsMode.REPLACE);
        // A terminal step: it sends nothing on once the file is written (the default would reply with the
        // written File and require an output channel).
        writer.setExpectReply(false);
        return writer;
    }

    /**
     * @param value free text written as the value of a {@code key=value} line
     * @return the same text on a single line
     */
    static String singleLine(String value) {
        return value.replaceAll("\\R", " ");
    }
}
