// اختبارات قواعد Firestore (firestore.rules في جذر المستودع) على محاكي فايربيز — OVERRIDES §80 وقسم «الأمان» في CLAUDE.md (البند 2):
// مستخدمين اتنين (alice · bob) وواحد مش داخل. مشروع demo بس (المحاكي — ولا اتصال بفايربيز الحقيقي)، وكل البيانات مخترعة.
//
// التشغيل (Git Bash، من native-app/firestore/rules-test):
//   npm ci
//   PATH="$JAVA_HOME/bin:$PATH" E:/work/masroufy/node_modules/.bin/firebase emulators:exec --only firestore --project demo-masroufy-rules "node --test rules.test.mjs"
// (JAVA_HOME زي HANDOVER §2 · المحاكي على المنفذ 8095 عشان ما يتخانقش مع محاكيات اختبارات الأجهزة 8088/8089.)
import { after, before, beforeEach, describe, test } from 'node:test'
import { readFileSync } from 'node:fs'
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing'
import { collection, deleteDoc, doc, getDoc, getDocs, serverTimestamp, setDoc, Timestamp, updateDoc } from 'firebase/firestore'

const RULES = readFileSync(new URL('../../../firestore.rules', import.meta.url), 'utf8')
let env

before(async () => {
  env = await initializeTestEnvironment({ projectId: 'demo-masroufy-rules', firestore: { rules: RULES, host: '127.0.0.1', port: 8095 } })
})
after(async () => { await env?.cleanup() })
beforeEach(async () => { await env.clearFirestore() })

const alice = () => env.authenticatedContext('alice').firestore()
const bob = () => env.authenticatedContext('bob').firestore()
const nobody = () => env.unauthenticatedContext().firestore()
/** كتابة من غير قواعد (زي لوحة فايربيز) — لتجهيز بيانات موجودة. */
const seed = (path, data) => env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), path), data))

const TXN = 'users/alice/spaces/sa/transactions/t-1'
const txn = { id: 't-1', amountMinor: 1500, currency: 'SAR', occurredAt: '2026-10-01', economicKind: 'purchase' }

describe('users/{uid}: كل مستخدم بياناته بس', () => {
  test('صاحب الحساب يكتب ويقرا بياناته (الملف نفسه وأي عمق تحته)', async () => {
    await assertSucceeds(setDoc(doc(alice(), 'users/alice'), { displayName: 'TEST A' }))
    await assertSucceeds(setDoc(doc(alice(), TXN), txn))
    await assertSucceeds(getDoc(doc(alice(), TXN)))
    await assertSucceeds(getDocs(collection(alice(), 'users/alice/spaces/sa/transactions')))
    await assertSucceeds(updateDoc(doc(alice(), TXN), { amountMinor: 1600 }))
    await assertSucceeds(deleteDoc(doc(alice(), TXN)))
  })

  test('مستخدم تاني ما يقراش ولا يكتبش ولا يمسحش بيانات غيره', async () => {
    await seed(TXN, txn)
    await seed('users/alice', { displayName: 'TEST A' })
    await assertFails(getDoc(doc(bob(), TXN)))
    await assertFails(getDoc(doc(bob(), 'users/alice')))
    await assertFails(getDocs(collection(bob(), 'users/alice/spaces/sa/transactions')))
    await assertFails(setDoc(doc(bob(), 'users/alice/spaces/sa/transactions/t-2'), txn))
    await assertFails(updateDoc(doc(bob(), TXN), { amountMinor: 1 }))
    await assertFails(deleteDoc(doc(bob(), TXN)))
    await assertFails(setDoc(doc(bob(), 'users/alice'), { displayName: 'X' }))
  })

  test('المش داخل ما يوصلش لأي بيانات مستخدم', async () => {
    await seed(TXN, txn)
    await assertFails(getDoc(doc(nobody(), TXN)))
    await assertFails(getDocs(collection(nobody(), 'users/alice/spaces/sa/transactions')))
    await assertFails(setDoc(doc(nobody(), 'users/alice/spaces/sa/transactions/t-3'), txn))
    await assertFails(deleteDoc(doc(nobody(), TXN)))
  })

  test('الهوية من request.auth بس — uid مكتوب في البيانات ما يفتحش حساب حد', async () => {
    await assertFails(setDoc(doc(alice(), 'users/bob/spaces/sa/transactions/t-1'), { ...txn, uid: 'alice', ownerUid: 'alice' }))
    await assertFails(setDoc(doc(alice(), 'users/bob'), { uid: 'alice' }))
  })

  test('الممنوع هو الأصل: أي مجموعة تانية مقفولة حتى على الداخل', async () => {
    await seed('other/x', { a: 1 })
    await assertFails(getDoc(doc(alice(), 'other/x')))
    await assertFails(setDoc(doc(alice(), 'other/y'), { a: 1 }))
    await assertFails(getDocs(collection(alice(), 'users')))
  })
})

