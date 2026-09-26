package com.edgareldy.springintegrationtutorial.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Page of results used as the payload of {@code ApiResponse<PageResponse<T>>} on list endpoints.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 *
 * @param content       the elements of this page
 * @param page          zero-based page index
 * @param size          requested page size
 * @param totalElements total number of elements across all pages
 * @param totalPages    total number of pages
 * @param <T>           element type
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    /**
     * Copies a Spring Data page, so the JSON shape never depends on Spring Data's own serialization.
     *
     * @param page the Spring Data page
     * @param <T>  element type
     * @return the page response
     */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    /**
     * Copies a Spring Data page while mapping each element, typically an entity to its response DTO.
     *
     * @param page   the Spring Data page
     * @param mapper the element mapping
     * @param <S>    source element type
     * @param <T>    target element type
     * @return the page response
     */
    public static <S, T> PageResponse<T> from(Page<S> page, Function<? super S, ? extends T> mapper) {
        return from(page.map(mapper));
    }
}
