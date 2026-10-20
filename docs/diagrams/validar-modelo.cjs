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
for (const groupName of new Set(implementationGroups.values())) {
  assert.equal(schema.tables.filter(t => t.group?.name === groupName).length,
    [...implementationGroups.values()].filter(g => g === groupName).length);
}
for (const table of schema.tables) {
  assert.ok(implemented.has(table.name) || table.note.includes('OBJETIVO'), `${table.name}: missing target status`);
}
assert.ok(!/\buuid\b|gen_random_uuid/i.test(primary), 'Stale UUID policy');
console.log(`DBML OK: ${schema.tables.length} tables, ${schema.refs.length} relationships; both copies identical.`);
console.log(`Implemented schema matches V1/V3: ${tables.length} tables, column types/nullability, PK/unique constraints and ${expectedRefs.length} foreign keys.`);
console.log('CHECK, triggers and partial indexes are described in notes; executable authority remains Flyway SQL.');
