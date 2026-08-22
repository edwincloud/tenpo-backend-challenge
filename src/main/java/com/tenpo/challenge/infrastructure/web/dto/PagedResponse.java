package com.tenpo.challenge.infrastructure.web.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** Envoltorio simple de paginación para no exponer directamente el Page de Spring Data en la API. */
public record PagedResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
