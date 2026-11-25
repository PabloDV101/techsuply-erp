# Sprint 7 — matriz de trazabilidad de pruebas

**Revisión:** 24 nov. 2026, rama `integracion`, HEAD `aeb661a`.  
**Uso:** separar cobertura existente de brechas por atender en este sprint. Las pruebas enumeradas son evidencia del código; su última ejecución completa es la línea base JaCoCo de parte 0. La presencia de una prueba no acredita por sí sola todos los criterios de un requisito.

**Actualización parte 2:** `RecorridoHttpRealIT` usa cliente HTTP por socket, Tomcat en puerto aleatorio y PostgreSQL/Testcontainers. En un solo conjunto de datos comprueba compras, recepción, existencias y movimientos, factura y pago de proveedor, venta, factura, asiento, CxC y dashboard; incluye 401, 403 y auditoría. Los catálogos, usuarios y configuración inicial son fixtures SQL. No acredita navegador, proxy, carga ni despliegue. Los ejemplos MockMvc de la tabla conservan el alcance indicado.

## Parte 3 — evidencia de consistencia, seguridad y migraciones

| Regla o riesgo | Pruebas ejecutadas y alcance |
|---|---|
| Recepciones, stock y kardex | `ConfirmarRecepcionIT.partialReceiptThenCompleteAndIdempotentRetry`, `simultaneousReceiptsCannotOverrunAndRepeatedConfirmationIsSafe`, `lateFailureRollsBackAllAndAllRejectedCreatesNoMovement`: persistencia, asiento, auditoría, reintento, carrera y rollback. |
| Reservas, despacho y ajustes | `ConfirmarPedidoIT` (sobreventa, repetición y fallo de auditoría), `ConfirmarDespachoIT` (idempotencia concurrente, fallo de auditoría y rollback en segunda línea) y `AplicarAjusteIT` (rollback y carreras). No equivalen a una prueba de carga. |
| Facturas, cobros y pagos | `FacturaApiIT` cubre concurrencia y rollback por auditoría, reintento de outbox y cobros; `ConfirmarRecepcionIT.facturaProveedorConciliaRecepcionYContabilizaCxPUnaSolaVez` cubre aplicación de pago, permisos, reintento y competencia por saldo. |
| Sesiones y autorización | `AuthIntegrationIT` cubre 401, revocación, 403 administrativo y delegación; `DashboardApiIT` y `AnalyticalReportsApiIT` verifican fuentes y exportaciones. Rentabilidad exportada exige ambos permisos incluso en CSV/PDF/XLSX. La auditoría fallida aborta exportación y revierte una política de mínimo. |
| Políticas V25 | `AnalyticalReportsApiIT.clasificacionesConcurrentesNoSeSolapanYAnulacionEsIdempotente`: dos solicitudes solapadas producen una creación y un conflicto, la anulación repetida no duplica auditoría y una nueva clasificación conserva el historial. |
| Esquema y actualización | Flyway y validación JPA inician contra PostgreSQL 17/Testcontainers; `AccountingMigrationIT.actualizaV24AReporteriaSinInferirPoliticas` actualiza un esquema de V24 a V25, conserva existencias, costo, clasificación de flujo y permisos previos, no asigna políticas históricas ni permisos nuevos a un rol ordinario, y valida migraciones. No es un ensayo sobre copia de una base compartida. |
| Contratos | Suites `*OpenApiContractTest` y pruebas HTTP existentes; `DashboardOpenApiContractTest` revisa adicionalmente tipos de exportación y respuestas documentadas 200/401/403. No demuestra conformidad exhaustiva de cada respuesta ni consumidores externos. |

La regresión completa pasó con 91 Surefire y 229 Failsafe. El caso de concurrencia de clasificación se añadió después y pasó en una ejecución focalizada de la clase completa (4 pruebas) y su contrato (1 prueba); no se atribuye a la regresión completa anterior.

## Suites y requisitos relacionados

