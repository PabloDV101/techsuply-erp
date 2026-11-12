package com.techsupply.erp_backend.inventario;

import com.techsupply.erp_backend.inventario.api.AlertaStockController;
import com.techsupply.erp_backend.inventario.application.dto.AlertaStockResponse;
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
import static org.assertj.core.api.Assertions.assertThat;

class AlertaStockOpenApiContractTest {
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object v) { return (Map<String,Object>)v; }
    @Test void contratoCoincideConRespuestaYParametros() throws Exception {
        LoaderOptions options=new LoaderOptions(); options.setAllowDuplicateKeys(false); Map<String,Object> root;
        try(var stream=Files.newInputStream(Path.of(System.getProperty("basedir","."),"..","docs","api","openapi-sprint-2-inventario.yaml"))) {root=new Yaml(new SafeConstructor(options)).load(stream);}
        assertThat(root.get("x-implementation-status")).isEqualTo("implemented");
        var path=map(map(root.get("paths")).get("/inventory/low-stock-alerts")); assertThat(path.get("x-implementation-status")).isEqualTo("implemented");
        var components=map(root.get("components"));var schema=map(map(components.get("schemas")).get("AlertaStock"));
        var fields=Arrays.stream(AlertaStockResponse.class.getRecordComponents()).map(RecordComponent::getName).collect(Collectors.toSet());
        assertThat(map(schema.get("properties")).keySet()).containsExactlyInAnyOrderElementsOf(fields);
        var method=Arrays.stream(AlertaStockController.class.getDeclaredMethods()).filter(m->m.getName().equals("listar")).findFirst().orElseThrow();
        var actual=Arrays.stream(method.getParameters()).filter(p->p.isAnnotationPresent(RequestParam.class)).map(p->p.getName()).collect(Collectors.toSet());
        var declared=((List<?>)map(path.get("get")).get("parameters")).stream().map(AlertaStockOpenApiContractTest::map).map(item->{String ref=item.get("$ref").toString();return map(map(components.get("parameters")).get(ref.substring(ref.lastIndexOf('/')+1))).get("name").toString();}).collect(Collectors.toSet());
        assertThat(declared).containsExactlyInAnyOrderElementsOf(actual);
        assertThat(map(map(path.get("get")).get("responses"))).containsKeys("200","400","401","403");
    }
}
