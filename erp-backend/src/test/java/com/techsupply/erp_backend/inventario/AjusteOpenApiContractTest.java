package com.techsupply.erp_backend.inventario;

import com.techsupply.erp_backend.inventario.api.AjusteInventarioController;
import com.techsupply.erp_backend.inventario.application.dto.AjusteResponse;
import com.techsupply.erp_backend.inventario.application.dto.CrearAjusteRequest;
import com.techsupply.erp_backend.inventario.application.dto.RechazarAjusteRequest;
import com.techsupply.erp_backend.inventario.domain.model.EstadoAjuste;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestParam;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import static org.assertj.core.api.Assertions.*;

class AjusteOpenApiContractTest {
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value) { return (Map<String,Object>) value; }
    @Test void adjustmentSchemasAndImplementedRoutesMatch() throws Exception {
        LoaderOptions options=new LoaderOptions(); options.setAllowDuplicateKeys(false); Map<String,Object> root;
        try(var stream=Files.newInputStream(Path.of(System.getProperty("basedir", "."),"..","docs","api","openapi-sprint-2-inventario.yaml"))) {
            root=new Yaml(new SafeConstructor(options)).load(stream);
        }
        assertThat(root.get("x-implementation-status")).isEqualTo("implemented");
        var paths=map(root.get("paths"));
        assertThat(map(paths.get("/inventory/adjustments")).get("x-implementation-status")).isEqualTo("implemented");
        assertThat(map(paths.get("/inventory/adjustments/{id}")).get("x-implementation-status")).isEqualTo("implemented");
        assertThat(map(paths.get("/inventory/adjustments/{id}/approve")).get("x-implementation-status")).isEqualTo("implemented");
        assertThat(map(paths.get("/inventory/adjustments/{id}/reject")).get("x-implementation-status")).isEqualTo("implemented");
        assertThat(map(paths.get("/inventory/adjustments/{id}/apply")).get("x-implementation-status")).isEqualTo("implemented");
        var components=map(root.get("components")); var schemas=map(components.get("schemas"));
        for(var e:Map.of("AjusteCreate",CrearAjusteRequest.class,"AjusteLineaWrite",CrearAjusteRequest.Linea.class,"Ajuste",AjusteResponse.class,"AjusteLinea",AjusteResponse.Linea.class,"Rechazo",RechazarAjusteRequest.class).entrySet()) {
            var actual=Arrays.stream(e.getValue().getRecordComponents()).map(RecordComponent::getName).collect(Collectors.toSet());
            assertThat(map(map(schemas.get(e.getKey())).get("properties")).keySet()).containsExactlyInAnyOrderElementsOf(actual);
        }
        var method=Arrays.stream(AjusteInventarioController.class.getDeclaredMethods()).filter(m->m.getName().equals("listar")).findFirst().orElseThrow();
        Set<String> actual=Arrays.stream(method.getParameters()).filter(p->p.isAnnotationPresent(RequestParam.class)).map(p->p.getName()).collect(Collectors.toSet());
        Set<String> declared=((List<?>)map(map(paths.get("/inventory/adjustments")).get("get")).get("parameters")).stream().map(AjusteOpenApiContractTest::map).map(item->{
            String ref=item.get("$ref").toString();return map(map(components.get("parameters")).get(ref.substring(ref.lastIndexOf('/')+1))).get("name").toString();
        }).collect(Collectors.toSet());
        assertThat(declared).containsExactlyInAnyOrderElementsOf(actual);
        var state=map(map(map(components.get("parameters")).get("EstadoAjuste")).get("schema"));
        assertThat(((List<?>)state.get("enum")).stream().map(Object::toString).toList()).containsExactlyInAnyOrderElementsOf(Arrays.stream(EstadoAjuste.values()).map(Enum::name).toList());
    }
}
