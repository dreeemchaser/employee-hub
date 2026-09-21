#!/usr/bin/env node
/**
 * Refreshes src/api/openapi.json from a running employeeapi instance.
 *
 * Usage: node scripts/fetch-openapi-spec.js [url]
 *   url defaults to $REACT_APP_API_URL or http://localhost:8080.
 *
 * This is a plain Node script (no axios, no project dependencies) so it can
 * run before `npm install` finishes and outside the React build pipeline.
 * The fetched spec is committed to git — `npm run spec:types` regenerates
 * api-types.d.ts from the committed snapshot, so type generation and `npm
 * install` both work without a running backend. Re-run this script (then
 * spec:types) whenever the backend's API contract changes.
 */
const http = require('http');
const https = require('https');
const fs = require('fs');
const path = require('path');

const baseUrl = process.argv[2] || process.env.REACT_APP_API_URL || 'http://localhost:8080';
const specUrl = `${baseUrl.replace(/\/$/, '')}/v3/api-docs`;
const outPath = path.join(__dirname, '..', 'src', 'api', 'openapi.json');

const client = specUrl.startsWith('https') ? https : http;

console.log(`Fetching OpenAPI spec from ${specUrl} ...`);

client.get(specUrl, (res) => {
    if (res.statusCode !== 200) {
        console.error(`Failed to fetch spec: HTTP ${res.statusCode}`);
        process.exit(1);
    }
    let body = '';
    res.on('data', (chunk) => { body += chunk; });
    res.on('end', () => {
        try {
            const parsed = JSON.parse(body);
            fs.writeFileSync(outPath, JSON.stringify(parsed, null, 2) + '\n');
            console.log(`Wrote ${outPath}`);
            console.log('Run `npm run spec:types` to regenerate api-types.d.ts.');
        } catch (err) {
            console.error('Response was not valid JSON:', err.message);
            process.exit(1);
        }
    });
}).on('error', (err) => {
    console.error(`Could not reach ${specUrl}: ${err.message}`);
    console.error('Is the backend running? (docker compose up, or mvnw spring-boot:run)');
    process.exit(1);
});
