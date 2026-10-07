import { makeReadBankSms } from '../../src/application/useCases/readBankSms'
import { makeManageSmsInbox } from '../../src/application/useCases/manageSmsInbox'
import { makeReviewSmsInbox } from '../../src/application/useCases/reviewSmsInbox'
import { makeImportStatement } from '../../src/application/useCases/importStatement'
import type { BankSmsMessage, BankSmsPort } from '../../src/application/ports/BankSmsPort'
import type { QueuedSms } from '../../src/application/ports/SmsInboxPort'
import {
  MemoryTransactionRepository,
  MemorySourceRecordRepository,
  MemoryImportBatchRepository,
} from '../../src/infrastructure/memory/memoryRepositories'
import { MemoryCategoryRepository, MemoryMerchantRepository, MemoryRuleRepository } from '../../src/infrastructure/memory/memoryReferenceRepositories'
import { MemoryUnitOfWork, SequentialIdGenerator, FixedClock } from '../../src/infrastructure/memory/memorySupport'
import { memorySmsInbox } from '../../src/infrastructure/memory/smsInbox'
import { parseBankSms } from '../../src/infrastructure/import/bankSmsParser'
import type { Category, ClassificationRule, ImportBatch, Merchant, SourceRecord, Transaction } from '../../src/domain/entities/types'
import { recordAsync, type GoldenCase } from './goldenKit'

/**
 * رسايل البنك: القراية من الجوال · صندوق الرسايل · شاشة المراجعة و«سجّل الكل» (OVERRIDES §36).
 * ⚠️ رسايل وهمية بالكامل (أشكال رسايل حقيقية بأرقام وأسماء مخترعة، زي `sms.json`).
 * المراجعة بتمشي على خط استيراد الكشف الحقيقي، فمنع التكرار والتصنيف هنا نفس `importFlow.json`.
 */

const NOW = '2026-09-22T10:00:00.000Z'

const cats: Category[] = [
  { id: 'food', parentId: null, name: 'أكل', iconKey: 'chef-hat', lightColor: '#a4451f', darkColor: '#f0a68c', active: true, order: 1, groupKey: 'food' },
  { id: 'shopping', parentId: null, name: 'تسوق', iconKey: 'shopping-basket', lightColor: '#6b1fa4', darkColor: '#c08cf0', active: true, order: 2, groupKey: 'personal' },
  { id: 'old-hidden', parentId: null, name: 'قديم مخفي', iconKey: 'tag', lightColor: '#123456', darkColor: '#654321', active: false, order: 3 },
]
const merchants: Merchant[] = [
  { id: 'm-cafe', displayName: 'TEST CAFE', normalizedName: 'test cafe', verifiedCategoryId: 'food' },
  { id: 'm-mart', displayName: 'TEST MART', normalizedName: 'test mart', aliases: ['mart alias'] },
]
const rules: ClassificationRule[] = [
  { id: 'r-store', priority: 1, matchText: 'store', matchMode: 'contains', categoryId: 'shopping', enabled: true },
]

const RECEIVED = '2026-09-18T10:00:00Z'
const inbox: QueuedSms[] = [
  { id: 'q-1', sender: 'TESTBANK', receivedAt: RECEIVED, body: 'شراء PoS\nعبر1111;مدى\nبـSR 24\nلـTEST STORE\n26/9/18 09:35' },
  { id: 'q-2', sender: 'TESTBANK', receivedAt: '2026-09-17T08:00:00Z', body: 'شراء انترنت بـSR 35.62\nعبر1111;مدى\nلـTEST CAFE\n18:57 16/9/26' },
  { id: 'q-3', sender: 'TESTBANK', receivedAt: RECEIVED, body: 'حوالة محلية واردة\nمن:TEST PERSON\nبـSR 1000\nإلى:1111\n26/9/18' },
  { id: 'q-4', sender: 'TESTBANK', receivedAt: RECEIVED, body: 'ننصح بعدم مشاركة الرمز لحمايتك من الاحتيال\nالرمز:111111\nمبلغ:SAR 35.62' },
  { id: 'q-5', sender: 'TESTBANK', receivedAt: RECEIVED, body: 'دفع\nعبر:1111;مدى\nبـSR 50\nلـTEST MART\n16:47 17/9/26' },
  { id: 'q-6', sender: 'TESTBANK', receivedAt: RECEIVED, body: 'شراء 25 EGP 2026-09-10' },
  { id: 'q-7', sender: 'OTHERBANK', receivedAt: RECEIVED, body: 'سحب\nبـSR 300\n26/9/15' },
]

