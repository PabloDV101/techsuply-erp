package com.techsupply.erp_backend.compras;

import com.techsupply.erp_backend.compras.api.FacturacionProveedorReportController;
import com.techsupply.erp_backend.compras.application.FacturacionProveedorReportService;
import com.techsupply.erp_backend.compras.application.dto.FacturacionProveedorReportResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import static org.assertj.core.api.Assertions.assertThat;

class FacturacionProveedorReportOpenApiContractTest {
    @SuppressWarnings("unchecked") private static Map<String, Object> map(Object value) { return (Map<String, Object>) value; }

    @Test void contratoCoincideConRutaRespuestaYPermisos() throws Exception {
        var options = new LoaderOptions(); options.setAllowDuplicateKeys(false); options.setMaxAliasesForCollections(300);
        Path path = Path.of(System.getProperty("basedir", "."), "..", "docs", "api", "openapi-reporteria.yaml");
        Map<String, Object> api;
        try (var stream = Files.newInputStream(path)) { api = new Yaml(new SafeConstructor(options)).load(stream); }
        String route = "/reports/purchases/invoiced";
        var operation = map(map(map(api.get("paths")).get(route)).get("get"));
        assertThat(operation.get("x-implementation-status")).isEqualTo("implemented");
        assertThat(FacturacionProveedorReportController.class.getAnnotation(RequestMapping.class).value())
            .containsExactly("/api/v1" + route);
        var controller = FacturacionProveedorReportController.class.getDeclaredMethod("consultar", java.time.LocalDate.class, java.time.LocalDate.class);
        var service = FacturacionProveedorReportService.class.getDeclaredMethod("consultar", java.time.LocalDate.class, java.time.LocalDate.class);
        for (var method : new java.lang.reflect.Method[] { controller, service })
            assertThat(AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class).value())
                .isEqualTo("hasAuthority('COMPRAS_FACTURAS_PROVEEDOR_LEER')");
        var schemas = map(map(api.get("components")).get("schemas"));
        assertThat(map(map(schemas.get("SupplierInvoicedReport")).get("properties")).keySet())
            .containsExactlyInAnyOrderElementsOf(Arrays.stream(FacturacionProveedorReportResponse.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).collect(Collectors.toSet()));
    }
}
