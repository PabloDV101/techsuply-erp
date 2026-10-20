# Especificación de Requisitos de Software (SRS)

**Proyecto:** Sistema ERP para Distribuidora TechSupply Solutions  
**Identificador:** SRS-TSS-ERP-001  
**Versión:** 1.1 — propuesta consolidada para validación académica (sincronización documental)
**Fecha:** 11 de octubre de 2026  
**Revisión documental:** 19 de octubre de 2026
**Estado:** Borrador de trabajo  
**Tecnologías objetivo:** React + TypeScript, Java 21 + Spring Boot, PostgreSQL  

> **Nota de alcance documental.** Este SRS integra la narrativa de TechSupply Solutions y las decisiones de diseño acordadas durante el análisis. Los requisitos proceden del caso suministrado; las reglas de arquitectura y los criterios operativos señalados como decisiones propuestas son recomendaciones para volver el alcance desarrollable y deben validarse con el docente/equipo antes de congelar la versión final.

## Control del documento

| Campo | Descripción |
|---|---|
| Documento | Especificación de Requisitos de Software (SRS) |
| Producto | TechSupply Solutions ERP |
| Identificador | SRS-TSS-ERP-001 |
| Versión | 1.0 |
| Estado | Borrador consolidado para validación |
| Fuente primaria | Narrativa y especificación académica del proyecto proporcionadas por el usuario |
| Arquitectura | Monolito modular en capas |
| Tecnologías | React + TypeScript; Java 21 + Spring Boot; PostgreSQL |

## 1. Introducción

### 1.1 Propósito

Este documento define los requisitos funcionales y no funcionales, el alcance, los usuarios, las reglas de negocio, las interfaces y la arquitectura de referencia del sistema ERP de TechSupply Solutions. Su objetivo es servir como contrato de trabajo entre análisis, diseño, desarrollo y pruebas, evitando que los módulos se implementen con interpretaciones incompatibles.

El documento está dirigido al equipo de desarrollo, al docente/evaluador, a los responsables simulados del negocio y a quienes elaboren los modelos de datos, la API, las pruebas y los manuales.

### 1.2 Alcance del producto

TechSupply Solutions es una distribuidora mediana de componentes electrónicos y suministros de TI con más de 80 colaboradores y tres sedes funcionales. Actualmente utiliza hojas de cálculo, un sistema contable heredado y registros manuales de inventario. El proyecto propone una plataforma web integrada para centralizar catálogos, compras, inventario, ventas, contabilidad básica, recursos humanos y reportería, con control de acceso y trazabilidad.

El sistema permitirá administrar datos maestros; preparar y aprobar requisiciones y órdenes de compra; registrar recepciones y devoluciones; controlar existencias por producto y almacén; crear cotizaciones y pedidos desde usuarios internos y el portal básico de clientes; administrar despachos, facturas comerciales y pagos; mantener cuentas por cobrar/pagar y asientos contables; gestionar datos básicos de empleados y nómina; generar reportes; y auditar operaciones relevantes.

### 1.3 Objetivos

**Objetivo general.** Diseñar y desarrollar un ERP web modular que integre los procesos operativos principales de TechSupply Solutions, reduzca tareas manuales, mejore la consistencia de los datos y facilite la trazabilidad y la toma de decisiones.

**Objetivos específicos.**
- Integrar compras, inventario, ventas y registros financieros en una base de datos relacional compartida.
- Reducir la captura repetida y los errores mediante validación y automatización; el caso propone reducir errores en 85%, meta que requiere línea base y método de medición acordados.
- Mantener trazabilidad de documentos, movimientos, aprobaciones y modificaciones.
- Proporcionar consultas, reportes e indicadores de gestión.
- Establecer módulos y componentes reutilizables con separación de responsabilidades.
- Aplicar el modelo 4 + 1 vistas para documentar la arquitectura.
- Probar reglas críticas de integridad, seguridad y consistencia transaccional.

### 1.4 Convenciones

- **RF-xxx:** requisito funcional.
- **RNF-xxx:** requisito no funcional.
- **RN-xxx:** regla de negocio.
- **MVP:** versión mínima funcional que se desarrollará dentro del alcance académico.
- **AS-IS / TO-BE:** proceso actual / proceso propuesto.
- **Prioridad Alta:** necesaria para los flujos principales o la evaluación; **Media:** necesaria para completar un módulo; **Baja/Futura:** propuesta de evolución.
- “El sistema deberá” expresa un requisito normativo. “Se propone” identifica una decisión de diseño que debe validarse.

### 1.5 Referencias internas del proyecto

- Narrativa y criterios de evaluación de TechSupply Solutions proporcionados para el proyecto académico.
- Diagramas PlantUML de casos de uso, vistas lógicas de clases, procesos, implementación y despliegue preparados para este proyecto.
- Decisiones de negocio acordadas en el análisis: monolito modular, React + Spring Boot + PostgreSQL, control de stock por producto/almacén, recepciones y despachos transaccionales, facturación comercial y nómina simplificada.

## 2. Descripción general

### 2.1 Contexto del negocio y problemas

Los procesos actuales se encuentran fragmentados. Compras usa correos y documentos, almacén recurre a registros manuales, ventas verifica existencias de forma separada y contabilidad recibe documentos de varios sistemas. Esto provoca duplicidades, diferencias en inventario, demoras de conciliación y reportes tardíos.

El ERP funcionará como fuente central de información operativa. Cada módulo será responsable de sus reglas, pero deberá exponer operaciones documentadas para coordinarse con los demás. Por ejemplo, el módulo de compras no actualizará directamente las tablas internas de inventario: solicitará al módulo de inventario la entrada derivada de una recepción confirmada.

### 2.2 Perspectiva del producto

El producto es una aplicación web empresarial con tres partes principales:
1. **Frontend:** interfaz React + TypeScript para personal interno y, dentro del alcance previsto, clientes que consulten catálogo y realicen pedidos.
2. **Backend:** aplicación Java 21 + Spring Boot con API REST, seguridad, servicios de aplicación y módulos de dominio.
3. **Persistencia:** PostgreSQL con un esquema relacional normalizado, restricciones, migraciones versionadas y transacciones.

Nginx podrá servir los archivos estáticos y funcionar como proxy inverso. En desarrollo puede usarse el servidor de React y una instancia local del backend; la topología definitiva de producción dependerá de la infraestructura disponible.

### 2.3 Funciones principales

- Gestión de usuarios, roles, permisos y auditoría.
- Gestión maestra de productos, categorías, proveedores, clientes, empleados, departamentos, puestos y almacenes.
- Gestión de requisiciones, aprobación, órdenes de compra, recepción, facturas de proveedor y devoluciones.
- Gestión de existencias, reservas, movimientos, conteos, ajustes y alertas.
- Gestión de cotizaciones, pedidos, reservas de stock, despachos, facturas comerciales y pagos.
- Gestión contable: plan de cuentas, periodos, asientos, cuentas por cobrar/pagar, aplicaciones y estados financieros básicos.
- Gestión simplificada de RR. HH. y nómina.
- Dashboards y reportes con filtros, exportación y permisos.

