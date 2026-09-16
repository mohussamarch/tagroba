import { afterEach, describe, expect, it } from 'vitest'
import { initializeApp, deleteApp, type FirebaseApp } from 'firebase/app'
import { connectFirestoreEmulator, deleteDoc, disableNetwork, doc, getDoc, getDocs, collection, getFirestore, setDoc, terminate, type Firestore } from 'firebase/firestore'
import { firestoreReferenceSeed } from '../../src/infrastructure/firestore/referenceSeed'
import { FirestoreCategoryRepository, FirestoreRuleRepository, FirestoreMerchantRepository } from '../../src/infrastructure/firestore/referenceRepositories'
import { makeSeedUserReferences, type SeedSource } from '../../src/application/useCases/seedUserReferences'

// Explicit loopback-only opt-in. Never connect these tests to a real Firebase project.
const host = process.env.MASROUFY_TEST_FIRESTORE
const enabled = host === '127.0.0.1:8088'
const clients: { app: FirebaseApp; db: Firestore }[] = []
const source: SeedSource = {
  categories: [{ id: 'food', parentId: null, name: 'طعام', lightColor: '#008800', darkColor: '#008800', iconKey: 'food', active: true, order: 1 }],
  rules: [{ id: 'rule', matchText: 'SHOP', matchMode: 'contains', categoryId: 'food', priority: 1, enabled: true }],
  merchants: [{ id: 'shop', normalizedName: 'SHOP', displayName: 'Shop' }],
}

function client(uid = 'seed-' + crypto.randomUUID()) {
  const app = initializeApp({ projectId: 'demo-masroufy-audit', apiKey: 'fake-test-key', appId: 'seed-test' }, crypto.randomUUID())
  const db = getFirestore(app)
  connectFirestoreEmulator(db, '127.0.0.1', 8088, { mockUserToken: { sub: uid } })
  clients.push({ app, db })
  const repos = { categories: new FirestoreCategoryRepository(db, uid), rules: new FirestoreRuleRepository(db, uid), merchants: new FirestoreMerchantRepository(db, uid) }
  const progress = firestoreReferenceSeed(db, uid)
  return { db, uid, repos, progress, seed: makeSeedUserReferences({ ...repos, progress }) }
}

afterEach(async () => {
  for (const { db, app } of clients.splice(0)) { await terminate(db); await deleteApp(app) }
})

describe.skipIf(!enabled)('Reference initialization against real Firestore Emulator', () => {
  it('resumes after categories were persisted and another client takes over', async () => {
    const first = client()
    await first.progress.begin(false)
    await first.progress.insertMissing({ ...source, rules: [], merchants: [] })
    await first.repos.categories.save({ ...source.categories[0], name: 'تعديل محفوظ' })
    const next = client(first.uid)
    expect((await next.seed(source)).seeded).toBe(true)
    expect((await next.repos.categories.listAll())[0].name).toBe('تعديل محفوظ')
    expect(await next.repos.rules.listAll()).toHaveLength(1)
    expect(await next.repos.merchants.listAll()).toHaveLength(1)
    expect((await getDoc(doc(next.db, 'users', next.uid, 'initialization', 'references-v1'))).data()?.state).toBe('complete')
  }, 20000)

  it('does not resurrect deleted references, including deletion of all categories', async () => {
    const first = client()
    await first.seed(source)
    await deleteDoc(doc(first.db, 'users', first.uid, 'categories', 'food'))
    await deleteDoc(doc(first.db, 'users', first.uid, 'rules', 'rule'))
    const next = client(first.uid)
    expect((await next.seed(source)).seeded).toBe(false)
    expect(await next.repos.categories.listAll()).toEqual([])
    expect(await next.repos.rules.listAll()).toEqual([])
  }, 20000)

  it('two clients initialize concurrently with more than one write page', async () => {
    const first = client(), second = client(first.uid)
    const large = { ...source, merchants: Array.from({ length: 205 }, (_, i) => ({ id: 'm' + i, normalizedName: 'shop ' + i, displayName: 'Shop ' + i })) }
    await Promise.all([first.seed(large), second.seed(large)])
    expect((await getDocs(collection(first.db, 'users', first.uid, 'merchants'))).size).toBe(205)
    expect(await first.repos.categories.listAll()).toHaveLength(1)
    expect((await second.seed(large)).seeded).toBe(false)
  }, 30000)

  it('adopts old accounts without filling intentionally absent references', async () => {
    const sys = client()
    await sys.repos.categories.save(source.categories[0])
    expect((await sys.seed(source)).seeded).toBe(false)
    expect(await sys.repos.rules.listAll()).toEqual([])
  }, 20000)

  it('reopens an initialized account from its local cache with networking disabled', async () => {
    const sys = client()
    await sys.seed(source)
    await sys.seed(source)
    await disableNetwork(sys.db)
    expect((await sys.seed(source)).seeded).toBe(false)
    expect(await sys.repos.rules.listAll()).toHaveLength(1)
  }, 10000)

  it('rejects a corrupt marker without adding reference data', async () => {
    const sys = client()
    await setDoc(doc(sys.db, 'users', sys.uid, 'initialization', 'references-v1'), { state: 'invalid' })
    await expect(sys.seed(source)).rejects.toThrow('حالة تجهيز المراجع غير سليمة')
    expect(await sys.repos.categories.listAll()).toEqual([])
  }, 20000)
})
