// فحص المفاتيح قبل أي رفع (CLAUDE.md «الأمان» بند 6 — قرار المالك 2026-10-10 · SECRETS-5 في PLAN-0.6 §1).
// بيدوّر بين نقطتين في Git على:
//   ١. أي ملف **اتضاف** اسمه ممنوع: google-services.json · GoogleService-Info.plist · .env* (غير .env.example) · *.jks · *.keystore ·
//      *.p12 · *.pfx · *.zip · *.apk · *.aab — الملفات دي ممكن يبقى جواها مفاتيح والفحص النصي مش شايفها.
//   ٢. أي ملف **ثنائي** (binary) اتضاف ومش صورة ولا خط — Git بيقول عليه «Binary files differ» والفحص النصي ما بيشوفش جواه.
//   ٣. أي سطر مضاف شكله مفتاح أو توكن.
// ما بيطبعش القيمة نفسها — بس الملف والسطر ونوع المطابقة.
// التشغيل: node scripts/secretscan.mjs <من> <لحد>   (مثلًا: origin/claude/masroufy-kotlin-setup-c02bd8 HEAD)
// الخروج 1 لو فيه مطابقة.
import { execFileSync } from 'child_process'

const [from = 'HEAD~1', to = 'HEAD'] = process.argv.slice(2)
const git = (...args) => execFileSync('git', args, { maxBuffer: 1 << 28 }).toString('utf8')

const PATTERNS = [
  ['مفتاح جوجل', /AIza[0-9A-Za-z_\-]{35}/],
  ['مفتاح سري (sk-)', /\bsk-[A-Za-z0-9_\-]{20,}/],
  ['توكن Bearer', /Bearer\s+[A-Za-z0-9._\-]{20,}/],
  ['مفتاح خاص', /-----BEGIN [A-Z ]*PRIVATE KEY-----|"private_key"\s*:/],
  ['توكن جيت هب', /\bgh[pousr]_[A-Za-z0-9]{30,}/],
  ['مفتاح AWS', /\bAKIA[0-9A-Z]{16}\b/],
  ['كلمة سر مكتوبة', /\b(password|passwd|secret|api[_-]?key)\s*[:=]\s*["']([^"'\s]{8,})["']/i],
]
// ملفات مسموحة، وكل واحد معاه السبب:
// - السكربت ده نفسه: بيشرح الأنماط.
// - FirebaseSetup.kt: قيم وهمية لمحاكي فايربيز (مشروع demo-*، مش مفتاح حقيقي — المحاكي بيطلب أي قيمة).
//   (مسموح فيه نمط «كلمة سر مكتوبة» بس — أي مفتاح جوجل حقيقي فيه لسه بيتمسك)
const ALLOW = [[/^scripts\/secretscan\.mjs$/, null], [/androidApp\/src\/main\/kotlin\/app\/masroufy\/android\/FirebaseSetup\.kt$/, 'كلمة سر مكتوبة']]
const allowed = (f, name) => ALLOW.some(([re, only]) => re.test(f) && (only === null || only === name))
// قيم وهمية معروفة للمحاكيات والاختبارات (مشاريع demo-* — المحاكي بيقبل أي قيمة). لنمط «كلمة سر مكتوبة» بس.
const KNOWN_FAKE = new Set(['fake-test-key', 'emulator-only'])

// ١. أسامي ممنوعة لما تتضاف
const FORBIDDEN_NAMES = [
  [/(^|\/)google-services\.json$/i, 'إعدادات فايربيز (google-services.json)'],
  [/(^|\/)GoogleService-Info\.plist$/i, 'إعدادات فايربيز للآيفون'],
  [/(^|\/)\.env(\..*)?$/i, 'ملف .env'],
  [/\.(jks|keystore|p12|pfx)$/i, 'مفتاح توقيع أو شهادة'],
  [/\.(zip|apk|aab)$/i, 'ملف مضغوط أو برنامج'],
]
const NAME_OK = [/(^|\/)\.env\.example$/i]
// ٢. الثنائي المسموح: صور وخطوط، وملفات معروفة بسببها
const BINARY_OK_EXT = /\.(png|jpe?g|gif|webp|svg|ico|bmp|ttf|otf|woff2?)$/i
const BINARY_OK_FILES = [
  [/(^|\/)gradle\/wrapper\/gradle-wrapper\.jar$/, 'مشغّل Gradle الرسمي'],
  [/^tests\/acceptance\/inspectFile\.test\.ts$/, 'اختبار فيه بايتات NUL مقصودة (كشف الملف الثنائي)'],
]

let hits = 0
const warn = (msg) => { console.log(`⚠️ ${msg}`); hits++ }

// الملفات الجديدة (وأسامي الملفات بعد إعادة التسمية أو النسخ)
const added = git('diff', '--name-status', '-M', '-C', '--no-color', from, to).split('\n').filter(Boolean).flatMap((row) => {
  const [status, ...paths] = row.split('\t')
  return /^[ACR]/.test(status) ? [paths[paths.length - 1]] : []
})
for (const f of added) {
  if (NAME_OK.some((re) => re.test(f))) continue
  const bad = FORBIDDEN_NAMES.find(([re]) => re.test(f))
  if (bad) warn(`${f} — ملف ممنوع: ${bad[1]}`)
}
// الثنائي: `--numstat` بيكتب «-» مكان عدد السطور
const addedSet = new Set(added)
for (const row of git('diff', '--numstat', '-M', '-C', '--no-color', from, to).split('\n')) {
  const m = row.match(/^-\t-\t(.+)$/)
  if (!m) continue
  const f = m[1].includes(' => ') ? m[1].replace(/^(.*)\{.* => (.*)\}(.*)$/, '$1$2$3').replace(/^.* => /, '') : m[1]
  if (!addedSet.has(f) || BINARY_OK_EXT.test(f) || BINARY_OK_FILES.some(([re]) => re.test(f))) continue
  if (FORBIDDEN_NAMES.some(([re]) => re.test(f))) continue // اتقال فوق
  warn(`${f} — ملف ثنائي جديد مش صورة ولا خط (الفحص النصي مش شايف جواه)`)
}

// ٣. السطور المضافة
const diff = git('diff', from, to, '-U0', '--no-color')
let file = '', line = 0
for (const raw of diff.split('\n')) {
  if (raw.startsWith('+++ ')) { file = raw.replace(/^\+\+\+ (b\/)?/, ''); continue }
  const h = raw.match(/^@@ -\d+(?:,\d+)? \+(\d+)/)
  if (h) { line = Number(h[1]); continue }
  if (!raw.startsWith('+')) continue
  for (const [name, re] of PATTERNS) {
    const m = raw.match(re)
    if (!m || allowed(file, name)) continue
    if (name === 'كلمة سر مكتوبة' && KNOWN_FAKE.has(m[2])) continue
    warn(`${file}:${line} — ${name}`)
  }
  line++
}
console.log(hits ? `فحص المفاتيح: ${hits} مطابقة — راجعها قبل الرفع` : 'فحص المفاتيح: مفيش حاجة شكلها مفتاح ✓')
process.exit(hits ? 1 : 0)
