#!/usr/bin/env node
/**
 * Packages the generated neomacros.d.ts into an npm-publishable
 * @neomacros/types package.
 *
 * Usage: node scripts/package-types.mjs <dts-source> <output-dir> <version>
 */
import { cpSync, mkdirSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const [dtsSource, outputDir, version] = process.argv.slice(2);
if (!dtsSource || !outputDir || !version) {
  console.error('Usage: node scripts/package-types.mjs <dts-source> <output-dir> <version>');
  process.exit(1);
}

mkdirSync(outputDir, { recursive: true });
cpSync(dtsSource, resolve(outputDir, 'neomacros.d.ts'));
writeFileSync(resolve(outputDir, 'package.json'), JSON.stringify({
  name: '@neomacros/types',
  version,
  description: 'TypeScript definitions for NeoMacros scripting API',
  types: './neomacros.d.ts',
  license: 'MIT',
}, null, 2) + '
');
writeFileSync(resolve(outputDir, 'README.md'), '# @neomacros/types

TypeScript definitions for the NeoMacros scripting API.
');

console.log(`@neomacros/types v${version} packaged at ${outputDir}`);
