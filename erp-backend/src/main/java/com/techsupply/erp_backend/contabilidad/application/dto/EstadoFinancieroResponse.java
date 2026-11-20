package com.techsupply.erp_backend.contabilidad.application.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
public record EstadoFinancieroResponse(Long periodoId, LocalDate inicio, LocalDate fin, String estado, String detalle, List<Rubro> rubros) {
    public record Rubro(String nombre, BigDecimal importe) {}
}
