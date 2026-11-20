package com.techsupply.erp_backend.contabilidad;

import com.techsupply.erp_backend.contabilidad.api.CobrosClientesController;
import com.techsupply.erp_backend.contabilidad.application.CobrosClientesService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import static org.assertj.core.api.Assertions.assertThat;

class CobrosClientesOpenApiContractTest {
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object o){return (Map<String,Object>)o;}
    @Test void apiPermisosYContratoEstanAlineados() throws Exception {
        var options=new LoaderOptions(); options.setAllowDuplicateKeys(false);
        Path path=Path.of(System.getProperty("basedir","."),"..","docs","api","openapi-sprint-4-contabilidad.yaml");
        Map<String,Object> doc;
        try(var in=Files.newInputStream(path)){doc=new Yaml(new SafeConstructor(options)).load(in);}
        var paths=map(doc.get("paths"));
        for(String route:Set.of("/accounting/customer-receipts","/accounting/customer-receipts/{id}",
                "/accounting/customer-receipts/{id}/confirm","/accounting/customer-receipts/{id}/applications",
                "/accounting/customer-receipts/accounts"))
            assertThat(paths).containsKey(route);
        assertThat(CobrosClientesController.class.getAnnotation(RequestMapping.class).value())
            .containsExactly("/api/v1/accounting/customer-receipts");
        for(String method:Set.of("registrar","obtener","confirmar","aplicar","configuracion","configurar"))
            assertThat(java.util.Arrays.stream(CobrosClientesService.class.getDeclaredMethods())
                .filter(m->m.getName().equals(method)).anyMatch(m->AnnotatedElementUtils.findMergedAnnotation(m,PreAuthorize.class)!=null)).isTrue();
    }
}
