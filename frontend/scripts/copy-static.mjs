import { cp, mkdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// Vite emits the React dashboard to dist/live. Copy the original static pages
// into dist/ afterwards so the whole frontend is one deployable folder.
const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const source = path.join(frontendRoot, 'public');
const destination = path.join(frontendRoot, 'dist');

await mkdir(destination, { recursive: true });
await cp(source, destination, { recursive: true, force: true });
console.log('Static pages copied from public/ into dist/');
