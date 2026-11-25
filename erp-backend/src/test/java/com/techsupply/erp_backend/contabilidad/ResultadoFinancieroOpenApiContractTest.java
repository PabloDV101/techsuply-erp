package com.techsupply.erp_backend.contabilidad;

import com.techsupply.erp_backend.contabilidad.api.ResultadoFinancieroController;
import com.techsupply.erp_backend.contabilidad.application.ResultadoFinancieroService;
import com.techsupply.erp_backend.contabilidad.application.dto.ResultadoFinancieroResponse;
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

class ResultadoFinancieroOpenApiContractTest {
    @SuppressWarnings("unchecked") private static Map<String, Object> map(Object value) { return (Map<String, Object>) value; }

    @Test void contratoCoincideConRutaRespuestaYPermisos() throws Exception {
        var options = new LoaderOptions(); options.setAllowDuplicateKeys(false); options.setMaxAliasesForCollections(300);
        Path path = Path.of(System.getProperty("basedir", "."), "..", "docs", "api", "openapi-reporteria.yaml");
        Map<String, Object> api;
        try (var stream = Files.newInputStream(path)) { api = new Yaml(new SafeConstructor(options)).load(stream); }
        String route = "/reports/finance/result-summary";
        var operation = map(map(map(api.get("paths")).get(route)).get("get"));
        assertThat(operation.get("x-implementation-status")).isEqualTo("implemented");
        assertThat(ResultadoFinancieroController.class.getAnnotation(RequestMapping.class).value()).containsExactly("/api/v1" + route);
        var controller = ResultadoFinancieroController.class.getDeclaredMethod("consultar", Long.class);
        var service = ResultadoFinancieroService.class.getDeclaredMethod("consultar", Long.class);
        for (var method : new java.lang.reflect.Method[] { controller, service })
            assertThat(AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class).value())
                .isEqualTo("hasAuthority('CONTABILIDAD_REPORTES_LEER')");
        var schemas = map(map(api.get("components")).get("schemas"));
        assertThat(map(map(schemas.get("AccountingResultSummary")).get("properties")).keySet())
            .containsExactlyInAnyOrderElementsOf(Arrays.stream(ResultadoFinancieroResponse.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).collect(Collectors.toSet()));
    }
}
