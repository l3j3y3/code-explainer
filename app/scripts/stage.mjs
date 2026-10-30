// 把网页源码（仓库根目录）拷进 app/dist，供 cap sync 打进 APK
// 只拷 4 样东西：index.html / manifest.json / sw.js / icons/
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(here, '..', '..');
const dist = path.resolve(here, '..', 'dist');

fs.rmSync(dist, { recursive: true, force: true });
fs.mkdirSync(dist, { recursive: true });

const files = ['index.html', 'manifest.json', 'sw.js'];
for (const f of files) {
  fs.copyFileSync(path.join(repoRoot, f), path.join(dist, f));
}
fs.cpSync(path.join(repoRoot, 'icons'), path.join(dist, 'icons'), { recursive: true });

const total = fs.readdirSync(dist).map(f => {
  const p = path.join(dist, f);
  return fs.statSync(p).isDirectory()
    ? f + '/ (' + fs.readdirSync(p).length + ' files)'
    : f + ' (' + fs.statSync(p).size + 'B)';
});
console.log('staged into dist/: ' + total.join(', '));
