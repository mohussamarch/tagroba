import type { Id, ImportBatch } from '../../domain/entities/types'
import type {
  AllocationRepository,
  ImportBatchRepository,
  ObligationRepository,
  SettlementRepository,
  SourceRecordRepository,
  TransactionRepository,
  UnitOfWork,
} from '../ports/repositories'

/**
 * RevertImportBatch — التراجع عن دفعة استيراد.
 *
 * spec/03، قيد حاسم:
 * «batchId ليس ملكية مطلقة للعملية: عند التراجع عن دفعة **لا تحذف عملية
 *  تؤيدها مصادر أخرى** أو **دخلت عليها تسوية لاحقة**.
 *  اعرض الأثر وطبّق عكسًا متسقًا.»
 *
 * لذلك التراجع هنا **ليس حذفًا أعمى**: يفحص كل عملية أولًا،
 * ويحتفظ بما له سند آخر، ويشرح ماذا فعل بكل واحدة.
 */

export type RevertDecision = 'deleted' | 'kept_other_source' | 'kept_has_settlement' | 'kept_has_allocation'

export interface RevertLineOutcome {
  transactionId: Id
  decision: RevertDecision
  reason: string
}

export interface RevertPlan {
  batchId: Id
  /** ما سيُحذف فعلًا. */
  toDelete: Id[]
  /** ما سيبقى ولماذا. */
  toKeep: RevertLineOutcome[]
  outcomes: RevertLineOutcome[]
  /** هل التراجع نظيف تمامًا (لا شيء محتفظ به)؟ */
  isClean: boolean
  /** عدد العمليات اللي الدفعة سجّلتها وقت الاستيراد. */
  expectedCount: number
  /** عدد سجلات المصدر اللي اتلقت فعلًا للدفعة دي. */
  recordsFound: number
  /**
   * الدفعة سجّلت عمليات لكن ولا سجل مصدر اتلقى لها — غالبًا معرّفات تالفة من
   * الحفظ القديم (HANDOVER §23). التراجع يُرفض لحد ما تتصلح، وإلا كان هيعلّم الدفعة
   * «متراجَع عنها» من غير ما يشيل ولا عملية.
   */
  blocked: boolean
}

export interface RevertDeps {
  txns: TransactionRepository
  sources: SourceRecordRepository
  batches: ImportBatchRepository
  settlements: SettlementRepository
  allocations: AllocationRepository
  obligations: ObligationRepository
  uow: UnitOfWork
}

export const REVERT_BLOCKED_MESSAGE =
  'مقدرناش نلاقي عمليات الاستيراد ده. غالبًا معرّفاته اتحفظت ناقصة في نسخة قديمة. ' +
  'من الإعدادات دوس «افحص البيانات القديمة» وصلّح، وبعدين جرّب التراجع تاني.'

export function makeRevertImportBatch(deps: RevertDeps) {
  /** سجل الاستيرادات، الأحدث أولًا — للعرض فقط. */
  async function history(limit = 50): Promise<ImportBatch[]> {
    const recent = await deps.batches.listRecent(limit)
    return [...recent].sort((a, b) => b.importedAt.localeCompare(a.importedAt))
  }

  /** يحسب الأثر بلا أي كتابة — يُعرض للمستخدم قبل التأكيد (spec/03). */
  async function plan(batchId: Id): Promise<RevertPlan> {
    const batch = await deps.batches.findById(batchId)
    if (!batch) throw new Error(`دفعة غير موجودة: ${batchId}`)
    if (batch.state === 'reverted') throw new Error('الدفعة دي متراجَع عنها قبل كده')

    const batchRecords = await deps.sources.listByBatch(batchId)
    const expectedCount = batch.counts.imported
    const recordsFound = batchRecords.length
    const blocked = expectedCount > 0 && recordsFound === 0
    const txnIds = batchRecords
      .map((r) => r.transactionId)
      .filter((id): id is Id => id !== null)

    if (txnIds.length === 0) {
      return { batchId, toDelete: [], toKeep: [], outcomes: [], isClean: true, expectedCount, recordsFound, blocked }
    }

    const [allSources, settlements, allocations, obligations] = await Promise.all([
      deps.sources.listByTransactionIds(txnIds),
      deps.settlements.listByTransactionIds(txnIds),
      deps.allocations.listByTransactionIds(txnIds),
      deps.obligations.listByTransactionIds(txnIds),
    ])

    const settledIds = new Set(settlements.map((s) => s.transactionId))
    const allocatedIds = new Set(allocations.map((a) => a.transactionId))
    const obligationOriginIds = new Set(obligations.map((o) => o.originTransactionId))

    const sourceCountByTxn = new Map<Id, number>()
    for (const record of allSources) {
      if (!record.transactionId) continue
      sourceCountByTxn.set(record.transactionId, (sourceCountByTxn.get(record.transactionId) ?? 0) + 1)
    }

    const toDelete: Id[] = []
    const toKeep: RevertLineOutcome[] = []
    const outcomes: RevertLineOutcome[] = []

    for (const id of txnIds) {
      let outcome: RevertLineOutcome

      if (settledIds.has(id)) {
        outcome = {
          transactionId: id,
          decision: 'kept_has_settlement',
          reason: 'العملية دي دخلت عليها تسوية بعد الاستيراد، فمش هتتحذف',
        }
      } else if (allocatedIds.has(id) || obligationOriginIds.has(id)) {
        outcome = {
          transactionId: id,
          decision: 'kept_has_allocation',
          reason: 'العملية دي متربطة بشخص (تخصيص أو التزام)، فمش هتتحذف',
        }
      } else if ((sourceCountByTxn.get(id) ?? 0) > 1) {
        outcome = {
          transactionId: id,
          decision: 'kept_other_source',
          reason: 'العملية دي ليها مصدر تاني غير الدفعة دي (رسالة مثلًا)، فمش هتتحذف',
        }
      } else {
        outcome = {
          transactionId: id,
          decision: 'deleted',
          reason: 'الدفعة دي هي المصدر الوحيد ومفيش عليها تسويات',
        }
        toDelete.push(id)
      }

      outcomes.push(outcome)
      if (outcome.decision !== 'deleted') toKeep.push(outcome)
    }

    return { batchId, toDelete, toKeep, outcomes, isClean: toKeep.length === 0, expectedCount, recordsFound, blocked }
  }

  /** ينفّذ الخطة ذريًا. سجلات المصدر الخاصة بالدفعة تُحذف دائمًا. */
  async function execute(batchId: Id): Promise<RevertPlan> {
    const revertPlan = await plan(batchId)
    if (revertPlan.blocked) throw new Error(REVERT_BLOCKED_MESSAGE)

    return deps.uow.run(async () => {
      if (revertPlan.toDelete.length > 0) {
        await deps.txns.deleteMany(revertPlan.toDelete)
      }
      const batchRecords = await deps.sources.listByBatch(batchId)
      await deps.sources.deleteMany(batchRecords.map((r) => r.id))
      await deps.batches.updateState(batchId, 'reverted')
      return revertPlan
    })
  }

  return { plan, execute, history }
}
