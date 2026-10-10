// فحص المفاتيح قبل أي رفع (CLAUDE.md «الأمان» بند 6 — قرار المالك 2026-10-10).
// بيدوّر في السطور المضافة بين نقطتين في Git على أي حاجة شكلها مفتاح أو توكن. ما بيطبعش القيمة نفسها — بس الملف والسطر ونوع المطابقة.
// التشغيل: node scripts/secretscan.mjs <من> <لحد>   (مثلًا: origin/claude/masroufy-kotlin-setup-c02bd8 HEAD)
// الخروج 1 لو فيه مطابقة.
import { execSync } from 'child_process'

const [from = 'HEAD~1', to = 'HEAD'] = process.argv.slice(2)
const diff = execSync(`git diff ${from} ${to} -U0 --no-color`, { maxBuffer: 1 << 28 }).toString('utf8')

const PATTERNS = [
  ['مفتاح جوجل', /AIza[0-9A-Za-z_\-]{35}/],
  ['مفتاح سري (sk-)', /\bsk-[A-Za-z0-9_\-]{20,}/],
  ['توكن Bearer', /Bearer\s+[A-Za-z0-9._\-]{20,}/],
  ['مفتاح خاص', /-----BEGIN [A-Z ]*PRIVATE KEY-----|"private_key"\s*:/],
  ['توكن جيت هب', /\bgh[pousr]_[A-Za-z0-9]{30,}/],
  ['مفتاح AWS', /\bAKIA[0-9A-Z]{16}\b/],
  ['كلمة سر مكتوبة', /\b(password|passwd|secret|api[_-]?key)\s*[:=]\s*["'][^"'\s]{8,}["']/i],
]
// ملفات مسموحة، وكل واحد معاه السبب:
// - السكربت ده نفسه: بيشرح الأنماط.
// - FirebaseSetup.kt: قيم وهمية لمحاكي فايربيز (مشروع demo-*، مش مفتاح حقيقي — المحاكي بيطلب أي قيمة).
//   (مسموح فيه نمط «كلمة سر مكتوبة» بس — أي مفتاح جوجل حقيقي فيه لسه بيتمسك)
const ALLOW = [[/^scripts\/secretscan\.mjs$/, null], [/androidApp\/src\/main\/kotlin\/app\/masroufy\/android\/FirebaseSetup\.kt$/, 'كلمة سر مكتوبة']]
const allowed = (f, name) => ALLOW.some(([re, only]) => re.test(f) && (only === null || only === name))

let file = '', line = 0, hits = 0
for (const raw of diff.split('\n')) {
  if (raw.startsWith('+++ ')) { file = raw.replace(/^\+\+\+ (b\/)?/, ''); continue }
  const h = raw.match(/^@@ -\d+(?:,\d+)? \+(\d+)/)
  if (h) { line = Number(h[1]); continue }
  if (!raw.startsWith('+')) continue
  for (const [name, re] of PATTERNS) if (re.test(raw) && !allowed(file, name)) { console.log(`⚠️ ${file}:${line} — ${name}`); hits++ }
  line++
}
console.log(hits ? `فحص المفاتيح: ${hits} مطابقة — راجعها قبل الرفع` : 'فحص المفاتيح: مفيش حاجة شكلها مفتاح ✓')
process.exit(hits ? 1 : 0)
