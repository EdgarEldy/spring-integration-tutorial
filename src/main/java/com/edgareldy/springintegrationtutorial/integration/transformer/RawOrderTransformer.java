package com.edgareldy.springintegrationtutorial.integration.transformer;

import com.edgareldy.springintegrationtutorial.config.IntegrationConfig;
import com.edgareldy.springintegrationtutorial.dto.order.OrderIntakeRequest;
import com.edgareldy.springintegrationtutorial.entity.OrderSource;
import com.edgareldy.springintegrationtutorial.integration.message.OrderCommand;
import org.springframework.integration.annotation.Transformer;
import org.springframework.stereotype.Component;

/**
 * The single place where a raw input becomes an {@link OrderCommand}: a REST {@link OrderIntakeRequest}
 * from the HTTP gateway, or a CSV line ({@code customerId,productId,quantity}) read from a dropped file.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
@Component
public class RawOrderTransformer {

    private static final int CSV_FIELDS = 3;
    private static final char BYTE_ORDER_MARK = '﻿';

    /**
     * @param payload the raw payload found on the intake channel
     * @return the normalized order, stamped with the source its payload type reveals
     * @throws IllegalArgumentException if the payload is a malformed CSV line or of an unsupported type
     */
    // A @Transformer endpoint subscribes to its input channel, calls this method with each message's
    // payload and sends the return value, wrapped in a new message that keeps the original headers, to its
    // output channel. Transforming changes the representation, never the meaning: an order stays the same
    // order. Both sources send their raw payload to the same intake channel, so this is where "where did
    // the message come from" stops mattering to the rest of the flow, and the conversion exists only once.
    @Transformer(inputChannel = IntegrationConfig.INTAKE_CHANNEL,
            outputChannel = IntegrationConfig.ORDER_COMMAND_CHANNEL)
    public OrderCommand transform(Object payload) {
        if (payload instanceof OrderIntakeRequest request) {
            return new OrderCommand(request.customerId(), request.productId(), request.quantity(), OrderSource.API);
        }
        if (payload instanceof String line) {
            return fromCsvLine(line);
        }
        throw new IllegalArgumentException("Unsupported order payload type: " + payload.getClass().getName());
    }

    private static OrderCommand fromCsvLine(String rawLine) {
        String line = rawLine.strip();
        // A file saved as "UTF-8 with BOM" (Windows Notepad does it) starts with an invisible marker
        // that would otherwise make the first number unparsable.
        if (!line.isEmpty() && line.charAt(0) == BYTE_ORDER_MARK) {
            line = line.substring(1).strip();
        }
        String[] fields = line.split(",", -1);
        if (fields.length != CSV_FIELDS) {
            throw malformed(rawLine, "expected customerId,productId,quantity");
        }
        try {
            long customerId = Long.parseLong(fields[0].strip());
            long productId = Long.parseLong(fields[1].strip());
            int quantity = Integer.parseInt(fields[2].strip());
            // Same rule as the Bean Validation of the REST payload: the file path has no controller in front.
            if (quantity <= 0) {
                throw malformed(rawLine, "quantity must be greater than 0");
            }
            return new OrderCommand(customerId, productId, quantity, OrderSource.FILE);
        } catch (NumberFormatException ex) {
            throw malformed(rawLine, "customerId, productId and quantity must be integers");
        }
    }

    private static IllegalArgumentException malformed(String line, String reason) {
        return new IllegalArgumentException("Malformed order line '" + line.strip() + "': " + reason);
    }
}
