// بيانات قياس السرعة (HANDOVER §7 «السرعة») — محاكي Firestore بتاع التطبيق بس (مشروع demo-masroufy-kt، منفذ 8089).
// كله **مخترع**: 3,500 عملية على 21 شهر + محافظ وتصنيفات وتجار وأشخاص وديون واشتراكات، لحساب القياس `perf-user`
// (التطبيق بيدخله من غير كلمة سر: adb shell am start -n app.masroufy.mobile/app.masroufy.android.MainActivity --es perf_uid perf-user).
// التشغيل (والمحاكي شغال): node perf-seed.mjs   ·   «Bearer owner» = صلاحية المحاكي بس، مالهاش معنى على سيرفر حقيقي.
const HOST = process.env.PERF_FIRESTORE ?? '127.0.0.1:8089'
const PROJECT = 'demo-masroufy-kt'
const UID = process.env.PERF_UID ?? 'perf-user'
const COUNT = Number(process.env.PERF_COUNT ?? 3500)
const docs = `projects/${PROJECT}/databases/(default)/documents`
const root = `${docs}/users/${UID}`

let state = 20261010
const rand = () => ((state = (state * 1103515245 + 12345) % 2147483648) / 2147483648)
const pick = (xs) => xs[Math.floor(rand() * xs.length)]
const pad = (n, w = 2) => String(n).padStart(w, '0')
const STAMP = '2026-10-01T10:00:00.000Z'

function value(v) {
  if (v === null || v === undefined) return { nullValue: null }
  if (typeof v === 'boolean') return { booleanValue: v }
  if (typeof v === 'number') return Number.isInteger(v) ? { integerValue: String(v) } : { doubleValue: v }
  if (Array.isArray(v)) return { arrayValue: { values: v.map(value) } }
  if (typeof v === 'object') return { mapValue: { fields: fields(v) } }
  return { stringValue: String(v) }
}
function fields(o) {
  return Object.fromEntries(Object.entries(o).filter(([, v]) => v !== undefined).map(([k, v]) => [k, value(v)]))
}

const writes = []
const put = (path, o) => writes.push({ update: { name: `${root}/${path}`, fields: fields(o) } })

put('profile/main', { displayName: 'مستخدم القياس', salaryMinor: 1500000, payday: 28, gender: 'male', supportsDependents: false, dependentKinds: [], hasCar: true, renter: true, domesticWorker: false, business: false, onboardedAt: STAMP })
put('initialization/references-v1', { state: 'complete', version: 1 })

const wallets = [
  { id: 'w-bank', name: 'بنك القياس', currency: 'SAR', kind: 'bank', openingBalanceMinor: 250000, openingAt: '2025-01-01', accountLast4: '0000' },
  { id: 'w-cash', name: 'كاش', currency: 'SAR', kind: 'cash', openingBalanceMinor: 100000, openingAt: '2025-01-01' },
  { id: 'w-card', name: 'بطاقة القياس', currency: 'SAR', kind: 'bank', openingBalanceMinor: 0, openingAt: '2025-01-01', accountLast4: '1111' },
]
wallets.forEach((w) => put(`wallets/${w.id}`, w))

const catNames = ['أكل', 'مطاعم', 'بقالة', 'مواصلات', 'بنزين', 'سكن', 'فواتير', 'كهرباء', 'جوال', 'صحة', 'تعليم', 'ترفيه', 'ملابس', 'هدايا', 'سفر', 'اشتراكات', 'صيانة', 'أطفال', 'رسوم', 'متفرقات']
const categories = catNames.map((name, i) => ({ id: `c-${pad(i + 1)}`, parentId: i > 0 && i % 4 === 1 ? `c-${pad(i)}` : null, name, iconKey: 'tag', lightColor: '#336699', darkColor: '#88aacc', active: true, order: i + 1 }))
categories.forEach((c) => put(`categories/${c.id}`, c))

