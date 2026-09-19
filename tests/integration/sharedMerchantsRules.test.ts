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
    const name = 'GOOD SHOP ' + crypto.randomUUID().slice(0, 8).toUpperCase()
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