### 2.4 Tipos de usuario

| Actor/rol | Responsabilidad principal | Restricciones relevantes |
|---|---|---|
| Gerente general | Consultar indicadores, reportes y estados financieros autorizados | No obtiene automáticamente permisos de edición en todos los módulos |
| Responsable de compras | Gestionar requisiciones, proveedores, órdenes y seguimiento | Aprobación sujeta al permiso de aprobación definido |
| Responsable de almacén | Registrar recepciones, despachos, conteos e incidencias | Los ajustes requieren autorización independiente |
| Responsable de ventas | Gestionar clientes, cotizaciones, pedidos y facturación comercial | No puede confirmar pedidos sin validar disponibilidad y reglas de crédito |
| Contador | Gestionar cuentas auxiliares, pagos y asientos | Solo contabiliza con periodo abierto y asiento equilibrado |
| Responsable de RR. HH. | Mantener expedientes, asistencia, vacaciones y preparar nómina | Acceso restringido a datos laborales y salariales |
| Administrador del sistema | Gestionar usuarios, roles y configuración técnica | Las acciones administrativas relevantes se auditan |
| Cliente | Consultar catálogo, realizar pedidos desde el portal y consultar sus propios pedidos | Solo accede a su propia información; no entra a módulos internos |
| Proveedor | Participante externo del proceso de compra | No tendrá portal propio dentro del MVP; la comunicación será realizada por personal de compras |

Un usuario puede tener varios roles. Un empleado no tiene que disponer obligatoriamente de una cuenta del ERP.

### 2.5 Entorno operativo

- Navegador moderno de escritorio para usuarios internos; interfaz web adaptable como objetivo de usabilidad.
- React + TypeScript para el frontend.
- Java 21 y una versión de Spring Boot compatible definida al iniciar implementación.
- Spring Security para autenticación y autorización.
- Spring Data JPA/Hibernate para persistencia desde el backend.
- PostgreSQL como motor relacional.
- Flyway para administrar cambios de esquema.
- Git y GitHub para control de versiones.
- OpenAPI/Swagger para documentar la API.
- Docker Compose recomendado para el entorno local integrado.

Las versiones concretas de bibliotecas y contenedores deben quedar fijadas en los archivos de construcción y configuración del repositorio.

### 2.6 Restricciones de diseño

- El sistema debe conservar el modelo de monolito modular; los módulos no serán microservicios independientes en el MVP.
- Los módulos deben interactuar por servicios públicos de aplicación/contratos y no modificar directamente los repositorios privados de otros módulos.
- La fuente de verdad de las existencias será el módulo de inventario.
- Se utilizará una base de datos PostgreSQL central; la integridad no debe depender solo de validaciones visuales en React.
- Los importes se manejarán con tipos decimales (`BigDecimal` en Java y `NUMERIC` en PostgreSQL), no `float`/`double`.
- Las operaciones confirmadas con impacto financiero o de stock conservarán su trazabilidad y no se borrarán físicamente de forma silenciosa.
- El proyecto es académico; no se asumirá integración fiscal con SAT, pasarela de pagos, transportista, SMS ni motor fiscal completo salvo ampliación explícita del alcance.

### 2.7 Supuestos y decisiones de alcance

Las siguientes decisiones hacen el alcance más implementable. Deben tratarse como supuestos aprobados por el equipo y confirmarse con el docente:
- Una factura comercial por pedido, emitida cuando el pedido esté completamente despachado; los pedidos sí pueden tener varios despachos parciales.
- Una factura de proveedor por orden de compra para el MVP, validada contra la orden y las recepciones.
- La nómina registra conceptos e importes y ofrece una integración contable básica; no implementa por sí sola todas las fórmulas fiscales/laborales mexicanas.
- Las alertas de stock bajo sugieren compras, pero no generan órdenes aprobadas automáticamente.
- Los reportes muestran información derivada de operaciones válidas. Los indicadores deben documentar fórmula y periodo.
- SMTP, mensajería asíncrona y patrón Outbox son opcionales; una falla de correo no debe revertir una operación comercial ya confirmada.

## 3. Límites del alcance

### 3.1 Incluido en el alcance del sistema

- Catálogos maestros y control de usuarios/roles.
- Compras con requisiciones, aprobación, orden de compra, recepción parcial, factura de proveedor y devolución.
- Existencias por producto y almacén, movimientos trazables, reservas, despachos, conteos, ajustes autorizados y alertas.
- Ventas con cotización, pedido, validación de stock/crédito, despacho parcial, factura comercial y registro/aplicación de pagos.
- Contabilidad básica con plan de cuentas, periodos, asientos de partida doble, cuentas por cobrar/pagar y reportes financieros soportados por los registros implementados.
- Registro simplificado de empleados, asistencia, vacaciones y nómina.
- Dashboard y reportes con filtros y control de acceso.
- Auditoría, autenticación, autorización, validaciones, pruebas y documentación.

### 3.2 Fuera del alcance inicial (salvo requerimiento posterior)

- Timbrado y envío de CFDI al SAT o certificación fiscal.
- Integraciones directas con bancos, pasarelas de pago o conciliación bancaria automática.
- Portal de proveedores.
- Integración con APIs de transportistas y seguimiento logístico externo.
- Notificaciones SMS.
- Motor completo de nómina fiscal, cálculo legal avanzado de impuestos, prestaciones y obligaciones de nómina.
- Microservicios, mensajería distribuida obligatoria o alta disponibilidad geográfica.
- Migración automatizada completa de todos los sistemas heredados; puede requerir carga inicial preparada manualmente.

## 4. Reglas de negocio