/** عملية موجودة من كشف قديم لنفس الحساب بنفس تاريخ ومبلغ رسالة `q-5` ⇒ «شبيه» مش جديد. */
const statementTxn: Transaction = {
  id: 't-statement', occurredAt: '2026-09-17', datePrecision: 'day', sourceOrder: 1,
  economicKind: 'purchase', economicKindConfirmed: false, observedDirection: 'out', amountMinor: 5_000, currency: 'SAR',
  categoryConfirmed: false, excludedFromBudget: false, reviewState: 'suggested', isCashTagged: false,
  rawMerchantName: 'MART BRANCH 7', createdAt: '2026-09-18T00:00:00.000Z', updatedAt: '2026-09-18T00:00:00.000Z',
}

const statementRecord: SourceRecord = {
  id: 'sr-statement', batchId: 'b-statement', accountIdentity: 'البنك الأساسي', sourceReference: null,
  sourceHash: 'hash-statement', originalRowIndex: 1, rawLine: 'raw-statement', transactionId: 't-statement', matchingState: 'new', reason: 'seed',
}
const statementBatch: ImportBatch = {
  id: 'b-statement', sourceType: 'csv_preview', fileHash: 'file-statement', fileName: 'old.csv', importedAt: '2026-09-18T00:00:00.000Z',
  state: 'committed', counts: { total: 1, imported: 1, duplicates: 0, similar: 0, conflicts: 0, invalid: 0 },
}

async function readBankSmsCases() {
  const cases: GoldenCase[] = []
  const messages: BankSmsMessage[] = inbox.map(({ sender, receivedAt, body }) => ({ sender, receivedAt, body }))
  const reads: { from: string; to: string; senders: string[]; truncated?: boolean }[] = [
    { from: '2026-09-01', to: '2026-09-30', senders: ['TESTBANK'], truncated: true },
    // 366 يوم بالظبط مسموح، و367 لأ
    { from: '2025-09-29', to: '2026-09-30', senders: ['TESTBANK'] },
    { from: '2025-09-28', to: '2026-09-30', senders: ['TESTBANK'] },
    { from: '2026-09-31', to: '2026-10-01', senders: ['TESTBANK'] },
    { from: '2026-10-01', to: '2026-09-01', senders: ['TESTBANK'] },
    { from: '2026-09-01', to: '2026-09-30', senders: [] },
    { from: '2026-09-01', to: '2026-09-30', senders: Array.from({ length: 11 }, (_, i) => `BANK${i}`) },
    { from: '2026-09-01', to: '2026-09-30', senders: ['TESTBANK', '   '] },
    { from: '2026-09-01', to: '2026-09-30', senders: ['B'.repeat(51)] },
  ]
  for (const r of reads) {
    cases.push(await recordAsync({ kind: 'read', request: r, messages }, async () => {
      const port: BankSmsPort = { available: true, read: async () => ({ messages, truncated: r.truncated ?? false }) }
      const read = makeReadBankSms(port, parseBankSms)
      return await read.read({ from: r.from, to: r.to, senders: r.senders })
    }))
  }
  for (const body of [inbox[0]!.body, 'شراء 25 SAR عند محل تجريبي بتاريخ 2026-09-10', 'مجرد كلام مش رسالة بنك']) {
    cases.push(await recordAsync({ kind: 'paste', body }, async () => makeReadBankSms({ available: false, read: async () => ({ messages: [], truncated: false }) }, parseBankSms).paste(body)))
  }
  return cases
}

