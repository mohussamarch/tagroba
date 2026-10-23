// مصروفي — صندوق الملاحظات ⇒ مشاكل (issues) في المستودع الخاص mohussamarch/masroufy-feedback (OVERRIDES §113 · §114 · PLAN-0.6 §16).
// بيشتغل من GitHub Actions (feedback.yml) كل 3 ساعات. قالب محفوظ في المستودع العام — **مفيش فيه أي مفتاح**: المفتاح في سر FIREBASE_SA_JSON
// في المستودع الخاص بس.
//
// 1) inbox/{id}: مستند لكل رسالة «ابعتلنا» ⇒ مشكلة بعلامة النوع (سؤال · اقتراح · مشكلة) والبلد (مصر · السعودية) ⇒ المستند يتمسح.
// 2) تنبيه التصنيف (§113): sharedMerchants اللي مش مؤكدة وتصنيفها عكس قاعدة جاهزة (builtin-rules.json) — لما يتجمع 3 مساهمات أو أكتر لنفس
//    المحل ⇒ مشكلة واحدة «تنبيه تصنيف» بتتفتح أو تتحدث. اسم المحل والقاعدة والتصنيفات بس — مفيش أي معرّف لحد ولا عدد لكل شخص.
//    ⚠️ المستند واحد لكل محل ومفيهوش مين كتبه (بالقصد) ⇒ «مساهمة» = كل مرة المستند اتكتب بتصنيف مختلف عن القاعدة (وقت تعديل جديد —
//    محفوظ في .feedback-state/category-votes.json). نفس الشخص ممكن يتعد مرتين، وكتابتين بين تشغيلتين بيتعدوا واحدة. (سؤال مفتوح للمالك.)
//
// التشغيل المحلي للتجربة (على محاكي فايربيز بس): FIRESTORE_EMULATOR_HOST=127.0.0.1:8095 DRY_RUN=1 node inbox-to-issues.mjs
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs'
import { initializeApp, cert } from 'firebase-admin/app'
import { getFirestore } from 'firebase-admin/firestore'

const DRY = process.env.DRY_RUN === '1'
const REPO = process.env.GITHUB_REPOSITORY // owner/name — GitHub بيحطه لوحده
const TOKEN = process.env.GITHUB_TOKEN
const STATE = '.feedback-state/category-votes.json'
const ALERT_AT = 3
const MAX_PER_RUN = 100

const KIND = { question: 'سؤال', suggestion: 'اقتراح', problem: 'مشكلة' }
const COUNTRY = { eg: 'مصر', sa: 'السعودية' }
const ALERT = 'تنبيه تصنيف'

function app() {
  if (process.env.FIRESTORE_EMULATOR_HOST) return initializeApp({ projectId: process.env.GCLOUD_PROJECT || 'demo-masroufy-kt' })
  const raw = process.env.FIREBASE_SA_JSON
  if (!raw) throw new Error('FIREBASE_SA_JSON secret is missing')
  const sa = JSON.parse(raw)
  return initializeApp({ credential: cert(sa), projectId: sa.project_id })
}

