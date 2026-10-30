package com.techsupply.erp_backend.maestros.application;

import com.techsupply.erp_backend.maestros.domain.model.Departamento;
import com.techsupply.erp_backend.maestros.domain.model.Almacen;
import com.techsupply.erp_backend.maestros.domain.model.Producto;
import com.techsupply.erp_backend.maestros.domain.model.Proveedor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Boundary for purchase order creation; caller owns the transaction and authorization. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class CatalogoParaCompras {
    private final EntityManager em;
    public CatalogoParaCompras(EntityManager em) { this.em = em; }
    public Proveedor proveedorActivo(Long id) {
        Proveedor supplier = em.find(Proveedor.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (supplier == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Proveedor no encontrado");
        if (!supplier.isActivo()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Proveedor inactivo");
        return supplier;
    }
    public Departamento departamento(Long id) {
        Departamento department = em.find(Departamento.class, id);
        if (department == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Departamento no encontrado");
        return department;
    }
    public Producto productoActivo(Long id) {
        Producto product = em.find(Producto.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (product == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado");
        if (!product.isActivo()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Producto inactivo");
        return product;
    }
    public Almacen almacenActivo(Long id) {
        Almacen warehouse = em.find(Almacen.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (warehouse == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Almacén no encontrado");
        if (!warehouse.isActivo()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Almacén inactivo");
        return warehouse;
    }
}