const merchant = (name, extra = {}) => ({
  normalizedName: name, displayName: name, aliases: [], categoryId: 'cat-food', confirmed: false, updatedAt: serverTimestamp(), ...extra,
})
const keyOf = (name) => name.replace(/ /g, '-')
const write = (db, name, data) => setDoc(doc(db, 'sharedMerchants', keyOf(name)), data)

describe('sharedMerchants: قايمة التجار المشتركة (§25.1 · §80)', () => {
  test('الداخل بيقرا والمش داخل لأ', async () => {
    await seed('sharedMerchants/TEST-SHOP', { ...merchant('TEST SHOP'), updatedAt: Timestamp.now() })
    await assertSucceeds(getDoc(doc(alice(), 'sharedMerchants/TEST-SHOP')))
    await assertSucceeds(getDocs(collection(bob(), 'sharedMerchants')))
    await assertFails(getDoc(doc(nobody(), 'sharedMerchants/TEST-SHOP')))
    await assertFails(getDocs(collection(nobody(), 'sharedMerchants')))
  })

  test('إضافة صح بالحقول المسموحة بس — والمش داخل ما يضيفش', async () => {
    await assertSucceeds(write(alice(), 'TEST SHOP', merchant('TEST SHOP', { aliases: Array.from({ length: 10 }, (_, i) => 'ALIAS ' + i) })))
    await assertSucceeds(write(alice(), 'NO CATEGORY', merchant('NO CATEGORY', { categoryId: null })))
    await assertFails(write(nobody(), 'ANON SHOP', merchant('ANON SHOP')))
  })

  test('الحقول: ناقص · زيادة (مبالغ أو مين ضاف) ⇒ مرفوض', async () => {
    const { categoryId, ...missing } = merchant('MISSING FIELD')
    await assertFails(write(alice(), 'MISSING FIELD', missing))
    await assertFails(write(alice(), 'EXTRA AMOUNT', merchant('EXTRA AMOUNT', { amountMinor: 1500 })))
    await assertFails(write(alice(), 'EXTRA UID', merchant('EXTRA UID', { addedBy: 'alice' })))
  })

  test('المفتاح = الاسم الموحد بشرطة مكان المسافة', async () => {
    await assertFails(setDoc(doc(alice(), 'sharedMerchants/OTHER-KEY'), merchant('TEST SHOP')))
    await assertFails(setDoc(doc(alice(), 'sharedMerchants/TEST SHOP'), merchant('TEST SHOP')))
  })

  test('الأحجام: الاسم 1-80 · الاسم المعروض لحد 80 · لحد 10 أسامي كل واحد 1-80 نص · التصنيف لحد 120', async () => {
    // الحد بالظبط مقبول — عشان الرفض اللي تحت يبقى بسبب الطول نفسه مش حاجة تانية.
    await assertSucceeds(write(alice(), 'Y'.repeat(80), merchant('Y'.repeat(80), { displayName: 'D'.repeat(80), aliases: ['A'.repeat(80)], categoryId: 'c'.repeat(120) })))
    // اسم فاضي: المفتاح الفاضي مش مسموح أصلًا، وأي مفتاح تاني ما يساويش الاسم ⇒ مرفوض.
    await assertFails(setDoc(doc(alice(), 'sharedMerchants/EMPTY'), merchant('')))
    await assertFails(write(alice(), 'X'.repeat(81), merchant('X'.repeat(81))))
    await assertFails(write(alice(), 'LONG DISPLAY', merchant('LONG DISPLAY', { displayName: 'D'.repeat(81) })))
    await assertFails(write(alice(), 'ELEVEN ALIASES', merchant('ELEVEN ALIASES', { aliases: Array.from({ length: 11 }, (_, i) => 'A' + i) })))
    await assertFails(write(alice(), 'LONG ALIAS', merchant('LONG ALIAS', { aliases: ['A'.repeat(81)] })))
    await assertFails(write(alice(), 'EMPTY ALIAS', merchant('EMPTY ALIAS', { aliases: [''] })))
    await assertFails(write(alice(), 'NUMBER ALIAS', merchant('NUMBER ALIAS', { aliases: [42] })))
    await assertFails(write(alice(), 'LONG CATEGORY', merchant('LONG CATEGORY', { categoryId: 'c'.repeat(121) })))
    await assertFails(write(alice(), 'NAME NUMBER', merchant('NAME NUMBER', { normalizedName: 7 })))
  })

  test('«مؤكد» عمره ما يتكتب من التطبيق، ووقت التعديل وقت السيرفر', async () => {
    await assertFails(write(alice(), 'SELF CONFIRMED', merchant('SELF CONFIRMED', { confirmed: true })))
    await assertFails(write(alice(), 'CLIENT TIME', merchant('CLIENT TIME', { updatedAt: Timestamp.fromMillis(Date.UTC(2026, 0, 1)) })))
  })

  test('التعديل: المش مؤكد بس — المؤكد من اللوحة ما يتلمسش', async () => {
    await seed('sharedMerchants/OPEN-SHOP', { ...merchant('OPEN SHOP'), updatedAt: Timestamp.now() })
    await assertSucceeds(write(bob(), 'OPEN SHOP', merchant('OPEN SHOP', { categoryId: 'cat-coffee' })))
    await seed('sharedMerchants/LOCKED-SHOP', { ...merchant('LOCKED SHOP'), confirmed: true, updatedAt: Timestamp.now() })
    await assertFails(write(bob(), 'LOCKED SHOP', merchant('LOCKED SHOP', { categoryId: 'cat-other' })))
    await assertFails(updateDoc(doc(alice(), 'sharedMerchants/LOCKED-SHOP'), { confirmed: false, updatedAt: serverTimestamp() }))
  })

  test('مفيش مسح خالص', async () => {
    await seed('sharedMerchants/KEEP-SHOP', { ...merchant('KEEP SHOP'), updatedAt: Timestamp.now() })
    await assertFails(deleteDoc(doc(alice(), 'sharedMerchants/KEEP-SHOP')))
    await assertFails(deleteDoc(doc(nobody(), 'sharedMerchants/KEEP-SHOP')))
  })
})

