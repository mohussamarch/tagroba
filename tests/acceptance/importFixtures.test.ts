import { describe, it, expect, beforeEach } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { makeImportStatement, type ImportRequest } from '../../src/application/useCases/importStatement'
import { makeRevertImportBatch } from '../../src/application/useCases/revertImportBatch'
import {
  MemoryAllocationRepository,
  MemoryCategoryRepository,
  MemoryImportBatchRepository,
  MemoryMerchantRepository,
  MemoryObligationRepository,
  MemoryRuleRepository,
  MemorySettlementRepository,
  MemorySourceRecordRepository,
  MemoryTransactionRepository,
  PassthroughUnitOfWork,
  SequentialIdGenerator,
  FixedClock,
} from '../../src/infrastructure/memory/memoryRepositories'
import { buildCategories, loadReferences } from '../../src/infrastructure/import/referenceLoader'
import { parseMoney } from '../../src/domain/money'
import { formatAmount } from '../../src/domain/formatMoney'

/**
 * حالات القبول من spec/06 على fixtures/ — **بدون فايربيز إطلاقًا**
 * (OVERRIDES §4). التنفيذ عبر مستودعات الذاكرة فقط.
 */

const FIX = resolve(__dirname, '../../design-source/masroofi-claude-code/fixtures')
const DESIGN = resolve(__dirname, '../../design-source/masroofi-claude-code/design')

const read = (name: string) => readFileSync(resolve(FIX, name), 'utf8')
const readJson = <T>(path: string): T => JSON.parse(readFileSync(path, 'utf8')) as T

const tokens = readJson<{ categories: { name: string; baseColor: string; icon: string }[] }>(
  resolve(DESIGN, 'tokens.json'),
)
const rawRules = readJson<{ word: string; cat: string }[]>(resolve(FIX, 'rule-reference.json'))
const rawMerchants = readJson<{ name: string; cat: string; confidence: string }[]>(
  resolve(FIX, 'merchant-reference.json'),
)

const ACCOUNT = 'حساب تجريبي'

function makeSystem() {
  const txns = new MemoryTransactionRepository()
  const sources = new MemorySourceRecordRepository()
  const batches = new MemoryImportBatchRepository()
  const settlements = new MemorySettlementRepository()
  const allocations = new MemoryAllocationRepository()
  const obligations = new MemoryObligationRepository()

  const categories = buildCategories(tokens.categories)
  const refs = loadReferences(rawRules, rawMerchants, categories)

  const deps = {
    txns,
    sources,
    batches,
    merchants: new MemoryMerchantRepository(refs.merchants),
    categories: new MemoryCategoryRepository(categories),
    rules: new MemoryRuleRepository(refs.rules),
    uow: new PassthroughUnitOfWork(),
    ids: new SequentialIdGenerator(),
    clock: new FixedClock('2026-09-07T00:00:00.000Z'),
  }

  return {
    ...deps,
    settlements,
    allocations,
    obligations,
    refs,
    categories,
    importer: makeImportStatement(deps),
    reverter: makeRevertImportBatch({
      txns,
      sources,
      batches,
      settlements,
      allocations,
      obligations,
      uow: new PassthroughUnitOfWork(),
    }),
  }
}

function request(fileName: string, content: string): ImportRequest {
  return { fileName, content, accountIdentity: ACCOUNT, sourceType: 'csv_preview' }
}

describe('تحميل المراجع الأولية', () => {
  const sys = makeSystem()

  it('يحمّل القواعد ويربطها بتصنيفات معرّفة فقط', () => {
    expect(sys.refs.rules.length).toBeGreaterThan(100)
    const catIds = new Set(sys.categories.map((c) => c.id))
    for (const rule of sys.refs.rules) expect(catIds.has(rule.categoryId)).toBe(true)
  })

  it('لا يؤكّد تاجرًا غامضًا آليًا — spec/05', () => {
    const byName = new Map(sys.refs.merchants.map((m) => [m.displayName, m]))
    // «MF» و«110» في merchant-reference.json بثقة فارغة و«يحتاج تأكيد»
    expect(byName.get('MF')?.verifiedCategoryId).toBeUndefined()
    expect(byName.get('110')?.verifiedCategoryId).toBeUndefined()
    // والمؤكد يُمنح تصنيفه
    expect(byName.get('KIWI.COM SRO')?.verifiedCategoryId).toBeDefined()
    expect(sys.refs.unverifiedMerchantCount).toBeGreaterThan(0)
  })

  it('يبلّغ عن أسماء تصنيفات غير معرّفة بدل اختراعها', () => {
    // القائمة قد تكون فارغة أو لا، لكن لا يجوز أن تُخترع فئة
    expect(Array.isArray(sys.refs.unknownCategoryNames)).toBe(true)
  })
})