| ID | Regla |
|---|---|
| RN-01 | Cada producto tiene SKU único; correo y código de usuario deben ser únicos. Los folios de documentos también deben ser únicos dentro de su tipo. |
| RN-02 | Los registros operativos deben incluir identificador, fecha, estado y responsable, según aplique. |
| RN-03 | Los documentos confirmados con operaciones asociadas no se eliminan físicamente; se cancelan/revierten mediante un proceso auditable. |
| RN-04 | Los detalles de órdenes, pedidos y facturas conservan la cantidad, precio, descuento e impuesto acordados en la operación, aun si cambia el catálogo. |
| RN-05 | La existencia es única por combinación producto-almacén. `disponible = cantidadFisica - cantidadReservada`. |
| RN-06 | La cantidad física y la cantidad reservada no pueden ser negativas; la disponibilidad no puede ser negativa. |
| RN-07 | Crear una orden de compra no altera existencias. Solo una recepción confirmada incrementa el stock por las unidades aceptadas. |
| RN-08 | En recepción, cantidad aceptada + cantidad rechazada = cantidad recibida. La recepción acumulada no supera la cantidad pendiente sin autorización documentada. |
| RN-09 | Una orden de compra puede tener recepciones parciales y se marca recibida cuando se completa la cantidad que corresponda. |
| RN-10 | Una devolución al proveedor solo puede devolver unidades recibidas y no previamente devueltas; genera una salida de stock. |
| RN-11 | Al confirmar un pedido, el sistema valida cliente, condiciones comerciales, disponibilidad y crédito cuando aplique, y reserva existencias transaccionalmente. |
| RN-12 | La confirmación del despacho disminuye cantidad física y cantidad reservada en una única transacción y registra el movimiento de salida. La cantidad despachada no puede superar la reserva ni lo pendiente del pedido. |
| RN-13 | Una devolución de cliente solo incorpora las unidades inspeccionadas y aceptadas. |
| RN-14 | En el MVP se genera una factura comercial por pedido cuando esté totalmente despachado. Esto no implica timbrado CFDI ante el SAT. |
| RN-15 | Los cobros y pagos se registran separados de las facturas y se aplican mediante entidades de aplicación; un importe no puede aplicarse por encima del saldo de la factura ni del disponible del pago. |
| RN-16 | Un asiento contabilizado tiene al menos dos partidas, la suma del debe iguala la suma del haber y la fecha pertenece a un periodo abierto. |
| RN-17 | Los asientos contabilizados no se editan ni eliminan directamente; se corrigen mediante reversión y asiento nuevo. |
| RN-18 | Una misma operación no puede generar movimientos, facturas ni asientos duplicados ante solicitudes repetidas. Las operaciones críticas deben ser idempotentes. |
| RN-19 | Cada movimiento de inventario debe tener exactamente una fuente de negocio válida; la reversa debe referenciar el movimiento original. |
| RN-20 | Los permisos se verifican en backend en cada operación protegida. Ocultar controles en React no sustituye autorización. |
| RN-21 | La información financiera, salarial y los datos de clientes están sujetos a permisos apropiados. |
| RN-22 | El evento de auditoría no guarda contraseñas, tokens ni secretos. Los logs deben minimizar datos personales. |
| RN-23 | Las aplicaciones de cobro deben corresponder al mismo cliente que la cuenta por cobrar; las aplicaciones de pago, al mismo proveedor de la cuenta por pagar. |
| RN-24 | Notificaciones se procesan después de confirmar la operación; una falla de correo no revierte la transacción de negocio. |

## 5. Requisitos funcionales

Prioridad: **Alta** = necesaria para completar flujos principales; **Media** = requerida para el módulo o alcance completo; **Futura** = considerada extensión y no bloquea el MVP si el equipo/documento de evaluación lo acuerda.

### 5.1 Seguridad y usuarios

**RF-SEG-01 — Autenticación (Alta).** El sistema deberá autenticar usuarios mediante identificador de acceso y contraseña, verificar que la cuenta esté activa y rechazar credenciales incorrectas.  
*Criterio de aceptación:* una cuenta inactiva o una contraseña incorrecta no obtiene acceso; no se devuelve el hash ni se registra la contraseña en logs.

**RF-SEG-02 — Roles y permisos (Alta).** El administrador autorizado deberá gestionar usuarios, roles y permisos por módulo/acción. El backend verificará permisos en todos los endpoints protegidos.  
*Criterio:* un usuario sin el permiso requerido recibe una respuesta de acceso denegado aunque invoque la API sin usar la interfaz.

**RF-SEG-03 — Auditoría (Alta).** El sistema deberá registrar las operaciones críticas con usuario, fecha/hora, acción, entidad afectada, resultado y referencia del registro.  
*Criterio:* aprobación de orden, ajuste de inventario, confirmación de despacho y contabilización dejan eventos consultables por usuarios autorizados.

**RF-SEG-04 — Gestión de sesiones/token (Alta).** El sistema deberá validar la sesión o token de acceso y su expiración, y permitir el cierre de sesión o invalidación conforme a la estrategia implementada.  
*Criterio:* un token inválido o vencido no permite operaciones protegidas.

### 5.2 Gestión maestra

**RF-MAE-01 — Productos (Alta).** El sistema deberá crear, consultar, actualizar y desactivar productos con SKU único, nombre, descripción, unidad de medida, precio de venta, costo de referencia, stock mínimo y estado.  
*Criterio:* se rechaza un SKU duplicado y no se permiten precios/costos negativos.

**RF-MAE-02 — Categorías (Media).** El sistema deberá gestionar categorías jerárquicas y asociarlas a productos.  
*Criterio:* cada producto activo puede consultarse por su categoría; una categoría no puede ser padre de sí misma ni formar ciclos.

**RF-MAE-03 — Proveedores (Alta).** El sistema deberá gestionar datos y estado de proveedores, incluyendo contacto, dirección y condiciones de pago.  
*Criterio:* una orden nueva requiere proveedor activo; las órdenes históricas mantienen la referencia a su proveedor.

**RF-MAE-04 — Clientes (Alta).** El sistema deberá gestionar clientes, información de contacto, estado y límite de crédito.  
*Criterio:* pedidos nuevos requieren cliente activo y, cuando la venta sea a crédito, se aplica el límite y saldo según política configurada.

**RF-MAE-05 — Estructura interna (Media).** El sistema deberá mantener empleados, departamentos, puestos y almacenes, con códigos identificables y estado.  
*Criterio:* registros operativos enlazan al empleado responsable y/o almacén correspondiente.

### 5.3 Compras

**RF-COM-01 — Requisición (Alta).** El sistema deberá permitir crear requisiciones con uno o varios productos y cantidades. También podrá originar propuestas de requisición a partir de un umbral de stock mínimo.  
*Criterio:* la propuesta automática no crea ni aprueba por sí sola una orden de compra.

**RF-COM-02 — Aprobar/rechazar requisición (Alta).** Un usuario con permiso deberá aprobar o rechazar una requisición, dejando fecha, responsable y motivo si se rechaza.  
*Criterio:* únicamente requisiciones aprobadas pueden convertirse en órdenes autorizadas.

**RF-COM-03 — Orden de compra (Alta).** El sistema deberá generar órdenes de compra vinculadas a un proveedor y con folio único, detalles, cantidades, costos, descuentos, impuestos y estados definidos.  
*Criterio:* cada orden contiene al menos un detalle y conserva precios del momento de emisión.

**RF-COM-04 — Seguimiento y recepción parcial (Alta).** El sistema deberá consultar el estado de la orden y permitir múltiples recepciones parciales.  
*Criterio:* una orden cambia a parcialmente recibida mientras queden cantidades pendientes y a recibida al completar las cantidades aplicables.

**RF-COM-05 — Confirmar recepción (Alta).** El sistema deberá registrar cantidades recibidas, aceptadas y rechazadas por detalle, usuario, almacén y fecha.  
*Criterio:* aceptadas + rechazadas = recibidas; solo unidades aceptadas actualizan stock y el movimiento queda en la misma transacción.

**RF-COM-06 — Factura de proveedor (Alta).** El sistema deberá registrar una factura de proveedor y validarla contra orden y recepciones antes de crear la cuenta por pagar. Para el MVP se permite una factura por orden.  
*Criterio:* diferencias o documentos faltantes impiden marcar la validación como completada; la operación no duplica cuentas por pagar.

**RF-COM-07 — Devolución a proveedor (Media).** El sistema deberá registrar devoluciones con motivo, proveedor, almacén, producto y cantidad, asociadas con la recepción/línea original.  
*Criterio:* no puede devolverse más de lo aceptado y aún disponible para devolución; la confirmación genera salida trazable.

