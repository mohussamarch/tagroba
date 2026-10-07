import { makeImportStatement } from '../../src/application/useCases/importStatement'
import type { ImportPreview, ImportRequest } from '../../src/application/useCases/importTypes'
import {
  MemoryTransactionRepository,
  MemorySourceRecordRepository,
  MemoryImportBatchRepository,
} from '../../src/infrastructure/memory/memoryRepositories'
import { MemoryCategoryRepository, MemoryMerchantRepository, MemoryRuleRepository } from '../../src/infrastructure/memory/memoryReferenceRepositories'
import { MemoryUnitOfWork, SequentialIdGenerator, FixedClock } from '../../src/infrastructure/memory/memorySupport'
import { hashContent, importFingerprint } from '../../src/domain/dedupe'
import type { ParsedRow } from '../../src/infrastructure/import/schemas'
import type { Category, ClassificationRule, Merchant, ImportBatch, SourceRecord, Transaction } from '../../src/domain/entities/types'
import { recordAsync } from './goldenKit'

/**
 * تدفق الاستيراد كامل (معاينة → التزام) — أكبر حالة استخدام في ملفات المرجع.
 * الحالات مكتوبة بالإيد مش عشوائية: كل درجة من درجات منع التكرار الخمس (spec/05)
 * وكل خطأ التزام ليهم حالة باسمها. ⚠️ بيانات وهمية بالكامل.
 */

const HEADER = 'date,name,amount,type,source,reference'
const LEGACY_HEADER = 'التاريخ,مدين,دائن,الرصيد,التاجر,التصنيف,نوع العملية,التفاصيل'

const cats: Category[] = [
  { id: 'food', parentId: null, name: 'أكل', iconKey: 'chef-hat', lightColor: '#a4451f', darkColor: '#f0a68c', active: true, order: 1, groupKey: 'food' },
  { id: 'shopping', parentId: null, name: 'تسوق', iconKey: 'shopping-basket', lightColor: '#6b1fa4', darkColor: '#c08cf0', active: true, order: 2, groupKey: 'personal' },
  { id: 'telecom', parentId: null, name: 'اتصالات', iconKey: 'wifi', lightColor: '#1f5aa4', darkColor: '#8cbcf0', active: true, order: 3, groupKey: 'home' },
]
const merchants: Merchant[] = [
  { id: 'm-amazon', displayName: 'أمازون', normalizedName: 'امازون', aliases: ['amazon'], verifiedCategoryId: 'shopping' },
  { id: 'm-stc', displayName: 'STC', normalizedName: 'stc' },
]
const rules: ClassificationRule[] = [
  { id: 'r-stc', priority: 1, matchText: 'stc', matchMode: 'contains', categoryId: 'telecom', enabled: true },
  { id: 'r-coffee', priority: 2, matchText: 'قهوة', matchMode: 'contains', categoryId: 'food', enabled: false },
]

/** عملية موجودة من استيراد سابق — الحقول اللي منع التكرار بيقرأها بس هي المهمة. */
function seedTxn(id: string, date: string, amountMinor: number, direction: 'in' | 'out', merchant: string, balance?: number, originalAmountMinor?: number): Transaction {
  const t: Transaction = {
    id, occurredAt: date, datePrecision: 'day', sourceOrder: 1,
    economicKind: 'unclassified', economicKindConfirmed: false,
    observedDirection: direction, amountMinor, currency: 'SAR',
    categoryConfirmed: false, excludedFromBudget: false, reviewState: 'suggested',
    isCashTagged: false, rawMerchantName: merchant,
    createdAt: '2026-08-01T00:00:00.000Z', updatedAt: '2026-08-01T00:00:00.000Z',
  }
  if (balance !== undefined) t.statedBalanceMinor = balance
  if (originalAmountMinor !== undefined) t.originalAmountMinor = originalAmountMinor
  return t
}

function seedRecord(id: string, batchId: string, accountIdentity: string, reference: string | null, rowIndex: number, transactionId: string | null): SourceRecord {
  return {
    id, batchId, accountIdentity, sourceReference: reference,
    sourceHash: `hash-${id}`, originalRowIndex: rowIndex, rawLine: `raw-${id}`,
    transactionId, matchingState: 'new', reason: 'seed',
  }
}

