/** Validate every OpenAPI document without changing project dependencies. */
const fs = require('node:fs');
const path = require('node:path');
const parser = require(process.env.SWAGGER_PARSER_PATH || '@apidevtools/swagger-parser');

(async () => {
  const files = fs.readdirSync(__dirname).filter(name => /^openapi-.*\.yaml$/.test(name)).sort();
  if (files.length === 0) throw new Error('No OpenAPI documents found');
  for (const file of files) {
    const api = await parser.validate(path.join(__dirname, file));
    if (file === 'openapi-sprint-1-maestros-compras.yaml') {
      if (api['x-implementation-status'] !== 'implemented') throw new Error('Sprint contract is not marked implemented');
      for (const [route, item] of Object.entries(api.paths)) {
        if (item['x-implementation-status'] !== 'implemented') throw new Error(`Unimplemented sprint route: ${route}`);
      }
    }
    console.log(`${file}: OK (${Object.keys(api.paths).length} paths)`);
  }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
