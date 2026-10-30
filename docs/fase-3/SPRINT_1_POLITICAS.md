# Sprint 1 — decisiones para compras e inventario

Estas decisiones de implementación las adopta el equipo para el MVP por instrucción del responsable del proyecto. No son reglas fiscales ni garantizan conformidad con CFDI. Los importes y la API de órdenes están implementados; el núcleo de inventario y su valoración se probaron en la parte 8. El registro, la consulta y confirmación de recepción están implementados; las políticas de alerta siguen pendientes de implementación. El SRS y CA-01 prevalecen ante cualquier contradicción.

## Importes de orden

Usar `BigDecimal`, sin `double`. Para cada línea, calcular el bruto exacto `cantidad × precioUnitario`; descuento = bruto × descuentoPct / 100; base = bruto − descuento; impuesto = base × tasaImpuesto / 100. Redondear la base y el impuesto de cada línea a dos decimales con `HALF_UP`; subtotal de orden = suma de bases redondeadas, impuestos = suma de impuestos redondeados y total = subtotal + impuestos. Los porcentajes y precios pactados se guardan en la línea; validar precisión y desbordamiento de los totales antes de persistir. Las operaciones de recepción no cambian los valores pactados. La diferencia con el precio recibido se conserva para conciliación posterior; no altera automáticamente el importe de la orden.

Ejemplo: 3 unidades a 10.00, descuento 10 %, impuesto 16 %: subtotal 27.00, impuestos 4.32, total 31.32. Para la parte 6 probar medios centavos, varias líneas, 0 y 100 %, precisión máxima y desbordamiento. La política es una convención de cálculo de compra, no una liquidación fiscal.

## Autorización y estados

El usuario que creó una orden no puede aprobarla; la aprobación exige `COMPRAS_ORDENES_APROBAR` y un actor distinto. El envío exige `COMPRAS_ORDENES_ADMIN` y una orden aprobada; puede hacerlo el creador o aprobador. Cada transición se autoriza en backend y registra actor, fecha y auditoría. No introducir excepción administrativa implícita. Los casos de rechazo/cancelación se implementarán cuando su contrato esté definido.

## Recepción e inventario

Solo una orden ENVIADA o PARCIALMENTE_RECIBIDA admite confirmación. Pendiente = ordenado − suma de aceptadas en recepciones confirmadas; lo rechazado no reduce el pendiente (CA-01). La recepción registrada inicia en PENDIENTE y no cambia inventario. Al confirmar se exige recibida = aceptada + rechazada y aceptada acumulada ≤ ordenado; un exceso se rechaza, sin inventar una autorización de excepción. Por cada línea con aceptada > 0 se crea una entrada única. Si aceptada = 0 no se crea movimiento ni se altera el costo. La orden se marca RECIBIDA solo cuando todas sus líneas quedan sin pendiente. Estos cambios y la auditoría se confirman o revierten juntos.

El costo de la entrada es el `precioUnitarioRecibido` capturado en la recepción, sin impuestos ni descuentos inferidos. Para cada producto y almacén, bajo bloqueo transaccional, calcular costo promedio = `(cantidadFisicaAnterior × costoPromedioAnterior + cantidadAceptada × precioUnitarioRecibido) / (cantidadFisicaAnterior + cantidadAceptada)`, con `HALF_UP` a cuatro decimales. Si la cantidad anterior es cero, usar el precio recibido con cuatro decimales. La cantidad reservada no interviene en el promedio. Aplicar la entrada sobre la cantidad física anterior y guardar en el movimiento el costo unitario recibido. Esta regla de valoración es una decisión del MVP y no sustituye una política contable aprobada.

## Umbral y referencias maestras

`puntoReorden` es un umbral global por producto, para comparar con la suma de cantidades disponibles (`fisica − reservada`) de almacenes activos cuando se implemente la alerta; `cantidadReorden` es la cantidad sugerida, no crea una orden automática. No se calcula alerta en el sprint actual. La categoría y el proveedor principal de un producto solo deben existir: pueden estar inactivos para conservar datos y permitir edición; operaciones nuevas de compra requieren producto y proveedor activos. La recepción requiere almacén activo.

## Identificadores e idempotencia

El servidor genera `OC-` o `RC-` seguido de una secuencia de 12 dígitos, sin promesa de consecutividad; rechazar desbordamiento del formato. El cliente aporta `claveIdempotencia` al registrar la recepción, única globalmente. Una repetición con la misma clave y contenido normalizado idéntico devuelve la recepción existente; con contenido distinto devuelve 409. Confirmar por ID es idempotente: repetir una confirmación exitosa devuelve el documento confirmado sin crear otra entrada. La clave de cada movimiento es `RECEPCION:<idDetalle>` y existe además unicidad por detalle. Las comparaciones y escrituras concurren bajo bloqueo transaccional y restricciones de base de datos.
