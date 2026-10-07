// Shared helpers for the mascot tools in studioshare-mascot.
import { createRequire } from 'node:module';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import fs from 'node:fs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
export const TOOLS_DIR = __dirname;
export const PIPELINE_DIR = path.resolve(__dirname, '..');
export const PROJECT_ROOT = path.resolve(PIPELINE_DIR, '..');
export const WORKSPACE_ROOT = path.resolve(PROJECT_ROOT, '../..');

export const MASCOT_SPEC = path.join(PIPELINE_DIR, 'spec/mascot.json');
export const SRC = path.join(PIPELINE_DIR, 'src');
export const DIST = path.join(PIPELINE_DIR, 'dist');
export const DOCS = path.join(PIPELINE_DIR, 'docs');

// Resolve sharp from local node_modules, sibling web repo, or workspace
let resolvedSharp = null;
const candidatePkgPaths = [
  path.join(PROJECT_ROOT, 'package.json'),
  path.join(WORKSPACE_ROOT, 'Projects/studioshare-web/package.json'),
  path.join(WORKSPACE_ROOT, 'package.json')
];

for (const pkgPath of candidatePkgPaths) {
  if (fs.existsSync(pkgPath)) {
    try {
      const req = createRequire(pkgPath);
      resolvedSharp = req('sharp');
      if (resolvedSharp) break;
    } catch (_) {
      // continue checking next path
    }
  }
}

if (!resolvedSharp) {
  try {
    resolvedSharp = (await import('sharp')).default;
  } catch (err) {
    console.error('Warning: sharp not found. Please run `npm install` in studioshare-mascot or studioshare-web.');
  }
}

export const sharp = resolvedSharp;
