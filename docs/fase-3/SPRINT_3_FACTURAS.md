# Sprint 3, parte 12 — Factura comercial interna

La API permite emitir explícitamente y consultar una factura comercial interna por pedido totalmente despachado. El usuario aprobó que la fecha de vencimiento sea la misma fecha de emisión de negocio, en `America/Mexico_City`. El documento se entrega mediante JSON: no genera PDF, no es CFDI, no se timbra ante el SAT y no registra pago.

## Emisión y reglas acordadas

`POST /api/v1/sales/invoices` exige `VENTAS_FACTURAS_EMITIR`, un pedido y una clave idempotente. El pedido debe estar en `DESPACHADO`, con sus líneas totalmente despachadas y sin reserva; además cada cantidad debe estar respaldada por despachos confirmados y sus movimientos de salida. Esto evita facturar una cabecera marcada como despachada sin evidencia de las salidas. La emisión copia cantidades, precios, descuentos, impuestos e importes pactados, calcula base e impuesto por línea con `HALF_UP` a dos decimales y comprueba los totales del pedido. Cambios posteriores del catálogo no sustituyen el precio acordado. Se permite facturar un pedido ya despachado aunque después se haya desactivado un cliente o producto.

La primera solicitud devuelve `201` y `Location`. Un reintento con la misma clave normalizada, pedido y actor devuelve la factura existente con `200`. La misma clave con otro contenido o actor, o una clave distinta para un pedido ya facturado, devuelve `409`. La unicidad de pedido y clave está respaldada por restricciones SQL y bloqueos transaccionales. La secuencia de folios puede dejar huecos si una transacción se revierte.

`GET /api/v1/sales/invoices` y `GET /api/v1/sales/invoices/{id}` requieren `VENTAS_FACTURAS_LEER`; el listado se pagina y admite `clienteId`. La consulta por página recupera las líneas en una consulta agrupada. La API expone solo facturas `EMITIDA` y no modifica stock ni kardex.

## Integridad y migración

La migración [V13](../../erp-backend/src/main/resources/db/migration/V13__sales_commercial_invoices.sql) crea las dos tablas, secuencia, índices, permisos y restricciones. La cabecera se prepara como `BORRADOR` únicamente dentro de la transacción: un trigger diferido impide confirmarla en ese estado. Los triggers validan el pedido, salidas, líneas e importes y protegen las facturas emitidas y sus detalles frente a DML ordinario. La emisión, detalles y auditoría se confirman o revierten juntos. Un propietario o administrador puede alterar los controles de base de datos.

La factura es un documento fuente para una integración posterior con Contabilidad. Por decisión del usuario, no se crea ni se simula una cuenta por cobrar, asiento o cobro; tampoco se marcan estados de pago. El vínculo, saldos, contabilización, cancelación o reversa de facturas y el crédito quedan para los módulos y políticas correspondientes. V13 admite actualmente `BORRADOR` transitorio y `EMITIDA`; el diagrama de dominio completo incluye estados futuros.

## Contratos y diseño

El [OpenAPI](../api/openapi-sprint-3-ventas.yaml) describe emisión y consulta. La fuente de verdad del esquema ejecutable es V13 y ambas copias del DBML describen sus tablas. La [secuencia UML](../diagrams/arquitectura/13-secuencia-factura-comercial.puml) representa la operación.

`FacturaService` coordina la transacción, autorización, repositorios y auditoría. Reutiliza `CalculoCotizacion.calcular`, una función determinista sobre `ValoresVenta.Linea`, para cada línea y la comprobación de totales; los repositorios aíslan la persistencia y los DTO devuelven copias inmutables de las listas. Los bloqueos, triggers y auditoría son efectos de infraestructura, no programación funcional. La portabilidad entre proyectos se verificará en la parte 13; no se da por demostrada aquí.

La regresión inicial detectó que el [contrato de auditoría](../api/openapi-audit.yaml) no incluía tres estados de Ventas añadidos en la parte 11 (`BORRADOR`, `CONFIRMADO` y `PARCIALMENTE_DESPACHADO`). Se corrigió su enumeración y se actualizó su versión a `1.1.0`; los consumidores que validan ese enum de forma estricta también deben actualizarse. La primera repetición se ejecutó antes de aplicar la corrección por un error de ruta; la verificación final se repitió con los archivos corregidos.

## Verificación

La regresión completa con Java 21 y PostgreSQL en Testcontainers terminó con 69 pruebas Surefire y 181 Failsafe sin fallos. Para reproducirla desde `erp-backend`, con Docker disponible y dependencias locales:

```sh
MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o -DargLine='-XX:+UseSerialGC -Xms128m -Xmx768m' verify
```
