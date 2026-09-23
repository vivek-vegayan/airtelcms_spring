package com.vegayan.airtelmanagement.common.util;

import com.vegayan.airtelmanagement.common.dto.PageResponseDto;
import org.springframework.data.domain.Page;

public class PaginationUtils {

    private PaginationUtils() {
    }

    public static <T> PageResponseDto<T> buildPageResponse(Page<T> page) {
        return new PageResponseDto<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }

    public static <T> PageResponseDto<T> buildPageResponse(
            java.util.List<T> content,
            org.springframework.data.domain.Pageable pageable,
            long totalElements
    ) {

        int totalPages = (int) Math.ceil((double) totalElements / pageable.getPageSize());

        return new PageResponseDto<>(
                content,
                pageable.getPageNumber(),
                pageable.getPageSize(),
                totalElements,
                totalPages,
                pageable.getPageNumber() + 1 >= totalPages
        );
    }
}
