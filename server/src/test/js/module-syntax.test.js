import './dom-shim.js'; // Sets up globalThis.document/window before module imports
import { test } from 'node:test';
import assert from 'node:assert';
import { readdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const modulesDir = join(__dirname, '..', '..', 'main', 'resources', 'static', 'modules');

const files = readdirSync(modulesDir).filter(f => f.endsWith('.js'));

for (const file of files) {
  test(`no syntax errors: ${file}`, async () => {
    try {
      await import(join(modulesDir, file));
    } catch (e) {
      // SyntaxError (duplicate declarations, bad syntax) is a real bug
      if (e instanceof SyntaxError) {
        assert.fail(`Syntax error in ${file}: ${e.message}`);
      }
      // Other errors (fetch, requestIdleCallback, etc.) are expected in Node
    }
  });
}