const merchants = Array.from({ length: 60 }, (_, i) => ({ id: `m-${pad(i + 1)}`, displayName: `متجر وهمي ${i + 1}`, normalizedName: `PERF SHOP ${i + 1}`, verifiedCategoryId: pick(categories).id, aliases: [] }))
merchants.forEach((m) => put(`merchants/${m.id}`, m))

const people = Array.from({ length: 25 }, (_, i) => ({ id: `p-${pad(i + 1)}`, name: `شخص ${i + 1}`, archived: false }))
people.forEach((p) => put(`people/${p.id}`, p))

const recurring = Array.from({ length: 8 }, (_, i) => ({ id: `r-${i + 1}`, name: `اشتراك ${i + 1}`, merchantKey: `name:PERF SHOP ${i + 1}`, kind: 'subscription', cycleMonths: 1, expectedMinor: 2500 + i * 1000, currency: 'SAR', nextDueAt: `2026-10-${pad(5 + i * 3)}`, active: true, confirmed: true }))
recurring.forEach((r) => put(`recurringItems/${r.id}`, r))

// 21 شهر: 2025-01-01 ⇒ 2026-09-30 — الراتب يوم 28 كل شهر والباقي موزّع
const start = Date.UTC(2025, 0, 1)
const days = 637
const kinds = ['purchase', 'purchase', 'purchase', 'purchase', 'purchase', 'purchase', 'fee', 'internal_transfer', 'support_gift', 'loan_granted', 'debt_collected']
const txs = []
for (let i = 0; i < COUNT; i++) {
  const day = new Date(start + Math.floor((i / COUNT) * days) * 86400000).toISOString().slice(0, 10)
  let kind = pick(kinds)
  if (day.endsWith('-28') && rand() < 0.2) kind = 'salary'
  const incoming = kind === 'salary' || kind === 'debt_collected'
  const m = pick(merchants)
  const wallet = rand() < 0.75 ? 'w-bank' : rand() < 0.5 ? 'w-cash' : 'w-card'
  const amount = kind === 'salary' ? 1500000 : Math.floor(rand() * 40000) + 300
  const t = {
    id: `t-${pad(i, 5)}`, occurredAt: day, datePrecision: 'day', sourceOrder: i % 9, economicKind: kind, economicKindConfirmed: true,
    observedDirection: incoming ? 'in' : 'out', amountMinor: amount, currency: 'SAR', categoryConfirmed: rand() < 0.6, excludedFromBudget: false,
    reviewState: rand() < 0.9 ? 'confirmed' : 'suggested', isCashTagged: wallet === 'w-cash', createdAt: STAMP, updatedAt: STAMP,
    walletId: wallet, categoryId: kind === 'purchase' || kind === 'fee' ? m.verifiedCategoryId : null, rawMerchantName: m.normalizedName, merchantId: m.id,
  }
  if (kind === 'internal_transfer') t.transferToWalletId = wallet === 'w-bank' ? 'w-cash' : 'w-bank'
  txs.push(t)
}
txs.forEach((t) => put(`transactions/${t.id}`, t))

// ديون: من عمليات «سلف» و«تحصيل» لأشخاص
txs.filter((t) => t.economicKind === 'loan_granted').slice(0, 30).forEach((t, i) =>
  put(`obligations/o-${pad(i + 1)}`, { id: `o-${pad(i + 1)}`, personId: people[i % people.length].id, originTransactionId: t.id, kind: 'receivable', originalMinor: t.amountMinor, currency: 'SAR' }),
)

for (let i = 0; i < writes.length; i += 400) {
  const res = await fetch(`http://${HOST}/v1/${docs}:batchWrite`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: 'Bearer owner' },
    body: JSON.stringify({ writes: writes.slice(i, i + 400) }),
  })
  if (!res.ok) throw new Error(`batch ${i}: ${res.status} ${await res.text()}`)
}
console.log(`seeded ${writes.length} documents (${txs.length} transactions) under users/${UID}`)