type InboxAction =
  | { kind: 'refresh' | 'disable' }
  | { kind: 'enable'; senders: string[] }
  | { kind: 'dismiss'; ids: string[] }
  | { kind: 'imported'; items: { id: string; lineNumber: number }[]; confirmed: number[] }

async function manageSmsInboxCases() {
  const cases: GoldenCase[] = []
  const sequences: InboxAction[][] = [
    [{ kind: 'refresh' }],
    [{ kind: 'enable', senders: [' TESTBANK ', 'TESTBANK', '', 'OTHERBANK'] }, { kind: 'disable' }],
    [{ kind: 'enable', senders: ['  ', ''] }],
    [{ kind: 'enable', senders: Array.from({ length: 11 }, (_, i) => `BANK${i}`) }],
    [{ kind: 'enable', senders: ['B'.repeat(51)] }],
    [{ kind: 'dismiss', ids: ['q-4', 'q-6', 'q-ghost'] }],
    // المؤكد بس اللي بيتشال — السطر 2 مش في المؤكد فرسالته بتفضل
    [{ kind: 'imported', items: [{ id: 'q-1', lineNumber: 1 }, { id: 'q-2', lineNumber: 2 }, { id: 'q-3', lineNumber: 3 }], confirmed: [1, 3] }, { kind: 'refresh' }],
    [{ kind: 'imported', items: [{ id: 'q-1', lineNumber: 1 }], confirmed: [] }, { kind: 'refresh' }],
  ]
  for (const steps of sequences) {
    cases.push(await recordAsync({ inbox, steps }, async () => {
      const manage = makeManageSmsInbox(memorySmsInbox(inbox, true), parseBankSms)
      const out: unknown[] = []
      for (const step of steps) {
        switch (step.kind) {
          case 'refresh': out.push(await manage.refresh()); break
          case 'enable': out.push(await manage.enable(step.senders)); break
          case 'disable': out.push(await manage.disable()); break
          case 'dismiss': out.push(await manage.dismiss(step.ids)); break
          case 'imported': await manage.imported(step.items, step.confirmed); out.push(null); break
        }
      }
      return { available: manage.available, steps: out }
    }))
  }
  return cases
}

type ReviewStep =
  | { kind: 'load' | 'disable' }
  | { kind: 'enable'; senders: string[] }
  | { kind: 'dismiss'; ids: string[] }
  | { kind: 'recordAll'; categories: [number, string][]; includeSimilar: number[] }
  | { kind: 'remember'; merchantName: string; categoryId: string; direction: 'in' | 'out'; contributeFails?: boolean }