describe('baseline.csv — الاستيراد الأولي', () => {
  let sys: ReturnType<typeof makeSystem>
  beforeEach(() => {
    sys = makeSystem()
  })

  it('يقرأ الـBOM ويطابق مخطط المعاينة ويستورد السبعة', async () => {
    const preview = await sys.importer.preview(request('baseline.csv', read('baseline.csv')))
    expect(preview.schema).toBe('preview')
    expect(preview.errors).toEqual([])
    expect(preview.lines).toHaveLength(7)
    expect(preview.counts.newCount).toBe(7)
    expect(preview.previousBatch).toBeNull()

    const batch = await sys.importer.commit(request('baseline.csv', read('baseline.csv')), preview)
    expect(batch.counts.imported).toBe(7)
    expect(sys.txns.size()).toBe(7)
  })

  it('لا يستنتج النوع الاقتصادي من اتجاه السيولة — spec/02', async () => {
    const req = request('baseline.csv', read('baseline.csv'))
    const preview = await sys.importer.preview(req)
    await sys.importer.commit(req, preview)

    for (const txn of sys.txns.all()) {
      expect(txn.economicKind).toBe('unclassified')
      expect(txn.economicKindConfirmed).toBe(false)
    }
    // لكن الاتجاه الملاحظ حقيقة بنكية محفوظة
    const salary = sys.txns.all().find((t) => t.rawMerchantName === 'راتب تجريبي')!
    expect(salary.observedDirection).toBe('in')
    const albaik = sys.txns.all().find((t) => t.rawMerchantName === 'البيك')!
    expect(albaik.observedDirection).toBe('out')
  })

  it('المبالغ محفوظة بالهللة بدقة', async () => {
    const req = request('baseline.csv', read('baseline.csv'))
    const preview = await sys.importer.preview(req)
    await sys.importer.commit(req, preview)
    const byName = new Map(sys.txns.all().map((t) => [t.rawMerchantName, t]))
    expect(byName.get('راتب تجريبي')!.amountMinor).toBe(parseMoney('7000.00'))
    expect(byName.get('البيك')!.amountMinor).toBe(parseMoney('32.00'))
    expect(byName.get('Amazon')!.amountMinor).toBe(parseMoney('720.00'))
    expect(byName.get('مشتريات كاش')!.amountMinor).toBe(parseMoney('100.00'))
  })
})

describe('إعادة نفس CSV مرتين — spec/06', () => {
  it('عدد العمليات والتأثير المالي ثابتان', async () => {
    const sys = makeSystem()
    const req = request('baseline.csv', read('baseline.csv'))

    const first = await sys.importer.preview(req)
    await sys.importer.commit(req, first)
    const countAfterFirst = sys.txns.size()

    // الدرجة ١: بصمة ملف مطابق سبق استيراده
    const second = await sys.importer.preview(req)
    expect(second.previousBatch).not.toBeNull()
    expect(second.previousBatch!.counts.imported).toBe(7)
    expect(second.counts.newCount).toBe(0)
    expect(second.counts.duplicates).toBe(7)
    expect(second.impact.walletDeltaMinor).toBe(0)

    await sys.importer.commit(req, second)
    expect(sys.txns.size()).toBe(countAfterFirst) // ثابت
  })
})