**RF-COM-08 — Historial de proveedores (Media).** El sistema deberá conservar el historial de órdenes, precios aplicados, recepciones e incidencias por proveedor.  
*Criterio:* usuarios autorizados pueden consultar y comparar operaciones previas sin modificar documentos confirmados.

### 5.4 Inventario

**RF-INV-01 — Existencias por almacén (Alta).** El sistema deberá consultar cantidad física, reservada y disponible por producto y almacén.  
*Criterio:* existe como máximo un registro de existencia por combinación producto-almacén; disponible se calcula como física menos reservada.

**RF-INV-02 — Movimientos (Alta).** El sistema deberá registrar entradas, salidas, devoluciones, ajustes y reversiones con fecha, cantidad, tipo, usuario y fuente del movimiento.  
*Criterio:* cada movimiento confirmado tiene exactamente una fuente válida y no se elimina físicamente.

**RF-INV-03 — Reserva de ventas (Alta).** Al confirmar un pedido, el sistema deberá reservar cantidades disponibles dentro de una transacción.  
*Criterio:* las reservas concurrentes no permiten comprometer más unidades que las disponibles.

**RF-INV-04 — Despacho (Alta).** El sistema deberá registrar despachos completos o parciales. La confirmación restará cantidad física y reservada, registrará movimiento y actualizará el estado del pedido de forma atómica.  
*Criterio:* la cantidad despachada no supera la reservada ni la pendiente y el reintento no duplica la salida.

**RF-INV-05 — Conteo físico (Media).** El sistema deberá permitir conteos por almacén y producto, comparar cantidad física contada con la registrada y conservar diferencias.  
*Criterio:* el conteo por sí solo no cambia el stock.

**RF-INV-06 — Ajustes autorizados (Alta).** El sistema deberá crear solicitudes de ajuste con motivo, detalle, solicitante y aprobador.  
*Criterio:* solo un ajuste aprobado puede aplicarse y el cambio de existencia se acompaña del movimiento en una transacción.

**RF-INV-07 — Alertas e informes de inventario (Media).** El sistema deberá identificar stock bajo y permitir consultar rotación y productos sin movimiento/obsoletos según un umbral configurable.  
*Criterio:* el umbral usado aparece en la configuración o parámetros del reporte; la alerta no genera una orden aprobada automáticamente.

### 5.5 Ventas

**RF-VEN-01 — Cotizaciones (Alta).** El sistema deberá crear y consultar cotizaciones con productos, cantidades, precio, descuentos, impuestos, vigencia y estado.  
*Criterio:* una cotización vencida no puede convertirse directamente en pedido sin revalidación.

**RF-VEN-02 — Pedido de venta (Alta).** El sistema deberá convertir una cotización aceptada en pedido, conservando los detalles y datos comerciales.  
*Criterio:* la conversión no duplica detalles y el pedido dispone de folio y estados definidos.

**RF-VEN-03 — Validar pedido (Alta).** Antes de confirmar un pedido, el backend deberá validar cliente activo, precios, descuentos, disponibilidad y crédito cuando aplique.  
*Criterio:* cualquier fallo impide la confirmación y no deja una reserva parcial.

**RF-VEN-04 — Despachos parciales (Alta).** El sistema deberá permitir preparar y confirmar uno o varios despachos de un pedido.  
*Criterio:* cada despacho confirmado genera salidas y libera sus reservas correspondientes; el pedido conserva el estado parcial mientras existan cantidades pendientes.

**RF-VEN-05 — Factura comercial (Alta).** El sistema deberá emitir una factura comercial vinculada al pedido cuando este se encuentre completamente despachado. En el MVP se establece una factura por pedido.  
*Criterio:* no se emite una segunda factura del mismo pedido y se mantiene explícito que no hay timbrado CFDI salvo que se implemente después.

**RF-VEN-06 — Pagos de clientes (Media).** El sistema deberá registrar cobros y aplicarlos a una o varias cuentas por cobrar mediante aplicaciones explícitas.  
*Criterio:* solo cobros confirmados reducen saldos; ninguna aplicación supera el saldo de factura o monto disponible del cobro.

**RF-VEN-07 — Historial del cliente (Media).** El sistema deberá mostrar a usuarios autorizados el historial de cotizaciones, pedidos, facturas y pagos del cliente.  
*Criterio:* un cliente del portal solo accede a registros que le pertenecen.

**RF-VEN-08 — Portal de clientes (Alta).** El portal permitirá al cliente autenticado consultar catálogo y precios habilitados, crear pedidos y consultar su estado.  
*Criterio:* solo accede a sus propios registros; el backend vuelve a validar disponibilidad, precio, crédito y permisos antes de confirmar.

**RF-VEN-09 — Comisiones comerciales (Futura).** El sistema podrá calcular comisiones básicas mediante porcentajes/reglas configurables.  
*Criterio:* antes de activarlo se documentan base de cálculo, momento de devengo y efecto de devoluciones.

### 5.6 Contabilidad y finanzas

**RF-CON-01 — Plan de cuentas y periodos (Alta).** El sistema deberá gestionar cuentas contables jerárquicas y periodos abiertos/cerrados.  
*Criterio:* código de cuenta único; las cuentas de agrupación no aceptan movimientos cuando así estén configuradas.

**RF-CON-02 — Asientos contables (Alta).** El sistema deberá crear asientos con dos o más partidas vinculadas a cuentas contables, validando que debe y haber sean iguales.  
*Criterio:* un asiento desbalanceado no puede contabilizarse.

**RF-CON-03 — Contabilización y reversión (Alta).** El sistema deberá generar o preparar asientos a partir de documentos operativos validados según reglas contables configuradas, y permitir contabilizarlos en un periodo abierto. Asociará la fuente y evitará duplicados.  
*Criterio:* reintentar la misma solicitud no genera otro asiento; los asientos contabilizados solo se corrigen mediante reversión auditada.

**RF-CON-04 — Cuentas por pagar (Alta).** El sistema deberá generar o mantener cuentas por pagar a partir de facturas de proveedores validadas.  
*Criterio:* cada cuenta conserva proveedor, documento origen, importe original, vencimiento y saldo.

**RF-CON-05 — Cuentas por cobrar (Alta).** El sistema deberá generar o mantener cuentas por cobrar a partir de facturas comerciales válidas.  
*Criterio:* cada cuenta conserva cliente, factura origen, vencimiento y saldo.

**RF-CON-06 — Pagos a proveedores y cobros de clientes (Alta).** El sistema deberá registrar pagos/cobros y permitir aplicaciones parciales con control de saldo.  
*Criterio:* aplicaciones de cobro y de pago son entidades separadas y deben corresponder a cliente/proveedor de la cuenta auxiliar.

**RF-CON-07 — Estados y reportes financieros (Media).** El sistema deberá generar balance de prueba, estado de resultados, balance general y flujo de efectivo conforme a las operaciones contables implementadas.  
*Criterio:* los reportes identifican el periodo y se calculan con los datos contables válidos; su alcance y fórmulas se documentan.

