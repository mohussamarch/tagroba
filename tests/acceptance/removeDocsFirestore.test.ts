import { it, expect, vi } from 'vitest'

const mocks = vi.hoisted(() => ({ deletes: [] as string[], commits: 0 }))
vi.mock('firebase/firestore', () => ({
  doc: (_db: unknown, ...parts: string[]) => parts.join('/'),
  writeBatch: () => ({ delete: (path: string) => mocks.deletes.push(path), commit: async () => { mocks.commits++ } }),
}))

import { firestoreRemoveDocs } from '../../src/infrastructure/firestore/removeDocs'

/**
 * الحذف لازم يكون بمسار الوثيقة الحقيقي (docId) وجوه حساب المستخدم بس — الحذف
 * بحقل `id` المقصوص هو اللي ساب بقايا التراجع عن دفعة الإكسل.
 */
it('يحذف بمسار الوثيقة داخل حساب المستخدم، على دفعات لا تتجاوز 400', async () => {
  mocks.deletes.length = 0; mocks.commits = 0
  const items = Array.from({ length: 401 }, (_, i) => ({ group: 'transactions' as const, docId: `txn-0mtqh4w${String(i).padStart(5, '0')}-x` }))
  expect(await firestoreRemoveDocs({} as never, 'owner').remove(items)).toBe(401)
  expect(mocks.commits).toBe(2)
  expect(mocks.deletes[0]).toBe('users/owner/transactions/txn-0mtqh4w00000-x')
  expect(mocks.deletes.every((path) => path.startsWith('users/owner/') && !path.includes('****'))).toBe(true)
})
