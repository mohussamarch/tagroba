// «شخصيتك» (r041) — prototype vs Compose fidelity helper. Node, no packages. Every path is an argument.
//
//   node scripts/character-fidelity.cjs proto-svg <CharacterArt.dc.html> <fidelity-set.json> <outDir>
//        run the prototype's own render() on every look of the set -> <outDir>/<id>.svg
//   node scripts/character-fidelity.cjs shots <sheetDir> <proto|kotlin> <outDir> [idPrefix]
//        one PNG per look with Edge headless (200x200 head/bust, 200x440 full). <sheetDir> = a copy of native-app/golden/character
//        (for "kotlin" it also needs kotlin-svgs.js, written by CharacterSheetTest when CHARACTER_SVG_DIR is set)
//   node scripts/character-fidelity.cjs cmp-svg <protoSvgDir> <kotlinSvgDir> <report.txt>
//        SVG text comparison: attribute order canonicalised, scale(a) = scale(a a), numbers rounded to 0.1
//
// Prototype: git show claude/design-system-v0-4-90a69a:design-source/prototype/canvas/project/CharacterArt.dc.html > <tmp>/CharacterArt.dc.html
// Then the jvm side (from native-app/, log to a file):
//   CHARACTER_PROTO_SVG_DIR=<protoSvgDir>   CharacterSheetTest compares the Kotlin scene text with the prototype SVGs
//   CHARACTER_PNG_DIR=<dir>/compose         CharacterComposePngTest renders CharacterArt itself to PNG (+ contact-sheet.png)
//   CHARACTER_DIFF_DIR=<dir>                CharacterPixelDiffTest compares <dir>/proto vs <dir>/compose (diff-report.txt, pairs/, sheets/)
//   ./gradlew :ui:jvmTest --tests "*Character*" > build-char.log 2>&1
// Edge needs its own --user-data-dir, or it exits silently when an Edge window is already open.
const fs = require('fs'), path = require('path'), cp = require('child_process');
const [, , cmd, a, b, c0, d] = process.argv;
const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';

function edgeShot(url, w, h, out, profile) {
  cp.execFileSync(EDGE, ['--headless=new', '--disable-gpu', '--no-first-run', '--user-data-dir=' + profile, '--hide-scrollbars',
    '--force-device-scale-factor=1', '--default-background-color=00000000', '--window-size=' + w + ',' + h, '--screenshot=' + out, url],
  { stdio: 'ignore', timeout: 60000 });
}

const canon = (s) => s.replace(/scale\(([-\d.e]+)\)/g, 'scale($1 $1)').replace(/<([a-zA-Z]+)((?:\s+[\w:-]+="[^"]*")*)\s*(\/?)>/g, (m, tag, attrs, slash) => {
  const list = [...attrs.matchAll(/([\w:-]+)="([^"]*)"/g)].map((x) => x[1] + '="' + x[2] + '"').sort();
  return '<' + tag + (list.length ? ' ' + list.join(' ') : '') + slash + '>';
});
const norm = (s) => canon(s).replace(/-?\d+\.?\d*(e-?\d+)?/g, (x) => { const v = Math.round(parseFloat(x) * 10) / 10; return (Object.is(v, -0) ? 0 : v).toString(); });

if (cmd === 'proto-svg') {
  const html = fs.readFileSync(a, 'utf8');
  const js = html.match(/data-dc-script[^>]*>([\s\S]*?)<\/script>/)[1].split(/\nclass Component extends DCLogic/)[0];
  const api = new Function(js + '\nreturn { render };')();
  const set = JSON.parse(fs.readFileSync(b, 'utf8'));
  fs.mkdirSync(c0, { recursive: true });
  set.forEach((it) => fs.writeFileSync(path.join(c0, it.id + '.svg'), api.render(it.look, it.mode)));
  console.log('proto svgs', set.length);
} else if (cmd === 'shots') {
  const out = path.resolve(c0);
  const set = JSON.parse(fs.readFileSync(path.join(a, 'fidelity-set.json'), 'utf8'));
  fs.mkdirSync(out, { recursive: true });
  const prof = path.join(path.dirname(out), 'edge-profile-' + b);
  let n = 0;
  for (const it of set) {
    if (d && !it.id.startsWith(d)) continue;
    const url = 'file:///' + path.resolve(a, 'sheet.html').replace(/\\/g, '/') + '?src=' + b + '&only=' + encodeURIComponent(it.id);
    edgeShot(url, 200, it.mode === 'full' ? 440 : 200, path.join(out, it.id + '.png'), prof); n++;
  }
  console.log('shots', n);
} else if (cmd === 'cmp-svg') {
  const lines = []; let same = 0, diff = 0;
  for (const f of fs.readdirSync(a).filter((x) => x.endsWith('.svg')).sort()) {
    const p = norm(fs.readFileSync(path.join(a, f), 'utf8')), kf = path.join(b, f);
    if (!fs.existsSync(kf)) { lines.push(f + ': MISSING in kotlin'); diff++; continue; }
    const k = norm(fs.readFileSync(kf, 'utf8'));
    if (p === k) { same++; continue; }
    diff++;
    let i = 0; while (i < p.length && p[i] === k[i]) i++;
    lines.push(f + ': first difference at ' + i + '\n  proto : ' + p.slice(Math.max(0, i - 60), i + 80) + '\n  kotlin: ' + k.slice(Math.max(0, i - 60), i + 80));
  }
  fs.writeFileSync(c0, 'identical ' + same + ' / differ ' + diff + '\n' + lines.join('\n') + '\n');
  console.log('identical', same, 'differ', diff);
} else {
  console.log('usage: see the header of this file');
}