**RF-CON-08 — Control presupuestario (Futura).** El sistema podrá registrar presupuestos por departamento y periodo, y comparar gastos contra importes presupuestados.  
*Criterio:* se definen periodo, versión, responsable y regla de cálculo antes de activar alertas o bloqueos de gasto.

### 5.7 Recursos humanos y nómina

**RF-RH-01 — Expediente laboral (Media).** El sistema deberá gestionar expediente/contrato laboral, puesto, departamento, vigencia y salario base histórico en el expediente correspondiente.  
*Criterio:* se preserva historial y el salario no se duplica como atributo independiente inconsistente del empleado.

**RF-RH-02 — Asistencia y vacaciones (Media).** El sistema deberá registrar asistencia e incidencias, y gestionar solicitudes y autorizaciones de vacaciones.  
*Criterio:* las solicitudes mantienen estado, fechas y aprobador; el acceso a información es restringido.

**RF-RH-03 — Cálculo de nómina básica (Media).** El sistema deberá calcular percepciones, deducciones y neto utilizando conceptos e importes autorizados.  
*Criterio:* neto = percepciones − deducciones; no se duplica nómina para empleado/periodo.

**RF-RH-04 — Aprobar y pagar nómina (Media).** El sistema deberá permitir revisar y aprobar la nómina antes del pago y preparar información para contabilidad.  
*Criterio:* no se registra pago antes de la aprobación; se conserva referencia de pago y auditoría.

### 5.8 Reportería e integración

**RF-REP-01 — Dashboard (Media).** El sistema deberá presentar indicadores de compras, ventas, inventario y finanzas según permisos.  
*Criterio:* cada indicador identifica periodo y fuente; las fórmulas principales se documentan.

**RF-REP-02 — Informes parametrizables (Media).** El sistema deberá permitir filtrar, ordenar y paginar catálogos y reportes; habilitar exportación PDF/CSV/XLSX cuando el formato se implemente.  
*Criterio:* la exportación mantiene filtros, respeta permisos y registra la ejecución cuando corresponda.

**RF-REP-03 — Análisis de gestión (Media).** El sistema deberá ofrecer reportes de rotación, clasificación ABC, rentabilidad por cliente y margen operativo conforme a definiciones documentadas.  
*Criterio:* cada indicador define fórmula, fuentes, periodo y exclusiones; si faltan datos, se identifica el indicador como no disponible en lugar de inventar valores.

**RF-REP-04 — Reportes y permisos (Alta).** Toda consulta o exportación deberá respetar los permisos de datos financieros, salariales y comerciales.  
*Criterio:* un usuario sin permiso no puede obtener información protegida invocando directamente API o exportaciones.

**RF-REP-05 — Costos de personal por departamento (Media).** El sistema deberá ofrecer un reporte de costos de personal por departamento usando los registros de nómina disponibles.  
*Criterio:* el reporte identifica periodo, departamento y origen de los importes; solo lo consultan roles autorizados.

**RF-SYS-01 — Validación y errores (Alta).** La API deberá validar entradas y devolver errores consistentes sin exponer trazas internas, contraseñas, SQL ni secretos.  
*Criterio:* los errores de validación se comunican con un código/mensaje funcional y se registra detalle técnico apropiado en los logs.

**RF-SYS-02 — Notificaciones (Baja/MVP opcional).** El sistema podrá emitir avisos de stock bajo, aprobación pendiente o resultado de una operación.  
*Criterio:* las notificaciones se procesan después del commit; un fallo de correo no revierte la operación de negocio.

## 6. Requisitos no funcionales

**RNF-01 — Disponibilidad (Alta).** El sistema deberá alcanzar disponibilidad mensual de al menos 99.5%, excluyendo periodos acordados de mantenimiento solo si la política de medición así lo define. El enunciado también limita el mantenimiento a una hora mensual. El equipo debe confirmar si esa hora es el máximo de mantenimiento programado o el máximo total de interrupción; son medidas distintas.

**RNF-02 — Rendimiento de consultas (Alta).** Las consultas operativas habituales responderán en menos de 2 segundos en al menos el 95% de las solicitudes bajo una carga de prueba y un conjunto de datos representativos documentados.

**RNF-03 — Rendimiento de reportes (Alta).** Los reportes estándar responderán en menos de 5 segundos para al menos el 95% de las ejecuciones en condiciones de prueba definidas. Reportes analíticos complejos deberán identificarse por separado si no pueden cumplir el mismo umbral.

**RNF-04 — Concurrencia y escalabilidad (Alta).** La solución deberá soportar hasta 200 usuarios concurrentes como objetivo de evaluación, mediante pruebas de carga. El diseño permitirá añadir instancias backend si la sesión/autenticación y la infraestructura lo permiten.

**RNF-05 — Seguridad de transporte y almacenamiento (Alta).** La comunicación externa utilizará HTTPS/TLS. Las credenciales se almacenarán con un algoritmo robusto de hash; los volúmenes o medios de datos y respaldos deberán cifrarse según las capacidades de la infraestructura.

**RNF-06 — Control de acceso (Alta).** La API deberá aplicar autorización por roles/permisos en backend en cada operación protegida. Los datos financieros, salariales y de clientes estarán limitados según el rol.

**RNF-07 — Integridad transaccional (Alta).** La creación de movimientos y la actualización de existencias, así como las operaciones de reserva/despacho, aplicación de pagos y contabilización, se realizarán atómicamente cuando afecten varias entidades relacionadas.

**RNF-08 — Idempotencia (Alta).** Las operaciones que se puedan reintentar tendrán una estrategia para evitar duplicar facturas, movimientos, pagos o asientos. Las claves de idempotencia o restricciones únicas se validarán en backend y base de datos según corresponda.

**RNF-09 — Auditoría (Alta).** Los eventos relevantes serán consultables por usuarios autorizados y estarán protegidos contra modificación/borrado no autorizado. Los logs no registrarán secretos ni contraseñas.

**RNF-10 — Mantenibilidad (Alta).** El código se organizará por módulos y capas con responsabilidades explícitas, contratos internos, convenciones consistentes y documentación suficiente para dar mantenimiento.

**RNF-11 — Reutilización (Alta).** Se deberán implementar y demostrar al menos cinco componentes reutilizables, documentando sus interfaces/contratos y usos en más de un módulo cuando aplique, conforme a los criterios de evaluación.

**RNF-12 — Pruebas (Alta).** Las pruebas unitarias deberán alcanzar como mínimo 70% de cobertura según la métrica acordada (recomendado: cobertura de líneas/instrucciones del backend). Se realizarán pruebas de integración, end-to-end, seguridad y carga.

**RNF-13 — Usabilidad (Media).** Las pantallas emplearán terminología consistente con el negocio, formularios con validaciones comprensibles, confirmación en operaciones críticas, estados de proceso visibles y mensajes de error accionables.

**RNF-14 — Compatibilidad (Media).** La aplicación deberá funcionar en versiones recientes de navegadores modernos acordados por el equipo y adaptarse como mínimo a resoluciones de escritorio utilizadas en demostración.

