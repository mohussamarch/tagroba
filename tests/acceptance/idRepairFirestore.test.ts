import { it, expect, vi } from 'vitest'

const mocks = vi.hoisted(() => ({ getDocs: vi.fn(), updates: [] as [string, unknown][], commits: 0 }))
vi.mock('firebase/firestore', () => ({
  collection: (_db: unknown, ...parts: string[]) => parts.join('/'),
  doc: (_db: unknown, ...parts: string[]) => parts.join('/'),
  documentId: () => '__name__', orderBy: (key: string) => ({ orderBy: key }), limit: (n: number) => ({ limit: n }),
  startAfter: (cursor: unknown) => ({ startAfter: cursor }),
  query: (path: unknown, ...constraints: unknown[]) => ({ path, constraints }),
  getDocsFromServer: mocks.getDocs,
  writeBatch: () => ({ update: (path: string, fields: unknown) => mocks.updates.push([path, fields]), commit: async () => { mocks.commits++ } }),
}))

import { firestoreIdRepair } from '../../src/infrastructure/firestore/idRepairRepository'

it('يقرأ صفحات الخادم محتفظًا بمعرّف الوثيقة الحقيقي، داخل حساب المستخدم فقط', async () => {
  const docs = Array.from({ length: 200 }, (_, i) => ({ id: `txn-${i}00000`, data: () => ({ id: `txn-${i}****0000` }) }))
  mocks.getDocs.mockReset()
  mocks.getDocs.mockResolvedValue({ size: 0, docs: [] })
    .mockResolvedValueOnce({ size: 0, docs: [] }) // wallets
  // المجموعة الحادية عشرة (transactions): صفحتان
  for (let i = 1; i < 10; i++) mocks.getDocs.mockResolvedValueOnce({ size: 0, docs: [] })
  mocks.getDocs.mockResolvedValueOnce({ size: 200, docs }).mockResolvedValueOnce({ size: 1, docs: [{ id: 'txn-last', data: () => ({ id: 'txn-last' }) }] })
  const data = await firestoreIdRepair({} as never, 'owner').readAll()
  expect(data.transactions).toHaveLength(201)
  expect(data.transactions[0]).toEqual({ docId: 'txn-000000', data: { id: 'txn-0****0000' } })
  const queries = mocks.getDocs.mock.calls.map((call) => call[0])
  expect(queries.every((q) => q.path.startsWith('users/owner/'))).toBe(true)
})

it('يكتب update فقط على مسار الوثيقة، على دفعات لا تتجاوز 400', async () => {
  mocks.updates.length = 0; mocks.commits = 0
  const patches = Array.from({ length: 401 }, (_, i) => ({ group: 'transactions' as const, docId: `txn-${i}`, fields: { id: `txn-${i}` } }))
  expect(await firestoreIdRepair({} as never, 'owner').apply(patches)).toBe(401)
  expect(mocks.commits).toBe(2)
  expect(mocks.updates[0]).toEqual(['users/owner/transactions/txn-0', { id: 'txn-0' }])
})
