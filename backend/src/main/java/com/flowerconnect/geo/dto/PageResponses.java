package com.flowerconnect.geo.dto;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Construction of the public {@link PageResponse} envelope for the geo
 * endpoints (plan tasks 4.3 discovery and 4.4 search).
 *
 * <p>Both endpoints paginate in memory — the sort key (distance) is not a
 * database column — so the clamping, window arithmetic and envelope live here
 * once rather than in each service. A change to the page contract (a new
 * envelope field, a different clamp bound) then reaches both endpoints, so
 * they cannot report divergent metadata for the same query parameters.
 */
public final class PageResponses {

    /** Page size used when the caller supplies none. */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** Upper bound on any page size. */
    public static final int MAX_PAGE_SIZE = 100;

    private PageResponses() {
    }

    /**
     * Clamps a caller-supplied page number: negative values become 0 and a
     * null value means the first page.
     */
    public static int safePage(Integer page) {
        return Math.max(0, page != null ? page : 0);
    }

    /**
     * Clamps a caller-supplied page size into {@code 1..100}; a null value
     * means {@link #DEFAULT_PAGE_SIZE}.
     */
    public static int safeSize(Integer size) {
        return Math.min(Math.max(1, size != null ? size : DEFAULT_PAGE_SIZE), MAX_PAGE_SIZE);
    }

    /**
     * Takes the page window from an already-sorted list.
     *
     * <p>The offset is computed in {@code long} because page and size are
     * caller-supplied on a public endpoint: {@code int} multiplication
     * overflows for large pages and would index the list with a negative
     * start. A page past the end yields an empty page, not an exception.
     *
     * @param sortedContent results already ordered by the caller's criterion
     * @param page          caller-supplied zero-based page number (nullable)
     * @param size          caller-supplied page size (nullable)
     * @return the page window with accurate totals
     */
    public static <T> PageResponse<T> of(List<T> sortedContent, Integer page, Integer size) {
        int safePage = safePage(page);
        int safeSize = safeSize(size);

        long offset = (long) safePage * safeSize;
        int start = (int) Math.min(Math.max(offset, 0), sortedContent.size());
        int end = (int) Math.min(start + (long) safeSize, sortedContent.size());
        List<T> pageContent = sortedContent.subList(start, end);

        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<T> resultPage = new PageImpl<>(pageContent, pageable, sortedContent.size());

        return envelope(resultPage);
    }

    /**
     * The empty page: no content, accurate (clamped) page metadata.
     */
    public static <T> PageResponse<T> empty(Integer page, Integer size) {
        Pageable pageable = PageRequest.of(safePage(page), safeSize(size));
        Page<T> empty = new PageImpl<>(List.of(), pageable, 0);
        return envelope(empty);
    }

    private static <T> PageResponse<T> envelope(Page<T> resultPage) {
        return PageResponse.<T>builder()
                .content(resultPage.getContent())
                .page(resultPage.getNumber())
                .size(resultPage.getSize())
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages())
                .first(resultPage.isFirst())
                .last(resultPage.isLast())
                .empty(resultPage.isEmpty())
                .build();
    }
}
