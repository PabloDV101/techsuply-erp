package com.techsupply.erp_backend.maestros.application.dto;

public record CategoriaResponse(Long id, String codigo, String nombre, String descripcion,
                                Long categoriaPadreId, boolean activa) {}
