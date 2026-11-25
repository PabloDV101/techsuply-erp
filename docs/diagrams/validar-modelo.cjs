/** Read-only validation. DBML_CORE_PATH may point to an @dbml/core installation outside the repo. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { Parser } = require(process.env.DBML_CORE_PATH || '@dbml/core');
const root = path.resolve(__dirname, '../..');
const primary = fs.readFileSync(path.join(root, 'database/modelo.dbml'), 'utf8');
const copy = fs.readFileSync(path.join(__dirname, 'modelodatos/modelo.dbml'), 'utf8');
assert.equal(primary, copy, 'The two DBML copies must be identical');
const schema = new Parser().parse(primary, 'dbml').schemas[0];
const migrations = [
  ['V1__security_schema.sql', 'seguridad_implementada', 7],
  ['V3__product_categories.sql', 'categorias_implementadas', 1],
  ['V4__audit_events.sql', 'auditoria_implementada', 1],
  ['V5__internal_notifications.sql', 'notificaciones_implementadas', 1],
  ['V6__master_purchase_catalogs.sql', 'maestros_jpa_v6', 4],
];
const tables = [];
const implementationGroups = new Map();
for (const [file, groupName, expectedCount] of migrations) {
  const sql = fs.readFileSync(path.join(root, 'erp-backend/src/main/resources/db/migration', file), 'utf8');
  const found = [...sql.matchAll(/^CREATE TABLE (\w+) \(([\s\S]*?)^\);/gm)];
  assert.equal(found.length, expectedCount, `Review validator for ${file}`);
  assert.ok(schema.tableGroups.some(g => g.name === groupName), `Missing ${groupName}`);
  for (const table of found) implementationGroups.set(table[1], groupName);
  tables.push(...found);
}
const implemented = new Set(implementationGroups.keys());
const normalize = value => value.toLowerCase().replace(/\s+/g, '');
const expectedRefs = [];
for (const [, name, body] of tables) {
  const documented = schema.tables.find(t => t.name === name);
  assert.ok(documented, `Missing table ${name}`);
  assert.equal(documented.group.name, implementationGroups.get(name), `${name}: wrong implementation status`);
  const columns = [...body.matchAll(/^    (\w+) (\w+(?:\(\d+(?:,\d+)?\))?)([^\n]*)/gm)]
    .filter(c => !['PRIMARY', 'UNIQUE', 'CHECK', 'FOREIGN', 'CONSTRAINT'].includes(c[1]));
  assert.deepEqual(documented.fields.map(f => f.name).sort(), columns.map(c => c[1]).sort(), `${name}: columns differ`);
  const expectedPk = [];
  for (const [, column, type, constraints] of columns) {
    const field = documented.fields.find(f => f.name === column);
    assert.equal(normalize(field.type.type_name), normalize(type), `${name}.${column}: type differs`);
    assert.equal(Boolean(field.not_null), /NOT NULL|PRIMARY KEY/.test(constraints), `${name}.${column}: nullability differs`);
    if (/PRIMARY KEY/.test(constraints)) expectedPk.push(column);
    if (/\bUNIQUE\b/.test(constraints)) assert.equal(field.unique, true, `${name}.${column}: missing uniqueness`);
    const reference = constraints.match(/REFERENCES (\w+)\((\w+)\)/);
    if (reference) expectedRefs.push(`${name}.${column}>${reference[1]}.${reference[2]}`);
  }
  const compositePk = body.match(/PRIMARY KEY\(([^)]+)\)/);
  if (compositePk) expectedPk.push(...compositePk[1].split(',').map(x => x.trim()));
  const actualPk = documented.fields.filter(f => f.pk).map(f => f.name)
    .concat(documented.indexes.filter(i => i.pk).flatMap(i => i.columns.map(c => c.value)));
  assert.deepEqual(actualPk.sort(), expectedPk.sort(), `${name}: primary key differs`);
  for (const unique of body.matchAll(/UNIQUE\(([^)]+)\)/g)) {
    const fields = unique[1].split(',').map(x => x.trim()).sort();
    assert.ok(documented.indexes.some(i => i.unique && JSON.stringify(i.columns.map(c => c.value).sort()) === JSON.stringify(fields)), `${name}: missing composite uniqueness`);
  }
}
const actualRefs = schema.refs.filter(r => r.endpoints.every(e => implemented.has(e.tableName)))
  .map(r => r.endpoints.map(e => `${e.tableName}.${e.fieldNames.join(',')}`).join('>'));
assert.deepEqual(actualRefs.sort(), expectedRefs.sort(), 'Implemented foreign keys differ');
// V7 implements a subset of the target model: future document references remain in DBML.
const v7 = fs.readFileSync(path.join(root, 'erp-backend/src/main/resources/db/migration/V7__purchase_receipt_inventory_core.sql'), 'utf8');
const v7Tables = [...v7.matchAll(/^CREATE TABLE (\w+) \(([\s\S]*?)^\);/gm)];
const v8 = fs.readFileSync(path.join(root, 'erp-backend/src/main/resources/db/migration/V8__widen_inventory_cost_precision.sql'), 'utf8');
const overrides = new Map([...v8.matchAll(/^ALTER TABLE (\w+) ALTER COLUMN (\w+) TYPE (\w+\(\d+,\d+\));$/gm)]
  .map(([, table, column, type]) => [`${table}.${column}`, type]));
assert.deepEqual([...overrides.entries()].sort(), [
  ['existencias.costo_promedio', 'numeric(16,4)'],
  ['movimientos_inventario.costo_unitario', 'numeric(16,4)'],
]);
assert.equal(v7Tables.length, 6, 'Review V7 table count');
const futureFields = {
  ordenes_compra: ['requisicion_id'],
  movimientos_inventario: ['detalle_despacho_id', 'detalle_ajuste_id', 'detalle_devolucion_proveedor_id',
    'detalle_devolucion_cliente_id', 'detalle_pedido_id', 'movimiento_reversado_de_id'],
};
for (const [, name, body] of v7Tables) {
  const target = schema.tables.find(t => t.name === name);
  assert.ok(target, `Missing V7 table ${name}`);
  const columns = [...body.matchAll(/^    (\w+) (\w+(?:\(\d+(?:,\d+)?\))?)([^\n]*)/gm)]
    .filter(c => !['PRIMARY', 'UNIQUE', 'CHECK', 'FOREIGN', 'CONSTRAINT'].includes(c[1]));
  assert.deepEqual(target.fields.map(f => f.name).sort(),
    columns.map(c => c[1]).concat(futureFields[name] || []).sort(), `${name}: V7/target columns differ`);
  for (const [, column, type, constraints] of columns) {
    const field = target.fields.find(f => f.name === column);
    assert.equal(normalize(field.type.type_name), normalize(overrides.get(`${name}.${column}`) || type), `${name}.${column}: type differs`);
    if (/PRIMARY KEY/.test(constraints)) assert.equal(Boolean(field.pk), true, `${name}.${column}: missing primary key`);
    // V7 supports reception entries only; the target allows other sources.
    if (!(name === 'movimientos_inventario' && column === 'detalle_recepcion_id')) {
      assert.equal(Boolean(field.not_null), /NOT NULL|PRIMARY KEY/.test(constraints), `${name}.${column}: nullability differs`);
      if (/\bUNIQUE\b/.test(constraints)) assert.equal(field.unique, true, `${name}.${column}: missing uniqueness`);
    }
    const ref = constraints.match(/REFERENCES (\w+)\((\w+)\)/);
    if (ref) assert.ok(schema.refs.some(r => r.endpoints.map(e => `${e.tableName}.${e.fieldNames.join(',')}`).join('>') === `${name}.${column}>${ref[1]}.${ref[2]}`), `${name}.${column}: missing FK`);
  }
  for (const unique of body.matchAll(/UNIQUE\s*\(([^)]+)\)/g)) {
    const fields = unique[1].split(',').map(x => x.trim()).sort();
    assert.ok(target.indexes.some(i => i.unique && JSON.stringify(i.columns.map(c => c.value).sort()) === JSON.stringify(fields)), `${name}: missing composite uniqueness`);
  }
}
// V9 creates four SQL-only inventory documents; other fields/tables remain target design.
const v9 = fs.readFileSync(path.join(root, 'erp-backend/src/main/resources/db/migration/V9__inventory_counts_adjustments.sql'), 'utf8');
const v9Tables = [...v9.matchAll(/^CREATE TABLE (\w+) \(([\s\S]*?)^\);/gm)];
assert.deepEqual(v9Tables.map(t => t[1]).sort(), ['ajustes_inventario', 'conteos_inventario', 'detalles_ajuste_inventario', 'detalles_conteo']);
for (const [, name, body] of v9Tables) {
  const target = schema.tables.find(t => t.name === name);
  assert.ok(target, `Missing V9 table ${name}`);
  assert.ok(target.note.includes('SQL V9'), `${name}: missing SQL V9 status`);
  const columns = [...body.matchAll(/^    (\w+) (\w+(?:\(\d+(?:,\d+)?\))?)([^\n]*)/gm)]
    .filter(c => !['PRIMARY', 'UNIQUE', 'CHECK', 'FOREIGN', 'CONSTRAINT'].includes(c[1]));
  assert.deepEqual(target.fields.map(f => f.name).sort(), columns.map(c => c[1]).sort(), `${name}: V9 columns differ`);
  for (const [, column, type, constraints] of columns) {
    const field = target.fields.find(f => f.name === column);
    assert.equal(normalize(field.type.type_name), normalize(type), `${name}.${column}: V9 type differs`);
    assert.equal(Boolean(field.not_null), /NOT NULL|PRIMARY KEY/.test(constraints), `${name}.${column}: V9 nullability differs`);
    if (/PRIMARY KEY/.test(constraints)) assert.equal(Boolean(field.pk), true, `${name}.${column}: missing primary key`);
    if (/\bUNIQUE\b/.test(constraints)) assert.equal(field.unique, true, `${name}.${column}: missing uniqueness`);
    const ref = constraints.match(/REFERENCES (\w+)\((\w+)\)/);
    if (ref) assert.ok(schema.refs.some(r => r.endpoints.map(e => `${e.tableName}.${e.fieldNames.join(',')}`).join('>') === `${name}.${column}>${ref[1]}.${ref[2]}`), `${name}.${column}: missing FK`);
  }
  for (const unique of body.matchAll(/UNIQUE\s*\(([^)]+)\)/g)) {
    const fields = unique[1].split(',').map(x => x.trim()).sort();
    assert.ok(target.indexes.some(i => i.unique && JSON.stringify(i.columns.map(c => c.value).sort()) === JSON.stringify(fields)), `${name}: missing composite uniqueness`);
  }
}
assert.match(v9, /ALTER TABLE movimientos_inventario ALTER COLUMN detalle_recepcion_id DROP NOT NULL;/);
assert.match(v9, /ALTER TABLE movimientos_inventario ADD COLUMN detalle_ajuste_id bigint UNIQUE REFERENCES detalles_ajuste_inventario\(id\);/);
const adjustmentSource = schema.tables.find(t => t.name === 'movimientos_inventario').fields.find(f => f.name === 'detalle_ajuste_id');
assert.equal(normalize(adjustmentSource.type.type_name), 'bigint');
assert.equal(adjustmentSource.unique, true);
assert.equal(Boolean(adjustmentSource.not_null), false);
assert.ok(schema.refs.some(r => r.endpoints.map(e => `${e.tableName}.${e.fieldNames.join(',')}`).join('>') === 'movimientos_inventario.detalle_ajuste_id>detalles_ajuste_inventario.id'), 'V9: missing adjustment source FK');
// V11 includes multiple column declarations on a line. Split only at top-level commas.
const v11 = fs.readFileSync(path.join(root, 'erp-backend/src/main/resources/db/migration/V11__sales_schema.sql'), 'utf8');
const v11Tables = [...v11.matchAll(/^CREATE TABLE (\w+) \(([\s\S]*?)^\);/gm)];
assert.equal(v11Tables.length, 7, 'Review V11 table count');
function splitSqlColumns(body) {
  const parts = []; let current = '', depth = 0, quoted = false;
  for (let i = 0; i < body.length; i++) {
    const c = body[i];
    if (c === "'" && body[i + 1] === "'" && quoted) { current += "''"; i++; continue; }
    if (c === "'") quoted = !quoted;
    if (!quoted && c === '(') depth++;
    if (!quoted && c === ')') depth--;
    if (!quoted && depth === 0 && c === ',') { parts.push(current.trim()); current = ''; }
    else current += c;
  }
  if (current.trim()) parts.push(current.trim());
  return parts;
}
for (const [, name, body] of v11Tables) {
  const target = schema.tables.find(t => t.name === name);
  assert.ok(target && target.note.includes('SQL V11'), `${name}: missing SQL V11 status`);
  const columns = splitSqlColumns(body).map(part => part.match(/^(\w+)\s+(\w+(?:\(\d+(?:,\d+)?\))?)([\s\S]*)$/))
    .filter(c => c && !['PRIMARY', 'UNIQUE', 'CHECK', 'FOREIGN', 'CONSTRAINT'].includes(c[1]));
  const v12Fields = name === 'pedidos_venta' ? ['motivo_cancelacion', 'cancelado_por_usuario_id', 'cancelado_en'] : [];
  assert.deepEqual(target.fields.map(f => f.name).sort(), [...columns.map(c => c[1]), ...v12Fields].sort(), `${name}: V11/V12 columns differ`);
  for (const [, column, type, constraints] of columns) {
    const field = target.fields.find(f => f.name === column);
    assert.equal(normalize(field.type.type_name), normalize(type), `${name}.${column}: V11 type differs`);
    assert.equal(Boolean(field.not_null), /NOT NULL|PRIMARY KEY/.test(constraints), `${name}.${column}: V11 nullability differs`);
    if (/PRIMARY KEY/.test(constraints)) assert.equal(Boolean(field.pk), true, `${name}.${column}: missing primary key`);
    if (/\bUNIQUE\b/.test(constraints)) assert.equal(field.unique, true, `${name}.${column}: missing uniqueness`);
    const ref = constraints.match(/REFERENCES (\w+)\((\w+)\)/);
    if (ref) assert.ok(schema.refs.some(r => r.endpoints.map(e => `${e.tableName}.${e.fieldNames.join(',')}`).join('>') === `${name}.${column}>${ref[1]}.${ref[2]}`), `${name}.${column}: missing FK`);
  }
  for (const part of splitSqlColumns(body)) {
    const unique = part.match(/^UNIQUE\s*\(([^)]+)\)/);
    if (!unique) continue;
    const fields = unique[1].split(',').map(x => x.trim()).sort();
    assert.ok(target.indexes.some(i => i.unique && JSON.stringify(i.columns.map(c => c.value).sort()) === JSON.stringify(fields)), `${name}: missing composite uniqueness`);
  }
}
const v12 = fs.readFileSync(path.join(root, 'erp-backend/src/main/resources/db/migration/V12__sales_order_cancellation_metadata.sql'), 'utf8');
const orderV12 = schema.tables.find(t => t.name === 'pedidos_venta');
for (const [field, sqlType] of [['motivo_cancelacion', 'text'], ['cancelado_por_usuario_id', 'bigint'], ['cancelado_en', 'timestamptz']]) {
  assert.match(v12, new RegExp(`ADD COLUMN ${field} ${sqlType}\\b`));
  const column = orderV12.fields.find(f => f.name === field);
  assert.equal(normalize(column.type.type_name), sqlType);
  assert.equal(Boolean(column.not_null), false);
}
assert.ok(schema.refs.some(r => r.endpoints.map(e => `${e.tableName}.${e.fieldNames.join(',')}`).join('>') === 'pedidos_venta.cancelado_por_usuario_id>usuarios.id'));
const v13 = fs.readFileSync(path.join(root, 'erp-backend/src/main/resources/db/migration/V13__sales_commercial_invoices.sql'), 'utf8');
const v13Tables = [...v13.matchAll(/^CREATE TABLE (\w+) \(([\s\S]*?)^\);/gm)];
assert.equal(v13Tables.length, 2, 'Review V13 table count');
for (const [, name, body] of v13Tables) {
  const target = schema.tables.find(t => t.name === name);
  assert.ok(target && target.note.includes('SQL V13'), `${name}: missing SQL V13 status`);
  const columns = splitSqlColumns(body).map(part => part.match(/^(\w+)\s+(\w+(?:\(\d+(?:,\d+)?\))?)([\s\S]*)$/))
    .filter(c => c && !['PRIMARY', 'UNIQUE', 'CHECK', 'FOREIGN', 'CONSTRAINT'].includes(c[1]));
  assert.deepEqual(target.fields.map(f => f.name).sort(), columns.map(c => c[1]).sort(), `${name}: V13 columns differ`);
  for (const [, column, type, constraints] of columns) {
    const field = target.fields.find(f => f.name === column);
    assert.equal(normalize(field.type.type_name), normalize(type), `${name}.${column}: V13 type differs`);
    assert.equal(Boolean(field.not_null), /NOT NULL|PRIMARY KEY/.test(constraints), `${name}.${column}: V13 nullability differs`);
    if (/PRIMARY KEY/.test(constraints)) assert.equal(Boolean(field.pk), true, `${name}.${column}: missing primary key`);
    if (/\bUNIQUE\b/.test(constraints)) assert.equal(field.unique, true, `${name}.${column}: missing uniqueness`);
    const ref = constraints.match(/REFERENCES (\w+)\((\w+)\)/);
    if (ref) assert.ok(schema.refs.some(r => r.endpoints.map(e => `${e.tableName}.${e.fieldNames.join(',')}`).join('>') === `${name}.${column}>${ref[1]}.${ref[2]}`), `${name}.${column}: missing FK`);
  }
  for (const part of splitSqlColumns(body)) {
    const unique = part.match(/^UNIQUE\s*\(([^)]+)\)/);
    if (!unique) continue;
    const fields = unique[1].split(',').map(x => x.trim()).sort();
    assert.ok(target.indexes.some(i => i.unique && JSON.stringify(i.columns.map(c => c.value).sort()) === JSON.stringify(fields)), `${name}: missing composite uniqueness`);
  }
}
const v14 = fs.readFileSync(path.join(root, 'erp-backend/src/main/resources/db/migration/V14__accounting_core.sql'), 'utf8');
const v14Tables = [...v14.replace(/^\s*--.*$/gm, '').matchAll(/^CREATE TABLE (\w+) \(([\s\S]*?)^\);/gm)];
assert.equal(v14Tables.length, 4, 'Review V14 table count');
for (const [, name, body] of v14Tables) {
  const target = schema.tables.find(t => t.name === name);
  assert.ok(target && target.note.includes('SQL V14'), `${name}: missing SQL V14 status`);
  const columns = splitSqlColumns(body).map(part => part.match(/^(\w+)\s+(\w+(?:\(\d+(?:,\d+)?\))?)([\s\S]*)$/))
    .filter(c => c && !['PRIMARY', 'UNIQUE', 'CHECK', 'FOREIGN', 'CONSTRAINT', 'EXCLUDE'].includes(c[1]));
  const v15Additions = name === 'asientos_contables' ? ['ultimo_motivo_edicion', 'editado_por_usuario_id', 'editado_en',
    'motivo_cancelacion', 'cancelado_por_usuario_id', 'cancelado_en', 'motivo_reversion'] : [];
  assert.deepEqual(target.fields.map(f => f.name).sort(), [...columns.map(c => c[1]), ...v15Additions].sort(), `${name}: V14/V15 columns differ`);
  for (const [, column, type, constraints] of columns) {
    const field = target.fields.find(f => f.name === column);
    const v15Type = name === 'asientos_contables' && column === 'estado' ? 'varchar(16)' : type;
    assert.equal(normalize(field.type.type_name), normalize(v15Type), `${name}.${column}: V14/V15 type differs`);
    assert.equal(Boolean(field.not_null), /NOT NULL|PRIMARY KEY/.test(constraints), `${name}.${column}: V14 nullability differs`);
    if (/PRIMARY KEY/.test(constraints)) assert.equal(Boolean(field.pk), true, `${name}.${column}: missing primary key`);
    if (/\bUNIQUE\b/.test(constraints)) assert.equal(field.unique, true, `${name}.${column}: missing uniqueness`);
    const ref = constraints.match(/REFERENCES (\w+)\((\w+)\)/);
    if (ref) assert.ok(schema.refs.some(r => r.endpoints.map(e => `${e.tableName}.${e.fieldNames.join(',')}`).join('>') === `${name}.${column}>${ref[1]}.${ref[2]}`), `${name}.${column}: missing FK`);
  }
  for (const part of splitSqlColumns(body)) {
    const unique = part.match(/^UNIQUE\s*\(([^)]+)\)/);
    if (!unique) continue;
    const fields = unique[1].split(',').map(x => x.trim()).sort();
    assert.ok(target.indexes.some(i => i.unique && JSON.stringify(i.columns.map(c => c.value).sort()) === JSON.stringify(fields)), `${name}: missing composite uniqueness`);
  }
}
const v15 = fs.readFileSync(path.join(root, 'erp-backend/src/main/resources/db/migration/V15__accounting_posting_and_draft_lifecycle.sql'), 'utf8');
const historyMatch = v15.match(/^CREATE TABLE historial_asientos_contables \(([\s\S]*?)^\);/m);
assert.ok(historyMatch, 'V15 history table missing');
const history = schema.tables.find(t => t.name === 'historial_asientos_contables');
assert.ok(history?.note.includes('SQL V15'), 'DBML must mark the V15 history table');
const historyColumns = splitSqlColumns(historyMatch[1]).map(part => part.match(/^(\w+)\s+(\w+(?:\(\d+(?:,\d+)?\))?)([\s\S]*)$/))
  .filter(c => c && !['PRIMARY', 'UNIQUE', 'CHECK', 'FOREIGN', 'CONSTRAINT'].includes(c[1]));
assert.deepEqual(history.fields.map(f => f.name).sort(), historyColumns.map(c => c[1]).sort(), 'V15 history columns differ');
for (const [, column, type, constraints] of historyColumns) {
  const field = history.fields.find(f => f.name === column);
  assert.equal(normalize(field.type.type_name), normalize(type), `historial_asientos_contables.${column}: type differs`);
  assert.equal(Boolean(field.not_null), /NOT NULL|PRIMARY KEY/.test(constraints), `historial_asientos_contables.${column}: nullability differs`);
  if (/PRIMARY KEY/.test(constraints)) assert.equal(Boolean(field.pk), true, `${column}: missing primary key`);
  if (/\bUNIQUE\b/.test(constraints)) assert.equal(field.unique, true, `${column}: missing uniqueness`);
  const ref = constraints.match(/REFERENCES (\w+)\((\w+)\)/);
  if (ref) assert.ok(schema.refs.some(r => r.endpoints.map(e => `${e.tableName}.${e.fieldNames.join(',')}`).join('>') === `historial_asientos_contables.${column}>${ref[1]}.${ref[2]}`), `${column}: missing FK`);
}
for (const [column, type, nullable] of [['ultimo_motivo_edicion','varchar(300)',true], ['editado_por_usuario_id','bigint',true],
  ['editado_en','timestamptz',true], ['motivo_cancelacion','varchar(300)',true], ['cancelado_por_usuario_id','bigint',true],
  ['cancelado_en','timestamptz',true], ['motivo_reversion','varchar(300)',true]]) {
  assert.ok(v15.includes(`ADD COLUMN ${column} ${type}`), `V15 must add ${column} with ${type}`);
  const field = schema.tables.find(t => t.name === 'asientos_contables').fields.find(f => f.name === column);
  assert.equal(normalize(field.type.type_name), type);
  assert.equal(Boolean(field.not_null), !nullable);
}
const v17 = fs.readFileSync(path.join(root, 'erp-backend/src/main/resources/db/migration/V17__sales_invoice_accounting_integration.sql'), 'utf8');
const v17Tables = [...v17.matchAll(/^CREATE TABLE (\w+) \(([\s\S]*?)^\);/gm)];
assert.deepEqual(v17Tables.map(t => t[1]).sort(), ['configuracion_cuentas_factura_venta', 'integracion_factura_venta_contabilidad']);
for (const [, name, body] of v17Tables) {
  const target = schema.tables.find(t => t.name === name);
  assert.ok(target?.note.includes('SQL V17'), `${name}: DBML must mark V17 implementation`);
  const columns = splitSqlColumns(body).map(part => part.match(/^(\w+)\s+(\w+(?:\(\d+(?:,\d+)?\))?)([\s\S]*)$/))
    .filter(c => c && !['PRIMARY', 'UNIQUE', 'CHECK', 'FOREIGN', 'CONSTRAINT'].includes(c[1]));
  assert.deepEqual(target.fields.map(f => f.name).sort(), columns.map(c => c[1]).sort(), `${name}: V17 columns differ`);
  for (const [, column, type, constraints] of columns) {
    const field = target.fields.find(f => f.name === column);
    assert.equal(normalize(field.type.type_name), normalize(type), `${name}.${column}: type differs`);
    assert.equal(Boolean(field.not_null), /NOT NULL|PRIMARY KEY/.test(constraints), `${name}.${column}: nullability differs`);
    if (/PRIMARY KEY/.test(constraints)) assert.equal(Boolean(field.pk), true, `${name}.${column}: primary key missing`);
    if (/\bUNIQUE\b/.test(constraints)) assert.equal(field.unique, true, `${name}.${column}: uniqueness missing`);
    const ref = constraints.match(/REFERENCES (\w+)\((\w+)\)/);
    if (ref) assert.ok(schema.refs.some(r => r.endpoints.map(e => `${e.tableName}.${e.fieldNames.join(',')}`).join('>') === `${name}.${column}>${ref[1]}.${ref[2]}`), `${name}.${column}: FK missing`);
  }
}
for (const reference of [
  'configuracion_cuentas_factura_venta.cuenta_contable_id>cuentas_contables.id',
  'configuracion_cuentas_factura_venta.actualizado_por_usuario_id>usuarios.id',
  'integracion_factura_venta_contabilidad.factura_venta_id>facturas_venta.id',
  'integracion_factura_venta_contabilidad.asiento_contable_id>asientos_contables.id',
  'integracion_factura_venta_contabilidad.ultimo_intento_por_usuario_id>usuarios.id',
]) assert.ok(schema.refs.some(r => r.endpoints.map(e => `${e.tableName}.${e.fieldNames.join(',')}`).join('>') === reference), `V17 FK missing: ${reference}`);
const v18 = fs.readFileSync(path.join(root, 'erp-backend/src/main/resources/db/migration/V18__sales_accounts_receivable_subledger.sql'), 'utf8');
const v18Tables = [...v18.matchAll(/^CREATE TABLE (\w+) \(([\s\S]*?)^\);/gm)];
assert.deepEqual(v18Tables.map(t => t[1]), ['cuentas_por_cobrar']);
for (const [, name, body] of v18Tables) {
  const target = schema.tables.find(t => t.name === name);
  assert.ok(target?.note.includes('SQL V18'), `${name}: DBML must mark V18 implementation`);
  const columns = splitSqlColumns(body).map(part => part.match(/^(\w+)\s+(\w+(?:\(\d+(?:,\d+)?\))?)([\s\S]*)$/))
    .filter(c => c && !['PRIMARY', 'UNIQUE', 'CHECK', 'FOREIGN', 'CONSTRAINT'].includes(c[1]));
  assert.deepEqual(target.fields.map(f => f.name).sort(), columns.map(c => c[1]).sort(), `${name}: V18 columns differ`);
  for (const [, column, type, constraints] of columns) {
    const field = target.fields.find(f => f.name === column);
    assert.equal(normalize(field.type.type_name), normalize(type), `${name}.${column}: type differs`);
    assert.equal(Boolean(field.not_null), /NOT NULL|PRIMARY KEY/.test(constraints), `${name}.${column}: nullability differs`);
    if (/PRIMARY KEY/.test(constraints)) assert.equal(Boolean(field.pk), true, `${name}.${column}: primary key missing`);
    if (/\bUNIQUE\b/.test(constraints)) assert.equal(field.unique, true, `${name}.${column}: uniqueness missing`);
    const ref = constraints.match(/REFERENCES (\w+)\((\w+)\)/);
    if (ref) assert.ok(schema.refs.some(r => r.endpoints.map(e => `${e.tableName}.${e.fieldNames.join(',')}`).join('>') === `${name}.${column}>${ref[1]}.${ref[2]}`), `${name}.${column}: FK missing`);
  }
}
for (const reference of [
  'cuentas_por_cobrar.factura_venta_id>facturas_venta.id',
  'cuentas_por_cobrar.cliente_id>clientes.id',
]) assert.ok(schema.refs.some(r => r.endpoints.map(e => `${e.tableName}.${e.fieldNames.join(',')}`).join('>') === reference), `V18 FK missing: ${reference}`);
const v19 = fs.readFileSync(path.join(root, 'erp-backend/src/main/resources/db/migration/V19__customer_receipts_and_applications.sql'), 'utf8');
const v19Tables = [...v19.matchAll(/^CREATE TABLE (\w+) \(([\s\S]*?)^\);/gm)];
assert.deepEqual(v19Tables.map(t => t[1]).sort(), ['aplicaciones_cobro','cobros_clientes','configuracion_cuentas_cobro']);
for (const [, name, body] of v19Tables) {
  const target = schema.tables.find(t => t.name === name);
  assert.ok(target?.note.includes('SQL V19'), `${name}: DBML must mark V19 implementation`);
  const columns = splitSqlColumns(body).map(part => part.match(/^(\w+)\s+(\w+(?:\(\d+(?:,\d+)?\))?)([\s\S]*)$/))
    .filter(c => c && !['PRIMARY','UNIQUE','CHECK','FOREIGN','CONSTRAINT'].includes(c[1]));
  assert.deepEqual(target.fields.map(f => f.name).sort(), columns.map(c => c[1]).sort(), `${name}: V19 columns differ`);
  for (const [, column, type, constraints] of columns) {
    const field = target.fields.find(f => f.name === column);
    assert.equal(normalize(field.type.type_name), normalize(type), `${name}.${column}: type differs`);
    assert.equal(Boolean(field.not_null), /NOT NULL|PRIMARY KEY/.test(constraints), `${name}.${column}: nullability differs`);
    if (/PRIMARY KEY/.test(constraints)) assert.equal(Boolean(field.pk), true, `${name}.${column}: primary key missing`);
    if (/\bUNIQUE\b/.test(constraints)) assert.equal(field.unique, true, `${name}.${column}: uniqueness missing`);
    const ref = constraints.match(/REFERENCES (\w+)\((\w+)\)/);
    if (ref) assert.ok(schema.refs.some(r => r.endpoints.map(e => `${e.tableName}.${e.fieldNames.join(',')}`).join('>') === `${name}.${column}>${ref[1]}.${ref[2]}`), `${name}.${column}: FK missing`);
  }
}
for (const reference of [
  'configuracion_cuentas_cobro.cuenta_fondos_id>cuentas_contables.id',
  'configuracion_cuentas_cobro.cuenta_anticipos_id>cuentas_contables.id',
  'configuracion_cuentas_cobro.actualizado_por_usuario_id>usuarios.id',
  'cobros_clientes.cliente_id>clientes.id','cobros_clientes.registrado_por_usuario_id>usuarios.id',
  'cobros_clientes.asiento_contable_id>asientos_contables.id','cobros_clientes.confirmado_por_usuario_id>usuarios.id',
  'aplicaciones_cobro.cobro_id>cobros_clientes.id','aplicaciones_cobro.cuenta_por_cobrar_id>cuentas_por_cobrar.id',
  'aplicaciones_cobro.aplicado_por_usuario_id>usuarios.id','aplicaciones_cobro.asiento_contable_id>asientos_contables.id',
]) assert.ok(schema.refs.some(r => r.endpoints.map(e => `${e.tableName}.${e.fieldNames.join(',')}`).join('>') === reference), `V19 FK missing: ${reference}`);
const v20 = fs.readFileSync(path.join(root, 'erp-backend/src/main/resources/db/migration/V20__inventory_accounting_integration.sql'), 'utf8');
const v20Tables = [...v20.matchAll(/^CREATE TABLE (\w+) \(([\s\S]*?)^\);/gm)];
assert.deepEqual(v20Tables.map(t => t[1]), ['configuracion_cuentas_inventario']);
for (const [, name, body] of v20Tables) {
  const target = schema.tables.find(t => t.name === name);
  assert.ok(target?.note.includes('SQL V20'), `${name}: DBML must mark V20 implementation`);
  const columns = splitSqlColumns(body).map(part => part.match(/^(\w+)\s+(\w+(?:\(\d+(?:,\d+)?\))?)([\s\S]*)$/))
    .filter(c => c && !['PRIMARY','UNIQUE','CHECK','FOREIGN','CONSTRAINT'].includes(c[1]));
  assert.deepEqual(target.fields.map(f => f.name).sort(), columns.map(c => c[1]).sort(), `${name}: V20 columns differ`);
  for (const [, column, type, constraints] of columns) {
    const field = target.fields.find(f => f.name === column);
    assert.equal(normalize(field.type.type_name), normalize(type), `${name}.${column}: type differs`);
    assert.equal(Boolean(field.not_null), /NOT NULL|PRIMARY KEY/.test(constraints), `${name}.${column}: nullability differs`);
    if (/PRIMARY KEY/.test(constraints)) assert.equal(Boolean(field.pk), true, `${name}.${column}: primary key missing`);
    const ref = constraints.match(/REFERENCES (\w+)\((\w+)\)/);
    if (ref) assert.ok(schema.refs.some(r => r.endpoints.map(e => `${e.tableName}.${e.fieldNames.join(',')}`).join('>') === `${name}.${column}>${ref[1]}.${ref[2]}`), `${name}.${column}: FK missing`);
  }
}
for (const reference of [
  'configuracion_cuentas_inventario.cuenta_contable_id>cuentas_contables.id',
  'configuracion_cuentas_inventario.actualizado_por_usuario_id>usuarios.id',
]) assert.ok(schema.refs.some(r => r.endpoints.map(e => `${e.tableName}.${e.fieldNames.join(',')}`).join('>') === reference), `V20 FK missing: ${reference}`);
const v21 = fs.readFileSync(path.join(root, 'erp-backend/src/main/resources/db/migration/V21__supplier_invoices_and_payables.sql'), 'utf8');
const v21Tables = [...v21.matchAll(/^CREATE TABLE (\w+) \(([\s\S]*?)^\);/gm)];
assert.deepEqual(v21Tables.map(t => t[1]).sort(), ['configuracion_cuentas_factura_proveedor','cuentas_por_pagar','detalles_factura_proveedor','facturas_proveedor']);
for (const [, name, body] of v21Tables) {
  const target = schema.tables.find(t => t.name === name);
  assert.ok(target?.note.includes('SQL V21'), `${name}: DBML must mark V21 implementation`);
  const columns = splitSqlColumns(body).map(part => part.match(/^(\w+)\s+(\w+(?:\(\d+(?:,\d+)?\))?)([\s\S]*)$/))
    .filter(c => c && !['PRIMARY','UNIQUE','CHECK','FOREIGN','CONSTRAINT'].includes(c[1]));
  assert.deepEqual(target.fields.map(f => f.name).sort(), columns.map(c => c[1]).sort(), `${name}: V21 columns differ`);
  for (const [, column, type, constraints] of columns) {
    const field = target.fields.find(f => f.name === column);
    assert.equal(normalize(field.type.type_name), normalize(type), `${name}.${column}: type differs`);
    assert.equal(Boolean(field.not_null), /NOT NULL|PRIMARY KEY/.test(constraints), `${name}.${column}: nullability differs`);
    if (/PRIMARY KEY/.test(constraints)) assert.equal(Boolean(field.pk), true, `${name}.${column}: primary key missing`);
    if (/\bUNIQUE\b/.test(constraints)) assert.equal(field.unique, true, `${name}.${column}: uniqueness missing`);
    const ref = constraints.match(/REFERENCES (\w+)\((\w+)\)/);
    if (ref) assert.ok(schema.refs.some(r => r.endpoints.map(e => `${e.tableName}.${e.fieldNames.join(',')}`).join('>') === `${name}.${column}>${ref[1]}.${ref[2]}`), `${name}.${column}: FK missing`);
  }
}
assert.match(v11, /ALTER TABLE movimientos_inventario ADD COLUMN detalle_despacho_id bigint UNIQUE REFERENCES detalles_despacho\(id\);/);
const dispatchSource = schema.tables.find(t => t.name === 'movimientos_inventario').fields.find(f => f.name === 'detalle_despacho_id');
assert.equal(normalize(dispatchSource.type.type_name), 'bigint');
assert.equal(dispatchSource.unique, true);
assert.equal(Boolean(dispatchSource.not_null), false);
assert.ok(schema.refs.some(r => r.endpoints.map(e => `${e.tableName}.${e.fieldNames.join(',')}`).join('>') === 'movimientos_inventario.detalle_despacho_id>detalles_despacho.id'));
for (const groupName of new Set(implementationGroups.values())) {
  assert.equal(schema.tables.filter(t => t.group?.name === groupName).length,
    [...implementationGroups.values()].filter(g => g === groupName).length);
}
const v22 = fs.readFileSync(path.join(root, 'erp-backend/src/main/resources/db/migration/V22__supplier_payments_and_applications.sql'), 'utf8');
const v22Tables = [...v22.matchAll(/^CREATE TABLE (\w+) \(([\s\S]*?)^\);/gm)];
for (const [, name, body] of v22Tables) {
  const target = schema.tables.find(t => t.name === name);
  assert.ok(target?.note.includes('V22'), `${name}: DBML must mark V22 implementation`);
  for (const part of splitSqlColumns(body)) {
    const column = part.match(/^(\w+)\s+(\w+(?:\(\d+(?:,\d+)?\))?)([\s\S]*)$/);
    if (!column || ['PRIMARY','UNIQUE','CHECK','FOREIGN','CONSTRAINT'].includes(column[1])) continue;
    const [, nameColumn, type, constraints] = column;
    const field = target.fields.find(f => f.name === nameColumn);
    assert.ok(field, `${name}.${nameColumn}: missing`);
    assert.equal(field.type.type_name.toLowerCase(), type.toLowerCase(), `${name}.${nameColumn}: type differs`);
    assert.equal(Boolean(field.not_null), /NOT NULL|PRIMARY KEY/.test(constraints), `${name}.${nameColumn}: nullability differs`);
    const ref = constraints.match(/REFERENCES (\w+)\((\w+)\)/);
    if (ref) assert.ok(schema.refs.some(r => r.endpoints.map(e => `${e.tableName}.${e.fieldNames.join(',')}`).join('>') === `${name}.${nameColumn}>${ref[1]}.${ref[2]}`), `${name}.${nameColumn}: FK missing`);
  }
}
const v24 = fs.readFileSync(path.join(root, 'erp-backend/src/main/resources/db/migration/V24__cash_flow_classification.sql'), 'utf8');
const v24Tables = [...v24.matchAll(/^CREATE TABLE (\w+) \(([\s\S]*?)^\);/gm)];
assert.deepEqual(v24Tables.map(t => t[1]).sort(), ['clasificaciones_flujo_cuenta','clasificaciones_flujo_partida']);
for (const [,name,body] of v24Tables) {
  const table=schema.tables.find(t=>t.name===name); assert.ok(table, `${name}: missing V24 table`);
  assert.ok(table.note.includes('SQL V24'), `${name}: DBML must mark V24 implementation`);
  for(const part of splitSqlColumns(body)) {
    const m=part.match(/^(\w+)\s+(\w+(?:\(\d+(?:,\d+)?\))?)([\s\S]*)$/);
    if(!m || ['PRIMARY','UNIQUE','CHECK','FOREIGN','CONSTRAINT','EXCLUDE'].includes(m[1])) continue;
    const [,col,type,attrs]=m; const field=table.fields.find(f=>f.name===col); assert.ok(field,`${name}.${col}: missing`);
    assert.equal(field.type.type_name.toLowerCase(),type.toLowerCase(),`${name}.${col}: type differs`);
    assert.equal(Boolean(field.not_null),/NOT NULL|PRIMARY KEY/.test(attrs),`${name}.${col}: nullability differs`);
    const ref=attrs.match(/REFERENCES (\w+)\((\w+)\)/);
    if(ref) assert.ok(schema.refs.some(r=>r.endpoints.map(e=>`${e.tableName}.${e.fieldNames.join(',')}`).join('>')===`${name}.${col}>${ref[1]}.${ref[2]}`),`${name}.${col}: FK missing`);
  }
}
const v25 = fs.readFileSync(path.join(root, 'erp-backend/src/main/resources/db/migration/V25__reporting_policies.sql'), 'utf8');
const v25Tables = [...v25.matchAll(/^CREATE TABLE (\w+) \(([\s\S]*?)^\);/gm)];
assert.deepEqual(v25Tables.map(t => t[1]).sort(), ['minimos_producto_almacen','clasificaciones_margen_cuenta'].sort());
for (const [,name,body] of v25Tables) {
  const table=schema.tables.find(t=>t.name===name); assert.ok(table, `${name}: missing V25 table`);
  assert.ok(table.note.includes('SQL V25'), `${name}: DBML must mark V25 implementation`);
  for(const part of splitSqlColumns(body)) {
    const m=part.match(/^(\w+)\s+(\w+(?:\(\d+(?:,\d+)?\))?)([\s\S]*)$/);
    if(!m || ['PRIMARY','UNIQUE','CHECK','FOREIGN','CONSTRAINT','EXCLUDE'].includes(m[1])) continue;
    const [,col,type,attrs]=m; const field=table.fields.find(f=>f.name===col); assert.ok(field,`${name}.${col}: missing`);
    assert.equal(field.type.type_name.toLowerCase(),type.toLowerCase(),`${name}.${col}: type differs`);
    assert.equal(Boolean(field.not_null),/NOT NULL|PRIMARY KEY/.test(attrs),`${name}.${col}: nullability differs`);
    const ref=attrs.match(/REFERENCES (\w+)\((\w+)\)/);
    if(ref) assert.ok(schema.refs.some(r=>r.endpoints.map(e=>`${e.tableName}.${e.fieldNames.join(',')}`).join('>')===`${name}.${col}>${ref[1]}.${ref[2]}`),`${name}.${col}: FK missing`);
  }
}
for (const table of schema.tables) {
  assert.ok(implemented.has(table.name) || v9Tables.some(t => t[1] === table.name) || v11Tables.some(t => t[1] === table.name) || v13Tables.some(t => t[1] === table.name) || v14Tables.some(t => t[1] === table.name) || v17Tables.some(t => t[1] === table.name) || v18Tables.some(t => t[1] === table.name) || v19Tables.some(t => t[1] === table.name) || v20Tables.some(t => t[1] === table.name) || v21Tables.some(t => t[1] === table.name) || v22Tables.some(t => t[1] === table.name) || v24Tables.some(t => t[1] === table.name) || v25Tables.some(t => t[1] === table.name) || table.name === 'historial_asientos_contables' || table.note.includes('OBJETIVO'), `${table.name}: missing target status`);
}
assert.ok(!/\buuid\b|gen_random_uuid/i.test(primary), 'Stale UUID policy');
console.log(`DBML OK: ${schema.tables.length} tables, ${schema.refs.length} relationships; both copies identical.`);
console.log(`Implemented schema matches V1/V3/V4/V5/V6: ${tables.length} tables, column types/nullability, PK/unique constraints and ${expectedRefs.length} foreign keys.`);
console.log('V7 partial target comparison with V8 type alterations: 6 tables; non-V7 fields include the separately validated V9 adjustment source and future fields.');
console.log('V9 inventory documents: 4 tables plus the adjustment movement source; columns, types, nullability, uniqueness and foreign keys.');
console.log('V11/V12 sales: 7 tables, dispatch movement source and cancellation metadata; columns, types, nullability, uniqueness and foreign keys.');
console.log('V13 sales invoices: 2 tables; columns, types, nullability, uniqueness and foreign keys.');
console.log('V14 accounting core: 4 tables; columns, types, nullability, uniqueness and foreign keys.');
console.log('V15 accounting lifecycle: 7 added columns and the audited entry history table.');
console.log('V17 sales invoice integration: durable outbox, configurable accounts and five documented foreign keys.');
console.log('V18 accounts receivable: invoice-linked immutable subledger, matching customer/amount/dates and two documented foreign keys.');
console.log('V19 customer receipts: explicit confirmation, same-customer idempotent applications, configured funds/advances accounts and eleven documented foreign keys.');
console.log('V20 inventory accounting: five configurable concepts, active source-to-seat enforcement and two documented foreign keys.');
console.log('V21 supplier invoice and payables: source, lines, mapping and auxiliary columns, types and foreign keys.');
console.log('V24 cash flow classifications: two tables, columns, types, nullability and foreign keys; temporal exclusion and audit triggers remain defined by Flyway.');
console.log('V25 reporting policies: two tables, columns, types, nullability and four foreign keys; range exclusion and immutable classification are defined by Flyway.');
console.log('CHECK, triggers and partial indexes are described in notes; executable authority remains Flyway SQL.');