**RNF-15 — Persistencia y migraciones (Alta).** El esquema PostgreSQL se administrará con migraciones versionadas y reproducibles. Los cambios de esquema se revisarán en Git y podrán instalarse en un entorno limpio.

**RNF-16 — Respaldos y recuperación (Alta).** Se deberán generar respaldos periódicos, proteger su acceso y probar restauraciones antes de la entrega. Frecuencia, retención y tiempos objetivo de recuperación deberán acordarse y documentarse por el equipo; la narrativa original no define RPO/RTO.

**RNF-17 — Documentación (Alta).** La API contará con documentación OpenAPI/Swagger; se entregarán manual técnico, manual de usuario, matriz de trazabilidad, plan y resultados de pruebas, y presentación ejecutiva.

**RNF-18 — Exactitud monetaria (Alta).** Los importes se calcularán con tipos decimales y una política de redondeo documentada; no se utilizarán `float`/`double` para dinero.

**RNF-19 — Integridad referencial (Alta).** El esquema tendrá claves foráneas, restricciones de unicidad, restricciones para cantidades/importes y reglas de estado implementadas en la base de datos cuando sea posible, complementadas por validaciones de dominio.

**RNF-20 — Despliegue reproducible (Media).** El sistema podrá configurarse en entorno de desarrollo a partir de instrucciones y archivos versionados, con secretos fuera del repositorio. Docker Compose es la opción recomendada para la demostración integrada.

## 7. Interfaces externas

### 7.1 Interfaz de usuario

Una aplicación web React + TypeScript proporcionará navegación por módulos de acuerdo con los permisos del usuario. El frontend mostrará estados, errores de validación y confirmaciones, pero la autorización definitiva estará en el backend.

### 7.2 API de software

Spring Boot expondrá una API REST sobre HTTPS en despliegue. Los datos se intercambiarán en JSON. Los endpoints deberán utilizar códigos HTTP apropiados, DTO para solicitudes/respuestas, validación de entrada y documentación OpenAPI. La API no expondrá entidades JPA sin control ni permitirá acceso directo a repositorios.

### 7.3 Interfaz de datos

El backend utilizará Spring Data JPA/Hibernate y PostgreSQL por JDBC. Las migraciones Flyway crearán/modificarán el esquema. Las credenciales de la base se suministrarán por configuración segura, no se escribirán directamente en el código fuente y PostgreSQL no se expondrá a Internet.

### 7.4 Interfaces externas opcionales

- SMTP para notificaciones por correo. Su indisponibilidad no afectará la confirmación de una recepción, despacho o asiento.
- Generación de PDF/CSV/XLSX según el componente de reportes implementado.
- No se contempla integración directa con SAT, bancos, pasarelas, transportistas o SMS dentro del alcance inicial.

## 8. Definición de la arquitectura

### 8.1 Estilo arquitectónico

La arquitectura elegida es un **monolito modular en capas**. El backend se despliega como una aplicación Spring Boot, pero el código está dividido en módulos de dominio con responsabilidades claras. No se crean microservicios separados para compras, inventario o ventas en el MVP.

Esta decisión limita la complejidad de despliegue y permite operaciones transaccionales coordinadas en PostgreSQL, al mismo tiempo que conserva límites lógicos para mantenimiento y posible evolución.

### 8.2 Tecnologías

| Capa/función | Tecnología o decisión |
|---|---|
| Interfaz | React + TypeScript |
| Servidor de archivos/proxy | Nginx en despliegue integrado/producción; opcional en desarrollo local |
| Lenguaje backend | Java 21 |
| Framework backend | Spring Boot, con versión compatible fijada al iniciar |
| API | Spring MVC REST + JSON |
| Seguridad | Spring Security, autenticación y autorización por permisos |
| Persistencia | Spring Data JPA / Hibernate |
| Base de datos | PostgreSQL |
| Migraciones | Flyway |
| Documentación API | OpenAPI/Swagger |
| Versionado | Git + GitHub |
| Entorno de integración | Docker Compose recomendado |

### 8.3 Capas del backend

1. **API / presentación backend:** controladores REST, DTO, mapeo, validación sintáctica, manejo uniforme de errores y filtros de seguridad. No contiene reglas comerciales complejas.
2. **Aplicación:** coordina casos de uso, permisos contextuales, límites transaccionales y llamadas a contratos públicos de módulos.
3. **Dominio:** contiene entidades, estados y reglas de negocio de gestión maestra, compras, inventario, ventas, contabilidad y nómina.
4. **Persistencia e infraestructura:** implementaciones de repositorios, JPA/Hibernate, migraciones y conexiones externas. Implementa contratos definidos por capas superiores.
5. **Servicios compartidos:** auditoría, autorización común, validación, reportería, errores y notificaciones. No se deben convertir en un conjunto de dependencias globales que permita saltarse los límites de los módulos.

**Estado actual:** `com.techsupply.erp_backend.security` implementa autenticación, administración y errores propios de seguridad. `com.techsupply.erp_backend.common.api` implementa el formato de error y un `@RestControllerAdvice` global para validación de entradas y errores HTTP; el advice de seguridad conserva prioridad para sus endpoints. Este componente transversal solo tiene integración demostrada con seguridad; su reutilización en otro módulo aún está pendiente. Las entidades de seguridad contienen anotaciones JPA y sus servicios usan repositorios Spring Data directamente. La separación más estricta por puertos y los demás módulos son diseño objetivo; no se declaran implementados por aparecer en estas vistas. Auditoría, portal React, CORS, Nginx/TLS, notificaciones y jobs de despliegue/backup siguen pendientes.

### 8.4 Módulos del dominio

| Módulo | Responsabilidad y límite principal |
|---|---|
| Gestión maestra | Productos, categorías, proveedores, clientes, empleados, departamentos, puestos y almacenes. |
| Seguridad | Usuarios, roles, permisos y validación de acceso. Spring Security aplica autenticación/autorización en backend. |
| Compras | Requisiciones, autorizaciones, órdenes, recepciones, devoluciones y factura de proveedor. |
| Inventario | Existencias por producto/almacén, cantidad reservada, movimientos, conteos, ajustes y alertas. Es el único módulo autorizado para cambiar existencias. |
| Ventas | Cotizaciones, pedidos, validación comercial, reservas solicitadas a inventario, despachos, facturas comerciales y cobros iniciados/consultados. |
| Contabilidad | Plan de cuentas, periodos, asientos, cuentas por cobrar/pagar y aplicación de cobros/pagos. |
| RR. HH. y nómina | Expedientes laborales, asistencia, vacaciones, cálculo básico, aprobación y pago de nómina. |
| Reportería | Consultas e indicadores; respeta permisos y no cambia datos operativos. |
| Auditoría | Registro transversal de acciones críticas. Una sola definición de dominio/tabla compartida. |

### 8.5 Reglas de interacción entre módulos

