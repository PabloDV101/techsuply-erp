package com.techsupply.erp_backend.contabilidad.application.dto;

import java.util.List;

public record ConfiguracionCobroResponse(List<Medio> medios) {
    public ConfiguracionCobroResponse { medios = List.copyOf(medios); }
    public record Medio(String medio, Long cuentaFondosId, String codigoFondos, Long cuentaAnticiposId, String codigoAnticipos) {}
}
