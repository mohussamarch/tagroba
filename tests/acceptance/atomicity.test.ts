import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { makeImportStatement } from '../../src/application/useCases/importStatement'
import type { ImportRequest } from '../../src/application/useCases/importStatement'
import {
  MemoryCategoryRepository,
  MemoryImportBatchRepository,
  MemoryMerchantRepository,
  MemoryRuleRepository,
  MemorySourceRecordRepository,
  MemoryTransactionRepository,
  MemoryUnitOfWork,
  PassthroughUnitOfWork,
  SequentialIdGenerator,
  FixedClock,
} from '../../src/infrastructure/memory/memoryRepositories'
import { buildCategories } from '../../src/infrastructure/import/referenceLoader'
import { makeResumeStagedBatch } from '../../src/application/useCases/resumeStagedBatch'
import type { SourceRecord, Transaction } from '../../src/domain/entities/types'

/**
 * spec/06: «انقطاع أثناء حفظ دفعة ⇒ **صفر أو كامل الدفعة، لا نصفها**».
 *
 * الاختبار يكسر الحفظ عمدًا في منتصف الدفعة (بعد حفظ العمليات وقبل
 * حفظ سجلات المصدر) ثم يتحقق أن **لا شيء** بقي محفوظًا.
 *
 * بدون هذا الاختبار تكون الذرّية ادعاءً لا حقيقة (CLAUDE.md #14).
 */

const FIX = resolve(__dirname, '../../design-source/masroofi-claude-code/fixtures')
const baseline = readFileSync(resolve(FIX, 'baseline.csv'), 'utf8')

/** مستودع سجلات مصدر ينفجر عند الكتابة — يحاكي انقطاع الاتصال. */
class ExplodingSourceRecordRepository extends MemorySourceRecordRepository {
  failNext = true
  override async saveMany(records: readonly SourceRecord[]): Promise<void> {
    if (this.failNext) throw new Error('انقطع الاتصال أثناء حفظ سجلات المصدر')
    return super.saveMany(records)
  }
}

/**
 * مستودع عمليات ينفجر — يحاكي **أخطر لحظة**: الانقطاع بعد كتابة
 * سجلات المصدر وقبل كتابة العمليات.
 */
class ExplodingTransactionRepository extends MemoryTransactionRepository {
  failNext = true
  override async saveMany(transactions: readonly Transaction[]): Promise<void> {
    if (this.failNext) throw new Error('انقطع الاتصال أثناء حفظ العمليات')
    return super.saveMany(transactions)
  }
}

function makeSystem() {
  const txns = new MemoryTransactionRepository()
  const sources = new ExplodingSourceRecordRepository()
  const batches = new MemoryImportBatchRepository()
  const categories = buildCategories([])

  // الذرّية الحقيقية: كل المستودعات الثلاثة داخل نفس وحدة العمل
  const uow = new MemoryUnitOfWork([txns, sources, batches])

  return {
    txns,
    sources,
    batches,
    importer: makeImportStatement({
      txns,
      sources,
      batches,
      merchants: new MemoryMerchantRepository([]),
      categories: new MemoryCategoryRepository(categories),
      rules: new MemoryRuleRepository([]),
      uow,
      ids: new SequentialIdGenerator(),
      clock: new FixedClock('2026-09-07T00:00:00.000Z'),
    }),
  }
}

const request: ImportRequest = {
  fileName: 'baseline.csv',
  content: baseline,
  accountIdentity: 'حساب تجريبي',
  sourceType: 'csv_preview',
}

