import { afterEach, describe, expect, it } from 'vitest'
import { initializeApp, deleteApp, type FirebaseApp } from 'firebase/app'
import { connectFirestoreEmulator, doc, getFirestore, serverTimestamp, setDoc, terminate, type Firestore } from 'firebase/firestore'
import { FirestoreSharedMerchantCatalog } from '../../src/infrastructure/firestore/sharedMerchantCatalogRepository'

/**
 * قواعد قاعدة التجار المشتركة على Firestore Emulator حقيقي (مشروع demo بس) — بند Codex §38-3.
 * التشغيل: HANDOVER §38 (`MASROUFY_TEST_FIRESTORE=127.0.0.1:8088`). أسماء وهمية.
 */
const enabled = process.env.MASROUFY_TEST_FIRESTORE === '127.0.0.1:8088'
const clients: { app: FirebaseApp; db: Firestore }[] = []
function client(uid = 'shared-' + crypto.randomUUID()) {
  const app = initializeApp({ projectId: 'demo-masroufy-audit', apiKey: 'fake-test-key', appId: 'shared-test' }, crypto.randomUUID())
  const db = getFirestore(app)
  connectFirestoreEmulator(db, '127.0.0.1', 8088, { mockUserToken: { sub: uid } })
  clients.push({ app, db })
  return db
}
afterEach(async () => { for (const { db, app } of clients.splice(0)) { await terminate(db); await deleteApp(app) } })

const body = (name: string, aliases: unknown) => ({
  normalizedName: name, displayName: name, aliases, categoryId: 'cat-food', confirmed: false, updatedAt: serverTimestamp(),
})

describe.skipIf(!enabled)('Shared merchant rules on Firestore Emulator', () => {
  it('accepts up to ten text aliases through the app writer', async () => {
    const catalog = new FirestoreSharedMerchantCatalog(client())
    // RULES-4: من غير 4 أرقام ورا بعض (الجزء العشوائي كان ممكن يطلع فيه أرقام)
    const name = 'GOOD SHOP ' + crypto.randomUUID().slice(0, 8).toUpperCase().replace(/[0-9]/g, 'X')
    await catalog.save({ normalizedName: name, displayName: name, aliases: Array.from({ length: 10 }, (_, i) => 'ALIAS ' + i), categoryId: 'cat-food', confirmed: false })
    expect((await catalog.get(name))?.aliases).toHaveLength(10)
  }, 20000)

  it.each([
    ['a number', [42]],
    ['an empty alias', ['']],
    ['an alias over 80 characters', ['X'.repeat(81)]],
    ['eleven aliases', Array.from({ length: 11 }, (_, i) => 'A' + i)],
    ['a bad value after good ones', ['OK', 'OK 2', { nested: true }]],
  ])('rejects %s', async (_label, aliases) => {
    const name = 'BAD SHOP ' + crypto.randomUUID().slice(0, 8).toUpperCase()
    await expect(setDoc(doc(client(), 'sharedMerchants', name.replace(/ /g, '-')), body(name, aliases))).rejects.toThrow()
  }, 20000)

  it('still refuses a confirmed entry from the app', async () => {
    const name = 'SELF CONFIRMED ' + crypto.randomUUID().slice(0, 8).toUpperCase()
    await expect(setDoc(doc(client(), 'sharedMerchants', name.replace(/ /g, '-')), { ...body(name, []), confirmed: true })).rejects.toThrow()
  }, 20000)
})

/** RULES-4 (r06-security — مسودة ما اتنشرتش): التصنيف معرّف «cat-…» أو null · مفيش 4 أرقام ورا بعض. مستخدمين وهميين اتنين + واحد مش داخل. */
function signedOut() {
  const app = initializeApp({ projectId: 'demo-masroufy-audit', apiKey: 'fake-test-key', appId: 'shared-test' }, crypto.randomUUID())
  const db = getFirestore(app)
  connectFirestoreEmulator(db, '127.0.0.1', 8088)
  clients.push({ app, db })
  return db
}
const fresh = (prefix: string) => prefix + ' ' + crypto.randomUUID().slice(0, 6).toUpperCase().replace(/[0-9]/g, 'X')
const ref = (db: Firestore, name: string) => doc(db, 'sharedMerchants', name.replace(/ /g, '-'))

describe.skipIf(!enabled)('RULES-4: category id and digit runs', () => {
  it('two users: seed category ids (Arabic slugs too) and null are accepted, and the other user can update', async () => {
    const name = fresh('CAFE')
    await setDoc(ref(client('rules4-user-a'), name), { ...body(name, ['CAFE ONE']), categoryId: 'cat-مطاعم-وقهوه' })
    await setDoc(ref(client('rules4-user-b'), name), { ...body(name, []), categoryId: 'cat-العنايه-الشخصيه--عطور' })
    await setDoc(ref(client('rules4-user-b'), name), { ...body(name, []), categoryId: null })
    const other = fresh('SHOP')
    await setDoc(ref(client('rules4-user-a'), other), { ...body(other, []), categoryId: 'cat-bank-fees' })
  }, 20000)

  it.each([
    ['free text', 'any words here'],
    ['upper case', 'cat-FOOD'],
    ['too long', 'cat-' + 'a'.repeat(61)],
    ['a number', 7],
  ])('rejects a category id that is %s', async (_label, categoryId) => {
    const name = fresh('BADCAT')
    await expect(setDoc(ref(client('rules4-user-a'), name), { ...body(name, []), categoryId })).rejects.toThrow()
  }, 20000)

  it('rejects four digits in a row in the name, display name or an alias', async () => {
    const a = client('rules4-user-a')
    const named = fresh('SHOP 1234')
    await expect(setDoc(ref(a, named), body(named, []))).rejects.toThrow()
    const shown = fresh('SHOP')
    await expect(setDoc(ref(a, shown), { ...body(shown, []), displayName: 'SHOP ٠١٢٣' })).rejects.toThrow()
    const alias = fresh('SHOP')
    await expect(setDoc(ref(a, alias), body(alias, ['OK', 'BRANCH 5678']))).rejects.toThrow()
    const fine = fresh('SHOP 123')
    await setDoc(ref(a, fine), body(fine, ['BRANCH 12']))
  }, 20000)

  it('a signed-out client can neither create nor read', async () => {
    const name = fresh('NOAUTH')
    await expect(setDoc(ref(signedOut(), name), body(name, []))).rejects.toThrow()
    const { getDoc } = await import('firebase/firestore')
    await expect(getDoc(ref(signedOut(), 'ANY'))).rejects.toThrow()
  }, 20000)
})
