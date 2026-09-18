import { afterEach, describe, expect, it } from 'vitest'
import { initializeApp, deleteApp, type FirebaseApp } from 'firebase/app'
import { connectFirestoreEmulator, doc, getDoc, getDocs, collection, getFirestore, setDoc, terminate, type Firestore } from 'firebase/firestore'
import { firestoreSettlementWriter, settlementRevision } from '../../src/infrastructure/firestore/settlementWriter'
import { FirestoreSettlementRepository } from '../../src/infrastructure/firestore/peopleRepositories'
import { firestoreFullBackup } from '../../src/infrastructure/firestore/fullBackupRepository'
import { emptyBackupData } from '../../src/domain/fullBackup'
import type { Obligation } from '../../src/domain/entities/types'

const enabled = process.env.MASROUFY_TEST_FIRESTORE === '127.0.0.1:8088'
const clients: { app: FirebaseApp; db: Firestore }[] = []
const debt: Obligation = { id: 'debt', personId: 'person', kind: 'receivable', originalMinor: 10000, originTransactionId: null, currency: 'SAR' }
const command = { id: 'request', personId: 'person', obligationId: 'debt', transactionId: '', amountMinor: 7000 }
function client(uid = 'settle-' + crypto.randomUUID()) {
  const app = initializeApp({ projectId: 'demo-masroufy-audit', apiKey: 'fake-test-key', appId: 'settle-test' }, crypto.randomUUID())
  const db = getFirestore(app)
  connectFirestoreEmulator(db, '127.0.0.1', 8088, { mockUserToken: { sub: uid } })
  clients.push({ app, db })
  return { db, uid, writer: firestoreSettlementWriter(db, uid), repo: new FirestoreSettlementRepository(db, uid) }
}
async function setup() {
  const first = client(), second = client(first.uid)
  await setDoc(doc(first.db, 'users', first.uid, 'obligations', 'debt'), debt)
  return { first, second }
}
afterEach(async () => { for (const { db, app } of clients.splice(0)) { await terminate(db); await deleteApp(app) } })

describe.skipIf(!enabled)('Settlement transactions on Firestore Emulator', () => {
  it('two devices cannot settle 140 against 100', async () => {
    const { first, second } = await setup()
    const results = await Promise.allSettled([first.writer.settle(command), second.writer.settle({ ...command, id: 'second' })])
    expect(results.filter(result => result.status === 'fulfilled')).toHaveLength(1)
    const rows = await first.repo.listByObligations(['debt'])
    expect(rows).toHaveLength(1)
    expect(rows[0].amountMinor).toBe(7000)
  }, 20000)
  it('two devices can each settle half, without losing either payment', async () => {
    const { first, second } = await setup()
    await Promise.all([first.writer.settle({ ...command, amountMinor: 5000 }), second.writer.settle({ ...command, id: 'second', amountMinor: 5000 })])
    expect((await first.repo.listByObligations(['debt'])).reduce((sum, row) => sum + row.amountMinor, 0)).toBe(10000)
  }, 20000)
  it('same request from two devices and then a restarted client has one effect', async () => {
    const { first, second } = await setup()
    const input = { ...command, amountMinor: 10000 }
    const [a, b] = await Promise.all([first.writer.settle(input), second.writer.settle(input)])
    expect(a).toEqual(b)
    expect(await client(first.uid).writer.settle(input)).toEqual(a)
    expect((await getDocs(collection(first.db, 'users', first.uid, 'settlements'))).size).toBe(1)
    await expect(second.writer.settle({ ...input, amountMinor: 1000 })).rejects.toThrow('بيانات مختلفة')
  }, 20000)
  it('counts historical settlements and tracks repository deletion revisions', async () => {
    const { first } = await setup()
    await first.repo.saveMany([{ ...command, id: 'historical', amountMinor: 4000 }])
    await expect(first.writer.settle(command)).rejects.toThrow('أكبر من المتبقي')
    const before = (await getDoc(settlementRevision(first.db, first.uid))).data()?.revision
    await first.repo.deleteMany(['historical'])
    expect((await getDoc(settlementRevision(first.db, first.uid))).data()?.revision).toBe(before + 1)
    await expect(first.writer.settle(command)).resolves.toMatchObject({ amountMinor: 7000 })
  }, 20000)
  it('rejects wrong person and missing debt without persisting a settlement', async () => {
    const { first } = await setup()
    await expect(first.writer.settle({ ...command, personId: 'other' })).rejects.toThrow()
    await expect(first.writer.settle({ ...command, obligationId: 'missing' })).rejects.toThrow()
    expect(await first.repo.listByObligations(['debt'])).toEqual([])
  }, 20000)
  it('backup restoration advances the same revision and restored payments reduce the remaining debt', async () => {
    const { first } = await setup()
    const data = emptyBackupData()
    data.settlements.push({ id: 'restored', obligationId: 'debt', transactionId: '', amountMinor: 4000 })
    await firestoreFullBackup(first.db, first.uid).addMissing(data)
    expect((await getDoc(settlementRevision(first.db, first.uid))).data()?.revision).toBe(1)
    await expect(first.writer.settle(command)).rejects.toThrow('أكبر من المتبقي')
    await firestoreFullBackup(first.db, first.uid).addMissing(data)
    expect((await getDoc(settlementRevision(first.db, first.uid))).data()?.revision).toBe(1)
  }, 20000)
})