| Área / requisitos SRS | Evidencia existente | Tipo y datos | Cobertura observada / límite |
|---|---|---|---|
| Seguridad RF-SEG-01–04, RF-SYS-01 | `AuthIntegrationIT`, `AuthServiceTest`, `AuthOpenApiContractTest`, `GlobalApiExceptionHandlerTest` | Persistencia PostgreSQL, MockMvc/API, reglas unitarias y esquema OpenAPI. | Login/bloqueo/sesión/revocación, permisos administrativos, validación y errores. Los tests API corren con MockMvc; no ejercitan un socket HTTP real. |
| Maestros RF-MAE-01–05 | `MasterCatalogApiIT`, `MasterCatalogPersistenceIT`, `MasterCatalogMigrationIT`, `ClienteApiIT`, `ClienteOpenApiContractTest`, `CategoriaProductoIntegrationIT` | API/persistencia PostgreSQL, migraciones y contratos. | Altas, búsquedas, estados, permisos y referencias principales. Requisito parcial se conserva como tal en sus cierres. |
| Compras RF-COM-03–06 | `OrdenCompraApiIT`, `OrdenCompraPersistenceIT`, `RecepcionCompraApiIT`, `ConfirmarRecepcionIT`, `PurchaseCoreMigrationIT` | MockMvc con PostgreSQL/Testcontainers y migración. | CA-01, recepción parcial, reintento, carrera, rollback y factura proveedor/CxP. No implementa requisiciones RF-COM-01/02 ni devoluciones/historial RF-COM-07/08. |
| Inventario RF-INV-01–07 parcial | `FlujoCompraInventarioIT`, `InventarioIntegrationIT`, `AplicarAjusteIT`, `AjusteInventarioIT`, `AutorizarAjusteIT`, `ConteoInventarioIT`, `AlertaStockIT`, `MovimientoConsultaIT` | MockMvc, PostgreSQL, kardex, rollback y operaciones concurrentes. | Existencias, conteos, ajustes, fuentes, alertas y partes de CA-01/02/03/06. No implementa devoluciones/reversiones; RF-INV-07 tiene límites históricos del cierre de sprints 2 y 5. |
| Ventas RF-VEN-01–06 parcial | `ConfirmarPedidoIT`, `ConfirmarDespachoIT`, `PedidoApiIT`, `DespachoApiIT`, `FacturaApiIT`, `FacturaMigrationIT`, `CancelacionVentasIT` | MockMvc y PostgreSQL; hay pruebas de concurrencia, idempotencia y rollback. | CA-02/03; `FacturaApiIT.flujoHttpDesdeCotizacionHastaFactura`; outbox/reintento de asiento; cobro/aplicación y CA-04. No es portal externo RF-VEN-08, comisiones ni CFDI. |
| Contabilidad RF-CON-01–07 parcial | `CuentaContableApiIT`, `PeriodoContableApiIT`, `AsientoContableApiIT`, `ReportesContablesApiIT`, `AccountingMigrationIT` | API/MockMvc, PostgreSQL, Flyway, clasificación y estados contables. | CA-05, cierres/reaperturas prohibidas según decisiones, integración de fuentes y reportes con sus límites. RF-CON-08 (presupuestos) es futuro. |
| Reportería RF-REP-01–04 parcial | `DashboardApiIT`, `AnalyticalReportsApiIT`, `PrepararDashboardIT`, pruebas de contrato por operación | MockMvc/PostgreSQL, permisos, snapshot, exportación y políticas V25. | Dashboard, CSV/PDF/XLSX, fórmulas aprobadas y controles por fuente. No hay consumidor UI funcional. Nómina RF-REP-05 está fuera del alcance. |
| Migraciones RF-SYS-02 / RNF-15 | `AccountingMigrationIT`, `AuthIntegrationIT`, `SalesMigrationIT`, `PurchaseCoreMigrationIT`, `MasterCatalogMigrationIT`, `InventoryDocumentsMigrationIT`, `FacturaMigrationIT`, `CancelacionMigrationIT`, `InventoryCostPrecisionMigrationIT` | Schemas PostgreSQL aislados con Flyway. | Upgrade y preservación de datos desde varios puntos; existe prueba específica V24→V25. No sustituye ensayo de despliegue contra una copia de base compartida. |

## Casos de aceptación de extremo a extremo

