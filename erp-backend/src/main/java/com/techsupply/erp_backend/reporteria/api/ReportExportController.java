package com.techsupply.erp_backend.reporteria.api;

import com.techsupply.erp_backend.reporteria.application.*;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports/export")
public class ReportExportController {
    private final ReportExportService service;
    public ReportExportController(ReportExportService service) {this.service=service;}
    @GetMapping
    public ResponseEntity<byte[]> export(@RequestParam String report,@RequestParam String format,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate asOf,
        @RequestParam(required=false) Integer days,@RequestParam(required=false) Long warehouseId,
        @RequestParam(required=false) Long productId,@RequestParam(required=false) Long customerId,
        @RequestParam(required=false) Long periodId,@RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="20") int size,@RequestParam(required=false) String sort,
        @RequestParam(defaultValue="asc") String direction) {
        if(sort==null) sort=report.equals("sales/customer-profitability")?"customerId":report.equals("finance/trial-balance")?"codigoCuenta":"productId";
        var file=service.export(report,format,new AnalyticalReportsService.Filter(from,to,asOf,days,warehouseId,productId,customerId,page,size,sort,direction),periodId);
        return ResponseEntity.ok().header("Content-Type",file.contentType()).header("Content-Disposition","attachment; filename="+file.filename())
            .header("Cache-Control","no-store").body(file.bytes());
    }
}
