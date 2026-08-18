package com.codeguard.agent.api;

import com.codeguard.agent.service.SarifExportService;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 报告导出接口。
 */
@RestController
@RequestMapping("/api/reviews")
public class ReportController {

    private final SarifExportService sarifExportService;

    public ReportController(SarifExportService sarifExportService) {
        this.sarifExportService = sarifExportService;
    }

    @GetMapping(value = "/{reviewId}/sarif", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> sarif(@PathVariable String reviewId) {
        return sarifExportService.export(reviewId);
    }
}