| CA | Prueba existente | Estado de la evidencia | Trabajo Sprint 7 |
|---|---|---|---|
| CA-01 Compra/recepción/stock | `FlujoCompraInventarioIT`, `ConfirmarRecepcionIT` | Implementado: 20/18/2, kardex, saldo, reintentos, rollback y concurrencia. | Verificar cobertura de auditoría y persistencia final; evitar duplicar el recorrido. |
| CA-02 Sobreventa/reserva | `ConfirmarPedidoIT`, `InventarioIntegrationIT` | Implementado con concurrencia y rollback en PostgreSQL. | Reusar como regresión. |
| CA-03 Despacho parcial | `ConfirmarDespachoIT` | Implementado: parcialidad, estado de pedido, movimiento, idempotencia y concurrencia. | Reusar como regresión. |
| CA-04 Cobro parcial | `FacturaApiIT.confirmaCobroYAplicaParcialmenteConLimitesSaldoIdempotenciaYAsientos` | Implementado con saldo, límites, idempotencia y asientos. | Verificar la ruta integrada hacia estado/reportes sin rehacer el caso numérico. |
| CA-05 Contabilización | `AsientoContableApiIT.contabilizaSoloBalanceadoYRevierteEnPeriodoAbiertoConIdempotencia`, `PeriodoContableApiIT` | Implementado para balance, periodo e idempotencia. | Reusar como regresión y revisar contrato de error. |
| CA-06 Autorización | `AplicarAjusteIT`, `FlujoCompraInventarioIT`, `AuthIntegrationIT` | Implementado para 401/403 y denegación; cierta auditoría es de mejor esfuerzo según política. | Probar que no se filtran datos en nuevas rutas/exports, sin redefinir política aprobada. |
| CA-07 Nómina | Sin prueba operativa | No implementado; depende de nómina y fuera del sprint 5. | Registrar fuera de alcance; no simular como requisito cumplido. |
| CA-08 Reportería protegida | `DashboardApiIT`, `AnalyticalReportsApiIT`, `ReportesContablesApiIT` | Implementado para fuentes existentes y permisos; salarios no existen en el sistema. | Verificar explícitamente denegación en endpoints y formatos de exportación. |

## Requisitos no funcionales

| RNF | Evidencia observada al inicio | Brecha y criterio de evaluación |
|---|---|---|
| RNF-02 consultas p95 <2 s | Parte 5: con 200 sesiones, p95 categorías 2.074 ms y productos 2.049 ms en ejecución extendida sintética. | No alcanzado en ese escenario; pendiente medir y perfilar con datos y entorno representativos. |
| RNF-03 reportes p95 <5 s | Parte 5: p95 stock bajo 2.270 ms y dashboard 2.150 ms; sin errores en 1.600 solicitudes por ruta a 200 sesiones. | Umbrales alcanzados solo para estas dos rutas y dataset local sin facturas ni kardex; no acredita todos los reportes. |
| RNF-04 200 usuarios | Parte 5: 200 sesiones distintas, pico cliente 200 solicitudes en vuelo y 6.400 lecturas válidas en 44,77 s. | Carga cerrada y corta, sin login, escrituras ni red externa; no acredita capacidad sostenida ni todos los módulos. |
| RNF-12 cobertura unitaria ≥70 % | Parte 4: selección de 61 pruebas `*Test` excluyendo `*OpenApiContractTest`, 765/4533 líneas (16,88 %), con mocks aislados y sin Testcontainers. | Brecha de 53,12 puntos porcentuales respecto a 70 %. La cobertura combinada no sustituye la unitaria; la selección y sus límites se describen en la bitácora del sprint. |
| RNF-17 manuales/trazabilidad | README, guía de API, documentos de cierre y trazabilidad inicial en el SRS. | No se encontró manual de usuario ni guion de demostración; la matriz actual mezcla diseño y evidencia histórica. Completar documentos técnicos y de usuario solo para flujos existentes. |
| Integración navegador | Build/lint de frontend pasan. `App.tsx` contiene plantilla Vite. | Sin pantallas ERP, script de tests, Playwright/Cypress ni llamada al backend; pendiente de entrega del compañero. |

## Decisiones de ejecución para la parte 2

1. No repetir los casos CA-01–06 que ya ejecutan flujo real de negocio con Testcontainers; integrar solo una frontera aún no cubierta.
2. Al inicio había 31 archivos de pruebas con `MockMvc` y la búsqueda no encontró `TestRestTemplate`, `WebTestClient` ni `RANDOM_PORT`. La parte 2 agregó `RecorridoHttpRealIT` con servidor y cliente HTTP reales; las pruebas MockMvc anteriores siguen sin cubrir socket, proxy ni cliente de red.
3. La brecha vertical de mayor valor es comprobar un mismo conjunto de datos desde documentos operativos hasta factura/CxP o CxC y reporte/dashboard, leyendo estado final desde PostgreSQL. Diseñar un caso acotado que reutilice fixtures existentes.
4. Requisiciones RF-COM-01/02, devoluciones, portal RF-VEN-08, nómina y presupuesto son brechas funcionales conocidas, no fallos de regresión; no introducirlas como parte del sprint de testing sin decisión explícita de alcance.
5. La suite completa baseline pasó; durante la parte 2 y siguientes usar pruebas focalizadas en cada cambio y regresar a `verify` en el cierre. Coverage, carga y prueba de navegador se reportan en sus partes propias.