describe('ذرّية الدفعة — spec/06', () => {
  it('انقطاع في منتصف الحفظ ⇒ صفر عمليات وصفر سجلات وصفر دفعات', async () => {
    const sys = makeSystem()
    const preview = await sys.importer.preview(request)
    expect(preview.counts.newCount).toBe(7)

    await expect(sys.importer.commit(request, preview)).rejects.toThrow(/انقطع الاتصال/)

    // لا نصف دفعة: العمليات اتحفظت قبل الانفجار، والتراجع شالها
    expect(sys.txns.size()).toBe(0)
    expect(sys.sources.all()).toHaveLength(0)
    expect(sys.batches.all()).toHaveLength(0)
  })

  it('إعادة المحاولة بعد نجاح الاتصال تحفظ الدفعة كاملة', async () => {
    const sys = makeSystem()
    const preview = await sys.importer.preview(request)

    await expect(sys.importer.commit(request, preview)).rejects.toThrow()
    expect(sys.txns.size()).toBe(0)

    // الاتصال رجع
    sys.sources.failNext = false
    const batch = await sys.importer.commit(request, preview)

    expect(batch.counts.imported).toBe(7)
    expect(sys.txns.size()).toBe(7)
    expect(sys.sources.all()).toHaveLength(7)
    expect(sys.batches.all()).toHaveLength(1)
    expect(sys.batches.all()[0].state).toBe('committed')
  })

  it('الفشل لا يمس بيانات كانت موجودة قبل الدفعة', async () => {
    const sys = makeSystem()

    // دفعة أولى ناجحة
    sys.sources.failNext = false
    const first = await sys.importer.preview(request)
    await sys.importer.commit(request, first)
    const countBefore = sys.txns.size()
    const idsBefore = sys.txns.all().map((t) => t.id).sort()
    expect(countBefore).toBe(7)

    // دفعة ثانية بملف مختلف تفشل
    const second: ImportRequest = {
      ...request,
      fileName: 'other.csv',
      content: baseline.replace(/DEMO-/g, 'OTHER-'),
    }
    const secondPreview = await sys.importer.preview(second)
    expect(secondPreview.counts.newCount).toBe(7)

    sys.sources.failNext = true
    await expect(sys.importer.commit(second, secondPreview)).rejects.toThrow()

    // القديم سليم بالضبط كما كان، ولا أثر للدفعة الفاشلة
    expect(sys.txns.size()).toBe(countBefore)
    expect(sys.txns.all().map((t) => t.id).sort()).toEqual(idsBefore)
    expect(sys.batches.all()).toHaveLength(1)
  })

  it('اللقطة عميقة: تعديل عملية موجودة يُتراجع عنه أيضًا', async () => {
    const sys = makeSystem()
    sys.sources.failNext = false
    const preview = await sys.importer.preview(request)
    await sys.importer.commit(request, preview)

    const target = sys.txns.all()[0]
    const originalNote = target.note

    const uow = new MemoryUnitOfWork([sys.txns, sys.sources, sys.batches])
    await expect(
      uow.run(async () => {
        await sys.txns.update(target.id, { note: 'ملاحظة وسط عملية هتفشل' })
        throw new Error('فشل بعد التعديل')
      }),
    ).rejects.toThrow(/فشل بعد التعديل/)

    const after = sys.txns.all().find((t) => t.id === target.id)!
    expect(after.note).toBe(originalNote) // رجع لأصله، لا التعديل باقٍ
  })
})

/**
 * بروتوكول علامة الالتزام — ARCHITECTURE.md §10.5.
 *
 * على Firestore لا توجد وحدة عمل حقيقية، فالضمان يأتي من الترتيب:
 * staged → كتابة → committed. أي انقطاع يترك دفعة معلّقة
 * وما تحتها **غير معتمد**، ثم يُنظَّف عند فتح التطبيق.
 */
