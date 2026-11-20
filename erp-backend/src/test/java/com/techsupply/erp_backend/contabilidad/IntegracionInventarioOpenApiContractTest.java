package com.techsupply.erp_backend.contabilidad;

import com.techsupply.erp_backend.contabilidad.api.IntegracionInventarioController;
import com.techsupply.erp_backend.contabilidad.application.IntegracionInventarioContableService;
import com.techsupply.erp_backend.contabilidad.application.dto.ConfiguracionCuentasInventarioRequest;
import com.techsupply.erp_backend.contabilidad.application.dto.ConfiguracionCuentasInventarioResponse;
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

class IntegracionInventarioOpenApiContractTest {
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value) { return (Map<String,Object>) value; }
    private static Set<String> fields(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).collect(Collectors.toSet());
    }

    @Test void rutasPermisosYEsquemasCoincidenConOpenApi() throws Exception {
        var options = new LoaderOptions(); options.setAllowDuplicateKeys(false); options.setMaxAliasesForCollections(300);
        Path path = Path.of(System.getProperty("basedir", "."), "..", "docs", "api", "openapi-sprint-4-contabilidad.yaml");
        Map<String,Object> document;
        try (var stream = Files.newInputStream(path)) { document = new Yaml(new SafeConstructor(options)).load(stream); }
        var paths = map(document.get("paths"));
        var contract = map(paths.get("/accounting/inventory-account-mappings"));
        assertThat(contract.get("x-implementation-status")).isEqualTo("implemented");
        var methods = Arrays.stream(IntegracionInventarioController.class.getDeclaredMethods()).toList();
        assertThat(methods.stream().anyMatch(m -> m.isAnnotationPresent(GetMapping.class))).isTrue();
        assertThat(methods.stream().anyMatch(m -> m.isAnnotationPresent(PutMapping.class))).isTrue();
        var service = Arrays.stream(IntegracionInventarioContableService.class.getDeclaredMethods()).toList();
        assertThat(preauthorize(service, "configuracion")).contains("CONTABILIDAD_INVENTARIO_LEER");
        assertThat(preauthorize(service, "configurar")).contains("CONTABILIDAD_INVENTARIO_CONFIGURAR");
        var schemas = map(map(document.get("components")).get("schemas"));
        for (var pair : List.of(Map.entry("ConfiguracionCuentasInventarioWrite", ConfiguracionCuentasInventarioRequest.class),
                Map.entry("ConfiguracionCuentasInventario", ConfiguracionCuentasInventarioResponse.class)))
            assertThat(map(map(schemas.get(pair.getKey())).get("properties")).keySet())
                .containsExactlyInAnyOrderElementsOf(fields(pair.getValue()));
    }

    private static String preauthorize(List<java.lang.reflect.Method> methods, String name) {
        var method = methods.stream().filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
        return AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class).value();
    }
}
