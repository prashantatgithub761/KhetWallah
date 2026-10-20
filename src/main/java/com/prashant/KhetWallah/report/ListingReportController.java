package com.prashant.KhetWallah.report;

import com.prashant.KhetWallah.admin.AdminActionRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class ListingReportController {
    private final ListingReportService service;
    public ListingReportController(ListingReportService service) { this.service = service; }

    @PostMapping("/api/listings/{id}/reports") @ResponseStatus(HttpStatus.CREATED)
    public ListingReportService.Receipt submit(@PathVariable Long id, @Valid @RequestBody AdminActionRequest body, Principal principal) {
        return service.submit(id, body.reason(), principal.getName());
    }
    @GetMapping("/api/admin/reports")
    public Page<ListingReportService.ReportView> list(@RequestParam(defaultValue = "OPEN") ListingReport.Status status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, Principal principal) {
        return service.list(status, page, size, principal.getName());
    }
    @PostMapping("/api/admin/reports/{id}/dismiss")
    public ListingReportService.ReportView dismiss(@PathVariable Long id, @Valid @RequestBody AdminActionRequest body, Principal principal) {
        return service.resolve(id, false, body.reason(), principal.getName());
    }
    @PostMapping("/api/admin/reports/{id}/hide")
    public ListingReportService.ReportView hide(@PathVariable Long id, @Valid @RequestBody AdminActionRequest body, Principal principal) {
        return service.resolve(id, true, body.reason(), principal.getName());
    }
}
