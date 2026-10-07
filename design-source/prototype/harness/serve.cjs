// معاينة محلية للوحة كلود ديزاين — من غير مكتبات.
// بتقدّم ملفات اللوحة (design-source/prototype/canvas/project) كأنها اللوحة نفسها:
//   /Main.dc.html … الشاشات · /support.js ← محرّك اللوحة (DC_RUNTIME = مساره على الجهاز، مش في المستودع)
//   /ds/masroufy/… ← ملفات نظام التصميم (design-source/prototype/ds/project)
// التشغيل: DC_RUNTIME=<path to dc-runtime.js> node design-source/prototype/harness/serve.cjs  (المنفذ 5179)
const http = require('http');
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const CANVAS = path.join(ROOT, 'canvas', 'project');
const DS = path.join(ROOT, 'ds', 'project');
const HARNESS = __dirname;
const PORT = Number(process.env.PORT || 5179);
const TYPES = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.json': 'application/json; charset=utf-8', '.svg': 'image/svg+xml', '.png': 'image/png' };

function resolve(urlPath) {
  const p = decodeURIComponent(urlPath.split('?')[0]);
  if (p === '/support.js') return process.env.DC_RUNTIME || path.join(HARNESS, '.runtime', 'dc-runtime.js');
  if (p.startsWith('/ds/masroufy/')) return path.join(DS, p.slice('/ds/masroufy/'.length));
  if (p.startsWith('/harness/')) return path.join(HARNESS, p.slice('/harness/'.length));
  if (p === '/' || p === '') return path.join(HARNESS, 'index.html');
  return path.join(CANVAS, p.slice(1));
}

http.createServer((req, res) => {
  const file = resolve(req.url);
  const inside = file && [CANVAS, DS, HARNESS].some((d) => path.resolve(file).startsWith(d)) || file === process.env.DC_RUNTIME;
  if (!file || !inside || !fs.existsSync(file) || fs.statSync(file).isDirectory()) {
    res.writeHead(404, { 'content-type': 'text/plain; charset=utf-8' });
    res.end('not found: ' + req.url);
    return;
  }
  res.writeHead(200, { 'content-type': TYPES[path.extname(file)] || 'application/octet-stream', 'cache-control': 'no-store' });
  fs.createReadStream(file).pipe(res);
}).listen(PORT, () => console.log('prototype preview on http://localhost:' + PORT));
