// لوجوهات البنوك جوه البرنامج (OVERRIDES §76 · §81): من research/banks/logos (١١٢ ملف بأشكال مختلفة) ⇒ صورة WebP صغيرة واحدة لكل بنك
// في drawables أندرويد (`ui/src/androidMain/res/drawable-nodpi/bank_*.webp`) + خريطة `scripts/bank-logos.json` (مين اتاخد من أنهي ملف).
// اللوجو **زي ما هو** (§76: من غير تعديل في شكله ولا ألوانه) — بس بيتصغّر عشان حجم البرنامج: أقصاه 192×144 بكسل، والصور النقطية ما بتتكبرش.
// التحويل بمتصفح Chrome المثبت على الجهاز من غير شبكة (headless --dump-dom: الصفحة بترسم كل صورة على canvas وتطلّعها WebP).
// التشغيل من جذر المستودع: node scripts/bank-logos.mjs   ⇒ وبعده: node scripts/bank-catalog.mjs (بيقرا الخريطة).
import fs from 'fs'
import path from 'path'
import os from 'os'
import crypto from 'crypto'
import { execFileSync } from 'child_process'

const LOGOS = path.resolve('research/banks/logos')
const OUT_DIR = path.resolve('native-app/ui/src/androidMain/res/drawable-nodpi')
const MAP = path.resolve('scripts/bank-logos.json')
const MAX_W = 192
const MAX_H = 144
const QUALITY = 0.9
const BROWSERS = [
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  // Edge على ويندوز ما بيطبعش --dump-dom (اتجرب 2026-10-10) — احتياطي بس
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
]
// نفس العلامة التجارية في البلدين (فرع البنك في السعودية = نفس لوجو البنك) — لما البلد التاني مالوش ملف، أو ملفه صغير قوي (أقل من 48 بكسل)
const SAME_BRAND = { 'EG:banque-misr': 'SA:banque-misr', 'EG:qnb-egypt': 'SA:qatar-national-bank', 'SA:national-bank-of-egypt': 'EG:national-bank-of-egypt', 'SA:abu-dhabi-commercial-bank': 'EG:adcb-egypt' }
const MIME = { png: 'image/png', jpg: 'image/jpeg', gif: 'image/gif', webp: 'image/webp', ico: 'image/x-icon', svg: 'image/svg+xml' }

