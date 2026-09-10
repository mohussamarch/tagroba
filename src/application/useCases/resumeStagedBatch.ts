import type { Id, ImportBatch } from '../../domain/entities/types'
import type {
  ImportBatchRepository,
  SourceRecordRepository,
  TransactionRepository,
} from '../ports/repositories'

/**
 * ResumeStagedBatch — تنظيف دفعة انقطعت قبل اكتمالها.
 *
 * يحل قيد ARCHITECTURE.md §10.5: معاملة Firestore لا تغطي مئات المستندات،
 * فالذرّية الحقيقية هناك مستحيلة بمعاملة واحدة.
 *
 * الحل المطبَّق بدلها — **بروتوكول علامة الالتزام**:
 *
 *   ١. تُكتب الدفعة بحالة `staged` **قبل** أي عملية
 *   ٢. تُكتب العمليات وسجلات المصدر (قد تنقطع هنا)
 *   ٣. تتحول الدفعة إلى `committed` بكتابة **واحدة** ذرّية بطبيعتها
 *
 * فأي انقطاع يترك دفعة `staged`، وما كُتب تحتها **غير معتمد**:
 * - `findByFileHash` لا يرى إلا `committed`، فلا تُحسب كاستيراد سابق
 * - هذه الحالة تنظّف ما تحت الدفعة المعلّقة عند فتح التطبيق
 *
 * النتيجة: «صفر أو كامل الدفعة» تتحقق **بالأثر** لا بالمعاملة —
 * لا يظهر للمستخدم أبدًا نصف دفعة معتمدة.
 */

export interface StagedCleanupOutcome {
  batchId: Id
  fileName: string
  deletedTransactions: number
  deletedRecords: number
  /** سبب تعذر التنظيف، لو تعذر. الدفعة تبقى معلّقة ولا تُحذف منها حاجة. */
  error?: string
}

export interface ResumeStagedBatchDeps {
  txns: TransactionRepository
  sources: SourceRecordRepository
  batches: ImportBatchRepository
  /** كم دفعة أخيرة تُفحص. القراءة محدودة — ARCHITECTURE.md §5.6. */
  scanLimit?: number
}

export function makeResumeStagedBatch(deps: ResumeStagedBatchDeps) {
  /** يعرض الدفعات المعلّقة بلا حذف — للعرض على المستخدم قبل التنظيف. */
  async function findStaged(): Promise<ImportBatch[]> {
    const recent = await deps.batches.listRecent(deps.scanLimit ?? 20)
    return recent.filter((b) => b.state === 'staged')
  }

  /**
   * ينظّف دفعة معلّقة: يحذف ما كُتب تحتها ويعلّمها `reverted`.
   *
   * آمن التكرار: لو انقطع التنظيف نفسه تبقى الدفعة `staged` فيُعاد التنظيف
   * في المرة الجاية، وحذف ما هو محذوف أصلًا لا يضر.
   */
  async function cleanup(batchId: Id): Promise<StagedCleanupOutcome> {
    const batch = await deps.batches.findById(batchId)
    if (!batch) throw new Error(`دفعة غير موجودة: ${batchId}`)
    if (batch.state !== 'staged') {
      throw new Error(
        `الدفعة دي حالتها «${batch.state}» مش «staged». التنظيف بيشتغل على المعلّق بس.`,
      )
    }

    const records = await deps.sources.listByBatch(batchId)
    const txnIds = records
      .map((r) => r.transactionId)
      .filter((id): id is Id => id !== null)

    // الترتيب مقصود: العمليات أولًا ثم السجلات ثم علامة الدفعة.
    // لو انقطع بينهما تبقى الدفعة staged ويُعاد التنظيف بلا ضرر.
    if (txnIds.length > 0) await deps.txns.deleteMany(txnIds)
    if (records.length > 0) await deps.sources.deleteMany(records.map((r) => r.id))
    await deps.batches.updateState(batchId, 'reverted')

    return {
      batchId,
      fileName: batch.fileName,
      deletedTransactions: txnIds.length,
      deletedRecords: records.length,
    }
  }

  /**
   * ينظّف كل ما هو معلّق. يُستدعى عند فتح التطبيق.
   *
   * ⚠️ دفعة واحدة تعذر تنظيفها **لا توقف الباقي ولا تفتح التطبيق على خطأ**:
   * عطل sanitize القديم ترك حقل `id` مختلفًا عن مسار الوثيقة، فكان
   * `findById` يرمي فتتوقف كل الشاشات (بلاغ المالك 2026-09-11،
   * tests/acceptance/importVisibility.test.ts).
   */
  async function cleanupAll(): Promise<StagedCleanupOutcome[]> {
    const staged = await findStaged()
    const outcomes: StagedCleanupOutcome[] = []
    for (const batch of staged) {
      try {
        outcomes.push(await cleanup(batch.id))
      } catch (error) {
        outcomes.push({
          batchId: batch.id,
          fileName: batch.fileName,
          deletedTransactions: 0,
          deletedRecords: 0,
          error: error instanceof Error ? error.message : String(error),
        })
      }
    }
    return outcomes
  }

  return { findStaged, cleanup, cleanupAll }
}
