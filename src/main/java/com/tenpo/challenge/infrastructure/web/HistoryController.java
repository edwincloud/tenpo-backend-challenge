package com.tenpo.challenge.infrastructure.web;

import com.tenpo.challenge.application.CallHistoryService;
import com.tenpo.challenge.infrastructure.web.dto.CallRecordResponse;
import com.tenpo.challenge.infrastructure.web.dto.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@Tag(name = "History", description = "Historial paginado de llamadas realizadas a la API")
public class HistoryController {

    private final CallHistoryService callHistoryService;

    public HistoryController(CallHistoryService callHistoryService) {
        this.callHistoryService = callHistoryService;
    }

    @Operation(summary = "Consulta el historial de llamadas, paginado, ordenado por fecha descendente")
    @GetMapping("/api/v1/history")
    public Mono<PagedResponse<CallRecordResponse>> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return callHistoryService.getHistory(PageRequest.of(page, size))
                .map(result -> PagedResponse.from(result.map(CallRecordResponse::from)));
    }
}