function seedBatch(id: string, fileHash: string, state: 'staged' | 'committed' | 'reverted'): ImportBatch {
  return {
    id, sourceType: 'csv_preview', fileHash, fileName: 'old.csv', importedAt: '2026-08-01T00:00:00.000Z',
    state, counts: { total: 2, imported: 2, duplicates: 0, similar: 0, conflicts: 0, invalid: 0 },
  }
}

interface Seed { transactions: Transaction[]; sourceRecords: SourceRecord[]; batches: ImportBatch[] }
interface Action {
  kind: 'preview' | 'commit'
  selected?: number[]
  chosenCategories?: Record<string, string>
  /** محتوى مختلف وقت الالتزام — لتغطية «بيانات الاستيراد اتغيرت». */
  commitContent?: string
}

function previewJson(p: ImportPreview) {
  return {
    fileName: p.fileName, fileHash: p.fileHash, schema: p.schema, accountIdentity: p.accountIdentity,
    previousBatch: p.previousBatch,
    lines: p.lines.map((l) => ({
      lineNumber: l.row.lineNumber, date: l.row.date, amountMinor: l.row.amountMinor,
      direction: l.row.direction, merchantName: l.row.merchantName, reference: l.row.reference,
      statedBalanceMinor: l.row.statedBalanceMinor ?? null,
      state: l.state, reason: l.reason,
      matchedTransactionId: l.matchedTransactionId ?? null,
      categoryId: l.categoryId ?? null, categoryReason: l.categoryReason, categorySource: l.categorySource ?? null,
      selectedByDefault: l.selectedByDefault,
    })),
    errors: p.errors, counts: p.counts, impact: p.impact,
  }
}