- Compras solicita al servicio público de Inventario la aplicación de una recepción confirmada; no modifica existencias directamente.
- Ventas solicita a Inventario reservar unidades durante la confirmación del pedido y registrar la salida al confirmar el despacho.
- Compras, Ventas y Nómina proporcionan documentos de origen validados a Contabilidad. El servicio contable valida el estado, la idempotencia, el periodo y el equilibrio del asiento.
- Reportería consulta datos autorizados; no ejecuta operaciones de cambio de estado.
- Auditoría se invoca desde los casos de uso relevantes y registra actor, acción, fecha, entidad, resultado y referencia necesaria para rastrear la operación.
- Notificaciones se procesan después del commit. Para el MVP pueden ejecutarse con una estrategia simple; si se requiere entrega confiable, se evaluará un patrón Outbox.

### 8.6 Persistencia y transacciones

La base de datos será relacional y normalizada. Las relaciones principales se materializarán mediante claves foráneas y restricciones únicas, mientras que las reglas que dependen de varios registros se implementarán en servicios de aplicación y transacciones de Spring.

Operaciones que deben ser atómicas:
- Recepción confirmada: validar cantidades, actualizar existencia, insertar movimiento, actualizar estado de recepción y registrar auditoría.
- Confirmar pedido: validar disponibilidad/credito y crear reservas sin sobreventa.
- Confirmar despacho: reducir cantidad física y reserva, insertar movimiento de salida y actualizar cantidades/estado del pedido.
- Aplicar cobro o pago: crear aplicación y actualizar saldo lógico sin superar saldo de factura ni monto disponible.
- Contabilizar: validar origen/periodo/equilibrio, insertar asiento y partidas, y establecer idempotencia.
- Ajuste: aplicar solo si está autorizado, actualizar existencia y registrar movimiento dentro de la misma transacción.

En Spring, estas operaciones se pueden delimitar con `@Transactional`. La concurrencia sobre existencias necesita bloqueos/actualizaciones condicionales o una estrategia equivalente en PostgreSQL, además de restricciones de base de datos.

### 8.7 Seguridad

- Cada persona utiliza su propia cuenta.
- Las contraseñas se almacenan con hash robusto, nunca en texto plano.
- HTTPS protege el tráfico externo. Los secretos y credenciales se mantienen fuera del repositorio.
- Spring Security valida la autenticación y el backend aplica permisos por acción/módulo.
- La implementación actual usa Bearer opaco: 32 bytes aleatorios, hash SHA-256 en `tokens_sesion`, vigencia de dos horas y revocación persistida. La sesión HTTP es stateless; el token mantiene estado en PostgreSQL. JWT sería una alternativa futura que requiere decisión explícita. No escribir tokens en logs. Si se usan cookies, se protegerán solicitudes mutables contra CSRF.
- Auditoría no debe guardar contraseñas, tokens ni secretos.
- Los respaldos y datos persistentes deben tener controles de acceso y cifrado compatibles con la infraestructura.

### 8.8 Despliegue objetivo

En el entorno de desarrollo, React puede servirse con su servidor de desarrollo y el backend como aplicación Spring Boot conectada a PostgreSQL local. Para una demostración integrada/producción se propone:
- Navegador -> HTTPS -> Nginx sirviendo React y enrutando `/api`.
- Nginx -> API Spring Boot dentro de la red privada.
- Spring Boot -> PostgreSQL por JDBC dentro de red privada.
- Proceso autorizado de respaldo -> almacenamiento separado, con acceso restringido.
- SMTP opcional para notificaciones posteriores a la operación.

El MVP puede ejecutarse con una instancia de Spring Boot y una de PostgreSQL. Instancias backend adicionales son una opción futura; no equivalen automáticamente a alta disponibilidad, porque la base de datos puede seguir siendo un punto único de fallo.

### 8.9 Diagramas de arquitectura entregados

Los códigos fuente están en `docs/diagrams/arquitectura/`. Comparten estilo local y distinguen el diseño objetivo de la implementación; `docs/diagrams/README.md` define el alcance y los comandos de validación:
- `01-arquitectura-contexto-contenedores.puml`: actores, interfaz, backend, módulos y base de datos.
- `02-componentes-backend-springboot.puml`: componentes internos y dependencias.
- `03-paquetes-java-arquitectura-capas.puml`: organización del código Java por capas/módulo.
- `04-vista-despliegue-react-spring-postgres.puml`: nodos, artefactos y comunicaciones.
- `05-secuencia-recepcion-compra.puml`: atomicidad de recepción e inventario.
- `06-secuencia-venta-despacho.puml`: reserva, despacho parcial y salida.
- `07-secuencia-contabilizacion.puml`: validación de origen, periodo, equilibrio e idempotencia (objetivo).
- `08-secuencia-autenticacion.puml`: login, validación Bearer, autorización y logout implementados.
- `../despliegue/00-despliegue-actual.puml`: ejecución local y pruebas aisladas reales; las demás vistas de despliegue describen objetivos.

### 8.10 Correspondencia con el modelo 4 + 1

| Vista | Artefactos principales | Validación por escenario |
|---|---|---|
| Escenarios (+1) | Casos de uso y descripciones de flujo | Registrar recepción; confirmar pedido y despacho; contabilizar operación |
| Lógica | Seis diagramas de clases por dominio y vista general de módulos | Entidades, relaciones y reglas de negocio coherentes |
| Procesos | Diagramas de actividad y secuencia | Orden de pasos, decisiones, roles y concurrencia |
| Desarrollo/implementación | Componentes, paquetes y capas | Dependencias controladas y reutilización |
| Despliegue | Nodos, artefactos, redes y protocolos | API privada, PostgreSQL no expuesto, HTTPS y respaldos |

## 9. Requisitos de datos

### 9.1 Entidades principales

- **Maestros:** Usuario, Rol, Permiso, Empleado, Departamento, Puesto, Cliente, Proveedor, CategoriaProducto, Producto, Almacen.
- **Compras:** RequisicionCompra, DetalleRequisicion, OrdenCompra, DetalleOrdenCompra, RecepcionCompra, DetalleRecepcion, FacturaProveedor, DevolucionProveedor y detalles.
- **Inventario:** Existencia, MovimientoInventario, ConteoInventario, DetalleConteo, AjusteInventario y DetalleAjuste.
- **Ventas:** Cotizacion, DetalleCotizacion, PedidoVenta, DetallePedido, Despacho, DetalleDespacho, FacturaVenta, DetalleFactura.
- **Finanzas:** CuentaContable, PeriodoContable, AsientoContable, DetalleAsiento, CuentaPorCobrar, CuentaPorPagar, CobroCliente, PagoProveedor y sus aplicaciones.
- **RR. HH.:** ExpedienteLaboral, RegistroAsistencia, SolicitudVacaciones, PeriodoNomina, Nomina, DetalleNomina, ConceptoNomina, MovimientoNomina, PagoNomina.
- **Transversales:** EventoAuditoria, DefinicionReporte, EjecucionReporte, Indicador, ValorIndicador y, si se implementa integración asíncrona, EventoIntegracion.

### 9.2 Restricciones de almacenamiento

