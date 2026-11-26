package com.techsupply.erp_backend.compras;

import com.techsupply.erp_backend.compras.api.FacturaProveedorController;
import com.techsupply.erp_backend.compras.application.FacturaProveedorService;
import com.techsupply.erp_backend.common.api.PageResponse;
import java.lang.reflect.ParameterizedType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import static org.assertj.core.api.Assertions.assertThat;

class CuentasPorPagarOpenApiContractTest {
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value) { return (Map<String,Object>) value; }

    @Test void listadoPaginadoEstaDocumentadoYProtegido() throws Exception {
        var options=new LoaderOptions(); options.setAllowDuplicateKeys(false); options.setMaxAliasesForCollections(300);
        Path contract=Path.of(System.getProperty("basedir","."),"..","docs","api","openapi-sprint-4-contabilidad.yaml");
        Map<String,Object> document;
        try(var stream=Files.newInputStream(contract)) { document=new Yaml(new SafeConstructor(options)).load(stream); }
        var path=map(map(document.get("paths")).get("/accounting/payables"));
        assertThat(map(path.get("get")).get("x-implementation-status")).isEqualTo("implemented");
        var supplierInvoices=map(map(document.get("paths")).get("/supplier-invoices"));
        assertThat(map(supplierInvoices.get("get")).get("x-implementation-status")).isEqualTo("implemented");
        assertThat(FacturaProveedorController.class.getAnnotation(RequestMapping.class).value()).containsExactly("/api/v1");
        var method=java.util.Arrays.stream(FacturaProveedorController.class.getDeclaredMethods())
            .filter(m->m.isAnnotationPresent(GetMapping.class)
                && java.util.Arrays.asList(m.getAnnotation(GetMapping.class).value()).contains("/accounting/payables"))
            .findFirst().orElseThrow();
        assertThat(method.getGenericReturnType()).isInstanceOf(ParameterizedType.class);
        assertThat(AnnotatedElementUtils.findMergedAnnotation(method,PreAuthorize.class).value())
            .contains("CONTABILIDAD_CXP_LEER");
        assertThat(java.util.Arrays.stream(method.getParameters()).anyMatch(p->p.isAnnotationPresent(RequestParam.class)
            && p.getAnnotation(RequestParam.class).name().equals("proveedorId"))).isTrue();
        var service=FacturaProveedorService.class.getDeclaredMethod("listarCuentasPorPagar",Long.class,int.class,int.class);
        assertThat(AnnotatedElementUtils.findMergedAnnotation(service,PreAuthorize.class).value())
            .contains("CONTABILIDAD_CXP_LEER");
        assertThat(method.getReturnType()).isEqualTo(PageResponse.class);

        var invoiceList=java.util.Arrays.stream(FacturaProveedorController.class.getDeclaredMethods())
            .filter(m->m.isAnnotationPresent(GetMapping.class)
                && java.util.Arrays.asList(m.getAnnotation(GetMapping.class).value()).contains("/supplier-invoices"))
            .findFirst().orElseThrow();
        assertThat(invoiceList.getGenericReturnType()).isInstanceOf(ParameterizedType.class);
        assertThat(AnnotatedElementUtils.findMergedAnnotation(invoiceList,PreAuthorize.class).value())
            .contains("COMPRAS_FACTURAS_PROVEEDOR_LEER");
        assertThat(java.util.Arrays.stream(invoiceList.getParameters()).anyMatch(p->p.isAnnotationPresent(RequestParam.class)
            && p.getAnnotation(RequestParam.class).name().equals("proveedorId"))).isTrue();
        var invoiceService=FacturaProveedorService.class.getDeclaredMethod("listar",Long.class,int.class,int.class);
        assertThat(AnnotatedElementUtils.findMergedAnnotation(invoiceService,PreAuthorize.class).value())
            .contains("COMPRAS_FACTURAS_PROVEEDOR_LEER");
    }
}
