import { it, expect, vi, beforeEach } from 'vitest'

/**
 * سلامة المعرّفات عند الكتابة في Firestore.
 *
 * حماية أرقام الحسابات (OVERRIDES §2) بتقص أي 5 أرقام متتالية في النص.
 * المعرّفات والروابط **مش نص حر**: لو اتقصت، الحقل `id` بيختلف عن مسار
 * الوثيقة، فالتعديل بيدور على وثيقة مش موجودة، والروابط (merchantId…)
 * بتشاور على حاجة مش موجودة، والنسخة الشاملة بترفض الحساب كله.
 * معرّف مولَّد بصيغة RandomIdGenerator فيه 5 أرقام متتالية في ~30% من الحالات،
 * ومعرّفات التجار المزروعة `merch-00001` كلها فيها 5 أرقام.
 */

const mocks = vi.hoisted(() => ({ sets: [] as [string, Record<string, unknown>][], updates: [] as [string, Record<string, unknown>][] }))
vi.mock('firebase/firestore', () => ({
  collection: (_db: unknown, path: string) => path,
  doc: (_db: unknown, path: string) => path,
  deleteDoc: vi.fn(), getDoc: vi.fn(), getDocs: vi.fn(), limit: vi.fn(), orderBy: vi.fn(), query: vi.fn(), where: vi.fn(),
  setDoc: async (path: string, data: Record<string, unknown>) => { mocks.sets.push([path, data]) },
  updateDoc: async (path: string, data: Record<string, unknown>) => { mocks.updates.push([path, data]) },
  writeBatch: () => ({ set: (path: string, data: Record<string, unknown>) => mocks.sets.push([path, data]), commit: async () => {} }),
}))

import {
  FirestoreTransactionRepository,
  FirestoreSourceRecordRepository,
} from '../../src/infrastructure/firestore/firestoreRepositories'
import type { SourceRecord, Transaction } from '../../src/domain/entities/types'

const TXN_ID = 'txn-0mtqh4w00000-4n444r1n275n' // صيغة RandomIdGenerator الحقيقية، فيها «00000»
const now = '2026-09-10T00:00:00.000Z'
const txn: Transaction = {
  id: TXN_ID, occurredAt: '2026-09-01', datePrecision: 'day', sourceOrder: 1,
  economicKind: 'purchase', economicKindConfirmed: false, observedDirection: 'out',
  amountMinor: 2999, currency: 'SAR', merchantId: 'merch-00001', categoryId: 'cat-مطاعم',
  walletId: 'wallet-bank', categoryConfirmed: false, excludedFromBudget: false,
  reviewState: 'suggested', isCashTagged: false,
  rawDescription: 'TOACCT/18100608016091127TOABDUL', createdAt: now, updatedAt: now,
}

beforeEach(() => { mocks.sets.length = 0; mocks.updates.length = 0 })

it('المعرّف والروابط تتحفظ زي ما هي، ورقم الحساب في النص يفضل مقصوص', async () => {
  await new FirestoreTransactionRepository({} as never, 'owner').saveMany([txn])
  const [path, data] = mocks.sets[0]
  expect(path).toBe(`users/owner/transactions/${TXN_ID}`)
  expect(data.id).toBe(TXN_ID) // لازم يساوي مسار الوثيقة
  expect(data.merchantId).toBe('merch-00001') // نفس معرّف وثيقة التاجر
  expect(data.rawDescription).toBe('TOACCT/****1127TOABDUL') // حماية الخصوصية باقية
})

it('التعديل بيكتب على نفس مسار الوثيقة اللي اتحفظت فيه', async () => {
  const repo = new FirestoreTransactionRepository({} as never, 'owner')
  await repo.saveMany([txn])
  const stored = mocks.sets[0][1] as unknown as Transaction
  await repo.update(stored.id, { note: 'تعديل' }) // الشاشة بتستعمل الـid اللي رجع من القراءة
  expect(mocks.updates[0][0]).toBe(mocks.sets[0][0])
})

it('سجل المصدر يحتفظ برابط العملية ويقص رقم الحساب من السطر', async () => {
  const record: SourceRecord = {
    id: 'src-0mtqh4w00001-2b5v2e2b6v5s', batchId: 'batch-0mtqh4w00002-4p1j0b3p083e',
    accountIdentity: 'SA0380000000608010167519', sourceReference: null, sourceHash: 'h',
    originalRowIndex: 12345, rawLine: 'FRACCT/67800608010143577FR', transactionId: TXN_ID,
    matchingState: 'new', reason: 'جديد',
  }
  await new FirestoreSourceRecordRepository({} as never, 'owner').saveMany([record])
  const data = mocks.sets[0][1]
  expect(data.id).toBe(record.id)
  expect(data.batchId).toBe(record.batchId)
  expect(data.transactionId).toBe(TXN_ID)
  expect(data.accountIdentity).toBe('SA****7519')
  expect(data.rawLine).toBe('FRACCT/****3577FR')
})
