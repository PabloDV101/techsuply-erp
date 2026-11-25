package com.techsupply.erp_backend.inventario.application.dto;

import java.time.Instant;

/** Conteo global de productos con alerta e instante de inicio de la consulta. */
public record ResumenAlertaStockResponse(Instant asOf, long productosEnAlerta) {}
