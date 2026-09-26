package com.edgareldy.springintegrationtutorial.integration.splitter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;

/**
 * Unit tests of {@link OrderFileSplitter#split(String)}: which lines of a file become messages, and the
 * line headers they carry. The correlation headers are added by the splitter endpoint and are tested in
 * the flow test.
 * <p>
 * Created edgar.muhamyangabo on 9/27/26
 * Author : edgar.muhamyangabo
 * Date : 9/27/26
 * Project : spring-integration-tutorial
 */
class OrderFileSplitterTest {

    private final OrderFileSplitter splitter = new OrderFileSplitter();

    @Test
    void _01_ShouldEmitOneMessagePerLineInFileOrder_WhenTheFileHoldsSeveralLines() {
        List<Message<String>> parts = splitter.split("1,2,3\n4,5,6\n7,8,9\n");

        assertThat(parts).extracting(Message::getPayload).containsExactly("1,2,3", "4,5,6", "7,8,9");
    }

    @Test
    void _02_ShouldSkipBlankLinesButKeepThePhysicalLineNumbers_WhenTheFileHoldsBlankLines() {
        List<Message<String>> parts = splitter.split("\n1,2,3\n   \n\t\n4,5,6\n\n");

        assertThat(parts).extracting(Message::getPayload).containsExactly("1,2,3", "4,5,6");
        assertThat(parts).extracting(part -> part.getHeaders().get(OrderFileSplitter.LINE_NUMBER_HEADER))
                .containsExactly(2, 5);
    }

    @Test
    void _03_ShouldSplitOnEveryLineEnding_WhenTheFileMixesWindowsAndUnixLineEndings() {
        List<Message<String>> parts = splitter.split("1,2,3\r\n4,5,6\n7,8,9\r10,11,12");

        assertThat(parts).extracting(Message::getPayload).containsExactly("1,2,3", "4,5,6", "7,8,9", "10,11,12");
    }

    @Test
    void _04_ShouldCarryTheStrippedLineInAHeader_WhenALineHasSurroundingSpaces() {
        List<Message<String>> parts = splitter.split("  1,2,3  \n");

        assertThat(parts).singleElement().satisfies(part -> {
            assertThat(part.getPayload()).isEqualTo("  1,2,3  ");
            assertThat(part.getHeaders()).containsEntry(OrderFileSplitter.LINE_HEADER, "1,2,3")
                    .containsEntry(OrderFileSplitter.LINE_NUMBER_HEADER, 1);
        });
    }

    @Test
    void _05_ShouldDropTheByteOrderMark_WhenTheFileStartsWithOne() {
        List<Message<String>> parts = splitter.split("﻿1,2,3\n4,5,6");

        assertThat(parts).extracting(Message::getPayload).containsExactly("1,2,3", "4,5,6");
    }

    @Test
    void _06_ShouldEmitABatchOfOne_WhenTheFileHoldsASingleOrderLine() {
        List<Message<String>> parts = splitter.split("1,2,3");

        assertThat(parts).extracting(Message::getPayload).containsExactly("1,2,3");
    }

    @Test
    void _07_ShouldEmitNothing_WhenTheFileIsEmptyOrBlank() {
        assertThat(splitter.split("")).isEmpty();
        assertThat(splitter.split("\n  \r\n")).isEmpty();
    }

    @Test
    void _08_ShouldKeepAMalformedLine_WhenItIsNotBlank() {
        // Validating a line is the transformer's job: the splitter only cuts the file.
        List<Message<String>> parts = splitter.split("not,an,order\n1,2");

        assertThat(parts).extracting(Message::getPayload).containsExactly("not,an,order", "1,2");
    }
}