describe('بروتوكول علامة الالتزام — بديل الذرّية على Firestore', () => {
  /** يحاكي Firestore: بلا تراجع، والكتابة تصل كما هي. */
  function makeFirestoreLike() {
    const txns = new ExplodingTransactionRepository()
    const sources = new MemorySourceRecordRepository()
    const batches = new MemoryImportBatchRepository()
    const importer = makeImportStatement({
      txns,
      sources,
      batches,
      merchants: new MemoryMerchantRepository([]),
      categories: new MemoryCategoryRepository(buildCategories([])),
      rules: new MemoryRuleRepository([]),
      uow: new PassthroughUnitOfWork(), // ← لا تراجع، زي Firestore بالظبط
      ids: new SequentialIdGenerator(),
      clock: new FixedClock('2026-09-07T00:00:00.000Z'),
    })
    const resumer = makeResumeStagedBatch({ txns, sources, batches })
    return { txns, sources, batches, importer, resumer }
  }

  it('الانقطاع يترك دفعة staged لا committed', async () => {
    const sys = makeFirestoreLike()
    const preview = await sys.importer.preview(request)
    await expect(sys.importer.commit(request, preview)).rejects.toThrow(/انقطع الاتصال/)

    const all = sys.batches.all()
    expect(all).toHaveLength(1)
    expect(all[0].state).toBe('staged') // مش committed

    // بلا وحدة عمل، سجلات المصدر فعلًا اتكتبت — لكنها غير معتمدة.
    // وهي بالظبط اللي بتخلي التنظيف ممكن: الفهرس موجود قبل العمليات.
    expect(sys.sources.all()).toHaveLength(7)
    expect(sys.txns.size()).toBe(0) // العمليات هي اللي انقطع عندها
  })

  it('الدفعة المعلّقة لا تُحسب استيرادًا سابقًا', async () => {
    const sys = makeFirestoreLike()
    const preview = await sys.importer.preview(request)
    await expect(sys.importer.commit(request, preview)).rejects.toThrow()

    // نفس الملف: findByFileHash لا يرى إلا committed
    const second = await sys.importer.preview(request)
    expect(second.previousBatch).toBeNull()
  })

  it('التنظيف عند فتح التطبيق يشيل كل أثر الدفعة المعلّقة', async () => {
    const sys = makeFirestoreLike()
    const preview = await sys.importer.preview(request)
    await expect(sys.importer.commit(request, preview)).rejects.toThrow()
    // الانقطاع عند العمليات: السجلات مكتوبة والعمليات لأ
    expect(sys.sources.all()).toHaveLength(7)
    expect(sys.txns.size()).toBe(0)

    const staged = await sys.resumer.findStaged()
    expect(staged).toHaveLength(1)

    const outcomes = await sys.resumer.cleanupAll()
    expect(outcomes).toHaveLength(1)
    expect(outcomes[0].deletedRecords).toBe(7)
    // 7 معرّف عملية اتطلب حذفها؛ حذف غير الموجود لا يضر وهو ما يجعل
    // التنظيف يعمل مهما كانت اللحظة التي انقطع فيها الاتصال
    expect(outcomes[0].deletedTransactions).toBe(7)
    expect(outcomes[0].fileName).toBe('baseline.csv')

    expect(sys.txns.size()).toBe(0)
    expect(sys.sources.all()).toHaveLength(0)
    expect(sys.batches.all()[0].state).toBe('reverted')
  })

  it('التنظيف آمن التكرار ولا يمس الدفعات المكتملة', async () => {
    const sys = makeFirestoreLike()

    sys.txns.failNext = false
    const good = await sys.importer.preview(request)
    const goodBatch = await sys.importer.commit(request, good)
    expect(goodBatch.state).toBe('committed')
    expect(sys.txns.size()).toBe(7)

    // دفعة ثانية تفشل
    const other = { ...request, fileName: 'other.csv', content: baseline.replace(/DEMO-/g, 'OTHER-') }
    const badPreview = await sys.importer.preview(other)
    sys.txns.failNext = true
    await expect(sys.importer.commit(other, badPreview)).rejects.toThrow()
    // 7 سجل من الدفعة الناجحة + 7 من المعلّقة
    expect(sys.sources.all()).toHaveLength(14)
    expect(sys.txns.size()).toBe(7) // عمليات الدفعة الفاشلة لم تُكتب

    await sys.resumer.cleanupAll()
    expect(sys.txns.size()).toBe(7) // المكتملة سليمة، المعلّقة اتشالت
    expect(sys.sources.all()).toHaveLength(7) // سجلات المعلّقة اتشالت كمان

    // تنظيف تاني بلا شيء معلّق: لا يفعل شيئًا ولا يرمي
    expect(await sys.resumer.cleanupAll()).toEqual([])
    expect(sys.txns.size()).toBe(7)
  })

  it('لا يُنظَّف ما هو مكتمل حتى لو طُلب صراحةً', async () => {
    const sys = makeFirestoreLike()
    sys.txns.failNext = false
    const preview = await sys.importer.preview(request)
    const batch = await sys.importer.commit(request, preview)

    await expect(sys.resumer.cleanup(batch.id)).rejects.toThrow(/committed/)
    expect(sys.txns.size()).toBe(7)
  })
})
