package com.edgareldy.springintegrationtutorial.integration.aggregator;

import com.edgareldy.springintegrationtutorial.config.IntegrationConfig;
import com.edgareldy.springintegrationtutorial.integration.message.LineOutcome;
import com.edgareldy.springintegrationtutorial.integration.splitter.OrderFileSplitter;
import java.util.Comparator;
import java.util.List;
import org.springframework.integration.IntegrationMessageHeaderAccessor;
import org.springframework.integration.annotation.Aggregator;
import org.springframework.integration.annotation.ReleaseStrategy;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * Collects the per-line outcomes of one order file back together and, once every line is accounted for,
 * turns them into the file's completion report (counts per result, reason of each failed line).
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 */
@Component
public class OrderFileAggregator {

    private static final String UNKNOWN_FILE = "unknown";
    // The same line ending on every system, so a report reads the same wherever the application runs.
    private static final char NEW_LINE = '\n';

    /**
     * @param outcomes the outcomes received so far for one file
     * @return {@code true} once there is one outcome per line the splitter emitted
     */
    // A @ReleaseStrategy method, on the same bean as the @Aggregator method, decides when a group is complete.
    // The splitter stamped every line with the sequence size (the number of non-blank lines), and every line
    // produces exactly one outcome, success or failure: the group is complete when it holds that many.
    // Spring Integration's default release strategy applies the same rule; writing it out keeps the
    // completion condition of this aggregator visible instead of implicit.
    @ReleaseStrategy
    public boolean allLinesAccountedFor(List<Message<?>> outcomes) {
        Integer expected = outcomes.get(0).getHeaders()
                .get(IntegrationMessageHeaderAccessor.SEQUENCE_SIZE, Integer.class);
        return expected != null && outcomes.size() >= expected;
    }

    /**
     * @param outcomes every outcome of one file
     * @return the text of the file's completion report
     */
    // An @Aggregator endpoint is the second half of the splitter/aggregator pair. It stores each incoming
    // message in a group keyed by its correlation id (no @CorrelationStrategy is declared, so the default one
    // reads the correlation id header the splitter set: one group per file), asks the release strategy after
    // each arrival whether the group is complete, and only then calls this method once with the whole group.
    // The returned report goes to the output channel with the headers every outcome shares, such as the
    // source file name. A released group stays in the in-memory store as an empty, completed marker, so a
    // stray late outcome of an already reported file is discarded instead of opening a new group.
    @Aggregator(inputChannel = IntegrationConfig.LINE_OUTCOME_CHANNEL,
            outputChannel = IntegrationConfig.REPORT_CHANNEL)
    public String aggregate(List<Message<LineOutcome>> outcomes) {
        String fileName = outcomes.get(0).getHeaders().get(OrderFileSplitter.SOURCE_FILE_HEADER, String.class);
        List<LineOutcome> lines = outcomes.stream()
                .map(Message::getPayload)
                .sorted(Comparator.comparingInt(LineOutcome::lineNumber))
                .toList();
        StringBuilder report = new StringBuilder()
                .append("Order file report").append(NEW_LINE)
                .append("Source file: ").append(fileName == null ? UNKNOWN_FILE : fileName).append(NEW_LINE)
                .append("Lines: ").append(lines.size()).append(NEW_LINE)
                .append("Auto-confirmed: ").append(count(lines, LineOutcome.Result.AUTO_CONFIRMED))
                .append(NEW_LINE)
                .append("Pending review: ").append(count(lines, LineOutcome.Result.PENDING_REVIEW))
                .append(NEW_LINE)
                .append("Failed: ").append(count(lines, LineOutcome.Result.FAILED)).append(NEW_LINE);
        List<LineOutcome> failed = lines.stream().filter(line -> line.result() == LineOutcome.Result.FAILED).toList();
        if (!failed.isEmpty()) {
            report.append(NEW_LINE).append("Failed lines:").append(NEW_LINE);
            for (LineOutcome line : failed) {
                report.append("line ").append(line.lineNumber()).append(" [").append(line.line()).append("]: ");
                // A line that failed after persistence names its order, so it can be found in the database.
                if (line.orderId() != null) {
                    report.append("(order ").append(line.orderId()).append(") ");
                }
                report.append(line.reason()).append(NEW_LINE);
            }
        }
        return report.toString();
    }

    private static long count(List<LineOutcome> lines, LineOutcome.Result result) {
        return lines.stream().filter(line -> line.result() == result).count();
    }
}
