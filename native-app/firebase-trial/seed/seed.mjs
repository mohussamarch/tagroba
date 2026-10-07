// بيانات وهمية لتجربة مكتبة فايربيز (KOTLIN_PLAN §4-أ4) — Firestore Emulator بس.
// 2,000 عملية على 21 شهر (قريب من حجم كشف حقيقي ~1,900)، أسماء تجار وأرقام **مخترعة بالكامل**.
// التشغيل: node seed.mjs (المحاكي شغال على 127.0.0.1:8088). «Bearer owner» = صلاحية المحاكي بس، مالهاش معنى على سيرفر حقيقي.
const HOST = process.env.TRIAL_FIRESTORE ?? '127.0.0.1:8088'
const PROJECT = 'demo-masroufy-trial'
const COUNT = 2000
const base = `http://${HOST}/v1/projects/${PROJECT}/databases/(default)/documents`

function pad(n, w = 2) {
  return String(n).padStart(w, '0')
}

// مولّد ثابت: نفس البيانات كل مرة، فالتشغيلتين بيقروا نفس الحاجة
let state = 20260930
function rand() {
  state = (state * 1103515245 + 12345) % 2147483648
  return state / 2147483648
}

function dateOf(i) {
  const start = Date.UTC(2025, 0, 1)
  const day = Math.floor((i / COUNT) * 634)
  return new Date(start + day * 86400000).toISOString().slice(0, 10)
}

const kinds = ['purchase', 'purchase', 'purchase', 'purchase', 'fee', 'internal_transfer', 'salary', 'support_gift']
const writes = []
for (let i = 0; i < COUNT; i++) {
  const kind = kinds[Math.floor(rand() * kinds.length)]
  const incoming = kind === 'salary'
  const fields = {
    occurredAt: { stringValue: dateOf(i) },
    sourceOrder: { integerValue: String(i % 7) },
    economicKind: { stringValue: kind },
    observedDirection: { stringValue: incoming ? 'in' : 'out' },
    amountMinor: { integerValue: String(Math.floor(rand() * 50000) + 100) },
    currency: { stringValue: 'SAR' },
    rawMerchantName: { stringValue: `متجر وهمي ${1 + Math.floor(rand() * 40)}` },
    categoryId: { stringValue: `cat-${1 + Math.floor(rand() * 25)}` },
    walletId: { stringValue: rand() < 0.8 ? 'wallet-bank' : 'wallet-cash' },
    reviewState: { stringValue: 'confirmed' },
    createdAt: { stringValue: '2026-09-30T00:00:00.000Z' },
    updatedAt: { stringValue: '2026-09-30T00:00:00.000Z' },
  }
  writes.push({ update: { name: `projects/${PROJECT}/databases/(default)/documents/users/trial-user/transactions/t-${pad(i, 5)}`, fields } })
}

for (let i = 0; i < writes.length; i += 400) {
  const res = await fetch(`${base}:batchWrite`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: 'Bearer owner' },
    body: JSON.stringify({ writes: writes.slice(i, i + 400) }),
  })
  if (!res.ok) throw new Error(`batch ${i}: ${res.status} ${await res.text()}`)
}
const check = await fetch(`${base}/users/trial-user/transactions?pageSize=1`, { headers: { Authorization: 'Bearer owner' } })
console.log(`seeded ${writes.length} documents; read-back status ${check.status}`)
