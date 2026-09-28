package com.edgareldy.springintegrationtutorial.integration.transformer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edgareldy.springintegrationtutorial.dto.order.OrderIntakeRequest;
import com.edgareldy.springintegrationtutorial.entity.OrderSource;
import com.edgareldy.springintegrationtutorial.integration.message.OrderCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests of the conversion rules of {@link RawOrderTransformer}, without any Spring context.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
class RawOrderTransformerTest {

    private final RawOrderTransformer transformer = new RawOrderTransformer();

    @Test
    void _01_ShouldBuildAnApiCommand_WhenThePayloadIsARestRequest() {
        OrderCommand command = transformer.transform(new OrderIntakeRequest(1L, 2L, 3));

        assertThat(command).isEqualTo(new OrderCommand(1L, 2L, 3, OrderSource.API));
    }

    @Test
    void _02_ShouldBuildAFileCommand_WhenThePayloadIsACsvLine() {
        OrderCommand command = transformer.transform("4,5,6");

        assertThat(command).isEqualTo(new OrderCommand(4L, 5L, 6, OrderSource.FILE));
    }

    @Test
    void _03_ShouldIgnoreSpacesTheLineBreakAndAByteOrderMark_WhenTheCsvLineHasThem() {
        OrderCommand command = transformer.transform("﻿ 4 , 5 ,6 \r\n");

        assertThat(command).isEqualTo(new OrderCommand(4L, 5L, 6, OrderSource.FILE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "4,5", "4,5,6,7", "a,5,6", "4,5,x", "4,,6"})
    void _04_ShouldRejectTheLine_WhenItIsNotThreeIntegers(String line) {
        assertThatThrownBy(() -> transformer.transform(line))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Malformed order line");
    }

    @ParameterizedTest
    @ValueSource(strings = {"4,5,0", "4,5,-2"})
    void _05_ShouldRejectTheLine_WhenTheQuantityIsNotPositive(String line) {
        assertThatThrownBy(() -> transformer.transform(line))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageEndingWith("quantity must be greater than 0");
    }

    @Test
    void _06_ShouldRejectThePayload_WhenItsTypeIsNeitherARequestNorALine() {
        assertThatThrownBy(() -> transformer.transform(42))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported order payload type");
    }
}