- Identificadores internos `Long` en Java y `bigint` autogenerado en PostgreSQL, como en seguridad y el contrato HTTP vigente. El modelo documental propaga esta convención a los módulos pendientes para mantener compatibilidad de FK; ello no implementa tablas ni autoriza cambiar datos existentes. Folios legibles únicos por tipo documental.
- SKU, correo de usuario y códigos de catálogo únicos donde aplique.
- Cantidades, importes y tasas con escala definida y validaciones de valores no negativos donde corresponda.
- Claves foráneas para relaciones persistentes.
- Índices sobre folios, SKU, estado/fecha de documentos, claves foráneas y combinaciones de consulta frecuentes.
- Unicidad de Existencia por `(producto_id, almacen_id)`.
- Evitar borrado en cascada de documentos confirmados con movimientos, facturas o asientos relacionados.
- Una estrategia de idempotencia para recepción, despacho, aplicación de pagos y contabilización.
- Migraciones versionadas y datos iniciales controlados.

## 10. Casos de aceptación de extremo a extremo

**CA-01 — Compra y recepción.** Dada una orden aprobada por 20 unidades, al confirmar una recepción con 18 aceptadas y 2 rechazadas, el sistema aumentará la cantidad física en 18, registrará el movimiento, conservará las cantidades rechazadas y mantendrá la orden abierta mientras exista saldo pendiente. Si la transacción falla, no cambiará parcialmente ni la existencia ni el documento.

**CA-02 — Sobreventa.** Dado un producto con cantidad física 10 y reserva 6, la disponibilidad es 4. El sistema no deberá permitir que un pedido reserve más de 4 unidades adicionales. Dos solicitudes concurrentes no deben comprometer unidades por encima de la existencia disponible.

**CA-03 — Despacho parcial.** Dado un pedido confirmado con 10 unidades reservadas, un despacho de 4 unidades disminuirá cantidad física en 4 y cantidad reservada en 4, registrará un movimiento de salida y dejará el pedido parcialmente despachado con 6 unidades pendientes.

**CA-04 — Pago parcial.** Dada una cuenta por cobrar de 1,000 y un cobro confirmado de 300 aplicado a la cuenta, el saldo pendiente será 700. El sistema rechazará una aplicación superior al monto disponible del cobro o al saldo de la cuenta.

**CA-05 — Contabilización.** Un asiento con debe 1,000 y haber 950 no podrá contabilizarse. Un asiento equilibrado asociado a un periodo cerrado tampoco podrá contabilizarse. Reintentar una contabilización exitosa no generará otro asiento.

**CA-06 — Autorización.** Un usuario sin permiso para ajustar inventario no podrá aplicar un ajuste enviando directamente una solicitud a la API. La denegación deberá quedar registrada según la política de auditoría.

**CA-07 — Nómina.** Una nómina que no se encuentre aprobada no podrá pasar al pago. El neto deberá coincidir con el total de percepciones menos deducciones.

**CA-08 — Reportería.** Un usuario sin permisos financieros no podrá obtener salarios, saldos contables o reportes protegidos a través de una exportación o endpoint.

## 11. Trazabilidad inicial de requisitos

| Grupo de requisitos | Caso de uso/proceso | Clases principales | Prueba sugerida |
|---|---|---|---|
| RF-COM-01 a RF-COM-08 | Compras y recepción | RequisicionCompra, OrdenCompra, RecepcionCompra, FacturaProveedor | CA-01; validar recepción parcial y factura de proveedor |
| RF-INV-01 a RF-INV-07 | Inventario, conteo y ajuste | Existencia, MovimientoInventario, ConteoInventario, AjusteInventario | CA-01, CA-02, CA-03 |
| RF-VEN-01 a RF-VEN-09 | Cotización, portal, pedido y despacho | Cotizacion, PedidoVenta, Despacho, FacturaVenta | CA-02, CA-03 |
| RF-CON-01 a RF-CON-08 | Contabilidad y aplicaciones | AsientoContable, DetalleAsiento, CuentaPorCobrar/Pagar, aplicaciones | CA-04, CA-05 |
| RF-RH-01 a RF-RH-04 | RR. HH. y nómina | ExpedienteLaboral, SolicitudVacaciones, Nomina, DetalleNomina | CA-07 |
| RF-SEG-01 a RF-SEG-04 | Seguridad | Usuario, Rol, Permiso, EventoAuditoria | CA-06, CA-08 |
| RF-REP-01 a RF-REP-05 | Dashboard e informes | DefinicionReporte, EjecucionReporte, Indicador | CA-08 y pruebas de rendimiento |
| RF-SYS-01 a RF-SYS-02 | Errores e integración | API, servicios compartidos y EventoAuditoria | pruebas de errores, reintentos y notificaciones |

La matriz completa de trazabilidad debe ampliarse cuando se creen los identificadores finales de todos los diagramas, endpoints y pruebas. No se considera completa hasta que cada requisito de prioridad Alta tenga al menos un caso de prueba identificado.

## 12. Riesgos, pendientes y criterios de cierre

### 12.1 Riesgos principales

- El alcance del caso es amplio para un proyecto académico y las fases/fechas del enunciado deben reconciliarse con el número de sprints.
- Los diagramas anteriores deben permanecer sincronizados con este SRS: clases, procesos, componentes y despliegue.
- La integración fiscal, transportistas, bancos, nómina fiscal y disponibilidad de producción no se deben prometer como funcionalidad implementada sin trabajo específico.
- Las reglas de simultaneidad del inventario y la idempotencia deben probarse con operaciones concurrentes, no solo con pruebas manuales secuenciales.
- Los umbrales de reportes, carga y disponibilidad requieren condiciones de prueba reproducibles.

### 12.2 Puntos por validar con el docente/equipo

1. Confirmar si el requisito de “máximo una hora de mantenimiento mensual” se refiere solo a mantenimiento programado o al total de indisponibilidad.
2. Confirmar cuáles módulos deben ser implementados funcionalmente y cuáles pueden quedar como diseño/futuras ampliaciones.
3. Definir los detalles de autenticación del portal de clientes y validar sus pantallas mínimas; el alcance base incluye consultar catálogo, crear pedidos y consultar estados propios.
4. Definir frecuencia/retención de respaldos y objetivo de recuperación; la especificación original no aporta RPO/RTO.
5. Definir el significado y fórmula de los indicadores de rentabilidad, margen operativo, rotación y productos obsoletos.
6. Definir el mecanismo de token/sesión de Spring Security y las medidas CSRF si se utilizan cookies.

### 12.3 Criterios para considerar aprobado el SRS

- Alcance dentro/fuera documentado y aprobado.
- Requisitos funcionales identificados con criterios de aceptación verificables.
- Requisitos no funcionales medibles o marcados como pendientes de validación.
- Reglas de negocio coherentes con los diagramas lógicos y procesos.
- Arquitectura seleccionada y justificada.
- Diagramas PlantUML generados y revisados visualmente.
- Trazabilidad inicial entre requisitos, procesos, clases y pruebas.
- Lista de supuestos y decisiones por validar aceptada por el equipo/docente.

---

**Fin del documento SRS-TSS-ERP-001, versión 1.1 — propuesta para validación académica.**
