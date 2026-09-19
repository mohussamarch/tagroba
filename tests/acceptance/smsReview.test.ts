import { describe, expect, it } from 'vitest'
import { makeImportStatement } from '../../src/application/useCases/importStatement'
import { makeManageSmsInbox } from '../../src/application/useCases/manageSmsInbox'
import { makeReviewSmsInbox } from '../../src/application/useCases/reviewSmsInbox'
import { parseBankSms } from '../../src/infrastructure/import/bankSmsParser'
import { memorySmsInbox } from '../../src/infrastructure/memory/smsInbox'
import {
  FixedClock,
  MemoryCategoryRepository,
  MemoryImportBatchRepository,
  MemoryMerchantRepository,
  MemoryRuleRepository,
  MemorySourceRecordRepository,
  MemoryTransactionRepository,
  PassthroughUnitOfWork,
  SequentialIdGenerator,
} from '../../src/infrastructure/memory/memoryRepositories'
import type { Category } from '../../src/domain/entities/types'

/** OVERRIDES §36 — شاشة رسايل البنك: قايمة بتصنيفها وزرار واحد، و«نفتكر المحل؟». أسماء وأرقام وهمية. */
const cat = (id: string, name: string): Category => ({ id, parentId: null, name, iconKey: 'tag', lightColor: '#000', darkColor: '#fff', active: true, order: 1 })
const sms = (id: string, body: string) => ({ id, sender: 'AlRajhiBank', receivedAt: '2026-09-18T10:00:00Z', body })
const pos = (store: string, amount: string) => `شراء PoS\nعبر1111;مدى\nبـSR ${amount}\nلـ${store}\n26/9/18 09:35`
const target = { walletId: 'w-bank', accountIdentity: 'الراجحي' }

function setup(messages = [sms('a', pos('TEST MART', '9.25')), sms('b', pos('TEST MART', '12')), sms('c', pos('NEW CAFE', '15')), sms('bad', 'شراء بمبلغ 25 SAR')]) {
  const txns = new MemoryTransactionRepository(), sources = new MemorySourceRecordRepository(), batches = new MemoryImportBatchRepository()
  const merchants = new MemoryMerchantRepository([{ id: 'm1', displayName: 'TEST MART', normalizedName: 'test mart', verifiedCategoryId: 'groceries' }])
  const ids = new SequentialIdGenerator()
  const categories = new MemoryCategoryRepository([cat('groceries', 'بقالة'), cat('cafe', 'مقاهي')])
  const importer = makeImportStatement({
    txns, sources, batches, merchants,
    categories, rules: new MemoryRuleRepository([]),
    uow: new PassthroughUnitOfWork(), ids, clock: new FixedClock('2026-09-19T08:00:00Z'),
  })
  const inbox = makeManageSmsInbox(memorySmsInbox(messages), parseBankSms)
  const shared: string[] = []
  const review = makeReviewSmsInbox({ inbox, importer, merchants, categories, ids, contribute: async (t, c) => { shared.push(`${t.rawMerchantName}:${c}`) } })
  return { review, txns, sources, batches, merchants, shared, inbox }
}

describe('رسايل البنك — سجّل الكل (OVERRIDES §36)', () => {
  it('shows every readable message with its suggested category, and the unreadable one apart', async () => {
    const { review } = setup()
    const view = await review.load(target)
    expect(view.ready).toHaveLength(3)
    const mart = view.ready.filter((l) => l.merchant === 'TEST MART')
    expect(mart.every((l) => l.categoryId === 'groceries' && l.remembered)).toBe(true)
    const cafe = view.ready.find((l) => l.merchant === 'NEW CAFE')!
    expect(cafe.categoryId).toBeUndefined()
    expect(cafe.remembered).toBe(false)
    expect(view.failed.map((f) => f.messageId)).toEqual(['bad'])
  })

  it('records everything with one tap; a category the user picked is saved as confirmed', async () => {
    const { review, txns, inbox } = setup()
    const view = await review.load(target)
    const cafe = view.ready.find((l) => l.merchant === 'NEW CAFE')!
    const result = await review.recordAll({ categories: new Map([[cafe.lineNumber, 'cafe']]), includeSimilar: [] })
    expect(result.recorded).toBe(3)
    const saved = txns.all()
    expect(saved).toHaveLength(3)
    const savedCafe = saved.find((t) => t.rawMerchantName === 'NEW CAFE')!
    expect(savedCafe.categoryId).toBe('cafe')
    expect(savedCafe.categoryConfirmed).toBe(true)
    expect(saved.find((t) => t.amountMinor === 925)!.categoryId).toBe('groceries')
    // اتسجلوا ⇒ اتشالوا؛ المرفوضة بتفضل لحد ما تتشال بإيد المستخدم
    expect((await inbox.refresh()).messages.map((m) => m.id)).toEqual(['bad'])
  })

  it('never records twice: the same message after recording is a duplicate and gets cleared', async () => {
    const first = setup()
    await first.review.load(target)
    await first.review.recordAll({ categories: new Map(), includeSimilar: [] })
    // نفس الرسالة وصلت تاني (مثلًا إعادة القراءة) — لازم تتعرف مكررة
    const again = makeReviewSmsInbox({
      inbox: makeManageSmsInbox(memorySmsInbox([sms('a2', pos('TEST MART', '9.25'))]), parseBankSms),
      importer: makeImportStatement({
        txns: first.txns, sources: first.sources, batches: first.batches, merchants: first.merchants,
        categories: new MemoryCategoryRepository([]), rules: new MemoryRuleRepository([]), uow: new PassthroughUnitOfWork(), ids: new SequentialIdGenerator(), clock: new FixedClock('2026-09-19T09:00:00Z'),
      }),
      merchants: first.merchants, categories: new MemoryCategoryRepository([]), ids: new SequentialIdGenerator(),
    })
    const view = await again.load(target)
    expect(view.ready).toHaveLength(0)
    expect(view.duplicates.length + view.similar.length).toBe(1)
    await again.recordAll({ categories: new Map(), includeSimilar: [] })
    expect(first.txns.all()).toHaveLength(3)
  })

  it('«yes, remember it» pins the store locally, shares it as a suggestion, and the next load uses it', async () => {
    const { review, merchants, shared } = setup()
    expect(await review.remember('NEW CAFE', 'cafe', 'out')).toBe(true)
    expect((await merchants.listAll()).find((m) => m.displayName === 'NEW CAFE')!.verifiedCategoryId).toBe('cafe')
    expect(shared).toEqual(['NEW CAFE:cafe'])
    const cafe = (await review.load(target)).ready.find((l) => l.merchant === 'NEW CAFE')!
    expect(cafe.categoryId).toBe('cafe')
    expect(cafe.remembered).toBe(true)
  })

  it('remembering an existing store updates it instead of making a second one; digits-only names are not stores', async () => {
    const { review, merchants, shared } = setup()
    await review.remember('test  mart', 'cafe', 'in')
    const all = await merchants.listAll()
    expect(all).toHaveLength(1)
    expect(all[0]!.verifiedCategoryId).toBe('cafe')
    expect(shared).toEqual([]) // الدخل ما بيترفعش للقايمة المشتركة
    expect(await review.remember('1234', 'cafe', 'out')).toBe(false)
  })
})
