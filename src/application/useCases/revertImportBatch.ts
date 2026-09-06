import type { Id } from '../../domain/entities/types'
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

export function makeRevertImportBatch(deps: RevertDeps) {
  /** يحسب الأثر بلا أي كتابة — يُعرض للمستخدم قبل التأكيد (spec/03). */
  async function plan(batchId: Id): Promise<RevertPlan> {
    const batch = await deps.batches.findById(batchId)
    if (!batch) throw new Error(`دفعة غير موجودة: ${batchId}`)
    if (batch.state === 'reverted') throw new Error('الدفعة دي متراجَع عنها قبل كده')

    const batchRecords = await deps.sources.listByBatch(batchId)
    const txnIds = batchRecords
      .map((r) => r.transactionId)
      .filter((id): id is Id => id !== null)

    if (txnIds.length === 0) {
      return { batchId, toDelete: [], toKeep: [], outcomes: [], isClean: true }
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

    return { batchId, toDelete, toKeep, outcomes, isClean: toKeep.length === 0 }
  }

  /** ينفّذ الخطة ذريًا. سجلات المصدر الخاصة بالدفعة تُحذف دائمًا. */
  async function execute(batchId: Id): Promise<RevertPlan> {
    const revertPlan = await plan(batchId)

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

  return { plan, execute }
}
