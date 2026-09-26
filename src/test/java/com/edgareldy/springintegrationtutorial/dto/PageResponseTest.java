package com.edgareldy.springintegrationtutorial.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * Unit tests of the {@link PageResponse} conversions from a Spring Data page.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
class PageResponseTest {

    @Test
    void _01_ShouldCopyContentAndPagingMetadata_WhenBuiltFromAPage() {
        PageImpl<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5);

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.content()).containsExactly("a", "b");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
    }

    @Test
    void _02_ShouldMapEveryElementAndKeepMetadata_WhenBuiltWithAMapper() {
        PageImpl<Integer> page = new PageImpl<>(List.of(1, 2, 3), PageRequest.of(0, 3), 3);

        PageResponse<String> response = PageResponse.from(page, value -> "#" + value);

        assertThat(response.content()).containsExactly("#1", "#2", "#3");
        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.totalPages()).isEqualTo(1);
    }

    @Test
    void _03_ShouldReturnEmptyContentAndZeroPages_WhenThePageIsEmpty() {
        PageImpl<String> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
    }
}
