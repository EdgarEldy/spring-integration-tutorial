package com.edgareldy.springintegrationtutorial.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Unit tests of the {@link ApiResponse} factories.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
class ApiResponseTest {

    @Test
    void _01_ShouldCarryDataAndSuccessFlag_WhenBuiltAsSuccess() {
        Instant before = Instant.now();

        ApiResponse<String> response = ApiResponse.success("payload", "Done");

        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Done");
        assertThat(response.data()).isEqualTo("payload");
        assertThat(response.timestamp()).isBetween(before, Instant.now());
    }

    @Test
    void _02_ShouldHaveNoDataAndFailureFlag_WhenBuiltAsError() {
        ApiResponse<Object> response = ApiResponse.error("Boom");

        assertThat(response.success()).isFalse();
        assertThat(response.message()).isEqualTo("Boom");
        assertThat(response.data()).isNull();
        assertThat(response.timestamp()).isNotNull();
    }
}