async function reviewSmsInboxCases() {
  const cases: GoldenCase[] = []
  const target = { walletId: 'w-bank', accountIdentity: 'البنك الأساسي' }

  async function run(steps: ReviewStep[], opts: { preRecorded?: string[]; withContribute?: boolean } = {}) {
    cases.push(await recordAsync({ inbox, categories: cats, merchants, rules, statementTxn, statementRecord, statementBatch, target, steps, ...opts }, async () => {
      const txns = new MemoryTransactionRepository()
      await txns.saveMany([statementTxn])
      const sources = new MemorySourceRecordRepository()
      await sources.saveMany([statementRecord])
      const batches = new MemoryImportBatchRepository()
      await batches.save(statementBatch)
      const merchantRepo = new MemoryMerchantRepository(merchants)
      const ids = new SequentialIdGenerator()
      const importer = makeImportStatement({
        txns, sources, batches, merchants: merchantRepo,
        categories: new MemoryCategoryRepository(cats), rules: new MemoryRuleRepository(rules),
        uow: new MemoryUnitOfWork([txns, sources, batches]), ids, clock: new FixedClock(NOW),
      })
      // رسايل اتسجلت قبل كده بس شيلها من الصندوق ضاع ⇒ لازم ترجع «موجودة فعلًا» مش جديدة
      if (opts.preRecorded?.length) {
        const rows = inbox.filter((m) => opts.preRecorded!.includes(m.id)).flatMap((m, i) => {
          const parsed = parseBankSms(m, i + 1)
          return parsed.ok ? [parsed.row] : []
        })
        const request = { fileName: 'earlier-sms.json', content: JSON.stringify(rows), accountIdentity: target.accountIdentity, sourceType: 'sms' as const, walletId: target.walletId, parsedRows: rows, schema: 'sms' as const }
        await importer.commit(request, await importer.preview(request))
      }
      const contributed: unknown[] = []
      let failContribute = false
      const review = makeReviewSmsInbox({
        inbox: makeManageSmsInbox(memorySmsInbox(inbox, true), parseBankSms),
        importer, merchants: merchantRepo, categories: new MemoryCategoryRepository(cats), ids,
        contribute: opts.withContribute
          ? async (transaction, categoryId) => {
              if (failContribute) throw new Error('الشبكة مش متاحة')
              contributed.push({ transaction, categoryId })
            }
          : undefined,
      })
      const summarize = (v: Awaited<ReturnType<typeof review.load>>) => ({ ...v, categories: v.categories.map((c) => c.id) })
      const out: unknown[] = []
      for (const step of steps) {
        switch (step.kind) {
          case 'load': out.push(summarize(await review.load(target))); break
          case 'enable': out.push(summarize(await review.enable(step.senders, target))); break
          case 'disable': out.push(summarize(await review.disable(target))); break
          case 'dismiss': out.push(summarize(await review.dismiss(step.ids, target))); break
          case 'recordAll': out.push(await review.recordAll({ categories: new Map(step.categories), includeSimilar: step.includeSimilar })); break
          case 'remember':
            failContribute = !!step.contributeFails
            out.push({ remembered: await review.remember(step.merchantName, step.categoryId, step.direction) })
            break
        }
      }
      return {
        available: review.available,
        steps: out,
        contributed,
        storedTransactions: [...txns.snapshot().values()].map((t) => ({
          id: t.id, occurredAt: t.occurredAt, amountMinor: t.amountMinor, observedDirection: t.observedDirection,
          categoryId: t.categoryId ?? null, categoryConfirmed: t.categoryConfirmed, rawMerchantName: t.rawMerchantName ?? null, walletId: t.walletId ?? null,
        })),
        storedMerchants: await merchantRepo.listAll(),
      }
    }))
  }

  await run([{ kind: 'load' }])
  // «سجّل الكل» من غير ما يختار الشبيه ⇒ الشبيه بيفضل في الصندوق، واللي اتسجل بيتشال
  await run([{ kind: 'load' }, { kind: 'recordAll', categories: [[1, 'food']], includeSimilar: [] }, { kind: 'load' }])
  await run([{ kind: 'load' }, { kind: 'recordAll', categories: [], includeSimilar: [5] }, { kind: 'load' }])
  await run([{ kind: 'load' }, { kind: 'recordAll', categories: [], includeSimilar: [] }], { preRecorded: ['q-1', 'q-3'] })
  await run([{ kind: 'recordAll', categories: [], includeSimilar: [] }])
  await run([{ kind: 'dismiss', ids: ['q-1', 'q-2', 'q-3', 'q-5', 'q-7'] }, { kind: 'recordAll', categories: [], includeSimilar: [] }])
  await run([{ kind: 'enable', senders: ['TESTBANK'] }, { kind: 'disable' }])
  await run([
    { kind: 'remember', merchantName: '  TEST   STORE ', categoryId: 'shopping', direction: 'out' },
    { kind: 'remember', merchantName: 'mart alias', categoryId: 'food', direction: 'in' },
    { kind: 'remember', merchantName: ' 1234 ', categoryId: 'food', direction: 'out' },
    { kind: 'remember', merchantName: 'TEST CAFE', categoryId: 'shopping', direction: 'out', contributeFails: true },
  ], { withContribute: true })
  await run([{ kind: 'remember', merchantName: 'TEST PERSON', categoryId: 'food', direction: 'out' }])
  return cases
}

export async function smsFlowGolden() {
  return {
    readBankSms: await readBankSmsCases(),
    manageSmsInbox: await manageSmsInboxCases(),
    reviewSmsInbox: await reviewSmsInboxCases(),
  }
}
