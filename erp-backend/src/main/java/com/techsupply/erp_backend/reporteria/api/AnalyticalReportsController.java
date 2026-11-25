package com.techsupply.erp_backend.reporteria.api;
import com.techsupply.erp_backend.reporteria.application.*;
import com.techsupply.erp_backend.common.api.PageResponse;
import java.time.LocalDate;
import java.util.Map;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports")
public class AnalyticalReportsController {
 private final AnalyticalReportsService reports; private final ReportingPoliciesService policies;
 public AnalyticalReportsController(AnalyticalReportsService reports,ReportingPoliciesService policies) {this.reports=reports;this.policies=policies;}
 @GetMapping("/inventory/low-stock")
 public PageResponse<Map<String,Object>> lowStock(
@RequestParam(required=false) Long warehouseId,
 @RequestParam(required=false) Long productId,
 @RequestParam(required=false) Long customerId,
 @RequestParam(defaultValue="0") int page,
 @RequestParam(defaultValue="20") int size,
 @RequestParam(defaultValue="productId") String sort,
 @RequestParam(defaultValue="asc") String direction) {return reports.lowStock(new AnalyticalReportsService.Filter(null,null,null,null,warehouseId,productId,customerId,page,size,sort,direction));}
 @GetMapping("/inventory/inactive-products")
 public PageResponse<Map<String,Object>> inactive(
@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate asOf,
 @RequestParam Integer days,
 @RequestParam(required=false) Long warehouseId,
 @RequestParam(required=false) Long productId,
 @RequestParam(required=false) Long customerId,
 @RequestParam(defaultValue="0") int page,
 @RequestParam(defaultValue="20") int size,
 @RequestParam(defaultValue="productId") String sort,
 @RequestParam(defaultValue="asc") String direction) {return reports.inactive(new AnalyticalReportsService.Filter(null,null,asOf,days,warehouseId,productId,customerId,page,size,sort,direction));}
 @GetMapping("/inventory/turnover")
 public PageResponse<Map<String,Object>> turnover(
@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
 @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
 @RequestParam(required=false) Long warehouseId,
 @RequestParam(required=false) Long productId,
 @RequestParam(required=false) Long customerId,
 @RequestParam(defaultValue="0") int page,
 @RequestParam(defaultValue="20") int size,
 @RequestParam(defaultValue="productId") String sort,
 @RequestParam(defaultValue="asc") String direction) {return reports.turnover(new AnalyticalReportsService.Filter(from,to,null,null,warehouseId,productId,customerId,page,size,sort,direction));}
 @GetMapping("/inventory/abc")
 public PageResponse<Map<String,Object>> abc(
@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
 @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
 @RequestParam(required=false) Long warehouseId,
 @RequestParam(required=false) Long productId,
 @RequestParam(required=false) Long customerId,
 @RequestParam(defaultValue="0") int page,
 @RequestParam(defaultValue="20") int size,
 @RequestParam(defaultValue="productId") String sort,
 @RequestParam(defaultValue="asc") String direction) {return reports.abc(new AnalyticalReportsService.Filter(from,to,null,null,warehouseId,productId,customerId,page,size,sort,direction));}
 @GetMapping("/sales/customer-profitability")
 public PageResponse<Map<String,Object>> profitability(
@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
 @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
 @RequestParam(required=false) Long warehouseId,
 @RequestParam(required=false) Long productId,
 @RequestParam(required=false) Long customerId,
 @RequestParam(defaultValue="0") int page,
 @RequestParam(defaultValue="20") int size,
 @RequestParam(defaultValue="customerId") String sort,
 @RequestParam(defaultValue="asc") String direction) {return reports.profitability(new AnalyticalReportsService.Filter(from,to,null,null,warehouseId,productId,customerId,page,size,sort,direction));}
 @PutMapping("/inventory/minimums")
 public ReportingPoliciesService.Minimum minimum(@Valid @RequestBody ReportingPoliciesService.Minimum request) {return policies.minimum(request);}
 @PostMapping("/finance/operating-classifications")
 @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
 public Map<String,Object> classify(@Valid @RequestBody ReportingPoliciesService.Classification request) {return policies.classify(request);}
 @GetMapping("/finance/operating-margin")
 public Map<String,Object> margin(@RequestParam Long periodId) {return policies.margin(periodId);}
 @GetMapping("/inventory/minimums")
 public java.util.List<Map<String,Object>> minimums(@RequestParam Long productId) {return policies.minimums(productId);}
 @GetMapping("/finance/operating-classifications")
 public java.util.List<Map<String,Object>> classifications(@RequestParam Long accountId) {return policies.classifications(accountId);}
 @PostMapping("/finance/operating-classifications/{id}/void")
 @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
 public void voidClassification(@PathVariable Long id,@Valid @RequestBody ReportingPoliciesService.VoidReason reason) {policies.voidClassification(id,reason);}
}
