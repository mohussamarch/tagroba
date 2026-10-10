// دليل البنوك جوه البرنامج (OVERRIDES §81): research/banks/saudi-banks.json و egypt-banks.json + scripts/bank-logos.json (اللوجوهات)
// ⇒ native-app/core/.../BankCatalogSaudi.kt و BankCatalogEgypt.kt (البيانات) + native-app/ui/src/androidMain/.../BankLogos.android.kt (صور أندرويد).
// ما تعدّلش الملفات المتولّدة باليد — عدّل البحث أو القواعد هنا وشغّل تاني. من غير شبكة.
// التشغيل من جذر المستودع: node scripts/bank-catalog.mjs   (ولو اللوجوهات اتغيرت: node scripts/bank-logos.mjs الأول)
import fs from 'fs'
import path from 'path'

const CORE = path.resolve('native-app/core/src/commonMain/kotlin/app/masroufy/core')
const UI_ANDROID = path.resolve('native-app/ui/src/androidMain/kotlin/app/masroufy/ui/components/BankLogos.android.kt')
const logos = JSON.parse(fs.readFileSync('scripts/bank-logos.json', 'utf8'))

const KIND = {
  'local-bank': 'BANK', 'public bank': 'BANK', 'private bank': 'BANK', 'digital-bank': 'DIGITAL_BANK', 'digital bank': 'DIGITAL_BANK',
  'foreign-branch': 'FOREIGN_BANK', 'foreign bank': 'FOREIGN_BANK', 'payment-wallet': 'WALLET', 'mobile wallet': 'WALLET',
  'payment app': 'PAYMENT_APP', remittance: 'REMITTANCE', fintech: 'FINTECH',
}
const WALLETISH = new Set(['WALLET', 'PAYMENT_APP', 'REMITTANCE', 'FINTECH'])
// الحالات اللي بتخدم الأفراد فعلًا (من غير حالة = شغال — البحث كتب الحالة للاستثناءات بس)
const RETAIL_STATUS = new Set([undefined, 'operating', 'retail-sale-announced', 'bank-agent'])
// مش محفظة فلوس: «إنستاباي» قناة تحويل بين البنوك، ورسايلها بتيجي من بنك العميل نفسه (ملاحظة البحث)
const NOT_A_WALLET = new Set(['EG:instapay'])
// الاسم العربي الرسمي فيه حروف إنجليزي ⇒ النطق بالعربي من البحث نفسه (nameArDisplay / nameArShort)
const NAME_AR = { 'SA:d360-bank': 'بنك دي 360', 'EG:hsbc-egypt': 'بنك إتش إس بي سي' }

const EASTERN = '٠١٢٣٤٥٦٧٨٩'
const western = (s) => (s || '').replace(/[٠-٩]/g, (d) => String(EASTERN.indexOf(d)))
const hasArabic = (s) => /[؀-ۿ]/.test(s || '')
const noParens = (s) => s.replace(/\s*\([^)]*\)/g, '').trim()
const noCountry = (s) => s.replace(/\s+[-–]\s+(مصر|السعودية)$/, '').trim()

function nameAr(b, kind) {
  const id = `${b.country}:${b.id}`
  if (NAME_AR[id]) return NAME_AR[id]
  if (WALLETISH.has(kind)) return western(b.nameArDisplay || (hasArabic(b.nameArShort) ? b.nameArShort : noParens(b.nameArOfficial)))
  const official = noCountry(noParens(western(b.nameArOfficial)))
  return official || western(b.nameArDisplay || b.nameArShort)
}

/** الإنجليزي من غير الشرح بين القوسين، إلا لو اختصار البنك («SNB» · «BSF»). */
const nameEn = (s) => s.replace(/\s*\(([^)]*)\)/g, (m, inner) => (/^[A-Za-z&-]{2,6}$/.test(inner.trim()) ? ` (${inner.trim()})` : '')).trim()

