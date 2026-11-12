/** Check the draft request examples against their OpenAPI schemas. */
const path = require('node:path');
const parserFile = require.resolve(process.env.SWAGGER_PARSER_PATH || '@apidevtools/swagger-parser');
const parser = require(parserFile);
const Ajv = require(require.resolve('ajv', { paths: [path.dirname(parserFile)] }));

(async () => {
  const api = await parser.dereference(path.join(__dirname, 'openapi-sprint-3-ventas.yaml'));
  if (api['x-implementation-status'] !== 'implemented') throw new Error('El contrato debe estar marcado como implementado');
  // OpenAPI 3.0 uses a boolean exclusiveMinimum; Ajv draft 7 uses a number.
  const seen = new WeakSet();
  function adapt(value) {
    if (!value || typeof value !== 'object' || seen.has(value)) return;
    seen.add(value);
    if (value.exclusiveMinimum === true) {
      value.exclusiveMinimum = value.minimum;
      delete value.minimum;
    } else if (value.exclusiveMinimum === false) delete value.exclusiveMinimum;
    for (const item of Object.values(value)) adapt(item);
  }
  adapt(api);
  const ajv = new Ajv({ strict: false, validateFormats: false });
  let checked = 0;
  for (const [route, item] of Object.entries(api.paths)) {
    const expectedStatus = 'implemented';
    if (item['x-implementation-status'] !== expectedStatus) throw new Error(`${route}: estado esperado ${expectedStatus}`);
    for (const operation of Object.values(item)) {
      const json = operation?.requestBody?.content?.['application/json'];
      if (!json || json.example === undefined) continue;
      const validate = ajv.compile(json.schema);
      if (!validate(json.example)) throw new Error(`${route}: ${ajv.errorsText(validate.errors)}`);
      checked++;
    }
  }
  if (checked < 5) throw new Error('Faltan ejemplos de solicitudes');
  const cases = [
    ['PedidoCreate', { cotizacionId: 1, almacenId: 1, modalidad: 'CREDITO', claveIdempotencia: 'x' }],
    ['LineaComercialWrite', { productoId: 1, cantidad: 0, precioUnitario: 10, descuentoPct: 0, tasaImpuesto: 16 }],
    ['CotizacionCreate', { clienteId: 0, vigenciaHasta: '2026-11-30', claveIdempotencia: 'x', detalles: [{ productoId: 1, cantidad: 1, precioUnitario: 10, descuentoPct: 0, tasaImpuesto: 16 }] }],
  ];
  for (const [schema, input] of cases) {
    if (ajv.compile(api.components.schemas[schema])(input)) throw new Error(`${schema}: se aceptó un caso inválido`);
  }
  console.log(`Ventas: ${checked} ejemplos válidos y ${cases.length} casos inválidos rechazados; formatos no evaluados`);
})().catch(error => { console.error(error.message); process.exitCode = 1; });
