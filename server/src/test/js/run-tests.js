import { execSync } from 'node:child_process';
import { readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const dir = dirname(fileURLToPath(import.meta.url));
const files = readdirSync(dir)
  .filter(f => f.endsWith('.test.js'))
  .map(f => join(dir, f));

if (files.length === 0) {
  console.log('No test files found.');
  process.exit(0);
}

execSync(`node --test ${files.join(' ')}`, { stdio: ['ignore', 'pipe', 'inherit'] });