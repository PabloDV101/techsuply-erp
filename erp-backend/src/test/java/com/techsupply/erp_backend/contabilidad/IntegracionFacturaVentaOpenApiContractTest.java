package com.techsupply.erp_backend.contabilidad;

import com.techsupply.erp_backend.contabilidad.api.IntegracionFacturaVentaController;
import com.techsupply.erp_backend.contabilidad.application.IntegracionFacturaVentaService;
import com.techsupply.erp_backend.contabilidad.application.dto.*;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import static org.assertj.core.api.Assertions.assertThat;

class IntegracionFacturaVentaOpenApiContractTest {
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value) { return (Map<String,Object>) value; }
    private static Set<String> fields(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).collect(Collectors.toSet());
    }

    @Test void rutasPermisosYDtosCoincidenConOpenApi() throws Exception {
        var options = new LoaderOptions(); options.setAllowDuplicateKeys(false); options.setMaxAliasesForCollections(300);
        Path contract = Path.of(System.getProperty("basedir", "."), "..", "docs", "api", "openapi-sprint-4-contabilidad.yaml");
        Map<String,Object> document;
        try (var stream = Files.newInputStream(contract)) { document = new Yaml(new SafeConstructor(options)).load(stream); }
        var paths = map(document.get("paths"));
        assertThat(map(paths.get("/accounting/integrations/sales-invoices")).get("x-implementation-status")).isEqualTo("implemented");
        assertThat(map(paths.get("/accounting/integrations/sales-invoices/accounts")).get("x-implementation-status")).isEqualTo("implemented");
        assertThat(map(paths.get("/accounting/integrations/sales-invoices/{invoiceId}")).get("x-implementation-status")).isEqualTo("implemented");
        assertThat(map(paths.get("/accounting/integrations/sales-invoices/{invoiceId}/retry")).get("x-implementation-status")).isEqualTo("implemented");

        var controller = Arrays.stream(IntegracionFacturaVentaController.class.getDeclaredMethods()).toList();
        assertThat(controller.stream().anyMatch(m -> m.isAnnotationPresent(GetMapping.class) && Arrays.asList(m.getAnnotation(GetMapping.class).value()).contains("/{invoiceId}"))).isTrue();
        assertThat(controller.stream().anyMatch(m -> m.isAnnotationPresent(PostMapping.class) && Arrays.asList(m.getAnnotation(PostMapping.class).value()).contains("/{invoiceId}/retry"))).isTrue();
        var service = Arrays.stream(IntegracionFacturaVentaService.class.getDeclaredMethods()).toList();
        assertThat(preauthorize(service, "pendientes")).contains("CONTABILIDAD_INTEGRACIONES_LEER");
        assertThat(preauthorize(service, "obtener")).contains("CONTABILIDAD_INTEGRACIONES_LEER");
        assertThat(preauthorize(service, "configurar")).contains("CONTABILIDAD_INTEGRACIONES_ADMIN");
        assertThat(preauthorize(service, "reintentar")).contains("CONTABILIDAD_INTEGRACIONES_REINTENTAR")
            .contains("CONTABILIDAD_ASIENTOS_REGISTRAR").contains("CONTABILIDAD_ASIENTOS_CONTABILIZAR");

        var schemas = map(map(document.get("components")).get("schemas"));
        for (var pair : List.of(Map.entry("IntegracionFacturaVenta", IntegracionFacturaVentaResponse.class),
                Map.entry("ConfiguracionFacturaVenta", ConfiguracionFacturaVentaResponse.class),
                Map.entry("ConfiguracionFacturaVentaWrite", ConfiguracionFacturaVentaRequest.class),
                Map.entry("ReintentoFacturaVenta", ReintentoFacturaVentaRequest.class))) {
            assertThat(map(map(schemas.get(pair.getKey())).get("properties")).keySet())
                .containsExactlyInAnyOrderElementsOf(fields(pair.getValue()));
        }
    }

    private static String preauthorize(List<java.lang.reflect.Method> methods, String name) {
        var method = methods.stream().filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
        return AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class).value();
    }
}
