package com.techsupply.erp_backend.reporteria.application;

import com.techsupply.erp_backend.audit.application.*;
import com.techsupply.erp_backend.contabilidad.application.*;
import com.techsupply.erp_backend.compras.application.FacturacionProveedorReportService;
import com.techsupply.erp_backend.ventas.application.FacturacionVentaReportService;
import com.techsupply.erp_backend.inventario.application.AlertaStockService;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.*;

@Service
public class ReportExportService {
    private final AnalyticalReportsService reports; private final ReportingPoliciesService policies;
    private final DashboardService dashboard; private final AuditTrail audit; private final ObjectMapper json;
    private final EstadosFinancierosService financial; private final ReportesContablesService accounting;
    private final FacturacionProveedorReportService purchases; private final FacturacionVentaReportService sales;
    private final ResultadoFinancieroService result; private final AlertaStockService stock;
    public ReportExportService(AnalyticalReportsService reports,ReportingPoliciesService policies,DashboardService dashboard,
        AuditTrail audit,ObjectMapper json,EstadosFinancierosService financial,ReportesContablesService accounting,
        FacturacionProveedorReportService purchases,FacturacionVentaReportService sales,ResultadoFinancieroService result,AlertaStockService stock) {
        this.reports=reports;this.policies=policies;this.dashboard=dashboard;this.audit=audit;this.json=json;
        this.financial=financial;this.accounting=accounting;this.purchases=purchases;this.sales=sales;this.result=result;this.stock=stock;
    }
    public record File(byte[] bytes,String contentType,String filename) {}
    @Transactional(isolation=Isolation.REPEATABLE_READ)
    @PreAuthorize("isAuthenticated()")
    public File export(String report,String format,AnalyticalReportsService.Filter filter,Long periodId) {
        if(!Set.of("csv","pdf","xlsx").contains(format)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Formato no admitido");
        if(report.startsWith("finance/") && (periodId==null || periodId<1)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Periodo requerido");
        Object data=switch(report) {
            case "dashboard" -> dashboard.consultar(filter.from(),filter.to(),periodId);
            case "inventory/low-stock" -> reports.lowStock(filter);
            case "inventory/inactive-products" -> reports.inactive(filter);
            case "inventory/turnover" -> reports.turnover(filter);
            case "inventory/abc" -> reports.abc(filter);
            case "sales/customer-profitability" -> reports.profitability(filter);
            case "finance/operating-margin" -> policies.margin(periodId);
            case "finance/income-statement" -> financial.resultados(periodId);
            case "finance/balance-sheet" -> financial.balance(periodId);
            case "finance/cash-flow" -> financial.flujo(periodId);
            case "finance/trial-balance" -> accounting.balancePrueba(periodId,filter.page(),filter.size(),filter.sort(),filter.direction());
            case "finance/result-summary" -> result.consultar(periodId);
            case "purchases/invoiced" -> purchases.consultar(filter.from(),filter.to());
            case "sales/invoiced" -> sales.consultar(filter.from(),filter.to());
            case "inventory/low-stock-summary" -> stock.resumir();
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Reporte no admitido");
        };
        var rows=new ArrayList<List<String>>(); rows.add(List.of("campo","valor"));rows.add(List.of("reporte",report));
        flatten("filtros",json.valueToTree(filter),rows);rows.add(List.of("periodId",periodId==null?"":periodId.toString()));
        flatten("resultado",json.valueToTree(data),rows);
        byte[] bytes; String contentType;
        try {
            switch(format) {
                case "csv" -> { bytes=csv(rows);contentType="text/csv;charset=UTF-8"; }
                case "xlsx" -> {bytes=xlsx(rows);contentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";}
                default -> {bytes=pdf(rows);contentType="application/pdf";}
            }
        } catch(IOException e) {throw new IllegalStateException("No se pudo generar el reporte",e);}
        audit.change(AuditAction.REPORT_FILE_GENERATED,null);
        return new File(bytes,contentType,"reporte."+format);
    }
    private void flatten(String path,JsonNode node,List<List<String>> rows) {
        if(node.isObject()) {for(var entry:node.properties()) flatten(path+"."+entry.getKey(),entry.getValue(),rows);}
        else if(node.isArray()) {if(node.isEmpty()) rows.add(List.of(path,"[]"));else for(int i=0;i<node.size();i++) flatten(path+"["+i+"]",node.get(i),rows);}
        else rows.add(List.of(path,node.isNull()?"":node.isNumber()?node.decimalValue().toPlainString():node.asText()));
    }
    private byte[] csv(List<List<String>> rows) {
        var out=new StringBuilder();
        for(var row:rows) {for(int i=0;i<row.size();i++) {if(i>0) out.append(',');String v=row.get(i);
            if(!v.isEmpty() && "=+-@\t\r\n".indexOf(v.charAt(0))>=0) v="'"+v;
            out.append('"').append(v.replace("\"","\"\"")).append('"');}out.append("\r\n");}
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }
    private byte[] xlsx(List<List<String>> rows) throws IOException {
        try(var book=new XSSFWorkbook();var out=new ByteArrayOutputStream()) {
            var sheet=book.createSheet("Reporte");int n=0;
            for(var row:rows) {var line=sheet.createRow(n++);for(int i=0;i<row.size();i++) line.createCell(i).setCellValue(row.get(i));}
            sheet.setColumnWidth(0,65*256);sheet.setColumnWidth(1,70*256);sheet.createFreezePane(0,1);book.write(out);return out.toByteArray();
        }
    }
    private byte[] pdf(List<List<String>> rows) throws IOException {
        try(var doc=new PDDocument();var out=new ByteArrayOutputStream();var fontStream=getClass().getResourceAsStream("/reporting/DejaVuSansMono.ttf")) {
            var font=PDType0Font.load(doc,Objects.requireNonNull(fontStream));
            var lines=new ArrayList<String>();lines.add("TechSupply - Reporte (pagina de consulta seleccionada)");
            for(var row:rows) {String value=String.join(": ",row).replaceAll("[\\p{Cntrl}]"," ");
                StringBuilder safe=new StringBuilder();
                value.codePoints().forEach(cp->{try {font.encode(new String(Character.toChars(cp)));safe.appendCodePoint(cp);}catch(Exception e){safe.append("[U+").append(Integer.toHexString(cp)).append(']');}});
                for(int offset=0;offset<safe.length();offset+=95) lines.add(safe.substring(offset,Math.min(offset+95,safe.length())));
            }
            for(int start=0;start<lines.size();start+=60) {
                var page=new PDPage();doc.addPage(page);
                try(var stream=new PDPageContentStream(doc,page)) {stream.beginText();stream.setFont(font,9);stream.setLeading(12);stream.newLineAtOffset(35,750);
                    for(int i=start;i<Math.min(start+60,lines.size());i++){stream.showText(lines.get(i));stream.newLine();}stream.endText();}
            }
            doc.save(out);return out.toByteArray();
        }
    }
}