async function gh(method, path, body) {
  if (DRY) {
    console.log(`[dry] ${method} ${path} ${body?.title ?? ''}`)
    return method === 'GET' ? [] : {}
  }
  const res = await fetch(`https://api.github.com/repos/${REPO}${path}`, {
    method,
    headers: { authorization: `Bearer ${TOKEN}`, accept: 'application/vnd.github+json', 'content-type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  })
  if (res.status === 422 && path === '/labels') return {} // العلامة موجودة
  if (!res.ok) throw new Error(`GitHub ${method} ${path} => ${res.status}`)
  return res.status === 204 ? {} : res.json()
}

async function ensureLabels() {
  const colors = { سؤال: '1d76db', اقتراح: '0e8a16', مشكلة: 'd93f0b', مصر: 'c5def5', السعودية: 'bfdadc', [ALERT]: 'fbca04' }
  for (const [name, color] of Object.entries(colors)) await gh('POST', '/labels', { name, color })
}

const clip = (s, n) => (s.length > n ? s.slice(0, n - 1) + '…' : s)

async function inboxToIssues(db) {
  const snap = await db.collection('inbox').orderBy('createdAt').limit(MAX_PER_RUN).get()
  let done = 0
  for (const d of snap.docs) {
    const x = d.data()
    const kind = KIND[x.kind] ?? 'مشكلة'
    const country = COUNTRY[x.country]
    const text = String(x.text ?? '')
    const when = x.createdAt?.toDate?.().toISOString?.() ?? ''
    const body = [text, '', '---', `النسخة: ${x.appVersion ?? 'غير معروف'}`, `الوقت: ${when}`].join('\n')
    await gh('POST', '/issues', { title: `${kind}: ${clip(text.replace(/\s+/g, ' ').trim(), 70)}`, body, labels: [kind, ...(country ? [country] : [])] })
    if (!DRY) await d.ref.delete()
    done++
  }
  console.log(`inbox: ${done} issue(s)`)
}

// نفس فكرة normalizeText في التطبيق (تقريبي): حروف صغيرة · من غير تشكيل · أ/إ/آ ⇒ ا · ة ⇒ ه · ى ⇒ ي · مسافة واحدة
function norm(s) {
  return String(s ?? '').toLowerCase().replace(/[ً-ٰٟـ]/g, '').replace(/[أإآ]/g, 'ا').replace(/ة/g, 'ه').replace(/ى/g, 'ي')
    .replace(/[^\p{L}\p{N}]+/gu, ' ').trim()
}

function loadRules() {
  const file = JSON.parse(readFileSync(new URL('./builtin-rules.json', import.meta.url), 'utf8'))
  return { rules: file.rules.map((r) => ({ ...r, key: norm(r.word) })).filter((r) => r.key), names: file.categoryNames }
}

function ruleFor(name, rules) {
  const hay = ` ${norm(name)} `
  return rules.find((r) => hay.includes(` ${r.key} `)) ?? null
}

async function categoryAlerts(db) {
  const { rules, names } = loadRules()
  const state = existsSync(STATE) ? JSON.parse(readFileSync(STATE, 'utf8')) : {}
  const snap = await db.collection('sharedMerchants').where('confirmed', '==', false).get()
  const alerts = []
  for (const d of snap.docs) {
    const x = d.data()
    if (!x.categoryId || !x.normalizedName) continue
    const rule = ruleFor(x.normalizedName, rules)
    if (!rule) continue
    const expected = Object.values(rule.category)
    if (expected.includes(x.categoryId)) continue
    const at = x.updatedAt?.toDate?.().toISOString?.() ?? ''
    const s = (state[d.id] ??= { seen: [] })
    if (at && !s.seen.includes(at)) s.seen = [...s.seen, at].slice(-20)
    if (s.seen.length >= ALERT_AT) alerts.push({ shop: x.displayName || x.normalizedName, rule, chosen: x.categoryId, count: s.seen.length })
  }
  mkdirSync('.feedback-state', { recursive: true })
  writeFileSync(STATE, JSON.stringify(state, null, 2) + '\n')
  const open = await gh('GET', `/issues?state=open&labels=${encodeURIComponent(ALERT)}&per_page=100`)
  for (const a of alerts) {
    const title = `${ALERT}: ${a.shop}`
    const want = [...new Set(Object.values(a.rule.category))].map((id) => names[id] ?? id).join(' / ')
    const body = [
      `المحل: **${a.shop}**`,
      `القاعدة الجاهزة: كلمة «${a.rule.word}» ⇒ ${want}`,
      `اللي الناس اختاروه: ${names[a.chosen] ?? a.chosen}`,
      `عدد المساهمات المختلفة عن القاعدة (تقريبي): ${a.count}`,
      '',
      'لو القاعدة غلط: عدّلها في التطبيق (قواعد التصنيف الجاهزة) واقفل المشكلة دي.',
    ].join('\n')
    const old = open.find((i) => i.title === title)
    if (old) await gh('PATCH', `/issues/${old.number}`, { body })
    else await gh('POST', '/issues', { title, body, labels: [ALERT] })
  }
  console.log(`category alerts: ${alerts.length}`)
}

const db = getFirestore(app())
if (!DRY) await ensureLabels()
await inboxToIssues(db)
await categoryAlerts(db)