/** SVG من غير width/height ⇒ ناخدهم من viewBox (وإلا المتصفح بيفترض 300×150). */
function sizedSvg(text) {
  const root = text.match(/<svg\b[^>]*>/)
  if (!root) return text
  const tag = root[0]
  if (/\swidth=/.test(tag) && /\sheight=/.test(tag)) return text
  const vb = tag.match(/viewBox=["']\s*[-\d.]+[\s,]+[-\d.]+[\s,]+([\d.]+)[\s,]+([\d.]+)/)
  if (!vb) return text
  const fixed = tag.replace(/<svg\b/, `<svg width="${vb[1]}" height="${vb[2]}"`)
  return text.replace(tag, fixed)
}

function dataUrl(entry) {
  const file = path.join(LOGOS, entry.file)
  if (entry.format === 'svg') return 'data:image/svg+xml;base64,' + Buffer.from(sizedSvg(fs.readFileSync(file, 'utf8'))).toString('base64')
  return `data:${MIME[entry.format]};base64,` + fs.readFileSync(file).toString('base64')
}

const index = JSON.parse(fs.readFileSync(path.join(LOGOS, 'index.json'), 'utf8'))
const imgs = index.map((e, i) => `<img id="i${i}" data-file="${e.file}" data-svg="${e.format === 'svg' ? 1 : 0}" src="${dataUrl(e)}">`).join('\n')
const page = `<!doctype html><meta charset="utf-8"><body>${imgs}<pre id="out"></pre><script>
window.onload = () => {
  const out = {}
  for (const img of document.images) {
    const w = img.naturalWidth, h = img.naturalHeight
    if (!w || !h) { out[img.dataset.file] = { error: 'no size' }; continue }
    const vector = img.dataset.svg === '1'
    const scale = vector ? Math.min(${MAX_W} / w, ${MAX_H} / h) : Math.min(1, ${MAX_W} / w, ${MAX_H} / h)
    const c = document.createElement('canvas')
    c.width = Math.max(1, Math.round(w * scale)); c.height = Math.max(1, Math.round(h * scale))
    const g = c.getContext('2d'); g.imageSmoothingQuality = 'high'
    g.drawImage(img, 0, 0, c.width, c.height)
    // لوجو فاتح (أبيض على شفاف — معمول لخلفية غامقة): الخلفية شفافة وأغلب البكسلات الظاهرة قريبة من الأبيض (اللي خلفيته بيضا مليانة ما بيتحسبش) ⇒ البرنامج يعرضه على خلفية غامقة زي ما اتعمل
    const px = g.getImageData(0, 0, c.width, c.height).data
    let shown = 0, light = 0
    for (let p = 0; p < px.length; p += 4) if (px[p + 3] > 128) { shown++; if (0.2126 * px[p] + 0.7152 * px[p + 1] + 0.0722 * px[p + 2] > 230) light++ }
    out[img.dataset.file] = { w, h, outW: c.width, outH: c.height, onDark: shown > 0 && shown < px.length / 4 * 0.95 && light / shown > 0.45, webp: c.toDataURL('image/webp', ${QUALITY}) }
  }
  document.getElementById('out').textContent = JSON.stringify(out)
  for (const img of [...document.images]) img.remove()
}
</script>`

const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'bank-logos-'))
const pageFile = path.join(tmp, 'page.html')
fs.writeFileSync(pageFile, page)
const browser = BROWSERS.find((b) => fs.existsSync(b))
if (!browser) throw new Error('محتاج Edge أو Chrome على الجهاز')
const dom = execFileSync(browser, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check', `--user-data-dir=${path.join(tmp, 'profile')}`,
  '--virtual-time-budget=20000', '--dump-dom', 'file:///' + pageFile.replace(/\\/g, '/'),
], { maxBuffer: 64 * 1024 * 1024 }).toString('utf8')
const json = dom.match(/<pre id="out">([\s\S]*?)<\/pre>/)
if (!json) throw new Error('المتصفح ما رجّعش نتيجة')
const rendered = JSON.parse(json[1].replace(/&quot;/g, '"').replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>'))

// لكل بنك صورة واحدة: الأيقونة المربعة لو واضحة (96 بكسل فأكتر)، وإلا اللوجو، وإلا أكبر الموجود
const byBank = {}
for (const e of index) {
  const r = rendered[e.file]
  if (!r || r.error) { console.warn('⚠️ ما اترسمش:', e.file, r && r.error); continue }
  ;(byBank[`${e.country}:${e.id}`] ||= []).push({ ...e, ...r })
}
const squareOk = (c) => Math.min(c.w, c.h) >= 96 && c.w / c.h >= 0.75 && c.w / c.h <= 1.34
function pick(cands) {
  const icon = cands.find((c) => c.kind === 'icon' && (c.format === 'svg' || squareOk(c)))
  if (icon) return icon
  const logo = cands.find((c) => c.kind === 'logo')
  return logo || cands.sort((a, b) => b.outW * b.outH - a.outW * a.outH)[0]
}

fs.mkdirSync(OUT_DIR, { recursive: true })
for (const f of fs.readdirSync(OUT_DIR)) if (/^bank_.*\.webp$/.test(f)) fs.unlinkSync(path.join(OUT_DIR, f))
const byHash = {}
const map = {}
let total = 0
for (const key of Object.keys(byBank).sort()) {
  const c = pick(byBank[key])
  const bytes = Buffer.from(c.webp.split(',')[1], 'base64')
  const hash = crypto.createHash('sha1').update(bytes).digest('hex')
  let name = byHash[hash]
  if (!name) {
    name = 'bank_' + key.replace(':', '_').toLowerCase().replace(/[^a-z0-9]+/g, '_')
    fs.writeFileSync(path.join(OUT_DIR, name + '.webp'), bytes)
    byHash[hash] = name
    total += bytes.length
  }
  map[key] = { logo: name.slice(5), from: c.file, source: c.source, size: `${c.outW}x${c.outH}`, ...(c.onDark ? { onDark: true } : {}) }
}
const tiny = (m) => m && Math.max(...m.size.split('x').map(Number)) < 48
for (const [key, brand] of Object.entries(SAME_BRAND)) if ((!map[key] || tiny(map[key])) && map[brand] && !tiny(map[brand])) map[key] = { ...map[brand], sameBrandAs: brand }
const used = new Set(Object.values(map).map((m) => `bank_${m.logo}.webp`))
for (const f of fs.readdirSync(OUT_DIR)) if (/^bank_.*\.webp$/.test(f) && !used.has(f)) fs.unlinkSync(path.join(OUT_DIR, f))
total = [...used].reduce((sum, f) => sum + fs.statSync(path.join(OUT_DIR, f)).size, 0)
fs.writeFileSync(MAP, JSON.stringify(map, null, 1) + '\n')
fs.rmSync(tmp, { recursive: true, force: true })
console.log(`${Object.keys(map).length} بنك · ${used.size} ملف WebP · ${total} بايت`)