// «ابعتلنا» — inbox/{id} (OVERRIDES §114 · PLAN-0.6 §16): كتابة بس، المفاتيح بالظبط، من غير قراية ولا تعديل ولا مسح
const feedback = (extra = {}) => ({ kind: 'question', country: 'eg', text: 'TEST QUESTION', appVersion: 'kotlin-0.6', createdAt: serverTimestamp(), ...extra })
const send = (db, id, data) => setDoc(doc(db, 'inbox', id), data)

describe('inbox: «ابعتلنا» كتابة بس (§114)', () => {
  test('أي داخل (alice · bob) يكتب مستند جديد بالحقول الخمسة بالظبط — والمش داخل لأ', async () => {
    await assertSucceeds(send(alice(), 'fb-a', feedback()))
    await assertSucceeds(send(bob(), 'fb-b', feedback({ kind: 'problem', country: 'sa' })))
    await assertSucceeds(send(alice(), 'fb-c', feedback({ kind: 'suggestion', text: 'x'.repeat(2000) })))
    await assertFails(send(nobody(), 'fb-n', feedback()))
  })

  test('حقول زيادة (uid · إيميل · مبلغ) أو ناقصة ⇒ مرفوض', async () => {
    await assertFails(send(alice(), 'fb-1', feedback({ uid: 'alice' })))
    await assertFails(send(alice(), 'fb-2', feedback({ email: 'a@example.com' })))
    await assertFails(send(alice(), 'fb-3', feedback({ amountMinor: 1500 })))
    const { appVersion, ...noVersion } = feedback()
    await assertFails(send(alice(), 'fb-4', noVersion))
    const { createdAt, ...noTime } = feedback()
    await assertFails(send(alice(), 'fb-5', noTime))
  })

  test('الأنواع والأطوال: النوع والبلد من القايمة · النص 1-2000 · النسخة 1-40 · الوقت وقت السيرفر', async () => {
    await assertFails(send(alice(), 'fb-6', feedback({ kind: 'spam' })))
    await assertFails(send(alice(), 'fb-7', feedback({ country: 'ae' })))
    await assertFails(send(alice(), 'fb-8', feedback({ text: '' })))
    await assertFails(send(alice(), 'fb-9', feedback({ text: 'x'.repeat(2001) })))
    await assertFails(send(alice(), 'fb-10', feedback({ text: 42 })))
    await assertFails(send(alice(), 'fb-11', feedback({ appVersion: 'v'.repeat(41) })))
    await assertFails(send(alice(), 'fb-12', feedback({ createdAt: Timestamp.fromDate(new Date('2026-01-01T00:00:00Z')) })))
  })

  test('لا قراية ولا تعديل ولا مسح — حتى صاحب المستند', async () => {
    await assertSucceeds(send(alice(), 'fb-own', feedback()))
    await assertFails(getDoc(doc(alice(), 'inbox/fb-own')))
    await assertFails(getDocs(collection(alice(), 'inbox')))
    await assertFails(getDocs(collection(bob(), 'inbox')))
    await assertFails(getDoc(doc(nobody(), 'inbox/fb-own')))
    await assertFails(updateDoc(doc(alice(), 'inbox/fb-own'), { text: 'changed' }))
    // نفس المعرّف تاني (إعادة محاولة بعد ما الأولى وصلت) = تعديل ⇒ مرفوض (التطبيق بيشيلها من المستنية)
    await assertFails(send(alice(), 'fb-own', feedback()))
    await assertFails(deleteDoc(doc(alice(), 'inbox/fb-own')))
    await assertFails(deleteDoc(doc(bob(), 'inbox/fb-own')))
  })
})
