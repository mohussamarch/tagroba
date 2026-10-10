// خريطة الكود (CODEMAP.md): سطر لكل ملف Kotlin — أول تعليق فيه + أهم الأسماء اللي بيعرّفها.
// الهدف: أي وكيل/مطوّر يقرا الخريطة ويفتح الملفات اللي محتاجها بس، بدل ما يدوّر في الكود كله (طلب المالك 2026-10-10: توفير الاستهلاك).
// التشغيل من جذر المشروع: node scripts/codemap.mjs  ⇒ بيكتب native-app/CODEMAP.md (من غير ذكاء اصطناعي ولا شبكة).
import fs from 'fs'
import path from 'path'

const ROOT = path.resolve(process.argv[2] || 'native-app')
const OUT = path.join(ROOT, 'CODEMAP.md')
const SKIP = new Set(['build', '.gradle', '.idea', 'node_modules'])
const MAX_NAMES = 8

function walk(dir, out) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    if (SKIP.has(e.name)) continue
    const p = path.join(dir, e.name)
    if (e.isDirectory()) walk(p, out)
    else if (e.name.endsWith('.kt') && !/[\\/]src[\\/][a-zA-Z]*Test[\\/]/.test(p)) out.push(p)
  }
  return out
}

// أول سطر تعليق ذو معنى (KDoc أو //) قبل أول تعريف
function firstNote(src) {
  for (const raw of src.split('\n').slice(0, 60)) {
    const l = raw.trim()
    if (/^(package|import|@file)/.test(l) || l === '') continue
    const m = l.match(/^(?:\/\*\*?|\*|\/\/)\s*(.+?)\s*(?:\*\/)?$/)
    if (m && m[1] && !/^[*\/\s]*$/.test(m[1])) return m[1].replace(/\s+/g, ' ').slice(0, 140)
    if (!l.startsWith('*') && !l.startsWith('/')) break
  }
  return ''
}

// أسماء التعريفات العامة على المستوى الأعلى (بدون private/internal)
function topNames(src) {
  const names = []
  const re = /^(?!\s)(?:(?:public|data|sealed|enum|abstract|open|value|inline|fun|suspend|expect|actual|annotation)\s+)*(class|object|interface|fun|typealias|val)\s+(?:<[^>]*>\s*)?(?:[A-Za-z0-9_.]+\.)?([A-Za-z_][A-Za-z0-9_]*)/gm
  for (const m of src.matchAll(re)) {
    const line = src.slice(src.lastIndexOf('\n', m.index) + 1, m.index + m[0].length)
    if (/\b(private|internal)\b/.test(line)) continue
    const n = m[2]
    if (!names.includes(n)) names.push(n)
  }
  return names
}

const files = walk(ROOT, []).sort()
const byModule = new Map()
for (const f of files) {
  const rel = path.relative(ROOT, f).replace(/\\/g, '/')
  const mod = rel.split('/')[0]
  const src = fs.readFileSync(f, 'utf8')
  const lines = src.split('\n').length
  const names = topNames(src)
  const note = firstNote(src)
  const shown = names.slice(0, MAX_NAMES).join(', ') + (names.length > MAX_NAMES ? ` +${names.length - MAX_NAMES}` : '')
  if (!byModule.has(mod)) byModule.set(mod, [])
  byModule.get(mod).push(`- \`${rel.replace(/^[^/]+\/src\//, '')}\` (${lines}) — ${shown || '—'}${note ? ' · ' + note : ''}`)
}

let md = '# خريطة الكود — بتتولّد لوحدها\n\n'
md += '> **ما تتعدلش باليد.** بتتولّد بـ`node scripts/codemap.mjs` بعد كل دمج. سطر لكل ملف: المسار (عدد السطور) — أهم الأسماء · أول تعليق.\n'
md += '> اقرا الخريطة الأول، وافتح الملفات اللي هتشتغل عليها بس.\n\n'
md += `الملفات: ${files.length} (من غير الاختبارات)\n`
for (const [mod, rows] of byModule) md += `\n## ${mod} (${rows.length})\n\n${rows.join('\n')}\n`
fs.writeFileSync(OUT, md)
console.log(`CODEMAP: ${files.length} files → ${path.relative(process.cwd(), OUT)} (${Math.round(md.length / 1024)} KB)`)