const kt = (s) => '"' + s.replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\$/g, '\\$') + '"'
const list = (xs) => (xs.length ? `listOf(${xs.map(kt).join(', ')})` : 'emptyList()')

function entry(b) {
  const kind = KIND[b.type]
  if (!kind) throw new Error(`نوع مش معروف: ${b.type} (${b.id})`)
  const id = `${b.country}:${b.id}`
  const ar = nameAr(b, kind)
  const words = [...new Set([b.nameArOfficial, b.nameArShort, b.nameArDisplay, ...(b.aliases || [])].filter(Boolean).map(western).map(noParens))]
    .filter((w) => w && w !== ar)
  const senders = (b.senderIds || []).filter((s) => s.verified === true).map((s) => s.id)
  const retail = RETAIL_STATUS.has(b.status) && !NOT_A_WALLET.has(id)
  const logo = logos[id]?.logo
  if (/[٠-٩۰-۹]/.test(ar + words.join(''))) throw new Error(`أرقام عربية في ${id}`)
  return `    Bank(${kt(`${b.country.toLowerCase()}-${b.id}`)}, ${kt(b.country)}, ${kt(ar)}, ${kt(nameEn(b.nameEn))}, BankKind.${kind}, ${retail}, ` +
    `${list(senders)}, ${list(words)}, ${logo ? kt(logo) : 'null'}),`
}

function writeCountry(file, name, source, title) {
  const banks = JSON.parse(fs.readFileSync(`research/banks/${source}`, 'utf8'))
  const body = [
    '// ⚠️ ملف متولّد — ما يتعدلش باليد: node scripts/bank-catalog.mjs (من research/banks/' + source + ' + scripts/bank-logos.json).',
    'package app.masroufy.core',
    '',
    `/** ${title} (${banks.length}) — المفتاح · البلد · الاسم بالعربي · بالإنجليزي · النوع · للأفراد؟ · المرسلين المؤكدين · كلمات البحث · اللوجو. */`,
    `internal val ${name}: List<Bank> = listOf(`,
    ...banks.map(entry),
    ')',
    '',
  ]
  fs.writeFileSync(path.join(CORE, file), body.join('\n'))
  return banks.length
}

const sa = writeCountry('BankCatalogSaudi.kt', 'SAUDI_BANKS', 'saudi-banks.json', 'بنوك ومحافظ السعودية')
const eg = writeCountry('BankCatalogEgypt.kt', 'EGYPT_BANKS', 'egypt-banks.json', 'بنوك ومحافظ مصر')

// صور أندرويد: مفتاح اللوجو ⇒ الصورة (مراجع صريحة عشان R8 ما يشيلهاش) + «على غامق» للوجو الأبيض
const seen = new Map()
for (const v of Object.values(logos)) seen.set(v.logo, !!v.onDark)
const rows = [...seen.entries()].sort().map(([k, dark]) => `    ${kt(k)} to BankLogoImage(R.drawable.bank_${k}${dark ? ', onDark = true' : ''}),`)
fs.mkdirSync(path.dirname(UI_ANDROID), { recursive: true })
fs.writeFileSync(UI_ANDROID, [
  '// ⚠️ ملف متولّد — ما يتعدلش باليد: node scripts/bank-catalog.mjs (من scripts/bank-logos.json).',
  'package app.masroufy.ui.components',
  '',
  'import app.masroufy.ui.R',
  '',
  `/** لوجوهات البنوك (${seen.size} صورة WebP في drawable-nodpi — OVERRIDES §76): مفتاح \`Bank.logo\` ⇒ الصورة. [onDark] = لوجو أبيض معمول لخلفية غامقة. */`,
  'internal data class BankLogoImage(val res: Int, val onDark: Boolean = false)',
  '',
  'internal val BANK_LOGO_IMAGES: Map<String, BankLogoImage> = mapOf(',
  ...rows,
  ')',
  '',
].join('\n'))
console.log(`السعودية ${sa} · مصر ${eg} · صور ${seen.size}`)