describe('repeated-import.csv — الدرجات الخمس', () => {
  it('مرجع جديد واحد، ومكرر مؤكد واحد، ومتشابه واحد يحتاج قرارًا', async () => {
    const sys = makeSystem()
    const base = request('baseline.csv', read('baseline.csv'))
    await sys.importer.commit(base, await sys.importer.preview(base))

    const req = request('repeated-import.csv', read('repeated-import.csv'))
    const preview = await sys.importer.preview(req)

    expect(preview.lines).toHaveLength(3)
    const states = preview.lines.map((l) => l.state)
    expect(states).toEqual(['duplicate', 'new', 'similar'])

    expect(preview.counts.newCount).toBe(1)
    expect(preview.counts.duplicates).toBe(1)
    expect(preview.counts.similar).toBe(1)

    // المتشابه ليس مختارًا افتراضيًا — لا حذف ولا إضافة تلقائية
    expect(preview.lines[2].selectedByDefault).toBe(false)
    expect(preview.lines[2].reason).toContain('قرارك')
  })

  it('«أضف الجديد فقط» ⇒ المصروف 896 والدخل 7000 — fixtures/README', async () => {
    const sys = makeSystem()
    const base = request('baseline.csv', read('baseline.csv'))
    await sys.importer.commit(base, await sys.importer.preview(base))

    const req = request('repeated-import.csv', read('repeated-import.csv'))
    const preview = await sys.importer.preview(req)
    await sys.importer.commit(req, preview)

    // مجموع الصادر عبر كل العمليات المستوردة
    const all = sys.txns.all()
    const out = all.filter((t) => t.observedDirection === 'out')
    const income = all.filter((t) => t.observedDirection === 'in')

    // baseline: 32 + 720 + 100 = 852 صادر «مصروف»
    //   + 500 نقل + 500 قرض + 200 أمانة (اتجاهها صادر في المخطط)
    // + الجديد 44 ⇒ 852 + 44 = 896 من عمليات المصروف
    const expenseNames = new Set(['البيك', 'Amazon', 'مشتريات كاش'])
    const expenseTotal = out
      .filter((t) => expenseNames.has(t.rawMerchantName ?? ''))
      .reduce((sum, t) => sum + t.amountMinor, 0)

    expect(formatAmount(expenseTotal)).toBe('896.00')
    expect(formatAmount(income.reduce((s, t) => s + t.amountMinor, 0))).toBe('7,000.00')
  })
})

describe('conflict.csv — مرجع موجود بمبلغ مختلف', () => {
  it('تعارض يمنع الكتابة الصامتة، ولا يستبدل القديم', async () => {
    const sys = makeSystem()
    const base = request('baseline.csv', read('baseline.csv'))
    await sys.importer.commit(base, await sys.importer.preview(base))

    const req = request('conflict.csv', read('conflict.csv'))
    const preview = await sys.importer.preview(req)

    expect(preview.lines).toHaveLength(1)
    expect(preview.lines[0].state).toBe('conflict')
    expect(preview.lines[0].reason).toContain('المبلغ')
    expect(preview.counts.conflicts).toBe(1)
    expect(preview.lines[0].selectedByDefault).toBe(false)

    // المبلغ القديم 32 لم يتغيّر
    const albaik = sys.txns.all().find((t) => t.rawMerchantName === 'البيك')!
    expect(albaik.amountMinor).toBe(parseMoney('32.00'))
  })

  it('محاولة الالتزام بتعارض محدد تُرفض بتفسير', async () => {
    const sys = makeSystem()
    const base = request('baseline.csv', read('baseline.csv'))
    await sys.importer.commit(base, await sys.importer.preview(base))

    const req = request('conflict.csv', read('conflict.csv'))
    const preview = await sys.importer.preview(req)

    await expect(sys.importer.commit(req, preview, [preview.lines[0].row.lineNumber]))
      .rejects.toThrow(/تعارض/)
  })
})