export async function importFlowGolden() {
  const cases = []

  async function run(seed: Seed, request: ImportRequest, action: Action) {
    // المراجع جوه المدخل عشان ملف المرجع يبقى مكتفيًا بذاته — كوتلن بتبني منه كل حاجة
    const input = { seed, references: { merchants, categories: cats, rules }, request, action }
    cases.push(await recordAsync(input, async () => {
      const txns = new MemoryTransactionRepository()
      await txns.saveMany(seed.transactions)
      const sources = new MemorySourceRecordRepository()
      await sources.saveMany(seed.sourceRecords)
      const batches = new MemoryImportBatchRepository()
      for (const b of seed.batches) await batches.save(b)
      const deps = {
        txns, sources, batches,
        merchants: new MemoryMerchantRepository(merchants),
        categories: new MemoryCategoryRepository(cats),
        rules: new MemoryRuleRepository(rules),
        uow: new MemoryUnitOfWork([txns, sources, batches]),
        ids: new SequentialIdGenerator(),
        clock: new FixedClock('2026-09-22T10:00:00.000Z'),
      }
      const importer = makeImportStatement(deps)
      const preview = await importer.preview(request)
      if (action.kind === 'preview') return { preview: previewJson(preview) }

      const chosen = action.chosenCategories
        ? new Map(Object.entries(action.chosenCategories).map(([k, v]) => [Number(k), v]))
        : undefined
      const commitRequest = action.commitContent ? { ...request, content: action.commitContent } : request
      const batch = await importer.commit(commitRequest, preview, action.selected, chosen)
      return {
        batch,
        storedBatches: batches.all(),
        storedTransactions: txns.all(),
        storedSourceRecords: sources.all(),
      }
    }))
  }

  const account = 'حساب-تجريبي'
  const csv = [
    HEADER,
    '2026-09-05,Amazon,100.50,expense,البنك,REF-1',
    '2026-09-06,STC,55.25,expense,البنك,REF-2',
    '2026-09-07,تحويل وارد,7000.00,income,البنك,REF-3',
    '2026-09-08,قهوة الحي,12.00,expense,البنك,',
    'bad-date,غلط,10,expense,البنك,REF-5',
    '2026-09-09,صفر,0,expense,البنك,REF-6',
  ].join('\n')
  const empty: Seed = { transactions: [], sourceRecords: [], batches: [] }
  const req = (over: Partial<ImportRequest> = {}): ImportRequest => ({
    fileName: 'new.csv', content: csv, accountIdentity: account, sourceType: 'csv_preview', walletId: 'w-bank', ...over,
  })

  // ١. معاينة ملف جديد نظيف: تصنيف بتاجر مؤكد وقاعدة، وصفين غلط
  await run(empty, req(), { kind: 'preview' })

  // ٢. التزام بالاختيار الافتراضي
  await run(empty, req(), { kind: 'commit' })

  // ٣. التزام باختيار صريح + تصنيف اختاره المستخدم لسطر (بيتحفظ مؤكد)
  await run(empty, req(), { kind: 'commit', selected: [2, 3], chosenCategories: { '3': 'telecom' } })

  // ٤. درجات التكرار ضد الموجود: مكرر بالمرجع · تعارض (نفس المرجع بتفاصيل مختلفة) · متشابه (من غير مرجع)
  const dedupeSeed: Seed = {
    transactions: [
      seedTxn('t-old-1', '2026-09-06', 5525, 'out', 'STC'),
      seedTxn('t-old-2', '2026-09-05', 10050, 'out', 'Amazon'),
      seedTxn('t-old-3', '2026-09-08', 1200, 'out', 'قهوة الحي'),
    ],
    sourceRecords: [
      seedRecord('s-old-1', 'b-old', account, 'REF-2', 11, 't-old-1'),
      seedRecord('s-old-2', 'b-old', account, 'REF-1-OLD', 12, 't-old-2'),
      seedRecord('s-old-3', 'b-old', account, null, 13, 't-old-3'),
    ],
    batches: [seedBatch('b-old', 'some-other-hash', 'committed')],
  }
  const conflictCsv = [
    HEADER,
    '2026-09-06,STC,55.25,expense,البنك,REF-2',
    '2026-09-05,Amazon,999.99,expense,البنك,REF-1-OLD',
    '2026-09-08,قهوة الحي,12.00,expense,البنك,',
    '2026-09-20,جديد خالص,80.00,expense,البنك,REF-9',
  ].join('\n')
  await run(dedupeSeed, req({ content: conflictCsv }), { kind: 'preview' })

  // (أرقام السطور: الترويسة سطر 1 والبيانات من 2)
  // ٥. اختيار سطر متعارض للالتزام ⇒ رفض قبل أي كتابة
  await run(dedupeSeed, req({ content: conflictCsv }), { kind: 'commit', selected: [3, 5] })

  // ٦. اختيار المتشابه صراحةً مع الجديد ⇒ بيتكتبوا
  await run(dedupeSeed, req({ content: conflictCsv }), { kind: 'commit', selected: [4, 5] })

  // ٧. اختيار مكرر صراحةً ⇒ «مش قابلة للإضافة»
  await run(dedupeSeed, req({ content: conflictCsv }), { kind: 'commit', selected: [2] })

  // ٨. تغيير المحتوى بعد المعاينة ⇒ «بيانات الاستيراد اتغيرت»
  await run(empty, req(), { kind: 'commit', commitContent: csv + '\n2026-09-10,زيادة,9.99,expense,البنك,REF-7' })

  // ٩. اختيار رقم سطر مش موجود ⇒ «فيه بيانات اتغيرت بعد المعاينة»
  await run(empty, req(), { kind: 'commit', selected: [999] })

  // ١٠. نفس الملف متسجل ببصمته الحالية وكله مكرر ⇒ الالتزام بيرجّع الدفعة القديمة
  const sameCsv = [HEADER, '2026-09-06,STC,55.25,expense,البنك,REF-2'].join('\n')
  const sameSeed: Seed = {
    transactions: [seedTxn('t-old-1', '2026-09-06', 5525, 'out', 'STC')],
    sourceRecords: [seedRecord('s-old-1', 'b-prev', account, 'REF-2', 2, 't-old-1')],
    batches: [seedBatch('b-prev', importFingerprint(sameCsv, account), 'committed')],
  }
  await run(sameSeed, req({ content: sameCsv, fileName: 'same.csv' }), { kind: 'commit' })

  // ١١. بصمة قديمة (hashContent من غير هوية الحساب) وفيه صفوف جديدة ⇒ الدفعة القديمة بتبان والاستيراد بيكمل
  const partialCsv = [HEADER, '2026-09-06,STC,55.25,expense,البنك,REF-2', '2026-09-21,مكمل,44.00,expense,البنك,REF-8'].join('\n')
  const legacySeed: Seed = {
    transactions: [seedTxn('t-old-1', '2026-09-06', 5525, 'out', 'STC')],
    sourceRecords: [seedRecord('s-old-1', 'b-legacy', account, 'REF-2', 2, 't-old-1')],
    batches: [seedBatch('b-legacy', hashContent(partialCsv), 'committed')],
  }
  await run(legacySeed, req({ content: partialCsv, fileName: 'partial.csv' }), { kind: 'commit' })

  // ١٢. المخطط القديم بعمود رصيد: تكرار بمفتاح الرصيد (من غير مرجع) — سجل موجود بيبلع صف واحد بس،
  //     وتصنيف من عمود الملف، والمبلغ الأصلي المتخزن (OVERRIDES §32) هو اللي بيتطابق
  const legacyCsv = [
    LEGACY_HEADER,
    '2026/09/05,96.47,0,4741.36,أمازون,تسوق,شراء,تفاصيل أولى',
    '2026/09/05,96.47,0,4741.36,أمازون,تسوق,شراء,تفاصيل أولى',
    '2026/09/12,50.00,0,4691.36,محل تاني,أكل,شراء,تفاصيل تانية',
  ].join('\n')
  const balanceSeed: Seed = {
    transactions: [seedTxn('t-bal-1', '2026-09-05', 9000, 'out', 'أمازون', 474136, 9647)],
    sourceRecords: [seedRecord('s-bal-1', 'b-old', account, null, 21, 't-bal-1')],
    batches: [seedBatch('b-old', 'other-hash', 'committed')],
  }
  await run(balanceSeed, req({ content: legacyCsv, fileName: 'legacy.csv' }), { kind: 'preview' })

  // ١٣. مسار الصفوف الجاهزة (PDF): القارئ بيتخطى وبقية الخط زي ما هو
  const pdfRows: ParsedRow[] = [
    { lineNumber: 1, date: '2026-09-15', amountMinor: 30000, direction: 'out', merchantName: 'أمازون', reference: 'PDF-1', sourceName: 'الراجحي', description: 'شراء أمازون', raw: 'pdf-row-1', statedBalanceMinor: 400000 },
    { lineNumber: 2, date: '2026-09-16', amountMinor: 150000, direction: 'in', merchantName: '', reference: 'PDF-2', sourceName: 'الراجحي', description: 'حوالة واردة', raw: 'pdf-row-2', statedBalanceMinor: 550000 },
  ]
  await run(empty, req({ fileName: 'statement.pdf', content: 'pdf-binary-stand-in', sourceType: 'pdf_alrajhi', parsedRows: pdfRows, schema: 'alrajhi_pdf' }), { kind: 'commit' })

  // ١٤. مصدر رسايل: مفيش مطابقة بمفتاح الرصيد، والسجل الموجود من رسالة بيتعلم smsSource
  const smsCsv = [HEADER, '2026-09-18,مطعم,35.00,expense,رسالة,'].join('\n')
  const smsSeed: Seed = {
    transactions: [seedTxn('t-sms-1', '2026-09-18', 3500, 'out', 'مطعم')],
    sourceRecords: [seedRecord('s-sms-1', 'b-old', account, 'SMS:abc123', 1, 't-sms-1')],
    batches: [seedBatch('b-old', 'other-hash', 'committed')],
  }
  await run(smsSeed, req({ content: smsCsv, fileName: 'sms.csv', sourceType: 'sms' }), { kind: 'preview' })

  return { importFlow: cases }
}
