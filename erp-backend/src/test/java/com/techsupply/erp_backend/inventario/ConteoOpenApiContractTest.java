package com.techsupply.erp_backend.inventario;

import com.techsupply.erp_backend.inventario.api.ConteoInventarioController;
import com.techsupply.erp_backend.inventario.application.dto.CapturarConteoRequest;
import com.techsupply.erp_backend.inventario.application.dto.ConteoResponse;
import com.techsupply.erp_backend.inventario.application.dto.CrearConteoRequest;
import com.techsupply.erp_backend.inventario.domain.model.EstadoConteo;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestParam;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import static org.assertj.core.api.Assertions.*;

class ConteoOpenApiContractTest {
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value) { return (Map<String,Object>) value; }
    @Test void conteoPathsAndSchemasMatchImplementedDtos() throws Exception {
        LoaderOptions opts=new LoaderOptions(); opts.setAllowDuplicateKeys(false); Map<String,Object> root;
        try(var stream=Files.newInputStream(Path.of(System.getProperty("basedir", "."),"..","docs","api","openapi-sprint-2-inventario.yaml"))) {
            root=new Yaml(new SafeConstructor(opts)).load(stream);
        }
        assertThat(root.get("x-implementation-status")).isEqualTo("implemented");
        Map<String,Object> paths=map(root.get("paths"));
        for(String path:List.of("/inventory/counts","/inventory/counts/{id}","/inventory/counts/{id}/lines/{productoId}","/inventory/counts/{id}/close","/inventory/counts/{id}/cancel"))
            assertThat(map(paths.get(path)).get("x-implementation-status")).isEqualTo("implemented");
        Map<String,Object> components=map(root.get("components")), schemas=map(components.get("schemas"));
        for(var entry:Map.of("ConteoCreate",CrearConteoRequest.class,"ConteoLineaWrite",CapturarConteoRequest.class,"Conteo",ConteoResponse.class,"ConteoLinea",ConteoResponse.Linea.class).entrySet()) {
            Set<String> actual=Arrays.stream(entry.getValue().getRecordComponents()).map(RecordComponent::getName).collect(Collectors.toSet());
            assertThat(map(map(schemas.get(entry.getKey())).get("properties")).keySet()).containsExactlyInAnyOrderElementsOf(actual);
        }
        var method=Arrays.stream(ConteoInventarioController.class.getDeclaredMethods()).filter(m->m.getName().equals("listar")).findFirst().orElseThrow();
        Set<String> actual=Arrays.stream(method.getParameters()).filter(p->p.isAnnotationPresent(RequestParam.class)).map(p->p.getName()).collect(Collectors.toSet());
        Set<String> declared=((List<?>)map(map(paths.get("/inventory/counts")).get("get")).get("parameters")).stream().map(ConteoOpenApiContractTest::map).map(item->{
            String ref=item.get("$ref").toString(); return map(map(components.get("parameters")).get(ref.substring(ref.lastIndexOf('/')+1))).get("name").toString();
        }).collect(Collectors.toSet());
        assertThat(declared).containsExactlyInAnyOrderElementsOf(actual);
        var estado=map(map(map(components.get("parameters")).get("EstadoConteo")).get("schema"));
        assertThat(((List<?>)estado.get("enum")).stream().map(Object::toString).toList()).containsExactlyInAnyOrderElementsOf(Arrays.stream(EstadoConteo.values()).map(Enum::name).toList());
    }
}
