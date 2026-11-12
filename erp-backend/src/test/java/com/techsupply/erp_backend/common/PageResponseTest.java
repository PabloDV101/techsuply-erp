package com.techsupply.erp_backend.common;

import com.techsupply.erp_backend.common.api.PageResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {
    private record Cliente(long id, String nombre, boolean activo) {}
    private record ClienteResumen(long id, String nombre) {}
    private record ClienteEstado(long id, String estado) {}

    @Test void conservaMetadatosAlSustituirLaProyeccion() {
        var source = new PageImpl<>(List.of(new Cliente(7, "Alfa", true), new Cliente(8, "Beta", false)),
            PageRequest.of(1, 2), 5);

        var summary = PageResponse.from(source, c -> new ClienteResumen(c.id(), c.nombre()));
        var status = PageResponse.from(source, c -> new ClienteEstado(c.id(), c.activo() ? "activo" : "inactivo"));

        assertThat(summary.content()).containsExactly(new ClienteResumen(7, "Alfa"), new ClienteResumen(8, "Beta"));
        assertThat(status.content()).containsExactly(new ClienteEstado(7, "activo"), new ClienteEstado(8, "inactivo"));
        assertThat(summary.page()).isEqualTo(1);
        assertThat(summary.size()).isEqualTo(2);
        assertThat(summary.totalElements()).isEqualTo(5);
        assertThat(summary.totalPages()).isEqualTo(3);
        assertThat(status.totalElements()).isEqualTo(summary.totalElements());
    }
}