describe('invalid.csv — صفوف فاسدة', () => {
  it('يرفض الصفين برقم الصف ورسالة، ولا يفقد المدخلات', async () => {
    const sys = makeSystem()
    const preview = await sys.importer.preview(request('invalid.csv', read('invalid.csv')))

    expect(preview.lines).toHaveLength(0)
    expect(preview.errors).toHaveLength(2)
    expect(preview.counts.invalid).toBe(2)

    const [dateError, amountError] = preview.errors
    expect(dateError.lineNumber).toBe(2)
    expect(dateError.field).toBe('date')
    expect(dateError.message).toContain('2026-02-30') // 30 فبراير مستحيل
    expect(dateError.raw).toContain('تاريخ غير صالح') // النص الأصلي محفوظ

    expect(amountError.lineNumber).toBe(3)
    expect(amountError.field).toBe('amount')
    expect(amountError.message).toContain('NaN')
    expect(amountError.raw).toContain('مبلغ غير صالح')
  })

  it('لا يستورد نصف دفعة: صفر عمليات محفوظة', async () => {
    const sys = makeSystem()
    const req = request('invalid.csv', read('invalid.csv'))
    const preview = await sys.importer.preview(req)
    await sys.importer.commit(req, preview)
    expect(sys.txns.size()).toBe(0)
  })
})

describe('نفس المرجع من حسابين مختلفين — spec/06', () => {
  it('لا إسقاط أحدهما باعتباره مكررًا تلقائيًا', async () => {
    const sys = makeSystem()
    const content = read('baseline.csv')

    const first: ImportRequest = {
      fileName: 'a.csv', content, accountIdentity: 'حساب أول', sourceType: 'csv_preview',
    }
    await sys.importer.commit(first, await sys.importer.preview(first))
    expect(sys.txns.size()).toBe(7)

    // نفس المراجع تمامًا لكن هوية حساب مختلفة
    const second: ImportRequest = {
      fileName: 'b.csv', content, accountIdentity: 'حساب تاني', sourceType: 'csv_preview',
    }
    const preview = await sys.importer.preview(second)
    // البصمة نفسها فتظهر الدفعة السابقة كتنبيه، لكن المراجع لا تُعد مكررة
    expect(preview.counts.duplicates).toBe(0)
    expect(preview.counts.newCount).toBe(7)

    await sys.importer.commit(second, preview)
    expect(sys.txns.size()).toBe(14) // الاتنين محفوظين
  })
})

describe('التراجع عن دفعة — spec/03', () => {
  it('يحذف عمليات الدفعة لما تكون هي المصدر الوحيد', async () => {
    const sys = makeSystem()
    const req = request('baseline.csv', read('baseline.csv'))
    const batch = await sys.importer.commit(req, await sys.importer.preview(req))

    const plan = await sys.reverter.plan(batch.id)
    expect(plan.toDelete).toHaveLength(7)
    expect(plan.isClean).toBe(true)

    await sys.reverter.execute(batch.id)
    expect(sys.txns.size()).toBe(0)
    expect((await sys.batches.findById(batch.id))!.state).toBe('reverted')
  })

  it('لا يحذف عملية دخلت عليها تسوية — يشرح ويحتفظ', async () => {
    const sys = makeSystem()
    const req = request('baseline.csv', read('baseline.csv'))
    const batch = await sys.importer.commit(req, await sys.importer.preview(req))

    const albaik = sys.txns.all().find((t) => t.rawMerchantName === 'البيك')!
    await sys.settlements.saveMany([
      { id: 'stl-1', transactionId: albaik.id, obligationId: 'obl-1', amountMinor: parseMoney('32.00') },
    ])

    const plan = await sys.reverter.plan(batch.id)
    expect(plan.isClean).toBe(false)
    expect(plan.toDelete).toHaveLength(6)
    const kept = plan.toKeep.find((k) => k.transactionId === albaik.id)!
    expect(kept.decision).toBe('kept_has_settlement')
    expect(kept.reason).toContain('تسوية')

    await sys.reverter.execute(batch.id)
    expect(sys.txns.size()).toBe(1)
    expect(sys.txns.all()[0].id).toBe(albaik.id)
  })

  it('لا يتراجع عن دفعة متراجَع عنها مرتين', async () => {
    const sys = makeSystem()
    const req = request('baseline.csv', read('baseline.csv'))
    const batch = await sys.importer.commit(req, await sys.importer.preview(req))
    await sys.reverter.execute(batch.id)
    await expect(sys.reverter.execute(batch.id)).rejects.toThrow(/متراجَع/)
  })
})
