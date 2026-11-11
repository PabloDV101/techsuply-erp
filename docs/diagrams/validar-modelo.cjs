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
  assert.deepEqual(target.fields.map(f => f.name).sort(), columns.map(c => c[1]).sort(), `${name}: V11 columns differ`);
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
for (const table of schema.tables) {
  assert.ok(implemented.has(table.name) || v9Tables.some(t => t[1] === table.name) || v11Tables.some(t => t[1] === table.name) || table.note.includes('OBJETIVO'), `${table.name}: missing target status`);
}
assert.ok(!/\buuid\b|gen_random_uuid/i.test(primary), 'Stale UUID policy');
console.log(`DBML OK: ${schema.tables.length} tables, ${schema.refs.length} relationships; both copies identical.`);
console.log(`Implemented schema matches V1/V3/V4/V5/V6: ${tables.length} tables, column types/nullability, PK/unique constraints and ${expectedRefs.length} foreign keys.`);
console.log('V7 partial target comparison with V8 type alterations: 6 tables; non-V7 fields include the separately validated V9 adjustment source and future fields.');
console.log('V9 inventory documents: 4 tables plus the adjustment movement source; columns, types, nullability, uniqueness and foreign keys.');
console.log('V11 sales: 7 tables and dispatch movement source; columns, types, nullability, uniqueness and foreign keys.');
console.log('CHECK, triggers and partial indexes are described in notes; executable authority remains Flyway SQL.');
