package com.techsupply.erp_backend.contabilidad;

import com.techsupply.erp_backend.contabilidad.api.ReportesContablesController;
import com.techsupply.erp_backend.contabilidad.application.dto.*;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import static org.assertj.core.api.Assertions.assertThat;

class ReportesContablesOpenApiContractTest {
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value) { return (Map<String,Object>) value; }
    private static Set<String> fields(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).collect(Collectors.toSet());
    }

    @Test void rutasPermisoYRespuestaCoincidenConLosContratos() throws Exception {
        var options = new LoaderOptions(); options.setAllowDuplicateKeys(false); options.setMaxAliasesForCollections(300);
        Path base = Path.of(System.getProperty("basedir", "."), "..", "docs", "api");
        Map<String,Object> accounting;
        try (var stream = Files.newInputStream(base.resolve("openapi-sprint-4-contabilidad.yaml"))) {
            accounting = new Yaml(new SafeConstructor(options)).load(stream);
        }
        var accountingPaths = map(accounting.get("paths"));
        String ledgerPath = "/accounting/periods/{periodId}/accounts/{accountId}/ledger";
        assertThat(map(accountingPaths.get(ledgerPath)).get("x-implementation-status")).isEqualTo("implemented");
        var ledgerMethod = Arrays.stream(ReportesContablesController.class.getDeclaredMethods())
            .filter(m -> m.getName().equals("libroMayor")).findFirst().orElseThrow();
        var mapping = AnnotatedElementUtils.findMergedAnnotation(ledgerMethod, RequestMapping.class);
        assertThat(mapping.path()).containsExactly(ledgerPath);
        assertThat(AnnotatedElementUtils.findMergedAnnotation(ledgerMethod, PreAuthorize.class).value())
            .contains("CONTABILIDAD_REPORTES_LEER");
        var schemas = map(map(accounting.get("components")).get("schemas"));
        assertThat(map(map(schemas.get("LibroMayor")).get("properties")).keySet()).containsExactlyInAnyOrderElementsOf(fields(LibroMayorResponse.class));
        assertThat(map(map(schemas.get("LibroMayorLinea")).get("properties")).keySet()).containsExactlyInAnyOrderElementsOf(fields(LibroMayorLineaResponse.class));

        Map<String,Object> reporting;
        try (var stream = Files.newInputStream(base.resolve("openapi-reporteria.yaml"))) {
            reporting = new Yaml(new SafeConstructor(options)).load(stream);
        }
        var reportingPaths = map(reporting.get("paths"));
        var trial = map(reportingPaths.get("/reports/finance/trial-balance"));
        assertThat(map(trial.get("get")).get("x-implementation-status")).isEqualTo("implemented");
        var reportSchemas = map(map(reporting.get("components")).get("schemas"));
        var trialMeta = map(map(reportSchemas.get("PageMeta")).get("properties")).keySet();
        @SuppressWarnings("unchecked") var allOf = (java.util.List<Object>) map(reportSchemas.get("TrialBalance")).get("allOf");
        var trialObject = allOf.stream().map(ReportesContablesOpenApiContractTest::map)
            .filter(s -> s.containsKey("properties")).findFirst().orElseThrow();
        var trialFields = map(trialObject.get("properties")).keySet();
        assertThat(java.util.stream.Stream.concat(trialMeta.stream(), trialFields.stream()).collect(Collectors.toSet()))
            .containsExactlyInAnyOrderElementsOf(fields(BalancePruebaResponse.class));
        assertThat(map(map(reportSchemas.get("TrialBalanceRow")).get("properties")).keySet())
            .containsExactlyInAnyOrderElementsOf(fields(BalancePruebaFilaResponse.class));
    }
}
